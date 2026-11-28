package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 悬浮窗聊天（v2.2.0）：小悬浮窗「聊一聊」钮打开的聊天大窗——独立
 * WindowManager 窗口，不走 BigOverlay 的 99% 盖屏盒（那是问询/检查页的
 * 模态形态），两者互不干扰。
 *
 * 形态口径（用户 2026-11-28 亲定）：
 * - 默认大号：屏宽 85% × 屏高 60%；用户可拖右下角改大小、可拖标题栏移位，
 *   尺寸与位置存本地（SharedPreferences「ui」），下次打开照旧（本地先行）。
 * - 不挡下面软件的使用：FLAG_NOT_TOUCH_MODAL 常驻，窗外触摸穿透到下面的
 *   App；键盘焦点只在点中输入框时才取——沿用 FloatingWindow.beginEditFocus
 *   的既有口径：平时 FLAG_NOT_FOCUSABLE，输入框 ACTION_DOWN 时才清掉该
 *   flag＋requestFocus＋showSoftInput，点头部栏/收起时再恢复。
 * - 聊天本体直接复用 ChatPanel（与聊聊页签/ChatActivity 同一组件、同一条
 *   ChatStore.MAIN 主线、同一套提案卡），不另写第二份；打开时若有正在
 *   计时/暂停的任务，自动带上它作上下文，改时长/改标题走 update 提案卡。
 */
public final class ChatOverlay {

    private static WindowManager wm;
    private static WindowManager.LayoutParams lp;
    private static FrameLayout root;
    private static ChatPanel panel;
    private static EditText chatInput;
    private static boolean focusable = false;
    private static final Handler ui = new Handler(Looper.getMainLooper());

    private ChatOverlay() { }

    public static boolean isShowing() { return root != null; }

    public static void toggle(Context c) {
        if (isShowing()) hide();
        else show(c);
    }

    public static void show(Context c) {
        if (c == null || root != null) return;
        if (!AlertHelper.canDrawOverlays(c)) {
            Toast.makeText(c, Strings.en()
                    ? "Turn on the overlay permission first, then the chat window can float"
                    : "先把悬浮窗权限打开，聊天窗才能浮起来", Toast.LENGTH_LONG).show();
            return;
        }
        final Context app = c.getApplicationContext();
        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        DisplayMetrics dm = app.getResources().getDisplayMetrics();
        SharedPreferences prefs = app.getSharedPreferences("ui", Context.MODE_PRIVATE);
        int w = clampW(app, prefs.getInt("chatwin_w", Math.round(dm.widthPixels * 0.85f)));
        int h = clampH(app, prefs.getInt("chatwin_h", Math.round(dm.heightPixels * 0.60f)));
        int x = prefs.getInt("chatwin_x", Math.max(0, (dm.widthPixels - w) / 2));
        int y = prefs.getInt("chatwin_y", Math.round(dm.heightPixels * 0.16f));
        x = Math.max(0, Math.min(x, Math.max(0, dm.widthPixels - w)));
        y = Math.max(0, Math.min(y, Math.max(0, dm.heightPixels - h)));

        root = new FrameLayout(app);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Ui.bg(app));
        frame.setCornerRadius(dp(app, 18));
        frame.setStroke(Math.max(1, dp(app, 1)), Ui.line(app));
        root.setBackground(frame);
        root.setPadding(dp(app, 4), dp(app, 4), dp(app, 4), dp(app, 4));

