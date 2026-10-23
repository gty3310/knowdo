package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机自启实时同步（开关开着且已选过桥文件时）。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && SyncManager.realtimeEnabled(context)
                && SyncManager.getUri(context) != null) {
            SyncService.start(context);
        }
    }
}
