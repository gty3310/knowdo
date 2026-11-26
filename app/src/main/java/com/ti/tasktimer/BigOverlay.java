package com.ti.tasktimer;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用 99% 超大悬浮窗壳（Guild Issue #6 第一批）：把原 PauseLock 遮罩的
 * 大窗机制（{@link FloatingWindow} 的通用内容盒）泛化给所有全屏提醒页。
 *
 * 壳负责：99% 尺寸、不可关/拖/划、触摸不穿透（窗口层 FloatingWindow 保证）、
 * 通话中缩小挂断恢复（poll 里让位，仿 PauseLock.poll）、同时只一个大窗的
 * 优先级排队。优先级：暂停遮罩（PauseLock，不经本壳、窗口层直接抢占）
 * ＞ 盖屏 PRI_BLOCK ＞ 空档催促 PRI_IDLE ＞ 后续页（更低值）。
 * 低优先级先到先排队，高优先级到时抢占在显的，低优先级等高优先级
 * 收回后由 poll 自动复出。锁屏/无悬浮权限/悬浮窗被关时本壳不出窗，
 * 调用方走原全屏通知＋Activity 降级路径（与 PauseLock 同口径）。
 *
 * 内容方只提供 Content：createView 每次出窗现建（被抢占收回后视图已清，
 * 复出时重建，保证数据新鲜）；tick 每秒一次（主线程，FloatService 节拍），
 * 返回 false 表示内容已失效、壳自动收回；onShown/onDismissed 做各自登记。
 * 状态只存进程内静态量（与 TopPages 同口径）：进程被杀后各页的持久化
 * 状态（AppBlock prefs、IdleAlarm 排期）会重新触发请求，不丢。
 */
public final class BigOverlay {
    public static final String KEY_BLOCK = "block";
    public static final String KEY_IDLE = "idle";
    public static final String KEY_CHECK = "check";
    public static final String KEY_ALARM = "alarm";
    public static final String KEY_RESUME = "resume";
    public static final String KEY_START = "start";
    public static final String KEY_THANKS = "thanks";
    public static final String KEY_GUARD = "guard";

    public static final int PRI_CHECK = 40;
    public static final int PRI_ALARM = 35;
    // 守卫问询（v1.97.0）：与盖屏/叫回同级 30——问询是守卫处置链上「必须先答」
    // 的一环，与盖屏同属一个量级；同级不互相抢占（壳只让更高优先级抢占），
    // 先到先显、后到排队，当前一环被用户处理完即递补，不会出现两页互抢闪烁
    public static final int PRI_GUARD = 30;
    public static final int PRI_RESUME = 30;
    public static final int PRI_START = 25;
    public static final int PRI_THANKS = 10;
    public static final int PRI_BLOCK = 30;
    public static final int PRI_IDLE = 20;

    public interface Content {
        /** 每次出窗时现建内容视图（壳负责装入通用内容盒并撑满）。 */
        View createView(Context c);
        /** 出窗成功后调一次。 */
        void onShown(Context c);
        /** 每秒调一次（主线程）；返回 false＝内容已失效，壳自动收回。 */
        boolean tick(Context c);
        /** 被收回后调一次（用户处理完／失效／外部清状态都算）。 */
        void onDismissed(Context c);
    }

    private static final class Entry {
        final int pri;
        final Content content;
        Entry(int pri, Content content) { this.pri = pri; this.content = content; }
    }

    private static final Map<String, Entry> PENDING = new LinkedHashMap<>();
    private static String showingKey;
    private static boolean shrunkForCall;

    private BigOverlay() {}

    // ---------------- 对外入口 ----------------

    /** 请求出窗：能立刻出就立刻出，否则排队（同 key 重复请求＝换新内容）。 */
    public static void request(Context c, String key, int pri, Content content) {
        if (c == null || key == null || content == null) return;
        CrashReporter.crumb("overlay:" + key);
        boolean reshowing = key.equals(showingKey) && FloatingWindow.isOverlayShowing();
        PENDING.put(key, new Entry(pri, content));
        if (reshowing) {
            // 同一页再次触发（如空档下一轮）：直接换新内容并对齐一次
            if (FloatingWindow.showOverlay(c, content.createView(c))) content.tick(c);
            return;
        }
        tryShow(c);
    }

    public static boolean isWanted(String key) { return PENDING.containsKey(key); }

