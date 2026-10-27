package com.ti.tasktimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

/**
 * 实时同步服务：前台服务（低打扰常驻通知），持有 Supabase Realtime 推送连接，
 * 并每 30 秒跑一次全量对账兜底（SbSync.syncNow：漏掉的变更、autoStart 立即
 * 开始、计时状态跨设备镜像都在对账里收敛）。云端有更新 → 发广播让主界面刷新。
 * （v1.26.0：本服务原有的 Drive 桥 30 秒轮询支路随桥整套移除，只剩 Supabase。）
 */
public class SyncService extends Service {
    public static final String ACTION_SYNC_UPDATED = "com.ti.tasktimer.SYNC_UPDATED";
    private static final String CHANNEL_ID = "knowdo_sync";
    private static final int NOTIF_ID = 43;
    private static final long INTERVAL_MS = 30_000L;

    private HandlerThread thread;
    private Handler handler;
    private volatile boolean running = false;

    public static void start(Context c) {
        Intent i = new Intent(c, SyncService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, SyncService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIF_ID, buildNotification("Supabase 实时同步中：云端推送 + 每 30 秒对账"));
        thread = new HandlerThread("knowdo-sync");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            running = true;
            handler.post(loop);
        }
        return START_STICKY;
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try { tick(); } catch (Exception ignored) { }
            if (running) handler.postDelayed(this, INTERVAL_MS);
        }
    };

    private void tick() {
        SupaRealtime.ensure(this, rtListener);
        sbReconcile(null);
    }

    // ---------------- Realtime 推送 + 30 秒对账 ----------------
    private boolean sbFirst = true;

    private final SupaRealtime.Listener rtListener = new SupaRealtime.Listener() {
        @Override public void onTaskRow(org.json.JSONObject record, String type) {
            boolean changed = SbSync.applyRemoteTaskRow(SyncService.this, record, type);
            if (changed) {
                TimerState.logEvent(SyncService.this, "实时推送：云端任务有更新，已合并");
                sendBroadcast(new Intent(ACTION_SYNC_UPDATED).setPackage(getPackageName()));
            }
        }

        @Override public void onUserStateRow(org.json.JSONObject record) {
            SbSync.applyRemoteUserState(SyncService.this, record);
            sendBroadcast(new Intent(ACTION_SYNC_UPDATED).setPackage(getPackageName()));
        }

        @Override public void onJoined() {
            // (重)连成功：跑一次全量对账，补上断线期间漏掉的变更/autoStart/计时状态
            if (handler != null) handler.post(() -> {
                try { sbReconcile("实时推送已连接"); } catch (Exception ignored) { }
            });
        }
    };

    private void sbReconcile(String note) {
        TaskStore store = new TaskStore(this);
        String result = SbSync.syncNow(this, store);
        if ("同步中…".equals(result)) return;
        String text = note == null ? result : note + "：" + result;
        boolean changed = !text.equals(SyncManager.lastResult(this));
        SyncManager.setLastResult(this, text);
        if (!sbFirst && changed) {
            TimerState.logEvent(this, "实时同步：" + text);
            sendBroadcast(new Intent(ACTION_SYNC_UPDATED).setPackage(getPackageName()));
            updateNotification("最近同步：" + text);
        }
        sbFirst = false;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "实时同步", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("知行实时同步的常驻通知（无声）");
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("知行 KnowDo · 实时同步中")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        running = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (thread != null) thread.quitSafely();
        // Realtime 连接由本服务持有：服务销毁就断开，下次启动时 ensure 会重建
        SupaRealtime.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
