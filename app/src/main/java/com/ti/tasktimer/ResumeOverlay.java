package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

/**
 * 暂停叫回的 99% 大窗内容（Guild Issue #6 第二批）：与 ResumeRemindActivity
 * 同画面、同文案、同动作——「休息时间到」＋该继续的任务与剩余时间＋「我的志」
 * ＋继续任务／再歇一会／换下一个任务三按钮。每个按钮都先
 * PauseEscalation.cancel（有反馈撤 60 秒渐强升级），与原页逐字相同；
 * 「换下一个任务」保留 PR #7 的直达行页＋跳后 PauseLock.onPauseStateChanged
 * 重新武装 30 秒盖屏，一行不删。
 *
 * 与原页的差异（路径适配，如实列明）：「再歇一会」在原页是 AlertDialog
 * 列表（＋自定义的第二个对话框），对话框需要 Activity 宿主，大窗里改为
 * 页内阶段（选项整页竖排、自定义是页内两个输入框），选项、时长、
 * ResumeReminder.schedule 调用与 toast 文案全不变；对话框的「取消」在
 * 页内阶段就是回到主阶段（等价原页关对话框回到页面）。自定义输入框
 * 在深色底上改用浅色字（原对话框是浅色底），仅配色适配。
 */
public final class ResumeOverlay implements BigOverlay.Content {
    private static final int STAGE_MAIN = 0;
    private static final int STAGE_SNOOZE = 1;
    private static final int STAGE_CUSTOM = 2;

    private Context ctx;
    private LinearLayout container;
    private int stage = STAGE_MAIN;

    @Override
    public View createView(Context c) {
        ctx = c;
        Strings.init(c);
        container = new LinearLayout(c);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setBackgroundColor(Color.rgb(23, 34, 29));
        int pad = dp(28);
        container.setPadding(pad, pad, pad, pad);
        buildStage();
        return container;
    }

    @Override
    public void onShown(Context c) {
        TopPages.resumeRemindShown = true; // 与原页 onResume 同登记
    }

    @Override
    public boolean tick(Context c) {
        // 暂停槽已清/已在别处继续：本页没有存在的意义了
        return TimerState.isPaused(c) && !TimerState.isRunning(c);
    }

    @Override
    public void onDismissed(Context c) {
        TopPages.resumeRemindShown = false;
        FloatingWindow.endOverlayInputFocus(c);
    }

    private void finish() { BigOverlay.dismiss(ctx, BigOverlay.KEY_RESUME); }

    private void buildStage() {
        container.removeAllViews();
        if (stage == STAGE_SNOOZE) { buildSnooze(); return; }
        if (stage == STAGE_CUSTOM) { buildCustom(); return; }
        buildMain();
    }

    // ---------------- 主阶段（与 ResumeRemindActivity.onCreate 逐项对应） ----------------

