package com.ti.tasktimer;

import android.app.Activity;
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
 * 「下一个任务」全屏页（v1.10.0 任务衔接守卫）：空档时被 IdleGuard 拉起。
 * 极简一页：名称输入（自动聚焦）+「休息」快捷 chip + 时长快捷按钮（2/3/5/10/15/20/30 分钟）
 * + 天/时/分三转轴（0–100，不循环，与快捷按钮双向同步）+ 实时合计行 + 大号「开始」。
 * 点开始 = 按名称与时长新建普通任务（走 TaskStore 现有同步路径）并立即开始计时；
 * 下方「今日未完成」列今天未完成的任务（必做优先），点一条按其计划时长直接开始，不新建。
 * 开始计时前复刻主页的屏幕守卫截图授权流程（开了内容守卫且未授权时先申请，结果如何都开始）。
 */
public class NextTaskActivity extends Activity {
    private static final int BG = Color.parseColor("#101418");
    private static final int CARD = Color.parseColor("#1B2129");
    private static final int BTN = Color.parseColor("#232B36");
    private static final int ACCENT = Color.parseColor("#F2A93B");
    private static final int SUB = Color.parseColor("#9AA4B2");
    private static final int[] QUICK_MIN = {2, 3, 5, 10, 15, 20, 30};

    private TaskStore store;
    private EditText nameBox;
    private NumberPicker dayPick, hourPick, minPick;
    private TextView totalLabel;
    private Button startBtn;
    private final List<Button> quickBtns = new ArrayList<>();
    private boolean syncingPickers;
    private static final int REQ_CAPTURE = 47;
    private TaskItem pendingStart;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AlertHelper.cancelNextTask(this);
        // 空档时钟锚点（v1.14.0）：守卫页展示时若尚无锚点，以展示时刻起算——
        // 覆盖「叫回页选换下一个任务直接进本页」等不经 IdleGuard 的入口（已有锚点不重置）。
        IdleAlarm.notePageShown(this);
        store = new TaskStore(this);

