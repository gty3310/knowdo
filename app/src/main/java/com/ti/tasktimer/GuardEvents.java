package com.ti.tasktimer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 屏幕内容守卫的判定事件记录：本地存应用私有文件 guard_events.json，
 * 同步时并入云端 user_state 的 guard_events（只含时间/类别/置信度/证据文字，
 * 绝不含截图原图——原图从不落盘，见 KnowDoAccessibilityService）。
 * 上限 200 条，超出丢最旧的。
 */
public final class GuardEvents {
    private static final String FILE = "guard_events.json";
    private static final int MAX = 200;

    private GuardEvents() {}

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    public static synchronized void add(Context c, TaskItem task, String category, float confidence, String detail) {
        addInternal(c, task, category, confidence, detail, false);
    }

    /**
     * 同 add，但返回这条事件的时间戳（v1.24.3）：触发问询时调用方把 ts 记下来，
     * 用户随后点「分析错误」就能精确标中这一条打误报标记，不靠类别猜。
     */
    public static synchronized long addEvent(Context c, TaskItem task, String category, float confidence, String detail) {
        return addInternal(c, task, category, confidence, detail, false);
    }

    /**
     * 备注事件（v1.21.4）：不是一次触发，只是一条可审计的记录（如语义降级
     * 放行），带 note=true 标记。知行指数的分神计数会跳过备注事件，
     * 备注绝不当成一次分神扣分。
     */
    public static synchronized void addNote(Context c, TaskItem task, String category, float confidence, String detail) {
        addInternal(c, task, category, confidence, detail, true);
    }

    private static synchronized long addInternal(Context c, TaskItem task, String category,
                                                   float confidence, String detail, boolean note) {
        long now = System.currentTimeMillis();
        try {
            JSONArray arr = loadArray(c);
            JSONObject e = new JSONObject();
            e.put("ts", now);
            e.put("time", TaskItem.nowIso());
            e.put("taskId", task == null ? "" : task.id);
            e.put("taskTitle", task == null ? "" : task.title);
            e.put("category", category);
            e.put("confidence", Math.round(confidence * 100.0) / 100.0);
            e.put("detail", detail == null ? "" : detail);
            if (note) e.put("note", true);
            arr.put(e);
            saveArray(c, trim(arr));
        } catch (Exception ignored) { }
        return now;
    }

    /**
     * 误报标记（v1.24.3）：给 ts 命中的那条事件打 overturned=true。
     * 知行指数的分神计数跳过带此标记的事件（见 ZhixingCalc.driftCount）；
     * 标记随 guard_events 既有通道同步（SbSync/SyncManager 的事件键签名带此标记，
     * 内容变了就会触发回写）。找不到返回 false。
     */
    public static synchronized boolean markOverturned(Context c, long ts) {
        if (ts <= 0) return false;
        try {
            JSONArray arr = loadArray(c);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e != null && e.optLong("ts", 0) == ts) {
                    e.put("overturned", true);
                    saveArray(c, arr);
                    return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    /** 兜底：触发时记下的 ts 丢了时，把最近一条未标误报的真实事件（非备注）标上。 */
    public static synchronized boolean markLatestOverturned(Context c) {
        try {
            JSONArray arr = loadArray(c);
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null || e.optBoolean("note", false)
                        || e.optBoolean("overturned", false)) continue;
                e.put("overturned", true);
                saveArray(c, arr);
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    public static synchronized JSONArray loadArray(Context c) {
        try {
            File f = file(c);
            if (!f.exists()) return new JSONArray();
            String s = TaskStore.readAll(new FileInputStream(f));
            return new JSONArray(s);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** 与桥上的 guardEvents 合并（按 ts+类别+任务去重），结果写回本地并返回（供回写桥）。 */
    public static synchronized JSONArray merge(Context c, JSONArray remote) {
        try {
            List<JSONObject> all = new ArrayList<>();
            java.util.Map<String, JSONObject> byKey = new java.util.HashMap<>();
            collect(loadArray(c), all, byKey);
            if (remote != null) collect(remote, all, byKey);
            all.sort(Comparator.comparingLong(o -> o.optLong("ts", 0)));
            JSONArray out = new JSONArray();
            int from = Math.max(0, all.size() - MAX);
            for (int i = from; i < all.size(); i++) out.put(all.get(i));
            saveArray(c, out);
            return out;
        } catch (Exception e) {
            return loadArray(c);
        }
    }

    private static void collect(JSONArray arr, List<JSONObject> all,
                                java.util.Map<String, JSONObject> byKey) throws Exception {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String key = o.optLong("ts", 0) + "|" + o.optString("category", "") + "|" + o.optString("taskId", "");
            JSONObject cur = byKey.get(key);
            if (cur == null) {
                byKey.put(key, o);
                all.add(o);
            } else if (o.optBoolean("overturned", false) && !cur.optBoolean("overturned", false)) {
                // 误报标记单向收敛（v1.24.3）：任一侧标了误报，合并结果就是误报，
                // 防一端标了、另一端旧副本把标记冲掉
                cur.put("overturned", true);
            }
        }
    }

    private static JSONArray trim(JSONArray arr) throws Exception {
        if (arr.length() <= MAX) return arr;
        JSONArray out = new JSONArray();
        for (int i = arr.length() - MAX; i < arr.length(); i++) out.put(arr.get(i));
        return out;
    }

    private static void saveArray(Context c, JSONArray arr) {
        try {
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) { }
    }
}
