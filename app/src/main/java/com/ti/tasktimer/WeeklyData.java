package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

/**
 * 「每周目标反思」数据层（v1.47.0）：weekly 型习惯的专属数据——周目标＋每日反省。
 * 本地存应用私有文件 weekly_data.json，并经 Supabase user_state 的 weekly_data 列
 * （jsonb 对象）同步；同步纪律与 habit_data 同一套（SbSync：列先探测再碰、本机从未
 * 存过且云端有值→整份采用、本机有值→并集合并本机优先、Realtime 推来只合并不回写）。
 * 列未就绪时 App 完全不碰该键、只用本地，绝不崩溃、绝不连累其他回写。
 *
 * 结构（云端与本地同一份 JSON）：
 * { "<habitId>": { "goals": {"<周一日期 yyyy-MM-dd>": "目标文本"},
 *                  "reflections": {"<yyyy-MM-dd>": "反省文本"} } }
 * 周以周一为始（DayUtil.mondayOf 口径，与知行页一致）；goals 的键是目标所属周的周一。
 *
 * 完成口径不在这里：今日反省非空⇔logs[今天][habitId]=1 由详情页写 HabitData.logs，
 * 与知行指数/晨间昨日习惯输入的既有口径一致，本类只管文本。
 */
public final class WeeklyData {
    private static final String FILE = "weekly_data.json";
    private static final String PREF = "weekly_store";
    /** 周门触发时刻：周日 20:00（本地墙钟，用户 2026-11-03 亲定，不用 dayStartMinutes）。 */
    public static final int GATE_HOUR = 20;

    private WeeklyData() {}

    // ---------------- 本地读写（仿 HabitData） ----------------
    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

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

    /** 保存（写盘＋标记本机有值＋触发一次后台同步；未登录/列未就绪时同步层自己短路）。 */
    public static synchronized void save(Context c, JSONObject root) {
        writeFile(c, root);
        requestSync(c);
    }

    /** 只写盘、不触发同步（逐字保存与同步流程内部落盘用，避免递归触发）。 */
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

    // ---------------- 条目读写 ----------------
    private static JSONObject entry(JSONObject root, String habitId, boolean create) {
        JSONObject e = root.optJSONObject(habitId);
        if (e == null && create) {
            e = new JSONObject();
            try {
                e.put("goals", new JSONObject());
                e.put("reflections", new JSONObject());
                root.put(habitId, e);
            } catch (Exception ignored) { }
        }
        return e;
    }

    private static JSONObject sub(JSONObject e, String key, boolean create) {
        if (e == null) return null;
        JSONObject m = e.optJSONObject(key);
        if (m == null && create) {
            m = new JSONObject();
            try { e.put(key, m); } catch (Exception ignored) { }
        }
        return m;
    }

    public static String goal(JSONObject root, String habitId, String monday) {
        JSONObject g = sub(entry(root, habitId, false), "goals", false);
        return g == null ? "" : g.optString(monday, "").trim();
    }

    /** 写周目标；空文本＝清掉这一周的目标（周门正是看它空不空）。 */
    public static void putGoal(JSONObject root, String habitId, String monday, String text) {
        try {
            JSONObject g = sub(entry(root, habitId, true), "goals", true);
            if (g == null) return;
            String t = text == null ? "" : text.trim();
            if (t.isEmpty()) g.remove(monday); else g.put(monday, t);
        } catch (Exception ignored) { }
    }

    public static String reflection(JSONObject root, String habitId, String date) {
        JSONObject r = sub(entry(root, habitId, false), "reflections", false);
        return r == null ? "" : r.optString(date, "");
    }

    /** 写某日反省；空文本＝清掉（详情页据此同步 logs 的完成位）。 */
    public static void putReflection(JSONObject root, String habitId, String date, String text) {
        try {
            JSONObject r = sub(entry(root, habitId, true), "reflections", true);
            if (r == null) return;
            String t = text == null ? "" : text;
            if (t.trim().isEmpty()) r.remove(date); else r.put(date, t);
        } catch (Exception ignored) { }
    }