        LinearLayout col = new LinearLayout(app);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 头部栏：标题（兼移位拖柄）＋改大小提示＋收起
        LinearLayout header = new LinearLayout(app);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(app, 14), dp(app, 8), dp(app, 8), dp(app, 2));
        TextView title = new TextView(app);
        title.setText(Strings.chatOverlayTitle());
        title.setTextSize(15);
        title.setTextColor(Ui.text(app));
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);
        TextView hint = new TextView(app);
        hint.setText(Strings.chatResizeHint());
        hint.setTextSize(10.5f);
        hint.setTextColor(Ui.sub(app));
        hint.setPadding(0, 0, dp(app, 8), 0);
        header.addView(hint);
        TextView close = new TextView(app);
        close.setText(Strings.chatOverlayClose());
        close.setTextSize(13.5f);
        close.setTextColor(Ui.accent(app));
        close.setPadding(dp(app, 10), dp(app, 4), dp(app, 6), dp(app, 4));
        close.setOnClickListener(v -> hide());
        header.addView(close);
        col.addView(header);
        title.setOnTouchListener(new MoveDragger(app));

        // 聊天本体：ChatPanel 填满剩余空间
        LinearLayout host = new LinearLayout(app);
        host.setOrientation(LinearLayout.VERTICAL);
        col.addView(host, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        panel = new ChatPanel(app, host, false);
        FloatingWindow.Display d = FloatingWindow.resolveDisplay(app);
        if (d != null && d.task != null
                && (d.state == FloatingWindow.ST_RUNNING || d.state == FloatingWindow.ST_PAUSED)) {
            panel.setTaskContext(d.task.id);
        }
        chatInput = findEditText(root);
        if (chatInput != null) {
            chatInput.setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN) enterFocus(app);
                return false;
            });
        }

        // 右下角改大小拖柄（朱砂红小三角意象，用字形＋点缀色，不另画图）
        TextView grip = new TextView(app);
        grip.setText("◢");
        grip.setTextSize(15);
        grip.setTextColor(Ui.accent(app));
        grip.setGravity(Gravity.BOTTOM | Gravity.END);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(app, 34), dp(app, 34));
        glp.gravity = Gravity.BOTTOM | Gravity.END;
        grip.setLayoutParams(glp);
        grip.setOnTouchListener(new ResizeDragger(app));
        root.addView(grip);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(w, h, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = x;
        lp.y = y;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        focusable = false;
        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            panel.detach();
            panel = null;
            root = null;
            chatInput = null;
        }
    }

    public static void hide() {
        if (panel != null) {
            panel.detach();
            panel = null;
        }
        if (wm != null && root != null) {
            try { wm.removeView(root); } catch (Exception ignored) { }
        }
        root = null;
        chatInput = null;
        focusable = false;
    }

    // ---------------- 焦点（沿用 FloatingWindow.beginEditFocus 口径） ----------------

    private static void enterFocus(final Context app) {
        if (lp == null || root == null || focusable) return;
        focusable = true;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
        updateLayout();
        if (chatInput != null) {
            chatInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) app.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                ui.postDelayed(() -> imm.showSoftInput(chatInput, InputMethodManager.SHOW_IMPLICIT), 80);
            }
        }
    }

    /** 点标题栏时把键盘焦点还回去（窗外触摸本就穿透，这里只管键盘）。 */
    private static void exitFocus(Context app) {
        if (lp == null || root == null || !focusable) return;
        focusable = false;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        if (chatInput != null) {
            InputMethodManager imm = (InputMethodManager) app.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(chatInput.getWindowToken(), 0);
            chatInput.clearFocus();
        }
        updateLayout();
    }

    private static void updateLayout() {
        if (wm == null || root == null || lp == null) return;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
    }

    private static void persist(Context app) {
        if (lp == null) return;
        app.getSharedPreferences("ui", Context.MODE_PRIVATE).edit()
                .putInt("chatwin_w", lp.width)
                .putInt("chatwin_h", lp.height)
                .putInt("chatwin_x", lp.x)
                .putInt("chatwin_y", lp.y)
                .apply();
    }

    // ---------------- 拖动 ----------------

    /** 拖标题栏移位；点一下（没拖动）顺手把键盘焦点还回去。 */
    private static final class MoveDragger implements View.OnTouchListener {
        private final Context app;
        private int startX, startY, origX, origY;
        private boolean moved;

        MoveDragger(Context app) { this.app = app; }

        @Override public boolean onTouch(View v, MotionEvent e) {
            if (lp == null) return false;
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = (int) e.getRawX();
                    startY = (int) e.getRawY();
                    origX = lp.x;
                    origY = lp.y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) e.getRawX() - startX;
                    int dy = (int) e.getRawY() - startY;
                    if (Math.abs(dx) + Math.abs(dy) > 6) moved = true;
                    DisplayMetrics dm = app.getResources().getDisplayMetrics();
                    lp.x = Math.max(0, Math.min(origX + dx, Math.max(0, dm.widthPixels - lp.width)));
                    lp.y = Math.max(0, Math.min(origY + dy, Math.max(0, dm.heightPixels - lp.height)));
                    updateLayout();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) persist(app);
                    else exitFocus(app);
                    return true;
                default:
                    return false;
            }
        }
    }

    /** 拖右下角改大小：跟手改宽高，松手持久化。 */
    private static final class ResizeDragger implements View.OnTouchListener {
        private final Context app;
        private int startX, startY, origW, origH;

        ResizeDragger(Context app) { this.app = app; }

        @Override public boolean onTouch(View v, MotionEvent e) {
            if (lp == null) return false;
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = (int) e.getRawX();
                    startY = (int) e.getRawY();
                    origW = lp.width;
                    origH = lp.height;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.width = clampW(app, origW + (int) e.getRawX() - startX);
                    lp.height = clampH(app, origH + (int) e.getRawY() - startY);
                    updateLayout();
                    return true;
                case MotionEvent.ACTION_UP:
                    persist(app);
                    return true;
                default:
                    return false;
            }
        }
    }

    // ---------------- 小工具 ----------------

    private static int clampW(Context c, int w) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int min = dp(c, 240);
        int max = Math.round(dm.widthPixels * 0.96f);
        return Math.max(min, Math.min(w, Math.max(min, max)));
    }

    private static int clampH(Context c, int h) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int min = dp(c, 300);
        int max = Math.round(dm.heightPixels * 0.92f);
        return Math.max(min, Math.min(h, Math.max(min, max)));
    }

    private static EditText findEditText(View v) {
        if (v instanceof EditText) return (EditText) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                EditText found = findEditText(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
