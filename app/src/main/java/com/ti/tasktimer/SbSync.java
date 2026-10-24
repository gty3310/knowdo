package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Supabase 同步引擎（v1.7.0）：与 Drive 桥时代完全同一套合并纪律——
 * 按任务 id 对齐、updated_at 新者胜、删除墓碑参与比较不复活、本地字段保全。
 * - 首次登录且云端为空：本机任务全量 upsert 成该用户的数据（播种），然后拉取合并，本地数据不丢。
 * - 之后：拉取云端全量 → 合并进本地 → 把本地胜出的差异 upsert 回云端（state-diff 即离线队列：
 *   断网时差异留在本地与签名之间，下次 syncNow 先推后拉自动收敛）。
 * - user_state 一行：schedule/gcal 只读缓存（与桥时代同一个本地缓存口，CalendarActivity 无感），
 *   guard_events 双向合并，timer_state 交给 SbTimerSync；
 *   v1.12.0 加 self_checks 同纪律合并与 aspiration 单键合并（两列均先探测再碰）。
 * 全部方法只能在后台线程调用。
 */
public final class SbSync {
    private static final String PREF = "sbsync";
    private static final AtomicBoolean SYNCING = new AtomicBoolean(false);
    private static final Object STORE_LOCK = new Object();

    private SbSync() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static boolean isSyncing() { return SYNCING.get(); }

    // ---------------- 状态行 ----------------
    public static String statusText(Context c, TaskStore store) {
        if (!Supa.isLoggedIn(c)) return "Supabase：未登录（点「账号」登录）";
        if (SYNCING.get()) return "Supabase：同步中…";
        int n = pendingCount(c, store);
        if (n > 0) return "Supabase：待同步 " + n + " 条";
        long last = prefs(c).getLong("last_sync_at", 0);
        if (last > 0) {
            String hm = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                    .format(new java.util.Date(last));
            return "Supabase：已同步 " + hm;
        }
        return "Supabase：已登录，等待首次同步";
    }

    /** 待同步条数：本地比上次已知云端签名更新的任务数（外加一条待写的计时状态）。 */
    public static int pendingCount(Context c, TaskStore store) {
        Map<String, Long> sig = loadSig(c);
        int n = 0;
        for (TaskItem t : store.tasks) {
            Long s = sig.get(t.id);
            if (s == null || t.updatedAtMs > s) n++;
        }
        if (SbTimerSync.pendingJson(c) != null) n++;
        return n;
    }

    // ---------------- 主同步 ----------------
    public static String syncNow(Context c, TaskStore store) {
        if (!Supa.isLoggedIn(c)) return "未登录 Supabase：点「账号」登录后自动同步";
        if (!SYNCING.compareAndSet(false, true)) return "同步中…";
        try {
            return doSync(c, store);
        } catch (Supa.Err e) {
            if (e.network) return "离线：本地变更已保留，联网后自动同步（待同步 "
                    + pendingCount(c, store) + " 条）";
            if (e.status == 401 || e.status == 403) return "Supabase 登录已过期或无权限，"
                    + "请到「账号」重新登录（待同步 " + pendingCount(c, store) + " 条）";
            String m = e.getMessage();
            return "Supabase 同步失败（" + e.status + "）：" + (m == null ? "" : m.substring(0, Math.min(120, m.length())));
        } catch (Exception e) {
            return "Supabase 同步失败：" + e.getMessage();
        } finally {
            SYNCING.set(false);
        }
    }

