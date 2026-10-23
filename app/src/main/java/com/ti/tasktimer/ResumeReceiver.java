package com.ti.tasktimer;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

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
        TimerState.clearRemind(c); // 一次性
        TimerState.logEvent(c, "暂停提醒到点：叫回「" + TimerState.pausedTitle(c) + "」");
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
}
