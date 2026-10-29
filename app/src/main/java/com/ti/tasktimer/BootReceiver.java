package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 开机自启实时同步（已登录云端时；v1.26.0 起同步只有 Supabase 一条路）。 */
public class BootReceiver extends BroadcastReceiver {
    private static final String ACTION_BOOT_SYNC = "com.ti.tasktimer.BOOT_SYNC";
    private static final int REQ_BOOT_SYNC = 781;
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (SyncManager.realtimeEnabled(context) && Supa.isLoggedIn(context)) {
                startSyncAfterBoot(context);
            }
            if (FloatingWindow.enabled(context)) FloatService.start(context);
            // 开始提醒：开机后把最近的一个 nextStartAt 闹钟重新排上（闹钟不跨重启保留）
            try { StartReminder.reschedule(context, new TaskStore(context)); } catch (Exception ignored) { }
            // 任务闹钟同理：开机后按本地任务逐个重排（v1.9.0）
            try { AlarmScheduler.rescheduleAll(context, new TaskStore(context)); } catch (Exception ignored) { }
            // 空档催促：开机后按持久化状态续排（v1.14.0）；悬浮窗 snooze 截止时刻是
            // 持久化的，FloatService 起来后由 FloatSnooze.isActive/poll 照算，无需这里处理
            try { IdleAlarm.ensureScheduled(context); } catch (Exception ignored) { }
            // 结束页催促：开机后按持久化状态续排（v1.21.1；结束已被处置时 EndNag 自己会清）
            try { EndNag.ensureScheduled(context); } catch (Exception ignored) { }
            // 宽限到点处理：开机后按持久化宽限状态续排（v1.21.3；已过点则补触发，由 applyEnforcement 复查前台）
            try { GraceEnforcer.ensureScheduled(context); } catch (Exception ignored) { }
            // 习惯提醒：开机后按当前习惯定义全量续排（v1.16.0，闹钟不跨重启保留）
            try { HabitReminder.rescheduleAll(context); } catch (Exception ignored) { }
            // 晨间页「暂停一会儿」推后闹钟：开机后按持久化截止时刻续排（v1.17.0；
            // 已过点则 1 秒后补响，由 MorningReceiver 再核对晨间是否仍未完成）
            try { MorningGate.ensureScheduled(context); } catch (Exception ignored) { }
        }
    }

    /**
     * 开机路径的同步启动（v1.37.0）：还在延后期内 → 约 BootSyncReceiver 闹钟
     * 到点启动（精确闹钟可用则精确、否则非精确，与其他提醒同一口径；
     * 闹钟排不上则回落立即启动，不丢同步）；已过延后期 → 立即启动。
     * 用户主动路径（MainActivity/AccountUi）不走这里、照常立即启动。
     */
    private static void startSyncAfterBoot(Context context) {
        long delay = BootGrace.remainingSyncDelayMs();
        if (delay <= 0) { SyncService.start(context); return; }
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) { SyncService.start(context); return; }
            Intent i = new Intent(context, BootSyncReceiver.class);
            i.setAction(ACTION_BOOT_SYNC);
            PendingIntent pi = PendingIntent.getBroadcast(context, REQ_BOOT_SYNC, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long at = System.currentTimeMillis() + delay;
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (Exception e) {
            SyncService.start(context);
        }
    }
}
