package com.ti.tasktimer;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * 屏幕内容守卫的采集层（前台服务，类型 mediaProjection）——v1.21.0 起是回退通道：
 * 首选通道是无障碍一次性截图（KnowDoAccessibilityService，采样宿主也在那边）；
 * 无障碍未开启时才回退到本服务的录屏通道，两条通道共用 PipelineAnalyzer 与
 * VerdictEngine，分析与判定只有一套，不许复制。
 * 仅在任务计时中 + 屏幕亮着 + 已解锁时工作；每次截完后随机隔 15–45 秒再截
 * （平均 30 秒，v1.21.0 采样加密）；连续帧画面未变（感知哈希相同）跳过分析省电。
 * 隐私红线：截图只在内存里分析（位图用完立刻 recycle），从不落盘、从不上传；
 * 判分神只留文字事件（GuardEvents）。带 FLAG_SECURE 的界面截出来是纯黑帧，
 * 只记“不可见”日志、不判分神（无痕/银行类界面是天然盲区）。
 * 授权约束（安卓 14+ 硬规定）：一份授权只够创建一次投影，所以每次计时开始
 * 由 MainActivity 请求一次授权；自动开始（autoStart）的计时没有授权就整场不采样。
 */
public class ScreenCaptureService extends Service {
    private static final String ACTION_START = "com.ti.tasktimer.CAPTURE_START";
    private static final String CHANNEL_ID = "knowdo_capture";
    private static final int NOTIF_ID = 44;
    private static final int MAX_WIDTH = 1080;

    private static MediaProjection projection;

    private HandlerThread thread;
    private Handler handler;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private int width, height;
    private volatile boolean running;
    private final Random random = new Random();
    private final ContentAnalyzer analyzer = new PipelineAnalyzer();
    private long lastInvisibleLogAt;
    private long lastHash;
    private boolean hasLastHash;

    // ---------- 对外入口（TimerService / PermActivity 调用） ----------
    /** 是否已有可用授权（投影活着，或有一份没用掉的授权）。 */
    public static boolean isAuthorized(Context c) {
        return projection != null || ProjectionAuth.has();
    }