    private void buildMain() {
        TextView bell = new TextView(ctx);
        bell.setText("⏰ 休息时间到");
        bell.setTextColor(Color.WHITE);
        bell.setTextSize(24);
        bell.setGravity(Gravity.CENTER);
        container.addView(bell);

        final String title = TimerState.pausedTitle(ctx);
        long remain = Math.max(0, TimerState.pausedRemainMs(ctx) / 1000);
        TextView task = new TextView(ctx);
        task.setText("该继续「" + title + "」了\n还剩 " + (remain / 60) + " 分 " + (remain % 60) + " 秒");
        task.setTextColor(Color.rgb(200, 200, 200));
        task.setTextSize(17);
        task.setGravity(Gravity.CENTER);
        task.setPadding(0, dp(14), 0, dp(22));
        container.addView(task);

        TextView asp = Aspiration.makeView(ctx, true);
        if (asp != null) {
            asp.setPadding(0, 0, 0, dp(18));
            container.addView(asp, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        Button go = bigButton("继续任务");
        go.setOnClickListener(v -> {
            PauseEscalation.cancel(ctx); // 有反馈：撤掉 60 秒渐强闹铃升级
            TaskStore store = new TaskStore(ctx);
            TaskItem t = store.byId(TimerState.pausedTaskId(ctx));
            if (t == null) {
                Toast.makeText(ctx, "这个任务已经不在了", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            int total = TimerState.pausedTotalSec(ctx);
            if (total <= 0) total = Math.max(1, t.durationMinutes) * 60;
            int rem = (int) Math.max(1, TimerState.pausedRemainMs(ctx) / 1000);
            TimerService.resumeTimer(ctx, total, rem, t.id, t.title);
            finish();
        });
        container.addView(go);

        Button more = bigButton("再歇一会");
        more.setOnClickListener(v -> {
            PauseEscalation.cancel(ctx); // 点开选时长就算反馈，不起闹铃
            stage = STAGE_SNOOZE;
            buildStage();
        });
        container.addView(more);

        Button dismiss = bigButton("换下一个任务");
        dismiss.setOnClickListener(v -> {
            PauseEscalation.cancel(ctx); // 有反馈：撤掉 60 秒渐强闹铃升级
            try { MainActivity.openXing(ctx); } catch (Exception ignored) { }
            // 暂停槽还在、叫回已清：重新估价，30 秒没有开始下一件就盖屏
            PauseLock.onPauseStateChanged(ctx);
            finish();
        });
        container.addView(dismiss);
    }

    // ---------------- 再歇一会（原页 AlertDialog 列表的页内阶段） ----------------

    private void buildSnooze() {
        TextView title = new TextView(ctx);
        title.setText("再过多长时间提醒？");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(16));
        container.addView(title);
        for (int i = 0; i < ResumeReminder.LABELS.length; i++) {
            final int which = i;
            Button b = bigButton(ResumeReminder.LABELS[i]);
            b.setOnClickListener(v -> {
                ResumeReminder.schedule(ctx, TimerState.pausedTaskId(ctx),
                        ResumeReminder.DELAYS_MS[which]);
                Toast.makeText(ctx, "好，" + ResumeReminder.LABELS[which] + "后叫你",
                        Toast.LENGTH_SHORT).show();
                finish();
            });
            container.addView(b);
        }
        Button custom = bigButton("自定义…");
        custom.setOnClickListener(v -> {
            stage = STAGE_CUSTOM;
            buildStage();
        });
        container.addView(custom);
        Button cancel = bigButton("取消");
        cancel.setOnClickListener(v -> {
            stage = STAGE_MAIN;
            buildStage();
        });
        container.addView(cancel);
    }

    // ---------------- 自定义时长（原页第二个对话框的页内阶段） ----------------

    private void buildCustom() {
        TextView title = new TextView(ctx);
        title.setText("自定义提醒时间");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(16));
        container.addView(title);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        final EditText hours = new EditText(ctx);
        hours.setHint("小时");
        hours.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        styleInput(hours);
        final EditText mins = new EditText(ctx);
        mins.setHint("分钟");
        mins.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        styleInput(mins);
        box.addView(hours);
        TextView colon = new TextView(ctx);
        colon.setText(" : ");
        colon.setTextColor(Color.WHITE);
        box.addView(colon);
        box.addView(mins);
        container.addView(box);

        Button ok = bigButton("好");
        ok.setOnClickListener(v -> {
            int h = parseInt(hours.getText().toString());
            int m = parseInt(mins.getText().toString());
            long ms = (h * 60L + m) * 60_000L;
            if (ms < 60_000L) ms = 60_000L;
            ResumeReminder.schedule(ctx, TimerState.pausedTaskId(ctx), ms);
            Toast.makeText(ctx, "好，" + (ms / 60000) + " 分钟后叫你", Toast.LENGTH_SHORT).show();
            finish();
        });
        container.addView(ok);
        Button cancel = bigButton("取消");
        cancel.setOnClickListener(v -> {
            stage = STAGE_MAIN;
            buildStage();
        });
        container.addView(cancel);
    }

    private void styleInput(final EditText e) {
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(150, 150, 150));
        e.setGravity(Gravity.CENTER);
        e.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                FloatingWindow.focusOverlayInput(ctx, e);
            }
            return false;
        });
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

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
