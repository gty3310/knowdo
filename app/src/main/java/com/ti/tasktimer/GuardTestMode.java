package com.ti.tasktimer;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 守卫测试模式（v1.21.0，用户验收工具）：设置里打开后，每 10 秒借无障碍通道
 * 截一帧、跑与正式守卫完全相同的分析管线（PipelineAnalyzer），把读数刷进
 * 专用悬浮窗（GuardTestWindow）：时间、前台应用、读到的文字摘要、判定结论、
 * 命中证据、本帧状态、同类连续命中计数。用户自己操控屏幕，看判定对不对。
 * 红线：测试路径只调分析层，绝不调用 VerdictEngine 的处置路径——不弹问询页、
 * 不跳回、不设宽限；连续命中计数是本类自维护的展示口径（语义与 VerdictEngine
 * 一致：同类连续命中计数、nsfw 一张即触发时第 1 张就算已确认）。
 * 与正式守卫互不干扰：正式守卫在计时中照常采样与处置，测试只多截自己的帧。
 */
public final class GuardTestMode {
    private static final long INTERVAL_MS = 10_000L;

    private static HandlerThread thread;
    private static Handler handler;
    /** 截图回调专用执行器：tick 在测试线程上阻塞等结果，回调绝不能投回同一线程（会死等）。 */
    private static final Executor SHOT_EXEC = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static volatile boolean running;
    private static Context appCtx;
    private static final PipelineAnalyzer ANALYZER = new PipelineAnalyzer();

    // 展示用连续命中计数（自维护，不读 VerdictEngine 的状态）
    private static String dispLastCat;
    private static int dispStreak;
    private static long dispHash;
    private static boolean dispHasHash;

    private GuardTestMode() {}

    public static boolean isRunning() { return running; }

