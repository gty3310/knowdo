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
 * 计时悬浮窗（借鉴 Floating Notes 一类应用的做法）：
 * 三种模式——卡片（任务名+大字倒计时+笔记+按钮）、紧凑条（一行：任务名+时间）、药丸（只剩时间）。
 * 整张悬浮窗除按钮与输入框外处处可拖动；药丸轻点展开回卡片。
 * 笔记在悬浮窗里写即自动保存（随桥同步），不用点保存。
 * 全屏检查页弹出时自动让位隐藏，检查页关掉后自动回来。
 * 依赖「上层显示」权限，未授权时静默不显示。
 */
public class FloatingWindow {
    public static final int MODE_CARD = 0;
    public static final int MODE_BAR = 1;
    public static final int MODE_PILL = 2;

    private static WindowManager wm;
    private static WindowManager.LayoutParams lp;
    private static LinearLayout rootView;
    private static LinearLayout bodyBox;
    private static LinearLayout barBox;
    private static LinearLayout editBox;
    private static TextView titleView;
    private static TextView timeView;
    private static TextView noteView;
    private static TextView barText;
    private static TextView pillView;
    private static TextView saveStateView;
    private static EditText editText;
    private static boolean editing = false;
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
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("float_mode", MODE_CARD);
    }

    private static void setMode(Context c, int m) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putInt("float_mode", m).apply();
        applyMode(c);
    }

    private static void applyMode(Context c) {
        if (rootView == null || lp == null) return;
        int m = mode(c);
        bodyBox.setVisibility(m == MODE_CARD ? View.VISIBLE : View.GONE);
        barBox.setVisibility(m == MODE_BAR ? View.VISIBLE : View.GONE);
        pillView.setVisibility(m == MODE_PILL ? View.VISIBLE : View.GONE);
        float d = c.getResources().getDisplayMetrics().density;
        lp.width = m == MODE_CARD ? (int) (292 * d)
                : m == MODE_BAR ? (int) (236 * d)
                : WindowManager.LayoutParams.WRAP_CONTENT;
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
    }

    /** 预览（权限页用）：无需计时，直接显示 10 秒样例，方便确认悬浮效果与权限是否生效。 */
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
        if (noteView != null) noteView.setText("这是预览。可以按住任意空白处拖动它；「收起」变紧凑条，再缩成小药丸；笔记写完自动保存。");
        if (barText != null) barText.setText("预览任务 · 24:59");
        if (pillView != null) pillView.setText("⏱ 24:59");
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
        if (timeView != null && !editing) timeView.setText(remainText);
        if (pillView != null) pillView.setText("⏱ " + remainText);
        if (barText != null) barText.setText(shortTitle(title) + " · " + remainText);
        String id = TimerState.taskId(c);
        if (noteView != null && !editing && (cachedTaskId == null || !cachedTaskId.equals(id))) {
            cachedTaskId = id;
            noteView.setText(noteText(c));
        }
    }

    private static String shortTitle(String t) {
        if (t == null) return "";
        return t.length() > 10 ? t.substring(0, 10) + "…" : t;
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

    private static String noteText(Context c) {
        TaskItem t = currentTask(c);
        String n = t == null ? "" : t.note;
        return (n == null || n.trim().isEmpty()) ? "点「笔记」给这个任务写点什么（自动保存）" : n;
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

        // —— 药丸模式 ——
        pillView = new TextView(c);
        pillView.setTextColor(Color.WHITE);
        pillView.setTextSize(15);
        pillView.setVisibility(View.GONE);
        rootView.addView(pillView);

        // —— 紧凑条模式 ——
        barBox = new LinearLayout(c);
        barBox.setOrientation(LinearLayout.HORIZONTAL);
        barBox.setGravity(Gravity.CENTER_VERTICAL);
        barBox.setVisibility(View.GONE);
        barText = new TextView(c);
        barText.setTextColor(Color.WHITE);
        barText.setTextSize(14);
        barText.setMaxLines(1);
        barText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        barBox.addView(barText);
        TextView barOpen = smallLabel(c, "展开");
        barOpen.setOnClickListener(v -> setMode(c, MODE_CARD));
        barBox.addView(barOpen);
        TextView barMin = smallLabel(c, "缩到最小");
        barMin.setOnClickListener(v -> setMode(c, MODE_PILL));
        barBox.addView(barMin);
        rootView.addView(barBox);

        // —— 卡片模式 ——
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
        shrinkBtn.setOnClickListener(v -> setMode(c, MODE_BAR));
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

        noteView = new TextView(c);
        noteView.setTextColor(Color.rgb(200, 200, 200));
        noteView.setTextSize(13);
        noteView.setMaxLines(3);
        bodyBox.addView(noteView);

        // 笔记编辑区（自动保存，无保存按钮）
        editBox = new LinearLayout(c);
        editBox.setOrientation(LinearLayout.VERTICAL);
        editBox.setVisibility(View.GONE);
        editText = new EditText(c);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.GRAY);
        editText.setHint("写笔记：要点、卡点、灵感……（自动保存）");
        editText.setTextSize(14);
        editText.setMinLines(2);
        editText.setGravity(Gravity.TOP);
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void afterTextChanged(Editable s) {
                if (!editing) return;
                noteDirty = true;
                if (saveStateView != null) saveStateView.setText("正在保存…");
                if (pendingSave != null) ui.removeCallbacks(pendingSave);
                pendingSave = () -> persistNote(c, false);
                ui.postDelayed(pendingSave, 700);
            }
        });
        editBox.addView(editText);
        LinearLayout editRow = new LinearLayout(c);
        editRow.setOrientation(LinearLayout.HORIZONTAL);
        editRow.setGravity(Gravity.CENTER_VERTICAL);
        saveStateView = new TextView(c);
        saveStateView.setText("笔记会自动保存");
        saveStateView.setTextColor(Color.rgb(150, 150, 150));
        saveStateView.setTextSize(11);
        saveStateView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        editRow.addView(saveStateView);
        Button doneEditBtn = smallButton(c, "写完");
        doneEditBtn.setOnClickListener(v -> endEdit(c));
        editRow.addView(doneEditBtn);
        editBox.addView(editRow);
        bodyBox.addView(editBox);

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

        // 整窗拖动：挂在根视图上。按钮与输入框会自己消费触摸（不触发拖动），
        // 其余区域（标题、时间、笔记文字、空白）按下拖动即可移动；药丸轻点（几乎没位移）视为展开。
        rootView.setOnTouchListener(new Dragger(c));
        try {
            wm.addView(rootView, lp);
        } catch (Exception e) {
            rootView = null;
            return;
        }
        applyMode(c);
    }

    /** 拖动处理器：位移超过阈值才算拖动；药丸模式下轻点 = 展开回卡片。 */
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
                    if (!moved && mode(c) == MODE_PILL) setMode(c, MODE_CARD);
                    return true;
                default:
                    return false;
            }
        }
    }

    // ---------- 笔记编辑（自动保存） ----------
    private static void beginEdit(Context c) {
        if (rootView == null) return;
        TaskItem t = currentTask(c);
        if (t == null) return;
        editing = true;
        noteDirty = false;
        editText.setText(t.note == null ? "" : t.note);
        editText.setSelection(editText.getText().length());
        if (saveStateView != null) saveStateView.setText("笔记会自动保存");
        editBox.setVisibility(View.VISIBLE);
        noteView.setVisibility(View.GONE);
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
        editText.requestFocus();
    }

    private static void persistNote(Context c, boolean fromEnd) {
        if (editText == null) return;
        String id = TimerState.taskId(c);
        if (id == null || id.isEmpty()) return;
        TaskStore store = new TaskStore(c);
        TaskItem t = store.byId(id);
        if (t == null) return;
        String v = editText.getText().toString().trim();
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

    private static void endEdit(Context c) {
        if (pendingSave != null) { ui.removeCallbacks(pendingSave); pendingSave = null; }
        persistNote(c, true);
        editing = false;
        noteDirty = false;
        if (rootView == null) return;
        editBox.setVisibility(View.GONE);
        noteView.setVisibility(View.VISIBLE);
        cachedTaskId = null; // 强制 update 重读笔记
        noteView.setText(noteText(c));
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = 0;
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
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
