package com.ti.tasktimer;

import android.app.Activity;
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
 * 晨间页（v1.16.0）：晨间首填开启后，每天 dayStart 之后第一次打开知行先到这里，
 * 全屏、不可返回退出，填完三节才能点「开启今天」：
 *   A 昨日习惯——逐项按类型填（至少/至多型二选一、数值型填数、累积型记增量），
 *     留空不算数，必须全答；
 *   B 昨日知行小报——只读，单日口径的昨日指数（ZhixingCalc.computeDay）；
 *   C 晨间日记——必填非空，存到新一天（session 日）名下。
 * 完成时落盘：logs 记昨日、diary 存当日、morningDone 记 session 日（当天不再出现）。
 * session/昨日的日界口径见 HabitData.sessionDate（dayStart 之前还算前一个 session）。
 */
public class MorningActivity extends Activity {
    private JSONObject root;
    private String session;
    private String logDate;
    private List<HabitData.Habit> habits;
    private final Map<String, Long> ynAnswers = new HashMap<>(); // min/max 的已答值（1/0）
    private final Map<String, EditText> numInputs = new HashMap<>(); // num/sum 的输入框
    private EditText diaryInput;
    private TextView startBtn;
    private TextView startNote;
    private final List<View> dots = new ArrayList<>();

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        root = HabitData.load(this);
        if (!HabitData.shouldShowMorning(root)) {
            finish();
            return;
        }
        session = HabitData.sessionDate(root);
        logDate = HabitData.logDateFor(session);
        habits = HabitData.habits(root);
        buildUi();
    }

    /** 不可返回退出：晨间页没有出口，填完点「开启今天」是唯一出路。 */
    @Override
    public void onBackPressed() {
        // 按设计不响应返回键
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
        hello.setText(s("早上好", "Good morning"));
        hello.setTextSize(27);
        hello.setTypeface(Typeface.SERIF, Typeface.BOLD);
        hello.setTextColor(Ui.text(this));
        col.addView(hello);
        java.util.Calendar sc = DayUtil.parse(session);
        int dowIdx = sc == null ? 0 : (sc.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7;
        TextView date = small(session + " · " + DayUtil.weekdayName(dowIdx)
                + s(" ｜ 填完这一页，今天才算开启", " | Finish this page to start the day"), 12.5f);
        date.setPadding(0, dp(4), 0, 0);
        col.addView(date);

        // 进度点
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

        col.addView(buildSectionA());
        col.addView(buildSectionB());
        col.addView(buildSectionC());

        startBtn = new TextView(this);
        startBtn.setText(s("开启今天", "Start the day"));
        startBtn.setTextSize(16.5f);
        startBtn.setTypeface(startBtn.getTypeface(), Typeface.BOLD);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setPadding(0, dp(15), 0, dp(15));
        startBtn.setTextColor(Color.WHITE);
        startBtn.setBackground(round(Color.rgb(221, 214, 198), dp(15)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(18);
        startBtn.setOnClickListener(v -> complete());
        col.addView(startBtn, slp);
        startNote = small(s("填完才能进入今天", "Finish everything to enter the day"), 11);
        startNote.setGravity(Gravity.CENTER);
        startNote.setPadding(0, dp(8), 0, 0);
        col.addView(startNote);
        refreshGate();
    }

    // ---------------- A：昨日习惯 ----------------
    private View buildSectionA() {
        LinearLayout sec = section(s("A · 昨日习惯", "A · Yesterday's habits"),
                s("回想昨天（", "Think back to yesterday (") + DayUtil.shortDate(logDate)
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
        b1.setOnClickListener(v -> { ynAnswers.put(h.id, 1L); paint.run(); refreshGate(); });
        b2.setOnClickListener(v -> { ynAnswers.put(h.id, 0L); paint.run(); refreshGate(); });
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
                boolean filled = e.toString().trim().length() > 0;
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
        LinearLayout sec = section(s("B · 昨日知行小报", "B · Yesterday's report"),
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
            @Override public void afterTextChanged(Editable e) { refreshGate(); }
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
        });
        sec.addView(diaryField);
        return sec;
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
        Toast.makeText(this, s("今天已开启", "The day has begun"), Toast.LENGTH_SHORT).show();
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
