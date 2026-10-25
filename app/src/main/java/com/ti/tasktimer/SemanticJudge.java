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
 * 三段式：
 * 1) 本地预筛：bge-small-zh 向量模型（EmbedModel）算「任务标题＋备注」
 *    与「屏幕文字摘要」的余弦相似度。≥0.65 判相关放行；≤0.35 判不相关；
 *    中间带升级云端。模型未下载则跳过预筛直接走云端。
 * 2) 云端终判：Supabase Edge Function glm-judge（智谱 GLM 免费版在
 *    服务端，App 里不出现任何模型名）。只传打码后的文字四样，绝不传图。
 *    响应 fallback:false 直接采用；fallback:true/网络错误/超时（15 秒）
 *    一律进降级。
 * 3) 降级（静默）：以本地分数单独定判——中间带从宽放行（降级期证据
 *    最弱，不冤枉用户）并记一条 GuardEvents 备注（一退避窗口一条）。
 *    连续失败后退避：10 分钟起、指数翻倍、上限 30 分钟，不每帧硬撞。
 *
 * 「语义判定」开关关掉时只跑本地预筛，云端一行都不发。
 * 判定来源（本地预筛/云端/本地降级）与分数写进 Result.semanticSource /
 * semanticScore，并留 lastNote 给测试窗读数；降级对普通用户全程静默，
 * 诊断文案只在 diag_mode 开启时才出现在展示底部（见 GuardActivity /
 * GuardTestMode）。
 *
 * 阈值 0.65/0.35 是备料实测（锚点同主题 0.85、无关 0.29）下的暂定值，
 * 写死常量，待真机数据再调。
 * 全部方法为阻塞 IO，只能在后台线程调用。
 */
public final class SemanticJudge {
    static final float HIGH = 0.65f; // 待真机调
    static final float LOW = 0.35f;  // 待真机调
    private static final int MIN_TEXT = 10;

    public static final String SRC_LOCAL = "local";
    public static final String SRC_CLOUD = "cloud";
    public static final String SRC_DEGRADED = "degraded";

    /** 最近一帧的语义读数（给测试窗与日志）：如「本地预筛 0.23（判不相关）」；未过语义层为空串。 */
    public static volatile String lastNote = "";
    /** 最近一帧是否处于降级状态（云端想用而不可用）。 */
    public static volatile boolean lastDegraded = false;

    private static String cachedTaskText;
    private static float[] cachedTaskVec;

    private SemanticJudge() {}

    /** 每帧分析开始时清掉上一帧的读数（PipelineAnalyzer 入口调用）。 */
    public static void beginFrame() {
        lastNote = "";
        lastDegraded = false;
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
        if (task == null || screenText == null) return null;
        if (screenText.replaceAll("\\s+", "").length() < MIN_TEXT) return null;

        String taskText = ((task.title == null ? "" : task.title) + " "
                + (task.note == null ? "" : task.note)).trim();
        if (taskText.isEmpty()) return null;

        // ---- 1) 本地预筛 ----
        Float sim = null;
        if (EmbedModel.isReady(c)) {
            float[] tv = taskVector(c, taskText);
            float[] sv = EmbedModel.embed(c, screenText);
            if (tv != null && sv != null) sim = EmbedModel.cosine(tv, sv);
        }
        if (sim != null) {
            if (sim >= HIGH) {
                lastNote = "本地预筛 " + fmt(sim) + "（判相关）";
                return annotated(ContentAnalyzer.Result.none(), SRC_LOCAL, sim);
            }
            if (sim <= LOW) {
                lastNote = "本地预筛 " + fmt(sim) + "（判不相关）";
                float conf = Math.min(0.9f, 0.55f + (LOW - sim));
                ContentAnalyzer.Result r = new ContentAnalyzer.Result(
                        ContentAnalyzer.CAT_UNRELATED, conf,
                        "语义判定（本地预筛）：屏幕内容与任务「" + task.title + "」相似度 " + fmt(sim) + "，判不相关");
                return annotated(r, SRC_LOCAL, sim);
            }
            // 中间带 → 云端
        }

        // ---- 2) 云端终判 ----
        if (!ScreenGuardPrefs.semanticEnabled(c)) {
            // 开关关：只本地判。中间带按本地口径从宽放行；无分数则维持粗判
            if (sim != null) {
                lastNote = "本地预筛 " + fmt(sim) + "（中间带，语义判定已关，只本地判）";
                return annotated(ContentAnalyzer.Result.none(), SRC_LOCAL, sim);
            }
            return null;
        }
        long now = System.currentTimeMillis();
        if (now < ScreenGuardPrefs.semanticBackoffUntil(c)) {
            return degrade(c, task, sim, coarse, "云端退避中");
        }
        CloudVerdict v = callCloud(c, task, fgPkg, screenText);
        if (v != null) {
            ScreenGuardPrefs.noteSemanticCloudSuccess(c);
            if (v.relevant) {
                lastNote = "云端终判（判相关）";
                return annotated(ContentAnalyzer.Result.none(), SRC_CLOUD, sim);
            }
            lastNote = "云端终判（判不相关）";
            ContentAnalyzer.Result r = new ContentAnalyzer.Result(
                    ContentAnalyzer.CAT_UNRELATED, v.confidence,
                    "语义判定（云端）：" + (v.reason.isEmpty() ? "与任务不相关" : v.reason));
            return annotated(r, SRC_CLOUD, sim);
        }
        ScreenGuardPrefs.noteSemanticCloudFailure(c);
        return degrade(c, task, sim, coarse, "云端不可用");
    }

