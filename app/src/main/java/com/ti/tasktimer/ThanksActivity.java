package com.ti.tasktimer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 致谢页（v1.19.0）：路人帮机主静音后，机主下一次亮屏解锁时优先拉起
 * （优先级：晨间门 ＞ 本页 ＞ 选任务页）。大字一句「之前有人帮助你关掉消息了」，
 * 主按钮直接继续上一个任务，次按钮去任务页选新的；没有上一个任务可续时
 * 只显示次按钮并升为主按钮样式。本页不设「记录刚才做了什么」的入口——
 * 要记回任务页记（用户定案）。呈现即消费路人静音状态。
 */
public class ThanksActivity extends Activity {
    private static final int BG = Color.parseColor("#1F2F2A");
    private static final int INK = Color.parseColor("#F7F3EC");
    private static final int INK2 = Color.parseColor("#A49C8B");
    private static final int INK3 = Color.parseColor("#7E7769");
    private static final int AMBER = Color.parseColor("#D94B3A");

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        BystanderMute.consumeThanks(this); // 呈现即消费：压制与待致谢一并清掉
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        TopPages.thanksShown = true;
    }

    @Override protected void onPause() {
        TopPages.thanksShown = false;
        super.onPause();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(34), dp(64), dp(34), dp(38));

        TextView tag = new TextView(this);
        tag.setText(Strings.en() ? "While you were away" : "下次亮屏");
        tag.setTextColor(INK3);
        tag.setTextSize(12);
        tag.setGravity(Gravity.CENTER);
        root.addView(tag);

        TextView big = new TextView(this);
        big.setText(Strings.en() ? "Someone helped you\nturn it off" : "之前有人帮助你关掉消息了");
        big.setTextColor(INK);
        big.setTextSize(30);
        big.setTypeface(Typeface.SERIF, Typeface.BOLD);
        big.setGravity(Gravity.CENTER);
        big.setLineSpacing(0, 1.5f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(110);
        root.addView(big, bp);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        TaskStore store = new TaskStore(this);
        final TaskItem last = lastTask(store);
        final boolean lastIsPaused = last != null && TimerState.isPaused(this)
                && last.id.equals(TimerState.pausedTaskId(this));
        Button pickNew = new Button(this);
        pickNew.setText(Strings.en() ? "Pick a new task on the task page" : "到任务页选择新任务");
        pickNew.setAllCaps(false);
        pickNew.setTypeface(Typeface.SERIF);
        if (last != null) {
            Button cont = styleButton("继续「" + last.title + "」", true);
            cont.setOnClickListener(v -> {
                if (lastIsPaused) {
                    PauseEscalation.cancel(this);
                    int total = TimerState.pausedTotalSec(this);
                    if (total <= 0) total = Math.max(1, last.durationMinutes) * 60;
                    int rem = (int) Math.max(1, TimerState.pausedRemainMs(this) / 1000);
                    TimerService.resumeTimer(this, total, rem, last.id, last.title);
                } else if (!TimerService.startTimerSmart(this, last)) {
                    // 今天已计过时按剩余接着倒数（v1.21.1）；暂停槽恢复走上面一支、不经这里
                    TimerService.startTimer(this,
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
        setContentView(root);
    }

    private void goTaskPage() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { startActivity(i); } catch (Exception ignored) { }
        finish();
    }

    /** 上一个任务：空档前最近在做/被打断的——暂停槽里的优先，其次最近计时的。 */
    private TaskItem lastTask(TaskStore store) {
        if (TimerState.isPaused(this)) {
            TaskItem t = store.byId(TimerState.pausedTaskId(this));
            if (t != null && !t.deleted) return t;
        }
        String lastId = TimerState.taskId(this);
        if (lastId != null && !lastId.isEmpty()) {
            TaskItem t = store.byId(lastId);
            if (t != null && !t.deleted && !t.isDoneToday()) return t;
        }
        return null;
    }

    private Button styleButton(String text, boolean primary) {
        Button b = new Button(this);
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

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
