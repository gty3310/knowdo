package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** AlarmManager 精确闹钟的接收端：即使计时服务进程不在了，系统也会拉起这里发全屏提醒。 */
public class AlertReceiver extends BroadcastReceiver {
    public static final String ACTION_ALARM = "com.ti.tasktimer.ALARM";
    public static final String EXTRA_TYPE = "type";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_ALARM.equals(intent.getAction())) return;
        int type = intent.getIntExtra(EXTRA_TYPE, AlertHelper.TYPE_END);
        TimerService.onAlarmFired(context, type);
    }
}