    public static void removeHabit(JSONObject root, String habitId) {
        root.remove(habitId);
    }

    /** 出现过目标或反省的全部周一（倒序），历史区用。 */
    public static List<String> weeksOf(JSONObject root, String habitId) {
        java.util.TreeSet<String> mondays = new java.util.TreeSet<>();
        JSONObject e = entry(root, habitId, false);
        JSONObject g = sub(e, "goals", false);
        if (g != null) {
            Iterator<String> it = g.keys();
            while (it.hasNext()) mondays.add(it.next());
        }
        JSONObject r = sub(e, "reflections", false);
        if (r != null) {
            Iterator<String> it = r.keys();
            while (it.hasNext()) mondays.add(DayUtil.mondayOf(it.next()));
        }
        List<String> out = new ArrayList<>(mondays);
        out.sort((a, b) -> b.compareTo(a));
        return out;
    }

    // ---------------- 周门 ----------------
    public static String thisMonday() { return DayUtil.mondayOf(DayUtil.today()); }

    public static String nextMonday() { return DayUtil.addDays(thisMonday(), 7); }

    /**
     * 当前必须已就位的目标周（周一日期）：
     * 本周目标的截止是上周日 20:00——一周已开始而本周目标还空着，周门照样拦
     * （用户口径：拖到周一早上还没填，周门仍然拦）；到了本周日 20:00，
     * 下周目标也加入必须就位之列。
     */
    public static List<String> requiredMondays() {
        List<String> out = new ArrayList<>();
        String today = DayUtil.today();
        String monday = DayUtil.mondayOf(today);
        out.add(monday);
        Calendar cal = DayUtil.parse(today);
        if (cal != null && cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            int mins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            if (mins >= GATE_HOUR * 60) out.add(DayUtil.addDays(monday, 7));
        }
        return out;
    }

    /** 全部 weekly 型习惯（习惯定义在 HabitData）。 */
    public static List<HabitData.Habit> weeklyHabits(Context c) {
        List<HabitData.Habit> out = new ArrayList<>();
        for (HabitData.Habit h : HabitData.habits(HabitData.load(c))) {
            if (HabitData.TYPE_WEEKLY.equals(h.type)) out.add(h);
        }
        return out;
    }

    /** 周门是否该拦：存在 weekly 习惯、且某个必须就位的周目标还空着。 */
    public static boolean gatePending(Context c) {
        List<HabitData.Habit> ws = weeklyHabits(c);
        if (ws.isEmpty()) return false;
        JSONObject root = load(c);
        for (HabitData.Habit h : ws) {
            for (String m : requiredMondays()) {
                if (goal(root, h.id, m).isEmpty()) return true;
            }
        }
        return false;
    }

    // ---------------- 合并（同步纪律核心，仿 HabitData.merge） ----------------
    /**
     * 本机与云端合并：按 habitId 并集；goals/reflections 逐键并集、同键本机优先
     * （文本是在打卡设备上写的，本机这份最权威）。纯函数，不落盘。
     */
    public static JSONObject merge(JSONObject local, JSONObject remote) {
        if (remote == null) return local;
        if (local == null) return remote;
        try {
            JSONObject out = new JSONObject(local.toString());
            Iterator<String> ids = remote.keys();
            while (ids.hasNext()) {
                String hid = ids.next();
                JSONObject theirE = remote.optJSONObject(hid);
                if (theirE == null) continue;
                JSONObject myE = entry(out, hid, true);
                mergeTextMap(myE, theirE, "goals");
                mergeTextMap(myE, theirE, "reflections");
            }
            return out;
        } catch (Exception e) {
            return local;
        }
    }

    private static void mergeTextMap(JSONObject myE, JSONObject theirE, String key) throws Exception {
        JSONObject mine = sub(myE, key, true);
        JSONObject theirs = sub(theirE, key, false);
        if (mine == null || theirs == null) return;
        Iterator<String> ks = theirs.keys();
        while (ks.hasNext()) {
            String k = ks.next();
            if (!mine.has(k)) mine.put(k, theirs.get(k));
        }
    }
}
