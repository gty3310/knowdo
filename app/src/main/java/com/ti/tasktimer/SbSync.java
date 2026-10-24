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
 *   guard_events 双向合并，timer_state 交给 SbTimerSync。
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

        // 1) 拉云端任务全量
        Map<String, TaskItem> remote = new HashMap<>();
        Map<String, Long> remoteMs = new HashMap<>();
        pullTasks(c, store, remote, remoteMs);

        // 2) 首次登录且云端为空 → 播种：本机全量 upsert 成本人数据，再拉一次拿权威快照
        if (remote.isEmpty() && !prefs(c).getBoolean("seeded_" + uid, false)) {
            if (!store.tasks.isEmpty()) {
                JSONArray payload = new JSONArray();
                for (TaskItem t : store.tasks) payload.put(SbMapper.toRow(t, uid, hasExtra));
                JSONArray back = Supa.restUpsert(c, "tasks", "id", payload);
                adoptServerTimes(back, store);
                store.save();
            }
            prefs(c).edit().putBoolean("seeded_" + uid, true).apply();
            remote.clear();
            remoteMs.clear();
            pullTasks(c, store, remote, remoteMs);
        }

        // 3) 合并（新者胜、墓碑参与）→ 本地落盘
        Map<String, TaskItem> merged;
        synchronized (STORE_LOCK) {
            merged = mergeTasks(remote, store.tasks);
            store.tasks.clear();
            store.tasks.addAll(merged.values());
            store.save();
        }

        // 4) 差异推送：本地胜出且比云端快照新的，一批 upsert；回读服务端 updated_at 对齐本地
        java.util.List<TaskItem> toPush = new java.util.ArrayList<>();
        for (TaskItem t : merged.values()) {
            Long r = remoteMs.get(t.id);
            if (r == null || t.updatedAtMs > r) toPush.add(t);
        }
        if (!toPush.isEmpty()) {
            JSONArray payload = new JSONArray();
            for (TaskItem t : toPush) payload.put(SbMapper.toRow(t, uid, hasExtra));
            JSONArray back = Supa.restUpsert(c, "tasks", "id", payload);
            adoptServerTimes(back, store);
            for (TaskItem t : toPush) remoteMs.put(t.id, t.updatedAtMs);
            synchronized (STORE_LOCK) { store.save(); }
        }
        saveSig(c, remoteMs);

        // 5) user_state：schedule/gcal 缓存、guard_events 合并、timer_state 对账与补写
        syncUserState(c, uid);

        prefs(c).edit().putLong("last_sync_at", System.currentTimeMillis()).apply();
        StartReminder.reschedule(c, store);

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

    /** upsert 返回的 representation 里带服务端 updated_at：对齐本地，避免下一轮把自己刚写的又当新版。 */
    private static void adoptServerTimes(JSONArray back, TaskStore store) {
        if (back == null) return;
        for (int i = 0; i < back.length(); i++) {
            JSONObject row = back.optJSONObject(i);
            if (row == null) continue;
            TaskItem t = store.byId(row.optString("id", ""));
            long ms = SbMapper.parseIsoMs(row.optString("updated_at", ""));
            if (t != null && ms > t.updatedAtMs) t.updatedAtMs = ms;
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
            if (!urow.isNull("timer_state")) {
                SbTimerSync.onRemoteState(c, urow.optJSONObject("timer_state"));
            }
        } else {
            JSONArray local = GuardEvents.loadArray(c);
            if (local.length() > 0) {
                write.put("guard_events", local);
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
                    store.tasks.set(store.tasks.indexOf(local), remote);
                } else {
                    return false;
                }
                store.save();
                Map<String, Long> sig = loadSig(c);
                sig.put(remote.id, remote.updatedAtMs);
                saveSig(c, sig);
            }
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
        for (TaskItem t : store.tasks) {
            if (t.deleted || !t.autoStart) continue;
            if (SyncManager.autoStartHandled(c, t.id)) {
                // 已处理过但云端标记还在（另一端可能写回旧版）：清掉防反复
                t.autoStart = false;
                t.touch();
                store.save();
                pushOneQuiet(c, store, t, hasExtra);
                continue;
            }
            SyncManager.markAutoStartHandled(c, t.id);
            t.autoStart = false;
            t.touch();
            store.save();
            TimerService.startTimer(c, Math.max(1, t.durationMinutes) * 60, t.id, t.title);
            TimerState.logEvent(c, "Supabase 同步：自动开始计时[" + t.title + "] " + t.durationMinutes + " 分钟");
            pushOneQuiet(c, store, t, hasExtra); // 立刻把清掉的标记回写云端，防另一端重复启动
            break; // 一次只启动一个
        }
    }

    private static void pushOneQuiet(Context c, TaskStore store, TaskItem t, boolean hasExtra) {
        try {
            JSONArray payload = new JSONArray();
            payload.put(SbMapper.toRow(t, Supa.userId(c), hasExtra));
            JSONArray back = Supa.restUpsert(c, "tasks", "id", payload);
            adoptServerTimes(back, store);
            store.save();
            Map<String, Long> sig = loadSig(c);
            sig.put(t.id, t.updatedAtMs);
            saveSig(c, sig);
        } catch (Exception ignored) { }
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
