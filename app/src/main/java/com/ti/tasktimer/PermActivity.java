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

/** 权限体检页：六项权限逐项显示状态，未开的一键跳转开启；另有 Muse 应用、悬浮窗与屏幕内容守卫设置。 */
public class PermActivity extends Activity {
    private static final int REQ_SG_AUTH = 77;
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

        boolean usageOk = FocusGuard.detectorReady(this);
        addRow(root, "使用情况访问（检测分神软件）", usageOk, v -> {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });

        // 「回到 Muse」的目标应用（当前任务页的按钮跳它；与 Muse 对话不被守卫拦截）
        LinearLayout museRow = new LinearLayout(this);
        museRow.setOrientation(LinearLayout.HORIZONTAL);
        museRow.setPadding(0, dp(10), 0, dp(6));
        TextView mt = new TextView(this);
        String mp = FocusGuard.musePackage(this);
        mt.setText("「回到 Muse」指向：" + (mp == null ? "未设置" : AppLister.labelOf(this, mp)));
        mt.setTextSize(15);
        mt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        museRow.addView(mt);
        Button pickMuse = new Button(this);
        pickMuse.setText("选择");
        pickMuse.setOnClickListener(v -> {
            final java.util.List<AppLister.AppInfo> apps = AppLister.launchableApps(this);
            String[] labels = new String[apps.size()];
            for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;
            new android.app.AlertDialog.Builder(this)
                    .setTitle("哪个是 Muse？")
                    .setItems(labels, (d, which) -> {
                        FocusGuard.setMusePackage(this, apps.get(which).pkg);
                        buildUi();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        museRow.addView(pickMuse);
        root.addView(museRow);

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

        Button preview = new Button(this);
        preview.setText("预览悬浮窗（显示 10 秒，不用先开始计时）");
        preview.setOnClickListener(v -> FloatingWindow.showPreview(this));
        root.addView(preview);
        TextView hint = new TextView(this);
        hint.setText("正式用法：悬浮窗不用手动开——在任务列表点任意任务的 ▶ 开始计时后，它会自动出现在任何界面之上；计时结束自动消失。");
        hint.setTextSize(13);
        hint.setTextColor(Color.DKGRAY);
        hint.setPadding(0, dp(6), 0, 0);
        root.addView(hint);

        addScreenGuardSection(root);
    }

    // ---------------- 屏幕内容守卫 ----------------
    private void addScreenGuardSection(LinearLayout root) {
        TextView header = new TextView(this);
        header.setText("屏幕内容守卫（看屏幕内容抓分神）");
        header.setTextSize(19);
        header.setPadding(0, dp(26), 0, dp(4));
        root.addView(header);
        TextView intro = new TextView(this);
        intro.setText("专注守卫只能看“在用哪个应用”；这个守卫看屏幕内容，抓在浏览器里购物、刷短视频网页版、看黄色内容这类分神。"
                + "只在计时中采样，截图只在本机分析、分析完立刻销毁，绝不保存、绝不上传。详见主界面「帮助」第七节。");
        intro.setTextSize(13);
        intro.setTextColor(Color.DKGRAY);
        root.addView(intro);

        addToggleRow(root, "屏幕内容守卫总开关（默认关）", ScreenGuardPrefs.enabled(this), on -> {
            ScreenGuardPrefs.setEnabled(this, on);
            if (on && TimerState.isRunning(this)) ScreenCaptureService.onTimerStarted(this);
            if (!on) ScreenCaptureService.onTimerStopped(this);
            buildUi();
        });

        addToggleRow(root, "黄色/暴露内容一张即触发（关掉则也要连中两张）", ScreenGuardPrefs.nsfwOneShot(this), on -> {
            ScreenGuardPrefs.setNsfwOneShot(this, on);
            buildUi();
        });

        // 无障碍截图通道（v1.21.0 首选通道）：开了之后守卫改走无障碍一次性截图，
        // 不再需要每次计时授权录屏，进程被杀也能由系统重绑续跑
        LinearLayout a11yRow = new LinearLayout(this);
        a11yRow.setOrientation(LinearLayout.HORIZONTAL);
        a11yRow.setPadding(0, dp(10), 0, dp(6));
        TextView a11yText = new TextView(this);
        boolean a11yOn = KnowDoAccessibilityService.isEnabled(this);
        a11yText.setText("无障碍截图通道：" + (a11yOn ? "已开启" : "未开启"));
        a11yText.setTextSize(15);
        a11yText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        a11yRow.addView(a11yText);
        if (!a11yOn) {
            Button a11yBtn = new Button(this);
            a11yBtn.setText("去开启");
            a11yBtn.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            });
            a11yRow.addView(a11yBtn);
        }
        root.addView(a11yRow);
        TextView a11yNote = new TextView(this);
        a11yNote.setText("开启后守卫优先用它截图：不用每次授权、采样更密；不开则回退到下面的录屏授权");
        a11yNote.setTextSize(13);
        a11yNote.setTextColor(Color.DKGRAY);
        root.addView(a11yNote);

        addToggleRow(root, "守卫测试模式（每 10 秒把判定读数放进悬浮窗）", ScreenGuardPrefs.testMode(this), on -> {
            if (on) {
                if (!KnowDoAccessibilityService.isEnabled(this)) {
                    android.widget.Toast.makeText(this,
                            "先开启上面的无障碍截图通道，再开测试模式",
                            android.widget.Toast.LENGTH_LONG).show();
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    }
                    buildUi();
                    return;
                }
                if (!AlertHelper.canDrawOverlays(this)) {
                    android.widget.Toast.makeText(this,
                            "测试悬浮窗需要先把上面的「上层显示」开成 ✓",
                            android.widget.Toast.LENGTH_LONG).show();
                    buildUi();
                    return;
                }
                ScreenGuardPrefs.setTestMode(this, true);
                GuardTestMode.start(this);
            } else {
                ScreenGuardPrefs.setTestMode(this, false);
                GuardTestMode.stop();
            }
            buildUi();
        });
        // 测试模式开着时（比如重启后回到本页），把采样与悬浮窗续上
        if (ScreenGuardPrefs.testMode(this) && KnowDoAccessibilityService.isEnabled(this)
                && AlertHelper.canDrawOverlays(this)) {
            GuardTestMode.start(this);
        }

        LinearLayout authRow = new LinearLayout(this);
        authRow.setOrientation(LinearLayout.HORIZONTAL);
        authRow.setPadding(0, dp(10), 0, dp(6));
        TextView at = new TextView(this);
        at.setText("截图授权：" + (ScreenCaptureService.isAuthorized(this) ? "已有可用授权" : "暂无（开始计时时系统会请求）"));
        at.setTextSize(15);
        at.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        authRow.addView(at);
        Button authBtn = new Button(this);
        authBtn.setText("授权/重新授权");
        authBtn.setOnClickListener(v -> {
            android.media.projection.MediaProjectionManager mpm =
                    (android.media.projection.MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mpm != null) startActivityForResult(mpm.createScreenCaptureIntent(), REQ_SG_AUTH);
        });
        authRow.addView(authBtn);
        root.addView(authRow);

        TextView note = new TextView(this);
        note.setText("说明：这份录屏授权只在无障碍通道没开时作回退用。安卓 14 起每次截图会话都要授权一次——"
                + "回退流程是开始计时时顺手点一次“允许”，整个计时期间持续采样，计时结束授权即失效。这里的按钮是提前授权/换授权用："
                + "授权在被用掉（开始一次带守卫的计时）之前一直有效；自动开始（autoStart）的任务没有授权时不会采样，会在记录里留一行说明。");
        note.setTextSize(13);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(4), 0, 0);
        root.addView(note);
    }

    private interface ToggleAction { void run(boolean on); }

    private void addToggleRow(LinearLayout root, String label, boolean on, ToggleAction action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(6));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(16);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        Button b = new Button(this);
        b.setText(on ? "已开" : "已关");
        b.setOnClickListener(v -> action.run(!on));
        row.addView(b);
        root.addView(row);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SG_AUTH) {
            if (resultCode == RESULT_OK && data != null) {
                ProjectionAuth.store(resultCode, data);
                android.widget.Toast.makeText(this, "已授权：下一次开着守卫的计时会用它采样", android.widget.Toast.LENGTH_LONG).show();
                if (TimerState.isRunning(this)) ScreenCaptureService.onTimerStarted(this);
            } else {
                android.widget.Toast.makeText(this, "未获得截图授权", android.widget.Toast.LENGTH_LONG).show();
            }
            buildUi();
        }
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