    private static String doSync(Context c, TaskStore store) throws Exception {
        String uid = Supa.userId(c);
        boolean hasExtra = probeExtraCols(c);
        boolean hasAlarm = probeAlarmCols(c);
        boolean hasSub = probeSubtaskCols(c);

        // 1) 拉云端任务全量
        Map<String, TaskItem> remote = new HashMap<>();
        Map<String, Long> remoteMs = new HashMap<>();
        pullTasks(c, store, remote, remoteMs);

        // 2) 首次登录且云端为空 → 播种：本机全量 upsert 成本人数据，再拉一次拿权威快照
        if (remote.isEmpty() && !prefs(c).getBoolean("seeded_" + uid, false)) {
            if (!store.tasks.isEmpty()) {
                JSONArray payload = new JSONArray();
                for (TaskItem t : store.tasks) payload.put(SbMapper.toRow(t, uid, hasExtra, hasAlarm, hasSub));
                JSONArray back = Supa.restUpsert(c, "tasks", "id", payload);
                synchronized (STORE_LOCK) {
                    store.load(); // 先拿磁盘最新状态再对齐时间戳，别用旧快照整表盖盘（v1.14.0 修复）
                    adoptServerTimes(back, store, null);
                    store.save();
                }
            }
            prefs(c).edit().putBoolean("seeded_" + uid, true).apply();
            remote.clear();
            remoteMs.clear();
            pullTasks(c, store, remote, remoteMs);
        }

        // 3) 合并（新者胜、墓碑参与）→ 本地落盘
        //    v1.14.0 修复「完成被弹回」根因：合并前必须在锁内先 store.load() 重读磁盘——
        //    本轮同步从拉取到此已经历多次网络往返（数秒），用户在这段时间里的本地改动
        //    （如点完成）只在磁盘上；拿同步开始时的内存快照合并再整表 save，会把改动盖回旧值，
        //    且第 4 步的差异比较也基于旧快照，完成根本推不上云。
        Map<String, TaskItem> merged;
        synchronized (STORE_LOCK) {
            store.load();
            merged = mergeTasks(remote, store.tasks);
            store.tasks.clear();
            store.tasks.addAll(merged.values());
            store.save();
        }

        // 4) 差异推送：本地胜出且比云端快照新的，一批 upsert；回读服务端 updated_at 对齐本地
        java.util.List<TaskItem> toPush = new java.util.ArrayList<>();
        Map<String, Long> pushedMs = new HashMap<>();
        for (TaskItem t : merged.values()) {
            Long r = remoteMs.get(t.id);
            if (r == null || t.updatedAtMs > r) { toPush.add(t); pushedMs.put(t.id, t.updatedAtMs); }
        }
        if (!toPush.isEmpty()) {
            JSONArray payload = new JSONArray();
            for (TaskItem t : toPush) payload.put(SbMapper.toRow(t, uid, hasExtra, hasAlarm, hasSub));
            JSONArray back = Supa.restUpsert(c, "tasks", "id", payload);
            synchronized (STORE_LOCK) {
                store.load(); // 同上：对齐前先重读，且仅当条目未被更新的本地改动动过时才对齐
                adoptServerTimes(back, store, pushedMs);
                store.save();
                for (TaskItem t : toPush) {
                    TaskItem cur = store.byId(t.id);
                    if (cur != null) remoteMs.put(t.id, cur.updatedAtMs); // 签名记对齐后的值
                }
            }
        }
        saveSig(c, remoteMs);

        // 5) user_state：schedule/gcal 缓存、guard_events 合并、timer_state 对账与补写
        syncUserState(c, uid);

        prefs(c).edit().putLong("last_sync_at", System.currentTimeMillis()).apply();
        StartReminder.reschedule(c, store);
        AlarmScheduler.rescheduleAll(c, store);

        // 6) autoStart 消费（合并之后，与桥时代同一语义）
        handleAutoStart(c, store);

        int visible = 0;
        for (TaskItem t : store.tasks) if (!t.deleted) visible++;
        return "Supabase 同步完成：共 " + visible + " 个任务（" + TaskItem.nowIso() + "）";
    }

