package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

/**
 * 静音暂停的到点提醒链（v1.11.0）：到点不震动、不出声、不弹全屏——
 * 只点亮屏幕一次＋发一条普通通知；用户无反馈则每隔「提示页重弹间隔」（PromptRepop，v1.26.3；原固定 5 分钟）重复（亮屏＋通知），
 * 直到用户点开叫回页处理（或暂停被恢复/清除）为止。与普通叫回路径互斥。
 */
public final class SilentResume {
    public static final String ACTION = "com.ti.tasktimer.SILENT_NUDGE";
    private static final int REQ = 780;
    /** 再次提醒间隔：v1.26.3 起读 PromptRepop（提示页重弹间隔），原固定 5 分钟。 */
    private static long intervalMs(Context c) { return PromptRepop.millis(c); }

    private SilentResume() {}

    private static PendingIntent intent(Context c) {
        Intent i = new Intent(c, SilentNudgeReceiver.class);
        i.setAction(ACTION);
        return PendingIntent.getBroadcast(c, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 到点入口：亮屏＋通知＋排下一次（一轮重弹间隔后），链状态记在 TimerState。 */
    public static void onDue(Context c) {
        TimerState.setSilentNudge(c, TimerState.pausedTaskId(c));
        fire(c);
        scheduleNext(c);
        TimerState.logEvent(c, "静音暂停到点：只亮屏＋通知（每隔重弹间隔重复，直到处理）");
    }

    /** 亮屏一次＋通知一次（链的每一拍都走这里）。 */
    public static void fire(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "knowdo:silent-resume");
                wl.acquire(5_000L);
            }
        } catch (Exception ignored) { }
        AlertHelper.notifySilentResume(c, TimerState.pausedTitle(c));
    }

    public static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = System.currentTimeMillis() + intervalMs(c);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(c));
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(c));
    }

    /** 用户已处理（点开叫回页/恢复/换任务）或暂停已清除：停链并撤通知。 */
    public static void cancel(Context c) {
        TimerState.clearSilentNudge(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(intent(c));
        AlertHelper.cancelSilentResume(c);
    }
}
