package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 「今天安排」的时段数据（v1.28.0 feature 9，用户亲自设计）：
 * - 睡眠段：长期保存，每天沿用（默认 22:30–06:00，06:00 即起床、是时段轴的零点）
 * - 锁时段：按日期存，被其他事占住、不能安排任务的时段，可命名；起止存「轴分钟」
 *   （从当天起床时刻起算的 0..1440，横条跨度＝今晨起床→明晨起床，天然跨午夜不歧义）
 * - 可支配输入：按日期存的「今天可支配 X 小时」，驱动今天安排页的时间账
 *
 * 存储与同步口径仿 HabitDisplay：本地 SharedPreferences 存整份 JSON ＋云端
 * user_state.day_avail（jsonb）。列未就绪时完全不碰云端、只用本地（SbSync 探测
 * 纪律）；本机没存过且云端有值 → 采用云端；本机存过且与云端不同 → 回写本机值。
 * 无数据/坏数据一律回落「无锁时、无可支配输入、默认睡眠」，任何读取不抛异常。
 */
public final class DayAvail {
    private static final String PREF = "day_avail";
    private static final String KEY_JSON = "json";
    private static final String KEY_HAS_LOCAL = "has_local";

    public static final String DEFAULT_SLEEP_START = "22:30";
    public static final String DEFAULT_SLEEP_END = "06:00";

    private DayAvail() {}

    /** 一段锁时：轴分钟 [s, e) ＋名字（可空）。 */
    public static final class Seg {
        public int s;
        public int e;
        public String name;
        public Seg(int s, int e, String name) {
            this.s = s; this.e = e; this.name = name == null ? "" : name;
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean hasLocal(Context c) { return sp(c).getBoolean(KEY_HAS_LOCAL, false); }

    /** 整份数据；没存过或解析坏了返回只有默认睡眠的空壳，绝不返回 null。 */
    public static JSONObject load(Context c) {
        String raw = sp(c).getString(KEY_JSON, null);
        if (raw != null) {
            try {
                return new JSONObject(raw);
            } catch (Exception ignored) { }
        }
        return new JSONObject();
    }

    private static void save(Context c, JSONObject root) {
        // 修剪 7 天前的按日数据，防 days 无限膨胀（睡眠是长期键、不在此列）
        try {
            JSONObject days = root.optJSONObject("days");
            if (days != null) {
                String cutoff = DayUtil.addDays(DayUtil.today(), -7);
                List<String> drop = new ArrayList<>();
                Iterator<String> it = days.keys();
                while (it.hasNext()) {
                    String d = it.next();
                    if (d.compareTo(cutoff) < 0) drop.add(d);
                }
                for (String d : drop) days.remove(d);
            }
        } catch (Exception ignored) { }
        sp(c).edit().putBoolean(KEY_HAS_LOCAL, true).putString(KEY_JSON, root.toString()).apply();
        GuardRules.kickSync(c);
    }

    // ---------------- 睡眠（长期） ----------------

    /** 睡眠开始（墙钟分钟，默认 22:30）。 */
    public static int sleepStartMin(Context c) {
        JSONObject s = load(c).optJSONObject("sleep");
        int v = s == null ? -1 : LocalAutoScheduler.parseHhmm(s.optString("start", ""));
        return v >= 0 ? v : LocalAutoScheduler.parseHhmm(DEFAULT_SLEEP_START);
    }

    /** 睡眠结束＝起床（墙钟分钟，默认 06:00）。 */
    public static int sleepEndMin(Context c) {
        JSONObject s = load(c).optJSONObject("sleep");
        int v = s == null ? -1 : LocalAutoScheduler.parseHhmm(s.optString("end", ""));
        return v >= 0 ? v : LocalAutoScheduler.parseHhmm(DEFAULT_SLEEP_END);
    }

    /** 时段轴零点＝起床时刻（墙钟分钟）。 */
    public static int wakeMin(Context c) { return sleepEndMin(c); }

    /** 睡眠时长（分钟）：跨午夜按 start→次日 end 算。 */
    public static int sleepDurMin(Context c) {
        int d = sleepEndMin(c) - sleepStartMin(c);
        return d > 0 ? d : d + 1440;
    }

    public static void setSleep(Context c, int startMin, int endMin) {
        try {
            JSONObject root = load(c);
            JSONObject s = new JSONObject();
            s.put("start", LocalAutoScheduler.hhmm(((startMin % 1440) + 1440) % 1440));
            s.put("end", LocalAutoScheduler.hhmm(((endMin % 1440) + 1440) % 1440));
            root.put("sleep", s);
            save(c, root);
        } catch (Exception ignored) { }
    }

    // ---------------- 锁时（按日期） ----------------

    private static JSONObject dayObj(JSONObject root, String date, boolean create) {
        JSONObject days = root.optJSONObject("days");
        if (days == null) {
            if (!create) return null;
            days = new JSONObject();
            try { root.put("days", days); } catch (Exception ignored) { return null; }
        }
        JSONObject d = days.optJSONObject(date);
        if (d == null && create) {
            d = new JSONObject();
            try { days.put(date, d); } catch (Exception ignored) { }
        }
        return d;
    }

    /** 某天的锁时段（轴分钟），按开始排序；无数据返回空表。 */
    public static List<Seg> locksFor(Context c, String date) {
        List<Seg> out = new ArrayList<>();
        JSONObject d = dayObj(load(c), date, false);
        if (d == null) return out;
        JSONArray arr = d.optJSONArray("locks");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            int s = o.optInt("s", -1), e = o.optInt("e", -1);
            if (s < 0 || e <= s || s >= 1440) continue;
            out.add(new Seg(s, Math.min(e, 1440), o.optString("name", "")));
        }
        out.sort((a, b) -> Integer.compare(a.s, b.s));
        return out;
    }

