package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 健康数据层（v2.0.0）：睡眠设置、今晚目标、给今晚的话、每日健康汇总的
 * 本地存储与同步。本地存应用私有文件 health_data.json，同步走 Supabase
 * user_state 的 health_data 列（jsonb 对象）——SbSync 探列纪律：列不存在
 * 则纯本地、不碰云端、绝不崩溃（落点理由见发版报告：habit_data 的 merge
 * 只并已知键，新键会被合并丢弃且该文件是红线，故不用 habit_data 内新键）。
 *
 * 结构（云端与本地同一份 JSON）：
 * { "settings": {"bed":1380,"wake":360,"nap":60},
 *   "tonight":  {"<晚间日期>": 上床分钟},
 *   "words":    {"<session 日>": "给今晚的话"},
 *   "days":     {"<逻辑日>": {startMs,wakeMs,totalMin,deepMin,napMin,met,
 *                lateMin,rhr,steps,kcal,hrv,recovery,recMissing,recBase,manual}} }
 * days 以逻辑日为键幂等：重复读取同一晚只会重算出同一份汇总，不重复记账。
 * 原始分期明细只存本机 health_raw.json（永不进上面这份同步文档、永不上传）。
 *
 * 合并纪律（与 WeeklyData 同套）：settings 整份本机优先；tonight/words/days
 * 逐键并集、同键本机优先。改睡眠设置只影响此后新算的汇总——已存日期的
 * met 是当日算定的值，重读/合并永不回头重算（不翻旧账）。
 */
public final class HealthStore {
    private static final String FILE = "health_data.json";
    private static final String RAW_FILE = "health_raw.json";
    private static final String PREF = "health_store";

    public static final int DEFAULT_BED_MIN = 23 * 60;   // 23:00（F2 定案）
    public static final int DEFAULT_WAKE_MIN = 6 * 60;   // 06:00
    public static final int DEFAULT_NAP_MIN = 60;        // 午睡目标 60 分钟（用户未接受改短）

    public static final String CONN_UNKNOWN = "unknown";
    public static final String CONN_OK = "ok";
    public static final String CONN_NO_PERM = "no_perm";
    public static final String CONN_UNAVAILABLE = "unavailable";

    private HealthStore() {}

