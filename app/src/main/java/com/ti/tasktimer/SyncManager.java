package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 与 Google Drive 桥的同步：用户一次性通过系统文件选择器选中 Drive 里的
 * _电子大脑/task list/tasks.json（SAF 授权，免 Google SDK），之后读写都走该 Uri。
 * 合并规则：按任务 id 对齐，同一任务取 updatedAtMs 大的一份（新者胜），删除墓碑同样参与比较。
 */
public class SyncManager {
    private static final String PREF = "sync";
    private static final String KEY_URI = "tasks_json_uri";
    private static final String KEY_SCHEDULE = "schedule_json";
    private static final String KEY_GCAL = "gcal_json";
    private static final String KEY_RESCHEDULE = "reschedule_json";

    public static void setUri(Context c, Uri uri) {
        prefs(c).edit().putString(KEY_URI, uri.toString()).apply();
    }

    public static Uri getUri(Context c) {
        String s = prefs(c).getString(KEY_URI, null);
        return s == null ? null : Uri.parse(s);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void setLastResult(Context c, String text) {
        prefs(c).edit().putString("last_result", text).apply();
    }

    public static String lastResult(Context c) {
        return prefs(c).getString("last_result", "");
    }

    // ---- 实时同步开关与 autoStart 防重复 ----
    public static boolean realtimeEnabled(Context c) {
        // v1.9.0 起设置页不再提供「实时同步」开关：云端（Supabase）模式实时推送常开；
        // 桥模式仍读原偏好（30 秒轮询行为不变）。
        if (isSupabaseMode(c)) return true;
        return prefs(c).getBoolean("realtime", true);
    }

    public static void setRealtimeEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("realtime", on).apply();
    }

    public static boolean autoStartHandled(Context c, String taskId) {
        return prefs(c).getStringSet("autostart_handled", new java.util.HashSet<>()).contains(taskId);
    }

    public static void markAutoStartHandled(Context c, String taskId) {
        java.util.Set<String> s = new java.util.HashSet<>(
                prefs(c).getStringSet("autostart_handled", new java.util.HashSet<>()));
        s.add(taskId);
        prefs(c).edit().putStringSet("autostart_handled", s).apply();
    }

    // ---- 同步模式（v1.7.0）：登录过 Supabase 后进入 Supabase 模式，Drive 桥轮询停用 ----
    public static boolean isSupabaseMode(Context c) {
        return prefs(c).getBoolean("sb_mode", false);
    }

    public static void setSupabaseMode(Context c, boolean on) {
        prefs(c).edit().putBoolean("sb_mode", on).apply();
    }

    /** 同步总入口：Supabase 模式走 SbSync，否则走桥。MainActivity / CalendarActivity 都从这里进。 */
    public static String syncNowSmart(Context c, TaskStore store) {
        return isSupabaseMode(c) ? SbSync.syncNow(c, store) : syncNow(c, store);
    }

    /** Supabase 同步把云端 schedule 写进与桥时代相同的本地缓存（CalendarActivity 读口不变）。 */
    public static void cacheSchedule(Context c, JSONObject sch) {
        if (sch != null) prefs(c).edit().putString(KEY_SCHEDULE, sch.toString()).apply();
    }

    /** Supabase 同步把云端 gcal 写进与桥时代相同的本地缓存（只读渲染，App 不改）。 */
    public static void cacheGcal(Context c, JSONObject gcal) {
        if (gcal != null) prefs(c).edit().putString(KEY_GCAL, gcal.toString()).apply();
    }

    /**
     * 「问 AI 重排」请求状态缓存（v1.20.0）：云端 user_state.reschedule_request 的
     * 本地镜像，同步时由 SbSync 写入、日历页读它渲染按钮态。null＝云端为空，清缓存。
     * 结构：{"date":"YYYY-MM-DD","requested_at":ISO,"status":"pending|done|failed","message"?}
     */
    public static void cacheReschedule(Context c, JSONObject req) {
        if (req != null) prefs(c).edit().putString(KEY_RESCHEDULE, req.toString()).apply();
        else prefs(c).edit().remove(KEY_RESCHEDULE).apply();
    }

