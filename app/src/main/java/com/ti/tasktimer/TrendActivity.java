package com.ti.tasktimer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 走势页（v1.76.0，F3）：合一页点圆圈里的分数进来。照批准草案
 * （mockup-unity-trend-v1）：两排普通小按钮（指标：知行指数／原则践行／
 * 动机至善；范围：月／年），原生 Canvas 自绘曲线——朱砂线＋极淡填充、
 * 三条横虚线参考线（0/50/100）、选中点白心朱砂圈＋竖虚线；手指横滑选点，
 * 下方说明卡与统计行（均值/最高/最低）跟着变。
 *
 * 数据只读 ScoreDaily 的本地快照（每日成绩单）＋今日实时点，渲染路径不扫
 * 原始事件：月＝近 30 个逻辑日的日值（知行指数取日定格，比例指标按日计数
 * 现算比例）；年＝近 52 周，每周一个点（知行指数取周内日值均值，比例指标
 * 周内先汇总计数再算比例）；分母为 0 的日/周断线不画。旧的指数详情页
 * （一周/一月/一年回算）从本页底部入口仍可达。
 */
public class TrendActivity extends Activity {
    private static final int METRIC_ZHIXING = 0, METRIC_PRINCIPLE = 1, METRIC_MOTIVE = 2;
    private static final int RANGE_MONTH = 0, RANGE_YEAR = 1;

    private int metric = METRIC_ZHIXING;
    private int range = RANGE_MONTH;

    private LinearLayout metricRow, rangeRow;
    private TextView rangeNote;
    private ChartView chart;
    private TextView calloutTitle, calloutDetail;
    private TextView statAvgV, statMaxV, statMinV, statAvgK, statMaxK, statMinK;
    private TextView emptyHint;

    private List<Pt> points = new ArrayList<>();
    private int selected = -1;

    private static final class Pt {
        Float v;            // null = 断线
        String title;       // 说明卡首行（日期/第 N 周）
        String valueText;   // 说明卡首行的值（74 分 / 73%）
        String detail;      // 说明卡次行（构成/计数）
        String axis;        // 轴下标签（只在少数下标用）
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(24));
        scroll.addView(col);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(s("走势", "Trend"));
        title.setTextSize(23);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(Ui.text(this));
        col.addView(title);

        metricRow = chipRow();
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mLp.topMargin = dp(16);
        col.addView(metricRow, mLp);

        rangeRow = chipRow();
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rLp.topMargin = dp(8);
        col.addView(rangeRow, rLp);
        rangeNote = new TextView(this);
        rangeNote.setTextSize(11.5f);
        rangeNote.setTextColor(Ui.sub(this));
        rangeRow.addView(rangeNote);

