package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 知行页（v1.16.0，底部三 Tab 之一）：按设计稿 mockup-morning-v2 视图 3 实现。
 * 大圆环「知行合一指数」＋公式小字＋本周构成三项＋逐习惯本周 vs 上周对照＋
 * 知行落差清单＋下周目标（改动即存、下周自动生效、未改的沿用本周）。
 * 全部计算走 ZhixingCalc（公式定案集中在那里，权重是常量）。
 */
public final class ZhixingPage {
    private final Activity act;
    private final ViewGroup container;
    private JSONObject root;

    public ZhixingPage(Activity act, ViewGroup container) {
        this.act = act;
        this.container = container;
    }

    private int dp(int v) { return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    public void refresh(TaskStore store) {
        root = HabitData.load(act);
        container.removeAllViews();
        ScrollView scroll = new ScrollView(act);
        scroll.setBackgroundColor(Ui.bg(act));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(24));
        scroll.addView(col);
        container.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        List<HabitData.Habit> habits = HabitData.habits(root);
        store.load();
        List<TaskItem> tasks = new ArrayList<>(store.tasks);
        String today = DayUtil.today();
        String monday = DayUtil.mondayOf(today);
        String lastMonday = DayUtil.addDays(monday, -7);
        ZhixingCalc.WeekStats cur = ZhixingCalc.computeWeek(act, root, habits, tasks, monday);
        ZhixingCalc.WeekStats prev = ZhixingCalc.computeWeek(act, root, habits, tasks, lastMonday);

        TextView title = new TextView(act);
        title.setText(s("知行合一", "Knowledge & Action"));
        title.setTextSize(23);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(Ui.text(act));
        col.addView(title);
        TextView sub = small(s("本周 · ", "This week · ") + DayUtil.shortDate(monday) + " — " + DayUtil.shortDate(DayUtil.addDays(monday, 6)), 12);
        sub.setPadding(0, dp(4), 0, 0);
        col.addView(sub);

        // ---- 大圆环 ----
        FrameLayout ringBox = new FrameLayout(act);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(168), dp(168));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        rlp.topMargin = dp(12);
        RingView ring = new RingView(act, cur.index);
        ringBox.addView(ring, new FrameLayout.LayoutParams(dp(168), dp(168)));
        LinearLayout center = new LinearLayout(act);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        TextView num = new TextView(act);
        num.setText(String.valueOf(cur.index));
        num.setTextSize(44);
        num.setTypeface(Typeface.SERIF, Typeface.BOLD);
        num.setTextColor(Ui.text(act));
        num.setGravity(Gravity.CENTER);
        center.addView(num);
        TextView cap = small(s("知行合一指数", "Unity index"), 11);
        cap.setGravity(Gravity.CENTER);
        center.addView(cap);
        ringBox.addView(center, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // 点圆环进指数详情页（一周/一月/一年回算，v1.28.0 feature 5）
        ringBox.setOnClickListener(v -> act.startActivity(
                new android.content.Intent(act, ZhixingDetailActivity.class)));
        col.addView(ringBox, rlp);

        TextView delta = new TextView(act);
        int dIdx = cur.index - prev.index;
        delta.setText(s("较上周 ", "vs last week ") + (dIdx > 0 ? "▲ +" + dIdx : dIdx < 0 ? "▼ " + dIdx : "— 0"));
        delta.setTextSize(13);
        delta.setTypeface(delta.getTypeface(), Typeface.BOLD);
        delta.setTextColor(dIdx >= 0 ? Ui.green(act) : Ui.danger(act));
        delta.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(6);
        col.addView(delta, dlp);
        TextView hint = small(s("点圆环看详情 · 一周 / 一月 / 一年", "Tap the ring for details · week / month / year"), 11);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint);
        TextView define = small(s("知 ＝ 立下的志与承诺 · 行 ＝ 实际做到的事", "Knowing = what you aspired to and promised · Doing = what you actually did"), 11.5f);
        define.setGravity(Gravity.CENTER);
        col.addView(define);
        TextView formula = small(s("指数 ＝ 习惯达成 ×50% ＋ 任务完成 ×35% ＋ 分神控制 ×15%\n"
                + "每种习惯按自己的目标类型算达成率，封顶 100%，至多型超标扣分；累积型按本周增量达成算",
                "Index = Habits ×50% + Tasks ×35% + Drift control ×15%\n"
                + "Each habit is scored by its own goal type, capped at 100%; at-most habits lose points when over; "
                + "accumulating habits score on this week's increment"), 11);
        formula.setGravity(Gravity.CENTER);
        formula.setPadding(dp(8), dp(6), dp(8), dp(6));
        formula.setBackground(round(HabitPage.mix(Ui.accent(act), Ui.surface(act), 0.86f), dp(8)));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.CENTER_HORIZONTAL;
        flp.topMargin = dp(8);
        col.addView(formula, flp);
        if (habits.isEmpty()) {
            TextView cold = small(s("还没有习惯：习惯达成暂时按满分计，先去「习惯」页加一个吧。",
                    "No habits yet: the habit part counts as full marks for now — add one on the Habits page."), 11);
            cold.setGravity(Gravity.CENTER);
            col.addView(cold);
        }

