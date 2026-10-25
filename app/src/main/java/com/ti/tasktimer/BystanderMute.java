package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 路人静音状态（v1.19.0）：空档 alarm 响个不停时，旁边的人点 alarm 页底部大按钮
 * 「如果您不是手机主人 请点击这里静音」——立即停震停声，并且在机主下一次亮屏
 * 之前，巡检与 alarm 一律不拉页、不震、不响；机主下一次解锁时先看致谢页。
 *
 * 状态只存本机 prefs（不进同步，与 MorningGate 的门行为状态同类）：
 * - muted：静音压制中。按下时置位；屏幕经历一次「灭→亮」之后自动解除压制
 *   （onScreenOff 记 off_seen，onScreenOn 在 off_seen 时清 muted）——按下时屏幕
 *   本来就亮着，要等的是「下一次」亮起，不能一按下就被当前亮屏解除。
 * - thanks_pending：致谢页还没给机主看过。与 muted 分开存：压制在亮屏时解除，
 *   但致谢要等解锁（USER_PRESENT）拉起致谢页并被消费才清。
 */
public final class BystanderMute {
    private static final String PREF = "bystander_mute";

    private BystanderMute() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 路人按下静音按钮：落盘压制＋待致谢（停震停声由调用方经 IdleStage.stop 执行）。 */
    public static void press(Context c) {
        sp(c).edit()
                .putBoolean("muted", true)
                .putBoolean("thanks_pending", true)
                .putBoolean("off_seen", false)
                .putLong("muted_at", System.currentTimeMillis())
                .apply();
        TimerState.logEvent(c, "路人静音：已按下，静音至下一次亮屏，下次解锁先出致谢页");
    }

    /** 压制是否生效（巡检/alarm 拉页与出声前都查这里）。 */
    public static boolean isMuted(Context c) { return sp(c).getBoolean("muted", false); }

    /** 致谢页是否还欠机主一次。 */
    public static boolean thanksPending(Context c) { return sp(c).getBoolean("thanks_pending", false); }

    /** 屏幕灭掉（FloatService 的 SCREEN_OFF 接收转交）：压制解除的前半程。 */
    public static void onScreenOff(Context c) {
        if (isMuted(c)) sp(c).edit().putBoolean("off_seen", true).apply();
    }

    /** 屏幕亮起：若压制期间已经历过灭屏，压制到此结束（致谢仍待解锁时呈现）。 */
    public static void onScreenOn(Context c) {
        if (isMuted(c) && sp(c).getBoolean("off_seen", false)) {
            sp(c).edit().putBoolean("muted", false).putBoolean("off_seen", false).apply();
            TimerState.logEvent(c, "路人静音：屏幕再次亮起，压制解除（致谢页待解锁呈现）");
        }
    }

    /** 致谢页已呈现：待致谢与压制一并消费掉。 */
    public static void consumeThanks(Context c) {
        sp(c).edit().clear().apply();
    }

    /** 机主已经开始任务（或空档周期被取消）：静音/致谢都不再有意义，整表清掉。 */
    public static void clearAll(Context c) {
        if (isMuted(c) || thanksPending(c)) sp(c).edit().clear().apply();
    }
}