    /** 最近一次同步到的重排请求状态；从没请求过返回 null。 */
    public static JSONObject latestReschedule(Context c) {
        String s = prefs(c).getString(KEY_RESCHEDULE, null);
        if (s == null) return null;
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 执行一次同步；返回结果说明。必须在后台线程调用。 */
    public static String syncNow(Context c, TaskStore store) {
        Uri uri = getUri(c);
        if (uri == null) return "尚未选择桥文件：请点「同步」并在 Google Drive 里选中 _电子大脑/task list/tasks.json";
        try {
            // 1) 读远端
            RemoteSnapshot remote;
            try {
                remote = readRemote(c, uri);
            } catch (Exception e) {
                return "读取桥文件失败：" + e.getMessage();
            }
            int remoteVisible = 0;
            for (TaskItem t : remote.tasks.values()) if (!t.deleted) remoteVisible++;
            // 2) 合并：同 id 取 updatedAtMs 大者
            Map<String, TaskItem> merged = mergeTasks(remote.tasks, store.tasks);
            store.tasks.clear();
            store.tasks.addAll(merged.values());
            store.save();
            // 2.5) 屏幕守卫事件与桥上的 guardEvents 合并（只含时间/类别/置信度/证据文字，无截图）
            JSONArray mergedEvents = GuardEvents.merge(c, remote.guardEvents);
            int visible = 0;
            for (TaskItem t : store.tasks) if (!t.deleted) visible++;
            String counts = "共 " + visible + " 个任务（桥上读到 " + remoteVisible + " 个）";
            // 3) 没变化不回写：合并结果与读到的远端语义一致（任务 id→updatedAtMs 映射、
            //    guardEvents 键集合都相同）就跳过回写。手机 Drive 通道可能读到旧缓存，
            //    若每周期无条件整表回写，会把桥上刚写入的新内容用旧的盖掉。
            if (taskSignature(merged).equals(taskSignature(remote.tasks))
                    && eventKeys(mergedEvents).equals(eventKeys(remote.guardEvents))) {
                StartReminder.reschedule(c, store);
                AlarmScheduler.rescheduleAll(c, store);
                return "同步完成：" + counts + "，内容无变化，未回写（" + TaskItem.nowIso() + "）";
            }
            // 4) 写前复读：两次读之间远端若又变了（多半是 Muse 刚写入），以第二次为准重新合并再写
            Map<String, TaskItem> writeBase = merged;
            JSONArray writeEvents = mergedEvents;
            try {
                RemoteSnapshot remote2 = readRemote(c, uri);
                if (!taskSignature(remote2.tasks).equals(taskSignature(remote.tasks))
                        || !eventKeys(remote2.guardEvents).equals(eventKeys(remote.guardEvents))) {
                    writeBase = mergeTasks(remote2.tasks, merged.values());
                    writeEvents = GuardEvents.merge(c, remote2.guardEvents);
                    store.tasks.clear();
                    store.tasks.addAll(writeBase.values());
                    store.save();
                }
            } catch (Exception ignored) { }
            // 5) 回写远端
            if (!writeRemote(c, uri, store, writeEvents)) {
                StartReminder.reschedule(c, store);
                AlarmScheduler.rescheduleAll(c, store);
                return "合并完成，但回写桥文件失败（未能打开输出流）";
            }
            // 6) 写后校验：再读一次，若合并结果里非 deleted 的任务有缺失，
            //    把「校验读到的远端 ∪ 合并结果」再合并补写一次（最多一次，不循环）；校验读失败就跳过。
            try {
                RemoteSnapshot check = readRemote(c, uri);
                boolean missing = false;
                for (TaskItem t : writeBase.values()) {
                    if (!t.deleted && !check.tasks.containsKey(t.id)) { missing = true; break; }
                }
                if (missing) {
                    Map<String, TaskItem> union = mergeTasks(check.tasks, writeBase.values());
                    JSONArray unionEvents = GuardEvents.merge(c, check.guardEvents);
                    store.tasks.clear();
                    store.tasks.addAll(union.values());
                    store.save();
                    writeRemote(c, uri, store, unionEvents);
                }
            } catch (Exception ignored) { }
            StartReminder.reschedule(c, store);
            AlarmScheduler.rescheduleAll(c, store);
            return "同步完成：" + counts + "（" + TaskItem.nowIso() + "）";
        } catch (Exception e) {
            return "同步失败：" + e.getMessage();
        }
    }

    /** 远端桥文件的一次读取结果：任务表 + guardEvents（原样 JSONArray，桥上没有该字段时为 null）。 */
    private static class RemoteSnapshot {
        final Map<String, TaskItem> tasks = new HashMap<>();
        JSONArray guardEvents;
        JSONObject schedule;
    }

    /**
     * 最近一次从桥上读到的日程（schedule 顶层字段，Muse 排、App 只读）。
     * 日历页用它画今日时间表；桥上暂时没有时返回 null。
     */
    public static JSONObject latestSchedule(Context c) {
        String s = prefs(c).getString(KEY_SCHEDULE, null);
        if (s == null) return null;
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 桥上的 gcal 顶层字段（Muse 写入的 Google 日历事件，App 只渲染不改，v1.6.2）。
     * 结构：{"date":"YYYY-MM-DD","events":[{"id","title","start":"HH:mm","end":"HH:mm","allDay":false}]}。
     * 读到就缓存一份，回写整表时原样带回——和 schedule 一样只是保管员，防被整表回写吞掉。
     */
    public static JSONObject latestGcal(Context c) {
        String s = prefs(c).getString(KEY_GCAL, null);
        if (s == null) return null;
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 【App 对 schedule 的唯一写入口】（用户 2026-10-24 定案，随 v1.6.2 落地）
     *
     * 只允许两件事（v1.15.0 起）：① 用户在日历页把「今天」的某个计划块拖到了新时间，
     * 把那个块的 start/end 改掉（时长不变，本方法 updateScheduleBlock）；② 用户把一个
     * 未排任务从日历左栏拖进今天的日历，在落点新建一个计划块（addScheduleBlock）。
     * 除此之外的任何代码都不许动 schedule——它仍是 Muse 排的日程，本 App 只是保管员。
     *
     * 纪律（与 syncNow 同一条）：先读远端最新版 → 只改/只加命中的那个块 → 写回 →
     * 再读一次校验。任务表与 guardEvents 用刚读到的远端原样带回，本方法不碰它们。
     * 写成功时同步更新本地 schedule 缓存，日历页立刻能重渲染。
     * 必须在后台线程调用。返回 null 表示成功，否则是可读的失败原因。
     */
    public static String updateScheduleBlock(Context c, String taskId, String oldStart,
                                             String newStart, String newEnd) {
        if (isSupabaseMode(c)) return SbSync.updateScheduleBlock(c, taskId, oldStart, newStart, newEnd);
        Uri uri = getUri(c);
        if (uri == null) return "尚未选择桥文件，无法保存新时间";
        try {
            RemoteSnapshot remote = readRemote(c, uri);
            JSONObject sch = remote.schedule;
            if (sch == null) return "桥上现在没有日程";
            if (!TaskItem.today().equals(sch.optString("date", ""))) return "桥上日程不是今天的，先让 Muse 重排";
            JSONArray blocks = sch.optJSONArray("blocks");
            if (blocks == null) return "日程里没有时段块";
            boolean hit = false;
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject o = blocks.optJSONObject(i);
                if (o == null) continue;
                if (taskId.equals(o.optString("taskId", ""))
                        && oldStart.equals(o.optString("start", ""))) {
                    o.put("start", newStart);
                    o.put("end", newEnd);
                    hit = true;
                    break;
                }
            }
            if (!hit) return "那个时段在桥上已经变了，页面刷新后请重新拖一次";
            sch.put("updatedAtMs", System.currentTimeMillis());
            sch.put("updatedBy", "KnowDo App（用户在日历页拖拽）");
            prefs(c).edit().putString(KEY_SCHEDULE, sch.toString()).apply();
            // 写回：任务表/守卫事件用刚读到的远端原样，schedule 用刚改好的这一份覆盖
            TaskStore carrier = new TaskStore(c);
            carrier.tasks.clear();
            carrier.tasks.addAll(remote.tasks.values());
            if (!writeRemote(c, uri, carrier, remote.guardEvents, sch)) {
                return "回写桥文件失败（未能打开输出流）";
            }
            // 回读校验：那块确实已是新时间才算落定
            RemoteSnapshot check = readRemote(c, uri);
            boolean ok = false;
            if (check.schedule != null) {
                JSONArray cb = check.schedule.optJSONArray("blocks");
                if (cb != null) for (int i = 0; i < cb.length(); i++) {
                    JSONObject o = cb.optJSONObject(i);
                    if (o == null) continue;
                    if (taskId.equals(o.optString("taskId", ""))
                            && newStart.equals(o.optString("start", ""))
                            && newEnd.equals(o.optString("end", ""))) { ok = true; break; }
                }
            }
            return ok ? null : "已写回但回读没看到新时间，请刷新页面确认";
        } catch (Exception e) {
            return "保存新时间失败：" + e.getMessage();
        }
    }

    /**
     * 【唯一写入口之二，v1.15.0】用户把一个未排任务拖进今天的日历：在落点新建计划块。
     * 纪律与 updateScheduleBlock 同一条：先读远端最新版 → 只往 blocks 里加这一块 →
     * 写回 → 回读校验该块已在。远端还没有今天的日程（无 schedule 或 date 不是今天）
     * 时，就地为今天新建一份只含这一块的日程——「拖进来」本身就是给今天排，
     * 不因此报错。任务表/guardEvents 用远端原样带回，一个字不改。
     * 必须在后台线程调用。返回 null 表示成功，否则是可读的失败原因。
     */
    public static String addScheduleBlock(Context c, String taskId, String newStart, String newEnd) {
        if (isSupabaseMode(c)) return SbSync.addScheduleBlock(c, taskId, newStart, newEnd);
        Uri uri = getUri(c);
        if (uri == null) return "尚未选择桥文件，无法保存计划块";
        try {
            RemoteSnapshot remote = readRemote(c, uri);
            JSONObject sch = remote.schedule;
            if (sch == null || !TaskItem.today().equals(sch.optString("date", ""))) {
                sch = new JSONObject();
                sch.put("date", TaskItem.today());
                sch.put("blocks", new JSONArray());
            }
            JSONArray blocks = sch.optJSONArray("blocks");
            if (blocks == null) { blocks = new JSONArray(); sch.put("blocks", blocks); }
            JSONObject nb = new JSONObject();
            nb.put("taskId", taskId);
            nb.put("start", newStart);
            nb.put("end", newEnd);
            blocks.put(nb);
            sch.put("updatedAtMs", System.currentTimeMillis());
            sch.put("updatedBy", "KnowDo App（用户把任务拖进日历）");
            prefs(c).edit().putString(KEY_SCHEDULE, sch.toString()).apply();
            TaskStore carrier = new TaskStore(c);
            carrier.tasks.clear();
            carrier.tasks.addAll(remote.tasks.values());
            if (!writeRemote(c, uri, carrier, remote.guardEvents, sch)) {
                return "回写桥文件失败（未能打开输出流）";
            }
            RemoteSnapshot check = readRemote(c, uri);
            boolean ok = false;
            if (check.schedule != null) {
                JSONArray cb = check.schedule.optJSONArray("blocks");
                if (cb != null) for (int i = 0; i < cb.length(); i++) {
                    JSONObject o = cb.optJSONObject(i);
                    if (o == null) continue;
                    if (taskId.equals(o.optString("taskId", ""))
                            && newStart.equals(o.optString("start", ""))
                            && newEnd.equals(o.optString("end", ""))) { ok = true; break; }
                }
            }
            return ok ? null : "已写回但回读没看到新块，请刷新页面确认";
        } catch (Exception e) {
            return "保存计划块失败：" + e.getMessage();
        }
    }

    /**
     * 【App 对 schedule 的写入口之三，v1.23.0】本地自动排程落盘：用户定案的口径是
     * 「新增与滚入自动排」——今日必做（含滚入）没排上的，App 自己按 ×1.5 填空档。
     * 纪律与既有写入口同一条：先回读远端最新 schedule 当底（外部刚写入的块都在里面，
     * 一个不动）→ 只往空档里加新块 → 写回 → 更新本地缓存。远端日程不是今天的，
     * 底按空表算（旧日程已过期）。没有新块可加时不写远端，直接返回计划。
     * 桥文件未选/离线读不到远端时：只按本地缓存算计划、written=false，不落盘，
     * 等下一次触发（回前台/任务变更）再试。必须在后台线程调用。
     */
    public static LocalAutoScheduler.Plan autoFillSchedule(
            Context c, List<LocalAutoScheduler.Candidate> cands, String updatedBy) {
        if (isSupabaseMode(c)) return SbSync.autoFillSchedule(c, cands, updatedBy);
        String today = TaskItem.today();
        int nowMin = DayAccount.nowMinutes();
        Uri uri = getUri(c);
        if (uri == null) {
            return LocalAutoScheduler.plan(cands,
                    LocalAutoScheduler.blocksOf(latestSchedule(c), today), nowMin);
        }
        try {
            RemoteSnapshot remote = readRemote(c, uri);
            List<LocalAutoScheduler.Block> base =
                    LocalAutoScheduler.blocksOf(remote.schedule, today);
            LocalAutoScheduler.Plan plan = LocalAutoScheduler.plan(cands, base, nowMin);
            if (plan.additions.isEmpty()) return plan;
            List<LocalAutoScheduler.Block> merged = new ArrayList<>(base);
            merged.addAll(plan.additions);
            JSONObject sch = LocalAutoScheduler.scheduleJson(today, merged);
            sch.put("updatedAtMs", System.currentTimeMillis());
            sch.put("updatedBy", updatedBy);
            prefs(c).edit().putString(KEY_SCHEDULE, sch.toString()).apply();
            TaskStore carrier = new TaskStore(c);
            carrier.tasks.clear();
            carrier.tasks.addAll(remote.tasks.values());
            plan.written = writeRemote(c, uri, carrier, remote.guardEvents, sch);
            if (!plan.written) {
                // 写失败：本地缓存回滚到远端那份，别让界面显示没落盘的排程
                if (remote.schedule != null) {
                    prefs(c).edit().putString(KEY_SCHEDULE, remote.schedule.toString()).apply();
                }
            }
            return plan;
        } catch (Exception e) {
            return LocalAutoScheduler.plan(cands,
                    LocalAutoScheduler.blocksOf(latestSchedule(c), today), nowMin);
        }
    }

    /**
     * 【App 对 schedule 的写入口之四，v1.23.0】聊天排程提案卡点「应用」落盘：
     * 用户在对话里亲口拍板的一版新排程。纪律：先回读远端最新版合并
     * （LocalAutoScheduler.mergeProposal——已过去/已完成的块保留，其余按卡落盘）
     * → 写回 → 回读校验块数。必须在后台线程调用。返回 null 表示成功，否则失败原因。
     */
    public static String applyScheduleProposal(Context c, TaskStore store,
                                               List<LocalAutoScheduler.Block> cardBlocks,
                                               String updatedBy) {
        if (isSupabaseMode(c)) return SbSync.applyScheduleProposal(c, store, cardBlocks, updatedBy);
        Uri uri = getUri(c);
        if (uri == null) return "尚未选择桥文件，无法保存排程";
        String today = TaskItem.today();
        try {
            RemoteSnapshot remote = readRemote(c, uri);
            List<LocalAutoScheduler.Block> base =
                    LocalAutoScheduler.blocksOf(remote.schedule, today);
            List<LocalAutoScheduler.Block> merged = LocalAutoScheduler.mergeProposal(
                    base, cardBlocks, store, DayAccount.nowMinutes());
            JSONObject sch = LocalAutoScheduler.scheduleJson(today, merged);
            sch.put("updatedAtMs", System.currentTimeMillis());
            sch.put("updatedBy", updatedBy);
            prefs(c).edit().putString(KEY_SCHEDULE, sch.toString()).apply();
            TaskStore carrier = new TaskStore(c);
            carrier.tasks.clear();
            carrier.tasks.addAll(remote.tasks.values());
            if (!writeRemote(c, uri, carrier, remote.guardEvents, sch)) {
                return "回写桥文件失败（未能打开输出流）";
            }
            RemoteSnapshot check = readRemote(c, uri);
            List<LocalAutoScheduler.Block> chk =
                    LocalAutoScheduler.blocksOf(check.schedule, today);
            return chk.size() == merged.size() ? null : "已写回但回读块数对不上，请刷新日历确认";
        } catch (Exception e) {
            return "保存排程失败：" + e.getMessage();
        }
    }

    private static RemoteSnapshot readRemote(Context c, Uri uri) throws Exception {
        RemoteSnapshot snap = new RemoteSnapshot();
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) return snap;
        String s = TaskStore.readAll(in);
        JSONObject root = new JSONObject(s);
        JSONArray arr = root.optJSONArray("tasks");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            TaskItem t = TaskItem.fromJson(arr.getJSONObject(i));
            snap.tasks.put(t.id, t);
        }
        snap.guardEvents = root.optJSONArray("guardEvents");
        // schedule 是 Muse 维护的顶层字段：App 只读不改。读到就存一份到本地，
        // 回写桥文件时原样带回——否则整表回写会把 Muse 刚排好的日程抹掉。
        JSONObject sch = root.optJSONObject("schedule");
        if (sch != null) {
            snap.schedule = sch;
            prefs(c).edit().putString(KEY_SCHEDULE, sch.toString()).apply();
        }
        // gcal（Google 日历事件）同理：Muse 写入、App 只渲染，读到就缓存，
        // 回写时原样带回，绝不能在整表回写时把它吞掉（v1.6.2）。
        JSONObject gcal = root.optJSONObject("gcal");
        if (gcal != null) {
            prefs(c).edit().putString(KEY_GCAL, gcal.toString()).apply();
        }
        return snap;
    }

