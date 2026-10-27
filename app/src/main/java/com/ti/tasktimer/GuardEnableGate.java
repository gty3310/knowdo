package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;

/**
 * 开启屏幕内容守卫的统一闸门（v1.21.4）：守卫的黄色识别模型改为按需
 * 下载后，「开守卫」多了一步前置——模型没下好不能直接开。设置页与
 * 权限页两处总开关都走这里：
 * - 模型已就绪：照旧开启（并在计时中时启动采样），返回 true；
 * - 未就绪：不开启，弹下载引导（说清用途与大小），用户点「去下载」
 *   跳模型下载页并记下待开启标记——下载完成由 ModelActivity 自动
 *   把守卫开启；用户选「先不开启」则守卫保持关闭并说明原因。
 * 引导只在用户主动点开开关时出现一次，不反复催。
 */
public final class GuardEnableGate {
    private static final String PREF = "screenguard";
    private static final String KEY_PENDING = "guard_enable_pending";

    private GuardEnableGate() {}

    public static boolean pendingEnable(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_PENDING, false);
    }

    private static void setPending(Context c, boolean v) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_PENDING, v).apply();
    }

    /** 模型下载完成后的收尾：此前有待开启的守卫就把它开启。返回是否开启了。 */
    public static boolean completePendingEnable(Context c) {
        if (!pendingEnable(c)) return false;
        if (!ModelManager.isReady(c, ModelManager.ID_NUDENET)) return false;
        setPending(c, false);
        ScreenGuardPrefs.setEnabled(c, true);
        if (TimerState.isRunning(c)) KnowDoAccessibilityService.onTimerStarted(c);
        return true;
    }

    /**
     * 尝试开启守卫。onEnabled 在真正开启后回调（给调用方弹提示/重画）。
     * 返回 true＝已开启；false＝被闸门拦下（已弹引导或源未配置提示）。
     */
    public static boolean tryEnable(final Activity act, final Runnable onEnabled) {
        if (ModelManager.isReady(act, ModelManager.ID_NUDENET)) {
            ScreenGuardPrefs.setEnabled(act, true);
            if (TimerState.isRunning(act)) KnowDoAccessibilityService.onTimerStarted(act);
            if (onEnabled != null) onEnabled.run();
            return true;
        }
        boolean en = Strings.en();
        ModelManager.Model m = ModelManager.find(act, ModelManager.ID_NUDENET);
        String size = m != null ? ModelManager.sizeText(m.totalBytes()) : "12 MB";
        String msg = en
                ? "The screen-content guard needs its image model first (about " + size
                + "). It spots explicit or barely-dressed images on screen. Download it "
                + "and the guard turns on right away. Until then the guard stays off."
                : "开启屏幕内容守卫前，要先下载黄色识别模型（约 " + size + "）。"
                + "它负责认出屏幕上的黄色与暴露画面。下载完成守卫会随即开启；"
                + "在那之前守卫保持关闭。";
        new AlertDialog.Builder(act)
                .setTitle(en ? "Download the model first" : "先下载模型")
                .setMessage(msg)
                .setPositiveButton(en ? "Go download" : "去下载", (d, w) -> {
                    setPending(act, true);
                    act.startActivity(new Intent(act, ModelActivity.class));
                })
                .setNegativeButton(en ? "Not now" : "先不开启", (d, w) -> setPending(act, false))
                .show();
        return false;
    }
}
