package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * 提示页重弹间隔（v1.26.3）：任何全屏提示页被用户划走（未解决就离开）后，
 * 隔多久再弹回来——一个数值、四处共用：
 * ① 晨间门未提交未推迟的 nag 链（MorningGate，原固定 5 分钟）；
 * ② 守卫问询页（专注 FocusGuard 原 12 秒防抖／内容 VerdictEngine 原 60 秒冷却）；
 * ③ 空档「下一个任务」页按返回退出后的静默期（IdleGuard，原 3 分钟）；
 * ④ 静音暂停叫回链的再次提醒间隔（SilentResume，原 5 分钟）。
 * 明确选了推迟/snooze 时长的仍按所选时长走，不经本类；声音 staging 不受影响。
 *
 * 存储与同步口径仿 IdleNag：本地 SharedPreferences（prompt_repop）＋云端
 * user_state.prompt_repop（jsonb 单键对象 {"sec": 秒数}）。列未就绪时完全
 * 不碰云端、只用本地（SbSync 探测纪律）。本机没存过且云端有值 → 采用云端；
 * 本机存过且与云端不同 → 回写本机值。无值时回落默认 300 秒。
 * 作者账号云端已由 Jarvis 预设 15 秒（2026-10-27 用户亲口定：5 分钟太久）。
 */
public final class PromptRepop {
    private static final String PREF = "prompt_repop";
    public static final int DEFAULT_SEC = 300;
    private static final int MIN_SEC = 5;
    private static final int MAX_SEC = 3600;

    private PromptRepop() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 本机是否存过一份值（区分「没设置过，用默认/云端」与「用户在本机定过」）。 */
    public static boolean hasLocal(Context c) { return sp(c).getBoolean("has_local", false); }

    /** 当前生效间隔（秒）：本机存值优先，无存值回落默认 300。 */
    public static int seconds(Context c) {
        if (!hasLocal(c)) return DEFAULT_SEC;
        return clamp(sp(c).getInt("sec", DEFAULT_SEC));
    }

    public static long millis(Context c) { return seconds(c) * 1000L; }

    private static int clamp(int sec) {
        return Math.max(MIN_SEC, Math.min(MAX_SEC, sec));
    }

    /** 设置页改值：写本地（标 has_local）并踢一脚同步，尽快回写云端。 */
    public static void setSeconds(Context c, int sec) {
        sp(c).edit().putBoolean("has_local", true).putInt("sec", clamp(sec)).apply();
        GuardRules.kickSync(c);
    }

    /** 一行话讲清当前间隔（设置页用）。 */
    public static String label(int sec) {
        if (sec < 60) return Strings.en() ? sec + " sec" : sec + " 秒";
        int min = sec / 60;
        return Strings.en() ? min + " min" : min + " 分钟";
    }

    // ---------------- 云端 JSON（user_state.prompt_repop，单键对象） ----------------

    public static JSONObject toCloudJson(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("sec", seconds(c));
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 采用云端值（整份替换本地并标 has_local，之后以本机为准参与合并——与 IdleNag 同口径）。 */
    public static void adoptCloud(Context c, JSONObject o) {
        if (o == null) return;
        int sec = o.optInt("sec", 0);
        if (sec <= 0) return;
        sp(c).edit().putBoolean("has_local", true).putInt("sec", clamp(sec)).apply();
    }

    /** 本机值与云端对象是否一致（云端为 null/缺键视为不一致——前提是本机有值）。 */
    public static boolean cloudEquals(Context c, JSONObject remote) {
        if (remote == null) return false;
        return remote.optInt("sec", -1) == seconds(c);
    }
}
