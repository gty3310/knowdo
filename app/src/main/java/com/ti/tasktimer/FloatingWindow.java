package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 计时悬浮窗（以 Floating Notes 的交互为模板）：
 * 三种模式——
 *  吸附 DOCK：贴在屏幕侧边的小标签，只显示剩余分钟数（向上取整，最多 99+）；点它展开，拖离边缘也展开，沿边缘拖动换高度。
 *  小展开 MINI：半透明小窗，只显示任务名 + 倒计时 + 一个 ✏️；点内容区或 ✏️ 直接进入编辑；
 *               拖到屏幕左/右边缘松手就吸附成 DOCK。
 *  编辑 EDIT：不透明度更高的大卡片，笔记就是输入框本身——点笔记区直接打字（无「笔记」按钮），
 *               停笔自动保存（随桥同步）；另有打开知行 / 完成 / 收起。
 * 全屏检查页弹出时自动让位，关掉后自动回来。依赖「上层显示」权限，未授权静默不显示。
 */
public class FloatingWindow {
    public static final int MODE_EDIT = 0;
    public static final int MODE_MINI = 1;
    public static final int MODE_DOCK = 2;

    private static WindowManager wm;
    private static WindowManager.LayoutParams lp;
    private static LinearLayout rootView;
    private static LinearLayout bodyBox;
    private static LinearLayout miniBox;
    private static TextView dockView;
    private static TextView titleView;
    private static TextView timeView;
    private static TextView miniTitle;
    private static TextView miniTime;
    private static TextView saveStateView;
    private static EditText noteEdit;
    private static boolean editing = false;     // 键盘编辑中（窗口可聚焦）
    private static boolean settingText = false; // 程序matic 设置笔记文本时抑制自动保存
    private static boolean suppressed = false;
    private static boolean noteDirty = false;
    private static String cachedTaskId = null;
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static Runnable pendingSave;

    // ---------- 开关与模式 ----------
    public static boolean enabled(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("float_enabled", true);
    }

