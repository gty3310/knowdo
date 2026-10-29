package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机同步延后的到点出口（v1.37.0）：BootReceiver 在开机延后期内不立即拉
 * SyncService，改约这一发闹钟；到点按开机时同一组条件（实时同步开＋已登录）
 * 再核对一次才启动。SyncService.start 幂等（onStartCommand 有 running 守卫），
 * 用户在延后期里已经打开知行、同步已启动时，这一发只是无害的空转。
 */
public class BootSyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (SyncManager.realtimeEnabled(c) && Supa.isLoggedIn(c)) {
            SyncService.start(c);
        }
    }
}