        // ---- 本周构成 ----
        col.addView(sectionTitle(s("本周构成", "This week's makeup")));
        col.addView(compRow(s("习惯达成", "Habits"), "50%", cur.habitAvg, cur.habitAvg - prev.habitAvg, true,
                habits.isEmpty() ? s("还没有习惯", "No habits yet")
                        : s("全部习惯达成率平均 · 上周 ", "Average of all habits · last week ") + prev.habitAvg + "%",
                Ui.green(act)));
        col.addView(compRow(s("任务完成", "Tasks done"), "35%", cur.taskRate, cur.taskRate - prev.taskRate, true,
                s("本周完成 ", "Done ") + cur.taskDone + " / " + cur.taskTotal + s(" 项 · 上周 ", " · last week ") + prev.taskRate + "%",
                Ui.accent(act)));
        col.addView(compRow(s("分神控制", "Drift control"), "15%", cur.driftScore,
                prev.driftCount - cur.driftCount, false,
                s("本周分神 ", "Drifted ") + cur.driftCount + s(" 次 · 控制分 ", " times · score ") + cur.driftScore
                        + s(" · 上周 ", " · last week ") + prev.driftCount + s(" 次", " times"),
                Ui.danger(act)));

        // ---- 逐习惯对照 ----
        if (!habits.isEmpty()) {
            col.addView(sectionTitle(s("本周 vs 上周", "This week vs last")));
            for (int i = 0; i < habits.size(); i++) {
                HabitData.Habit h = habits.get(i);
                int cr = cur.habitRates.get(i);
                int pr = prev.habitRates.get(i);
                col.addView(cmpRow(h, cr, pr, monday, lastMonday));
            }
        }

