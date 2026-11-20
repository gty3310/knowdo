package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 致谢的 99% 大窗内容（Guild Issue #6 第二批）：与 ThanksActivity 同画面、
 * 同文案、同动作——「之前有人帮助你关掉消息了」大字、继续上一个任务
 * （暂停槽里的走恢复、其余走 startTimerSmart，与原页逐字相同）、到任务页
 * 选新任务；没有上一个任务时只剩后者并升为主按钮样式。呈现即消费：
 * BystanderMute.consumeThanks 在 onShown 调用（与原页 onCreate 同语义，
 * 排队未出窗时不消费）。本页不设记录入口，与原页一致。
 */
public final class ThanksOverlay implements BigOverlay.Content {
    private static final int BG = Color.parseColor("#1F2F2A");
    private static final int INK = Color.parseColor("#F7F3EC");
    private static final int INK2 = Color.parseColor("#A49C8B");
    private static final int INK3 = Color.parseColor("#7E7769");

    private Context ctx;

    @Override
    public View createView(Context c) {
        ctx = c;
        Strings.init(c);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(34), dp(64), dp(34), dp(38));

        TextView tag = new TextView(c);
        tag.setText(Strings.en() ? "While you were away" : "下次亮屏");
        tag.setTextColor(INK3);
        tag.setTextSize(12);
        tag.setGravity(Gravity.CENTER);
        root.addView(tag);

        TextView big = new TextView(c);
        big.setText(Strings.en() ? "Someone helped you\nturn it off" : "之前有人帮助你关掉消息了");
        big.setTextColor(INK);
        big.setTextSize(30);
        big.setTypeface(Typeface.SERIF, Typeface.BOLD);
        big.setGravity(Gravity.CENTER);
        big.setLineSpacing(0, 1.5f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(110);
        root.addView(big, bp);

        View spacer = new View(c);
        root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        TaskStore store = new TaskStore(c);
        final TaskItem last = lastTask(c, store);
        final boolean lastIsPaused = last != null && TimerState.isPaused(c)
                && last.id.equals(TimerState.pausedTaskId(c));
        Button pickNew = new Button(c);
        pickNew.setText(Strings.en() ? "Pick a new task on the task page" : "到任务页选择新任务");
        pickNew.setAllCaps(false);
        pickNew.setTypeface(Typeface.SERIF);
        if (last != null) {
            Button cont = styleButton("继续「" + last.title + "」", true);
            cont.setOnClickListener(v -> {
                if (lastIsPaused) {
                    PauseEscalation.cancel(ctx);
                    int total = TimerState.pausedTotalSec(ctx);
                    if (total <= 0) total = Math.max(1, last.durationMinutes) * 60;
                    int rem = (int) Math.max(1, TimerState.pausedRemainMs(ctx) / 1000);
                    TimerService.resumeTimer(ctx, total, rem, last.id, last.title);
                } else if (!TimerService.startTimerSmart(ctx, last)) {
                    TimerService.startTimer(ctx,
                            Math.max(1, last.durationMinutes) * 60, last.id, last.title);
                }
                finish();
            });
            root.addView(cont);
            pickNew.setTextSize(16);
            pickNew.setTextColor(INK2);
            pickNew.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, dp(52));
            np.topMargin = dp(14);
            root.addView(pickNew, np);
        } else {
            // 没有上一个任务可续：只剩这一个按钮，升为主按钮样式
            Button only = styleButton(pickNew.getText().toString(), true);
            only.setOnClickListener(v -> goTaskPage());
            root.addView(only);
        }
        pickNew.setOnClickListener(v -> goTaskPage());
        return root;
    }

    @Override
    public void onShown(Context c) {
        BystanderMute.consumeThanks(c); // 呈现即消费：压制与待致谢一并清掉（与原页 onCreate 同）
        TopPages.thanksShown = true; // 与原页 onResume 同登记
    }

    @Override
    public boolean tick(Context c) {
        return true; // 与原页同：无超时，等用户处理
    }

    @Override
    public void onDismissed(Context c) {
        TopPages.thanksShown = false;
    }

    private void finish() { BigOverlay.dismiss(ctx, BigOverlay.KEY_THANKS); }

    private void goTaskPage() {
        Intent i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { ctx.startActivity(i); } catch (Exception ignored) { }
        finish();
    }

    /** 上一个任务：暂停槽里的优先，其次最近计时的（与 ThanksActivity.lastTask 同）。 */
    private TaskItem lastTask(Context c, TaskStore store) {
        if (TimerState.isPaused(c)) {
            TaskItem t = store.byId(TimerState.pausedTaskId(c));
            if (t != null && !t.deleted) return t;
        }
        String lastId = TimerState.taskId(c);
        if (lastId != null && !lastId.isEmpty()) {
            TaskItem t = store.byId(lastId);
            if (t != null && !t.deleted && !t.isDoneToday()) return t;
        }
        return null;
    }

    private Button styleButton(String text, boolean primary) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTypeface(Typeface.SERIF);
        b.setTextSize(18);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        if (primary) {
            g.setColor(Color.TRANSPARENT);
            g.setStroke(dp(1), Color.parseColor("#51635A"));
            b.setTextColor(INK);
        } else {
            g.setColor(Color.TRANSPARENT);
            b.setTextColor(INK2);
        }
        b.setBackground(g);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(60)));
        return b;
    }

    private int dp(int v) { return Math.round(v * ctx.getResources().getDisplayMetrics().density); }
}
