package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * 朗读页（v1.49.0）：read 型习惯（自读）的专属页。
 * 点开就是要读的全文（习惯的 remindText），没有打卡格、没有计数；
 * 读完点底部「读过了」——经 HabitData.putLog 写 logs[今天]=1 并关页，
 * 完成只走这一个口径（知行指数经 logs 计入，不另设）。今天已读过再进来，
 * 按钮显示「今天已读过」，仍可再点（幂等，logs 本就是 1）。
 * 全文为空时只展示占位提示，按钮照样可点。
 */
public class ReadActivity extends Activity {
    public static final String EXTRA_HABIT_ID = "read_habit_id";

    private String habitId;
    private HabitData.Habit habit;
    private JSONObject root;
    private TextView doneBtn;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        habitId = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_HABIT_ID);
        root = HabitData.load(this);
        habit = habitId == null ? null : HabitData.habitById(root, habitId);
        if (habit == null || !HabitData.TYPE_READ.equals(habit.type)) { finish(); return; }
        build();
    }

    private boolean readToday() {
        Long v = HabitData.logValue(root, DayUtil.today(), habit.id);
        return v != null && v > 0;
    }

    // ---------------- 页面 ----------------
    private void build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.bg(this));
        setContentView(page);

        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(26), dp(22), dp(20));
        scroll.addView(col);

        TextView title = new TextView(this);
        title.setText(habit.name);
        title.setTextSize(23);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(Ui.text(this));
        col.addView(title);
        TextView tag = small(s("自读 · 慢慢读，读完点下面的按钮",
                "Reading · take your time — tap the button below when done"), 12);
        tag.setPadding(0, dp(4), 0, 0);
        col.addView(tag);

        String full = habit.remindText == null ? "" : habit.remindText.trim();
        TextView body = new TextView(this);
        if (full.isEmpty()) {
            body.setText(s("还没填要读的内容——回习惯页长按这个习惯选编辑，在那里把全文填上",
                    "Nothing to read yet — go back to the Habits page, long-press this habit and edit to add the text"));
            body.setTextColor(Ui.sub(this));
            body.setTextSize(15);
        } else {
            body.setText(full);
            body.setTextColor(Ui.text(this));
            body.setTextSize(17);
        }
        body.setLineSpacing(0, 1.55f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(22);
        col.addView(body, blp);

        doneBtn = new TextView(this);
        doneBtn.setTextSize(15.5f);
        doneBtn.setTypeface(doneBtn.getTypeface(), Typeface.BOLD);
        doneBtn.setGravity(Gravity.CENTER);
        doneBtn.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(dp(18), dp(10), dp(18), dp(16));
        page.addView(doneBtn, dlp);
        paintBtn();
        doneBtn.setOnClickListener(v -> {
            // 完成只走 logs 这一个口径：点「读过了」才写，重复点幂等
            HabitData.putLog(root, DayUtil.today(), habit.id, 1);
            HabitData.save(ReadActivity.this, root);
            finish();
        });
    }

    private void paintBtn() {
        if (doneBtn == null) return;
        boolean done = readToday();
        doneBtn.setText(done ? s("今天已读过", "Read today") : s("读过了", "Read it"));
        doneBtn.setBackground(round(done ? Ui.muted(this) : Ui.accent(this), dp(15)));
        doneBtn.setTextColor(done ? Ui.sub(this) : Ui.onAccent(this));
    }

    // ---------------- 小构件 ----------------
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
