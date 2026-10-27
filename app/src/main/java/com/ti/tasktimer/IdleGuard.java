package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * 任务衔接守卫（v1.10.0）：消灭「没有状态的空档」。
 * 本地有效计时状态既非 running、又没有生效中的定时暂停时，视为「空档」，
 * 经 AlertHelper.fireNextTask 全屏拉起「下一个任务」页（NextTaskActivity）。
 *
 * 状态来源是 TimerState，其中已含 Supabase timer_state 的跨设备镜像：
 * 另一台设备在计时（running 镜像）或暂停（暂停槽镜像）时本机同样不算空档，不会误弹。
 *
 * 触发分两档：
 * - checkAndFire：常驻巡检（FloatService）、主页 onResume——
 *   受「守卫页被返回键退出后的静默期」与最小重拉间隔约束，免得用户临时翻列表/设置时被反复打断；
 * - fireNow：事件触发（检查页确认完成、主页/日历手动完成任务、叫回页转入）——
 *   不受静默期约束（完成一个任务正是「问下一个」的时刻），仍受开关/空档/页面互斥约束。
 */
public final class IdleGuard {
    private static final String PREF = "idle_guard";
    private static final long REFIRE_MIN_MS = 10_000L;          // 两次自动拉起的最小间隔
    private static final long EVENT_DEBOUNCE_MS = 3_000L;       // 事件档防抖（与自动档叠加触发时）
    // 守卫页按返回退出后自动档静默多久再重拉：v1.26.3 起读 PromptRepop（原固定 3 分钟）
    private static long dismissSnoozeMs(Context c) { return PromptRepop.millis(c); }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile long lastFiredAt = 0L;

    private IdleGuard() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 设置页「任务衔接守卫」开关（默认开，只存本地不进同步）。关掉时看门狗整体停用。 */
    public static boolean enabled(Context c) { return sp(c).getBoolean("enabled", true); }

    public static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("enabled", on).apply();
        if (!on) {
            AlertHelper.cancelNextTask(c);
            IdleAlarm.onNotIdle(c); // 总开关关：催促周期一并取消（弹页与 alarm 都不发生）
        }
    }

    /** 是否空档：没在计时，也没有生效中的定时暂停。 */
    public static boolean isIdle(Context c) {
        if (TimerState.isRunning(c)) return false;
        return !timedPauseActive(c);
    }

    /**
     * 生效中的定时暂停（暂停槽里有任务，且）：
     * - 已到期标记在（v1.19.0）：普通暂停已经到点叫回过——叫回页正在显示时归叫回页管，
     *   不在显就是空档（催促 staging 从到期时刻起算）。这一条必须排在 r_at==0 之前：
     *   到点后 r_at 已被清零，旧判法会把它误判成下面的「无期限暂停」而永不空档（原 bug）；
     * - 没设叫回时间：旧版的无期限暂停，或远端镜像过来的暂停（远端没有「多久后叫回」的概念）
     *   ——视为「有当前任务（暂停中）」，不算空档；
     * - 叫回时间在未来：定时暂停生效中；
     * - 叫回时间已过：叫回页正在显示时归叫回页管；不在显才算空档。
     */
    private static boolean timedPauseActive(Context c) {
        if (!TimerState.isPaused(c)) return false;
        if (TimerState.pauseExpired(c)) return TopPages.resumeRemindShown;
        long ra = TimerState.remindAt(c);
        if (ra == 0L) return true;
        if (ra > System.currentTimeMillis()) return true;
        return TopPages.resumeRemindShown;
    }

    /** 自动档：巡检与主页 onResume 调用。 */
    public static void checkAndFire(Context c) {
        // 空档催促周期先与空档状态同步（v1.14.0）：非空档即取消整个周期；
        // 空档中维护锚点与 alarm 排期。这一步在总开关/静默判断之前——
        // 按返回的重弹间隔静默只压自动弹页，绝不动 alarm 排期。
        if (!isIdle(c)) { IdleAlarm.onNotIdle(c); return; }
        IdleAlarm.observeIdle(c);
        if (BystanderMute.isMuted(c)) return; // 路人已静音：下一次亮屏前不拉页（排期不动，v1.19.0）
        if (!enabled(c) || TopPages.anyBlockingShown()) return;
        if (IdleNag.suppressIdlePage(c)) return; // 温和档：空档连页都不弹
        long now = System.currentTimeMillis();
        if (now < sp(c).getLong("snooze_until", 0L)) return;
        if (now - lastFiredAt < REFIRE_MIN_MS) return;
        fire(c);
    }

    /** 事件档：完成任务/叫回页转入等明确时刻调用，不受返回静默期约束。 */
    public static void fireNow(Context c) {
        if (!enabled(c) || !isIdle(c) || TopPages.anyBlockingShown()) return;
        if (BystanderMute.isMuted(c)) return;
        if (IdleNag.suppressIdlePage(c)) return;
        if (System.currentTimeMillis() - lastFiredAt < EVENT_DEBOUNCE_MS) return;
        fire(c);
    }

    /**
     * 解锁档（v1.19.0 选任务页守卫）：用户解锁开始用手机（USER_PRESENT）时调用——
     * 与自动档同门槛（开关/空档/页面互斥/温和档/路人静音），但不受「按返回退出后的
     * 重弹间隔静默」约束：重新拿起手机就是一个新的开始时刻，该直接见选任务页。
     * 仍受最小重拉间隔约束，避免与巡检同刻叠拉。
     */
    public static void checkOnUserPresent(Context c) {
        if (!isIdle(c)) { IdleAlarm.onNotIdle(c); return; }
        IdleAlarm.observeIdle(c);
        if (BystanderMute.isMuted(c)) return;
        if (!enabled(c) || TopPages.anyBlockingShown()) return;
        if (IdleNag.suppressIdlePage(c)) return;
        if (System.currentTimeMillis() - lastFiredAt < REFIRE_MIN_MS) return;
        fire(c);
    }

    /** 稍后（600ms）以事件档拉起：给检查页确认完成等流程留出收尾与页面切换时间。 */
    public static void fireSoon(final Context c) {
        final Context app = c.getApplicationContext();
        MAIN.postDelayed(() -> fireNow(app), 600);
    }

    /** 守卫页被用户按返回退出时调用：自动档静默一轮重弹间隔（PromptRepop），过后照常重拉。 */
    public static void snoozeOnDismiss(Context c) {
        sp(c).edit().putLong("snooze_until",
                System.currentTimeMillis() + dismissSnoozeMs(c)).apply();
    }

    private static void fire(Context c) {
        lastFiredAt = System.currentTimeMillis();
        IdleAlarm.notePageShown(c); // 守卫页展示＝空档时钟的锚点候选（已有锚点时不重置）
        TimerState.logEvent(c, "任务衔接守卫：检测到空档，拉起「下一个任务」页");
        AlertHelper.fireNextTask(c);
    }
}
