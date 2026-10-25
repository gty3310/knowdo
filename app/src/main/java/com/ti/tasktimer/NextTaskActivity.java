package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 「接下来，做点什么」全屏页（v1.10.0 任务衔接守卫；v1.19.0 按 UI 稿
 * mockup-next-task-v1 改版）：空档时被 IdleGuard 拉起——巡检、事件完成、
 * 以及 v1.19.0 新增的「解锁用机」档（GuardDispatch ＞ IdleGuard.checkOnUserPresent）。
 *
 * 三态一页（稿定案）：
 * - 默认态：衬线大字「接下来，做点什么」＋今日未完成最多五行（必做在前、带 ★），
 *   点一行即开始；底部三入口——其他任务（回主页列表）／新建任务／休息一会；
 * - 新建态：名字一行＋时长 chips 快选＋天/时/分三转轴细调，合计与结束时刻实时算，
 *   点「开始」建任务并立即开始；
 * - 休息态：休息与任务同等对待——选时长开始一个「休息」占位计时，到点走结束检查。
 * 本页不催不响：催促的震/响在 IdleAlarm 的 staging 里（alarm 页），这里只管选。
 * 开始计时前复刻主页的屏幕守卫截图授权流程（开了内容守卫且未授权时先申请，结果如何都开始）。
 */
public class NextTaskActivity extends Activity {
    private static final int BG = Color.parseColor("#171512");
    private static final int CARD = Color.parseColor("#1D1A16");
    private static final int INK = Color.parseColor("#ECE7DB");
    private static final int INK2 = Color.parseColor("#A49C8B");
    private static final int INK3 = Color.parseColor("#6F695C");
    private static final int LINE = Color.parseColor("#2E2A24");
    private static final int AMBER = Color.parseColor("#C9A24D");
    private static final int[] QUICK_MIN = {2, 3, 5, 10, 15, 20, 30};
    private static final int[] REST_MIN = {5, 10, 15, 20, 30, 60};

    private static final int STATE_DEFAULT = 0;
    private static final int STATE_NEW = 1;
    private static final int STATE_REST = 2;

    private TaskStore store;
    private LinearLayout root;
    private int state = STATE_DEFAULT;

    // 新建态控件
    private EditText nameBox;
    private NumberPicker dayPick, hourPick, minPick;
    private TextView totalLabel;
    private Button startBtn;
    private final List<Button> quickBtns = new ArrayList<>();
    private boolean syncingPickers;

    // 休息态
    private int restMinutes = 15;
    private final List<Button> restBtns = new ArrayList<>();

