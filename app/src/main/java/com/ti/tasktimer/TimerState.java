package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

/** 验证版的状态存储：只存绝对时间点，剩余时间永远现算（进程被杀也不漂移）。 */
public final class TimerState {
    private static final String PREF = "timer_state";

    private TimerState() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void start(Context c, int totalSec, String taskId, String taskTitle) {
        long now = System.currentTimeMillis();
        sp(c).edit()
            .putBoolean("running", true)
            .putLong("startedAt", now)
            .putLong("halfAt", now + totalSec * 500L)
            .putLong("endAt", now + totalSec * 1000L)
            .putInt("totalSec", totalSec)
            .putString("taskId", taskId == null ? "" : taskId)
            .putString("taskTitle", taskTitle == null ? "" : taskTitle)
            .putBoolean("halfFired", false)
            .putBoolean("endFired", false)
            .apply();
    }

    public static String taskId(Context c) { return sp(c).getString("taskId", ""); }
    public static String taskTitle(Context c) {
        String t = sp(c).getString("taskTitle", "");
        return t == null || t.isEmpty() ? "当前任务" : t;
    }

    public static void extend(Context c, int addSec) {
        SharedPreferences p = sp(c);
        long base = Math.max(System.currentTimeMillis(), p.getLong("endAt", 0));
        p.edit()
            .putLong("endAt", base + addSec * 1000L)
            .putInt("totalSec", p.getInt("totalSec", 0) + addSec)
            .putBoolean("endFired", false)
            .putBoolean("running", true)
            .apply();
    }

    public static void stop(Context c) {
        sp(c).edit().putBoolean("running", false).apply();
    }

    // ---------- 暂停 / 继续（全局只留一个暂停槽） ----------
    public static void setPaused(Context c, long remainMs) {
        SharedPreferences p = sp(c);
        p.edit()
            .putString("p_taskId", p.getString("taskId", ""))
            .putString("p_title", p.getString("taskTitle", ""))
            .putInt("p_totalSec", p.getInt("totalSec", 0))
            .putLong("p_remainMs", Math.max(0, remainMs))
            .apply();
    }

    public static void clearPaused(Context c) {
        sp(c).edit().remove("p_taskId").remove("p_title").remove("p_totalSec").remove("p_remainMs").apply();
    }

    public static String pausedTaskId(Context c) {
        String s = sp(c).getString("p_taskId", "");
        return s == null ? "" : s;
    }

    public static boolean isPaused(Context c) { return !pausedTaskId(c).isEmpty(); }
    public static long pausedRemainMs(Context c) { return sp(c).getLong("p_remainMs", 0); }
    public static int pausedTotalSec(Context c) { return sp(c).getInt("p_totalSec", 0); }

    public static String pausedTitle(Context c) {
        String s = sp(c).getString("p_title", "");
        return s == null ? "" : s;
    }

    /** 继续：按原总时长与剩余秒数重建绝对时间点；过半标记沿用暂停前的状态（或已过半则视为已触发）。 */
    public static void startResumed(Context c, int totalSec, int remainSec, String taskId, String taskTitle) {
        SharedPreferences p = sp(c);
        long now = System.currentTimeMillis();
        long elapsedSec = Math.max(0, totalSec - remainSec);
        boolean half = p.getBoolean("halfFired", false) || elapsedSec * 2 >= totalSec;
        p.edit()
            .putBoolean("running", true)
            .putLong("startedAt", now - elapsedSec * 1000L)
            .putLong("halfAt", now - elapsedSec * 1000L + totalSec * 500L)
            .putLong("endAt", now + remainSec * 1000L)
            .putInt("totalSec", totalSec)
            .putString("taskId", taskId == null ? "" : taskId)
            .putString("taskTitle", taskTitle == null ? "" : taskTitle)
            .putBoolean("halfFired", half)
            .putBoolean("endFired", false)
            .remove("p_taskId").remove("p_title").remove("p_totalSec").remove("p_remainMs")
            .apply();
    }

    public static boolean isRunning(Context c) { return sp(c).getBoolean("running", false); }
    public static long startedAt(Context c) { return sp(c).getLong("startedAt", 0); }
    public static long endAt(Context c) { return sp(c).getLong("endAt", 0); }
    public static long halfAt(Context c) { return sp(c).getLong("halfAt", 0); }
    public static int totalSec(Context c) { return sp(c).getInt("totalSec", 0); }
    public static boolean halfFired(Context c) { return sp(c).getBoolean("halfFired", false); }
    public static boolean endFired(Context c) { return sp(c).getBoolean("endFired", false); }

    public static void markHalfFired(Context c) { sp(c).edit().putBoolean("halfFired", true).apply(); }
    public static void markEndFired(Context c) { sp(c).edit().putBoolean("endFired", true).apply(); }

    // ---------- 暂停提醒（暂停时约好多久后叫回来） ----------
    public static void setRemind(Context c, String taskId, long atMs) {
        sp(c).edit().putString("r_taskId", taskId == null ? "" : taskId).putLong("r_at", atMs).apply();
    }

    public static void clearRemind(Context c) {
        sp(c).edit().remove("r_taskId").remove("r_at").apply();
    }

    public static long remindAt(Context c) { return sp(c).getLong("r_at", 0); }

    public static String remindTaskId(Context c) {
        String s = sp(c).getString("r_taskId", "");
        return s == null ? "" : s;
    }

    /** 把用户的每次点按结果记成一行日志，测试后回主界面可核对。 */
    public static void logEvent(Context c, String text) {
        SharedPreferences p = sp(c);
        String old = p.getString("log", "");
        String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date());
        p.edit().putString("log", old + time + "  " + text + "\n").apply();
        // 同步落盘，保证进程被回收后日志不丢
        p.edit().commit();
    }

    public static String log(Context c) { return sp(c).getString("log", ""); }
    public static void clearLog(Context c) { sp(c).edit().remove("log").apply(); }
}
