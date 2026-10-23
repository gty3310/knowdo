package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 权限体检页：五项权限逐项显示状态，未开的一键跳转开启。 */
public class PermActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(28), pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("权限体检");
        title.setTextSize(22);
        root.addView(title);
        TextView intro = new TextView(this);
        intro.setText("这五项是知行能否准时、全屏提醒你的前提，全部 ✓ 才可靠。");
        intro.setTextSize(14);
        intro.setPadding(0, dp(6), 0, dp(14));
        root.addView(intro);

        boolean notifOk = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        addRow(root, "通知权限", notifOk, v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        });

        addRow(root, "全屏通知（熄屏弹出）", AlertHelper.canUseFullScreen(this), v -> {
            Intent i = new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT");
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        addRow(root, "上层显示（亮屏全屏的关键）", AlertHelper.canDrawOverlays(this), v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        boolean exactOk = Build.VERSION.SDK_INT < 31 || (am != null && am.canScheduleExactAlarms());
        addRow(root, "精确闹钟", exactOk, v -> {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean batteryOk = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        addRow(root, "电池优化豁免", batteryOk, v -> {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        // 计时悬浮窗开关（依赖上面的“上层显示”授权）
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(18), 0, dp(6));
        TextView t = new TextView(this);
        t.setText("计时悬浮窗（任务 + 倒计时 + 笔记）");
        t.setTextSize(17);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        Button toggle = new Button(this);
        boolean on = FloatingWindow.enabled(this);
        toggle.setText(on ? "已开" : "已关");
        toggle.setOnClickListener(v -> {
            FloatingWindow.setEnabled(this, !FloatingWindow.enabled(this));
            buildUi();
        });
        row.addView(toggle);
        root.addView(row);
    }

    private void addRow(LinearLayout root, String name, boolean ok, View.OnClickListener go) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView t = new TextView(this);
        t.setText((ok ? "✓ " : "✗ ") + name);
        t.setTextSize(17);
        t.setTextColor(ok ? Color.rgb(46, 125, 50) : Color.rgb(198, 40, 40));
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        if (!ok) {
            Button b = new Button(this);
            b.setText("去开启");
            b.setOnClickListener(go);
            row.addView(b);
        }
        root.addView(row);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
