package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * 暂停 30 秒无下一步时，把现有 TYPE_APPLICATION_OVERLAY 悬浮窗放大到约 99% 屏幕
 * （不能划掉、不能关，必须选了下一步才收回）。
 *
 * 口径开关（待 Ti 确认，改这里即可）：
 * {@link #COVER_DURING_ACTIVE_BREAK}＝false：已经选好目的和时长的正经休息
 * 期间不盖屏；叫回到点后 30 秒仍未回来，再出 99% 遮罩。
 *
 * 30 秒触发用精确闹钟（与 PauseEscalation 同法），进程被杀也能到点；
 * 开机 / 服务重启由 {@link #ensureScheduled} 续排或补触发。
 *
 * 无悬浮窗权限时降级为全屏通知＋{@link PauseLockActivity}（Activity 可被划掉，
 * 所以只作后备；划掉后 lock_due 仍在，下一拍会再发通知）。
 */
public final class PauseLock {
    public static final String ACTION = "com.ti.tasktimer.PAUSE_LOCK";
    public static final long DELAY_MS = 30_000L;
    /**
     * 待确认：正经休息（有目的且叫回还在未来）期间不要盖屏。
     * 改成 true＝一暂停满 30 秒就盖，不论是否已选好休息。
     */
    public static final boolean COVER_DURING_ACTIVE_BREAK = false;

    private static final String PREF = "pause_lock";
    private static final int REQ = 791;
    static final int NOTIF = 209;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PauseLock() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 已到点、用户还没选下一步：遮罩（或降级页）应该在。 */
    public static boolean isDue(Context c) { return sp(c).getBoolean("due", false); }

    public static long armedAt(Context c) { return sp(c).getLong("at", 0); }

    public static String taskId(Context c) {
        String s = sp(c).getString("task_id", "");
        return s == null ? "" : s;
    }

    /** 已排期或已到期：衔接守卫把这段交给本类，不要再弹选任务页。 */
    public static boolean blocksIdle(Context c) {
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) return false;
        return isDue(c) || armedAt(c) > 0;
    }

    /** 这次暂停算不算已经有了「下一步」（目的＋还在未来的叫回）。 */
    public static boolean hasNextStep(Context c) {
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) return false;
        String reason = TimerState.pauseReason(c);
        if (reason == null || reason.isEmpty()) return false;
        return TimerState.remindAt(c) > System.currentTimeMillis();
    }

    /**
     * 暂停槽刚发生变化（开始/结束/改目的/改叫回/远端镜像）后调用。
     * 有下一步且不在休息中盖屏：撤掉排期和遮罩；否则给这次暂停排 30 秒。
     */
    public static void onPauseStateChanged(Context c) {
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) {
            cancel(c);
            return;
        }
        if (hasNextStep(c) && !COVER_DURING_ACTIVE_BREAK) {
            cancel(c);
            return;
        }
        if (isDue(c) && taskId(c).equals(TimerState.pausedTaskId(c))) {
            // 已经在等选择：保持遮罩，不要把 30 秒重新计一遍
            showOrFallback(c);
            return;
        }
        arm(c);
    }

    /** 叫回到点（ResumeReceiver / 静音到点）：再给 30 秒，不回来就盖屏。 */
    public static void onBreakDue(Context c) {
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) return;
        arm(c);
    }

    public static void arm(Context c) {
        String tid = TimerState.pausedTaskId(c);
        if (tid.isEmpty() || TimerState.isRunning(c)) { cancel(c); return; }
        long at = System.currentTimeMillis() + DELAY_MS;
        sp(c).edit().putBoolean("due", false).putString("task_id", tid).putLong("at", at).apply();
        FloatingWindow.retractLock(c);
        cancelFallback(c);
        setExact(c, at);
        TimerState.logEvent(c, "暂停遮罩：已排 30 秒后盖屏「" + TimerState.pausedTitle(c) + "」");
    }

    /** 用户已选下一步，或暂停槽已空：撤闹钟、收遮罩、清状态。 */
    public static void cancel(Context c) {
        boolean had = isDue(c) || armedAt(c) > 0;
        sp(c).edit().clear().apply();
        cancelAlarm(c);
        cancelFallback(c);
        FloatingWindow.retractLock(c);
        PauseLockActivity.dismissCurrent();
        if (had) { /* 状态已清，不再打日志以免刷屏 */ }
    }

    /** 闹钟到点或开机补触发。 */
    static void onAlarmFired(Context c) {
        sp(c).edit().putLong("at", 0).apply();
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) { cancel(c); return; }
        String tid = taskId(c);
        if (!tid.isEmpty() && !tid.equals(TimerState.pausedTaskId(c))) { cancel(c); return; }
        sp(c).edit().putBoolean("due", true)
                .putString("task_id", TimerState.pausedTaskId(c)).apply();
        TimerState.logEvent(c, "暂停遮罩：30 秒到，盖屏「" + TimerState.pausedTitle(c) + "」");
        showOrFallback(c);
    }

    /**
     * 每秒由 FloatService 调用：通话中把遮罩缩回去；挂断后若仍 due 再盖上。
     * 无权限时保证降级通知还在。
     */
    public static void poll(Context c) {
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) {
            if (isDue(c) || armedAt(c) > 0) cancel(c);
            return;
        }
        if (!isDue(c)) return;
        if (GuardDispatch.inCall(c)) {
            if (FloatingWindow.isLockShowing()) {
                FloatingWindow.retractLock(c);
                TimerState.logEvent(c, "暂停遮罩：通话中暂时缩小");
            }
            return;
        }
        showOrFallback(c);
    }

    /** 开机 / 服务起来：已到期立刻补盖；未到期把精确闹钟续上。 */
    public static void ensureScheduled(Context c) {
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) {
            if (isDue(c) || armedAt(c) > 0) cancel(c);
            return;
        }
        if (isDue(c)) { showOrFallback(c); return; }
        long at = armedAt(c);
        if (at <= 0) return;
        if (at <= System.currentTimeMillis()) onAlarmFired(c);
        else setExact(c, at);
    }

    public static void showOrFallback(final Context c) {
        if (!isDue(c)) return;
        if (GuardDispatch.inCall(c)) return; // 通话中不抢，poll 会在挂断后补
        MAIN.post(() -> {
            if (!isDue(c)) return;
            if (GuardDispatch.inCall(c)) return;
            if (AlertHelper.canDrawOverlays(c) && FloatingWindow.enabled(c)) {
                cancelFallback(c);
                FloatingWindow.showLock(c);
            } else {
                FloatingWindow.retractLock(c);
                fireFallback(c);
            }
        });
    }

    // ---------------- 精确闹钟 ----------------
    private static PendingIntent intent(Context c) {
        Intent i = new Intent(c, PauseLockReceiver.class);
        i.setAction(ACTION);
        return PendingIntent.getBroadcast(c, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setExact(Context c, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(c));
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(c));
        } catch (SecurityException se) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(c)); }
            catch (Exception ignored) { }
        } catch (Exception ignored) { }
    }

    private static void cancelAlarm(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(intent(c));
        } catch (Exception ignored) { }
    }

    // ---------------- 无悬浮权限：全屏通知＋后备页 ----------------
    private static void fireFallback(Context c) {
        AlertHelper.createChannels(c);
        Intent page = new Intent(c, PauseLockActivity.class);
        page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, NOTIF, page,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, AlertHelper.CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(Strings.pauseLockNotifTitle())
                .setContentText(Strings.pauseLockNotifText(TimerState.pausedTitle(c)))
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF, n);
        // 亮屏未锁时顺便直接拉后备页（没有悬浮权限时这条路不一定成功，失败就只留通知）
        try { c.startActivity(page); } catch (Exception ignored) { }
    }

    static void cancelFallback(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIF);
        } catch (Exception ignored) { }
    }

    // ---------------- 四个下一步（遮罩与后备页共用） ----------------

    public static void chooseContinue(Context c) {
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) { cancel(c); return; }
        TaskItem t = new TaskStore(c).byId(TimerState.pausedTaskId(c));
        if (t == null) {
            android.widget.Toast.makeText(c, Strings.pauseLockTaskGone(), android.widget.Toast.LENGTH_LONG).show();
            cancel(c);
            TimerState.clearPaused(c);
            ResumeReminder.cancel(c);
            return;
        }
        int total = TimerState.pausedTotalSec(c);
        if (total <= 0) total = Math.max(1, t.durationMinutes) * 60;
        int rem = (int) Math.max(1, TimerState.pausedRemainMs(c) / 1000);
        cancel(c);
        TimerService.resumeTimer(c, total, rem, t.id, t.title);
    }

    /**
     * 在遮罩上叠一张暂停卡；确认后写入目的和叫回，遮罩收回。
     * 取消卡＝遮罩不动。
     */
    public static void chooseTimedBreak(Context c) {
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) return;
        String title = TimerState.pausedTitle(c);
        PauseCard.showOverlay(c, title, Strings.pauseStartBtn(), ch -> {
            TimerState.setPauseMeta(c, ch.reason, ch.emoji, ch.silent);
            ResumeReminder.schedule(c, TimerState.pausedTaskId(c), ch.delayMs);
            SbTimerSync.onLocalTimerChanged(c);
            onPauseStateChanged(c); // 有下一步：收回遮罩
            FloatingWindow.refresh(c);
        });
    }

    /** 先恢复再立刻到点，走既有结束检查页（加时/做完了都还能用）。 */
    public static void chooseMarkDone(Context c) {
        if (!TimerState.isPaused(c) || TimerState.isRunning(c)) { cancel(c); return; }
        cancel(c);
        TimerService.endPaused(c);
    }

    /**
     * 换任务：清掉暂停槽（已用已在 SessionLog 里，之后可按今日已用接着倒数），
     * 打开行页让他选下一件。不把当前任务继续留在「无下一步的暂停」里。
     */
    public static void chooseSwitchTask(Context c) {
        ResumeReminder.cancel(c);
        TimerState.clearPaused(c);
        SbTimerSync.onLocalTimerChanged(c);
        cancel(c);
        try { MainActivity.openXing(c); } catch (Exception ignored) { }
        FloatingWindow.refresh(c);
    }
}
