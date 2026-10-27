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

    // ---- 语义判定（v1.21.4） ----
    /**
     * 「语义判定」独立开关（默认开）：守卫粗判拿不准时，本地向量预筛之后
     * 还拿不准，才把打码后的屏幕文字发云端大模型终判。关掉则只跑本地
     * 预筛，任何数据都不出手机（SemanticJudge 一行云端调用都不许发）。
     */
    public static boolean semanticEnabled(Context c) {
        return prefs(c).getBoolean("semantic_enabled", true);
    }

    public static void setSemanticEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("semantic_enabled", on).apply();
    }

    /**
     * 诊断显示开关（内部 key diag_mode，UI 文案「测试者模式」——v1.24.1
     * 用户钦定，原暂名「测试模式」）：给愿意帮忙 debug、检查软件错误
     * 的用户用的模式，默认关。开启后判定展示底部才会出现诊断行（如降级
     * 时的「大模型无法连接，用本地小模型」），且软件内部报错会经
     * ErrorReporter 弹悬浮窗亮出状态码与原因；与守卫测试模式
     * （test_mode）是两回事，互不影响。
     */
    public static boolean diagMode(Context c) {
        return prefs(c).getBoolean("diag_mode", false);
    }

    public static void setDiagMode(Context c, boolean on) {
        prefs(c).edit().putBoolean("diag_mode", on).apply();
    }

    // ---- 任务软件名单（v1.26.0） ----
    /**
     * 「任务软件名单」开关（默认开）：开始任务时把任务与常用软件名单交云端
     * 巡查分类（白/灰/黑），黑名单软件在任务期间一打开就弹问询。关掉后不发
     * 请求、不按名单弹问询（内置通用兜底名单也一并停用）。
     */
    public static boolean appListsEnabled(Context c) {
        return prefs(c).getBoolean("app_lists_enabled", true);
    }

    public static void setAppListsEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("app_lists_enabled", on).apply();
    }

    // ---- 语义云端退避（SemanticJudge 用）：连续失败后一段时间内不再试云端 ----
    public static long semanticBackoffUntil(Context c) {
        return prefs(c).getLong("semantic_backoff_until", 0);
    }

    public static int semanticFailStreak(Context c) {
        return prefs(c).getInt("semantic_fail_streak", 0);
    }

    /** 记一次云端失败：streak+1，退避 10 分钟起、指数翻倍、上限 30 分钟。 */
    public static void noteSemanticCloudFailure(Context c) {
        int streak = semanticFailStreak(c) + 1;
        long mins = Math.min(30L, 10L << Math.min(streak - 1, 2));
        if (streak >= 3) mins = 30L;
        prefs(c).edit()
                .putInt("semantic_fail_streak", streak)
                .putLong("semantic_backoff_until", System.currentTimeMillis() + mins * 60_000L)
                .apply();
    }

    public static void noteSemanticCloudSuccess(Context c) {
        prefs(c).edit().remove("semantic_fail_streak").remove("semantic_backoff_until").apply();
    }

    /** 降级放行备注的节流：同一退避窗口只记一条（GuardEvents 备注用）。 */
    public static long semanticNoteForBackoff(Context c) {
        return prefs(c).getLong("semantic_note_backoff", -1);
    }

    public static void setSemanticNoteForBackoff(Context c, long backoffUntil) {
        prefs(c).edit().putLong("semantic_note_backoff", backoffUntil).apply();
    }
}
