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
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

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
    private ScrollView morningScroll;
    private LinearLayout morningFoot;
    private LinearLayout pauseArea;
    private TextView pauseBtn;
    private final List<View> dots = new ArrayList<>();

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        HabitData.flipLegacyMaxLogsOnce(this); // v1.66.0 一次性旧账翻正，须在 load 之前
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
            // 门模式自此进入 5 分钟重弹循环（v1.26.0）：未提交、未选推迟就每 5 分钟
            // 弹回，直到提交或选了推迟时长；测试页只是验收工具，不进循环
            if (!testMode) MorningGate.engageNag(this, session);
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
            HabitData.putDiary(root, session, WikiLinks.extractText(diaryInput).trim());
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        morningScroll = scroll;
        scroll.setBackgroundColor(Ui.bg(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(24), dp(18), dp(26));
        scroll.addView(col);
        // v1.40.0 固定底栏：开启今天/保存＋提示语＋暂停区钉在页面最底部、不随
        // 内容滚动；键盘弹起时窗口压缩、底栏稳停在键盘上方。
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.bg(this));
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        morningFoot = new LinearLayout(this);
        morningFoot.setOrientation(LinearLayout.VERTICAL);
        morningFoot.setBackgroundColor(Ui.bg(this));
        morningFoot.setPadding(dp(18), dp(2), dp(18), dp(18));
        page.addView(morningFoot);
        setContentView(page);

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
        View remindSec = buildSectionRemind();
        if (remindSec != null) col.addView(remindSec);
        col.addView(buildSectionB());
        col.addView(buildSectionC());
        col.addView(buildSectionWeekly());

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
        morningFoot.addView(startBtn, slp);
        startNote = small("", 11);
        startNote.setGravity(Gravity.CENTER);
        startNote.setPadding(0, dp(8), 0, 0);
        morningFoot.addView(startNote);

        if (!editMode) morningFoot.addView(buildPauseArea());
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
            if (HabitData.TYPE_WEEKLY.equals(h.type)) {
                // v1.68.0：A 区不再单列只读行——日记框下方有「本周目标反思」就地
                // 填写区（见 buildSectionWeekly），同一信息不在两处呈现
                continue;
            } else if (HabitData.TYPE_DIARY.equals(h.type)) {
                // v1.66.0：日记型不在 A 区再出现——日记框就在本页 C 节，写了即由
                // putDiary 自动记完成，不再单列一行（用户 2026-11-04 亲定）
                continue;
            } else if (HabitData.TYPE_READ.equals(h.type)) {
                sec.addView(readBlock(h, existing));
            } else if (HabitData.TYPE_REMIND.equals(h.type)) {
                sec.addView(remindYnRow(h, existing));
            } else if (HabitData.TYPE_MIN.equals(h.type) || HabitData.TYPE_MAX.equals(h.type)) {
                sec.addView(ynRow(h, existing));
            } else {
                sec.addView(numRow(h, existing));
            }
        }
        return sec;
    }

    /**
     * 自读型的晨间行（v1.49.0 只读昨日行；v1.66.0 合并改版）：旧版这里只读展示
     * 「昨天读没读」，读全文要去习惯页/朗读页，晨间页里出现两处自读呈现。本版合并
     * 成一处（用户 2026-11-04 亲定）：昨日状态小字＋今天这篇的全文（先显示 20 行、
     * 超了给展开按钮）＋文下「读完了」按钮——点按经 putLog 记 session 日（今天）
     * 完成、再点撤销，与提醒型今日块、习惯页、朗读页同一条 putLog 路径、同一个
     * 完成口径。仍不进 A 节必答（aDone 跳过 read——读没读如实记达成率，不拦开启今天）。
     */
    private View readBlock(final HabitData.Habit h, Long yesterdayVal) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(9), 0, dp(9));
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(h.name);
        name.setTextSize(14);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setTextColor(Ui.text(this));
        nameRow.addView(name);
        TextView tn = small(ZhixingPage.HabitPageTypeName.name(h.type), 10);
        tn.setPadding(dp(6), 0, 0, 0);
        nameRow.addView(tn);
        box.addView(nameRow);
        boolean yDone = yesterdayVal != null && yesterdayVal > 0;
        TextView yst = small(yDone
                ? s("昨天已读 · 今天这篇读完点下面「读完了」", "Read yesterday · when you finish today's passage, tap below")
                : s("昨天未读 · 今天这篇读完点下面「读完了」", "Not read yesterday · when you finish today's passage, tap below"), 12);
        if (yDone) yst.setTextColor(Ui.green(this));
        yst.setPadding(0, dp(3), 0, 0);
        box.addView(yst);
        final TextView full = new TextView(this);
        full.setText(h.remindText == null || h.remindText.isEmpty()
                ? s("（还没写要读的全文，去习惯页点习惯名补上）", "(No reading text yet — add it from the habits page)") : h.remindText);
        full.setTextSize(14);
        full.setTextColor(Ui.text(this));
        full.setLineSpacing(0, 1.45f);
        full.setPadding(0, dp(4), 0, dp(6));
        box.addView(full);
        // 先不限行高渲一遍，布局后行数超 20 才收成 20 行并亮出展开按钮
        final TextView expand = ynButton(s("展开全文", "Show all"));
        expand.setVisibility(View.GONE);
        final boolean[] expanded = {false};
        expand.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            full.setMaxLines(expanded[0] ? Integer.MAX_VALUE : 20);
            expand.setText(expanded[0] ? s("收起", "Collapse") : s("展开全文", "Show all"));
            paintYn(expand, expanded[0], Ui.accent(MorningActivity.this));
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        box.addView(expand, elp);
        full.post(() -> {
            if (full.getLineCount() > 20) {
                full.setMaxLines(20);
                expand.setVisibility(View.VISIBLE);
            }
        });
        final TextView btn = ynButton("");
        Runnable paint = () -> {
            Long v = HabitData.logValue(root, session, h.id);
            boolean read = v != null && v > 0;
            btn.setText(read ? s("读完了 ✓ · 点一下撤销", "Finished ✓ · tap to undo") : s("读完了", "Finished reading"));
            paintYn(btn, read, Ui.green(this));
        };
        btn.setOnClickListener(v -> {
            Long cur = HabitData.logValue(root, session, h.id);
            if (cur != null && cur > 0) HabitData.removeLog(root, session, h.id);
            else HabitData.putLog(root, session, h.id, 1L);
            HabitData.save(MorningActivity.this, root); // 边点边存：暂停/被杀不丢
            paint.run();
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(6);
        box.addView(btn, blp);
        paint.run();
        return box;
    }

    /** 每日提醒型的昨日必答行：全文先读，再答「读过了/没读」（写 logDate，边答边存，同 ynRow 口径）。 */
    private View remindYnRow(final HabitData.Habit h, Long existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(9), 0, dp(9));
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(h.name);
        name.setTextSize(14);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setTextColor(Ui.text(this));
        nameRow.addView(name);
        TextView tn = small(ZhixingPage.HabitPageTypeName.name(h.type), 10);
        tn.setPadding(dp(6), 0, 0, 0);
        nameRow.addView(tn);
        box.addView(nameRow);
        TextView full = new TextView(this);
        full.setText(h.remindText == null || h.remindText.isEmpty()
                ? s("（还没写提醒的话）", "(No reminder text yet)") : h.remindText);
        full.setTextSize(14);
        full.setTextColor(Ui.text(this));
        full.setLineSpacing(0, 1.45f);
        full.setPadding(0, dp(4), 0, dp(6));
        box.addView(full);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final TextView b1 = ynButton(s("读过了", "Read it"));
        final TextView b2 = ynButton(s("没读", "Didn't read"));
        final TextView hint = small(s("还没选", "Pick one"), 10.5f);
        hint.setLayoutParams(new LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.WRAP_CONTENT));
        hint.setGravity(Gravity.END);
        if (existing != null) ynAnswers.put(h.id, existing);
        Runnable paint = () -> {
            Long v = ynAnswers.get(h.id);
            paintYn(b1, v != null && v == 1, Ui.green(this));
            paintYn(b2, v != null && v == 0, Color.rgb(160, 152, 132));
            hint.setText(v == null ? s("还没选", "Pick one") : s("已选", "Done"));
            hint.setTextColor(v == null ? Ui.accent(this) : Ui.green(this));
        };
        b1.setOnClickListener(v -> {
            ynAnswers.put(h.id, 1L);
            HabitData.putLog(root, logDate, h.id, 1L);
            HabitData.save(MorningActivity.this, root);
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
        row.addView(b1, blp);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        blp2.leftMargin = dp(6);
        row.addView(b2, blp2);
        row.addView(hint);
        box.addView(row);
        paint.run();
        return box;
    }

    // ---------------- 今日提醒（当天的阅读区） ----------------
    /**
     * 每个每日提醒习惯的全文 + 「读过了」按钮，写的是 session 日（当天）的记录：
     * 在晨间门流程内读完即完成当天，不必再去习惯页点第二次。没有提醒型习惯时整节不出现。
     * 这一节不拦「开启今天」（三节门槛 A/B/C 语义不变），读没读由当天的习惯达成率如实记。
     * 编辑模式下同样出现，写的是记录日当天的记录（可补改）。
     */
    private View buildSectionRemind() {
        List<HabitData.Habit> rems = new ArrayList<>();
        for (HabitData.Habit h : habits) {
            if (HabitData.TYPE_REMIND.equals(h.type)) rems.add(h);
        }
        if (rems.isEmpty()) return null;
        LinearLayout sec = section(
                editMode ? s("今日提醒 · 记录日当天读", "Today's reminders · the record day")
                        : s("今日提醒 · 先读一读", "Today's reminders · read these first"),
                s("读完点「读过了」，今天就算完成，不用再去习惯页点一次",
                        "Tap \"I've read it\" when done — that completes it for today, no need to open the habits page"));
        for (final HabitData.Habit h : rems) sec.addView(remindTodayBlock(h));
        return sec;
    }

    private View remindTodayBlock(final HabitData.Habit h) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(6));
        TextView name = new TextView(this);
        name.setText(h.name);
        name.setTextSize(14);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setTextColor(Ui.text(this));
        box.addView(name);
        TextView full = new TextView(this);
        full.setText(h.remindText == null || h.remindText.isEmpty()
                ? s("（还没写提醒的话，去习惯页点习惯名补上）", "(No reminder text yet — add it from the habits page)") : h.remindText);
        full.setTextSize(14);
        full.setTextColor(Ui.text(this));
        full.setLineSpacing(0, 1.45f);
        full.setPadding(0, dp(3), 0, dp(7));
        box.addView(full);
        final TextView btn = ynButton("");
        Runnable paint = () -> {
            Long v = HabitData.logValue(root, session, h.id);
            boolean read = v != null && v > 0;
            btn.setText(read ? s("已读过了 ✓ · 点一下撤销", "Read ✓ · tap to undo") : s("读过了", "I've read it"));
            paintYn(btn, read, Ui.green(this));
        };
        btn.setOnClickListener(v -> {
            Long cur = HabitData.logValue(root, session, h.id);
            if (cur != null && cur > 0) HabitData.removeLog(root, session, h.id);
            else HabitData.putLog(root, session, h.id, 1L);
            HabitData.save(MorningActivity.this, root); // 边点边存：暂停/被杀不丢
            paint.run();
        });
        box.addView(btn);
        paint.run();
        return box;
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
        // v1.66.0 翻正：至多型旧问法「有/没有」（「有」存 1 涂红、「没有」存 0 涂绿）语义反了
        // ——用户一直按「有＝有做到」理解作答，记下的数据全反。本版起至少/至多同款问
        // 「做到了/没做到」：至少型 做到了=1、没做到=0（不变）；至多型 做到了=0（没发生，
        // 绿）、没做到=1（发生了一次，红）。旧账由 HabitData.flipLegacyMaxLogsOnce
        // 在升级后首启一次性对调，本行只管新口径。
        final long doneVal = isMin ? 1L : 0L; // 「做到了」写入的值
        final long notVal = isMin ? 0L : 1L;  // 「没做到」写入的值
        final TextView b1 = ynButton(s("做到了", "Done"));
        final TextView b2 = ynButton(s("没做到", "Not done"));
        final TextView hint = small(s("还没选", "Pick one"), 10.5f);
        hint.setLayoutParams(new LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.WRAP_CONTENT));
        hint.setGravity(Gravity.END);
        if (existing != null) ynAnswers.put(h.id, existing);
        Runnable paint = () -> {
            Long v = ynAnswers.get(h.id);
            paintYn(b1, v != null && v == doneVal, Ui.green(this));
            paintYn(b2, v != null && v == notVal, isMin ? Color.rgb(160, 152, 132) : Ui.danger(this));
            hint.setText(v == null ? s("还没选", "Pick one") : s("已选", "Done"));
            hint.setTextColor(v == null ? Ui.accent(this) : Ui.green(this));
        };
        b1.setOnClickListener(v -> {
            ynAnswers.put(h.id, doneVal);
            HabitData.putLog(root, logDate, h.id, doneVal);
            HabitData.save(MorningActivity.this, root); // 边填边存：暂停/被杀不丢
            paint.run();
            refreshGate();
        });
        b2.setOnClickListener(v -> {
            ynAnswers.put(h.id, notVal);
            HabitData.putLog(root, logDate, h.id, notVal);
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
                // 边填边存（记到 session 日名下）；双链编辑面存的是记号原文（v1.61.0）
                String text = WikiLinks.extractText(diaryField).trim();
                HabitData.putDiary(root, session, text);
                HabitData.saveQuiet(MorningActivity.this, root);
                // 写日记即静默标晨间卡已读（v1.66.0，卡常驻无按钮，见彼处注释）
                markCardReadSilentlyIfWriting(text);
                refreshGate();
            }
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
        });
        // 预填已存的日记（暂停回来/编辑版先填过/门模式接着填，都读同一处）
        String saved = HabitData.diary(root, session);
        if (!saved.isEmpty()) diaryField.setText(saved);
        // 双链（v1.61.0）：输入｛｛引用、点链接开浮窗改词条；浮窗改过数据后重读 root，防旧根整份回写盖掉浮窗的修改
        WikiLinks.attach(this, diaryField, () -> { root = HabitData.load(MorningActivity.this); });
        // 晨间卡区（v1.51.0）：插在日记框上方；编辑模式不拉卡、此区恒空，页面与改前一致
        cardZone = new LinearLayout(this);
        cardZone.setOrientation(LinearLayout.VERTICAL);
        sec.addView(cardZone);
        sec.addView(diaryField);
        if (!editMode) loadMorningCard();
        return sec;
    }

    // ---------------- 本周目标反思（v1.68.0：晨间页就地填） ----------------
    // 用户 2026-11-05 亲定：每周目标反思与日记是同一个概念——在晨间页日记框下方
    // 直接放填写区，就地写、就地存，不再跳习惯页补。欠填项口径与 WeeklyData 的
    // 周门一致（requiredMondays：本周恒在列，周日 20:00 后下周也加入）；目标按
    // 周一日期存（putGoal），反思同样以对应周的周一日期为键存（WeeklyData 的
    // reflection 按日期取键，与 goals 同键——详情页历史区会把它呈现为那周周一
    // 的反省，数据结构不变）。保存走 WeeklyData 既有通道：点「存」时重新 load
    // → putGoal/putReflection → save（本地存＋weekly_data 同步合并沿用既有实现），
    // 存完当场把该项翻成已填呈现。保存方式选点按钮而非失焦自动存：与详情页目标
    // 编辑块同款（写完点保存、即时落盘），晨间页误触失焦也不会半截存入。
    // 本节不进晨间门完成条件（A 节必答与 aDone 跳过 weekly 的口径逐字未动），
    // 也不碰 HabitData.logs——完成位仍归详情页「今日反省」的既有口径。
    // 日期口径：weekly 一系历来用本地墙钟（WeeklyData 的 thisMonday/
    // requiredMondays 即如此），不随晨间 session 日/编辑版记录日走。
    private JSONObject wroot;
    private LinearLayout weeklyItems;

    private View buildSectionWeekly() {
        LinearLayout sec = section(s("本周目标反思", "Weekly goal & reflection"),
                s("就地写、就地存，跟写日记一样", "Write it right here, saved on the spot — just like the diary"));
        weeklyItems = new LinearLayout(this);
        weeklyItems.setOrientation(LinearLayout.VERTICAL);
        sec.addView(weeklyItems);
        renderWeeklyItems();
        return sec;
    }

    private void renderWeeklyItems() {
        if (weeklyItems == null) return;
        weeklyItems.removeAllViews();
        wroot = WeeklyData.load(this);
        List<HabitData.Habit> ws = new ArrayList<>();
        for (HabitData.Habit h : habits) {
            if (HabitData.TYPE_WEEKLY.equals(h.type)) ws.add(h);
        }
        if (ws.isEmpty()) {
            // 没有这类习惯时整节仍在（只留标题＋一行小字），免得用户找不到
            weeklyItems.addView(small(s("还没有每周目标反思的习惯", "No weekly goal & reflection habit yet"), 12.5f));
            return;
        }
        boolean anyItem = false;
        for (final HabitData.Habit h : ws) {
            if (ws.size() > 1) {
                TextView hn = new TextView(this);
                hn.setText(h.name);
                hn.setTextSize(14);
                hn.setTypeface(hn.getTypeface(), Typeface.BOLD);
                hn.setTextColor(Ui.text(this));
                hn.setPadding(0, dp(8), 0, 0);
                weeklyItems.addView(hn);
            }
            for (final String monday : WeeklyData.requiredMondays()) {
                weeklyItems.addView(weeklyItem(h, monday, true));
                weeklyItems.addView(weeklyItem(h, monday, false));
                anyItem = true;
            }
        }
        if (!anyItem) {
            weeklyItems.addView(small(s("本周目标已定，该写的反思也写了", "This week's goal is set and the reflection is written"), 12.5f));
        }
    }

    /**
     * 一项填写（某周目标或某周反思）：未填→多行输入框＋「存」；已填→摘要一行
     * （单行省略）＋「改」，点「改」展开同一个输入框就地改，不跳页。存后整节
     * 按最新 WeeklyData 重画（本节自成一块，不影响 A/C 的输入框状态）。
     */
    private View weeklyItem(final HabitData.Habit h, final String monday, final boolean isGoal) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(4));
        final boolean thisWeek = monday.equals(WeeklyData.thisMonday());
        TextView head = new TextView(this);
        head.setText((isGoal
                        ? (thisWeek ? s("本周目标", "This week's goal") : s("下周目标", "Next week's goal"))
                        : (thisWeek ? s("本周反思", "This week's reflection") : s("下周反思", "Next week's reflection")))
                + " · " + DayUtil.shortDate(monday) + s(" 那周", " week"));
        head.setTextSize(14);
        head.setTypeface(head.getTypeface(), Typeface.BOLD);
        head.setTextColor(Ui.text(this));
        box.addView(head);
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        box.addView(body);
        final boolean[] editing = {false};
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            body.removeAllViews();
            String cur = isGoal ? WeeklyData.goal(wroot, h.id, monday)
                    : WeeklyData.reflection(wroot, h.id, monday);
            if (!editing[0] && !cur.trim().isEmpty()) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(3), 0, 0);
                TextView sum = new TextView(this);
                sum.setText(cur.trim().replace('\n', ' '));
                sum.setTextSize(13.5f);
                sum.setTextColor(Ui.text(this));
                sum.setMaxLines(1);
                sum.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(sum, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                TextView edit = ynButton(s("改", "Edit"));
                edit.setOnClickListener(v -> { editing[0] = true; render[0].run(); });
                LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT);
                elp.leftMargin = dp(8);
                row.addView(edit, elp);
                body.addView(row);
                return;
            }
            final EditText in = new EditText(this);
            in.setText(cur);
            in.setHint(isGoal ? s("这一周的目标", "The goal for this week")
                    : s("这一周做得怎么样，写几句", "How did this week go — a few lines"));
            in.setTextSize(14.5f);
            in.setMinLines(isGoal ? 2 : 3);
            in.setGravity(Gravity.TOP);
            if (!cur.isEmpty()) in.setSelection(in.getText().length());
            body.addView(in);
            TextView save = ynButton(s("存", "Save"));
            paintYn(save, true, Ui.accent(this));
            save.setOnClickListener(v -> {
                String t = in.getText().toString();
                wroot = WeeklyData.load(MorningActivity.this);
                if (isGoal) WeeklyData.putGoal(wroot, h.id, monday, t);
                else WeeklyData.putReflection(wroot, h.id, monday, t);
                WeeklyData.save(MorningActivity.this, wroot);
                Toast.makeText(MorningActivity.this, s("已保存", "Saved"), Toast.LENGTH_SHORT).show();
                renderWeeklyItems();
            });
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT);
            slp.gravity = Gravity.END;
            slp.topMargin = dp(6);
            body.addView(save, slp);
        };
        render[0].run();
        return box;
    }

    // ---------------- 晨间卡（v1.51.0；v1.66.0 常驻改版） ----------------
    // 昨夜由作者的 AI 助手生成的卡片（云端 morning_cards 表，一行一天）：门/测试
    // 模式打开晨间页时拉今天 session 日的那一张。无行/拉失败/无网/未登录/已读
    // → 整区不渲染，日记段与晨间流程保持原样；有未读卡 → 玻璃框常驻列在日记框
    // 上方：v1.66.0 起不再藏日记框、卡上没有「读过了」按钮，用户在日记框写下
    // 内容即静默标已读（markCardReadSilentlyIfWriting：本机标记＋云端 read_at
    // 回写），下次打开晨间页不再重复拉这张。卡不进任何完成条件。
    private static final String CARD_PREF = "morning_cards";
    private LinearLayout cardZone;
    private String cardRowId = "";

    private boolean cardReadLocally(String date) {
        return getSharedPreferences(CARD_PREF, MODE_PRIVATE).getBoolean("read_" + date, false);
    }

    private void markCardReadLocally(String date) {
        getSharedPreferences(CARD_PREF, MODE_PRIVATE).edit()
                .putBoolean("read_" + date, true).apply();
    }

    private void loadMorningCard() {
        if (cardReadLocally(session)) return; // 本机已读：不拉、不显示、不再拦
        if (!Supa.isLoggedIn(this)) return;
        final String date = session;
        new Thread(() -> {
            try {
                JSONArray rows = Supa.restGet(MorningActivity.this,
                        "morning_cards?date=eq." + date + "&select=*");
                if (rows == null || rows.length() == 0) return;
                JSONObject card = rows.optJSONObject(0);
                if (card == null) return;
                if (!card.isNull("read_at")) { markCardReadLocally(date); return; }
                final String id = card.optString("id", "");
                final String summary = card.optString("summary", "").trim();
                final JSONArray items = card.optJSONArray("items");
                boolean hasItems = false;
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.optJSONObject(i);
                        if (it != null && (!it.optString("title", "").trim().isEmpty()
                                || !it.optString("body", "").trim().isEmpty())) { hasItems = true; break; }
                    }
                }
                if (summary.isEmpty() && !hasItems) return; // 空卡不当卡：不拦日记
                runOnUiThread(() -> showMorningCard(id, summary, items));
            } catch (Exception ignored) { }
        }).start();
    }

    private void showMorningCard(final String id, String summary, JSONArray items) {
        if (cardZone == null || diaryField == null) return;
        cardRowId = id == null ? "" : id;
        cardZone.removeAllViews();
        if (!summary.isEmpty()) {
            cardZone.addView(glassCard(s("昨天的状态", "Yesterday, in short"), "", summary, true));
        }
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) continue;
                String title = it.optString("title", "").trim();
                String body = it.optString("body", "").trim();
                if (title.isEmpty() && body.isEmpty()) continue;
                cardZone.addView(glassCard(cardKindLabel(it.optString("kind", "")), title, body, false));
            }
        }
        // v1.66.0：卡常驻在日记框上方——不加「读过了」按钮、不藏日记框；
        // 已读靠写日记静默标记（markCardReadSilentlyIfWriting），见日记输入路径
    }

    /**
     * 写日记即静默记卡已读（v1.66.0）：日记保存路径（晨间页输入监听、complete 落盘）
     * 每次存入非空文本时调一次——本机先标已读，再异步回写云端 read_at，失败静默
     * （本机标记已保证不再重复拉卡）。没拉到卡（cardRowId 空，如编辑模式）是空操作；
     * 已标过已读后靠 cardReadLocally 短路，不重复回写。
     */
    private void markCardReadSilentlyIfWriting(String text) {
        if (cardRowId == null || cardRowId.isEmpty()) return;
        if (text == null || text.trim().isEmpty()) return;
        if (cardReadLocally(session)) return;
        markCardReadLocally(session);
        patchCardRead(cardRowId);
    }

    private String cardKindLabel(String kind) {
        if ("think".equals(kind)) return s("思维原则", "Thinking principle");
        if ("act".equals(kind)) return s("行为原则", "Principle for action");
        if ("question".equals(kind)) return s("待问的问题", "A question to ask");
        return s("晨间卡", "Morning card");
    }

    /** 玻璃框：半透明米白底＋细线边＋轻微抬起，近似毛玻璃；类别小字朱砂、标题宋体墨色。 */
    private View glassCard(String label, String title, String body, boolean isSummary) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(13));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.dark(this) ? Color.argb(190, 52, 72, 62) : Color.argb(205, 255, 254, 249));
        g.setCornerRadius(dp(14));
        g.setStroke(dp(1), Ui.line(this));
        box.setBackground(g);
        box.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = cardZone.getChildCount() == 0 ? 0 : dp(10);
        box.setLayoutParams(lp);
        TextView lab = small(label, 11);
        lab.setTextColor(Ui.accent(this));
        box.addView(lab);
        if (!title.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(title);
            t.setTextSize(15.5f);
            t.setTypeface(Typeface.SERIF, Typeface.BOLD);
            t.setTextColor(Ui.text(this));
            t.setPadding(0, dp(4), 0, 0);
            box.addView(t);
        }
        if (!body.isEmpty()) {
            TextView b = new TextView(this);
            b.setText(body);
            b.setTextSize(isSummary ? 14.5f : 14);
            b.setTextColor(Ui.text(this));
            b.setLineSpacing(0, 1.5f);
            b.setPadding(0, dp(title.isEmpty() ? 4 : 3), 0, 0);
            box.addView(b);
        }
        return box;
    }

    /** 已读回写云端（upsert 只补 read_at 一列，其余列不动）：失败静默，本机标记已保证不再拦。 */
    private void patchCardRead(final String id) {
        if (id == null || id.isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject row = new JSONObject();
                row.put("id", id);
                row.put("read_at", new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(new java.util.Date()));
                Supa.restUpsertOne(MorningActivity.this, "morning_cards", "id", row);
            } catch (Exception ignored) { }
        }).start();
    }

    // ---------------- 暂停一会儿（门/测试模式） ----------------
    private View buildPauseArea() {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        pauseArea = area;
        pauseBtn = new TextView(this);
        pauseBtn.setTextSize(14.5f);
        pauseBtn.setGravity(Gravity.CENTER);
        pauseBtn.setPadding(0, dp(12), 0, dp(12));
        pauseBtn.setMinimumHeight(dp(52));
        pauseBtn.setClickable(true);
        pauseBtn.setFocusable(true);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(14);
        area.addView(pauseBtn, plp);

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
            for (int k = r * 3; k < r * 3 + 3; k++) {
                final long minutes = opts[k];
                TextView chip = ynButton(labels[k]);
                chip.setOnClickListener(v -> doSnooze(minutes * 60_000L,
                        minutes >= 60 ? s("1 小时", "1 hour") : (minutes + s(" 分钟", " min"))));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (k % 3 > 0) lp.leftMargin = dp(6);
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

        TextView note = small(s("有急事（关闹钟、回紧急消息、接电话）先去处理：选个分钟数，这一页会退下，到点自己回来。可以反复暂停。",
                "Something urgent (an alarm to silence, a message, a call)? Pick how long — this page steps aside and comes back on its own when the time is up. You can pause again and again."), 11);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(7), 0, 0);
        area.addView(note);

        paintPauseBtn(false);
        pauseBtn.setOnClickListener(v -> {
            hideKeyboard();
            boolean expand = pausePanel.getVisibility() != View.VISIBLE;
            pausePanel.setVisibility(expand ? View.VISIBLE : View.GONE);
            paintPauseBtn(expand);
            if (expand) scrollPauseIntoView();
        });
        return area;
    }

    /** 暂停按钮两态：收起＝低饱和描边＋「▾」；展开＝主色描边与文字＋「▴」。与填写状态无关，永不禁用。 */
    private void paintPauseBtn(boolean expanded) {
        if (pauseBtn == null) return;
        pauseBtn.setText(expanded ? s("暂停一会儿 ▴", "Pause for a bit ▴")
                : s("暂停一会儿 ▾", "Pause for a bit ▾"));
        pauseBtn.setTextColor(expanded ? Ui.accent(this) : Ui.sub(this));
        pauseBtn.setBackground(roundStroke(Ui.surface(this),
                expanded ? Ui.accent(this) : Ui.line(this), dp(15)));
    }

    /** 点暂停先收键盘：刚在日记/数值框打完字也能立刻展开面板，不被键盘挡住。 */
    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View focus = getCurrentFocus();
            if (imm != null && focus != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            } else if (imm != null && diaryField != null) {
                imm.hideSoftInputFromWindow(diaryField.getWindowToken(), 0);
            }
        } catch (Exception ignored) { }
    }

    /**
     * 展开后把暂停区滚进视口：目标＝pauseArea 在滚动内容中的顶部（getTop 相对父列即内容坐标），
     * 再留 8dp 余量。area 的顶部不随面板展开改变，故布局完成前后目标一致；
     * smoothScrollTo 本身会把目标钳制在 [0, 内容高−视口高]，滚不到内容之外、不会过头；
     * 按钮（52dp）＋面板紧随其下，滚到 area 顶部即整块进入视口。收起时不调用。
     */
    private void scrollPauseIntoView() {
        if (morningScroll == null || pauseArea == null) return;
        // v1.40.0：暂停区已随固定底栏移出滚动区、恒可见，无需滚动定位；仅当它
        // 仍在滚动区内时才按原逻辑滚过去（结构保险，判定与行为口径不变）。
        View p = pauseArea;
        boolean inScroll = false;
        while (p != null) {
            if (p == morningScroll) { inScroll = true; break; }
            android.view.ViewParent par = p.getParent();
            p = par instanceof View ? (View) par : null;
        }
        if (!inScroll) return;
        morningScroll.post(() -> {
            int y = Math.max(0, pauseArea.getTop() - dp(8));
            morningScroll.smoothScrollTo(0, y);
        });
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
            if (HabitData.TYPE_WEEKLY.equals(h.type)) continue; // v1.47.0：反省在详情页写，不进晨间必答
            if (HabitData.TYPE_DIARY.equals(h.type)) continue; // v1.48.0：日记在日记页写，不进晨间必答
            if (HabitData.TYPE_READ.equals(h.type)) continue; // v1.49.0：自读在朗读页读，不进晨间必答
            if (HabitData.TYPE_MIN.equals(h.type) || HabitData.TYPE_MAX.equals(h.type)
                    || HabitData.TYPE_REMIND.equals(h.type)) {
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
            if (HabitData.TYPE_MIN.equals(h.type) || HabitData.TYPE_MAX.equals(h.type)
                    || HabitData.TYPE_REMIND.equals(h.type)) {
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
        String diaryText = WikiLinks.extractText(diaryInput).trim();
        HabitData.putDiary(root, session, diaryText);
        markCardReadSilentlyIfWriting(diaryText); // 输入监听一般已标过，这里兜底
        HabitData.markMorningDone(root, session);
        HabitData.save(this, root);
        MorningGate.onCompleted(this);
        Toast.makeText(this, s("今天已开启", "The day has begun"), Toast.LENGTH_SHORT).show();
        // v1.28.0 feature 9：开启今天 后直接进「今天安排」页排定今天（用户定案的晨间动线）
        startActivity(new android.content.Intent(this, TodayPlanActivity.class));
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
