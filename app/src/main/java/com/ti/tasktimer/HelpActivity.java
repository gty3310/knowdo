package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 帮助页：知行是什么、每个功能怎么用、设计是怎么定的，
 * 以及“提到过但暂不做 / 决定不做”的清单——功能忘了就来这里翻。
 * v1.8.0：按新风格（主题色）重排，文案集中在 Strings（中/英两套，本页只负责排版）。
 */
public class HelpActivity extends Activity {
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg(this));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(24), pad, dp(32));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(Strings.helpTitle());
        title.setTextSize(22);
        title.setTextColor(Ui.text(this));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        root.addView(title);
        body(Strings.helpIntro());

        for (String[] sec : Strings.helpSections()) {
            h(sec[0]);
            body(sec[1]);
        }
    }

    private void h(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(17);
        t.setTextColor(Ui.text(this));
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setPadding(0, dp(18), 0, dp(4));
        root.addView(t);
    }

    private void body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(Ui.text(this));
        t.setLineSpacing(0, 1.15f);
        root.addView(t);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
