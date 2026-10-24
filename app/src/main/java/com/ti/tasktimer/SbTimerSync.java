package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 跨设备计时同步（v1.7.0）：计时状态存 user_state.timer_state，一台逻辑计时器、多端镜像。
 * 结构：{task_id, phase: running|paused|idle, started_at(epoch 毫秒), accumulated_seconds,
 *       duration_seconds, updated_by(本机稳定设备 id), updated_at(epoch 毫秒), task_title(附加)}。
 * - 本端任何计时动作（开始/暂停/继续/加时/结束）由 TimerService 汇到 onLocalTimerChanged 写云端。
 * - 远端状态经 Realtime 或对账进来：updated_by 是自己或 updated_at 不比上次应用的更新 → 忽略（防回声）。
 * - 应用远端状态只改 TimerState 与 TimerService 的运行镜像，不动 SessionLog/守卫等本端语义。
 */
public final class SbTimerSync {
    public static final String ACTION_TIMER_REMOTE = "com.ti.tasktimer.TIMER_REMOTE";
    private static final String PREF = "sbtimer";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Object LOCK = new Object();

    private SbTimerSync() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String deviceId(Context c) {
        String id = prefs(c).getString("device_id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs(c).edit().putString("device_id", id).apply();
        }
        return id;
    }

    /** 本机当前计时状态 → timer_state JSON（永远反映 TimerState 的现行真相）。 */
    public static JSONObject buildLocalState(Context c) {
        JSONObject o = new JSONObject();
        try {
            long now = System.currentTimeMillis();
            if (TimerState.isRunning(c)) {
                o.put("task_id", TimerState.taskId(c));
                o.put("task_title", TimerState.taskTitle(c));
                o.put("phase", "running");
                o.put("started_at", TimerState.startedAt(c));
                o.put("accumulated_seconds", 0);
                o.put("duration_seconds", TimerState.totalSec(c));
                // 子任务进度（v1.11.0）：老端忽略未知字段
                if (TimerState.subActive(c)) {
                    o.put("sub_index", TimerState.subIndex(c));
                    o.put("sub_title", TimerState.subTitle(c));
                    o.put("sub_id", TimerState.subId(c));
                    o.put("sub_count", TimerState.subCount(c));
                    o.put("sub_end_at", TimerState.subEndAt(c));
                }
            } else if (TimerState.isPaused(c)) {
                int total = TimerState.pausedTotalSec(c);
                long remainSec = Math.max(0, TimerState.pausedRemainMs(c) / 1000);
                o.put("task_id", TimerState.pausedTaskId(c));
                o.put("task_title", TimerState.pausedTitle(c));
                o.put("phase", "paused");
                o.put("started_at", TimerState.startedAt(c));
                o.put("accumulated_seconds", Math.max(0, total - (int) remainSec));
                o.put("duration_seconds", total);
                if (TimerState.pausedSubActive(c)) {
                    o.put("sub_index", TimerState.pausedSubIndex(c));
                    o.put("sub_title", TimerState.pausedSubTitle(c));
                    o.put("sub_count", TimerState.pausedSubCount(c));
                    o.put("sub_remain_ms", TimerState.pausedSubRemainMs(c));
                }
                // 暂停目的三件套（v1.11.0）：老端忽略未知字段
                String reason = TimerState.pauseReason(c);
                if (reason != null) o.put("pause_reason", reason);
                String emoji = TimerState.pauseEmoji(c);
                if (!emoji.isEmpty()) o.put("pause_emoji", emoji);
                if (TimerState.pauseSilent(c)) o.put("pause_silent", true);
            } else {
                o.put("task_id", "");
                o.put("task_title", "");
                o.put("phase", "idle");
                o.put("started_at", 0);
                o.put("accumulated_seconds", 0);
                o.put("duration_seconds", 0);
            }
            o.put("updated_by", deviceId(c));
            o.put("updated_at", now);
        } catch (Exception ignored) { }
        return o;
    }

    // 逻辑计时器的归属（v1.13.0）：最近一次应用的 timer_state 若来自另一台设备，本机只是
    // 镜像端——此时本机编辑正在跑的任务的子任务，不做本地队列重建（SubtaskResync 会跳过），
    // 由拥有端重建后经 timer_state 推过来，本机镜像跟随。本机任何计时动作都会夺回所有权。
    private static final String K_REMOTE_OWNED = "remote_owned";

    public static boolean isRemoteOwned(Context c) {
        return prefs(c).getBoolean(K_REMOTE_OWNED, false);
    }

    /** TimerService 在每次本地计时动作后调用：记下待写状态并异步上传（失败留待 syncNow 补写）。 */
    public static void onLocalTimerChanged(Context c) {
        final Context app = c.getApplicationContext();
        prefs(app).edit().putBoolean(K_REMOTE_OWNED, false).apply();
        JSONObject st = buildLocalState(app);
        synchronized (LOCK) {
            prefs(app).edit()
                    .putString("pending", st.toString())
                    .putLong("pending_at", st.optLong("updated_at", 0))
                    .apply();
        }
        IO.execute(() -> flushPending(app));
    }

    /** 待写的本地 timer_state（没有返回 null）。syncNow 的 user_state 回写会带上它。 */
    public static JSONObject pendingJson(Context c) {
        synchronized (LOCK) {
            String s = prefs(c).getString("pending", null);
            if (s == null) return null;
            try { return new JSONObject(s); } catch (Exception e) { return null; }
        }
    }

