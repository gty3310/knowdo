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
}
