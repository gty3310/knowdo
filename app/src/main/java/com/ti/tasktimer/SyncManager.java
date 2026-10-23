package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
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
            return "同步完成：" + counts + "（" + TaskItem.nowIso() + "）";
        } catch (Exception e) {
            return "同步失败：" + e.getMessage();
        }
    }

    /** 远端桥文件的一次读取结果：任务表 + guardEvents（原样 JSONArray，桥上没有该字段时为 null）。 */
    private static class RemoteSnapshot {
        final Map<String, TaskItem> tasks = new HashMap<>();
        JSONArray guardEvents;
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
        OutputStream out = c.getContentResolver().openOutputStream(uri, "wt");
        if (out == null) return false;
        JSONObject root = store.toRootJson();
        root.put("guardEvents", events);
        out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        out.close();
        return true;
    }
}
