package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * 宽限到点主动处理（v1.21.3）：旧版宽限只是 VerdictEngine 里的一对
 * grace_cat/grace_until，到期后要等采样再次命中同类才跳回——用户可能一直
 * 停在分神应用里不触发新帧，或采样间隙里到点无人处理。用户定案：选宽限
 * 时同时选到点怎么办（退掉该软件／只放到后台），到点由本类的精确闹钟
 * 主动复查前台并执行，不再等再次命中。
 * 状态仍存在 VerdictEngine 的 prefs「screenguard_state」（grace_cat／
 * grace_until／grace_pkg），本类只管排期／取消／开机续排与到点转发；
 * 真正的处理（复查包名、执行所选动作、记 GuardEvents）只有一份实现：
 * VerdictEngine.applyEnforcement，闹钟路径与「到期再命中」路径共用。
 * 排期模式仿 EndNag：AlarmManager 精确闹钟＋持久化截止时刻，进程被杀／
 * 开机后由 ensureScheduled 按持久化状态续排（已过点则 1 秒后补触发，
 * 由 applyEnforcement 再复查前台决定动不动手）。
 */
public final class GraceEnforcer {
    private static final int REQ = 784;
    public static final String ACTION = "com.ti.tasktimer.GRACE_ENFORCE";

    private GraceEnforcer() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("screenguard_state", Context.MODE_PRIVATE);
    }

    /** 选了宽限后调用：把闹钟排到 grace_until（同一 PendingIntent，重排即覆盖）。 */
    public static void schedule(Context c) {
        long until = prefs(c).getLong("grace_until", 0);
        if (until == 0) { cancel(c); return; }
        setExact(c, Math.max(until, System.currentTimeMillis() + 1_000));
    }

    public static void cancel(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(c));
        } catch (Exception ignored) { }
    }

    /** 开机／进程重启后按持久化状态续排：宽限还在就把闹钟补上，已过点立刻补触发。 */
    public static void ensureScheduled(Context c) {
        String cat = prefs(c).getString("grace_cat", null);
        long until = prefs(c).getLong("grace_until", 0);
        if (cat == null || until == 0) { cancel(c); return; }
        setExact(c, Math.max(until, System.currentTimeMillis() + 1_000));
    }

    static void onAlarmFired(Context c) {
        VerdictEngine.applyEnforcement(c);
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, GraceEnforceReceiver.class).setAction(ACTION);
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
