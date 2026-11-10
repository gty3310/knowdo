package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每日成绩单（v1.76.0，F1/F2）：把每个逻辑日的分数定格成一行——知行指数与
 * 三项构成（习惯/任务/分神）、动机三档计数、原则做到/没做到计数——本地先行
 * 存快照缓存，再 upsert 云端 score_daily 表（unique user_id+date，幂等）。
 * 曲线页（TrendActivity）只读这份快照渲染，绝不回扫原始记录，攒再久也不卡。
 *
 * 口径全部取既有实现，本类只调用不新发明：
 * - 分数走 ZhixingCalc.computeDay（单日口径，与晨间小报同一路）；习惯分项
 *   habitRate=-1（当日无可计的习惯记录）时，computeDay 合成指数用的是 100，
 *   本表 habit 列存同一个生效值 100，保证 zhixing 与三项构成对得上。
 * - 事件计数读 ZhiPage 本地 events 缓存：motive_check 只数动机原则那条
 *   （与 MotiveGate.stats 同口径，值分 有/没考虑/没有 三档）；principle_check
 *   数全部原则的 做到了/没做到（「没考虑」按定案不入）。
 * - 事件按逻辑日归属：ts 的钟点早于晨间日界（dayStartMinutes）算前一天，
 *   与习惯 logs 的记账日界一致。
 *
 * 定格时机：MainActivity.onResume（跨日界后的首次活动）与走势页打开时各
 * 调一次 sealAsync（内部自守卫、后台线程）；把昨天及此前未定格的日期逐日
 * 补齐，最近 7 天每次重算（迟到的事件/同步数据能修正旧行，upsert 覆盖同行）。
 * 首次启用回算本地数据覆盖的全部过去日期（上限 370 天）；没有任何原始
 * 记录的日期不建行（ZhixingCalc.hasDataInWindow 与事件缓存双口径判存在）。
 * 断网/未登录时只落本地并记待传，下次 seal/pull 时补 upsert。
 */
public final class ScoreDaily {
    private static final String PREF = "score_daily";
    private static final String KEY_ROWS = "rows";
    private static final String KEY_DIRTY = "dirty";
    private static final String TABLE = "score_daily";
    private static final int MAX_BACKFILL_DAYS = 370;
    private static final int RESEAL_WINDOW_DAYS = 7;

    private static final AtomicBoolean SEALING = new AtomicBoolean(false);
    private static final AtomicBoolean PULLING = new AtomicBoolean(false);

    private ScoreDaily() {}

    /** 一行成绩单（date 为逻辑日 yyyy-MM-dd）。 */
    public static final class Row {
        public String date;
        public int zhixing, habit, task, drift;
        public int motiveYes, motiveSkip, motiveNo;
        public int prDone, prMissed;

        int[] pack() {
            return new int[]{zhixing, habit, task, drift,
                    motiveYes, motiveSkip, motiveNo, prDone, prMissed};
        }

        static Row unpack(String date, JSONArray a) {
            Row r = new Row();
            r.date = date;
            r.zhixing = a.optInt(0); r.habit = a.optInt(1);
            r.task = a.optInt(2); r.drift = a.optInt(3);
            r.motiveYes = a.optInt(4); r.motiveSkip = a.optInt(5); r.motiveNo = a.optInt(6);
            r.prDone = a.optInt(7); r.prMissed = a.optInt(8);
            return r;
        }

        boolean sameValues(Row o) {
            if (o == null) return false;
            int[] a = pack(), b = o.pack();
            for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
            return true;
        }

        JSONObject toCloudJson(String uid) {
            JSONObject o = new JSONObject();
            try {
                o.put("user_id", uid);
                o.put("date", date);
                o.put("zhixing", zhixing);
                o.put("habit", habit);
                o.put("task", task);
                o.put("drift", drift);
                o.put("motive_yes", motiveYes);
                o.put("motive_skip", motiveSkip);
                o.put("motive_no", motiveNo);
                o.put("pr_done", prDone);
                o.put("pr_missed", prMissed);
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                o.put("updated_at", f.format(new Date()));
            } catch (Exception ignored) { }
            return o;
        }
    }

