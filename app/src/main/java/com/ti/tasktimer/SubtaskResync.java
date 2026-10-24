package com.ti.tasktimer;

import android.content.Context;

import java.util.List;

/**
 * 子任务计时中接管的对账入口（v1.13.0，用户定案：「一个任务只要设置了子任务，就默认直接
 * 开始这个子任务计时」——包括计时已经在跑、中途才加/改子任务的情形）。
 *
 * 触发：TaskStore 任何一次 save（主页展开区/大悬浮窗编辑、同步合并都算）都会经既有
 * ChangeListener 通知到 MainActivity / FloatingWindow / TimerService 三处，它们各自的回调里
 * 调一次 check()。check 只做便宜的对账：找到正在计时（或暂停中）的那个任务，拿它的最新
 * 子任务列表与 TimerState 里「当前队列所依据的快照」比对，一致就立刻返回；不一致才把任务
 * id 交给 TimerService.ACTION_SUB_RESYNC，由服务在主线程串行地重建队列（单一落点，避免
 * 多个回调并发改 TimerState）。重建只动 TimerState，不写 TaskStore，不会自触发循环。
 *
 * 跨设备：本机是镜像端（timer_state 归另一台设备）时直接跳过——拥有端会重建并把新队列
 * 经 timer_state 推过来，本机镜像跟随，不抢逻辑计时器的所有权。
 */
public final class SubtaskResync {

    private SubtaskResync() {}

    public static void check(Context c) {
        if (c == null) return;
        Context app = c.getApplicationContext();
        boolean running = TimerState.isRunning(app);
        boolean paused = !running && TimerState.isPaused(app);
        if (!running && !paused) return;
        String tid = running ? TimerState.taskId(app) : TimerState.pausedTaskId(app);
        if (tid == null || tid.isEmpty()) return;
        if (SbTimerSync.isRemoteOwned(app)) return;
        TaskItem t = new TaskStore(app).byId(tid);
        if (t == null) return;
        List<Subtask> cur = t.subtasks;
        List<Subtask> snap = TimerState.subSnapshot(app);
        boolean active = running ? TimerState.subActive(app) : TimerState.pausedSubActive(app);
        boolean has = cur != null && !cur.isEmpty();
        if (active == has && sameList(cur, snap)) return; // 队列与最新列表一致，无事可做
        TimerService.resyncSubtasks(app, tid);
    }

    /** 顺序、id、名称、分钟都一致才算同一版队列（改名也要让运行态跟上新名字）。 */
    static boolean sameList(List<Subtask> a, List<Subtask> b) {
        int na = a == null ? 0 : a.size();
        int nb = b == null ? 0 : b.size();
        if (na != nb) return false;
        for (int i = 0; i < na; i++) {
            Subtask x = a.get(i), y = b.get(i);
            if (!eq(x.id, y.id) || !eq(x.title, y.title) || x.minutes != y.minutes) return false;
        }
        return true;
    }

    private static boolean eq(String x, String y) {
        return (x == null ? "" : x).equals(y == null ? "" : y);
    }
}
