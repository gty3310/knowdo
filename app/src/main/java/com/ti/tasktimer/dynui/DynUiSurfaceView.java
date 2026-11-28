package com.ti.tasktimer.dynui;

import android.content.Context;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * dynamic UI 的宿主容器：把一个 surface 的组件表渲染成一列 View。
 * 与具体宿主（聊天窗、问询窗、晨间页）无关——宿主只需给处理器、surfaceId、
 * 目录与动作处理器，换个窗口即可复用。
 * 行为口径：
 * - 数据变化（updateDataModel）只经数据模型监听原地刷新文本，本容器不重建；
 * - 组件被替换（updateComponents 同 id）只重渲那一个组件、原位换入；
 * - 容器销毁重建后按同一 surface 重新绑定，组件与数据都是处理器里的那一份。
 */
public final class DynUiSurfaceView extends LinearLayout implements DynUiProcessor.SurfaceListener {

    private final DynUiProcessor processor;
    private final String surfaceId;
    private final DynUiCatalog catalog;
    private final DynUiCatalog.ActionHandler actions;
    private final Map<String, android.view.View> slots = new LinkedHashMap<>();
    private int buildCount = 0;
    private boolean listening = false;

    public DynUiSurfaceView(Context c, DynUiProcessor processor, String surfaceId,
                            DynUiCatalog catalog, DynUiCatalog.ActionHandler actions) {
        super(c);
        setOrientation(VERTICAL);
        this.processor = processor;
        this.surfaceId = surfaceId;
        this.catalog = catalog;
        this.actions = actions;
        buildAll();
    }

    /** 容器整体重建过几次（验收/诊断用：数据刷新不应让它增长）。 */
    public int buildCount() {
        return buildCount;
    }

    public int slotCount() {
        return slots.size();
    }

    private void buildAll() {
        buildCount++;
        removeAllViews();
        slots.clear();
        DynUiProcessor.Surface s = processor.surface(surfaceId);
        if (s == null) return;
        DynUiCatalog.RenderContext ctx = new DynUiCatalog.RenderContext(getContext(), s, actions);
        for (DynUiProcessor.ComponentSpec spec : s.components) {
            android.view.View v = catalog.render(ctx, spec);
            slots.put(spec.id, v);
            addView(v);
        }
    }

    @Override
    public void onComponentsChanged(String changedSurfaceId, List<String> componentIds) {
        if (!surfaceId.equals(changedSurfaceId)) return;
        DynUiProcessor.Surface s = processor.surface(surfaceId);
        if (s == null) return;
        DynUiCatalog.RenderContext ctx = new DynUiCatalog.RenderContext(getContext(), s, actions);
        for (String cid : componentIds) {
            DynUiProcessor.ComponentSpec spec = s.component(cid);
            android.view.View old = slots.get(cid);
            if (spec == null) {
                if (old != null) {
                    removeView(old);
                    slots.remove(cid);
                }
                continue;
            }
            android.view.View fresh = catalog.render(ctx, spec);
            if (old != null) {
                int idx = indexOfChild(old);
                removeViewAt(idx);
                addView(fresh, idx);
            } else {
                addView(fresh);
            }
            slots.put(cid, fresh);
        }
    }

    @Override
    public void onSurfaceDeleted(String deletedSurfaceId) {
        if (!surfaceId.equals(deletedSurfaceId)) return;
        removeAllViews();
        slots.clear();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!listening) {
            processor.addSurfaceListener(this);
            listening = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (listening) {
            processor.removeSurfaceListener(this);
            listening = false;
        }
        super.onDetachedFromWindow();
    }

    /** 宿主在外部数据源变化后调：整面按处理器里的最新状态重绑（仍是同一组组件定义）。 */
    public void rebind() {
        buildAll();
    }

    /** 便捷转发：宿主处理动作时构造上下文 JSON 用。 */
    public static JSONObject actionContext(String key, String value) {
        JSONObject o = new JSONObject();
        try {
            o.put(key, value);
        } catch (Exception ignored) {
        }
        return o;
    }
}