    /** 以 base 为底，把 others 按 id 合并进来：同 id 取 updatedAtMs 大者（新者胜，删除墓碑同样参与比较）。 */
    private static Map<String, TaskItem> mergeTasks(Map<String, TaskItem> base, Iterable<TaskItem> others) {
        Map<String, TaskItem> merged = new HashMap<>(base);
        for (TaskItem t : others) {
            TaskItem r = merged.get(t.id);
            if (r == null || t.updatedAtMs >= r.updatedAtMs) merged.put(t.id, t);
        }
        return merged;
    }

    /** 任务集合的语义签名：id → updatedAtMs。两边签名相等即认为内容一致（不比原始字符串）。 */
    private static Map<String, Long> taskSignature(Map<String, TaskItem> m) {
        Map<String, Long> sig = new HashMap<>();
        for (Map.Entry<String, TaskItem> e : m.entrySet()) sig.put(e.getKey(), e.getValue().updatedAtMs);
        return sig;
    }

    /** guardEvents 的语义签名：与 GuardEvents 去重口径相同的键集合（ts|类别|任务 id）。 */
    private static Set<String> eventKeys(JSONArray arr) {
        Set<String> keys = new HashSet<>();
        if (arr == null) return keys;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            keys.add(o.optLong("ts", 0) + "|" + o.optString("category", "") + "|" + o.optString("taskId", ""));
        }
        return keys;
    }

    /** 把 store 当前任务表 + 给定 guardEvents 整表写回桥文件；打不开输出流返回 false。 */
    private static boolean writeRemote(Context c, Uri uri, TaskStore store, JSONArray events) throws Exception {
        return writeRemote(c, uri, store, events, null);
    }

    /**
     * 整表写回的实现：schedule 默认用本地缓存的那份原样带回；scheduleOverride 非空时用它
     * （只有 updateScheduleBlock 这一个入口会传，它刚按纪律改好了一个块）。
     * gcal 与 schedule 同等待遇：读到过的那份原样带回，一个字不改。
     */
    private static boolean writeRemote(Context c, Uri uri, TaskStore store, JSONArray events,
                                       JSONObject scheduleOverride) throws Exception {
        OutputStream out = c.getContentResolver().openOutputStream(uri, "wt");
        if (out == null) return false;
        JSONObject root = store.toRootJson();
        root.put("guardEvents", events);
        // 把最近一次读到的 schedule 原样带回（内容一字不改，App 只是保管员）
        if (scheduleOverride != null) {
            root.put("schedule", scheduleOverride);
        } else {
            String sch = prefs(c).getString(KEY_SCHEDULE, null);
            if (sch != null) {
                try { root.put("schedule", new JSONObject(sch)); } catch (Exception ignored) { }
            }
        }
        String gcal = prefs(c).getString(KEY_GCAL, null);
        if (gcal != null) {
            try { root.put("gcal", new JSONObject(gcal)); } catch (Exception ignored) { }
        }
        out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        out.close();
        return true;
    }
}
