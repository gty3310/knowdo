package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * 空档催促个人设置（v1.14.0）：催促不是写死行为，而是用户偏好——
 * 档位（行为分档）＋一组数值，本地 SharedPreferences 保存，并经 Supabase
 * user_state 的 idle_nag 列（jsonb 对象）同步，手机与电脑共用一份。
 *
 * 档位语义（用户定案）：
 * - gentle 温和：不催——空档不自动弹守卫页、不响 alarm，任务结束就结束。
 * - standard 标准：催但有上限——空档满阈值后全屏＋按间隔重复，最多 max_rounds 轮，
 *   全程没有声音（sound 强制关）。
 * - strict 严格：不停催，直到开始任务为止；只有这一档（与 custom）有声音开关。
 * - custom 自定义：重复策略（不催/有限轮/无限）＋全部数字自调；由任一预设档
 *   手动改任一数值即转入本档（原档语义先折算进 max_rounds 再转，保证行为不变）。
 * v1.20.0 起出厂默认变温和：本机从未存过值（has_local=false）且云端无值时，
 * 回退默认＝standard 档、最多 1 轮、无声、不震动——只弹页提醒一轮就安静，
 * 任务结束也不默认出声（结束响铃是 per-task 开关、默认关，口径不变）。
 * 已存过偏好或云端有值的用户一律按存值走，升级不重置任何人的现行设置；
 * 想要严格全开，去设置页选严格档即可。
 *
 * 同步合并口径（与 aspiration 同一套纪律，见 SbSync.syncUserState）：
 * 本机从未存过值（has_local=false）且云端有值 → 采用云端；本机有值且与云端
 * 不同 → 回写本机值。列（idle_nag）未就绪时完全不碰，只用本地。
 */
public final class IdleNag {
    private static final String PREF = "idle_nag";

    public static final String GENTLE = "gentle";
    public static final String STANDARD = "standard";
    public static final String STRICT = "strict";
    public static final String CUSTOM = "custom";

    private IdleNag() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---------------- 基础读写 ----------------
    /** 本机是否存过一份设置（区分「没设置过，用默认/云端」与「用户在本机定过」）。 */
    public static boolean hasLocal(Context c) { return sp(c).getBoolean("has_local", false); }

    public static boolean enabled(Context c) { return sp(c).getBoolean("enabled", true); }
    /** 无存值时的回退档位（v1.20.0 温和默认）：standard。 */
    public static String profile(Context c) { return sp(c).getString("profile", STANDARD); }
    public static int idleMinutes(Context c) { return sp(c).getInt("idle_minutes", 5); }
    public static int firstRepeatMinutes(Context c) { return sp(c).getInt("first_repeat_minutes", 1); }
    public static int repeatMinutes(Context c) { return sp(c).getInt("repeat_minutes", 5); }
    public static int ringSeconds(Context c) { return sp(c).getInt("ring_seconds", 60); }
    /** 每轮先震动多久（秒，v1.19.0 staging 新增）：先震不出声，无反应才进入声音段。设置页可调、随云端同步。
     *  无存值时默认 0（v1.20.0 温和默认：不震）。 */
    public static int vibrateSeconds(Context c) { return sp(c).getInt("vibrate_seconds", 0); }
    /** 无存值时默认关（v1.20.0 温和默认）。 */
    public static boolean sound(Context c) { return sp(c).getBoolean("sound", false); }
    /** 轮数上限：0=不催，-1=无限，>0=有限轮（无存值时默认 1，v1.20.0 温和默认；设置页选 standard 预设写 3，custom 直接存策略值）。 */
    public static int maxRounds(Context c) { return sp(c).getInt("max_rounds", 1); }

    private static void putAll(Context c, boolean enabled, String profile, int idleMin,
                               int firstMin, int repeatMin, int ringSec, boolean sound,
                               int maxRounds) {
        putAll(c, enabled, profile, idleMin, firstMin, repeatMin, ringSec, sound, maxRounds,
                vibrateSeconds(c));
    }

