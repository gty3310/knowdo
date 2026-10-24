package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 子任务队列面板（v1.11.0，主页展开区与大号悬浮窗 EDIT 共用同一组件）：
 * 标题行＋子任务行（拖动手柄 ≡＋序号·名称＋分钟/剩余）＋底部「＋ 加子任务」。
 * 点行＝请宿主打开编辑（主页＝对话框、悬浮窗＝卡内行内表单，宿主决定）；
 * 按住手柄上下拖＝改顺序（松手才落盘，拖动中只移视图）。任何编辑经宿主回调落盘，
 * 组件内统一先调 syncDurationFromSubtasks 保持「总时长＝子任务之和」。
 * 计时中的当前子任务行高亮并显示其剩余（由宿主提供运行态信息）。
 */
public class SubtaskPanel extends LinearLayout {

    public interface Host {
        /** 子任务列表已变更（增删改/排序）：宿主负责落盘与重渲染。 */
        void onSubtasksEdited(TaskItem t);
        /** 请宿主打开子任务编辑（index=-1 表示新增）。 */
        void requestEditSubtask(TaskItem t, int index);
        /** 当前正在跑的子任务序号（针对该任务），没有返回 -1。 */
        int runningIndex(TaskItem t);
        /** 正在跑的子任务的剩余文案（如「剩 03:12」），没有返回 null。 */
        String runningRemainText(TaskItem t);
        /** 标题行右侧小字（进度/总时长），没有返回 null。 */
        String headerRightText(TaskItem t);
    }

    private final boolean dark;
    private final boolean compact;
    private TaskItem task;
    private Host host;
    private TextView currentRemainView;
    private int currentRemainIndex = -1;

    public SubtaskPanel(Context c, boolean dark, boolean compact) {
        super(c);
        this.dark = dark;
        this.compact = compact;
        setOrientation(VERTICAL);
    }

    public void bind(TaskItem t, Host h) {
        task = t;
        host = h;
        rebuild();
    }

    private int textCol() { return dark ? Color.WHITE : Ui.text(getContext()); }
    private int subCol() { return dark ? Color.rgb(176, 176, 176) : Ui.sub(getContext()); }

    public void rebuild() {
        removeAllViews();
        currentRemainView = null;
        currentRemainIndex = -1;
        if (task == null || host == null) return;
        Context c = getContext();

        // 标题行
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(c);
        title.setText(Strings.subtasksTitle());
        title.setTextSize(compact ? 12 : 13);
        title.setTextColor(subCol());
        head.addView(title);
        String right = host.headerRightText(task);
        if (right != null) {
            TextView r = new TextView(c);
            r.setText(right);
            r.setTextSize(compact ? 11 : 12);
            r.setTextColor(subCol());
            r.setGravity(Gravity.END);
            r.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            head.addView(r);
        }
        addView(head);

        int runIdx = host.runningIndex(task);
        for (int i = 0; i < task.subtasks.size(); i++) {
            addView(rowView(i, task.subtasks.get(i), i == runIdx));
        }

        TextView add = new TextView(c);
        add.setText(Strings.subAdd());
        add.setTextSize(compact ? 12.5f : 13.5f);
        add.setTextColor(subCol());
        add.setPadding(0, dp(compact ? 5 : 7), 0, dp(2));
        add.setOnClickListener(v -> host.requestEditSubtask(task, -1));
        addView(add);
    }

    private View rowView(final int index, final Subtask s, boolean running) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int rowH = dp(compact ? 30 : 38);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, rowH));
        row.setPadding(dp(4), 0, dp(4), 0);
        if (running) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(dark ? Color.argb(46, 226, 170, 61)
                    : Color.argb(30, 226, 170, 61));
            g.setCornerRadius(dp(8));
            row.setBackground(g);
        }

        TextView handle = new TextView(c);
        handle.setText("≡");
        handle.setTextSize(15);
        handle.setTextColor(subCol());
        handle.setGravity(Gravity.CENTER);
        handle.setLayoutParams(new LinearLayout.LayoutParams(dp(26), rowH));
        handle.setOnTouchListener(new Dragger(index, row));
        row.addView(handle);

        TextView name = new TextView(c);
        name.setText((index + 1) + " · " + s.title);
        name.setTextSize(compact ? 13 : 14);
        name.setTextColor(textCol());
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        name.setOnClickListener(v -> host.requestEditSubtask(task, index));
        row.addView(name);

        TextView mins = new TextView(c);
        if (running) {
            String rt = host.runningRemainText(task);
            mins.setText(rt != null ? rt : s.minutes + (Strings.en() ? " min" : " 分钟"));
            currentRemainView = mins;
            currentRemainIndex = index;
        } else {
            mins.setText(s.minutes + (Strings.en() ? " min" : " 分钟"));
        }
        mins.setTextSize(compact ? 11.5f : 12.5f);
        mins.setTextColor(running ? (dark ? Color.rgb(255, 217, 122) : Ui.accent(c)) : subCol());
        row.addView(mins);
        return row;
    }

    /** 刷新节拍里只更新当前子任务的剩余文案（不重建整表，免得打断拖动/编辑）。 */
    public void tickRemain() {
        if (currentRemainView != null && host != null && task != null) {
            String rt = host.runningRemainText(task);
            if (rt != null) currentRemainView.setText(rt);
        }
    }

    /** 列表签名：宿主据此判断要不要整表重建（编辑/拖动中签名不变则跳过）。 */
    public String signature(TaskItem t, int runIdx) {
        if (t == null) return "null";
        StringBuilder sb = new StringBuilder(t.id).append('#').append(runIdx);
        for (Subtask s : t.subtasks) {
            sb.append('|').append(s.id).append(',').append(s.title).append(',').append(s.minutes);
        }
        return sb.toString();
    }

    /** 拖动手柄：按住上下拖，松手时按位移整行数落位并回调宿主落盘。 */
    private class Dragger implements View.OnTouchListener {
        private final int index;
        private final View row;
        private float startY;
        private int steps;

        Dragger(int index, View row) {
            this.index = index;
            this.row = row;
        }

        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY = e.getRawY();
                    steps = 0;
                    if (row.getParent() != null) {
                        row.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    row.setAlpha(0.75f);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = e.getRawY() - startY;
                    row.setTranslationY(dy);
                    int h = Math.max(1, row.getHeight());
                    steps = Math.round(dy / h);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    row.setTranslationY(0);
                    row.setAlpha(1f);
                    if (row.getParent() != null) {
                        row.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    int target = index + steps;
                    if (target < 0) target = 0;
                    if (target >= task.subtasks.size()) target = task.subtasks.size() - 1;
                    if (target != index && task.subtasks.size() > 1) {
                        Subtask s = task.subtasks.remove(index);
                        task.subtasks.add(target, s);
                        task.syncDurationFromSubtasks();
                        task.touch();
                        host.onSubtasksEdited(task);
                    }
                    return true;
                }
                default:
                    return false;
            }
        }
    }

    private int dp(int v) {
        return (int) (v * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