    public static void setLocks(Context c, String date, List<Seg> locks) {
        try {
            JSONObject root = load(c);
            JSONObject d = dayObj(root, date, true);
            if (d == null) return;
            JSONArray arr = new JSONArray();
            List<Seg> sorted = new ArrayList<>(locks);
            sorted.sort((a, b) -> Integer.compare(a.s, b.s));
            for (Seg g : sorted) {
                if (g.e <= g.s) continue;
                JSONObject o = new JSONObject();
                o.put("s", Math.max(0, g.s));
                o.put("e", Math.min(1440, g.e));
                o.put("name", g.name == null ? "" : g.name);
                arr.put(o);
            }
            d.put("locks", arr);
            save(c, root);
        } catch (Exception ignored) { }
    }

    // ---------------- 可支配输入（按日期） ----------------

    /** 今天可支配（小时，小数）；未设返回 -1。 */
    public static double freeHours(Context c, String date) {
        JSONObject d = dayObj(load(c), date, false);
        if (d == null || !d.has("free_hours")) return -1;
        double v = d.optDouble("free_hours", -1);
        return v >= 0 ? v : -1;
    }

    /** 设置可支配小时；负数＝清除（回到未设）。 */
    public static void setFreeHours(Context c, String date, double hours) {
        try {
            JSONObject root = load(c);
            JSONObject d = dayObj(root, date, true);
            if (d == null) return;
            if (hours < 0) d.remove("free_hours");
            else d.put("free_hours", hours);
            save(c, root);
        } catch (Exception ignored) { }
    }

    // ---------------- 共用换算 ----------------

    /** 轴分钟 → 墙钟分钟（可 ≥1440，表示次日；显示时 %1440）。 */
    public static int axisToWall(int wakeMin, int axisMin) { return wakeMin + axisMin; }

    /** 带标签的墙钟段（日历渲染要区分锁时与睡觉）：[s,e) 在 [0,1440) 内。 */
    public static final class WallSeg {
        public final int s;
        public final int e;
        public final String label;
        public final boolean sleep;
        public WallSeg(int s, int e, String label, boolean sleep) {
            this.s = s; this.e = e; this.label = label; this.sleep = sleep;
        }
    }

    /**
     * 某天的占用段（墙钟、带标签）：锁时段（轴分钟＋起床时刻换算，跨到次日的
     * 部分截掉）＋睡眠段（晚上 [sleepStart,1440) 与凌晨 [0,sleepEnd)；
     * 白天睡的罕见形状按 [start,end)）。按开始排序。
     */
    public static List<WallSeg> wallSegments(Context c, String date) {
        List<WallSeg> out = new ArrayList<>();
        int wake = wakeMin(c);
        for (Seg g : locksFor(c, date)) {
            int ws = wake + g.s, we = wake + g.e;
            int s = Math.max(0, Math.min(ws, 1440));
            int e = Math.max(0, Math.min(we, 1440));
            if (e > s) out.add(new WallSeg(s, e, g.name, false));
        }
        int ss = sleepStartMin(c), se = sleepEndMin(c);
        if (ss > se) { // 常规：夜里睡到次晨
            out.add(new WallSeg(ss, 1440, "", true));
            if (se > 0) out.add(new WallSeg(0, se, "", true));
        } else if (se > ss) {
            out.add(new WallSeg(ss, se, "", true));
        }
        out.sort((a, b) -> Integer.compare(a.s, b.s));
        return out;
    }

    /**
     * 某天排程要避开的墙钟占用区间（[0,1440) 内，{start,end} 列表）：
     * 本地自动排、快速重排、AI 重排、聊天排程卡共用这一份口径。
     */
    public static List<int[]> blockedWall(Context c, String date) {
        List<int[]> out = new ArrayList<>();
        for (WallSeg w : wallSegments(c, date)) out.add(new int[]{w.s, w.e});
        return out;
    }

    /**
     * 时间账的预算（分钟）：填了可支配就按可支配（小时→分钟）；
     * 没填按这一天的理论可用＝1440 − 睡眠 − 锁时合计。
     */
    public static int budgetMin(Context c, String date) {
        double fh = freeHours(c, date);
        if (fh >= 0) return (int) Math.round(fh * 60);
        int lockSum = 0;
        for (Seg g : locksFor(c, date)) lockSum += g.e - g.s;
        return Math.max(0, 1440 - sleepDurMin(c) - lockSum);
    }

    // ---------------- 云端 JSON（user_state.day_avail，整份对象） ----------------

    public static JSONObject toCloudJson(Context c) { return load(c); }

    /** 采用云端值（整份替换本地并标 has_local，之后以本机为准参与合并——与 HabitDisplay 同口径）。 */
    public static void adoptCloud(Context c, JSONObject o) {
        if (o == null) return;
        sp(c).edit().putBoolean(KEY_HAS_LOCAL, true).putString(KEY_JSON, o.toString()).apply();
    }

    /** 本机值与云端对象是否一致（云端为 null 视为不一致——前提是本机有值）。 */
    public static boolean cloudEquals(Context c, JSONObject remote) {
        if (remote == null) return false;
        return load(c).toString().equals(remote.toString());
    }
}
