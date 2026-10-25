package com.ti.tasktimer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 中央报错通道（v1.24.1）：各模块把内部错误报到这里，统一展示。
 *
 * 展示纪律（用户定案）：
 * - 只在「测试者模式」（ScreenGuardPrefs.diagMode，内部 key diag_mode）开启时
 *   才有任何动作；关着时 report() 是彻底的空操作，既有友好文案与静默降级
 *   行为一律不变。
 * - 开着时：一个悬浮窗贴在当前界面上方约 5 秒自动消失——第一行「来源 ·
 *   HTTP 状态码」，第二行简短说明（代理的 error 字段/异常摘要，截断）。
 *   连续报错只显示最新一条（替换内容并重新计时），点一下立即关掉。
 * - 报错详情只上本机屏幕：不写任何本地持久化、更不写任何会同步上云的字段。
 * - 本类绝不许抛异常：展示失败就静默丢弃，报错通道自己不能成为新的错误源。
 *
 * 展示形态：优先 WindowManager 悬浮层（用户在别的 App 里也能看见，守卫
 * 判定这类后台错误靠它）；没有悬浮窗权限时回落到前台 Activity 的 decorView
 * 顶部横幅（KnowDoApp 记的最近 resumed Activity）；两者都不可用就丢弃。
 * 配色与守卫测试窗一致（深暖炭半透明底＋米白字），不挡核心操作区。
 */
public final class ErrorReporter {
    private static final long SHOW_MS = 5_000;
    private static final int MAX_DETAIL = 120;
    private static final Handler ui = new Handler(Looper.getMainLooper());

    private static WindowManager wm;
    private static View overlayView;       // WindowManager 路径的视图
    private static View activityView;      // Activity 回落路径的视图
    private static Activity activityHost;  // activityView 挂着的 Activity
    private static TextView titleView;
    private static TextView bodyView;
    private static Runnable pendingDismiss;

    private ErrorReporter() { }

    /**
     * 报一条错误（任意线程可调）。httpStatus<=0 表示没有 HTTP 状态码。
     * detail 是简短说明（如代理 error 字段、异常类名＋消息摘要），内部截断。
     */
    public static void report(Context ctx, String source, int httpStatus, String detail) {
        try {
            if (ctx == null) return;
            final Context app = ctx.getApplicationContext();
            if (!ScreenGuardPrefs.diagMode(app)) return;
            final String title = buildTitle(source, httpStatus);
            final String body = truncate(detail);
            ui.post(() -> show(app, title, body));
        } catch (Throwable ignored) {
            // 报错通道自己绝不成为错误源
        }
    }

    /** 把异常摘成一行：类名＋消息摘要（给各接入点共用，保证口径一致）。 */
    public static String summarize(Throwable e) {
        if (e == null) return "";
        String m = e.getMessage();
        String s = e.getClass().getSimpleName();
        if (m != null && !m.trim().isEmpty()) {
            s += "：" + m.trim();
        }
        return truncate(s);
    }

    private static String buildTitle(String source, int httpStatus) {
        String t = (source == null || source.isEmpty()) ? "错误" : source;
        if (httpStatus > 0) t += " · HTTP " + httpStatus;
        return t;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > MAX_DETAIL ? s.substring(0, MAX_DETAIL) + "…" : s;
    }

    // ---------------- 展示 ----------------

    private static void show(Context app, String title, String body) {
        try {
            if (pendingDismiss != null) ui.removeCallbacks(pendingDismiss);
            if (AlertHelper.canDrawOverlays(app)) {
                detachActivityView();
                showOverlay(app, title, body);
            } else {
                detachOverlay();
                showInActivity(title, body);
            }
            pendingDismiss = ErrorReporter::dismiss;
            ui.postDelayed(pendingDismiss, SHOW_MS);
        } catch (Throwable ignored) { }
    }

