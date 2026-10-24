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
 * 磨练自检记录（v1.12.0）：分神问询页自答（做到了/没有＋现在的状态）与加时前状态点选。
 * 事件 {id, ts, time, context: drift|extend, didRight: true|false|null,
 *       state: string|null, taskId}。
 * 本地存应用私有文件 self_checks.json；同步并入 Supabase user_state.self_checks，
 * 与 GuardEvents 同一套合并纪律：按 id 去重（无 id 的旧条按 ts|context|taskId）、
 * 按 ts 排序、上限 200 条丢最旧。
 * App 只追加新条、并回填自己刚在同一页面建的那一条；不改更早的记录。
 * 本版只记录与同步，不做统计页（日后省察功能用）。
 */
public final class SelfChecks {
    private static final String FILE = "self_checks.json";
    private static final int MAX = 200;

    /** 六个状态的标准存值（与 Strings.selfCheckStates() 同序同文案，存值与界面语言无关）。 */
    public static final String[] STATES = {"平静", "烦", "躁", "昏沉", "贪多", "求快"};

    private SelfChecks() {}

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    /** 新建一条自检记录（两问先留 null），返回其 id；同页后续点选回填这一条。 */
    public static synchronized String begin(Context c, String context, String taskId) {
        String id = java.util.UUID.randomUUID().toString();
        try {
            JSONArray arr = loadArray(c);
            JSONObject e = new JSONObject();
            e.put("id", id);
            e.put("ts", System.currentTimeMillis());
            e.put("time", TaskItem.nowIso());
            e.put("context", context);
            e.put("didRight", JSONObject.NULL);
            e.put("state", JSONObject.NULL);
            e.put("taskId", taskId == null ? "" : taskId);
            arr.put(e);
            saveArray(c, trim(arr));
        } catch (Exception ignored) { }
        return id;
    }

    /** 回填某条记录的一问：点了才记，没点保持 null。didRight/state 传 null 表示这一问不回填。 */
    public static synchronized void answer(Context c, String id, Boolean didRight, String state) {
        if (id == null) return;
        try {
            JSONArray arr = loadArray(c);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e != null && id.equals(e.optString("id", ""))) {
                    if (didRight != null) e.put("didRight", didRight.booleanValue());
                    if (state != null) e.put("state", state);
                    saveArray(c, arr);
                    return;
                }
            }
        } catch (Exception ignored) { }
    }

    /** 一次点选直接成条（加时自检：只有状态一问，didRight 留 null）。 */
    public static synchronized void add(Context c, String context, Boolean didRight,
                                        String state, String taskId) {
        String id = begin(c, context, taskId);
        answer(c, id, didRight, state);
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

    /** 与云端 self_checks 合并（本机同 id 条目优先——答题设备上的回填最全）。 */
    public static synchronized JSONArray merge(Context c, JSONArray remote) {
        try {
            List<JSONObject> all = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            collect(loadArray(c), all, seen);
            if (remote != null) collect(remote, all, seen);
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

    /** 合并签名：与去重同口径的键集合，供 SbSync 判断要不要回写。 */
    public static Set<String> keys(JSONArray arr) {
        Set<String> keys = new HashSet<>();
        if (arr == null) return keys;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) keys.add(keyOf(o));
        }
        return keys;
    }

    private static String keyOf(JSONObject o) {
        String id = o.optString("id", "");
        if (!id.isEmpty()) return "id|" + id;
        return o.optLong("ts", 0) + "|" + o.optString("context", "") + "|" + o.optString("taskId", "");
    }

    private static void collect(JSONArray arr, List<JSONObject> all, Set<String> seen) throws Exception {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (seen.add(keyOf(o))) all.add(o);
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
