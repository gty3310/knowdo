package com.ti.tasktimer;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 任务闹钟响铃页（v1.9.0）：全屏盖在锁屏上（manifest 的 showWhenLocked/turnScreenOn），
 * 循环播放内置铃声 res/raw/alarm_nature.ogg（清晨鸟鸣，录音 Joseph Sardin，
 * CC0 1.0，裁剪与循环加工见 CHANGELOG），60 秒内播放音量从 0 线性渐强到满。
 * 走 USAGE_ALARM 闹钟音频通道：手机静音/铃声音量关也照常响，且不改系统音量、无需恢复。
 * 点「知道了」停铃；计时结束响铃（thenCheck）停铃后接着拉起原结束检查页。
 * 没人理 10 分钟自动停（结束响铃同样转检查页，别让计时状态悬着）。
 */
public class TaskAlarmActivity extends Activity {
    public static final String EXTRA_TASK_ID = "alarm_task_id";
    public static final String EXTRA_TASK_TITLE = "alarm_task_title";
    public static final String EXTRA_THEN_CHECK = "alarm_then_check";

    private static final long RAMP_MS = 60_000L;
    private static final long AUTO_STOP_MS = 10 * 60_000L;

    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long ringStartMs;
    private boolean thenCheck;
    private boolean stopped;

    private final Runnable rampTick = new Runnable() {
        @Override public void run() {
            if (player == null || stopped) return;
            float v = Math.min(1f, (System.currentTimeMillis() - ringStartMs) / (float) RAMP_MS);
            try { player.setVolume(v, v); } catch (Exception ignored) { }
            if (v < 1f) handler.postDelayed(this, 500);
        }
    };
    private final Runnable autoStop = () -> stopAndFinish();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AlertHelper.cancelTaskAlarm(this);

        String taskId = getIntent().getStringExtra(EXTRA_TASK_ID);
        String title = getIntent().getStringExtra(EXTRA_TASK_TITLE);
        thenCheck = getIntent().getBooleanExtra(EXTRA_THEN_CHECK, false);
        TaskItem task = null;
        if (title == null && taskId != null) {
            task = new TaskStore(this).byId(taskId);
            if (task != null) title = task.title;
        }
        if (title == null) title = "";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));
        int pad = dp(28);
        root.setPadding(pad, dp(72), pad, pad);
        setContentView(root);

        TextView bell = new TextView(this);
        bell.setText("⏰");
        bell.setTextSize(44);
        bell.setGravity(Gravity.CENTER);
        root.addView(bell);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(30);
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        titleView.setTextColor(Ui.text(this));
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, dp(18), 0, 0);
        root.addView(titleView);

        TextView sub = new TextView(this);
        if (thenCheck) {
            sub.setText(Strings.alarmTimerEnded());
        } else {
            String at = task != null ? task.alarmAt
                    : (taskId == null ? null : alarmAtOf(taskId));
            sub.setText(Strings.alarmTimeArrived()
                    + (at == null ? "" : " · " + at.replace('T', ' ')));
        }
        sub.setTextSize(15);
        sub.setTextColor(Ui.sub(this));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(10), 0, dp(30));
        root.addView(sub);

        Button ok = new Button(this);
        ok.setText(Strings.alarmGotIt());
        ok.setTextSize(19);
        ok.setOnClickListener(v -> stopAndFinish());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60));
        ok.setLayoutParams(lp);
        root.addView(ok);

        startRinging();
        handler.postDelayed(autoStop, AUTO_STOP_MS);
    }

    private String alarmAtOf(String taskId) {
        TaskItem t = new TaskStore(this).byId(taskId);
        return t == null ? null : t.alarmAt;
    }

    private void startRinging() {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.alarm_nature);
            if (afd == null) {
                player.release();
                player = null;
                return;
            }
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setLooping(true);
            player.setVolume(0f, 0f);
            player.prepare();
            player.start();
            ringStartMs = System.currentTimeMillis();
            handler.post(rampTick);
        } catch (Exception e) {
            TimerState.logEvent(this, "任务闹钟铃声播放失败：" + e.getClass().getSimpleName());
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (player == null) return;
        try { player.stop(); } catch (Exception ignored) { }
        try { player.release(); } catch (Exception ignored) { }
        player = null;
    }

    private void stopAndFinish() {
        if (stopped) return;
        stopped = true;
        handler.removeCallbacks(rampTick);
        handler.removeCallbacks(autoStop);
        releasePlayer();
        if (thenCheck) {
            Intent i = new Intent(this, CheckActivity.class);
            i.putExtra(CheckActivity.EXTRA_TYPE, AlertHelper.TYPE_END);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception ignored) { }
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        handler.removeCallbacks(rampTick);
        handler.removeCallbacks(autoStop);
        releasePlayer();
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
