package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 未决守卫问询的持久化（v1.97.0，问询页大窗化配套）：问询改走 99% 大窗后，
 * 大窗是进程内 View——进程被杀窗就没了。把每次问询的关键上下文（模式／
 * 触发包名／类别／详情／理由／暂停期标记／触发时刻）落到 prefs，进程重启
 * 后由 FloatService 启动时 maybeRefire 在合理窗内重新出窗（经 AlertHelper
 * 原入口，大窗优先、降级口径不变）。
 *
 * 落盘点：AlertHelper.fireGuard / fireGuardContent 两个统一出口（两条路径
 * 都覆盖）。清除点：问询被解决的每个终局——大窗内各终局动作、转呼吸门、
 * GuardActivity 正常 finish（onDestroy 且 isFinishing）、重出时发现状态
 * 已不成立。被大窗壳抢占收回不算解决，不清（壳的 onDismissed 不经本类）。
 *
 * 重出口径：触发后 {@link #REFIRE_WINDOW_MS} 内、且计时/暂停状态仍与问询
 * 模式相符、且（拿得到前台时）前台仍是触发问询的那个应用，才重出；任何
 * 一条不成立就清掉不再重出——过期的问询不许在用户已经回到正轨后突然跳脸。
 */
public final class GuardPending {
    private static final String PREF = "guard_pending";
    /** 冷却合理窗：进程被杀到服务重启通常在数秒级，120 秒足够覆盖且不至太陈旧。 */
    static final long REFIRE_WINDOW_MS = 120_000L;
    /** 重出次数上限（v1.98.1）：重出不再刷新 fired_at（见 maybeRefire），且
     * 最多重出这么多次——防「崩溃→重启→重出→再崩」把 120 秒窗无限续命。 */
    static final int MAX_REFIRE = 2;

    private GuardPending() {}

    /** 一次未决问询的快照（字段与 GuardActivity 的 extras 一一对应）。 */
    public static final class Inquiry {
        public boolean contentMode;
        public String pkg = "";
        public String category = "";
        public String detail = "";
        public String reason = "";
        public boolean pauseGuard;
        public long firedAt;
        public int refireCount;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context c, boolean contentMode, String pkg, String category,
                            String detail, String reason, boolean pauseGuard) {
        CrashReporter.crumb("guard_inquiry");
        prefs(c).edit()
                .putBoolean("has", true)
                .putBoolean("content", contentMode)
                .putString("pkg", pkg == null ? "" : pkg)
                .putString("category", category == null ? "" : category)
                .putString("detail", detail == null ? "" : detail)
                .putString("reason", reason == null ? "" : reason)
                .putBoolean("pause_guard", pauseGuard)
                .putLong("fired_at", System.currentTimeMillis())
                .putInt("refire_count", 0)
                .apply();
    }

    public static Inquiry load(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("has", false)) return null;
        Inquiry q = new Inquiry();
        q.contentMode = p.getBoolean("content", false);
        q.pkg = p.getString("pkg", "");
        q.category = p.getString("category", "");
        q.detail = p.getString("detail", "");
        q.reason = p.getString("reason", "");
        q.pauseGuard = p.getBoolean("pause_guard", false);
        q.firedAt = p.getLong("fired_at", 0);
        q.refireCount = p.getInt("refire_count", 0);
        return q;
    }

    public static boolean has(Context c) {
        return prefs(c).getBoolean("has", false);
    }

    public static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }

    /**
     * FloatService 启动时调（进程被杀重启的落点）：有未决问询且仍成立时，
     * 经 AlertHelper 原入口重新触发一次。不成立的顺手清掉。返回是否重出了
     * （供日志/测试观察，调用方不依赖）。
     *
     * v1.98.1 断放大器：重出不再续命——fire 内部的 save 会把 fired_at 刷成
     * 当前时刻，重出后立刻写回原 fired_at；且重出次数达 {@link #MAX_REFIRE}
     * 即清掉不再重出。这样即便问询呈现路径再有别的崩溃，最多重出两轮，
     * 120 秒窗到期自然作废，不会无限循环弹崩溃（v1.97.0 现场教训）。
     */
    public static boolean maybeRefire(Context c) {
        Inquiry q = load(c);
        if (q == null) return false;
        long now = System.currentTimeMillis();
        if (now - q.firedAt > REFIRE_WINDOW_MS) { clear(c); return false; }
        if (q.refireCount >= MAX_REFIRE) { clear(c); return false; }
        if (BigOverlay.isWanted(BigOverlay.KEY_GUARD)) return false; // 已在出窗/排队
        // 状态仍须与问询模式相符：普通问询要还在计时；暂停期问询要还在暂停
        if (q.pauseGuard) {
            if (!TimerState.isPaused(c) || TimerState.isRunning(c)) { clear(c); return false; }
        } else if (!TimerState.isRunning(c)) { clear(c); return false; }
        // 前台仍须是触发问询的那个应用（拿不到前台时不拦，按窗内重出口径走）：
        // 内容模式的触发应用在 VerdictEngine 的 inquiry_pkg；名单模式即 pkg 本身
        String expect = q.contentMode ? VerdictEngine.inquiryPkg(c) : q.pkg;
        if (expect != null && !expect.isEmpty()) {
            String fg = null;
            try { fg = new UsageStatsDetector().foregroundPackage(c); } catch (Exception ignored) { }
            if (fg != null && !fg.equals(expect)) { clear(c); return false; }
        }
        TimerState.logEvent(c, "守卫问询：进程重启后重出未决问询（"
                + (q.contentMode ? "内容·" + ContentAnalyzer.label(q.category) : "名单·" + q.pkg) + "）");
        if (q.contentMode) AlertHelper.fireGuardContent(c, q.category, q.detail);
        else AlertHelper.fireGuard(c, q.pkg, q.reason.isEmpty() ? null : q.reason);
        // fire 内的 save 刚把 fired_at 刷新、refire_count 归零——写回原值并
        // 记上这一次重出（has 若已被终局动作清掉，load 不再认这两键，无害）
        prefs(c).edit()
                .putLong("fired_at", q.firedAt)
                .putInt("refire_count", q.refireCount + 1)
                .apply();
        return true;
    }
}
