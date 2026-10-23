package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 开始提醒调度：每个任务可设 nextStartAt（下一个开始时间），这里永远只排最近的一个闹钟。
 * 到点由 StartReceiver 双路径拉起 StartRemindActivity 全屏提醒开始；触发后 Receiver 会再调
 * reschedule 排下一个。单槽设计与 ResumeReminder 同法：固定 action + requestCode，
 * FLAG_UPDATE_CURRENT 保证换任务时 extras 跟着更新、cancel 用任意 taskId 都能撤。
 * 调用点：MainActivity 数据变化/回到前台、SyncManager.syncNow 合并后、BootReceiver 开机后。
 */
public class StartReminder {
    public static final String ACTION = "com.ti.tasktimer.START_REMIND";
    public static final String EXTRA_TASK_ID = "task_id";
    private static final int REQ_ALARM = 779;

    private static PendingIntent alarmIntent(Context c, String taskId) {
        Intent i = new Intent(c, StartReceiver.class);
        i.setAction(ACTION);
        i.putExtra(EXTRA_TASK_ID, taskId);
        return PendingIntent.getBroadcast(c, REQ_ALARM, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 扫描全部任务，把最近的一个未来 nextStartAt 设成闹钟；没有就撤掉旧闹钟。 */
    public static synchronized void reschedule(Context c, TaskStore store) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try { am.cancel(alarmIntent(c, "")); } catch (Exception ignored) { }
        long now = System.currentTimeMillis();
        TaskItem best = null;
        long bestMs = Long.MAX_VALUE;
        for (TaskItem t : store.tasks) {
            if (t.deleted || t.isDoneToday()) continue;
            long ms = t.nextStartMs();
            if (ms > now && ms < bestMs) {
                bestMs = ms;
                best = t;
            }
        }
        if (best == null) return;
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        PendingIntent pi = alarmIntent(c, best.id);
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bestMs, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, bestMs, pi);
        TimerState.logEvent(c, "开始提醒已排：「" + best.title + "」于 "
                + new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(bestMs)));
    }

    public static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            try { am.cancel(alarmIntent(c, "")); } catch (Exception ignored) { }
        }
    }
}