    public static void clearPending(Context c, long updatedAt) {
        synchronized (LOCK) {
            if (prefs(c).getLong("pending_at", 0) <= updatedAt) {
                prefs(c).edit().remove("pending").remove("pending_at").apply();
            }
        }
    }

    private static void flushPending(Context c) {
        JSONObject st = pendingJson(c);
        if (st == null) return;
        if (!Supa.isLoggedIn(c)) return;
        try {
            JSONObject row = new JSONObject();
            row.put("user_id", Supa.userId(c));
            row.put("timer_state", st);
            Supa.restUpsertOne(c, "user_state", "user_id", row);
            clearPending(c, st.optLong("updated_at", 0));
        } catch (Exception ignored) {
            // 离线或令牌问题：pending 留着，syncNow 对账时会补写
        }
    }

    /**
     * 应用一份远端 timer_state（Realtime 推送或 syncNow 对账都走这里）。
     * 返回 true 表示确实应用了（本地镜像发生了变化）。
     */
    public static boolean onRemoteState(Context c, JSONObject ts) {
        if (ts == null) return false;
        String by = ts.optString("updated_by", "");
        long upd = ts.optLong("updated_at", 0);
        if (by.equals(deviceId(c))) return false;               // 自己写的回声
        SharedPreferences p = prefs(c);
        if (upd <= p.getLong("last_remote_applied", 0)) return false; // 旧状态/重复投递
        p.edit().putLong("last_remote_applied", upd).apply();

        String phase = ts.optString("phase", "idle");
        // 归属更新：远端的 running/paused 状态被接纳后，逻辑计时器归远端；idle 则无人拥有
        p.edit().putBoolean(K_REMOTE_OWNED, "running".equals(phase) || "paused".equals(phase)).apply();
        String taskId = ts.optString("task_id", "");
        String title = ts.optString("task_title", "");
        int duration = ts.optInt("duration_seconds", 0);
        int accumulated = ts.optInt("accumulated_seconds", 0);
        long startedAt = ts.optLong("started_at", 0);
        boolean changed = false;

        if ("running".equals(phase) && duration > 0) {
            long endAtR = startedAt + (long) Math.max(0, duration - accumulated) * 1000L;
            long virtualStart = endAtR - (long) duration * 1000L;
            // 远端子任务状态（老端没带则 subIndex=-1，镜像时清掉本机子任务态）
            int subIndex = ts.has("sub_index") ? ts.optInt("sub_index", -1) : -1;
            String subId = ts.optString("sub_id", "");
            String subTitle = ts.optString("sub_title", "");
            int subCount = ts.optInt("sub_count", 0);
            long subEndR = ts.optLong("sub_end_at", 0);
            boolean same = TimerState.isRunning(c)
                    && taskId.equals(TimerState.taskId(c))
                    && Math.abs(TimerState.endAt(c) - endAtR) < 2000
                    && (subIndex < 0
                        ? !TimerState.subActive(c)
                        : TimerState.subActive(c) && TimerState.subIndex(c) == subIndex
                            && Math.abs(TimerState.subEndAt(c) - subEndR) < 2000);
            if (!same) {
                TimerService.mirrorStart(c, duration, virtualStart, endAtR, taskId, title,
                        subIndex, subId, subTitle, subCount, subEndR);
                TimerState.logEvent(c, "跨设备同步：镜像远端计时[" + title + "]");
                changed = true;
            }
        } else if ("paused".equals(phase) && duration > 0) {
            long remainMs = (long) Math.max(0, duration - accumulated) * 1000L;
            int subIndex = ts.has("sub_index") ? ts.optInt("sub_index", -1) : -1;
            String subTitle = ts.optString("sub_title", "");
            int subCount = ts.optInt("sub_count", 0);
            long subRemainMs = ts.optLong("sub_remain_ms", 0);
            String reason = ts.isNull("pause_reason") ? null : ts.optString("pause_reason", null);
            String emoji = ts.isNull("pause_emoji") ? null : ts.optString("pause_emoji", null);
            boolean silent = ts.optBoolean("pause_silent", false);
            boolean same = !TimerState.isRunning(c) && TimerState.isPaused(c)
                    && taskId.equals(TimerState.pausedTaskId(c))
                    && Math.abs(TimerState.pausedRemainMs(c) - remainMs) < 2000;
            if (!same) {
                if (TimerState.isRunning(c)) TimerService.stopTimer(c);
                TimerState.setPausedRemote(c, taskId, title, duration, remainMs,
                        subIndex, subTitle, subCount, subRemainMs, reason, emoji, silent);
                TimerState.logEvent(c, "跨设备同步：远端已暂停[" + title + "]");
                changed = true;
            }
        } else { // idle
            if (TimerState.isRunning(c) || TimerState.isPaused(c)) {
                if (TimerState.isRunning(c)) TimerService.stopTimer(c);
                TimerState.clearPaused(c);
                AlertHelper.cancelAlert(c);
                TimerState.logEvent(c, "跨设备同步：远端已结束计时，本机跟随停止");
                changed = true;
            }
        }
        // 通知可能开着的检查页跟随（在显则关闭/更新）
        Intent i = new Intent(ACTION_TIMER_REMOTE).setPackage(c.getPackageName());
        i.putExtra("phase", phase);
        i.putExtra("task_id", taskId);
        c.sendBroadcast(i);
        return changed;
    }
}
