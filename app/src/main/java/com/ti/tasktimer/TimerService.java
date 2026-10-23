package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * 倒计时前台服务。
 * 主触发：服务内每 250ms 对照绝对时间点检查；兜底：AlarmManager 精确闹钟（进程被杀也能触发）。
 * 两条路都汇入 dispatch()，用 halfFired/endFired 标记保证每个点只弹一次。
 */
public class TimerService extends Service {
    public static final String ACTION_START = "com.ti.tasktimer.START";
    public static final String ACTION_STOP = "com.ti.tasktimer.STOP";
    public static final String ACTION_EXTEND = "com.ti.tasktimer.EXTEND";
    public static final String EXTRA_TOTAL_SEC = "total_sec";
    public static final String EXTRA_ADD_SEC = "add_sec";
    public static final String EXTRA_TASK_ID = "task_id";
    public static final String EXTRA_TASK_TITLE = "task_title";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, 250);
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    public static void startTimer(Context c, int totalSec, String taskId, String taskTitle) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_TOTAL_SEC, totalSec);
        i.putExtra(EXTRA_TASK_ID, taskId);
        i.putExtra(EXTRA_TASK_TITLE, taskTitle);
        c.startForegroundService(i);
    }

    public static void extendTimer(Context c, int addSec) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_EXTEND);
        i.putExtra(EXTRA_ADD_SEC, addSec);
        c.startForegroundService(i);
    }

    public static void stopTimer(Context c) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_STOP);
        c.startService(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AlertHelper.createChannels(this);
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            TimerState.logEvent(this, "计时已停止/完成");
            TimerState.stop(this);
            cancelAlarms();
            AlertHelper.cancelAlert(this);
            FloatingWindow.hide();
            handler.removeCallbacks(ticker);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            int total = intent.getIntExtra(EXTRA_TOTAL_SEC, 60);
            TimerState.start(this, total,
                    intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_TASK_TITLE));
            FocusGuard.reset(this);
            TimerState.logEvent(this, "开始计时：" + TimerState.taskTitle(this) + "（" + total + " 秒）");
        } else if (ACTION_EXTEND.equals(action)) {
            int add = intent.getIntExtra(EXTRA_ADD_SEC, 30);
            TimerState.extend(this, add);
            TimerState.logEvent(this, "加时 " + add + " 秒，新结束时间已顺延");
        }
        // START / EXTEND / 系统重启恢复（intent==null）都汇到这里
        if (TimerState.isRunning(this)) {
            startInForeground();
            scheduleAlarms();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
            tick();
            return START_STICKY;
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    private void tick() {
        if (!TimerState.isRunning(this)) return;
        long now = System.currentTimeMillis();
        // 悬浮窗：计时中显示任务与倒计时；检查页将弹出时让位；到点等待确认时隐藏
        if (TimerState.endFired(this)) {
            FloatingWindow.hide();
        } else {
            FloatingWindow.ensureShown(this);
            long remainSec = Math.max(0, (TimerState.endAt(this) - now) / 1000);
            String remain = remainSec >= 3600
                    ? String.format("%d:%02d:%02d", remainSec / 3600, (remainSec % 3600) / 60, remainSec % 60)
                    : String.format("%d:%02d", remainSec / 60, remainSec % 60);
            FloatingWindow.update(this, remain);
        }
        if (!TimerState.halfFired(this) && now >= TimerState.halfAt(this)) {
            TimerState.markHalfFired(this);
            FloatingWindow.setSuppressed(true);
            AlertHelper.fire(this, AlertHelper.TYPE_HALF);
        }
        if (!TimerState.endFired(this) && now >= TimerState.endAt(this)) {
            TimerState.markEndFired(this);
            FloatingWindow.setSuppressed(true);
            AlertHelper.fire(this, AlertHelper.TYPE_END);
        }
        FocusGuard.tick(this);
        // 倒计时数字由通知的 chronometer 显示，文本每 tick 刷新一次当前阶段
        startInForeground();
    }

    /** 兜底触发点的统一入口（AlertReceiver 也调这里的逻辑标记，避免重复弹）。 */
    public static void onAlarmFired(Context c, int type) {
        if (!TimerState.isRunning(c)) return;
        if (type == AlertHelper.TYPE_HALF) {
            if (TimerState.halfFired(c)) return;
            TimerState.markHalfFired(c);
        } else {
            if (TimerState.endFired(c)) return;
            TimerState.markEndFired(c);
        }
        FloatingWindow.setSuppressed(true);
        AlertHelper.fire(c, type);
    }

    private void startInForeground() {
        long endAt = TimerState.endAt(this);
        long remainSec = Math.max(0, (endAt - System.currentTimeMillis()) / 1000);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, AlertHelper.CHANNEL_RUNNING)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(TimerState.taskTitle(this) + " · 计时中")
                .setContentText(TimerState.endFired(this) ? "时间到，等待你确认" : "剩余约 " + remainSec + " 秒")
                .setContentIntent(pi)
                .setOngoing(true)
                .setWhen(endAt)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(AlertHelper.NOTIF_RUNNING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(AlertHelper.NOTIF_RUNNING, n);
        }
    }

    private PendingIntent alarmIntent(int type) {
        Intent i = new Intent(this, AlertReceiver.class);
        i.setAction(AlertReceiver.ACTION_ALARM);
        i.putExtra(AlertReceiver.EXTRA_TYPE, type);
        return PendingIntent.getBroadcast(this, type, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void scheduleAlarms() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (!TimerState.halfFired(this)) {
            setAlarm(am, TimerState.halfAt(this), alarmIntent(AlertHelper.TYPE_HALF), exact);
        }
        setAlarm(am, TimerState.endAt(this), alarmIntent(AlertHelper.TYPE_END), exact);
    }

    private void setAlarm(AlarmManager am, long atMillis, PendingIntent pi, boolean exact) {
        if (atMillis <= System.currentTimeMillis()) return;
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
        }
    }

    private void cancelAlarms() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(alarmIntent(AlertHelper.TYPE_HALF));
        am.cancel(alarmIntent(AlertHelper.TYPE_END));
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        FloatingWindow.hide();
        super.onDestroy();
    }
}
