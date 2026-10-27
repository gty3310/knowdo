package com.ti.tasktimer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 空档催促全屏页（v1.19.0，按 UI 稿 mockup-idle-alarm-v3 画面 1–4）：
 * 一轮 staging 只有这一页——先震动不出声，无反应才出声渐强；页面自始至终不变，
 * 只多一条阶段小字。机主点任何「开始任务」整个循环结束；页面最下方的大按钮是
 * 给路人的：按下立即静音到下一次亮屏，机主下次解锁先看致谢页。
 * 样式按定稿清净风：深暖炭底、衬线大字、克制琥珀、文案不用句号。
 */
public class IdleAlarmActivity extends Activity {
    private static final int BG = Color.parseColor("#1F2F2A");
    private static final int INK = Color.parseColor("#F7F3EC");
    private static final int INK2 = Color.parseColor("#A49C8B");
    private static final int INK3 = Color.parseColor("#7E7769");
    private static final int LINE = Color.parseColor("#3A4A41");
    private static final int AMBER = Color.parseColor("#D94B3A");
    private static final int PASSER_BG = Color.parseColor("#2C4036");

    private TaskStore store;
    private TextView stageTag;
    private TextView soundCap;
    private boolean quietShown;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable stageTick = new Runnable() {
        @Override public void run() {
            refreshStage();
            uiHandler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AlertHelper.cancelNextTask(this);
        store = new TaskStore(this);
        buildMain();
        uiHandler.post(stageTick);
    }

    @Override protected void onResume() {
        super.onResume();
        TopPages.idleAlarmShown = true;
    }

    @Override protected void onPause() {
        TopPages.idleAlarmShown = false;
        super.onPause();
    }

    @Override protected void onDestroy() {
        uiHandler.removeCallbacks(stageTick);
        super.onDestroy();
    }

    /** 按返回离开：本轮震/响已因交互停掉；巡检弹页静默 3 分钟（alarm 轮次排期不动）。 */
    @Override public void onBackPressed() {
        IdleGuard.snoozeOnDismiss(this);
        super.onBackPressed();
    }

    /** 机主有任何操作：立刻停掉本轮震/响（页面保留），下轮间隔从停声时刻起算。路人按钮走自己的路径（已先停声并落盘静音），不重复处理。 */
    @Override public void onUserInteraction() {
        super.onUserInteraction();
        if (quietShown) return;
        if (IdleStage.isActive() || AlarmRing.isRinging()) {
            IdleStage.stop(this);
            IdleAlarm.onSoundStoppedEarly(this);
        }
    }

    // ---------------- 界面 ----------------

    private void buildMain() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(34), dp(64), dp(34), dp(30));

        stageTag = new TextView(this);
        stageTag.setTextColor(INK3);
        stageTag.setTextSize(12);
        stageTag.setGravity(Gravity.CENTER);
        root.addView(stageTag);

        TextView big = new TextView(this);
        big.setText(Strings.en() ? "No task is timing" : "没有任务在计时");
        big.setTextColor(INK);
        big.setTextSize(32);
        big.setTypeface(Typeface.SERIF, Typeface.BOLD);
        big.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(90);
        bp.bottomMargin = dp(10);
        root.addView(big, bp);

        TextView idleLine = new TextView(this);
        long anchor = IdleAlarm.anchorMs(this);
        if (anchor > 0) {
            int mins = (int) Math.max(0, (System.currentTimeMillis() - anchor) / 60_000L);
            idleLine.setText(TimerState.pauseExpired(this)
                    ? (Strings.en() ? "Paused for " + mins + " min" : "暂停 " + mins + " 分钟")
                    : (Strings.en() ? "Idle for " + mins + " min" : "空档 " + mins + " 分钟"));
        }
        idleLine.setTextColor(INK2);
        idleLine.setTextSize(13);
        idleLine.setGravity(Gravity.CENTER);
        root.addView(idleLine);

        soundCap = new TextView(this);
        soundCap.setText(Strings.en() ? "Sound fading in" : "声音渐强");
        soundCap.setTextColor(INK2);
        soundCap.setTextSize(12);
        soundCap.setGravity(Gravity.CENTER);
        soundCap.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(-1, -2);
        scp.topMargin = dp(26);
        root.addView(soundCap, scp);

