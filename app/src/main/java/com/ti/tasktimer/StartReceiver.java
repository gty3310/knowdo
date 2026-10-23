package com.ti.tasktimer;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/**
 * 开始提醒到点：先排下一个闹钟，再双路径拉起提醒页——
 * 亮屏未锁且有悬浮权限直接开页，否则全屏通知（与 ResumeReceiver 同法）。
 * 任务已删/已完成/正在计时、或时间已被改到更远的未来，都直接跳过不弹页。
 */
public class StartReceiver extends BroadcastReceiver {
    private static final int REQ_PAGE = 780;
    private static final int NOTIF_ID = 7790;

    @Override
    public void onReceive(Context c, Intent intent) {
        String taskId = intent == null ? null : intent.getStringExtra(StartReminder.EXTRA_TASK_ID);
        TaskStore store = new TaskStore(c);
        TaskItem t = taskId == null ? null : store.byId(taskId);
        // 无论弹不弹页，先把下一个开始提醒排上（刚触发的这个时间已过，扫描会自动跳过它）
        StartReminder.reschedule(c, store);
        if (t == null || t.deleted) return;
        if (t.isDoneToday()) {
            TimerState.logEvent(c, "开始提醒跳过（今天已完成）：「" + t.title + "」");
            return;
        }
        if (TimerState.isRunning(c) && t.id.equals(TimerState.taskId(c))) {
            TimerState.logEvent(c, "开始提醒跳过（正在计时）：「" + t.title + "」");
            return;
        }
        long ms = t.nextStartMs();
        if (ms > System.currentTimeMillis() + 90_000L) {
            // 时间已被改到更远的未来：reschedule 已按新时间重排，这次不弹
            TimerState.logEvent(c, "开始提醒跳过（时间已改后）：「" + t.title + "」");
            return;
        }
        TimerState.logEvent(c, "开始提醒到点：「" + t.title + "」");
        AlertHelper.createChannels(c);
        Intent page = new Intent(c, StartRemindActivity.class);
        page.putExtra(StartReminder.EXTRA_TASK_ID, t.id);
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
        PendingIntent pi = PendingIntent.getActivity(c, REQ_PAGE, page,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, AlertHelper.CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ 到时间了")
                .setContentText("开始「" + t.title + "」吧")
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, n);
    }
}
