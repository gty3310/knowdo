package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 晨间页（v1.16.0 建，v1.17.0 升级为三种模式）：
 *
 * - 门模式（gate，无 extra）：晨间首填开启后，每天 dayStart 之后、session 未完成时
 *   出现——v1.17.0 起由晨间门在用户早上第一次用手机时全屏拉起（MorningGate），
 *   打开知行的 MainActivity.onResume 入口保留。全屏、不可返回退出，填完三节
 *   才能点「开启今天」：A 昨日习惯（逐项必答）、B 昨日知行小报（只读）、
 *   C 晨间日记（必填）。底部另有「暂停一会儿」：选 5–60 分钟或自定义，
 *   页面退下、到点由 MorningReceiver 全屏再次拉起，可反复暂停不限次数。
 * - 测试模式（test，设置「晨间」组「测试晨间页」按钮）：不看 dayStart 与
 *   当日完成状态，直接以门模式打开正式页，填完点「开启今天」按正常完成落盘。
 * - 编辑模式（edit，习惯页「晨间记录补记/修改」入口）：可返回退出、不强制填完；
 *   顶部可切换记录日（今天/昨天/前天/选日期），A 记记录日前一天的习惯、
 *   B 是那天的小报、C 是记录日的日记，数据口径与门模式完全一致（同一批
 *   logs/diary）。编辑不标记门完成；若编辑的正是今日 session，门照常触发，
 *   且门模式打开时 A/C 预填这里已存的内容。
 *
 * 暂存（v1.17.0）：三种模式下 A/C 的每次输入都即时写进 HabitData（logs/diary）
 * 并落盘——暂停退下、页面被杀、切日期都不会丢已填项；门模式再次打开时
 * A 区与日记都按已存内容预填（与编辑模式共用同一套数据）。完成落盘口径不变：
 * logs 记昨日、diary 存 session 日、morningDone 记 session 日。
 * session/昨日的日界口径见 HabitData.sessionDate（dayStart 之前还算前一个 session）。
 */
public class MorningActivity extends Activity {
    public static final String EXTRA_MODE = "morning_mode";
    public static final String EXTRA_SESSION = "morning_session";
    public static final String MODE_GATE = "gate";
    public static final String MODE_TEST = "test";
    public static final String MODE_EDIT = "edit";