    // ---------------- 本地快照缓存 ----------------
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 本地快照（日期→行），曲线页渲染只读它。 */
    public static synchronized Map<String, Row> snapshot(Context c) {
        Map<String, Row> out = new TreeMap<>();
        try {
            String s = prefs(c).getString(KEY_ROWS, "");
            if (s == null || s.isEmpty()) return out;
            JSONObject o = new JSONObject(s);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String d = it.next();
                JSONArray a = o.optJSONArray(d);
                if (a != null) out.put(d, Row.unpack(d, a));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static synchronized void saveSnapshot(Context c, Map<String, Row> rows) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Row> e : rows.entrySet()) {
                JSONArray a = new JSONArray();
                for (int v : e.getValue().pack()) a.put(v);
                o.put(e.getKey(), a);
            }
            prefs(c).edit().putString(KEY_ROWS, o.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static synchronized Set<String> dirtySet(Context c) {
        try {
            return new HashSet<>(prefs(c).getStringSet(KEY_DIRTY, new HashSet<>()));
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    private static synchronized void saveDirty(Context c, Set<String> dirty) {
        prefs(c).edit().putStringSet(KEY_DIRTY, dirty).apply();
    }

    // ---------------- 事件按逻辑日分桶 ----------------
    /** ts 所属的逻辑日：钟点早于日界算前一天（与习惯记账日界一致）。 */
    static String logicalDateOf(long tsMs, int dayStartMinutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(tsMs);
        int mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        String d = DayUtil.fmt(cal);
        if (mins < dayStartMinutes) d = DayUtil.addDays(d, -1);
        return d;
    }

    /** 一次扫完 events 缓存：日期 → {动机有， 动机没考虑， 动机没有， 原则做到， 原则没做到}。 */
    static Map<String, int[]> bucketEvents(Context c, int dayStartMinutes) {
        Map<String, int[]> out = new HashMap<>();
        try {
            JSONArray arr = ZhiPage.eventsCache(c);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null) continue;
                long ms = e.optLong("tsMs", 0);
                if (ms <= 0) continue;
                String kind = e.optString("kind", "");
                String v = e.optString("value", "");
                int idx = -1;
                if ("motive_check".equals(kind)) {
                    if (!MotiveGate.PRINCIPLE_ID.equals(e.optString("subject_id", ""))) continue;
                    idx = "有".equals(v) ? 0 : "没考虑".equals(v) ? 1 : 2;
                } else if ("principle_check".equals(kind)) {
                    if ("做到了".equals(v)) idx = 3;
                    else if ("没做到".equals(v)) idx = 4;
                    else continue; // 没考虑不入成绩单
                } else {
                    continue;
                }
                String d = logicalDateOf(ms, dayStartMinutes);
                int[] b = out.get(d);
                if (b == null) { b = new int[5]; out.put(d, b); }
                b[idx]++;
            }
        } catch (Exception ignored) { }
        return out;
    }

    // ---------------- 算一行 ----------------
    private static Row computeRow(Context c, JSONObject root, List<HabitData.Habit> habits,
                                  List<TaskItem> tasks, String date, Map<String, int[]> evByDate) {
        ZhixingCalc.DayStats s = ZhixingCalc.computeDay(c, root, habits, tasks, date);
        Row r = new Row();
        r.date = date;
        r.zhixing = s.index;
        r.habit = s.habitRate < 0 ? 100 : s.habitRate; // 与 computeDay 合成指数用的生效值一致
        r.task = s.taskRate;
        r.drift = s.driftScore;
        int[] b = evByDate.get(date);
        if (b != null) {
            r.motiveYes = b[0]; r.motiveSkip = b[1]; r.motiveNo = b[2];
            r.prDone = b[3]; r.prMissed = b[4];
        }
        return r;
    }

    /** 今日点（不落库）：走势页用实时值画今天。 */
    public static Row liveToday(Context c) {
        JSONObject root = HabitData.load(c);
        List<HabitData.Habit> habits = HabitData.habits(root);
        TaskStore store = new TaskStore(c);
        store.load();
        List<TaskItem> tasks = new ArrayList<>(store.tasks);
        String today = Rollover.logicalToday(c);
        Map<String, int[]> ev = bucketEvents(c, HabitData.morningDayStartMinutes(root));
        return computeRow(c, root, habits, tasks, today, ev);
    }

    /** 本地数据最早有记录的日期（回算起点）：习惯 logs、任务、事件三处取最早，再与上限取晚。 */
    private static String earliestDataDate(Context c, JSONObject root, List<TaskItem> tasks,
                                           Map<String, int[]> evByDate, String floor) {
        String best = null;
        try {
            JSONObject logs = root == null ? null : root.optJSONObject("logs");
            if (logs != null) {
                Iterator<String> it = logs.keys();
                while (it.hasNext()) {
                    String d = it.next();
                    if (best == null || d.compareTo(best) < 0) best = d;
                }
            }
        } catch (Exception ignored) { }
        for (TaskItem t : tasks) {
            if (t == null || t.deleted) continue;
            if (t.deadline != null && (best == null || t.deadline.compareTo(best) < 0)) best = t.deadline;
            if (t.doneDates != null) {
                for (String d : t.doneDates) {
                    if (best == null || d.compareTo(best) < 0) best = d;
                }
            }
        }
        for (String d : evByDate.keySet()) {
            if (best == null || d.compareTo(best) < 0) best = d;
        }
        if (best == null || best.compareTo(floor) < 0) best = floor;
        return best;
    }

    // ---------------- F1 定格 ----------------
    /** 跨日后首次活动与走势页打开时调用：后台定格＋补传，重复调无副作用。 */
    public static void sealAsync(Context c) {
        if (!SEALING.compareAndSet(false, true)) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                sealNow(app);
            } catch (Exception ignored) {
            } finally {
                SEALING.set(false);
            }
        }, "score-daily-seal").start();
    }

    private static void sealNow(Context app) {
        JSONObject root = HabitData.load(app);
        List<HabitData.Habit> habits = HabitData.habits(root);
        TaskStore store = new TaskStore(app);
        store.load();
        List<TaskItem> tasks = new ArrayList<>(store.tasks);
        String today = Rollover.logicalToday(app);
        String yesterday = DayUtil.addDays(today, -1);
        Map<String, int[]> evByDate = bucketEvents(app, HabitData.morningDayStartMinutes(root));

        Map<String, Row> rows = snapshot(app);
        Set<String> dirty = dirtySet(app);
        String floor = DayUtil.addDays(yesterday, -(MAX_BACKFILL_DAYS - 1));
        String start = earliestDataDate(app, root, tasks, evByDate, floor);

        boolean changed = false;
        String d = start;
        while (d.compareTo(yesterday) <= 0) {
            boolean fresh = !rows.containsKey(d);
            boolean inWindow = DayUtil.daysBetween(d, yesterday) < RESEAL_WINDOW_DAYS;
            if (fresh || inWindow) {
                boolean has = ZhixingCalc.hasDataInWindow(app, root, tasks, d, d)
                        || evByDate.containsKey(d);
                if (has) {
                    Row r = computeRow(app, root, habits, tasks, d, evByDate);
                    if (!r.sameValues(rows.get(d))) {
                        rows.put(d, r);
                        dirty.add(d);
                        changed = true;
                    }
                }
            }
            d = DayUtil.addDays(d, 1);
        }
        if (changed) saveSnapshot(app, rows);
        if (!dirty.isEmpty()) {
            saveDirty(app, dirty);
            uploadDirty(app, rows, dirty);
        }
    }

    /** 把待传日期批量 upsert 上云（幂等：同 user+date 覆盖同行）；成功才摘待传。 */
    private static void uploadDirty(Context app, Map<String, Row> rows, Set<String> dirty) {
        String uid = "";
        try { uid = Supa.userId(app); } catch (Exception ignored) { }
        if (uid == null || uid.isEmpty() || !Supa.isLoggedIn(app)) return; // 未登录：只落本地，下次再传
        List<String> batch = new ArrayList<>();
        for (String d : dirty) if (rows.containsKey(d)) batch.add(d);
        if (batch.isEmpty()) {
            dirty.clear();
            saveDirty(app, dirty);
            return;
        }
        // 一次最多 400 行（回算上限），分批 100
        for (int i = 0; i < batch.size(); i += 100) {
            List<String> part = batch.subList(i, Math.min(i + 100, batch.size()));
            try {
                JSONArray arr = new JSONArray();
                for (String d : part) arr.put(rows.get(d).toCloudJson(uid));
                Supa.restUpsert(app, TABLE, "user_id,date", arr);
                dirty.removeAll(part);
            } catch (Exception e) {
                break; // 断网/表未就绪：剩下的留待下次
            }
        }
        saveDirty(app, dirty);
    }

    // ---------------- F2 拉取合并 ----------------
    /** 走势页打开时调用：拉云端全量（≤370 行）合并进本地快照，完成回调主线程。 */
    public static void pullAsync(Context c, final Runnable done) {
        final Runnable finish = () -> {
            if (done != null) new Handler(Looper.getMainLooper()).post(done);
        };
        if (!PULLING.compareAndSet(false, true)) { finish.run(); return; }
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                pullNow(app);
            } catch (Exception ignored) {
            } finally {
                PULLING.set(false);
                finish.run();
            }
        }, "score-daily-pull").start();
    }

    private static void pullNow(Context app) {
        if (!Supa.isLoggedIn(app)) return;
        JSONArray back;
        try {
            back = Supa.restGet(app, TABLE + "?select=date,zhixing,habit,task,drift,"
                    + "motive_yes,motive_skip,motive_no,pr_done,pr_missed"
                    + "&order=date.asc&limit=400");
        } catch (Exception e) {
            return; // 拉取失败：用本地缓存照常显示
        }
        Map<String, Row> rows = snapshot(app);
        Set<String> dirty = dirtySet(app);
        Set<String> cloudDates = new HashSet<>();
        boolean changed = false;
        for (int i = 0; i < back.length(); i++) {
            JSONObject o = back.optJSONObject(i);
            if (o == null) continue;
            String d = o.optString("date", "");
            if (d.isEmpty()) continue;
            cloudDates.add(d);
            if (dirty.contains(d)) continue; // 本机定格待传中，以本机为准（F2）
            Row r = new Row();
            r.date = d;
            r.zhixing = o.optInt("zhixing"); r.habit = o.optInt("habit");
            r.task = o.optInt("task"); r.drift = o.optInt("drift");
            r.motiveYes = o.optInt("motive_yes"); r.motiveSkip = o.optInt("motive_skip");
            r.motiveNo = o.optInt("motive_no");
            r.prDone = o.optInt("pr_done"); r.prMissed = o.optInt("pr_missed");
            if (!r.sameValues(rows.get(d))) {
                rows.put(d, r);
                changed = true;
            }
        }
        // 本机已定格但云端没有、又不在待传里的（云端曾丢行等）：重新入待传自愈
        String today = Rollover.logicalToday(app);
        for (String d : rows.keySet()) {
            if (d.compareTo(today) < 0 && !cloudDates.contains(d) && !dirty.contains(d)) {
                dirty.add(d);
                changed = true;
            }
        }
        if (changed) {
            saveSnapshot(app, rows);
            saveDirty(app, dirty);
        }
        if (!dirty.isEmpty()) uploadDirty(app, rows, dirty);
    }
}