    // ---------------- 本地读写（仿 WeeklyData） ----------------
    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }
    private static File rawFile(Context c) { return new File(c.getFilesDir(), RAW_FILE); }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean hasLocal(Context c) { return prefs(c).getBoolean("has_local", false); }

    public static synchronized JSONObject load(Context c) {
        try {
            File f = file(c);
            if (f.exists()) {
                String s = TaskStore.readAll(new FileInputStream(f));
                return new JSONObject(s);
            }
        } catch (Exception ignored) { }
        return new JSONObject();
    }

    /** 保存（写盘＋触发一次后台同步；未登录/列未就绪时同步层自己短路）。 */
    public static synchronized void save(Context c, JSONObject root) {
        writeFile(c, root);
        requestSync(c);
    }

    public static synchronized void saveQuiet(Context c, JSONObject root) {
        writeFile(c, root);
    }

    /** 采用云端整份（本机从未存过时）；不触发同步。 */
    public static synchronized void adopt(Context c, JSONObject remote) {
        if (remote == null) return;
        try {
            writeFile(c, new JSONObject(remote.toString()));
        } catch (Exception ignored) { }
    }

    private static void writeFile(Context c, JSONObject root) {
        try {
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
            prefs(c).edit().putBoolean("has_local", true).apply();
        } catch (Exception ignored) { }
    }

    private static void requestSync(final Context c) {
        if (!Supa.isLoggedIn(c)) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try { SbSync.syncNow(app, new TaskStore(app)); } catch (Exception ignored) { }
        }).start();
    }

    // ---------------- 连接状态（prefs 缓存，供 UI 同步读；refresh 时更新） ----------------

    public static String connState(Context c) {
        return prefs(c).getString("conn_state", CONN_UNKNOWN);
    }

    private static void setConnState(Context c, String s) {
        prefs(c).edit().putString("conn_state", s)
                .putLong("conn_checked_at", System.currentTimeMillis()).apply();
    }

    public static long lastRefreshMs(Context c) {
        return prefs(c).getLong("last_refresh_at", 0);
    }

    // ---------------- 诊断留痕（v2.0.1）：只存本机 prefs，永不上传 ----------------
    // v2.0.0 的授权故障被 catch 吞掉、无从查起，本版起把 SDK 状态码与最近
    // 一次健康链路异常（类名＋message＋时刻）留下来，设置页健康组底部可见。

    public static void recordSdkStatus(Context c, int status) {
        prefs(c).edit().putInt("last_sdk_status", status).apply();
    }

    /** 最近一次记录的 SDK 状态码；从未记录过返回 -1。 */
    public static int lastSdkStatus(Context c) {
        return prefs(c).getInt("last_sdk_status", -1);
    }

    public static void recordHealthError(Context c, Throwable t) {
        if (t == null) return;
        String msg = t.getMessage();
        String s = t.getClass().getSimpleName() + (msg == null || msg.isEmpty() ? "" : ": " + msg);
        prefs(c).edit().putString("last_health_error", s)
                .putLong("last_health_error_at", System.currentTimeMillis()).apply();
    }

    public static String lastHealthError(Context c) {
        return prefs(c).getString("last_health_error", "");
    }

    public static long lastHealthErrorAt(Context c) {
        return prefs(c).getLong("last_health_error_at", 0);
    }

    // ---------------- F2 睡眠目标设置 ----------------

    private static JSONObject settingsObj(JSONObject root, boolean create) {
        JSONObject s = root.optJSONObject("settings");
        if (s == null && create) {
            s = new JSONObject();
            try { root.put("settings", s); } catch (Exception ignored) { }
        }
        return s;
    }

    public static int bedMin(JSONObject root) {
        JSONObject s = settingsObj(root, false);
        int v = s == null ? -1 : s.optInt("bed", -1);
        return v >= 0 && v < 1440 ? v : DEFAULT_BED_MIN;
    }

    public static int wakeMin(JSONObject root) {
        JSONObject s = settingsObj(root, false);
        int v = s == null ? -1 : s.optInt("wake", -1);
        return v >= 0 && v < 1440 ? v : DEFAULT_WAKE_MIN;
    }

    public static int napGoalMin(JSONObject root) {
        JSONObject s = settingsObj(root, false);
        int v = s == null ? -1 : s.optInt("nap", -1);
        return v > 0 && v <= 720 ? v : DEFAULT_NAP_MIN;
    }

    public static void setSettings(Context c, int bed, int wake, int nap) {
        JSONObject root = load(c);
        try {
            JSONObject s = settingsObj(root, true);
            s.put("bed", ((bed % 1440) + 1440) % 1440);
            s.put("wake", ((wake % 1440) + 1440) % 1440);
            s.put("nap", Math.max(1, Math.min(720, nap)));
        } catch (Exception ignored) { }
        save(c, root);
    }

    // ---------------- 今晚目标（只改今晚，不动设置） ----------------

    private static JSONObject tonightObj(JSONObject root, boolean create) {
        JSONObject t = root.optJSONObject("tonight");
        if (t == null && create) {
            t = new JSONObject();
            try { root.put("tonight", t); } catch (Exception ignored) { }
        }
        return t;
    }

    /** 今晚（eveningDate 这个晚间）的上床目标：有今晚覆盖用覆盖，否则用设置。 */
    public static int tonightBedMin(JSONObject root, String eveningDate) {
        JSONObject t = tonightObj(root, false);
        if (t != null) {
            int v = t.optInt(eveningDate, -1);
            if (v >= 0 && v < 1440) return v;
        }
        return bedMin(root);
    }

    public static void setTonightBedMin(Context c, String eveningDate, int min) {
        JSONObject root = load(c);
        try {
            tonightObj(root, true).put(eveningDate, ((min % 1440) + 1440) % 1440);
        } catch (Exception ignored) { }
        save(c, root);
    }

    // ---------------- 给今晚的话（晨间意义区，本地先行＋同步） ----------------

    private static JSONObject wordsObj(JSONObject root, boolean create) {
        JSONObject w = root.optJSONObject("words");
        if (w == null && create) {
            w = new JSONObject();
            try { root.put("words", w); } catch (Exception ignored) { }
        }
        return w;
    }

    public static String words(JSONObject root, String date) {
        JSONObject w = wordsObj(root, false);
        return w == null ? "" : w.optString(date, "").trim();
    }

    /** 写「给今晚的话」：逐字输入走静默存（不逐键触发同步），同步由 kickSync 收口。 */
    public static void setWords(Context c, String date, String text) {
        JSONObject root = load(c);
        try {
            JSONObject w = wordsObj(root, true);
            String t = text == null ? "" : text.trim();
            if (t.isEmpty()) w.remove(date); else w.put(date, t);
        } catch (Exception ignored) { }
        saveQuiet(c, root);
    }

    /** 把已静默落盘的本地数据推一次同步（页面收尾时调，收口逐字输入的补传）。 */
    public static void kickSync(Context c) {
        if (hasLocal(c)) save(c, load(c));
    }

    // ---------------- 每日汇总 ----------------

    private static JSONObject daysObj(JSONObject root, boolean create) {
        JSONObject d = root.optJSONObject("days");
        if (d == null && create) {
            d = new JSONObject();
            try { root.put("days", d); } catch (Exception ignored) { }
        }
        return d;
    }

    public static JSONObject daySummary(JSONObject root, String date) {
        JSONObject d = daysObj(root, false);
        return d == null ? null : d.optJSONObject(date);
    }

    private static HealthMath.Summary jsonToSummary(JSONObject d) {
        HealthMath.Summary s = new HealthMath.Summary();
        if (d == null) return s;
        s.sleepStartMs = d.optLong("startMs", 0);
        s.wakeMs = d.optLong("wakeMs", 0);
        s.hasSleep = s.sleepStartMs > 0 && s.wakeMs > s.sleepStartMs;
        s.totalMin = d.optInt("totalMin", 0);
        s.deepMin = d.optInt("deepMin", -1);
        s.napMin = d.optInt("napMin", 0);
        s.met = d.optBoolean("met", false);
        s.lateMin = d.optInt("lateMin", 0);
        s.rhr = d.optInt("rhr", -1);
        s.steps = d.optLong("steps", -1);
        s.kcal = d.optDouble("kcal", -1);
        return s;
    }

    /** 把一份算好的汇总写进 days[date]；保留既有 hrv（手填）与 manual 标记由调用方决定。 */
    private static void putSummary(JSONObject root, String date, HealthMath.Summary s,
                                   boolean manual) {
        try {
            JSONObject days = daysObj(root, true);
            JSONObject d = days.optJSONObject(date);
            Integer hrv = null;
            if (d != null && d.has("hrv") && !d.isNull("hrv")) hrv = d.optInt("hrv");
            JSONObject nd = new JSONObject();
            if (s.hasSleep) {
                nd.put("startMs", s.sleepStartMs);
                nd.put("wakeMs", s.wakeMs);
                nd.put("totalMin", s.totalMin);
                nd.put("deepMin", s.deepMin);
                nd.put("napMin", s.napMin);
                nd.put("met", s.met);
                nd.put("lateMin", s.lateMin);
            }
            if (s.rhr >= 0) nd.put("rhr", s.rhr);
            if (s.steps >= 0) nd.put("steps", s.steps);
            if (s.kcal >= 0) nd.put("kcal", s.kcal);
            if (manual) nd.put("manual", true);
            if (hrv != null) nd.put("hrv", hrv.intValue());
            days.put(date, nd);
        } catch (Exception ignored) { }
    }

    /** HRV 手填（晨间页）：填了重算恢复分；清空则删键、恢复分按缺项折算。
     *  逐字输入走静默存，同步由 kickSync 收口（同 setWords）。 */
    public static void setHrv(Context c, String date, Integer value) {
        JSONObject root = load(c);
        try {
            JSONObject days = daysObj(root, true);
            JSONObject d = days.optJSONObject(date);
            if (d == null) { d = new JSONObject(); days.put(date, d); }
            if (value == null || value <= 0) d.remove("hrv"); else d.put("hrv", value.intValue());
        } catch (Exception ignored) { }
        recomputeRecovery(root, date);
        saveQuiet(c, root);
    }

    public static Integer hrv(JSONObject root, String date) {
        JSONObject d = daySummary(root, date);
        if (d == null || !d.has("hrv") || d.isNull("hrv")) return null;
        int v = d.optInt("hrv", 0);
        return v > 0 ? v : null;
    }

    /**
     * 无表/未授权兜底手填（F4）：晨间页填入睡/起床墙钟分钟，照常记账与算分。
     * deepMin 记 -1（未知，恢复分深睡项缺项）；已有的 hrv/rhr/steps 保留。
     */
    public static void setManualSleep(Context c, String date, int startMin, int wakeMinWall) {
        JSONObject root = load(c);
        int total = (wakeMinWall - startMin + 1440) % 1440;
        if (total <= 0) return;
        String startDay = startMin >= 720 ? DayUtil.addDays(date, -1) : date;
        long startMs = DayUtil.dayStartMs(startDay) + startMin * 60000L;
        HealthMath.Summary s = new HealthMath.Summary();
        JSONObject old = daySummary(root, date);
        if (old != null) {
            HealthMath.Summary o = jsonToSummary(old);
            s.rhr = o.rhr; s.steps = o.steps; s.kcal = o.kcal; s.napMin = o.napMin;
        }
        s.hasSleep = true;
        s.sleepStartMs = startMs;
        s.wakeMs = startMs + total * 60000L;
        s.totalMin = total;
        s.deepMin = -1;
        s.lateMin = HealthMath.lateMinutes(startMin, bedMin(root));
        int win = HealthMath.windowMin(bedMin(root), wakeMin(root));
        s.met = s.lateMin == 0 && total * 10 >= win * 9;
        putSummary(root, date, s, true);
        recomputeRecovery(root, date);
        save(c, root);
    }

    // ---------------- 恢复分（结果落进当日汇总，页面只读） ----------------

    /** 按 root 里已存的前 7 天数据算基线，重算 date 的恢复分并写回 day。无睡眠则清掉旧分。 */
    public static void recomputeRecovery(JSONObject root, String date) {
        try {
            JSONObject days = daysObj(root, false);
            if (days == null) return;
            JSONObject day = days.optJSONObject(date);
            if (day == null) return;
            HealthMath.Summary s = jsonToSummary(day);
            if (!s.hasSleep) {
                day.remove("recovery");
                day.remove("recMissing");
                day.remove("recBase");
                return;
            }
            List<Integer> rhrs = new ArrayList<>();
            List<Integer> hrvs = new ArrayList<>();
            List<Long> stepsList = new ArrayList<>();
            int baseDays = 0;
            for (int i = 1; i <= 7; i++) {
                JSONObject pd = days.optJSONObject(DayUtil.addDays(date, -i));
                if (pd == null) continue;
                boolean any = pd.optLong("startMs", 0) > 0
                        || pd.optLong("steps", -1) >= 0 || pd.optInt("rhr", -1) > 0;
                if (any) baseDays++;
                if (pd.optInt("rhr", -1) > 0) rhrs.add(pd.optInt("rhr"));
                if (pd.has("hrv") && !pd.isNull("hrv") && pd.optInt("hrv") > 0) {
                    hrvs.add(pd.optInt("hrv"));
                }
                if (pd.optLong("steps", -1) >= 0) stepsList.add(pd.optLong("steps"));
            }
            Double rhrBase = rhrs.isEmpty() ? null : mean(rhrs);
            Double hrvBase = hrvs.isEmpty() ? null : mean(hrvs);
            Double stepsMedian = stepsList.isEmpty() ? null : median(stepsList);
            JSONObject yday = days.optJSONObject(DayUtil.addDays(date, -1));
            long stepsYesterday = yday == null ? -1 : yday.optLong("steps", -1);
            Integer hrvToday = null;
            if (day.has("hrv") && !day.isNull("hrv") && day.optInt("hrv") > 0) {
                hrvToday = day.optInt("hrv");
            }
            int win = HealthMath.windowMin(bedMin(root), wakeMin(root));
            HealthMath.Recovery r = HealthMath.recovery(s, win, rhrBase, hrvToday, hrvBase,
                    stepsYesterday, stepsMedian, baseDays);
            if (r == null) {
                day.remove("recovery");
                return;
            }
            day.put("recovery", r.score);
            JSONArray miss = new JSONArray();
            for (String m : r.missing) miss.put(m);
            day.put("recMissing", miss);
            day.put("recBase", baseDays);
            JSONObject parts = new JSONObject();
            parts.put("dur", round1(r.durPts));
            parts.put("timing", round1(r.timingPts));
            parts.put("deep", round1(r.deepPts));
            parts.put("rhr", round1(r.rhrPts));
            parts.put("hrv", round1(r.hrvPts));
            parts.put("act", round1(r.actPts));
            day.put("recParts", parts);
        } catch (Exception ignored) { }
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }

    private static Double mean(List<Integer> xs) {
        if (xs.isEmpty()) return null;
        long sum = 0;
        for (int x : xs) sum += x;
        return (double) sum / xs.size();
    }

    private static Double median(List<Long> xs) {
        if (xs.isEmpty()) return null;
        List<Long> s = new ArrayList<>(xs);
        Collections.sort(s);
        int n = s.size();
        return n % 2 == 1 ? (double) s.get(n / 2)
                : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }

    // ---------------- F7 每周汇总（由每日汇总现算，不另存） ----------------

    /** 本周（monday 起 7 天）：[达标天数, 平均入睡分钟, 平均时长分钟, 有睡眠天数]。 */
    public static int[] weeklySummary(JSONObject root, String monday) {
        int[] starts = new int[7];
        int[] totals = new int[7];
        boolean[] mets = new boolean[7];
        for (int i = 0; i < 7; i++) {
            JSONObject d = daySummary(root, DayUtil.addDays(monday, i));
            if (d != null && d.optLong("startMs", 0) > 0) {
                starts[i] = HealthMath.minutesOfDay(d.optLong("startMs"));
                totals[i] = d.optInt("totalMin", 0);
                mets[i] = d.optBoolean("met", false);
            } else {
                starts[i] = -1;
            }
        }
        return HealthMath.weekly(starts, totals, mets);
    }

    // ---------------- F1 读取（后台线程；结果经汇总落盘后回调） ----------------

    /**
     * 拉最近 11 天原始数据并重算各逻辑日汇总。未授权/不可用时只更新连接
     * 状态、不动已有数据。回调在后台线程执行，UI 侧自行切主线程。
     * 幂等：同一份原始数据重算结果一致；手填日期在有表数据时以表数据为准，
     * 无表数据的手填汇总保留（manual 标记不清）。
     */
    public static void refresh(final Context c, final Runnable done) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                recordSdkStatus(app, HealthConnectBridge.sdkStatus(app));
                if (!HealthConnectBridge.isAvailable(app)) {
                    setConnState(app, CONN_UNAVAILABLE);
                    return;
                }
                if (!HealthConnectBridge.hasAllPermissions(app)) {
                    setConnState(app, CONN_NO_PERM);
                    return;
                }
                setConnState(app, CONN_OK);
                long endMs = System.currentTimeMillis();
                long startMs = endMs - 11L * 86400000L;
                String raw = HealthConnectBridge.readJson(app, startMs, endMs);
                writeRaw(app, raw);
                applyRaw(app, new JSONObject(raw));
                prefs(app).edit().putLong("last_refresh_at", System.currentTimeMillis()).apply();
            } catch (Exception e) {
                // 读不到不崩、不清数据：已有汇总照常显示，下次打开再试；
                // 但异常必须留痕（v2.0.1），不许再静默吞掉
                recordHealthError(app, e);
            } finally {
                if (done != null) done.run();
            }
        }).start();
    }

    private static void writeRaw(Context c, String raw) {
        try {
            FileOutputStream out = new FileOutputStream(rawFile(c));
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) { }
    }

    /** 本机缓存的原始数据（只读回放/排查用，永不上传）。 */
    public static String readRaw(Context c) {
        try {
            File f = rawFile(c);
            if (f.exists()) return TaskStore.readAll(new FileInputStream(f));
        } catch (Exception ignored) { }
        return "";
    }

    private static void applyRaw(Context c, JSONObject raw) {
        try {
            List<HealthMath.Session> sessions = new ArrayList<>();
            JSONArray ss = raw.optJSONArray("sessions");
            if (ss != null) {
                for (int i = 0; i < ss.length(); i++) {
                    JSONObject o = ss.optJSONObject(i);
                    if (o == null) continue;
                    HealthMath.Session s = new HealthMath.Session(
                            o.optLong("startMs"), o.optLong("endMs"), o.optString("source", ""));
                    JSONArray stages = o.optJSONArray("stages");
                    if (stages != null) {
                        for (int j = 0; j < stages.length(); j++) {
                            JSONObject so = stages.optJSONObject(j);
                            if (so == null) continue;
                            s.stages.add(new HealthMath.Stage(
                                    so.optLong("startMs"), so.optLong("endMs"), so.optInt("type")));
                        }
                    }
                    sessions.add(s);
                }
            }
            List<long[]> rhr = new ArrayList<>();
            JSONArray ra = raw.optJSONArray("rhr");
            if (ra != null) {
                for (int i = 0; i < ra.length(); i++) {
                    JSONObject o = ra.optJSONObject(i);
                    if (o != null) rhr.add(new long[]{o.optLong("timeMs"), o.optLong("bpm")});
                }
            }
            List<long[]> steps = new ArrayList<>();
            JSONArray sa = raw.optJSONArray("steps");
            if (sa != null) {
                for (int i = 0; i < sa.length(); i++) {
                    JSONObject o = sa.optJSONObject(i);
                    if (o != null) steps.add(new long[]{o.optLong("endMs"), o.optLong("count")});
                }
            }
            List<double[]> kcal = new ArrayList<>();
            JSONArray ka = raw.optJSONArray("kcal");
            if (ka != null) {
                for (int i = 0; i < ka.length(); i++) {
                    JSONObject o = ka.optJSONObject(i);
                    if (o != null) kcal.add(new double[]{o.optLong("endMs"), o.optDouble("kcal")});
                }
            }

            JSONObject root = load(c);
            int dayStart = HabitData.morningDayStartMinutes(HabitData.load(c));
            int bed = bedMin(root), wake = wakeMin(root);
            String logicalToday = Rollover.logicalToday(c);
            for (int back = 10; back >= 0; back--) {
                String date = DayUtil.addDays(logicalToday, -back);
                HealthMath.Summary s = HealthMath.summarize(
                        sessions, rhr, steps, kcal, date, dayStart, bed, wake);
                JSONObject old = daySummary(root, date);
                boolean oldManual = old != null && old.optBoolean("manual", false);
                if (s.hasSleep) {
                    // 表数据为准；步数/心率/热量表里没有的保留旧值
                    if (s.rhr < 0 && old != null) s.rhr = old.optInt("rhr", -1);
                    if (s.steps < 0 && old != null) s.steps = old.optLong("steps", -1);
                    if (s.kcal < 0 && old != null) s.kcal = old.optDouble("kcal", -1);
                    putSummary(root, date, s, false);
                    // 不翻旧账（F2）：已记过账的日期，达标判定（met/lateMin）冻结在
                    // 当日算定的值——之后改睡眠设置、或表数据回补重算，都不许把
                    // 历史日期的达标翻过来；原始数字（时长等）仍可随表数据更新
                    if (old != null && old.optLong("startMs", 0) > 0) {
                        JSONObject nd = daySummary(root, date);
                        if (nd != null) {
                            try {
                                nd.put("met", old.optBoolean("met", false));
                                nd.put("lateMin", old.optInt("lateMin", 0));
                            } catch (Exception ignored) { }
                        }
                    }
                } else if (!oldManual) {
                    // 没读到睡眠：只更新步数/心率/热量（有才写），不动已有睡眠账
                    if (old != null || s.rhr >= 0 || s.steps >= 0 || s.kcal >= 0) {
                        JSONObject days = daysObj(root, true);
                        JSONObject d = old == null ? new JSONObject() : old;
                        if (s.rhr >= 0) d.put("rhr", (int) s.rhr);
                        if (s.steps >= 0) d.put("steps", s.steps);
                        if (s.kcal >= 0) d.put("kcal", s.kcal);
                        days.put(date, d);
                    }
                }
                recomputeRecovery(root, date);
            }
            save(c, root);
        } catch (Exception ignored) { }
    }

    // ---------------- 合并（同步纪律核心） ----------------

    /**
     * 本机与云端合并：settings 整份本机优先（本机没有才用云端）；tonight /
     * words / days 逐键并集、同键本机优先（汇总在本机按当日设置算定，
     * 云端只是副本；手填与表测都产生在本机）。纯函数，不落盘。
     */
    public static JSONObject merge(JSONObject local, JSONObject remote) {
        if (remote == null) return local;
        if (local == null) return remote;
        try {
            JSONObject out = new JSONObject(local.toString());
            if (settingsObj(out, false) == null && settingsObj(remote, false) != null) {
                out.put("settings", settingsObj(remote, false));
            }
            mergeKeyMap(out, remote, "tonight");
            mergeKeyMap(out, remote, "words");
            mergeKeyMap(out, remote, "days");
            return out;
        } catch (Exception e) {
            return local;
        }
    }

    private static void mergeKeyMap(JSONObject out, JSONObject remote, String key) throws Exception {
        JSONObject theirs = remote.optJSONObject(key);
        if (theirs == null) return;
        JSONObject mine = out.optJSONObject(key);
        if (mine == null) {
            out.put(key, new JSONObject(theirs.toString()));
            return;
        }
        Iterator<String> ks = theirs.keys();
        while (ks.hasNext()) {
            String k = ks.next();
            if (!mine.has(k)) mine.put(k, theirs.get(k));
        }
    }
}
