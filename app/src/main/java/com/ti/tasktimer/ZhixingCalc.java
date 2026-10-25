package com.ti.tasktimer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知行合一指数与各项统计的计算层（v1.16.0）。公式定案（写进帮助第二十二节）：
 *
 *   指数 ＝ 习惯达成 ×50% ＋ 任务完成 ×35% ＋ 分神控制 ×15%（权重常量见下，改权重只改这里）
 *
 * 四种习惯的周达成率（周＝周一至周日）：
 *   至少型 min：做到天数 ÷ 目标天数，封顶 100%（多做还是 100%）
 *   至多型 max：次数 ≤ 目标 → 100%；超出 → 目标 ÷ 实际 ×100 取整
 *   数值型 num：每天 实际÷目标（单日封顶 100%）取一周平均（只算已过去的天数，含今天）
 *   累积型 sum：本周增量 ÷ 周增量目标；未设周目标（0）时，本周有增量记 100%、没有记 0%
 * 习惯达成 ＝ 全部习惯周达成率的平均；没有任何习惯时按 100 起算（冷启动，页面有注明）。
 *
 * 任务完成比例 ＝ 区间内「到期/排期」的任务里已完成数 ÷ 总数，来源三处并集去重：
 *   ① 非每日任务 deadline 落在区间内（键＝任务 id，完成＝status 为 done）；
 *   ② 每日任务按天计（键＝id@日期，完成＝doneDates 含该日；未来天不计）；
 *   ③ 本地缓存的 schedule（只有一份、通常是今天）其日期落在区间内时，块里的任务
 *      按同一键空间补入。总数为 0 时按 100 起算（没有承诺，不扣分）。
 *
 * 分神控制：分神次数 ＝ 区间内 自检 didRight=false 条数 ＋ 守卫事件条数；
 *   控制分 ＝ max(0, 100 − 5×次数)。没有数据时次数为 0、控制分 100（冷启动同口径）。
 *
 * 单日口径（晨间小报）：习惯按单日达成（至少型做了=100/没做=0、至多型没发生=100/发生了=0、
 *   数值型按比例、累积型有增量=100/没有=0；至少/至多/数值型当天没记录则不计入平均，
 *   累积型没记录按 0 计）；任务与分神同上、区间缩成一天。
 */
public final class ZhixingCalc {
    // ---- 权重（定案：习惯 50% / 任务 35% / 分神 15%） ----
    public static final double W_HABIT = 0.50;
    public static final double W_TASK = 0.35;
    public static final double W_DRIFT = 0.15;

    private ZhixingCalc() {}

    // ---------------- 单个习惯：一周 ----------------
    public static final class HabitWeek {
        public int rate;            // 周达成率 0..100
        public long primary;        // min=做到天数 / max=发生次数 / num=昨日值 / sum=本周增量
        public long target;         // 本周生效目标
        public double avgRatio;     // num 专用：日均比例（0..1+，未封顶前）
    }

    public static HabitWeek habitWeek(JSONObject root, HabitData.Habit h, String monday) {
        HabitWeek w = new HabitWeek();
        w.target = HabitData.effectiveTarget(root, h, monday);
        List<String> days = DayUtil.weekDates(monday);
        String today = DayUtil.today();
        switch (h.type) {
            case HabitData.TYPE_MIN: {
                int done = 0;
                for (String d : days) {
                    Long v = HabitData.logValue(root, d, h.id);
                    if (v != null && v > 0) done++;
                }
                w.primary = done;
                w.rate = w.target <= 0 ? (done > 0 ? 100 : 0)
                        : (int) Math.min(100, Math.round(done * 100.0 / w.target));
                break;
            }
            case HabitData.TYPE_MAX: {
                int n = 0;
                for (String d : days) {
                    Long v = HabitData.logValue(root, d, h.id);
                    if (v != null && v > 0) n++;
                }
                w.primary = n;
                if (n <= w.target) w.rate = 100;
                else if (n == 0) w.rate = 100;
                else w.rate = (int) Math.round(w.target * 100.0 / n);
                break;
            }
            case HabitData.TYPE_NUM: {
                double sum = 0;
                int cnt = 0;
                Long yest = HabitData.logValue(root, DayUtil.addDays(today, -1), h.id);
                w.primary = yest == null ? 0 : yest;
                for (String d : days) {
                    if (d.compareTo(today) > 0) break; // 未来天不计
                    Long v = HabitData.logValue(root, d, h.id);
                    double r = (v == null || w.target <= 0) ? 0 : Math.min(1.0, v * 1.0 / w.target);
                    sum += r;
                    cnt++;
                }
                w.avgRatio = cnt == 0 ? 0 : sum / cnt;
                w.rate = (int) Math.round(w.avgRatio * 100);
                break;
            }
            default: { // sum
                long s = 0;
                for (String d : days) {
                    Long v = HabitData.logValue(root, d, h.id);
                    if (v != null) s += v;
                }
                w.primary = s;
                if (w.target > 0) w.rate = (int) Math.min(100, Math.round(s * 100.0 / w.target));
                else w.rate = s > 0 ? 100 : 0;
                break;
            }
        }
        return w;
    }

