package com.ti.tasktimer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
    // 任务级 alarm 模式（v1.20.0）：只管「这个任务」的结束提醒与暂停叫回出不出声，
    // 空档催促仍走全局 idle_nag、不受它影响。follow 跟随全局（默认）/ sound 有声 /
    // silent 静默（只震动与亮屏、不出声）/ off 关闭（不出声提醒）。
    public String alarmMode = "follow";
    public static final String ALARM_FOLLOW = "follow";
    public static final String ALARM_SOUND = "sound";
    public static final String ALARM_SILENT = "silent";
    public static final String ALARM_OFF = "off";

    /** alarmMode 归一化：未知值一律按 follow。 */
    public static String normAlarmMode(String m) {
        if (ALARM_SOUND.equals(m) || ALARM_SILENT.equals(m) || ALARM_OFF.equals(m)) return m;
        return ALARM_FOLLOW;
    }

    /** 这个任务的提醒是否禁止出声（结束响铃/叫回升级铃）：silent、off 都不出声。 */
    public boolean alarmMuted() {
        return ALARM_SILENT.equals(alarmMode) || ALARM_OFF.equals(alarmMode);
    }
    // ---- 谁来做（v1.74.0）：self 给我自己做（默认）/ ai 给 AI 做 ----
    // ai 任务由作者的 AI 助手在云端接单：不可计时、不进排程/时间账/滚入/开始提醒、
    // 不计知行指数任务分项（各消费端的排除口径都认 isAi() 这一个判定）。
    public String assignee = "self";   // self / ai
    // AI 任务状态（云端巡查推进，App 只写初始值与展示）：null 不适用 /
    // queued 排队中 / working 进行中 / await_confirm 待确认 / question 有问题 / done 已做完
    public String aiState;
    public static final String ASSIGN_SELF = "self";
    public static final String ASSIGN_AI = "ai";

    /** assignee 归一化：未知值一律按 self。 */
    public static String normAssignee(String a) {
        return ASSIGN_AI.equals(a) ? ASSIGN_AI : ASSIGN_SELF;
    }

    public boolean isAi() { return ASSIGN_AI.equals(assignee); }

    /** aiState 展示文案；null/未知值返回 null（调用方只画「AI」标记本身）。 */
    public static String aiStateLabel(String s, boolean en) {
        if (s == null) return null;
        switch (s) {
            case "queued": return en ? "Queued" : "排队中";
            case "working": return en ? "In progress" : "进行中";
            case "await_confirm": return en ? "Awaiting your OK" : "待确认";
            case "question": return en ? "Has a question" : "有问题";
            case "done": return en ? "Done by AI" : "已做完";
            default: return null;
        }
    }

    public static final String REPEAT_NONE = "none";
    public static final String REPEAT_DAILY = "daily";
    public static final String REPEAT_WEEKLY = "weekly";
    public String repeat = "none";     // none / daily / weekly
    /** ISO 星期 1=周一 … 7=周日；仅 weekly 有意义。 */
    public List<Integer> repeatWeekdays = new ArrayList<>();
    public int durationMinutes = 30;
    public int actualMinutes = 0;      // 手动记录的实际用时（分钟），0=未填
    public String priority;            // must / important / secondary / leisure / null（null 与未知值按 secondary 论）
    public String status = "todo";     // todo / done（daily 任务用 doneDates 记每日完成）
    public boolean autoStart = false;  // 桥端置 true：App 实时同步发现后立即自动开始计时，随即回写 false 防重复
    public String note = "";           // 任务笔记，可在计时悬浮窗里随手写，随桥同步
    public String description = "";    // 任务说明：给守卫判定用，与笔记分开
    public List<Subtask> subtasks = new ArrayList<>(); // 子任务队列（v1.11.0）：非空时总时长＝其分钟之和
    // ---- 专注守卫 ----
    public String focusRule = "";      // 自然语言规则（人读的，App 不解析；结构化名单才执行）
    public String focusMode = "off";   // off / block（拦 blockedApps）/ allow（只许 allowedApps）
    public List<String> blockedApps = new ArrayList<>();
    public List<String> allowedApps = new ArrayList<>();
    public String primaryApp;          // 指定的主软件包名：被拦下时跳回它；为空则跳知行「当前任务页」
    public List<String> doneDates = new ArrayList<>();
    // 跳过日期（v1.79.0）：重复任务点「跳过今天」记下的日期，与 doneDates 并列——
    // 记在这里的那一天，该任务既不算完成也不算未完成（知行指数与时间账都剔除）。
    public List<String> skippedDates = new ArrayList<>();
    public boolean deleted = false;
    public long createdAtMs;
    public long updatedAtMs;
    // 暂停守卫哨兵（v1.96.0）：仅内存标记，不进 toJson/不同步——暂停期采样合成的
    // 「任务」带此标记：id＝PAUSE_GUARD_ID、title＝暂停目的文本，守卫管线拿它当
    // 判定参照；title 为空（目的还没填的空窗）时管线按底线模式只让黄图出结论
    public boolean pauseGuard;
    public static final String PAUSE_GUARD_ID = "pause-guard";

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

    /**
     * 给判定层看的副本：把任务说明拼进 note，不改本机任务。
     * 这样 SemanticJudge 无需改动也能读到任务说明（它只看 title+note）。
     */
    public TaskItem forJudging() {
        if (description == null || description.trim().isEmpty()) return this;
        try {
            TaskItem c = fromJson(toJson());
            String d = description.trim();
            String n = note == null ? "" : note.trim();
            c.note = n.isEmpty() ? d : d + "\n" + n;
            return c;
        } catch (Exception e) {
            return this;
        }
    }

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

    public boolean isDaily() { return REPEAT_DAILY.equals(repeat); }
    public boolean isWeekly() { return REPEAT_WEEKLY.equals(repeat); }
    public boolean isRecurring() { return isDaily() || isWeekly(); }

    /** yyyy-MM-dd 的 ISO 星期：1=周一 … 7=周日；解析失败返回 0。 */
    public static int isoWeekday(String date) {
        Calendar cal = DayUtil.parse(date);
        if (cal == null) return 0;
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        return dow == Calendar.SUNDAY ? 7 : dow - 1;
    }

    public static String weekdaysToStr(List<Integer> days) {
        if (days == null || days.isEmpty()) return "";
        List<Integer> u = new ArrayList<>();
        for (Integer d : days) {
            if (d == null || d < 1 || d > 7 || u.contains(d)) continue;
            u.add(d);
        }
        java.util.Collections.sort(u);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < u.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(u.get(i));
        }
        return sb.toString();
    }

    public static List<Integer> weekdaysFromStr(String raw) {
        List<Integer> out = new ArrayList<>();
        if (raw == null) return out;
        for (String p : raw.split(",")) {
            p = p.trim();
            if (p.isEmpty()) continue;
            try {
                int n = Integer.parseInt(p);
                if (n >= 1 && n <= 7 && !out.contains(n)) out.add(n);
            } catch (NumberFormatException ignored) { }
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** 每日：每天都到期。每周：仅选中的星期。其他：不算按日重复到期。 */
    public boolean isDueOn(String date) {
        if (date == null || date.isEmpty()) return false;
        if (isDaily()) return true;
        if (!isWeekly()) return false;
        int iso = isoWeekday(date);
        return iso != 0 && repeatWeekdays != null && repeatWeekdays.contains(iso);
    }

    /** 从 date（含当天）起下一个到期日；每日即当天；每周没有选日子则返回 null。 */
    public String nextDueOnOrAfter(String date) {
        if (date == null) return null;
        if (isDaily()) return date;
        if (!isWeekly() || repeatWeekdays == null || repeatWeekdays.isEmpty()) return null;
        String d = date;
        for (int i = 0; i < 8; i++) {
            if (isDueOn(d)) return d;
            d = DayUtil.addDays(d, 1);
        }
        return null;
    }

    /** 用于列表展示：每周一、三、五 / Weekly Mon, Wed, Fri。 */
    public String weeklyLabel() { return weeklyLabel(Strings.en()); }
    public String weeklyLabel(boolean en) {
        if (repeatWeekdays == null || repeatWeekdays.isEmpty()) {
            return en ? "Weekly" : "每周";
        }
        String[] zh = {"一", "二", "三", "四", "五", "六", "日"};
        String[] enN = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        StringBuilder sb = new StringBuilder(en ? "Weekly " : "每周");
        for (int i = 0; i < repeatWeekdays.size(); i++) {
            if (i > 0) sb.append(en ? ", " : "、");
            int iso = repeatWeekdays.get(i);
            if (iso < 1 || iso > 7) continue;
            sb.append(en ? enN[iso - 1] : zh[iso - 1]);
        }
        return sb.toString();
    }

    public boolean isDoneToday() {
        if (isRecurring()) return doneDates.contains(today());
        return "done".equals(status);
    }

    /** 某一天是否被「跳过今天」跳过（v1.79.0）；date 为 null 时恒 false。 */
    public boolean isSkippedOn(String date) {
        return date != null && skippedDates != null && skippedDates.contains(date);
    }

    public void markDone() {
        if (isRecurring()) {
            String d = today();
            if (!doneDates.contains(d)) doneDates.add(d);
        } else {
            status = "done";
        }
        touch();
    }

    public void markUndone() {
        if (isRecurring()) doneDates.remove(today());
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
        o.put("alarmMode", alarmMode == null ? ALARM_FOLLOW : alarmMode);
        o.put("repeat", repeat);
        JSONArray wd = new JSONArray();
        if (repeatWeekdays != null) for (Integer d : repeatWeekdays) if (d != null) wd.put(d.intValue());
        o.put("repeatWeekdays", wd);
        o.put("assignee", assignee == null ? ASSIGN_SELF : assignee);
        o.put("aiState", aiState == null ? JSONObject.NULL : aiState);
        o.put("durationMinutes", durationMinutes);
        o.put("actualMinutes", actualMinutes);
        o.put("priority", priority == null ? JSONObject.NULL : priority);
        o.put("status", status);
        o.put("autoStart", autoStart);
        o.put("note", note);
        o.put("description", description == null ? "" : description);
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
        JSONArray skd = new JSONArray();
        if (skippedDates != null) for (String s : skippedDates) skd.put(s);
        o.put("skippedDates", skd);
        o.put("deleted", deleted);
        o.put("createdAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date(createdAtMs)));
        o.put("updatedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date(updatedAtMs)));
        o.put("createdAtMs", createdAtMs);
        o.put("updatedAtMs", updatedAtMs);
        return o;
    }

// ---- 优先级四档（v1.26.0：must 必做 ＞ important 重要 ＞ secondary 次要 ＞ leisure 休息） ----
    /** 档位序：must=0、important=1、secondary=2（含 null 与未知值）、leisure=3。 */
    public static int priorityRank(String p) {
        if ("must".equals(p)) return 0;
        if ("important".equals(p)) return 1;
        if ("leisure".equals(p)) return 3;
        return 2;
    }

    /** 档位文案（界面与日历 tag 共用）。 */
    public static String priorityLabel(String p, boolean en) {
        switch (priorityRank(p)) {
            case 0: return en ? "Must" : "必做";
            case 1: return en ? "Important" : "重要";
            case 3: return en ? "Leisure" : "休息";
            default: return en ? "Secondary" : "次要";
        }
    }

    /** 是否休息档（娱乐类：永不自动排程、不进时间账、不参与滚入与落差清单）。 */
    public static boolean isLeisure(String p) { return "leisure".equals(p); }

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
        t.alarmMode = normAlarmMode(o.optString("alarmMode", ALARM_FOLLOW));
        t.repeat = o.optString("repeat", "none");
        JSONArray wd = o.optJSONArray("repeatWeekdays");
        if (wd != null) {
            for (int i = 0; i < wd.length(); i++) {
                int n = wd.optInt(i, 0);
                if (n >= 1 && n <= 7 && !t.repeatWeekdays.contains(n)) t.repeatWeekdays.add(n);
            }
            java.util.Collections.sort(t.repeatWeekdays);
        } else {
            // 兼容本机曾把星期写在 repeat 字符串里（weekly:1,2,3）
            if (t.repeat != null && t.repeat.startsWith("weekly:")) {
                t.repeatWeekdays = weekdaysFromStr(t.repeat.substring(7));
                t.repeat = REPEAT_WEEKLY;
            }
        }
        // 老本地数据无此两字段时 optString 给默认：assignee=self、aiState=null
        t.assignee = normAssignee(o.optString("assignee", ASSIGN_SELF));
        t.aiState = o.isNull("aiState") ? null : o.optString("aiState", null);
        if (t.aiState != null && t.aiState.isEmpty()) t.aiState = null;
        t.durationMinutes = o.optInt("durationMinutes", 30);
        t.actualMinutes = o.optInt("actualMinutes", 0);

        t.priority = o.isNull("priority") ? null : o.optString("priority", null);
        if (t.priority != null && t.priority.isEmpty()) t.priority = null;
        t.status = o.optString("status", "todo");
        t.autoStart = o.optBoolean("autoStart", false);
        t.note = o.optString("note", "");
        t.description = o.optString("description", "");
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
        // 老本地数据无此字段时留空表（v1.79.0）
        JSONArray skd = o.optJSONArray("skippedDates");
        if (skd != null) for (int i = 0; i < skd.length(); i++) t.skippedDates.add(skd.getString(i));
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
