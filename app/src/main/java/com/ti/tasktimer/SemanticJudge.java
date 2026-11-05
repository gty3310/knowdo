package com.ti.tasktimer;

import android.content.Context;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 语义判定层（v1.21.4）：接在 PipelineAnalyzer 现有粗判的最后，只处理
 * 粗判拿不准的帧（规则给不出明确结论、关键词放行、或只有 0.55 低置信
 * 「无关」这类勉强结论）。明确结论（包名类别/关键词命中/NudeNet）不经过这里。
 *
 * 三段式（v1.69.0 起云端优先，用户定案）：
 * 1) 本地向量分数：bge-small-zh 向量模型（EmbedModel）算「任务标题＋
 *    备注」与「屏幕文字摘要」的余弦相似度——只计算、随判定留痕，不再
 *    单独定判（模型未下载则无分数）。
 * 2) 云端终判：Supabase Edge Function glm-judge（上游模型在服务端，
 *    App 里不出现任何模型名）。只传打码后的文字四样，绝不传图。响应
 *    fallback:false 直接采用；同一（包名＋屏幕文本哈希）5 分钟内复用
 *    缓存 verdict，不重复上云。
 * 3) 本地兜底：没网（src=offline）或云端失败/退避（src=degraded）时
 *    才用本地分数按原阈值 0.65/0.35 裁决，中间带从宽放行；没分数则
 *    维持粗判。连续失败后退避：10 分钟起、指数翻倍、上限 30 分钟。
 *
 * 「语义判定」开关关掉时只跑本地分数定判，云端一行都不发（src=embed）。
 * 每个出口都经 trace() 落一条带 src/score/cloud 的备注（同状态 10 分钟
 * 内合并，防刷满事件表），触发事件由 VerdictEngine 带同一组留痕字段。
 * 判定来源与分数写进 Result.semanticSource / semanticScore，并留
 * lastNote 给测试窗读数；诊断文案只在 diag_mode 开启时才出现在展示
 * 底部（见 GuardActivity / GuardTestMode）。
 *
 * 阈值 0.65/0.35 是备料实测（锚点同主题 0.85、无关 0.29）下的暂定值，
 * 写死常量，待真机数据再调。
 * 全部方法为阻塞 IO，只能在后台线程调用。
 */
public final class SemanticJudge {
    static final float HIGH = 0.65f; // 待真机调
    static final float LOW = 0.35f;  // 待真机调
    private static final int MIN_TEXT = 10;

    /** 判定来源（v1.69.0 起与 guard_events 的 src 字段同值域）。 */
    public static final String SRC_EMBED = "embed";
    /** 旧名兼容（值已改为 embed 口径）。 */
    public static final String SRC_LOCAL = SRC_EMBED;
    public static final String SRC_CLOUD = "cloud";
    public static final String SRC_OFFLINE = "offline";
    public static final String SRC_DEGRADED = "degraded";

    /** 最近一帧的语义读数（给测试窗与日志）：如「本地预筛 0.23（判不相关）」；未过语义层为空串。 */
    public static volatile String lastNote = "";
    /** 最近一帧是否处于降级状态（云端想用而不可用）。 */
    public static volatile boolean lastDegraded = false;
    /** 最近一帧的云端结果（ok／fallback:&lt;错误类别&gt;）；没走云端为空串。VerdictEngine 写事件时取用。 */
    public static volatile String lastCloud = "";

    private static String cachedTaskText;
    private static float[] cachedTaskVec;

    // ---- 云端 verdict 缓存（v1.69.0 成本护栏）：同（包名＋屏幕文本哈希）5 分钟复用 ----
    private static final long CACHE_MS = 5 * 60_000L;
    private static final java.util.Map<String, CachedVerdict> VERDICTS = new java.util.HashMap<>();

    private static final class CachedVerdict {
        boolean relevant;
        float confidence;
        String reason = "";
        long at;
    }

