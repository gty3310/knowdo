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
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 验证版主界面：四项权限体检 + 开始/停止测试 + 测试日志。
 * 实测流程：先把下面四项都变成 ✓，点「开始 60 秒测试」，立刻按电源键息屏放下手机。
 */
public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView statusView;
    private TextView logView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshLoop = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 700);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlertHelper.createChannels(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildUi(); // 回到本页时重画权限状态
        handler.removeCallbacks(refreshLoop);
        handler.post(refreshLoop);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshLoop);
        super.onPause();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(28), pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("知行 KnowDo · 全屏验证版");
        title.setTextSize(24);
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText("验证目标：熄屏和亮屏两种状态下，过半和结束时都直接弹同一个全屏页。\n"
                + "步骤：①下面五项权限全 ✓ → ②点开始 → ③分别在熄屏和亮屏各测一次。");
        intro.setTextSize(15);
        intro.setPadding(0, dp(8), 0, dp(16));
        root.addView(intro);

        // ---- 权限体检 ----
        boolean notifOk = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33;
        addPermRow("通知权限", notifOk, "去开启", v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        });

        boolean fsiOk = AlertHelper.canUseFullScreen(this);
        addPermRow("全屏通知（最关键）", fsiOk, "去开启", v -> {
            Intent i = new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT");
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        boolean exactOk = Build.VERSION.SDK_INT < 31 || (am != null && am.canScheduleExactAlarms());
        addPermRow("精确闹钟", exactOk, "去开启", v -> {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean batteryOk = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        addPermRow("电池优化豁免", batteryOk, "去开启", v -> {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        boolean overlayOk = AlertHelper.canDrawOverlays(this);
        addPermRow("上层显示（亮屏全屏的关键）", overlayOk, "去开启", v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        spacer(18);
        Button start60 = bigButton("开始 60 秒测试", Color.rgb(46, 125, 50));
        start60.setOnClickListener(v -> TimerService.startTimer(this, 60));
        root.addView(start60);
        spacer(10);
        Button start30 = bigButton("开始 30 秒测试", Color.rgb(21, 101, 192));
        start30.setOnClickListener(v -> TimerService.startTimer(this, 30));
        root.addView(start30);
        spacer(10);
        Button stop = bigButton("停止测试", Color.rgb(120, 120, 120));
        stop.setOnClickListener(v -> TimerService.stopTimer(this));
        root.addView(stop);

        spacer(18);
        statusView = new TextView(this);
        statusView.setTextSize(16);
        root.addView(statusView);

        spacer(10);
        TextView logTitle = new TextView(this);
        logTitle.setText("测试日志（每次点按都有记录）：");
        logTitle.setTextSize(15);
        root.addView(logTitle);
        logView = new TextView(this);
        logView.setTextSize(13);
        root.addView(logView);
        refreshStatus();
    }

    private void addPermRow(String name, boolean ok, String btnText, View.OnClickListener go) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView t = new TextView(this);
        t.setText((ok ? "✓ " : "✗ ") + name);
        t.setTextSize(17);
        t.setTextColor(ok ? Color.rgb(46, 125, 50) : Color.rgb(198, 40, 40));
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        if (!ok) {
            Button b = new Button(this);
            b.setText(btnText);
            b.setOnClickListener(go);
            row.addView(b);
        }
        root.addView(row);
    }

    private void refreshStatus() {
        if (statusView == null) return;
        if (TimerState.isRunning(this)) {
            long remain = Math.max(0, (TimerState.endAt(this) - System.currentTimeMillis()) / 1000);
            statusView.setText(TimerState.endFired(this)
                    ? "状态：时间已到，等你确认"
                    : "状态：计时中，剩余 " + remain + " 秒（现在可以息屏了）");
        } else {
            statusView.setText("状态：未在计时");
        }
        String log = TimerState.log(this);
        logView.setText(log.isEmpty() ? "（暂无）" : log);
    }

    private Button bigButton(String s, int bg) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(19);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));
        return b;
    }

    private void spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        root.addView(v);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
