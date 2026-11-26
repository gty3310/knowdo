package com.ti.tasktimer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

/**
 * 崩溃／错误上报（v1.98.1）：挂在「测试者模式」（ScreenGuardPrefs.diagMode）
 * 之下——关着时本类一切入口都是彻底空操作；开着时把错误与崩溃落本地、
 * 按指纹去重，随 SbSync 既有同步节奏批量上传到 Supabase 供远程 debug。
 *
 * 捕获两路：
 * - non-fatal：ErrorReporter.report 在原悬浮窗展示之外多调 recordReported
 *   落一条（只有来源＋摘要，没有堆栈）；
 * - fatal：KnowDoApp 安装的 UncaughtExceptionHandler——同步写文件、try/catch
 *   全包、最后链回原 handler 让进程照常退出；handler 里绝不发网络。
 *
 * 指纹（行业口径，见 knowdo-release 的最佳实践调研）：异常类名＋顶部 App
 * 自有帧的「类名.方法名」——**不含行号**（行号随无关编辑平移，会让同一
 * bug 换 ID、去重与封顶双双失效）；指纹带算法版本 fp_v，将来改算法时升
 * 版本、新旧可区分。本构建 minifyEnabled=false、无混淆，类名方法名是真名。
 *
 * 存储形状（本地文件 crash_reports.json 与云端同一结构）：
 * - details：{uuid: 明细}——完整堆栈只留 App 帧、截断封顶；带 UUID 供服务端
 *   幂等防双报（上传成功但回执丢了，下轮按 uuid 合并不会重复）；
 * - daily：{日期: {指纹: {count, first, last, uuid}}}——计数与明细**分开存**：
 *   明细保留 90 天（上传合并时清理，对齐 Crashlytics 惯例），计数永久。
 * 本地封顶：明细 ≤30 条、文件 ≤256KB，满了删最旧。
 * 隐私：exception message 先截断 ≤200 字并复用 SemanticJudge.minimize 打码
 * 再入记录（防任务标题等用户内容混入）；面包屑只记事件名，零用户内容。
 *
 * 上传落点：user_state.reschedule_request 列的 crash_reports 键——该列是
 * v1.26.0 已撤除接线的空闲 JSON 列（安卓/Mac 两端都零引用），且已移出
 * USER_STATE_COLS 拉取清单，App 对崩溃数据只写不读。上传只在有新数据时
 * 做一次「读该列→合并→字段级 PATCH 该列」；失败留本地等下一个同步周期，
 * 不单独重试（天然退避）。本类所有方法绝不抛异常——报错通道不能成为
 * 新的错误源（与 ErrorReporter 同一纪律）。
 */
public final class CrashReporter {
    static final int FP_V = 1;
    static final String COLUMN = "reschedule_request";
    static final String NODE = "crash_reports";

    private static final String FILE = "crash_reports.json";
    private static final int MAX_DETAILS = 30;
    private static final int MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_TRACE_CHARS = 4000;
    private static final int MAX_MSG_CHARS = 200;
    private static final int MAX_CRUMBS = 16;
    private static final long DETAIL_KEEP_MS = 90L * 24 * 3600 * 1000;
    private static final String APP_PKG = "com.ti.tasktimer";

    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> CRUMBS = new ArrayDeque<>();
    private static boolean installed;

    private CrashReporter() { }

    // ---------------- 面包屑（极简：时刻＋事件名，零用户内容） ----------------

    public static void crumb(String event) {
        try {
            if (event == null || event.isEmpty()) return;
            String s = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                    + " " + event;
            synchronized (CRUMBS) {
                CRUMBS.addLast(s);
                while (CRUMBS.size() > MAX_CRUMBS) CRUMBS.removeFirst();
            }
        } catch (Throwable ignored) { }
    }

    private static JSONArray crumbsJson() {
        JSONArray a = new JSONArray();
        synchronized (CRUMBS) {
            for (String s : CRUMBS) a.put(s);
        }
        return a;
    }

    // ---------------- fatal：未捕获异常 ----------------

