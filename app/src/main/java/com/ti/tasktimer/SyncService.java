package com.ti.tasktimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.io.InputStream;

/**
 * 实时同步服务：前台服务（低打扰常驻通知），每 30 秒检查一次 Drive 桥文件。
 * 文件有变化 → 合并进本地（SyncManager.syncNow）→ 发广播让主界面刷新；
 * 发现 autoStart=true 的新任务且当前没有计时在跑 → 立即开始它的倒计时，
 * 并把 autoStart 回写为 false（新时间戳，合并时胜出）防止重复启动。
 * 30 秒级轮询是 Drive 桥下能做到的近实时方案；真正的秒级推送需换 Firebase/FCM。
 */
public class SyncService extends Service {
    public static final String ACTION_SYNC_UPDATED = "com.ti.tasktimer.SYNC_UPDATED";
    private static final String CHANNEL_ID = "knowdo_sync";
    private static final int NOTIF_ID = 43;
    private static final long INTERVAL_MS = 30_000L;

    private HandlerThread thread;
    private Handler handler;
    private volatile boolean running = false;
    private int lastHash = Integer.MIN_VALUE;

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
        startForeground(NOTIF_ID, buildNotification("每 30 秒与 Drive 任务清单同步一次"));
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
        Uri uri = SyncManager.getUri(this);
        if (uri == null) return;
        String content;
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return;
            content = TaskStore.readAll(in);
        } catch (Exception e) {
            return; // 读不到（离线/Drive 未就绪）就等下一轮
        }
        int h = content.hashCode();
        if (h == lastHash) return;
        boolean first = (lastHash == Integer.MIN_VALUE);
        lastHash = h;
        TaskStore store = new TaskStore(this);
        String result = SyncManager.syncNow(this, store);
        SyncManager.setLastResult(this, result);
        if (!first) {
            TimerState.logEvent(this, "实时同步：" + result);
            sendBroadcast(new Intent(ACTION_SYNC_UPDATED).setPackage(getPackageName()));
            updateNotification("最近同步：" + result);
        }
        handleAutoStart(store);
    }

    private void handleAutoStart(TaskStore store) {
        if (TimerState.isRunning(this)) return; // 有计时在跑就不打断，标记留着，等空闲轮次再启动
        for (TaskItem t : store.tasks) {
            if (t.deleted || !t.autoStart) continue;
            if (SyncManager.autoStartHandled(this, t.id)) {
                // 已处理过但标记还在（桥端可能又写回旧版）：清掉标记防反复
                t.autoStart = false;
                t.touch();
                store.save();
                continue;
            }
            SyncManager.markAutoStartHandled(this, t.id);
            t.autoStart = false;
            t.touch();
            store.save();
            TimerService.startTimer(this, Math.max(1, t.durationMinutes) * 60, t.id, t.title);
            TimerState.logEvent(this, "实时同步：自动开始计时[" + t.title + "] " + t.durationMinutes + " 分钟");
            SyncManager.syncNow(this, store); // 把清掉的标记回写到桥
            lastHash = Integer.MIN_VALUE;    // 强制下一轮重新读桥确认
            sendBroadcast(new Intent(ACTION_SYNC_UPDATED).setPackage(getPackageName()));
            break; // 一次只启动一个
        }
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
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
