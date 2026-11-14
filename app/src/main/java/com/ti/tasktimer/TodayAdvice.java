package com.ti.tasktimer;

import android.content.Context;

import org.json.JSONObject;

/**
 * AI 建议的本地缓存（v1.85.0）：云端 user_state.today_advice 是唯一数据源，
 * 由作者的 AI 助手每小时写入（白天 06:00–23:00），App 只读、永不回写。
 * SbSync 全量同步与 Realtime 落地时把云端值 adopt 进本机 prefs，今天页现读
 * 现画。云端为 null（还没生成/已清空）时本机同步清掉，今天页整卡不显示。
 * 形状：{"date":"YYYY-MM-DD","updated_at":ISO,"text":"…",
 *       "principle_id":uuid|null,"task_id":uuid|null}（无 url 字段）。
 */
public final class TodayAdvice {

    private static final String PREF = "today_advice";
    private static final String KEY = "json";

    private TodayAdvice() { }

    /** 采用云端值（null＝云端已清空，本机一并清）。 */
    public static void adopt(Context c, JSONObject o) {
        try {
            if (o == null) {
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                        .edit().remove(KEY).apply();
            } else {
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                        .edit().putString(KEY, o.toString()).apply();
            }
        } catch (Exception ignored) { }
    }

    /** 本机缓存的建议（没有返回 null）。 */
    public static JSONObject load(Context c) {
        try {
            String raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .getString(KEY, null);
            if (raw == null || raw.isEmpty()) return null;
            return new JSONObject(raw);
        } catch (Exception ignored) { return null; }
    }

    public static String date(JSONObject o) { return o == null ? "" : o.optString("date", ""); }
    public static String text(JSONObject o) { return o == null ? "" : o.optString("text", ""); }

    /** 可空字段：JSON null 或缺失都返回 null。 */
    public static String principleId(JSONObject o) {
        if (o == null || o.isNull("principle_id")) return null;
        String v = o.optString("principle_id", "");
        return v.isEmpty() ? null : v;
    }

    public static String taskId(JSONObject o) {
        if (o == null || o.isNull("task_id")) return null;
        String v = o.optString("task_id", "");
        return v.isEmpty() ? null : v;
    }

    /** updated_at（ISO）→ 本地时区 "HH:mm"；解析不了返回空串。 */
    public static String updatedHm(JSONObject o) {
        long ms = updatedMs(o);
        if (ms <= 0) return "";
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                .format(new java.util.Date(ms));
    }

    private static long updatedMs(JSONObject o) {
        if (o == null) return 0;
        String iso = o.optString("updated_at", "");
        if (iso.isEmpty()) return 0;
        try { return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli(); }
        catch (Exception e1) {
            try { return java.time.Instant.parse(iso).toEpochMilli(); }
            catch (Exception e2) { return 0; }
        }
    }
}
