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
}
