package com.ti.tasktimer;

/**
 * 今日时间账（v1.23.0，学 Sunsama 的「先看今天装不装得下」）：三个数——
 * 今日还剩（现在到 22:30）、必干占（今日必做未完成任务的 duration 合计 ×1.5，
 * 用户的 50% 缓冲规矩，口径与 Jarvis 晚间时间账一致：priority=must 且今日未完成，
 * 不分 deadline 远近）、空余（还剩 − 必干占，可为负＝超了）。
 * 纯计算、无 IO，任务页置顶行与聊天上下文共用这一份口径。
 */
public final class DayAccount {
    /** 今日截止线 22:30（用户作息规矩，与排程上限同一条线）。 */
    public static final int DAY_END_MIN = 22 * 60 + 30;

    private DayAccount() { }

    public static final class Result {
        public int remainMin;      // 今日还剩（分钟，≥0）
        public int mustRawMin;     // 必干原始时长合计
        public int mustBufferedMin;// 必干占（×1.5 向上取整）
        public int freeMin;        // 空余（可为负）
        public boolean over() { return freeMin < 0; }
    }

    public static int nowMinutes() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
    }

    public static Result compute(TaskStore store) {
        Result r = new Result();
        r.remainMin = Math.max(0, DAY_END_MIN - nowMinutes());
        int sum = 0;
        if (store != null) {
            for (TaskItem t : store.tasks) {
                if (t.deleted) continue;
                if (!"must".equals(t.priority)) continue;
                if (t.isDoneToday()) continue;
                sum += Math.max(1, t.durationMinutes);
            }
        }
        r.mustRawMin = sum;
        r.mustBufferedMin = buffered(sum);
        r.freeMin = r.remainMin - r.mustBufferedMin;
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
