package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;

/**
 * 朗读页（v1.49.0）：read 型习惯（自读）的专属页。
 * 点开就是要读的全文（习惯的 remindText），没有打卡格。
 * v1.73.0：自读是特殊习惯——要找得到、能编辑、记得住一共读了多少遍（用户亲定）。
 * 底部按钮改「读完一遍」：每点一次经 HabitData.addReadPass 给逻辑日今天的
 * logs +1（同日多遍多计），落盘＋走既有 habit_data 同步，点完留在本页、
 * 计数当场刷新；长按按钮经 removeReadPass 减一遍（误点纠正）。
 * 完成口径不变：某天遍数 >0 即那天完成，知行指数照旧经 logs 计入。
 * 右上角「编辑」可直接改全文：保存写回 HabitData 落盘＋同步（本地先行，
 * 没网也存得下），与习惯页的既有编辑分支改的是同一份 remindText。
 */
public class ReadActivity extends Activity {
    public static final String EXTRA_HABIT_ID = "read_habit_id";

    private String habitId;
    private HabitData.Habit habit;
    private JSONObject root;
    private TextView countsView;
    private TextView bodyView;
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

    private long todayCount() {
        Long v = HabitData.logValue(root, DayUtil.today(), habit.id);
        return v == null ? 0 : v;
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

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(habit.name);
        title.setTextSize(23);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(Ui.text(this));
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView editBtn = new TextView(this);
        editBtn.setText(s("编辑", "Edit"));
        editBtn.setTextSize(14);
        editBtn.setTextColor(Ui.accent(this));
        editBtn.setPadding(dp(10), dp(6), 0, dp(6));
        editBtn.setOnClickListener(v -> showEditDialog());
        head.addView(editBtn);
        col.addView(head);

        countsView = small("", 12.5f);
        countsView.setPadding(0, dp(5), 0, 0);
        col.addView(countsView);
        TextView tag = small(s("自读 · 慢慢读，读完点下面的「读完一遍」；长按按钮可减一遍",
                "Reading · take your time — tap \"Finished one reading\" below; long-press the button to take one back"), 12);
        tag.setPadding(0, dp(3), 0, 0);
        col.addView(tag);

        bodyView = new TextView(this);
        bodyView.setLineSpacing(0, 1.55f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(22);
        col.addView(bodyView, blp);
        paintBody();
        paintCounts();

        doneBtn = new TextView(this);
        doneBtn.setText(s("读完一遍", "Finished one reading"));
        doneBtn.setTextSize(15.5f);
        doneBtn.setTypeface(doneBtn.getTypeface(), Typeface.BOLD);
        doneBtn.setGravity(Gravity.CENTER);
        doneBtn.setPadding(0, dp(14), 0, dp(14));
        doneBtn.setBackground(round(Ui.accent(this), dp(15)));
        doneBtn.setTextColor(Ui.onAccent(this));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(dp(18), dp(10), dp(18), dp(16));
        page.addView(doneBtn, dlp);
        doneBtn.setOnClickListener(v -> {
            // 遍数只走 HabitData 的计数口径（与晨间页同一实现）：点一次 +1
            HabitData.addReadPass(root, DayUtil.today(), habit.id);
            HabitData.save(ReadActivity.this, root);
            paintCounts();
            Toast.makeText(this, s("记了一遍 · 累计 " + HabitData.readTotalCount(root, habit.id) + " 遍",
                    "One more · " + HabitData.readTotalCount(root, habit.id) + " in total"),
                    Toast.LENGTH_SHORT).show();
        });
        doneBtn.setOnLongClickListener(v -> {
            if (todayCount() <= 0) return true;
            HabitData.removeReadPass(root, DayUtil.today(), habit.id);
            HabitData.save(ReadActivity.this, root);
            paintCounts();
            Toast.makeText(this, s("减了一遍 · 今天 " + todayCount() + " 遍",
                    "One taken back · today " + todayCount()), Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void paintCounts() {
        if (countsView == null) return;
        countsView.setText(s("累计 " + HabitData.readTotalCount(root, habit.id) + " 遍 · 今天 " + todayCount() + " 遍",
                "Total " + HabitData.readTotalCount(root, habit.id) + " readings · today " + todayCount()));
    }

    private void paintBody() {
        if (bodyView == null) return;
        String full = habit.remindText == null ? "" : habit.remindText.trim();
        if (full.isEmpty()) {
            bodyView.setText(s("还没填要读的内容——点右上角「编辑」，把全文填在这里",
                    "Nothing to read yet — tap Edit at the top right and put the full text here"));
            bodyView.setTextColor(Ui.sub(this));
            bodyView.setTextSize(15);
        } else {
            bodyView.setText(full);
            bodyView.setTextColor(Ui.text(this));
            bodyView.setTextSize(17);
        }
    }

    // ---------------- 编辑全文（F3） ----------------
    /**
     * 全文编辑：大对话框多行输入（照习惯页编辑分支的口径，改的是同一份 remindText）。
     * 保存＝本地先行：先写回 HabitData 落盘，同步走 HabitData.save 的既有路径，
     * 没网也存得下、不丢字；习惯页的 TYPE_READ 编辑分支保留不动，两边改同一份数据。
     */
    private void showEditDialog() {
        final EditText input = new EditText(this);
        input.setText(habit.remindText == null ? "" : habit.remindText);
        input.setHint(s("把要读的全文写在这里", "Put the full passage to read here"));
        input.setTextSize(15);
        input.setMinLines(10);
        input.setGravity(Gravity.TOP);
        input.setSelection(input.getText().length());
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(habit.name + s(" · 全文", " · text"))
                .setView(box)
                .setNegativeButton(s("取消", "Cancel"), null)
                .setPositiveButton(s("保存", "Save"), (d, w) -> {
                    String text = input.getText().toString().trim();
                    List<HabitData.Habit> all = HabitData.habits(root);
                    for (HabitData.Habit x : all) {
                        if (x.id.equals(habit.id)) x.remindText = text;
                    }
                    HabitData.putHabits(root, all);
                    HabitData.save(ReadActivity.this, root);
                    HabitData.Habit fresh = HabitData.habitById(root, habit.id);
                    if (fresh != null) habit = fresh;
                    paintBody();
                    Toast.makeText(this, s("全文已保存", "Text saved"), Toast.LENGTH_SHORT).show();
                })
                .show();
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
