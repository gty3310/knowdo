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

    // ---- 宽限到点后的处理方式（v1.21.3 布尔 → v1.80.0 三档枚举） ----
    public static final String ACTION_QUIT = "quit";
    public static final String ACTION_BACKGROUND = "background";
    public static final String ACTION_BLOCK = "block";

    /**
     * 三档：quit 退掉这个软件／background 只放到后台／block 盖住 3 分钟。
     * 名单模式与内容模式共用这一份（问询页选择行与设置页读写同一处）。
     * 迁移口径：旧布尔 grace_action_quit 已存过值时，true→quit、
     * false→background，首次读时落新键 grace_action 并删旧键；两个键都
     * 从未存过（全新安装，或老用户从未改过此项）→ 新默认 block
     * （用户 2026-11-11 定案：新默认盖住）。
     */
    public static String graceAction(Context c) {
        SharedPreferences p = prefs(c);
        String v = p.getString("grace_action", null);
        if (v != null) return v;
        if (p.contains("grace_action_quit")) {
            String m = p.getBoolean("grace_action_quit", true) ? ACTION_QUIT : ACTION_BACKGROUND;
            p.edit().putString("grace_action", m).remove("grace_action_quit").apply();
            return m;
        }
        return ACTION_BLOCK;
    }

    public static void setGraceAction(Context c, String action) {
        prefs(c).edit().putString("grace_action", action).apply();
    }

    // ---- 提前解封（v1.80.0）：每天限 1 次，按设备日历日记账 ----
    /** 今天的提前解封机会是否已用掉（与 unblock_date 比对日历日）。 */
    public static boolean unblockUsedToday(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean("unblock_used", false)
                && todayStr().equals(p.getString("unblock_date", ""));
    }

    public static void markUnblockUsed(Context c) {
        prefs(c).edit().putBoolean("unblock_used", true)
                .putString("unblock_date", todayStr()).apply();
    }

    private static String todayStr() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
    }

    // ---- 守卫难度三档（v1.83.0）：easy / medium / hard ----
    public static final String DIFF_EASY = "easy";
    public static final String DIFF_MEDIUM = "medium";
    public static final String DIFF_HARD = "hard";

    /**
     * 守卫难度（与宽限处置等设置同存 screenguard prefs）：新装、从未设置
     * 过的老用户一律 medium（用户 2026-11-12 定案），不弹迁移提示。
     */
    public static String guardDifficulty(Context c) {
        String v = prefs(c).getString("guard_difficulty", null);
        if (DIFF_EASY.equals(v) || DIFF_HARD.equals(v)) return v;
        return DIFF_MEDIUM;
    }

    public static void setGuardDifficulty(Context c, String d) {
        prefs(c).edit().putString("guard_difficulty", d).apply();
    }

    /** 困难档每日宽限配额（v1.83.0）：可选 {0,1,2,3,5}，默认 1；占用计数在 GuardLedger。 */
    public static int hardGraceQuota(Context c) {
        return prefs(c).getInt("hard_grace_quota", 1);
    }

    public static void setHardGraceQuota(Context c, int n) {
        prefs(c).edit().putInt("hard_grace_quota", n).apply();
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
