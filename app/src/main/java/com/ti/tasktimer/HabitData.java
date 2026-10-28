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
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * 习惯系统数据层（v1.16.0）：本地存应用私有文件 habit_data.json，并经 Supabase
 * user_state 的 habit_data 列（jsonb 对象）同步，手机与电脑（Mac 后续读同一列）共用一份。
 *
 * 结构（云端与本地同一份 JSON）：
 * {
 *   "v": 1,
 *   "habits": [ {id, name, type: min|max|num|sum,
 *                target: 至少型=天/周 · 至多型=次/周 · 数值型=值/天 · 累积型=增量/周(0=未设),
 *                unit: 数值型/累积型的单位（如 步/件）,
 *                startDate: 累积型起始日 yyyy-MM-dd,
 *                totalGoal: 累积型总量目标（0=未设）,
 *                startValue: 累积型起始数字（建习惯前已攒下的量，0=无）,
 *                reminder: "HH:mm" 或 null,
 *                createdAtMs} ],
 *   "logs":  { "yyyy-MM-dd": { habitId: 数值 } },   // 至少/至多型：1=是 0=否（记了才有键）
 *   "diary": { "yyyy-MM-dd": "晨间日记文本" },        // 键是写日记那天（新一天的 session 日）
 *   "morning": { "enabled": bool, "dayStartMinutes": int(默认 240=4:00) },
 *   "weekTargets": { "yyyy-MM-dd(该周周一)": { habitId: target 覆盖值 } },
 *   "suggestDismissed": bool,                        // 晨间建议卡点了「不用」后不再出现
 *   "morningDone": { "yyyy-MM-dd(session 日)": true },
 *   "updatedAtMs": long
 * }
 *
 * 同步合并纪律（SbSync.syncUserState，与 idle_nag/aspiration 同一套，列先探测再碰）：
 * 本机从未存过（has_local=false）且云端有值 → 整份采用云端；
 * 本机有值 → 与云端按 merge() 合并（定义按 id 并集本机优先；logs/diary/weekTargets
 * 按日期并集、同一格本机优先——记录是打卡设备上产生的，本机这份最权威，与 SelfChecks
 * 合并口径一致），合并结果与云端不同才回写。Realtime 推来时只合并进本地、不回写。
 * 列未就绪时 App 完全不碰该键、只用本地，绝不崩溃、绝不连累其他回写。
 */
public final class HabitData {
    private static final String FILE = "habit_data.json";
    private static final String PREF = "habit_store";

    public static final String TYPE_MIN = "min"; // 至少型
    public static final String TYPE_MAX = "max"; // 至多型
    public static final String TYPE_NUM = "num"; // 数值型
    public static final String TYPE_SUM = "sum"; // 累积型

    private HabitData() {}

    // ---------------- 习惯定义 ----------------
    public static final class Habit {
        public String id;
        public String name = "";
        public String type = TYPE_MIN;
        public long target = 0;
        public String unit = "";
        public String startDate = "";  // 累积型
        public long totalGoal = 0;     // 累积型（0=未设）
        public long startValue = 0;    // 累积型起始数字（0=无）
        public String reminder;        // "HH:mm"，null=不提醒
        public long createdAtMs = 0;

        public JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("name", name);
                o.put("type", type);
                o.put("target", target);
                o.put("unit", unit == null ? "" : unit);
                o.put("startDate", startDate == null ? "" : startDate);
                o.put("totalGoal", totalGoal);
                o.put("startValue", startValue);
                o.put("reminder", reminder == null ? JSONObject.NULL : reminder);
                o.put("createdAtMs", createdAtMs);
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static Habit fromJson(JSONObject o) {
            if (o == null) return null;
            Habit h = new Habit();
            h.id = o.optString("id", "");
            if (h.id.isEmpty()) return null;
            h.name = o.optString("name", "");
            h.type = o.optString("type", TYPE_MIN);
            if (!TYPE_MIN.equals(h.type) && !TYPE_MAX.equals(h.type)
                    && !TYPE_NUM.equals(h.type) && !TYPE_SUM.equals(h.type)) h.type = TYPE_MIN;
            h.target = o.optLong("target", 0);
            h.unit = o.optString("unit", "");
            h.startDate = o.optString("startDate", "");
            h.totalGoal = o.optLong("totalGoal", 0);
            h.startValue = o.optLong("startValue", 0);
            h.reminder = o.isNull("reminder") ? null : o.optString("reminder", null);
            if (h.reminder != null && h.reminder.isEmpty()) h.reminder = null;
            h.createdAtMs = o.optLong("createdAtMs", 0);
            return h;
        }
    }

