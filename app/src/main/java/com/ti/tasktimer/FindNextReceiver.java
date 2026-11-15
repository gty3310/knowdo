package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 「找下一个任务」3 分钟倒计时到点：转交 FindNextTimer 拉起选任务页。 */
public class FindNextReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && FindNextTimer.ACTION.equals(intent.getAction())) {
            FindNextTimer.onAlarmFired(context.getApplicationContext());
        }
    }
}
