package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * 软件更新偏好（v1.27.0）：「自动检查更新」开关＋「下载网络」选择。
 * 下载网络经云端 user_state.update_prefs（jsonb 单键对象 {"wifi_only": bool}）
 * 同步，口径仿 PromptRepop：本地 SharedPreferences ＋列未就绪时完全不碰云端、
 * 只用本地（SbSync 探测纪律）；本机没存过且云端有值 → 采用云端；本机存过且
 * 与云端不同 → 回写本机值。默认 wifi_only=true（只在 Wi-Fi 下自动下载）。
 * 作者账号云端已由 Jarvis 预设 wifi_only=false（2026-10-27 用户亲口定：
 * 没有 Wi-Fi 时也可以下载）。
 * 自动检查开关只存本地（不上云）：它只决定本机要不要主动查，查法本身无副作用。
 */
public final class UpdatePrefs {
    private static final String PREF = "update_prefs";

    private UpdatePrefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---------------- 自动检查（本地） ----------------

    public static boolean autoCheck(Context c) {
        return sp(c).getBoolean("auto_check", true);
    }

    public static void setAutoCheck(Context c, boolean on) {
        sp(c).edit().putBoolean("auto_check", on).apply();
    }

    // ---------------- 下载网络（本地＋云端 update_prefs） ----------------

    /** 本机是否存过下载网络选择（区分「没设置过，用默认/云端」与「用户在本机定过」）。 */
    public static boolean hasLocal(Context c) { return sp(c).getBoolean("has_local", false); }

    /** 当前生效：true＝只在 Wi-Fi 下自动下载新版；false＝Wi-Fi 与流量都下。无存值回落 true。 */
    public static boolean wifiOnly(Context c) {
        if (!hasLocal(c)) return true;
        return sp(c).getBoolean("wifi_only", true);
    }

    /** 设置页改值：写本地（标 has_local）并踢一脚同步，尽快回写云端。 */
    public static void setWifiOnly(Context c, boolean wifiOnly) {
        sp(c).edit().putBoolean("has_local", true).putBoolean("wifi_only", wifiOnly).apply();
        GuardRules.kickSync(c);
    }

    public static String wifiOnlyLabel(boolean wifiOnly) {
        if (Strings.en()) return wifiOnly ? "Wi-Fi only" : "Wi-Fi & mobile data";
        return wifiOnly ? "仅 Wi-Fi" : "Wi-Fi 与流量";
    }

    // ---------------- 云端 JSON（user_state.update_prefs，单键对象） ----------------

    public static JSONObject toCloudJson(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("wifi_only", wifiOnly(c));
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 采用云端值（整份替换本地并标 has_local，之后以本机为准参与合并——与 PromptRepop 同口径）。 */
    public static void adoptCloud(Context c, JSONObject o) {
        if (o == null || !o.has("wifi_only")) return;
        sp(c).edit().putBoolean("has_local", true)
                .putBoolean("wifi_only", o.optBoolean("wifi_only", true)).apply();
    }

    /** 本机值与云端对象是否一致（云端为 null/缺键视为不一致——前提是本机有值）。 */
    public static boolean cloudEquals(Context c, JSONObject remote) {
        if (remote == null || !remote.has("wifi_only")) return false;
        return remote.optBoolean("wifi_only", true) == wifiOnly(c);
    }
}
