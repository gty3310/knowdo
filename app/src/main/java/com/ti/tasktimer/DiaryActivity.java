package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 日记页（v1.48.0）：diary 型习惯（记日记）的专属页。
 * 上部写今天的日记，下部按日期倒序翻看历史，点任意一天可补改。
 * 日记文本就是 HabitData.diary 这份全局日记（与晨间页 C 节共用同一份，
 * 不按习惯分仓）；保存走 HabitData.putDiary——它会把 diary 型习惯的
 * 完成位与文本同步维护（非空⇔logs[date]=1、清空回落未完成），
 * 与知行指数/晨间昨日习惯输入的既有 logs 口径一致，本页不另设计数。
 * 保存口径与晨间页一致：存的是 trim 后的文本，输入即时落盘、离开整份同步。
 */
public class DiaryActivity extends Activity {
    public static final String EXTRA_HABIT_ID = "diary_habit_id";

    private String habitId;
    private HabitData.Habit habit;
    private JSONObject root;
    private TextView todayStatus;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        habitId = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_HABIT_ID);
        root = HabitData.load(this);
        habit = habitId == null ? null : HabitData.habitById(root, habitId);
        if (habit == null || !HabitData.TYPE_DIARY.equals(habit.type)) { finish(); return; }
        build();
    }

    @Override protected void onPause() {
        super.onPause();
        // 离开页面时整份落盘＋同步（逐字输入只走 saveQuiet，这里补齐）
        try { HabitData.save(this, root); } catch (Exception ignored) { }
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
        TextView tag = small(s("记日记 · 写了就算完成，没有字数要求",
                "Diary · writing counts as done, no length required"), 12);
        tag.setPadding(0, dp(4), 0, 0);
        col.addView(tag);

        final String today = DayUtil.today();

        // 今天的日记
        col.addView(sectionTitle(s("今天的日记", "Today's entry") + " · " + DayUtil.shortDate(today)));
        final EditText din = new EditText(this);
        din.setText(HabitData.diary(root, today));
        din.setHint(s("写一句今天的打算，或今天的省察……", "One line: today's plan, or today's reflection…"));
        din.setTextSize(14.5f);
        din.setMinLines(5);
        din.setGravity(Gravity.TOP);
        din.setSelection(din.getText().length());
        col.addView(din);
        todayStatus = small("", 11.5f);
        col.addView(todayStatus);
        paintTodayStatus(din.getText().toString());
        din.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable e) {
                // 与晨间页同一口径：存 trim 后的文本；putDiary 同步维护完成位
                HabitData.putDiary(root, today, e.toString().trim());
                HabitData.saveQuiet(DiaryActivity.this, root);
                paintTodayStatus(e.toString());
            }
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
        });

        // 历史日记（今天在上面写，不进列表；空文本的日期不算一篇，不列）
        col.addView(sectionTitle(s("过去的日记", "Past entries")));
        List<String> dates = diaryDates();
        boolean any = false;
        for (String date : dates) {
            if (date.equals(today)) continue;
            String text = HabitData.diary(root, date);
            if (text.trim().isEmpty()) continue;
            any = true;
            col.addView(historyRow(date, text));
        }
        if (!any) {
            col.addView(small(s("还没有过去的日记", "No past entries yet"), 12.5f));
        }
    }

    private void rebuild() {
        root = HabitData.load(this);
        build();
    }

    private void paintTodayStatus(String t) {
        if (todayStatus == null) return;
        String trimmed = t == null ? "" : t.trim();
        boolean done = !trimmed.isEmpty();
        todayStatus.setText(done
                ? s("已记为今天完成 · ", "Counts as done for today · ") + trimmed.length() + s(" 字", " chars")
                : s("写下日记，今天这个习惯就算完成", "Write an entry and today counts as done"));
        todayStatus.setTextColor(done ? Ui.green(this) : Ui.sub(this));
    }

    /** 日记里出现过的全部日期，倒序（新→旧）。 */
    private List<String> diaryDates() {
        List<String> out = new ArrayList<>();
        try {
            JSONObject d = root.optJSONObject("diary");
            if (d != null) {
                Iterator<String> it = d.keys();
                while (it.hasNext()) out.add(it.next());
            }
        } catch (Exception ignored) { }
        Collections.sort(out, (a, b) -> b.compareTo(a));
        return out;
    }

    /** 历史里的一天：日期＋星期＋字数，下带首行预览；点开补改。 */
    private LinearLayout historyRow(final String date, final String text) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(roundStroke(Ui.surface(this), Ui.line(this), dp(12)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(8);
        box.setLayoutParams(blp);

        TextView head = new TextView(this);
        head.setText(DayUtil.shortDate(date) + " " + weekdayOf(date)
                + " · " + text.trim().length() + s(" 字", " chars"));
        head.setTextSize(13.5f);
        head.setTypeface(head.getTypeface(), Typeface.BOLD);
        head.setTextColor(Ui.text(this));
        box.addView(head);

        TextView prev = new TextView(this);
        prev.setText(firstLine(text));
        prev.setTextSize(12.5f);
        prev.setTextColor(Ui.sub(this));
        prev.setPadding(0, dp(4), 0, 0);
        box.addView(prev);

        box.setOnClickListener(v -> editDiaryDialog(date, text));
        return box;
    }

    private void editDiaryDialog(final String date, String current) {
        final EditText in = new EditText(this);
        in.setText(current);
        in.setMinLines(5);
        in.setGravity(Gravity.TOP);
        in.setSelection(in.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(DayUtil.shortDate(date) + " " + weekdayOf(date) + s(" 的日记", "'s entry"))
                .setView(in)
                .setPositiveButton(s("保存", "Save"), (d, w) -> {
                    HabitData.putDiary(root, date, in.getText().toString().trim());
                    HabitData.save(this, root);
                    rebuild();
                })
                .setNegativeButton(Strings.btnCancel(), null)
                .show();
    }

    private static String weekdayOf(String date) {
        Calendar cal = DayUtil.parse(date);
        if (cal == null) return "";
        int idx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7; // 周一=0 … 周日=6
        return DayUtil.weekdayName(idx);
    }

    private static String firstLine(String text) {
        String t = text.trim();
        int nl = t.indexOf('\n');
        String line = nl >= 0 ? t.substring(0, nl) : t;
        return line.length() > 60 ? line.substring(0, 60) + "…" : line;
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

    private GradientDrawable roundStroke(int color, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        g.setStroke(dp(1), stroke);
        return g;
    }
}
