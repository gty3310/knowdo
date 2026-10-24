package com.ti.tasktimer;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/**
 * 后台渐强闹铃（v1.11.0）：暂停叫回 60 秒无反馈时的升级提醒。
 * 与任务闹钟响铃页同一套机制：素材 alarm_nature.ogg（清晨鸟鸣）、USAGE_ALARM 通道、
 * 播放增益 60 秒内从 0 线性渐强到满，不动系统音量。只响一轮，满 60 秒自动停；
 * 用户在叫回页一有操作，PauseEscalation.cancel 会立刻把它停掉。
 */
public final class AlarmRing {
    private static long ringMs = 60_000L; // 本轮时长（渐强与自停同此，v1.14.0 起可由个人设置决定）
    private static MediaPlayer player;
    private static long ringStartMs;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static final Runnable rampTick = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            float v = Math.min(1f, (System.currentTimeMillis() - ringStartMs) / (float) ringMs);
            try { player.setVolume(v, v); } catch (Exception ignored) { }
            if (v < 1f) handler.postDelayed(this, 500);
        }
    };
    private static final Runnable autoStop = () -> stopInternal();

    private AlarmRing() {}

    public static synchronized void start(Context c) {
        start(c, 60_000L);
    }

    /** 按指定时长响铃：播放增益在 durationMs 内线性渐强到满，响满即自停。 */
    public static synchronized void start(Context c, long durationMs) {
        stopInternal();
        ringMs = Math.max(1_000L, durationMs);
        try {
            Context app = c.getApplicationContext();
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            AssetFileDescriptor afd = app.getResources().openRawResourceFd(R.raw.alarm_nature);
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
            handler.postDelayed(autoStop, ringMs);
            TimerState.logEvent(app, "渐强闹铃开始（" + (ringMs / 1000) + " 秒）");
        } catch (Exception e) {
            TimerState.logEvent(c, "渐强闹铃播放失败：" + e.getClass().getSimpleName());
            stopInternal();
        }
    }

    public static synchronized void stop(Context c) {
        if (player != null) TimerState.logEvent(c, "渐强闹铃已停");
        stopInternal();
    }

    public static synchronized boolean isRinging() { return player != null; }

    private static synchronized void stopInternal() {
        handler.removeCallbacks(rampTick);
        handler.removeCallbacks(autoStop);
        if (player == null) return;
        try { player.stop(); } catch (Exception ignored) { }
        try { player.release(); } catch (Exception ignored) { }
        player = null;
    }
}
