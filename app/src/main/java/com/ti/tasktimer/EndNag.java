package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * 结束页催促（v1.21.1 热修）：任务到点、结束检查页弹出后，用户可能把手机放一边，
 * 既不确认完成也不加时——旧版这个状态只有一个弹页＋一次短震，之后永远安静：
 * running 还没清，空档催促（IdleAlarm）按 isIdle 判不到它（要求 !isRunning），
 * 检查页自己又没有任何升级计时，是个无人接管的状态盲区。本类正面补上这个
 * 盲区（不动 IdleGuard 的判定语义）：给结束页一条自己的升级链，与空档 staging
 * 同节奏、不同锚点——锚点＝到点时刻（经响铃页转入检查页时＝检查页展示时刻），
 * 页面出现后 60 秒无回应进第一轮，此后「先震 vibrate_seconds→无反应才进声音段
 * （AlarmRing 渐强 ring_seconds）→静默 repeat_minutes」循环，直到用户处置为止。
 *
 * 档位与轮数完全借 IdleNag 口径（strict 无限轮、standard 封顶 max_rounds、
 * gentle 与总开关关不进循环），与空档催促一致；per-task alarmMode（v1.20.0）
 * 再裁一刀：off→不进本链，silent→只震不响（跳过声音段），sound/follow→按
 * 档位全套。作用范围只限 TYPE_END 结束页（子任务最后一个边界汇入的结束走
 * 的也是 TYPE_END）；过半页/中间子任务页/暂停叫回/空档 staging 一律不碰。
 *
 * 架构仿 IdleAlarm：状态持久化在 prefs「end_nag_state」（与 idle_nag_state 分开
 * 存、互不污染），AlarmManager 精确闹钟推进轮次，进程被杀/开机后由
 * ensureScheduled 按持久化状态续排。两段执行复用 IdleStage（与空档 staging
 * 共用一份）：结束待确认时 running 未清、空档 staging 不可能同时活跃，天然
 * 互斥；IdleStage.start 自带「先停上一段」，AlarmRing 也只有现成这一套，
 * 不存在双响。停止条件任一成立即 cancel 并清状态：用户在结束页处置（完成→
 * 计时停止；加时→endFired 清除、新 endAt 产生，本链重置待命，下次到点重新
 * 武装）、计时被停止/远端结束、新计时开始。路人静音生效中：本轮静默消耗，
 * 与空档口径一致。
 */
public final class EndNag {
    private static final String PREF = "end_nag_state";
    private static final int REQ = 783;
    public static final String ACTION = "com.ti.tasktimer.END_NAG";
    /** 结束页出现后多久无回应进第一轮（仿 PauseEscalation 叫回页的 60 秒升级）。 */
    private static final long FIRST_DELAY_MS = 60_000L;

    private EndNag() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static long anchor(Context c) { return sp(c).getLong("anchor_ms", 0); }
    private static int round(Context c) { return sp(c).getInt("round", 0); }
    private static long nextAt(Context c) { return sp(c).getLong("next_at", 0); }
    /** 武装时对应的任务结束点；与当前 TimerState.endAt 对不上即旧周期（已加时/换任务）。 */
    private static long endKey(Context c) { return sp(c).getLong("end_key", 0); }

    // ---------------- 裁决 ----------------

    private static String alarmMode(Context c) {
        try {
            String tid = TimerState.taskId(c);
            TaskItem t = tid == null ? null : new TaskStore(c).byId(tid);
            return t == null ? TaskItem.ALARM_FOLLOW : TaskItem.normAlarmMode(t.alarmMode);
        } catch (Exception e) {
            return TaskItem.ALARM_FOLLOW;
        }
    }

    /** 是否允许进催促循环：催促档位（IdleNag 口径）＋该任务没把 alarm 设成 off。 */
    private static boolean eligible(Context c) {
        return IdleNag.nagActive(c) && !TaskItem.ALARM_OFF.equals(alarmMode(c));
    }

    /** 这一轮出不出声：档位声音（IdleNag.effectiveSound）再被任务级 silent 压掉。 */
    private static boolean roundSound(Context c) {
        return IdleNag.effectiveSound(c) && !TaskItem.ALARM_SILENT.equals(alarmMode(c));
    }

    /** 结束待确认是否仍成立：还在计时、结束已触发、结束点还是武装时那个（没被加时改掉）。 */
    private static boolean pendingEnd(Context c) {
        return TimerState.isRunning(c) && TimerState.endFired(c)
                && endKey(c) != 0 && TimerState.endAt(c) == endKey(c);
    }

    private static boolean exhausted(Context c) {
        int max = IdleNag.effectiveMaxRounds(c);
        return max > 0 && round(c) >= max;
    }

    // ---------------- 武装 / 解除 ----------------

    /**
     * 到点弹结束页时武装（AlertHelper.fire 的 TYPE_END 直达检查页路径调用）：
     * 锚点＝到点时刻，第一轮排在锚点＋60 秒。同一个结束点重复调用只续排，
     * 不重置轮次；结束点变了（新一轮到点）则按新锚点重新武装。
     */
    public static void arm(Context c) {
        long endAt = TimerState.endAt(c);
        if (endAt == 0) return;
        if (anchor(c) != 0 && endKey(c) == endAt) { ensureScheduled(c); return; }
        if (!TimerState.endFired(c) || !eligible(c)) { cancel(c); return; }
        long now = System.currentTimeMillis();
        long anchorMs = Math.min(endAt, now); // 兜底闹钟迟到时锚点仍是到点时刻，不排进未来
        sp(c).edit().putLong("anchor_ms", anchorMs).putLong("end_key", endAt)
                .putInt("round", 0).apply();
        schedule(c, Math.max(anchorMs + FIRST_DELAY_MS, now + 1_000));
        TimerState.logEvent(c, "结束催促已武装：结束页 60 秒无回应将先震后响循环催");
    }

