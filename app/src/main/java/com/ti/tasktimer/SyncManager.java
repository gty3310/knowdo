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
import java.util.Map;

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
            Map<String, TaskItem> remote = new HashMap<>();
            JSONArray remoteEvents = null;
            try {
                InputStream in = c.getContentResolver().openInputStream(uri);
                if (in != null) {
                    String s = TaskStore.readAll(in);
                    JSONObject root = new JSONObject(s);
                    JSONArray arr = root.optJSONArray("tasks");
                    if (arr != null) for (int i = 0; i < arr.length(); i++) {
                        TaskItem t = TaskItem.fromJson(arr.getJSONObject(i));
                        remote.put(t.id, t);
                    }
                    remoteEvents = root.optJSONArray("guardEvents");
                }
            } catch (Exception e) {
                return "读取桥文件失败：" + e.getMessage();
            }
            // 2) 合并：同 id 取 updatedAtMs 大者
            Map<String, TaskItem> merged = new HashMap<>(remote);
            for (TaskItem local : store.tasks) {
                TaskItem r = merged.get(local.id);
                if (r == null || local.updatedAtMs >= r.updatedAtMs) merged.put(local.id, local);
            }
            store.tasks.clear();
            store.tasks.addAll(merged.values());
            store.save();
            // 2.5) 屏幕守卫事件与桥上的 guardEvents 合并（只含时间/类别/置信度/证据文字，无截图）
            JSONArray mergedEvents = GuardEvents.merge(c, remoteEvents);
            // 3) 回写远端
            OutputStream out = c.getContentResolver().openOutputStream(uri, "wt");
            if (out == null) return "合并完成，但回写桥文件失败（未能打开输出流）";
            JSONObject outRoot = store.toRootJson();
            outRoot.put("guardEvents", mergedEvents);
            out.write(outRoot.toString(2).getBytes(StandardCharsets.UTF_8));
            out.close();
            int visible = 0;
            for (TaskItem t : store.tasks) if (!t.deleted) visible++;
            return "同步完成：共 " + visible + " 个任务（" + TaskItem.nowIso() + "）";
        } catch (Exception e) {
            return "同步失败：" + e.getMessage();
        }
    }
}