        // ---- 知行落差 ----
        List<String[]> gaps = gapItems(habits, tasks, cur, monday);
        col.addView(sectionTitle(s("知行落差 · 没对上的事", "Gaps · where knowing and doing parted")));
        if (gaps.isEmpty()) {
            TextView none = small(s("本周暂时没有落差，知行合一。", "No gaps this week — knowing and doing are one."), 13);
            col.addView(none);
        } else {
            for (String[] g : gaps) {
                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(7), 0, dp(7));
                TextView dot = new TextView(act);
                dot.setText("●");
                dot.setTextSize(8);
                dot.setTextColor(Ui.danger(act));
                dot.setPadding(0, dp(3), dp(8), 0);
                row.addView(dot);
                LinearLayout texts = new LinearLayout(act);
                texts.setOrientation(LinearLayout.VERTICAL);
                TextView main = new TextView(act);
                main.setText(g[0]);
                main.setTextSize(13);
                main.setTextColor(Ui.text(act));
                texts.addView(main);
                if (g[1] != null && !g[1].isEmpty()) texts.addView(small(g[1], 11.5f));
                row.addView(texts);
                col.addView(row);
            }
        }

        // ---- 下周目标 ----
        if (!habits.isEmpty()) {
            col.addView(sectionTitle(s("下周目标", "Next week's goals")));
            final String nextMonday = DayUtil.addDays(monday, 7);
            for (final HabitData.Habit h : habits) {
                col.addView(goalRow(h, nextMonday));
            }
            LinearLayout btns = new LinearLayout(act);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            btns.setPadding(0, dp(12), 0, 0);
            TextView keep = pill(s("沿用本周目标", "Keep this week's"), false);
            TextView save = pill(s("保存下周目标", "Save"), true);
            final TextView note = small(s("改动即存，下周一自动生效；不改就自动沿用",
                    "Changes save as you tap and take effect next Monday; untouched goals carry over"), 11);
            keep.setOnClickListener(v -> {
                HabitData.clearWeekTargets(root, nextMonday);
                HabitData.save(act, root);
                Toast.makeText(act, s("已恢复沿用本周目标", "Next week now carries this week's goals over"), Toast.LENGTH_SHORT).show();
                refresh(store);
            });
            save.setOnClickListener(v -> {
                Toast.makeText(act, s("下周目标已保存 ✓ 周一生效", "Saved ✓ — effective Monday"), Toast.LENGTH_SHORT).show();
                note.setText(s("下周目标已保存 ✓ 周一生效", "Saved ✓ — effective Monday"));
            });
            btns.addView(keep, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            slp.leftMargin = dp(8);
            btns.addView(save, slp);
            col.addView(btns);
            note.setGravity(Gravity.CENTER);
            note.setPadding(0, dp(8), 0, 0);
            col.addView(note);
        }
    }

    // ---------------- 构成行 ----------------
    private View compRow(String name, String weight, int value, int delta, boolean deltaIsPoints, String subText, int color) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(9), 0, dp(9));
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = new TextView(act);
        n.setText(name + " ");
        n.setTextSize(14);
        n.setTypeface(n.getTypeface(), Typeface.BOLD);
        n.setTextColor(Ui.text(act));
        head.addView(n);
        head.addView(small(s("权重 ", "weight ") + weight, 11));
        View sp = new View(act);
        head.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView v = new TextView(act);
        v.setText(value + "%");
        v.setTextSize(17);
        v.setTypeface(Typeface.SERIF, Typeface.BOLD);
        v.setTextColor(Ui.text(act));
        head.addView(v);
        head.addView(chip(delta, deltaIsPoints));
        box.addView(head);
        HabitPage.BarView bar = new HabitPage.BarView(act, value, color);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        blp.topMargin = dp(6);
        box.addView(bar, blp);
        TextView subTv = small(subText, 11.5f);
        subTv.setPadding(0, dp(4), 0, 0);
        box.addView(subTv);
        return box;
    }

    private View chip(int delta, boolean points) {
        TextView c = new TextView(act);
        String txt;
        if (delta > 0) txt = "+" + delta;
        else if (delta < 0) txt = String.valueOf(delta);
        else txt = s("持平", "0");
        if (!points && delta != 0) txt = (delta > 0 ? "−" + delta : "+" + (-delta)) + s(" 次", "");
        c.setText(txt);
        c.setTextSize(11);
        c.setTypeface(c.getTypeface(), Typeface.BOLD);
        boolean good = delta >= 0;
        c.setTextColor(good ? Ui.green(act) : Ui.danger(act));
        c.setBackground(round(HabitPage.mix(good ? Ui.green(act) : Ui.danger(act), Ui.surface(act), 0.84f), dp(99)));
        c.setPadding(dp(8), dp(2), dp(8), dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    // ---------------- 对照行 ----------------
    private View cmpRow(HabitData.Habit h, int curRate, int prevRate, String monday, String lastMonday) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        ZhixingCalc.HabitWeek cw = ZhixingCalc.habitWeek(root, h, monday);
        ZhixingCalc.HabitWeek pw = ZhixingCalc.habitWeek(root, h, lastMonday);
        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(act);
        name.setText(h.name + " · " + HabitPageTypeName.name(h.type));
        name.setTextSize(13.5f);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setTextColor(Ui.text(act));
        texts.addView(name);
        String detail;
        switch (h.type) {
            case HabitData.TYPE_MIN:
                detail = s("本周 ", "This wk ") + cw.primary + s(" 天 · 上周 ", " d · last ") + pw.primary + s(" 天", " d");
                break;
            case HabitData.TYPE_MAX:
                detail = s("本周 ", "This wk ") + cw.primary + s(" 次 · 上周 ", " times · last ") + pw.primary + s(" 次", " times");
                if (cw.primary > cw.target) detail += s(" · 超标 ", " · over by ") + (cw.primary - cw.target);
                break;
            case HabitData.TYPE_NUM:
                detail = s("日均达成 本周 ", "Daily avg this wk ") + curRate + "%" + s(" · 上周 ", " · last ") + prevRate + "%";
                break;
            case HabitData.TYPE_REMIND:
                detail = s("本周已读 ", "Read ") + cw.primary + s(" 天 · 上周 ", " d this wk · last ") + pw.primary + s(" 天", " d");
                break;
            default:
                detail = s("本周新增 +", "This wk +") + cw.primary + " " + h.unit + s(" · 上周 +", " · last +") + pw.primary + " " + h.unit;
                break;
        }
        texts.addView(small(detail, 11.5f));
        row.addView(texts);
        TextView rates = new TextView(act);
        rates.setText(curRate + "% / " + prevRate + "%");
        rates.setTextSize(13);
        rates.setTextColor(Ui.text(act));
        row.addView(rates);
        row.addView(chip(curRate - prevRate, true));
        return row;
    }

    // ---------------- 落差 ----------------
    private List<String[]> gapItems(List<HabitData.Habit> habits, List<TaskItem> tasks,
                                    ZhixingCalc.WeekStats cur, String monday) {
        List<String[]> out = new ArrayList<>();
        String today = DayUtil.today();
        int elapsed = DayUtil.daysBetween(monday, today) + 1; // 本周已过去的天数（含今天）
        int remainInclToday = 7 - elapsed + 1;
        for (int i = 0; i < habits.size(); i++) {
            HabitData.Habit h = habits.get(i);
            ZhixingCalc.HabitWeek w = ZhixingCalc.habitWeek(root, h, monday);
            switch (h.type) {
                case HabitData.TYPE_MIN:
                    if (w.primary < w.target) {
                        if (w.primary + remainInclToday < w.target) {
                            out.add(new String[]{h.name + s("本周已补不回来了", " can't be caught up this week"),
                                    s("目标 ≥", "Goal ≥") + w.target + s(" 天，目前 ", " days, now ") + w.primary + s(" 天", " days")});
                        } else if (w.primary < Math.ceil(w.target * elapsed / 7.0)) {
                            out.add(new String[]{h.name + s("还没跟上本周节奏", " is behind this week's pace"),
                                    s("目标 ≥", "Goal ≥") + w.target + s(" 天，目前 ", " days, now ") + w.primary
                                            + s(" 天，还剩 ", " days, ") + remainInclToday + s(" 天", " days left")});
                        }
                    }
                    break;
                case HabitData.TYPE_MAX:
                    if (w.primary > w.target) {
                        out.add(new String[]{h.name + s("本周已 ", " this week: ") + w.primary + s(" 次，超过目标（≤", " times, over the goal (≤") + w.target + s(" 次）", ")"),
                                s("至多型超标扣分，本周得分 ", "Over the cap — this week's score is ") + w.rate + "%"});
                    }
                    break;
                case HabitData.TYPE_REMIND:
                    if (elapsed > 0 && w.primary < elapsed) {
                        out.add(new String[]{h.name + s("本周还有 ", " this week: ") + (elapsed - w.primary) + s(" 天没读", " days unread"),
                                s("每天都读 · 本周已读 ", "A daily read · ") + w.primary + s(" 天", " days read so far")});
                    }
                    break;
                case HabitData.TYPE_NUM:
                    if (w.rate < 80) {
                        out.add(new String[]{h.name + s("本周日均达成只有 ", " daily average this week is only ") + w.rate + "%",
                                s("目标 ", "Goal ") + HabitPage.fmtNum(w.target) + " " + h.unit + s("/天", "/day")});
                    }
                    break;
                default:
                    if (w.target > 0 && w.primary < w.target * elapsed / 7.0) {
                        out.add(new String[]{h.name + s("本周增量还没跟上节奏", " increment is behind pace"),
                                s("本周 +", "This week +") + w.primary + " " + h.unit + s("，周目标 ", ", weekly goal ") + w.target + " " + h.unit});
                    }
                    break;
            }
        }
        String sunday = DayUtil.addDays(monday, 6);
        for (TaskItem t : tasks) {
            if (t == null || t.deleted) continue;
            if (!"must".equals(t.priority) || t.deadline == null) continue;
            if (t.deadline.compareTo(monday) < 0 || t.deadline.compareTo(sunday) > 0) continue;
            if ("done".equals(t.status)) continue;
            out.add(new String[]{s("必做任务未完成：", "Must-do not done: ") + t.title,
                    s("截止 ", "Due ") + DayUtil.shortDate(t.deadline)});
        }
        String yesterday = DayUtil.addDays(today, -1);
        int yd = ZhixingCalc.driftCount(act, yesterday, yesterday);
        if (yd > 0) {
            out.add(new String[]{s("昨日分神 ", "Yesterday: drifted ") + yd + s(" 次", " times"),
                    s("来自磨练自检与守卫记录", "From self-checks and guard records")});
        }
        if (out.size() > 6) return out.subList(0, 6);
        return out;
    }

    // ---------------- 下周目标行 ----------------
    private View goalRow(final HabitData.Habit h, final String nextMonday) {
        if (HabitData.TYPE_REMIND.equals(h.type)) {
            // 每日提醒没有周目标：只列名与说明，不出步进器（写 weekTargets 对它没有意义）
            LinearLayout rrow = new LinearLayout(act);
            rrow.setOrientation(LinearLayout.HORIZONTAL);
            rrow.setGravity(Gravity.CENTER_VERTICAL);
            rrow.setPadding(0, dp(8), 0, dp(8));
            LinearLayout rtexts = new LinearLayout(act);
            rtexts.setOrientation(LinearLayout.VERTICAL);
            rtexts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView rname = new TextView(act);
            rname.setText(h.name);
            rname.setTextSize(14);
            rname.setTypeface(rname.getTypeface(), Typeface.BOLD);
            rname.setTextColor(Ui.text(act));
            rtexts.addView(rname);
            rtexts.addView(small(s("每日提醒 · 每天读一遍，不设周目标", "Daily reminder · read it every day, no weekly target"), 11));
            rrow.addView(rtexts);
            return rrow;
        }
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        final long curEffective = HabitData.effectiveTarget(root, h, nextMonday);
        final long thisWeekTarget = HabitData.effectiveTarget(root, h, DayUtil.mondayOf(DayUtil.today()));
        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(act);
        name.setText(h.name);
        name.setTextSize(14);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setTextColor(Ui.text(act));
        texts.addView(name);
        texts.addView(small(goalSubText(h, thisWeekTarget), 11));
        row.addView(texts);

        final long step = h.target >= 1000 ? 500 : h.target >= 100 ? 10 : 1;
        final long maxV = HabitData.TYPE_MIN.equals(h.type) ? 7
                : HabitData.TYPE_MAX.equals(h.type) ? 20
                : HabitData.TYPE_SUM.equals(h.type) ? 999 : 999999;
        final TextView val = new TextView(act);
        val.setTextSize(13.5f);
        val.setTextColor(Ui.text(act));
        val.setGravity(Gravity.CENTER);
        val.setText(HabitPage.fmtNum(curEffective) + " " + goalUnit(h));
        LinearLayout stepper = new LinearLayout(act);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = stepBtn("−");
        TextView plus = stepBtn("＋");
        final long[] holder = {curEffective};
        minus.setOnClickListener(v -> {
            holder[0] = Math.max(0, holder[0] - step);
            HabitData.setWeekTarget(root, nextMonday, h.id, holder[0]);
            HabitData.save(act, root);
            val.setText(HabitPage.fmtNum(holder[0]) + " " + goalUnit(h));
        });
        plus.setOnClickListener(v -> {
            holder[0] = Math.min(maxV, holder[0] + step);
            HabitData.setWeekTarget(root, nextMonday, h.id, holder[0]);
            HabitData.save(act, root);
            val.setText(HabitPage.fmtNum(holder[0]) + " " + goalUnit(h));
        });
        stepper.addView(minus);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(dp(86), LinearLayout.LayoutParams.WRAP_CONTENT);
        stepper.addView(val, vlp);
        stepper.addView(plus);
        row.addView(stepper);
        return row;
    }

    private String goalSubText(HabitData.Habit h, long thisWeekTarget) {
        switch (h.type) {
            case HabitData.TYPE_MIN: return s("至少型 · 本周目标 ≥", "At-least · this week ≥") + thisWeekTarget + s(" 天/周", " days/wk");
            case HabitData.TYPE_MAX: return s("至多型 · 本周目标 ≤", "At-most · this week ≤") + thisWeekTarget + s(" 次/周", " times/wk");
            case HabitData.TYPE_NUM: return s("数值型 · 本周目标 ", "Number · this week ") + HabitPage.fmtNum(thisWeekTarget) + " " + h.unit + s("/天", "/day");
            default: return s("累积型 · 本周增量目标 ", "Accumulating · this week's increment goal ") + thisWeekTarget + " " + h.unit
                    + (h.totalGoal > 0 ? s(" · 总量目标 ", " · total goal ") + HabitPage.fmtNum(h.totalGoal) : "");
        }
    }

    private String goalUnit(HabitData.Habit h) {
        switch (h.type) {
            case HabitData.TYPE_MIN: return s("天", "d");
            case HabitData.TYPE_MAX: return s("次", "x");
            default: return h.unit;
        }
    }

    private TextView stepBtn(String t) {
        TextView b = new TextView(act);
        b.setText(t);
        b.setTextSize(15);
        b.setTextColor(Ui.text(act));
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.muted(act));
        g.setCornerRadius(dp(9));
        g.setStroke(dp(1), Ui.line(act));
        b.setBackground(g);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        return b;
    }

    private TextView pill(String text, boolean solid) {
        TextView b = new TextView(act);
        b.setText(text);
        b.setTextSize(13.5f);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, dp(11), 0, dp(11));
        if (solid) {
            b.setTextColor(Ui.onAccent(act));
            b.setBackground(round(Ui.text(act), dp(11)));
        } else {
            b.setTextColor(Ui.text(act));
            GradientDrawable g = round(Ui.surface(act), dp(11));
            g.setStroke(dp(1), Ui.line(act));
            b.setBackground(g);
        }
        return b;
    }

    private View sectionTitle(String t) {
        TextView v = new TextView(act);
        v.setText(t);
        v.setTextSize(16.5f);
        v.setTypeface(Typeface.SERIF, Typeface.BOLD);
        v.setTextColor(Ui.text(act));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView small(String text, float sizeSp) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(Ui.sub(act));
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** 类型名小工具（避免与 HabitPage 的私有方法耦合，直接本地一份）。 */
    static final class HabitPageTypeName {
        static String name(String type) {
            switch (type) {
                case HabitData.TYPE_MIN: return s("至少型", "At-least");
                case HabitData.TYPE_MAX: return s("至多型", "At-most");
                case HabitData.TYPE_NUM: return s("数值型", "Number");
                case HabitData.TYPE_REMIND: return s("每日提醒", "Daily reminder");
                default: return s("累积型", "Accumulating");
            }
        }
    }

    /** 大圆环：底环＋按指数画的琥珀弧（从正上方顺时针）。 */
    static final class RingView extends View {
        private final int pct;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        RingView(Activity a, int pct) {
            super(a);
            this.pct = Math.max(0, Math.min(100, pct));
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float density = getResources().getDisplayMetrics().density;
            float stroke = 11 * density;
            float inset = stroke / 2 + 2;
            android.graphics.RectF oval = new android.graphics.RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(Ui.dark(getContext()) ? Ui.muted(getContext()) : Color.rgb(233, 226, 207));
            canvas.drawArc(oval, 0, 360, false, paint);
            if (pct > 0) {
                paint.setColor(Ui.accent(getContext()));
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                canvas.drawArc(oval, -90, 360f * pct / 100f, false, paint);
            }
        }
    }
}
