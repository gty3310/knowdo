package com.ti.tasktimer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 健康数据纯计算层（v2.0.0）：睡眠记账口径（F3）与恢复分公式（F6）。
 * 不依赖 Android 与 org.json，输入输出都是基本类型/简单结构，
 * 可在 JVM 上直接自测（开发期用五晚真实口径数据对照过，见发版报告）。
 *
 * 记账口径（F3 定案）：
 * - 逻辑日以 dayStartMin（默认 04:00，与 HabitData 同一条日界）为界；
 *   一段睡眠归属「它结束时刻所在的逻辑日」——晨间页在 session 日 D 看到的
 *   「昨晚」就是结束于逻辑日 D 的主睡眠（与佳明五晚数据的报法一致）。
 * - 主睡眠＝该逻辑日内最长一段；其余段计午睡。
 * - 多来源去重：同一时段多来源只记一份，优先 Garmin Connect 来源；
 *   实现＝按（Garmin 优先、时长降序）贪心保留，与已保留段重叠 ≥ 自身一半
 *   的后来者丢弃。
 * - 达标：入睡不晚于目标上床时间（跨午夜按夜轴算晚点分钟），
 *   且总时长 ≥ 目标窗口（起床−上床）×90%。
 */
public final class HealthMath {
    private HealthMath() {}

    /** SleepSessionRecord.STAGE_TYPE_DEEP（connect-client 常量）。 */
    public static final int STAGE_DEEP = 5;
    /** Garmin Connect 的包名（多来源去重时优先保留它的记录）。 */
    public static final String GARMIN_PKG = "com.garmin.android.apps.connectmobile";

    public static final class Stage {
        public long startMs, endMs;
        public int type;
        public Stage(long s, long e, int t) { startMs = s; endMs = e; type = t; }
    }

    public static final class Session {
        public long startMs, endMs;
        public String source;
        public List<Stage> stages = new ArrayList<>();
        public Session(long s, long e, String src) { startMs = s; endMs = e; source = src; }
        public long durMs() { return Math.max(0, endMs - startMs); }
    }

    /** 一天的汇总（内存形态；HealthStore 负责与 JSON 互转）。 */
    public static final class Summary {
        public boolean hasSleep;
        public long sleepStartMs, wakeMs;
        public int totalMin;
        public int deepMin = -1;   // -1＝无分期数据（未知）
        public int napMin;
        public boolean met;
        public int lateMin;        // 晚于目标上床的分钟（0＝没晚）
        public int rhr = -1;       // -1＝无
        public long steps = -1;    // -1＝无
        public double kcal = -1;   // -1＝无
    }

    public static final class Recovery {
        public int score;
        public double durPts, timingPts, deepPts, rhrPts, hrvPts, actPts;
        /** 缺项键：deep / rhr / hrv / activity（权重按比例分给其余项）。 */
        public final List<String> missing = new ArrayList<>();
        public boolean baselineBuilding;
    }

    // ---------------- 时间小工具（本地时区，与 DayUtil 同口径） ----------------

    public static String dateOf(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return DayUtil.fmt(cal);
    }

