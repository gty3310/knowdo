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
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 常驻悬浮窗（交互模板来自 Floating Notes，用户打开过知行后由 FloatService 一直维持）：
 * 永远显示「当前第一要务」——正在计时的任务 ＞ 刚暂停的任务 ＞ 列表第一名（未完成、必要优先）。
 * 三种模式——
 *  吸附 DOCK：贴屏幕侧边的小标签，只显示分钟数（计时/暂停=剩余分钟，待开始=计划分钟，封顶 99+）；
 *            没有任何按钮，不能暂停；点它或拖离边缘展开。
 *  小展开 MINI：半透明小窗：任务名 + 时间 + ▶/⏸ + ✎；点内容区或 ✎ 进编辑；
 *            拖到屏幕左/右边缘松手吸附成 DOCK。
 *  编辑 EDIT：更不透明的大卡片：开始/暂停、AI（复制任务信息并打开助手）、完成、打开知行、收起；
 *            笔记就是输入框本身，点开直接写、停笔自动保存。
 * 暂停不直接生效（MINI/EDIT 点 ⏸ 时）：先弹暂停菜单，选多久后提醒（1/3/5/10/15/20/30/60 分钟、
 * 2 小时、自定义），或「立刻暂停」（不提醒）。到点由 ResumeRemindActivity 全屏叫回。
 */
public class FloatingWindow {
    public static final int MODE_EDIT = 0;
    public static final int MODE_MINI = 1;
    public static final int MODE_DOCK = 2;

    public static final int ST_NONE = 0;
    public static final int ST_IDLE = 1;
    public static final int ST_RUNNING = 2;
    public static final int ST_PAUSED = 3;

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
    private static TextView miniPlay;
    private static TextView stateView;
    private static TextView saveStateView;
    private static Button playPauseBtn;
    private static Button doneBtn;
    private static EditText noteEdit;
    private static boolean editing = false;
    private static boolean settingText = false;
    private static boolean suppressed = false;
    private static boolean noteDirty = false;
    private static String cachedTaskId = null;
    private static String displayTaskId = null;
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static Runnable pendingSave;

    // ---------- 显示对象：当前第一要务 ----------
    public static class Display {
        public int state = ST_NONE;
        public TaskItem task;
        public String title = "暂无任务";
        public long remainMs = 0;
    }

    public static Display resolveDisplay(Context c) {
        Display d = new Display();
        TaskStore store = new TaskStore(c);
        if (TimerState.isRunning(c)) {
            d.state = ST_RUNNING;
            d.task = store.byId(TimerState.taskId(c));
            d.title = TimerState.taskTitle(c);
            d.remainMs = Math.max(0, TimerState.endAt(c) - System.currentTimeMillis());
            return d;
        }
        if (TimerState.isPaused(c)) {
            d.state = ST_PAUSED;
            d.task = store.byId(TimerState.pausedTaskId(c));
            d.title = TimerState.pausedTitle(c);
            if (d.title == null || d.title.isEmpty()) d.title = d.task != null ? d.task.title : "已暂停的任务";
            d.remainMs = TimerState.pausedRemainMs(c);
            return d;
        }
        for (TaskItem t : store.visibleSorted()) {
            if (!t.isDoneToday()) {
                d.state = ST_IDLE;
                d.task = t;
                d.title = t.title;
                return d;
            }
        }
        return d;
    }

    public static String displaySummary(Context c) {
        Display d = resolveDisplay(c);
        if (d.state == ST_RUNNING) return "「" + d.title + "」计时中，剩余 " + fmt(d.remainMs);
        if (d.state == ST_PAUSED) return "「" + d.title + "」已暂停，剩余 " + fmt(d.remainMs);
        if (d.state == ST_IDLE) return "下一件：「" + d.title + "」（" + d.task.durationMinutes + " 分钟）";
        return "暂无未完成任务";
    }

