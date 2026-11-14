package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
            // v1.24.1：失败口径不变，只在测试者模式下另经 ErrorReporter 亮出状态码/原因
            String em = e.getMessage();
            ErrorReporter.report(c, Strings.en() ? "Sync" : "同步", e.network ? 0 : e.status,
                    e.network
                            ? (Strings.en() ? "Network error" : "网络错误")
                                    + (em == null || em.isEmpty() ? "" : "：" + em)
                            : (em == null ? "" : em));
            if (e.network) return "离线：本地变更已保留，联网后自动同步（待同步 "
                    + pendingCount(c, store) + " 条）";
            if (e.status == 401 || e.status == 403) return "Supabase 登录已过期或无权限，"
                    + "请到「账号」重新登录（待同步 " + pendingCount(c, store) + " 条）";
            String m = e.getMessage();
            return "Supabase 同步失败（" + e.status + "）：" + (m == null ? "" : m.substring(0, Math.min(120, m.length())));
        } catch (Exception e) {
            ErrorReporter.report(c, Strings.en() ? "Sync" : "同步", 0,
                    ErrorReporter.summarize(e));
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
        boolean hasAlarmMode = probeAlarmModeCol(c);
        boolean hasAi = probeAiCols(c);
        boolean hasSkip = probeSkipCol(c);

        // 1) 拉云端任务全量
        Map<String, TaskItem> remote = new HashMap<>();
        Map<String, Long> remoteMs = new HashMap<>();
        pullTasks(c, store, remote, remoteMs);

        // 2) 首次登录且云端为空 → 播种：本机全量 upsert 成本人数据，再拉一次拿权威快照
        if (remote.isEmpty() && !prefs(c).getBoolean("seeded_" + uid, false)) {
            if (!store.tasks.isEmpty()) {
                JSONArray payload = new JSONArray();
                for (TaskItem t : store.tasks) payload.put(SbMapper.toRow(t, uid, hasExtra, hasAlarm, hasSub, hasAlarmMode, hasAi, hasSkip));
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
        Map<String, String> noteBase = loadNoteBase(c);
        Map<String, String> remoteNotes = new HashMap<>();
        for (Map.Entry<String, TaskItem> e : remote.entrySet()) {
            remoteNotes.put(e.getKey(), nz(e.getValue().note));
        }
        synchronized (STORE_LOCK) {
            store.load();
            Map<String, TaskItem> localById = new HashMap<>();
            for (TaskItem t : store.tasks) localById.put(t.id, t);
            merged = mergeTasks(remote, store.tasks);
            // F5 字段级防冲（v1.74.0）：整行胜者之上再叠一层——ai_state 以云端
            // 为准回拉、note 三方合并，云端巡查追加的结果段与用户在手机上改的
            // 其他字段互不覆盖（整行覆盖正是这里要防的，见 mergeAiFields）
            for (Map.Entry<String, TaskItem> e : merged.entrySet()) {
                TaskItem local = localById.get(e.getKey());
                TaskItem rem = remote.get(e.getKey());
                if (local != null && rem != null) {
                    mergeAiFields(e.getValue(), local, rem, noteBase.get(e.getKey()));
                }
            }
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
            for (TaskItem t : toPush) payload.put(SbMapper.toRow(t, uid, hasExtra, hasAlarm, hasSub, hasAlarmMode, hasAi, hasSkip));
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
        // note 基线随收敛推进（F5）：本轮推上云的以推送值为准（服务端即将持有
        // 它），没推的以拉取时的云端原文为准——基线永远是「已知的云端 note」，
        // 本地独有的追加留在本地 note 里、靠前缀规则在下轮合并中继续存活
        {
            Set<String> pushedIds = new HashSet<>();
            for (TaskItem t : toPush) pushedIds.add(t.id);
            for (Map.Entry<String, TaskItem> e : merged.entrySet()) {
                if (pushedIds.contains(e.getKey()) || !remoteNotes.containsKey(e.getKey())) {
                    noteBase.put(e.getKey(), nz(e.getValue().note));
                } else {
                    noteBase.put(e.getKey(), remoteNotes.get(e.getKey()));
                }
            }
            saveNoteBase(c, noteBase);
        }

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
    /**
     * user_state 显式列清单（v1.84.0）：现行全部列除 app_lists 外的 23 列。
     * 名单本地化后同步层永不读 app_lists——select=* 会把整包名单（曾达 71 份
     * 约 562KB）每次同步整包拉回，是 Supabase 出流量的最大单一来源，故本类
     * 所有 user_state 拉取一律走这份清单（app_list_request 在清单内，供信箱
     * 取件）。列增减时同步改这里。
     */
    private static final String USER_STATE_COLS =
            "user_id,updated_at,schedule,gcal,guard_events,timer_state,aspiration,"
            + "self_checks,idle_nag,habit_data,guard_rules,app_list_request,app_rulings,"
            + "prompt_repop,update_prefs,habit_display,accountability,day_avail,"
            + "weekly_data,cloud_patch,diary_titles,diary_importance,reschedule_request";

    private static String userStateUrl() { return "user_state?select=" + USER_STATE_COLS; }

    private static void syncUserState(Context c, String uid) throws Exception {
        JSONArray us = Supa.restGet(c, userStateUrl());
        JSONObject urow = us.length() > 0 ? us.getJSONObject(0) : null;
        JSONObject write = new JSONObject();
        write.put("user_id", uid);
        boolean needWrite = false;
        // v1.12.0 两列（aspiration / self_checks）按附加列同法探测：列未就绪就不碰这两个键，
        // 免得整行 upsert 因未知列被服务端拒掉、连累 timer_state 等既有回写
        boolean hasAsp = probeUserStateCol(c, "aspiration");
        boolean hasSc = probeUserStateCol(c, "self_checks");
        boolean hasNag = probeUserStateCol(c, "idle_nag"); // v1.14.0：列未就绪只用本地，绝不碰此键
        boolean hasHabit = probeUserStateCol(c, "habit_data"); // v1.16.0：习惯系统整份数据，同纪律
        boolean hasRules = probeUserStateCol(c, "guard_rules"); // v1.24.3：用户纪律，列未就绪只用本地、绝不碰此键
        // 任务软件名单自 v1.84.0 起只存本地：本类不再探测/读/写 app_lists 列，
        // 新名单改走 app_list_request 信箱（下方取件段），拉取列清单亦已排除该列
        boolean hasRepop = probeUserStateCol(c, "prompt_repop"); // v1.26.3：提示页重弹间隔，列未就绪只用本地、绝不碰此键
        boolean hasUpd = probeUserStateCol(c, "update_prefs"); // v1.27.0：更新下载网络，列未就绪只用本地、绝不碰此键
        boolean hasDisp = probeUserStateCol(c, "habit_display"); // v1.28.0：习惯显示口径（总设置），列未就绪只用本地、绝不碰此键
        boolean hasAcc = probeUserStateCol(c, "accountability"); // v1.28.0：黄图问责邮件设置，列未就绪只用本地、绝不碰此键
        boolean hasDayAvail = probeUserStateCol(c, "day_avail"); // v1.28.0 feature 9：今天安排时段（睡眠/锁时/可支配），列未就绪只用本地、绝不碰此键
        boolean hasWeekly = probeUserStateCol(c, "weekly_data"); // v1.47.0：每周目标反思专属数据（周目标＋每日反省），列未就绪只用本地、绝不碰此键
        boolean hasDiaryTitles = probeUserStateCol(c, "diary_titles"); // v1.61.0：日记标题（日期→标题），列未就绪只用本地、绝不碰此键
        boolean hasPatch = probeUserStateCol(c, "cloud_patch"); // v1.52.0：云端补丁（云端协助整理的数据改动落地用），列未就绪不读、行为与旧版一字不差
        // 注：reschedule_request 的 App 侧接线已于 v1.26.0 撤除（日历页「问 AI 重排」
        // 按钮下线，重排改由用户在对话里直接吩咐＋本地自动排程），本类不再碰该列。

        if (urow != null) {
            JSONObject sch = urow.isNull("schedule") ? null : urow.optJSONObject("schedule");
            if (sch != null) SyncManager.cacheSchedule(c, sch);
            // 信箱取件（v1.84.0）：云端巡查把生成好的名单放进 app_list_request
            // 的 result 字段并置 status=done；这里取走存本地（AppLists.ingestLocal，
            // 本地手改过的按 manual 纪律保护），清该任务 pending，并把信箱字段级
            // 清空（write 里只动 app_list_request 一列），云端不留存。解析/存储
            // 异常时不清信箱、保持 pending 语义，下一轮同步重试；result 缺失
            // （巡查还没生成/旧巡查未切换）时什么都不做，不报错、不重复请求。
            if (!urow.isNull("app_list_request")) {
                JSONObject reqBox = urow.optJSONObject("app_list_request");
                if (reqBox != null && "done".equals(reqBox.optString("status", ""))) {
                    JSONObject result = reqBox.optJSONObject("result");
                    String rTaskId = reqBox.optString("taskId", "");
                    if (result != null && !rTaskId.isEmpty()
                            && AppLists.ingestLocal(c, rTaskId, result)) {
                        AppLists.clearPending(c, rTaskId);
                        write.put("app_list_request", new JSONObject());
                        needWrite = true;
                    }
                }
            }
            JSONObject g = urow.isNull("gcal") ? null : urow.optJSONObject("gcal");
            if (g != null) SyncManager.cacheGcal(c, g);
            JSONArray remoteEv = urow.isNull("guard_events") ? null : urow.optJSONArray("guard_events");
            JSONArray mergedEv = GuardEvents.merge(c, remoteEv);
            if (!eventKeys(mergedEv).equals(eventKeys(remoteEv))) {
                write.put("guard_events", mergedEv);
                needWrite = true;
            }
            if (hasRules) {
                // 用户纪律合并（v1.24.3）：按 id 并集、墓碑不复活（GuardRules.merge），
                // 合并签名与云端不同才回写，只动 guard_rules 一个键
                JSONArray remoteRules = urow.isNull("guard_rules") ? null : urow.optJSONArray("guard_rules");
                JSONArray mergedRules = GuardRules.merge(c, remoteRules);
                if (!GuardRules.keys(mergedRules).equals(GuardRules.keys(remoteRules))) {
                    write.put("guard_rules", mergedRules);
                    needWrite = true;
                }
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
                    EndNag.reevaluate(c);
                } else if (IdleNag.hasLocal(c) && !IdleNag.cloudEquals(c, remoteNag)) {
                    write.put("idle_nag", IdleNag.toCloudJson(c));
                    needWrite = true;
                }
            }
            if (hasRepop) {
                // 提示页重弹间隔合并（v1.26.3，与 idle_nag 同口径，只动 prompt_repop 一个键）：
                // 本机从未存过且云端有值→采用云端；本机有值且与云端不同→回写本机值。
                JSONObject remoteRepop = urow.isNull("prompt_repop") ? null : urow.optJSONObject("prompt_repop");
                if (!PromptRepop.hasLocal(c) && remoteRepop != null) {
                    PromptRepop.adoptCloud(c, remoteRepop);
                } else if (PromptRepop.hasLocal(c) && !PromptRepop.cloudEquals(c, remoteRepop)) {
                    write.put("prompt_repop", PromptRepop.toCloudJson(c));
                    needWrite = true;
                }
            }
            if (hasUpd) {
                // 更新下载网络合并（v1.27.0，与 prompt_repop 同口径，只动 update_prefs 一个键）
                JSONObject remoteUpd = urow.isNull("update_prefs") ? null : urow.optJSONObject("update_prefs");
                if (!UpdatePrefs.hasLocal(c) && remoteUpd != null) {
                    UpdatePrefs.adoptCloud(c, remoteUpd);
                } else if (UpdatePrefs.hasLocal(c) && !UpdatePrefs.cloudEquals(c, remoteUpd)) {
                    write.put("update_prefs", UpdatePrefs.toCloudJson(c));
                    needWrite = true;
                }
            }
            if (hasDisp) {
                // 习惯显示口径合并（v1.28.0，与 update_prefs 同口径，只动 habit_display 一个键）
                JSONObject remoteDisp = urow.isNull("habit_display") ? null : urow.optJSONObject("habit_display");
                if (!HabitDisplay.hasLocal(c) && remoteDisp != null) {
                    HabitDisplay.adoptCloud(c, remoteDisp);
                } else if (HabitDisplay.hasLocal(c) && !HabitDisplay.cloudEquals(c, remoteDisp)) {
                    write.put("habit_display", HabitDisplay.toCloudJson(c));
                    needWrite = true;
                }
            }
            if (hasAcc) {
                // 黄图问责设置合并（v1.28.0，与 habit_display 同口径，只动 accountability 一个键）
                JSONObject remoteAcc = urow.isNull("accountability") ? null : urow.optJSONObject("accountability");
                if (!Accountability.hasLocal(c) && remoteAcc != null) {
                    Accountability.adoptCloud(c, remoteAcc);
                } else if (Accountability.hasLocal(c) && !Accountability.cloudEquals(c, remoteAcc)) {
                    write.put("accountability", Accountability.toCloudJson(c));
                    needWrite = true;
                }
            }
            if (hasDayAvail) {
                // 今天安排时段合并（v1.28.0 feature 9，与 habit_display 同口径，只动 day_avail 一个键）
                JSONObject remoteDa = urow.isNull("day_avail") ? null : urow.optJSONObject("day_avail");
                if (!DayAvail.hasLocal(c) && remoteDa != null) {
                    DayAvail.adoptCloud(c, remoteDa);
                } else if (DayAvail.hasLocal(c) && !DayAvail.cloudEquals(c, remoteDa)) {
                    write.put("day_avail", DayAvail.toCloudJson(c));
                    needWrite = true;
                }
            }
            if (hasHabit) {
                // 习惯数据合并（v1.16.0，与 idle_nag 同一套探测纪律，只动 habit_data 一个键）：
                // 本机从未存过且云端有值→整份采用；本机有值→HabitData.merge 并集（本机优先），
                // 合并结果变了先落本地、与云端不同才回写。
                JSONObject remoteHabit = urow.isNull("habit_data") ? null : urow.optJSONObject("habit_data");
                // 云端补丁（v1.52.0）：随行读出，只读不写；列未就绪/为 null 时为 null，下面两处应用自动跳过
                JSONObject cloudPatch = (hasPatch && !urow.isNull("cloud_patch"))
                        ? urow.optJSONObject("cloud_patch") : null;
                if (!HabitData.hasLocal(c) && remoteHabit != null) {
                    HabitData.adopt(c, applyCloudPatch(c, new JSONObject(remoteHabit.toString()), cloudPatch));
                    HabitReminder.rescheduleAll(c);
                } else if (HabitData.hasLocal(c)) {
                    JSONObject localHabit = HabitData.load(c);
                    JSONObject mergedHabit = HabitData.merge(localHabit, remoteHabit);
                    mergedHabit = applyCloudPatch(c, mergedHabit, cloudPatch); // 合并后、落本地/回写前应用补丁（v1.52.0）
                    if (!HabitData.contentEquals(mergedHabit, localHabit)) {
                        HabitData.saveQuiet(c, mergedHabit);
                        HabitReminder.rescheduleAll(c);
                    }
                    if (!HabitData.contentEquals(mergedHabit, remoteHabit)) {
                        write.put("habit_data", mergedHabit);
                        needWrite = true;
                    }
                }
            }
            if (hasWeekly) {
                // 每周目标反思数据合并（v1.47.0，与 habit_data 同一套探测纪律，只动 weekly_data 一个键）：
                // 本机从未存过且云端有值→整份采用；本机有值→WeeklyData.merge 并集（本机优先），
                // 合并结果变了先落本地、与云端不同才回写。
                JSONObject remoteW = urow.isNull("weekly_data") ? null : urow.optJSONObject("weekly_data");
                if (!WeeklyData.hasLocal(c) && remoteW != null) {
                    WeeklyData.adopt(c, remoteW);
                } else if (WeeklyData.hasLocal(c)) {
                    JSONObject localW = WeeklyData.load(c);
                    JSONObject mergedW = WeeklyData.merge(localW, remoteW);
                    if (!HabitData.contentEquals(mergedW, localW)) {
                        WeeklyData.saveQuiet(c, mergedW);
                    }
                    if (!HabitData.contentEquals(mergedW, remoteW)) {
                        write.put("weekly_data", mergedW);
                        needWrite = true;
                    }
                }
            }
            if (hasDiaryTitles) {
                // 日记标题合并（v1.61.0，与 weekly_data 同一套探测纪律，只动 diary_titles 一个键）：
                // 本机从未存过且云端有值→整份采用；本机有值→DiaryTitles.merge 并集（本机优先），
                // 合并结果变了先落本地、与云端不同才回写。
                JSONObject remoteT = urow.isNull("diary_titles") ? null : urow.optJSONObject("diary_titles");
                if (!DiaryTitles.hasLocal(c) && remoteT != null) {
                    DiaryTitles.adopt(c, remoteT);
                } else if (DiaryTitles.hasLocal(c)) {
                    JSONObject localT = DiaryTitles.load(c);
                    JSONObject mergedT = DiaryTitles.merge(localT, remoteT);
                    if (!HabitData.contentEquals(mergedT, localT)) {
                        DiaryTitles.saveQuiet(c, mergedT);
                    }
                    if (!HabitData.contentEquals(mergedT, remoteT)) {
                        write.put("diary_titles", mergedT);
                        needWrite = true;
                    }
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
            if (hasRules) {
                JSONArray localRules = GuardRules.loadArray(c);
                if (localRules.length() > 0) {
                    write.put("guard_rules", localRules);
                    needWrite = true;
                }
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
            if (hasRepop && PromptRepop.hasLocal(c)) {
                write.put("prompt_repop", PromptRepop.toCloudJson(c));
                needWrite = true;
            }
            if (hasUpd && UpdatePrefs.hasLocal(c)) {
                write.put("update_prefs", UpdatePrefs.toCloudJson(c));
                needWrite = true;
            }
            if (hasDisp && HabitDisplay.hasLocal(c)) {
                write.put("habit_display", HabitDisplay.toCloudJson(c));
                needWrite = true;
            }
            if (hasAcc && Accountability.hasLocal(c)) {
                write.put("accountability", Accountability.toCloudJson(c));
                needWrite = true;
            }
            if (hasDayAvail && DayAvail.hasLocal(c)) {
                write.put("day_avail", DayAvail.toCloudJson(c));
                needWrite = true;
            }
            if (hasHabit && HabitData.hasLocal(c)) {
                write.put("habit_data", HabitData.load(c));
                needWrite = true;
            }
            if (hasWeekly && WeeklyData.hasLocal(c)) {
                write.put("weekly_data", WeeklyData.load(c));
                needWrite = true;
            }
            if (hasDiaryTitles && DiaryTitles.hasLocal(c)) {
                write.put("diary_titles", DiaryTitles.load(c));
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

    /**
     * 云端补丁（v1.52.0）：把 user_state.cloud_patch 应用到刚合并出的 habit_data 上，
     * 随后走既有路径（变了先落本地、与云端不同才回写），不新增写通道。
     * 背景：HabitData.merge 是定案的本机优先——同 id 习惯定义、同日期日记都以本机为准，
     * 所以从云端直改的习惯 type、往已有日期续写的日记都会被下一次合并覆盖回滚；补丁在
     * 合并之后改 merged，merged 一落本机即成本机新值，此后合并天然保持。
     * 结构：{habit_types:{habitId:type}, diary_appends:[{date,marker,text}],
     *        habit_texts:{habitId:全文}}（habit_texts 为 v1.73.0 第三类）。
     * 幂等：type 是赋值、重复应用无副作用；日记续写以 marker 子串判重，已含则跳过；
     * habit_texts 防冲掉用户手改——同一 habitId 的同一份补丁文本只应用一次：
     * 本地 SharedPreferences 记 habitId→已应用文本的 SHA-256，补丁文本与标记一致
     * 则跳过（用户之后的手改不被冲掉），补丁文本变了（新内容）才再应用一次并
     * 更新标记；本机 remindText 已与补丁文本相同时只补标记、不动数据；本机没有
     * 该习惯则跳过、不新建。这是「本机主动接受补丁」，本机优先口径不变。
     * 任何异常静默跳过（返回原 merged），绝不阻断正常同步；cloud_patch 列只读不写。
     */
    private static JSONObject applyCloudPatch(Context c, JSONObject merged, JSONObject patch) {
        if (merged == null || patch == null) return merged;
        try {
            JSONObject types = patch.optJSONObject("habit_types");
            if (types != null && types.length() > 0) {
                JSONArray habits = merged.optJSONArray("habits");
                if (habits != null) {
                    for (int i = 0; i < habits.length(); i++) {
                        JSONObject h = habits.optJSONObject(i);
                        if (h == null) continue;
                        String want = types.optString(h.optString("id", ""), "");
                        if (!want.isEmpty() && !want.equals(h.optString("type", ""))) {
                            h.put("type", want); // 只改 type 一个字段，其余（名/目标/全文等）一律不动
                        }
                    }
                }
            }
            JSONArray appends = patch.optJSONArray("diary_appends");
            if (appends != null && appends.length() > 0) {
                JSONObject diary = merged.optJSONObject("diary");
                for (int i = 0; i < appends.length(); i++) {
                    JSONObject a = appends.optJSONObject(i);
                    if (a == null) continue;
                    String date = a.optString("date", "");
                    String text = a.optString("text", "");
                    if (date.isEmpty() || text.isEmpty()) continue;
                    if (diary == null) { diary = new JSONObject(); merged.put("diary", diary); }
                    String cur = diary.optString(date, "");
                    String marker = a.optString("marker", "");
                    boolean already = marker.isEmpty() ? cur.contains(text) : cur.contains(marker);
                    if (already) continue;
                    diary.put(date, cur.isEmpty() ? text : cur + "\n\n" + text);
                }
            }
            JSONObject texts = patch.optJSONObject("habit_texts");
            if (texts != null && texts.length() > 0) {
                JSONArray habitsArr = merged.optJSONArray("habits");
                if (habitsArr != null) {
                    for (int i = 0; i < habitsArr.length(); i++) {
                        JSONObject h = habitsArr.optJSONObject(i);
                        if (h == null) continue;
                        String hid = h.optString("id", "");
                        if (hid.isEmpty() || !texts.has(hid) || texts.isNull(hid)) continue;
                        String want = texts.optString(hid, "");
                        String wantHash = textHash(want);
                        if (want.equals(h.optString("remindText", ""))) {
                            // 本机已是这份文本：只补已应用标记，不动数据
                            if (!wantHash.equals(appliedTextHash(c, hid))) {
                                saveAppliedTextHash(c, hid, wantHash);
                            }
                            continue;
                        }
                        // 这份补丁文本已经应用过一次：用户之后的手改不许再被它冲掉
                        if (wantHash.equals(appliedTextHash(c, hid))) continue;
                        h.put("remindText", want); // 只改 remindText 一个字段，其余一律不动
                        saveAppliedTextHash(c, hid, wantHash);
                    }
                }
            }
        } catch (Exception ignored) { }
        return merged;
    }

    // habit_texts 已应用标记（v1.73.0）：SharedPreferences 存 habitId→已应用补丁文本的哈希
    private static final String PREF_PATCH_APPLIED = "cloud_patch_applied";

    private static String textHash(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "h" + s.hashCode() + ":" + s.length();
        }
    }

    private static String appliedTextHash(Context c, String habitId) {
        return c.getSharedPreferences(PREF_PATCH_APPLIED, Context.MODE_PRIVATE)
                .getString("habit_text_" + habitId, "");
    }

    private static void saveAppliedTextHash(Context c, String habitId, String hash) {
        c.getSharedPreferences(PREF_PATCH_APPLIED, Context.MODE_PRIVATE)
                .edit().putString("habit_text_" + habitId, hash).apply();
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
            // 用户纪律（v1.24.3）：推来时按 id 并集合并进本地（墓碑不复活），不在这里回写；
            // 列不存在时行里没有这个键，自然不碰
            if (!row.isNull("guard_rules")) {
                GuardRules.merge(c, row.optJSONArray("guard_rules"));
            }
            // 软件名单自 v1.84.0 起只存本地：Realtime 推来的行里即使带 app_lists
            // 也不再合并（名单改走 app_list_request 信箱，在全量同步时取件）
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
                    EndNag.reevaluate(c);
                }
            }
            // 提示页重弹间隔（v1.26.3）：本机从未存过时才采用云端推来的值（与 idle_nag 同口径），不在这里回写；
            // 各重弹链排下一拍时现读本值，故采用后下一拍起即按新间隔走
            if (!row.isNull("prompt_repop") && !PromptRepop.hasLocal(c)) {
                JSONObject rp = row.optJSONObject("prompt_repop");
                if (rp != null) PromptRepop.adoptCloud(c, rp);
            }
            // 更新下载网络（v1.27.0）：本机从未存过时才采用云端推来的值（与 prompt_repop 同口径），不在这里回写
            if (!row.isNull("update_prefs") && !UpdatePrefs.hasLocal(c)) {
                JSONObject ru = row.optJSONObject("update_prefs");
                if (ru != null) UpdatePrefs.adoptCloud(c, ru);
            }
            // 习惯显示口径（v1.28.0）：本机从未存过时才采用云端推来的值（与 update_prefs 同口径），不在这里回写；
            // 习惯页每次 refresh 现读本值，采用后下次进习惯页即按新口径显示
            if (!row.isNull("habit_display") && !HabitDisplay.hasLocal(c)) {
                JSONObject rd = row.optJSONObject("habit_display");
                if (rd != null) HabitDisplay.adoptCloud(c, rd);
            }
            // 黄图问责设置（v1.28.0）：本机从未存过时才采用云端推来的值（与 habit_display 同口径），不在这里回写
            if (!row.isNull("accountability") && !Accountability.hasLocal(c)) {
                JSONObject ra = row.optJSONObject("accountability");
                if (ra != null) Accountability.adoptCloud(c, ra);
            }
            // 今天安排时段（v1.28.0 feature 9）：本机从未存过时才采用云端推来的值（与 accountability 同口径），不在这里回写
            if (!row.isNull("day_avail") && !DayAvail.hasLocal(c)) {
                JSONObject rda = row.optJSONObject("day_avail");
                if (rda != null) DayAvail.adoptCloud(c, rda);
            }
            // 习惯数据（v1.16.0）：推来时合并进本地（本机优先的并集，记录不丢），不在这里回写
            if (!row.isNull("habit_data")) {
                JSONObject rh = row.optJSONObject("habit_data");
                if (rh != null) {
                    if (!HabitData.hasLocal(c)) {
                        HabitData.adopt(c, rh);
                    } else {
                        JSONObject mergedH = HabitData.merge(HabitData.load(c), rh);
                        if (!HabitData.contentEquals(mergedH, HabitData.load(c))) {
                            HabitData.saveQuiet(c, mergedH);
                        }
                    }
                    try { HabitReminder.rescheduleAll(c); } catch (Exception ignored) { }
                }
            }
            // 每周目标反思数据（v1.47.0）：推来时合并进本地（本机优先的并集，文本不丢），不在这里回写
            if (!row.isNull("weekly_data")) {
                JSONObject rw = row.optJSONObject("weekly_data");
                if (rw != null) {
                    if (!WeeklyData.hasLocal(c)) {
                        WeeklyData.adopt(c, rw);
                    } else {
                        JSONObject mergedW = WeeklyData.merge(WeeklyData.load(c), rw);
                        if (!HabitData.contentEquals(mergedW, WeeklyData.load(c))) {
                            WeeklyData.saveQuiet(c, mergedW);
                        }
                    }
                }
            }
            // 日记标题（v1.61.0）：推来时合并进本地（本机优先的并集，标题不丢），不在这里回写
            if (!row.isNull("diary_titles")) {
                JSONObject rt = row.optJSONObject("diary_titles");
                if (rt != null) {
                    if (!DiaryTitles.hasLocal(c)) {
                        DiaryTitles.adopt(c, rt);
                    } else {
                        JSONObject mergedT = DiaryTitles.merge(DiaryTitles.load(c), rt);
                        if (!HabitData.contentEquals(mergedT, DiaryTitles.load(c))) {
                            DiaryTitles.saveQuiet(c, mergedT);
                        }
                    }
                    WikiLinks.noteDiaryChanged(c);   // 标题变了，已打开的编辑面重渲染显示名
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
            JSONArray us = Supa.restGet(c, userStateUrl());
            if (us.length() == 0) return "云端还没有日程";
            JSONObject sch = us.getJSONObject(0).optJSONObject("schedule");
            if (sch == null) return "云端现在没有日程";
            if (!TaskItem.today().equals(sch.optString("date", ""))) return "云端日程不是今天的，先在日历页点「AI 重排」排一版今天的";
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
                    o.put("manual", true); // v1.27.0：用户亲手拖定的块，快速重排绕开（「拖过的块固定」）
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
            JSONArray chk = Supa.restGet(c, userStateUrl());
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

    /**
     * 「跳过今天」删块（v1.79.0）：把某任务今日在云端日程里的块全部摘掉。
     * 纪律与 updateScheduleBlock 一致：读云端最新 schedule → 只摘这个任务的块
     * （不整表覆盖别人的改动）→ 写回本地缓存 → upsert → 回读校验。后台线程
     * 调用；返回 null＝成功、或本来就没有它的块，否则是可读的失败原因。
     */
    public static String removeTaskBlocksToday(Context c, String taskId, String updatedBy) {
        if (!Supa.isLoggedIn(c)) return "未登录 Supabase，删块未上云";
        try {
            JSONArray us = Supa.restGet(c, userStateUrl());
            if (us.length() == 0) return null;
            JSONObject sch = us.getJSONObject(0).optJSONObject("schedule");
            if (sch == null) return null;
            if (!TaskItem.today().equals(sch.optString("date", ""))) return null;
            JSONArray blocks = sch.optJSONArray("blocks");
            if (blocks == null) return null;
            JSONArray kept = new JSONArray();
            boolean hit = false;
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject o = blocks.optJSONObject(i);
                if (o == null) continue;
                if (taskId.equals(o.optString("taskId", ""))) { hit = true; continue; }
                kept.put(o);
            }
            if (!hit) return null;
            sch.put("blocks", kept);
            sch.put("updatedAtMs", System.currentTimeMillis());
            sch.put("updatedBy", updatedBy);
            SyncManager.cacheSchedule(c, sch);
            JSONObject write = new JSONObject();
            write.put("user_id", Supa.userId(c));
            write.put("schedule", sch);
            Supa.restUpsertOne(c, "user_state", "user_id", write);
            JSONArray chk = Supa.restGet(c, userStateUrl());
            if (chk.length() > 0) {
                JSONObject s2 = chk.getJSONObject(0).optJSONObject("schedule");
                JSONArray cb = s2 == null ? null : s2.optJSONArray("blocks");
                if (cb != null) for (int i = 0; i < cb.length(); i++) {
                    JSONObject o = cb.optJSONObject(i);
                    if (o != null && taskId.equals(o.optString("taskId", ""))) {
                        return "已写回但回读还看到这个任务的块";
                    }
                }
            }
            return null;
        } catch (Supa.Err e) {
            if (e.network) return "离线，日程块还没删掉";
            return "删日程块失败（" + e.status + "）";
        } catch (Exception e) {
            return "删日程块失败：" + e.getMessage();
        }
    }

    /**
     * 日历页把任务拖进日历（Supabase 模式下的 schedule 唯一写入口之二，纪律与桥时代一致）：
     * 读云端最新 user_state → 只往 blocks 加这一块 → upsert → 回读校验。
     * 云端还没有今天的日程时，就地为今天新建一份只含这一块的日程（拖入即是给今天排）。
     * 返回 null 表示成功，否则是可读的失败原因。
     */
    public static String addScheduleBlock(Context c, String taskId, String newStart, String newEnd) {
        if (!Supa.isLoggedIn(c)) return "未登录 Supabase，无法保存计划块";
        try {
            JSONArray us = Supa.restGet(c, userStateUrl());
            JSONObject sch = null;
            if (us.length() > 0) sch = us.getJSONObject(0).optJSONObject("schedule");
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
            nb.put("manual", true); // v1.27.0：用户亲手拖进日历定的块，同 updateScheduleBlock 口径
            blocks.put(nb);
            sch.put("updatedAtMs", System.currentTimeMillis());
            sch.put("updatedBy", "KnowDo App（用户把任务拖进日历）");
            SyncManager.cacheSchedule(c, sch);
            JSONObject write = new JSONObject();
            write.put("user_id", Supa.userId(c));
            write.put("schedule", sch);
            Supa.restUpsertOne(c, "user_state", "user_id", write);
            // 回读校验：新块确实已在云端
            JSONArray chk = Supa.restGet(c, userStateUrl());
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
            return ok ? null : "已写回但回读没看到新块，请刷新页面确认";
        } catch (Supa.Err e) {
            if (e.network) return "离线，暂时存不了计划块，联网后再拖一次";
            return "保存计划块失败（" + e.status + "）";
        } catch (Exception e) {
            return "保存计划块失败：" + e.getMessage();
        }
    }

    /**
     * 本地自动排程落盘（Supabase 模式，写入口之三的云端实现，纪律与桥模式一致）：
     * 先读云端最新 user_state.schedule 当底 → 只往空档加新块 → upsert → 更新本地缓存。
     * 未登录/网络失败时只按本地缓存算计划、written=false，不落盘，等下一次触发再试。
     */
    public static LocalAutoScheduler.Plan autoFillSchedule(
            Context c, List<LocalAutoScheduler.Candidate> cands, String updatedBy) {
        String today = TaskItem.today();
        int nowMin = DayAccount.nowMinutes();
        String logicToday = Rollover.logicalToday(c);
        // v1.28.0 feature 9 硬约束：锁时段与睡眠段必须避开（本地缓存口径，离线也算数）
        List<int[]> blocked = DayAvail.blockedWall(c, logicToday);
        LocalAutoScheduler.Plan fallback = LocalAutoScheduler.plan(cands,
                LocalAutoScheduler.blocksOf(SyncManager.latestSchedule(c), today), nowMin,
                logicToday, blocked);
        if (!Supa.isLoggedIn(c)) return fallback;
        try {
            JSONArray us = Supa.restGet(c, userStateUrl());
            JSONObject sch = us.length() > 0 ? us.getJSONObject(0).optJSONObject("schedule") : null;
            List<LocalAutoScheduler.Block> base = LocalAutoScheduler.blocksOf(sch, today);
            LocalAutoScheduler.Plan plan = LocalAutoScheduler.plan(cands, base, nowMin,
                    logicToday, blocked);
            if (plan.additions.isEmpty()) return plan;
            List<LocalAutoScheduler.Block> merged = new ArrayList<>(base);
            merged.addAll(plan.additions);
            JSONObject nsch = LocalAutoScheduler.scheduleJson(today, merged);
            nsch.put("updatedAtMs", System.currentTimeMillis());
            nsch.put("updatedBy", updatedBy);
            SyncManager.cacheSchedule(c, nsch);
            JSONObject write = new JSONObject();
            write.put("user_id", Supa.userId(c));
            write.put("schedule", nsch);
            Supa.restUpsertOne(c, "user_state", "user_id", write);
            plan.written = true;
            return plan;
        } catch (Exception e) {
            // 写回失败静默回落本地计划的口径不变；测试者模式下亮出原因（v1.24.1）
            ErrorReporter.report(c, Strings.en() ? "Auto-schedule" : "自动排程",
                    e instanceof Supa.Err ? ((Supa.Err) e).status : 0,
                    ErrorReporter.summarize(e));
            return fallback;
        }
    }

    /**
     * 聊天排程提案卡点「应用」落盘（Supabase 模式，写入口之四的云端实现）：
     * 读云端最新 schedule → mergeProposal 合并（已过去/已完成的块保留，其余按卡落盘）
     * → upsert → 回读校验块数。返回 null 表示成功，否则是可读的失败原因。
     */
    public static String applyScheduleProposal(Context c, TaskStore store,
                                               List<LocalAutoScheduler.Block> cardBlocks,
                                               String updatedBy) {
        if (!Supa.isLoggedIn(c)) return "未登录 Supabase，无法保存排程";
        String today = TaskItem.today();
        try {
            JSONArray us = Supa.restGet(c, userStateUrl());
            JSONObject sch = us.length() > 0 ? us.getJSONObject(0).optJSONObject("schedule") : null;
            List<LocalAutoScheduler.Block> base = LocalAutoScheduler.blocksOf(sch, today);
            List<LocalAutoScheduler.Block> merged = LocalAutoScheduler.mergeProposal(
                    base, cardBlocks, store, DayAccount.nowMinutes());
            JSONObject nsch = LocalAutoScheduler.scheduleJson(today, merged);
            nsch.put("updatedAtMs", System.currentTimeMillis());
            nsch.put("updatedBy", updatedBy);
            SyncManager.cacheSchedule(c, nsch);
            JSONObject write = new JSONObject();
            write.put("user_id", Supa.userId(c));
            write.put("schedule", nsch);
            Supa.restUpsertOne(c, "user_state", "user_id", write);
            JSONArray chk = Supa.restGet(c, userStateUrl());
            List<LocalAutoScheduler.Block> got = chk.length() > 0
                    ? LocalAutoScheduler.blocksOf(chk.getJSONObject(0).optJSONObject("schedule"), today)
                    : new ArrayList<>();
            return got.size() == merged.size() ? null : "已写回但回读块数对不上，请刷新日历确认";
        } catch (Supa.Err e) {
            if (e.network) return "离线，暂时存不了排程，联网后再点一次应用";
            return "保存排程失败（" + e.status + "）";
        } catch (Exception e) {
            return "保存排程失败：" + e.getMessage();
        }
    }

    /**
     * 快速重排落盘（v1.27.0，纯算法、点下立即生效）：读云端最新 schedule 为底 →
     * LocalAutoScheduler.computeReschedule 算锁定块＋重排 → 整表写回 → 回读校验。
     * 生效前的 schedule 原文放在 outcome.previousSchedule 里，调用方（日历页）
     * 存为撤销快照。后台线程调用；outcome.error 非 null 即失败原因。
     */
    public static LocalAutoScheduler.RescheduleOutcome applyQuickReschedule(
            Context c, TaskStore store, String updatedBy) {
        LocalAutoScheduler.RescheduleOutcome o = new LocalAutoScheduler.RescheduleOutcome();
        if (!Supa.isLoggedIn(c)) {
            o.error = "未登录 Supabase，无法保存排程";
            return o;
        }
        String today = TaskItem.today();
        try {
            JSONArray us = Supa.restGet(c, userStateUrl());
            JSONObject sch = us.length() > 0 ? us.getJSONObject(0).optJSONObject("schedule") : null;
            o.previousSchedule = sch;
            List<LocalAutoScheduler.Block> base = LocalAutoScheduler.blocksOf(sch, today);
            String runningId = TimerState.isRunning(c) ? TimerState.taskId(c) : null;
            String logicTodayQ = Rollover.logicalToday(c);
            LocalAutoScheduler.RescheduleOutcome calc = LocalAutoScheduler.computeReschedule(
                    LocalAutoScheduler.snapshot(store, logicTodayQ), base, DayAccount.nowMinutes(),
                    logicTodayQ, runningId, DayAvail.blockedWall(c, logicTodayQ));
            o.locked.addAll(calc.locked);
            o.additions.addAll(calc.additions);
            o.overflow.addAll(calc.overflow);
            List<LocalAutoScheduler.Block> merged = o.merged();
            JSONObject nsch = LocalAutoScheduler.scheduleJson(today, merged);
            nsch.put("updatedAtMs", System.currentTimeMillis());
            nsch.put("updatedBy", updatedBy);
            SyncManager.cacheSchedule(c, nsch);
            JSONObject write = new JSONObject();
            write.put("user_id", Supa.userId(c));
            write.put("schedule", nsch);
            Supa.restUpsertOne(c, "user_state", "user_id", write);
            JSONArray chk = Supa.restGet(c, userStateUrl());
            List<LocalAutoScheduler.Block> got = chk.length() > 0
                    ? LocalAutoScheduler.blocksOf(chk.getJSONObject(0).optJSONObject("schedule"), today)
                    : new ArrayList<>();
            if (got.size() != merged.size()) {
                o.error = "已写回但回读块数对不上，请刷新日历确认";
            }
            return o;
        } catch (Supa.Err e) {
            o.error = e.network ? "离线，暂时存不了排程，联网后再点一次" : "保存排程失败（" + e.status + "）";
            return o;
        } catch (Exception e) {
            o.error = "保存排程失败：" + e.getMessage();
            return o;
        }
    }

    /**
     * 撤销快速重排：把快照的 schedule 原样写回（只换 updatedAtMs/updatedBy）。
     * 后台线程调用；null＝成功。
     */
    public static String restoreScheduleSnapshot(Context c, JSONObject snapshot, String updatedBy) {
        if (!Supa.isLoggedIn(c)) return "未登录 Supabase，无法恢复排程";
        try {
            JSONObject sch = new JSONObject(snapshot.toString());
            sch.put("updatedAtMs", System.currentTimeMillis());
            sch.put("updatedBy", updatedBy);
            SyncManager.cacheSchedule(c, sch);
            JSONObject write = new JSONObject();
            write.put("user_id", Supa.userId(c));
            write.put("schedule", sch);
            Supa.restUpsertOne(c, "user_state", "user_id", write);
            return null;
        } catch (Supa.Err e) {
            if (e.network) return "离线，暂时恢复不了，联网后再点一次撤销";
            return "恢复排程失败（" + e.status + "）";
        } catch (Exception e) {
            return "恢复排程失败：" + e.getMessage();
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
                    // F5（v1.74.0）：整行替换前先做字段级合并——云端刚推进的
                    // ai_state 与追加的 note 结果段不得被本地旧值冲回，本地对
                    // note 的未同步追加也不得被整行替换吞掉（与全量同步同口径）
                    Map<String, String> nb0 = loadNoteBase(c);
                    mergeAiFields(remote, local, remote, nb0.get(local.id));
                    store.tasks.set(store.tasks.indexOf(local), remote);
                } else {
                    return false;
                }
                store.save();
                Map<String, Long> sig = loadSig(c);
                sig.put(remote.id, remote.updatedAtMs);
                saveSig(c, sig);
                // note 基线跟上这行已落地的云端原文（合并增量靠前缀规则在本地存活）
                Map<String, String> nb = loadNoteBase(c);
                nb.put(remote.id, nz(row.optString("note", "")));
                saveNoteBase(c, nb);
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
        // v1.16.1 定案：远程触发的开始也是直接切换——有任务在计时就直接切过去
        // （原任务停表落盘、不进暂停态），不再「等空闲轮次」把标记晾着。
        boolean hasExtra = prefs(c).getBoolean("has_extra", false);
        boolean hasAlarm = prefs(c).getBoolean("has_alarm", false);
        boolean hasSub = prefs(c).getBoolean("has_subtasks", false);
        boolean hasAlarmMode = prefs(c).getBoolean("has_alarm_mode", false);
        for (TaskItem t : store.tasks) {
            if (t.deleted || !t.autoStart) continue;
            if (t.isAi()) {
                // 给 AI 做的任务永不计时（v1.74.0）：云端误置 auto_start 时只清标记、
                // 回写收工，绝不进 startTimer——与其它入口的不可开始口径一致
                SyncManager.markAutoStartHandled(c, t.id);
                t.autoStart = false;
                t.touch();
                store.save();
                pushOneQuiet(c, store, t, hasExtra, hasAlarm, hasSub);
                continue;
            }
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
     * v1.74.0（F5）：upsert 前先与云端最新行做字段级合并（mergeAiFields）——
     * 一致性判断与回读校验的字段集同步扩到 ai_state/note，云端巡查的推进不被整行盖回。
     */
    public static void pushOneVerified(Context c, TaskStore store, String taskId) {
        if (!Supa.isLoggedIn(c) || taskId == null) return;
        boolean hasExtra = prefs(c).getBoolean("has_extra", false);
        boolean hasAlarm = prefs(c).getBoolean("has_alarm", false);
        boolean hasSub = prefs(c).getBoolean("has_subtasks", false);
        boolean hasAlarmMode = prefs(c).getBoolean("has_alarm_mode", false);
        boolean hasAi = prefs(c).getBoolean("has_ai_cols", false);
        boolean hasSkip = prefs(c).getBoolean("has_skip_col", false);
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
                    // F5（v1.74.0）：整行 upsert 之前先把云端最新行与本地做字段级
                    // 合并再落盘——本地这次只想推 status/done_dates，云端巡查刚
                    // 推进的 ai_state 与追加的 note 结果段不得被本地旧行盖掉；
                    // 合并结果写回本地后，upsert 的就是收敛后的行
                    if (remote != null) {
                        Map<String, String> nb1 = loadNoteBase(c);
                        String beforeNote = nz(local.note);
                        String beforeState = local.aiState;
                        mergeAiFields(local, local, remote, nb1.get(taskId));
                        if (!nz(local.note).equals(beforeNote)
                                || !eqStr(local.aiState, beforeState)) {
                            synchronized (STORE_LOCK) {
                                store.load();
                                TaskItem cur = store.byId(taskId);
                                if (cur != null) {
                                    cur.note = local.note;
                                    cur.aiState = local.aiState;
                                    cur.assignee = local.assignee;
                                    store.save();
                                }
                            }
                        }
                    }
                    if (remote != null && eqStr(remote.status, local.status)
                            && remote.autoStart == local.autoStart
                            && remote.doneDates.equals(local.doneDates)
                            && remote.skippedDates.equals(local.skippedDates)
                            && eqStr(remote.aiState, local.aiState)
                            && nz(remote.note).equals(nz(local.note))) {
                        alignSigAndTime(c, store, local, rows.getJSONObject(0));
                        Map<String, String> nb2 = loadNoteBase(c);
                        nb2.put(taskId, nz(remote.note));
                        saveNoteBase(c, nb2);
                        return; // 云端已是这份内容，收工
                    }
                }
                JSONArray payload = new JSONArray();
                payload.put(SbMapper.toRow(local, Supa.userId(c), hasExtra, hasAlarm, hasSub, hasAlarmMode, hasAi, hasSkip));
                Supa.restUpsert(c, "tasks", "id", payload);
                // 回读校验
                JSONArray chk = Supa.restGet(c, "tasks?select=*&id=eq." + taskId);
                if (chk.length() > 0) {
                    TaskItem back = SbMapper.fromRow(chk.getJSONObject(0), null);
                    if (back != null && eqStr(back.status, local.status)
                            && back.doneDates.equals(local.doneDates)
                            && back.skippedDates.equals(local.skippedDates)) {
                        alignSigAndTime(c, store, local, chk.getJSONObject(0));
                        // 推送成功：云端 note 已是本地（合并后）这份，基线跟上
                        Map<String, String> nb3 = loadNoteBase(c);
                        nb3.put(taskId, nz(local.note));
                        saveNoteBase(c, nb3);
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

    // ---------------- 任务软件名单生成请求（v1.26.0） ----------------
    /**
     * 开始计时一个还没有名单的任务时（AppLists.maybeRequest 收口），把
     * user_state.app_list_request 写成 pending：{status, taskId, taskTitle, note,
     * apps[至多 100], requestedAt}。云端巡查分类后把结果放回同一请求的 result
     * 字段并置 status=done（信箱），由 syncUserState 的信箱取件存本地
     * （AppLists.ingestLocal）后清空信箱；名单不再写 app_lists 列（v1.84.0）。
     * 列（app_list_request）未就绪时按既有纪律完全不碰、静默返回（功能走本地兜底名单）。
     * 必须在后台线程调用；写成功才给该任务落 pending 标记（AppLists.markPending），
     * 失败不标记，下次开始同一任务再试。
     */
    public static void requestAppList(Context c, String taskId, String taskTitle,
                                      String note, JSONArray apps) {
        try {
            if (!Supa.isLoggedIn(c)) return;
            if (!probeUserStateCol(c, "app_list_request")) return;
            JSONObject req = new JSONObject();
            req.put("status", "pending");
            req.put("taskId", taskId);
            req.put("taskTitle", taskTitle == null ? "" : taskTitle);
            req.put("note", note == null ? "" : note);
            req.put("apps", apps == null ? new JSONArray() : apps);
            req.put("requestedAt", TaskItem.nowIso());
            JSONObject write = new JSONObject();
            write.put("user_id", Supa.userId(c));
            write.put("app_list_request", req);
            Supa.restUpsertOne(c, "user_state", "user_id", write);
            AppLists.markPending(c, taskId);
            TimerState.logEvent(c, "软件名单：已为「" + taskTitle + "」发出生成请求，名单到达前用通用名单兜底");
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

    // ---------------- F5 字段级防冲（v1.74.0） ----------------
    /**
     * 「上次同步时云端已知的 note」基线（与 sig 同存 prefs，键 note_base_<uid>，
     * id→note 原文）。整行新者胜的合并分不清「用户改了 note」与「云端（Muse
     * 巡查给 AI 任务追加结果段）改了 note」，三方合并必须有一份基线做锚：
     * 本地与基线比＝用户动没动过，远端与基线比＝云端动没动过。基线在每次
     * 全量同步收敛后、定向写成功后、Realtime 行落地后随之更新为收敛值。
     */
    private static Map<String, String> loadNoteBase(Context c) {
        Map<String, String> base = new HashMap<>();
        String s = prefs(c).getString("note_base_" + Supa.userId(c), null);
        if (s == null) return base;
        try {
            JSONObject o = new JSONObject(s);
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                base.put(k, o.optString(k, ""));
            }
        } catch (Exception ignored) { }
        return base;
    }

    private static void saveNoteBase(Context c, Map<String, String> base) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, String> e : base.entrySet()) {
                o.put(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
            prefs(c).edit().putString("note_base_" + Supa.userId(c), o.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /**
     * note 三方合并：base＝上次同步基线、local＝本地现值、remote＝云端现值。
     * 返回合并结果；两边都改写且拼不出（真分叉改写）返回 null，调用方回退
     * 整行胜者口径。规则：一边没动→取另一边；一边是另一边的前缀→取长者
     * （纯追加）；两边都只在基线后追加→基线＋云端增量＋本地增量（都不丢）。
     */
    static String mergeNote3Way(String base, String local, String remote) {
        String b = nz(base), l = nz(local), r = nz(remote);
        if (l.equals(r)) return l;
        if (l.equals(b)) return r;      // 本地没动 note → 云端说了算
        if (r.equals(b)) return l;      // 云端没动 note → 本地说了算
        if (r.startsWith(l)) return r;  // 一边是另一边前缀：追加关系，取长者
        if (l.startsWith(r)) return l;
        if (!b.isEmpty() && r.startsWith(b) && l.startsWith(b)) {
            String rs = r.substring(b.length()), ls = l.substring(b.length());
            if (rs.contains(ls)) return r;
            if (ls.contains(rs)) return l;
            return b + rs + (rs.endsWith("\n") || rs.isEmpty() ? "" : "\n") + ls;
        }
        return null;
    }

    /**
     * AI 两字段的字段级合并（整行胜者之上叠加，winner 被原地修正）：
     * - assignee 本地刚翻过（与云端不同）＝用户的主动改动未上云：assignee /
     *   ai_state / note 三样都以本地为准（状态由 F1 口径在编辑层已置好）。
     * - 否则 ai_state 以云端为准（状态只由云端巡查推进，本地旧值不得冲回），
     *   note 走三方合并（云端追加的结果段与用户改的其他字段互不覆盖）。
     */
    private static void mergeAiFields(TaskItem winner, TaskItem local, TaskItem remote,
                                      String baseNote) {
        if (winner == null || local == null || remote == null) return;
        if (!eqStr(nz(local.assignee), nz(remote.assignee))) {
            winner.assignee = local.assignee;
            winner.aiState = local.aiState;
            winner.note = local.note;
            return;
        }
        winner.aiState = remote.aiState;
        String merged = mergeNote3Way(baseNote, local.note, remote.note);
        if (merged != null) winner.note = merged;
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

    /** 服务端有没有任务级 alarm 模式列 alarm_mode（v1.20.0，与附加列同法探测）。 */
    private static boolean probeAlarmModeCol(Context c) {
        boolean has = false;
        try {
            Supa.restGet(c, "tasks?select=alarm_mode&limit=0");
            has = true;
        } catch (Exception ignored) { }
        prefs(c).edit().putBoolean("has_alarm_mode", has).apply();
        return has;
    }

    /** 服务端有没有谁来做两列 assignee / ai_state（v1.74.0，与附加列同法探测）。 */
    private static boolean probeAiCols(Context c) {
        boolean has = false;
        try {
            Supa.restGet(c, "tasks?select=assignee,ai_state&limit=0");
            has = true;
        } catch (Exception ignored) { }
        prefs(c).edit().putBoolean("has_ai_cols", has).apply();
        return has;
    }

    /** 服务端有没有跳过日期列 skipped_dates（v1.79.0，与附加列同法探测）。 */
    private static boolean probeSkipCol(Context c) {
        boolean has = false;
        try {
            Supa.restGet(c, "tasks?select=skipped_dates&limit=0");
            has = true;
        } catch (Exception ignored) { }
        prefs(c).edit().putBoolean("has_skip_col", has).apply();
        return has;
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
            // v1.24.3：键带误报标记——只标了 overturned 也算内容变了，要触发回写把标记送上云
            keys.add(o.optLong("ts", 0) + "|" + o.optString("category", "") + "|" + o.optString("taskId", "")
                    + (o.optBoolean("overturned", false) ? "|ov" : ""));
        }
        return keys;
    }
}