    private static void pullTasks(Context c, TaskStore store,
                                  Map<String, TaskItem> remote, Map<String, Long> remoteMs) throws Exception {
        JSONArray rows = Supa.restGet(c, "tasks?select=*");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            TaskItem t = SbMapper.fromRow(row, store.byId(row.optString("id", "")));
            remote.put(t.id, t);
            remoteMs.put(t.id, t.updatedAtMs);
        }
    }

    /**
     * upsert 返回的 representation 里带服务端 updated_at：对齐本地，避免下一轮把自己刚写的又当新版。
     * expected 非空时（第 4 步差异推送）只对齐「updatedAtMs 仍等于本次推送值」的条目——
     * 推送往返期间用户又改过的条目，本地时间戳更新，不许被服务端时间倒灌（v1.14.0）。
     * 调用方必须已在 STORE_LOCK 内 store.load() 过。
     */
    private static void adoptServerTimes(JSONArray back, TaskStore store, Map<String, Long> expected) {
        if (back == null) return;
        for (int i = 0; i < back.length(); i++) {
            JSONObject row = back.optJSONObject(i);
            if (row == null) continue;
            TaskItem t = store.byId(row.optString("id", ""));
            if (t == null) continue;
            if (expected != null) {
                Long exp = expected.get(t.id);
                if (exp == null || t.updatedAtMs != exp) continue;
            }
            long ms = SbMapper.parseIsoMs(row.optString("updated_at", ""));
            if (ms > t.updatedAtMs) t.updatedAtMs = ms;
        }
    }

    private static Map<String, TaskItem> mergeTasks(Map<String, TaskItem> base, Iterable<TaskItem> others) {
        Map<String, TaskItem> merged = new HashMap<>(base);
        for (TaskItem t : others) {
            TaskItem r = merged.get(t.id);
            if (r == null || t.updatedAtMs >= r.updatedAtMs) merged.put(t.id, t);
        }
        return merged;
    }

    // ---------------- user_state ----------------
    private static void syncUserState(Context c, String uid) throws Exception {
        JSONArray us = Supa.restGet(c, "user_state?select=*");
        JSONObject urow = us.length() > 0 ? us.getJSONObject(0) : null;
        JSONObject write = new JSONObject();
        write.put("user_id", uid);
        boolean needWrite = false;
        // v1.12.0 两列（aspiration / self_checks）按附加列同法探测：列未就绪就不碰这两个键，
        // 免得整行 upsert 因未知列被服务端拒掉、连累 timer_state 等既有回写
        boolean hasAsp = probeUserStateCol(c, "aspiration");
        boolean hasSc = probeUserStateCol(c, "self_checks");
        boolean hasNag = probeUserStateCol(c, "idle_nag"); // v1.14.0：列未就绪只用本地，绝不碰此键

        if (urow != null) {
            JSONObject sch = urow.isNull("schedule") ? null : urow.optJSONObject("schedule");
            if (sch != null) SyncManager.cacheSchedule(c, sch);
            JSONObject g = urow.isNull("gcal") ? null : urow.optJSONObject("gcal");
            if (g != null) SyncManager.cacheGcal(c, g);
            JSONArray remoteEv = urow.isNull("guard_events") ? null : urow.optJSONArray("guard_events");
            JSONArray mergedEv = GuardEvents.merge(c, remoteEv);
            if (!eventKeys(mergedEv).equals(eventKeys(remoteEv))) {
                write.put("guard_events", mergedEv);
                needWrite = true;
            }
            if (hasSc) {
                JSONArray remoteSc = urow.isNull("self_checks") ? null : urow.optJSONArray("self_checks");
                JSONArray mergedSc = SelfChecks.merge(c, remoteSc);
                if (!SelfChecks.keys(mergedSc).equals(SelfChecks.keys(remoteSc))) {
                    write.put("self_checks", mergedSc);
                    needWrite = true;
                }
            }
            if (hasAsp) {
                // 立志合并：本机为空且云端有值→采用云端（Jarvis 预填/另一台设备所写）；
                // 本机有值且与云端不同→回写本机值。两种情况都只动 aspiration 一个键。
                String remoteAsp = Aspiration.fromCloudRow(urow);
                String localAsp = Aspiration.get(c);
                if (localAsp.isEmpty() && !remoteAsp.isEmpty()) {
                    Aspiration.setLocal(c, remoteAsp);
                } else if (!localAsp.isEmpty() && !localAsp.equals(remoteAsp)) {
                    write.put("aspiration", localAsp);
                    needWrite = true;
                }
            }
            if (hasNag) {
                // 空档催促设置合并（v1.14.0，与立志同口径）：本机从未存过且云端有值→采用云端；
                // 本机有值且与云端不同→回写本机值。只动 idle_nag 一个键。
                JSONObject remoteNag = urow.isNull("idle_nag") ? null : urow.optJSONObject("idle_nag");
                if (!IdleNag.hasLocal(c) && remoteNag != null) {
                    IdleNag.adoptCloud(c, remoteNag);
                    IdleAlarm.reevaluate(c);
                } else if (IdleNag.hasLocal(c) && !IdleNag.cloudEquals(c, remoteNag)) {
                    write.put("idle_nag", IdleNag.toCloudJson(c));
                    needWrite = true;
                }
            }
            if (!urow.isNull("timer_state")) {
                SbTimerSync.onRemoteState(c, urow.optJSONObject("timer_state"));
            }
        } else {
            JSONArray local = GuardEvents.loadArray(c);
            if (local.length() > 0) {
                write.put("guard_events", local);
                needWrite = true;
            }
            if (hasSc) {
                JSONArray localSc = SelfChecks.loadArray(c);
                if (localSc.length() > 0) {
                    write.put("self_checks", localSc);
                    needWrite = true;
                }
            }
            if (hasAsp) {
                String localAsp = Aspiration.get(c);
                if (!localAsp.isEmpty()) {
                    write.put("aspiration", localAsp);
                    needWrite = true;
                }
            }
            if (hasNag && IdleNag.hasLocal(c)) {
                write.put("idle_nag", IdleNag.toCloudJson(c));
                needWrite = true;
            }
        }
        JSONObject pendingTimer = SbTimerSync.pendingJson(c);
        if (pendingTimer != null) {
            write.put("timer_state", pendingTimer);
            needWrite = true;
        }
        if (needWrite) {
            Supa.restUpsertOne(c, "user_state", "user_id", write);
            if (pendingTimer != null) SbTimerSync.clearPending(c, pendingTimer.optLong("updated_at", 0));
        }
    }

    /** Realtime 推来的 user_state 行：缓存 schedule/gcal、合并守卫事件、应用计时状态。不在这里回写。 */
    public static void applyRemoteUserState(Context c, JSONObject row) {
        try {
            if (!row.isNull("schedule")) {
                JSONObject sch = row.optJSONObject("schedule");
                if (sch != null) SyncManager.cacheSchedule(c, sch);
            }
            if (!row.isNull("gcal")) {
                JSONObject g = row.optJSONObject("gcal");
                if (g != null) SyncManager.cacheGcal(c, g);
            }
            if (!row.isNull("guard_events")) {
                GuardEvents.merge(c, row.optJSONArray("guard_events"));
            }
            if (!row.isNull("self_checks")) {
                SelfChecks.merge(c, row.optJSONArray("self_checks"));
            }
            // 立志：本机还没写过时才采用云端推来的值（与全量同步同一口径），不在这里回写
            String remoteAsp = Aspiration.fromCloudRow(row);
            if (!remoteAsp.isEmpty() && Aspiration.get(c).isEmpty()) {
                Aspiration.setLocal(c, remoteAsp);
            }
            // 空档催促设置：本机从未存过时才采用云端推来的值（同口径），不在这里回写
            if (!row.isNull("idle_nag") && !IdleNag.hasLocal(c)) {
                JSONObject rn = row.optJSONObject("idle_nag");
                if (rn != null) {
                    IdleNag.adoptCloud(c, rn);
                    IdleAlarm.reevaluate(c);
                }
            }
            if (!row.isNull("timer_state")) {
                SbTimerSync.onRemoteState(c, row.optJSONObject("timer_state"));
            }
        } catch (Exception ignored) { }
    }

    /**
     * 日历页拖拽改时间（Supabase 模式下的 schedule 唯一写入口，纪律与桥时代一致）：
     * 读云端最新 user_state → 只改命中的那个块（taskId＋oldStart 双中）→ upsert → 回读校验。
     * 返回 null 表示成功，否则是可读的失败原因。
     */
    public static String updateScheduleBlock(Context c, String taskId, String oldStart,
                                             String newStart, String newEnd) {
        if (!Supa.isLoggedIn(c)) return "未登录 Supabase，无法保存新时间";
        try {
            JSONArray us = Supa.restGet(c, "user_state?select=*");
            if (us.length() == 0) return "云端还没有日程";
            JSONObject sch = us.getJSONObject(0).optJSONObject("schedule");
            if (sch == null) return "云端现在没有日程";
            if (!TaskItem.today().equals(sch.optString("date", ""))) return "云端日程不是今天的，先让 Muse 重排";
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
            if (!hit) return "那个时段在云端已经变了，页面刷新后请重新拖一次";
            sch.put("updatedAtMs", System.currentTimeMillis());
            sch.put("updatedBy", "KnowDo App（用户在日历页拖拽）");
            SyncManager.cacheSchedule(c, sch);
            JSONObject write = new JSONObject();
            write.put("user_id", Supa.userId(c));
            write.put("schedule", sch);
            Supa.restUpsertOne(c, "user_state", "user_id", write);
            // 回读校验
            JSONArray chk = Supa.restGet(c, "user_state?select=*");
            boolean ok = false;
            if (chk.length() > 0) {
                JSONObject s2 = chk.getJSONObject(0).optJSONObject("schedule");
                JSONArray cb = s2 == null ? null : s2.optJSONArray("blocks");
                if (cb != null) for (int i = 0; i < cb.length(); i++) {
                    JSONObject o = cb.optJSONObject(i);
                    if (o == null) continue;
                    if (taskId.equals(o.optString("taskId", ""))
                            && newStart.equals(o.optString("start", ""))
                            && newEnd.equals(o.optString("end", ""))) { ok = true; break; }
                }
            }
            return ok ? null : "已写回但回读没看到新时间，请刷新页面确认";
        } catch (Supa.Err e) {
            if (e.network) return "离线，暂时存不了新时间，联网后再拖一次";
            return "保存新时间失败（" + e.status + "）";
        } catch (Exception e) {
            return "保存新时间失败：" + e.getMessage();
        }
    }

    // ---------------- Realtime 单行合并 ----------------
    /** Realtime 推来的 tasks 行变更：按同一合并纪律并入本地。返回本地是否发生变化。 */
    public static boolean applyRemoteTaskRow(Context c, JSONObject row, String type) {
        try {
            TaskStore store = new TaskStore(c);
            synchronized (STORE_LOCK) {
                if ("DELETE".equals(type)) {
                    String id = row.optString("id", "");
                    TaskItem t = store.byId(id);
                    if (t != null && !t.deleted) {
                        t.deleted = true;
                        t.touch();
                        store.save();
                        return true;
                    }
                    return false;
                }
                TaskItem local = store.byId(row.optString("id", ""));
                TaskItem remote = SbMapper.fromRow(row, local);
                if (local == null) {
                    store.tasks.add(remote);
                } else if (remote.updatedAtMs >= local.updatedAtMs) {
                    // 待确认写守卫（v1.14.0）：本地 updatedAtMs 比已知云端签名新＝有一笔本地
                    // 改动还没被云端确认；此时到达的远端行若并不比本地新（同戳回声/旧快照），
                    // 不许替换本地——本地时钟与服务端时钟混比时同戳回滚正是「完成被弹回」的帮凶。
                    Map<String, Long> sig0 = loadSig(c);
                    Long s0 = sig0.get(local.id);
                    if (s0 != null && local.updatedAtMs > s0 && remote.updatedAtMs <= local.updatedAtMs) {
                        return false;
                    }
                    store.tasks.set(store.tasks.indexOf(local), remote);
                } else {
                    return false;
                }
                store.save();
                Map<String, Long> sig = loadSig(c);
                sig.put(remote.id, remote.updatedAtMs);
                saveSig(c, sig);
            }
            // 远端可能刚改了某任务的闹钟：按合并后的本地状态重排一次
            AlarmScheduler.rescheduleAll(c, store);
            if (remoteAutoStart(row)) handleAutoStart(c, new TaskStore(c));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean remoteAutoStart(JSONObject row) {
        return row.optBoolean("auto_start", false);
    }

    // ---------------- autoStart 消费（与 SyncService 桥时代同一语义） ----------------
    public static void handleAutoStart(Context c, TaskStore store) {
        if (TimerState.isRunning(c)) return; // 有计时在跑就不打断，标记留着等空闲轮次
        boolean hasExtra = prefs(c).getBoolean("has_extra", false);
        boolean hasAlarm = prefs(c).getBoolean("has_alarm", false);
        boolean hasSub = prefs(c).getBoolean("has_subtasks", false);
        for (TaskItem t : store.tasks) {
            if (t.deleted || !t.autoStart) continue;
            if (SyncManager.autoStartHandled(c, t.id)) {
                // 已处理过但云端标记还在（另一端可能写回旧版）：清掉防反复
                t.autoStart = false;
                t.touch();
                store.save();
                pushOneQuiet(c, store, t, hasExtra, hasAlarm, hasSub);
                continue;
            }
            SyncManager.markAutoStartHandled(c, t.id);
            t.autoStart = false;
            t.touch();
            store.save();
            TimerService.startTimer(c, Math.max(1, t.durationMinutes) * 60, t.id, t.title);
            TimerState.logEvent(c, "Supabase 同步：自动开始计时[" + t.title + "] " + t.durationMinutes + " 分钟");
            pushOneQuiet(c, store, t, hasExtra, hasAlarm, hasSub); // 立刻把清掉的标记回写云端，防另一端重复启动
            break; // 一次只启动一个
        }
    }

    private static void pushOneQuiet(Context c, TaskStore store, TaskItem t,
                                     boolean hasExtra, boolean hasAlarm, boolean hasSub) {
        // v1.14.0 起统一走定向校验写（原实现同样有「拿旧快照 adopt+save 盖盘」的隐患）
        pushOneVerified(c, store, t.id);
    }

    /**
     * 定向单条校验写（v1.14.0，完成弹回 bug 的主修法）——只能在后台线程调用：
     * 锁内重读磁盘拿该任务的最新本地状态 → 先读云端最新行 → 按内容（status/done_dates/
     * auto_start）判断：云端已一致就只对齐签名收工；不一致就整行 upsert（本地这份是
     * 刚从磁盘重读的最新版，不会盖掉别人的新改动）→ 回读校验 status/done_dates，
     * 对不上重写一次（自愈）；两次都不过就留给下轮全量同步兜底。
     * 时间戳是手机时钟与服务器时钟混比、不可靠，所以「要不要推」按内容定，不按时间定。
     */
    public static void pushOneVerified(Context c, TaskStore store, String taskId) {
        if (!Supa.isLoggedIn(c) || taskId == null) return;
        boolean hasExtra = prefs(c).getBoolean("has_extra", false);
        boolean hasAlarm = prefs(c).getBoolean("has_alarm", false);
        boolean hasSub = prefs(c).getBoolean("has_subtasks", false);
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                TaskItem local;
                synchronized (STORE_LOCK) {
                    store.load();
                    local = store.byId(taskId);
                }
                if (local == null) return;
                JSONArray rows = Supa.restGet(c, "tasks?select=*&id=eq." + taskId);
                if (rows.length() > 0) {
                    TaskItem remote = SbMapper.fromRow(rows.getJSONObject(0), null);
                    if (remote != null && eqStr(remote.status, local.status)
                            && remote.autoStart == local.autoStart
                            && remote.doneDates.equals(local.doneDates)) {
                        alignSigAndTime(c, store, local, rows.getJSONObject(0));
                        return; // 云端已是这份内容，收工
                    }
                }
                JSONArray payload = new JSONArray();
                payload.put(SbMapper.toRow(local, Supa.userId(c), hasExtra, hasAlarm, hasSub));
                Supa.restUpsert(c, "tasks", "id", payload);
                // 回读校验
                JSONArray chk = Supa.restGet(c, "tasks?select=*&id=eq." + taskId);
                if (chk.length() > 0) {
                    TaskItem back = SbMapper.fromRow(chk.getJSONObject(0), null);
                    if (back != null && eqStr(back.status, local.status)
                            && back.doneDates.equals(local.doneDates)) {
                        alignSigAndTime(c, store, local, chk.getJSONObject(0));
                        return;
                    }
                }
                // 校验不过 → 循环重写一次（自愈）
            }
        } catch (Exception ignored) { }
    }

    /** 定向写确认后：把服务端 updated_at 对齐进本地（仍是推送值才对齐）并更新签名。 */
    private static void alignSigAndTime(Context c, TaskStore store, TaskItem pushed, JSONObject serverRow) {
        long serverMs = SbMapper.parseIsoMs(serverRow.optString("updated_at", ""));
        long sigVal = pushed.updatedAtMs;
        synchronized (STORE_LOCK) {
            store.load();
            TaskItem cur = store.byId(pushed.id);
            if (cur != null && cur.updatedAtMs == pushed.updatedAtMs && serverMs > cur.updatedAtMs) {
                cur.updatedAtMs = serverMs;
                sigVal = serverMs;
                store.save();
            }
        }
        Map<String, Long> sig = loadSig(c);
        sig.put(pushed.id, sigVal);
        saveSig(c, sig);
    }

    private static boolean eqStr(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** 完成/取消完成落盘后调用：后台线程把这一条立刻定向写上云并回读确认（约 1 秒内）。 */
    public static void pushDoneAsync(final Context c, final String taskId) {
        if (!Supa.isLoggedIn(c) || taskId == null) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> pushOneVerified(app, new TaskStore(app), taskId)).start();
    }

    // ---------------- 签名与探测 ----------------
    private static Map<String, Long> loadSig(Context c) {
        Map<String, Long> sig = new HashMap<>();
        String uid = Supa.userId(c);
        String s = prefs(c).getString("sig_" + uid, null);
        if (s == null) return sig;
        try {
            JSONObject o = new JSONObject(s);
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                sig.put(k, o.optLong(k, 0));
            }
        } catch (Exception ignored) { }
        return sig;
    }

    private static void saveSig(Context c, Map<String, Long> sig) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Long> e : sig.entrySet()) o.put(e.getKey(), e.getValue());
            prefs(c).edit().putString("sig_" + Supa.userId(c), o.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** 服务端有没有 deadline_time / actual_minutes 两列（每次全量同步探一次，开销一行 limit=0）。 */
    private static boolean probeExtraCols(Context c) {
        boolean has = false;
        try {
            Supa.restGet(c, "tasks?select=deadline_time&limit=0");
            has = true;
        } catch (Exception ignored) { }
        prefs(c).edit().putBoolean("has_extra", has).apply();
        return has;
    }

    /** 服务端有没有闹钟两列 alarm_at / alarm_on_timer_end（v1.9.0，与附加列同法探测）。 */
    private static boolean probeAlarmCols(Context c) {
        boolean has = false;
        try {
            Supa.restGet(c, "tasks?select=alarm_at&limit=0");
            has = true;
        } catch (Exception ignored) { }
        prefs(c).edit().putBoolean("has_alarm", has).apply();
        return has;
    }

    /** user_state 有没有某列（v1.12.0 的 aspiration / self_checks 用，与附加列同法探测）。 */
    private static boolean probeUserStateCol(Context c, String col) {
        try {
            Supa.restGet(c, "user_state?select=" + col + "&limit=0");
            return true;
        } catch (Exception ignored) { return false; }
    }

    /** 服务端有没有子任务列 subtasks（jsonb，v1.11.0，与附加列同法探测）。 */
    private static boolean probeSubtaskCols(Context c) {
        boolean has = false;
        try {
            Supa.restGet(c, "tasks?select=subtasks&limit=0");
            has = true;
        } catch (Exception ignored) { }
        prefs(c).edit().putBoolean("has_subtasks", has).apply();
        return has;
    }

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
}