        // 机主操作区
        LinearLayout owner = new LinearLayout(this);
        owner.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ow = new LinearLayout.LayoutParams(-1, -2);
        ow.topMargin = dp(56);
        root.addView(owner, ow);

        // 继续上一个任务（暂停中的任务优先，其次最近计时的任务）
        final TaskItem contTask = continueTask();
        final boolean contIsPaused = TimerState.isPaused(this) && contTask != null
                && contTask.id.equals(TimerState.pausedTaskId(this));
        if (contTask != null) {
            Button cont = outlineButton(contIsPaused
                    ? (Strings.en() ? "Resume " : "继续") + contTask.title
                    : (Strings.en() ? "Continue " : "继续") + contTask.title);
            if (contIsPaused) {
                long rem = Math.max(0, TimerState.pausedRemainMs(this) / 1000);
                cont.setText(cont.getText() + "  " + (rem / 60) + ":"
                        + String.format(java.util.Locale.US, "%02d", rem % 60));
            }
            cont.setOnClickListener(v -> {
                if (contIsPaused) resumePaused(contTask);
                else startTiming(contTask);
            });
            owner.addView(cont);
        }

        // 选其他任务（展开才列今日未完成里最要紧的 3 个）
        List<TaskItem> others = otherTasks(contTask);
        if (!others.isEmpty()) {
            final LinearLayout pickList = new LinearLayout(this);
            pickList.setOrientation(LinearLayout.VERTICAL);
            pickList.setVisibility(View.GONE);
            TextView pickRow = new TextView(this);
            pickRow.setText(Strings.en() ? "Pick another task  ›" : "选其他任务  ›");
            pickRow.setTextColor(INK2);
            pickRow.setTextSize(15);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
            pp.topMargin = dp(22);
            pp.bottomMargin = dp(4);
            owner.addView(pickRow, pp);
            pickRow.setOnClickListener(v -> {
                boolean open = pickList.getVisibility() != View.VISIBLE;
                pickList.setVisibility(open ? View.VISIBLE : View.GONE);
                pickRow.setText(open
                        ? (Strings.en() ? "Pick another task  ⌄" : "选其他任务  ⌄")
                        : (Strings.en() ? "Pick another task  ›" : "选其他任务  ›"));
            });
            for (final TaskItem t : others) {
                pickList.addView(taskRow(t));
            }
            owner.addView(pickList);
        }

        TextView newRow = new TextView(this);
        newRow.setText(Strings.en() ? "＋  New task" : "＋  新建任务");
        newRow.setTextColor(INK2);
        newRow.setTextSize(15);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(20);
        owner.addView(newRow, np);
        newRow.setOnClickListener(v -> {
            Intent i = new Intent(this, NextTaskActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception ignored) { }
            finish();
        });

