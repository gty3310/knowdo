package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 暂停叫回的闹铃升级调度（v1.11.0，普通模式）：
 * 叫回页弹出时武装一个 +60 秒的精确闹钟；用户在叫回页有任何反馈（继续/再歇/换任务）
 * 就取消并停铃；60 秒到了仍无反馈，PauseEscalationReceiver 起 60 秒渐强闹铃（只一轮）。
 */
public final class PauseEscalation {
    public static final String ACTION = "com.ti.tasktimer.PAUSE_ESCALATE";
    private static final int REQ = 779;
    public static final long DELAY_MS = 60_000L;

    private PauseEscalation() {}

    private static PendingIntent intent(Context c) {
        Intent i = new Intent(c, PauseEscalationReceiver.class);
        i.setAction(ACTION);
        return PendingIntent.getBroadcast(c, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void arm(Context c, String taskId) {
        TimerState.armEscalation(c, taskId, System.currentTimeMillis() + DELAY_MS);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = System.currentTimeMillis() + DELAY_MS;
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(c));
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(c));
    }

    /** 用户有反馈（或暂停已结束）：撤掉升级并停掉可能已响起的闹铃。 */
    public static void cancel(Context c) {
        TimerState.clearEscalation(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(intent(c));
        AlarmRing.stop(c);
    }
}
