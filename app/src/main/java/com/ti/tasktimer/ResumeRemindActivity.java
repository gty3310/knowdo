package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 暂停提醒页：休息时间到，叫用户回来继续刚才暂停的任务。 */
public class ResumeRemindActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 静音暂停从通知点开本页＝已处理：停掉每 5 分钟的亮屏链并撤通知（普通模式下是 no-op）
        SilentResume.cancel(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(23, 34, 29));
        int pad = dp(28);
        root.setPadding(pad, pad, pad, pad);
        setContentView(root);

        TextView bell = new TextView(this);
        bell.setText("⏰ 休息时间到");
        bell.setTextColor(Color.WHITE);
        bell.setTextSize(24);
        bell.setGravity(Gravity.CENTER);
        root.addView(bell);

        final String title = TimerState.pausedTitle(this);
        long remain = Math.max(0, TimerState.pausedRemainMs(this) / 1000);
        TextView task = new TextView(this);
        task.setText("该继续「" + title + "」了\n还剩 " + (remain / 60) + " 分 " + (remain % 60) + " 秒");
        task.setTextColor(Color.rgb(200, 200, 200));
        task.setTextSize(17);
        task.setGravity(Gravity.CENTER);
        task.setPadding(0, dp(14), 0, dp(22));
        root.addView(task);

        // 立志常驻（v1.12.0）：叫回页也展示「我的志」，未设置不显示
        TextView asp = Aspiration.makeView(this, true);
        if (asp != null) {
            asp.setPadding(0, 0, 0, dp(18));
            root.addView(asp, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        Button go = bigButton("继续任务");
        go.setOnClickListener(v -> {
            PauseEscalation.cancel(this); // 有反馈：撤掉 60 秒渐强闹铃升级
            TaskStore store = new TaskStore(this);
            TaskItem t = store.byId(TimerState.pausedTaskId(this));
            if (t == null) {
                Toast.makeText(this, "这个任务已经不在了", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            int total = TimerState.pausedTotalSec(this);
            if (total <= 0) total = Math.max(1, t.durationMinutes) * 60;
            int rem = (int) Math.max(1, TimerState.pausedRemainMs(this) / 1000);
            TimerService.resumeTimer(this, total, rem, t.id, t.title);
            finish();
        });
        root.addView(go);

        Button more = bigButton("再歇一会");
        more.setOnClickListener(v -> {
            PauseEscalation.cancel(this); // 点开选时长就算反馈，不起闹铃
            showSnooze();
        });
        root.addView(more);

        // v1.10.0：原「先这样，不用再提醒」（无限期搁置）改为「换下一个任务」——
        // 不继续当前任务就直接进衔接守卫页定下一个，不留空档。
        // 暂停槽保留（原任务仍在暂停中，随时可从悬浮窗/主页继续）。
        Button dismiss = bigButton("换下一个任务");
        dismiss.setOnClickListener(v -> {
            PauseEscalation.cancel(this); // 有反馈：撤掉 60 秒渐强闹铃升级
            Intent i = new Intent(this, NextTaskActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception ignored) { }
            finish();
        });
        root.addView(dismiss);
    }

    @Override protected void onResume() {
        super.onResume();
        TopPages.resumeRemindShown = true; // 衔接守卫避让：叫回页在显时不弹「下一个任务」页
    }

    @Override protected void onPause() {
        TopPages.resumeRemindShown = false;
        super.onPause();
    }

    private void showSnooze() {
        String[] items = new String[ResumeReminder.LABELS.length + 1];
        System.arraycopy(ResumeReminder.LABELS, 0, items, 0, ResumeReminder.LABELS.length);
        items[items.length - 1] = "自定义…";
        new AlertDialog.Builder(this)
                .setTitle("再过多长时间提醒？")
                .setItems(items, (d, which) -> {
                    if (which < ResumeReminder.DELAYS_MS.length) {
                        ResumeReminder.schedule(this, TimerState.pausedTaskId(this),
                                ResumeReminder.DELAYS_MS[which]);
                        Toast.makeText(this, "好，" + ResumeReminder.LABELS[which] + "后叫你", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        showCustom();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCustom() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        final EditText hours = new EditText(this);
        hours.setHint("小时");
        hours.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final EditText mins = new EditText(this);
        mins.setHint("分钟");
        mins.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        box.addView(hours);
        TextView colon = new TextView(this);
        colon.setText(" : ");
        box.addView(colon);
        box.addView(mins);
        new AlertDialog.Builder(this)
                .setTitle("自定义提醒时间")
                .setView(box)
                .setPositiveButton("好", (d, w) -> {
                    int h = parseInt(hours.getText().toString());
                    int m = parseInt(mins.getText().toString());
                    long ms = (h * 60L + m) * 60_000L;
                    if (ms < 60_000L) ms = 60_000L;
                    ResumeReminder.schedule(this, TimerState.pausedTaskId(this), ms);
                    Toast.makeText(this, "好，" + (ms / 60000) + " 分钟后叫你", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
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
