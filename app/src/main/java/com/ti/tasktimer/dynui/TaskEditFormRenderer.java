package com.ti.tasktimer.dynui;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ti.tasktimer.Strings;
import com.ti.tasktimer.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录组件 TaskEditForm：一件任务的编辑表单（改现在的任务／卡上点「改」展开的就是它）。
 * props：taskId（字面量）＋ title / durationMinutes / priority / deadline 四个绑定
 * （指向宿主在数据模型里开的 /edit 草稿区，宿主进表单前灌入当前值）＋
 * options{durations:[分钟数], priorities:[{label,value}], deadlines:[{label,value}]}。
 * 编辑只动表单内的临时态；点保存才以 saveEdit 动作把整组字段回给宿主写库，
 * 点取消以 cancelEdit 回宿主。动作只有这两个，合「最多两个主要动作」口径。
 */
final class TaskEditFormRenderer implements DynUiCatalog.Renderer {

    @Override
    public View render(DynUiCatalog.RenderContext ctx, DynUiProcessor.ComponentSpec spec) {
        android.content.Context c = ctx.android;
        LinearLayout box = DynUiViews.cardBox(c);

        EditText titleIn = new EditText(c);
        titleIn.setText(ctx.value(spec, "title", ""));
        titleIn.setTextSize(16);
        titleIn.setTextColor(Ui.text(c));
        titleIn.setSingleLine(true);
        titleIn.setHint(Strings.dynTitleHint());
        box.addView(titleIn);

        JSONObject options = spec.props.optJSONObject("options");
        // 时长片
        List<Integer> durations = new ArrayList<>();
        JSONArray dArr = options == null ? null : options.optJSONArray("durations");
        if (dArr != null) for (int i = 0; i < dArr.length(); i++) durations.add(dArr.optInt(i, 0));
        int curDur = parseInt(ctx.value(spec, "durationMinutes", ""), 0);
        if (curDur > 0 && !durations.contains(curDur)) durations.add(0, curDur);
        final int[] selDur = {curDur};
        LinearLayout durRow = DynUiViews.row(c);
        box.addView(label(c, Strings.dynDurationLabel()));
        box.addView(durRow);
        drawDurChips(c, durRow, durations, selDur);

        // 截止片（选项由宿主按真实日历算好：今天/明天/周六/下周一/不设截止）
        List<String[]> deadlines = options(options, "deadlines");
        final String[] selDl = {ctx.value(spec, "deadline", "")};
        LinearLayout dlRow = DynUiViews.row(c);
        box.addView(label(c, Strings.dynDeadlineLabel()));
        box.addView(dlRow);
        drawOptionChips(c, dlRow, deadlines, selDl);

        // 优先级片
        List<String[]> priorities = options(options, "priorities");
        final String[] selPri = {ctx.value(spec, "priority", "")};
        LinearLayout priRow = DynUiViews.row(c);
        box.addView(label(c, Strings.dynPriorityTitle()));
        box.addView(priRow);
        drawOptionChips(c, priRow, priorities, selPri);

        LinearLayout btnRow = DynUiViews.row(c);
        btnRow.setPadding(0, DynUiViews.dp(c, 8), 0, 0);
        TextView save = DynUiViews.btn(c, Strings.dynSave(), true);
        TextView cancel = DynUiViews.btn(c, Strings.dynCancel(), false);
        btnRow.addView(save);
        btnRow.addView(cancel);
        box.addView(btnRow);

        save.setOnClickListener(v -> {
            String title = titleIn.getText().toString().trim();
            if (title.isEmpty()) {
                titleIn.requestFocus();
                return;
            }
            try {
                JSONObject context = new JSONObject();
                context.put("taskId", spec.props.optString("taskId", ""));
                context.put("title", title);
                context.put("durationMinutes", selDur[0]);
                context.put("priority", selPri[0]);
                context.put("deadline", selDl[0]);
                ctx.fire(spec, "saveEdit", context);
            } catch (Exception ignored) {
            }
        });
        cancel.setOnClickListener(v -> {
            try {
                JSONObject context = new JSONObject();
                context.put("taskId", spec.props.optString("taskId", ""));
                ctx.fire(spec, "cancelEdit", context);
            } catch (Exception ignored) {
            }
        });
        return box;
    }

    private TextView label(android.content.Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(Ui.sub(c));
        t.setPadding(0, DynUiViews.dp(c, 8), 0, 0);
        return t;
    }

    private void drawDurChips(android.content.Context c, LinearLayout row,
                              List<Integer> values, int[] sel) {
        row.removeAllViews();
        for (int d : values) {
            TextView chip = DynUiViews.chip(c, d + Strings.dynMinUnit(), d == sel[0]);
            chip.setOnClickListener(v -> {
                sel[0] = d;
                drawDurChips(c, row, values, sel);
            });
            row.addView(chip);
        }
    }

    /** 通用 {label,value} 选项片组：点击更新 sel[0] 并重画选中态。 */
    private void drawOptionChips(android.content.Context c, LinearLayout row,
                                 List<String[]> options, String[] sel) {
        row.removeAllViews();
        for (String[] o : options) {
            TextView chip = DynUiViews.chip(c, o[0], o[1].equals(sel[0]));
            chip.setOnClickListener(v -> {
                sel[0] = o[1];
                drawOptionChips(c, row, options, sel);
            });
            row.addView(chip);
        }
    }

    private List<String[]> options(JSONObject options, String key) {
        List<String[]> out = new ArrayList<>();
        JSONArray arr = options == null ? null : options.optJSONArray(key);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(new String[]{o.optString("label", ""), o.optString("value", "")});
            }
        }
        return out;
    }

    private int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