    public static void dismiss() {
        try {
            if (pendingDismiss != null) {
                ui.removeCallbacks(pendingDismiss);
                pendingDismiss = null;
            }
            detachOverlay();
            detachActivityView();
        } catch (Throwable ignored) { }
    }

    private static void showOverlay(Context app, String title, String body) {
        if (overlayView == null) {
            wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;
            overlayView = buildCard(app);
            float d = app.getResources().getDisplayMetrics().density;
            int screenW = app.getResources().getDisplayMetrics().widthPixels;
            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    screenW - (int) (24 * d), WindowManager.LayoutParams.WRAP_CONTENT, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = (int) (52 * d);
            try {
                wm.addView(overlayView, lp);
            } catch (Exception e) {
                overlayView = null;
                titleView = null;
                bodyView = null;
                showInActivity(title, body); // 悬浮层被系统拒了：回落到 Activity 横幅
                return;
            }
        }
        fill(title, body);
    }

    private static void showInActivity(String title, String body) {
        Activity a = KnowDoApp.currentActivity();
        if (a == null || a.isFinishing() || a.isDestroyed()) return;
        if (activityView == null || activityHost != a) {
            detachActivityView();
            ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
            activityView = buildCard(a);
            float d = a.getResources().getDisplayMetrics().density;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP;
            lp.setMargins((int) (12 * d), (int) (52 * d), (int) (12 * d), 0);
            activityView.setElevation(12 * d);
            decor.addView(activityView, lp);
            activityHost = a;
        }
        fill(title, body);
        activityView.bringToFront();
    }

    /** 造一张报错卡（两条路径共用同一份长相）：标题行＋说明行，点它关掉。 */
    private static View buildCard(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(232, 27, 41, 35));
        bg.setCornerRadius(12 * d);
        root.setBackground(bg);
        root.setPadding((int) (12 * d), (int) (9 * d), (int) (12 * d), (int) (10 * d));
        root.setOnClickListener(v -> dismiss());

        titleView = new TextView(c);
        titleView.setTextColor(Color.rgb(247, 243, 236));
        titleView.setTextSize(13);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setMaxLines(1);
        root.addView(titleView);

        bodyView = new TextView(c);
        bodyView.setTextColor(Color.rgb(226, 224, 218));
        bodyView.setTextSize(12);
        bodyView.setMaxLines(2);
        bodyView.setPadding(0, (int) (3 * d), 0, 0);
        root.addView(bodyView);
        return root;
    }

    private static void fill(String title, String body) {
        if (titleView != null) titleView.setText(title);
        if (bodyView != null) {
            bodyView.setText(body);
            bodyView.setVisibility(body == null || body.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private static void detachOverlay() {
        if (wm != null && overlayView != null) {
            try { wm.removeView(overlayView); } catch (Exception ignored) { }
        }
        overlayView = null;
        if (activityView == null) { titleView = null; bodyView = null; }
    }

    private static void detachActivityView() {
        if (activityView != null && activityView.getParent() instanceof ViewGroup) {
            try { ((ViewGroup) activityView.getParent()).removeView(activityView); }
            catch (Exception ignored) { }
        }
        activityView = null;
        activityHost = null;
        if (overlayView == null) { titleView = null; bodyView = null; }
    }

    /**
     * 截图前临时藏起本窗（与 GuardTestWindow.setCaptureHidden 对齐）：
     * 测试模式借帧时窗里的报错文字别被拍进帧里污染读数。同步等主线程
     * 执行完才返回（超时兜底）；没在显示时直接返回。只藏不关：
     * 5 秒计时照常走，到点该消失还是消失。
     */
    public static void setCaptureHidden(boolean hidden) {
        if (overlayView == null && activityView == null) return;
        final CountDownLatch latch = new CountDownLatch(1);
        ui.post(() -> {
            if (overlayView != null) {
                overlayView.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
            }
            if (activityView != null) {
                activityView.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
            }
            latch.countDown();
        });
        try { latch.await(600, TimeUnit.MILLISECONDS); }
        catch (InterruptedException ignored) { }
    }
}
