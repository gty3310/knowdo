package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * 空档声音催促排期（v1.14.0）：空档（没有任务在计时、且不在带时长的暂停中）持续
 * 满 idle_minutes 后，守卫页＋鸟鸣 alarm 一轮；之后按 first_repeat_minutes /
 * repeat_minutes 的节奏重复，直到有任务开始计时为止。参数与档位全部读 IdleNag
 * 个人设置，不写死。
 *
 * 状态持久化在 prefs「idle_nag_state」（空档起始 anchor、已响轮次 round、下轮
 * 时刻 next_at），进程/服务重启、开机后由 ensureScheduled 按持久化状态续排，
 * 不丢失、不重头乱响。排期用精确闹钟（setExactAndAllowWhileIdle，仿
 * PauseEscalation）扛 Doze。按返回的 3 分钟静默只压巡检弹页，不动这里的排期。
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
            sp(c).edit().putLong("anchor_ms", now).putInt("round", 0).apply();
        }
        if (nextAt(c) == 0 && !exhausted(c)) {
            long at = anchor(c) + IdleNag.idleMinutes(c) * 60_000L;
            schedule(c, Math.max(at, now + 1_000), false, 0);
        } else {
            ensureScheduled(c);
        }
    }

    /** 守卫页被展示（事件路径 fireNow/fireSoon 或从叫回页「换下一个任务」进来）。锚点＝展示时刻。 */
    public static void notePageShown(Context c) {
        if (!IdleGuard.enabled(c) || !IdleNag.nagActive(c)) return;
        long now = System.currentTimeMillis();
        if (anchor(c) == 0) {
            sp(c).edit().putLong("anchor_ms", now).putInt("round", 0).apply();
            if (nextAt(c) == 0 && !exhausted(c)) {
                schedule(c, now + IdleNag.idleMinutes(c) * 60_000L, false, 0);
            }
        }
    }

    /** 不再是空档（任务开始计时／进入带时长暂停／守卫被关）：整个周期取消并重置。 */
    public static void onNotIdle(Context c) {
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
        else at = now + (r == 1 ? IdleNag.firstRepeatMinutes(c) : IdleNag.repeatMinutes(c)) * 60_000L;
        schedule(c, at, false, 0);
    }

    /** 用户在守卫页有操作、声音被提前停掉：若下轮排期含「等响完」的一段，改为从现在起算间隔。 */
    public static void onSoundStoppedEarly(Context c) {
        if (!sp(c).getBoolean("next_includes_ring", false)) return;
        long gap = sp(c).getLong("next_gap_ms", 0);
        cancelAlarm(c);
        schedule(c, System.currentTimeMillis() + gap, false, 0);
    }

    // ---------------- 闹钟到点 ----------------
    static void onAlarmFired(Context c) {
        sp(c).edit().remove("next_at").remove("next_includes_ring").apply();
        if (!IdleGuard.enabled(c) || !IdleNag.nagActive(c) || !IdleGuard.isIdle(c)) {
            clearState(c);
            return;
        }
        if (exhausted(c)) return;
        int r = round(c) + 1;
        sp(c).edit().putInt("round", r).apply();
        boolean snd = IdleNag.effectiveSound(c);
        long ringMs = snd ? IdleNag.ringSeconds(c) * 1000L : 0;
        // 守卫页已经在前台（用户正在填）就不重开，只响铃，别冲掉正在输入的内容
        if (!TopPages.nextShown) {
            Intent i = new Intent(c, NextTaskActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try { c.startActivity(i); } catch (Exception ignored) { }
        }
        if (snd) AlarmRing.start(c, ringMs);
        TimerState.logEvent(c, "空档催促第 " + r + " 轮" + (snd ? "（响铃 " + (ringMs / 1000) + " 秒）" : "（无声）"));
        if (IdleNag.effectiveMaxRounds(c) > 0 && r >= IdleNag.effectiveMaxRounds(c)) {
            // 本轮是最后一轮：不再排下轮，周期到此安静（锚点保留，等任务开始/结束重置）
            return;
        }
        long gapMin = (r == 1) ? IdleNag.firstRepeatMinutes(c) : IdleNag.repeatMinutes(c);
        long gapMs = gapMin * 60_000L;
        schedule(c, System.currentTimeMillis() + ringMs + gapMs, snd, gapMs);
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