    public static int minutesOfDay(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    /** 某时刻所在的逻辑日（dayStartMin 之前算前一天）。 */
    public static String logicalDateOf(long ms, int dayStartMin) {
        String d = dateOf(ms);
        return minutesOfDay(ms) < dayStartMin ? DayUtil.addDays(d, -1) : d;
    }

    /** 目标睡眠窗口（分钟）：起床−上床，跨午夜补 1440；同值按整天。 */
    public static int windowMin(int bedMin, int wakeMin) {
        int w = (wakeMin - bedMin + 1440) % 1440;
        return w == 0 ? 1440 : w;
    }

    /**
     * 入睡晚点（分钟）：在夜轴上算——比目标上床晚多少分钟。
     * startMin/wakeMin 都是墙钟分钟。恰在目标或更早（含凌晨前的傍晚）＝0；
     * 绕回超过半天视为「更早」也记 0。
     */
    public static int lateMinutes(int startMin, int bedMin) {
        int raw = (startMin - bedMin + 1440) % 1440;
        return raw > 720 ? 0 : raw;
    }

    // ---------------- F3 记账 ----------------

    /**
     * 把原始记录收成某逻辑日的一份汇总。
     * rhr：[timeMs, bpm] 列表（取该逻辑日内最晚一条）；
     * steps：[endMs, count] 列表（按 endMs 的逻辑日归属求和）；
     * kcal：[endMs, kcal] 列表（同上）。
     */
    public static Summary summarize(List<Session> sessions, List<long[]> rhr,
                                    List<long[]> steps, List<double[]> kcal,
                                    String logicalDate, int dayStartMin,
                                    int bedMin, int wakeMin) {
        Summary out = new Summary();
        // 该逻辑日的候选段（按结束时刻归属）
        List<Session> cand = new ArrayList<>();
        if (sessions != null) {
            for (Session s : sessions) {
                if (s == null || s.durMs() <= 0) continue;
                if (logicalDate.equals(logicalDateOf(s.endMs, dayStartMin))) cand.add(s);
            }
        }
        // 去重：Garmin 优先、长段优先，贪心保留
        cand.sort((a, b) -> {
            boolean ga = GARMIN_PKG.equals(a.source), gb = GARMIN_PKG.equals(b.source);
            if (ga != gb) return ga ? -1 : 1;
            return Long.compare(b.durMs(), a.durMs());
        });
        List<Session> kept = new ArrayList<>();
        for (Session s : cand) {
            boolean dup = false;
            for (Session k : kept) {
                long overlap = Math.min(s.endMs, k.endMs) - Math.max(s.startMs, k.startMs);
                if (overlap > 0 && overlap * 2 >= s.durMs()) { dup = true; break; }
            }
            if (!dup) kept.add(s);
        }
        Session main = null;
        long napMs = 0;
        for (Session s : kept) {
            if (main == null || s.durMs() > main.durMs()) {
                if (main != null) napMs += main.durMs();
                main = s;
            } else {
                napMs += s.durMs();
            }
        }
        if (main != null) {
            out.hasSleep = true;
            out.sleepStartMs = main.startMs;
            out.wakeMs = main.endMs;
            out.totalMin = (int) Math.round(main.durMs() / 60000.0);
            out.napMin = (int) Math.round(napMs / 60000.0);
            // 深睡：主段内 DEEP 分期（裁到主段边界）；主段无分期数据＝-1 未知
            if (main.stages != null && !main.stages.isEmpty()) {
                long deepMs = 0;
                for (Stage st : main.stages) {
                    if (st.type != STAGE_DEEP) continue;
                    long a = Math.max(st.startMs, main.startMs);
                    long b = Math.min(st.endMs, main.endMs);
                    if (b > a) deepMs += b - a;
                }
                out.deepMin = (int) Math.round(deepMs / 60000.0);
            }
            out.lateMin = lateMinutes(minutesOfDay(main.startMs), bedMin);
            int win = windowMin(bedMin, wakeMin);
            out.met = out.lateMin == 0 && out.totalMin * 10 >= win * 9;
        }
        if (rhr != null) {
            long bestT = -1;
            for (long[] e : rhr) {
                if (e == null || e.length < 2) continue;
                if (!logicalDate.equals(logicalDateOf(e[0], dayStartMin))) continue;
                if (e[0] > bestT) { bestT = e[0]; out.rhr = (int) e[1]; }
            }
        }
        if (steps != null) {
            long sum = 0;
            boolean any = false;
            for (long[] e : steps) {
                if (e == null || e.length < 2) continue;
                if (!logicalDate.equals(logicalDateOf(e[0], dayStartMin))) continue;
                sum += e[1];
                any = true;
            }
            if (any) out.steps = sum;
        }
        if (kcal != null) {
            double sum = 0;
            boolean any = false;
            for (double[] e : kcal) {
                if (e == null || e.length < 2) continue;
                if (!logicalDate.equals(logicalDateOf((long) e[0], dayStartMin))) continue;
                sum += e[1];
                any = true;
            }
            if (any) out.kcal = sum;
        }
        return out;
    }

    // ---------------- F6 恢复分 ----------------

    /**
     * 恢复分（满分 100，六项加权；缺项权重按比例分给其余项）：
     * 时长充足度 35（实际/目标窗口，封顶 1）／入睡时间点 25（不晚于目标满分，
     * 每晚 30 分钟线性扣，晚 2 小时扣完）／深睡占比 15（15%–25% 满分带，
     * 带外线性扣：低于带按比例、高于带每多 10 个百分点扣完）／
     * 静息心率 10（不高于基线+5 满分，其上每多 1 bpm 线性扣，+15 扣完）／
     * HRV 10（达到基线满分，低到基线的 70% 扣完）／昨日活动 5
     * （昨日步数达近 7 天中位数 80% 记满分，其下按比例）。
     * 无睡眠数据返回 null（不算分）。
     * rhrBase/hrvBase/stepsMedian 为 null 或当日值缺失＝该项缺项；
     * baselineDays＜7 时标 baselineBuilding（页面标「基线建立中」）。
     */
    public static Recovery recovery(Summary s, int windowMin,
                                    Double rhrBase, Integer hrvToday, Double hrvBase,
                                    long stepsYesterday, Double stepsMedian,
                                    int baselineDays) {
        if (s == null || !s.hasSleep) return null;
        Recovery r = new Recovery();
        r.baselineBuilding = baselineDays < 7;
        double earned = 0, presentMax = 0;

        double durRatio = windowMin > 0 ? Math.min(1.0, (double) s.totalMin / windowMin) : 0;
        r.durPts = 35.0 * durRatio;
        earned += r.durPts; presentMax += 35;

        r.timingPts = s.lateMin <= 0 ? 25.0 : 25.0 * Math.max(0, 1.0 - s.lateMin / 120.0);
        earned += r.timingPts; presentMax += 25;

        if (s.deepMin >= 0 && s.totalMin > 0) {
            double ratio = (double) s.deepMin / s.totalMin;
            if (ratio >= 0.15 && ratio <= 0.25) r.deepPts = 15.0;
            else if (ratio < 0.15) r.deepPts = 15.0 * ratio / 0.15;
            else r.deepPts = 15.0 * Math.max(0, 1.0 - (ratio - 0.25) / 0.10);
            earned += r.deepPts; presentMax += 15;
        } else {
            r.missing.add("deep");
        }

        if (s.rhr >= 0 && rhrBase != null) {
            double diff = s.rhr - rhrBase;
            r.rhrPts = diff <= 5 ? 10.0 : 10.0 * Math.max(0, 1.0 - (diff - 5) / 10.0);
            earned += r.rhrPts; presentMax += 10;
        } else {
            r.missing.add("rhr");
        }

        if (hrvToday != null && hrvToday > 0 && hrvBase != null && hrvBase > 0) {
            double ratio = hrvToday / hrvBase;
            r.hrvPts = ratio >= 1.0 ? 10.0 : 10.0 * Math.max(0, (ratio - 0.7) / 0.3);
            earned += r.hrvPts; presentMax += 10;
        } else {
            r.missing.add("hrv");
        }

        if (stepsYesterday >= 0 && stepsMedian != null && stepsMedian > 0) {
            double target = 0.8 * stepsMedian;
            r.actPts = stepsYesterday >= target ? 5.0 : 5.0 * stepsYesterday / target;
            earned += r.actPts; presentMax += 5;
        } else {
            r.missing.add("activity");
        }

        r.score = presentMax > 0 ? (int) Math.round(100.0 * earned / presentMax) : 0;
        return r;
    }

    // ---------------- F7 每周汇总 ----------------

    /**
     * 一周汇总（纯计算，输入与 days 顺序无关）：
     * 返回 [达标天数, 平均入睡墙钟分钟（夜轴平均，无数据 -1）, 平均总时长分钟（无数据 -1）, 有睡眠天数]。
     * 夜轴平均：以正午为 0 点折算，避免 23:50 与 00:10 被算成相差近一天。
     */
    public static int[] weekly(int[] startMins, int[] totalMins, boolean[] mets) {
        int metDays = 0, n = 0;
        long axisSum = 0, totalSum = 0;
        for (int i = 0; i < mets.length; i++) {
            if (mets[i]) metDays++;
            if (startMins[i] >= 0) {
                axisSum += (startMins[i] - 720 + 1440) % 1440;
                totalSum += totalMins[i];
                n++;
            }
        }
        int avgStart = n == 0 ? -1 : (int) Math.round((double) axisSum / n + 720) % 1440;
        int avgTotal = n == 0 ? -1 : (int) Math.round((double) totalSum / n);
        return new int[]{metDays, avgStart, avgTotal, n};
    }
}