    public static boolean isShowing(String key) {
        return key != null && key.equals(showingKey) && FloatingWindow.isOverlayShowing();
    }

    /** 收回指定大窗（在显的收视觉、排队的摘除），随后递补下一个待办。 */
    public static void dismiss(Context c, String key) {
        dismissInternal(c, key, true);
    }

    /**
     * 收回但暂不递补：调用方紧接着要拉起 Activity（如盖屏申诉转原页），
     * 递补留给 poll——届时原页多半已把悬浮窗抑制住，不会叠窗。
     */
    public static void dismissQuietly(Context c, String key) {
        dismissInternal(c, key, false);
    }

    private static void dismissInternal(Context c, String key, boolean promote) {
        if (key == null) return;
        CrashReporter.crumb("overlay_dismiss:" + key);
        Entry e = PENDING.remove(key);
        if (key.equals(showingKey)) {
            showingKey = null;
            shrunkForCall = false;
            FloatingWindow.retractOverlay(c);
        }
        if (e != null) e.content.onDismissed(c);
        if (promote) tryShow(c);
    }

    // ---------------- 具体页的便捷入口 ----------------

    /**
     * 盖屏大窗（AlertHelper.fireBlock 在亮屏未锁＋有悬浮权限时调）：
     * 返回 true＝已由大窗接管（已出窗或已排队）；false＝调用方走原 Activity 路径。
     */
    public static boolean requestBlock(Context c) {
        if (!FloatingWindow.enabled(c)) return false;
        // 前台检测链会随被盖软件的窗口事件反复调到这里：已在列就不重建内容，
        // 倒计时靠 tick 每秒刷新；被收回后（用户切走）下次请求自然是新一份
        if (!isWanted(KEY_BLOCK)) request(c, KEY_BLOCK, PRI_BLOCK, new BlockOverlay());
        return true;
    }

    public static void dismissBlock(Context c) {
        if (isWanted(KEY_BLOCK)) dismiss(c, KEY_BLOCK);
    }

    /**
     * 空档催促大窗（IdleAlarm.onAlarmFired 调）：出窗条件与原直拉页同口径
     * （亮屏、未锁、有悬浮权限、悬浮窗开着）。返回 false＝调用方走原 Activity。
     * 已在列时不重建内容（舞台小字靠 tick 刷新，任务列表本轮内不变）。
     */
    public static boolean requestIdle(Context c) {
        if (!FloatingWindow.enabled(c)) return false;
        if (!AlertHelper.canDrawOverlays(c)) return false;
        if (!screenUsable(c)) return false;
        AlertHelper.cancelNextTask(c); // 与 IdleAlarmActivity.onCreate 同一步
        if (!isWanted(KEY_IDLE)) request(c, KEY_IDLE, PRI_IDLE, new IdleOverlay());
        return true;
    }

    public static void dismissIdle(Context c) {
        if (isWanted(KEY_IDLE)) dismiss(c, KEY_IDLE);
    }

    private static CheckOverlay sCheck;

    /**
     * 检查页大窗（AlertHelper.fire 在亮屏未锁＋有悬浮权限时调）：返回 true＝已由
     * 大窗接管（已出窗或已排队）；false＝调用方走原 Activity 路径（悬浮窗关着，
     * 或本应用页面正在前台——原页本来就是直接盖在本应用页上，保持原行为）。
     * 内容对象全仓单例复用：同一时刻只可能有一张检查页，新触发先重置状态
     * （等价原页新开一个实例），阶段状态由内容对象持有（动机门/原则问询）。
     */
    public static boolean requestCheck(Context c, int type) {
        if (!FloatingWindow.enabled(c)) return false;
        if (KnowDoApp.ownActivityVisible()) return false;
        if (sCheck == null) sCheck = new CheckOverlay();
        sCheck.prepare(type);
        request(c, KEY_CHECK, PRI_CHECK, sCheck);
        return true;
    }

    public static void dismissCheck(Context c) {
        if (isWanted(KEY_CHECK)) dismiss(c, KEY_CHECK);
    }

    private static AlarmOverlay sAlarm;

