package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

/**
 * 「我的纪律」页（v1.24.3，设置 → 屏幕守卫区进入）：查看守卫问询页点「分析错误」
 * 时留下的纪律，逐条删除。删除走墓碑（GuardRules.delete），同步后云端也不复活。
 * 页首一句说明把生效范围讲白：纪律只在联网判定（云端大模型终判）时随判定
 * 请求带出；本地降级判定用不上纪律——但「分析错误」的撤销与不计分神照常有效。
 */
public class GuardRulesActivity extends Activity {

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 243, 236));
        int pad = dp(24);
        root.setPadding(pad, dp(40), pad, pad);
        // 整页可滚动（v1.25.0 修复）：纪律上限 100 条，此前裸 LinearLayout
        // 无 ScrollView，条数一多（小屏/大字体下约 9 条起）靠后的纪律行被挤出
        // 屏幕、滑不到也删不掉。与问询页同类问题，一并套 ScrollView。
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(247, 243, 236));
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        build();
    }

    private void build() {
        root.removeAllViews();

        TextView title = new TextView(this);
        title.setText(Strings.en() ? "My guard rules" : "我的纪律");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(31, 47, 42));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText(Strings.en()
                ? "These rules only take effect during online judging (when the cloud model "
                        + "makes the final call). In on-device judging they don't apply yet — but "
                        + "every \"Analysis error\" you tapped still stays out of your drift count"
                : "这些纪律只在联网判定（云端大模型终判）时生效；本地判定时暂时不起作用，"
                        + "但你点过的「分析错误」照样不算分神");
        note.setTextSize(15);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(10), 0, dp(20));
        root.addView(note);

        List<JSONObject> rules = GuardRules.visible(this);
        if (rules.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(Strings.en()
                    ? "No rules yet. Next time the guard gets it wrong, tap \"Analysis error\" "
                            + "on the inquiry page to leave one"
                    : "还没有纪律。下次守卫判错了，在问询页点「分析错误」就能留一条");
            empty.setTextSize(17);
            empty.setTextColor(Color.GRAY);
            root.addView(empty);
            return;
        }
        for (JSONObject r : rules) {
            final String id = r.optString("id", "");
            final String text = r.optString("text", "");
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, dp(8));
            root.addView(row);

            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(19);
            tv.setTextColor(Color.rgb(31, 47, 42));
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tv);

            Button del = new Button(this);
            del.setText(Strings.en() ? "Delete" : "删除");
            del.setTextSize(15);
            del.setAllCaps(false);
            del.setTextColor(Color.rgb(160, 60, 50));
            GradientDrawable g = new GradientDrawable();
            g.setColor(Color.rgb(247, 243, 236));
            g.setStroke(dp(1), Color.rgb(210, 170, 165));
            g.setCornerRadius(dp(8));
            del.setBackground(g);
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage(Strings.en()
                            ? "Delete this rule? Online judging will no longer follow it"
                            : "删掉这条纪律？联网判定就不会再带上它了")
                    .setPositiveButton(Strings.en() ? "Delete" : "删除", (d, w) -> {
                        GuardRules.delete(this, id);
                        build();
                    })
                    .setNegativeButton(Strings.en() ? "Cancel" : "取消", null)
                    .show());
            row.addView(del);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
