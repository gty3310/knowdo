package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 习惯页（v1.16.0，底部三 Tab 之一）：按设计稿 mockup-morning-v2 视图 2 实现。
 * 习惯卡（类型标签＋目标文案＋本周进度＋年格＋三项统计）、建议卡（第一个习惯后
 * 出现一次的晨间首填建议）、两步添加表单（默认习惯库 / 自定义 → 四类型参数）。
 * 点卡片头部＝编辑习惯（改名/目标/提醒/删除）；点年格＝给那天补记/改记。
 * 数据全部经 HabitData（本地文件＋云端 habit_data 同步）。
 */
public final class HabitPage {
    private final Activity act;
    private final ViewGroup container;

    private JSONObject root;
    private String dispScope = HabitDisplay.SCOPE_WEEK; // 习惯显示口径（总设置，refresh 时现读）
    // ---- 卡片模式（v1.28.0 feature 3：展开/精简，本地持久化，只管本页渲染） ----
    private static final String PREF_MODE = "habit_page_mode";
    private static final String KEY_COMPACT = "compact";
    private boolean compactMode;
    // ---- 添加表单状态（整页重画时保留） ----
    private boolean formOpen;
    private int formStep = 1;
    private String fType = HabitData.TYPE_MIN;
    private String fReminder;
    private String fSumDate = DayUtil.today();
    private long fStepperValue = 5;
    private int fStepMin = 0, fStepMax = 7;
    private String fStepUnit = "";
    // 表单输入引用（重画前把内容收进缓存，避免丢失）
    private EditText fNameInput, fNumTargetInput, fNumUnitInput, fSumTotalInput, fSumUnitInput, fSumWeekInput, fSumStartInput, fRemindTextInput;
    private String cName = "", cNumTarget = "6000", cNumUnit = "", cSumTotal = "", cSumUnit = "", cSumWeek = "", cSumStart = "", cRemindText = "";
    private TextView fStepperVal, fReminderVal, fSumDateVal;
    private LinearLayout fParamBox;
    private final List<TextView> fTypeCards = new ArrayList<>();

    private static final Object[][] PRESETS = {
            {"早起", HabitData.TYPE_MIN, 4L, "", "05:50"},
            {"站桩", HabitData.TYPE_MIN, 5L, "", "06:30"},
            {"静坐", HabitData.TYPE_MIN, 5L, "", null},
            {"冥想", HabitData.TYPE_MIN, 4L, "", null},
            {"读经", HabitData.TYPE_MIN, 5L, "", null},
            {"写日记", HabitData.TYPE_MIN, 7L, "", null},
            {"运动", HabitData.TYPE_MIN, 3L, "", null},
            {"戒晚睡", HabitData.TYPE_MAX, 2L, "", "22:00"},
            {"少刷手机", HabitData.TYPE_MAX, 3L, "", null},
            {"步数", HabitData.TYPE_NUM, 6000L, "步", null},
            {"善事", HabitData.TYPE_SUM, 0L, "件", "21:30"},
    };

    public HabitPage(Activity act, ViewGroup container) {
        this.act = act;
        this.container = container;
    }

    private int dp(int v) { return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }
    private void toast(String t) { Toast.makeText(act, t, Toast.LENGTH_SHORT).show(); }
    static String fmtNum(long v) { return String.format(Locale.US, "%,d", v); }

    // ---------------- 主刷新 ----------------
    public void refresh() {
        captureForm();
        root = HabitData.load(act);
        dispScope = HabitDisplay.scope(act);
        compactMode = loadCompactMode();
        container.removeAllViews();
        ScrollView scroll = new ScrollView(act);
        scroll.setBackgroundColor(Ui.bg(act));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(24));
        scroll.addView(col);
        container.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(act);
        title.setText(s("习惯记录", "Habits"));
        title.setTextSize(23);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(Ui.text(act));
        col.addView(title);
        TextView sub = small(s("每天早上在晨间页填昨天；这里看长年累月的账",
                "Yesterday gets filled in on the morning page; this is the long-term ledger"), 12);
        sub.setPadding(0, dp(4), 0, 0);
        col.addView(sub);
        col.addView(modeToggle());

