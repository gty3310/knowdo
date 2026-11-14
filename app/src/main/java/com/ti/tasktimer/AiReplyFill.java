package com.ti.tasktimer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * 「给 AI 回复」的投递（v1.85.0）：把一段现成文字送进目标 AI 软件——
 * 1) 先复制到剪贴板（兜底：自动填入失败时用户长按粘贴即可）；
 * 2) 登记一笔「待填」（目标包名＋文字＋30 秒有效期，存 prefs）；
 * 3) 拉起目标软件；知行无障碍服务在目标软件窗口出现时把文字填入其
 *    输入框（KnowDoAccessibilityService.tryFillPending），填好即销账。
 * 发送钮永不自动点：填好后停在对方界面，用户扫一眼自己点发送。
 * 目标软件现只适配 Muse App（包名见 DEFAULT_PKG）；目标包名是配置项
 * （prefs 键 pkg），以后加别的 AI 软件只改配置、不动这条流程。
 */
public final class AiReplyFill {

    /** 现唯一适配的目标：Muse App。 */
    public static final String DEFAULT_PKG = "com.facebook.aura";

    private static final String PREF = "ai_reply_fill";
    private static final String KEY_PKG_CONF = "pkg";
    private static final String KEY_P_PKG = "pending_pkg";
    private static final String KEY_P_TEXT = "pending_text";
    private static final String KEY_P_UNTIL = "pending_until";
    private static final long WINDOW_MS = 30_000L;

    private AiReplyFill() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 目标 AI 软件包名（配置项，缺省 Muse App）。 */
    public static String targetPackage(Context c) {
        String p = prefs(c).getString(KEY_PKG_CONF, "");
        return p == null || p.isEmpty() ? DEFAULT_PKG : p;
    }

    /** 改目标软件（配置口；本版没有设置界面入口，留给以后加软件时接）。 */
    public static void setTargetPackage(Context c, String pkg) {
        prefs(c).edit().putString(KEY_PKG_CONF, pkg == null ? "" : pkg).apply();
    }

    /**
     * 投递一段文字：复制兜底＋登记待填＋拉起目标软件。
     * 必须由前台 Activity 的 Context 调用（剪贴板写入要前台身份）。
     * 返回 false＝目标软件没装/拉不起来（文字已在剪贴板，待填已撤销）。
     */
    public static boolean send(Context c, String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("知行", text));
        } catch (Exception ignored) { }
        String pkg = targetPackage(c);
        prefs(c).edit()
                .putString(KEY_P_PKG, pkg)
                .putString(KEY_P_TEXT, text)
                .putLong(KEY_P_UNTIL, System.currentTimeMillis() + WINDOW_MS)
                .apply();
        Intent launch = null;
        try { launch = c.getPackageManager().getLaunchIntentForPackage(pkg); }
        catch (Exception ignored) { }
        if (launch == null) {
            clearPending(c);
            return false;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(launch);
            return true;
        } catch (Exception e) {
            clearPending(c);
            return false;
        }
    }

    // ---------------- 待填账本（无障碍服务侧读写） ----------------

    public static String pendingPkg(Context c) {
        return prefs(c).getString(KEY_P_PKG, "");
    }

    /** 待填文字；无待填或已过有效期返回 null（过期顺手销账）。 */
    public static String pendingText(Context c) {
        SharedPreferences sp = prefs(c);
        String t = sp.getString(KEY_P_TEXT, null);
        if (t == null || t.isEmpty()) return null;
        if (System.currentTimeMillis() > sp.getLong(KEY_P_UNTIL, 0)) {
            clearPending(c);
            return null;
        }
        return t;
    }

    public static void clearPending(Context c) {
        prefs(c).edit().remove(KEY_P_PKG).remove(KEY_P_TEXT)
                .remove(KEY_P_UNTIL).apply();
    }
}
