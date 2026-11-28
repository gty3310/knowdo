package com.ti.tasktimer.dynui;

import android.view.View;
import android.widget.TextView;

import com.ti.tasktimer.Ui;

/**
 * 目录组件 Text：一段文字，可绑定数据路径（如今日总结由宿主现读现算后写进模型）。
 * props：text 为 {"path": "..."} 绑定或字面量。
 */
final class TextRenderer implements DynUiCatalog.Renderer {

    @Override
    public View render(DynUiCatalog.RenderContext ctx, DynUiProcessor.ComponentSpec spec) {
        TextView tv = new TextView(ctx.android);
        tv.setTextSize(15);
        tv.setTextColor(Ui.text(ctx.android));
        tv.setPadding(0, DynUiViews.dp(ctx.android, 4), 0, DynUiViews.dp(ctx.android, 6));
        tv.setText(ctx.value(spec, "text", ""));
        ctx.onChange(spec, "text", () -> tv.setText(ctx.value(spec, "text", "")));
        return tv;
    }
}
