package com.ti.tasktimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
 * 悬浮窗常驻服务：用户打开过知行之后一直运行，让悬浮窗永远在——
 * 没在计时时显示当前第一要务（刚暂停的任务优先，其次列表第一名），可直接开始/继续/暂停。
 * 计时中显示与文案由 TimerService 的节拍与本服务的 refresh 共同维持（同一套渲染）。
 */
public class FloatService extends Service {
    private static final String CHANNEL = "knowdo_float";
    private static final int NOTIF_ID = 4401;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastText = "";
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, 1000);
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
        super.onDestroy();
    }
}
