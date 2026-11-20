package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 空档催促的 99% 大窗内容（Guild Issue #6 第一批）：与 IdleAlarmActivity
 * 同画面、同文案、同动作——舞台小字（第 N 次·震动中/出声中）、「没有任务
 * 在计时」、空档时长、继续上一个任务、选其他任务（展开 3 个）、新建任务、
 * 页面最下方的路人静音大按钮与按下后的致谢画面。震动/响铃 staging 仍由
 * IdleStage/IdleAlarm 管，本类只渲染与接按钮；壳每秒调 tick 刷新舞台小字。
 * 原页的 onUserInteraction（有操作就停本轮震/响）以内容区触摸监听＋
 * 展开列表时的显式调用等价实现；原页的按返回静默（IdleGuard
 * .snoozeOnDismiss）是大窗「划不走/关不掉」口径下不存在的动作，不搬。
 */
public final class IdleOverlay implements BigOverlay.Content {
    private static final int BG = Color.parseColor("#1F2F2A");
    private static final int INK = Color.parseColor("#F7F3EC");
    private static final int INK2 = Color.parseColor("#A49C8B");
    private static final int INK3 = Color.parseColor("#7E7769");
    private static final int LINE = Color.parseColor("#3A4A41");
    private static final int AMBER = Color.parseColor("#D94B3A");
    private static final int PASSER_BG = Color.parseColor("#2C4036");

    private TaskStore store;
    private LinearLayout container;
    private TextView stageTag;
    private TextView soundCap;
    private boolean quietShown;

