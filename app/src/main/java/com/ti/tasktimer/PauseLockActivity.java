package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 没有「上层显示」权限时的降级页：与 99% 悬浮遮罩同一组下一步。
 * 这是 Activity，可能被系统手势划掉，所以只作后备；划掉后 PauseLock.due
 * 仍在，FloatService 会再发全屏通知。按返回不退出。
 */
public class PauseLockActivity extends Activity {
    private static PauseLockActivity current;

    static void dismissCurrent() {
        if (current != null) {
            try { current.finish(); } catch (Exception ignored) { }
            current = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        current = this;
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!TimerState.isPaused(this) || TimerState.isRunning(this) || !PauseLock.isDue(this)) {
            finish();
            return;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(28), dp(24), dp(24));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(24, 36, 31));
        card.setBackgroundColor(Color.rgb(24, 36, 31));

        TextView tag = new TextView(this);
        tag.setText("知行 · 当前第一要务");
        tag.setTextColor(Color.rgb(176, 176, 176));
        tag.setTextSize(13);
        card.addView(tag);

        TextView title = new TextView(this);
        title.setText(TimerState.pausedTitle(this));
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setPadding(0, dp(8), 0, dp(12));
        card.addView(title);

        TextView banner = new TextView(this);
        banner.setText(Strings.pauseLockBanner());
        banner.setTextColor(Color.rgb(247, 164, 148));
        banner.setTextSize(15);
        banner.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(Color.argb(50, 217, 75, 58));
        sb.setCornerRadius(dp(12));
        sb.setStroke(dp(1), Color.argb(140, 217, 75, 58));
        banner.setBackground(sb);
        card.addView(banner);

        long remain = TimerState.pausedRemainMs(this);
        TextView time = new TextView(this);
        time.setText(fmt(remain) + "  " + (Strings.en() ? "Paused" : "已暂停"));
        time.setTextColor(Color.WHITE);
        time.setTextSize(28);
        time.setPadding(0, dp(14), 0, dp(8));
        card.addView(time);

        TextView body = new TextView(this);
        body.setText(Strings.pauseLockBody());
        body.setTextColor(Color.rgb(190, 200, 195));
        body.setTextSize(15);
        body.setPadding(0, 0, 0, dp(16));
        card.addView(body);

        card.addView(bigBtn(Strings.pauseLockContinue(), Color.rgb(46, 122, 72), v -> {
            PauseLock.chooseContinue(this);
            finish();
        }));
        card.addView(bigBtn(Strings.pauseLockBreak(), Color.rgb(55, 66, 60), v -> {
            // 暂停卡是 overlay，无上层显示权限时可能加不上；改走 AlertDialog 版 PauseFlow
            PauseFlow.ask(this, TimerState.pausedTitle(this), ch -> {
                TimerState.setPauseMeta(this, ch.reason, ch.emoji, ch.silent);
                ResumeReminder.schedule(this, TimerState.pausedTaskId(this), ch.delayMs);
                SbTimerSync.onLocalTimerChanged(this);
                PauseLock.onPauseStateChanged(this);
                finish();
            });
        }));
        card.addView(bigBtn(Strings.pauseLockDone(), Color.rgb(55, 66, 60), v -> {
            PauseLock.chooseMarkDone(this);
            finish();
        }));
        card.addView(bigBtn(Strings.pauseLockSwitch(), Color.rgb(55, 66, 60), v -> {
            PauseLock.chooseSwitchTask(this);
            finish();
        }));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Color.rgb(24, 36, 31));
        sv.addView(card);
        setContentView(sv);
    }

    @Override public void onBackPressed() {
        // 按返回不退出：必须选下一步。系统手势仍可能把本页划掉，由通知兜底。
    }

    @Override protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    private Button bigBtn(String label, int color, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(click);
        return b;
    }

    private static String fmt(long ms) {
        long sec = Math.max(0, ms / 1000);
        return sec >= 3600
                ? String.format("%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
                : String.format("%d:%02d", sec / 60, sec % 60);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