    /** KnowDoApp.onCreate 调一次：保存原 handler 并链回（系统崩溃行为不变）。 */
    public static void install(final Context app) {
        try {
            if (installed) return;
            installed = true;
            final Thread.UncaughtExceptionHandler prev =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                try { recordFatal(app, t, e); } catch (Throwable ignored) { }
                if (prev != null) prev.uncaughtException(t, e);
            });
        } catch (Throwable ignored) { }
    }

    private static void recordFatal(Context c, Thread t, Throwable e) {
        try {
            if (c == null || e == null) return;
            if (!ScreenGuardPrefs.diagMode(c)) return;
            JSONObject detail = baseDetail(c, "fatal");
            detail.put("exc", e.getClass().getName());
            detail.put("msg", SemanticJudge.minimize(safeMsg(e), MAX_MSG_CHARS));
            detail.put("trace", appTrace(e));
            detail.put("thread", t == null ? "" : t.getName());
            record(c, fingerprint(e), detail);
        } catch (Throwable ignored) { }
    }

    // ---------------- non-fatal：ErrorReporter 主动上报的错误 ----------------

    /** ErrorReporter.report 在 diag 已开、弹窗照常之外调用；无堆栈，摘要即现场。 */
    public static void recordReported(Context c, String source, int httpStatus, String detailText) {
        try {
            if (c == null) return;
            JSONObject detail = baseDetail(c, "non-fatal");
            String src = source == null || source.isEmpty() ? "错误" : source;
            String sum = detailText == null ? "" : detailText;
            detail.put("exc", src);
            detail.put("msg", SemanticJudge.minimize(sum, MAX_MSG_CHARS));
            detail.put("trace", "");
            detail.put("thread", Thread.currentThread().getName());
            if (httpStatus > 0) detail.put("http", httpStatus);
            // 指纹：来源＋摘要首段（summarize 的摘要以异常类名打头，取到第一个
            // 冒号/空格为止），同类错误在同一来源下归一
            String head = sum;
            int cut = head.length();
            for (int i = 0; i < head.length(); i++) {
                char ch = head.charAt(i);
                if (ch == '：' || ch == ':' || ch == ' ') { cut = i; break; }
            }
            head = head.substring(0, cut);
            record(c, "nf|" + src + "|" + head, detail);
        } catch (Throwable ignored) { }
    }

    // ---------------- 记录与去重 ----------------

    private static JSONObject baseDetail(Context c, String kind) {
        JSONObject d = new JSONObject();
        try {
            d.put("id", UUID.randomUUID().toString());
            d.put("kind", kind);
            d.put("fp_v", FP_V);
            d.put("ts", System.currentTimeMillis());
            d.put("fg", KnowDoApp.ownActivityVisible());
            d.put("model", Build.MODEL == null ? "" : Build.MODEL);
            d.put("android", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE);
            d.put("api", Build.VERSION.SDK_INT);
            String vn = "", vc = "";
            try {
                PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
                vn = pi.versionName == null ? "" : pi.versionName;
                vc = String.valueOf(pi.versionCode);
            } catch (Throwable ignored) { }
            d.put("vn", vn);
            d.put("vc", vc);
            String page = "";
            try {
                android.app.Activity a = KnowDoApp.currentActivity();
                if (a != null) page = a.getClass().getSimpleName();
            } catch (Throwable ignored) { }
            d.put("page", page);
            d.put("crumbs", crumbsJson());
            d.put("up", false);
        } catch (Throwable ignored) { }
        return d;
    }

    /** 当天同指纹只存一份明细，再犯只累加计数与最后时间；同步写文件。 */
    private static void record(Context c, String fp, JSONObject detail) {
        synchronized (LOCK) {
            try {
                JSONObject store = loadStore(c);
                JSONObject details = store.optJSONObject("details");
                if (details == null) { details = new JSONObject(); store.put("details", details); }
                JSONObject daily = store.optJSONObject("daily");
                if (daily == null) { daily = new JSONObject(); store.put("daily", daily); }
                String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                JSONObject dayMap = daily.optJSONObject(day);
                if (dayMap == null) { dayMap = new JSONObject(); daily.put(day, dayMap); }
                long now = System.currentTimeMillis();
                JSONObject hit = dayMap.optJSONObject(fp);
                if (hit != null) {
                    hit.put("count", hit.optInt("count", 1) + 1);
                    hit.put("last", now);
                } else {
                    detail.put("fp", fp);
                    details.put(detail.getString("id"), detail);
                    JSONObject e = new JSONObject();
                    e.put("count", 1);
                    e.put("first", now);
                    e.put("last", now);
                    e.put("uuid", detail.getString("id"));
                    e.put("synced", 0);
                    dayMap.put(fp, e);
                }
                pruneDetails(details);
                saveStore(c, store);
            } catch (Throwable ignored) { }
        }
    }

    /** 明细封顶：先删已上传的最旧，再删最旧，直到 ≤MAX_DETAILS 且文件不超限。 */
    private static void pruneDetails(JSONObject details) {
        try {
            while (details.length() > MAX_DETAILS) {
                String victim = oldestDetail(details, true);
                if (victim == null) victim = oldestDetail(details, false);
                if (victim == null) break;
                details.remove(victim);
            }
        } catch (Throwable ignored) { }
    }

    private static String oldestDetail(JSONObject details, boolean uploadedOnly) {
        String best = null;
        long bestTs = Long.MAX_VALUE;
        Iterator<String> it = details.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject d = details.optJSONObject(k);
            if (d == null) continue;
            if (uploadedOnly && !d.optBoolean("up", false)) continue;
            long ts = d.optLong("ts", 0);
            if (ts < bestTs) { bestTs = ts; best = k; }
        }
        return best;
    }

    // ---------------- 指纹与堆栈 ----------------

    /** 异常类名＋顶部 App 帧「类名.方法名」（剔行号）；无 App 帧时退到顶部 2 帧。 */
    static String fingerprint(Throwable e) {
        StringBuilder sb = new StringBuilder(e.getClass().getName());
        int n = 0;
        StackTraceElement[] st = e.getStackTrace();
        if (st != null) {
            for (StackTraceElement f : st) {
                if (f.getClassName().startsWith(APP_PKG)) {
                    sb.append('|').append(f.getClassName()).append('.').append(f.getMethodName());
                    if (++n >= 4) break;
                }
            }
            if (n == 0) {
                for (StackTraceElement f : st) {
                    sb.append('|').append(f.getClassName()).append('.').append(f.getMethodName());
                    if (++n >= 2) break;
                }
            }
        }
        String s = sb.toString();
        return s.length() > 240 ? s.substring(0, 240) : s;
    }

    /** 完整堆栈只留 App 帧与异常头（含 cause 链），截断＋打码后上报。 */
    private static String appTrace(Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            StringBuilder out = new StringBuilder();
            for (String line : sw.toString().split("\n")) {
                String t = line.trim();
                if (t.startsWith("Caused by:") || t.contains(APP_PKG)
                        || out.length() == 0) {
                    out.append(line).append('\n');
                }
            }
            return SemanticJudge.minimize(out.toString(), MAX_TRACE_CHARS);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeMsg(Throwable e) {
        try {
            String m = e.getMessage();
            return m == null ? "" : m;
        } catch (Throwable ignored) {
            return "";
        }
    }

    // ---------------- 本地存储（同步写，handler 路径也不丢） ----------------

    private static File storeFile(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    private static JSONObject loadStore(Context c) {
        try {
            File f = storeFile(c);
            if (f.exists()) {
                FileInputStream in = new FileInputStream(f);
                byte[] buf = new byte[(int) Math.min(f.length(), MAX_FILE_BYTES)];
                int off = 0, r;
                while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) off += r;
                in.close();
                if (off > 0) return new JSONObject(new String(buf, 0, off, "UTF-8"));
            }
        } catch (Throwable ignored) { }
        return new JSONObject();
    }

    private static void saveStore(Context c, JSONObject store) {
        try {
            String s = store.toString();
            if (s.length() > MAX_FILE_BYTES) {
                // 兜底：明细再砍到只剩最新 10 条（计数不动）
                JSONObject details = store.optJSONObject("details");
                if (details != null) {
                    while (details.length() > 10 && s.length() > MAX_FILE_BYTES) {
                        String v = oldestDetail(details, false);
                        if (v == null) break;
                        details.remove(v);
                        s = store.toString();
                    }
                }
            }
            FileOutputStream out = new FileOutputStream(storeFile(c));
            out.write(s.getBytes("UTF-8"));
            out.flush();
            try { out.getFD().sync(); } catch (Throwable ignored) { }
            out.close();
        } catch (Throwable ignored) { }
    }

    // ---------------- 上传（随 SbSync 节奏，失败等下一周期） ----------------

    /**
     * SbSync.doSync 在 user_state 同步成功后顺带调：本地有未上传的新数据时，
     * 读云端该列→合并→字段级 PATCH 该列。无新数据时零请求（不给同步加流量）。
     */
    public static void flushUpload(Context c, String uid) {
        try {
            if (c == null || uid == null || uid.isEmpty()) return;
            if (!ScreenGuardPrefs.diagMode(c)) return;
            JSONObject store;
            synchronized (LOCK) { store = loadStore(c); }
            if (!isDirty(store)) return;
            JSONArray rows = Supa.restGet(c,
                    "user_state?user_id=eq." + uid + "&select=" + COLUMN);
            JSONObject row = rows.length() > 0 ? rows.getJSONObject(0) : new JSONObject();
            JSONObject colVal = row.isNull(COLUMN) ? new JSONObject() : row.optJSONObject(COLUMN);
            if (colVal == null) colVal = new JSONObject();
            JSONObject serverNode = colVal.optJSONObject(NODE);
            JSONObject merged = mergeNodes(serverNode, store);
            colVal.put(NODE, merged);
            JSONObject body = new JSONObject();
            body.put(COLUMN, colVal);
            Supa.restPatch(c, "user_state?user_id=eq." + uid, body);
            // 成功：本地明细标已上传、日计数标已同步量，下轮不再重发（uuid 幂等兜底）
            synchronized (LOCK) {
                JSONObject fresh = loadStore(c);
                markUploaded(fresh, merged);
                saveStore(c, fresh);
            }
        } catch (Throwable ignored) {
            // 失败留本地，下一个同步周期再试——不单独重试、不缩短周期
        }
    }

    private static boolean isDirty(JSONObject store) {
        try {
            JSONObject details = store.optJSONObject("details");
            if (details != null) {
                Iterator<String> it = details.keys();
                while (it.hasNext()) {
                    JSONObject d = details.optJSONObject(it.next());
                    if (d != null && !d.optBoolean("up", false)) return true;
                }
            }
            JSONObject daily = store.optJSONObject("daily");
            if (daily != null) {
                Iterator<String> days = daily.keys();
                while (days.hasNext()) {
                    JSONObject dayMap = daily.optJSONObject(days.next());
                    if (dayMap == null) continue;
                    Iterator<String> fps = dayMap.keys();
                    while (fps.hasNext()) {
                        JSONObject e = dayMap.optJSONObject(fps.next());
                        if (e != null && e.optInt("count", 0) != e.optInt("synced", 0)) return true;
                    }
                }
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /** 合并：明细按 uuid 并集（90 天前的明细在此清理）；计数取 max、首末取极值。 */
    private static JSONObject mergeNodes(JSONObject serverNode, JSONObject localStore) {
        JSONObject out = new JSONObject();
        try {
            out.put("v", 1);
            long cutoff = System.currentTimeMillis() - DETAIL_KEEP_MS;
            JSONObject details = new JSONObject();
            if (serverNode != null) {
                JSONObject sd = serverNode.optJSONObject("details");
                if (sd != null) {
                    Iterator<String> it = sd.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        JSONObject d = sd.optJSONObject(k);
                        if (d != null && d.optLong("ts", 0) >= cutoff) details.put(k, d);
                    }
                }
            }
            JSONObject ld = localStore.optJSONObject("details");
            if (ld != null) {
                Iterator<String> it = ld.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if (!details.has(k)) {
                        JSONObject d = ld.optJSONObject(k);
                        if (d != null && d.optLong("ts", 0) >= cutoff) details.put(k, d);
                    }
                }
            }
            pruneDetails(details);
            out.put("details", details);
            JSONObject daily = new JSONObject();
            mergeDailyInto(daily, serverNode == null ? null : serverNode.optJSONObject("daily"));
            mergeDailyInto(daily, localStore.optJSONObject("daily"));
            out.put("daily", daily);
        } catch (Throwable ignored) { }
        return out;
    }

    private static void mergeDailyInto(JSONObject target, JSONObject src) {
        try {
            if (src == null) return;
            Iterator<String> days = src.keys();
            while (days.hasNext()) {
                String day = days.next();
                JSONObject sMap = src.optJSONObject(day);
                if (sMap == null) continue;
                JSONObject tMap = target.optJSONObject(day);
                if (tMap == null) { tMap = new JSONObject(); target.put(day, tMap); }
                Iterator<String> fps = sMap.keys();
                while (fps.hasNext()) {
                    String fp = fps.next();
                    JSONObject se = sMap.optJSONObject(fp);
                    if (se == null) continue;
                    JSONObject te = tMap.optJSONObject(fp);
                    if (te == null) {
                        JSONObject cp = new JSONObject(se.toString());
                        cp.remove("synced");
                        tMap.put(fp, cp);
                    } else {
                        te.put("count", Math.max(te.optInt("count", 0), se.optInt("count", 0)));
                        te.put("first", Math.min(te.optLong("first", Long.MAX_VALUE),
                                se.optLong("first", Long.MAX_VALUE)));
                        te.put("last", Math.max(te.optLong("last", 0), se.optLong("last", 0)));
                    }
                }
            }
        } catch (Throwable ignored) { }
    }

    private static void markUploaded(JSONObject store, JSONObject merged) {
        try {
            JSONObject details = store.optJSONObject("details");
            if (details != null) {
                Iterator<String> it = details.keys();
                while (it.hasNext()) {
                    JSONObject d = details.optJSONObject(it.next());
                    if (d != null) d.put("up", true);
                }
            }
            JSONObject daily = store.optJSONObject("daily");
            JSONObject mDaily = merged.optJSONObject("daily");
            if (daily != null && mDaily != null) {
                Iterator<String> days = daily.keys();
                while (days.hasNext()) {
                    String day = days.next();
                    JSONObject dayMap = daily.optJSONObject(day);
                    JSONObject mMap = mDaily.optJSONObject(day);
                    if (dayMap == null || mMap == null) continue;
                    Iterator<String> fps = dayMap.keys();
                    while (fps.hasNext()) {
                        String fp = fps.next();
                        JSONObject e = dayMap.optJSONObject(fp);
                        JSONObject me = mMap.optJSONObject(fp);
                        if (e != null && me != null) e.put("synced", me.optInt("count", 0));
                    }
                }
            }
        } catch (Throwable ignored) { }
    }
}
