package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 「建议设置」清单（v1.19.0）：新用户首次打开（以及现有用户升级到本版后首次打开）
 * 呈现一次，设置里留常设入口随时复查。定位是建议、不是门槛——每一项都是可选的，
 * 按用不用对应功能决定，不开不影响其他功能使用，只是写明不开的后果：系统可能在
 * 后台把知行停掉，晨间与提醒可能不准时。
 * 六项都可自动检测状态、并一键跳到对应的系统设置页；回到本页自动刷新。
 * （v1.21.2 起含「无障碍截图通道」——屏幕内容守卫靠它截图，不用守卫可不开。）
 * 小米/华为的自启动与后台弹出系统没有可检测的接口，清单底部只留一行静态说明。
 */
public class SuggestedSettingsActivity extends Activity {
    public static final String EXTRA_FIRST_RUN = "first_run";
    private static final String PREF = "suggested_settings";

    /**
     * 新装/升级后首次打开呈现一次：本机记「已呈现过的最高 versionCode」，
     * 呈现过就不再自动弹（设置里随时能再打开）。返回 true 表示本次已拉起清单页。
     */
    public static boolean maybeShow(Activity a) {
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            int cur = (int) pi.getLongVersionCode();
            SharedPreferences p = a.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            if (p.getInt("shown_vc", 0) >= cur) return false;
            p.edit().putInt("shown_vc", cur).apply(); // 先记下：只自动呈现一次
            Intent i = new Intent(a, SuggestedSettingsActivity.class);
            i.putExtra(EXTRA_FIRST_RUN, true);
            a.startActivity(i);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        buildUi(); // 从系统设置回来，逐项状态自动刷新
    }

    private void buildUi() {
        boolean en = Strings.en();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(30));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(en ? "Suggested settings" : "建议设置");
        title.setTextSize(22);
        title.setTextColor(Ui.text(this));
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText(en
                ? "All of these are optional: each one powers a feature — turn on the ones you use, "
                + "skip the ones you don't; skipping one never affects the others. Each item says "
                + "what happens without it — without a few of them the system may stop KnowDo in "
                + "the background, and the morning page and reminders may not arrive on time."
                : "这些都是可选的：每一项管一个功能，用得上就开、用不上就不开，不开不影响其他功能使用。每一项下面都写了不开会怎样——有几项不开的话，系统可能在后台把知行停掉，晨间和提醒就可能不准时，照着自己的需要挑就行");
        intro.setTextSize(13);
        intro.setTextColor(Ui.sub(this));
        intro.setLineSpacing(0, 1.5f);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = dp(10);
        ip.bottomMargin = dp(8);
        root.addView(intro, ip);

        addItem(root,
                en ? "Battery optimization exemption" : "电池优化豁免",
                en ? "Without it the system may freeze the timer, the guard patrol and sync — "
                        + "this is the item that decides whether reminders survive the background"
                        : "不设最容易出事：系统可能把计时、守卫巡检和同步一起冻掉，提醒就到不了",
                batteryOk(), this::openBattery);
        addItem(root,
                en ? "Display over other apps" : "悬浮窗",
                en ? "Without it, full-screen pages on an unlocked screen and the floating window can't appear directly"
                        : "不设的话，亮屏时全屏页没法直接出现，常驻悬浮窗也出不来",
                AlertHelper.canDrawOverlays(this), this::openOverlay);
        addItem(root,
                en ? "Accessibility screenshot channel" : "无障碍截图通道",
                en ? "Powers the screen-content guard: while a timer runs with the guard on, it screenshots the screen "
                        + "now and then to check you are on task. If you don't use the screen-content guard, you don't need it — "
                        + "without it the guard can't see the screen and guard test mode is unavailable too"
                        : "给「屏幕内容守卫」用的：守卫开着计时时，靠它定期截一眼屏幕，看你做的是不是任务相关的事。不用屏幕守卫就不用开它；不开的话守卫没法看屏幕，守卫测试模式也用不了",
                KnowDoAccessibilityService.isEnabled(this), this::openAccessibility);
        addItem(root,
                en ? "Full-screen notifications" : "全屏通知",
                en ? "Without it, pages can't take over the lock screen — alerts drop to banners that are easy to miss"
                        : "不设的话，全屏页顶不到锁屏上，提醒只剩容易错过的横幅",
                fullscreenOk(), this::openFullscreen);
        addItem(root,
                en ? "Exact alarms" : "精确闹钟",
                en ? "Without it, call-backs and nagging rounds can drift by minutes"
                        : "不设的话，叫回、催促这些到点动作可能被系统推迟几分钟",
                exactAlarmOk(), this::openExactAlarm);
        addItem(root,
                en ? "Notifications" : "通知权限",
                en ? "Without it there is no countdown in the notification bar and no fallback banners"
                        : "不设的话，通知栏没有常驻倒计时，提醒也没有横幅兜底",
                notificationsOk(), this::openNotifications);

