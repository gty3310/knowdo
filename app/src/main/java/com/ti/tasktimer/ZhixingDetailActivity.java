package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 知行指数详情页（v1.28.0 feature 5）：知行页点指数圆环进入。
 * 一周 / 一月 / 一年三档切换：大数字＝当前周期指数；构成表＝指数与三项构成
 * （习惯达成 50% / 任务完成 35% / 分神控制 15%）的本期、上期与变化；趋势行＝
 * 周期内逐时段（一周逐日、一月逐周、一年逐月）的指数。
 * 历史没有逐日指数存档，全部数值由原始记录回算（ZhixingCalc.computeWindow /
 * computeDay，口径与知行页本周指数一致）；窗口内没有任何原始记录的时段
 * 如实显示「数据不足」，不用起算值或今日值填充。
 */
public class ZhixingDetailActivity extends Activity {
    private static final int RANGE_WEEK = 0, RANGE_MONTH = 1, RANGE_YEAR = 2;
    private int range = RANGE_WEEK;

    private JSONObject root;
    private List<HabitData.Habit> habits = new ArrayList<>();
    private List<TaskItem> tasks = new ArrayList<>();
    private LinearLayout col;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = HabitData.load(this);
        habits = HabitData.habits(root);
        TaskStore store = new TaskStore(this);
        store.load();
        tasks = new ArrayList<>(store.tasks);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg(this));
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(24));
        scroll.addView(col);
        setContentView(scroll);
        render();
    }

    // ---------------- 周期定义 ----------------
    private static final class RangeDef {
        String curFrom, curTo, prevFrom, prevTo;
        String deltaLabel;   // 较上周 / 较上月 / 较上年
        String trendTitle;   // 近 7 天 / 本月逐周 / 今年逐月
    }

    private RangeDef rangeDef() {
        RangeDef d = new RangeDef();
        String today = DayUtil.today();
        int y = Integer.parseInt(today.substring(0, 4));
        int m = Integer.parseInt(today.substring(5, 7));
        if (range == RANGE_WEEK) {
            String monday = DayUtil.mondayOf(today);
            d.curFrom = monday;
            d.curTo = DayUtil.addDays(monday, 6);
            d.prevFrom = DayUtil.addDays(monday, -7);
            d.prevTo = DayUtil.addDays(monday, -1);
            d.deltaLabel = s("较上周 ", "vs last week ");
            d.trendTitle = s("近 7 天", "Last 7 days");
        } else if (range == RANGE_MONTH) {
            d.curFrom = monthFirst(y, m);
            d.curTo = monthLast(y, m);
            int py = m == 1 ? y - 1 : y, pm = m == 1 ? 12 : m - 1;
            d.prevFrom = monthFirst(py, pm);
            d.prevTo = monthLast(py, pm);
            d.deltaLabel = s("较上月 ", "vs last month ");
            d.trendTitle = s("本月逐周", "This month by week");
        } else {
            d.curFrom = y + "-01-01";
            d.curTo = y + "-12-31";
            d.prevFrom = (y - 1) + "-01-01";
            d.prevTo = (y - 1) + "-12-31";
            d.deltaLabel = s("较上年 ", "vs last year ");
            d.trendTitle = s("今年逐月", "This year by month");
        }
        return d;
    }

    private static String monthFirst(int y, int m) {
        return y + (m < 10 ? "-0" : "-") + m + "-01";
    }

    private static String monthLast(int y, int m) {
        int ny = m == 12 ? y + 1 : y, nm = m == 12 ? 1 : m + 1;
        return DayUtil.addDays(monthFirst(ny, nm), -1);
    }

    // ---------------- 渲染 ----------------
    private void render() {
        col.removeAllViews();
        TextView title = new TextView(this);
        title.setText(s("知行指数", "Unity index"));
        title.setTextSize(23);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(Ui.text(this));
        col.addView(title);

        // ---- 范围切换 ----
        LinearLayout segRow = new LinearLayout(this);
        segRow.setOrientation(LinearLayout.HORIZONTAL);
        segRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srlp.topMargin = dp(12);
        segRow.setLayoutParams(srlp);
        segRow.addView(seg(s("一周", "Week"), range == RANGE_WEEK, v -> { range = RANGE_WEEK; render(); }));
        segRow.addView(seg(s("一月", "Month"), range == RANGE_MONTH, v -> { range = RANGE_MONTH; render(); }));
        segRow.addView(seg(s("一年", "Year"), range == RANGE_YEAR, v -> { range = RANGE_YEAR; render(); }));
        col.addView(segRow);

        RangeDef def = rangeDef();
        ZhixingCalc.PeriodStats cur = ZhixingCalc.computeWindow(this, root, habits, tasks, def.curFrom, def.curTo);
        ZhixingCalc.PeriodStats prev = ZhixingCalc.computeWindow(this, root, habits, tasks, def.prevFrom, def.prevTo);

        TextView period = small(def.curFrom.equals(def.curTo)
                ? DayUtil.shortDate(def.curFrom)
                : DayUtil.shortDate(def.curFrom) + " — " + DayUtil.shortDate(def.curTo), 12);
        period.setPadding(0, dp(14), 0, 0);
        col.addView(period);

        if (!cur.hasData) {
            // 本期没有任何原始记录：如实呈现，不算指数
            TextView none = new TextView(this);
            none.setText(s("数据不足", "Not enough data"));
            none.setTextSize(30);
            none.setTypeface(Typeface.SERIF, Typeface.BOLD);
            none.setTextColor(Ui.text(this));
            none.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(36);
            col.addView(none, nlp);
            TextView why = small(s("这个时段还没有可回算的记录", "No records to recompute from in this period yet"), 12);
            why.setGravity(Gravity.CENTER);
            why.setPadding(0, dp(8), 0, 0);
            col.addView(why);
        } else {
            // ---- 大数字（与知行页同款圆环） ----
            FrameLayout ringBox = new FrameLayout(this);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(150), dp(150));
            rlp.gravity = Gravity.CENTER_HORIZONTAL;
            rlp.topMargin = dp(14);
            ringBox.addView(new ZhixingPage.RingView(this, cur.index),
                    new FrameLayout.LayoutParams(dp(150), dp(150)));
            LinearLayout center = new LinearLayout(this);
            center.setOrientation(LinearLayout.VERTICAL);
            center.setGravity(Gravity.CENTER);
            TextView num = new TextView(this);
            num.setText(String.valueOf(cur.index));
            num.setTextSize(40);
            num.setTypeface(Typeface.SERIF, Typeface.BOLD);
            num.setTextColor(Ui.text(this));
            num.setGravity(Gravity.CENTER);
            center.addView(num);
            TextView cap = small(s("知行合一指数", "Unity index"), 11);
            cap.setGravity(Gravity.CENTER);
            center.addView(cap);
            ringBox.addView(center, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            col.addView(ringBox, rlp);

            // ---- 较上期 ----
            TextView delta = new TextView(this);
            delta.setTextSize(13);
            delta.setTypeface(delta.getTypeface(), Typeface.BOLD);
            delta.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = dp(6);
            if (prev.hasData) {
                int dIdx = cur.index - prev.index;
                delta.setText(def.deltaLabel + (dIdx > 0 ? "▲ +" + dIdx : dIdx < 0 ? "▼ " + dIdx : "— 0"));
                delta.setTextColor(dIdx >= 0 ? Ui.green(this) : Ui.danger(this));
            } else {
                delta.setText(s("上期数据不足，暂无对比", "Previous period has no data to compare with"));
                delta.setTextColor(Ui.sub(this));
            }
            col.addView(delta, dlp);

            // ---- 构成表 ----
            col.addView(sectionTitle(s("构成", "Makeup")));
            col.addView(tableHeader());
            col.addView(tableRow(s("知行指数", "Index"), null,
                    String.valueOf(cur.index),
                    prev.hasData ? String.valueOf(prev.index) : "—",
                    prev.hasData ? cur.index - prev.index : null, true));
            col.addView(tableRow(s("习惯达成", "Habits"), s("权重 50%", "weight 50%"),
                    cur.habitAvg + "%",
                    prev.hasData ? prev.habitAvg + "%" : "—",
                    prev.hasData ? cur.habitAvg - prev.habitAvg : null, false));
            col.addView(tableRow(s("任务完成", "Tasks done"), s("权重 35%", "weight 35%"),
                    cur.taskRate + "%",
                    prev.hasData ? prev.taskRate + "%" : "—",
                    prev.hasData ? cur.taskRate - prev.taskRate : null, false));
            TextView taskSub = small(s("本期完成 ", "Done ") + cur.taskDone + " / " + cur.taskTotal
                    + s(" 项 · 上期 ", " · last period ") + prev.taskDone + " / " + prev.taskTotal + s(" 项", ""), 11);
            col.addView(taskSub);
            col.addView(tableRow(s("分神控制", "Drift control"), s("权重 15%", "weight 15%"),
                    cur.driftScore + "%",
                    prev.hasData ? prev.driftScore + "%" : "—",
                    prev.hasData ? cur.driftScore - prev.driftScore : null, false));
            TextView driftSub = small(s("本期分神 ", "Drifted ") + cur.driftCount
                    + s(" 次 · 上期 ", " times · last period ") + prev.driftCount + s(" 次", " times"), 11);
            col.addView(driftSub);
        }

        // ---- 趋势 ----
        col.addView(sectionTitle(def.trendTitle));
        List<String[]> rows = trendRows(def); // {label, index or "", hasData}
        Integer prevIdx = null;
        for (String[] r : rows) {
            boolean has = "1".equals(r[2]);
            Integer idx = has ? Integer.valueOf(r[1]) : null;
            Integer d = (idx != null && prevIdx != null) ? idx - prevIdx : null;
            col.addView(trendRow(r[0], idx, d));
            if (has) prevIdx = idx;
            else prevIdx = null;
        }

        // ---- 口径说明 ----
        TextView note = small(s("历史指数由原始记录回算：习惯按自然周的达成率聚合，任务按到期与完成记录，分神按自检与守卫记录；没有记录的时段显示数据不足，不用别的值填充",
                "History is recomputed from raw records: habits aggregated from weekly rates, tasks from due/done records, drift from self-checks and guard events. Periods with no records show as insufficient — never filled with other values"), 11);
        note.setPadding(0, dp(22), 0, 0);
        col.addView(note);
    }

    /** 趋势行数据：{标签, 指数（无数据为空）, 有数据=1}，按时间旧→新排列。 */
    private List<String[]> trendRows(RangeDef def) {
        List<String[]> out = new ArrayList<>();
        String today = DayUtil.today();
        if (range == RANGE_WEEK) {
            for (int i = 6; i >= 0; i--) {
                String d = DayUtil.addDays(today, -i);
                boolean has = ZhixingCalc.hasDataInWindow(this, root, tasks, d, d);
                String idx = "";
                if (has) {
                    ZhixingCalc.DayStats ds = ZhixingCalc.computeDay(this, root, habits, tasks, d);
                    idx = String.valueOf(ds.index);
                }
                int dowIdx = DayUtil.daysBetween(DayUtil.mondayOf(d), d);
                String label = DayUtil.shortDate(d) + " " + DayUtil.weekdayName(dowIdx)
                        + (i == 0 ? s(" · 今天", " · today") : "");
                out.add(new String[]{label, idx, has ? "1" : ""});
            }
        } else if (range == RANGE_MONTH) {
            String monday = DayUtil.mondayOf(def.curFrom);
            while (monday.compareTo(def.curTo) <= 0) {
                String sunday = DayUtil.addDays(monday, 6);
                ZhixingCalc.PeriodStats ps = ZhixingCalc.computeWindow(
                        this, root, habits, tasks, monday, sunday);
                out.add(new String[]{
                        DayUtil.shortDate(monday) + " — " + DayUtil.shortDate(sunday),
                        ps.hasData ? String.valueOf(ps.index) : "",
                        ps.hasData ? "1" : ""});
                monday = DayUtil.addDays(monday, 7);
            }
        } else {
            int y = Integer.parseInt(today.substring(0, 4));
            for (int m = 1; m <= 12; m++) {
                String from = monthFirst(y, m);
                if (from.compareTo(today) > 0) {
                    out.add(new String[]{monthLabel(y, m), "", ""});
                    continue;
                }
                ZhixingCalc.PeriodStats ps = ZhixingCalc.computeWindow(
                        this, root, habits, tasks, from, monthLast(y, m));
                out.add(new String[]{monthLabel(y, m),
                        ps.hasData ? String.valueOf(ps.index) : "",
                        ps.hasData ? "1" : ""});
            }
        }
        return out;
    }

    private static String monthLabel(int y, int m) {
        if (Strings.en()) {
            String[] names = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            return names[m - 1] + " " + y;
        }
        return m + " 月";
    }

    // ---------------- 小部件 ----------------
    private TextView seg(String label, boolean selected, View.OnClickListener oc) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12.5f);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        if (selected) {
            t.setTextColor(Ui.onAccent(this));
            t.setBackground(round(Ui.text(this), dp(99)));
        } else {
            t.setTextColor(Ui.sub(this));
            GradientDrawable g = round(Ui.surface(this), dp(99));
            g.setStroke(dp(1), Ui.line(this));
            t.setBackground(g);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        t.setLayoutParams(lp);
        t.setOnClickListener(oc);
        return t;
    }

    private View tableHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(2));
        row.addView(cell("", 1f, true));
        row.addView(cell(s("本期", "Now"), 0.8f, true));
        row.addView(cell(s("上期", "Prev"), 0.8f, true));
        row.addView(cell(s("变化", "Δ"), 0.8f, true));
        return row;
    }

    private View tableRow(String name, String weight, String curVal, String prevVal,
                          Integer delta, boolean bold) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(2));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout nameCell = new LinearLayout(this);
        nameCell.setOrientation(LinearLayout.VERTICAL);
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(14);
        n.setTypeface(n.getTypeface(), Typeface.BOLD);
        n.setTextColor(Ui.text(this));
        nameCell.addView(n);
        if (weight != null) nameCell.addView(small(weight, 10.5f));
        row.addView(nameCell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(cell(curVal, 0.8f, bold));
        row.addView(cell(prevVal, 0.8f, false));
        TextView dv = new TextView(this);
        if (delta == null) {
            dv.setText("—");
            dv.setTextColor(Ui.sub(this));
        } else {
            dv.setText(delta > 0 ? "+" + delta : delta < 0 ? String.valueOf(delta) : s("持平", "0"));
            dv.setTextColor(delta >= 0 ? Ui.green(this) : Ui.danger(this));
            dv.setTypeface(dv.getTypeface(), Typeface.BOLD);
        }
        dv.setTextSize(13);
        dv.setGravity(Gravity.RIGHT);
        row.addView(dv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f));
        box.addView(row);
        return box;
    }

    private TextView cell(String text, float weight, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(Ui.text(this));
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setGravity(Gravity.RIGHT);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        return t;
    }

    private View trendRow(String label, Integer idx, Integer delta) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(Ui.text(this));
        row.addView(l, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (idx == null) {
            TextView nd = small(s("数据不足", "No data"), 12);
            row.addView(nd);
        } else {
            if (delta != null) {
                TextView dv = new TextView(this);
                dv.setText(delta > 0 ? "+" + delta : delta < 0 ? String.valueOf(delta) : s("持平", "0"));
                dv.setTextSize(11.5f);
                dv.setTypeface(dv.getTypeface(), Typeface.BOLD);
                dv.setTextColor(delta >= 0 ? Ui.green(this) : Ui.danger(this));
                dv.setPadding(0, 0, dp(10), 0);
                row.addView(dv);
            }
            TextView v = new TextView(this);
            v.setText(String.valueOf(idx));
            v.setTextSize(15);
            v.setTypeface(Typeface.SERIF, Typeface.BOLD);
            v.setTextColor(Ui.text(this));
            row.addView(v);
        }
        return row;
    }

    private View sectionTitle(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(16.5f);
        v.setTypeface(Typeface.SERIF, Typeface.BOLD);
        v.setTextColor(Ui.text(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        v.setLayoutParams(lp);
        return v;
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
