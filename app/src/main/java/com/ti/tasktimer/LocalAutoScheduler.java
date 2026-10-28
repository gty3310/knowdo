package com.ti.tasktimer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地自动排程（v1.23.0，用户定案的生效口径：新增与滚入自动排、挪动已有走提案卡）：
 * 今日任务（含滚入）且还没排进今日 schedule 的，由 App 自己按 duration×1.5
 * 填进今天剩下的空档——只填空档：已有块、已过去的时段一律不动、不重叠、
 * 不排过 22:30；排不下的不硬塞，留给时间账下方的点名行。
 * 填充顺序（v1.27.0 统一口径，自动填空档与快速重排共用 candidateOrder）：
 * 置顶组（≤15 分钟＋重要以上＋今天到期）→ 其余必做 → 今天到期的重要 →
 * 今天到期的次要 → 未到期的重要 → 未到期的次要；休息（leisure）是
 * 娱乐类，永不自动排。
 *
 * 分工：本类只有纯算法（plan）＋快照/触发脚手架；真正落盘走 SyncManager 的
 * sanction 写入口（autoFillSchedule），写前在那边回读云端最新 schedule 合并，
 * 避免与外部写入（Jarvis 在云端重排、用户在日历拖拽）互相覆盖。
 *
 * 触发点（MainActivity 接线）：昨日滚入完成后、新建/改期任务落到今天后（走
 * TaskStore 变更回调）、App 回前台发现今日有未排的必做时。runAsync 内部先用
 * 本地缓存做一次短路判断：没有未排必做就直接返回，不发网络请求；并发触发合并。
 */
public final class LocalAutoScheduler {
    public static final int DAY_END_MIN = DayAccount.DAY_END_MIN; // 22:30

    private LocalAutoScheduler() { }

    /** schedule 里的一块（分钟制，本地当天 0 点起算）。 */
    public static final class Block {
        public final String taskId;
        public final int startMin;
        public final int endMin;
        /** 用户亲手拖动/拖入定下的块（写入口 updateScheduleBlock/addScheduleBlock 打标）：
         *  「用户拖过的块固定」口径的落盘标记，快速重排绕开它。 */
        public final boolean manual;
        public Block(String taskId, int startMin, int endMin) {
            this(taskId, startMin, endMin, false);
        }
        public Block(String taskId, int startMin, int endMin, boolean manual) {
            this.taskId = taskId; this.startMin = startMin; this.endMin = endMin;
            this.manual = manual;
        }
    }

    /** 候选任务的不可变快照（在调用线程从 TaskStore 摘下，后台排程只读快照不碰活对象）。 */
    public static final class Candidate {
        public final String id;
        public final String title;
        public final int durationMinutes;
        public final String deadline;
        public final long createdAtMs;
        public final boolean must;
        public final int rank; // 优先级档位序（TaskItem.priorityRank：0 必做 1 重要 2 次要 3 休息）
        public final boolean doneToday;
        Candidate(TaskItem t) {
            id = t.id;
            title = t.title;
            durationMinutes = Math.max(1, t.durationMinutes);
            deadline = t.deadline;
            createdAtMs = t.createdAtMs;
            must = "must".equals(t.priority);
            rank = TaskItem.priorityRank(t.priority);
            doneToday = t.isDoneToday();
        }
    }

    public static final class Plan {
        public final List<Block> additions = new ArrayList<>();
        public final List<Candidate> overflow = new ArrayList<>();
        public boolean written; // 是否已落盘（离线/未登录时只算不写，written=false）
    }

    public static List<Candidate> snapshot(TaskStore store) {
        List<Candidate> out = new ArrayList<>();
        if (store == null) return out;
        for (TaskItem t : store.tasks) if (!t.deleted) out.add(new Candidate(t));
        return out;
    }