    public static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("float_enabled", on).apply();
        if (!on) hide();
    }

    private static int mode(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("float_mode2", MODE_MINI);
    }

    private static void setMode(Context c, int m) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putInt("float_mode2", m).apply();
        if (m != MODE_EDIT) endEditFocus(c);
        applyMode(c);
    }

    private static int dockSide(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("float_dock_side", 1); // 0 左 1 右
    }

    private static void setDockSide(Context c, int s) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putInt("float_dock_side", s).apply();
    }

    private static void applyMode(Context c) {
        if (rootView == null || lp == null) return;
        int m = mode(c);
        float d = c.getResources().getDisplayMetrics().density;
        bodyBox.setVisibility(m == MODE_EDIT ? View.VISIBLE : View.GONE);
        miniBox.setVisibility(m == MODE_MINI ? View.VISIBLE : View.GONE);
        dockView.setVisibility(m == MODE_DOCK ? View.VISIBLE : View.GONE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(m == MODE_EDIT ? Color.argb(240, 33, 33, 33)
                : m == MODE_MINI ? Color.argb(148, 33, 33, 33)
                : Color.argb(170, 33, 33, 33));
        bg.setCornerRadius((m == MODE_DOCK ? 14 : 16) * d);
        rootView.setBackground(bg);
        if (m == MODE_DOCK) rootView.setPadding((int) (4 * d), (int) (10 * d), (int) (4 * d), (int) (10 * d));
        else if (m == MODE_MINI) rootView.setPadding((int) (10 * d), (int) (7 * d), (int) (10 * d), (int) (7 * d));
        else {
            int pad = (int) (12 * d);
            rootView.setPadding(pad, (int) (8 * d), pad, pad);
        }
        lp.width = m == MODE_EDIT ? (int) (292 * d)
                : m == MODE_MINI ? (int) (244 * d)
                : (int) (46 * d);
        if (m == MODE_DOCK) lp.x = dockX(c);
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
    }

    private static int dockX(Context c) {
        int w = c.getResources().getDisplayMetrics().widthPixels;
        float d = c.getResources().getDisplayMetrics().density;
        return dockSide(c) == 0 ? 0 : w - (int) (46 * d);
    }

    /** 预览（权限页用）：无需计时，显示 10 秒样例。 */
    public static void showPreview(final Context c) {
        if (!AlertHelper.canDrawOverlays(c)) {
            android.widget.Toast.makeText(c, "先把上面的「上层显示」权限开成 ✓，悬浮窗才能显示", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        hide();
        build(c.getApplicationContext());
        if (rootView == null) {
            android.widget.Toast.makeText(c, "悬浮窗创建失败，请把现象告诉 Muse", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        if (titleView != null) titleView.setText("预览：这里显示当前任务名");
        if (timeView != null) timeView.setText("24:59");
        if (miniTitle != null) miniTitle.setText("预览任务");
        if (miniTime != null) miniTime.setText("24:59");
        if (dockView != null) dockView.setText("25");
        settingText = true;
        if (noteEdit != null) noteEdit.setText("这是预览。现在默认是「小展开」模式：点它直接进来写字，停笔自动保存；拖到屏幕边缘松手就吸附。");
        settingText = false;
        ui.postDelayed(() -> { if (!TimerState.isRunning(c)) hide(); }, 10_000);
    }

    /** 检查页在前台时让位；检查页销毁后由 TimerService 的节拍自动恢复显示。 */
    public static void setSuppressed(boolean s) {
        suppressed = s;
        if (s) hideInternal();
    }

    // ---------- 显示/隐藏 ----------
    public static void ensureShown(Context c) {
        if (!enabled(c) || suppressed || rootView != null) return;
        if (!AlertHelper.canDrawOverlays(c)) return;
        build(c.getApplicationContext());
    }

    public static void update(Context c, String remainText) {
        if (rootView == null) return;
        String title = TimerState.taskTitle(c);
        if (titleView != null) titleView.setText(title);
        if (timeView != null) timeView.setText(remainText);
        if (miniTitle != null) miniTitle.setText(title);
        if (miniTime != null) miniTime.setText(remainText);
        if (dockView != null) {
            long remainMs = TimerState.endAt(c) - System.currentTimeMillis();
            long mins = Math.max(1, (remainMs + 59_999) / 60_000);
            dockView.setText(mins > 99 ? "99+" : String.valueOf(mins));
        }
        // 任务切换时才重载笔记，避免打断正在输入的内容
        String id = TimerState.taskId(c);
        if (noteEdit != null && (cachedTaskId == null || !cachedTaskId.equals(id))) {
            cachedTaskId = id;
            TaskItem t = currentTask(c);
            settingText = true;
            noteEdit.setText(t == null || t.note == null ? "" : t.note);
            settingText = false;
        }
    }

    public static void hide() {
        suppressed = false;
        hideInternal();
    }

    private static void hideInternal() {
        if (pendingSave != null) { ui.removeCallbacks(pendingSave); pendingSave = null; }
        if (wm != null && rootView != null) {
            try { wm.removeView(rootView); } catch (Exception ignored) { }
        }
        rootView = null;
        editing = false;
        noteDirty = false;
        cachedTaskId = null;
    }

    private static TaskItem currentTask(Context c) {
        String id = TimerState.taskId(c);
        if (id == null || id.isEmpty()) return null;
        return new TaskStore(c).byId(id);
    }

    // ---------- 构建 ----------
    private static void build(final Context c) {
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        float d = c.getResources().getDisplayMetrics().density;

        rootView = new LinearLayout(c);
        rootView.setOrientation(LinearLayout.VERTICAL);

        // —— 吸附模式：边缘小标签 ——
        dockView = new TextView(c);
        dockView.setText("25");
        dockView.setTextColor(Color.WHITE);
        dockView.setTextSize(16);
        dockView.setTypeface(dockView.getTypeface(), android.graphics.Typeface.BOLD);
        dockView.setGravity(Gravity.CENTER);
        dockView.setVisibility(View.GONE);
        rootView.addView(dockView);

        // —— 小展开模式：任务名 + 倒计时 + ! ——
        miniBox = new LinearLayout(c);
        miniBox.setOrientation(LinearLayout.HORIZONTAL);
        miniBox.setGravity(Gravity.CENTER_VERTICAL);
        miniBox.setVisibility(View.GONE);
        miniTitle = new TextView(c);
        miniTitle.setTextColor(Color.WHITE);
        miniTitle.setTextSize(13);
        miniTitle.setMaxLines(1);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        miniBox.addView(miniTitle);
        miniTime = new TextView(c);
        miniTime.setTextColor(Color.WHITE);
        miniTime.setTextSize(16);
        miniTime.setTypeface(miniTime.getTypeface(), android.graphics.Typeface.BOLD);
        miniTime.setPadding(dp(c, 6), 0, dp(c, 2), 0);
        miniBox.addView(miniTime);
        TextView bang = new TextView(c);
        bang.setText("✎");
        bang.setTextColor(Color.rgb(220, 220, 220));
        bang.setTextSize(17);
        bang.setPadding(dp(c, 8), dp(c, 2), dp(c, 4), dp(c, 2));
        bang.setOnClickListener(v -> openEdit(c));
        miniBox.addView(bang);
        rootView.addView(miniBox);

        // —— 编辑模式：完整卡片，笔记即输入框 ——
        bodyBox = new LinearLayout(c);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        rootView.addView(bodyBox);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = new TextView(c);
        tag.setText("知行 · 计时中");
        tag.setTextColor(Color.rgb(176, 176, 176));
        tag.setTextSize(11);
        tag.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(tag);
        TextView shrinkBtn = smallLabel(c, "收起 —");
        shrinkBtn.setOnClickListener(v -> setMode(c, MODE_MINI));
        head.addView(shrinkBtn);
        bodyBox.addView(head);

        titleView = new TextView(c);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setMaxLines(2);
        bodyBox.addView(titleView);

        timeView = new TextView(c);
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(32);
        timeView.setTypeface(timeView.getTypeface(), android.graphics.Typeface.BOLD);
        bodyBox.addView(timeView);

        noteEdit = new EditText(c);
        noteEdit.setTextColor(Color.WHITE);
        noteEdit.setHintTextColor(Color.rgb(150, 150, 150));
        noteEdit.setHint("点这里直接写笔记（自动保存）");
        noteEdit.setTextSize(14);
        noteEdit.setMinLines(2);
        noteEdit.setGravity(Gravity.TOP);
        noteEdit.setBackgroundColor(Color.TRANSPARENT);
        noteEdit.setPadding(0, dp(c, 4), 0, 0);
        noteEdit.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN && !editing) beginEditFocus(c);
            return false; // 让 EditText 自己处理（放光标、选字）
        });
        noteEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void afterTextChanged(Editable s) {
                if (settingText || !editing) return;
                noteDirty = true;
                if (saveStateView != null) saveStateView.setText("正在保存…");
                if (pendingSave != null) ui.removeCallbacks(pendingSave);
                pendingSave = () -> persistNote(c, false);
                ui.postDelayed(pendingSave, 700);
            }
        });
        bodyBox.addView(noteEdit);

        saveStateView = new TextView(c);
        saveStateView.setText("点笔记区直接写，停笔自动保存");
        saveStateView.setTextColor(Color.rgb(150, 150, 150));
        saveStateView.setTextSize(11);
        bodyBox.addView(saveStateView);

        LinearLayout btns = new LinearLayout(c);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button openBtn = smallButton(c, "打开知行");
        openBtn.setOnClickListener(v -> {
            Intent i = new Intent(c, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        });
        btns.addView(openBtn);
        Button doneBtn = smallButton(c, "完成");
        doneBtn.setOnClickListener(v -> {
            setSuppressed(true);
            Intent i = new Intent(c, CheckActivity.class);
            i.putExtra(CheckActivity.EXTRA_TYPE, AlertHelper.TYPE_END);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        });
        btns.addView(doneBtn);
        Button miniBtn = smallButton(c, "收起");
        miniBtn.setOnClickListener(v -> setMode(c, MODE_MINI));
        btns.addView(miniBtn);
        bodyBox.addView(btns);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
                (int) (244 * d), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = (int) (14 * d);
        lp.y = (int) (110 * d);

        rootView.setOnTouchListener(new Dragger(c));
        try {
            wm.addView(rootView, lp);
        } catch (Exception e) {
            rootView = null;
            return;
        }
        applyMode(c);
    }

    /** 进入编辑：展开卡片 + 笔记输入框立刻获得焦点并弹键盘（Floating Notes 式点即写）。 */
    private static void openEdit(Context c) {
        setMode(c, MODE_EDIT);
        ui.postDelayed(() -> beginEditFocus(c), 60);
    }

    private static void beginEditFocus(Context c) {
        if (rootView == null || noteEdit == null) return;
        if (mode(c) != MODE_EDIT) setMode(c, MODE_EDIT);
        editing = true;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
        noteEdit.requestFocus();
        noteEdit.setSelection(noteEdit.getText().length());
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) ui.postDelayed(() -> imm.showSoftInput(noteEdit, InputMethodManager.SHOW_IMPLICIT), 80);
    }

    private static void endEditFocus(Context c) {
        if (!editing) return;
        if (pendingSave != null) { ui.removeCallbacks(pendingSave); pendingSave = null; }
        persistNote(c, true);
        editing = false;
        noteDirty = false;
        if (lp == null || rootView == null) return;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = 0;
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
    }

    private static void persistNote(Context c, boolean fromEnd) {
        if (noteEdit == null) return;
        String id = TimerState.taskId(c);
        if (id == null || id.isEmpty()) return;
        TaskStore store = new TaskStore(c);
        TaskItem t = store.byId(id);
        if (t == null) return;
        String v = noteEdit.getText().toString().trim();
        if (!v.equals(t.note == null ? "" : t.note)) {
            t.note = v;
            t.touch();
            store.save();
        }
        if (saveStateView != null) saveStateView.setText("已保存 ✓");
        if (fromEnd && noteDirty) {
            TimerState.logEvent(c, "悬浮窗笔记[" + t.title + "]：" + v);
            final Context app = c.getApplicationContext();
            new Thread(() -> SyncManager.syncNow(app, new TaskStore(app))).start();
        }
    }

    /** 拖动：三模式共用。EDIT 只移动；MINI 点按=进编辑、拖到边缘=吸附；DOCK 点按/拖离=展开。 */
    private static class Dragger implements View.OnTouchListener {
        private final Context c;
        private int startX, startY;
        private float touchX, touchY;
        private boolean moved;

        Dragger(Context c) { this.c = c; }

        @Override public boolean onTouch(View v, MotionEvent e) {
            if (lp == null || rootView == null) return false;
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = lp.x; startY = lp.y;
                    touchX = e.getRawX(); touchY = e.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (e.getRawX() - touchX);
                    int dy = (int) (e.getRawY() - touchY);
                    if (Math.abs(dx) + Math.abs(dy) > 6) moved = true;
                    lp.x = startX + dx;
                    lp.y = startY + dy;
                    try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
                    return true;
                case MotionEvent.ACTION_UP:
                    onUp(dx(), dy());
                    return true;
                default:
                    return false;
            }
        }

        private int dx() { return lp.x - startX; }
        private int dy() { return lp.y - startY; }

        private void onUp(int dx, int dy) {
            int m = mode(c);
            float d = c.getResources().getDisplayMetrics().density;
            int screenW = c.getResources().getDisplayMetrics().widthPixels;
            int viewW = rootView.getWidth();
            if (m == MODE_DOCK) {
                int side = dockSide(c);
                boolean inward = side == 1 ? dx < -(int) (60 * d) : dx > (int) (60 * d);
                if (!moved) expandFromDock(c);
                else if (inward) {
                    setMode(c, MODE_MINI);
                } else {
                    lp.x = dockX(c); // 弹回边缘
                    try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
                }
            } else if (m == MODE_MINI) {
                if (!moved) { openEdit(c); return; }
                if (lp.x <= (int) (18 * d)) {
                    setDockSide(c, 0);
                    lp.y = Math.max(0, lp.y);
                    setMode(c, MODE_DOCK);
                } else if (lp.x + viewW >= screenW - (int) (18 * d)) {
                    setDockSide(c, 1);
                    lp.y = Math.max(0, lp.y);
                    setMode(c, MODE_DOCK);
                }
            }
            // EDIT：只移动，不做模式切换
        }

        private void expandFromDock(Context c) {
            float d = c.getResources().getDisplayMetrics().density;
            int screenW = c.getResources().getDisplayMetrics().widthPixels;
            if (dockSide(c) == 1) lp.x = screenW - (int) (244 * d) - (int) (10 * d);
            else lp.x = (int) (10 * d);
            setMode(c, MODE_MINI);
        }
    }

    // ---------- 小件 ----------
    private static TextView smallLabel(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(Color.rgb(176, 176, 176));
        t.setTextSize(12);
        t.setPadding(dp(c, 6), dp(c, 4), dp(c, 2), dp(c, 4));
        return t;
    }

    private static Button smallButton(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setTextSize(13);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, (int) (40 * c.getResources().getDisplayMetrics().density), 1f);
        p.setMargins(dp(c, 2), dp(c, 6), dp(c, 2), 0);
        b.setLayoutParams(p);
        return b;
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
