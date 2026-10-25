package com.ti.tasktimer;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Random;
import java.util.concurrent.Executor;

/**
 * 屏幕内容守卫的新截图通道（v1.21.0）：无障碍一次性截图，替代 MediaProjection。
 * 为什么换：录屏授权一份只够一次（安卓 14+）、锁屏即停、进程被杀不可自愈；
 * 无障碍服务由系统托管，进程被杀后系统会重新绑定续跑，且 takeScreenshot()
 * 是一次性截图，不需要每次计时重新授权（研究结论见仓库研究记录）。
 * 本服务同时是正式守卫的采样宿主：守卫开 + 计时中时，采样循环跑在这里
 * （15–45 秒随机一张，平均 30 秒）；无障碍未开启时 ScreenCaptureService
 * 回退到旧的录屏通道，两条通道共用 PipelineAnalyzer 与 VerdictEngine。
 * 隐私红线不变：截图只在内存里分析、分析完立刻 recycle，从不落盘、从不上传。
 * 另提供节点文字读取（getRootInActiveWindow）给管线做零成本预筛，
 * 以及 captureFrame 给守卫测试模式借帧（测试模式只显示、从不处置）。
 */
public class KnowDoAccessibilityService extends AccessibilityService {
    /** 通用失败哨兵：公开 SDK 没有 ERROR_TAKE_SCREENSHOT_FAILED 常量，内部异常用它回报。 */
    public static final int ERR_GENERIC = -2;

    private static volatile KnowDoAccessibilityService instance;

    private HandlerThread thread;
    private Handler handler;
    private Executor handlerExecutor;
    private final Random random = new Random();
    private final PipelineAnalyzer analyzer = new PipelineAnalyzer();
    private volatile boolean guardActive;
    private boolean guardRetryUsed;
    private long lastHash;
    private boolean hasLastHash;
    private long lastInvisibleLogAt;
    private boolean noAccessLogged;
    private volatile String lastFgPkg;

    // ---------- 对外状态 ----------
    /** 服务已绑定、可立即截图（takeScreenshot 需 API 30+）。 */
    public static boolean isReady() {
        return instance != null && Build.VERSION.SDK_INT >= 30;
    }

    /** 系统设置里本服务是否处于开启状态（已绑定或已登记）。 */
    public static boolean isEnabled(Context c) {
        if (instance != null) return true;
        try {
            String enabled = Settings.Secure.getString(
                    c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String flat = new ComponentName(c, KnowDoAccessibilityService.class).flattenToString();
            for (String s : enabled.split(":")) {
                if (flat.equalsIgnoreCase(s.trim())) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    public static KnowDoAccessibilityService get() { return instance; }

    // ---------- 生命周期 ----------
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        thread = new HandlerThread("knowdo-a11y-guard");
        thread.start();
        handler = new Handler(thread.getLooper());
        handlerExecutor = r -> handler.post(r);
        // 绑定时若守卫开着且正在计时（进程被杀后系统重绑、或计时中途刚开启无障碍），
        // 无缝续上采样，并停掉可能在跑的录屏回退通道，避免两通道同采
        if (ScreenGuardPrefs.enabled(this) && TimerState.isRunning(this)) {
            ScreenCaptureService.stopServiceOnly(this);
            startGuardSampling();
        }
        // 测试模式开关还开着的话，把测试采样也续上
        if (ScreenGuardPrefs.testMode(this)) {
            GuardTestMode.start(this);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        guardActive = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        if (thread != null) { thread.quitSafely(); thread = null; }
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName() != null) {
            lastFgPkg = event.getPackageName().toString();
        }
    }

    @Override
    public void onInterrupt() { }

    /** 最近一次窗口切换事件的前台包（测试模式显示应用名用；拿不到时回退节点根包名）。 */
    public String foregroundPackage() {
        if (lastFgPkg != null) return lastFgPkg;
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                return root.getPackageName().toString();
            }
        } catch (Exception ignored) {
        } finally {
            if (root != null) root.recycle();
        }
        return null;
    }

    // ---------- 一次性截图 ----------
    public interface ShotCallback {
        /** 成功：软件位图（ARGB_8888），用完由调用方 recycle。 */
        void onBitmap(Bitmap bmp);
        /** 失败：errorCode 为系统 ERROR_TAKE_SCREENSHOT_* 之一（或 -1 表示版本过低）。 */
        void onError(int errorCode);
    }

    /**
     * 截一帧：HardwareBuffer 必须 wrap 后立刻拷贝成软件位图再关闭——
     * ML Kit 与 ONNX 读不了硬件位图，buffer 本身也只许短暂持有。
     * 回调在 executor 指定的线程上执行。
     */
    public void captureFrame(Executor executor, ShotCallback cb) {
        if (Build.VERSION.SDK_INT < 30) {
            executor.execute(() -> cb.onError(-1));
            return;
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, executor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    Bitmap copy = null;
                    HardwareBuffer hb = result.getHardwareBuffer();
                    if (hb != null) {
                        Bitmap wrapped = null;
                        try {
                            ColorSpace cs = result.getColorSpace();
                            wrapped = cs != null
                                    ? Bitmap.wrapHardwareBuffer(hb, cs)
                                    : Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB));
                            if (wrapped != null) {
                                copy = wrapped.copy(Bitmap.Config.ARGB_8888, false);
                            }
                        } catch (Exception ignored) {
                        } finally {
                            if (wrapped != null) wrapped.recycle();
                            try { hb.close(); } catch (Exception ignored) { }
                        }
                    }
                    if (copy != null) cb.onBitmap(copy);
                    else cb.onError(ERR_GENERIC);
                }