    private static final int REQ_CAPTURE = 47;
    private TaskItem pendingStart;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AlertHelper.cancelNextTask(this);
        // 空档时钟锚点（v1.14.0）：守卫页展示时若尚无锚点，以展示时刻起算——
        // 覆盖「叫回页选换下一个任务直接进本页」等不经 IdleGuard 的入口（已有锚点不重置；
        // 暂停到期时锚点优先钉在到期时刻，见 IdleAlarm.preferredAnchor）。
        IdleAlarm.notePageShown(this);
        store = new TaskStore(this);
        buildState(STATE_DEFAULT);
    }

    @Override protected void onResume() {
        super.onResume();
        TopPages.nextShown = true;
    }

    @Override protected void onPause() {
        TopPages.nextShown = false;
        super.onPause();
    }

    /** 按返回：默认态＝退出本页，给自动触发留 3 分钟静默（不取消 alarm 轮次排期）；
     *  新建/休息态＝先回默认态。 */
    @Override public void onBackPressed() {
        if (state != STATE_DEFAULT) {
            buildState(STATE_DEFAULT);
            return;
        }
        IdleGuard.snoozeOnDismiss(this);
        super.onBackPressed();
    }

    /** 用户在本页有任何操作：立刻停掉正在进行的本轮震/响（页面保留），下轮间隔从停声时刻起算。 */
    @Override public void onUserInteraction() {
        super.onUserInteraction();
        if (IdleStage.isActive() || AlarmRing.isRinging()) {
            IdleStage.stop(this);
            IdleAlarm.onSoundStoppedEarly(this);
        }
    }

    // ---------------- 界面骨架 ----------------

    private void buildState(int s) {
        state = s;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(34), dp(56), dp(34), dp(30));
        if (s == STATE_NEW) buildNew();
        else if (s == STATE_REST) buildRest();
        else buildDefault();
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);
        if (s == STATE_NEW && nameBox != null) {
            nameBox.requestFocus();
            nameBox.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(nameBox, InputMethodManager.SHOW_IMPLICIT);
            }, 350);
        }
    }

    private TextView bigLine(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(INK);
        t.setTextSize(31);
        t.setTypeface(Typeface.SERIF, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView backRow() {
        TextView b = new TextView(this);
        b.setText(Strings.en() ? "‹ Back" : "‹ 返回");
        b.setTextColor(INK3);
        b.setTextSize(14);
        b.setOnClickListener(v -> buildState(STATE_DEFAULT));
        return b;
    }

    private TextView sectionCap(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(INK3);
        t.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(34);
        lp.bottomMargin = dp(12);
        t.setLayoutParams(lp);
        return t;
    }

    private Button serifButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(INK);
        b.setTextSize(18);
        b.setTypeface(Typeface.SERIF);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(1), Color.parseColor("#45403A"));
        g.setCornerRadius(dp(16));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(60));
        lp.topMargin = dp(26);
        b.setLayoutParams(lp);
        return b;
    }

    private Button chip(String text, boolean on) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTextColor(on ? AMBER : INK2);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(1), on ? AMBER : LINE);
        g.setCornerRadius(dp(999));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.rightMargin = dp(9);
        lp.bottomMargin = dp(9);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout chipFlow() {
        // 简单流式排布：用横向多行模拟稿上的 chips（每行 4 个）
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    private LinearLayout chipRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    // ---------------- 默认态 ----------------

    private void buildDefault() {
        TextView big = bigLine(Strings.en() ? "What's next" : "接下来，做点什么");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(30);
        root.addView(big, bp);

        // 今日未完成：最多五行，必做在前（沿用任务列表排序），★＝必做
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(48);
        root.addView(list, lp);
        store.load();
        List<TaskItem> today = new ArrayList<>();
        for (TaskItem t : store.visibleSorted()) {
            if (!t.isDoneToday()) today.add(t);
            if (today.size() >= 5) break;
        }
        if (today.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(Strings.nextNoneToday());
            none.setTextColor(INK2);
            none.setTextSize(14);
            none.setGravity(Gravity.CENTER);
            list.addView(none);
        } else {
            for (final TaskItem t : today) {
                list.addView(taskRow(t));
            }
        }

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(entryRow(Strings.en() ? "All tasks" : "其他任务", v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try { startActivity(i); } catch (Exception ignored) { }
            finish();
        }));
        root.addView(entryRow(Strings.en() ? "New task" : "新建任务", v -> buildState(STATE_NEW)));
        root.addView(entryRow(Strings.en() ? "Rest a while" : "休息一会", v -> buildState(STATE_REST)));
    }

    private View taskRow(final TaskItem t) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(15), dp(2), dp(15));
        boolean must = "must".equals(t.priority);
        TextView name = new TextView(this);
        name.setText((must ? "★ " : "") + t.title);
        name.setTextColor(INK);
        name.setTextSize(16);
        name.setMaxLines(1);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView meta = new TextView(this);
        meta.setText(Strings.fmtDur(t.durationMinutes) + "  " + (Strings.en() ? "Start" : "开始"));
        meta.setTextColor(must ? AMBER : INK3);
        meta.setTextSize(13);
        row.addView(meta);
        row.setOnClickListener(v -> startTiming(t));
        col.addView(row);
        View line = new View(this);
        line.setBackgroundColor(LINE);
        col.addView(line, new LinearLayout.LayoutParams(-1, 1));
        return col;
    }

    private View entryRow(String label, View.OnClickListener onClick) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(14), dp(2), dp(14));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(INK2);
        l.setTextSize(15);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView r = new TextView(this);
        r.setText("›");
        r.setTextColor(INK3);
        r.setTextSize(15);
        row.addView(r);
        row.setOnClickListener(onClick);
        col.addView(row);
        View line = new View(this);
        line.setBackgroundColor(LINE);
        col.addView(line, new LinearLayout.LayoutParams(-1, 1));
        return col;
    }

    // ---------------- 新建态 ----------------

    private void buildNew() {
        root.addView(backRow());

        nameBox = new EditText(this);
        nameBox.setHint(Strings.nextNameHint());
        nameBox.setHintTextColor(INK3);
        nameBox.setTextColor(INK);
        nameBox.setTextSize(21);
        nameBox.setTypeface(Typeface.SERIF);
        nameBox.setSingleLine(true);
        nameBox.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameBox.setBackground(null);
        nameBox.setPadding(dp(2), dp(10), dp(2), dp(14));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(26);
        root.addView(nameBox, np);
        View uline = new View(this);
        uline.setBackgroundColor(Color.parseColor("#45403A"));
        root.addView(uline, new LinearLayout.LayoutParams(-1, 1));
        nameBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
        nameBox.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && startBtn != null && startBtn.isEnabled()) {
                startNew();
                return true;
            }
            return false;
        });

        root.addView(sectionCap(Strings.en() ? "HOW LONG" : "做多久"));

        // 时长 chips：休息 chip + 快捷分钟（点一下同步三转轴）
        quickBtns.clear();
        LinearLayout flow = chipFlow();
        LinearLayout row1 = chipRow();
        Button restChip = chip(Strings.nextRest(), false);
        restChip.setOnClickListener(v -> {
            nameBox.setText(Strings.nextRest());
            nameBox.setSelection(nameBox.getText().length());
        });
        row1.addView(restChip);
        for (int i = 0; i < 3; i++) row1.addView(quickChip(QUICK_MIN[i]));
        flow.addView(row1);
        LinearLayout row2 = chipRow();
        for (int i = 3; i < QUICK_MIN.length; i++) row2.addView(quickChip(QUICK_MIN[i]));
        flow.addView(row2);
        root.addView(flow);

        // 天 / 时 / 分 三转轴
        LinearLayout pickRow = new LinearLayout(this);
        pickRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams prp = new LinearLayout.LayoutParams(-1, -2);
        prp.topMargin = dp(10);
        root.addView(pickRow, prp);
        dayPick = addPicker(pickRow, Strings.nextDayUnit());
        hourPick = addPicker(pickRow, Strings.nextHourUnit());
        minPick = addPicker(pickRow, Strings.nextMinuteUnit());
        NumberPicker.OnValueChangeListener changed = (picker, oldVal, newVal) -> {
            if (!syncingPickers) refresh();
        };
        dayPick.setOnValueChangedListener(changed);
        hourPick.setOnValueChangedListener(changed);
        minPick.setOnValueChangedListener(changed);

        totalLabel = new TextView(this);
        totalLabel.setTextColor(INK2);
        totalLabel.setTextSize(14);
        totalLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(16);
        root.addView(totalLabel, tp);

        startBtn = serifButton(Strings.nextStart());
        startBtn.setOnClickListener(v -> startNew());
        root.addView(startBtn);
        refresh();
    }

    private Button quickChip(final int mins) {
        Button b = chip(mins + (Strings.en() ? " min" : " 分"), false);
        b.setOnClickListener(v -> {
            syncingPickers = true;
            dayPick.setValue(0);
            hourPick.setValue(0);
            minPick.setValue(mins);
            syncingPickers = false;
            refresh();
        });
        quickBtns.add(b);
        return b;
    }

    private NumberPicker addPicker(LinearLayout parent, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        parent.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        NumberPicker np = new NumberPicker(this);
        np.setMinValue(0);
        np.setMaxValue(100);
        np.setWrapSelectorWheel(false);
        np.setValue(0);
        col.addView(np);
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextColor(INK3);
        lab.setTextSize(13);
        lab.setGravity(Gravity.CENTER);
        col.addView(lab);
        return np;
    }

    // ---------------- 休息态 ----------------

    private void buildRest() {
        root.addView(backRow());
        TextView big = bigLine(Strings.en() ? "Rest a while" : "休息一会");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(46);
        root.addView(big, bp);
        TextView note = new TextView(this);
        note.setText(Strings.en() ? "Rest is a plan too\nYou will be called back when it ends"
                : "休息也是正式安排\n到点会叫你回来");
        note.setTextColor(INK2);
        note.setTextSize(13);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(0, 1.6f);
        LinearLayout.LayoutParams np2 = new LinearLayout.LayoutParams(-1, -2);
        np2.topMargin = dp(18);
        root.addView(note, np2);

        root.addView(sectionCap(Strings.en() ? "HOW LONG" : "歇多久"));
        restBtns.clear();
        LinearLayout flow = chipFlow();
        LinearLayout row1 = chipRow();
        LinearLayout row2 = chipRow();
        for (int i = 0; i < REST_MIN.length; i++) {
            final int mins = REST_MIN[i];
            Button b = chip(mins >= 60 ? (Strings.en() ? "1 h" : "1 小时")
                    : mins + (Strings.en() ? " min" : " 分钟"), mins == restMinutes);
            b.setOnClickListener(v -> {
                restMinutes = mins;
                refreshRestChips();
            });
            restBtns.add(b);
            (i < 3 ? row1 : row2).addView(b);
        }
        Button custom = chip(Strings.en() ? "Custom" : "自定义", false);
        custom.setOnClickListener(v -> askCustomRest());
        row2.addView(custom);
        flow.addView(row1);
        flow.addView(row2);
        root.addView(flow);

        Button start = serifButton(Strings.en() ? "Start resting" : "开始休息");
        start.setOnClickListener(v -> {
            TaskItem t = TaskItem.create(Strings.nextRest());
            t.durationMinutes = Math.max(1, restMinutes);
            store.add(t);
            TimerState.logEvent(this, "选任务页开始休息：" + restMinutes + " 分钟（占位计时）");
            startTiming(t);
        });
        root.addView(start);
    }

    private void refreshRestChips() {
        for (int i = 0; i < restBtns.size(); i++) {
            boolean on = REST_MIN[i] == restMinutes;
            Button b = restBtns.get(i);
            b.setTextColor(on ? AMBER : INK2);
            GradientDrawable g = new GradientDrawable();
            g.setColor(Color.TRANSPARENT);
            g.setStroke(dp(1), on ? AMBER : LINE);
            g.setCornerRadius(dp(999));
            b.setBackground(g);
        }
    }

    private void askCustomRest() {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(Strings.en() ? "Minutes" : "分钟");
        new AlertDialog.Builder(this)
                .setTitle(Strings.en() ? "Rest for how long" : "歇多久")
                .setView(input)
                .setPositiveButton(Strings.en() ? "OK" : "好", (d, w) -> {
                    try {
                        int v = Integer.parseInt(input.getText().toString().trim());
                        if (v > 0) {
                            restMinutes = v;
                            refreshRestChips();
                            Toast.makeText(this, (Strings.en() ? "Rest " : "休息 ")
                                    + v + (Strings.en() ? " min" : " 分钟"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception ignored) { }
                })
                .setNegativeButton(Strings.btnCancel(), null)
                .show();
    }

    // ---------------- 新建态逻辑 ----------------

    private int totalMinutes() {
        if (dayPick == null) return 0;
        return dayPick.getValue() * 1440 + hourPick.getValue() * 60 + minPick.getValue();
    }

    private void refresh() {
        if (state != STATE_NEW || nameBox == null) return;
        int total = totalMinutes();
        for (int i = 0; i < quickBtns.size(); i++) {
            boolean on = dayPick.getValue() == 0 && hourPick.getValue() == 0
                    && minPick.getValue() == QUICK_MIN[i];
            Button b = quickBtns.get(i);
            b.setTextColor(on ? AMBER : INK2);
            GradientDrawable g = new GradientDrawable();
            g.setColor(Color.TRANSPARENT);
            g.setStroke(dp(1), on ? AMBER : LINE);
            g.setCornerRadius(dp(999));
            b.setBackground(g);
        }
        totalLabel.setText(Strings.nextTotal(total, endsAtText(total)));
        boolean can = nameBox.getText().toString().trim().length() > 0 && total > 0;
        startBtn.setEnabled(can);
        startBtn.setAlpha(can ? 1f : 0.45f);
    }

    /** 结束时刻文案：同一天只显示 HH:mm；跨天带日期。 */
    private String endsAtText(int totalMin) {
        long endMs = System.currentTimeMillis() + totalMin * 60_000L;
        Calendar now = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(endMs);
        boolean sameDay = now.get(Calendar.YEAR) == end.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == end.get(Calendar.DAY_OF_YEAR);
        Date d = new Date(endMs);
        if (sameDay) {
            return new SimpleDateFormat("HH:mm", Locale.US).format(d);
        }
        if (Strings.en()) {
            return new SimpleDateFormat("MMM d HH:mm", Locale.US).format(d);
        }
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(d);
    }

    /** 点「开始」：按名称与时长新建普通任务并开始计时。 */
    private void startNew() {
        String name = nameBox.getText().toString().trim();
        int total = totalMinutes();
        if (name.isEmpty() || total <= 0) return;
        TaskItem t = TaskItem.create(name);
        t.durationMinutes = total;
        store.add(t);
        TimerState.logEvent(this, "衔接守卫页新建任务：" + t.title + "（" + total + " 分钟）");
        startTiming(t);
    }

    /** 开始计时（含屏幕守卫截图授权流程，与主页同一口径：结果如何都开始）。 */
    private void startTiming(TaskItem t) {
        if (ScreenGuardPrefs.enabled(this) && !ScreenCaptureService.isAuthorized(this)) {
            try {
                MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
                Intent intent = mpm == null ? null : mpm.createScreenCaptureIntent();
                if (intent != null) {
                    pendingStart = t;
                    startActivityForResult(intent, REQ_CAPTURE);
                    return;
                }
            } catch (Exception e) {
                TimerState.logEvent(this, "衔接守卫页：申请截图授权失败，直接开始：" + e.getClass().getSimpleName());
            }
        }
        doStart(t);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        boolean ok = resultCode == RESULT_OK && data != null;
        if (ok) {
            ProjectionAuth.store(resultCode, data);
            TimerState.logEvent(this, "衔接守卫页：截图授权成功，屏幕内容守卫开启采样");
        } else {
            TimerState.logEvent(this, "衔接守卫页：截图授权未通过，本次不启用内容守卫");
        }
        if (pendingStart != null) {
            TaskItem t = pendingStart;
            pendingStart = null;
            doStart(t);
        }
    }

    private void doStart(TaskItem t) {
        Toast.makeText(this, "开始计时：" + t.title + "（" + t.durationMinutes + " 分钟）", Toast.LENGTH_SHORT).show();
        TimerService.startTimer(this, Math.max(1, t.durationMinutes) * 60, t.id, t.title);
        TimerState.logEvent(this, "从衔接守卫页开始任务：" + t.title + "（" + t.durationMinutes + " 分钟）");
        finish();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
