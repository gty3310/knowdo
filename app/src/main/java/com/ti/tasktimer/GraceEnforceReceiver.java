package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 宽限到点闹钟（v1.21.3）：转交 GraceEnforcer，再由 VerdictEngine.applyEnforcement 复查前台并按所选方式处理。 */
public class GraceEnforceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && GraceEnforcer.ACTION.equals(intent.getAction())) {
            GraceEnforcer.onAlarmFired(context.getApplicationContext());
        }
    }
}
