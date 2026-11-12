package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 深呼吸门进行中的抑制标记（v1.83.0）：呼吸页打开期间，内容守卫采样
 * （VerdictEngine.onResult）一律不触发新问询——呼吸页是本包页面，名单
 * 模式那侧的前台检测本来就豁免本包；这里补上内容模式那侧，避免呼吸页
 * 与问询页互拉。状态存 screenguard_state（与 VerdictEngine 同一份 prefs），
 * 带到期时间：呼吸页异常消失时标记也会自己过期，不会永久压住问询。
 */
public final class GuardBreath {
    private GuardBreath() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("screenguard_state", Context.MODE_PRIVATE);
    }

    /** 呼吸开始前调用：标记呼吸时长＋2 秒衔接余量。 */
    public static void start(Context c, long millis) {
        prefs(c).edit().putLong("breathing_until",
                System.currentTimeMillis() + millis + 2_000L).apply();
    }

    public static void clear(Context c) {
        prefs(c).edit().remove("breathing_until").apply();
    }

    public static boolean active(Context c) {
        return System.currentTimeMillis() < prefs(c).getLong("breathing_until", 0);
    }
}
