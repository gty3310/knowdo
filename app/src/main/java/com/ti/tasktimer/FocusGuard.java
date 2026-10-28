package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.HashSet;
import java.util.Set;

/**
 * 专注守卫状态机（轻量版）：
 * 计时进行中每 2 秒检测一次前台应用（ForegroundDetector，可替换实现）；
 * 违反当前任务的专注规则 → 弹全屏问询页（GuardActivity）；
 * 用户选宽限 30/60 秒 → 到期仍在分神应用里 → GuardActions 跳回应该用的软件；
 * 用户自己离开分神应用 → 宽限自动作废；选“误判放行” → 本次计时内不再拦该应用。
 */
public final class FocusGuard {
    private static final ForegroundDetector DETECTOR = new UsageStatsDetector();
    private static long lastEvalMs = 0;

    private FocusGuard() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("guard", Context.MODE_PRIVATE);
    }

    public static boolean detectorReady(Context c) { return DETECTOR.isReady(c); }

    /** 新计时开始时清掉上一场的守卫状态。 */
    public static void reset(Context c) {
        prefs(c).edit()
                .remove("grace_pkg").remove("grace_until")
                .remove("cooldown_until")
                .remove("last_popup_pkg").remove("last_popup_at")
                .remove("list_popup_pkg").remove("list_popup_at").remove("list_popup_task")
                .remove("session_allow")
                .apply();
    }

    // ---- 「回到 Muse」的目标应用（用户在手机上选一次） ----
    public static String musePackage(Context c) {
        return prefs(c).getString("muse_pkg", null);
    }

    public static void setMusePackage(Context c, String pkg) {
        prefs(c).edit().putString("muse_pkg", pkg).apply();
    }

    // ---- GuardActivity 的三个选择 ----
    public static void chooseImmediate(Context c) {
        TaskItem t = currentTask(c);
        clearGrace(c);
        prefs(c).edit().putLong("cooldown_until", System.currentTimeMillis() + 8_000L).apply();
        GuardActions.redirectToTask(c, t);
    }

    public static void chooseGrace(Context c, String pkg, int seconds) {
        prefs(c).edit()
                .putString("grace_pkg", pkg)
                .putLong("grace_until", System.currentTimeMillis() + seconds * 1000L)
                .apply();
        TimerState.logEvent(c, "专注守卫：用户给「" + AppLister.labelOf(c, pkg) + "」宽限 " + seconds + " 秒");
    }

    public static void chooseAllow(Context c, String pkg) {
        Set<String> s = new HashSet<>(prefs(c).getStringSet("session_allow", new HashSet<>()));
        s.add(pkg);
        prefs(c).edit().putStringSet("session_allow", s).apply();
        clearGrace(c);
        TimerState.logEvent(c, "专注守卫：用户放行「" + AppLister.labelOf(c, pkg) + "」（本次计时内不再拦）");
    }

    private static void clearGrace(Context c) {
        prefs(c).edit().remove("grace_pkg").remove("grace_until").apply();
    }

    private static TaskItem currentTask(Context c) {
        String id = TimerState.taskId(c);
        if (id == null || id.isEmpty()) return null;
        return new TaskStore(c).byId(id);
    }

    // ---- 主循环：由 TimerService 每拍调用，内部节流到 2 秒一次 ----
    public static void tick(Context c) {
        long now = System.currentTimeMillis();
        if (now - lastEvalMs < 2_000L) return;
        lastEvalMs = now;
        if (!TimerState.isRunning(c) || TimerState.endFired(c)) return;
        TaskItem task = currentTask(c);
        if (task == null) return;
        if (!DETECTOR.isReady(c)) return;

        String fg = DETECTOR.foregroundPackage(c);
        if (fg == null) return;

        SharedPreferences p = prefs(c);
        String gracePkg = p.getString("grace_pkg", null);
        long graceUntil = p.getLong("grace_until", 0);

        if (isExempt(c, fg, task)) {
            // 回到了正经应用：若宽限针对的是别的包，说明用户已自行离开分神应用，宽限作废
            if (gracePkg != null && !gracePkg.equals(fg)) clearGrace(c);
            return;
        }
        if (p.getStringSet("session_allow", new HashSet<>()).contains(fg)) return;

        // 任务软件名单·白（v1.26.0）：当前任务名单里放行的软件，本次计时内
        // 不再触发专注规则问询（与 session_allow 同位，排在宽限处理之前）
        if (AppLists.TIER_WHITE.equals(AppLists.tierFor(c, task.id, fg))) {
            if (gracePkg != null && !gracePkg.equals(fg)) clearGrace(c);
            return;
        }

        if (gracePkg != null) {
            if (gracePkg.equals(fg)) {
                if (now >= graceUntil) {
                    clearGrace(c);
                    p.edit().putLong("cooldown_until", now + 8_000L).apply();
                    TimerState.logEvent(c, "专注守卫：宽限到期，仍在用「" + AppLister.labelOf(c, fg) + "」，执行跳回");
                    GuardActions.redirectToTask(c, task);
                }
                return; // 宽限内不重复弹窗
            }
            clearGrace(c); // 换了个别的分神应用，按新违规处理
        }

        // 任务软件名单·黑（v1.26.0）：名单（含未生成时的通用兜底）判黑的软件，
        // 一打开就弹问询——不看任务有没有设专注规则。同应用同任务 3 分钟内
        // 不重复弹（防抖）；通话中不抢（GuardDispatch 同口径）；在问询页选过
        // 宽限的，宽限期内由上面的宽限分支兜住、到期照旧跳回。
        if (AppLists.TIER_BLACK.equals(AppLists.tierFor(c, task.id, fg))) {
            if (now < p.getLong("cooldown_until", 0)) return;
            if (GuardDispatch.inCall(c)) return;
            String lp = p.getString("list_popup_pkg", null);
            long lat = p.getLong("list_popup_at", 0);
            String lt = p.getString("list_popup_task", "");
            if (fg.equals(lp) && task.id.equals(lt) && now - lat < 180_000L) return;
            p.edit().putString("list_popup_pkg", fg).putLong("list_popup_at", now)
                    .putString("list_popup_task", task.id).apply();
            TimerState.logEvent(c, "软件名单：打开黑名单应用「" + AppLister.labelOf(c, fg)
                    + "」（任务「" + task.title + "」），弹出问询页");
            FloatingWindow.setSuppressed(true);
            AlertHelper.fireGuard(c, fg, AppLists.reasonFor(c, task.id, fg, task.title));
            return;
        }
        // 灰名单与未分类：不新增打扰，落回既有专注规则判定

        if (task.focusMode == null || "off".equals(task.focusMode)) return;
        if (!isViolation(task, fg)) return;
        if (now < p.getLong("cooldown_until", 0)) return;
        String lastPkg = p.getString("last_popup_pkg", null);
        long lastAt = p.getLong("last_popup_at", 0);
        if (fg.equals(lastPkg) && now - lastAt < PromptRepop.millis(c)) return; // 问询页正在打开/刚打开；划走未解决的重拉间隔（v1.26.3，原固定 12 秒）

        p.edit().putString("last_popup_pkg", fg).putLong("last_popup_at", now).apply();
        TimerState.logEvent(c, "专注守卫：检测到分神应用「" + AppLister.labelOf(c, fg) + "」，弹出问询页");
        FloatingWindow.setSuppressed(true);
        AlertHelper.fireGuard(c, fg);
    }

    private static boolean isViolation(TaskItem task, String fg) {
        if ("block".equals(task.focusMode)) return task.blockedApps.contains(fg);
        if ("allow".equals(task.focusMode)) return !task.allowedApps.contains(fg);
        return false;
    }

    private static boolean isExempt(Context c, String fg, TaskItem task) {
        if (fg.equals(c.getPackageName())) return true;
        if (fg.equals("com.android.systemui") || fg.equals("android")) return true;
        String launcher = AppLister.launcherPackage(c);
        if (launcher != null && fg.equals(launcher)) return true;
        String mp = musePackage(c);
        if (mp != null && fg.equals(mp)) return true; // 与 Muse 对话算元操作，不拦
        if (task.primaryApp != null && fg.equals(task.primaryApp)) return true;
        // 当前输入法不算分神
        try {
            String ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null) {
                int slash = ime.indexOf('/');
                String imePkg = slash > 0 ? ime.substring(0, slash) : ime;
                if (fg.equals(imePkg)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }
}
