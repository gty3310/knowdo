package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 暂停叫回 +60 秒：仍无反馈（暂停槽还在、没恢复计时）→ 起渐强闹铃，只响这一轮。 */
public class PauseEscalationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!PauseEscalation.ACTION.equals(intent.getAction())) return;
        String taskId = TimerState.escalationTaskId(c);
        TimerState.clearEscalation(c); // 一次性，响不响都撤
        if (taskId.isEmpty()) return;
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) return;
        if (!taskId.equals(TimerState.pausedTaskId(c))) return;
        AlarmRing.start(c);
    }
}
