package com.ti.tasktimer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * 空档催促一轮的两段执行（v1.19.0 staging）：先震动不出声（vibrate_seconds），
 * 无反应才进入声音段（AlarmRing 既有的 60 秒内 0→最大渐强，ring_seconds）。
 * 两段都在本进程内用 Handler 串联；任一段被用户操作/路人静音/任务开始打断，
 * stop() 一次全停（震动取消、铃声停、待触发的声音段撤掉）。
 */
public final class IdleStage {
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static boolean active;
    private static boolean vibrating;
    private static Vibrator vibrator;
    private static Runnable pendingRing;

    private IdleStage() { }

    /**
     * 开始一轮：先震 vibrateMs（0 则跳过），到点若仍无人理且 withSound，起声音段。
     * 声音段由 AlarmRing 自己按 ringMs 渐强并自停，本类只负责把它按时点起来。
     */
    public static synchronized void start(final Context c, long vibrateMs, final long ringMs,
                                          final boolean withSound) {
        stopInternal();
        active = true;
        final Context app = c.getApplicationContext();
        if (vibrateMs > 0) {
            try {
                vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrating = true;
                    vibrator.vibrate(VibrationEffect.createOneShot(
                            vibrateMs, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } catch (Exception ignored) { vibrating = false; }
        }
        if (withSound && ringMs > 0) {
            pendingRing = () -> {
                synchronized (IdleStage.class) {
                    vibrating = false;
                    pendingRing = null;
                    if (!active) return;
                }
                AlarmRing.start(app, ringMs);
            };
            H.postDelayed(pendingRing, Math.max(0, vibrateMs));
        } else if (vibrateMs > 0) {
            pendingRing = () -> {
                synchronized (IdleStage.class) { vibrating = false; pendingRing = null; }
            };
            H.postDelayed(pendingRing, vibrateMs);
        }
    }

    /** 全停：震动、待触发声音段、正在响的铃声一并停掉。 */
    public static synchronized void stop(Context c) {
        stopInternal();
    }

    private static synchronized void stopInternal() {
        active = false;
        vibrating = false;
        if (pendingRing != null) {
            H.removeCallbacks(pendingRing);
            pendingRing = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignored) { }
            vibrator = null;
        }
        if (AlarmRing.isRinging()) AlarmRing.stopQuiet();
    }

    public static synchronized boolean isActive() { return active; }
    public static synchronized boolean isVibrating() { return active && vibrating; }
    public static synchronized boolean isRinging() { return AlarmRing.isRinging(); }
}
