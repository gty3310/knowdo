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
 * 立即回到任务 → GuardActions 跳回；给我 30/60 秒 → 该类宽限，
 * 宽限到期后再命中同类 → 不再弹窗，直接跳回；误判放行 → 本次计时内不再判该类。
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
        prefs(c).edit()
                .remove("last_cat").remove("streak")
                .remove("grace_cat").remove("grace_until")
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
            // 宽限到期仍命中同类：直接跳回应该用的软件（与应用级守卫同一语义）
            p.edit().remove("grace_cat").remove("grace_until")
                    .putLong("cooldown_until", now + COOLDOWN_MS).apply();
            GuardEvents.add(c, task, cat, r.confidence, "宽限到期后再次命中：" + r.detail);
            TimerState.logEvent(c, "屏幕内容守卫：宽限到期仍在看「" + ContentAnalyzer.label(cat) + "」，直接跳回任务");
            GuardActions.redirectToTask(c, task);
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
        prefs(c).edit().remove("last_cat").remove("streak")
                .putLong("cooldown_until", now + COOLDOWN_MS).apply();
        GuardEvents.add(c, task, r.category, r.confidence, r.detail);
        TimerState.logEvent(c, "屏幕内容守卫：判定分神「" + ContentAnalyzer.label(r.category)
                + "」（置信 " + String.format(java.util.Locale.US, "%.2f", r.confidence) + "）" + r.detail);
        FloatingWindow.setSuppressed(true);
        AlertHelper.fireGuardContent(c, r.category, r.detail);
    }

    // ---- GuardActivity（content 模式）的三个选择 ----
    public static void contentImmediate(Context c) {
        prefs(c).edit().putLong("cooldown_until", System.currentTimeMillis() + 8_000L).apply();
        TaskItem t = currentTask(c);
        GuardActions.redirectToTask(c, t);
    }

    public static void contentGrace(Context c, String cat, int seconds) {
        prefs(c).edit().putString("grace_cat", cat)
                .putLong("grace_until", System.currentTimeMillis() + seconds * 1000L).apply();
        TimerState.logEvent(c, "屏幕内容守卫：用户给「" + ContentAnalyzer.label(cat) + "」宽限 " + seconds + " 秒");
    }

    public static void contentAllow(Context c, String cat) {
        SharedPreferences p = prefs(c);
        Set<String> s = new HashSet<>(p.getStringSet("session_allow_cats", new HashSet<>()));
        s.add(cat);
        p.edit().putStringSet("session_allow_cats", s).apply();
        TimerState.logEvent(c, "屏幕内容守卫：用户放行「" + ContentAnalyzer.label(cat) + "」（本次计时内不再判该类）");
    }

    private static TaskItem currentTask(Context c) {
        String id = TimerState.taskId(c);
        if (id == null || id.isEmpty()) return null;
        return new TaskStore(c).byId(id);
    }
}