    private static void putAll(Context c, boolean enabled, String profile, int idleMin,
                               int firstMin, int repeatMin, int ringSec, boolean sound,
                               int maxRounds, int vibrateSec) {
        sp(c).edit()
                .putBoolean("has_local", true)
                .putBoolean("enabled", enabled)
                .putString("profile", profile)
                .putInt("idle_minutes", idleMin)
                .putInt("first_repeat_minutes", firstMin)
                .putInt("repeat_minutes", repeatMin)
                .putInt("ring_seconds", ringSec)
                .putInt("vibrate_seconds", vibrateSec)
                .putBoolean("sound", sound)
                .putInt("max_rounds", maxRounds)
                .apply();
    }

    // ---------------- 派生语义（行为层只问这几个） ----------------
    /** 有效轮数上限：gentle 恒 0、strict 恒无限、standard/custom 取存值。 */
    public static int effectiveMaxRounds(Context c) {
        String p = profile(c);
        if (GENTLE.equals(p)) return 0;
        if (STRICT.equals(p)) return -1;
        return maxRounds(c);
    }

    /** 催促行为是否启用：总开关开着且有效轮数不是 0（gentle 恒不催）。 */
    public static boolean nagActive(Context c) {
        return enabled(c) && effectiveMaxRounds(c) != 0;
    }

    /** 空档时是否连守卫页都不自动弹：只有温和档。 */
    public static boolean suppressIdlePage(Context c) {
        return GENTLE.equals(profile(c));
    }

    /** 这一轮要不要出声：声音只属严格与自定义（且其 sound 开关开着）；标准全程无声。 */
    public static boolean effectiveSound(Context c) {
        String p = profile(c);
        if (STRICT.equals(p) || CUSTOM.equals(p)) return sound(c);
        return false;
    }

