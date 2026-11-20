package com.ti.tasktimer;

import android.app.KeyguardManager;
import android.content.Context;
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
        request(c, KEY_BLOCK, PRI_BLOCK, new BlockOverlay());
        return true;
    }

    public static void dismissBlock(Context c) {
        if (isWanted(KEY_BLOCK)) dismiss(c, KEY_BLOCK);
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