        TextView oem = new TextView(this);
        oem.setText(en
                ? "On Xiaomi, Huawei and similar phones, also allow KnowDo \"Auto-start\" and "
                + "\"Display pop-ups while running in the background\" in the system app settings — "
                + "those switches can't be detected from inside the app."
                : "小米、华为等机型，还需要在系统设置里给知行开「自启动」和「后台弹出界面」——这类开关在应用里检测不到，装好后照这行去开一遍");
        oem.setTextSize(12);
        oem.setTextColor(Ui.sub(this));
        oem.setLineSpacing(0, 1.5f);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, -2);
        op.topMargin = dp(18);
        root.addView(oem, op);

        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_FIRST_RUN, false)) {
            Button later = new Button(this);
            later.setText(en ? "Later" : "以后再说");
            later.setTextSize(16);
            later.setAllCaps(false);
            later.setTextColor(Ui.text(this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
            lp.topMargin = dp(24);
            root.addView(later, lp);
            later.setOnClickListener(v -> finish());
        }
    }

    private interface Opener { void open(); }

    private void addItem(LinearLayout root, String name, String risk, boolean ok, Opener opener) {
        boolean en = Strings.en();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(13));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(16);
        n.setTextColor(Ui.text(this));
        texts.addView(n);
        if (!ok) {
            TextView r = new TextView(this);
            r.setText(risk);
            r.setTextSize(12);
            r.setTextColor(Ui.sub(this));
            r.setLineSpacing(0, 1.4f);
            texts.addView(r);
        }
        row.addView(texts);
        TextView status = new TextView(this);
        status.setText(ok ? (en ? "On" : "已开启") : (en ? "Off ›" : "未开启 ›"));
        status.setTextSize(14);
        status.setTextColor(ok ? Ui.green(this) : Ui.accent(this));
        row.addView(status);
        row.setOnClickListener(v -> opener.open());
        root.addView(row);
        View line = new View(this);
        line.setBackgroundColor(Ui.line(this));
        root.addView(line, new LinearLayout.LayoutParams(-1, 1));
    }

    // ---------------- 检测 ----------------

    private boolean batteryOk() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean fullscreenOk() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.canUseFullScreenIntent();
        }
        return true; // 低版本视为已开
    }

    private boolean exactAlarmOk() {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    private boolean notificationsOk() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    // ---------------- 跳转 ----------------

    private void openBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) { }
        }
    }

    private void openOverlay() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) { }
    }

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) { }
        }
    }

    private void openFullscreen() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                Intent i = new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT");
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
                return;
            } catch (Exception ignored) { }
        }
        openNotificationsSettings();
    }

    private void openExactAlarm() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
                return;
            } catch (Exception ignored) { }
        }
        openNotificationsSettings();
    }

    private void openNotifications() {
        openNotificationsSettings();
    }

    private void openNotificationsSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Exception ignored) { }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