    // ---- 留痕去重：同（kind＋任务）10 分钟内只记一条，状态变化必记 ----
    private static String lastTraceKey = "";
    private static long lastTraceAt = 0;

    private SemanticJudge() {}

    /** 每帧分析开始时清掉上一帧的读数（PipelineAnalyzer 入口调用）。 */
    public static void beginFrame() {
        lastNote = "";
        lastDegraded = false;
        lastCloud = "";
    }

    /**
     * 复核一帧粗判。返回非 null＝语义层的结论（可能推翻 coarse）；
     * 返回 null＝语义层无从判定，维持 coarse。
     * coarse 只会是：不表态（none）、关键词放行（none）、低置信「无关」。
     */
    public static ContentAnalyzer.Result refine(Context c, TaskItem task, String fgPkg,
                                                String screenText, ContentAnalyzer.Result coarse) {
        lastNote = "";
        lastDegraded = false;
        lastCloud = "";
        if (task == null || screenText == null) return null;
        if (screenText.replaceAll("\\s+", "").length() < MIN_TEXT) return null;

        String taskText = ((task.title == null ? "" : task.title) + " "
                + (task.note == null ? "" : task.note)).trim();
        if (taskText.isEmpty()) return null;

        // ---- 本地向量分数：只计算、留痕；有网时不再单独定判（v1.69.0） ----
        Float sim = null;
        if (EmbedModel.isReady(c)) {
            float[] tv = taskVector(c, taskText);
            float[] sv = EmbedModel.embed(c, screenText);
            if (tv != null && sv != null) sim = EmbedModel.cosine(tv, sv);
        }

        // ---- 开关关：只本地判（既有口径不变，来源标 embed） ----
        if (!ScreenGuardPrefs.semanticEnabled(c)) {
            if (sim == null) {
                trace(c, task, SRC_EMBED, null, null, "embed-none",
                        "语义判定（本地）：没有本地分数，维持粗判（语义判定已关）");
                return null;
            }
            if (sim >= HIGH) {
                lastNote = "本地预筛 " + fmt(sim) + "（判相关）";
                trace(c, task, SRC_EMBED, sim, null, "embed-relevant",
                        "语义判定（本地）：相似度 " + fmt(sim) + "，判相关放行（语义判定已关，只本地判）");
                return annotated(ContentAnalyzer.Result.none(), SRC_EMBED, sim);
            }
            if (sim <= LOW) {
                lastNote = "本地预筛 " + fmt(sim) + "（判不相关）";
                trace(c, task, SRC_EMBED, sim, null, "embed-unrelated",
                        "语义判定（本地）：相似度 " + fmt(sim) + "，判不相关（语义判定已关，只本地判）");
                float conf = Math.min(0.9f, 0.55f + (LOW - sim));
                ContentAnalyzer.Result r = new ContentAnalyzer.Result(
                        ContentAnalyzer.CAT_UNRELATED, conf,
                        "语义判定（本地预筛）：屏幕内容与任务「" + task.title + "」相似度 " + fmt(sim) + "，判不相关");
                return annotated(r, SRC_EMBED, sim);
            }
            lastNote = "本地预筛 " + fmt(sim) + "（中间带，语义判定已关，只本地判）";
            trace(c, task, SRC_EMBED, sim, null, "embed-middle",
                    "语义判定（本地）：相似度 " + fmt(sim) + " 在中间带，从宽放行（语义判定已关，只本地判）");
            return annotated(ContentAnalyzer.Result.none(), SRC_EMBED, sim);
        }

        // ---- 云端优先：缓存 → 退避 → 上云 → 本地兜底 ----
        String key = cacheKey(fgPkg, screenText);
        CachedVerdict hit = verdictCacheGet(key);
        if (hit != null) {
            lastCloud = "ok";
            return applyCloudVerdict(c, task, hit.relevant, hit.confidence, hit.reason, sim, true);
        }
        long now = System.currentTimeMillis();
        if (now < ScreenGuardPrefs.semanticBackoffUntil(c)) {
            lastCloud = "fallback:backoff";
            return localAdjudicate(c, task, sim, coarse, SRC_DEGRADED, "云端退避中", "fallback:backoff");
        }
        CloudAttempt att = callCloud(c, task, fgPkg, screenText);
        if (att.verdict != null) {
            ScreenGuardPrefs.noteSemanticCloudSuccess(c);
            verdictCachePut(key, att.verdict);
            lastCloud = "ok";
            return applyCloudVerdict(c, task, att.verdict.relevant, att.verdict.confidence,
                    att.verdict.reason, sim, false);
        }
        ScreenGuardPrefs.noteSemanticCloudFailure(c);
        lastCloud = att.cloudLabel;
        return localAdjudicate(c, task, sim, coarse, att.src, att.why, att.cloudLabel);
    }

