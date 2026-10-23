package com.ti.tasktimer;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * 全屏提醒的统一出口。两种状态走同一个 CheckActivity，页面保证一模一样：
 * 亮屏未锁 + 已授予“上层显示”权限 -> 直接启动页面（官方后台启动豁免），不经通知；
 * 熄屏/锁屏 -> FullScreenIntent 通知拉起（已在用户真机验证通过）；
 * 其余情况 -> FSI 通知兜底（亮屏时降级为横幅，不会静默失败）。
 */
public final class AlertHelper {
    public static final String CHANNEL_RUNNING = "timer_running";
    public static final String CHANNEL_ALERTS = "timer_alerts";
    public static final int NOTIF_RUNNING = 100;
    public static final int NOTIF_ALERT = 200;

    public static final int TYPE_HALF = 1;
    public static final int TYPE_END = 2;
    public static final int NOTIF_GUARD = 201;

    private AlertHelper() {}

    public static void createChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel running = new NotificationChannel(
                CHANNEL_RUNNING, "计时进行中", NotificationManager.IMPORTANCE_LOW);
        running.setDescription("倒计时常驻通知");
        nm.createNotificationChannel(running);

        NotificationChannel alerts = new NotificationChannel(
                CHANNEL_ALERTS, "计时提醒（全屏）", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("过半与结束时的全屏提醒");
        alerts.enableVibration(true);
        alerts.setVibrationPattern(new long[]{0, 400, 200, 400});
        alerts.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(alerts);
    }

    public static boolean canUseFullScreen(Context c) {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.canUseFullScreenIntent();
        }
        return true; // Android 13 及以下声明权限即生效
    }

    public static boolean canDrawOverlays(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    public static void fire(Context c, int type) {
        createChannels(c);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = kg != null && kg.isKeyguardLocked();
        String label = type == TYPE_HALF ? "过半" : "结束";
        if (interactive && !locked && canDrawOverlays(c)) {
            try {
                Intent direct = new Intent(c, CheckActivity.class);
                direct.putExtra(CheckActivity.EXTRA_TYPE, type);
                direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(direct);
                TimerState.logEvent(c, "已直接弹出[" + label + "]全屏页（亮屏路径，未经通知）");
                return;
            } catch (Exception e) {
                TimerState.logEvent(c, "亮屏直接启动失败，退回通知路径：" + e.getClass().getSimpleName());
            }
        }
        fireViaNotification(c, type);
    }

    /** 发出带 FullScreenIntent 的提醒通知。无 FSI 权限时系统自动降级为横幅，不会静默失败。 */
    private static void fireViaNotification(Context c, int type) {
        Intent intent = new Intent(c, CheckActivity.class);
        intent.putExtra(CheckActivity.EXTRA_TYPE, type);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, type, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = type == TYPE_HALF ? "时间过了一半了" : "时间到了";
        String text = type == TYPE_HALF
                ? "剩下的时间还够你做完吗？点开确认"
                : "做完了吗？点开确认，或给自己加一点时间";

        Notification.Builder b = new Notification.Builder(c, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setFullScreenIntent(pi, true);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ALERT, b.build());
        TimerState.logEvent(c, type == TYPE_HALF ? "已发出[过半]全屏提醒（通知路径）" : "已发出[结束]全屏提醒（通知路径）");
    }

    public static void cancelAlert(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ALERT);
    }

    /** 专注守卫问询页的统一出口：与计时提醒同一策略（亮屏直接启动，锁屏走 FSI 通知）。 */
    public static void fireGuard(Context c, String fgPkg) {
        createChannels(c);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = kg != null && kg.isKeyguardLocked();
        if (interactive && !locked && canDrawOverlays(c)) {
            try {
                Intent direct = new Intent(c, GuardActivity.class);
                direct.putExtra(GuardActivity.EXTRA_PKG, fgPkg);
                direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(direct);
                TimerState.logEvent(c, "已直接弹出专注守卫页（亮屏路径）");
                return;
            } catch (Exception e) {
                TimerState.logEvent(c, "守卫页直接启动失败，退回通知路径：" + e.getClass().getSimpleName());
            }
        }
        Intent intent = new Intent(c, GuardActivity.class);
        intent.putExtra(GuardActivity.EXTRA_PKG, fgPkg);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, NOTIF_GUARD, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("你是在做正确的任务吗？")
                .setContentText("检测到你打开了「" + AppLister.labelOf(c, fgPkg) + "」，点开确认")
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setFullScreenIntent(pi, true);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_GUARD, b.build());
    }

    public static void cancelGuard(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_GUARD);
    }

    /** 屏幕内容守卫问询页的统一出口：复用 GuardActivity（content 模式），策略与应用级守卫一致。 */
    public static void fireGuardContent(Context c, String category, String detail) {
        createChannels(c);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = kg != null && kg.isKeyguardLocked();
        String label = ContentAnalyzer.label(category);
        if (interactive && !locked && canDrawOverlays(c)) {
            try {
                Intent direct = new Intent(c, GuardActivity.class);
                direct.putExtra(GuardActivity.EXTRA_SOURCE, "content");
                direct.putExtra(GuardActivity.EXTRA_CATEGORY, category);
                direct.putExtra(GuardActivity.EXTRA_DETAIL, detail);
                direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(direct);
                TimerState.logEvent(c, "已直接弹出屏幕内容守卫页（亮屏路径）");
                return;
            } catch (Exception e) {
                TimerState.logEvent(c, "内容守卫页直接启动失败，退回通知路径：" + e.getClass().getSimpleName());
            }
        }
        Intent intent = new Intent(c, GuardActivity.class);
        intent.putExtra(GuardActivity.EXTRA_SOURCE, "content");
        intent.putExtra(GuardActivity.EXTRA_CATEGORY, category);
        intent.putExtra(GuardActivity.EXTRA_DETAIL, detail);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, NOTIF_GUARD, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("屏幕内容守卫：检测到「" + label + "」")
                .setContentText(detail + " 点开确认")
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setFullScreenIntent(pi, true);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_GUARD, b.build());
    }
}