    /**
     * 任务闹钟大窗（AlertHelper.fireTaskAlarm 在亮屏未锁＋有悬浮权限时调）：
     * 返回口径与 requestCheck 相同（false＝走原 Activity 路径）。内容对象
     * 单例复用，prepare 时停掉上一轮可能还在响的播放器并重置状态。
     */
    public static boolean requestAlarm(Context c, String taskId, String title, boolean thenCheck) {
        if (!FloatingWindow.enabled(c)) return false;
        if (KnowDoApp.ownActivityVisible()) return false;
        if (sAlarm == null) sAlarm = new AlarmOverlay();
        sAlarm.prepare(taskId, title, thenCheck);
        request(c, KEY_ALARM, PRI_ALARM, sAlarm);
        return true;
    }

    public static void dismissAlarm(Context c) {
        if (isWanted(KEY_ALARM)) dismiss(c, KEY_ALARM);
    }

    /**
     * 叫回大窗（ResumeReceiver 在亮屏未锁＋有悬浮权限时调）：返回口径与
     * requestCheck 相同（false＝走原 Activity 路径）。每次触发新建内容
     * （无常驻资源，阶段状态在内容对象内）。
     */
    public static boolean requestResume(Context c) {
        if (!FloatingWindow.enabled(c)) return false;
        if (KnowDoApp.ownActivityVisible()) return false;
        request(c, KEY_RESUME, PRI_RESUME, new ResumeOverlay());
        return true;
    }

    public static void dismissResume(Context c) {
        if (isWanted(KEY_RESUME)) dismiss(c, KEY_RESUME);
    }

    /**
     * 开始提醒大窗（StartReceiver 在亮屏未锁＋有悬浮权限时调）：返回口径
     * 与 requestCheck 相同（false＝走原 Activity 路径）。任务 id 随请求
     * 传入，内容对象每次新建（无常驻资源）。
     */
    public static boolean requestStart(Context c, String taskId) {
        if (!FloatingWindow.enabled(c)) return false;
        if (KnowDoApp.ownActivityVisible()) return false;
        request(c, KEY_START, PRI_START, new StartOverlay(taskId));
        return true;
    }

    public static void dismissStart(Context c) {
        if (isWanted(KEY_START)) dismiss(c, KEY_START);
    }

    /**
     * 致谢大窗（AlertHelper.fireThanks 在亮屏未锁＋有悬浮权限时调）：返回
     * 口径与 requestCheck 相同（false＝走原 Activity 路径）。路人静音的
     * 待致谢状态在内容 onShown 时才消费（呈现即消费，与原页同）。
     */
    public static boolean requestThanks(Context c) {
        if (!FloatingWindow.enabled(c)) return false;
        if (KnowDoApp.ownActivityVisible()) return false;
        request(c, KEY_THANKS, PRI_THANKS, new ThanksOverlay());
        return true;
    }

    public static void dismissThanks(Context c) {
        if (isWanted(KEY_THANKS)) dismiss(c, KEY_THANKS);
    }

    private static GuardOverlay sGuard;

