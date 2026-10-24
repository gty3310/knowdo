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
    public String deadlineTime;        // "HH:mm" 完成时间（当天几点前做完），可空
    public String nextStartAt;         // "YYYY-MM-DDTHH:mm" 下一个开始时间（本地时间），到点全屏提醒开始，可空
    public String alarmAt;             // "yyyy-MM-dd'T'HH:mm" 闹钟时间（本地时间）：到点响铃，与计时状态无关，可空（v1.9.0）
    public boolean alarmOnTimerEnd = false; // 计时结束时响铃（响完再进结束检查页），默认关（v1.9.0）
    public String repeat = "none";     // none / daily
    public int durationMinutes = 30;
    public int actualMinutes = 0;      // 手动记录的实际用时（分钟），0=未填
    public String priority;            // must / secondary / null
    public String status = "todo";     // todo / done（daily 任务用 doneDates 记每日完成）
    public boolean autoStart = false;  // 桥端置 true：App 实时同步发现后立即自动开始计时，随即回写 false 防重复
    public String note = "";           // 任务笔记，可在计时悬浮窗里随手写，随桥同步
    public List<Subtask> subtasks = new ArrayList<>(); // 子任务队列（v1.11.0）：非空时总时长＝其分钟之和
    // ---- 专注守卫 ----
    public String focusRule = "";      // 自然语言规则（人读的，App 不解析；结构化名单才执行）
    public String focusMode = "off";   // off / block（拦 blockedApps）/ allow（只许 allowedApps）
    public List<String> blockedApps = new ArrayList<>();
    public List<String> allowedApps = new ArrayList<>();
    public String primaryApp;          // 指定的主软件包名：被拦下时跳回它；为空则跳知行「当前任务页」
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

    // ---------- 子任务（v1.11.0） ----------
    public boolean hasSubtasks() { return subtasks != null && !subtasks.isEmpty(); }

    public int subtasksTotalMinutes() {
        int sum = 0;
        if (subtasks != null) for (Subtask s : subtasks) sum += Math.max(1, s.minutes);
        return sum;
    }

    /** 时长口径：有子任务时总时长恒等于子任务之和；任何子任务编辑后调它对齐 durationMinutes。 */
    public void syncDurationFromSubtasks() {
        if (hasSubtasks()) durationMinutes = Math.max(1, subtasksTotalMinutes());
    }

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
        o.put("deadlineTime", deadlineTime == null ? JSONObject.NULL : deadlineTime);
        o.put("nextStartAt", nextStartAt == null ? JSONObject.NULL : nextStartAt);
        o.put("alarmAt", alarmAt == null ? JSONObject.NULL : alarmAt);
        o.put("alarmOnTimerEnd", alarmOnTimerEnd);
        o.put("repeat", repeat);
        o.put("durationMinutes", durationMinutes);
        o.put("actualMinutes", actualMinutes);
        o.put("priority", priority == null ? JSONObject.NULL : priority);
        o.put("status", status);
        o.put("autoStart", autoStart);
        o.put("note", note);
        JSONArray subs = new JSONArray();
        if (subtasks != null) for (Subtask s : subtasks) subs.put(s.toJson());
        o.put("subtasks", subs);
        o.put("focusRule", focusRule);
        o.put("focusMode", focusMode);
        JSONArray ba = new JSONArray();
        for (String s : blockedApps) ba.put(s);
        o.put("blockedApps", ba);
        JSONArray aa = new JSONArray();
        for (String s : allowedApps) aa.put(s);
        o.put("allowedApps", aa);
        o.put("primaryApp", primaryApp == null ? JSONObject.NULL : primaryApp);
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
        t.deadlineTime = o.isNull("deadlineTime") ? null : o.optString("deadlineTime", null);
        if (t.deadlineTime != null && t.deadlineTime.isEmpty()) t.deadlineTime = null;
        t.nextStartAt = o.isNull("nextStartAt") ? null : o.optString("nextStartAt", null);
        if (t.nextStartAt != null && t.nextStartAt.isEmpty()) t.nextStartAt = null;
        t.alarmAt = o.isNull("alarmAt") ? null : o.optString("alarmAt", null);
        if (t.alarmAt != null && t.alarmAt.isEmpty()) t.alarmAt = null;
        t.alarmOnTimerEnd = o.optBoolean("alarmOnTimerEnd", false);
        t.repeat = o.optString("repeat", "none");
        t.durationMinutes = o.optInt("durationMinutes", 30);
        t.actualMinutes = o.optInt("actualMinutes", 0);
        t.priority = o.isNull("priority") ? null : o.optString("priority", null);
        if (t.priority != null && t.priority.isEmpty()) t.priority = null;
        t.status = o.optString("status", "todo");
        t.autoStart = o.optBoolean("autoStart", false);
        t.note = o.optString("note", "");
        JSONArray subs = o.optJSONArray("subtasks");
        if (subs != null) for (int i = 0; i < subs.length(); i++) {
            JSONObject so = subs.optJSONObject(i);
            if (so != null) t.subtasks.add(Subtask.fromJson(so));
        }
        t.focusRule = o.optString("focusRule", "");
        t.focusMode = o.optString("focusMode", "off");
        JSONArray ba = o.optJSONArray("blockedApps");
        if (ba != null) for (int i = 0; i < ba.length(); i++) t.blockedApps.add(ba.getString(i));
        JSONArray aa = o.optJSONArray("allowedApps");
        if (aa != null) for (int i = 0; i < aa.length(); i++) t.allowedApps.add(aa.getString(i));
        t.primaryApp = o.isNull("primaryApp") ? null : o.optString("primaryApp", null);
        if (t.primaryApp != null && t.primaryApp.isEmpty()) t.primaryApp = null;
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

    /** nextStartAt（"YYYY-MM-DDTHH:mm"，本地时间）解析成毫秒；未设或格式坏返回 0。 */
    public long nextStartMs() {
        if (nextStartAt == null || nextStartAt.isEmpty()) return 0;
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(nextStartAt).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /** alarmAt（"yyyy-MM-dd'T'HH:mm"，本地时间）解析成毫秒；未设或格式坏返回 0。 */
    public long alarmMs() {
        if (alarmAt == null || alarmAt.isEmpty()) return 0;
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(alarmAt).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 毫秒 → nextStartAt 的字符串格式（本地时间）。 */
    public static String formatNextStart(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(new Date(ms));
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
