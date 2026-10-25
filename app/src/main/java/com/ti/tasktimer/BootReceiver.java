package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机自启实时同步（开关开着，且已选过桥文件或已进入 Supabase 模式时）。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (SyncManager.realtimeEnabled(context)
                    && (SyncManager.getUri(context) != null || SyncManager.isSupabaseMode(context))) {
                SyncService.start(context);
            }
            if (FloatingWindow.enabled(context)) FloatService.start(context);
            // 开始提醒：开机后把最近的一个 nextStartAt 闹钟重新排上（闹钟不跨重启保留）
            try { StartReminder.reschedule(context, new TaskStore(context)); } catch (Exception ignored) { }
            // 任务闹钟同理：开机后按本地任务逐个重排（v1.9.0）
            try { AlarmScheduler.rescheduleAll(context, new TaskStore(context)); } catch (Exception ignored) { }
            // 空档催促：开机后按持久化状态续排（v1.14.0）；悬浮窗 snooze 截止时刻是
            // 持久化的，FloatService 起来后由 FloatSnooze.isActive/poll 照算，无需这里处理
            try { IdleAlarm.ensureScheduled(context); } catch (Exception ignored) { }
            // 习惯提醒：开机后按当前习惯定义全量续排（v1.16.0，闹钟不跨重启保留）
            try { HabitReminder.rescheduleAll(context); } catch (Exception ignored) { }
            // 晨间页「暂停一会儿」推后闹钟：开机后按持久化截止时刻续排（v1.17.0；
            // 已过点则 1 秒后补响，由 MorningReceiver 再核对晨间是否仍未完成）
            try { MorningGate.ensureScheduled(context); } catch (Exception ignored) { }
        }
    }
}