                @Override
                public void onFailure(int errorCode) {
                    cb.onError(errorCode);
                }
            });
        } catch (Exception e) {
            executor.execute(() -> cb.onError(ERR_GENERIC));
        }
    }

    /** 失败码的人话说明（测试模式显示用）。 */
    public static String errorText(int errorCode) {
        if (errorCode == ERR_GENERIC) return "截图失败";
        if (errorCode == -1) return "系统版本低于安卓 11，无障碍截图不可用";
        if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) return "截图太频繁被系统限流";
        if (errorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) return "安全窗口不可见";
        if (errorCode == ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS) return "无障碍截图能力不可用（服务可能未完整开启）";
        if (errorCode == ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY) return "显示无效";
        if (errorCode == ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR) return "系统内部错误";
        return "截图失败（错误码 " + errorCode + "）";
    }

    // ---------- 节点文字（零成本预筛） ----------
    /** 收集当前窗口可见文字（节点 text + contentDescription），上限约 6000 字；拿不到返回空串。 */
    public String collectVisibleText() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) return "";
            StringBuilder sb = new StringBuilder();
            ArrayDeque<AccessibilityNodeInfo> stack = new ArrayDeque<>();
            stack.push(root);
            int visited = 0;
            while (!stack.isEmpty() && sb.length() < 6000 && visited < 3000) {
                AccessibilityNodeInfo node = stack.pop();
                visited++;
                try {
                    CharSequence t = node.getText();
                    if (t == null || t.length() == 0) t = node.getContentDescription();
                    if (t != null && t.length() > 0) sb.append(t).append('\n');
                    for (int i = node.getChildCount() - 1; i >= 0; i--) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) stack.push(child);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (node != root) node.recycle();
                }
            }
            while (!stack.isEmpty()) {
                AccessibilityNodeInfo n = stack.pop();
                if (n != root) n.recycle();
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (root != null) root.recycle();
        }
    }

    // ---------- 正式守卫采样（宿主在本服务内） ----------
    /** 计时开始时由 ScreenCaptureService 分流调用：无障碍就绪就走本通道。 */
    public static void startGuardSampling() {
        KnowDoAccessibilityService s = instance;
        if (s != null) s.startLoop();
    }

    public static void stopGuardSampling() {
        KnowDoAccessibilityService s = instance;
        if (s != null) s.stopLoop();
    }

    private void startLoop() {
        if (handler == null || guardActive) return;
        guardActive = true;
        hasLastHash = false;
        noAccessLogged = false;
        handler.postDelayed(guardLoop, 6_000L); // 首张稍等几秒，让用户先切到要用的应用
        TimerState.logEvent(this, "屏幕内容守卫：开始随机采样（无障碍通道，15–45 秒一张）");
    }

    private void stopLoop() {
        guardActive = false;
        if (handler != null) handler.removeCallbacks(guardLoop);
    }

    private final Runnable guardLoop = new Runnable() {
        @Override public void run() {
            if (!guardActive) return;
            try { sampleOnce(false); } catch (Exception ignored) { }
            if (guardActive) handler.postDelayed(this, 15_000L + random.nextInt(30_001));
        }
    };

    private void sampleOnce(boolean isRetry) {
        if (!TimerState.isRunning(this)) { stopLoop(); return; }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (pm == null || !pm.isInteractive()) return;         // 熄屏不截
        if (kg != null && kg.isKeyguardLocked()) return;       // 锁屏不截
        if (handler == null) return;
        guardRetryUsed = isRetry;
        captureFrame(handlerExecutor, new ShotCallback() {
            @Override public void onBitmap(Bitmap bmp) { onFrame(bmp); }

            @Override public void onError(int errorCode) { onShotError(errorCode); }
        });
    }

    private void onFrame(Bitmap bmp) {
        try {
            if (FrameUtil.isBlackFrame(bmp)) {
                noteInvisible();
                return;
            }
            long h = FrameUtil.aHash(bmp);
            if (hasLastHash && h == lastHash) return; // 画面未变，跳过分析省电
            lastHash = h;
            hasLastHash = true;
            TaskItem task = currentTask();
            String fg = new UsageStatsDetector().foregroundPackage(this);
            if (fg == null) fg = foregroundPackage();
            String nodeText = collectVisibleText();
            ContentAnalyzer.Result r = analyzer.analyze(this, bmp, task, fg, nodeText);
            VerdictEngine.onResult(this, r, task);
        } finally {
            bmp.recycle(); // 原图只活在内存里，分析完立刻销毁，从不落盘
        }
    }

    private void onShotError(int errorCode) {
        if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
            // 限流：退避一次重试，还不行就等下一轮采样周期
            if (!guardRetryUsed && guardActive && handler != null) {
                handler.postDelayed(() -> { if (guardActive) sampleOnce(true); }, 1_500L);
            }
            return;
        }
        if (errorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
            noteInvisible();
            return;
        }
        if (errorCode == ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS) {
            if (!noAccessLogged) {
                noAccessLogged = true;
                TimerState.logEvent(this, "屏幕内容守卫：无障碍截图能力不可用，本次计时停止采样");
            }
            stopLoop();
        }
    }

    private void noteInvisible() {
        long now = System.currentTimeMillis();
        if (now - lastInvisibleLogAt > 5 * 60_000L) {
            lastInvisibleLogAt = now;
            TimerState.logEvent(this, "屏幕内容守卫：当前界面不可见（安全窗口），不判定");
        }
    }

    private TaskItem currentTask() {
        String id = TimerState.taskId(this);
        if (id == null || id.isEmpty()) return null;
        return new TaskStore(this).byId(id);
    }
}
