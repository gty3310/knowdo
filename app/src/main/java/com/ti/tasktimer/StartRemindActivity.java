package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 开始提醒页：任务的「下次开始时间」到了，全屏叫用户开始这个任务。
 * 三个选择：开始计时（走现有 TimerService 开始逻辑，并清掉这个一次性开始时间）/
 * 延后 10 分钟（nextStartAt 顺延并同步到桥）/ 忽略（只关页，什么都不改）。
 */
public class StartRemindActivity extends Activity {
    private TaskStore store;
    private TaskItem task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new TaskStore(this);
        String taskId = getIntent().getStringExtra(StartReminder.EXTRA_TASK_ID);
        task = taskId == null ? null : store.byId(taskId);
        if (task == null || task.deleted) {
            Toast.makeText(this, "这个任务已经不在了", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(24, 24, 24));
        int pad = dp(28);
        root.setPadding(pad, pad, pad, pad);
        setContentView(root);

        TextView bell = new TextView(this);
        bell.setText("⏰ 到时间了");
        bell.setTextColor(Color.WHITE);
        bell.setTextSize(24);
        bell.setGravity(Gravity.CENTER);
        root.addView(bell);

        TextView taskView = new TextView(this);
        taskView.setText("开始「" + task.title + "」吧\n计划时长 " + task.durationMinutes + " 分钟");
        taskView.setTextColor(Color.rgb(200, 200, 200));
        taskView.setTextSize(17);
        taskView.setGravity(Gravity.CENTER);
        taskView.setPadding(0, dp(14), 0, dp(22));
        root.addView(taskView);

        Button go = bigButton("开始计时");
        go.setOnClickListener(v -> {
            // 一次性提醒：开始了就把这个开始时间清掉，避免列表里留着过期的 ▶ 时间
            task.nextStartAt = null;
            task.touch();
            store.save();
            StartReminder.reschedule(this, store);
            TimerService.startTimer(this, Math.max(1, task.durationMinutes) * 60, task.id, task.title);
            finish();
        });
        root.addView(go);

        Button snooze = bigButton("延后 10 分钟");
        snooze.setOnClickListener(v -> {
            long base = Math.max(task.nextStartMs(), System.currentTimeMillis());
            task.nextStartAt = TaskItem.formatNextStart(base + 10 * 60_000L);
            task.touch();
            store.save();
            StartReminder.reschedule(this, store);
            // 改动同步回桥（后台线程，口径与主界面手动同步一致）
            new Thread(() -> {
                try {
                    String r = SyncManager.syncNow(this, store);
                    SyncManager.setLastResult(this, r);
                } catch (Exception ignored) { }
            }).start();
            Toast.makeText(this, "好，10 分钟后再叫你开始「" + task.title + "」", Toast.LENGTH_SHORT).show();
            finish();
        });
        root.addView(snooze);

        Button dismiss = bigButton("忽略");
        dismiss.setOnClickListener(v -> finish());
        root.addView(dismiss);
    }

    @Override protected void onResume() {
        super.onResume();
        TopPages.startRemindShown = true; // 衔接守卫避让：开始提醒页在显时不弹「下一个任务」页
    }

    @Override protected void onPause() {
        TopPages.startRemindShown = false;
        super.onPause();
    }

    private Button bigButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(17);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        p.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(p);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
