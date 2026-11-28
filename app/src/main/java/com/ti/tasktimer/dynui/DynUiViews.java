package com.ti.tasktimer.dynui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ti.tasktimer.Ui;

/** dynui 渲染器共用的小部件构造（与 ChatPanel 现行卡片同一视觉口径）。 */
final class DynUiViews {

    private DynUiViews() { }

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** 卡片盒子：米白面、细线边、14 圆角，与聊天里现行提案卡一致。 */
    static LinearLayout cardBox(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 14));
        g.setColor(Ui.surface(c));
        g.setStroke(1, Ui.line(c));
        box.setBackground(g);
        box.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 4), 0, dp(c, 10));
        box.setLayoutParams(lp);
        return box;
    }

    static TextView btn(Context c, String text, boolean primary) {
        TextView b = new TextView(c);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(primary ? Ui.accent(c) : Ui.sub(c));
        b.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        return b;
    }

    /** 选项片：选中态朱砂红描边＋字色，未选灰线灰字。 */
    static TextView chip(Context c, String text, boolean selected) {
        TextView ch = new TextView(c);
        ch.setText(text);
        ch.setTextSize(13.5f);
        ch.setTextColor(selected ? Ui.accent(c) : Ui.sub(c));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 11));
        g.setColor(Ui.surface(c));
        g.setStroke(Math.max(1, dp(c, 1)), selected ? Ui.accent(c) : Ui.line(c));
        ch.setBackground(g);
        ch.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 3), dp(c, 6), dp(c, 3));
        ch.setLayoutParams(lp);
        return ch;
    }

    static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return r;
    }

    static void addWrap(LinearLayout parent, View v) {
        parent.addView(v);
    }
}