        // 路人区（页面最下方，大号按钮，文案为用户定稿原文、无句号）
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView passerNote = new TextView(this);
        passerNote.setText(Strings.en() ? "If the owner of this phone is not around"
                : "如果这台手机的主人不在旁边");
        passerNote.setTextColor(INK3);
        passerNote.setTextSize(11);
        passerNote.setGravity(Gravity.CENTER);
        root.addView(passerNote);
        Button passer = new Button(this);
        passer.setText("如果您不是手机主人 请点击这里静音");
        passer.setTextColor(Color.parseColor("#DDD6C6"));
        passer.setTextSize(18);
        passer.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(PASSER_BG);
        g.setStroke(dp(1), Color.parseColor("#43564B"));
        g.setCornerRadius(dp(18));
        passer.setBackground(g);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(-1, dp(84));
        pb.topMargin = dp(12);
        root.addView(passer, pb);
        passer.setOnClickListener(v -> onBystander());

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);
        refreshStage();
    }

    /** 画面 4：路人按下之后——只留一句道谢，随即退出。 */
    private void buildQuiet() {
        quietShown = true;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(BG);
        root.setPadding(dp(34), dp(64), dp(34), dp(30));
        TextView thanks = new TextView(this);
        thanks.setText(Strings.en() ? "Thank you" : "谢谢你");
        thanks.setTextColor(INK3);
        thanks.setTextSize(34);
        thanks.setTypeface(Typeface.SERIF);
        thanks.setGravity(Gravity.CENTER);
        root.addView(thanks);
        TextView sub = new TextView(this);
        sub.setText(Strings.en() ? "Muted\nThe owner will be told when the screen lights again"
                : "已静音\n下一次亮屏时提醒机主");
        sub.setTextColor(INK2);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(24);
        root.addView(sub, lp);
        setContentView(root);
    }

    private void refreshStage() {
        if (stageTag == null || quietShown) return;
        int r = Math.max(1, IdleAlarm.roundCount(this));
        boolean ringing = AlarmRing.isRinging();
        boolean vib = IdleStage.isVibrating();
        stageTag.setText((Strings.en() ? "Round " + r : "第 " + r + " 次")
                + (ringing ? (Strings.en() ? " · sounding" : " · 出声中")
                : vib ? (Strings.en() ? " · vibrating" : " · 震动中") : ""));
        if (soundCap != null) soundCap.setVisibility(ringing ? View.VISIBLE : View.INVISIBLE);
    }

    // ---------------- 动作 ----------------

    private void onBystander() {
        IdleStage.stop(this); // 立即停震停声
        BystanderMute.press(this); // 落盘：静音至下一次亮屏＋下次解锁先出致谢页
        buildQuiet();
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
    }

    /** 「继续」指向的任务：暂停槽里的任务优先，否则最近在计时的那个任务。 */
    private TaskItem continueTask() {
        store.load();
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

    /** 今日未完成（必做在前，沿用列表排序），去掉「继续」那个，最多 3 个。 */
    private List<TaskItem> otherTasks(TaskItem exclude) {
        List<TaskItem> out = new ArrayList<>();
        for (TaskItem t : store.visibleSorted()) {
            if (t.isDoneToday()) continue;
            if (exclude != null && t.id.equals(exclude.id)) continue;
            out.add(t);
            if (out.size() >= 3) break;
        }
        return out;
    }

    private View taskRow(final TaskItem t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(2);
        row.setLayoutParams(lp);
        row.setPadding(dp(2), dp(13), dp(2), dp(13));
        TextView name = new TextView(this);
        name.setText(t.title);
        name.setTextColor(INK);
        name.setTextSize(16);
        name.setMaxLines(1);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView meta = new TextView(this);
        boolean must = "must".equals(t.priority);
        meta.setText(Strings.fmtDur(t.durationMinutes) + "  "
                + (Strings.en() ? "Start" : "开始"));
        meta.setTextColor(must ? AMBER : INK3);
        meta.setTextSize(13);
        row.addView(meta);
        View line = new View(this);
        line.setBackgroundColor(LINE);
        LinearLayout parent = new LinearLayout(this);
        parent.setOrientation(LinearLayout.VERTICAL);
        parent.addView(row);
        parent.addView(line, new LinearLayout.LayoutParams(-1, 1));
        row.setOnClickListener(v -> startTiming(t));
        return parent;
    }

    private Button outlineButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(INK);
        b.setTextSize(18);
        b.setTypeface(Typeface.SERIF);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(1), Color.parseColor("#51635A"));
        g.setCornerRadius(dp(16));
        b.setBackground(g);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(60)));
        return b;
    }

    private void resumePaused(TaskItem t) {
        PauseEscalation.cancel(this);
        int total = TimerState.pausedTotalSec(this);
        if (total <= 0) total = Math.max(1, t.durationMinutes) * 60;
        int rem = (int) Math.max(1, TimerState.pausedRemainMs(this) / 1000);
        TimerService.resumeTimer(this, total, rem, t.id, t.title);
        finish();
    }

    /** 开始计时（v1.26.0：截图只走无障碍通道，不再请求录屏授权，直接开始）。 */
    private void startTiming(TaskItem t) {
        doStart(t);
    }

    private void doStart(TaskItem t) {
        // 今天已计过时按剩余接着倒数（v1.21.1，抵扣收口在 TimerService.startTimerSmart）
        if (TimerService.startTimerSmart(this, t)) {
            finish();
            return;
        }
        Toast.makeText(this, (Strings.en() ? "Started: " : "开始计时：") + t.title
                + "（" + t.durationMinutes + (Strings.en() ? " min)" : " 分钟）"),
                Toast.LENGTH_SHORT).show();
        TimerService.startTimer(this, Math.max(1, t.durationMinutes) * 60, t.id, t.title);
        finish();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
