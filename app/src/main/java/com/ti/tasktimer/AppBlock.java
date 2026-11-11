package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 分神处置「盖住 3 分钟」（v1.80.0）：宽限到点且处置方式＝盖住时，把触发
 * 的那个软件盖住固定 3 分钟。状态存 prefs「screenguard_state」（与内容
 * 守卫的宽限状态同处）：block_pkg／block_until。盖屏是 BlockActivity：
 * 前台检测链（无障碍窗口事件＋FocusGuard 每拍）发现前台回到 block_pkg
 * 就拉起它、离开就收起；到点由 BlockEnforcer 的精确闹钟清状态（盖屏
 * 自己的倒计时也会收尾）。本包永远不封。时长是常量、不做设置项
 * （用户定案：固定 3 分钟）。
 */
public final class AppBlock {
    /** 盖住时长：固定 3 分钟（用户定案，不做设置项）。 */
    public static final long BLOCK_MS = 3 * 60_000L;
    private static final String PREF = "screenguard_state";

    private AppBlock() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean isActive(Context c) {
        SharedPreferences p = prefs(c);
        return p.getString("block_pkg", null) != null
                && p.getLong("block_until", 0) > System.currentTimeMillis();
    }

    /** 正在被盖的包名；没在盖时为 null。 */
    public static String activePkg(Context c) {
        return isActive(c) ? prefs(c).getString("block_pkg", null) : null;
    }

    public static long until(Context c) {
        return prefs(c).getLong("block_until", 0);
    }

    /**
     * 开始盖住：记状态、排到点清状态的闹钟，并立刻尝试拉起盖屏（调用方
     * 已复查过前台仍停在该软件；用户切走再切回时由前台检测链再拉起）。
     */
    public static void start(Context c, String pkg) {
        if (pkg == null || pkg.equals(c.getPackageName())) return; // 本包永远不封
        prefs(c).edit()
                .putString("block_pkg", pkg)
                .putLong("block_until", System.currentTimeMillis() + BLOCK_MS)
                .apply();
        BlockEnforcer.schedule(c);
        show(c);
    }

    /** 清掉盖住状态（到点／设置页提前解封共用）：撤闹钟、收盖屏、撤通知。 */
    public static void clear(Context c) {
        prefs(c).edit().remove("block_pkg").remove("block_until").apply();
        BlockEnforcer.cancel(c);
        BlockActivity.dismissCurrent();
        AlertHelper.cancelBlock(c);
    }

    /**
     * 前台变化时调用（无障碍窗口事件与 FocusGuard 每拍各一条链路）：
     * 返回 true 表示本次前台已被盖住逻辑接管，调用方不要再触发问询。
     * 盖住已过期→清状态；前台正是被盖软件→拉起盖屏；前台是别的软件
     * （不是本包自己的页面）→收起盖屏。
     */
    public static boolean checkForeground(Context c, String fg) {
        SharedPreferences p = prefs(c);
        String pkg = p.getString("block_pkg", null);
        if (pkg == null) return false;
        if (p.getLong("block_until", 0) <= System.currentTimeMillis()) {
            clear(c);
            return false;
        }
        if (fg == null) return false;
        if (fg.equals(pkg)) {
            show(c);
            return true;
        }
        if (!fg.equals(c.getPackageName())) BlockActivity.dismissCurrent();
        return false;
    }

    /** 拉起盖屏（已在显示则不重复拉）：走 AlertHelper 的全屏出口。 */
    public static void show(Context c) {
        if (BlockActivity.isShowing()) return;
        AlertHelper.fireBlock(c);
    }
}
