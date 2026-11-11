package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 盖住到点闹钟（v1.80.0）：转交 BlockEnforcer，到点清掉盖住状态。 */
public class BlockEnforceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && BlockEnforcer.ACTION.equals(intent.getAction())) {
            BlockEnforcer.onAlarmFired(context.getApplicationContext());
        }
    }
}