    // ---------------- 设置页写入 ----------------
    public static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("has_local", true).putBoolean("enabled", on).apply();
    }

    public static void setSound(Context c, boolean on) {
        sp(c).edit().putBoolean("has_local", true).putBoolean("sound", on).apply();
    }

    /** 选预设档：把该档默认参数整套写入并立即生效。 */
    public static void applyProfile(Context c, String p) {
        boolean en = enabled(c);
        if (GENTLE.equals(p)) {
            // 数字无意义，保留现值，只切行为
            putAll(c, en, GENTLE, idleMinutes(c), firstRepeatMinutes(c), repeatMinutes(c),
                    ringSeconds(c), sound(c), 0);
        } else if (STANDARD.equals(p)) {
            putAll(c, en, STANDARD, 5, 5, 5, 30, sound(c), 3, 30);
        } else if (STRICT.equals(p)) {
            putAll(c, en, STRICT, 5, 1, 5, 60, true, -1, 30);
        }
    }

    /**
     * 从预设档转自定义：先把当前档的轮数语义折算进 max_rounds（严格→无限、
     * 温和→0、标准→存值），再改 profile，保证转档瞬间行为不变。已在自定义则无-op。
     */
    private static void toCustom(Context c) {
        String p = profile(c);
        if (CUSTOM.equals(p)) return;
        int mr = effectiveMaxRounds(c);
        sp(c).edit().putBoolean("has_local", true)
                .putString("profile", CUSTOM).putInt("max_rounds", mr).apply();
    }

    /** 在档位选择里直接选「自定义」：只转档，数值不动（语义已由 toCustom 折算保住）。 */
    public static void enterCustom(Context c) {
        toCustom(c);
        sp(c).edit().putBoolean("has_local", true).apply();
    }

    /** 设置页改任一数值：先转自定义（保语义），再写值。 */
    public static void editParam(Context c, String key, int value) {
        toCustom(c);
        sp(c).edit().putBoolean("has_local", true).putInt(key, value).apply();
    }

    /** 自定义档的重复策略：0=不催、-1=无限、>0=有限轮，直接写 max_rounds（本就在自定义语境）。 */
    public static void setCustomMaxRounds(Context c, int mr) {
        sp(c).edit().putBoolean("has_local", true)
                .putString("profile", CUSTOM).putInt("max_rounds", mr).apply();
    }

    // ---------------- 节奏说明（一行话讲清实际行为，设置页用） ----------------
    public static String rhythmText(Context c) {
        if (!enabled(c)) return Strings.en() ? "Off — no idle nagging at all."
                : "已关闭——空档时不催。";
        String p = profile(c);
        int idle = idleMinutes(c), ring = ringSeconds(c);
        int first = firstRepeatMinutes(c), rep = repeatMinutes(c);
        int mr = effectiveMaxRounds(c);
        boolean snd = effectiveSound(c);
        if (mr == 0) return Strings.en() ? "A task ends, and that's it — no popup, no sound, no nagging."
                : "任务结束就结束，不弹页、不出声，不催你。";
        int vib = vibrateSeconds(c);
        if (Strings.en()) {
            String s = "After " + idle + " min with no task, a full-screen reminder: each round vibrates "
                    + vib + "s first"
                    + (snd ? ", then the sound fades in over " + ring + "s" : " (no sound)")
                    + ", then every " + rep + " min";
            return mr < 0 ? s + ", until you start a task." : s + ", at most " + mr + " rounds, then quiet.";
        }
        String s = idle + " 分钟没任务就全屏提醒，每轮先震动 " + vib + " 秒"
                + (snd ? "，无反应再让声音在 " + ring + " 秒里渐强" : "（不出声）")
                + "，之后每 " + rep + " 分钟一轮";
        return mr < 0 ? s + "，直到你开始任务为止。" : s + "，最多 " + mr + " 轮，之后安静。";
    }

    public static String profileName(String p) {
        if (GENTLE.equals(p)) return Strings.en() ? "Gentle" : "温和";
        if (STANDARD.equals(p)) return Strings.en() ? "Standard" : "标准";
        if (STRICT.equals(p)) return Strings.en() ? "Strict" : "严格";
        return Strings.en() ? "Custom" : "自定义";
    }

    // ---------------- 云端 JSON（user_state.idle_nag） ----------------
    public static JSONObject toCloudJson(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("enabled", enabled(c));
            o.put("profile", profile(c));
            o.put("idle_minutes", idleMinutes(c));
            o.put("first_repeat_minutes", firstRepeatMinutes(c));
            o.put("repeat_minutes", repeatMinutes(c));
            o.put("ring_seconds", ringSeconds(c));
            o.put("vibrate_seconds", vibrateSeconds(c));
            o.put("sound", sound(c));
            o.put("max_rounds", maxRounds(c));
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 采用云端值（整套替换本地，并标记本机已有值，之后以本机为准参与合并）。
     *  云端对象缺键时的回退与全新状态的温和默认对齐（v1.20.0）；未知键在回写时
     *  由 toCloudJson 补齐，不丢既有字段。 */
    public static void adoptCloud(Context c, JSONObject o) {
        if (o == null) return;
        String p = o.optString("profile", STANDARD);
        if (!GENTLE.equals(p) && !STANDARD.equals(p) && !STRICT.equals(p) && !CUSTOM.equals(p)) p = STANDARD;
        putAll(c,
                o.optBoolean("enabled", true),
                p,
                clamp(o.optInt("idle_minutes", 5), 1, 240),
                clamp(o.optInt("first_repeat_minutes", 1), 1, 240),
                clamp(o.optInt("repeat_minutes", 5), 1, 240),
                clamp(o.optInt("ring_seconds", 60), 5, 600),
                o.optBoolean("sound", false),
                o.optInt("max_rounds", 1),
                clamp(o.optInt("vibrate_seconds", 0), 0, 300));
    }

    /** 本机现值与云端对象是否等价（逐字段比，避免字符串比对受键序影响）。 */
    public static boolean cloudEquals(Context c, JSONObject o) {
        if (o == null) return false;
        return o.optBoolean("enabled", true) == enabled(c)
                && profile(c).equals(o.optString("profile", ""))
                && o.optInt("idle_minutes", -999) == idleMinutes(c)
                && o.optInt("first_repeat_minutes", -999) == firstRepeatMinutes(c)
                && o.optInt("repeat_minutes", -999) == repeatMinutes(c)
                && o.optInt("ring_seconds", -999) == ringSeconds(c)
                && o.optInt("vibrate_seconds", 0) == vibrateSeconds(c)
                && o.optBoolean("sound", false) == sound(c)
                && o.optInt("max_rounds", -999) == maxRounds(c);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
