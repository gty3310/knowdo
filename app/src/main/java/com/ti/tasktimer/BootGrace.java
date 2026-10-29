package com.ti.tasktimer;

import android.os.SystemClock;

/**
 * 开机宽限的单一口径（v1.37.0）：刚开机那几分钟手机本来就忙，知行的本地 AI
 * 重活（屏幕守卫采样、向量/黄图模型首次加载）与开机拉起的云端同步都往后挪，
 * 先让用户顺畅把今天的任务输完。计时、提醒、悬浮窗、晨间门一律不在宽限之列。
 * 以 SystemClock.elapsedRealtime()（自开机流逝毫秒，含深度睡眠）算剩余时长：
 * 进程重启不影响，不用持久化、不用 BootReceiver 的时间戳。
 */
public final class BootGrace {
    /** AI 宽限：开机后 3 分钟内不启动守卫采样（模型随采样懒加载，自然一并推迟）。 */
    public static final long GRACE_MS = 180_000L;
    /** 同步延后：开机自动拉起的实时同步改到开机 2 分钟后（用户主动打开 App 的同步不延后）。 */
    public static final long SYNC_DELAY_MS = 120_000L;

    private BootGrace() { }

    /** 距 AI 宽限结束还剩多少毫秒；已过宽限返回 0。 */
    public static long remainingGraceMs() { return remaining(GRACE_MS); }

    /** 距同步可启动还剩多少毫秒；已过延后期返回 0。 */
    public static long remainingSyncDelayMs() { return remaining(SYNC_DELAY_MS); }

    private static long remaining(long windowMs) {
        long elapsed = SystemClock.elapsedRealtime();
        return elapsed >= windowMs ? 0L : windowMs - elapsed;
    }
}
