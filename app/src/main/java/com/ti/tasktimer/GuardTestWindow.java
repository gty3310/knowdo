package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 守卫测试模式的专用悬浮窗（v1.21.0）：与计时悬浮窗（FloatingWindow）完全独立，
 * 只干一件事——把测试采样每帧的读数（时间/前台应用/读到的文字/判定/证据/连续命中）
 * 原样刷给用户看，让用户自己操控屏幕验收判定质量。
 * 诊断工具定位：数据行可以小字密排，但配色与克制感与全 App 一致
 * （深暖炭半透明底、白字、低饱和）；可拖动；右上角 ✕ 直接关掉测试模式。
 */
public final class GuardTestWindow {
    private static WindowManager wm;
    private static WindowManager.LayoutParams lp;
    private static LinearLayout rootView;
    private static TextView bodyView;
    private static final Handler ui = new Handler(Looper.getMainLooper());

    private GuardTestWindow() {}

    public static boolean isShown() { return rootView != null; }

    public static void show(final Context c) {
        if (rootView != null) return;
        if (!AlertHelper.canDrawOverlays(c)) return;
        ui.post(() -> build(c.getApplicationContext()));
    }

    public static void hide() {
        ui.post(() -> {
            if (wm != null && rootView != null) {
                try { wm.removeView(rootView); } catch (Exception ignored) { }
            }
            rootView = null;
            bodyView = null;
        });
    }

    /** 刷新读数（任意线程可调，内部切主线程）。 */
    public static void update(final String text) {
        ui.post(() -> {
            if (bodyView != null) bodyView.setText(text);
        });
    }

    /**
     * 截图前临时藏起本窗（v1.21.2）：无障碍截图是全屏的，不藏的话测试窗自己的
     * 读数文字会被拍进帧里、污染「读到」与判定。INVISIBLE 保留占位与位置，
     * 帧到手后恢复 VISIBLE。同步等主线程真正执行完才返回（超时兜底），
     * 调用方随即可截图；窗口没在显示时直接返回。
     */
    public static void setCaptureHidden(boolean hidden) {
        if (rootView == null) return;
        final CountDownLatch latch = new CountDownLatch(1);
        ui.post(() -> {
            if (rootView != null) {
                rootView.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
            }
            latch.countDown();
        });
        try { latch.await(600, TimeUnit.MILLISECONDS); }
        catch (InterruptedException ignored) { }
    }

    private static void build(final Context c) {
        if (rootView != null) return;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        float d = c.getResources().getDisplayMetrics().density;

        rootView = new LinearLayout(c);
        rootView.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(228, 34, 32, 29));
        bg.setCornerRadius(14 * d);
        rootView.setBackground(bg);
        rootView.setPadding((int) (12 * d), (int) (9 * d), (int) (12 * d), (int) (11 * d));

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(c);
        title.setText("守卫测试");
        title.setTextColor(Color.rgb(240, 234, 222));
        title.setTextSize(13);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(title);
        TextView close = new TextView(c);
        close.setText("✕");
        close.setTextColor(Color.rgb(190, 186, 178));
        close.setTextSize(14);
        close.setPadding((int) (8 * d), (int) (2 * d), (int) (2 * d), (int) (2 * d));
        close.setOnClickListener(v -> {
            ScreenGuardPrefs.setTestMode(c, false);
            GuardTestMode.stop();
        });
        head.addView(close);
        rootView.addView(head);

        bodyView = new TextView(c);
        bodyView.setText("测试模式已开，正在截第一帧…");
        bodyView.setTextColor(Color.rgb(226, 224, 218));
        bodyView.setTextSize(11.5f);
        bodyView.setLineSpacing(0, 1.25f);
        bodyView.setPadding(0, (int) (6 * d), 0, 0);
        rootView.addView(bodyView);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
                (int) (302 * d), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = (int) (14 * d);
        lp.y = (int) (190 * d); // 让开计时悬浮窗（约 110dp 起），两窗同屏不打架

        rootView.setOnTouchListener(new Dragger());
        try {
            wm.addView(rootView, lp);
        } catch (Exception e) {
            rootView = null;
            bodyView = null;
        }
    }

    /** 简单拖动：整窗可拖（✕ 的点击在子 View 上优先消费，不受影响）。 */
    private static class Dragger implements View.OnTouchListener {
        private int startX, startY;
        private float touchX, touchY;

        @Override public boolean onTouch(View v, MotionEvent e) {
            if (lp == null || rootView == null) return false;
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = lp.x; startY = lp.y;
                    touchX = e.getRawX(); touchY = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x = startX + (int) (e.getRawX() - touchX);
                    lp.y = startY + (int) (e.getRawY() - touchY);
                    try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
                    return true;
                default:
                    return false;
            }
        }
    }
}
