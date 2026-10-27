package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.List;

/**
 * 同步的本地侧小设施（v1.26.0 瘦身）：同步引擎本体是 SbSync（Supabase，
 * 唯一的同步通道）。本类只剩三类东西：
 * 1) 云端 user_state 里 schedule/gcal 的本地只读缓存口——SbSync 同步时写入，
 *    日历页/聊天上下文读它渲染（gcal 是助手侧写进云端的 Google 日历事件，
 *    App 只读缓存、从不回写修改）；
 * 2) schedule 的四个写入口转发（用户拖拽改块/拖入新块/本地自动排程/提案落盘），
 *    实现全在 SbSync；
 * 3) 同步结果文案、实时开关、autoStart 防重复标记等杂项偏好。
 * 历史注记：v1.26.0 之前这里还有一整套 Google Drive 桥同步（SAF 选文件、
 * 读写桥文件、双读合并回写）——桥已于 2026-10-25 退役、同步早已全量走
 * Supabase，本版把桥实现、桥入口与桥文案整套删除；旧桥在 prefs 里留下的
 * Uri 由 purgeLegacyBridge 一次性清掉（schedule/gcal 缓存不动）。
 */
public class SyncManager {
    private static final String PREF = "sync";
    private static final String KEY_URI = "tasks_json_uri"; // 旧桥残留键，仅供清理
    private static final String KEY_SCHEDULE = "schedule_json";
    private static final String KEY_GCAL = "gcal_json";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 一次性清理旧桥残留：删掉 prefs 里的桥文件 Uri（本地任务与云端缓存一概不动）。 */
    public static void purgeLegacyBridge(Context c) {
        if (prefs(c).contains(KEY_URI)) {
            prefs(c).edit().remove(KEY_URI).apply();
        }
    }

    public static void setLastResult(Context c, String text) {
        prefs(c).edit().putString("last_result", text).apply();
    }

    public static String lastResult(Context c) {
        return prefs(c).getString("last_result", "");
    }

    // ---- 实时同步与 autoStart 防重复 ----
    /** 实时同步常开（v1.9.0 起无开关；v1.26.0 起只有 Supabase 实时推送这一条路）。 */
    public static boolean realtimeEnabled(Context c) {
        return true;
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

    // ---- 云端 schedule/gcal 的本地只读缓存（SbSync 写、日历页读） ----

    /** SbSync 把云端 schedule 写进本地缓存（日历页读口不变）。 */
    public static void cacheSchedule(Context c, JSONObject sch) {
        if (sch != null) prefs(c).edit().putString(KEY_SCHEDULE, sch.toString()).apply();
    }

    /** SbSync 把云端 gcal 写进本地缓存（只读渲染，App 不改）。 */
    public static void cacheGcal(Context c, JSONObject gcal) {
        if (gcal != null) prefs(c).edit().putString(KEY_GCAL, gcal.toString()).apply();
    }

    /**
     * 最近一次同步到的日程（云端 user_state.schedule 的本地缓存，助手侧与 App
     * 提案共同维护）。日历页用它画今日时间表；云端暂时没有时返回 null。
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
     * 云端 gcal 的本地缓存（助手侧写进云端的 Google 日历事件，App 只渲染不改）。
     * 结构：{"date":"YYYY-MM-DD","events":[{"id","title","start":"HH:mm","end":"HH:mm","allDay":false}]}。
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

    // ---- schedule 写入口（实现全在 SbSync，本类只是历史调用口的转发） ----

    /** 用户在日历页把今天的某个计划块拖到新时间（时长不变）。后台线程调用；null＝成功。 */
    public static String updateScheduleBlock(Context c, String taskId, String oldStart,
                                             String newStart, String newEnd) {
        return SbSync.updateScheduleBlock(c, taskId, oldStart, newStart, newEnd);
    }

    /** 用户把一个未排任务拖进今天的日历，在落点新建计划块。后台线程调用；null＝成功。 */
    public static String addScheduleBlock(Context c, String taskId, String newStart, String newEnd) {
        return SbSync.addScheduleBlock(c, taskId, newStart, newEnd);
    }

    /** 本地自动排程落盘（今日必做没排上的按 ×1.5 填空档）。后台线程调用。 */
    public static LocalAutoScheduler.Plan autoFillSchedule(
            Context c, List<LocalAutoScheduler.Candidate> cands, String updatedBy) {
        return SbSync.autoFillSchedule(c, cands, updatedBy);
    }

    /** 排程提案卡点「应用」落盘（已过去/已完成的块保留，其余按卡落盘）。后台线程调用；null＝成功。 */
    public static String applyScheduleProposal(Context c, TaskStore store,
                                               List<LocalAutoScheduler.Block> cardBlocks,
                                               String updatedBy) {
        return SbSync.applyScheduleProposal(c, store, cardBlocks, updatedBy);
    }

    /** 快速重排落盘（v1.27.0，纯算法立即生效；生效前原文在 outcome.previousSchedule）。后台线程调用。 */
    public static LocalAutoScheduler.RescheduleOutcome applyQuickReschedule(
            Context c, TaskStore store, String updatedBy) {
        return SbSync.applyQuickReschedule(c, store, updatedBy);
    }

    /** 撤销快速重排：把快照 schedule 原样写回。后台线程调用；null＝成功。 */
    public static String restoreScheduleSnapshot(Context c, org.json.JSONObject snapshot,
                                                 String updatedBy) {
        return SbSync.restoreScheduleSnapshot(c, snapshot, updatedBy);
    }
}