    /**
     * 计时开始时调：通道分流——无障碍已就绪走无障碍通道（采样宿主在无障碍服务里）；
     * 否则回退录屏通道（守卫开 + 有授权才启动）；两边都没有只记一次日志（autoStart 场景）。
     */
    public static void onTimerStarted(Context c) {
        if (!ScreenGuardPrefs.enabled(c)) return;
        if (KnowDoAccessibilityService.isReady()) {
            KnowDoAccessibilityService.startGuardSampling();
            return;
        }
        if (!isAuthorized(c)) {
            VerdictEngine.noteNoAuthOnce(c);
            return;
        }
        Intent i = new Intent(c, ScreenCaptureService.class);
        i.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    /** 计时结束时调：两条通道都停（无障碍采样循环 + 本服务），并释放投影（下次计时重新授权）。 */
    public static void onTimerStopped(Context c) {
        KnowDoAccessibilityService.stopGuardSampling();
        c.stopService(new Intent(c, ScreenCaptureService.class));
        releaseProjection();
    }

    /** 无障碍通道中途接管时调：只停本服务（onDestroy 会释放投影），避免两通道同采。 */
    public static void stopServiceOnly(Context c) {
        c.stopService(new Intent(c, ScreenCaptureService.class));
    }

    private static void releaseProjection() {
        if (projection != null) {
            try { projection.stop(); } catch (Exception ignored) { }
            projection = null;
        }
    }

    // ---------- 服务生命周期 ----------
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        thread = new HandlerThread("knowdo-capture");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 安卓 14+ 要求：先以 mediaProjection 类型进前台，再创建投影与虚拟显示
        startForegroundWithType();
        if (projection == null) {
            Intent data = ProjectionAuth.takeData();
            if (data == null) {
                TimerState.logEvent(this, "屏幕内容守卫：采集服务启动时授权已失效，本次不采样");
                stopSelf();
                return START_NOT_STICKY;
            }
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mpm == null) { stopSelf(); return START_NOT_STICKY; }
            try {
                projection = mpm.getMediaProjection(ProjectionAuth.resultCode(), data);
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override public void onStop() {
                        projection = null;
                        stopCapture();
                    }
                }, handler);
            } catch (Exception e) {
                TimerState.logEvent(this, "屏幕内容守卫：创建截图投影失败（" + e.getClass().getSimpleName() + "）");
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        if (!setupDisplay()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            hasLastHash = false;
            handler.postDelayed(loop, 6_000L); // 首张稍等几秒，让用户先切到要用的应用
            TimerState.logEvent(this, "屏幕内容守卫：开始随机采样（录屏回退通道，15–45 秒一张）");
        }
        return START_STICKY;
    }

    private boolean setupDisplay() {
        if (virtualDisplay != null) return true;
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return false;
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        float scale = dm.widthPixels > MAX_WIDTH ? (float) MAX_WIDTH / dm.widthPixels : 1f;
        width = Math.max(2, Math.round(dm.widthPixels * scale));
        height = Math.max(2, Math.round(dm.heightPixels * scale));
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        try {
            virtualDisplay = projection.createVirtualDisplay("knowdo-guard",
                    width, height, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, handler);
        } catch (Exception e) {
            TimerState.logEvent(this, "屏幕内容守卫：创建虚拟显示失败（" + e.getClass().getSimpleName() + "）");
            return false;
        }
        return virtualDisplay != null;
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try { sampleOnce(); } catch (Exception ignored) { }
            if (running) handler.postDelayed(this, 15_000L + random.nextInt(30_001));
        }
    };

    private void sampleOnce() {
        if (!TimerState.isRunning(this)) { stopCapture(); return; }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (pm == null || !pm.isInteractive()) return;         // 熄屏不截
        if (kg != null && kg.isKeyguardLocked()) return;       // 锁屏不截

        Bitmap bmp = captureOnce();
        if (bmp == null) return;                               // 没有新帧（画面静止）就跳过
        try {
            if (FrameUtil.isBlackFrame(bmp)) {
                long now = System.currentTimeMillis();
                if (now - lastInvisibleLogAt > 5 * 60_000L) {
                    lastInvisibleLogAt = now;
                    TimerState.logEvent(this, "屏幕内容守卫：当前界面不可见（FLAG_SECURE 黑帧），不判定");
                }
                return;
            }
            long h = FrameUtil.aHash(bmp);
            if (hasLastHash && h == lastHash) return;          // 画面未变，跳过分析省电
            lastHash = h;
            hasLastHash = true;
            TaskItem task = currentTask();
            String fg = new UsageStatsDetector().foregroundPackage(this);
            ContentAnalyzer.Result r = analyzer.analyze(this, bmp, task, fg);
            VerdictEngine.onResult(this, r, task);
        } finally {
            bmp.recycle(); // 原图只活在内存里，分析完立刻销毁，从不落盘
        }
    }

    private Bitmap captureOnce() {
        if (reader == null) return null;
        Image img = null;
        long deadline = System.currentTimeMillis() + 1_200L;
        while (System.currentTimeMillis() < deadline) {
            img = reader.acquireLatestImage();
            if (img != null) break;
            try { Thread.sleep(60); } catch (InterruptedException e) { return null; }
        }
        if (img == null) return null;
        try {
            Image.Plane plane = img.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();
            int w = img.getWidth(), h = img.getHeight();
            Bitmap padded = Bitmap.createBitmap(rowStride / pixelStride, h, Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            Bitmap bmp = Bitmap.createBitmap(padded, 0, 0, w, h);
            if (bmp != padded) padded.recycle();
            return bmp;
        } catch (Exception e) {
            return null;
        } finally {
            img.close();
        }
    }

    private TaskItem currentTask() {
        String id = TimerState.taskId(this);
        if (id == null || id.isEmpty()) return null;
        return new TaskStore(this).byId(id);
    }

    private void stopCapture() {
        running = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (reader != null) { reader.close(); reader = null; }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (reader != null) { reader.close(); reader = null; }
        releaseProjection();
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "屏幕内容守卫", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("计时期间随机采样屏幕内容（仅本机分析）的常驻通知");
                nm.createNotificationChannel(ch);
            }
        }
    }

    private void startForegroundWithType() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification n = b.setContentTitle("知行 KnowDo · 屏幕内容守卫运行中")
                .setContentText("计时期间随机采样屏幕，只在本机分析，截图不保存、不上传")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }
}
