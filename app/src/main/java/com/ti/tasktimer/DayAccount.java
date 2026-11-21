package com.ti.tasktimer;

import android.content.Context;

/**
 * 今日时间账（v1.23.0，学 Sunsama 的「先看今天装不装得下」）：三个数——
 * 今日还剩（现在到 22:30）、必干占（今日必做未完成任务的 duration 合计 ×1.5，
 * 用户的 50% 缓冲规矩，口径与 Jarvis 晚间时间账一致：priority=must 且今日未完成，
 * 不分 deadline 远近）、空余（还剩 − 必干占，可为负＝超了）。
 * v1.26.2 另加一笔「到期」小账：今天到期（deadline ≤ 逻辑今天）的非必做
 * （重要/次要，休息不进账——既有口径）未完成件数与 ×1.5 占用。它只作
 * 提示、不并入主账：主账仍 must-only，防档位通胀把账冲淡（用户口径）。
 * 纯计算、无 IO，任务页置顶行与聊天上下文共用这一份口径。
 * v1.92.0 追加：Result 新增 mustRemainRawMin（必做未完成所剩原时长：扣今日
 * 已用、不 ×1.5），由带 Context 的 compute 重载算（要读 SessionLog 的今日
 * 已用，故该重载不是纯计算），行页页头专用；无 Context 的旧 compute 里该
 * 字段回退为 mustRawMin。主账口径不变。
 */
public final class DayAccount {
    /** 今日截止线 22:30（用户作息规矩，与排程上限同一条线）。 */
    public static final int DAY_END_MIN = 22 * 60 + 30;

    private DayAccount() { }

    public static final class Result {
        public int remainMin;      // 今日还剩（分钟，≥0）
        public int mustRawMin;     // 必干原始时长合计
        public int mustBufferedMin;// 必干占（×1.5 向上取整）
        public int mustRemainRawMin; // 必做未完成所剩原时长（扣今日已用、不 ×1.5；v1.92.0，行页页头用）
        public int dueCount;       // 今天到期（非必做）未完成件数（v1.26.2）
        public int dueRawMin;      // 到期原始时长合计
        public int dueBufferedMin; // 到期占（×1.5 向上取整）
        public int freeMin;        // 空余（可为负）
        public boolean over() { return freeMin < 0; }
    }

    public static int nowMinutes() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
    }

    public static Result compute(TaskStore store) {
        return compute(store, DayUtil.today());
    }

    /** today＝逻辑今天（到期判定口径与 Rollover 同一条日界）。 */
    public static Result compute(TaskStore store, String today) {
        Result r = new Result();
        r.remainMin = Math.max(0, DAY_END_MIN - nowMinutes());
        int sum = 0;
        int dueSum = 0;
        if (store != null) {
            for (TaskItem t : store.tasks) {
                if (t.deleted) continue;
                if (t.isAi()) continue; // 给 AI 做的任务不占用户的时间账（v1.74.0，主账与到期小账都不进）
                if (t.isSkippedOn(today)) continue; // 跳过今天：不计今日应做时长（v1.79.0，主账与到期小账都不进）
                if (t.isDoneToday()) continue;
                if ("must".equals(t.priority)) {
                    sum += Math.max(1, t.durationMinutes);
                    continue;
                }
                // 到期小账：重要/次要且 deadline ≤ 逻辑今天；休息永不进账
                int rank = TaskItem.priorityRank(t.priority);
                if ((rank == 1 || rank == 2) && t.deadline != null
                        && today != null && t.deadline.compareTo(today) <= 0) {
                    r.dueCount++;
                    dueSum += Math.max(1, t.durationMinutes);
                }
            }
        }
        r.mustRawMin = sum;
        r.mustRemainRawMin = sum; // 无 Context 拿不到今日已用，回退＝mustRawMin（v1.92.0）
        r.mustBufferedMin = buffered(sum);
        r.dueRawMin = dueSum;
        r.dueBufferedMin = buffered(dueSum);
        r.freeMin = r.remainMin - r.mustBufferedMin;
        return r;
    }

    /**
     * 带 Context 的版本（v1.92.0）：与旧 compute 同一份结果，另多算
     * mustRemainRawMin——任务集合与 mustRawMin 完全相同（未删、非 AI、今日
     * 未 skip、未 doneToday、priority=must），逐件按「duration − 今日已用」
     * 取所剩原时长（下限 0、不 ×1.5）。只有行页页头用这个数展示；其余字段
     * 与旧口径逐字一致，其他调用方不受影响。
     */
    public static Result compute(Context ctx, TaskStore store, String today) {
        Result r = compute(store, today);
        int sum = 0;
        if (ctx != null && store != null) {
            for (TaskItem t : store.tasks) {
                if (t.deleted) continue;
                if (t.isAi()) continue;
                if (t.isSkippedOn(today)) continue;
                if (t.isDoneToday()) continue;
                if ("must".equals(t.priority)) {
                    sum += Math.max(0, t.durationMinutes
                            - (int) (SessionLog.todayMsForTask(ctx, t.id) / 60000));
                }
            }
        }
        r.mustRemainRawMin = sum;
        return r;
    }

    /** ×1.5 缓冲（向上取整到整分钟）。 */
    public static int buffered(int minutes) {
        return (minutes * 3 + 1) / 2;
    }

    /** 时长大白话：≥1 小时「5 小时 20 分」／「5 h 20 min」，不足 1 小时「45 分钟」／「45 min」。 */
    public static String fmtDur(int minutes) {
        int m = Math.max(0, minutes);
        int h = m / 60, mm = m % 60;
        if (Strings.en()) {
            if (h > 0) return mm > 0 ? h + " h " + mm + " min" : h + " h";
            return m + " min";
        }
        if (h > 0) return mm > 0 ? h + " 小时 " + mm + " 分" : h + " 小时";
        return m + " 分钟";
    }
}
