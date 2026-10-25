package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

/** 屏幕内容守卫的用户设置（权限页「屏幕内容守卫」一节读写）。 */
public final class ScreenGuardPrefs {
    private static final String PREF = "screenguard";

    private ScreenGuardPrefs() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 总开关，默认关：开了之后，每次开始计时会请求一次截图授权并随机采样屏幕内容。 */
    public static boolean enabled(Context c) {
        return prefs(c).getBoolean("enabled", false);
    }

    public static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("enabled", on).apply();
    }

    /** nsfw 类是否一张即触发（跳过两次确认），默认开（与设计文档一致）。 */
    public static boolean nsfwOneShot(Context c) {
        return prefs(c).getBoolean("nsfw_one_shot", true);
    }

    public static void setNsfwOneShot(Context c, boolean on) {
        prefs(c).edit().putBoolean("nsfw_one_shot", on).apply();
    }

    /**
     * 守卫测试模式（v1.21.0，默认关）：每 10 秒借无障碍通道截一帧跑完整管线，
     * 把读到的文字与判定结果显示在一个专用悬浮窗里，供用户亲眼验收判定质量。
     * 测试采样只显示、绝不触发任何处置（问询页/跳回/宽限）。
     */
    public static boolean testMode(Context c) {
        return prefs(c).getBoolean("test_mode", false);
    }

    public static void setTestMode(Context c, boolean on) {
        prefs(c).edit().putBoolean("test_mode", on).apply();
    }

    // ---- 测试模式的「尺子」任务（v1.21.3） ----
    /** 临时任务的哨兵 id：只活在测试会话里，永不写入 TaskStore/云端/任务列表。 */
    public static final String TEST_TEMP_ID = "temp";

    /**
     * 开测试前选定的尺子任务：id 为真实任务 id，或 TEST_TEMP_ID（临时任务，
     * title/note 一并存）；没选过（或选了「不选任务」）时 id 为 null，判定回落到
     * 正在计时的任务（v1.21.2 之前的口径）。测试模式关闭即清（会话结束）。
     */
    public static void setTestTask(Context c, String id, String title, String note) {
        prefs(c).edit()
                .putString("test_task_id", id)
                .putString("test_task_title", title)
                .putString("test_task_note", note)
                .apply();
    }

    public static void clearTestTask(Context c) {
        prefs(c).edit().remove("test_task_id").remove("test_task_title")
                .remove("test_task_note").apply();
    }

    public static String testTaskId(Context c) { return prefs(c).getString("test_task_id", null); }
    public static String testTaskTitle(Context c) { return prefs(c).getString("test_task_title", null); }
    public static String testTaskNote(Context c) { return prefs(c).getString("test_task_note", null); }

    // ---- 宽限到点后的处理方式（v1.21.3） ----
    /**
     * 屏幕内容守卫问询页选宽限时一并选的到点处理：「退掉这个软件」（默认，
     * quit=true）或「只放到后台」。选一次记一次，下次默认沿用上次的选择。
     */
    public static boolean graceActionQuit(Context c) {
        return prefs(c).getBoolean("grace_action_quit", true);
    }

    public static void setGraceActionQuit(Context c, boolean quit) {
        prefs(c).edit().putBoolean("grace_action_quit", quit).apply();
    }
}
