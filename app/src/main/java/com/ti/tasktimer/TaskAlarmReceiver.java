package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 任务闹钟到点：先重排（每日任务自动续排下一天；一次性闹钟的时间已过不会再排），
 * 再做有效性检查（任务没了/闹钟被清/今天已完成/一次性闹钟被改到更远的未来，都不响），
 * 通过后交给 AlertHelper 双路径拉起响铃页（与开始提醒同一策略）。
 */
public class TaskAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String taskId = intent == null ? null : intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID);
        TaskStore store = new TaskStore(c);
        TaskItem t = taskId == null ? null : store.byId(taskId);
        AlarmScheduler.rescheduleAll(c, store);
        if (t == null || t.deleted || t.alarmMs() == 0) return;
        if (t.isDoneToday()) {
            TimerState.logEvent(c, "任务闹钟跳过（今天已完成）：「" + t.title + "」");
            return;
        }
        if (!"daily".equals(t.repeat)
                && t.alarmMs() > System.currentTimeMillis() + 90_000L) {
            // 一次性闹钟被改到更远的未来：rescheduleAll 已按新时间重排，这次不响
            TimerState.logEvent(c, "任务闹钟跳过（时间已改后）：「" + t.title + "」");
            return;
        }
        TimerState.logEvent(c, "任务闹钟到点：「" + t.title + "」");
        AlertHelper.fireTaskAlarm(c, t.id, t.title, false);
    }
}