    // ---------------- 单个习惯：单日（晨间小报） ----------------
    /** 返回 null 表示当天没记录、不计入平均（仅累积型没记录按 0 返回）。 */
    public static Integer habitDayRate(JSONObject root, HabitData.Habit h, String date) {
        Long v = HabitData.logValue(root, date, h.id);
        switch (h.type) {
            case HabitData.TYPE_MIN:
                return v == null ? null : (v > 0 ? 100 : 0);
            case HabitData.TYPE_MAX:
                return v == null ? null : (v > 0 ? 0 : 100);
            case HabitData.TYPE_NUM: {
                if (v == null) return null;
                long t = HabitData.effectiveTarget(root, h, DayUtil.mondayOf(date));
                if (t <= 0) return 100;
                return (int) Math.min(100, Math.round(v * 100.0 / t));
            }
            default:
                return v == null || v <= 0 ? 0 : 100;
        }
    }

    // ---------------- 任务完成 ----------------
    /** 区间 [from, to]（含两端，均 ≤ 今天的部分）的任务完成 {done, total}。 */
    public static int[] taskCompletion(Context c, List<TaskItem> tasks, String from, String to) {
        String today = DayUtil.today();
        Set<String> seen = new HashSet<>();
        int done = 0, total = 0;
        for (TaskItem t : tasks) {
            if (t == null || t.deleted) continue;
            if ("daily".equals(t.repeat)) {
                String d = from;
                while (d.compareTo(to) <= 0 && d.compareTo(today) <= 0) {
                    String key = t.id + "@" + d;
                    if (seen.add(key)) {
                        total++;
                        if (t.doneDates != null && t.doneDates.contains(d)) done++;
                    }
                    d = DayUtil.addDays(d, 1);
                }
            } else if (t.deadline != null && t.deadline.compareTo(from) >= 0
                    && t.deadline.compareTo(to) <= 0) {
                if (seen.add(t.id)) {
                    total++;
                    if ("done".equals(t.status)) done++;
                }
            }
        }
        // schedule 缓存（只有一份）：日期落在区间内时，块里的任务按同一键空间补入
        try {
            JSONObject sch = SyncManager.latestSchedule(c);
            if (sch != null) {
                String d = sch.optString("date", "");
                if (!d.isEmpty() && d.compareTo(from) >= 0 && d.compareTo(to) <= 0
                        && d.compareTo(today) <= 0) {
                    java.util.Map<String, TaskItem> byId = new java.util.HashMap<>();
                    for (TaskItem t : tasks) if (t != null) byId.put(t.id, t);
                    JSONArray blocks = sch.optJSONArray("blocks");
                    if (blocks != null) {
                        for (int i = 0; i < blocks.length(); i++) {
                            JSONObject b = blocks.optJSONObject(i);
                            if (b == null) continue;
                            TaskItem t = byId.get(b.optString("taskId", ""));
                            if (t == null || t.deleted) continue;
                            String key = "daily".equals(t.repeat) ? t.id + "@" + d : t.id;
                            if (seen.add(key)) {
                                total++;
                                boolean dn = "daily".equals(t.repeat)
                                        ? (t.doneDates != null && t.doneDates.contains(d))
                                        : "done".equals(t.status);
                                if (dn) done++;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
        return new int[]{done, total};
    }

    // ---------------- 分神 ----------------
    /** 区间内的分神次数：自检 didRight=false ＋ 守卫事件。 */
    public static int driftCount(Context c, String from, String to) {
        long lo = DayUtil.dayStartMs(from);
        long hi = DayUtil.dayStartMs(DayUtil.addDays(to, 1));
        int n = 0;
        try {
            JSONArray sc = SelfChecks.loadArray(c);
            for (int i = 0; i < sc.length(); i++) {
                JSONObject e = sc.optJSONObject(i);
                if (e == null) continue;
                long ts = e.optLong("ts", 0);
                if (ts >= lo && ts < hi && e.has("didRight") && !e.isNull("didRight")
                        && !e.optBoolean("didRight", true)) n++;
            }
        } catch (Exception ignored) { }
        try {
            JSONArray ev = GuardEvents.loadArray(c);
            for (int i = 0; i < ev.length(); i++) {
                JSONObject e = ev.optJSONObject(i);
                if (e == null) continue;
                if (e.optBoolean("note", false)) continue; // 备注事件（如语义降级放行）不是分神，不计数（v1.21.4）
                if (e.optBoolean("overturned", false)) continue; // 用户点「分析错误」认定的误报，不计数（v1.24.3）
                long ts = e.optLong("ts", 0);
                if (ts >= lo && ts < hi) n++;
            }
        } catch (Exception ignored) { }
        return n;
    }

    public static int driftScore(int count) { return Math.max(0, 100 - 5 * count); }

    // ---------------- 周指数 ----------------
    public static final class WeekStats {
        public String monday;
        public int habitAvg;             // 习惯达成（平均）
        public List<Integer> habitRates; // 与 habits 入参同序
        public int taskDone, taskTotal, taskRate;
        public int driftCount, driftScore;
        public int index;
    }

    public static WeekStats computeWeek(Context c, JSONObject root, List<HabitData.Habit> habits,
                                        List<TaskItem> tasks, String monday) {
        WeekStats s = new WeekStats();
        s.monday = monday;
        String sunday = DayUtil.addDays(monday, 6);
        s.habitRates = new ArrayList<>();
        long sum = 0;
        for (HabitData.Habit h : habits) {
            int r = habitWeek(root, h, monday).rate;
            s.habitRates.add(r);
            sum += r;
        }
        s.habitAvg = habits.isEmpty() ? 100 : (int) Math.round(sum * 1.0 / habits.size());
        int[] tc = taskCompletion(c, tasks, monday, sunday);
        s.taskDone = tc[0];
        s.taskTotal = tc[1];
        s.taskRate = s.taskTotal == 0 ? 100 : (int) Math.round(s.taskDone * 100.0 / s.taskTotal);
        s.driftCount = driftCount(c, monday, sunday);
        s.driftScore = driftScore(s.driftCount);
        s.index = (int) Math.round(s.habitAvg * W_HABIT + s.taskRate * W_TASK
                + s.driftScore * W_DRIFT);
        return s;
    }

    // ---------------- 日指数（晨间小报） ----------------
    public static final class DayStats {
        public int habitRate;      // -1 表示当日无可计的习惯记录
        public int taskDone, taskTotal, taskRate;
        public int driftCount, driftScore;
        public int index;
        public final List<String> misses = new ArrayList<>(); // 漏项（未完成到期任务/没记的习惯）
        public final List<String> states = new ArrayList<>(); // 昨日自检状态词（去重保序）
    }

    public static DayStats computeDay(Context c, JSONObject root, List<HabitData.Habit> habits,
                                      List<TaskItem> tasks, String date) {
        DayStats s = new DayStats();
        long sum = 0;
        int cnt = 0;
        for (HabitData.Habit h : habits) {
            Integer r = habitDayRate(root, h, date);
            if (r == null) {
                s.misses.add(h.name);
            } else {
                sum += r;
                cnt++;
            }
        }
        s.habitRate = cnt == 0 ? -1 : (int) Math.round(sum * 1.0 / cnt);
        int[] tc = taskCompletion(c, tasks, date, date);
        s.taskDone = tc[0];
        s.taskTotal = tc[1];
        s.taskRate = s.taskTotal == 0 ? 100 : (int) Math.round(s.taskDone * 100.0 / s.taskTotal);
        // 漏项里补未完成的到期任务（排在习惯漏记前面展示时由页面决定顺序）
        for (TaskItem t : tasks) {
            if (t == null || t.deleted) continue;
            boolean due = "daily".equals(t.repeat)
                    ? !(t.doneDates != null && t.doneDates.contains(date))
                    : (date.equals(t.deadline) && !"done".equals(t.status));
            if (due && ("daily".equals(t.repeat) || date.equals(t.deadline))) {
                s.misses.add(0, t.title);
            }
        }
        s.driftCount = driftCount(c, date, date);
        s.driftScore = driftScore(s.driftCount);
        int habitForIndex = s.habitRate < 0 ? 100 : s.habitRate;
        s.index = (int) Math.round(habitForIndex * W_HABIT + s.taskRate * W_TASK
                + s.driftScore * W_DRIFT);
        // 昨日自检状态词
        try {
            long lo = DayUtil.dayStartMs(date);
            long hi = DayUtil.dayStartMs(DayUtil.addDays(date, 1));
            JSONArray sc = SelfChecks.loadArray(c);
            for (int i = 0; i < sc.length(); i++) {
                JSONObject e = sc.optJSONObject(i);
                if (e == null) continue;
                long ts = e.optLong("ts", 0);
                if (ts >= lo && ts < hi) {
                    String st = e.optString("state", "");
                    if (!st.isEmpty() && !s.states.contains(st)) s.states.add(st);
                }
            }
        } catch (Exception ignored) { }
        return s;
    }
}
