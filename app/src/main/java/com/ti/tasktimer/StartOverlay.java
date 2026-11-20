package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 开始提醒的 99% 大窗内容（Guild Issue #6 第二批）：与 StartRemindActivity
 * 同画面、同文案、同动作——「到时间了」＋任务名与计划时长＋开始计时／
 * 延后 10 分钟／忽略三按钮。开始计时清掉一次性开始时间并 reschedule、
 * 走 startTimerSmart（与原页逐字相同）；延后把 nextStartAt 顺延 10 分钟、
 * 保存＋reschedule＋后台线程同步回桥（与原页同一条线程逻辑）；忽略只
 * 收窗什么都不改。任务已删时与原页同：toast 提示后关页（大窗为 tick
 * 返回 false 自动收回）。
 */
public final class StartOverlay implements BigOverlay.Content {
    private final String taskId;
    private Context ctx;
    private TaskStore store;
    private TaskItem task;
    private boolean valid;

    StartOverlay(String taskId) { this.taskId = taskId; }

    @Override
    public View createView(Context c) {
        ctx = c;
        Strings.init(c);
        store = new TaskStore(c);
        task = taskId == null ? null : store.byId(taskId);
        valid = task != null && !task.deleted;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(23, 34, 29));
        int pad = dp(28);
        root.setPadding(pad, pad, pad, pad);
        if (!valid) {
            Toast.makeText(c, "这个任务已经不在了", Toast.LENGTH_LONG).show();
            return root;
        }

        TextView bell = new TextView(c);
        bell.setText("⏰ 到时间了");
        bell.setTextColor(Color.WHITE);
        bell.setTextSize(24);
        bell.setGravity(Gravity.CENTER);
        root.addView(bell);

        TextView taskView = new TextView(c);
        taskView.setText("开始「" + task.title + "」吧\n计划时长 " + task.durationMinutes + " 分钟");
        taskView.setTextColor(Color.rgb(200, 200, 200));
        taskView.setTextSize(17);
        taskView.setGravity(Gravity.CENTER);
        taskView.setPadding(0, dp(14), 0, dp(22));
        root.addView(taskView);

        Button go = bigButton("开始计时");
        go.setOnClickListener(v -> {
            // 一次性提醒：开始了就把这个开始时间清掉（与原页同）
            task.nextStartAt = null;
            task.touch();
            store.save();
            StartReminder.reschedule(ctx, store);
            if (!TimerService.startTimerSmart(ctx, task)) {
                TimerService.startTimer(ctx, Math.max(1, task.durationMinutes) * 60, task.id, task.title);
            }
            finish();
        });
        root.addView(go);

        Button snooze = bigButton("延后 10 分钟");
        snooze.setOnClickListener(v -> {
            long base = Math.max(task.nextStartMs(), System.currentTimeMillis());
            task.nextStartAt = TaskItem.formatNextStart(base + 10 * 60_000L);
            task.touch();
            store.save();
            StartReminder.reschedule(ctx, store);
            // 改动同步回桥（后台线程，口径与原页一致）
            new Thread(() -> {
                try {
                    String r = SbSync.syncNow(ctx, store);
                    SyncManager.setLastResult(ctx, r);
                } catch (Exception ignored) { }
            }).start();
            Toast.makeText(ctx, "好，10 分钟后再叫你开始「" + task.title + "」", Toast.LENGTH_SHORT).show();
            finish();
        });
        root.addView(snooze);

        Button dismiss = bigButton("忽略");
        dismiss.setOnClickListener(v -> finish());
        root.addView(dismiss);
        return root;
    }

    @Override
    public void onShown(Context c) {
        TopPages.startRemindShown = true; // 与原页 onResume 同登记
    }

    @Override
    public boolean tick(Context c) {
        if (!valid) return false;
        // 任务已在别处开始：本页没有存在的意义了
        return !(TimerState.isRunning(c) && task != null && task.id.equals(TimerState.taskId(c)));
    }

    @Override
    public void onDismissed(Context c) {
        TopPages.startRemindShown = false;
    }

    private void finish() { BigOverlay.dismiss(ctx, BigOverlay.KEY_START); }

    private Button bigButton(String s) {
        Button b = new Button(ctx);
        b.setText(s);
        b.setTextSize(17);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        p.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(p);
        return b;
    }

    private int dp(int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
