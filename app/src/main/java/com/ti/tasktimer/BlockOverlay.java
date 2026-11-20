package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * 盖屏的 99% 大窗内容（Guild Issue #6 第一批）：与 BlockActivity 同内容、
 * 同文案、同动作——「{软件名} 先歇一会儿」＋mm:ss 大倒计时＋当前任务＋
 * 「回到任务」大按钮，另有 PR #8 加的「这是误判」申诉钮（原样保留，不删
 * 出口）。倒计时与封禁状态仍由 AppBlock 管，本类只渲染：壳每秒调 tick
 * 刷新倒计时，到点（或状态被清）自动收回。屏上不放解封入口——提前解封
 * 仍只在设置页、每天限 1 次，与原页一致。
 *
 * 与原页的唯一路径差异：申诉钮在原页是页内展开 OverturnFlow 输入框
 * （需要 Activity 宿主），大窗里点它改为收回大窗、转回 BlockActivity
 * 原页完成申诉流程，功能与出口都不变。
 */
public final class BlockOverlay implements BigOverlay.Content {
    private TextView countView;

    @Override
    public View createView(final Context c) {
        Strings.init(c);
        String appName = AppLister.labelOf(c, AppBlock.activePkg(c));

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 243, 236));
        int pad = dp(c, 28);
        root.setPadding(pad, dp(c, 56), pad, pad);

        TextView title = new TextView(c);
        title.setText(Strings.blockTitle(appName));
        title.setTextSize(28);
        title.setTextColor(Color.rgb(31, 47, 42));
        root.addView(title);

        countView = new TextView(c);
        countView.setTextSize(54);
        countView.setTextColor(Color.rgb(31, 47, 42));
        countView.setPadding(0, dp(c, 28), 0, dp(c, 8));
        root.addView(countView);

        TextView task = new TextView(c);
        task.setText(Strings.blockTaskLine(TimerState.taskTitle(c)));
        task.setTextSize(18);
        task.setTextColor(Color.rgb(31, 47, 42));
        task.setPadding(0, dp(c, 10), 0, dp(c, 36));
        root.addView(task);

        Button back = new Button(c);
        back.setText(Strings.backToTask());
        back.setTextSize(20);
        back.setTextColor(Color.WHITE);
        back.setBackgroundColor(Color.rgb(46, 125, 50));
        back.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 70)));
        back.setOnClickListener(v -> {
            String id = TimerState.taskId(c);
            TaskItem t = (id == null || id.isEmpty()) ? null : new TaskStore(c).byId(id);
            GuardActions.redirectToTask(c, t);
            BigOverlay.dismissQuietly(c, BigOverlay.KEY_BLOCK);
        });
        root.addView(back);

        Button overturn = new Button(c);
        overturn.setText(Strings.overturnThis());
        overturn.setTextSize(18);
        overturn.setTextColor(Color.WHITE);
        overturn.setBackgroundColor(Color.rgb(117, 117, 117));
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 56));
        op.topMargin = dp(c, 16);
        overturn.setLayoutParams(op);
        overturn.setOnClickListener(v -> {
            // 申诉流程需要 Activity 宿主（输入框/AI 复核）：收回大窗转原页
            BigOverlay.dismissQuietly(c, BigOverlay.KEY_BLOCK);
            try {
                Intent i = new Intent(c, BlockActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Exception ignored) { }
        });
        root.addView(overturn);

        tick(c);
        return root;
    }

    @Override
    public void onShown(Context c) { }

    /** 刷新倒计时；盖住已结束（到点／被解封／状态已清）返回 false 让壳收回。与 BlockActivity.refresh 同口径。 */
    @Override
    public boolean tick(Context c) {
        long until = AppBlock.until(c);
        if (until == 0) return false;
        long left = until - System.currentTimeMillis();
        if (left <= 0) { AppBlock.clear(c); return false; }
        long sec = (left + 999) / 1000;
        if (countView != null) {
            countView.setText(String.format(Locale.US, "%d:%02d", sec / 60, sec % 60));
        }
        return true;
    }

    @Override
    public void onDismissed(Context c) { }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
