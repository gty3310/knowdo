package com.ti.tasktimer.dynui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * dynamic UI 地基层之一：数据模型（v2.3.0）。
 * 语义照 A2UI 0.9.1：界面结构与运行数据分离，组件按 RFC 6901 JSON Pointer
 * 绑定数据路径；updateDataModel 对目标路径做 upsert，路径变化时只通知绑定
 * 在该路径子树上的监听者——数据刷新永远不触发组件重建。
 * 纯 Java + org.json，不依赖 Android 框架，可在 JVM 单元测试里直接跑。
 * 本层与任何宿主（聊天、守卫、晨间卡）无关，宿主只通过路径读写。
 */
public final class DynUiDataModel {

    public interface Listener {
        void onChange(String changedPointer);
    }

    private static final class Binding {
        final String pointer;
        final Listener listener;

        Binding(String pointer, Listener listener) {
            this.pointer = pointer;
            this.listener = listener;
        }
    }

    private JSONObject root = new JSONObject();
    private final List<Binding> bindings = new ArrayList<>();

    public void addListener(String pointer, Listener listener) {
        bindings.add(new Binding(pointer, listener));
    }

    /** 读路径值；不存在返回 null。pointer 形如 /tasks/0/durationMinutes，根为 "" 或 "/"。 */
    public Object get(String pointer) {
        Object cur = root;
        for (String tok : tokens(pointer)) {
            if (cur instanceof JSONObject) {
                cur = ((JSONObject) cur).opt(tok);
            } else if (cur instanceof JSONArray) {
                int i = parseIndex(tok);
                if (i < 0) return null;
                cur = ((JSONArray) cur).opt(i);
            } else {
                return null;
            }
            if (cur == null) return null;
        }
        return cur;
    }

    public String getString(String pointer, String fallback) {
        Object v = get(pointer);
        return v == null ? fallback : String.valueOf(v);
    }

    public boolean getBoolean(String pointer, boolean fallback) {
        Object v = get(pointer);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    /** upsert：中间缺失的对象/数组按下一 token 是否为数字自动创建。 */
    public void set(String pointer, Object value) {
        List<String> toks = tokens(pointer);
        if (toks.isEmpty()) {
            if (value instanceof JSONObject) root = (JSONObject) value;
            notify(pointer);
            return;
        }
        Object cur = root;
        for (int i = 0; i < toks.size() - 1; i++) {
            String tok = toks.get(i);
            boolean nextIsIndex = parseIndex(toks.get(i + 1)) >= 0;
            Object next = child(cur, tok);
            if (next == null) {
                next = nextIsIndex ? new JSONArray() : new JSONObject();
                putChild(cur, tok, next);
            }
            cur = next;
        }
        putChild(cur, toks.get(toks.size() - 1), value);
        notify(pointer);
    }

    /** 整棵数据模型的快照（存档用）；宿主存下它即可在重开时恢复 surface。 */
    public JSONObject snapshot() {
        try {
            return new JSONObject(root.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void notify(String changedPointer) {
        for (Binding b : new ArrayList<>(bindings)) {
            // 绑定路径是变化路径的前缀（绑 /tasks/0 时 /tasks/0/durationMinutes 的变化要通知），
            // 或变化路径是绑定路径的前缀（整段被替换时也要通知）
            if (changedPointer.equals(b.pointer)
                    || changedPointer.startsWith(b.pointer + "/")
                    || b.pointer.startsWith(changedPointer + "/")) {
                b.listener.onChange(changedPointer);
            }
        }
    }

    private static List<String> tokens(String pointer) {
        List<String> out = new ArrayList<>();
        if (pointer == null || pointer.isEmpty() || "/".equals(pointer)) return out;
        String p = pointer.startsWith("/") ? pointer.substring(1) : pointer;
        for (String t : p.split("/")) {
            out.add(t.replace("~1", "/").replace("~0", "~"));
        }
        return out;
    }

    private static int parseIndex(String tok) {
        try {
            int i = Integer.parseInt(tok);
            return i >= 0 ? i : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Object child(Object container, String tok) {
        if (container instanceof JSONObject) return ((JSONObject) container).opt(tok);
        if (container instanceof JSONArray) {
            int i = parseIndex(tok);
            return i >= 0 ? ((JSONArray) container).opt(i) : null;
        }
        return null;
    }

    private static void putChild(Object container, String tok, Object value) {
        try {
            if (container instanceof JSONObject) {
                ((JSONObject) container).put(tok, value);
            } else if (container instanceof JSONArray) {
                int i = parseIndex(tok);
                JSONArray arr = (JSONArray) container;
                while (arr.length() <= i) arr.put(JSONObject.NULL);
                arr.put(i, value);
            }
        } catch (Exception ignored) {
            // 写失败静默：调用方以读回值校验为准
        }
    }
}
