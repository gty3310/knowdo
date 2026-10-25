package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 屏幕内容守卫的判定层：
 * 单张命中只记“嫌疑”；同一类连续命中 2 张才触发问询页（nsfw 可在设置里改成一张即触发）。
 * 触发后：记 GuardEvents（同步进桥）、复用 GuardActivity（content 模式）、
 * 冷却 60 秒不再重复触发。用户在问询页的选择：
 * 立即回到任务 → GuardActions 跳回；给我 30/60 秒 → 该类宽限，并同时选定
 * 到点处理方式（退掉该软件／只放到后台，ScreenGuardPrefs 记选择）；到点由
 * GraceEnforcer 的精确闹钟主动复查前台执行（不等再次命中），若到期前采样
 * 再次命中同类也走同一个 applyEnforcement；误判放行 → 本次计时内不再判该类。
 */
public final class VerdictEngine {
    private static final String PREF = "screenguard_state";
    private static final long COOLDOWN_MS = 60_000L;

    private VerdictEngine() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 新计时开始时调用：清掉上一场的连续命中、宽限、放行与未授权提示标记。 */
    public static void reset(Context c) {
        GraceEnforcer.cancel(c); // 上一场的宽限闹钟一并作废
        prefs(c).edit()
                .remove("last_cat").remove("streak")
                .remove("grace_cat").remove("grace_until").remove("grace_pkg")
                .remove("inquiry_pkg")
                .remove("cooldown_until")
                .remove("session_allow_cats")
                .remove("noauth_logged")
                .apply();
    }

