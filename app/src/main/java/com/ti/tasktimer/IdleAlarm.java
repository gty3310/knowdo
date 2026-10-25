package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * 空档声音催促排期（v1.14.0；v1.19.0 改 staging）：空档（没有任务在计时、且不在
 * 带时长的暂停中——含普通暂停已到期，见 TimerState.pauseExpired）持续满
 * idle_minutes（threshold）后进入催促循环，直到有任务开始计时为止。
 *
 * 每轮 staging（严格/自定义完整版；标准档只有页面＋震动段、无声、轮数封顶；
 * 温和档 nagActive 为假、根本不进本类）：
 * 全屏页（IdleAlarmActivity；已有全屏页在显时不叠页，震/响直接作用于当前页）
 * ＋先震动 vibrate_seconds 不出声＋无反应才起声音段（AlarmRing 渐强 ring_seconds），
 * 到顶停；静默 repeat_minutes 后下一轮。轮间隔统一 repeat_minutes——
 * first_repeat_minutes 字段保留兼容，staging 下不再单独生效。
 *
 * 空档锚点：暂停到期时＝到期时刻（TimerState.pauseExpiredAt），其余情形＝首次
 * 确认空档/守卫页展示时刻；锚点确立后不重置（任务开始才清）。
 *
 * 状态持久化在 prefs「idle_nag_state」（空档起始 anchor、已响轮次 round、下轮
 * 时刻 next_at），进程/服务重启、开机后由 ensureScheduled 按持久化状态续排，
 * 不丢失、不重头乱响。排期用精确闹钟（setExactAndAllowWhileIdle，仿
 * PauseEscalation）扛 Doze。按返回的 3 分钟静默只压巡检弹页，不动这里的排期。
 * 路人静音中（BystanderMute）：轮次照常消耗与排期，但不拉页、不震、不响，
 * 直到下一次亮屏解除压制。
 */
public final class IdleAlarm {
    private static final String PREF = "idle_nag_state";
    private static final int REQ = 781;
    public static final String ACTION = "com.ti.tasktimer.IDLE_NAG";

    private IdleAlarm() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static long anchor(Context c) { return sp(c).getLong("anchor_ms", 0); }
    private static int round(Context c) { return sp(c).getInt("round", 0); }
    private static long nextAt(Context c) { return sp(c).getLong("next_at", 0); }

    /** 当前轮次与锚点（alarm 页显示「第 N 次」与空档时长用）。 */
    public static int roundCount(Context c) { return round(c); }
    public static long anchorMs(Context c) { return anchor(c); }

    /**
     * 锚点候选：暂停已到期时锚点必须钉在到期时刻（到期＋threshold＝第一轮准点），
     * 不能等巡检发现才起算；其余情形返回 0 表示用当前时刻。
     */
    private static long preferredAnchor(Context c) {
        return TimerState.pauseExpired(c) ? TimerState.pauseExpiredAt(c) : 0L;
    }

    private static void clearState(Context c) {
        cancelAlarm(c);
        sp(c).edit().clear().apply();
    }

    // ---------------- 外部事件 ----------------

    /** 巡检确认正处于空档（IdleGuard.checkAndFire 在 idle 时调用，含被静默压住时）。 */
    public static void observeIdle(Context c) {
        if (!IdleGuard.enabled(c) || !IdleNag.nagActive(c)) { clearState(c); return; }
        long now = System.currentTimeMillis();
        if (anchor(c) == 0) {
            long pa = preferredAnchor(c);
            sp(c).edit().putLong("anchor_ms", pa > 0 ? pa : now).putInt("round", 0).apply();
        }
        if (nextAt(c) == 0 && !exhausted(c)) {
            long at = anchor(c) + IdleNag.idleMinutes(c) * 60_000L;
            schedule(c, Math.max(at, now + 1_000), false, 0);
        } else {
            ensureScheduled(c);
        }
    }

    /** 守卫页被展示（事件路径 fireNow/fireSoon 或从叫回页「换下一个任务」进来）。锚点＝展示时刻（暂停到期时优先到期时刻）。 */
    public static void notePageShown(Context c) {
        if (!IdleGuard.enabled(c) || !IdleNag.nagActive(c)) return;
        long now = System.currentTimeMillis();
        if (anchor(c) == 0) {
            long pa = preferredAnchor(c);
            sp(c).edit().putLong("anchor_ms", pa > 0 ? pa : now).putInt("round", 0).apply();
            if (nextAt(c) == 0 && !exhausted(c)) {
                schedule(c, now + IdleNag.idleMinutes(c) * 60_000L, false, 0);
            }
        }
    }

    /** 不再是空档（任务开始计时／进入带时长暂停／守卫被关）：整个周期取消并重置，正在进行的震/响立即停，路人静音/致谢一并作废。 */
    public static void onNotIdle(Context c) {
        IdleStage.stop(c);
        BystanderMute.clearAll(c);
        if (anchor(c) != 0 || nextAt(c) != 0) clearState(c);
        else cancelAlarm(c);
    }

