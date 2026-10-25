package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 结束页催促闹钟到点（v1.21.1）：转交 EndNag 处理（校验结束仍待确认→演一轮 staging→排下一轮）。 */
public class EndNagReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && EndNag.ACTION.equals(intent.getAction())) {
            EndNag.onAlarmFired(context.getApplicationContext());
        }
    }
}