    @Override
    public View createView(final Context c) {
        Strings.init(c);
        store = new TaskStore(c);
        quietShown = false;
        container = new LinearLayout(c);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(BG);
        container.setPadding(dp(c, 34), dp(c, 64), dp(c, 34), dp(c, 30));
        // 等价原页 onUserInteraction：点到内容区（按钮各自的动作另有处理）先停本轮震/响
        container.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) stopEarly(c);
            return false;
        });
        buildMain(c);
        ScrollView sv = new ScrollView(c);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(container);
        return sv;
    }

    @Override
    public void onShown(Context c) {
        TopPages.idleAlarmShown = true; // 与原页 onResume 同登记，防其他全屏页叠拉
    }

    @Override
    public boolean tick(Context c) {
        if (TimerState.isRunning(c)) return false; // 任务已从别处开始：本页使命结束
        refreshStage(c);
        return true;
    }

    @Override
    public void onDismissed(Context c) {
        TopPages.idleAlarmShown = false;
    }

    // ---------------- 界面（与 IdleAlarmActivity.buildMain 逐项对应） ----------------

    private void buildMain(final Context c) {
        stageTag = new TextView(c);
        stageTag.setTextColor(INK3);
        stageTag.setTextSize(12);
        stageTag.setGravity(Gravity.CENTER);
        container.addView(stageTag);

        TextView big = new TextView(c);
        big.setText(Strings.en() ? "No task is timing" : "没有任务在计时");
        big.setTextColor(INK);
        big.setTextSize(32);
        big.setTypeface(Typeface.SERIF, Typeface.BOLD);
        big.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(c, 90);
        bp.bottomMargin = dp(c, 10);
        container.addView(big, bp);

        TextView idleLine = new TextView(c);
        long anchor = IdleAlarm.anchorMs(c);
        if (anchor > 0) {
            int mins = (int) Math.max(0, (System.currentTimeMillis() - anchor) / 60_000L);
            idleLine.setText(TimerState.pauseExpired(c)
                    ? (Strings.en() ? "Paused for " + mins + " min" : "暂停 " + mins + " 分钟")
                    : (Strings.en() ? "Idle for " + mins + " min" : "空档 " + mins + " 分钟"));
        }
        idleLine.setTextColor(INK2);
        idleLine.setTextSize(13);
        idleLine.setGravity(Gravity.CENTER);
        container.addView(idleLine);

        soundCap = new TextView(c);
        soundCap.setText(Strings.en() ? "Sound fading in" : "声音渐强");
        soundCap.setTextColor(INK2);
        soundCap.setTextSize(12);
        soundCap.setGravity(Gravity.CENTER);
        soundCap.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(-1, -2);
        scp.topMargin = dp(c, 26);
        container.addView(soundCap, scp);

        LinearLayout owner = new LinearLayout(c);
        owner.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ow = new LinearLayout.LayoutParams(-1, -2);
        ow.topMargin = dp(c, 56);
        container.addView(owner, ow);

        final TaskItem contTask = continueTask(c);
        final boolean contIsPaused = TimerState.isPaused(c) && contTask != null
                && contTask.id.equals(TimerState.pausedTaskId(c));
        if (contTask != null) {
            Button cont = outlineButton(c, contIsPaused
                    ? (Strings.en() ? "Resume " : "继续") + contTask.title
                    : (Strings.en() ? "Continue " : "继续") + contTask.title);
            if (contIsPaused) {
                long rem = Math.max(0, TimerState.pausedRemainMs(c) / 1000);
                cont.setText(cont.getText() + "  " + (rem / 60) + ":"
                        + String.format(java.util.Locale.US, "%02d", rem % 60));
            }
            cont.setOnClickListener(v -> {
                if (contIsPaused) resumePaused(c, contTask);
                else startTiming(c, contTask);
            });
            owner.addView(cont);
        }

        List<TaskItem> others = otherTasks(contTask);
        if (!others.isEmpty()) {
            final LinearLayout pickList = new LinearLayout(c);
            pickList.setOrientation(LinearLayout.VERTICAL);
            pickList.setVisibility(View.GONE);
            TextView pickRow = new TextView(c);
            pickRow.setText(Strings.en() ? "Pick another task  ›" : "选其他任务  ›");
            pickRow.setTextColor(INK2);
            pickRow.setTextSize(15);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
            pp.topMargin = dp(c, 22);
            pp.bottomMargin = dp(c, 4);
            owner.addView(pickRow, pp);
            pickRow.setOnClickListener(v -> {
                stopEarly(c); // 与原页 onUserInteraction 同口径
                boolean open = pickList.getVisibility() != View.VISIBLE;
                pickList.setVisibility(open ? View.VISIBLE : View.GONE);
                pickRow.setText(open
                        ? (Strings.en() ? "Pick another task  ⌄" : "选其他任务  ⌄")
                        : (Strings.en() ? "Pick another task  ›" : "选其他任务  ›"));
            });
            for (final TaskItem t : others) {
                pickList.addView(taskRow(c, t));
            }
            owner.addView(pickList);
        }

        TextView newRow = new TextView(c);
        newRow.setText(Strings.en() ? "＋  New task" : "＋  新建任务");
        newRow.setTextColor(INK2);
        newRow.setTextSize(15);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(c, 20);
        owner.addView(newRow, np);
        newRow.setOnClickListener(v -> {
            try { MainActivity.openXing(c); } catch (Exception ignored) { }
            BigOverlay.dismiss(c, BigOverlay.KEY_IDLE);
        });

        View spacer = new View(c);
        container.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView passerNote = new TextView(c);
        passerNote.setText(Strings.en() ? "If the owner of this phone is not around"
                : "如果这台手机的主人不在旁边");
        passerNote.setTextColor(INK3);
        passerNote.setTextSize(11);
        passerNote.setGravity(Gravity.CENTER);
        container.addView(passerNote);
        Button passer = new Button(c);
        passer.setText("如果您不是手机主人 请点击这里静音");
        passer.setTextColor(Color.parseColor("#DDD6C6"));
        passer.setTextSize(18);
        passer.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(PASSER_BG);
        g.setStroke(dp(c, 1), Color.parseColor("#43564B"));
        g.setCornerRadius(dp(c, 18));
        passer.setBackground(g);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(-1, dp(c, 84));
        pb.topMargin = dp(c, 12);
        container.addView(passer, pb);
        passer.setOnClickListener(v -> onBystander(c));

        refreshStage(c);
    }

    /** 路人按下之后：只留致谢画面，随即收回（与原页 buildQuiet＋1.5 秒 finish 对应）。 */
    private void buildQuiet(final Context c) {
        quietShown = true;
        container.removeAllViews();
        container.setGravity(Gravity.CENTER);
        TextView thanks = new TextView(c);
        thanks.setText(Strings.en() ? "Thank you" : "谢谢你");
        thanks.setTextColor(INK3);
        thanks.setTextSize(34);
        thanks.setTypeface(Typeface.SERIF);
        thanks.setGravity(Gravity.CENTER);
        container.addView(thanks);
        TextView sub = new TextView(c);
        sub.setText(Strings.en() ? "Muted\nThe owner will be told when the screen lights again"
                : "已静音\n下一次亮屏时提醒机主");
        sub.setTextColor(INK2);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 24);
        container.addView(sub, lp);
    }

    private void refreshStage(Context c) {
        if (stageTag == null || quietShown) return;
        int r = Math.max(1, IdleAlarm.roundCount(c));
        boolean ringing = AlarmRing.isRinging();
        boolean vib = IdleStage.isVibrating();
        stageTag.setText((Strings.en() ? "Round " + r : "第 " + r + " 次")
                + (ringing ? (Strings.en() ? " · sounding" : " · 出声中")
                : vib ? (Strings.en() ? " · vibrating" : " · 震动中") : ""));
        if (soundCap != null) soundCap.setVisibility(ringing ? View.VISIBLE : View.INVISIBLE);
    }

    // ---------------- 动作（与 IdleAlarmActivity 对应） ----------------

    /** 机主有操作：立刻停掉本轮震/响，下轮间隔从停声时刻起算（原页 onUserInteraction）。 */
    private void stopEarly(Context c) {
        if (quietShown) return;
        if (IdleStage.isActive() || AlarmRing.isRinging()) {
            IdleStage.stop(c);
            IdleAlarm.onSoundStoppedEarly(c);
        }
    }

    private void onBystander(final Context c) {
        IdleStage.stop(c); // 立即停震停声
        BystanderMute.press(c); // 落盘：静音至下一次亮屏＋下次解锁先出致谢页
        buildQuiet(c);
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> BigOverlay.dismissIdle(c), 1500);
    }

    private TaskItem continueTask(Context c) {
        store.load();
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

    private View taskRow(final Context c, final TaskItem t) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 2);
        row.setLayoutParams(lp);
        row.setPadding(dp(c, 2), dp(c, 13), dp(c, 2), dp(c, 13));
        TextView name = new TextView(c);
        name.setText(t.title);
        name.setTextColor(INK);
        name.setTextSize(16);
        name.setMaxLines(1);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView meta = new TextView(c);
        boolean must = "must".equals(t.priority);
        meta.setText(Strings.fmtDur(t.durationMinutes) + "  "
                + (Strings.en() ? "Start" : "开始"));
        meta.setTextColor(must ? AMBER : INK3);
        meta.setTextSize(13);
        row.addView(meta);
        View line = new View(c);
        line.setBackgroundColor(LINE);
        LinearLayout parent = new LinearLayout(c);
        parent.setOrientation(LinearLayout.VERTICAL);
        parent.addView(row);
        parent.addView(line, new LinearLayout.LayoutParams(-1, 1));
        row.setOnClickListener(v -> startTiming(c, t));
        return parent;
    }

    private Button outlineButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(INK);
        b.setTextSize(18);
        b.setTypeface(Typeface.SERIF);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(c, 1), Color.parseColor("#51635A"));
        g.setCornerRadius(dp(c, 16));
        b.setBackground(g);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(c, 60)));
        return b;
    }

    private void resumePaused(Context c, TaskItem t) {
        PauseEscalation.cancel(c);
        int total = TimerState.pausedTotalSec(c);
        if (total <= 0) total = Math.max(1, t.durationMinutes) * 60;
        int rem = (int) Math.max(1, TimerState.pausedRemainMs(c) / 1000);
        TimerService.resumeTimer(c, total, rem, t.id, t.title);
        BigOverlay.dismiss(c, BigOverlay.KEY_IDLE);
    }

    private void startTiming(Context c, TaskItem t) {
        // 今天已计过时按剩余接着倒数（v1.21.1，抵扣收口在 TimerService.startTimerSmart）
        if (TimerService.startTimerSmart(c, t)) {
            BigOverlay.dismiss(c, BigOverlay.KEY_IDLE);
            return;
        }
        Toast.makeText(c, (Strings.en() ? "Started: " : "开始计时：") + t.title
                + "（" + t.durationMinutes + (Strings.en() ? " min)" : " 分钟）"),
                Toast.LENGTH_SHORT).show();
        TimerService.startTimer(c, Math.max(1, t.durationMinutes) * 60, t.id, t.title);
        BigOverlay.dismiss(c, BigOverlay.KEY_IDLE);
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
