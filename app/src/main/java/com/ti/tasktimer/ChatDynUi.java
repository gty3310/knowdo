package com.ti.tasktimer;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.ti.tasktimer.dynui.DynUiCatalog;
import com.ti.tasktimer.dynui.DynUiProcessor;
import com.ti.tasktimer.dynui.DynUiSurfaceView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 聊天宿主与 dynamic UI 地基之间的胶水（v2.3.0）——本文件之外，
 * dynui 包不认识 TaskStore，ChatPanel 不认识组件渲染细节。
 * 一组已应用的任务（一件或一批）→ 一个 surface：
 * 数据模型 /tasks/i/* 是任务库的投影，卡上编辑经 ChatPanel.applyFieldsTo
 * 与提案应用共用同一条写回路径；任务库在别处被改时经 TaskStore 监听
 * 以 updateDataModel 回流，卡片原地刷新、组件不重建。
 * surface 不另存档：绑定关系（taskId）写在提案 JSON 的 createdId 里，
 * 聊天记录重开时按它重建 surface 并从任务库现读最新值——一份账。
 */
final class ChatDynUi implements DynUiCatalog.ActionHandler {

    private final Context act;
    private final TaskStore store;
    private final DynUiProcessor processor = new DynUiProcessor();
    private final List<String> taskIds;
    private final String surfaceId;
    private DynUiSurfaceView view;
    private TaskStore.ChangeListener storeListener;

    private ChatDynUi(Context act, TaskStore store, List<String> taskIds) {
        this.act = act;
        this.store = store;
        this.taskIds = taskIds;
        this.surfaceId = "chat-tasks-" + (taskIds.isEmpty() ? "empty" : taskIds.get(0));
    }

    /** 构建一组活卡片；无可绑定任务时返回 null（调用方走旧渲染兜底）。 */
    static View buildGroup(Context act, TaskStore store, List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return null;
        ChatDynUi glue = new ChatDynUi(act, store, new ArrayList<>(taskIds));
        glue.emitSurface();
        DynUiSurfaceView v = new DynUiSurfaceView(act, glue.processor, glue.surfaceId,
                DynUiCatalog.knowdoDefault(), glue);
        glue.view = v;
        glue.watchStore(v);
        return v;
    }

    // ---------------- surface 组装 ----------------

    private void emitSurface() {
        try {
            JSONArray msgs = new JSONArray();
            JSONObject create = new JSONObject();
            create.put("surfaceId", surfaceId);
            msgs.put(new JSONObject().put("createSurface", create));

            JSONObject dm = new JSONObject();
            dm.put("surfaceId", surfaceId);
            dm.put("path", "/");
            dm.put("value", dataRoot());
            msgs.put(new JSONObject().put("updateDataModel", dm));

            JSONArray comps = new JSONArray();
            for (int i = 0; i < taskIds.size(); i++) comps.put(cardSpec(i, taskIds.get(i)));
            JSONObject uc = new JSONObject();
            uc.put("surfaceId", surfaceId);
            uc.put("components", comps);
            msgs.put(new JSONObject().put("updateComponents", uc));
            processor.process(msgs.toString());
        } catch (Exception ignored) {
        }
    }

    private JSONObject dataRoot() throws Exception {
        JSONObject root = new JSONObject();
        JSONArray tasks = new JSONArray();
        for (String id : taskIds) tasks.put(taskProjection(id));
        root.put("tasks", tasks);
        root.put("edit", new JSONObject());
        return root;
    }

    /** 任务库 → 数据模型投影；任务已删则只剩 deleted 标记。 */
    private JSONObject taskProjection(String taskId) throws Exception {
        JSONObject o = new JSONObject();
        TaskItem t = store.byId(taskId);
        if (t == null || t.deleted) {
            o.put("deleted", true);
            o.put("title", t == null ? "" : t.title);
            return o;
        }
        o.put("deleted", false);
        o.put("title", t.title == null ? "" : t.title);
        o.put("durationMinutes", t.durationMinutes);
        o.put("priority", t.priority == null ? "secondary" : t.priority);
        o.put("deadline", t.deadline == null ? "" : t.deadline);
        return o;
    }

    private JSONObject cardSpec(int i, String taskId) throws Exception {
        JSONObject props = new JSONObject();
        props.put("taskId", taskId);
        props.put("title", bind("/tasks/" + i + "/title"));
        props.put("durationMinutes", bind("/tasks/" + i + "/durationMinutes"));
        props.put("priority", bind("/tasks/" + i + "/priority"));
        props.put("deadline", bind("/tasks/" + i + "/deadline"));
        props.put("deleted", bind("/tasks/" + i + "/deleted"));
        JSONObject spec = new JSONObject();
        spec.put("id", "card-" + taskId);
        spec.put("component", "TaskCard");
        spec.put("props", props);
        return spec;
    }

    private JSONObject formSpec(int i, String taskId) throws Exception {
        JSONObject props = new JSONObject();
        props.put("taskId", taskId);
        String base = "/edit/" + taskId;
        props.put("title", bind(base + "/title"));
        props.put("durationMinutes", bind(base + "/durationMinutes"));
        props.put("priority", bind(base + "/priority"));
        props.put("deadline", bind(base + "/deadline"));
        props.put("options", formOptions(taskId));
        JSONObject spec = new JSONObject();
        spec.put("id", "card-" + taskId);
        spec.put("component", "TaskEditForm");
        spec.put("props", props);
        return spec;
    }

