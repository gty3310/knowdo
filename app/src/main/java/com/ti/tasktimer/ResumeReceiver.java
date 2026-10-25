package com.ti.tasktimer;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** 暂停提醒到点：双路径叫回——亮屏未锁且有悬浮权限直接开页，否则全屏通知（与计时提醒同法）。 */
public class ResumeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) {
            TimerState.clearRemind(c);
            return;
        }
        String taskId = TimerState.remindTaskId(c);
        if (!taskId.isEmpty() && !taskId.equals(TimerState.pausedTaskId(c))) {
            TimerState.clearRemind(c);
            return;
        }
        long dueAt = TimerState.remindAt(c); // 清零前先记下到期时刻（空档起算点，v1.19.0）
        TimerState.clearRemind(c); // 一次性
        // 静音暂停（v1.11.0）：不弹全屏、不震动、不出声——只亮屏＋通知，之后每 5 分钟重复
        if (TimerState.pauseSilent(c)) {
            SilentResume.onDue(c);
            return;
        }
        // 普通暂停到点（v1.19.0 根因修复）：置「暂停已到期」标记——暂停槽保留（任务还能
        // 继续），但 IdleGuard 从此按空档处理，到期＋阈值后进入空档催促 staging；
        // 锚点就是到期时刻。静音暂停例外：不置位、不进出声 staging（上面已分流）。
        TimerState.setPauseExpired(c, dueAt > 0 ? dueAt : System.currentTimeMillis());
        TimerState.logEvent(c, "暂停提醒到点：叫回「" + TimerState.pausedTitle(c) + "」");
        // 普通模式：到点先短震一次，并武装 60 秒无反馈的渐强闹铃升级
        vibrateShort(c);
        PauseEscalation.arm(c, taskId);
        AlertHelper.createChannels(c);
        Intent page = new Intent(c, ResumeRemindActivity.class);
        page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = km != null && km.isKeyguardLocked();
        if (interactive && !locked && AlertHelper.canDrawOverlays(c)) {
            try {
                c.startActivity(page);
                return;
            } catch (Exception ignored) { }
        }
        PendingIntent pi = PendingIntent.getActivity(c, 778, page,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, AlertHelper.CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ 休息时间到")
                .setContentText("该继续「" + TimerState.pausedTitle(c) + "」了")
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(7780, n);
    }

    /** 到点短震一次：一小段节奏（约 0.8 秒），不长震（v1.11.0）。 */
    private void vibrateShort(Context c) {
        try {
            Vibrator vib = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null) {
                vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 150, 300}, -1));
            }
        } catch (Exception ignored) { }
    }
}
