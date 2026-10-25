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
    /** 子任务队列重建（v1.13.0）：计时/暂停中该任务的子任务被编辑，按最新列表重建队列并接管。 */
    public static final String ACTION_SUB_RESYNC = "com.ti.tasktimer.SUB_RESYNC";
    public static final String EXTRA_REASON = "pause_reason";
    public static final String EXTRA_EMOJI = "pause_emoji";
    public static final String EXTRA_SILENT = "pause_silent";
    public static final String EXTRA_DELAY_MS = "delay_ms";
    public static final String EXTRA_TOTAL_SEC = "total_sec";
    public static final String EXTRA_REMAIN_SEC = "remain_sec";
    /** 开始时按今日已用抵扣的剩余秒数（v1.21.1，仅 startTimerSmart 发出；>0 且 <total 才生效）。 */
    public static final String EXTRA_DEDUCT_REMAIN_SEC = "deduct_remain_sec";
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
    // 子任务接管对账（v1.13.0）：主页/悬浮窗的监听覆盖大多数情形，这里再注册一份，
    // 兜住「悬浮窗关着、主页已销毁」时计时中编辑经同步合并进来的子任务变化
    private final TaskStore.ChangeListener storeListener = () -> SubtaskResync.check(this);

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        TaskStore.addChangeListener(storeListener);
    }

    public static void startTimer(Context c, int totalSec, String taskId, String taskTitle) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_TOTAL_SEC, totalSec);
        i.putExtra(EXTRA_TASK_ID, taskId);
        i.putExtra(EXTRA_TASK_TITLE, taskTitle);
        c.startForegroundService(i);
    }

    /**
     * 本地开始入口的统一抵扣收口（v1.21.1）：所有「用户亲手点开始」的入口（主页、悬浮窗、
     * 日历、选任务页、空档页、致谢页、开始提醒页）开始前先过这里——该任务今天已有累计
     * 已用、且未被完成/重置基线截断（StartBaseline）、且 0＜已用＜总时长时，按剩余时间
     * 接着倒数（EXTRA_DEDUCT_REMAIN_SEC → TimerState.startWithElapsed），并弹出与暂停恢复
     * 同款的「继续计时：X（剩余约 …）」提示。返回 true＝已按抵扣起表，调用方不要再走
     * 普通 startTimer、也不要再弹「开始计时」提示；返回 false＝照常全新开始（无已用、
     * 已用≥总时长、或带子任务的任务——子任务队列位置无法由总已用还原，不做抵扣）。
     * 不许走这里的路径：ACTION_MIRROR 跨设备镜像、同步 autoStart 远程开始（仍直调
     * startTimer），暂停槽恢复（有精确 remain，走 resumeTimer，不叠加抵扣）。
     */
    public static boolean startTimerSmart(Context c, TaskItem t) {
        if (c == null || t == null || t.id == null || t.id.isEmpty()) return false;
        if (t.hasSubtasks()) return false;
        int totalSec = Math.max(1, t.durationMinutes) * 60;
        long elapsedSec = SessionLog.todayMsForTaskSince(c, t.id, StartBaseline.sinceMs(c, t.id)) / 1000;
        if (elapsedSec <= 0 || elapsedSec >= totalSec) return false;
        int remain = totalSec - (int) elapsedSec;
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_TOTAL_SEC, totalSec);
        i.putExtra(EXTRA_TASK_ID, t.id);
        i.putExtra(EXTRA_TASK_TITLE, t.title);
        i.putExtra(EXTRA_DEDUCT_REMAIN_SEC, remain);
        c.startForegroundService(i);
        android.widget.Toast.makeText(c, "继续计时：" + t.title + "（剩余约 "
                + (remain / 60) + " 分 " + (remain % 60) + " 秒）",
                android.widget.Toast.LENGTH_SHORT).show();
        return true;
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
     * 子任务队列重建请求（v1.13.0）：SubtaskResync 对账发现运行/暂停中任务的子任务变了才调。
     * 服务活着（计时中）走 startService 直达；服务没活（暂停中）把它拉起来处理完暂停槽
     * 重建后，onStartCommand 末尾会因不在计时而自行 stopSelf。
     */
    public static void resyncSubtasks(Context c, String taskId) {
        Intent i = new Intent(c, TimerService.class);
        i.setAction(ACTION_SUB_RESYNC);
        i.putExtra(EXTRA_TASK_ID, taskId);
        c.startService(i);
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
        // 结束页催促（v1.21.1）：计时被停止/暂停（用户处置）、加时（endFired 清除、
        // 新 endAt 产生，本链重置待命）、新计时开始、远端镜像覆盖——都意味着旧的
        // 「结束待确认」已作废或被接管，升级链一并停掉并清状态（无状态时为无-op）
        if (ACTION_STOP.equals(action) || ACTION_PAUSE.equals(action)
                || ACTION_PAUSE_REMIND.equals(action) || ACTION_EXTEND.equals(action)
                || ACTION_START.equals(action) || ACTION_RESUME.equals(action)
                || ACTION_MIRROR.equals(action)) {
            EndNag.cancel(this);
        }
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
            IdleAlarm.onNotIdle(this); // 任何一台设备开始计时，空档催促周期立即取消
            // 跨设备镜像：只落状态，刻意不动 SessionLog/专注守卫/屏幕采样（那些是发起端的本端语义）
            int total = intent.getIntExtra(EXTRA_TOTAL_SEC, 60);
            long startedAtMs = intent.getLongExtra("started_at_ms", System.currentTimeMillis());
            long endAtMs = intent.getLongExtra("end_at_ms", System.currentTimeMillis() + total * 1000L);
            String mid = intent.getStringExtra(EXTRA_TASK_ID);
            // 例外一处（v1.16.1，远程切换同理）：远端切到另一个任务、而本机正为旧任务开着段
            // （旧任务曾在本机发起）时，先在切换时刻把旧段关段落盘——否则段一直开着，
            // 要等新任务结束走 ACTION_STOP 才关，旧任务的「已用」会被灌进新任务的时长。
            // 同一任务的镜像（接管/对账回声）不关；本机没有打开的段时 close() 自行 no-op。
            String openId = SessionLog.openTaskId(this);
            if (!openId.isEmpty() && !openId.equals(mid)) SessionLog.close(this);
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
            // 队列快照跟本地任务列表对齐（镜像端不重建，仅作呈现/对账基线；任务不在本地时不动）
            TaskItem mt = mid == null ? null : new TaskStore(this).byId(mid);
            if (mt != null) TimerState.setSubSnapshot(this, mt.subtasks);
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
        } else if (ACTION_SUB_RESYNC.equals(action)) {
            // 计时/暂停中子任务被编辑：按最新列表重建队列（算法见 handleSubResync）
            handleSubResync();
        } else if (ACTION_RESUME.equals(action)) {
            IdleAlarm.onNotIdle(this);
            ResumeReminder.cancel(this);
            TimerState.startResumed(this,
                    intent.getIntExtra(EXTRA_TOTAL_SEC, 60), intent.getIntExtra(EXTRA_REMAIN_SEC, 60),
                    intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_TASK_TITLE));
            FocusGuard.reset(this);
            VerdictEngine.reset(this);
            ScreenCaptureService.onTimerStarted(this);
            SessionLog.open(this, intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_TASK_TITLE));
            // 队列快照按最新列表重写：暂停期间的重建已让暂停槽与列表一致，这里对齐基线
            TaskItem rt = new TaskStore(this).byId(intent.getStringExtra(EXTRA_TASK_ID));
            TimerState.setSubSnapshot(this, rt != null ? rt.subtasks : null);
            TimerState.logEvent(this, "继续计时：" + TimerState.taskTitle(this));
            SbTimerSync.onLocalTimerChanged(this);
        } else if (ACTION_START.equals(action)) {
            IdleAlarm.onNotIdle(this); // 任何入口开始计时都算：催促周期取消、空档时钟重置
            ResumeReminder.cancel(this);
            int total = intent.getIntExtra(EXTRA_TOTAL_SEC, 60);
            String sid = intent.getStringExtra(EXTRA_TASK_ID);
            TaskItem st = sid == null ? null : new TaskStore(this).byId(sid);
            // 按今日已用抵扣开始（v1.21.1）：只有 startTimerSmart 会带这个 extra；
            // 带子任务的任务双保险拦截（队列位置无法由总已用还原，仍走普通开始/暂停槽）
            int deductRemain = intent.getIntExtra(EXTRA_DEDUCT_REMAIN_SEC, 0);
            boolean deducted = deductRemain > 0 && deductRemain < total
                    && (st == null || !st.hasSubtasks());
            if (sid != null && sid.equals(TimerState.pausedTaskId(this))) TimerState.clearPaused(this);
            if (deducted) {
                TimerState.startWithElapsed(this, total, deductRemain,
                        sid, intent.getStringExtra(EXTRA_TASK_TITLE));
            } else {
                TimerState.start(this, total,
                        sid, intent.getStringExtra(EXTRA_TASK_TITLE));
            }
            // 子任务（v1.11.0）：任务带子任务时按队列逐个跑，检查点在子任务边界
            if (!deducted && st != null && st.hasSubtasks()) {
                TimerState.beginSubtasks(this, st);
                TimerState.logEvent(this, "子任务模式：「" + st.title + "」共 "
                        + st.subtasks.size() + " 个子任务，先跑「" + st.subtasks.get(0).title + "」");
            }
            // 队列快照（v1.13.0）：记下这轮计时是按哪版子任务列表跑的，供中途接管对账
            TimerState.setSubSnapshot(this, st != null ? st.subtasks : null);
            FocusGuard.reset(this);
            VerdictEngine.reset(this);
            ScreenCaptureService.onTimerStarted(this);
            SessionLog.open(this, sid, intent.getStringExtra(EXTRA_TASK_TITLE));
            if (deducted) {
                TimerState.logEvent(this, "继续计时（今日已用抵扣）：" + TimerState.taskTitle(this)
                        + "（总 " + total + " 秒，剩余 " + deductRemain + " 秒）");
            } else {
                TimerState.logEvent(this, "开始计时：" + TimerState.taskTitle(this) + "（" + total + " 秒）");
            }
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

    // ---------- 子任务队列重建（v1.13.0）：计时中加/改子任务立即接管 ----------
    //
    // 共通口径：任务级结束点/总时长只按「新旧队列剩余之差」平移——检查页停留吃掉的时间
    // （子任务边界与任务结束点之间的漂移）保持原样，不因重建被还回或重复扣掉；唯独
    // 「任务级计时中首次加子任务」的接管是整表重排：从第一个子任务开始计，总时长与结束点
    // 按子任务之和重新定，与「开始时就带子任务」的落点完全一致。
    private void handleSubResync() {
        boolean running = TimerState.isRunning(this);
        String tid = running ? TimerState.taskId(this)
                : (TimerState.isPaused(this) ? TimerState.pausedTaskId(this) : "");
        if (tid == null || tid.isEmpty()) return;
        TaskItem t = new TaskStore(this).byId(tid);
        if (t == null) return;
        java.util.List<Subtask> news = t.subtasks != null
                ? t.subtasks : new java.util.ArrayList<Subtask>();
        java.util.List<Subtask> snap = TimerState.subSnapshot(this);
        boolean changed = running
                ? rebuildRunningQueue(t, news, snap)
                : rebuildPausedQueue(t, news, snap);
        TimerState.setSubSnapshot(this, news);
        if (changed) SbTimerSync.onLocalTimerChanged(this);
    }

    private boolean rebuildRunningQueue(TaskItem t, java.util.List<Subtask> news,
                                        java.util.List<Subtask> snap) {
        long now = System.currentTimeMillis();
        if (TimerState.endFired(this)) return false; // 结束检查页已出：只同步快照，不搅动已到点的状态
        if (news.isEmpty()) {
            if (!TimerState.subActive(this)) return false;
            // 子任务被删光 → 退出子任务模式，按无子任务的原有逻辑收尾（走任务结束检查页）
            TimerState.clearSubRunState(this);
            TimerState.finishNow(this);
            TimerState.logEvent(this, "子任务已全部删除：「" + t.title + "」回任务级收尾");
            return true;
        }
        if (!TimerState.subActive(this)) {
            // 接管：任务级计时中首次出现子任务 → 立即开始第一个子任务，整表按子任务之和重排
            int totalSec = totalMinutesOf(news) * 60;
            Subtask s0 = news.get(0);
            TimerState.applyQueueState(this, 0, s0.id, s0.title, news.size(),
                    now + Math.max(1, s0.minutes) * 60_000L, now + totalSec * 1000L, totalSec);
            TimerState.logEvent(this, "子任务接管：「" + t.title + "」开始子任务「" + s0.title
                    + "」（共 " + news.size() + " 个，总时长按子任务之和 " + totalMinutesOf(news) + " 分钟）");
            return true;
        }
        if (TimerState.subFired(this)) {
            // 子任务边界已到、检查页在显：只同步计数；推进时 ACTION_SUB_ADVANCE 读最新列表
            if (TimerState.subCount(this) != news.size()) {
                TimerState.touchSubCount(this, news.size());
                return true;
            }
            return false;
        }
        String curId = TimerState.subId(this);
        int oldIndex = TimerState.subIndex(this);
        long subEnd = TimerState.subEndAt(this);
        // 旧队列从现在起的剩余（含当前子任务剩余＋其后全部子任务时长），以快照为准
        long remainOldMs = Math.max(0, subEnd - now) + sumAfterMs(snap, oldIndex);
        int p = indexOfId(news, curId);
        if (p >= 0) {
            Subtask cur = news.get(p);
            long curRemainMs = Math.max(0, subEnd - now);
            int oldMin = minutesOf(snap, curId);
            if (oldMin > 0 && oldMin != cur.minutes) {
                // 改了当前子任务时长：已用不变，剩余按「新时长 − 已用」重算（用完则立即到点）
                long usedMs = Math.max(0, oldMin * 60_000L - curRemainMs);
                curRemainMs = Math.max(0, cur.minutes * 60_000L - usedMs);
            }
            long newSubEnd = now + curRemainMs;
            long remainNewMs = curRemainMs + sumAfterMs(news, p);
            long deltaMs = remainNewMs - remainOldMs;
            boolean same = p == oldIndex && newSubEnd == subEnd && deltaMs == 0
                    && cur.title != null && cur.title.equals(TimerState.subTitle(this))
                    && TimerState.subCount(this) == news.size();
            if (same) return false;
            TimerState.applyQueueState(this, p, cur.id, cur.title, news.size(), newSubEnd,
                    TimerState.endAt(this) + deltaMs,
                    TimerState.totalSec(this) + (int) (deltaMs / 1000));
            TimerState.logEvent(this, "子任务队列已更新：「" + t.title + "」当前第 "
                    + (p + 1) + "/" + news.size() + " 个「" + cur.title + "」");
            return true;
        }
        if (oldIndex < news.size()) {
            // 正在计时的子任务被删 → 立即推进到顶上来的下一个（给它完整时长）
            Subtask nxt = news.get(oldIndex);
            long remainNewMs = Math.max(1, nxt.minutes) * 60_000L + sumAfterMs(news, oldIndex);
            long deltaMs = remainNewMs - remainOldMs;
            TimerState.applyQueueState(this, oldIndex, nxt.id, nxt.title, news.size(),
                    now + Math.max(1, nxt.minutes) * 60_000L,
                    TimerState.endAt(this) + deltaMs,
                    TimerState.totalSec(this) + (int) (deltaMs / 1000));
            TimerState.logEvent(this, "当前子任务被删除：「" + t.title + "」立即推进到「" + nxt.title + "」");
            return true;
        }
        // 正在计时的子任务被删且没有下一个 → 回任务级收尾（走任务结束检查页）
        TimerState.clearSubRunState(this);
        TimerState.finishNow(this);
        TimerState.logEvent(this, "当前子任务被删除且没有下一个：「" + t.title + "」回任务级收尾");
        return true;
    }

    private boolean rebuildPausedQueue(TaskItem t, java.util.List<Subtask> news,
                                       java.util.List<Subtask> snap) {
        if (news.isEmpty()) {
            if (!TimerState.pausedSubActive(this)) return false;
            // 子任务删光：清掉暂停槽的子任务快照，恢复后按任务级剩余走完
            TimerState.clearPausedSub(this);
            TimerState.logEvent(this, "暂停中子任务已全部删除：「" + t.title + "」恢复后按任务级收尾");
            return true;
        }
        if (!TimerState.pausedSubActive(this)) {
            // 暂停中（任务级）首次加子任务 → 队列接管：恢复计时后直接进第一个子任务
            int totalSec = totalMinutesOf(news) * 60;
            Subtask s0 = news.get(0);
            TimerState.rewritePausedSub(this, 0, s0.id, s0.title, news.size(),
                    Math.max(1, s0.minutes) * 60_000L, totalSec * 1000L, totalSec);
            TimerState.logEvent(this, "暂停中子任务接管：「" + t.title + "」恢复后从子任务「"
                    + s0.title + "」开始（共 " + news.size() + " 个）");
            return true;
        }
        String curId = TimerState.pausedSubId(this);
        int oldIndex = TimerState.pausedSubIndex(this);
        long curRemain = TimerState.pausedSubRemainMs(this);
        long remainOldMs = curRemain + sumAfterMs(snap, oldIndex);
        int p = (curId == null || curId.isEmpty()) ? -1 : indexOfId(news, curId);
        if (p < 0 && (curId == null || curId.isEmpty()) && oldIndex < news.size()) {
            p = oldIndex; // 远端镜像暂停没带子任务 id：按序号对位（尽力而为）
        }
        if (p >= 0) {
            Subtask cur = news.get(p);
            long newRemain = curRemain;
            int oldMin = minutesOf(snap, cur.id);
            if (oldMin > 0 && oldMin != cur.minutes) {
                long usedMs = Math.max(0, oldMin * 60_000L - curRemain);
                newRemain = Math.max(0, cur.minutes * 60_000L - usedMs);
            }
            long remainNewMs = newRemain + sumAfterMs(news, p);
            boolean same = p == oldIndex && newRemain == curRemain && remainNewMs == remainOldMs
                    && TimerState.pausedSubCount(this) == news.size()
                    && cur.title != null && cur.title.equals(TimerState.pausedSubTitle(this));
            if (same) return false;
            int totalSec = TimerState.pausedTotalSec(this) + (int) ((remainNewMs - remainOldMs) / 1000);
            TimerState.rewritePausedSub(this, p, cur.id, cur.title, news.size(),
                    newRemain, remainNewMs, totalSec);
            TimerState.logEvent(this, "暂停中子任务队列已更新：「" + t.title + "」当前第 "
                    + (p + 1) + "/" + news.size() + " 个「" + cur.title + "」");
            return true;
        }
        if (oldIndex < news.size()) {
            Subtask nxt = news.get(oldIndex);
            long remainNewMs = Math.max(1, nxt.minutes) * 60_000L + sumAfterMs(news, oldIndex);
            int totalSec = TimerState.pausedTotalSec(this) + (int) ((remainNewMs - remainOldMs) / 1000);
            TimerState.rewritePausedSub(this, oldIndex, nxt.id, nxt.title, news.size(),
                    Math.max(1, nxt.minutes) * 60_000L, remainNewMs, totalSec);
            TimerState.logEvent(this, "暂停中当前子任务被删除：「" + t.title + "」恢复后从「" + nxt.title + "」开始");
            return true;
        }
        // 当前子任务被删且没有下一个：清子任务快照，恢复后按任务级剩余走完
        TimerState.clearPausedSub(this);
        TimerState.logEvent(this, "暂停中当前子任务被删除且没有下一个：「" + t.title + "」恢复后按任务级收尾");
        return true;
    }

    private static int totalMinutesOf(java.util.List<Subtask> subs) {
        int sum = 0;
        for (Subtask s : subs) sum += Math.max(1, s.minutes);
        return Math.max(1, sum);
    }

    /** 列表中 index 之后全部子任务的时长合计（毫秒）。 */
    private static long sumAfterMs(java.util.List<Subtask> subs, int index) {
        long sum = 0;
        if (subs != null) {
            for (int i = index + 1; i < subs.size(); i++) sum += Math.max(1, subs.get(i).minutes) * 60_000L;
        }
        return sum;
    }

    private static int indexOfId(java.util.List<Subtask> subs, String id) {
        if (subs == null || id == null) return -1;
        for (int i = 0; i < subs.size(); i++) {
            if (id.equals(subs.get(i).id)) return i;
        }
        return -1;
    }

    /** 快照里某子任务的分钟数；不在快照里（或快照为空）返回 -1，表示无从对比、按未改处理。 */
    private static int minutesOf(java.util.List<Subtask> snap, String id) {
        int i = indexOfId(snap, id);
        return i < 0 ? -1 : Math.max(1, snap.get(i).minutes);
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
        TaskStore.removeChangeListener(storeListener);
        handler.removeCallbacks(ticker);
        FloatingWindow.hide();
        super.onDestroy();
    }
}
