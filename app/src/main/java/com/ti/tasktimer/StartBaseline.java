package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 开始抵扣的时间基线（v1.21.1）：按任务记两个本机时间戳——上次被标记完成的时刻
 * 与上次被「重置计时」的时刻。用户本地再开始一个任务时，今天已用只算这两个时刻
 * 之后（取较晚者）的分段（见 SessionLog.todayMsForTaskSince）：任务被标记完成后
 * 再开始（每日重复次日、或当天重做）必须从完整时长开始；「重置」是从零开始的
 * 明确语义，同样清掉抵扣基数。
 * 写入点只有两类本地写路径：各完成入口在 markDone/markUndone 旁打戳/清戳，
 * 各重置入口在开始前打戳。只存本机 prefs、不进同步（与 SessionLog 同口径）；
 * 跨设备在别台机器上完成的任务，本机没有戳——按本机分段口径走（已知边界）。
 */
public final class StartBaseline {
    private static final String PREF = "start_baseline";

    private StartBaseline() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 任务刚被标记完成：此后抵扣基数从这一刻重新算。 */
    public static void markCompleted(Context c, String taskId) {
        if (taskId == null || taskId.isEmpty()) return;
        sp(c).edit().putLong("done_" + taskId, System.currentTimeMillis()).apply();
    }

    /** 完成标记被撤销：完成戳一并清掉，回到「接着计」的口径。 */
    public static void clearCompleted(Context c, String taskId) {
        if (taskId == null || taskId.isEmpty()) return;
        sp(c).edit().remove("done_" + taskId).apply();
    }

    /** 任务刚被「重置计时」：抵扣基数从这一刻重新算（与重置后立即开始的起表同刻，互不干扰）。 */
    public static void markReset(Context c, String taskId) {
        if (taskId == null || taskId.isEmpty()) return;
        sp(c).edit().putLong("reset_" + taskId, System.currentTimeMillis()).apply();
    }

    /** 抵扣基数下限＝完成戳与重置戳的较晚者；都没有返回 0（＝今天从头算）。 */
    public static long sinceMs(Context c, String taskId) {
        if (taskId == null || taskId.isEmpty()) return 0;
        SharedPreferences p = sp(c);
        return Math.max(p.getLong("done_" + taskId, 0), p.getLong("reset_" + taskId, 0));
    }
}
