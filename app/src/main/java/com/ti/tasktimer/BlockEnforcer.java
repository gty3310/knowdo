package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * 盖住到点清状态（v1.80.0）：排期模式仿 GraceEnforcer——精确闹钟＋持久化
 * 截止时刻（screenguard_state 的 block_pkg／block_until）。盖屏自己的
 * 倒计时也会收尾，本类保证的是：进程被杀／开机后状态不残留——到点把
 * block 状态清掉（已过点则 ensureScheduled 补一次触发立刻清）。
 */
public final class BlockEnforcer {
    private static final int REQ = 785;
    public static final String ACTION = "com.ti.tasktimer.BLOCK_ENFORCE";

    private BlockEnforcer() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("screenguard_state", Context.MODE_PRIVATE);
    }

    /** 盖住开始后调用：把闹钟排到 block_until。 */
    public static void schedule(Context c) {
        long until = prefs(c).getLong("block_until", 0);
        if (until == 0) { cancel(c); return; }
        setExact(c, Math.max(until, System.currentTimeMillis() + 1_000));
    }

    public static void cancel(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(c));
        } catch (Exception ignored) { }
    }

    /** 开机／进程重启后按持久化状态续排：还在盖就补闹钟，已过点立刻补触发清掉。 */
    public static void ensureScheduled(Context c) {
        String pkg = prefs(c).getString("block_pkg", null);
        long until = prefs(c).getLong("block_until", 0);
        if (pkg == null || until == 0) { cancel(c); return; }
        setExact(c, Math.max(until, System.currentTimeMillis() + 1_000));
    }

    static void onAlarmFired(Context c) {
        if (prefs(c).getString("block_pkg", null) == null) return;
        TimerState.logEvent(c, "分神处置：暂停软件 3 分钟到点，已解除");
        AppBlock.clear(c);
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, BlockEnforceReceiver.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, REQ, i, flags);
    }

    private static void setExact(Context c, long at) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c));
            } catch (SecurityException se) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c));
            }
        } catch (Exception ignored) { }
    }
}