    private static String fmt(long ms) {
        long sec = Math.max(0, ms / 1000);
        return sec >= 3600
                ? String.format("%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
                : String.format("%d:%02d", sec / 60, sec % 60);
    }

    // ---------- 开关与模式 ----------
    public static boolean enabled(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("float_enabled", true);
    }

    public static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("float_enabled", on).apply();
        if (!on) {
            hide();
            FloatService.stop(c);
        } else {
            FloatService.start(c);
        }
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
                : m == MODE_MINI ? (int) (262 * d)
                : (int) (46 * d);
        if (m == MODE_DOCK) lp.x = dockX(c);
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
    }

    private static int dockX(Context c) {
        int w = c.getResources().getDisplayMetrics().widthPixels;
        float d = c.getResources().getDisplayMetrics().density;
        return dockSide(c) == 0 ? 0 : w - (int) (46 * d);
    }

    /** 预览（权限页用）：无需计时，显示 10 秒样例；常驻服务在跑时预览会被实时内容覆盖，属正常。 */
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
        if (titleView != null) titleView.setText("预览：这里显示当前第一要务");
        if (timeView != null) timeView.setText("24:59");
        if (miniTitle != null) miniTitle.setText("预览任务");
        if (miniTime != null) miniTime.setText("24:59");
        if (dockView != null) dockView.setText("25");
        settingText = true;
        if (noteEdit != null) noteEdit.setText("这是预览。悬浮窗现在是常驻的：没计时时显示第一要务，可以直接开始、暂停（先选多久后提醒）、点 AI 把任务发给助手。");
        settingText = false;
        ui.postDelayed(() -> { if (!TimerState.isRunning(c)) hide(); }, 10_000);
    }

    /** 检查页在前台时让位；检查页销毁后由常驻服务自动恢复显示。 */
    public static void setSuppressed(boolean s) {
        suppressed = s;
        if (s) hideInternal();
    }

    // ---------- 显示/隐藏/刷新 ----------
    public static void ensureShown(Context c) {
        if (!enabled(c) || suppressed || rootView != null) return;
        if (!AlertHelper.canDrawOverlays(c)) return;
        build(c.getApplicationContext());
    }

    /** TimerService 的旧入口：统一走 refresh 渲染。 */
    public static void update(Context c, String remainText) {
        refresh(c);
    }

    public static void refresh(Context c) {
        if (rootView == null) return;
        if (PauseMenu.isOpen() && !TimerState.isRunning(c)) PauseMenu.close();
        Display d = resolveDisplay(c);
        displayTaskId = d.task != null ? d.task.id : null;
        String timeText;
        if (d.state == ST_RUNNING || d.state == ST_PAUSED) timeText = fmt(d.remainMs);
        else if (d.state == ST_IDLE) timeText = d.task.durationMinutes + "分";
        else timeText = "--:--";
        if (titleView != null) titleView.setText(d.title);
        if (timeView != null) timeView.setText(timeText);
        if (miniTitle != null) miniTitle.setText(d.title);
        if (miniTime != null) miniTime.setText(timeText);
        if (miniPlay != null) miniPlay.setText(d.state == ST_RUNNING ? "⏸" : "▶");
        if (stateView != null) {
            stateView.setText(d.state == ST_RUNNING ? "计时中"
                    : d.state == ST_PAUSED ? "已暂停，点 ▶ 继续"
                    : d.state == ST_IDLE ? "第一要务，点 ▶ 开始" : "今天的任务都完成了");
        }
        if (playPauseBtn != null) {
            playPauseBtn.setText(d.state == ST_RUNNING ? "⏸ 暂停"
                    : d.state == ST_PAUSED ? "▶ 继续" : "▶ 开始");
            playPauseBtn.setEnabled(d.state != ST_NONE);
        }
        if (doneBtn != null) doneBtn.setVisibility(d.state == ST_RUNNING ? View.VISIBLE : View.GONE);
        if (dockView != null) {
            long mins;
            if (d.state == ST_RUNNING || d.state == ST_PAUSED) mins = Math.max(1, (d.remainMs + 59_999) / 60_000);
            else if (d.state == ST_IDLE) mins = d.task.durationMinutes;
            else mins = 0;
            dockView.setText(d.state == ST_NONE ? "✓" : mins > 99 ? "99+" : String.valueOf(mins));
        }
        // 笔记绑定到当前显示的任务；任务变了才重载，避免打断输入
        if (noteEdit != null && (cachedTaskId == null || !cachedTaskId.equals(displayTaskId))) {
            cachedTaskId = displayTaskId;
            settingText = true;
            noteEdit.setText(d.task == null || d.task.note == null ? "" : d.task.note);
            settingText = false;
        }
    }

    public static void hide() {
        suppressed = false;
        hideInternal();
    }

    private static void hideInternal() {
        PauseMenu.close();
        if (pendingSave != null) { ui.removeCallbacks(pendingSave); pendingSave = null; }
        if (wm != null && rootView != null) {
            try { wm.removeView(rootView); } catch (Exception ignored) { }
        }
        rootView = null;
        editing = false;
        noteDirty = false;
        cachedTaskId = null;
        displayTaskId = null;
    }

    // ---------- 开始 / 暂停 / 继续 ----------
    private static void onPlayPause(Context c) {
        Display d = resolveDisplay(c);
        if (d.state == ST_RUNNING) {
            PauseMenu.show(c, d.title);
        } else if (d.state == ST_PAUSED && d.task != null) {
            int total = TimerState.pausedTotalSec(c);
            if (total <= 0) total = Math.max(1, d.task.durationMinutes) * 60;
            int remain = (int) Math.max(1, TimerState.pausedRemainMs(c) / 1000);
            TimerService.resumeTimer(c, total, remain, d.task.id, d.task.title);
        } else if (d.state == ST_IDLE && d.task != null) {
            TimerService.startTimer(c, Math.max(1, d.task.durationMinutes) * 60, d.task.id, d.task.title);
        }
    }

    // ---------- 构建 ----------
    private static void build(final Context c) {
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        float d = c.getResources().getDisplayMetrics().density;

        rootView = new LinearLayout(c);
        rootView.setOrientation(LinearLayout.VERTICAL);

        // —— 吸附模式：边缘分钟数标签（无按钮，不能暂停） ——
        dockView = new TextView(c);
        dockView.setText("25");
        dockView.setTextColor(Color.WHITE);
        dockView.setTextSize(16);
        dockView.setTypeface(dockView.getTypeface(), android.graphics.Typeface.BOLD);
        dockView.setGravity(Gravity.CENTER);
        dockView.setVisibility(View.GONE);
        rootView.addView(dockView);

        // —— 小展开模式：任务名 + 时间 + ▶/⏸ + ✎ ——
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
        miniPlay = new TextView(c);
        miniPlay.setText("▶");
        miniPlay.setTextColor(Color.WHITE);
        miniPlay.setTextSize(16);
        miniPlay.setPadding(dp(c, 8), dp(c, 4), dp(c, 4), dp(c, 4));
        miniPlay.setOnClickListener(v -> onPlayPause(c));
        miniBox.addView(miniPlay);
        TextView bang = new TextView(c);
        bang.setText("✎");
        bang.setTextColor(Color.rgb(220, 220, 220));
        bang.setTextSize(17);
        bang.setPadding(dp(c, 6), dp(c, 2), dp(c, 4), dp(c, 2));
        bang.setOnClickListener(v -> openEdit(c));
        miniBox.addView(bang);
        rootView.addView(miniBox);

        // —— 编辑模式：完整卡片 ——
        bodyBox = new LinearLayout(c);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        rootView.addView(bodyBox);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = new TextView(c);
        tag.setText("知行 · 当前第一要务");
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

        LinearLayout timeRow = new LinearLayout(c);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeView = new TextView(c);
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(32);
        timeView.setTypeface(timeView.getTypeface(), android.graphics.Typeface.BOLD);
        timeRow.addView(timeView);
        stateView = new TextView(c);
        stateView.setTextColor(Color.rgb(176, 176, 176));
        stateView.setTextSize(12);
        stateView.setPadding(dp(c, 10), 0, 0, 0);
        timeRow.addView(stateView);
        bodyBox.addView(timeRow);

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
            return false;
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
        playPauseBtn = smallButton(c, "▶ 开始");
        playPauseBtn.setOnClickListener(v -> onPlayPause(c));
        btns.addView(playPauseBtn);
        Button aiBtn = smallButton(c, "AI");
        aiBtn.setOnClickListener(v -> {
            Display dd = resolveDisplay(c);
            if (dd.task == null) {
                android.widget.Toast.makeText(c, "现在没有可发送的任务", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(c, AiLaunchActivity.class);
            i.putExtra(AiLaunchActivity.EXTRA_TASK_ID, dd.task.id);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        });
        btns.addView(aiBtn);
        doneBtn = smallButton(c, "完成");
        doneBtn.setOnClickListener(v -> {
            setSuppressed(true);
            Intent i = new Intent(c, CheckActivity.class);
            i.putExtra(CheckActivity.EXTRA_TYPE, AlertHelper.TYPE_END);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        });
        btns.addView(doneBtn);
        bodyBox.addView(btns);

        LinearLayout btns2 = new LinearLayout(c);
        btns2.setOrientation(LinearLayout.HORIZONTAL);
        Button openBtn = smallButton(c, "打开知行");
        openBtn.setOnClickListener(v -> {
            Intent i = new Intent(c, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        });
        btns2.addView(openBtn);
        Button miniBtn = smallButton(c, "收起");
        miniBtn.setOnClickListener(v -> setMode(c, MODE_MINI));
        btns2.addView(miniBtn);
        bodyBox.addView(btns2);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
                (int) (262 * d), WindowManager.LayoutParams.WRAP_CONTENT, type,
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
        refresh(c);
    }

    /** 进入编辑：展开卡片 + 笔记输入框立刻获得焦点并弹键盘。 */
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
        if (noteEdit == null || displayTaskId == null) return;
        TaskStore store = new TaskStore(c);
        TaskItem t = store.byId(displayTaskId);
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
                    onUp(lp.x - startX);
                    return true;
                default:
                    return false;
            }
        }

        private void onUp(int dx) {
            int m = mode(c);
            float d = c.getResources().getDisplayMetrics().density;
            int screenW = c.getResources().getDisplayMetrics().widthPixels;
            int viewW = rootView.getWidth();
            if (m == MODE_DOCK) {
                int side = dockSide(c);
                boolean inward = side == 1 ? dx < -(int) (60 * d) : dx > (int) (60 * d);
                if (!moved) expandFromDock(c);
                else if (inward) setMode(c, MODE_MINI);
                else {
                    lp.x = dockX(c);
                    try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
                }
            } else if (m == MODE_MINI) {
                if (!moved) { openEdit(c); return; }
                if (lp.x <= (int) (18 * d)) {
                    setDockSide(c, 0);
                    setMode(c, MODE_DOCK);
                } else if (lp.x + viewW >= screenW - (int) (18 * d)) {
                    setDockSide(c, 1);
                    setMode(c, MODE_DOCK);
                }
            }
        }

        private void expandFromDock(Context c) {
            float d = c.getResources().getDisplayMetrics().density;
            int screenW = c.getResources().getDisplayMetrics().widthPixels;
            if (dockSide(c) == 1) lp.x = screenW - (int) (262 * d) - (int) (10 * d);
            else lp.x = (int) (10 * d);
            setMode(c, MODE_MINI);
        }
    }

    // ---------- 暂停菜单（选提醒时间） ----------
    private static class PauseMenu {
        private static WindowManager mwm;
        private static View menuView;
        private static LinearLayout listBox;
        private static LinearLayout customBox;
        private static EditText hourInput;
        private static EditText minInput;

        static boolean isOpen() { return menuView != null; }

        static void show(final Context c, String taskTitle) {
            close();
            mwm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            if (mwm == null) return;
            float d = c.getResources().getDisplayMetrics().density;

            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.argb(245, 28, 28, 28));
            bg.setCornerRadius(16 * d);
            card.setBackground(bg);
            int pad = (int) (16 * d);
            card.setPadding(pad, pad, pad, pad);

            TextView title = new TextView(c);
            title.setText("暂停「" + taskTitle + "」\n多久之后提醒你回来？");
            title.setTextColor(Color.WHITE);
            title.setTextSize(16);
            card.addView(title);

            listBox = new LinearLayout(c);
            listBox.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < ResumeReminder.LABELS.length; i++) {
                final long delay = ResumeReminder.DELAYS_MS[i];
                listBox.addView(menuRow(c, ResumeReminder.LABELS[i], v -> {
                    TimerService.pauseWithReminder(c, delay);
                    close();
                }));
            }
            listBox.addView(menuRow(c, "自定义…", v -> {
                listBox.setVisibility(View.GONE);
                customBox.setVisibility(View.VISIBLE);
            }));
            listBox.addView(menuRow(c, "立刻暂停（不提醒）", v -> {
                TimerService.pauseTimer(c);
                close();
            }));
            listBox.addView(menuRow(c, "取消", v -> close()));
            ScrollView scroll = new ScrollView(c);
            scroll.addView(listBox);
            card.addView(scroll);

            customBox = new LinearLayout(c);
            customBox.setOrientation(LinearLayout.VERTICAL);
            customBox.setVisibility(View.GONE);
            LinearLayout inputs = new LinearLayout(c);
            inputs.setOrientation(LinearLayout.HORIZONTAL);
            inputs.setGravity(Gravity.CENTER);
            hourInput = menuInput(c, "小时");
            minInput = menuInput(c, "分钟");
            inputs.addView(hourInput);
            TextView colon = new TextView(c);
            colon.setText(" : ");
            colon.setTextColor(Color.WHITE);
            colon.setTextSize(18);
            inputs.addView(colon);
            inputs.addView(minInput);
            customBox.addView(inputs);
            customBox.addView(menuRow(c, "确定", v -> {
                int h = parse(hourInput), m = parse(minInput);
                long ms = (h * 60L + m) * 60_000L;
                if (ms < 60_000L) ms = 60_000L;
                TimerService.pauseWithReminder(c, ms);
                close();
            }));
            customBox.addView(menuRow(c, "返回", v -> {
                customBox.setVisibility(View.GONE);
                listBox.setVisibility(View.VISIBLE);
            }));
            card.addView(customBox);

            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams mlp = new WindowManager.LayoutParams(
                    (int) (280 * d), WindowManager.LayoutParams.WRAP_CONTENT, type,
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND, PixelFormat.TRANSLUCENT);
            mlp.gravity = Gravity.CENTER;
            mlp.dimAmount = 0.45f;
            mlp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            try {
                mwm.addView(card, mlp);
                menuView = card;
            } catch (Exception e) {
                menuView = null;
            }
        }

        static void close() {
            if (mwm != null && menuView != null) {
                try { mwm.removeView(menuView); } catch (Exception ignored) { }
            }
            menuView = null;
            listBox = null;
            customBox = null;
        }

        private static TextView menuRow(Context c, String s, View.OnClickListener onClick) {
            TextView t = new TextView(c);
            t.setText(s);
            t.setTextColor(Color.WHITE);
            t.setTextSize(16);
            t.setGravity(Gravity.CENTER);
            float d = c.getResources().getDisplayMetrics().density;
            t.setPadding(0, (int) (11 * d), 0, (int) (11 * d));
            t.setOnClickListener(onClick);
            return t;
        }

        private static EditText menuInput(Context c, String hint) {
            EditText e = new EditText(c);
            e.setHint(hint);
            e.setHintTextColor(Color.GRAY);
            e.setTextColor(Color.WHITE);
            e.setTextSize(18);
            e.setGravity(Gravity.CENTER);
            e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            e.setLayoutParams(new LinearLayout.LayoutParams(dpC(c, 84), LinearLayout.LayoutParams.WRAP_CONTENT));
            return e;
        }

        private static int parse(EditText e) {
            try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return 0; }
        }

        private static int dpC(Context c, int v) {
            return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
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