    /** "HH:mm" → 分钟；坏格式返回 -1。 */
    public static int parseHhmm(String hhmm) {
        if (hhmm == null) return -1;
        try {
            String[] p = hhmm.trim().split(":");
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            if (h < 0 || h > 24 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String hhmm(int min) {
        return String.format(java.util.Locale.US, "%02d:%02d", min / 60, min % 60);
    }

    /** 从 schedule JSON 摘块；date 不是今天（或无 schedule）返回空表。 */
    public static List<Block> blocksOf(JSONObject sch, String today) {
        List<Block> out = new ArrayList<>();
        if (sch == null || !today.equals(sch.optString("date", ""))) return out;
        JSONArray arr = sch.optJSONArray("blocks");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String tid = o.optString("taskId", "");
            int s = parseHhmm(o.optString("start", ""));
            int e = parseHhmm(o.optString("end", ""));
            if (tid.isEmpty() || s < 0 || e <= s) continue;
            out.add(new Block(tid, s, e, o.optBoolean("manual", false)));
        }
        return out;
    }

    /** 块表 → schedule JSON（按开始时间排序，写盘统一走这里）。 */
    public static JSONObject scheduleJson(String today, List<Block> blocks) throws Exception {
        List<Block> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt(b -> b.startMin));
        JSONObject sch = new JSONObject();
        sch.put("date", today);
        JSONArray arr = new JSONArray();
        for (Block b : sorted) {
            JSONObject o = new JSONObject();
            o.put("taskId", b.taskId);
            o.put("start", hhmm(b.startMin));
            o.put("end", hhmm(b.endMin));
            if (b.manual) o.put("manual", true);
            arr.put(o);
        }
        sch.put("blocks", arr);
        return sch;
    }

    /** 到期＝deadline ≤ 逻辑今天 logicToday（含等于今天）；无 deadline 不算到期。 */
    private static boolean isDue(Candidate cd, String logicToday) {
        return cd.deadline != null && logicToday != null
                && cd.deadline.compareTo(logicToday) <= 0;
    }

    /**
     * 排程组序（v1.27.0 统一口径，自动填空档与快速重排共用，禁止两套）：
     * 0 置顶组（时长 ≤15 分钟＋档位重要以上＋今天到期）／1 其余必做／
     * 2 今天到期的重要／3 今天到期的次要／4 未到期的重要／5 未到期的次要。
     * （v1.26.2 时是必做无条件第一；v1.27.0 起置顶组按用户新规矩压过其余必做。）
     */
    private static int schedTier(Candidate cd, String logicToday) {
        boolean due = isDue(cd, logicToday);
        if (due && cd.rank <= 1 && cd.durationMinutes <= 15) return 0;
        if (cd.rank == 0) return 1;
        if (due) return cd.rank == 1 ? 2 : 3;
        return cd.rank == 1 ? 4 : 5;
    }

    /**
     * 唯一的候选排序（v1.27.0）：自动排程 plan()、快速重排与（经 AiReschedule
     * 转述的）AI 重排基准共用这一份。组序见 schedTier；置顶组内按时长升序，
     * 其余组内 deadline 早先（无 deadline 最后）、再按创建时间。
     */
    public static Comparator<Candidate> candidateOrder(final String logicToday) {
        return (a, b) -> {
            int ta = schedTier(a, logicToday), tb = schedTier(b, logicToday);
            if (ta != tb) return Integer.compare(ta, tb);
            if (ta == 0 && a.durationMinutes != b.durationMinutes) {
                return Integer.compare(a.durationMinutes, b.durationMinutes);
            }
            String da = a.deadline, db = b.deadline;
            if (da == null && db != null) return 1;
            if (da != null && db == null) return -1;
            if (da != null && !da.equals(db)) return da.compareTo(db);
            return Long.compare(a.createdAtMs, b.createdAtMs);
        };
    }

    /** 排序规则的原文（给 AI 重排做基准逻辑时整段引用，与 candidateOrder 同源，不许另行改写）。 */
    public static String rulesText() {
        return "先排置顶组：时长不超过 15 分钟、优先级是必做或重要、且截止日期是今天（或已逾期）的任务，"
                + "组内按时长从短到长；然后排其余必做；然后排今天到期的重要；然后排今天到期的次要；"
                + "然后排还没到期的重要；最后排还没到期的次要。同一组里截止早的先排、没有截止日的最后、"
                + "再按创建时间先后。每件任务按计划时长×1.5 占时间，从现在起依次排到 22:30 为止；"
                + "休息（leisure）是娱乐类，永不排进日程。";
    }

    /** 块与占用区间是否重叠（[s,e) 半开区间口径）。 */
    public static boolean overlaps(Block b, List<int[]> blocked) {
        if (blocked == null) return false;
        for (int[] iv : blocked) {
            if (b.startMin < iv[1] && iv[0] < b.endMin) return true;
        }
        return false;
    }

    /** 从块表里滤掉与占用区间重叠的块（AI 重排/聊天排程卡的落盘前校验共用，v1.28.0 feature 9）。 */
    public static List<Block> filterBlocked(List<Block> blocks, List<int[]> blocked) {
        List<Block> out = new ArrayList<>();
        if (blocks == null) return out;
        for (Block b : blocks) if (!overlaps(b, blocked)) out.add(b);
        return out;
    }

    /**
     * 纯算法：把未排候选按 ×1.5 填进 [nowMin, 22:30] 扣掉已有块后的空档。
     * 候选顺序走 candidateOrder（v1.27.0 统一口径，见 schedTier）。
     * 已有块原样视为占用，本方法从不产出与已有块重叠、或开始时间早于 nowMin 的块。
     */
    public static Plan plan(List<Candidate> cands, List<Block> existing, int nowMin,
                            String logicToday) {
        return plan(cands, existing, nowMin, logicToday, null);
    }

    /**
     * 带硬约束的版本（v1.28.0 feature 9）：blocked 是今天必须避开的墙钟区间
     * （锁时段＋睡眠段，DayAvail.blockedWall 给出），与已有块一样视为占用，
     * 但不产生块、不进 scheduledIds。本地自动排与快速重排都走这一份。
     */
    public static Plan plan(List<Candidate> cands, List<Block> existing, int nowMin,
                            String logicToday, List<int[]> blocked) {
        Plan plan = new Plan();
        Set<String> scheduledIds = new HashSet<>();
        List<int[]> busy = new ArrayList<>();
        for (Block b : existing) {
            scheduledIds.add(b.taskId);
            busy.add(new int[]{b.startMin, b.endMin});
        }
        if (blocked != null) {
            for (int[] iv : blocked) {
                if (iv != null && iv.length >= 2 && iv[1] > iv[0]) {
                    busy.add(new int[]{iv[0], iv[1]});
                }
            }
        }
        List<Candidate> todo = new ArrayList<>();
        for (Candidate cd : cands) {
            if (cd.rank >= 3 || cd.doneToday) continue; // 休息档永不自动排
            if (scheduledIds.contains(cd.id)) continue;
            todo.add(cd);
        }
        todo.sort(candidateOrder(logicToday));
        // 空档表：[nowMin, DAY_END] 扣掉 busy
        busy.sort(Comparator.comparingInt(iv -> iv[0]));
        List<int[]> free = new ArrayList<>();
        int cursor = nowMin;
        for (int[] iv : busy) {
            if (iv[1] <= cursor) continue;
            if (iv[0] > cursor) free.add(new int[]{cursor, Math.min(iv[0], DAY_END_MIN)});
            cursor = Math.max(cursor, iv[1]);
            if (cursor >= DAY_END_MIN) break;
        }
        if (cursor < DAY_END_MIN) free.add(new int[]{cursor, DAY_END_MIN});

        for (Candidate cd : todo) {
            int need = DayAccount.buffered(cd.durationMinutes);
            boolean placed = false;
            for (int[] gap : free) {
                if (gap[1] - gap[0] >= need) {
                    plan.additions.add(new Block(cd.id, gap[0], gap[0] + need));
                    gap[0] += need;
                    placed = true;
                    break;
                }
            }
            if (!placed) plan.overflow.add(cd);
        }
        return plan;
    }

    // ---------------- 快速重排（v1.27.0：纯算法、点下立即生效，与 AI 重排并存） ----------------

    /** 快速重排的计算结果：锁定块（原样保留）＋新排块＋排不下点名＋最终全表。 */
    public static final class RescheduleOutcome {
        public String error; // null＝算好了（落盘在 SbSync 侧）
        public JSONObject previousSchedule; // 生效前的 schedule 原文（撤销快照来源）
        public final List<Block> locked = new ArrayList<>();
        public final List<Block> additions = new ArrayList<>();
        public final List<Candidate> overflow = new ArrayList<>();
        public List<Block> merged() {
            List<Block> out = new ArrayList<>(locked);
            out.addAll(additions);
            out.sort(Comparator.comparingInt(b -> b.startMin));
            return out;
        }
    }

    /**
     * 快速重排的纯算法：把今天未锁定的计划块全部拆下、任务回到候选池，与还没排的
     * 任务一起按 candidateOrder 从 nowMin 起重新打包（×1.5、绕开锁定块、到 22:30）。
     * 锁定块＝正在计时的任务的块／已完成任务的块／用户亲手拖定（manual）的块／
     * 任务已不存在、无从判断的块。正在计时的任务本身不进候选池（它已经在做了）。
     * v1.27.1 修正（用户点名）：没做完的任务，其计划块哪怕结束时间已经过了，
     * 也不许留在过去——块收走、任务回候选池从此刻重排（已执行的部分在实际栏
     * 里、不靠计划块承载，不受影响）；真正执行的事实只由 doneToday 把关。
     * 真正的打包走 plan()（同一排序函数、同一打包器）。
     */
    public static RescheduleOutcome computeReschedule(List<Candidate> cands, List<Block> existing,
                                                        int nowMin, String logicToday,
                                                        String runningTaskId) {
        return computeReschedule(cands, existing, nowMin, logicToday, runningTaskId, null);
    }

    /** 带锁时/睡眠硬约束的版本（v1.28.0 feature 9）：blocked 透传给 plan()。 */
    public static RescheduleOutcome computeReschedule(List<Candidate> cands, List<Block> existing,
                                                        int nowMin, String logicToday,
                                                        String runningTaskId, List<int[]> blocked) {
        RescheduleOutcome o = new RescheduleOutcome();
        java.util.Map<String, Candidate> byId = new java.util.HashMap<>();
        for (Candidate cd : cands) byId.put(cd.id, cd);
        Set<String> lockedIds = new HashSet<>();
        for (Block b : existing) {
            Candidate cd = byId.get(b.taskId);
            boolean lock = b.manual
                    || (runningTaskId != null && runningTaskId.equals(b.taskId))
                    || cd == null
                    || cd.doneToday;
            if (lock) {
                o.locked.add(b);
                lockedIds.add(b.taskId);
            }
        }
        List<Candidate> pool = new ArrayList<>();
        for (Candidate cd : cands) {
            if (runningTaskId != null && runningTaskId.equals(cd.id)) continue;
            if (lockedIds.contains(cd.id)) continue;
            pool.add(cd);
        }
        Plan p = plan(pool, o.locked, nowMin, logicToday, blocked);
        o.additions.addAll(p.additions);
        o.overflow.addAll(p.overflow);
        return o;
    }

    /**
     * 聊天排程提案点「应用」时的合并（brief 四节口径：先回读合并、只按卡上内容落盘）：
     * 以刚回读到的最新 schedule 为底——其中已经过去的块、已完成任务的块是既成事实，
     * 原样保留（AI 也不许动它们）；其余位置按卡上的块落盘。卡上若重复给了保留块的
     * 任务，以保留块为准、卡上那块丢弃。返回值按开始时间排序。
     */
    public static List<Block> mergeProposal(List<Block> freshBase, List<Block> cardBlocks,
                                            TaskStore store, int nowMin) {
        List<Block> out = new ArrayList<>();
        Set<String> keptIds = new HashSet<>();
        for (Block b : freshBase) {
            TaskItem t = store == null ? null : store.byId(b.taskId);
            boolean done = t != null && t.isDoneToday();
            if (b.endMin <= nowMin || done) {
                out.add(b);
                keptIds.add(b.taskId);
            }
        }
        for (Block b : cardBlocks) {
            if (keptIds.contains(b.taskId)) continue;
            out.add(b);
        }
        out.sort(Comparator.comparingInt(b -> b.startMin));
        return out;
    }

    /** 用本地缓存的 schedule 现算一份计划（UI 点名行与触发短路判断用，不发网络）。
     *  v1.28.0 feature 9：同样避开锁时段与睡眠段，与落盘口径一致。 */
    public static Plan computeLocal(Context c, TaskStore store) {
        List<Block> base = blocksOf(SyncManager.latestSchedule(c), TaskItem.today());
        String logicToday = Rollover.logicalToday(c);
        return plan(snapshot(store), base, DayAccount.nowMinutes(), logicToday,
                DayAvail.blockedWall(c, logicToday));
    }

    /** 还有没排进今天的可填任务吗（必做/重要/次要，本地口径，触发短路用）。 */
    public static boolean hasUnscheduledMust(Context c, TaskStore store) {
        Plan p = computeLocal(c, store);
        return !p.additions.isEmpty() || !p.overflow.isEmpty();
    }

    // ---------------- 触发脚手架（并发合并） ----------------
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * 异步跑一次自动排程：先本地短路（没有未排必做直接回调返回），
     * 再走 SyncManager 的写入口（内部回读云端合并）。onDone 在主线程回调（可为 null）。
     */
    public static void runAsync(final Context c, final TaskStore store, final Runnable onDone) {
        if (!hasUnscheduledMust(c, store)) {
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            PENDING.set(true);
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        final Context app = c.getApplicationContext();
        final List<Candidate> snap = snapshot(store);
        new Thread(() -> {
            try {
                SyncManager.autoFillSchedule(app, snap, "KnowDo App（本地自动排程）");
            } catch (Exception e) {
                // 两个实现内部都已吞异常回落，这里兜的是计划外的漏网；
                // 口径不变，只在测试者模式下亮出（v1.24.1）
                ErrorReporter.report(app, Strings.en() ? "Auto-schedule" : "自动排程", 0,
                        ErrorReporter.summarize(e));
            }
            RUNNING.set(false);
            MAIN.post(() -> {
                if (onDone != null) onDone.run();
                if (PENDING.compareAndSet(true, false)) runAsync(c, store, null);
            });
        }).start();
    }
}
