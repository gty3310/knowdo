package com.ti.tasktimer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 任务模型，字段与 Drive 桥 tasks.json（schema v1）完全对齐。 */
public class TaskItem {
    public String id;
    public String title = "";
    public List<String> scenarios = new ArrayList<>();
    public String deadline;            // YYYY-MM-DD，可空
    public String repeat = "none";     // none / daily
    public int durationMinutes = 30;
    public String priority;            // must / secondary / null
    public String status = "todo";     // todo / done（daily 任务用 doneDates 记每日完成）
    public List<String> doneDates = new ArrayList<>();
    public boolean deleted = false;
    public long createdAtMs;
    public long updatedAtMs;

    public static String nowIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date());
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static TaskItem create(String title) {
        TaskItem t = new TaskItem();
        t.id = UUID.randomUUID().toString();
        t.title = title;
        t.createdAtMs = System.currentTimeMillis();
        t.updatedAtMs = t.createdAtMs;
        return t;
    }

    public void touch() { updatedAtMs = System.currentTimeMillis(); }

    public boolean isDoneToday() {
        if ("daily".equals(repeat)) return doneDates.contains(today());
        return "done".equals(status);
    }

    public void markDone() {
        if ("daily".equals(repeat)) {
            String d = today();
            if (!doneDates.contains(d)) doneDates.add(d);
        } else {
            status = "done";
        }
        touch();
    }

    public void markUndone() {
        if ("daily".equals(repeat)) doneDates.remove(today());
        else status = "todo";
        touch();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        JSONArray sc = new JSONArray();
        for (String s : scenarios) sc.put(s);
        o.put("scenarios", sc);
        o.put("deadline", deadline == null ? JSONObject.NULL : deadline);
        o.put("repeat", repeat);
        o.put("durationMinutes", durationMinutes);
        o.put("priority", priority == null ? JSONObject.NULL : priority);
        o.put("status", status);
        JSONArray dd = new JSONArray();
        for (String s : doneDates) dd.put(s);
        o.put("doneDates", dd);
        o.put("deleted", deleted);
        o.put("createdAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date(createdAtMs)));
        o.put("updatedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date(updatedAtMs)));
        o.put("createdAtMs", createdAtMs);
        o.put("updatedAtMs", updatedAtMs);
        return o;
    }

    public static TaskItem fromJson(JSONObject o) throws JSONException {
        TaskItem t = new TaskItem();
        t.id = o.getString("id");
        t.title = o.optString("title", "");
        JSONArray sc = o.optJSONArray("scenarios");
        if (sc != null) for (int i = 0; i < sc.length(); i++) t.scenarios.add(sc.getString(i));
        t.deadline = o.isNull("deadline") ? null : o.optString("deadline", null);
        if (t.deadline != null && t.deadline.isEmpty()) t.deadline = null;
        t.repeat = o.optString("repeat", "none");
        t.durationMinutes = o.optInt("durationMinutes", 30);
        t.priority = o.isNull("priority") ? null : o.optString("priority", null);
        if (t.priority != null && t.priority.isEmpty()) t.priority = null;
        t.status = o.optString("status", "todo");
        JSONArray dd = o.optJSONArray("doneDates");
        if (dd != null) for (int i = 0; i < dd.length(); i++) t.doneDates.add(dd.getString(i));
        t.deleted = o.optBoolean("deleted", false);
        t.createdAtMs = o.optLong("createdAtMs", 0);
        t.updatedAtMs = o.optLong("updatedAtMs", 0);
        // 兼容只有 ISO 时间、没有毫秒字段的文件（桥的初版由 Muse 维护时可能只写 ISO）
        if (t.updatedAtMs == 0) t.updatedAtMs = parseIso(o.optString("updatedAt", ""));
        if (t.createdAtMs == 0) t.createdAtMs = parseIso(o.optString("createdAt", ""));
        return t;
    }

    private static long parseIso(String s) {
        if (s == null || s.isEmpty()) return 0;
        String[] fmts = {"yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String f : fmts) {
            try { return new SimpleDateFormat(f, Locale.US).parse(s).getTime(); }
            catch (Exception ignored) { }
        }
        return 0;
    }
}