    // ---------------- 本地读写 ----------------
    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 本机是否存过一份习惯数据（区分「没用过」与「用户在本机定过」）。 */
    public static boolean hasLocal(Context c) { return prefs(c).getBoolean("has_local", false); }

    public static synchronized JSONObject load(Context c) {
        try {
            File f = file(c);
            if (f.exists()) {
                String s = TaskStore.readAll(new FileInputStream(f));
                JSONObject o = new JSONObject(s);
                ensureShape(o);
                return o;
            }
        } catch (Exception ignored) { }
        return empty();
    }

    /** 保存（写盘＋标记本机有值＋触发一次后台同步；同步没登录/列未就绪时自己短路）。 */
    public static synchronized void save(Context c, JSONObject root) {
        try {
            ensureShape(root);
            root.put("updatedAtMs", System.currentTimeMillis());
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
            prefs(c).edit().putBoolean("has_local", true).apply();
        } catch (Exception ignored) { }
        requestSync(c);
    }

    /** 只写盘、不触发同步（同步层在同步流程内部落盘合并结果时用，避免递归触发）。 */
    public static synchronized void saveQuiet(Context c, JSONObject root) {
        try {
            ensureShape(root);
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
            prefs(c).edit().putBoolean("has_local", true).apply();
        } catch (Exception ignored) { }
    }

