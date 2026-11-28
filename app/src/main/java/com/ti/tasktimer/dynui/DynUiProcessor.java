package com.ti.tasktimer.dynui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * dynamic UI 地基层之二：协议消息处理器（v2.3.0）。
 * 消息形状照 A2UI 0.9.1 的四种服务端→客户端消息：
 * createSurface / updateComponents / updateDataModel / deleteSurface。
 * Surface 的状态（组件表＋数据模型）由处理器持有、独立于任何 View：
 * View 销毁重建后按 surfaceId 重新绑定，拿到的仍是同一组组件与最新数据——
 * 这是「滚回去还是原来那张卡」的结构基础。
 * 组件结构变化通过 SurfaceListener 通知宿主容器（只重渲被替换的那一个组件）；
 * 数据变化走 DynUiDataModel 自己的路径监听，不经过这里。
 * 纯 Java + org.json，不依赖 Android 框架，可在 JVM 单元测试里直接跑。
 */
public final class DynUiProcessor {

    /** 组件结构变化监听（数据变化不走这里）。 */
    public interface SurfaceListener {
        /** 某组件被新增或替换（componentIds 为本次涉及的 id）。 */
        void onComponentsChanged(String surfaceId, List<String> componentIds);
        void onSurfaceDeleted(String surfaceId);
    }

    public static final class ComponentSpec {
        public final String id;
        public final String type;
        /** 原始属性 JSON；动态值写成 {"path": "/tasks/0/title"} 的绑定形状 */
        public final JSONObject props;

        ComponentSpec(String id, String type, JSONObject props) {
            this.id = id;
            this.type = type;
            this.props = props;
        }
    }

    public static final class Surface {
        public final String id;
        public final DynUiDataModel model = new DynUiDataModel();
        public final List<ComponentSpec> components = new ArrayList<>();

        Surface(String id) {
            this.id = id;
        }

        public ComponentSpec component(String componentId) {
            for (ComponentSpec c : components) if (c.id.equals(componentId)) return c;
            return null;
        }
    }

    private final Map<String, Surface> surfaces = new LinkedHashMap<>();
    private final List<SurfaceListener> listeners = new ArrayList<>();

    public void addSurfaceListener(SurfaceListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeSurfaceListener(SurfaceListener l) {
        listeners.remove(l);
    }

    public Surface surface(String id) {
        return surfaces.get(id);
    }

    public int surfaceCount() {
        return surfaces.size();
    }

    /** 处理一串消息（JSON 数组文本，元素为四种消息之一）。单条坏消息只跳过该条。 */
    public void process(String messagesJson) {
        try {
            JSONArray arr = new JSONArray(messagesJson);
            for (int i = 0; i < arr.length(); i++) {
                try {
                    processOne(arr.getJSONObject(i));
                } catch (Exception ignored) {
                    // 单条消息坏了不拖垮整串（降级口径）
                }
            }
        } catch (Exception ignored) {
            // 整串非法：什么都不做，宿主界面保持原样
        }
    }

    public void processOne(JSONObject msg) throws Exception {
        if (msg.has("createSurface")) {
            JSONObject b = msg.getJSONObject("createSurface");
            String id = b.getString("surfaceId");
            surfaces.put(id, new Surface(id));
        } else if (msg.has("updateComponents")) {
            JSONObject b = msg.getJSONObject("updateComponents");
            Surface s = surfaces.get(b.getString("surfaceId"));
            if (s == null) return;
            JSONArray comps = b.getJSONArray("components");
            List<String> changed = new ArrayList<>();
            for (int i = 0; i < comps.length(); i++) {
                JSONObject c = comps.getJSONObject(i);
                String cid = c.getString("id");
                // 同 id 组件按替换处理（原地更新语义），不重复追加
                s.components.removeIf(old -> old.id.equals(cid));
                JSONObject props = c.optJSONObject("props");
                s.components.add(new ComponentSpec(cid, c.getString("component"),
                        props == null ? new JSONObject() : props));
                changed.add(cid);
            }
            for (SurfaceListener l : new ArrayList<>(listeners)) {
                l.onComponentsChanged(s.id, changed);
            }
        } else if (msg.has("updateDataModel")) {
            JSONObject b = msg.getJSONObject("updateDataModel");
            Surface s = surfaces.get(b.getString("surfaceId"));
            if (s == null) return;
            String path = b.optString("path", "/");
            Object value = b.has("value") ? b.get("value") : JSONObject.NULL;
            s.model.set(path, value);
        } else if (msg.has("deleteSurface")) {
            JSONObject b = msg.getJSONObject("deleteSurface");
            String id = b.getString("surfaceId");
            if (surfaces.remove(id) != null) {
                for (SurfaceListener l : new ArrayList<>(listeners)) {
                    l.onSurfaceDeleted(id);
                }
            }
        }
    }

    /** 从 props 里取绑定路径：props.<key> 形如 {"path": "/tasks/0/title"} 时返回该路径，否则 null。 */
    public static String bindingPath(ComponentSpec spec, String key) {
        JSONObject bind = spec.props.optJSONObject(key);
        if (bind == null) return null;
        String p = bind.optString("path", "");
        return p.isEmpty() ? null : p;
    }
}
