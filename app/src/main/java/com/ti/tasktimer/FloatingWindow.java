package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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
 * 计时悬浮窗（借鉴 Floating Notes 一类应用的做法）：
 * 计时进行中在任何界面之上显示一张可拖动的深色卡片——任务名、大字倒计时、任务笔记；
 * 可收起成只显示时间的小药丸；点「笔记」可直接在悬浮窗里写并保存（随桥同步）；
 * 点「完成」打开结束检查页；全屏检查页弹出时自动让位隐藏，检查页关掉后自动回来。
 * 依赖「上层显示」权限（与亮屏全屏共用同一项授权），未授权时静默不显示。
 */
public class FloatingWindow {
    private static WindowManager wm;
    private static WindowManager.LayoutParams lp;
    private static LinearLayout rootView;
    private static LinearLayout bodyBox;
    private static LinearLayout editBox;
    private static TextView titleView;
    private static TextView timeView;
    private static TextView noteView;
    private static TextView pillView;
    private static EditText editText;
    private static boolean collapsed = false;
    private static boolean editing = false;
    private static boolean suppressed = false;

    // ---------- 开关 ----------
    public static boolean enabled(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("float_enabled", true);
    }

    public static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("float_enabled", on).apply();
        if (!on) hide();
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

    private static String cachedTaskId = null;

    public static void update(Context c, String remainText) {
        if (rootView == null) return;
        if (titleView != null) titleView.setText(TimerState.taskTitle(c));
        if (timeView != null && !editing) timeView.setText(remainText);
        if (pillView != null) pillView.setText("⏱ " + remainText);
        // 笔记只在任务切换时重读，避免每 250ms 解析一次本地文件
        String id = TimerState.taskId(c);
        if (noteView != null && !editing && (cachedTaskId == null || !cachedTaskId.equals(id))) {
            cachedTaskId = id;
            noteView.setText(noteText(c));
        }
    }

    public static void hide() {
        suppressed = false;
        hideInternal();
    }

    private static void hideInternal() {
        if (wm != null && rootView != null) {
            try { wm.removeView(rootView); } catch (Exception ignored) { }
        }
        rootView = null;
        editing = false;
        collapsed = false;
        cachedTaskId = null;
    }

    private static String noteText(Context c) {
        TaskItem t = currentTask(c);
        String n = t == null ? "" : t.note;
        return (n == null || n.trim().isEmpty()) ? "点「笔记」给这个任务写点什么" : n;
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
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(238, 33, 33, 33));
        bg.setCornerRadius(16 * d);
        rootView.setBackground(bg);
        int pad = (int) (12 * d);
        rootView.setPadding(pad, (int) (8 * d), pad, pad);

        // 收起态：小药丸（只显示时间），点它展开
        pillView = new TextView(c);
        pillView.setTextColor(Color.WHITE);
        pillView.setTextSize(15);
        pillView.setVisibility(View.GONE);
        pillView.setOnClickListener(v -> setCollapsed(c, false));
        rootView.addView(pillView);

        bodyBox = new LinearLayout(c);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        rootView.addView(bodyBox);

        // 头部行（可拖动）：小标签 + 收起按钮
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = new TextView(c);
        tag.setText("知行 · 计时中");
        tag.setTextColor(Color.rgb(176, 176, 176));
        tag.    setTextSize(11);
        tag.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(tag);
        TextView collapseBtn = smallLabel(c, "收起 —");
        collapseBtn.setOnClickListener(v -> setCollapsed(c, true));
        head.addView(collapseBtn);
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

        noteView = new TextView(c);
        noteView.setTextColor(Color.rgb(200, 200, 200));
        noteView.setTextSize(13);
        noteView.setMaxLines(3);
        bodyBox.addView(noteView);

        // 笔记编辑区（默认隐藏）
        editBox = new LinearLayout(c);
        editBox.setOrientation(LinearLayout.VERTICAL);
        editBox.setVisibility(View.GONE);
        editText = new EditText(c);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.GRAY);
        editText.setHint("写笔记：要点、卡点、灵感……");
        editText.setTextSize(14);
        editText.setMinLines(2);
        editText.setGravity(Gravity.TOP);
        editBox.addView(editText);
        LinearLayout editBtns = new LinearLayout(c);
        editBtns.setOrientation(LinearLayout.HORIZONTAL);
        Button saveBtn = smallButton(c, "保存笔记");
        saveBtn.setOnClickListener(v -> saveNote(c));
        editBtns.addView(saveBtn);
        Button cancelBtn = smallButton(c, "取消");
        cancelBtn.setOnClickListener(v -> endEdit(c, false));
        editBtns.addView(cancelBtn);
        editBox.addView(editBtns);
        bodyBox.addView(editBox);

        // 按钮行
        LinearLayout btns = new LinearLayout(c);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button noteBtn = smallButton(c, "笔记");
        noteBtn.setOnClickListener(v -> beginEdit(c));
        btns.addView(noteBtn);
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
        bodyBox.addView(btns);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
                (int) (292 * d), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = (int) (14 * d);
        lp.y = (int) (110 * d);

        // 拖动：按住头部行移动
        head.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            @Override public boolean onTouch(View v, MotionEvent e) {
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
        });

        try {
            wm.addView(rootView, lp);
        } catch (Exception e) {
            rootView = null;
        }
    }

    private static void setCollapsed(Context c, boolean col) {
        collapsed = col;
        if (rootView == null) return;
        bodyBox.setVisibility(col ? View.GONE : View.VISIBLE);
        pillView.setVisibility(col ? View.VISIBLE : View.GONE);
        float d = c.getResources().getDisplayMetrics().density;
        lp.width = col ? WindowManager.LayoutParams.WRAP_CONTENT : (int) (292 * d);
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
    }

    // ---------- 笔记编辑 ----------
    private static void beginEdit(Context c) {
        if (rootView == null) return;
        TaskItem t = currentTask(c);
        if (t == null) return;
        editing = true;
        editText.setText(t.note == null ? "" : t.note);
        editBox.setVisibility(View.VISIBLE);
        noteView.setVisibility(View.GONE);
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
        editText.requestFocus();
    }

    private static void saveNote(Context c) {
        TaskStore store = new TaskStore(c);
        TaskItem t = store.byId(TimerState.taskId(c));
        if (t != null) {
            t.note = editText.getText().toString().trim();
            t.touch();
            store.save();
            TimerState.logEvent(c, "悬浮窗笔记[" + t.title + "]：" + t.note);
        }
        endEdit(c, true);
    }

    private static void endEdit(Context c, boolean saved) {
        editing = false;
        if (rootView == null) return;
        editBox.setVisibility(View.GONE);
        noteView.setVisibility(View.VISIBLE);
        noteView.setText(noteText(c));
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = 0;
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && rootView != null) imm.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
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