    /**
     * 结束页实际展示时调用（CheckActivity 结束页，notePageShown 式处理）：
     * 经任务响铃页转入的结束，fire 时被响铃页先行接管、还没武装——锚点改取
     * 检查页展示时刻，60 秒后由本链接管；直达路径已武装的只续排，不动锚点。
     */
    public static void noteEndPageShown(Context c) {
        if (anchor(c) != 0) { ensureScheduled(c); return; }
        if (!TimerState.endFired(c) || !eligible(c)) return;
        long now = System.currentTimeMillis();
        sp(c).edit().putLong("anchor_ms", now).putLong("end_key", TimerState.endAt(c))
                .putInt("round", 0).apply();
        schedule(c, now + FIRST_DELAY_MS);
    }

    /**
     * 停止并清状态：用户在结束页做了任何处置（完成/加时）、计时被停止或远端
     * 结束、新计时开始时调用。没有本链状态时不动 IdleStage（不误停别人的段）。
     */
    public static void cancel(Context c) {
        boolean had = anchor(c) != 0 || nextAt(c) != 0;
        cancelAlarm(c);
        if (had) {
            IdleStage.stop(c); // 正在演的这一轮（震/待触发声音段/铃声）立即停
            sp(c).edit().clear().apply();
        }
    }

    /** 设置变了（催促关/转温和、或任务 alarm 改 off）：不再符合资格就整链取消；仍符合则参数每轮现读，不必重排。 */
    public static void reevaluate(Context c) {
        if (anchor(c) == 0) return;
        if (!pendingEnd(c) || !eligible(c)) cancel(c);
        else ensureScheduled(c);
    }

    // ---------------- 闹钟到点：一轮 staging ----------------
    static void onAlarmFired(Context c) {
        sp(c).edit().remove("next_at").apply();
        if (!pendingEnd(c) || !eligible(c)) { cancel(c); return; }
        if (exhausted(c)) return;
        int r = round(c) + 1;
        sp(c).edit().putInt("round", r).apply();
        boolean snd = roundSound(c);
        long vibMs = IdleNag.vibrateSeconds(c) * 1000L;
        long ringMs = snd ? IdleNag.ringSeconds(c) * 1000L : 0;
        if (BystanderMute.isMuted(c)) {
            // 路人静音中：本轮静默消耗——不震、不响，只排下一轮（与空档口径一致）
            TimerState.logEvent(c, "结束催促第 " + r + " 轮：路人静音中，静默跳过");
            scheduleNext(c, r, 0L);
            return;
        }
        if (TopPages.alarmShown) {
            // 响铃页在显期间不重复催：它自己在响，本轮静默消耗
            TimerState.logEvent(c, "结束催促第 " + r + " 轮：响铃页在显，静默跳过");
            scheduleNext(c, r, 0L);
            return;
        }
        IdleStage.start(c, vibMs, ringMs, snd);
        TimerState.logEvent(c, "结束催促第 " + r + " 轮（震 " + (vibMs / 1000) + " 秒"
                + (snd ? "→响 " + (ringMs / 1000) + " 秒渐强" : "，无声") + "）");
        scheduleNext(c, r, vibMs + ringMs);
    }

    /** 排下一轮：本轮 staging 演完（stageMs）＋ repeat_minutes 静默；到轮数上限就不再排（状态留待处置时清）。 */
    private static void scheduleNext(Context c, int r, long stageMs) {
        if (IdleNag.effectiveMaxRounds(c) > 0 && r >= IdleNag.effectiveMaxRounds(c)) return;
        long gapMs = IdleNag.repeatMinutes(c) * 60_000L;
        schedule(c, System.currentTimeMillis() + stageMs + gapMs);
    }

    // ---------------- 精确闹钟（仿 IdleAlarm） ----------------
    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, EndNagReceiver.class).setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, REQ, i, flags);
    }

    private static void schedule(Context c, long at) {
        sp(c).edit().putLong("next_at", at).apply();
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

    /** 进程重启、开机后按持久化状态续排（已过点则 1 秒后补当前轮）；结束早已被处置就顺手清掉。 */
    public static void ensureScheduled(Context c) {
        if (anchor(c) == 0) return;
        if (!pendingEnd(c) || !eligible(c)) { cancel(c); return; }
        long at = nextAt(c);
        if (at == 0) {
            if (exhausted(c)) return;
            // 有锚点却没排期（排期被系统清掉等罕见情形）：按轮间隔口径补排下一轮
            schedule(c, System.currentTimeMillis() + IdleNag.repeatMinutes(c) * 60_000L);
            return;
        }
        setExact(c, Math.max(at, System.currentTimeMillis() + 1_000));
    }

    private static void cancelAlarm(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(c));
        } catch (Exception ignored) { }
    }
}
