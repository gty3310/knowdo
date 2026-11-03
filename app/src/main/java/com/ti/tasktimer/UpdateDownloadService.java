package com.ti.tasktimer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * 软件更新后台下载（v1.53.0）：前台服务（dataSync 型）里跑 AppUpdate 的下载。
 *
 * 以前下载跑在裸线程上，软件一切到后台进程就被冻结/回收，连接被重置、
 * 整单作废（用户报障：切走再回来就见「更新没成：Connection reset」）。
 * 现在：检查决定要下载时 AppUpdate 把清单存好、启本服务；本服务先
 * startForeground 亮出进度通知（「正在下载知行 X · N%」，数据取 AppUpdate
 * 的 dl_done/dl_total，每秒刷新一次），再在工作线程里执行既有
 * downloadAndAssemble（分片跳过＋单片 Range 续传＋失败重试都在那侧）。
 * 下完：AppUpdate 在前台弹既有安装框、不在前台发「已下载好」通知；
 * 本服务撤进度通知并 stopSelf。最终失败同样撤通知停服务——失败态与
 * 自动续下的判断留在 AppUpdate 的状态里，下次打开软件会接着下。
 *
 * 重复启动（已在下载中再收到 start）直接忽略，不开第二个下载线程。
 */
public class UpdateDownloadService extends Service {

    private static volatile Thread worker;
    private static volatile boolean polling;

    /** 启服务下载；系统不让起（后台启动受限等）时返回 false，调用方回落裸线程兜底。 */
    public static boolean start(Context c) {
        try {
            Intent i = new Intent(c, UpdateDownloadService.class);
            c.startForegroundService(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AlertHelper.createChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (worker != null && worker.isAlive()) {
            return START_NOT_STICKY; // 已在下载中，不重复启
        }
        final Context app = getApplicationContext();
        final AppUpdate.Manifest m = AppUpdate.storedManifest(app);
        startInForeground(buildNotification(app));
        if (m == null) {
            // 没有清单就没什么可下：清掉可能悬着的 working 标记后停服务
            AppUpdate.clearWorking();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        polling = true;
        Thread poller = new Thread(() -> {
            while (polling) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                if (!polling) return;
                if (AppUpdate.state(app) == AppUpdate.ST_DOWNLOADING) {
                    NotificationManager nm =
                            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) nm.notify(AlertHelper.NOTIF_UPDATE_DL, buildNotification(app));
                }
            }
        });
        poller.setDaemon(true);
        poller.start();
        worker = new Thread(() -> {
            try {
                AppUpdate.runDownload(app, m);
            } finally {
                polling = false;
                stopForeground(true);
                stopSelf();
            }
        });
        worker.start();
        return START_NOT_STICKY;
    }

    private void startInForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(AlertHelper.NOTIF_UPDATE_DL, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(AlertHelper.NOTIF_UPDATE_DL, n);
        }
    }

    private Notification buildNotification(Context c) {
        boolean en = Strings.en();
        String name = AppUpdate.pendingName(c);
        long total = AppUpdate.dlTotal(c);
        long done = AppUpdate.dlDone(c);
        int pct = total > 0 ? (int) (done * 100 / total) : 0;
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, AlertHelper.NOTIF_UPDATE_DL, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, AlertHelper.CHANNEL_UPDATE_DL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(en ? "KnowDo KnowDo" : "知行 KnowDo")
                .setContentText(en ? "Downloading KnowDo " + name + " · " + pct + "%"
                        : "正在下载知行 " + name + " · " + pct + "%")
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (total > 0) b.setProgress(100, pct, false);
        return b.build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
