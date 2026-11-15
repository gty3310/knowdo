package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * 「找下一个任务」轻量倒计时（本地 prefs，不进 TimerState，不同步云端）：
 * 用户在结束检查页点「做完了」后立刻开始，固定 3 分钟，只为找下一件时给一点紧迫感。
 * 不是任务：不进任务名单、时间账、知行指数、SessionLog、排程、滚入、云端任务表。
 * 真正开始（或恢复、远端镜像）一个任务时立即取消；到点仍未开始则拉起既有「下一个任务」页，
 * 并恢复空档催促。进行中时 IdleGuard 不弹自己的选任务页，避免双重打扰。
 */
public final class FindNextTimer {
    public static final long DURATION_MS = 3 * 60_000L;
    public static final String ACTION = "com.ti.tasktimer.FIND_NEXT_TIMER";
    private static final String PREF = "find_next_timer";
    private static final int REQ = 784;

    private FindNextTimer() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static long endAt(Context c) { return sp(c).getLong("end_at", 0L); }

    /** 倒计时仍在进行（未到点、未被取消）。 */
    public static boolean isActive(Context c) {
        long end = endAt(c);
        return end > System.currentTimeMillis();
    }

    public static long remainMs(Context c) {
        return Math.max(0L, endAt(c) - System.currentTimeMillis());
    }

    /** 结束检查页点「做完了」后调用：重新计 3 分钟，并确保悬浮窗服务在跑以便显示倒计时。 */
    public static void start(Context c) {
        long end = System.currentTimeMillis() + DURATION_MS;
        sp(c).edit().putLong("end_at", end).apply();
        schedule(c, end);
        if (FloatingWindow.enabled(c)) FloatService.start(c);
        refreshFloat(c); // 立刻换成「找下一个任务」倒计时，不等 FloatService 下一秒 tick
        TimerState.logEvent(c, "找下一个任务：开始 3 分钟倒计时");
    }

    /** 开始/恢复/镜像一个真正的任务时调用；无状态时为无-op。 */
    public static void cancel(Context c) {
        boolean had = endAt(c) != 0L;
        cancelAlarm(c);
        sp(c).edit().clear().apply();
        if (had) {
            refreshFloat(c); // 去掉找下一件态，立刻把窗口交回真正的任务/下一要务
            TimerState.logEvent(c, "找下一个任务：已取消");
        }
    }

    /**
     * 每秒或闹钟到点调用：已过点且尚未开始下一件 → 清状态、拉选任务页。
     * 进程被杀后由 FloatService 启动或本闹钟唤醒时续上。
     */
    public static void poll(Context c) {
        long end = endAt(c);
        if (end == 0L) return;
        if (System.currentTimeMillis() < end) return;
        onExpired(c);
    }

    /** 服务（重）启动时按持久化截止时刻续排；已过点则立刻走到期。 */
    public static void ensureScheduled(Context c) {
        long end = endAt(c);
        if (end == 0L) return;
        if (System.currentTimeMillis() >= end) { poll(c); return; }
        schedule(c, end);
    }

    static void onAlarmFired(Context c) {
        poll(c);
    }

    private static void onExpired(Context c) {
        cancelAlarm(c);
        sp(c).edit().clear().apply();
        refreshFloat(c); // 闹钟路径没有 FloatService tick，也要立刻摘掉倒计时文案
        // 到期时若已经在计时/暂停，真正的任务优先，不再弹选任务页
        if (TimerState.isRunning(c) || TimerState.isPaused(c)) return;
        TimerState.logEvent(c, "找下一个任务：3 分钟到，拉起选任务页");
        // 走衔接守卫的事件档：尊重总开关、路人静音、温和档与页面互斥（检查页/响铃页在显时不叠弹，
        // 之后由巡检或主页 onResume 补拉）；状态已清，isActive 为假，不会被自己挡住
        IdleGuard.fireNow(c);
    }

    /** 把悬浮窗立刻刷新到当前 FindNext 状态；切到主线程，闹钟唤醒时也安全。 */
    private static void refreshFloat(Context c) {
        final Context app = c.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (FloatingWindow.enabled(app)) {
                    FloatingWindow.ensureShown(app);
                    FloatingWindow.refresh(app);
                }
            } catch (Exception ignored) { }
        });
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, FindNextReceiver.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, REQ, i, flags);
    }

    private static void schedule(Context c, long at) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = pending(c);
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (SecurityException se) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception ignored) { }
    }

    private static void cancelAlarm(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(c));
        } catch (Exception ignored) { }
    }
}
