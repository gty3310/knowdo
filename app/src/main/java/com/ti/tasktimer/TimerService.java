package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * 倒计时前台服务。
 * 主触发：服务内每 250ms 对照绝对时间点检查；兜底：AlarmManager 精确闹钟（进程被杀也能触发）。
 * 两条路都汇入 dispatch()，用 halfFired/endFired 标记保证每个点只弹一次。
 */
public class TimerService extends Service {
    public static final String ACTION_START = "com.ti.tasktimer.START";
    /** 跨设备镜像启动：只落 TimerState + 前台/闹钟/tick，不开 SessionLog、不重置守卫、不启屏幕采样。 */
    public static final String ACTION_MIRROR = "com.ti.tasktimer.MIRROR";
    public static final String ACTION_STOP = "com.ti.tasktimer.STOP";
    public static final String ACTION_EXTEND = "com.ti.tasktimer.EXTEND";
    public static final String ACTION_PAUSE = "com.ti.tasktimer.PAUSE";
    public static final String ACTION_PAUSE_REMIND = "com.ti.tasktimer.PAUSE_REMIND";
    public static final String ACTION_RESUME = "com.ti.tasktimer.RESUME";
    /** 子任务推进（v1.11.0）：当前子任务到点确认/跳过后，开始下一个子任务。 */
    public static final String ACTION_SUB_ADVANCE = "com.ti.tasktimer.SUB_ADVANCE";
    public static final String EXTRA_REASON = "pause_reason";
    public static final String EXTRA_EMOJI = "pause_emoji";
    public static final String EXTRA_SILENT = "pause_silent";
    public static final String EXTRA_DELAY_MS = "delay_ms";
    public static final String EXTRA_TOTAL_SEC = "total_sec";
    public static final String EXTRA_REMAIN_SEC = "remain_sec";
    public static final String EXTRA_ADD_SEC = "add_sec";
    public static final String EXTRA_TASK_ID = "task_id";
    public static final String EXTRA_TASK_TITLE = "task_title";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, 250);
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    public static void startTimer(Context c, int totalSec, String taskId, String taskTitle) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_TOTAL_SEC, totalSec);
        i.putExtra(EXTRA_TASK_ID, taskId);
        i.putExtra(EXTRA_TASK_TITLE, taskTitle);
        c.startForegroundService(i);
    }

    public static void extendTimer(Context c, int addSec) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_EXTEND);
        i.putExtra(EXTRA_ADD_SEC, addSec);
        c.startForegroundService(i);
    }

    public static void stopTimer(Context c) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_STOP);
        c.startService(i);
    }

    public static void pauseTimer(Context c) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_PAUSE);
        c.startService(i);
    }

    /** 暂停并约好多久后叫回（悬浮窗暂停菜单用）。 */
    public static void pauseWithReminder(Context c, long delayMs) {
        pauseWithReminder(c, delayMs, null, null, false);
    }

    /** 暂停并带目的（v1.11.0 暂停卡）：目的三件套经 intent 交给服务，在暂停落槽后写入暂停槽。 */
    public static void pauseWithReminder(Context c, long delayMs, String reason, String emoji, boolean silent) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_PAUSE_REMIND);
        i.putExtra(EXTRA_DELAY_MS, delayMs);
        if (reason != null) i.putExtra(EXTRA_REASON, reason);
        if (emoji != null) i.putExtra(EXTRA_EMOJI, emoji);
        i.putExtra(EXTRA_SILENT, silent);
        c.startService(i);
    }

    /** 子任务推进：开始下一个子任务（子任务检查页「下一个/跳过」用）。 */
    public static void advanceSubtask(Context c) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_SUB_ADVANCE);
        c.startForegroundService(i);
    }

    /**
     * 镜像远端正在跑的倒计时（SbTimerSync 收到远端 timer_state 后调用）：
     * startedAtMs 是虚拟起点（结束点 − 总时长），绝对时间点与远端严格对齐，双端同一时刻到点。
     */
    public static void mirrorStart(Context c, int totalSec, long startedAtMs, long endAtMs,
                                   String taskId, String taskTitle) {
        mirrorStart(c, totalSec, startedAtMs, endAtMs, taskId, taskTitle, -1, null, null, 0, 0);
    }

    /** 镜像启动（带子任务状态，v1.11.0）：subIndex<0 表示远端任务没有子任务。 */
    public static void mirrorStart(Context c, int totalSec, long startedAtMs, long endAtMs,
                                   String taskId, String taskTitle,
                                   int subIndex, String subId, String subTitle, int subCount, long subEndAt) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_MIRROR);
        i.putExtra(EXTRA_TOTAL_SEC, totalSec);
        i.putExtra("started_at_ms", startedAtMs);
        i.putExtra("end_at_ms", endAtMs);
        i.putExtra(EXTRA_TASK_ID, taskId);
        i.putExtra(EXTRA_TASK_TITLE, taskTitle);
        i.putExtra("sub_index", subIndex);
        if (subId != null) i.putExtra("sub_id", subId);
        if (subTitle != null) i.putExtra("sub_title", subTitle);
        i.putExtra("sub_count", subCount);
        i.putExtra("sub_end_at", subEndAt);
        c.startForegroundService(i);
    }

    public static void resumeTimer(Context c, int totalSec, int remainSec, String taskId, String taskTitle) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_RESUME);
        i.putExtra(EXTRA_TOTAL_SEC, totalSec);
        i.putExtra(EXTRA_REMAIN_SEC, remainSec);
        i.putExtra(EXTRA_TASK_ID, taskId);
        i.putExtra(EXTRA_TASK_TITLE, taskTitle);
        c.startForegroundService(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AlertHelper.createChannels(this);
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE.equals(action) || ACTION_PAUSE_REMIND.equals(action)) {
            long remain = TimerState.endAt(this) - System.currentTimeMillis();
            boolean saved = false;
            if (TimerState.isRunning(this) && remain > 0 && !TimerState.endFired(this)) {
                TimerState.setPaused(this, remain);
                saved = true;
                TimerState.logEvent(this, "已暂停：" + TimerState.taskTitle(this) + "，剩余 " + (remain / 1000) + " 秒");
            }
            if (saved && ACTION_PAUSE_REMIND.equals(action)) {
                long delay = intent.getLongExtra(EXTRA_DELAY_MS, 0);
                if (delay > 0) ResumeReminder.schedule(this, TimerState.pausedTaskId(this), delay);
                // 暂停卡的目的三件套：暂停落槽后写入（setPaused 已先清掉旧值）
                if (intent.hasExtra(EXTRA_REASON) || intent.hasExtra(EXTRA_EMOJI)) {
                    TimerState.setPauseMeta(this, intent.getStringExtra(EXTRA_REASON),
                            intent.getStringExtra(EXTRA_EMOJI),
                            intent.getBooleanExtra(EXTRA_SILENT, false));
                    SbTimerSync.onLocalTimerChanged(this); // 目的进 timer_state 镜像
                }
            }
            SessionLog.close(this);
            TimerState.stop(this);
            SbTimerSync.onLocalTimerChanged(this);
            ScreenCaptureService.onTimerStopped(this);
            cancelAlarms();
            AlertHelper.cancelAlert(this);
            FloatingWindow.hide();
            handler.removeCallbacks(ticker);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            TimerState.logEvent(this, "计时已停止/完成");
            SessionLog.close(this);
            TimerState.stop(this);
            SbTimerSync.onLocalTimerChanged(this);
            ScreenCaptureService.onTimerStopped(this);
            cancelAlarms();
            AlertHelper.cancelAlert(this);
            FloatingWindow.hide();
            handler.removeCallbacks(ticker);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_MIRROR.equals(action)) {
            // 跨设备镜像：只落状态，刻意不动 SessionLog/专注守卫/屏幕采样（那些是发起端的本端语义）
            int total = intent.getIntExtra(EXTRA_TOTAL_SEC, 60);
            long startedAtMs = intent.getLongExtra("started_at_ms", System.currentTimeMillis());
            long endAtMs = intent.getLongExtra("end_at_ms", System.currentTimeMillis() + total * 1000L);
            String mid = intent.getStringExtra(EXTRA_TASK_ID);
            boolean half = System.currentTimeMillis() >= startedAtMs + total * 500L
                    || (mid != null && mid.equals(TimerState.taskId(this)) && TimerState.halfFired(this));
            TimerState.mirrorStart(this, total, startedAtMs, endAtMs,
                    intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_TASK_TITLE), half);
            // 远端子任务状态一并镜像（老端没带这些 extra 时清掉本机旧子任务态）
            TimerState.applyMirrorSub(this,
                    intent.getIntExtra("sub_index", -1),
                    intent.getStringExtra("sub_id"),
                    intent.getStringExtra("sub_title"),
                    intent.getIntExtra("sub_count", 0),
                    intent.getLongExtra("sub_end_at", 0));
        } else if (ACTION_SUB_ADVANCE.equals(action)) {
            TaskItem t = new TaskStore(this).byId(TimerState.taskId(this));
            if (t != null && t.hasSubtasks() && TimerState.subActive(this)) {
                int next = TimerState.subIndex(this) + 1;
                if (next < t.subtasks.size()) {
                    TimerState.advanceSub(this, next, t.subtasks.get(next), t.subtasks.size());
                    TimerState.logEvent(this, "子任务推进：「" + t.title + "」开始第 "
                            + (next + 1) + "/" + t.subtasks.size() + " 个子任务「"
                            + t.subtasks.get(next).title + "」");
                    SbTimerSync.onLocalTimerChanged(this);
                }
            }
        } else if (ACTION_RESUME.equals(action)) {
            ResumeReminder.cancel(this);
            TimerState.startResumed(this,
                    intent.getIntExtra(EXTRA_TOTAL_SEC, 60), intent.getIntExtra(EXTRA_REMAIN_SEC, 60),
                    intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_TASK_TITLE));
            FocusGuard.reset(this);
            VerdictEngine.reset(this);
            ScreenCaptureService.onTimerStarted(this);
            SessionLog.open(this, intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_TASK_TITLE));
            TimerState.logEvent(this, "继续计时：" + TimerState.taskTitle(this));
            SbTimerSync.onLocalTimerChanged(this);
        } else if (ACTION_START.equals(action)) {
            ResumeReminder.cancel(this);
            int total = intent.getIntExtra(EXTRA_TOTAL_SEC, 60);
            String sid = intent.getStringExtra(EXTRA_TASK_ID);
            if (sid != null && sid.equals(TimerState.pausedTaskId(this))) TimerState.clearPaused(this);
            TimerState.start(this, total,
                    sid, intent.getStringExtra(EXTRA_TASK_TITLE));
            // 子任务（v1.11.0）：任务带子任务时按队列逐个跑，检查点在子任务边界
            TaskItem st = sid == null ? null : new TaskStore(this).byId(sid);
            if (st != null && st.hasSubtasks()) {
                TimerState.beginSubtasks(this, st);
                TimerState.logEvent(this, "子任务模式：「" + st.title + "」共 "
                        + st.subtasks.size() + " 个子任务，先跑「" + st.subtasks.get(0).title + "」");
            }
            FocusGuard.reset(this);
            VerdictEngine.reset(this);
            ScreenCaptureService.onTimerStarted(this);
            SessionLog.open(this, sid, intent.getStringExtra(EXTRA_TASK_TITLE));
            TimerState.logEvent(this, "开始计时：" + TimerState.taskTitle(this) + "（" + total + " 秒）");
            SbTimerSync.onLocalTimerChanged(this);
        } else if (ACTION_EXTEND.equals(action)) {
            int add = intent.getIntExtra(EXTRA_ADD_SEC, 30);
            TimerState.extend(this, add);
            TimerState.logEvent(this, "加时 " + add + " 秒，新结束时间已顺延");
            SbTimerSync.onLocalTimerChanged(this);
        }
        // START / EXTEND / 系统重启恢复（intent==null）都汇到这里
        if (TimerState.isRunning(this)) {
            startInForeground();
            scheduleAlarms();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
            tick();
            return START_STICKY;
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    private void tick() {
        if (!TimerState.isRunning(this)) return;
        long now = System.currentTimeMillis();
        // 悬浮窗：计时中显示任务与倒计时；检查页将弹出时让位；到点等待确认时隐藏
        if (TimerState.endFired(this)) {
            FloatingWindow.hide();
        } else {
            FloatingWindow.ensureShown(this);
            long remainSec = Math.max(0, (TimerState.endAt(this) - now) / 1000);
            String remain = remainSec >= 3600
                    ? String.format("%d:%02d:%02d", remainSec / 3600, (remainSec % 3600) / 60, remainSec % 60)
                    : String.format("%d:%02d", remainSec / 60, remainSec % 60);
            FloatingWindow.update(this, remain);
        }
        if (TimerState.subActive(this)) {
            // 子任务模式：检查点只在子任务边界；最后一个子任务的边界即任务结束点（下面 END 处理）
            if (!TimerState.subFired(this) && now >= TimerState.subEndAt(this)) {
                TimerState.markSubFired(this);
                if (TimerState.subIndex(this) < TimerState.subCount(this) - 1) {
                    FloatingWindow.setSuppressed(true);
                    AlertHelper.fire(this, AlertHelper.TYPE_SUB);
                }
            }
        } else if (!TimerState.halfFired(this) && now >= TimerState.halfAt(this)) {
            TimerState.markHalfFired(this);
            FloatingWindow.setSuppressed(true);
            AlertHelper.fire(this, AlertHelper.TYPE_HALF);
        }
        if (!TimerState.endFired(this) && now >= TimerState.endAt(this)) {
            TimerState.markEndFired(this);
            FloatingWindow.setSuppressed(true);
            AlertHelper.fire(this, AlertHelper.TYPE_END);
        }
        FocusGuard.tick(this);
        // 倒计时数字由通知的 chronometer 显示，文本每 tick 刷新一次当前阶段
        startInForeground();
    }

    /** 兜底触发点的统一入口（AlertReceiver 也调这里的逻辑标记，避免重复弹）。 */
    public static void onAlarmFired(Context c, int type) {
        if (!TimerState.isRunning(c)) return;
        if (type == AlertHelper.TYPE_SUB) {
            if (!TimerState.subActive(c) || TimerState.subFired(c)) return;
            TimerState.markSubFired(c);
            // 最后一个子任务的边界由 END 闹钟负责，这里不重复弹
            if (TimerState.subIndex(c) >= TimerState.subCount(c) - 1) return;
        } else if (type == AlertHelper.TYPE_HALF) {
            if (TimerState.halfFired(c)) return;
            TimerState.markHalfFired(c);
        } else {
            if (TimerState.endFired(c)) return;
            TimerState.markEndFired(c);
        }
        FloatingWindow.setSuppressed(true);
        AlertHelper.fire(c, type);
    }

    private void startInForeground() {
        long endAt = TimerState.endAt(this);
        long remainSec = Math.max(0, (endAt - System.currentTimeMillis()) / 1000);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, AlertHelper.CHANNEL_RUNNING)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(TimerState.taskTitle(this) + " · 计时中")
                .setContentText(TimerState.endFired(this) ? "时间到，等待你确认" : "剩余约 " + remainSec + " 秒")
                .setContentIntent(pi)
                .setOngoing(true)
                .setWhen(endAt)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(AlertHelper.NOTIF_RUNNING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(AlertHelper.NOTIF_RUNNING, n);
        }
    }

    private PendingIntent alarmIntent(int type) {
        Intent i = new Intent(this, AlertReceiver.class);
        i.setAction(AlertReceiver.ACTION_ALARM);
        i.putExtra(AlertReceiver.EXTRA_TYPE, type);
        return PendingIntent.getBroadcast(this, type, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void scheduleAlarms() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (!TimerState.halfFired(this)) {
            setAlarm(am, TimerState.halfAt(this), alarmIntent(AlertHelper.TYPE_HALF), exact);
        }
        if (TimerState.subActive(this) && !TimerState.subFired(this)
                && TimerState.subIndex(this) < TimerState.subCount(this) - 1) {
            setAlarm(am, TimerState.subEndAt(this), alarmIntent(AlertHelper.TYPE_SUB), exact);
        }
        setAlarm(am, TimerState.endAt(this), alarmIntent(AlertHelper.TYPE_END), exact);
    }

    private void setAlarm(AlarmManager am, long atMillis, PendingIntent pi, boolean exact) {
        if (atMillis <= System.currentTimeMillis()) return;
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
        }
    }

    private void cancelAlarms() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(alarmIntent(AlertHelper.TYPE_HALF));
        am.cancel(alarmIntent(AlertHelper.TYPE_SUB));
        am.cancel(alarmIntent(AlertHelper.TYPE_END));
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        FloatingWindow.hide();
        super.onDestroy();
    }
}
