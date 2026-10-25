package com.ti.tasktimer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * TaskItem ↔ Supabase tasks 行的互转（v1.7.0）。
 * 时间列都是 timestamptz：写出时把桥的本地时间字符串按本机时区解析成带偏移的 ISO，
 * 读回时把瞬间按本机时区格式化回桥格式，日期不会差一天（deadline 只取本地日期）。
 * 服务端目前没有 deadline_time / actual_minutes 两列：hasExtraCols=false 时这两项
 * 不进云端，合并时从本地同 id 任务保全下来，不会被远端行覆盖丢掉。
 * 闹钟两列（alarm_at / alarm_on_timer_end，v1.9.0）同法处理：hasAlarmCols 单独探测，
 * 服务端没列时不进云端、合并时本地保全，补列后自动纳入同步。
 * 子任务列 subtasks（jsonb，v1.11.0）同法：hasSubtaskCols 单独探测，没列时本地保全。
 * 任务级 alarm 模式列 alarm_mode（v1.20.0）同法：hasAlarmModeCol 单独探测，
 * 服务端没列时不进云端、合并时从本地同 id 任务保全；补列后自动纳入同步。
 */
public final class SbMapper {
    private SbMapper() {}

    // ---------- ISO / 毫秒 ----------
    public static long parseIsoMs(String s) {
        if (s == null || s.isEmpty()) return 0;
        String v = s.trim();
        // PostgREST 的 timestamptz 形如 2026-10-24T05:00:00.123456+00:00：小数位可能多于 3 位，先截到 3 位
        int dot = v.indexOf('.');
        if (dot > 0) {
            int end = dot + 1;
            while (end < v.length() && Character.isDigit(v.charAt(end))) end++;
            if (end - dot - 1 != 3) {
                String frac = v.substring(dot + 1, end);
                if (frac.length() > 3) frac = frac.substring(0, 3);
                while (frac.length() < 3) frac = frac + "0";
                v = v.substring(0, dot + 1) + frac + v.substring(end);
            }
        }
        String[] fmts = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd HH:mm:ssXXX", "yyyy-MM-dd HH:mm:ss",
        };
        for (String f : fmts) {
            try {
                SimpleDateFormat df = new SimpleDateFormat(f, Locale.US);
                if (f.endsWith("'Z'")) df.setTimeZone(TimeZone.getTimeZone("UTC"));
                return df.parse(v).getTime();
            } catch (Exception ignored) { }
        }
        return 0;
    }

    public static String isoFromMs(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date(ms));
    }

    // ---------- 桥日期 ↔ timestamptz ----------
    /** deadline "YYYY-MM-DD"（本地日期）→ 本地零点的 timestamptz ISO；空返回 null。 */
    static String dateToTs(String date) {
        if (date == null || date.isEmpty()) return null;
        try {
            long ms = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date).getTime();
            return isoFromMs(ms);
        } catch (Exception e) { return null; }
    }

    /** timestamptz → 本地日历日期 "YYYY-MM-DD"（按本机时区取日期，不会被 UTC 偏移带偏）。 */
    static String tsToDate(String ts) {
        long ms = parseIsoMs(ts);
        if (ms == 0) return null;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(ms));
    }

    /** nextStartAt "YYYY-MM-DDTHH:mm"（本地）→ timestamptz ISO；空返回 null。 */
    static String nextStartToTs(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            long ms = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(s).getTime();
            return isoFromMs(ms);
        } catch (Exception e) { return null; }
    }

    /** timestamptz → "YYYY-MM-DDTHH:mm"（本地）。 */
    static String tsToNextStart(String ts) {
        long ms = parseIsoMs(ts);
        if (ms == 0) return null;
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(new Date(ms));
    }

    // ---------- 行互转 ----------
    /**
     * 任务 → 行。updated_at 不写（服务端默认 now()，回读后再对齐本地 updatedAtMs）；
     * created_at 写入以便多端看到一致的创建时间。
     */
    public static JSONObject toRow(TaskItem t, String userId, boolean hasExtraCols,
                                   boolean hasAlarmCols) throws Exception {
        return toRow(t, userId, hasExtraCols, hasAlarmCols, false);
    }

    public static JSONObject toRow(TaskItem t, String userId, boolean hasExtraCols,
                                   boolean hasAlarmCols, boolean hasSubtaskCols) throws Exception {
        return toRow(t, userId, hasExtraCols, hasAlarmCols, hasSubtaskCols, false);
    }

    public static JSONObject toRow(TaskItem t, String userId, boolean hasExtraCols,
                                   boolean hasAlarmCols, boolean hasSubtaskCols,
                                   boolean hasAlarmModeCol) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", t.id);
        o.put("user_id", userId);
        o.put("title", t.title == null ? "" : t.title);
        o.put("note", t.note == null ? "" : t.note);
        o.put("scenarios", strArr(t.scenarios));
        String dl = dateToTs(t.deadline);
        o.put("deadline", dl == null ? JSONObject.NULL : dl);
        o.put("repeat", t.repeat == null ? "none" : t.repeat);
        o.put("duration_minutes", t.durationMinutes);
        o.put("priority", t.priority == null ? JSONObject.NULL : t.priority);
        o.put("status", t.status == null ? "todo" : t.status);
        o.put("deleted", t.deleted);
        o.put("done_dates", strArr(t.doneDates));
        String ns = nextStartToTs(t.nextStartAt);
        o.put("next_start_at", ns == null ? JSONObject.NULL : ns);
        o.put("auto_start", t.autoStart);
        o.put("focus_rule", t.focusRule == null ? "" : t.focusRule);
        o.put("focus_mode", t.focusMode == null ? "off" : t.focusMode);
        o.put("blocked_apps", strArr(t.blockedApps));
        o.put("allowed_apps", strArr(t.allowedApps));
        o.put("primary_app", t.primaryApp == null ? JSONObject.NULL : t.primaryApp);
        if (t.createdAtMs > 0) o.put("created_at", isoFromMs(t.createdAtMs));
        if (hasExtraCols) {
            o.put("deadline_time", t.deadlineTime == null ? JSONObject.NULL : t.deadlineTime);
            o.put("actual_minutes", t.actualMinutes);
        }
        if (hasAlarmCols) {
            String al = nextStartToTs(t.alarmAt);
            o.put("alarm_at", al == null ? JSONObject.NULL : al);
            o.put("alarm_on_timer_end", t.alarmOnTimerEnd);
        }
        if (hasSubtaskCols) {
            JSONArray subs = new JSONArray();
            if (t.subtasks != null) for (Subtask s : t.subtasks) subs.put(s.toJson());
            o.put("subtasks", subs);
        }
        if (hasAlarmModeCol) {
            o.put("alarm_mode", TaskItem.normAlarmMode(t.alarmMode));
        }
        return o;
    }

    /**
     * 行 → 任务。preserveFrom 是本地同 id 的现行任务：行里没有的字段（deadlineTime /
     * actualMinutes 在服务端无列时）从它身上保全，避免合并把本地值冲掉。
     */
    public static TaskItem fromRow(JSONObject o, TaskItem preserveFrom) {
        TaskItem t = new TaskItem();
        t.id = o.optString("id", "");
        t.title = o.optString("title", "");
        t.note = o.optString("note", "");
        t.scenarios = toStrList(o.optJSONArray("scenarios"));
        t.deadline = o.isNull("deadline") ? null : tsToDate(o.optString("deadline", ""));
        t.repeat = o.optString("repeat", "none");
        t.durationMinutes = o.optInt("duration_minutes", 30);
        t.priority = o.isNull("priority") ? null : o.optString("priority", null);
        t.status = o.optString("status", "todo");
        t.deleted = o.optBoolean("deleted", false);
        t.doneDates = toStrList(o.optJSONArray("done_dates"));
        t.nextStartAt = o.isNull("next_start_at") ? null : tsToNextStart(o.optString("next_start_at", ""));
        t.autoStart = o.optBoolean("auto_start", false);
        t.focusRule = o.optString("focus_rule", "");
        t.focusMode = o.optString("focus_mode", "off");
        t.blockedApps = toStrList(o.optJSONArray("blocked_apps"));
        t.allowedApps = toStrList(o.optJSONArray("allowed_apps"));
        t.primaryApp = o.isNull("primary_app") ? null : o.optString("primary_app", null);
        t.createdAtMs = parseIsoMs(o.optString("created_at", ""));
        t.updatedAtMs = parseIsoMs(o.optString("updated_at", ""));
        if (o.has("deadline_time")) {
            t.deadlineTime = o.isNull("deadline_time") ? null : o.optString("deadline_time", null);
            t.actualMinutes = o.optInt("actual_minutes", 0);
        } else if (preserveFrom != null) {
            t.deadlineTime = preserveFrom.deadlineTime;
            t.actualMinutes = preserveFrom.actualMinutes;
        }
        if (o.has("alarm_at")) {
            t.alarmAt = o.isNull("alarm_at") ? null : tsToNextStart(o.optString("alarm_at", ""));
            t.alarmOnTimerEnd = o.optBoolean("alarm_on_timer_end", false);
        } else if (preserveFrom != null) {
            t.alarmAt = preserveFrom.alarmAt;
            t.alarmOnTimerEnd = preserveFrom.alarmOnTimerEnd;
        }
        if (o.has("subtasks") && !o.isNull("subtasks")) {
            JSONArray subs = o.optJSONArray("subtasks");
            if (subs != null) for (int i = 0; i < subs.length(); i++) {
                JSONObject so = subs.optJSONObject(i);
                if (so != null) t.subtasks.add(Subtask.fromJson(so));
            }
            t.syncDurationFromSubtasks(); // 总时长口径：有子任务即等于子任务之和
        } else if (preserveFrom != null && preserveFrom.subtasks != null) {
            for (Subtask s : preserveFrom.subtasks) t.subtasks.add(s.copy());
        }
        if (o.has("alarm_mode")) {
            t.alarmMode = TaskItem.normAlarmMode(o.isNull("alarm_mode") ? null
                    : o.optString("alarm_mode", null));
        } else if (preserveFrom != null) {
            // 服务端没这列（或本行没带）：从本地同 id 任务保全，同步合并不得冲掉
            t.alarmMode = TaskItem.normAlarmMode(preserveFrom.alarmMode);
        }
        return t;
    }

    private static JSONArray strArr(java.util.List<String> list) {
        JSONArray a = new JSONArray();
        if (list != null) for (String s : list) a.put(s == null ? "" : s);
        return a;
    }

    private static java.util.List<String> toStrList(JSONArray a) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i, ""));
        return out;
    }
}
