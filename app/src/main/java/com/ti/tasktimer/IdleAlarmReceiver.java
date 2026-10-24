package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 空档催促闹钟到点（v1.14.0）：转交 IdleAlarm 处理（弹守卫页＋按档出声＋排下一轮）。 */
public class IdleAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && IdleAlarm.ACTION.equals(intent.getAction())) {
            IdleAlarm.onAlarmFired(context.getApplicationContext());
        }
    }
}