    /** 云端结论落地（缓存命中与新判共用）：判相关放行、判不相关出「无关」结论；两种都留痕。 */
    private static ContentAnalyzer.Result applyCloudVerdict(Context c, TaskItem task, boolean relevant,
                                                            float confidence, String reason,
                                                            Float sim, boolean fromCache) {
        String tail = fromCache ? "（缓存复用）" : "";
        if (relevant) {
            lastNote = "云端终判（判相关）" + tail;
            trace(c, task, SRC_CLOUD, sim, "ok", "cloud-relevant" + (fromCache ? "-cache" : ""),
                    "语义判定（云端）：与任务相关，放行" + tail);
            return annotated(ContentAnalyzer.Result.none(), SRC_CLOUD, sim);
        }
        lastNote = "云端终判（判不相关）" + tail;
        trace(c, task, SRC_CLOUD, sim, "ok", "cloud-unrelated" + (fromCache ? "-cache" : ""),
                "语义判定（云端）：与任务不相关" + tail);
        ContentAnalyzer.Result r = new ContentAnalyzer.Result(
                ContentAnalyzer.CAT_UNRELATED, confidence,
                "语义判定（云端）：" + (reason == null || reason.isEmpty() ? "与任务不相关" : reason));
        return annotated(r, SRC_CLOUD, sim);
    }

    /**
     * 本地兜底裁决（没网 src=offline／云端失败或退避 src=degraded）：有分数按原
     * 阈值定判、中间带从宽放行；没分数维持粗判（给粗判标上来源，能触发时事件
     * 才有留痕）。每个分支都留痕，不再静默。
     */
    private static ContentAnalyzer.Result localAdjudicate(Context c, TaskItem task, Float sim,
                                                          ContentAnalyzer.Result coarse,
                                                          String src, String why, String cloudLabel) {
        lastDegraded = SRC_DEGRADED.equals(src);
        String srcZh = SRC_OFFLINE.equals(src) ? "离线" : "降级";
        if (sim != null) {
            if (sim >= HIGH) {
                lastNote = "本地" + srcZh + " " + fmt(sim) + "（判相关，" + why + "）";
                trace(c, task, src, sim, cloudLabel, src + "-relevant",
                        "语义判定（本地兜底）：相似度 " + fmt(sim) + "，判相关放行（" + why + "）");
                return annotated(ContentAnalyzer.Result.none(), src, sim);
            }
            if (sim <= LOW) {
                lastNote = "本地" + srcZh + " " + fmt(sim) + "（判不相关，" + why + "）";
                trace(c, task, src, sim, cloudLabel, src + "-unrelated",
                        "语义判定（本地兜底）：相似度 " + fmt(sim) + "，判不相关（" + why + "）");
                float conf = Math.min(0.9f, 0.55f + (LOW - sim));
                ContentAnalyzer.Result r = new ContentAnalyzer.Result(
                        ContentAnalyzer.CAT_UNRELATED, conf,
                        "语义判定（本地兜底）：" + why + "，屏幕内容与任务「" + task.title
                                + "」相似度 " + fmt(sim) + "，判不相关");
                return annotated(r, src, sim);
            }
            // 中间带从宽放行：降级沿用既有的一窗口一备注，其余走 trace
            lastNote = "本地" + srcZh + " " + fmt(sim) + "（中间带从宽放行，" + why + "）";
            if (SRC_DEGRADED.equals(src)) {
                noteDegradeOnce(c, task, sim, cloudLabel);
            } else {
                trace(c, task, src, sim, cloudLabel, src + "-middle",
                        "语义离线放行备注（未触发问询）：没有网络，本地相似度 " + fmt(sim)
                                + " 在中间带，按从宽口径放行");
            }
            return annotated(ContentAnalyzer.Result.none(), src, sim);
        }
        lastNote = "本地" + srcZh + "（无本地分数，维持粗判，" + why + "）";
        trace(c, task, src, null, cloudLabel, src + "-none",
                "语义判定（本地兜底）：没有本地分数，维持粗判（" + why + "）");
        if (coarse != null && coarse.category != null) {
            coarse.semanticSource = src;
            return coarse;
        }
        return null;
    }

