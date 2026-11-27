package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 恢复分详情（v2.0.0 F6）：公式公开——六项各得多少分、缺哪项、基线建没建好，
 * 都写在这一页；另附本周汇总（F7 家书素材，只存数据不做图）。
 * 数据只读 HealthStore 当日汇总（晨间/今天页打开时已算好），本页不重算口径。
 */
public class RecoveryDetailActivity extends Activity {

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(22), dp(20), dp(30));
        scroll.addView(col);
        setContentView(scroll);

        TextView back = new TextView(this);
        back.setText(s("‹ 今天", "‹ Today"));
        back.setTextSize(15);
        back.setTextColor(Ui.link(this));
        back.setPadding(0, dp(2), 0, dp(10));
        back.setOnClickListener(v -> finish());
        col.addView(back);

        TextView title = new TextView(this);
        title.setText(s("恢复分", "Recovery score"));
        title.setTextSize(24);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(Ui.text(this));
        col.addView(title);

        JSONObject root = HealthStore.load(this);
        String today = Rollover.logicalToday(this);
        JSONObject day = HealthStore.daySummary(root, today);
        boolean en = Strings.en();

        if (day == null || day.optLong("startMs", 0) <= 0 || !day.has("recovery")) {
            TextView none = body(en
                    ? "No score yet — a score appears once last night's sleep is read or filled in"
                    : "还没有分数，昨晚睡眠读到或手填后就会算出来");
            none.setPadding(0, dp(14), 0, 0);
            col.addView(none);
            return;
        }

        TextView score = new TextView(this);
        score.setText(String.valueOf(day.optInt("recovery", 0)));
        score.setTextSize(52);
        score.setTypeface(Typeface.SERIF, Typeface.BOLD);
        score.setTextColor(Ui.text(this));
        score.setPadding(0, dp(10), 0, 0);
        col.addView(score);

        JSONObject parts = day.optJSONObject("recParts");
        if (parts != null) {
            col.addView(partRow(en ? "Sleep duration · of 35" : "睡眠时长 · 满 35", parts.optDouble("dur", 0)));
            col.addView(partRow(en ? "Bedtime · of 25" : "入睡时间点 · 满 25", parts.optDouble("timing", 0)));
            col.addView(partRow(en ? "Deep sleep share · of 15" : "深睡占比 · 满 15", parts.optDouble("deep", 0)));
            col.addView(partRow(en ? "Resting heart rate · of 10" : "静息心率 · 满 10", parts.optDouble("rhr", 0)));
            col.addView(partRow(en ? "HRV · of 10" : "HRV · 满 10", parts.optDouble("hrv", 0)));
            col.addView(partRow(en ? "Yesterday's activity · of 5" : "昨日活动 · 满 5", parts.optDouble("act", 0)));
        }
        JSONArray miss = day.optJSONArray("recMissing");
        if (miss != null && miss.length() > 0) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < miss.length(); i++) {
                if (i > 0) names.append("、");
                names.append(missingName(miss.optString(i)));
            }
            TextView m = body((en ? "Missing today: " : "今日缺 ") + names
                    + (en ? " — its weight was spread over the other parts"
                          : "，这一项的权重按比例分给了其余项"));
            m.setPadding(0, dp(12), 0, 0);
            col.addView(m);
        }
        int base = day.optInt("recBase", 0);
        if (base < 7) {
            TextView b = body(en
                    ? "Baseline still building — fewer than 7 days of history so far"
                    : "基线建立中，已有的日子还不到 7 天");
            b.setPadding(0, dp(4), 0, 0);
            col.addView(b);
        }

        TextView fh = head(en ? "How it is counted" : "公式");
        fh.setPadding(0, dp(22), 0, 0);
        col.addView(fh);
        TextView formula = body(en
                ? "Out of 100: sleep duration 35 (actual over your goal window, capped at full) · "
                + "bedtime 25 (full marks at or before your goal, losing evenly to zero at 2 hours late) · "
                + "deep sleep share 15 (full between 15% and 25% of the night, less outside that band) · "
                + "resting heart rate 10 (full up to 5 bpm above your 7-day baseline, less above that) · "
                + "HRV 10 (full at or above your baseline, less down to 70% of it) · "
                + "yesterday's activity 5 (full when yesterday's steps reach 80% of your 7-day median). "
                + "A missing part hands its weight to the others in proportion. "
                + "The score is only a morning reference — it never feeds the KnowDo index."
                : "满分 100：睡眠时长 35（实际睡的除以目标窗口，封顶满分）· 入睡时间点 25"
                + "（不晚于目标满分，晚 2 小时扣完，中间均匀扣）· 深睡占比 15（占整晚 "
                + "15%–25% 满分，带外递减）· 静息心率 10（不高于近 7 天基线 5 bpm 满分，"
                + "再高递减）· HRV 10（达到基线满分，低到基线的 70% 扣完）· 昨日活动 5"
                + "（昨日步数达到近 7 天中位数的 80% 满分）。缺哪项，那项的权重按比例"
                + "分给其余项。恢复分只作晨间参考，不进知行指数");
        formula.setPadding(0, dp(8), 0, 0);
        col.addView(formula);

        // F7 本周汇总（家书素材，只呈现已存的每日汇总现算结果）
        TextView wh = head(en ? "This week" : "本周");
        wh.setPadding(0, dp(22), 0, 0);
        col.addView(wh);
        int[] wk = HealthStore.weeklySummary(root, DayUtil.mondayOf(today));
        String wkText;
        if (wk[3] == 0) {
            wkText = en ? "No sleep recorded this week yet" : "本周还没有睡眠记录";
        } else {
            wkText = (en ? "Goals met " : "上床达标 ") + wk[0] + (en ? " nights · average bedtime " : " 天 · 平均入睡 ")
                    + LocalAutoScheduler.hhmm(wk[1])
                    + (en ? " · average sleep " : " · 平均时长 ") + durText(wk[2]);
        }
        TextView wv = body(wkText);
        wv.setPadding(0, dp(8), 0, 0);
        col.addView(wv);
    }

    private TextView head(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16.5f);
        t.setTypeface(Typeface.SERIF, Typeface.BOLD);
        t.setTextColor(Ui.text(this));
        return t;
    }

    private TextView body(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13.5f);
        t.setTextColor(Ui.sub(this));
        t.setLineSpacing(0, 1.55f);
        return t;
    }

    private TextView partRow(String label, double pts) {
        TextView t = new TextView(this);
        t.setText(label + " · " + trimNum(pts));
        t.setTextSize(14);
        t.setTextColor(Ui.text(this));
        t.setPadding(0, dp(7), 0, 0);
        return t;
    }

    private static String trimNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v)
                : String.format(java.util.Locale.US, "%.1f", v);
    }

    private static String missingName(String key) {
        switch (key) {
            case "deep": return s("深睡", "deep sleep");
            case "rhr": return s("静息心率", "resting heart rate");
            case "hrv": return "HRV";
            case "activity": return s("昨日活动", "yesterday's activity");
            default: return key;
        }
    }

    private static String durText(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return Strings.en() ? h + "h " + m + "m" : h + " 小时 " + m + " 分";
    }
}
