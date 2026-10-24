package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 悬浮窗关闭 snooze（v1.14.0）：悬浮窗默认常驻，但用户可以从悬浮窗本体把它
 * 定时关掉——到点自动再现；截止时刻持久化，服务/进程重启、开机后照算
 * （已过点则立即再现）。snooze 期间只藏悬浮窗：计时、守卫弹页、alarm、
 * 通知一律照常。「不再出现」不是 snooze，是关掉显示开关（float_enabled），
 * 在设置里能重新打开。
 */
public final class FloatSnooze {
    private static final String PREF = "float_snooze";
    private static final int MODE_NONE = 0;
    private static final int MODE_UNTIL = 1;      // 定时回来
    private static final int MODE_WAIT_TASK = 2;  // 等当前任务结束回来

    private static boolean wasRunning; // 「等任务结束」的跃迁检测（运行→非运行且非暂停）

    private FloatSnooze() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 定时 snooze：minutes 分钟后回来。 */
    public static void snoozeFor(Context c, int minutes) {
        sp(c).edit().putInt("mode", MODE_UNTIL)
                .putLong("until", System.currentTimeMillis() + minutes * 60_000L).apply();
        FloatingWindow.hide();
    }

    /** 等当前任务结束再回来（点关闭时没在计时＝等下一个任务结束）。 */
    public static void snoozeUntilTaskEnd(Context c) {
        sp(c).edit().putInt("mode", MODE_WAIT_TASK).remove("until").apply();
        wasRunning = TimerState.isRunning(c);
        FloatingWindow.hide();
    }

    public static void cancel(Context c) {
        sp(c).edit().clear().apply();
    }

    /** 当前是否处于 snooze（窗口应保持隐藏）。 */
    public static boolean isActive(Context c) {
        int m = sp(c).getInt("mode", MODE_NONE);
        if (m == MODE_UNTIL) {
            if (System.currentTimeMillis() >= sp(c).getLong("until", 0)) {
                cancel(c); // 已过点：snooze 结束，立即再现
                return false;
            }
            return true;
        }
        return m == MODE_WAIT_TASK;
    }

    /**
     * 每秒由 FloatService 节拍调用：维护「等任务结束」的跃迁——
     * 计时中→（计时结束且结束状态落定、也不是暂停）时 snooze 到期。
     * 计时结束的检查页展示期间计时状态仍在，状态落定后才算「处理完」，与口径一致。
     */
    public static void poll(Context c) {
        if (sp(c).getInt("mode", MODE_NONE) != MODE_WAIT_TASK) return;
        boolean running = TimerState.isRunning(c);
        if (running) { wasRunning = true; return; }
        if (wasRunning && !TimerState.isPaused(c)) {
            wasRunning = false;
            cancel(c);
            TimerState.logEvent(c, "悬浮窗 snooze（等任务结束）到期，再现");
        }
    }

    /** 给设置页/文案用的人话说明。 */
    public static String describe(Context c) {
        int m = sp(c).getInt("mode", MODE_NONE);
        if (m == MODE_UNTIL) {
            long left = sp(c).getLong("until", 0) - System.currentTimeMillis();
            if (left <= 0) return "";
            long min = Math.max(1, (left + 59_999) / 60_000);
            return Strings.en() ? "Floating window hidden, back in ~" + min + " min"
                    : "悬浮窗已隐藏，约 " + min + " 分钟后回来";
        }
        if (m == MODE_WAIT_TASK) return Strings.en()
                ? "Floating window hidden until the current task ends"
                : "悬浮窗已隐藏，等当前任务结束回来";
        return "";
    }
}