    /** 降级放行备注：同一退避窗口只记一条，防备注刷满事件表；备注带 note 标记，不计分神分。 */
    private static void noteDegradeOnce(Context c, TaskItem task, float sim, String cloudLabel) {
        try {
            long until = ScreenGuardPrefs.semanticBackoffUntil(c);
            if (ScreenGuardPrefs.semanticNoteForBackoff(c) == until && until > 0) return;
            ScreenGuardPrefs.setSemanticNoteForBackoff(c, until);
            GuardEvents.addNoteTraced(c, task, ContentAnalyzer.CAT_UNRELATED, sim,
                    "语义降级放行备注（未触发问询）：云端不可用，本地相似度 " + fmt(sim)
                            + " 在中间带，按从宽口径放行",
                    SRC_DEGRADED, sim, cloudLabel);
        } catch (Exception ignored) { }
    }

    /**
     * 判定留痕（v1.69.0）：语义层每个出口经此落一条备注（note，不计分神），
     * 带 src/score/cloud。同（kind＋任务）10 分钟内只记一条——同屏久停会反复
     * 复核同一帧，不合并会把 200 条事件表刷满、挤掉真正的触发事件；状态一变
     * （kind 不同）立即记，不受合并影响。
     */
    private static void trace(Context c, TaskItem task, String src, Float sim, String cloud,
                              String kind, String detail) {
        try {
            String key = kind + "|" + (task == null ? "" : task.id);
            long now = System.currentTimeMillis();
            if (key.equals(lastTraceKey) && now - lastTraceAt < 10 * 60_000L) return;
            lastTraceKey = key;
            lastTraceAt = now;
            GuardEvents.addNoteTraced(c, task, ContentAnalyzer.CAT_UNRELATED,
                    sim == null ? 0f : sim, detail, src, sim, cloud);
        } catch (Exception ignored) { }
    }

    // ---------------- 云端 verdict 缓存（包名＋屏幕文本哈希，5 分钟） ----------------

