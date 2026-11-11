package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * 分神处置盖屏（v1.80.0）：被盖住的软件在前台时全屏盖住它，固定 3 分钟，
 * 倒计时到 0 自动收起。风格同 GuardActivity（米白底、大标题、大按钮），
 * 内容极简：标题＋倒计时＋当前任务＋「回到任务」。屏上不放任何解封
 * 入口——提前解封每天限一次、只能在设置页里解。显示期间压住悬浮窗
 * （照 GuardActivity 的做法，onDestroy 恢复）。
 */
public class BlockActivity extends Activity {
    private static volatile BlockActivity current;

    public static boolean isShowing() { return current != null; }

    /** 外部收起（用户切去别的软件／解封／到点清状态时调用）。 */
    public static void dismissCurrent() {
        BlockActivity a = current;
        if (a != null) a.finish();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView countView;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (refresh()) handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        if (!AppBlock.isActive(this)) { finish(); return; }
        current = this;
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AlertHelper.cancelBlock(this);
        FloatingWindow.setSuppressed(true);

        String appName = AppLister.labelOf(this, AppBlock.activePkg(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 243, 236));
        int pad = dp(28);
        root.setPadding(pad, dp(56), pad, pad);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText(Strings.blockTitle(appName));
        title.setTextSize(28);
        title.setTextColor(Color.rgb(31, 47, 42));
        root.addView(title);

        countView = new TextView(this);
        countView.setTextSize(54);
        countView.setTextColor(Color.rgb(31, 47, 42));
        countView.setPadding(0, dp(28), 0, dp(8));
        root.addView(countView);

        TextView task = new TextView(this);
        task.setText(Strings.blockTaskLine(TimerState.taskTitle(this)));
        task.setTextSize(18);
        task.setTextColor(Color.rgb(31, 47, 42));
        task.setPadding(0, dp(10), 0, dp(36));
        root.addView(task);

        Button back = new Button(this);
        back.setText(Strings.backToTask());
        back.setTextSize(20);
        back.setTextColor(Color.WHITE);
        back.setBackgroundColor(Color.rgb(46, 125, 50));
        back.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));
        back.setOnClickListener(v -> {
            String id = TimerState.taskId(this);
            TaskItem t = (id == null || id.isEmpty()) ? null : new TaskStore(this).byId(id);
            GuardActions.redirectToTask(this, t);
            finish();
        });
        root.addView(back);

        refresh();
        handler.postDelayed(ticker, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (countView != null) refresh();
    }

    /** 刷新倒计时；盖住已结束（到点／被解封）时收起本页并返回 false。 */
    private boolean refresh() {
        long until = AppBlock.until(this);
        long left = until - System.currentTimeMillis();
        if (until == 0) { finish(); return false; }
        if (left <= 0) { AppBlock.clear(this); return false; }
        long sec = (left + 999) / 1000;
        countView.setText(String.format(Locale.US, "%d:%02d", sec / 60, sec % 60));
        return true;
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        handler.removeCallbacks(ticker);
        FloatingWindow.setSuppressed(false);
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