    /** 采用云端整份（本机从未存过时）；标记本机已有值，之后以合并纪律为准。不触发同步。 */
    public static synchronized void adopt(Context c, JSONObject remote) {
        if (remote == null) return;
        try {
            JSONObject o = new JSONObject(remote.toString());
            ensureShape(o);
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
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

    public static JSONObject empty() {
        try {
            JSONObject o = new JSONObject();
            ensureShape(o);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void ensureShape(JSONObject o) throws Exception {
        if (!o.has("v")) o.put("v", 1);
        if (o.isNull("habits") || !(o.opt("habits") instanceof JSONArray)) o.put("habits", new JSONArray());
        if (o.isNull("logs") || !(o.opt("logs") instanceof JSONObject)) o.put("logs", new JSONObject());
        if (o.isNull("diary") || !(o.opt("diary") instanceof JSONObject)) o.put("diary", new JSONObject());
        if (o.isNull("morning") || !(o.opt("morning") instanceof JSONObject)) {
            JSONObject m = new JSONObject();
            m.put("enabled", false);
            m.put("dayStartMinutes", 240);
            o.put("morning", m);
        }
        if (o.isNull("weekTargets") || !(o.opt("weekTargets") instanceof JSONObject)) {
            o.put("weekTargets", new JSONObject());
        }
        if (o.isNull("morningDone") || !(o.opt("morningDone") instanceof JSONObject)) {
            o.put("morningDone", new JSONObject());
        }
    }

    // ---------------- 习惯列表 ----------------
    public static List<Habit> habits(JSONObject root) {
        List<Habit> out = new ArrayList<>();
        JSONArray arr = root == null ? null : root.optJSONArray("habits");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                Habit h = Habit.fromJson(arr.optJSONObject(i));
                if (h != null) out.add(h);
            }
        }
        return out;
    }

    public static void putHabits(JSONObject root, List<Habit> habits) {
        try {
            JSONArray arr = new JSONArray();
            for (Habit h : habits) arr.put(h.toJson());
            root.put("habits", arr);
        } catch (Exception ignored) { }
    }

    public static Habit habitById(JSONObject root, String id) {
        for (Habit h : habits(root)) if (h.id.equals(id)) return h;
        return null;
    }

    // ---------------- 记录读写 ----------------
    /** 某天某习惯的记录值；没记返回 null（与记了 0 严格区分）。 */
    public static Long logValue(JSONObject root, String date, String habitId) {
        try {
            JSONObject logs = root.optJSONObject("logs");
            if (logs == null) return null;
            JSONObject day = logs.optJSONObject(date);
            if (day == null || !day.has(habitId) || day.isNull(habitId)) return null;
            return day.getLong(habitId);
        } catch (Exception e) {
            return null;
        }
    }

    public static void putLog(JSONObject root, String date, String habitId, long value) {
        try {
            JSONObject logs = root.optJSONObject("logs");
            if (logs == null) { logs = new JSONObject(); root.put("logs", logs); }
            JSONObject day = logs.optJSONObject(date);
            if (day == null) { day = new JSONObject(); logs.put(date, day); }
            day.put(habitId, value);
        } catch (Exception ignored) { }
    }

    public static void removeLog(JSONObject root, String date, String habitId) {
        try {
            JSONObject logs = root.optJSONObject("logs");
            if (logs == null) return;
            JSONObject day = logs.optJSONObject(date);
            if (day != null) {
                day.remove(habitId);
                if (day.length() == 0) logs.remove(date);
            }
        } catch (Exception ignored) { }
    }

    // ---------------- 晨间设置 ----------------
    public static boolean morningEnabled(JSONObject root) {
        JSONObject m = root == null ? null : root.optJSONObject("morning");
        return m != null && m.optBoolean("enabled", false);
    }

    public static int morningDayStartMinutes(JSONObject root) {
        JSONObject m = root == null ? null : root.optJSONObject("morning");
        int v = m == null ? 240 : m.optInt("dayStartMinutes", 240);
        return Math.max(120, Math.min(480, v));
    }

    public static void setMorning(JSONObject root, boolean enabled, int dayStartMinutes) {
        try {
            JSONObject m = new JSONObject();
            m.put("enabled", enabled);
            m.put("dayStartMinutes", Math.max(120, Math.min(480, dayStartMinutes)));
            root.put("morning", m);
        } catch (Exception ignored) { }
    }

    public static boolean suggestDismissed(JSONObject root) {
        return root != null && root.optBoolean("suggestDismissed", false);
    }

    public static void setSuggestDismissed(JSONObject root) {
        try { root.put("suggestDismissed", true); } catch (Exception ignored) { }
    }

    // ---------------- 晨间 session ----------------
    /**
     * 当前所属的晨间 session 日：最近一次已过的 dayStart 所在的日历日。
     * now 已过今天 dayStart → 今天；还没到 → 昨天（那一轮的 dayStart 在昨天）。
     */
    public static String sessionDate(JSONObject root) {
        int ds = morningDayStartMinutes(root);
        java.util.Calendar now = java.util.Calendar.getInstance();
        int mins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        String today = DayUtil.today();
        return mins >= ds ? today : DayUtil.addDays(today, -1);
    }

    /** session 日要填的「昨日」＝session 日的前一天。 */
    public static String logDateFor(String sessionDate) { return DayUtil.addDays(sessionDate, -1); }

    public static boolean morningDone(JSONObject root, String sessionDate) {
        JSONObject d = root == null ? null : root.optJSONObject("morningDone");
        return d != null && d.optBoolean(sessionDate, false);
    }

    public static void markMorningDone(JSONObject root, String sessionDate) {
        try {
            JSONObject d = root.optJSONObject("morningDone");
            if (d == null) { d = new JSONObject(); root.put("morningDone", d); }
            d.put(sessionDate, true);
        } catch (Exception ignored) { }
    }

    /** 晨间门是否该弹：开关开着、当前 session 还没完成。 */
    public static boolean shouldShowMorning(JSONObject root) {
        return morningEnabled(root) && !morningDone(root, sessionDate(root));
    }

    // ---------------- 周目标 ----------------
    /** 某周某习惯的生效 target：weekTargets 有覆盖用覆盖，否则用定义值。 */
    public static long effectiveTarget(JSONObject root, Habit h, String monday) {
        try {
            JSONObject wt = root.optJSONObject("weekTargets");
            if (wt != null) {
                JSONObject wk = wt.optJSONObject(monday);
                if (wk != null && wk.has(h.id) && !wk.isNull(h.id)) return wk.getLong(h.id);
            }
        } catch (Exception ignored) { }
        return h.target;
    }

    public static void setWeekTarget(JSONObject root, String monday, String habitId, long value) {
        try {
            JSONObject wt = root.optJSONObject("weekTargets");
            if (wt == null) { wt = new JSONObject(); root.put("weekTargets", wt); }
            JSONObject wk = wt.optJSONObject(monday);
            if (wk == null) { wk = new JSONObject(); wt.put(monday, wk); }
            wk.put(habitId, value);
        } catch (Exception ignored) { }
    }

    public static void clearWeekTargets(JSONObject root, String monday) {
        try {
            JSONObject wt = root.optJSONObject("weekTargets");
            if (wt != null) wt.remove(monday);
        } catch (Exception ignored) { }
    }

    public static String diary(JSONObject root, String date) {
        JSONObject d = root == null ? null : root.optJSONObject("diary");
        return d == null ? "" : d.optString(date, "");
    }

    public static void putDiary(JSONObject root, String date, String text) {
        try {
            JSONObject d = root.optJSONObject("diary");
            if (d == null) { d = new JSONObject(); root.put("diary", d); }
            d.put(date, text);
        } catch (Exception ignored) { }
    }

    // ---------------- 合并（同步纪律核心） ----------------
    /**
     * 本机与云端合并：定义按 id 并集（同 id 本机优先）；logs/diary/weekTargets/morningDone
     * 按日期并集、同格本机优先；morning 与 suggestDismissed 本机优先（本机有值语境下调用）。
     * 纯函数，不落盘。
     */
    public static JSONObject merge(JSONObject local, JSONObject remote) {
        if (remote == null) return local;
        if (local == null) return remote;
        try {
            JSONObject out = new JSONObject(local.toString());
            ensureShape(out);
            // habits：云端独有的定义补进来（本机同 id 的定义优先，不覆盖）
            List<Habit> mine = habits(out);
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (Habit h : mine) ids.add(h.id);
            for (Habit h : habits(remote)) {
                if (!ids.contains(h.id)) mine.add(h);
            }
            mine.sort((a, b) -> Long.compare(a.createdAtMs, b.createdAtMs));
            putHabits(out, mine);
            // logs / diary / weekTargets / morningDone：逐层并集，本机优先
            mergeDayMap(out, remote, "logs", true);
            mergeDayMap(out, remote, "diary", false);
            mergeWeekTargets(out, remote);
            JSONObject md = out.optJSONObject("morningDone");
            JSONObject rmd = remote.optJSONObject("morningDone");
            if (rmd != null) {
                if (md == null) { md = new JSONObject(); out.put("morningDone", md); }
                Iterator<String> it = rmd.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if (!md.has(k)) md.put(k, rmd.get(k));
                }
            }
            out.put("updatedAtMs", Math.max(local.optLong("updatedAtMs", 0),
                    remote.optLong("updatedAtMs", 0)));
            return out;
        } catch (Exception e) {
            return local;
        }
    }

    /** logs（值是 {habitId:数} 的二级 map）与 diary（值是字符串）同一套并集逻辑。 */
    private static void mergeDayMap(JSONObject out, JSONObject remote, String key, boolean nested) throws Exception {
        JSONObject mine = out.optJSONObject(key);
        JSONObject theirs = remote.optJSONObject(key);
        if (theirs == null) return;
        if (mine == null) { mine = new JSONObject(); out.put(key, mine); }
        Iterator<String> dates = theirs.keys();
        while (dates.hasNext()) {
            String date = dates.next();
            if (nested) {
                JSONObject theirDay = theirs.optJSONObject(date);
                if (theirDay == null) continue;
                JSONObject myDay = mine.optJSONObject(date);
                if (myDay == null) { myDay = new JSONObject(); mine.put(date, myDay); }
                Iterator<String> hs = theirDay.keys();
                while (hs.hasNext()) {
                    String hid = hs.next();
                    if (!myDay.has(hid)) myDay.put(hid, theirDay.get(hid));
                }
            } else {
                if (!mine.has(date)) mine.put(date, theirs.get(date));
            }
        }
    }

    private static void mergeWeekTargets(JSONObject out, JSONObject remote) throws Exception {
        JSONObject mine = out.optJSONObject("weekTargets");
        JSONObject theirs = remote.optJSONObject("weekTargets");
        if (theirs == null) return;
        if (mine == null) { mine = new JSONObject(); out.put("weekTargets", mine); }
        Iterator<String> weeks = theirs.keys();
        while (weeks.hasNext()) {
            String wk = weeks.next();
            JSONObject theirWk = theirs.optJSONObject(wk);
            if (theirWk == null) continue;
            JSONObject myWk = mine.optJSONObject(wk);
            if (myWk == null) { myWk = new JSONObject(); mine.put(wk, myWk); }
            Iterator<String> hs = theirWk.keys();
            while (hs.hasNext()) {
                String hid = hs.next();
                if (!myWk.has(hid)) myWk.put(hid, theirWk.get(hid));
            }
        }
    }

    /** 规范化序列化（键递归排序），供同步层比较两份数据是否语义等价，与键序无关。 */
    public static String canonical(JSONObject o) {
        if (o == null) return "null";
        StringBuilder sb = new StringBuilder();
        writeCanon(o, sb);
        return sb.toString();
    }

    private static void writeCanon(Object v, StringBuilder sb) {
        try {
            if (v == null || v == JSONObject.NULL) { sb.append("null"); return; }
            if (v instanceof JSONObject) {
                JSONObject o = (JSONObject) v;
                TreeMap<String, Object> sorted = new TreeMap<>();
                Iterator<String> it = o.keys();
                while (it.hasNext()) { String k = it.next(); sorted.put(k, o.get(k)); }
                sb.append('{');
                boolean first = true;
                for (java.util.Map.Entry<String, Object> e : sorted.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append(JSONObject.quote(e.getKey())).append(':');
                    writeCanon(e.getValue(), sb);
                }
                sb.append('}');
                return;
            }
            if (v instanceof JSONArray) {
                JSONArray a = (JSONArray) v;
                sb.append('[');
                for (int i = 0; i < a.length(); i++) {
                    if (i > 0) sb.append(',');
                    writeCanon(a.get(i), sb);
                }
                sb.append(']');
                return;
            }
            if (v instanceof String) { sb.append(JSONObject.quote((String) v)); return; }
            if (v instanceof Number) {
                double d = ((Number) v).doubleValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) sb.append(Long.toString((long) d));
                else sb.append(Double.toString(d));
                return;
            }
            sb.append(JSONObject.quote(String.valueOf(v)));
        } catch (Exception e) {
            sb.append("null");
        }
    }

    /** 两份数据语义是否等价（忽略 updatedAtMs——它每次保存都会变，不算内容差异）。 */
    public static boolean contentEquals(JSONObject a, JSONObject b) {
        if (a == null || b == null) return a == b;
        try {
            JSONObject x = new JSONObject(a.toString());
            JSONObject y = new JSONObject(b.toString());
            x.remove("updatedAtMs");
            y.remove("updatedAtMs");
            return canonical(x).equals(canonical(y));
        } catch (Exception e) {
            return false;
        }
    }
}
