package com.ti.tasktimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * 悬浮窗常驻服务：用户打开过知行之后一直运行，让悬浮窗永远在——
 * 没在计时时显示当前第一要务（刚暂停的任务优先，其次列表第一名），可直接开始/继续/暂停。
 * 计时中显示与文案由 TimerService 的节拍与本服务的 refresh 共同维持（同一套渲染）。
 */
public class FloatService extends Service {
    private static final String CHANNEL = "knowdo_float";
    private static final int NOTIF_ID = 4401;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastText = "";
    private int patrolCounter = 0; // 任务衔接守卫巡检计数（约每 30 秒一次）
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, 1000);
        }
    };
    /** 晨间门首触（v1.17.0）：用户早上第一次解锁用机就拉晨间页，不等他打开知行。 */
    private final BroadcastReceiver userPresentReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            MorningGate.autoCheck(c);
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, FloatService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, FloatService.class));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "知行常驻悬浮窗", NotificationManager.IMPORTANCE_MIN));
        }
        showNotification("知行常驻中");
        // 晨间门（v1.17.0）：监听解锁首触；服务（重）启动时按持久化状态续排推后闹钟
        try {
            registerReceiver(userPresentReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        } catch (Exception ignored) { }
        try { MorningGate.ensureScheduled(this); } catch (Exception ignored) { }
        handler.post(loop);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void tick() {
        if (!FloatingWindow.enabled(this)) {
            FloatingWindow.hide();
            stopSelf();
            return;
        }
        FloatSnooze.poll(this); // 「等任务结束」型 snooze 的到期检测（v1.14.0）
        if (FloatSnooze.isActive(this)) {
            FloatingWindow.hide(); // snooze 中：只藏窗，本服务的通知/巡检照常
            patrolIdle();
            return;
        }
        if (TimerState.endFired(this)) {
            // 到点等确认期间不显示（检查页在管事），确认完自动回来
            FloatingWindow.hide();
            return;
        }
        FloatingWindow.ensureShown(this);
        FloatingWindow.refresh(this);
        String text = FloatingWindow.displaySummary(this);
        if (!text.equals(lastText)) {
            lastText = text;
            showNotification(text);
        }
        patrolIdle();
    }

    /**
     * 任务衔接守卫巡检（v1.10.0）：约每 30 秒看一次——屏幕亮着、且本应用没有页面在前台时，
     * 若处于空档（没计时、没有生效中的定时暂停），经 IdleGuard 拉起「下一个任务」页。
     * 本应用页面在前台时不巡检打断：主页由它自己的 onResume 钩子负责，其余页面不该被打断。
     * 开机后本服务由 BootReceiver/主页拉起，巡检即是开机兜底，不单独处理开机。
     */
    private void patrolIdle() {
        if (++patrolCounter < 30) return;
        patrolCounter = 0;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null || !pm.isInteractive()) return; // 屏幕没亮，不巡检
        if (KnowDoApp.ownActivityVisible()) return;
        IdleGuard.checkAndFire(this);
        // 晨间门兜底（v1.17.0）：解锁那一刻本服务没赶上（进程重启稍晚）、或通话刚
        // 结束时，由巡检补拉一次（autoCheck 内部有 session 去重，不会反复打扰）
        MorningGate.autoCheck(this);
    }

    private void showNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 9, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("知行 · 当前第一要务")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(loop);
        try { unregisterReceiver(userPresentReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