    private JSONObject formOptions(String taskId) throws Exception {
        JSONObject options = new JSONObject();
        JSONArray durations = new JSONArray();
        for (int d : new int[]{10, 25, 45, 60}) durations.put(d);
        options.put("durations", durations);

        JSONArray priorities = new JSONArray();
        for (String p : new String[]{"must", "important", "secondary", "leisure"}) {
            priorities.put(new JSONObject()
                    .put("label", Strings.dynPriorityLabel(p)).put("value", p));
        }
        options.put("priorities", priorities);

        JSONArray deadlines = new JSONArray();
        deadlines.put(new JSONObject().put("label", Strings.dynDeadlineNone()).put("value", ""));
        deadlines.put(new JSONObject().put("label", Strings.dynToday()).put("value", TaskItem.today()));
        deadlines.put(new JSONObject().put("label", Strings.dynTomorrow()).put("value", plusDays(1)));
        deadlines.put(new JSONObject().put("label", Strings.dynSat()).put("value", nextWeekday(Calendar.SATURDAY)));
        deadlines.put(new JSONObject().put("label", Strings.dynNextMon()).put("value", nextWeekday(Calendar.MONDAY)));
        // 当前截止不在选项里时补一项原值，避免打开表单就丢信息
        TaskItem t = store.byId(taskId);
        String cur = t == null || t.deadline == null ? "" : t.deadline;
        boolean present = false;
        for (int i = 0; i < deadlines.length(); i++) {
            if (cur.equals(deadlines.getJSONObject(i).optString("value"))) { present = true; break; }
        }
        if (!cur.isEmpty() && !present) {
            deadlines.put(new JSONObject().put("label", cur).put("value", cur));
        }
        options.put("deadlines", deadlines);
        return options;
    }

    private static JSONObject bind(String path) throws Exception {
        return new JSONObject().put("path", path);
    }

    private static String plusDays(int n) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, n);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    /** 本周内（含今天）最近的该星期几；今天就是则取下周同一天，保证是未来日期。 */
    private static String nextWeekday(int dow) {
        Calendar cal = Calendar.getInstance();
        int add = (dow - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        if (add == 0) add = 7;
        cal.add(Calendar.DAY_OF_YEAR, add);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    // ---------------- 外部变更回流 ----------------

    private void watchStore(DynUiSurfaceView v) {
        storeListener = () -> refreshFromStore();
        TaskStore.addChangeListener(storeListener);
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View vv) { }
            @Override public void onViewDetachedFromWindow(View vv) {
                TaskStore.removeChangeListener(storeListener);
            }
        });
    }

    /** 任务库变了（列表页改的、同步回来的）→ 只推数据，卡片原地刷新。 */
    private void refreshFromStore() {
        try {
            for (int i = 0; i < taskIds.size(); i++) {
                JSONObject dm = new JSONObject();
                dm.put("surfaceId", surfaceId);
                dm.put("path", "/tasks/" + i);
                dm.put("value", taskProjection(taskIds.get(i)));
                processor.processOne(new JSONObject().put("updateDataModel", dm));
            }
        } catch (Exception ignored) {
        }
    }

    // ---------------- 动作处理 ----------------

    @Override
    public void onAction(String sid, String componentId, String action, JSONObject context) {
        String taskId = context.optString("taskId", "");
        int idx = taskIds.indexOf(taskId);
        if (idx < 0) return;
        try {
            if ("edit".equals(action)) {
                // 草稿区灌入当前值，再把该组件原位换成表单
                TaskItem t = store.byId(taskId);
                if (t == null || t.deleted) return;
                JSONObject seed = new JSONObject();
                seed.put("title", t.title == null ? "" : t.title);
                seed.put("durationMinutes", t.durationMinutes);
                seed.put("priority", t.priority == null ? "secondary" : t.priority);
                seed.put("deadline", t.deadline == null ? "" : t.deadline);
                JSONObject dm = new JSONObject();
                dm.put("surfaceId", surfaceId);
                dm.put("path", "/edit/" + taskId);
                dm.put("value", seed);
                processor.processOne(new JSONObject().put("updateDataModel", dm));
                swapComponent(formSpec(idx, taskId));
            } else if ("cancelEdit".equals(action)) {
                swapComponent(cardSpec(idx, taskId));
            } else if ("saveEdit".equals(action)) {
                TaskItem t = store.byId(taskId);
                if (t == null || t.deleted) return;
                JSONObject fields = new JSONObject();
                fields.put("title", context.optString("title", ""));
                fields.put("durationMinutes", context.optInt("durationMinutes", t.durationMinutes));
                fields.put("priority", context.optString("priority", ""));
                fields.put("deadline", context.optString("deadline", ""));
                ChatPanel.applyFieldsTo(t, fields, false);
                t.touch();
                store.save();
                StartReminder.reschedule(act, store);
                refreshFromStore();
                swapComponent(cardSpec(idx, taskId));
            } else if ("start".equals(action)) {
                TaskItem t = store.byId(taskId);
                if (t == null || t.deleted) return;
                if (!TimerService.startTimerSmart(act, t)) {
                    TimerService.startTimer(act, Math.max(1, t.durationMinutes) * 60, t.id, t.title);
                }
                Toast.makeText(act, Strings.dynStarted(), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {
        }
    }

    private void swapComponent(JSONObject spec) throws Exception {
        JSONObject uc = new JSONObject();
        uc.put("surfaceId", surfaceId);
        uc.put("components", new JSONArray().put(spec));
        processor.processOne(new JSONObject().put("updateComponents", uc));
    }
}
