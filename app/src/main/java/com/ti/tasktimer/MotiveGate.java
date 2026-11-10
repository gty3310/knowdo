package com.ti.tasktimer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * 加时动机门（v1.72.0）：每次加时之前先过一层自问——正文只放一句
 * 「动机至善，私心了无」，下面「有」「没有」两个按钮，点任何一个都放行进
 * 原来的选加时时长步；不点不能继续（本层没有关闭/跳过入口）。
 * v1.75.0：中间加第三个选项「没考虑」（用户亲定：答应/加时前没想过这句
 * 的，要如实有这一档），三档点哪个都放行。
 * 每次回答记一条事件进 zhi_events（subject＝这条原则本体、kind＝motive_check、
 * value＝有/没考虑/没有、source＝加时），写入走知页的本地先行＋待同步队列
 * （ZhiPage.recordEventExternal）：本地落盘即视为已答、立即放行，断网不丢、
 * 不卡加时流程；绝不写 user_state.self_checks（那是另一套口径）。
 * 本类是全软件唯一的动机门实现：检查页在页内嵌（buildInto），页外入口
 * （行页/日历的「+15 分钟」、日后的悬浮卡加时）走 MotiveGateActivity 套同一层，
 * 不许另复制一份。
 */
public final class MotiveGate {
    /** 「动机至善，私心了无」原则本体在 zhi_principles 的 id（云端已建，kind＝act）。 */
    public static final String PRINCIPLE_ID = "b271bb44-53d1-4c6e-bcb9-f14a61140316";
    public static final String QUESTION = "动机至善，私心了无";
    public static final String KIND = "motive_check";
    public static final String VALUE_YES = "有";
    public static final String VALUE_MID = "没考虑";
    public static final String VALUE_NO = "没有";

    private MotiveGate() { }

    /** 记一次回答：本地先行落盘＋排进待同步队列，立即返回，不等网络。 */
    public static void recordAnswer(Context c, boolean yes) {
        recordAnswer(c, yes ? VALUE_YES : VALUE_NO);
    }

    /** 记一次回答（三档原文）：本地先行落盘＋排进待同步队列，立即返回。 */
    public static void recordAnswer(Context c, String value) {
        ZhiPage.recordEventExternal(c, ZhiPage.T_PRINCIPLE, PRINCIPLE_ID, KIND,
                value, "加时");
    }

    /** 页外入口：拉起全屏动机门，过门后由门内直接给当前计时加 addSec 秒并关门。 */
    public static void launchExtend(Activity act, int addSec) {
        Intent i = new Intent(act, MotiveGateActivity.class);
        i.putExtra(MotiveGateActivity.EXTRA_ADD_SEC, addSec);
        act.startActivity(i);
    }

    /**
     * 在给定纵向容器里渲染自问一层（容器由调用方先清空/备好）：正文一句＋三按钮，
     * 朱砂只点在「有」上（v1.75.0：「没考虑」「没有」同为描边）。点任一：先记
     * 事件（本地落盘），再回调 onward。
     */
    public static void buildInto(final Activity act, LinearLayout col, final Runnable onward) {
        TextView q = new TextView(act);
        q.setText(QUESTION);
        q.setTextSize(26);
        q.setTypeface(Typeface.SERIF, Typeface.BOLD);
        q.setTextColor(Ui.text(act));
        q.setGravity(Gravity.CENTER);
        q.setPadding(0, dp(act, 28), 0, dp(act, 30));
        col.addView(q);
        col.addView(gateButton(act, VALUE_YES, true, () -> {
            recordAnswer(act, VALUE_YES);
            if (onward != null) onward.run();
        }));
        android.view.View sp = new android.view.View(act);
        sp.setLayoutParams(new LinearLayout.LayoutParams(1, dp(act, 12)));
        col.addView(sp);
        col.addView(gateButton(act, VALUE_MID, false, () -> {
            recordAnswer(act, VALUE_MID);
            if (onward != null) onward.run();
        }));
        android.view.View sp2 = new android.view.View(act);
        sp2.setLayoutParams(new LinearLayout.LayoutParams(1, dp(act, 12)));
        col.addView(sp2);
        col.addView(gateButton(act, VALUE_NO, false, () -> {
            recordAnswer(act, VALUE_NO);
            if (onward != null) onward.run();
        }));
    }

    /** 与检查页大按钮同一视觉语言：主按钮＝朱砂填充，次按钮＝描边。 */
    private static Button gateButton(Activity act, String text, boolean primary, Runnable onClick) {
        Button b = new Button(act);
        b.setText(text);
        b.setTextSize(20);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(act, 14));
        if (primary) {
            g.setColor(Ui.accent(act));
            b.setTextColor(Ui.onAccent(act));
        } else {
            g.setColor(Ui.surface(act));
            g.setStroke(dp(act, 1), Ui.line(act));
            b.setTextColor(Ui.text(act));
        }
        b.setBackground(g);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(act, 68)));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    /**
     * 比例聚合（只读本地事件缓存）：{今日有, 今日没考虑, 今日没有,
     * 累计有, 累计没考虑, 累计没有}，只算 kind＝motive_check 且 subject 是
     * 本原则的事件；今日按设备本地日期界。v1.75.0 前的历史事件只有有/没有
     * 两档，自然落进对应桶。
     */
    public static int[] stats(Context c) {
        int ty = 0, tm = 0, tn = 0, ay = 0, am = 0, an = 0;
        try {
            JSONArray arr = ZhiPage.eventsCache(c);
            String today = DayUtil.today();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null) continue;
                if (!KIND.equals(e.optString("kind", ""))) continue;
                if (!PRINCIPLE_ID.equals(e.optString("subject_id", ""))) continue;
                String v = e.optString("value", "");
                int bucket = VALUE_YES.equals(v) ? 0 : VALUE_MID.equals(v) ? 1 : 2;
                if (bucket == 0) ay++; else if (bucket == 1) am++; else an++;
                long ms = e.optLong("tsMs", 0);
                if (ms > 0) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(ms);
                    if (DayUtil.fmt(cal).equals(today)) {
                        if (bucket == 0) ty++; else if (bucket == 1) tm++; else tn++;
                    }
                }
            }
        } catch (Exception ignored) { }
        return new int[]{ty, tm, tn, ay, am, an};
    }

    private static int dp(Activity act, int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f);
    }
}
