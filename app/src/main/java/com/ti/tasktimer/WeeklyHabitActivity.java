package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * 「每周目标反思」详情页（v1.47.0）：weekly 型习惯的专属页。
 * 本周目标（页内改、保存即同步）＋下周目标（可随时提前填，周门填的就是它）
 * ＋今日反省（文本框，输入即时本地保存；非空⇔logs[今天]=1，清空回落未完成，
 * 与知行指数/晨间既有 logs 口径一致）＋历史（按周倒序：周目标只读，
 * 每天的反省点开可补改，补改同步回写对应日期的 logs）。
 * 数据在 WeeklyData（user_state.weekly_data 列同步），本页不自创存储。
 */
public class WeeklyHabitActivity extends Activity {
    public static final String EXTRA_HABIT_ID = "weekly_habit_id";

    private String habitId;
    private HabitData.Habit habit;
    private JSONObject wroot;
    private TextView todayStatus;
    private boolean logDirty = false;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        habitId = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_HABIT_ID);
        JSONObject hroot = HabitData.load(this);
        habit = habitId == null ? null : HabitData.habitById(hroot, habitId);
        if (habit == null || !HabitData.TYPE_WEEKLY.equals(habit.type)) { finish(); return; }
        wroot = WeeklyData.load(this);
        build();
    }

    @Override protected void onPause() {
        super.onPause();
        // 离开页面时整份落盘＋同步（逐字输入只走 saveQuiet，这里补齐）
        try {
            WeeklyData.save(this, wroot);
            if (logDirty) {
                HabitData.save(this, HabitData.load(this));
                logDirty = false;
            }
        } catch (Exception ignored) { }
    }

    // ---------------- 数据动作 ----------------
    /** 反省文本与完成位同步：非空→logs[date]=1，清空→撤下。与晨间/知行同一口径。 */
    private void syncLog(String date, String text) {
        JSONObject hr = HabitData.load(this);
        if (text == null || text.trim().isEmpty()) HabitData.removeLog(hr, date, habitId);
        else HabitData.putLog(hr, date, habitId, 1);
        HabitData.saveQuiet(this, hr);
        logDirty = true;
    }

    private void saveGoal(String monday, String text) {
        WeeklyData.putGoal(wroot, habitId, monday, text);
        WeeklyData.save(this, wroot);
        Toast.makeText(this, s("已保存", "Saved"), Toast.LENGTH_SHORT).show();
        rebuild();
    }

    // ---------------- 页面 ----------------
    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(24));
        scroll.addView(col);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(habit.name);
        title.setTextSize(23);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(Ui.text(this));
        col.addView(title);
        TextView tag = small(s("每周目标反思 · 每周一个目标，每天写一句反省",
                "Weekly goal & reflection · one goal a week, a line a day"), 12);
        tag.setPadding(0, dp(4), 0, 0);
        col.addView(tag);

        String thisMon = WeeklyData.thisMonday();
        String nextMon = WeeklyData.nextMonday();
        String today = DayUtil.today();

        // 本周目标
        col.addView(sectionTitle(s("本周目标", "This week's goal")));
        col.addView(goalEditor(thisMon, WeeklyData.goal(wroot, habitId, thisMon)));
        // 下周目标
        col.addView(sectionTitle(s("下周目标", "Next week's goal")));
        TextView ngHint = small(s("周日晚上 8 点前填好，周门就不会拦你",
                "Fill it before Sunday 8 pm and the weekly gate won't stop you"), 11.5f);
        col.addView(ngHint);
        col.addView(goalEditor(nextMon, WeeklyData.goal(wroot, habitId, nextMon)));

        // 今日反省
        col.addView(sectionTitle(s("今日反省", "Today's reflection")
                + " · " + DayUtil.shortDate(today)));
        final EditText rin = new EditText(this);
        rin.setText(WeeklyData.reflection(wroot, habitId, today));
        rin.setHint(s("今天做得怎么样，写一句", "How did today go — one line"));
        rin.setTextSize(14.5f);
        rin.setMinLines(3);
        rin.setGravity(Gravity.TOP);
        rin.setSelection(rin.getText().length());
        col.addView(rin);
        todayStatus = small("", 11.5f);
        col.addView(todayStatus);
        paintTodayStatus(rin.getText().toString());
        rin.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable e) {
                String t = e.toString();
                WeeklyData.putReflection(wroot, habitId, today, t);
                WeeklyData.saveQuiet(WeeklyHabitActivity.this, wroot);
                syncLog(today, t);
                paintTodayStatus(t);
            }
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
        });

        // 历史
        col.addView(sectionTitle(s("过去各周", "Past weeks")));
        boolean any = false;
        for (String monday : WeeklyData.weeksOf(wroot, habitId)) {
            if (monday.compareTo(thisMon) >= 0) continue; // 本周及以后在上面编辑，不进历史
            any = true;
            col.addView(weekBlock(monday));
        }
        if (!any) {
            col.addView(small(s("还没有过去的周。", "No past weeks yet."), 12.5f));
        }
    }

    private void rebuild() {
        wroot = WeeklyData.load(this);
        build();
    }

    private void paintTodayStatus(String t) {
        if (todayStatus == null) return;
        boolean done = t != null && !t.trim().isEmpty();
        todayStatus.setText(done ? s("已记为今天完成", "Counts as done for today")
                : s("写下反省，今天这个习惯就算完成", "Write a reflection and today counts as done"));
        todayStatus.setTextColor(done ? Ui.green(this) : Ui.sub(this));
    }

    /** 目标编辑块：输入框（预填当前值）＋保存按钮，保存即同步。 */
    private View goalEditor(final String monday, String current) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final EditText in = new EditText(this);
        in.setText(current);
        in.setHint(s("这一周的目标", "The goal for this week"));
        in.setTextSize(14.5f);
        in.setMinLines(2);
        in.setGravity(Gravity.TOP);
        box.addView(in);
        TextView save = pill(s("保存", "Save"), true);
        save.setOnClickListener(v -> saveGoal(monday, in.getText().toString()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        lp.gravity = Gravity.END;
        box.addView(save, lp);
        return box;
    }

    /** 历史里的一周：周目标（只读）＋该周每天的反省（点一条补改）。 */
    private View weekBlock(final String monday) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(roundStroke(Ui.surface(this), Ui.line(this), dp(12)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(8);
        box.setLayoutParams(blp);

        TextView head = new TextView(this);
        head.setText(DayUtil.shortDate(monday) + s(" 那周", " week"));
        head.setTextSize(13.5f);
        head.setTypeface(head.getTypeface(), Typeface.BOLD);
        head.setTextColor(Ui.text(this));
        box.addView(head);
        String g = WeeklyData.goal(wroot, habitId, monday);
        TextView goal = small(s("目标：", "Goal: ") + (g.isEmpty() ? s("未设", "not set") : g), 12.5f);
        goal.setTextColor(Ui.text(this));
        box.addView(goal);

        int idx = 0;
        for (final String date : DayUtil.weekDates(monday)) {
            final String text = WeeklyData.reflection(wroot, habitId, date);
            if (!text.trim().isEmpty()) {
                TextView line = new TextView(this);
                line.setText(DayUtil.shortDate(date) + " " + DayUtil.weekdayName(idx)
                        + " · " + text);
                line.setTextSize(12.5f);
                line.setTextColor(Ui.sub(this));
                line.setPadding(0, dp(5), 0, 0);
                line.setOnClickListener(v -> editReflectionDialog(date, text));
                box.addView(line);
            }
            idx++;
        }
        return box;
    }

    private void editReflectionDialog(final String date, String current) {
        final EditText in = new EditText(this);
        in.setText(current);
        in.setMinLines(3);
        in.setGravity(Gravity.TOP);
        in.setSelection(in.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(DayUtil.shortDate(date) + s(" 的反省", "'s reflection"))
                .setView(in)
                .setPositiveButton(s("保存", "Save"), (d, w) -> {
                    String t = in.getText().toString();
                    WeeklyData.putReflection(wroot, habitId, date, t);
                    WeeklyData.save(this, wroot);
                    syncLog(date, t);
                    HabitData.save(this, HabitData.load(this));
                    logDirty = false;
                    rebuild();
                })
                .setNegativeButton(Strings.btnCancel(), null)
                .show();
    }

    // ---------------- 小构件 ----------------
    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(Typeface.SERIF, Typeface.BOLD);
        t.setTextColor(Ui.text(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(18);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView small(String text, float sizeSp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(Ui.sub(this));
        return t;
    }

    private TextView pill(String text, boolean solid) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        if (solid) {
            b.setTextColor(Ui.onAccent(this));
            b.setBackground(round(Ui.text(this), dp(10)));
        } else {
            b.setTextColor(Ui.text(this));
            b.setBackground(roundStroke(Ui.surface(this), Ui.line(this), dp(10)));
        }
        return b;
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
