package com.ti.tasktimer.dynui;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.ti.tasktimer.Ui;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * dynamic UI 地基层之三：组件目录（v2.3.0）。
 * 目录是「AI 只能从里面选组件」的白名单：类型名 → 渲染器工厂。
 * 宿主可以换目录不换渲染管线；目录外的类型名一律降级为 Text 显示，
 * 并记一条诊断日志，绝不崩溃、绝不整面失效。
 * 本版目录只有三样：TaskCard / TaskEditForm / Text（用户 2026-11-28 定）。
 * 扩展点：register 即可加新组件（如目录扩展清单第一项 Image），
 * 守卫问询、选下一个、晨间/晚间卡等后续宿主共用同一目录与管线。
 */
public final class DynUiCatalog {

    /** 组件动作统一出口：宿主处理后以 updateDataModel / updateComponents 推回结果。 */
    public interface ActionHandler {
        void onAction(String surfaceId, String componentId, String action, JSONObject context);
    }

    public interface Renderer {
        View render(RenderContext ctx, DynUiProcessor.ComponentSpec spec);
    }

    /** 渲染上下文：渲染器只能经它读数据模型、发动作，不直接依赖宿主业务对象。 */
    public static final class RenderContext {
        public final Context android;
        public final DynUiProcessor.Surface surface;
        public final ActionHandler actions;

        RenderContext(Context android, DynUiProcessor.Surface surface, ActionHandler actions) {
            this.android = android;
            this.surface = surface;
            this.actions = actions;
        }

        /** 取 props 里某键的当前值：是绑定则读数据模型，是字面量则直接用。 */
        public String value(DynUiProcessor.ComponentSpec spec, String key, String fallback) {
            String path = DynUiProcessor.bindingPath(spec, key);
            if (path != null) return surface.model.getString(path, fallback);
            String lit = spec.props.optString(key, "");
            return lit.isEmpty() ? fallback : lit;
        }

        /** 订阅 props 某键所绑路径的变化（字面量键不订阅）。 */
        public void onChange(DynUiProcessor.ComponentSpec spec, String key, Runnable refresh) {
            String path = DynUiProcessor.bindingPath(spec, key);
            if (path != null) surface.model.addListener(path, changed -> refresh.run());
        }

        public void fire(DynUiProcessor.ComponentSpec spec, String action, JSONObject context) {
            actions.onAction(surface.id, spec.id, action, context);
        }
    }

    private final Map<String, Renderer> renderers = new LinkedHashMap<>();

    public DynUiCatalog register(String type, Renderer renderer) {
        renderers.put(type, renderer);
        return this;
    }

    public boolean has(String type) {
        return renderers.containsKey(type);
    }

    /** 渲染一个组件；目录外类型降级为 Text（显示其 text 字面量或类型名）并记日志。 */
    public View render(RenderContext ctx, DynUiProcessor.ComponentSpec spec) {
        Renderer r = renderers.get(spec.type);
        if (r == null) {
            Log.w("DynUi", "unknown component type '" + spec.type + "' -> Text fallback");
            TextView tv = new TextView(ctx.android);
            String t = spec.props.optString("text", "");
            tv.setText(t.isEmpty() ? spec.type : t);
            tv.setTextSize(15);
            tv.setTextColor(Ui.text(ctx.android));
            return tv;
        }
        return r.render(ctx, spec);
    }

    /** 知行本版目录：三样组件。 */
    public static DynUiCatalog knowdoDefault() {
        return new DynUiCatalog()
                .register("TaskCard", new TaskCardRenderer())
                .register("TaskEditForm", new TaskEditFormRenderer())
                .register("Text", new TextRenderer());
    }
}
