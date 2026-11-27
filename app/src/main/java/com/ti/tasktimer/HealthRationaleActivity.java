package com.ti.tasktimer;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 健康数据权限说明页（v2.0.0）：Health Connect 要求应用声明权限用途入口——
 * 系统在用户撤销/查看授权时可经 rationale 意图打开本页。这里只讲清三件事：
 * 读哪四类、算什么、什么永不上传。无任何操作控件，看完返回即可。
 */
public class HealthRationaleActivity extends Activity {

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        boolean en = Strings.en();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(28), dp(22), dp(32));
        scroll.addView(col);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(en ? "Why KnowDo reads health data" : "知行为什么读健康数据");
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(Ui.text(this));
        col.addView(title);

        String body = en
                ? "KnowDo reads four kinds of data from Health Connect: sleep (with stages), "
                + "resting heart rate, steps and active calories. They are used only to show "
                + "last night's sleep on the morning page, a daily overview on the Today page, "
                + "and a recovery score whose formula is shown in the app.\n\n"
                + "Raw records and sleep stages never leave this phone. Only a small daily "
                + "summary (bedtime, wake time, duration, deep sleep, whether the goal was met, "
                + "nap length, resting heart rate, steps, recovery score) is synced to your own "
                + "account so other devices and weekly reviews can use it.\n\n"
                + "You can revoke access any time in Health Connect settings; the app keeps "
                + "working without it, and sleep can still be filled in by hand."
                : "知行从「健康数据共享」（Health Connect）只读四类数据：睡眠（含分期）、"
                + "静息心率、步数、活动热量。用途只有三样：晨间页显示昨晚睡眠、今天页做"
                + "每日总览、算一个恢复分（公式在软件里公开可看）。\n\n"
                + "原始记录与睡眠分期永不离开这台手机。上云的只有每日汇总一小行：入睡、"
                + "起床、时长、深睡、是否达标、午睡、静息心率、步数、恢复分——同步到你"
                + "自己的账号，供其他设备与每周回顾使用。\n\n"
                + "随时可以在健康数据共享的设置里撤销授权；撤销后软件其余功能照常，"
                + "睡眠也可以手填。";
        TextView text = new TextView(this);
        text.setText(body);
        text.setTextSize(15);
        text.setTextColor(Ui.text(this));
        text.setLineSpacing(0, 1.6f);
        text.setPadding(0, dp(16), 0, 0);
        col.addView(text);

        TextView back = new TextView(this);
        back.setText(en ? "Back" : "返回");
        back.setTextSize(15);
        back.setTextColor(Ui.link(this));
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, dp(22), 0, dp(10));
        back.setOnClickListener(v -> finish());
        col.addView(back);
    }
}