    private JSONObject root;
    private String session;
    private String logDate;
    private String baseSession; // 编辑版日期切换的「今天」（打开页面时的当前 session 日）
    private boolean editMode;
    private boolean testMode;
    private List<HabitData.Habit> habits;
    private final Map<String, Long> ynAnswers = new HashMap<>(); // min/max 的已答值（1/0）
    private final Map<String, EditText> numInputs = new HashMap<>(); // num/sum 的输入框
    private EditText diaryInput;
    private TextView startBtn;
    private TextView startNote;
    private LinearLayout pausePanel;
    private final List<View> dots = new ArrayList<>();

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        root = HabitData.load(this);
        String mode = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_MODE);
        editMode = MODE_EDIT.equals(mode);
        testMode = MODE_TEST.equals(mode);
        baseSession = HabitData.sessionDate(root);
        if (editMode) {
            String s0 = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_SESSION);
            session = (s0 != null && DayUtil.parse(s0) != null) ? s0 : baseSession;
        } else {
            if (!testMode && !HabitData.shouldShowMorning(root)) {
                finish();
                return;
            }
            session = baseSession;
            // 门/测试页展示出来：推后闹钟的使命已达，清掉推后状态（完成或再暂停时另行处理）
            MorningGate.onGateShown(this);
        }
        logDate = HabitData.logDateFor(session);
        habits = HabitData.habits(root);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        TopPages.morningShown = true;
    }

    @Override
    protected void onPause() {
        TopPages.morningShown = false;
        // 兜底落盘：输入已边填边存，这里再整份保存一次（含同步），防任何漏网
        try {
            if (root != null) {
                flushDraft();
                HabitData.save(this, root);
            }
        } catch (Exception ignored) { }
        super.onPause();
    }

    /** 门/测试模式不可返回退出；编辑模式可以（已填内容边填边存，返回不丢）。 */
    @Override
    public void onBackPressed() {
        if (editMode) {
            super.onBackPressed();
        }
        // 门/测试模式按设计不响应返回键
    }

    /** 把当前控件里的值收进 root（数值框与日记以控件为准；是非题已在点击时写入）。 */
    private void flushDraft() {
        if (root == null || habits == null) return;
        for (HabitData.Habit h : habits) {
            EditText in = numInputs.get(h.id);
            if (in == null) continue;
            String t = in.getText().toString().trim();
            if (t.isEmpty()) {
                HabitData.removeLog(root, logDate, h.id);
            } else {
                try {
                    long v = Long.parseLong(t);
                    if (v >= 0) HabitData.putLog(root, logDate, h.id, v);
                } catch (Exception ignored) { }
            }
        }
        if (diaryInput != null) {
            HabitData.putDiary(root, session, diaryInput.getText().toString().trim());
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(24), dp(18), dp(26));
        scroll.addView(col);
        setContentView(scroll);

        TextView hello = new TextView(this);
        hello.setText(editMode ? s("晨间记录", "Morning records") : s("早上好", "Good morning"));
        hello.setTextSize(27);
        hello.setTypeface(Typeface.SERIF, Typeface.BOLD);
        hello.setTextColor(Ui.text(this));
        col.addView(hello);
        java.util.Calendar sc = DayUtil.parse(session);
        int dowIdx = sc == null ? 0 : (sc.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7;
        TextView date = small(editMode
                        ? s("记录日 ", "Record day ") + session + " · " + DayUtil.weekdayName(dowIdx)
                        : session + " · " + DayUtil.weekdayName(dowIdx)
                                + s(" ｜ 填完这一页，今天才算开启", " | Finish this page to start the day"), 12.5f);
        date.setPadding(0, dp(4), 0, 0);
        col.addView(date);

        if (editMode) col.addView(buildDateSwitcher());

        if (!editMode) {
            // 进度点（门/测试模式）
            LinearLayout dotRow = new LinearLayout(this);
            dotRow.setOrientation(LinearLayout.HORIZONTAL);
            dotRow.setGravity(Gravity.CENTER_VERTICAL);
            dotRow.setPadding(0, dp(12), 0, 0);
            dots.clear();
            for (int i = 0; i < 3; i++) {
                View d = new View(this);
                d.setBackground(round(Ui.line(this), dp(3)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(26), dp(5));
                if (i > 0) lp.leftMargin = dp(6);
                dotRow.addView(d, lp);
                dots.add(d);
            }
            TextView dotText = small("", 11);
            dotText.setTag("dotText");
            dotText.setPadding(dp(8), 0, 0, 0);
            dotRow.addView(dotText);
            col.addView(dotRow);
        }

        col.addView(buildSectionA());
        col.addView(buildSectionB());
        col.addView(buildSectionC());

        startBtn = new TextView(this);
        startBtn.setText(editMode ? s("保存", "Save") : s("开启今天", "Start the day"));
        startBtn.setTextSize(16.5f);
        startBtn.setTypeface(startBtn.getTypeface(), Typeface.BOLD);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding(0, dp(15), 0, dp(15));
        startBtn.setTextColor(Color.WHITE);
        startBtn.setBackground(round(Color.rgb(221, 214, 198), dp(15)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(18);
        startBtn.setOnClickListener(v -> { if (editMode) saveEdit(); else complete(); });
        col.addView(startBtn, slp);
        startNote = small("", 11);
        startNote.setGravity(Gravity.CENTER);
        startNote.setPadding(0, dp(8), 0, 0);
        col.addView(startNote);

        if (!editMode) col.addView(buildPauseArea());
        refreshGate();
    }

    // ---------------- 编辑版：记录日切换 ----------------
    private View buildDateSwitcher() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        String[] labels = {s("今天", "Today"), s("昨天", "Yesterday"), s("前天", "Day before")};
        String[] values = {baseSession, DayUtil.addDays(baseSession, -1), DayUtil.addDays(baseSession, -2)};
        for (int i = 0; i < 3; i++) {
            final String val = values[i];
            TextView chip = ynButton(labels[i]);
            paintYn(chip, session.equals(val), Ui.accent(this));
            chip.setOnClickListener(v -> { if (!val.equals(session)) switchRecordDate(val); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(6);
            row.addView(chip, lp);
        }
        TextView pick = ynButton(s("选日期", "Pick a date"));
        boolean customSel = !session.equals(values[0]) && !session.equals(values[1]) && !session.equals(values[2]);
        paintYn(pick, customSel, Ui.accent(this));
        pick.setOnClickListener(v -> pickRecordDate());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(6);
        row.addView(pick, lp);
        return row;
    }

    private void pickRecordDate() {
        java.util.Calendar cur = DayUtil.parse(session);
        if (cur == null) cur = java.util.Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            String picked = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            if (!picked.equals(session)) switchRecordDate(picked);
        }, cur.get(java.util.Calendar.YEAR), cur.get(java.util.Calendar.MONTH),
                cur.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    /** 切换记录日：当前输入先落盘，再按新日期整页重画（A/B/C 都跟着记录日走）。 */
    private void switchRecordDate(String newSession) {
        flushDraft();
        HabitData.save(this, root);
        session = newSession;
        logDate = HabitData.logDateFor(newSession);
        ynAnswers.clear();
        numInputs.clear();
        root = HabitData.load(this);
        habits = HabitData.habits(root);
        buildUi();
    }

    // ---------------- A：昨日习惯 ----------------
    private View buildSectionA() {
        LinearLayout sec = section(s("A · 昨日习惯", "A · Yesterday's habits"),
                editMode
                        ? s("这一节记的是 ", "This section files the habits of ") + DayUtil.shortDate(logDate)
                                + s("（记录日的前一天）—— 改完即存，不必全填",
                                  " (the day before the record day) — saved as you type, no need to finish")
                        : s("回想昨天（", "Think back to yesterday (") + DayUtil.shortDate(logDate)
                                + s("），逐项点明 —— 留空不算数；数值型填上昨天的数",
                                  "), answer every one — blanks don't count; number habits take yesterday's value"));
        if (habits.isEmpty()) {
            sec.addView(small(s("还没有习惯，这节可以直接过。", "No habits yet — this section is already complete."), 13));
            return sec;
        }
        for (final HabitData.Habit h : habits) {
            Long existing = HabitData.logValue(root, logDate, h.id);
            if (HabitData.TYPE_MIN.equals(h.type) || HabitData.TYPE_MAX.equals(h.type)) {
                sec.addView(ynRow(h, existing));
            } else {
                sec.addView(numRow(h, existing));
            }
        }
        return sec;
    }

    private View ynRow(final HabitData.Habit h, Long existing) {
        final boolean isMin = HabitData.TYPE_MIN.equals(h.type);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        LinearLayout nameBox = new LinearLayout(this);
        nameBox.setOrientation(LinearLayout.VERTICAL);
        nameBox.setLayoutParams(new LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView name = new TextView(this);
        name.setText(h.name);
        name.setTextSize(14);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setTextColor(Ui.text(this));
        nameBox.addView(name);
        nameBox.addView(small(ZhixingPage.HabitPageTypeName.name(h.type), 10));
        row.addView(nameBox);
        final TextView b1 = ynButton(isMin ? s("做到了", "Done") : s("有", "Yes"));
        final TextView b2 = ynButton(isMin ? s("没做到", "Not done") : s("没有", "No"));
        final TextView hint = small(s("还没选", "Pick one"), 10.5f);
        hint.setLayoutParams(new LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.WRAP_CONTENT));
        hint.setGravity(Gravity.END);
        if (existing != null) ynAnswers.put(h.id, existing);
        Runnable paint = () -> {
            Long v = ynAnswers.get(h.id);
            paintYn(b1, v != null && v == 1, isMin ? Ui.green(this) : Ui.danger(this));
            paintYn(b2, v != null && v == 0, isMin ? Color.rgb(160, 152, 132) : Ui.green(this));
            hint.setText(v == null ? s("还没选", "Pick one") : s("已选", "Done"));
            hint.setTextColor(v == null ? Ui.accent(this) : Ui.green(this));
        };
        b1.setOnClickListener(v -> {
            ynAnswers.put(h.id, 1L);
            HabitData.putLog(root, logDate, h.id, 1L);
            HabitData.save(MorningActivity.this, root); // 边填边存：暂停/被杀不丢
            paint.run();
            refreshGate();
        });
        b2.setOnClickListener(v -> {
            ynAnswers.put(h.id, 0L);
            HabitData.putLog(root, logDate, h.id, 0L);
            HabitData.save(MorningActivity.this, root);
            paint.run();
            refreshGate();
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        blp.leftMargin = dp(6);
        row.addView(b1, blp);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        blp2.leftMargin = dp(6);
        row.addView(b2, blp2);
        row.addView(hint);
        paint.run();
        return row;
    }

    private TextView ynButton(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(13.5f);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, dp(9), 0, dp(9));
        b.setTextColor(Ui.sub(this));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.surface(this));
        g.setCornerRadius(dp(10));
        g.setStroke(dp(1), Ui.line(this));
        b.setBackground(g);
        return b;
    }

    private void paintYn(TextView b, boolean selected, int selColor) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        if (selected) {
            g.setColor(selColor);
            b.setTextColor(Color.WHITE);
            b.setTypeface(b.getTypeface(), Typeface.BOLD);
        } else {
            g.setColor(Ui.surface(this));
            g.setStroke(dp(1), Ui.line(this));
            b.setTextColor(Ui.sub(this));
            b.setTypeface(b.getTypeface(), Typeface.NORMAL);
        }
        b.setBackground(g);
    }

    private View numRow(final HabitData.Habit h, Long existing) {
        boolean isSum = HabitData.TYPE_SUM.equals(h.type);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        LinearLayout nameBox = new LinearLayout(this);
        nameBox.setOrientation(LinearLayout.VERTICAL);
        nameBox.setLayoutParams(new LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView name = new TextView(this);
        name.setText(h.name);
        name.setTextSize(14);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setTextColor(Ui.text(this));
        nameBox.addView(name);
        nameBox.addView(small(ZhixingPage.HabitPageTypeName.name(h.type), 10));
        row.addView(nameBox);
        TextView lab = small(isSum ? s("昨天做了多少", "How many yesterday") : s("昨天的数", "Yesterday's value"), 13);
        row.addView(lab);
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setTextSize(14);
        input.setHint(isSum ? "0" : s("如 5230", "e.g. 5230"));
        if (existing != null) input.setText(String.valueOf(existing));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ilp.leftMargin = dp(8);
        row.addView(input, ilp);
        TextView unit = small(h.unit, 13);
        unit.setPadding(dp(4), 0, 0, 0);
        row.addView(unit);
        final TextView hint = small(existing == null ? s("还没填", "Empty") : s("已填", "Done"), 10.5f);
        hint.setLayoutParams(new LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.WRAP_CONTENT));
        hint.setGravity(Gravity.END);
        if (existing != null) hint.setTextColor(Ui.green(this));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable e) {
                String t = e.toString().trim();
                boolean filled = t.length() > 0;
                // 边填边存：有效数字即写入 logs，清空即撤下（只写盘，同步由整份保存兜底）
                if (!filled) {
                    HabitData.removeLog(root, logDate, h.id);
                    HabitData.saveQuiet(MorningActivity.this, root);
                } else {
                    try {
                        long v = Long.parseLong(t);
                        if (v >= 0) {
                            HabitData.putLog(root, logDate, h.id, v);
                            HabitData.saveQuiet(MorningActivity.this, root);
                        }
                    } catch (Exception ignored) { }
                }
                hint.setText(filled ? s("已填", "Done") : s("还没填", "Empty"));
                hint.setTextColor(filled ? Ui.green(MorningActivity.this) : Ui.accent(MorningActivity.this));
                refreshGate();
            }
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
        });
        row.addView(hint);
        numInputs.put(h.id, input);
        return row;
    }

    // ---------------- B：昨日小报 ----------------
    private View buildSectionB() {
        LinearLayout sec = section(
                editMode ? s("B · ", "B · ") + DayUtil.shortDate(logDate) + s(" 知行小报", "'s report")
                        : s("B · 昨日知行小报", "B · Yesterday's report"),
                s("自动算好的，只看，不用填", "Computed for you — read only"));
        TaskStore store = new TaskStore(this);
        store.load();
        ZhixingCalc.DayStats ds = ZhixingCalc.computeDay(this, root, habits,
                new ArrayList<>(store.tasks), logDate);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout ringBox = new FrameLayout(this);
        ZhixingPage.RingView ring = new ZhixingPage.RingView(this, ds.index);
        ringBox.addView(ring, new FrameLayout.LayoutParams(dp(76), dp(76)));
        TextView rn = new TextView(this);
        rn.setText(String.valueOf(ds.index));
        rn.setTextSize(22);
        rn.setTypeface(Typeface.SERIF, Typeface.BOLD);
        rn.setTextColor(Ui.text(this));
        rn.setGravity(Gravity.CENTER);
        ringBox.addView(rn, new FrameLayout.LayoutParams(dp(76), dp(76)));
        row.addView(ringBox);
        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        llp.leftMargin = dp(14);
        TextView l1 = new TextView(this);
        l1.setText(s("昨日指数 ", "Yesterday's index ") + ds.index
                + s(" · 承诺 ", " · promised ") + ds.taskTotal + s(" 件 · 做到 ", ", did ") + ds.taskDone + s(" 件", ""));
        l1.setTextSize(13);
        l1.setTextColor(Ui.text(this));
        l1.setLineSpacing(0, 1.5f);
        lines.addView(l1);
        if (!ds.misses.isEmpty()) {
            TextView miss = new TextView(this);
            miss.setText(s("漏项：", "Missed: ") + ds.misses.get(0));
            miss.setTextSize(12.5f);
            miss.setTextColor(Ui.danger(this));
            lines.addView(miss);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(s("分神 ", "Drifted ")).append(ds.driftCount).append(s(" 次", " times"));
        if (!ds.states.isEmpty()) {
            sb.append(s(" · 自检：", " · states: "));
            for (int i = 0; i < ds.states.size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(ds.states.get(i));
            }
        }
        lines.addView(small(sb.toString(), 12));
        row.addView(lines, llp);
        sec.addView(row);
        return sec;
    }

    // ---------------- C：晨间日记 ----------------
    private EditText diaryField;

    private View buildSectionC() {
        LinearLayout sec = section(s("C · 晨间日记", "C · Morning diary"),
                s("写一句即可 —— 今天的打算，或昨日的省察", "One line is enough — today's plan, or yesterday's reflection"));
        diaryField = new EditText(this);
        diaryField.setHint(s("写一句今天的打算，或昨日的省察……", "One line: today's plan, or yesterday's reflection…"));
        diaryField.setTextSize(14);
        diaryField.setMinLines(3);
        diaryField.setGravity(Gravity.TOP);
        diaryInput = diaryField;
        diaryField.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable e) {
                // 边填边存（记到 session 日名下）
                HabitData.putDiary(root, session, e.toString().trim());
                HabitData.saveQuiet(MorningActivity.this, root);
                refreshGate();
            }
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
        });
        // 预填已存的日记（暂停回来/编辑版先填过/门模式接着填，都读同一处）
        String saved = HabitData.diary(root, session);
        if (!saved.isEmpty()) diaryField.setText(saved);
        sec.addView(diaryField);
        return sec;
    }

    // ---------------- 暂停一会儿（门/测试模式） ----------------
    private View buildPauseArea() {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        TextView pauseBtn = new TextView(this);
        pauseBtn.setText(s("暂停一会儿", "Pause for a bit"));
        pauseBtn.setTextSize(14.5f);
        pauseBtn.setGravity(Gravity.CENTER);
        pauseBtn.setPadding(0, dp(12), 0, dp(12));
        pauseBtn.setTextColor(Ui.sub(this));
        pauseBtn.setBackground(roundStroke(Ui.surface(this), Ui.line(this), dp(15)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(14);
        area.addView(pauseBtn, plp);
        TextView note = small(s("有急事（关闹钟、回紧急消息、接电话）先去处理：选个分钟数，这一页会退下，到点自己回来。可以反复暂停。",
                "Something urgent (an alarm to silence, a message, a call)? Pick how long — this page steps aside and comes back on its own when the time is up. You can pause again and again."), 11);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(7), 0, 0);
        area.addView(note);

        pausePanel = new LinearLayout(this);
        pausePanel.setOrientation(LinearLayout.VERTICAL);
        pausePanel.setPadding(dp(14), dp(12), dp(14), dp(12));
        pausePanel.setBackground(roundStroke(Ui.surface(this), Ui.line(this), dp(16)));
        LinearLayout.LayoutParams panLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panLp.topMargin = dp(10);
        pausePanel.setLayoutParams(panLp);
        TextView pt = new TextView(this);
        pt.setText(s("先暂停多久？", "Pause for how long?"));
        pt.setTextSize(14);
        pt.setTypeface(pt.getTypeface(), Typeface.BOLD);
        pt.setTextColor(Ui.text(this));
        pausePanel.addView(pt);
        final long[] opts = {5, 10, 15, 20, 30, 60};
        String[] labels = {s("5 分钟", "5 min"), s("10 分钟", "10 min"), s("15 分钟", "15 min"),
                s("20 分钟", "20 min"), s("30 分钟", "30 min"), s("1 小时", "1 hour")};
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, 0);
            for (int i = r * 3; i < r * 3 + 3; i++) {
                final long minutes = opts[i];
                TextView chip = ynButton(labels[i]);
                chip.setOnClickListener(v -> doSnooze(minutes * 60_000L,
                        minutes >= 60 ? s("1 小时", "1 hour") : (minutes + s(" 分钟", " min"))));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i % 3 > 0) lp.leftMargin = dp(6);
                row.addView(chip, lp);
            }
            pausePanel.addView(row);
        }
        LinearLayout customRow = new LinearLayout(this);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setPadding(0, dp(8), 0, 0);
        TextView custom = ynButton(s("自定义…", "Custom…"));
        custom.setOnClickListener(v -> askCustomSnooze());
        customRow.addView(custom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pausePanel.addView(customRow);
        pausePanel.setVisibility(View.GONE);
        area.addView(pausePanel);
        pauseBtn.setOnClickListener(v -> pausePanel.setVisibility(
                pausePanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        return area;
    }

    private void askCustomSnooze() {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(s("分钟数，如 45", "Minutes, e.g. 45"));
        input.setTextSize(15);
        new AlertDialog.Builder(this)
                .setTitle(s("暂停多少分钟？", "Pause how many minutes?"))
                .setView(input)
                .setPositiveButton(s("暂停", "Pause"), (d, w) -> {
                    try {
                        long min = Long.parseLong(input.getText().toString().trim());
                        if (min >= 1 && min <= 720) doSnooze(min * 60_000L, min + s(" 分钟", " min"));
                    } catch (Exception ignored) { }
                })
                .setNegativeButton(s("取消", "Cancel"), null)
                .show();
    }

    /** 选定推后时长：已填内容先整份落盘，再约闹钟、退下这一页。 */
    private void doSnooze(long delayMs, String label) {
        flushDraft();
        HabitData.save(this, root);
        MorningGate.snooze(this, delayMs);
        Toast.makeText(this, s("已暂停 ", "Paused for ") + label
                + s("，到点晨间页会自己回来", " — the morning page will be back"), Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---------------- 门槛与完成 ----------------
    private boolean aDone() {
        for (HabitData.Habit h : habits) {
            if (HabitData.TYPE_MIN.equals(h.type) || HabitData.TYPE_MAX.equals(h.type)) {
                if (!ynAnswers.containsKey(h.id)) return false;
            } else {
                EditText in = numInputs.get(h.id);
                if (in == null || in.getText().toString().trim().isEmpty()) return false;
                try {
                    if (Long.parseLong(in.getText().toString().trim()) < 0) return false;
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return true;
    }

    private void refreshGate() {
        if (startBtn == null) return;
        if (editMode) {
            startBtn.setBackground(round(Ui.accent(this), dp(15)));
            startBtn.setTextColor(Color.WHITE);
            startNote.setText(s("边填边存，点「保存」或按返回都不会丢；这里改记录不会算作完成今天的晨间门",
                    "Saved as you type — Save or Back both keep it. Editing here doesn't count as finishing today's morning gate"));
            return;
        }
        boolean a = aDone();
        boolean c = diaryInput != null && !diaryInput.getText().toString().trim().isEmpty();
        int n = (a ? 2 : 0) + (a && c ? 1 : 0);
        for (int i = 0; i < dots.size(); i++) {
            boolean done = (i < 2 && a) || (i == 2 && a && c);
            dots.get(i).setBackground(round(done ? Ui.accent(this) : Ui.line(this), dp(3)));
        }
        // 点旁的计数文字随门槛更新（dotText 构建时挂在点行末尾，按 tag 找）
        TextView dotText = findDotText(getWindow().getDecorView());
        if (dotText != null) dotText.setText(s("开启今天 · ", "Start · ") + n + "/3");
        boolean ready = a && c;
        startBtn.setBackground(round(ready ? Ui.accent(this) : Color.rgb(221, 214, 198), dp(15)));
        startBtn.setTextColor(ready ? Color.WHITE : Color.rgb(152, 145, 126));
        startNote.setText(ready ? s("都填好了，点上面开启今天", "All filled — tap above to start the day")
                : !a ? s("昨日习惯还没全填完", "Yesterday's habits aren't all answered yet")
                : s("日记还空着，写一句就能开启", "The diary is empty — one line and you're in"));
    }

    private TextView findDotText(View v) {
        if (v instanceof TextView && "dotText".equals(v.getTag())) return (TextView) v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView r = findDotText(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    private void complete() {
        if (!aDone() || diaryInput == null || diaryInput.getText().toString().trim().isEmpty()) return;
        flushDraft();
        for (HabitData.Habit h : habits) {
            if (HabitData.TYPE_MIN.equals(h.type) || HabitData.TYPE_MAX.equals(h.type)) {
                Long v = ynAnswers.get(h.id);
                if (v != null) HabitData.putLog(root, logDate, h.id, v);
            } else {
                EditText in = numInputs.get(h.id);
                if (in != null) {
                    try {
                        long v = Long.parseLong(in.getText().toString().trim());
                        HabitData.putLog(root, logDate, h.id, Math.max(0, v));
                    } catch (Exception ignored) { }
                }
            }
        }
        HabitData.putDiary(root, session, diaryInput.getText().toString().trim());
        HabitData.markMorningDone(root, session);
        HabitData.save(this, root);
        MorningGate.onCompleted(this);
        Toast.makeText(this, s("今天已开启", "The day has begun"), Toast.LENGTH_SHORT).show();
        finish();
    }

    /** 编辑版保存：内容已边填边存，这里整份落盘＋同步后退出（不标记门完成）。 */
    private void saveEdit() {
        flushDraft();
        HabitData.save(this, root);
        Toast.makeText(this, s("已保存", "Saved"), Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---------------- 小构件 ----------------
    private LinearLayout section(String title, String subText) {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);
        sec.setPadding(dp(14), dp(12), dp(14), dp(12));
        sec.setBackground(roundStroke(Ui.surface(this), Ui.line(this), dp(16)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        sec.setLayoutParams(lp);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16.5f);
        t.setTypeface(Typeface.SERIF, Typeface.BOLD);
        t.setTextColor(Ui.text(this));
        sec.addView(t);
        TextView st = small(subText, 11.5f);
        st.setPadding(0, dp(3), 0, dp(4));
        sec.addView(st);
        return sec;
    }

    private TextView small(String text, float sizeSp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(Ui.sub(this));
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable roundStroke(int color, int stroke, int radius) {
        GradientDrawable g = round(color, radius);
        g.setStroke(dp(1), stroke);
        return g;
    }
}