        // 晨间记录补记/修改入口（v1.17.0）：打开晨间页的编辑版——可返回、不强制填完，
        // 顶部切换记录日（今天/昨天/前天/选日期），补改那一轮的习惯记录与日记
        TextView morningEdit = new TextView(act);
        morningEdit.setText(s("晨间记录补记 / 修改", "Morning records: backfill / edit"));
        morningEdit.setTextSize(14);
        morningEdit.setTypeface(morningEdit.getTypeface(), Typeface.BOLD);
        morningEdit.setTextColor(Ui.accent(act));
        morningEdit.setGravity(Gravity.CENTER);
        morningEdit.setPadding(0, dp(11), 0, dp(11));
        morningEdit.setBackground(roundStroke(Ui.surface(act), Ui.line(act), dp(14)));
        LinearLayout.LayoutParams melp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        melp.topMargin = dp(12);
        morningEdit.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(act, MorningActivity.class);
            i.putExtra(MorningActivity.EXTRA_MODE, MorningActivity.MODE_EDIT);
            act.startActivity(i);
        });
        col.addView(morningEdit, melp);

        List<HabitData.Habit> habits = HabitData.habits(root);
        maybeSuggestion(col, habits);
        if (!habits.isEmpty()) {
            TextView legend = small(s("习惯按目标分五类：至少型 做到≥目标（达标 100%）· 至多型 发生≤目标（超标扣分）· "
                    + "数值型 每天填数按比例 · 累积型 一件件攒总数 · 每日提醒 每天读过就算完成。点习惯名改目标与提醒，点年格补记。",
                    "Five kinds: At-least (hit the target = 100%) · At-most (over = points off) · "
                    + "Number (a value a day, scored by ratio) · Accumulating (a growing total) · "
                    + "Daily reminder (read it = done). "
                    + "Tap a habit's name to edit its target/reminder; tap the year grid to backfill."), 11);
            legend.setPadding(dp(10), dp(8), dp(10), dp(8));
            legend.setBackground(round(Ui.muted(act), dp(10)));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(10);
            col.addView(legend, llp);
        }
        for (HabitData.Habit h : habits) col.addView(compactMode ? compactRow(h) : habitCard(h));
        if (habits.isEmpty()) {
            TextView empty = small(s("还没有习惯。点下面「＋ 添加习惯」，从默认习惯库里选一个开始记。",
                    "No habits yet. Tap \"+ Add a habit\" below and pick one from the library."), 13);
            empty.setPadding(0, dp(18), 0, 0);
            col.addView(empty);
        }

        TextView addBtn = new TextView(act);
        addBtn.setText(s("＋ 添加习惯", "＋ Add a habit"));
        addBtn.setTextSize(15);
        addBtn.setTypeface(addBtn.getTypeface(), Typeface.BOLD);
        addBtn.setTextColor(Ui.accent(act));
        addBtn.setGravity(Gravity.CENTER);
        addBtn.setPadding(0, dp(13), 0, dp(13));
        GradientDrawable dashed = new GradientDrawable();
        dashed.setColor(Ui.surface(act));
        dashed.setCornerRadius(dp(14));
        dashed.setStroke(dp(1), Ui.accent(act));
        addBtn.setBackground(dashed);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(16);
        addBtn.setOnClickListener(v -> { formOpen = !formOpen; if (formOpen) formStep = 1; refresh(); });
        col.addView(addBtn, alp);
        if (formOpen) col.addView(buildForm());
    }

    // ---------------- 卡片模式（展开/精简，feature 3） ----------------
    private boolean loadCompactMode() {
        return act.getSharedPreferences(PREF_MODE, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_COMPACT, false);
    }

    private void saveCompactMode(boolean compact) {
        act.getSharedPreferences(PREF_MODE, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_COMPACT, compact).apply();
    }

    /** 习惯页顶部的模式切换（展开｜精简）：只影响本页卡片渲染，不进设置页、不影响晨间门。 */
    private View modeToggle() {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(12);
        row.setLayoutParams(rlp);
        row.addView(small(s("卡片", "Cards"), 12));
        row.addView(modeSeg(s("展开", "Full"), !compactMode, v -> {
            if (compactMode) { saveCompactMode(false); refresh(); }
        }));
        row.addView(modeSeg(s("精简", "Compact"), compactMode, v -> {
            if (!compactMode) { saveCompactMode(true); refresh(); }
        }));
        return row;
    }

    private TextView modeSeg(String label, boolean selected, View.OnClickListener oc) {
        TextView t = new TextView(act);
        t.setText(label);
        t.setTextSize(12.5f);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        if (selected) {
            t.setTextColor(Ui.onAccent(act));
            t.setBackground(round(Ui.text(act), dp(99)));
        } else {
            t.setTextColor(Ui.sub(act));
            t.setBackground(roundStroke(Ui.surface(act), Ui.line(act), dp(99)));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        t.setLayoutParams(lp);
        t.setOnClickListener(oc);
        return t;
    }

    // ---------------- 精简行（feature 3） ----------------
    /**
     * 精简模式一行：习惯名 + 本周 7 个点（周一至周日）+ 右侧「还差」。
     * 点击分工沿用展开态：点行＝编辑习惯（展开态点头部），点某个点＝补记那天（展开态点年格）。
     */
    private View compactRow(final HabitData.Habit h) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackground(roundStroke(Ui.surface(act), Ui.line(act), dp(12)));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);

        TextView name = new TextView(act);
        name.setText(h.name);
        name.setTextSize(14.5f);
        name.setTypeface(Typeface.SERIF, Typeface.BOLD);
        name.setTextColor(Ui.text(act));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String today = DayUtil.today();
        String monday = DayUtil.mondayOf(today);
        LinearLayout dots = new LinearLayout(act);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        for (final String date : DayUtil.weekDates(monday)) {
            View cell = dotCell(h, date, monday, today);
            cell.setOnClickListener(v -> logDialog(h, date));
            dots.addView(cell);
        }
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = dp(8);
        row.addView(dots, dlp);

        CS st = compactStatus(h, monday);
        TextView right = new TextView(act);
        right.setText(st.text);
        right.setTextSize(13);
        right.setTypeface(right.getTypeface(), Typeface.BOLD);
        if (st.kind == CS_DONE) right.setTextColor(Ui.green(act));
        else if (st.kind == CS_OVER) right.setTextColor(Ui.danger(act));
        else right.setTextColor(Ui.text(act));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(10);
        row.addView(right, tlp);

        row.setOnClickListener(v -> editHabitDialog(h));
        return wrapWithMargin(row, rlp);
    }

    private static final int DOT_GOOD = 0, DOT_BAD = 1, DOT_LOGGED = 2, DOT_EMPTY = 3;

    /** 一个点（带大一圈的点击区）：颜色语义见 dotState，未来天一律空心淡描边。 */
    private View dotCell(HabitData.Habit h, String date, String monday, String today) {
        FrameLayout cell = new FrameLayout(act);
        cell.setLayoutParams(new LinearLayout.LayoutParams(dp(17), dp(22)));
        boolean future = date.compareTo(today) > 0;
        int state = future ? DOT_EMPTY : dotState(h, date, monday);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (state == DOT_GOOD) {
            g.setColor(Ui.green(act));
        } else if (state == DOT_BAD) {
            g.setColor(Ui.danger(act));
        } else if (state == DOT_LOGGED) {
            g.setColor(mix(Ui.sub(act), Ui.surface(act), 0.35f));
        } else {
            g.setColor(Ui.surface(act));
            g.setStroke(dp(1), future ? mix(Ui.line(act), Ui.surface(act), 0.45f) : Ui.line(act));
        }
        View dot = new View(act);
        dot.setBackground(g);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(9), dp(9));
        dlp.gravity = Gravity.CENTER;
        cell.addView(dot, dlp);
        return cell;
    }

    /**
     * 点的状态（今天及以前）：绿实心＝那天达标/有贡献，红实心＝至多型那天发生了，
     * 灰实心＝数值型记了但没到日目标，空心＝没记或没做到。
     */
    private int dotState(HabitData.Habit h, String date, String monday) {
        Long v = HabitData.logValue(root, date, h.id);
        switch (h.type) {
            case HabitData.TYPE_MIN:
            case HabitData.TYPE_REMIND:
                return v != null && v > 0 ? DOT_GOOD : DOT_EMPTY;
            case HabitData.TYPE_MAX:
                if (v == null) return DOT_EMPTY;
                return v > 0 ? DOT_BAD : DOT_GOOD;
            case HabitData.TYPE_NUM: {
                if (v == null) return DOT_EMPTY;
                long t = HabitData.effectiveTarget(root, h, monday);
                return t <= 0 || v >= t ? DOT_GOOD : DOT_LOGGED;
            }
            default: // sum
                return v != null && v > 0 ? DOT_GOOD : DOT_EMPTY;
        }
    }

    private static final int CS_NORMAL = 0, CS_DONE = 1, CS_OVER = 2;

    private static final class CS {
        final String text;
        final int kind;
        CS(String text, int kind) { this.text = text; this.kind = kind; }
    }

    /**
     * 精简行右侧文案：本周还差多少。固定本周口径，不随总设置 HabitDisplay 变——
     * 用户定：精简模式的要害是一眼看出哪个习惯这周还没完成多少。四类口径：
     *   min 差额＝周目标天数−已做到天数（ZhixingCalc.habitWeek 的 target/primary）
     *   max 至多型没有「欠量」：未超显示剩余额度「还可 X 次」，超了显示「超了 X 次」（不写负数还差）
     *   num 按已过天数累计：应达＝日目标×已过天数，实际＝已过天数记录之和（没记按 0，
     *       与 habitWeek 的 rate 口径一致），差额按单位显示
     *   sum 距总量目标：totalGoal − 总累计（sumTotal，含起始数字）；没设总量目标时
     *       显示本周增量（与展开卡同口径）
     */
    private CS compactStatus(HabitData.Habit h, String monday) {
        String today = DayUtil.today();
        ZhixingCalc.HabitWeek w = ZhixingCalc.habitWeek(root, h, monday);
        switch (h.type) {
            case HabitData.TYPE_MIN: {
                if (w.target <= 0) {
                    return new CS(s("本周已做 ", "Done ") + w.primary + s(" 天", " days this week"), CS_NORMAL);
                }
                long remain = w.target - w.primary;
                if (remain <= 0) return new CS(s("已达标", "Goal met"), CS_DONE);
                return new CS(s("还差 ", "") + remain + (Strings.en()
                        ? (remain == 1 ? " day to go" : " days to go") : " 天"), CS_NORMAL);
            }
            case HabitData.TYPE_MAX: {
                if (w.primary > w.target) {
                    return new CS(s("超了 ", "Over by ") + (w.primary - w.target) + s(" 次", ""), CS_OVER);
                }
                if (w.target <= 0) return new CS(s("已达标", "Goal met"), CS_DONE);
                long left = w.target - w.primary;
                if (left <= 0) return new CS(s("已到上限", "At the limit"), CS_NORMAL);
                return new CS(s("还可 ", "") + left + (Strings.en()
                        ? (left == 1 ? " time left" : " times left") : " 次"), CS_NORMAL);
            }
            case HabitData.TYPE_NUM: {
                long target = w.target; // 本周生效的日目标
                if (target <= 0) {
                    long sum = 0;
                    int cnt = 0;
                    for (String d : DayUtil.weekDates(monday)) {
                        if (d.compareTo(today) > 0) break;
                        Long v = HabitData.logValue(root, d, h.id);
                        if (v != null) { sum += v; cnt++; }
                    }
                    long avg = cnt == 0 ? 0 : Math.round(sum * 1.0 / cnt);
                    return new CS(s("本周日均 ", "Weekly avg ") + fmtNum(avg) + " " + h.unit, CS_NORMAL);
                }
                long actual = 0;
                int elapsed = 0;
                for (String d : DayUtil.weekDates(monday)) {
                    if (d.compareTo(today) > 0) break;
                    elapsed++;
                    Long v = HabitData.logValue(root, d, h.id);
                    if (v != null) actual += v;
                }
                long remain = target * elapsed - actual;
                if (remain <= 0) return new CS(s("已达标", "Goal met"), CS_DONE);
                return new CS(s("还差 ", "") + fmtNum(remain) + " " + h.unit, CS_NORMAL);
            }
            case HabitData.TYPE_REMIND: {
                int elapsed = 0;
                for (String d : DayUtil.weekDates(monday)) {
                    if (d.compareTo(today) > 0) break;
                    elapsed++;
                }
                long remain = elapsed - w.primary;
                if (remain <= 0) return new CS(s("已达标", "Goal met"), CS_DONE);
                return new CS(s("还差 ", "") + remain + (Strings.en()
                        ? (remain == 1 ? " day to go" : " days to go") : " 天"), CS_NORMAL);
            }
            default: { // sum
                if (h.totalGoal > 0) {
                    long remain = h.totalGoal - sumTotal(h);
                    if (remain <= 0) return new CS(s("已达标", "Goal met"), CS_DONE);
                    return new CS(s("还差 ", "") + fmtNum(remain) + " " + h.unit, CS_NORMAL);
                }
                return new CS(s("本周 +", "This week +") + fmtNum(w.primary) + " " + h.unit, CS_NORMAL);
            }
        }
    }

    // ---------------- 建议卡 ----------------
    private void maybeSuggestion(LinearLayout col, List<HabitData.Habit> habits) {
        if (habits.isEmpty() || HabitData.morningEnabled(root) || HabitData.suggestDismissed(root)) return;
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(round(Ui.runningBg(act), dp(14)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);
        TextView b = new TextView(act);
        b.setText(s("要不要开启晨间首填？", "Turn on morning check-in?"));
        b.setTypeface(Typeface.SERIF, Typeface.BOLD);
        b.setTextSize(15.5f);
        b.setTextColor(Ui.text(act));
        card.addView(b);
        TextView p = small(s("你已开始记录习惯，要不要开启晨间首填？每天早上先填完再开启一天。",
                "You've started tracking habits — turn on morning check-in and fill yesterday in before the day starts."), 12.5f);
        p.setPadding(0, dp(4), 0, 0);
        card.addView(p);
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, dp(10), 0, 0);
        TextView on = pillButton(s("开启", "Turn on"), true);
        TextView off = pillButton(s("不用", "No thanks"), false);
        on.setOnClickListener(v -> {
            HabitData.setMorning(root, true, HabitData.morningDayStartMinutes(root));
            HabitData.save(act, root);
            toast(s("晨间首填已开启，明早打开知行会先进晨间页", "Morning check-in is on — tomorrow starts with the morning page"));
            refresh();
        });
        off.setOnClickListener(v -> {
            HabitData.setSuggestDismissed(root);
            HabitData.save(act, root);
            refresh();
        });
        btns.addView(on, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        olp.leftMargin = dp(8);
        btns.addView(off, olp);
        card.addView(btns);
        col.addView(card, clp);
    }

    // ---------------- 习惯卡 ----------------
    private View habitCard(final HabitData.Habit h) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundStroke(Ui.surface(act), Ui.line(act), dp(16)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);

        // 头部：名＋类型标签 ｜ 提醒/目标
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(act);
        name.setText(h.name);
        name.setTextSize(17.5f);
        name.setTypeface(Typeface.SERIF, Typeface.BOLD);
        name.setTextColor(Ui.text(act));
        head.addView(name);
        TextView tag = new TextView(act);
        tag.setText(typeName(h.type));
        tag.setTextSize(10.5f);
        tag.setTypeface(tag.getTypeface(), Typeface.BOLD);
        tag.setTextColor(typeColor(h.type));
        tag.setPadding(dp(7), dp(2), dp(7), dp(2));
        tag.setBackground(round(mix(typeColor(h.type), Ui.surface(act), 0.82f), dp(99)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(7);
        head.addView(tag, tlp);
        View spacer = new View(act);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView right = small("", 11);
        if (h.reminder != null) {
            right.setText("⏰ " + h.reminder + s(" 提醒", ""));
            right.setTextColor(Ui.accent(act));
        } else if (HabitData.TYPE_NUM.equals(h.type)) {
            right.setText(s("目标 ", "Target ") + fmtNum(h.target) + " " + h.unit + s("/天", "/day"));
        } else if (HabitData.TYPE_SUM.equals(h.type) && h.totalGoal > 0) {
            right.setText(s("总量目标 ", "Total goal ") + fmtNum(h.totalGoal) + " " + h.unit);
        }
        head.addView(right);
        head.setOnClickListener(v -> editHabitDialog(h));
        card.addView(head);

        // 本周行 / 累积大字
        String monday = DayUtil.mondayOf(DayUtil.today());
        ZhixingCalc.HabitWeek w = ZhixingCalc.habitWeek(root, h, monday);
        if (HabitData.TYPE_REMIND.equals(h.type)) {
            // 每日提醒：全文 + 按显示口径的主行 + 今日「读过了」按钮（晨间门里也能点，这里是补点）
            TextView full = new TextView(act);
            full.setText(h.remindText == null || h.remindText.isEmpty()
                    ? s("（还没写提醒的话，点上面的习惯名去补）", "(No reminder text yet — tap the habit name above to add it)")
                    : h.remindText);
            full.setTextSize(14.5f);
            full.setTextColor(Ui.text(act));
            full.setLineSpacing(0, 1.45f);
            full.setPadding(0, dp(8), 0, 0);
            card.addView(full);
            ZhixingCalc.HabitPeriod rp = ZhixingCalc.habitPeriod(root, h, dispScope);
            String rwin = HabitDisplay.label(dispScope);
            String rleft;
            if (HabitDisplay.SCOPE_TODAY.equals(dispScope)) {
                rleft = rwin + (rp.recorded
                        ? (rp.primary > 0 ? s(" 读过了", " read") : s(" 没读", " not read"))
                        : s(" 还没读", " not read yet"));
            } else {
                rleft = rwin + s(" 已读 ", " · read ") + rp.primary + s(" 天", " days");
            }
            String rright = rp.rate + "%";
            if (HabitDisplay.SCOPE_WEEK.equals(dispScope) || HabitDisplay.SCOPE_LAST_WEEK.equals(dispScope)) {
                int elapsedR = HabitDisplay.SCOPE_WEEK.equals(dispScope)
                        ? DayUtil.daysBetween(monday, DayUtil.today()) + 1 : 7;
                if (rp.primary < elapsedR) rright += s(" · 差 ", " · ") + (elapsedR - rp.primary) + s(" 天", " to go");
            }
            card.addView(weekLine(rleft, rp.rate, rright, Ui.green(act), false));
            final String todayStr = DayUtil.today();
            Long todayV = HabitData.logValue(root, todayStr, h.id);
            final boolean readToday = todayV != null && todayV > 0;
            TextView readBtn = new TextView(act);
            readBtn.setText(readToday ? s("今天已读过了 ✓ · 点一下撤销", "Read today ✓ · tap to undo")
                    : s("读过了", "I've read it"));
            readBtn.setTextSize(14);
            readBtn.setTypeface(readBtn.getTypeface(), Typeface.BOLD);
            readBtn.setGravity(Gravity.CENTER);
            readBtn.setPadding(0, dp(10), 0, dp(10));
            if (readToday) {
                readBtn.setTextColor(Ui.green(act));
                readBtn.setBackground(roundStroke(Ui.surface(act), Ui.line(act), dp(11)));
            } else {
                readBtn.setTextColor(Ui.onAccent(act));
                readBtn.setBackground(round(Ui.accent(act), dp(11)));
            }
            LinearLayout.LayoutParams rblp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rblp.topMargin = dp(10);
            readBtn.setOnClickListener(v -> {
                if (readToday) HabitData.removeLog(root, todayStr, h.id);
                else HabitData.putLog(root, todayStr, h.id, 1);
                HabitData.save(act, root);
                refresh();
            });
            card.addView(readBtn, rblp);
        } else if (HabitData.TYPE_SUM.equals(h.type)) {
            long total = sumTotal(h);
            TextView big = new TextView(act);
            big.setText(fmtNum(total));
            big.setTextSize(30);
            big.setTypeface(Typeface.SERIF, Typeface.BOLD);
            big.setTextColor(Ui.text(act));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.topMargin = dp(6);
            card.addView(big, blp);
            TextView unitLine = small(h.unit + s(" · 累计总数", " · total so far")
                    + (h.startDate == null || h.startDate.isEmpty() ? ""
                        : s("（自 ", " (since ") + DayUtil.shortDate(h.startDate) + ")"), 12);
            card.addView(unitLine);
            if (h.totalGoal > 0) {
                int pct = (int) Math.min(100, total * 100 / h.totalGoal);
                card.addView(weekLine(
                        s("总量目标 ", "Goal ") + fmtNum(h.totalGoal) + " " + h.unit,
                        pct, fmtNum(total) + "/" + fmtNum(h.totalGoal) + " · " + pct + "%", Ui.accent(act), false));
            } else {
                card.addView(weekLine(s("本周新增 ", "This week +") + fmtNum(w.primary) + " " + h.unit,
                        w.rate, w.rate + "%", Ui.accent(act), false));
            }
        } else {
            // 主行按总设置「习惯显示口径」走（v1.28.0，HabitDisplay）：口径=本周时文案与
            // 从前逐字一致（habitPeriod 本周直接复用 habitWeek）；今天/本月为展示区间，
            // 率的定义见 ZhixingCalc.habitPeriod，不参与知行指数
            ZhixingCalc.HabitPeriod p = ZhixingCalc.habitPeriod(root, h, dispScope);
            String win = HabitDisplay.label(dispScope);
            String left;
            boolean over = false;
            int barColor;
            if (HabitData.TYPE_MIN.equals(h.type)) {
                if (HabitDisplay.SCOPE_TODAY.equals(dispScope)) {
                    left = win + (p.recorded
                            ? (p.primary > 0 ? s(" 做了", " done") : s(" 没做", " not done"))
                            : s(" 还没记", " not logged yet"));
                } else if (p.hasTarget) {
                    left = win + " " + p.primary + s(" 天 / 目标 ≥", " days / ≥") + p.target + s(" 天", "");
                } else {
                    left = win + " " + p.primary + s(" 天", " days");
                }
                barColor = Ui.green(act);
            } else if (HabitData.TYPE_MAX.equals(h.type)) {
                if (HabitDisplay.SCOPE_TODAY.equals(dispScope)) {
                    left = win + (p.recorded
                            ? (p.primary > 0 ? s(" 发生了", " happened") : s(" 没发生", " didn't happen"))
                            : s(" 还没记", " not logged yet"));
                } else if (p.hasTarget) {
                    left = win + " " + p.primary + s(" 次 / 目标 ≤", " times / ≤") + p.target + s(" 次", "");
                    over = p.primary > p.target;
                } else {
                    left = win + " " + p.primary + s(" 次", " times");
                }
                barColor = over ? Ui.danger(act) : Ui.green(act);
            } else { // num：本周口径保留原「昨日值 · 本周日均达成」文案，其余口径用区间日均
                if (HabitDisplay.SCOPE_WEEK.equals(dispScope)) {
                    left = s("昨日 ", "Yesterday ") + fmtNum(w.primary) + " " + h.unit
                            + s(" · 本周日均达成 ", " · weekly daily avg ") + w.rate + "%";
                } else if (HabitDisplay.SCOPE_TODAY.equals(dispScope)) {
                    left = win + (p.recorded ? " " + fmtNum(p.primary) + " " + h.unit
                            : s(" 还没记", " not logged yet"));
                } else {
                    left = win + s("日均 ", " avg ") + fmtNum(p.primary) + " " + h.unit
                            + s(" · 日均达成 ", " · daily avg ") + p.rate + "%";
                }
                barColor = Ui.accent(act);
            }
            String right2 = p.rate + "%";
            if (HabitData.TYPE_MIN.equals(h.type) && p.hasTarget && p.primary < p.target) {
                right2 += s(" · 差 ", " · ") + (p.target - p.primary) + s(" 天", " to go");
            }
            if (over) right2 += s(" · 超标 ", " · over by ") + (p.primary - p.target) + s(" 次", "");
            card.addView(weekLine(left, p.rate, right2, barColor, over));
        }

        // 年格
        YearGridView grid = new YearGridView(act);
        grid.bind(root, h, date -> logDialog(h, date));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(10);
        card.addView(grid, glp);

        // 三项统计
        TextView nums = small(statsLine(h), 11.5f);
        nums.setPadding(0, dp(9), 0, 0);
        card.addView(nums);
        return wrapWithMargin(card, clp);
    }

    private View wrapWithMargin(View v, LinearLayout.LayoutParams lp) {
        LinearLayout wrap = new LinearLayout(act);
        wrap.addView(v, lp);
        return wrap;
    }

    private View weekLine(String left, int pct, String right, int barColor, boolean redText) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, 0);
        TextView l = small(left, 12);
        row.addView(l);
        BarView bar = new BarView(act, pct, barColor);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(5), 1f);
        blp.leftMargin = dp(8);
        blp.rightMargin = dp(8);
        row.addView(bar, blp);
        TextView r = small(right, 12);
        if (redText) {
            r.setTextColor(Ui.danger(act));
            r.setTypeface(r.getTypeface(), Typeface.BOLD);
        }
        row.addView(r);
        return row;
    }

    /** 卡片底部三项统计（按类型，口径见类头注释与 ZhixingCalc）。 */
    private String statsLine(HabitData.Habit h) {
        String today = DayUtil.today();
        String jan1 = today.substring(0, 4) + "-01-01";
        boolean en = Strings.en();
        switch (h.type) {
            case HabitData.TYPE_MIN:
            case HabitData.TYPE_REMIND: {
                int year = 0;
                String d = jan1;
                while (d.compareTo(today) <= 0) {
                    Long v = HabitData.logValue(root, d, h.id);
                    if (v != null && v > 0) year++;
                    d = DayUtil.addDays(d, 1);
                }
                return (en ? "This year " : "本年 ") + year + (en ? " days · Streak " : " 天 · 连续 ")
                        + currentStreak(h, false) + (en ? " days · Longest " : " 天 · 最长 ")
                        + longestStreak(h, false) + (en ? " days" : " 天");
            }
            case HabitData.TYPE_MAX: {
                long year = 0, month = 0;
                String d = jan1;
                String m1 = today.substring(0, 7) + "-01";
                while (d.compareTo(today) <= 0) {
                    Long v = HabitData.logValue(root, d, h.id);
                    if (v != null) {
                        year += v;
                        if (d.compareTo(m1) >= 0) month += v;
                    }
                    d = DayUtil.addDays(d, 1);
                }
                return (en ? "This year " : "本年") + (en ? " " : "") + year + (en ? " times · This month " : " 次 · 本月 ")
                        + month + (en ? " · Longest on-target streak " : " 次 · 最长连续达标 ")
                        + longestOkWeeks(h) + (en ? " weeks" : " 周");
            }
            case HabitData.TYPE_NUM: {
                int yearOk = 0, cnt = 0;
                long sum = 0;
                String d = jan1;
                while (d.compareTo(today) <= 0) {
                    Long v = HabitData.logValue(root, d, h.id);
                    if (v != null) {
                        cnt++;
                        sum += v;
                        if (v >= HabitData.effectiveTarget(root, h, DayUtil.mondayOf(d))) yearOk++;
                    }
                    d = DayUtil.addDays(d, 1);
                }
                long avg = cnt == 0 ? 0 : Math.round(sum * 1.0 / cnt);
                return (en ? "On target " : "本年达标 ") + yearOk + (en ? " days · Streak " : " 天 · 连续达标 ")
                        + currentStreak(h, true) + (en ? " days · Daily avg " : " 天 · 日均 ")
                        + fmtNum(avg) + " " + h.unit;
            }
            default: { // sum
                long total = sumTotal(h);
                String base = (en ? "Total " : "累计 ") + fmtNum(total) + " " + h.unit
                        + (en ? " · This week +" : " · 本周 +")
                        + fmtNum(ZhixingCalc.habitWeek(root, h, DayUtil.mondayOf(today)).primary);
                if (h.totalGoal > 0) {
                    base += (en ? " · " : " · 还差 ") + fmtNum(Math.max(0, h.totalGoal - total)) + " " + h.unit + (en ? " to go" : "");
                }
                return base;
            }
        }
    }

    private long sumTotal(HabitData.Habit h) {
        long total = Math.max(0, h.startValue);
        try {
            JSONObject logs = root.optJSONObject("logs");
            if (logs == null) return 0;
            java.util.Iterator<String> it = logs.keys();
            while (it.hasNext()) {
                Long v = HabitData.logValue(root, it.next(), h.id);
                if (v != null) total += v;
            }
        } catch (Exception ignored) { }
        return total;
    }

    /** 当前连续：今天没记从昨天起算，连续达标（min=做了 / num=达标）的天数。 */
    private int currentStreak(HabitData.Habit h, boolean numeric) {
        String d = DayUtil.today();
        if (HabitData.logValue(root, d, h.id) == null) d = DayUtil.addDays(d, -1);
        int n = 0;
        while (n < 400) {
            Long v = HabitData.logValue(root, d, h.id);
            if (v == null) break;
            boolean ok = numeric
                    ? v >= HabitData.effectiveTarget(root, h, DayUtil.mondayOf(d))
                    : v > 0;
            if (!ok) break;
            n++;
            d = DayUtil.addDays(d, -1);
        }
        return n;
    }

    private int longestStreak(HabitData.Habit h, boolean numeric) {
        String today = DayUtil.today();
        String d = today.substring(0, 4) + "-01-01";
        int best = 0, run = 0;
        while (d.compareTo(today) <= 0) {
            Long v = HabitData.logValue(root, d, h.id);
            boolean ok = v != null && (numeric
                    ? v >= HabitData.effectiveTarget(root, h, DayUtil.mondayOf(d))
                    : v > 0);
            run = ok ? run + 1 : 0;
            if (run > best) best = run;
            d = DayUtil.addDays(d, 1);
        }
        return best;
    }

    /** 至多型：最长连续达标周（从习惯创建那周算到本周，当前周按现状暂算）。 */
    private int longestOkWeeks(HabitData.Habit h) {
        String start = h.createdAtMs > 0
                ? DayUtil.fmt(calendarOf(h.createdAtMs)) : DayUtil.today();
        String monday = DayUtil.mondayOf(start);
        String cur = DayUtil.mondayOf(DayUtil.today());
        int best = 0, run = 0;
        while (monday.compareTo(cur) <= 0) {
            ZhixingCalc.HabitWeek w = ZhixingCalc.habitWeek(root, h, monday);
            boolean ok = w.rate >= 100;
            run = ok ? run + 1 : 0;
            if (run > best) best = run;
            monday = DayUtil.addDays(monday, 7);
        }
        return best;
    }

    private static java.util.Calendar calendarOf(long ms) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return cal;
    }

    // ---------------- 补记/改记 ----------------
    private void logDialog(final HabitData.Habit h, final String date) {
        final Long cur = HabitData.logValue(root, date, h.id);
        String title = h.name + " · " + DayUtil.shortDate(date);
        if (HabitData.TYPE_MIN.equals(h.type) || HabitData.TYPE_MAX.equals(h.type)
                || HabitData.TYPE_REMIND.equals(h.type)) {
            boolean isMin = HabitData.TYPE_MIN.equals(h.type);
            boolean isRemind = HabitData.TYPE_REMIND.equals(h.type);
            String[] items = isRemind
                    ? new String[]{s("读过了", "Read it"), s("没读", "Didn't read"), s("清除记录", "Clear record")}
                    : isMin
                    ? new String[]{s("做到了", "Done"), s("没做到", "Not done"), s("清除记录", "Clear record")}
                    : new String[]{s("发生了", "It happened"), s("没有发生", "It didn't"), s("清除记录", "Clear record")};
            new AlertDialog.Builder(act)
                    .setTitle(title + (cur == null ? "" : s("（当前：", " (now: ")
                            + (cur > 0 ? items[0] : items[1]) + ")"))
                    .setItems(items, (d, which) -> {
                        if (which == 2) HabitData.removeLog(root, date, h.id);
                        else HabitData.putLog(root, date, h.id, which == 0 ? 1 : 0);
                        HabitData.save(act, root);
                        refresh();
                    })
                    .setNegativeButton(Strings.btnCancel(), null)
                    .show();
            return;
        }
        final EditText input = new EditText(act);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(HabitData.TYPE_SUM.equals(h.type)
                ? s("这一天记了多少（增量）", "How many that day (added)")
                : s("这一天的数值", "That day's value"));
        if (cur != null) {
            input.setText(String.valueOf(cur));
            input.setSelection(input.getText().length());
        }
        new AlertDialog.Builder(act)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(s("保存", "Save"), (d, w) -> {
                    long v = parseLong(input.getText().toString(), -1);
                    if (v >= 0) {
                        HabitData.putLog(root, date, h.id, v);
                        HabitData.save(act, root);
                        refresh();
                    }
                })
                .setNeutralButton(s("清除记录", "Clear"), (d, w) -> {
                    HabitData.removeLog(root, date, h.id);
                    HabitData.save(act, root);
                    refresh();
                })
                .setNegativeButton(Strings.btnCancel(), null)
                .show();
    }

    // ---------------- 编辑习惯 ----------------
    private void editHabitDialog(final HabitData.Habit h) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText nameIn = new EditText(act);
        nameIn.setText(h.name);
        nameIn.setHint(s("习惯名", "Habit name"));
        box.addView(nameIn);
        final EditText targetIn = new EditText(act);
        targetIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        targetIn.setText(String.valueOf(h.target));
        if (!HabitData.TYPE_REMIND.equals(h.type)) {
            box.addView(labeled(targetIn, targetLabel(h.type)));
        }
        final EditText remindTextIn = new EditText(act);
        if (HabitData.TYPE_REMIND.equals(h.type)) {
            remindTextIn.setText(h.remindText);
            remindTextIn.setHint(s("写一段提醒自己的话", "Write yourself a reminder"));
            remindTextIn.setMinLines(3);
            remindTextIn.setGravity(Gravity.TOP);
            box.addView(labeled(remindTextIn, s("提醒的话", "Reminder text")));
        }
        final EditText unitIn = new EditText(act);
        final EditText totalIn = new EditText(act);
        final EditText startIn = new EditText(act);
        if (HabitData.TYPE_NUM.equals(h.type) || HabitData.TYPE_SUM.equals(h.type)) {
            unitIn.setText(h.unit);
            box.addView(labeled(unitIn, s("单位", "Unit")));
        }
        if (HabitData.TYPE_SUM.equals(h.type)) {
            totalIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            totalIn.setText(h.totalGoal > 0 ? String.valueOf(h.totalGoal) : "");
            totalIn.setHint(s("可空", "Optional"));
            box.addView(labeled(totalIn, s("总量目标", "Total goal")));
            startIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            startIn.setText(h.startValue > 0 ? String.valueOf(h.startValue) : "");
            startIn.setHint(s("可空，之前已攒下的量", "Optional, what you already had"));
            box.addView(labeled(startIn, s("起始数字", "Starting number")));
        }
        final TextView remindVal = new TextView(act);
        remindVal.setTextSize(14);
        remindVal.setTextColor(Ui.text(act));
        remindVal.setText(h.reminder == null ? s("不提醒", "No reminder") : h.reminder);
        final String[] remindHolder = {h.reminder};
        LinearLayout rrow = new LinearLayout(act);
        rrow.setOrientation(LinearLayout.HORIZONTAL);
        rrow.setGravity(Gravity.CENTER_VERTICAL);
        TextView rlab = small(s("每天提醒时间", "Daily reminder"), 13);
        rrow.addView(rlab);
        View sp = new View(act);
        rrow.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        rrow.addView(remindVal);
        TextView pick = pillButton(s("选时间", "Pick"), false);
        pick.setOnClickListener(v -> {
            int hh = remindHolder[0] == null ? 6 : Integer.parseInt(remindHolder[0].split(":")[0]);
            int mm = remindHolder[0] == null ? 30 : Integer.parseInt(remindHolder[0].split(":")[1]);
            new TimePickerDialog(act, (tp, H, M) -> {
                remindHolder[0] = String.format(Locale.US, "%02d:%02d", H, M);
                remindVal.setText(remindHolder[0]);
            }, hh, mm, true).show();
        });
        rrow.addView(pick);
        TextView clear = pillButton(s("清除", "Clear"), false);
        clear.setOnClickListener(v -> {
            remindHolder[0] = null;
            remindVal.setText(s("不提醒", "No reminder"));
        });
        rrow.addView(clear);
        box.addView(rrow);

        new AlertDialog.Builder(act)
                .setTitle(s("编辑习惯", "Edit habit"))
                .setView(box)
                .setPositiveButton(s("保存", "Save"), (d, w) -> {
                    String nm = nameIn.getText().toString().trim();
                    if (!nm.isEmpty()) h.name = nm;
                    h.target = Math.max(0, parseLong(targetIn.getText().toString(), h.target));
                    if (HabitData.TYPE_NUM.equals(h.type) || HabitData.TYPE_SUM.equals(h.type)) {
                        h.unit = unitIn.getText().toString().trim();
                    }
                    if (HabitData.TYPE_SUM.equals(h.type)) {
                        h.totalGoal = Math.max(0, parseLong(totalIn.getText().toString(), 0));
                        h.startValue = Math.max(0, parseLong(startIn.getText().toString(), 0));
                    }
                    if (HabitData.TYPE_REMIND.equals(h.type)) {
                        // 提醒文本必填：清空不覆盖（同名字留空保留原名的口径），避免存出无文本的提醒习惯
                        String rt = remindTextIn.getText().toString().trim();
                        if (!rt.isEmpty()) h.remindText = rt;
                    }
                    h.reminder = remindHolder[0];
                    List<HabitData.Habit> all = HabitData.habits(root);
                    for (int i = 0; i < all.size(); i++) {
                        if (all.get(i).id.equals(h.id)) { all.set(i, h); break; }
                    }
                    HabitData.putHabits(root, all);
                    HabitData.save(act, root);
                    HabitReminder.rescheduleAll(act);
                    refresh();
                })
                .setNeutralButton(s("删除习惯", "Delete"), (d, w) -> confirmDelete(h))
                .setNegativeButton(Strings.btnCancel(), null)
                .show();
    }

    private void confirmDelete(final HabitData.Habit h) {
        new AlertDialog.Builder(act)
                .setTitle(s("删除「" + h.name + "」？", "Delete \"" + h.name + "\"?"))
                .setMessage(s("它的全部记录会一起删掉。", "All of its records will be deleted too."))
                .setPositiveButton(s("删除", "Delete"), (d, w) -> {
                    List<HabitData.Habit> all = HabitData.habits(root);
                    all.removeIf(x -> x.id.equals(h.id));
                    HabitData.putHabits(root, all);
                    try {
                        JSONObject logs = root.optJSONObject("logs");
                        if (logs != null) {
                            java.util.Iterator<String> it = logs.keys();
                            List<String> dates = new ArrayList<>();
                            while (it.hasNext()) dates.add(it.next());
                            for (String date : dates) HabitData.removeLog(root, date, h.id);
                        }
                        JSONObject wt = root.optJSONObject("weekTargets");
                        if (wt != null) {
                            java.util.Iterator<String> it = wt.keys();
                            while (it.hasNext()) {
                                JSONObject wk = wt.optJSONObject(it.next());
                                if (wk != null) wk.remove(h.id);
                            }
                        }
                    } catch (Exception ignored) { }
                    HabitData.save(act, root);
                    HabitReminder.rescheduleAll(act);
                    refresh();
                })
                .setNegativeButton(Strings.btnCancel(), null)
                .show();
    }

    // ---------------- 添加表单 ----------------
    private void captureForm() {
        if (fNameInput != null) cName = fNameInput.getText().toString();
        if (fNumTargetInput != null) cNumTarget = fNumTargetInput.getText().toString();
        if (fNumUnitInput != null) cNumUnit = fNumUnitInput.getText().toString();
        if (fSumTotalInput != null) cSumTotal = fSumTotalInput.getText().toString();
        if (fSumUnitInput != null) cSumUnit = fSumUnitInput.getText().toString();
        if (fSumWeekInput != null) cSumWeek = fSumWeekInput.getText().toString();
        if (fSumStartInput != null) cSumStart = fSumStartInput.getText().toString();
        if (fRemindTextInput != null) cRemindText = fRemindTextInput.getText().toString();
    }

    private View buildForm() {
        LinearLayout form = new LinearLayout(act);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(12), dp(14), dp(14));
        form.setBackground(roundStroke(Ui.surface(act), Ui.line(act), dp(16)));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(12);
        form.setLayoutParams(flp);
        if (formStep == 1) buildFormStep1(form);
        else buildFormStep2(form);
        return form;
    }

    private void buildFormStep1(LinearLayout form) {
        form.addView(small(s("第一步 · 从默认习惯库选，或自己建一个", "Step 1 · Pick from the library, or create your own"), 12));
        for (final Object[] p : PRESETS) {
            final String name = (String) p[0];
            final String type = (String) p[1];
            final long target = (Long) p[2];
            final String unit = (String) p[3];
            final String reminder = (String) p[4];
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(11), dp(10), dp(11), dp(10));
            row.setBackground(roundStroke(Ui.surface(act), Ui.line(act), dp(11)));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(7);
            TextView n = new TextView(act);
            n.setText(name);
            n.setTextSize(14);
            n.setTypeface(n.getTypeface(), Typeface.BOLD);
            n.setTextColor(Ui.text(act));
            row.addView(n);
            View sp = new View(act);
            row.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
            row.addView(small(presetDesc(type, target, unit), 11));
            row.setOnClickListener(v -> {
                cName = name;
                fType = type;
                fReminder = reminder;
                if (HabitData.TYPE_NUM.equals(type)) { cNumTarget = String.valueOf(target); cNumUnit = unit; }
                if (HabitData.TYPE_SUM.equals(type)) { cSumUnit = unit; cSumTotal = ""; cSumWeek = ""; cSumStart = ""; fSumDate = DayUtil.today(); }
                if (HabitData.TYPE_MIN.equals(type) || HabitData.TYPE_MAX.equals(type)) fStepperValue = target;
                formStep = 2;
                refresh();
            });
            form.addView(row, rlp);
        }
        TextView custom = new TextView(act);
        custom.setText(s("✎ 自定义习惯（库里没有的）", "✎ Custom habit (not in the library)"));
        custom.setTextSize(14);
        custom.setTypeface(custom.getTypeface(), Typeface.BOLD);
        custom.setTextColor(Ui.accent(act));
        custom.setGravity(Gravity.CENTER);
        custom.setPadding(0, dp(11), 0, dp(11));
        custom.setBackground(round(Ui.muted(act), dp(11)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        custom.setOnClickListener(v -> {
            cName = "";
            fType = HabitData.TYPE_MIN;
            fReminder = null;
            fStepperValue = 5;
            formStep = 2;
            refresh();
        });
        form.addView(custom, clp);
    }

    private String presetDesc(String type, long target, String unit) {
        switch (type) {
            case HabitData.TYPE_MIN: return s("至少型 · ≥", "At-least · ≥") + target + s(" 天/周", " days/wk");
            case HabitData.TYPE_MAX: return s("至多型 · ≤", "At-most · ≤") + target + s(" 次/周", " times/wk");
            case HabitData.TYPE_NUM: return s("数值型 · ", "Number · ") + fmtNum(target) + " " + unit + s("/天", "/day");
            default: return s("累积型 · 一件件攒总数", "Accumulating · a growing total");
        }
    }

    private void buildFormStep2(LinearLayout form) {
        TextView back = new TextView(act);
        back.setText(s("← 换一个", "← Pick another"));
        back.setTextSize(12.5f);
        back.setTextColor(Ui.accent(act));
        back.setOnClickListener(v -> { captureForm(); formStep = 1; refresh(); });
        form.addView(back);

        form.addView(small(s("习惯名", "Habit name"), 12));
        fNameInput = new EditText(act);
        fNameInput.setText(cName);
        fNameInput.setHint(s("如：太极、夜跑、步数……", "e.g. Tai chi, night run, steps…"));
        fNameInput.setTextSize(14);
        form.addView(fNameInput);

        form.addView(small(s("目标类型", "Goal type"), 12));
        fTypeCards.clear();
        LinearLayout row1 = new LinearLayout(act);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout row2 = new LinearLayout(act);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(typeCard(HabitData.TYPE_MIN, s("至少型", "At-least"),
                s("做到至少 X 天，达标就是 100%，多做也 100%", "At least X days — hitting the target is 100%, more is still 100%")));
        row1.addView(typeCard(HabitData.TYPE_MAX, s("至多型", "At-most"),
                s("这种事至多 X 次，越少越好，超了按比例扣分", "At most X times — fewer is better, over costs points")));
        row2.addView(typeCard(HabitData.TYPE_NUM, s("数值型", "Number"),
                s("每天填一个数（如步数），按比例算，封顶 100%", "A number a day (e.g. steps), scored by ratio, capped at 100%")));
        row2.addView(typeCard(HabitData.TYPE_SUM, s("累积型", "Accumulating"),
                s("一件件攒，看总数，每天记增量，总量慢慢长", "Adds up bit by bit — log the increment daily, watch the total grow")));
        form.addView(row1);
        form.addView(row2);
        LinearLayout row3 = new LinearLayout(act);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(typeCard(HabitData.TYPE_REMIND, s("每日提醒", "Daily reminder"),
                s("写一段提醒自己的话，每天早上读一遍，读过就算完成", "Write yourself a reminder and read it every morning — reading it is the habit")));
        View spacer3 = new View(act);
        spacer3.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row3.addView(spacer3);
        form.addView(row3);

        fParamBox = new LinearLayout(act);
        fParamBox.setOrientation(LinearLayout.VERTICAL);
        form.addView(fParamBox);
        rebuildParamBox();

        form.addView(small(s("每天提醒时间", "Daily reminder"), 12));
        LinearLayout rrow = new LinearLayout(act);
        rrow.setOrientation(LinearLayout.HORIZONTAL);
        rrow.setGravity(Gravity.CENTER_VERTICAL);
        fReminderVal = new TextView(act);
        fReminderVal.setTextSize(14);
        fReminderVal.setTextColor(Ui.text(act));
        fReminderVal.setText(fReminder == null ? s("不提醒", "No reminder") : fReminder);
        rrow.addView(fReminderVal);
        TextView pick = pillButton(s("选时间", "Pick"), false);
        pick.setOnClickListener(v -> {
            int hh = fReminder == null ? 6 : Integer.parseInt(fReminder.split(":")[0]);
            int mm = fReminder == null ? 30 : Integer.parseInt(fReminder.split(":")[1]);
            new TimePickerDialog(act, (tp, H, M) -> {
                fReminder = String.format(Locale.US, "%02d:%02d", H, M);
                fReminderVal.setText(fReminder);
            }, hh, mm, true).show();
        });
        rrow.addView(pick);
        TextView clear = pillButton(s("清除", "Clear"), false);
        clear.setOnClickListener(v -> {
            fReminder = null;
            fReminderVal.setText(s("不提醒", "No reminder"));
        });
        rrow.addView(clear);
        form.addView(rrow);

        TextView save = new TextView(act);
        save.setText(s("保存这个习惯", "Save this habit"));
        save.setTextSize(14);
        save.setTypeface(save.getTypeface(), Typeface.BOLD);
        save.setTextColor(Ui.onAccent(act));
        save.setGravity(Gravity.CENTER);
        save.setPadding(0, dp(11), 0, dp(11));
        save.setBackground(round(Ui.text(act), dp(11)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(14);
        save.setOnClickListener(v -> saveFormHabit());
        form.addView(save, slp);
        updateTypeCards();
    }

    private View typeCard(final String type, String title, String desc) {
        TextView card = new TextView(act);
        card.setText(title + "\n" + desc);
        card.setTextSize(12);
        card.setTextColor(Ui.text(act));
        card.setPadding(dp(9), dp(8), dp(9), dp(8));
        card.setBackground(roundStroke(Ui.surface(act), Ui.line(act), dp(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), dp(4), dp(3), dp(2));
        card.setLayoutParams(lp);
        card.setTag(type);
        card.setOnClickListener(v -> {
            captureForm();
            fType = type;
            // 切类型时给参数一个合理默认（已填过的数值输入缓存保留）
            if (HabitData.TYPE_MIN.equals(type)) { fStepperValue = 5; }
            if (HabitData.TYPE_MAX.equals(type)) { fStepperValue = 2; }
            updateTypeCards();
            rebuildParamBox();
        });
        fTypeCards.add(card);
        return card;
    }

    private void updateTypeCards() {
        for (TextView card : fTypeCards) {
            boolean sel = fType.equals(card.getTag());
            card.setBackground(roundStroke(
                    sel ? Ui.runningBg(act) : Ui.surface(act),
                    sel ? Ui.accent(act) : Ui.line(act), dp(12)));
        }
    }

    private void rebuildParamBox() {
        if (fParamBox == null) return;
        fParamBox.removeAllViews();
        switch (fType) {
            case HabitData.TYPE_MIN:
                fStepMin = 1; fStepMax = 7; fStepUnit = s(" 天 / 周", " days / week");
                if (fStepperValue < 1 || fStepperValue > 7) fStepperValue = 5;
                fParamBox.addView(small(s("目标（每周至少几天）", "Target (at least how many days a week)"), 12));
                fParamBox.addView(buildStepper());
                break;
            case HabitData.TYPE_MAX:
                fStepMin = 0; fStepMax = 20; fStepUnit = s(" 次 / 周", " times / week");
                if (fStepperValue < 0 || fStepperValue > 20) fStepperValue = 2;
                fParamBox.addView(small(s("目标（每周至多几次）", "Target (at most how many times a week)"), 12));
                fParamBox.addView(buildStepper());
                break;
            case HabitData.TYPE_NUM: {
                fParamBox.addView(small(s("目标（每天的数值）", "Target (daily number)"), 12));
                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                fNumTargetInput = new EditText(act);
                fNumTargetInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                fNumTargetInput.setText(cNumTarget);
                fNumTargetInput.setTextSize(14);
                row.addView(fNumTargetInput, new LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));
                fNumUnitInput = new EditText(act);
                fNumUnitInput.setText(cNumUnit);
                fNumUnitInput.setHint(s("单位", "Unit"));
                fNumUnitInput.setTextSize(14);
                LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT);
                ulp.leftMargin = dp(7);
                row.addView(fNumUnitInput, ulp);
                row.addView(small(s("/ 天", "/ day"), 13));
                fParamBox.addView(row);
                break;
            }
            case HabitData.TYPE_REMIND: {
                fParamBox.addView(small(s("提醒的话（必填，每天早上在晨间页读一遍）", "Your reminder (required — read it each morning on the morning page)"), 12));
                fRemindTextInput = new EditText(act);
                fRemindTextInput.setText(cRemindText);
                fRemindTextInput.setHint(s("如：少想多做，先把眼前这一件做完", "e.g. Think less, do more — finish the one thing in front of you first"));
                fRemindTextInput.setTextSize(14);
                fRemindTextInput.setMinLines(3);
                fRemindTextInput.setGravity(Gravity.TOP);
                fParamBox.addView(fRemindTextInput);
                break;
            }
            default: { // sum
                fParamBox.addView(small(s("起始日期", "Start date"), 12));
                fSumDateVal = new TextView(act);
                fSumDateVal.setTextSize(14);
                fSumDateVal.setTextColor(Ui.text(act));
                fSumDateVal.setText(fSumDate + s("（点这里改）", " (tap to change)"));
                fSumDateVal.setOnClickListener(v -> {
                    java.util.Calendar cal = DayUtil.parse(fSumDate);
                    if (cal == null) cal = java.util.Calendar.getInstance();
                    new DatePickerDialog(act, (dpv, y, m, d) -> {
                        fSumDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                        fSumDateVal.setText(fSumDate + s("（点这里改）", " (tap to change)"));
                    }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH),
                            cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
                });
                fParamBox.addView(fSumDateVal);
                fParamBox.addView(small(s("总量目标（可空） · 单位", "Total goal (optional) · unit"), 12));
                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                fSumTotalInput = new EditText(act);
                fSumTotalInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                fSumTotalInput.setText(cSumTotal);
                fSumTotalInput.setHint(s("如 500", "e.g. 500"));
                fSumTotalInput.setTextSize(14);
                row.addView(fSumTotalInput, new LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));
                fSumUnitInput = new EditText(act);
                fSumUnitInput.setText(cSumUnit);
                fSumUnitInput.setHint(s("单位", "Unit"));
                fSumUnitInput.setTextSize(14);
                LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT);
                ulp.leftMargin = dp(7);
                row.addView(fSumUnitInput, ulp);
                fParamBox.addView(row);
                fParamBox.addView(small(s("每周增量目标（可空，知行页按它算达成率）", "Weekly increment goal (optional; the Zhixing page scores against it)"), 12));
                fSumWeekInput = new EditText(act);
                fSumWeekInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                fSumWeekInput.setText(cSumWeek);
                fSumWeekInput.setHint(s("如 28", "e.g. 28"));
                fSumWeekInput.setTextSize(14);
                fParamBox.addView(fSumWeekInput, new LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));
                fParamBox.addView(small(s("起始数字（可空，之前已攒下的量）", "Starting number (optional, what you already had)"), 12));
                fSumStartInput = new EditText(act);
                fSumStartInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                fSumStartInput.setText(cSumStart);
                fSumStartInput.setHint(s("如 120", "e.g. 120"));
                fSumStartInput.setTextSize(14);
                fParamBox.addView(fSumStartInput, new LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));
                break;
            }
        }
    }

    private View buildStepper() {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = stepButton("−");
        minus.setOnClickListener(v -> {
            fStepperValue = Math.max(fStepMin, fStepperValue - 1);
            fStepperVal.setText(fmtNum(fStepperValue) + fStepUnit);
        });
        row.addView(minus);
        fStepperVal = new TextView(act);
        fStepperVal.setTextSize(13.5f);
        fStepperVal.setTextColor(Ui.text(act));
        fStepperVal.setGravity(Gravity.CENTER);
        fStepperVal.setText(fmtNum(fStepperValue) + fStepUnit);
        row.addView(fStepperVal, new LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView plus = stepButton("＋");
        plus.setOnClickListener(v -> {
            fStepperValue = Math.min(fStepMax, fStepperValue + 1);
            fStepperVal.setText(fmtNum(fStepperValue) + fStepUnit);
        });
        row.addView(plus);
        return row;
    }

    private TextView stepButton(String t) {
        TextView b = new TextView(act);
        b.setText(t);
        b.setTextSize(16);
        b.setTextColor(Ui.text(act));
        b.setGravity(Gravity.CENTER);
        b.setBackground(roundStroke(Ui.muted(act), Ui.line(act), dp(10)));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
        return b;
    }

    private void saveFormHabit() {
        captureForm();
        String name = cName.trim();
        if (name.isEmpty()) {
            toast(s("先给习惯起个名字", "Give the habit a name first"));
            return;
        }
        HabitData.Habit h = new HabitData.Habit();
        h.id = UUID.randomUUID().toString();
        h.name = name;
        h.type = fType;
        h.createdAtMs = System.currentTimeMillis();
        h.reminder = fReminder;
        switch (fType) {
            case HabitData.TYPE_MIN:
            case HabitData.TYPE_MAX:
                h.target = fStepperValue;
                break;
            case HabitData.TYPE_NUM:
                h.target = Math.max(0, parseLong(cNumTarget, 0));
                h.unit = cNumUnit.trim();
                break;
            case HabitData.TYPE_REMIND:
                if (cRemindText.trim().isEmpty()) {
                    toast(s("每日提醒要先写下提醒的话", "Write the reminder text first"));
                    return;
                }
                h.remindText = cRemindText.trim();
                break;
            default:
                h.target = Math.max(0, parseLong(cSumWeek, 0));
                h.totalGoal = Math.max(0, parseLong(cSumTotal, 0));
                h.startValue = Math.max(0, parseLong(cSumStart, 0));
                h.unit = cSumUnit.trim();
                h.startDate = fSumDate;
                break;
        }
        List<HabitData.Habit> all = HabitData.habits(root);
        all.add(h);
        HabitData.putHabits(root, all);
        HabitData.save(act, root);
        HabitReminder.rescheduleAll(act);
        formOpen = false;
        formStep = 1;
        toast(s("已保存 ✓ ", "Saved ✓ ") + h.name);
        refresh();
    }

    // ---------------- 小构件 ----------------
    private TextView small(String text, float sizeSp) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(Ui.sub(act));
        return t;
    }

    private TextView pillButton(String text, boolean solid) {
        TextView b = new TextView(act);
        b.setText(text);
        b.setTextSize(13);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        if (solid) {
            b.setTextColor(Ui.onAccent(act));
            b.setBackground(round(Ui.text(act), dp(10)));
        } else {
            b.setTextColor(Ui.text(act));
            b.setBackground(roundStroke(Ui.surface(act), Ui.line(act), dp(10)));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private View labeled(View input, String label) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView l = small(label, 12);
        l.setPadding(0, dp(8), 0, 0);
        box.addView(l);
        box.addView(input);
        return box;
    }

    private String targetLabel(String type) {
        switch (type) {
            case HabitData.TYPE_MIN: return s("目标（天/周）", "Target (days/week)");
            case HabitData.TYPE_MAX: return s("目标（次/周）", "Target (times/week)");
            case HabitData.TYPE_NUM: return s("目标（每天的数值）", "Target (daily number)");
            default: return s("每周增量目标（0=未设）", "Weekly increment goal (0 = none)");
        }
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

    static int mix(int color, int base, float fracOfBase) {
        int r = (int) (Color.red(color) * (1 - fracOfBase) + Color.red(base) * fracOfBase);
        int g = (int) (Color.green(color) * (1 - fracOfBase) + Color.green(base) * fracOfBase);
        int b = (int) (Color.blue(color) * (1 - fracOfBase) + Color.blue(base) * fracOfBase);
        return Color.rgb(r, g, b);
    }

    static String typeName(String type) {
        switch (type) {
            case HabitData.TYPE_MIN: return s("至少型", "At-least");
            case HabitData.TYPE_MAX: return s("至多型", "At-most");
            case HabitData.TYPE_NUM: return s("数值型", "Number");
            case HabitData.TYPE_REMIND: return s("每日提醒", "Reminder");
            default: return s("累积型", "Sum");
        }
    }

    private int typeColor(String type) {
        switch (type) {
            case HabitData.TYPE_MIN: return Ui.green(act);
            case HabitData.TYPE_MAX: return Ui.danger(act);
            case HabitData.TYPE_NUM: return Ui.accent(act);
            case HabitData.TYPE_REMIND: return Ui.dark(act) ? Color.rgb(56, 189, 248) : Color.rgb(2, 132, 199);
            default: return Ui.dark(act) ? Color.rgb(167, 139, 250) : Color.rgb(91, 74, 138);
        }
    }

    static long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    /** 本周进度小横条（轨＋填充，圆角）。 */
    static final class BarView extends View {
        private final int pct;
        private final int color;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        BarView(Activity a, int pct, int color) {
            super(a);
            this.pct = Math.max(0, Math.min(100, pct));
            this.color = color;
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float h = getHeight(), w = getWidth();
            float r = h / 2f;
            paint.setColor(Ui.dark(getContext()) ? Ui.muted(getContext()) : Color.rgb(236, 232, 222));
            canvas.drawRoundRect(0, 0, w, h, r, r, paint);
            if (pct > 0) {
                paint.setColor(color);
                canvas.drawRoundRect(0, 0, Math.max(h, w * pct / 100f), h, r, r, paint);
            }
        }
    }
}
