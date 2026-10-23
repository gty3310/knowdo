package com.ti.tasktimer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地任务仓库：应用私有目录里的 tasks_local.json（含已删除墓碑）。
 * 这是数据层的唯一入口（对应研究报告的 TaskRepository），日后换 Room/远端只动这一层。
 */
public class TaskStore {
    private static final String FILE = "tasks_local.json";
    private final Context ctx;
    public final List<TaskItem> tasks = new ArrayList<>();

    public TaskStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        load();
    }

    private File file() { return new File(ctx.getFilesDir(), FILE); }

    public void load() {
        tasks.clear();
        try {
            File f = file();
            if (!f.exists()) return;
            String s = readAll(new FileInputStream(f));
            JSONObject root = new JSONObject(s);
            JSONArray arr = root.optJSONArray("tasks");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                tasks.add(TaskItem.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) { }
    }

    public void save() {
        try {
            JSONObject root = toRootJson();
            FileOutputStream out = new FileOutputStream(file());
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) { }
    }

    public JSONObject toRootJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "KnowDo");
        root.put("schemaVersion", 1);
        root.put("updatedAt", TaskItem.nowIso());
        root.put("updatedBy", "KnowDo App");
        JSONArray arr = new JSONArray();
        for (TaskItem t : tasks) arr.put(t.toJson());
        root.put("tasks", arr);
        return root;
    }

    /** 可见任务（未删除），按：未完成在前、必要在前、deadline 近在前（每日重复按今天算）。 */
    public List<TaskItem> visibleSorted() {
        List<TaskItem> v = new ArrayList<>();
        for (TaskItem t : tasks) if (!t.deleted) v.add(t);
        v.sort((a, b) -> {
            int da = a.isDoneToday() ? 1 : 0, db = b.isDoneToday() ? 1 : 0;
            if (da != db) return da - db;
            int pa = "must".equals(a.priority) ? 0 : 1, pb = "must".equals(b.priority) ? 0 : 1;
            if (pa != pb) return pa - pb;
            String ea = effDate(a), eb = effDate(b);
            if (ea == null && eb != null) return 1;
            if (ea != null && eb == null) return -1;
            if (ea != null && !ea.equals(eb)) return ea.compareTo(eb);
            return Long.compare(a.createdAtMs, b.createdAtMs);
        });
        return v;
    }

    private static String effDate(TaskItem t) {
        if ("daily".equals(t.repeat)) return TaskItem.today();
        return t.deadline;
    }

    public TaskItem byId(String id) {
        if (id == null) return null;
        for (TaskItem t : tasks) if (t.id.equals(id)) return t;
        return null;
    }

    public void add(TaskItem t) { tasks.add(t); save(); }

    public void remove(TaskItem t) { t.deleted = true; t.touch(); save(); }

    /** 全部已知情景（各任务用过的并集），给多选面板用。 */
    public List<String> allScenarios() {
        List<String> all = new ArrayList<>();
        for (TaskItem t : tasks) for (String s : t.scenarios) if (!all.contains(s)) all.add(s);
        return all;
    }

    public static String readAll(java.io.InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }
}
