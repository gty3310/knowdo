package com.ti.tasktimer;

import android.content.Context;
import android.media.AudioManager;

/**
 * 用机守卫调度（v1.19.0）：用户解锁（USER_PRESENT）或约 30 秒巡检时，按固定
 * 优先级只拉一页——晨间门 ＞ 致谢页 ＞ 选任务页。通话中一律不抢，挂断后由
 * 巡检补拉。alarm 循环进行中（alarm 页在显或震/响进行时）不拉选任务页：
 * alarm 页本身就是那一页的事。
 */
public final class GuardDispatch {

    private GuardDispatch() { }

    /** 解锁档：晨间/致谢优先，选任务页走 IdleGuard 的解锁档（不受返回静默约束）。 */
    public static void onUserPresent(Context c) {
        try {
            if (MorningGate.autoCheck(c)) return;
            if (inCall(c)) return;
            if (fireThanksIfPending(c)) return;
            // 知行自己的页面在前台时不直拉：主页 onResume 的 checkAndFire 会处理空档
            if (KnowDoApp.ownActivityVisible()) return;
            IdleGuard.checkOnUserPresent(c);
        } catch (Exception ignored) { }
    }

    /** 巡检档：与解锁档同一优先级，选任务页走自动档（受返回静默约束）。 */
    public static void patrol(Context c) {
        try {
            if (MorningGate.autoCheck(c)) return;
            if (inCall(c)) return;
            if (fireThanksIfPending(c)) return;
            IdleGuard.checkAndFire(c);
        } catch (Exception ignored) { }
    }

    /** 致谢页欠着且当前没有全屏页在显、本应用不在前台时，经统一出口拉起。 */
    private static boolean fireThanksIfPending(Context c) {
        if (!BystanderMute.thanksPending(c)) return false;
        if (TopPages.anyBlockingShown()) return false;
        if (KnowDoApp.ownActivityVisible()) return false;
        TimerState.logEvent(c, "守卫调度：路人静音待致谢，先拉致谢页");
        AlertHelper.fireThanks(c);
        return true;
    }

    /** 通话状态粗判（与 MorningGate 同口径，免电话权限）。 */
    static boolean inCall(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            int mode = am.getMode();
            return mode == AudioManager.MODE_IN_CALL
                    || mode == AudioManager.MODE_IN_COMMUNICATION
                    || mode == AudioManager.MODE_RINGTONE;
        } catch (Exception e) {
            return false;
        }
    }
}
