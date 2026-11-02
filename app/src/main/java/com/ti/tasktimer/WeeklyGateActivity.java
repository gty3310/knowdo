package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 周门页（v1.47.0）：周日 20:00 起（本地墙钟），只要有 weekly 型习惯的下周目标
 * 还空着，App 打开/回前台就先拦在这里（MainActivity 在晨间门之后拉起本页）；
 * 拖过周一早上仍拦，填完才进主界面。提前在详情页填好下周目标则不弹。
 * 本页内容：上周目标与上周每日反省（只读回顾）＋必须填的目标输入框
 * （差哪个周填哪个：本周目标拖欠的也在此补上）。返回键不放行，填完点按钮才走。
 */
public class WeeklyGateActivity extends Activity {

    private static final class Need {
        HabitData.Habit habit;
        String monday;
        EditText input;
    }

    private final List<Need> needs = new ArrayList<>();
    private TextView submit;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!WeeklyData.gatePending(this)) { finish(); return; }
        build();
    }

    @Override public void onBackPressed() {
        // 周门不放行：填完点按钮才走（回前台时 MainActivity 仍会再拉起）
    }

    private void build() {
        JSONObject wroot = WeeklyData.load(this);
        List<HabitData.Habit> ws = WeeklyData.weeklyHabits(this);
        List<String> required = WeeklyData.requiredMondays();
        String thisMon = WeeklyData.thisMonday();
        // 回顾周：周日看正在收尾的本周，其余日子看刚过去的一周
        java.util.Calendar cal = DayUtil.parse(DayUtil.today());
        boolean sunday = cal != null
                && cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY;
        String reviewMon = sunday ? thisMon : DayUtil.addDays(thisMon, -7);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(26), dp(20), dp(24));
        scroll.addView(col);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(s("先把目标填好", "Set the goal first"));
        title.setTextSize(24);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(Ui.text(this));
        col.addView(title);
        TextView sub = small(s("周日晚上 8 点了——先想清楚接下来这一周往哪走，填完就放行",
                "It's past Sunday 8 pm — decide where the coming week is heading, then you're through"), 12.5f);
        sub.setPadding(0, dp(6), 0, 0);
        col.addView(sub);

        TextWatcher tw = new TextWatcher() {
            @Override public void afterTextChanged(Editable e) { paintSubmit(); }
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
        };

        for (HabitData.Habit h : ws) {
            // 上周回顾（只读）
            col.addView(sectionTitle(h.name + s(" · 上周回顾", " · last week")));
            String g = WeeklyData.goal(wroot, h.id, reviewMon);
            TextView goal = small(s("上周目标：", "Last week's goal: ")
                    + (g.isEmpty() ? s("未设", "not set") : g), 13);
            goal.setTextColor(Ui.text(this));
            col.addView(goal);
            int idx = 0;
            boolean anyRef = false;
            for (String date : DayUtil.weekDates(reviewMon)) {
                String text = WeeklyData.reflection(wroot, h.id, date);
                if (!text.trim().isEmpty()) {
                    anyRef = true;
                    TextView line = small(DayUtil.shortDate(date) + " "
                            + DayUtil.weekdayName(idx) + " · " + text, 12.5f);
                    line.setPadding(0, dp(4), 0, 0);
                    col.addView(line);
                }
                idx++;
            }
            if (!anyRef) {
                TextView none = small(s("上周没有写反省", "No reflections last week"), 12.5f);
                none.setPadding(0, dp(4), 0, 0);
                col.addView(none);
            }
            // 还差的目标
            for (String monday : required) {
                if (!WeeklyData.goal(wroot, h.id, monday).isEmpty()) continue;
                boolean isThisWeek = monday.equals(thisMon);
                col.addView(sectionTitle(h.name + (isThisWeek
                        ? s(" · 本周目标还没填", " · this week's goal is empty")
                        : s(" · 下周目标", " · next week's goal"))));
                EditText in = new EditText(this);
                in.setHint(s("写下这一周的目标", "Write the goal for this week"));
                in.setTextSize(15);
                in.setMinLines(2);
                in.setGravity(Gravity.TOP);
                in.addTextChangedListener(tw);
                col.addView(in);
                Need n = new Need();
                n.habit = h;
                n.monday = monday;
                n.input = in;
                needs.add(n);
            }
        }

        submit = new TextView(this);
        submit.setText(s("填好了，开始下周", "Done — start the week"));
        submit.setTextSize(15.5f);
        submit.setTypeface(submit.getTypeface(), Typeface.BOLD);
        submit.setGravity(Gravity.CENTER);
        submit.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(22);
        submit.setLayoutParams(slp);
        submit.setOnClickListener(v -> {
            if (!allFilled()) return;
            JSONObject root = WeeklyData.load(this);
            for (Need n : needs) {
                WeeklyData.putGoal(root, n.habit.id, n.monday, n.input.getText().toString());
            }
            WeeklyData.save(this, root);
            finish();
        });
        col.addView(submit);
        paintSubmit();
        if (needs.isEmpty()) finish(); // 构建期间已被别处填好
    }

    private boolean allFilled() {
        for (Need n : needs) {
            if (n.input.getText().toString().trim().isEmpty()) return false;
        }
        return !needs.isEmpty();
    }

    private void paintSubmit() {
        if (submit == null) return;
        boolean ready = allFilled();
        submit.setBackground(round(ready ? Ui.accent(this) : Color.rgb(221, 214, 198), dp(15)));
        submit.setTextColor(ready ? Color.WHITE : Color.rgb(152, 145, 126));
    }

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

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }
}