    /**
     * 守卫问询大窗（AlertHelper.fireGuard / fireGuardContent 在亮屏未锁＋有
     * 悬浮权限时调）：返回 true＝已由大窗接管（已出窗或已排队）；false＝
     * 调用方走原 GuardActivity 路径（悬浮窗关着，或本应用页面正在前台——
     * 自家页面在前台时引擎本就豁免，偶发触发保持原页行为）。内容对象单例
     * 复用：同一时刻只可能有一张问询页，新触发先 prepare 换判定上下文
     * （等价原页新开一个实例）。
     *
     * 触发侧（VerdictEngine.trigger / FocusGuard.tick）在弹问询前刚调了
     * FloatingWindow.setSuppressed(true) 给原页让位；大窗接管时窗口层就是
     * 大窗本身，抑制不解除壳永远出不了窗——故接管路径在这里解除抑制。
     * 未接管（返回 false）时抑制原样保留，原页 onResume 会再置一次。
     */
    public static boolean requestGuard(Context c, boolean contentMode, String pkg,
                                       String category, String detail, String reason) {
        if (!FloatingWindow.enabled(c)) return false;
        if (KnowDoApp.ownActivityVisible()) return false;
        // 线程纪律（v1.98.1 修复）：触发侧在无障碍后台线程上，接管动作会同步
        // 重建整个悬浮窗视图层级——视图层级只能归属主线程，否则主线程下一拍
        // refresh 的 setText 必抛 CalledFromWrongThreadException（v1.97.0
        // 反复崩溃主因）。上面两个前置判断都是线程安全的轻读（prefs／静态
        // 计数），在调用线程先行；不成立照旧返回 false 走原页。成立且不在
        // 主线程时，把接管整段 post 到主线程再执行、照常返回 true（已接管）。
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final Context app = c.getApplicationContext();
            new Handler(Looper.getMainLooper()).post(
                    () -> requestGuard(app, contentMode, pkg, category, detail, reason));
            return true;
        }
        if (sGuard == null) sGuard = new GuardOverlay();
        sGuard.prepare(contentMode, pkg, category, detail, reason);
        FloatingWindow.setSuppressed(false);
        request(c, KEY_GUARD, PRI_GUARD, sGuard);
        return true;
    }

    public static void dismissGuard(Context c) {
        if (isWanted(KEY_GUARD)) dismiss(c, KEY_GUARD);
    }

    // ---------------- 每秒节拍（FloatService 调） ----------------

    public static void poll(Context c) {
        if (PENDING.isEmpty()) { showingKey = null; shrunkForCall = false; return; }
        if (showingKey != null) {
            Entry e = PENDING.get(showingKey);
            if (e == null) {
                showingKey = null;
                shrunkForCall = false;
            } else if (FloatingWindow.isLockShowing()) {
                // 暂停遮罩抢占中：不 tick、不复原，等它收回
                return;
            } else if (GuardDispatch.inCall(c)) {
                if (FloatingWindow.isOverlayShowing()) {
                    FloatingWindow.hideOverlayVisual();
                    shrunkForCall = true;
                }
                return;
            } else {
                if (shrunkForCall || !FloatingWindow.isOverlayShowing()) {
                    // 通话结束复原；或被暂停遮罩抢占后复出（盒内容可能已被清，重建）
                    if (!FloatingWindow.resumeOverlayVisual(c)
                            && !FloatingWindow.showOverlay(c, e.content.createView(c))) {
                        return; // 这一拍出不了窗（抑制中/无权限），下一拍再试
                    }
                    shrunkForCall = false;
                }
                if (!e.content.tick(c)) {
                    String k = showingKey;
                    if (k != null && k.equals(showingKey)) dismiss(c, k);
                    return;
                }
            }
        }
        // 抢占：待办里有更高优先级的，换下在显的（被换下的待办保留，之后自动复出）
        if (showingKey != null && FloatingWindow.isOverlayShowing()) {
            String bk = bestKey();
            Entry cur = PENDING.get(showingKey);
            if (bk != null && !bk.equals(showingKey) && cur != null
                    && PENDING.get(bk).pri > cur.pri) {
                FloatingWindow.retractOverlay(c);
                showingKey = null;
                shrunkForCall = false;
            }
        }
        tryShow(c);
    }

    // ---------------- 内部 ----------------

    private static void tryShow(Context c) {
        if (showingKey != null) return;
        if (!canShowNow(c)) return;
        String bk = bestKey();
        if (bk == null) return;
        Entry e = PENDING.get(bk);
        if (e == null) return;
        if (FloatingWindow.showOverlay(c, e.content.createView(c))) {
            showingKey = bk;
            shrunkForCall = false;
            e.content.onShown(c);
            e.content.tick(c); // 立刻对齐一次（如盖屏倒计时），不等下一拍
        }
    }

    private static String bestKey() {
        String best = null;
        int bestPri = Integer.MIN_VALUE;
        for (Map.Entry<String, Entry> en : PENDING.entrySet()) {
            if (en.getValue().pri > bestPri) {
                bestPri = en.getValue().pri;
                best = en.getKey();
            }
        }
        return best;
    }

    private static boolean canShowNow(Context c) {
        if (!FloatingWindow.enabled(c)) return false;
        if (!AlertHelper.canDrawOverlays(c)) return false;
        if (FloatingWindow.isSuppressed()) return false; // 守卫/盖屏原页在前，让位
        if (FloatingWindow.isLockShowing()) return false; // 暂停遮罩在显
        if (KnowDoApp.ownActivityVisible()) return false; // 本应用页面在前台，不叠窗（仿 GuardDispatch 口径）
        return screenUsable(c);
    }

    /** 亮屏且未锁：锁屏上悬浮窗看不见，锁屏一律走原全屏通知＋Activity。 */
    private static boolean screenUsable(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = kg != null && kg.isKeyguardLocked();
        return interactive && !locked;
    }
}
