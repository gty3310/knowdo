package com.ti.tasktimer.dynui;

import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ti.tasktimer.Strings;
import com.ti.tasktimer.Ui;

import org.json.JSONObject;

/**
 * 目录组件 TaskCard：一件任务的活卡片。
 * props：taskId（字面量，动作回传用）＋ title / durationMinutes / priority / deadline /
 * deleted 五个绑定（指向宿主数据模型里的任务投影）。
 * 主要动作最多两个：改（edit）、开始（start），宿主处理。
 * 数据变化时只原地刷新文本，不重建；任务被删后转已删态、动作消失。
 */
final class TaskCardRenderer implements DynUiCatalog.Renderer {

    @Override
    public View render(DynUiCatalog.RenderContext ctx, DynUiProcessor.ComponentSpec spec) {
        android.content.Context c = ctx.android;
        LinearLayout box = DynUiViews.cardBox(c);

        TextView title = new TextView(c);
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(Ui.text(c));
        box.addView(title);

        TextView meta = new TextView(c);
        meta.setTextSize(14.5f);
        meta.setTextColor(Ui.sub(c));
        meta.setPadding(0, DynUiViews.dp(c, 3), 0, 0);
        box.addView(meta);

        TextView gone = new TextView(c);
        gone.setText(Strings.dynTaskGone());
        gone.setTextSize(13.5f);
        gone.setTextColor(Ui.sub(c));
        gone.setPadding(0, DynUiViews.dp(c, 4), 0, 0);
        box.addView(gone);

        LinearLayout btnRow = DynUiViews.row(c);
        btnRow.setPadding(0, DynUiViews.dp(c, 6), 0, 0);
        TextView edit = DynUiViews.btn(c, Strings.dynEdit(), true);
        TextView start = DynUiViews.btn(c, Strings.dynStart(), false);
        btnRow.addView(edit);
        btnRow.addView(start);
        box.addView(btnRow);

        Runnable refresh = () -> {
            boolean deleted = isDeleted(ctx, spec);
            title.setText(ctx.value(spec, "title", ""));
            title.setTextColor(deleted ? Ui.sub(c) : Ui.text(c));
            meta.setText(metaLine(ctx, spec));
            gone.setVisibility(deleted ? View.VISIBLE : View.GONE);
            btnRow.setVisibility(deleted ? View.GONE : View.VISIBLE);
        };
        edit.setOnClickListener(v -> fire(ctx, spec, "edit"));
        start.setOnClickListener(v -> fire(ctx, spec, "start"));
        for (String key : new String[]{"title", "durationMinutes", "priority", "deadline", "deleted"}) {
            ctx.onChange(spec, key, refresh);
        }
        refresh.run();
        return box;
    }

    private boolean isDeleted(DynUiCatalog.RenderContext ctx, DynUiProcessor.ComponentSpec spec) {
        String path = DynUiProcessor.bindingPath(spec, "deleted");
        return path != null && ctx.surface.model.getBoolean(path, false);
    }

    private String metaLine(DynUiCatalog.RenderContext ctx, DynUiProcessor.ComponentSpec spec) {
        StringBuilder sb = new StringBuilder();
        String dur = ctx.value(spec, "durationMinutes", "");
        if (!dur.isEmpty()) sb.append(dur).append(Strings.dynMinUnit());
        String pri = ctx.value(spec, "priority", "");
        if (!pri.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(Strings.dynPriorityLabel(pri));
        }
        String dl = ctx.value(spec, "deadline", "");
        if (!dl.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(Strings.dynDuePrefix()).append(dl);
        }
        return sb.toString();
    }

    private void fire(DynUiCatalog.RenderContext ctx, DynUiProcessor.ComponentSpec spec, String action) {
        try {
            JSONObject context = new JSONObject();
            context.put("taskId", spec.props.optString("taskId", ""));
            ctx.fire(spec, action, context);
        } catch (Exception ignored) {
        }
    }
}
