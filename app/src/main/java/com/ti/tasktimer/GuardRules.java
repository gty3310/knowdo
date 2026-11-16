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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 用户纪律（v1.24.3 引入）：守卫判错时，用户在问询页点「分析错误」顺手留下的规矩。
 * 误判申诉机制已整套撤销：问询页的收集入口与设置里的「我的纪律」管理页都没了，
 * 问询页「加到 system prompt 里」会把通过复核的解释追加进来（每条最多 60 字）；
 * 删除入口仍未恢复。本地存储、云端合并与随终判带参照旧。
 * 本地存应用私有文件 guard_rules.json（与 GuardEvents 同办法），云端存
 * user_state.guard_rules（jsonb 数组）——列不存在时 SbSync 完全不碰该键、本类
 * 只管本地（探测纪律见 SbSync.probeUserStateCol，照 aspiration/habit_data 先例）。
 *
 * 每条纪律：{id, text, ts}。删除走墓碑：条目留在数组里标 deleted:true，
 * 合并是按 id 取并集、墓碑在场就不复活（与任务删除墓碑同一纪律），
 * 带参时滤掉墓碑。合并同 id 时本地优先。
 * 上限 100 条，超出先丢最旧的墓碑、再丢最旧的纪律。
 *
 * 纪律只随云端终判请求带出（SemanticJudge 的 rules 数组）；本地降级判定不参与。
 */
public final class GuardRules {
    private static final String FILE = "guard_rules.json";
    private static final int MAX = 100;
    /** 单条纪律字数上限（原收集入口是 20，issue #4 提到 60）。 */
    public static final int MAX_TEXT = 60;

    private GuardRules() {}

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    // ---------------- 本地读写 ----------------

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

    private static void saveArray(Context c, JSONArray arr) {
        try {
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) { }
    }

    /** 可见纪律（非墓碑），按立纪时间先后。 */
    public static synchronized List<JSONObject> visible(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray arr = loadArray(c);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || o.optBoolean("deleted", false)) continue;
                if (o.optString("text", "").isEmpty()) continue;
                out.add(o);
            }
        } catch (Exception ignored) { }
        out.sort(Comparator.comparingLong(o -> o.optLong("ts", 0)));
        return out;
    }

    /** 全部可见纪律的原文（云端终判带参用，立纪先后序）。 */
    public static synchronized List<String> texts(Context c) {
        List<String> out = new ArrayList<>();
        for (JSONObject o : visible(c)) {
            String t = o.optString("text", "").trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** rules 数组（JSON 字符串数组）——判定请求体里的字段值。 */
    public static synchronized JSONArray rulesJson(Context c) {
        JSONArray out = new JSONArray();
        for (String t : texts(c)) out.put(t);
        return out;
    }

    /**
     * 把一句已通过复核的误判解释追加为全局纪律，截到 MAX_TEXT，然后踢同步。
     * 空文案不加。
     */
    public static synchronized boolean add(Context c, String text) {
        if (text == null) return false;
        text = text.trim();
        if (text.isEmpty()) return false;
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
        try {
            JSONArray arr = loadArray(c);
            JSONObject o = new JSONObject();
            o.put("id", UUID.randomUUID().toString());
            o.put("text", text);
            o.put("ts", System.currentTimeMillis());
            arr.put(o);
            arr = trim(arr);
            saveArray(c, arr);
            kickSync(c);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- 云端合并 ----------------

    /**
     * 与云端 guard_rules 合并（按 id 并集，墓碑胜出防复活，同 id 内容本地优先），
     * 结果写回本地并返回（供 SbSync 判断要不要回写云端）。
     */
    public static synchronized JSONArray merge(Context c, JSONArray remote) {
        try {
            Map<String, JSONObject> byId = new LinkedHashMap<>();
            collect(loadArray(c), byId);
            if (remote != null) collect(remote, byId);
            List<JSONObject> all = new ArrayList<>(byId.values());
            all.sort(Comparator.comparingLong(o -> o.optLong("ts", 0)));
            JSONArray out = new JSONArray();
            for (JSONObject o : all) out.put(o);
            out = trim(out);
            saveArray(c, out);
            return out;
        } catch (Exception e) {
            return loadArray(c);
        }
    }

    private static void collect(JSONArray arr, Map<String, JSONObject> byId) throws Exception {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String id = o.optString("id", "");
            if (id.isEmpty()) continue;
            JSONObject cur = byId.get(id);
            if (cur == null) {
                byId.put(id, o);
            } else if (o.optBoolean("deleted", false) && !cur.optBoolean("deleted", false)) {
                cur.put("deleted", true); // 任一侧是墓碑就按墓碑，不复活
            }
        }
    }

    /** 合并签名：id＋墓碑＋原文的键集合，供 SbSync 判断合并结果与云端是否一致。 */
    public static Set<String> keys(JSONArray arr) {
        Set<String> keys = new HashSet<>();
        if (arr == null) return keys;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            keys.add(o.optString("id", "") + "|" + (o.optBoolean("deleted", false) ? "1" : "0")
                    + "|" + o.optString("text", ""));
        }
        return keys;
    }

    private static JSONArray trim(JSONArray arr) throws Exception {
        if (arr.length() <= MAX) return arr;
        List<JSONObject> vis = new ArrayList<>();
        List<JSONObject> tombs = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            (o.optBoolean("deleted", false) ? tombs : vis).add(o);
        }
        Comparator<JSONObject> byTs = Comparator.comparingLong(o -> o.optLong("ts", 0));
        vis.sort(byTs);
        tombs.sort(byTs);
        // 先丢最旧的墓碑；还超再丢最旧的可见纪律
        while (vis.size() + tombs.size() > MAX && !tombs.isEmpty()) tombs.remove(0);
        while (vis.size() + tombs.size() > MAX && !vis.isEmpty()) vis.remove(0);
        List<JSONObject> all = new ArrayList<>();
        all.addAll(tombs);
        all.addAll(vis);
        all.sort(byTs);
        JSONArray out = new JSONArray();
        for (JSONObject o : all) out.put(o);
        return out;
    }

    /**
     * 顺手把全量同步踢一脚（与 HabitData.requestSync 同办法）：未登录就不动，
     * 等常规同步。多个设置类借它做改完设置后的通用同步踢脚。
     */
    public static void kickSync(final Context c) {
        if (!Supa.isLoggedIn(c)) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try { SbSync.syncNow(app, new TaskStore(app)); } catch (Exception ignored) { }
        }).start();
    }
}
