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

    public static boolean isRunning(Context c) { return sp(c).getBoolean("running", false); }
    public static long endAt(Context c) { return sp(c).getLong("endAt", 0); }
    public static long halfAt(Context c) { return sp(c).getLong("halfAt", 0); }
    public static int totalSec(Context c) { return sp(c).getInt("totalSec", 0); }
    public static boolean halfFired(Context c) { return sp(c).getBoolean("halfFired", false); }
    public static boolean endFired(Context c) { return sp(c).getBoolean("endFired", false); }

    public static void markHalfFired(Context c) { sp(c).edit().putBoolean("halfFired", true).apply(); }
    public static void markEndFired(Context c) { sp(c).edit().putBoolean("endFired", true).apply(); }

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