        chart = new ChartView(this);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(190));
        cLp.topMargin = dp(10);
        col.addView(chart, cLp);
        chart.listener = idx -> {
            if (idx != selected) {
                selected = idx;
                paintCallout();
            }
        };

        // 说明卡
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(11), dp(13), dp(11));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Ui.surface(this));
        cardBg.setCornerRadius(dp(12));
        cardBg.setStroke(1, Ui.line(this));
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(12);
        calloutTitle = new TextView(this);
        calloutTitle.setTextSize(13.5f);
        calloutTitle.setTextColor(Ui.text(this));
        card.addView(calloutTitle);
        calloutDetail = new TextView(this);
        calloutDetail.setTextSize(13);
        calloutDetail.setTextColor(Ui.sub(this));
        card.addView(calloutDetail);
        col.addView(card, cardLp);

        // 统计行
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(12);
        statAvgV = statBox(stats, true);
        statMaxV = statBox(stats, false);
        statMinV = statBox(stats, false);
        statAvgK = lastLabelOf(stats, 0);
        statMaxK = lastLabelOf(stats, 1);
        statMinK = lastLabelOf(stats, 2);
        col.addView(stats, sLp);

        emptyHint = new TextView(this);
        emptyHint.setText(s("数据攒起来以后，曲线会长起来", "As days add up, the curve will grow"));
        emptyHint.setTextSize(12);
        emptyHint.setTextColor(Ui.sub(this));
        emptyHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        eLp.topMargin = dp(14);
        col.addView(emptyHint, eLp);

        TextView hint = new TextView(this);
        hint.setText(s("手指在曲线上滑动，逐点看那一天（那一周）的分数和构成",
                "Slide on the curve to see each day (week) and what it is made of"));
        hint.setTextSize(12);
        hint.setTextColor(Ui.sub(this));
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = dp(10);
        col.addView(hint, hLp);

        TextView detail = new TextView(this);
        detail.setText(s("指数详情（一周 / 一月 / 一年） ›", "Index details (week / month / year) ›"));
        detail.setTextSize(12.5f);
        detail.setTextColor(Ui.sub(this));
        detail.setPadding(0, dp(14), 0, 0);
        detail.setOnClickListener(v -> startActivity(new Intent(this, ZhixingDetailActivity.class)));
        col.addView(detail);

        rebuild();
        // 打开先按本地快照画，再定格/拉云端合并后重画（失败静默用本地）
        ScoreDaily.sealAsync(this);
        ScoreDaily.pullAsync(this, this::rebuild);
    }

    // ---------------- 小部件 ----------------
    private LinearLayout chipRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private TextView chip(String text, boolean on, View.OnClickListener cl) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11.5f);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(999));
        bg.setColor(Ui.surface(this));
        bg.setStroke(1, on ? Ui.accent(this) : Ui.line(this));
        t.setBackground(bg);
        t.setTextColor(on ? Ui.accent(this) : Ui.sub(this));
        if (on) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setOnClickListener(cl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView statBox(LinearLayout parent, boolean first) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, dp(9), 0, dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.surface(this));
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, Ui.line(this));
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (!first) lp.leftMargin = dp(8);
        parent.addView(box, lp);
        TextView v = new TextView(this);
        v.setTextSize(17);
        v.setTypeface(Typeface.SERIF);
        v.setTextColor(Ui.text(this));
        v.setGravity(Gravity.CENTER);
        box.addView(v);
        TextView k = new TextView(this);
        k.setTextSize(11);
        k.setTextColor(Ui.sub(this));
        k.setGravity(Gravity.CENTER);
        box.addView(k);
        box.setTag(k);
        return v;
    }

    private TextView lastLabelOf(LinearLayout stats, int idx) {
        LinearLayout box = (LinearLayout) stats.getChildAt(idx);
        return (TextView) box.getTag();
    }

    // ---------------- 数据拼装 ----------------
    private void rebuild() {
        Map<String, ScoreDaily.Row> rows = ScoreDaily.snapshot(this);
        ScoreDaily.Row live = null;
        try { live = ScoreDaily.liveToday(this); } catch (Exception ignored) { }
        points = new ArrayList<>();
        if (range == RANGE_MONTH) buildMonth(rows, live);
        else buildYear(rows, live);
        selected = -1;
        for (int i = points.size() - 1; i >= 0; i--) {
            if (points.get(i).v != null) { selected = i; break; }
        }
        paintChips();
        paintChart();
        paintCallout();
        paintStats();
    }

    private void buildMonth(Map<String, ScoreDaily.Row> rows, ScoreDaily.Row live) {
        String today = live != null ? live.date : Rollover.logicalToday(this);
        for (int i = 0; i < 30; i++) {
            String date = DayUtil.addDays(today, i - 29);
            ScoreDaily.Row r = date.equals(today) ? live : rows.get(date);
            points.add(makeDayPoint(r, date));
        }
        // 轴标签：首/中/尾
        if (points.size() == 30) {
            points.get(0).axis = DayUtil.shortDate(pointsDate(today, -29));
            points.get(15).axis = DayUtil.shortDate(pointsDate(today, -14));
            points.get(29).axis = s("今天", "Today");
        }
    }

    private String pointsDate(String today, int delta) { return DayUtil.addDays(today, delta); }

    private void buildYear(Map<String, ScoreDaily.Row> rows, ScoreDaily.Row live) {
        String today = live != null ? live.date : Rollover.logicalToday(this);
        String monday = DayUtil.mondayOf(today);
        for (int w = 0; w < 52; w++) {
            String wm = DayUtil.addDays(monday, (w - 51) * 7);
            List<ScoreDaily.Row> days = new ArrayList<>();
            for (int k = 0; k < 7; k++) {
                String date = DayUtil.addDays(wm, k);
                if (date.compareTo(today) > 0) break;
                ScoreDaily.Row r = date.equals(today) ? live : rows.get(date);
                if (r != null) days.add(r);
            }
            points.add(makeWeekPoint(days, wm, w));
        }
        if (points.size() == 52) {
            for (int idx : new int[]{0, 17, 34}) {
                String wm = DayUtil.addDays(monday, (idx - 51) * 7);
                points.get(idx).axis = DayUtil.shortDate(wm);
            }
            points.get(51).axis = s("现在", "Now");
        }
    }

    private Pt makeDayPoint(ScoreDaily.Row r, String date) {
        Pt p = new Pt();
        int wd = DayUtil.daysBetween(DayUtil.mondayOf(date), date);
        p.title = DayUtil.shortDate(date) + " · " + DayUtil.weekdayName(wd);
        if (r == null) return p;
        fillPoint(p, r.zhixing, r.habit, r.task, r.drift,
                r.prDone, r.prMissed, r.motiveYes, r.motiveSkip, r.motiveNo);
        return p;
    }

    private Pt makeWeekPoint(List<ScoreDaily.Row> days, String monday, int wIdx) {
        Pt p = new Pt();
        java.util.Calendar cal = DayUtil.parse(monday);
        String mid = "";
        if (cal != null) {
            int dom = cal.get(java.util.Calendar.DAY_OF_MONTH);
            String xun = dom <= 10 ? s("上", "early") : dom <= 20 ? s("中", "mid") : s("下", "late");
            mid = (cal.get(java.util.Calendar.MONTH) + 1) + s(" 月", " ") + xun;
            if (Strings.en()) mid = DayUtil.shortDate(monday);
        }
        p.title = s("第 ", "Week ") + (wIdx + 1) + s(" 周 · ", " · ") + mid;
        if (days.isEmpty()) return p;
        long z = 0, h = 0, t = 0, dr = 0;
        int pd = 0, pm = 0, my = 0, ms = 0, mn = 0;
        for (ScoreDaily.Row r : days) {
            z += r.zhixing; h += r.habit; t += r.task; dr += r.drift;
            pd += r.prDone; pm += r.prMissed;
            my += r.motiveYes; ms += r.motiveSkip; mn += r.motiveNo;
        }
        int n = days.size();
        fillPoint(p, Math.round(z * 1f / n), Math.round(h * 1f / n),
                Math.round(t * 1f / n), Math.round(dr * 1f / n), pd, pm, my, ms, mn);
        return p;
    }

    /** 按当前指标把一行（或一周汇总）翻译成曲线点＋说明卡文案。 */
    private void fillPoint(Pt p, int zhixing, int habit, int task, int drift,
                           int pd, int pm, int my, int ms, int mn) {
        switch (metric) {
            case METRIC_PRINCIPLE: {
                int den = pd + pm;
                if (den == 0) return;
                int pct = Math.round(pd * 100f / den);
                p.v = (float) pct;
                p.valueText = pct + "%";
                p.detail = s("做到了 ", "Did it ") + pd + s(" · 没做到 ", " · Didn't ") + pm;
                break;
            }
            case METRIC_MOTIVE: {
                int den = my + ms + mn;
                if (den == 0) return;
                int pct = Math.round(my * 100f / den);
                p.v = (float) pct;
                p.valueText = pct + "%";
                p.detail = s("有 ", "Yes ") + my + s(" · 没考虑 ", " · Unconsidered ") + ms
                        + s(" · 没有 ", " · No ") + mn;
                break;
            }
            default: {
                p.v = (float) zhixing;
                p.valueText = zhixing + s(" 分", "");
                p.detail = s("习惯 ", "Habits ") + habit + s(" · 任务 ", " · Tasks ") + task
                        + s(" · 分神控制 ", " · Drift control ") + drift;
                break;
            }
        }
    }

    // ---------------- 渲染 ----------------
    private void paintChips() {
        metricRow.removeAllViews();
        metricRow.addView(chip(s("知行指数", "Unity index"), metric == METRIC_ZHIXING,
                v -> { metric = METRIC_ZHIXING; rebuild(); }));
        metricRow.addView(chip(s("原则践行", "Principles"), metric == METRIC_PRINCIPLE,
                v -> { metric = METRIC_PRINCIPLE; rebuild(); }));
        metricRow.addView(chip(s("动机至善", "Motive"), metric == METRIC_MOTIVE,
                v -> { metric = METRIC_MOTIVE; rebuild(); }));
        // 范围行保留最后一个说明位
        rangeRow.removeAllViews();
        rangeRow.addView(chip(s("月", "Month"), range == RANGE_MONTH,
                v -> { range = RANGE_MONTH; rebuild(); }));
        rangeRow.addView(chip(s("年", "Year"), range == RANGE_YEAR,
                v -> { range = RANGE_YEAR; rebuild(); }));
        rangeNote.setText(range == RANGE_YEAR ? s("52 周", "52 weeks") : s("30 天", "30 days"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(4);
        rangeNote.setLayoutParams(lp);
        rangeRow.addView(rangeNote);
    }

    private void paintChart() {
        List<Float> vals = new ArrayList<>();
        java.util.Map<Integer, String> axis = new java.util.HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            vals.add(points.get(i).v);
            if (points.get(i).axis != null) axis.put(i, points.get(i).axis);
        }
        chart.setData(vals, selected, axis);
        int nonNull = 0;
        for (Pt p : points) if (p.v != null) nonNull++;
        emptyHint.setVisibility(nonNull < 2 ? View.VISIBLE : View.GONE);
    }

    private void paintCallout() {
        if (selected < 0 || selected >= points.size() || points.get(selected).v == null) {
            calloutTitle.setText(s("还没有数据", "No data yet"));
            calloutDetail.setText("");
            return;
        }
        Pt p = points.get(selected);
        String head = p.title + " · ";
        SpannableString ss = new SpannableString(head + p.valueText);
        ss.setSpan(new ForegroundColorSpan(Ui.accent(this)),
                head.length(), ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        calloutTitle.setText(ss);
        calloutDetail.setText(p.detail == null ? "" : p.detail);
    }

    private void paintStats() {
        float sum = 0;
        Float max = null, min = null;
        int cnt = 0;
        for (Pt p : points) {
            if (p.v == null) continue;
            cnt++;
            sum += p.v;
            if (max == null || p.v > max) max = p.v;
            if (min == null || p.v < min) min = p.v;
        }
        boolean pct = metric != METRIC_ZHIXING;
        statAvgV.setText(cnt == 0 ? "—" : Math.round(sum / cnt) + (pct ? "%" : ""));
        statMaxV.setText(max == null ? "—" : Math.round(max) + (pct ? "%" : ""));
        statMinV.setText(min == null ? "—" : Math.round(min) + (pct ? "%" : ""));
        statAvgK.setText(range == RANGE_YEAR ? s("年均", "Year avg") : s("月均", "Month avg"));
        statMaxK.setText(range == RANGE_YEAR ? s("最高周", "Best week") : s("最高日", "Best day"));
        statMinK.setText(range == RANGE_YEAR ? s("最低周", "Lowest week") : s("最低日", "Lowest day"));
    }

    // ---------------- 曲线 ----------------
    static final class ChartView extends View {
        interface Listener { void onSelect(int idx); }
        Listener listener;
        private List<Float> vals = new ArrayList<>();
        private java.util.Map<Integer, String> axis = new java.util.HashMap<>();
        private int sel = -1;
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ChartView(Context c) {
            super(c);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            axisPaint.setTextSize(dp(11));
        }

        void setData(List<Float> v, int selected, java.util.Map<Integer, String> ax) {
            vals = v == null ? new ArrayList<>() : v;
            sel = selected;
            axis = ax == null ? new java.util.HashMap<>() : ax;
            invalidate();
        }

        private float plotL() { return dp(4); }
        private float plotR() { return getWidth() - dp(4); }
        private float plotT() { return dp(8); }
        private float plotB() { return getHeight() - dp(26); }
        private float x(int i) {
            int n = vals.size();
            if (n <= 1) return (plotL() + plotR()) / 2f;
            return plotL() + (plotR() - plotL()) * i / (n - 1);
        }
        private float y(float v) {
            return plotT() + (100f - v) / 100f * (plotB() - plotT());
        }
        private int dp(int v) {
            return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
        }
        private float dpf(float v) {
            return v * getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            int accent = Ui.accent(getContext());
            int line = Ui.line(getContext());
            // 三条横虚线参考线（0 / 50 / 100）
            gridPaint.setColor(line);
            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setStrokeWidth(1);
            gridPaint.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(4)}, 0));
            for (float gv : new float[]{0f, 50f, 100f}) {
                Path p = new Path();
                p.moveTo(plotL(), y(gv));
                p.lineTo(plotR(), y(gv));
                cv.drawPath(p, gridPaint);
            }
            gridPaint.setPathEffect(null);
            if (vals.isEmpty()) return;
            // 填充＋折线（按连续段）
            fillPaint.setColor(accent);
            fillPaint.setAlpha(18);
            fillPaint.setStyle(Paint.Style.FILL);
            linePaint.setColor(accent);
            linePaint.setStrokeWidth(dp(2));
            int n = vals.size();
            int i = 0;
            while (i < n) {
                if (vals.get(i) == null) { i++; continue; }
                int j = i;
                while (j + 1 < n && vals.get(j + 1) != null) j++;
                if (j > i) {
                    Path fill = new Path();
                    fill.moveTo(x(i), y(vals.get(i)));
                    for (int k = i + 1; k <= j; k++) fill.lineTo(x(k), y(vals.get(k)));
                    fill.lineTo(x(j), plotB());
                    fill.lineTo(x(i), plotB());
                    fill.close();
                    cv.drawPath(fill, fillPaint);
                    Path lp = new Path();
                    lp.moveTo(x(i), y(vals.get(i)));
                    for (int k = i + 1; k <= j; k++) lp.lineTo(x(k), y(vals.get(k)));
                    cv.drawPath(lp, linePaint);
                } else {
                    dotPaint.setColor(accent);
                    dotPaint.setStyle(Paint.Style.FILL);
                    cv.drawCircle(x(i), y(vals.get(i)), dpf(2.5f), dotPaint);
                }
                i = j + 1;
            }
            // 选中点：竖虚线＋白心朱砂圈
            if (sel >= 0 && sel < n && vals.get(sel) != null) {
                gridPaint.setColor(accent);
                gridPaint.setAlpha(140);
                gridPaint.setPathEffect(new DashPathEffect(new float[]{dp(2), dp(3)}, 0));
                Path vp = new Path();
                vp.moveTo(x(sel), y(vals.get(sel)));
                vp.lineTo(x(sel), plotB());
                cv.drawPath(vp, gridPaint);
                gridPaint.setPathEffect(null);
                gridPaint.setAlpha(255);
                dotPaint.setColor(Ui.bg(getContext()));
                dotPaint.setStyle(Paint.Style.FILL);
                cv.drawCircle(x(sel), y(vals.get(sel)), dp(4), dotPaint);
                dotPaint.setColor(accent);
                dotPaint.setStyle(Paint.Style.STROKE);
                dotPaint.setStrokeWidth(dp(2));
                cv.drawCircle(x(sel), y(vals.get(sel)), dp(4), dotPaint);
            }
            // 轴下标签（按点下标定位：首左对齐、尾右对齐、中间居中，钳在图宽内）
            axisPaint.setColor(Ui.sub(getContext()));
            float baseline = getHeight() - dp(7);
            int last = vals.size() - 1;
            for (java.util.Map.Entry<Integer, String> e : axis.entrySet()) {
                int ai = e.getKey();
                String txt = e.getValue();
                float w = axisPaint.measureText(txt);
                float tx;
                if (ai <= 0) tx = plotL();
                else if (ai >= last) tx = plotR() - w;
                else tx = Math.max(plotL(), Math.min(x(ai) - w / 2f, plotR() - w));
                cv.drawText(txt, tx, baseline, axisPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN
                    || e.getAction() == MotionEvent.ACTION_MOVE) {
                float tx = e.getX();
                int best = -1;
                float bd = Float.MAX_VALUE;
                for (int i = 0; i < vals.size(); i++) {
                    if (vals.get(i) == null) continue;
                    float d = Math.abs(x(i) - tx);
                    if (d < bd) { bd = d; best = i; }
                }
                if (best >= 0 && listener != null) {
                    sel = best;
                    invalidate();
                    listener.onSelect(best);
                }
                return true;
            }
            return super.onTouchEvent(e);
        }
    }
}
