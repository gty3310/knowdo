package com.ti.tasktimer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/** 习惯提醒接收器（v1.16.0）：到点发一条本地通知，点开进知行习惯页；随后全量续排。 */
public class HabitReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String habitId = intent == null ? "" : intent.getStringExtra(HabitReminder.EXTRA_HABIT_ID);
            JSONObject root = HabitData.load(context);
            HabitData.Habit h = HabitData.habitById(root, habitId == null ? "" : habitId);
            if (h != null && h.reminder != null) {
                AlertHelper.createChannels(context);
                Intent open = new Intent(context, MainActivity.class);
                open.putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_HABITS);
                open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent pi = PendingIntent.getActivity(context, 71000 + Math.abs(h.id.hashCode() % 5000),
                        open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification n = new Notification.Builder(context, AlertHelper.CHANNEL_HABIT)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle(h.name)
                        .setContentText(Strings.en() ? "Time to check in on this habit"
                                : "到时间记录一下这个习惯了")
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build();
                NotificationManager nm = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(81000 + Math.abs(h.id.hashCode() % 5000), n);
            }
        } catch (Exception ignored) { }
        // 续排下一天
        try { HabitReminder.rescheduleAll(context); } catch (Exception ignored) { }
    }
}