    /** 本机设置变了：关掉/转温和→清周期；开着且正空档→按新参数重排下一轮。 */
    public static void reevaluate(Context c) {
        if (!IdleGuard.enabled(c) || !IdleNag.nagActive(c)) { clearState(c); return; }
        if (anchor(c) == 0) return;
        cancelAlarm(c);
        sp(c).edit().remove("next_at").remove("next_includes_ring").apply();
        if (exhausted(c)) return;
        long now = System.currentTimeMillis();
        int r = round(c);
        long at;
        if (r == 0) at = Math.max(anchor(c) + IdleNag.idleMinutes(c) * 60_000L, now + 1_000);
        else at = now + IdleNag.repeatMinutes(c) * 60_000L; // staging：轮间隔统一 repeat
        schedule(c, at, false, 0);
    }

    /** 用户在页面有操作、本轮震/响被提前停掉：若下轮排期含「等这一轮演完」的一段，改为从现在起算间隔。 */
    public static void onSoundStoppedEarly(Context c) {
        if (!sp(c).getBoolean("next_includes_ring", false)) return;
        long gap = sp(c).getLong("next_gap_ms", 0);
        cancelAlarm(c);
        schedule(c, System.currentTimeMillis() + gap, false, 0);
    }

    // ---------------- 闹钟到点：一轮 staging ----------------
    static void onAlarmFired(Context c) {
        sp(c).edit().remove("next_at").remove("next_includes_ring").apply();
        if (!IdleGuard.enabled(c) || !IdleNag.nagActive(c) || !IdleGuard.isIdle(c)) {
            IdleStage.stop(c);
            clearState(c);
            return;
        }
        if (exhausted(c)) return;
        int r = round(c) + 1;
        sp(c).edit().putInt("round", r).apply();
        boolean snd = IdleNag.effectiveSound(c);
        long vibMs = IdleNag.vibrateSeconds(c) * 1000L;
        long ringMs = snd ? IdleNag.ringSeconds(c) * 1000L : 0;
        if (BystanderMute.isMuted(c)) {
            // 路人静音中：这一轮静默消耗——不拉页、不震、不响，只排下一轮（压制解除后恢复正常轮次）
            TimerState.logEvent(c, "空档催促第 " + r + " 轮：路人静音中，静默跳过");
            scheduleNext(c, r, 0L);
            return;
        }
        // 全屏页：已有全屏页在显（叫回页/检查页/守卫页/alarm 页自身）就不叠页，
        // 本轮的震/响直接作用于当前页；否则拉起 alarm 页
        if (!TopPages.anyBlockingShown()) {
            Intent i = new Intent(c, IdleAlarmActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try { c.startActivity(i); } catch (Exception ignored) { }
        }
        IdleStage.start(c, vibMs, ringMs, snd);
        TimerState.logEvent(c, "空档催促第 " + r + " 轮（震 " + (vibMs / 1000) + " 秒"
                + (snd ? "→响 " + (ringMs / 1000) + " 秒渐强" : "，无声") + "）");
        scheduleNext(c, r, vibMs + ringMs);
    }

    /** 排下一轮：本轮 staging 演完（stageMs）＋ repeat_minutes 静默；到轮数上限就不再排。 */
    private static void scheduleNext(Context c, int r, long stageMs) {
        if (IdleNag.effectiveMaxRounds(c) > 0 && r >= IdleNag.effectiveMaxRounds(c)) {
            // 本轮是最后一轮：不再排下轮，周期到此安静（锚点保留，等任务开始/结束重置）
            return;
        }
        long gapMs = IdleNag.repeatMinutes(c) * 60_000L;
        schedule(c, System.currentTimeMillis() + stageMs + gapMs, stageMs > 0, gapMs);
    }

    private static boolean exhausted(Context c) {
        int max = IdleNag.effectiveMaxRounds(c);
        return max > 0 && round(c) >= max;
    }

    // ---------------- 精确闹钟 ----------------
    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, IdleAlarmReceiver.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, REQ, i, flags);
    }

    private static void schedule(Context c, long at, boolean includesRing, long gapMs) {
        sp(c).edit().putLong("next_at", at)
                .putBoolean("next_includes_ring", includesRing)
                .putLong("next_gap_ms", gapMs).apply();
        setExact(c, at);
    }

    private static void setExact(Context c, long at) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c));
            } catch (SecurityException se) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c));
            }
        } catch (Exception ignored) { }
    }

    /** 服务/进程重启、开机后按持久化状态续排（已过点则 1 秒后补响当前轮）。 */
    public static void ensureScheduled(Context c) {
        long at = nextAt(c);
        if (at == 0 || anchor(c) == 0) return;
        if (!IdleGuard.enabled(c) || !IdleNag.nagActive(c)) { clearState(c); return; }
        setExact(c, Math.max(at, System.currentTimeMillis() + 1_000));
    }

    private static void cancelAlarm(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(c));
        } catch (Exception ignored) { }
    }
}
