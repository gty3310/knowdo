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
    public static final String CHANNEL_HABIT = "habit_remind";
    public static final int NOTIF_RUNNING = 100;
    public static final int NOTIF_ALERT = 200;

    public static final int TYPE_HALF = 1;
    public static final int TYPE_END = 2;
    public static final int TYPE_SUB = 3;
    public static final int NOTIF_GUARD = 201;
    public static final int NOTIF_TASK_ALARM = 203;
    public static final int NOTIF_NEXT_TASK = 204;
    public static final int NOTIF_RESUME_SILENT = 205;
    public static final int NOTIF_MORNING = 206;
    public static final int NOTIF_THANKS = 207;

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

        // 习惯提醒（v1.16.0）：每习惯独立提醒时间的本地通知，普通横幅即可
        NotificationChannel habit = new NotificationChannel(
                CHANNEL_HABIT, "习惯提醒", NotificationManager.IMPORTANCE_DEFAULT);
        habit.setDescription("每个习惯自己设的提醒时间到了，通知你记一下");
        nm.createNotificationChannel(habit);
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
        // 计时结束且该任务开了「结束时响铃」：先响铃，点「知道了」后响铃页再拉起检查页
        if (type == TYPE_END && fireTaskAlarmIfNeeded(c)) return;
        // 结束页催促（v1.21.1）：结束页去向已定（直达检查页），顺手给它武装
        // 「60 秒无回应→先震后响循环催」的升级链；走响铃页的那条在上面已 return，
        // 改由检查页实际展示时 EndNag.noteEndPageShown 武装（锚点取展示时刻）
        if (type == TYPE_END) EndNag.arm(c);
        createChannels(c);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = kg != null && kg.isKeyguardLocked();
        String label = type == TYPE_HALF ? "过半" : type == TYPE_SUB ? "子任务" : "结束";
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

        String title = type == TYPE_HALF ? "时间过了一半了" : type == TYPE_SUB ? "子任务时间到" : "时间到了";
        String text = type == TYPE_HALF
                ? "剩下的时间还够你做完吗？点开确认"
                : type == TYPE_SUB
                ? "这个子任务做完了吗？点开确认，或开始下一个"
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
        TimerState.logEvent(c, type == TYPE_HALF ? "已发出[过半]全屏提醒（通知路径）"
                : type == TYPE_SUB ? "已发出[子任务]全屏提醒（通知路径）" : "已发出[结束]全屏提醒（通知路径）");
    }

    /**
     * 静音暂停到点的亮屏通知（v1.11.0）：普通通知（不全屏、不出声），点开进叫回页处理。
     * 与普通叫回的 FSI 路径互斥——静音分支由 ResumeReceiver 分流，只走这里。
     */
    public static void notifySilentResume(Context c, String taskTitle) {
        createChannels(c);
        Intent intent = new Intent(c, ResumeRemindActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, NOTIF_RESUME_SILENT, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CHANNEL_RUNNING)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏸ 暂停时间到")
                .setContentText("该继续「" + taskTitle + "」了，点开处理")
                .setContentIntent(pi)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_RESUME_SILENT, b.build());
    }

    public static void cancelSilentResume(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_RESUME_SILENT);
    }

    /**
     * 当前计时任务是否要求结束响铃：是则先拉起响铃页（thenCheck），检查页随后。
     * 任务级 alarm 模式（v1.20.0）在此裁决：silent/off → 结束不出声（即使开了结束响铃）；
     * sound → 结束必响（即使没开结束响铃开关）；follow → 按 alarmOnTimerEnd 旧口径。
     */
    private static boolean fireTaskAlarmIfNeeded(Context c) {
        try {
            String tid = TimerState.taskId(c);
            if (tid == null) return false;
            TaskItem t = new TaskStore(c).byId(tid);
            if (t == null || t.deleted) return false;
            if (t.alarmMuted()) return false;
            if (!t.alarmOnTimerEnd && !TaskItem.ALARM_SOUND.equals(t.alarmMode)) return false;
            fireTaskAlarm(c, t.id, t.title, true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 任务闹钟响铃页的统一出口（v1.9.0）：亮屏未锁 + 悬浮权限 -> 直接启动；
     * 否则 FullScreenIntent 通知拉起（与检查页同一策略）。铃声由响铃页本身播放
     * （USAGE_ALARM 通道），通知不带声音。
     */
    public static void fireTaskAlarm(Context c, String taskId, String title, boolean thenCheck) {
        createChannels(c);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = kg != null && kg.isKeyguardLocked();
        if (interactive && !locked && canDrawOverlays(c)) {
            try {
                Intent direct = new Intent(c, TaskAlarmActivity.class);
                direct.putExtra(TaskAlarmActivity.EXTRA_TASK_ID, taskId);
                direct.putExtra(TaskAlarmActivity.EXTRA_TASK_TITLE, title);
                direct.putExtra(TaskAlarmActivity.EXTRA_THEN_CHECK, thenCheck);
                direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(direct);
                TimerState.logEvent(c, "已直接弹出任务闹钟响铃页（亮屏路径，未经通知）");
                return;
            } catch (Exception e) {
                TimerState.logEvent(c, "响铃页直接启动失败，退回通知路径：" + e.getClass().getSimpleName());
            }
        }
        Intent intent = new Intent(c, TaskAlarmActivity.class);
        intent.putExtra(TaskAlarmActivity.EXTRA_TASK_ID, taskId);
        intent.putExtra(TaskAlarmActivity.EXTRA_TASK_TITLE, title);
        intent.putExtra(TaskAlarmActivity.EXTRA_THEN_CHECK, thenCheck);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, NOTIF_TASK_ALARM, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ " + title)
                .setContentText(thenCheck ? "计时结束了，点开停铃" : "闹钟时间到了，点开停铃")
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setFullScreenIntent(pi, true);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_TASK_ALARM, b.build());
        TimerState.logEvent(c, "已发出任务闹钟全屏提醒（通知路径）");
    }

    public static void cancelTaskAlarm(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_TASK_ALARM);
    }

    /**
     * 任务衔接守卫页的统一出口（v1.10.0）：与检查页同一策略——
     * 亮屏未锁 + 悬浮权限 -> 直接启动；否则 FullScreenIntent 通知拉起。
     */
    public static void fireNextTask(Context c) {
        createChannels(c);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = kg != null && kg.isKeyguardLocked();
        if (interactive && !locked && canDrawOverlays(c)) {
            try {
                Intent direct = new Intent(c, NextTaskActivity.class);
                direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(direct);
                TimerState.logEvent(c, "已直接弹出衔接守卫页（亮屏路径，未经通知）");
                return;
            } catch (Exception e) {
                TimerState.logEvent(c, "衔接守卫页直接启动失败，退回通知路径：" + e.getClass().getSimpleName());
            }
        }
        Intent intent = new Intent(c, NextTaskActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, NOTIF_NEXT_TASK, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("接下来干什么？")
                .setContentText("现在没有任务在计时，点开定下一个任务")
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setFullScreenIntent(pi, true);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_NEXT_TASK, b.build());
        TimerState.logEvent(c, "已发出衔接守卫全屏提醒（通知路径）");
    }

    public static void cancelNextTask(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_NEXT_TASK);
    }

    /**
     * 晨间页的统一出口（v1.17.0）：与衔接守卫页同一策略——
     * 亮屏未锁 + 悬浮权限 -> 直接启动；否则 FullScreenIntent 通知拉起。
     * 供晨间门首触（MorningGate）与「暂停一会儿」到点（MorningReceiver）共用；
     * 拉起的是门模式晨间页（无 extra），页内再按 shouldShowMorning 自行把关。
     */
    public static void fireMorning(Context c) {
        createChannels(c);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = kg != null && kg.isKeyguardLocked();
        if (interactive && !locked && canDrawOverlays(c)) {
            try {
                Intent direct = new Intent(c, MorningActivity.class);
                direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(direct);
                TimerState.logEvent(c, "已直接弹出晨间页（亮屏路径，未经通知）");
                return;
            } catch (Exception e) {
                TimerState.logEvent(c, "晨间页直接启动失败，退回通知路径：" + e.getClass().getSimpleName());
            }
        }
        Intent intent = new Intent(c, MorningActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, NOTIF_MORNING, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("先填晨间页，开启今天")
                .setContentText("昨日习惯＋昨日小报＋晨间日记，填完才算开启今天")
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setFullScreenIntent(pi, true);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_MORNING, b.build());
        TimerState.logEvent(c, "已发出晨间页全屏提醒（通知路径）");
    }

    /**
     * 致谢页的统一出口（v1.19.0）：与晨间页同一策略——亮屏未锁 + 悬浮权限 -> 直接启动；
     * 否则 FullScreenIntent 通知拉起。供 GuardDispatch 在路人静音待致谢时调用。
     */
    public static void fireThanks(Context c) {
        createChannels(c);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = kg != null && kg.isKeyguardLocked();
        if (interactive && !locked && canDrawOverlays(c)) {
            try {
                Intent direct = new Intent(c, ThanksActivity.class);
                direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(direct);
                TimerState.logEvent(c, "已直接弹出致谢页（亮屏路径，未经通知）");
                return;
            } catch (Exception e) {
                TimerState.logEvent(c, "致谢页直接启动失败，退回通知路径：" + e.getClass().getSimpleName());
            }
        }
        Intent intent = new Intent(c, ThanksActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, NOTIF_THANKS, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("之前有人帮助你关掉消息了")
                .setContentText("点开继续你的任务")
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setFullScreenIntent(pi, true);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_THANKS, b.build());
        TimerState.logEvent(c, "已发出致谢页全屏提醒（通知路径）");
    }

    public static void cancelMorning(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_MORNING);
    }

    public static void cancelAlert(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ALERT);
    }

    /** 专注守卫问询页的统一出口：与计时提醒同一策略（亮屏直接启动，锁屏走 FSI 通知）。 */
    public static void fireGuard(Context c, String fgPkg) {
        fireGuard(c, fgPkg, null);
    }

    /**
     * 带一句理由的问询（v1.26.0，任务软件名单黑名单命中时用）：reason 是给用户看的
     * 一句完整的话（如「你打开了「抖音」，它在「读经典」的黑名单里」），经
     * GuardActivity.EXTRA_REASON 带到问询页副文案；为 null 时走原文案。
     */
    public static void fireGuard(Context c, String fgPkg, String reason) {
        createChannels(c);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = kg != null && kg.isKeyguardLocked();
        if (interactive && !locked && canDrawOverlays(c)) {
            try {
                Intent direct = new Intent(c, GuardActivity.class);
                direct.putExtra(GuardActivity.EXTRA_PKG, fgPkg);
                if (reason != null) direct.putExtra(GuardActivity.EXTRA_REASON, reason);
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
        if (reason != null) intent.putExtra(GuardActivity.EXTRA_REASON, reason);
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
