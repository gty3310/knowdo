package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * 排超提醒闸门（v1.93.0，用户 2026-11-21 亲定）：当「未完成还剩（原时长）＞
 * 今天还剩」时，在三个时刻自动打开安排今日任务页（TodayPlanActivity 本体），
 * 请用户当场重新安排：①切换任务（TimerService ACTION_START，确为从另一个在跑
 * 任务切过来）②给任务加时（ACTION_EXTEND 入账后）③确认分神宽限（FocusGuard
 * .chooseGrace／VerdictEngine.contentGrace 落点——此时不立即落宽限，存待生效
 * 槽，用户离开安排页时才按原口径生效，免得宽限被安排页吃掉）。
 *
 * 两个数与今天安排页顶部（v1.92.0）严格同源：未完成还剩走 TodayPlanActivity
 * 的静态 todayCandidates＋isGrey 逐件 max(0, duration − 今日已用)；今天还剩＝
 * DayAvail.wallCapMinToday。refreshLedger 也调本类，保证「触发的数＝显示的数」。
 *
 * 节流（SharedPreferences over_plan_gate）：弹过后 30 分钟内不再弹，但超额比
 * 上次弹时又多出 ≥15 分钟可再弹；今天还剩不足 15 分钟不弹。本类全部入口都
 * 吞异常——判定/弹出失败绝不能影响切换、加时、宽限的主流程。
 */
public final class OverPlanGate {
    public static final String KIND_LIST = "list";
    public static final String KIND_CONTENT = "content";

    private static final String PREF = "over_plan_gate";
    private static final long THROTTLE_MS = 30L * 60 * 1000;
    private static final long PEND_TTL_MS = 30L * 60 * 1000;
    private static final int RE_POP_OVER_DELTA_MIN = 15;
    private static final int MIN_DAY_LEFT_MIN = 15;

    private OverPlanGate() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---------------- 两个数（与今天安排页顶部同源） ----------------

    /** 未完成还剩（分钟，原时长）：今日候选（灰卡除外）逐件 duration − 今日已用，下限 0。 */
    public static int unfinishedMinutes(Context c, TaskStore store, String logicToday) {
        int sum = 0;
        if (store == null) return 0;
        for (TaskItem t : TodayPlanActivity.todayCandidates(store, logicToday)) {
            if (TodayPlanActivity.isGrey(t, logicToday)) continue; // 灰卡（推迟到未来）不计
            int used = (int) (SessionLog.todayMsForTask(c, t.id) / 60000);
            sum += Math.max(0, t.durationMinutes - used);
        }
        return sum;
    }

    /** 今天还剩（分钟）：现在→min(22:30, 睡觉开始) 扣锁时段。 */
    public static int dayLeftMinutes(Context c, String logicToday) {
        return DayAvail.wallCapMinToday(c, logicToday);
    }

    /** 是否排超：未完成还剩 ＞ 今天还剩。 */
    public static boolean isOver(Context c, TaskStore store, String logicToday) {
        return unfinishedMinutes(c, store, logicToday) > dayLeftMinutes(c, logicToday);
    }

    // ---------------- 节流判定 ----------------

    /**
     * 现在这一刻该不该弹：返回超额分钟数（≥0）表示可弹，-1 表示不弹。
     * 顺序：今天还剩＜15 分钟不弹 → 未超不弹 → 30 分钟内且超额未比上次多
     * ≥15 分钟不弹 → 弹。
     */
    private static int poppableOver(Context c) {
        String today = Rollover.logicalToday(c);
        int dayLeft = dayLeftMinutes(c, today);
        if (dayLeft < MIN_DAY_LEFT_MIN) return -1;
        TaskStore store = new TaskStore(c);
        store.load();
        int unfinished = unfinishedMinutes(c, store, today);
        if (unfinished <= dayLeft) return -1;
        int over = unfinished - dayLeft;
        SharedPreferences p = prefs(c);
        long lastPop = p.getLong("last_pop_ms", 0);
        int lastOver = p.getInt("last_pop_over_min", 0);
        if (System.currentTimeMillis() - lastPop < THROTTLE_MS
                && over < lastOver + RE_POP_OVER_DELTA_MIN) return -1;
        return over;
    }

    private static void recordPop(Context c, int over) {
        prefs(c).edit()
                .putLong("last_pop_ms", System.currentTimeMillis())
                .putInt("last_pop_over_min", over)
                .apply();
    }

    private static void pop(Context c) {
        if (TodayPlanActivity.isInForeground()) return; // 已在本页，不重复弹
        Intent i = new Intent(c, TodayPlanActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    // ---------------- 时机一/二：切换与加时 ----------------

    /** 排超则打开安排页（节流与判定都在内）；任何异常静默吞掉，不影响主流程。 */
    public static void maybePop(Context c) {
        try {
            if (TodayPlanActivity.isInForeground()) return;
            int over = poppableOver(c);
            if (over < 0) return;
            recordPop(c, over);
            pop(c);
            TimerState.logEvent(c, "排超提醒：未完成还剩超过今天还剩 " + over
                    + " 分钟，已打开安排今日任务页");
        } catch (Exception ignored) { }
    }

    // ---------------- 时机三：宽限延迟生效 ----------------

    /**
     * 宽限落点先问这里：排超且过节流 → 不立即生效，把请求存待生效槽（一笔，
     * 新的覆盖旧的）、弹安排页、记节流账，返回 true；否则返回 false，调用方
     * 按原口径立即生效。pkg/cat 按 kind 取用（list 用 pkg、content 用 cat＋pkg）。
     */
    public static boolean deferGrace(Context c, String kind, String pkg, String cat, int seconds) {
        try {
            if (seconds <= 0) return false;
            int over = poppableOver(c);
            if (over < 0) return false;
            SharedPreferences.Editor e = prefs(c).edit()
                    .putString("pend_kind", kind)
                    .putInt("pend_seconds", seconds)
                    .putLong("pend_ms", System.currentTimeMillis());
            if (pkg != null) e.putString("pend_pkg", pkg); else e.remove("pend_pkg");
            if (cat != null) e.putString("pend_cat", cat); else e.remove("pend_cat");
            e.apply();
            recordPop(c, over);
            pop(c);
            TimerState.logEvent(c, "排超提醒：宽限 " + seconds
                    + " 秒先暂存，离开安排页后开始计时");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * TodayPlanActivity.onDestroy 单点回调：待生效槽未超 30 分钟 → 按两个落点
     * 的原口径补写 grace_until＝now＋seconds（宽限从离开安排页这一刻才开始算），
     * 写完清槽；超 30 分钟的槽直接弃掉。任何异常静默吞掉。
     */
    public static void applyPendingGrace(Context c) {
        try {
            SharedPreferences p = prefs(c);
            String kind = p.getString("pend_kind", null);
            if (kind == null) return;
            String pkg = p.getString("pend_pkg", null);
            String cat = p.getString("pend_cat", null);
            int seconds = p.getInt("pend_seconds", 0);
            long storedMs = p.getLong("pend_ms", 0);
            p.edit().remove("pend_kind").remove("pend_pkg").remove("pend_cat")
                    .remove("pend_seconds").remove("pend_ms").apply();
            if (seconds <= 0 || storedMs <= 0) return;
            if (System.currentTimeMillis() - storedMs > PEND_TTL_MS) return; // 过期弃掉
            if (KIND_CONTENT.equals(kind)) {
                VerdictEngine.applyDeferredContentGrace(c, cat, seconds, pkg);
            } else {
                FocusGuard.applyGraceNow(c, pkg, seconds);
            }
        } catch (Exception ignored) { }
    }
}