    /** 开：起采样线程 + 显示悬浮窗。无障碍未就绪也先起，悬浮窗里会直接写明原因。 */
    public static synchronized void start(Context c) {
        appCtx = c.getApplicationContext();
        if (thread == null) {
            thread = new HandlerThread("knowdo-guard-test");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        GuardTestWindow.show(appCtx);
        if (running) return;
        running = true;
        dispLastCat = null;
        dispStreak = 0;
        dispHasHash = false;
        handler.post(loop);
    }

    public static synchronized void stop() {
        running = false;
        if (handler != null) handler.removeCallbacks(loop);
        GuardTestWindow.hide();
    }

    private static final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try { tick(); } catch (Exception ignored) { }
            if (running && handler != null) handler.postDelayed(this, INTERVAL_MS);
        }
    };

    private static void tick() {
        Context c = appCtx;
        if (c == null) return;
        if (!ScreenGuardPrefs.testMode(c)) { stop(); return; }
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());

        KnowDoAccessibilityService svc = KnowDoAccessibilityService.get();
        if (svc == null || !KnowDoAccessibilityService.isReady()) {
            GuardTestWindow.update(time + "\n状态：无障碍服务未开启\n"
                    + "先去 设置 → 权限体检 → 屏幕内容守卫，点「去开启」打开知行的无障碍服务");
            return;
        }
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        if (pm == null || !pm.isInteractive() || (kg != null && kg.isKeyguardLocked())) {
            GuardTestWindow.update(time + "\n状态：屏幕熄灭或已锁屏，本帧跳过");
            return;
        }

        // 借帧：回调在专用执行器上投递，测试线程在这里等结果（截图本身是异步的）
        final Bitmap[] box = new Bitmap[1];
        final int[] err = new int[]{Integer.MIN_VALUE};
        final boolean[] abandoned = new boolean[1];
        final CountDownLatch latch = new CountDownLatch(1);
        svc.captureFrame(SHOT_EXEC, new KnowDoAccessibilityService.ShotCallback() {
            @Override public void onBitmap(Bitmap bmp) {
                if (abandoned[0]) { bmp.recycle(); return; } // 已超时放弃：迟到的帧直接销毁
                box[0] = bmp;
                latch.countDown();
            }
            @Override public void onError(int errorCode) { err[0] = errorCode; latch.countDown(); }
        });
        boolean arrived;
        try { arrived = latch.await(9, TimeUnit.SECONDS); }
        catch (InterruptedException e) { abandoned[0] = true; return; }
        if (!arrived) {
            abandoned[0] = true;
            if (box[0] != null) { box[0].recycle(); box[0] = null; }
            GuardTestWindow.update(time + "\n状态：截图超时，本帧跳过");
            return;
        }

        String fg = svc.foregroundPackage();
        if (fg == null) fg = new UsageStatsDetector().foregroundPackage(c);
        String appLabel = fg == null ? "未知应用" : AppLister.labelOf(c, fg);

        if (err[0] != Integer.MIN_VALUE) {
            int code = err[0];
            if (code == android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                GuardTestWindow.update(time + " · " + appLabel + "\n状态：安全窗口不可见，不判定");
            } else {
                GuardTestWindow.update(time + " · " + appLabel + "\n状态：截图失败——"
                        + KnowDoAccessibilityService.errorText(code));
            }
            return;
        }
        Bitmap bmp = box[0];
        if (bmp == null) {
            GuardTestWindow.update(time + " · " + appLabel + "\n状态：截图失败——没有拿到画面");
            return;
        }
        try {
            if (FrameUtil.isBlackFrame(bmp)) {
                GuardTestWindow.update(time + " · " + appLabel + "\n状态：安全窗口不可见（黑帧），不判定");
                return;
            }
            long h = FrameUtil.aHash(bmp);
            if (dispHasHash && h == dispHash) {
                GuardTestWindow.update(time + " · " + appLabel + "\n状态：画面未变，跳过");
                return;
            }
            dispHash = h;
            dispHasHash = true;

            TaskItem task = currentTask(c);
            String nodeText = svc.collectVisibleText();
            ContentAnalyzer.Result r = ANALYZER.analyze(c, bmp, task, fg, nodeText);
            GuardTestWindow.update(render(c, time, appLabel, task, r));
        } finally {
            bmp.recycle(); // 与正式管线同一红线：原图只在内存、用完销毁
        }
    }

    private static String render(Context c, String time, String appLabel, TaskItem task,
                                 ContentAnalyzer.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append(time).append(" · ").append(appLabel).append('\n');
        sb.append("状态：已分析").append('\n');
        String text = ANALYZER.lastText == null ? "" : ANALYZER.lastText.replaceAll("\\s+", " ").trim();
        if (!text.isEmpty()) {
            sb.append("读到：").append(text.length() > 120 ? text.substring(0, 120) + "…" : text).append('\n');
        } else {
            sb.append("读到：（没有读到文字）").append('\n');
        }
        if (r == null || r.category == null) {
            dispLastCat = null;
            dispStreak = 0;
            boolean relevant = task != null && ANALYZER.relevantToTask(
                    ANALYZER.lastText == null ? "" : ANALYZER.lastText, task);
            sb.append("判定：不判分神").append(relevant ? "（与任务相关）" : "");
            return sb.toString();
        }
        // 展示用 streak：与 VerdictEngine 同口径——同类连续计数；nsfw 且设置一张即触发时第 1 张即确认
        if (r.category.equals(dispLastCat)) dispStreak++;
        else { dispLastCat = r.category; dispStreak = 1; }
        boolean oneShot = ContentAnalyzer.CAT_NSFW.equals(r.category) && ScreenGuardPrefs.nsfwOneShot(c);
        boolean confirmed = oneShot || dispStreak >= 2;
        sb.append("判定：分神嫌疑「").append(ContentAnalyzer.label(r.category)).append("」 置信 ")
                .append(String.format(Locale.US, "%.2f", r.confidence)).append('\n');
        if (r.detail != null && !r.detail.isEmpty()) {
            sb.append("证据：").append(r.detail).append('\n');
        }
        sb.append("同类连续命中：第 ").append(dispStreak).append(" 张")
                .append(confirmed ? "（已确认，正式模式会弹问询页）" : "（等下一张确认）");
        if (oneShot) sb.append("（黄色类一张即触发）");
        sb.append("\n测试模式只显示，不会真的弹窗");
        return sb.toString();
    }

    private static TaskItem currentTask(Context c) {
        if (!TimerState.isRunning(c)) return null;
        String id = TimerState.taskId(c);
        if (id == null || id.isEmpty()) return null;
        return new TaskStore(c).byId(id);
    }
}