    private static String cacheKey(String fgPkg, String screenText) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest((screenText == null ? "" : screenText)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return (fgPkg == null ? "" : fgPkg) + "|" + sb;
        } catch (Exception e) {
            return (fgPkg == null ? "" : fgPkg) + "|" + (screenText == null ? 0 : screenText.hashCode());
        }
    }

    private static synchronized CachedVerdict verdictCacheGet(String key) {
        CachedVerdict v = VERDICTS.get(key);
        if (v == null) return null;
        if (System.currentTimeMillis() - v.at > CACHE_MS) {
            VERDICTS.remove(key);
            return null;
        }
        return v;
    }

    private static synchronized void verdictCachePut(String key, CloudVerdict v) {
        if (VERDICTS.size() > 64) VERDICTS.clear();
        CachedVerdict cv = new CachedVerdict();
        cv.relevant = v.relevant;
        cv.confidence = v.confidence;
        cv.reason = v.reason;
        cv.at = System.currentTimeMillis();
        VERDICTS.put(key, cv);
    }

    private static ContentAnalyzer.Result annotated(ContentAnalyzer.Result r, String src, Float sim) {
        r.semanticSource = src;
        if (sim != null) r.semanticScore = sim;
        return r;
    }

    private static float[] taskVector(Context c, String taskText) {
        if (taskText.equals(cachedTaskText) && cachedTaskVec != null) return cachedTaskVec;
        float[] v = EmbedModel.embed(c, taskText);
        if (v != null) {
            cachedTaskText = taskText;
            cachedTaskVec = v;
        }
        return v;
    }

    private static String fmt(float sim) {
        return String.format(Locale.US, "%.2f", sim);
    }

    // ---------------- 云端 ----------------

    private static final class CloudVerdict {
        boolean relevant;
        float confidence;
        String reason = "";
    }

    /** 一次云端尝试的结果：verdict 非 null 为成功；失败带兜底来源与云端结果标签。 */
    private static final class CloudAttempt {
        CloudVerdict verdict;
        String src;        // 失败时的兜底来源：SRC_OFFLINE（没网）／SRC_DEGRADED（云端坏了）
        String cloudLabel; // fallback:<错误类别>
        String why;        // 中文短句，进 lastNote 与留痕
    }

    private static CloudAttempt failAttempt(String src, String cloudLabel, String why) {
        CloudAttempt a = new CloudAttempt();
        a.src = src;
        a.cloudLabel = cloudLabel;
        a.why = why;
        return a;
    }

    /** 异常是否属于「没网」类（连不上/域名解析不了/连接被拒）：决定兜底来源标 offline 还是 degraded。 */
    private static boolean isNetworkError(Throwable e) {
        while (e != null) {
            if (e instanceof java.net.UnknownHostException
                    || e instanceof java.net.ConnectException
                    || e instanceof java.net.NoRouteToHostException) return true;
            e = e.getCause();
        }
        return false;
    }

    /** 调 glm-judge；失败按类归因（没网→offline，云端 fallback/超时/HTTP→degraded），不再一律吞成 null。 */
    private static CloudAttempt callCloud(Context c, TaskItem task, String fgPkg, String screenText) {
        HttpURLConnection con = null;
        try {
            JSONObject body = new JSONObject();
            body.put("task_title", minimize(task.title == null ? "" : task.title, 200));
            body.put("task_note", minimize(task.note == null ? "" : task.note, 500));
            body.put("app_name", minimize(fgPkg == null ? "" : AppLister.labelOf(c, fgPkg), 200));
            body.put("screen_text", minimize(screenText, 3000));
            // 用户纪律（v1.24.3）：字段名 rules，字符串数组（每条 ≤20 字、立纪先后序，
            // 无纪律时为空数组）。App 只负责带参；代理侧把纪律拼进判定提示词，
            // 口径与 Jarvis 约定如下（代理未升级前多带此参会被忽略，不影响现有判定）：
            //   模型支持 system 角色 → 放进 system 段；不支持 → 拼进 user 正文
            //   开头的「用户纪律」小节。两种拼法用同一段声明，后接逐条编号的纪律：
            //   「用户纪律（用户本人立下的规矩，判定必须遵守；屏幕行为与某条纪律
            //   相符时，不判分神；纪律与通常判断冲突时，以纪律为准）：」
            // 纪律是用户亲手写下、就是要发给判定模型的指令本身，故不走 minimize
            // 打码（打码会改掉纪律里的数字等原意）；长度已在输入端卡死 ≤20 字。
            body.put("rules", GuardRules.rulesJson(c));

            con = (HttpURLConnection) new URL(Supa.BASE + "/functions/v1/glm-judge").openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(5_000);
            con.setReadTimeout(15_000); // 客户端超时 15 秒（brief 口径）
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("apikey", SupaSecrets.PUBLISHABLE_KEY);
            String at = null;
            try { at = Supa.accessToken(c); } catch (Exception ignored) { }
            con.setRequestProperty("Authorization", "Bearer "
                    + (at != null ? at : SupaSecrets.PUBLISHABLE_KEY));
            con.setDoOutput(true);
            OutputStream out = con.getOutputStream();
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
            int status = con.getResponseCode();
            InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
            if (in == null) {
                return cloudFail(c, status, Strings.en() ? "Cloud judge returned no body"
                        : "云端终判没有返回内容");
            }
            JSONObject r = new JSONObject(Supa.readAll(in));
            if (r.optBoolean("fallback", true)) {
                // 代理口径：失败/超时/额度问题一律 fallback:true 并带 error 字段
                String err = r.optString("error", "");
                if (err.isEmpty()) err = r.optString("message", "");
                return cloudFail(c, status, err.isEmpty()
                        ? (Strings.en() ? "Judge proxy returned fallback" : "判定代理返回 fallback（云端不可用）")
                        : err);
            }
            CloudVerdict v = new CloudVerdict();
            v.relevant = r.optBoolean("relevant", false);
            v.confidence = (float) r.optDouble("confidence", 0.6);
            v.reason = r.optString("reason", "");
            CloudAttempt ok = new CloudAttempt();
            ok.verdict = v;
            return ok;
        } catch (Exception e) {
            if (isNetworkError(e)) {
                ErrorReporter.report(c, Strings.en() ? "Guard verdict" : "守卫判定", 0,
                        ErrorReporter.summarize(e));
                return failAttempt(SRC_OFFLINE, "fallback:network",
                        Strings.en() ? "No network" : "没有网络");
            }
            return cloudFail(c, 0, ErrorReporter.summarize(e));
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /**
     * 云端终判失败的统一出口（v1.24.1 起，v1.69.0 改带归因）：兜底口径不变，
     * 只多两步——测试者模式开启时把状态码/fallback 原因经 ErrorReporter 亮
     * 出来（失败本身已被退避纪律节流，不会逐帧刷屏）；并把失败归类成
     * degraded＋fallback:&lt;错误类别&gt;，供事件留痕区分「没网」与「云端坏了」。
     */
    private static CloudAttempt cloudFail(Context c, int status, String detail) {
        ErrorReporter.report(c, Strings.en() ? "Guard verdict" : "守卫判定", status, detail);
        String label = detail == null ? "" : detail.trim();
        // 错误类别只收短 ASCII 标识（如 upstream_http_429）；整句文案归到 http_<状态>/proxy
        if (!label.matches("[A-Za-z0-9_:.+\\-]{1,60}")) {
            label = status > 0 ? "http_" + status : "proxy";
        }
        return failAttempt(SRC_DEGRADED, "fallback:" + label,
                Strings.en() ? "Cloud unavailable" : "云端不可用");
    }

    // ---------------- 上传前数据最小化（App 侧第一道，与代理侧同口径） ----------------

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern IDCARD = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");
    private static final Pattern LONGNUM = Pattern.compile("(?<!\\d)\\d{15,19}(?!\\d)");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern CODE_PHRASE = Pattern.compile("(验证码|校验码|动态码)(\\s*[:：]?\\s*)\\d{4,8}");
    private static final Pattern SIX_DIGITS = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    /** 截断到 maxLen 并打码敏感串：邮箱/身份证/长数字串/手机号/验证码句式/独立 6 位数字串。 */
    public static String minimize(String text, int maxLen) {
        if (text == null) return "";
        String s = text.length() > maxLen ? text.substring(0, maxLen) : text;
        s = EMAIL.matcher(s).replaceAll("[邮箱]");
        s = IDCARD.matcher(s).replaceAll("[身份证]");
        s = LONGNUM.matcher(s).replaceAll("[长数字]");
        s = PHONE.matcher(s).replaceAll("[手机号]");
        s = CODE_PHRASE.matcher(s).replaceAll("$1$2[已打码]");
        s = SIX_DIGITS.matcher(s).replaceAll("[数字]");
        return s;
    }
}