    /** 降级定判：有中间带分数→从宽放行＋备注；没分数→维持粗判（但给粗判标降级来源，能触发时诊断行才有依据）。 */
    private static ContentAnalyzer.Result degrade(Context c, TaskItem task, Float sim,
                                                  ContentAnalyzer.Result coarse, String why) {
        lastDegraded = true;
        if (sim != null) {
            lastNote = "本地降级 " + fmt(sim) + "（中间带从宽放行，" + why + "）";
            noteDegradeOnce(c, task, sim);
            return annotated(ContentAnalyzer.Result.none(), SRC_DEGRADED, sim);
        }
        lastNote = "本地降级（无本地分数，维持粗判，" + why + "）";
        if (coarse != null && coarse.category != null) {
            coarse.semanticSource = SRC_DEGRADED;
            return coarse;
        }
        return null;
    }

    /** 降级放行备注：同一退避窗口只记一条，防备注刷满事件表；备注带 note 标记，不计分神分。 */
    private static void noteDegradeOnce(Context c, TaskItem task, float sim) {
        try {
            long until = ScreenGuardPrefs.semanticBackoffUntil(c);
            if (ScreenGuardPrefs.semanticNoteForBackoff(c) == until && until > 0) return;
            ScreenGuardPrefs.setSemanticNoteForBackoff(c, until);
            GuardEvents.addNote(c, task, ContentAnalyzer.CAT_UNRELATED, sim,
                    "语义降级放行备注（未触发问询）：云端不可用，本地相似度 " + fmt(sim)
                            + " 在中间带，按从宽口径放行");
        } catch (Exception ignored) { }
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

    /** 调 glm-judge；任何失败（网络/超时/非 200/fallback:true/解析失败）返回 null。 */
    private static CloudVerdict callCloud(Context c, TaskItem task, String fgPkg, String screenText) {
        HttpURLConnection con = null;
        try {
            JSONObject body = new JSONObject();
            body.put("task_title", minimize(task.title == null ? "" : task.title, 200));
            body.put("task_note", minimize(task.note == null ? "" : task.note, 500));
            body.put("app_name", minimize(fgPkg == null ? "" : AppLister.labelOf(c, fgPkg), 200));
            body.put("screen_text", minimize(screenText, 3000));

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
            if (in == null) return null;
            JSONObject r = new JSONObject(Supa.readAll(in));
            if (r.optBoolean("fallback", true)) return null;
            CloudVerdict v = new CloudVerdict();
            v.relevant = r.optBoolean("relevant", false);
            v.confidence = (float) r.optDouble("confidence", 0.6);
            v.reason = r.optString("reason", "");
            return v;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
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