    /** 本次计时还没拿到截图授权（autoStart 场景）时，记一次日志即可，别刷屏。 */
    public static void noteNoAuthOnce(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("noauth_logged", false)) return;
        p.edit().putBoolean("noauth_logged", true).apply();
        TimerState.logEvent(c, "屏幕内容守卫：本次计时没有截图授权（自动开始的任务无法弹授权框），本次不采样");
    }

    /** 最近一次问询是否由语义判定在降级状态下触发（GuardActivity 诊断行用）。 */
    public static boolean lastInquirySemanticDegraded(Context c) {
        return prefs(c).getBoolean("inquiry_semantic_degraded", false);
    }

    public static void onResult(Context c, ContentAnalyzer.Result r, TaskItem task) {
        SharedPreferences p = prefs(c);
        if (r == null || r.category == null) {
            p.edit().remove("last_cat").remove("streak").apply();
            return;
        }
        String cat = r.category;
        long now = System.currentTimeMillis();

        Set<String> allowed = p.getStringSet("session_allow_cats", new HashSet<>());
        if (allowed.contains(cat)) return;

        String graceCat = p.getString("grace_cat", null);
        if (cat.equals(graceCat)) {
            if (now < p.getLong("grace_until", 0)) return; // 宽限内，不打扰
            // 宽限到期仍命中同类：不再弹窗，按所选方式直接处理（与闹钟到点同一份实现）
            p.edit().putLong("cooldown_until", now + COOLDOWN_MS).apply();
            TimerState.logEvent(c, "屏幕内容守卫：宽限到期后再次命中「" + ContentAnalyzer.label(cat) + "」");
            applyEnforcement(c);
            return;
        }

        if (now < p.getLong("cooldown_until", 0)) return;

        boolean oneShot = ContentAnalyzer.CAT_NSFW.equals(cat) && ScreenGuardPrefs.nsfwOneShot(c);
        if (!oneShot) {
            String lastCat = p.getString("last_cat", null);
            int streak = cat.equals(lastCat) ? p.getInt("streak", 0) + 1 : 1;
            p.edit().putString("last_cat", cat).putInt("streak", streak).apply();
            if (streak < 2) {
                TimerState.logEvent(c, "屏幕内容守卫：嫌疑「" + ContentAnalyzer.label(cat)
                        + "」（第 1 张命中，等下一张确认）" + r.detail);
                return;
            }
        }
        trigger(c, r, task, now);
    }

    private static void trigger(Context c, ContentAnalyzer.Result r, TaskItem task, long now) {
        // 记下触发这一问询时的前台包名：用户若选宽限，宽限状态连它一起存，
        // 到点复查「是不是还停在当时那个分神应用」就靠它比对
        String fg = currentForeground(c);
        SharedPreferences.Editor e = prefs(c).edit().remove("last_cat").remove("streak")
                .putLong("cooldown_until", now + COOLDOWN_MS)
                // 本次问询是否「语义判定触发且当时在降级」（v1.21.4）：只供
                // diag_mode 开启时问询页底部显示诊断行，普通用户永远看不到
                .putBoolean("inquiry_semantic_degraded", "degraded".equals(r.semanticSource));
        if (fg != null) e.putString("inquiry_pkg", fg); else e.remove("inquiry_pkg");
        e.apply();
        GuardEvents.add(c, task, r.category, r.confidence, r.detail);
        TimerState.logEvent(c, "屏幕内容守卫：判定分神「" + ContentAnalyzer.label(r.category)
                + "」（置信 " + String.format(java.util.Locale.US, "%.2f", r.confidence) + "）" + r.detail);
        FloatingWindow.setSuppressed(true);
        AlertHelper.fireGuardContent(c, r.category, r.detail);
    }

    // ---- GuardActivity（content 模式）的三个选择 ----
    public static void contentImmediate(Context c) {
        // 立即回到任务：未决的宽限（若有）一并作废，闹钟取消
        GraceEnforcer.cancel(c);
        prefs(c).edit().remove("grace_cat").remove("grace_until").remove("grace_pkg")
                .remove("inquiry_pkg")
                .putLong("cooldown_until", System.currentTimeMillis() + 8_000L).apply();
        TaskItem t = currentTask(c);
        GuardActions.redirectToTask(c, t);
    }

    public static void contentGrace(Context c, String cat, int seconds) {
        SharedPreferences p = prefs(c);
        String pkg = p.getString("inquiry_pkg", null);
        SharedPreferences.Editor e = p.edit().putString("grace_cat", cat)
                .putLong("grace_until", System.currentTimeMillis() + seconds * 1000L)
                .remove("inquiry_pkg");
        if (pkg != null) e.putString("grace_pkg", pkg); else e.remove("grace_pkg");
        e.apply();
        // 到点主动处理：把闹钟排到 grace_until，不再等采样再次命中（v1.21.3）
        GraceEnforcer.schedule(c);
        boolean quit = ScreenGuardPrefs.graceActionQuit(c);
        TimerState.logEvent(c, "屏幕内容守卫：用户给「" + ContentAnalyzer.label(cat) + "」宽限 " + seconds
                + " 秒，到点处理：" + (quit ? "退掉该软件并跳回任务" : "只放到后台"));
    }

    public static void contentAllow(Context c, String cat) {
        SharedPreferences p = prefs(c);
        Set<String> s = new HashSet<>(p.getStringSet("session_allow_cats", new HashSet<>()));
        s.add(cat);
        p.edit().putStringSet("session_allow_cats", s).remove("inquiry_pkg").apply();
        TimerState.logEvent(c, "屏幕内容守卫：用户放行「" + ContentAnalyzer.label(cat) + "」（本次计时内不再判该类）");
    }

    // ---- 宽限到点处理（v1.21.3）：闹钟到点与「到期再命中」共用这一份 ----

    /**
     * 复查前台并按问询时选定的方式处理：仍停在触发宽限的那个分神应用才动手——
     * 「退掉」＝无障碍 HOME 把它切到后台＋跳回任务软件；「只放后台」＝只 HOME。
     * 已不在那个应用（或拿不到前台）则什么都不做。无论动不动手，宽限状态
     * 与闹钟都在这里清掉，一次宽限只处理一次。处理发生时记 GuardEvents
     * （注明方式）＋任务日志，用户事后看得见。
     */
    public static void applyEnforcement(Context c) {
        SharedPreferences p = prefs(c);
        String cat = p.getString("grace_cat", null);
        String pkg = p.getString("grace_pkg", null);
        if (cat == null) { GraceEnforcer.cancel(c); return; }
        boolean quit = ScreenGuardPrefs.graceActionQuit(c);
        p.edit().remove("grace_cat").remove("grace_until").remove("grace_pkg").apply();
        GraceEnforcer.cancel(c);

        String fg = currentForeground(c);
        TaskItem task = currentTask(c);
        if (pkg != null && (fg == null || !pkg.equals(fg))) {
            TimerState.logEvent(c, "屏幕内容守卫：宽限到点，复查发现已不在「"
                    + AppLister.labelOf(c, pkg) + "」，不处理");
            return;
        }
        String target = pkg != null ? pkg : fg;
        if (target == null) {
            TimerState.logEvent(c, "屏幕内容守卫：宽限到点，但拿不到当前前台应用，不处理");
            return;
        }
        String appName = AppLister.labelOf(c, target);
        boolean homed = GuardActions.pressHome(c);
        if (quit) {
            GuardActions.redirectToTask(c, task);
            GuardEvents.add(c, task, cat, 1f, "宽限到点处理（退掉软件）：仍停留在「" + appName
                    + "」，已切到后台并跳回任务" + (homed ? "" : "（无障碍不可用，仅完成跳回）"));
            TimerState.logEvent(c, "屏幕内容守卫：宽限到点，仍在「" + appName + "」，已退掉该软件并跳回任务");
        } else {
            GuardEvents.add(c, task, cat, 1f, "宽限到点处理（只放后台）：仍停留在「" + appName
                    + "」，已切到后台" + (homed ? "" : "（无障碍不可用，未能切后台）"));
            TimerState.logEvent(c, "屏幕内容守卫：宽限到点，仍在「" + appName + "」，已只放到后台"
                    + (homed ? "" : "（无障碍不可用，未能执行）"));
        }
    }

    /** 当前前台包名：优先无障碍服务的活动窗口，回落使用情况统计（与测试模式同口径）。 */
    private static String currentForeground(Context c) {
        KnowDoAccessibilityService svc = KnowDoAccessibilityService.get();
        if (svc != null && KnowDoAccessibilityService.isReady()) {
            String fg = svc.foregroundPackage();
            if (fg != null) return fg;
        }
        try {
            return new UsageStatsDetector().foregroundPackage(c);
        } catch (Exception e) {
            return null;
        }
    }

    private static TaskItem currentTask(Context c) {
        String id = TimerState.taskId(c);
        if (id == null || id.isEmpty()) return null;
        return new TaskStore(c).byId(id);
    }
}