        buildUi();
        nameBox.requestFocus();
        nameBox.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(nameBox, InputMethodManager.SHOW_IMPLICIT);
        }, 350);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        TopPages.nextShown = true;
    }

    @Override protected void onPause() {
        TopPages.nextShown = false;
        super.onPause();
    }

    /** 按返回退出：给自动触发留一段静默期，避免回主页被 onResume 立刻重弹（事件触发不受影响）。
     *  注意：这 3 分钟静默不取消空档 alarm 排期（v1.14.0 口径），到点照样带声拉回本页。 */
    @Override public void onBackPressed() {
        IdleGuard.snoozeOnDismiss(this);
        super.onBackPressed();
    }

    /** 用户在本页有任何操作：立刻停掉本轮铃声（页面保留），下轮间隔从停声时刻起算。 */
    @Override public void onUserInteraction() {
        super.onUserInteraction();
        if (AlarmRing.isRinging()) {
            AlarmRing.stop(this);
            IdleAlarm.onSoundStoppedEarly(this);
        }
    }

    // ---------------- 界面 ----------------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText(Strings.nextTitle());
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        nameBox = new EditText(this);
        nameBox.setHint(Strings.nextNameHint());
        nameBox.setHintTextColor(SUB);
        nameBox.setTextColor(Color.WHITE);
        nameBox.setTextSize(20);
        nameBox.setSingleLine(true);
        nameBox.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameBox.setBackground(round(CARD, 12));
        nameBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(20);
        root.addView(nameBox, np);
        nameBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
        nameBox.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && startBtn.isEnabled()) {
                startNew();
                return true;
            }
            return false;
        });

        // 「休息」快捷 chip
        Button rest = new Button(this);
        rest.setText(Strings.nextRest());
        rest.setTextColor(Color.WHITE);
        rest.setTextSize(15);
        rest.setAllCaps(false);
        rest.setBackground(round(BTN, 20));
        rest.setOnClickListener(v -> {
            nameBox.setText(Strings.nextRest());
            nameBox.setSelection(nameBox.getText().length());
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
        rp.topMargin = dp(12);
        root.addView(rest, rp);

        // 时长快捷按钮（两行）
        LinearLayout qRow1 = quickRow();
        LinearLayout qRow2 = quickRow();
        root.addView(qRow1);
        root.addView(qRow2);
        for (int i = 0; i < QUICK_MIN.length; i++) {
            final int mins = QUICK_MIN[i];
            Button b = new Button(this);
            b.setText(mins + "");
            b.setTextColor(Color.WHITE);
            b.setTextSize(16);
            b.setAllCaps(false);
            b.setBackground(round(BTN, 10));
            b.setOnClickListener(v -> {
                syncingPickers = true;
                dayPick.setValue(0);
                hourPick.setValue(0);
                minPick.setValue(mins);
                syncingPickers = false;
                refresh();
            });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            bp.leftMargin = dp(4);
            bp.rightMargin = dp(4);
            (i < 4 ? qRow1 : qRow2).addView(b, bp);
            quickBtns.add(b);
        }
        TextView minUnit = new TextView(this);
        minUnit.setText(Strings.nextMinuteUnit());
        minUnit.setTextColor(SUB);
        minUnit.setTextSize(13);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-1, -2);
        up.topMargin = dp(4);
        root.addView(minUnit, up);

        // 天 / 时 / 分 三转轴
        LinearLayout pickRow = new LinearLayout(this);
        pickRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams prp = new LinearLayout.LayoutParams(-1, -2);
        prp.topMargin = dp(8);
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
        totalLabel.setTextColor(ACCENT);
        totalLabel.setTextSize(17);
        totalLabel.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(14);
        root.addView(totalLabel, tp);

        startBtn = new Button(this);
        startBtn.setText(Strings.nextStart());
        startBtn.setTextColor(Color.parseColor("#1A1206"));
        startBtn.setTextSize(20);
        startBtn.setTypeface(Typeface.DEFAULT_BOLD);
        startBtn.setAllCaps(false);
        startBtn.setBackground(round(ACCENT, 12));
        startBtn.setOnClickListener(v -> startNew());
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(-1, dp(58));
        sbp.topMargin = dp(14);
        root.addView(startBtn, sbp);

        // 今日未完成
        TextView sec = new TextView(this);
        sec.setText(Strings.nextTodayTitle());
        sec.setTextColor(SUB);
        sec.setTextSize(14);
        LinearLayout.LayoutParams sep = new LinearLayout.LayoutParams(-1, -2);
        sep.topMargin = dp(26);
        sep.bottomMargin = dp(6);
        root.addView(sec, sep);

        List<TaskItem> today = new ArrayList<>();
        for (TaskItem t : store.visibleSorted()) {
            if (!t.isDoneToday()) today.add(t);
            if (today.size() >= 10) break;
        }
        if (today.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(Strings.nextNoneToday());
            none.setTextColor(SUB);
            none.setTextSize(14);
            root.addView(none);
        } else {
            for (final TaskItem t : today) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(round(CARD, 10));
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                TextView name = new TextView(this);
                name.setText(t.title);
                name.setTextColor(Color.WHITE);
                name.setTextSize(16);
                name.setMaxLines(1);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                boolean must = "must".equals(t.priority);
                TextView meta = new TextView(this);
                meta.setText((must ? "★ " : "") + Strings.fmtDur(t.durationMinutes));
                meta.setTextColor(must ? ACCENT : SUB);
                meta.setTextSize(14);
                row.addView(meta);
                row.setOnClickListener(v -> startTiming(t));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = dp(8);
                root.addView(row, lp);
            }
        }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.addView(root);
        setContentView(sv);
    }

    private LinearLayout quickRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(10);
        row.setLayoutParams(p);
        return row;
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
        lab.setTextColor(SUB);
        lab.setTextSize(13);
        lab.setGravity(Gravity.CENTER);
        col.addView(lab);
        return np;
    }

    // ---------------- 逻辑 ----------------

    private int totalMinutes() {
        return dayPick.getValue() * 1440 + hourPick.getValue() * 60 + minPick.getValue();
    }

    private void refresh() {
        int total = totalMinutes();
        // 快捷按钮高亮：当前转轴恰好等于某个快捷分钟数时点亮它
        for (int i = 0; i < quickBtns.size(); i++) {
            boolean on = dayPick.getValue() == 0 && hourPick.getValue() == 0
                    && minPick.getValue() == QUICK_MIN[i];
            quickBtns.get(i).setBackground(round(on ? ACCENT : BTN, 10));
            quickBtns.get(i).setTextColor(on ? Color.parseColor("#1A1206") : Color.WHITE);
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

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
