package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 静音暂停提醒链的每一拍：暂停还在且仍是静音 → 再亮屏＋通知一次，并排下一拍。 */
public class SilentNudgeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!SilentResume.ACTION.equals(intent.getAction())) return;
        if (!TimerState.silentNudgeActive(c)) return;
        if (!TimerState.isPaused(c) || TimerState.isRunning(c) || !TimerState.pauseSilent(c)) {
            SilentResume.cancel(c);
            return;
        }
        if (!TimerState.silentNudgeTaskId(c).equals(TimerState.pausedTaskId(c))) {
            SilentResume.cancel(c);
            return;
        }
        SilentResume.fire(c);
        SilentResume.scheduleNext(c);
    }
}
