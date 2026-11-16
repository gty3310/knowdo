package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 暂停 30 秒无下一步：精确闹钟到点，转交给 PauseLock 盖屏。 */
public class PauseLockReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!PauseLock.ACTION.equals(intent == null ? null : intent.getAction())) return;
        PauseLock.onAlarmFired(c);
    }
}
