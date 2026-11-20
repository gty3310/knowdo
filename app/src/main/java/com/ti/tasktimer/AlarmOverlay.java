package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 任务闹钟响铃的 99% 大窗内容（Guild Issue #6 第二批）：与 TaskAlarmActivity
 * 同画面、同文案、同响铃——铃声仍由本内容自带的 MediaPlayer 播放（内置
 * alarm_nature.ogg、USAGE_ALARM 通道、60 秒线性渐强），不是 AlarmRing
 * （那是空档/暂停升级链的铃）。「知道了」停铃；thenCheck 时停铃后接着
 * 出结束检查（优先走检查大窗、没被接管时退回原 CheckActivity 直拉，
 * 与原页 openCheckIfNeeded 的判定逐字相同）；没人理 10 分钟自动停、
 * 同样转检查。被外部收回（未经「知道了」）时 onDismissed 兜底转检查，
 * 等价原页 onDestroy 的防 endFired 卡死处理。
 *
 * 与原页的差异（路径适配，如实列明）：渐强步进由壳的每秒 tick 驱动
 * （原页是 500ms Handler），60 秒渐强听感等价；10 分钟自动停也由 tick
 * 按出窗时刻判定（原页是 Handler 定时）。
 */
public final class AlarmOverlay implements BigOverlay.Content {
    private static final long RAMP_MS = 60_000L;
    private static final long AUTO_STOP_MS = 10 * 60_000L;

    private String taskId;
    private String title;
    private boolean thenCheck;
    private MediaPlayer player;
    private long ringStartMs;
    private long showStartMs;
    private boolean stopped;
    private boolean proceededToCheck;
    private boolean ringFailed;

    /** 新一轮响铃先重置：停掉上一轮可能还在响的播放器，清全部状态位。 */
    void prepare(String newTaskId, String newTitle, boolean newThenCheck) {
        releasePlayer();
        taskId = newTaskId;
        title = newTitle;
        thenCheck = newThenCheck;
        stopped = false;
        proceededToCheck = false;
        ringFailed = false;
        ringStartMs = 0;
        showStartMs = 0;
    }

    @Override
    public View createView(Context c) {
        Strings.init(c);
        String t = title;
        TaskItem task = null;
        if (t == null && taskId != null) {
            task = new TaskStore(c).byId(taskId);
            if (task != null) t = task.title;
        }
        if (t == null) t = "";

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(c));
        int pad = dp(c, 28);
        root.setPadding(pad, dp(c, 72), pad, pad);

        TextView bell = new TextView(c);
        bell.setText("⏰");
        bell.setTextSize(44);
        bell.setGravity(Gravity.CENTER);
        root.addView(bell);

        TextView titleView = new TextView(c);
        titleView.setText(t);
        titleView.setTextSize(30);
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        titleView.setTextColor(Ui.text(c));
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, dp(c, 18), 0, 0);
        root.addView(titleView);

        TextView sub = new TextView(c);
        if (thenCheck) {
            sub.setText(Strings.alarmTimerEnded());
        } else {
            String at = task != null ? task.alarmAt
                    : (taskId == null ? null : alarmAtOf(c, taskId));
            sub.setText(Strings.alarmTimeArrived()
                    + (at == null ? "" : " · " + at.replace('T', ' ')));
        }
        sub.setTextSize(15);
        sub.setTextColor(Ui.sub(c));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(c, 10), 0, dp(c, 30));
        root.addView(sub);

        Button ok = new Button(c);
        ok.setText(Strings.alarmGotIt());
        ok.setTextSize(19);
        ok.setOnClickListener(v -> stopAndFinish(c));
        ok.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 60)));
        root.addView(ok);
        return root;
    }

    @Override
    public void onShown(Context c) {
        TopPages.alarmShown = true; // 与原页 onResume 同登记
        AlertHelper.cancelTaskAlarm(c); // 与原页 onCreate 同一步
        showStartMs = System.currentTimeMillis();
        startRinging(c);
    }

    @Override
    public boolean tick(Context c) {
        if (stopped) return false;
        // 任务已在别处确认/停表：本页使命结束，停铃收窗（不再转检查）
        if (thenCheck && (!TimerState.isRunning(c) || !TimerState.endFired(c))) {
            releasePlayer();
            return false;
        }
        if (player == null && !ringFailed) startRinging(c); // 复出重建后的补启
        if (player != null && ringStartMs > 0) {
            float v = Math.min(1f, (System.currentTimeMillis() - ringStartMs) / (float) RAMP_MS);
            try { player.setVolume(v, v); } catch (Exception ignored) { }
        }
        if (showStartMs > 0 && System.currentTimeMillis() - showStartMs >= AUTO_STOP_MS) {
            stopAndFinish(c); // 没人理 10 分钟自动停（thenCheck 时同样转检查页）
            return true; // stopAndFinish 已收窗
        }
        return true;
    }

    @Override
    public void onDismissed(Context c) {
        TopPages.alarmShown = false;
        releasePlayer();
        if (!stopped) {
            // 未经「知道了」被收回：与原页 onDestroy 同兜底，避免 endFired 卡住
            stopped = true;
            openCheckIfNeeded(c);
        }
    }

    private String alarmAtOf(Context c, String id) {
        TaskItem t = new TaskStore(c).byId(id);
        return t == null ? null : t.alarmAt;
    }

    private void startRinging(Context c) {
        if (player != null || ringFailed || stopped) return;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            AssetFileDescriptor afd = c.getResources().openRawResourceFd(R.raw.alarm_nature);
            if (afd == null) {
                player.release();
                player = null;
                ringFailed = true;
                return;
            }
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setLooping(true);
            player.setVolume(0f, 0f);
            player.prepare();
            player.start();
            ringStartMs = System.currentTimeMillis();
        } catch (Exception e) {
            TimerState.logEvent(c, "任务闹钟铃声播放失败：" + e.getClass().getSimpleName());
            releasePlayer();
            ringFailed = true;
        }
    }

    private void releasePlayer() {
        if (player == null) return;
        try { player.stop(); } catch (Exception ignored) { }
        try { player.release(); } catch (Exception ignored) { }
        player = null;
    }

    /** thenCheck 时转结束检查：优先检查大窗，没被接管退回原页直拉；只走一次（与原页同）。 */
    private void openCheckIfNeeded(Context c) {
        if (!thenCheck || proceededToCheck) return;
        proceededToCheck = true;
        // 任务已在别处确认/停表（悬浮窗、Mac 端、远端同步）就不再拉结束检查页
        if (!TimerState.isRunning(c) || !TimerState.endFired(c)) return;
        if (BigOverlay.requestCheck(c, AlertHelper.TYPE_END)) return;
        Intent i = new Intent(c, CheckActivity.class);
        i.putExtra(CheckActivity.EXTRA_TYPE, AlertHelper.TYPE_END);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception ignored) { }
    }

    private void stopAndFinish(Context c) {
        if (stopped) return;
        stopped = true;
        releasePlayer();
        openCheckIfNeeded(c);
        BigOverlay.dismiss(c, BigOverlay.KEY_ALARM);
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
