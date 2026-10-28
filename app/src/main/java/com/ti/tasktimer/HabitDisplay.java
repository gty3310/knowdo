package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * 习惯显示口径（v1.28.0，总设置）：习惯页卡片主行的次数按哪个时段来数——
 * 今天 / 本周 / 上周 / 本月，四选一，全部习惯统一（用户改定：不做每习惯单独设）。
 * 只管卡片展示：累积型仍显示累计总数不受影响；知行指数仍走 ZhixingCalc 的周口径；
 * 晨间门仍按昨日口径，都不跟随本设置。
 *
 * 存储与同步口径仿 UpdatePrefs：本地 SharedPreferences ＋云端 user_state.habit_display
 * （jsonb 单键对象 {"scope": "today|week|last_week|month"}）。列未就绪时完全不碰
 * 云端、只用本地（SbSync 探测纪律）；本机没存过且云端有值 → 采用云端；本机存过且
 * 与云端不同 → 回写本机值。无存值回落默认「本周」——与本设置出现前的卡片口径一致。
 */
public final class HabitDisplay {
    private static final String PREF = "habit_display";

    public static final String SCOPE_TODAY = "today";
    public static final String SCOPE_WEEK = "week";
    public static final String SCOPE_LAST_WEEK = "last_week";
    public static final String SCOPE_MONTH = "month";
    public static final String DEFAULT_SCOPE = SCOPE_WEEK;

    private HabitDisplay() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 本机是否存过口径（区分「没设置过，用默认/云端」与「用户在本机定过」）。 */
    public static boolean hasLocal(Context c) { return sp(c).getBoolean("has_local", false); }

    /** 当前生效口径：本机存值优先，无存值回落默认「本周」；存值非法也回落默认。 */
    public static String scope(Context c) {
        if (!hasLocal(c)) return DEFAULT_SCOPE;
        return valid(sp(c).getString("scope", DEFAULT_SCOPE));
    }

    private static String valid(String v) {
        if (SCOPE_TODAY.equals(v) || SCOPE_WEEK.equals(v)
                || SCOPE_LAST_WEEK.equals(v) || SCOPE_MONTH.equals(v)) return v;
        return DEFAULT_SCOPE;
    }

    /** 设置页改值：写本地（标 has_local）并踢一脚同步，尽快回写云端。 */
    public static void setScope(Context c, String scope) {
        sp(c).edit().putBoolean("has_local", true).putString("scope", valid(scope)).apply();
        GuardRules.kickSync(c);
    }

    public static String label(String scope) {
        if (Strings.en()) {
            if (SCOPE_TODAY.equals(scope)) return "Today";
            if (SCOPE_LAST_WEEK.equals(scope)) return "Last week";
            if (SCOPE_MONTH.equals(scope)) return "This month";
            return "This week";
        }
        if (SCOPE_TODAY.equals(scope)) return "今天";
        if (SCOPE_LAST_WEEK.equals(scope)) return "上周";
        if (SCOPE_MONTH.equals(scope)) return "本月";
        return "本周";
    }

    // ---------------- 云端 JSON（user_state.habit_display，单键对象） ----------------

    public static JSONObject toCloudJson(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("scope", scope(c));
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 采用云端值（整份替换本地并标 has_local，之后以本机为准参与合并——与 UpdatePrefs 同口径）。 */
    public static void adoptCloud(Context c, JSONObject o) {
        if (o == null || !o.has("scope")) return;
        sp(c).edit().putBoolean("has_local", true)
                .putString("scope", valid(o.optString("scope", DEFAULT_SCOPE))).apply();
    }

    /** 本机值与云端对象是否一致（云端为 null/缺键视为不一致——前提是本机有值）。 */
    public static boolean cloudEquals(Context c, JSONObject remote) {
        if (remote == null || !remote.has("scope")) return false;
        return valid(remote.optString("scope", "")).equals(scope(c));
    }
}
