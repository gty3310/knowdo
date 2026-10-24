package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 暂停提醒：暂停任务时约好「多久后叫我回来」，到点由 ResumeReceiver 全屏叫回。
 * 时间选项与悬浮窗暂停菜单、提醒页「再歇一会」共用同一组。
 */
public class ResumeReminder {
    public static final String[] LABELS = {
            "1 分钟", "3 分钟", "5 分钟", "10 分钟", "15 分钟",
            "20 分钟", "半个小时", "60 分钟", "2 个小时"};
    public static final long[] DELAYS_MS = {
            60_000L, 180_000L, 300_000L, 600_000L, 900_000L,
            1_200_000L, 1_800_000L, 3_600_000L, 7_200_000L};

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, ResumeReceiver.class);
        i.setAction("com.ti.tasktimer.RESUME_REMIND");
        return PendingIntent.getBroadcast(c, 777, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c, String taskId, long delayMs) {
        long at = System.currentTimeMillis() + Math.max(1_000L, delayMs);
        TimerState.setRemind(c, taskId, at);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(c));
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(c));
        TimerState.logEvent(c, "暂停提醒已设：" + (delayMs / 60000) + " 分钟后叫回「"
                + TimerState.pausedTitle(c) + "」");
    }

    public static void cancel(Context c) {
        TimerState.clearRemind(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarmIntent(c));
        // v1.11.0：叫回的升级闹铃与静音亮屏链都挂在同一次叫回上，暂停被恢复/开始新任务时一并撤
        PauseEscalation.cancel(c);
        SilentResume.cancel(c);
    }
}
