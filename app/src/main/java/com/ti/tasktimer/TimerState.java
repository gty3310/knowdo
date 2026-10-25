package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

/** 验证版的状态存储：只存绝对时间点，剩余时间永远现算（进程被杀也不漂移）。 */
public final class TimerState {
    private static final String PREF = "timer_state";

    private TimerState() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void start(Context c, int totalSec, String taskId, String taskTitle) {
        long now = System.currentTimeMillis();
        sp(c).edit()
            .remove("pause_expired").remove("pause_expired_at") // 新一轮计时：旧的到期标记作废（v1.19.0）
            .putBoolean("running", true)
            .putLong("startedAt", now)
            .putLong("halfAt", now + totalSec * 500L)
            .putLong("endAt", now + totalSec * 1000L)
            .putInt("totalSec", totalSec)
            .putString("taskId", taskId == null ? "" : taskId)
            .putString("taskTitle", taskTitle == null ? "" : taskTitle)
            .putBoolean("halfFired", false)
            .putBoolean("endFired", false)
            .remove("sub_active").remove("sub_index").remove("sub_count")
            .remove("sub_id").remove("sub_title").remove("sub_end").remove("sub_fired")
            .remove("sub_snap")
            .apply();
    }

    /**
     * 按今日已用抵扣开始（v1.21.1）：虚拟起点＝now−已用秒，endAt＝now＋剩余；
     * halfFired 预标沿用 startResumed 的口径（已用过半则过半页不再弹）。
     * 与 start() 的区别仅在时间锚点与 halfFired；暂停槽不在这里动——同任务的暂停槽
     * 由 TimerService.ACTION_START 既有逻辑在调用前清掉，别的任务的槽原样不动。
     * 子任务任务不走这里（调用方已拦截），故同样清子任务运行态。
     */
    public static void startWithElapsed(Context c, int totalSec, int remainSec,
                                        String taskId, String taskTitle) {
        long now = System.currentTimeMillis();
        int elapsedSec = Math.max(0, totalSec - remainSec);
        sp(c).edit()
            .remove("pause_expired").remove("pause_expired_at")
            .putBoolean("running", true)
            .putLong("startedAt", now - elapsedSec * 1000L)
            .putLong("halfAt", now - elapsedSec * 1000L + totalSec * 500L)
            .putLong("endAt", now + remainSec * 1000L)
            .putInt("totalSec", totalSec)
            .putString("taskId", taskId == null ? "" : taskId)
            .putString("taskTitle", taskTitle == null ? "" : taskTitle)
            .putBoolean("halfFired", elapsedSec * 2 >= totalSec)
            .putBoolean("endFired", false)
            .remove("sub_active").remove("sub_index").remove("sub_count")
            .remove("sub_id").remove("sub_title").remove("sub_end").remove("sub_fired")
            .remove("sub_snap")
            .apply();
    }

    public static String taskId(Context c) { return sp(c).getString("taskId", ""); }
    public static String taskTitle(Context c) {
        String t = sp(c).getString("taskTitle", "");
        return t == null || t.isEmpty() ? "当前任务" : t;
    }

    public static void extend(Context c, int addSec) {
        SharedPreferences p = sp(c);
        long base = Math.max(System.currentTimeMillis(), p.getLong("endAt", 0));
        SharedPreferences.Editor e = p.edit()
            .putLong("endAt", base + addSec * 1000L)
            .putInt("totalSec", p.getInt("totalSec", 0) + addSec)
            .putBoolean("endFired", false)
            .putBoolean("running", true);
        // 子任务模式：加时同时顺延当前子任务的边界（给当前子任务加时即走这里）
        if (p.getBoolean("sub_active", false)) {
            e.putLong("sub_end", p.getLong("sub_end", 0) + addSec * 1000L);
        }
        e.apply();
    }

    public static void stop(Context c) {
        sp(c).edit().putBoolean("running", false)
            .remove("sub_active").remove("sub_index").remove("sub_count")
            .remove("sub_id").remove("sub_title").remove("sub_end").remove("sub_fired")
            .apply();
    }

    // ---------- 子任务运行态（v1.11.0） ----------
    // 当前子任务的序号/名称/边界绝对时间；边界一到由 TimerService 弹子任务检查页（最后一个
    // 子任务的边界即任务结束点，走原结束页）。子任务模式下不触发任务级过半检查。
    public static void beginSubtasks(Context c, TaskItem t) {
        if (t == null || !t.hasSubtasks()) return;
        Subtask s = t.subtasks.get(0);
        long now = System.currentTimeMillis();
        sp(c).edit()
            .putBoolean("sub_active", true)
            .putInt("sub_index", 0)
            .putInt("sub_count", t.subtasks.size())
            .putString("sub_id", s.id)
            .putString("sub_title", s.title == null ? "" : s.title)
            .putLong("sub_end", now + Math.max(1, s.minutes) * 60_000L)
            .putBoolean("sub_fired", false)
            .putBoolean("halfFired", true) // 子任务模式：检查点只在子任务边界
            .apply();
    }

    /** 推进到第 index 个子任务：从现在起给它完整的 minutes。 */
    public static void advanceSub(Context c, int index, Subtask s, int count) {
        long now = System.currentTimeMillis();
        sp(c).edit()
            .putBoolean("sub_active", true)
            .putInt("sub_index", index)
            .putInt("sub_count", count)
            .putString("sub_id", s.id)
            .putString("sub_title", s.title == null ? "" : s.title)
            .putLong("sub_end", now + Math.max(1, s.minutes) * 60_000L)
            .putBoolean("sub_fired", false)
            .apply();
    }

    /** 跨设备镜像：远端子任务状态落到本机（index<0 表示远端任务没有子任务，清掉）。 */
    public static void applyMirrorSub(Context c, int index, String subId, String subTitle,
                                      int count, long subEndMs) {
        SharedPreferences.Editor e = sp(c).edit();
        if (index < 0) {
            e.remove("sub_active").remove("sub_index").remove("sub_count")
             .remove("sub_id").remove("sub_title").remove("sub_end").remove("sub_fired");
        } else {
            e.putBoolean("sub_active", true)
             .putInt("sub_index", index)
             .putInt("sub_count", count)
             .putString("sub_id", subId == null ? "" : subId)
             .putString("sub_title", subTitle == null ? "" : subTitle)
             .putLong("sub_end", subEndMs)
             .putBoolean("sub_fired", false)
             .putBoolean("halfFired", true);
        }
        e.apply();
    }

    public static boolean subActive(Context c) { return sp(c).getBoolean("sub_active", false); }
    public static int subIndex(Context c) { return sp(c).getInt("sub_index", 0); }
    public static int subCount(Context c) { return sp(c).getInt("sub_count", 0); }
    public static String subTitle(Context c) {
        String s = sp(c).getString("sub_title", "");
        return s == null ? "" : s;
    }
    public static String subId(Context c) {
        String s = sp(c).getString("sub_id", "");
        return s == null ? "" : s;
    }
    public static long subEndAt(Context c) { return sp(c).getLong("sub_end", 0); }
    public static boolean subFired(Context c) { return sp(c).getBoolean("sub_fired", false); }
    public static void markSubFired(Context c) { sp(c).edit().putBoolean("sub_fired", true).apply(); }

    // ---------- 子任务队列快照与运行中重建（v1.13.0） ----------
    // sub_snap 存「当前队列是按哪一版子任务列表建的」（[{id,title,minutes}] 的 JSON 串）：
    // TaskStore 一有变更，SubtaskResync 拿最新列表与快照比对，不同才让 TimerService 重建队列，
    // 实现「计时中加/改子任务立即接管」。快照随新一轮计时（start）清掉；暂停走 stop() 不清——
    // 暂停期间编辑子任务同样要靠它对账，恢复时由 TimerService 按最新列表重写。

    public static void setSubSnapshot(Context c, java.util.List<Subtask> subs) {
        if (subs == null || subs.isEmpty()) {
            sp(c).edit().remove("sub_snap").apply();
            return;
        }
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Subtask s : subs) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("id", s.id == null ? "" : s.id);
                o.put("title", s.title == null ? "" : s.title);
                o.put("minutes", Math.max(1, s.minutes));
                arr.put(o);
            }
            sp(c).edit().putString("sub_snap", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static java.util.List<Subtask> subSnapshot(Context c) {
        java.util.List<Subtask> out = new java.util.ArrayList<>();
        String raw = sp(c).getString("sub_snap", null);
        if (raw == null || raw.isEmpty()) return out;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Subtask s = new Subtask();
                s.id = o.optString("id", "");
                s.title = o.optString("title", "");
                s.minutes = Math.max(1, o.optInt("minutes", 5));
                out.add(s);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /**
     * 队列重建落点（运行中）：一次性写入子任务运行态＋任务级总时长/结束点。
     * startedAt 按 endAt − totalSec 顺推，保证跨设备镜像算出的结束点与本机一致；
     * 调用方用「新旧队列剩余之差」平移 endAt/totalSec 时 startedAt 自然不变，检查页停留
     * 吃掉的时间（dwell）不会被重建悄悄还回来。
     */
    public static void applyQueueState(Context c, int index, String subId, String subTitle,
                                       int count, long subEndMs, long endAtMs, int totalSec) {
        sp(c).edit()
            .putBoolean("running", true)
            .putBoolean("sub_active", true)
            .putInt("sub_index", index)
            .putInt("sub_count", count)
            .putString("sub_id", subId == null ? "" : subId)
            .putString("sub_title", subTitle == null ? "" : subTitle)
            .putLong("sub_end", subEndMs)
            .putBoolean("sub_fired", false)
            .putLong("endAt", endAtMs)
            .putInt("totalSec", totalSec)
            .putLong("startedAt", endAtMs - totalSec * 1000L)
            .putLong("halfAt", endAtMs - totalSec * 500L)
            .putBoolean("halfFired", true) // 子任务模式：检查点只在子任务边界
            .putBoolean("endFired", false)
            .apply();
    }

    /** 退出子任务模式（运行中）：只清子任务运行态，任务级时间点由调用方决定。 */
    public static void clearSubRunState(Context c) {
        sp(c).edit().remove("sub_active").remove("sub_index").remove("sub_count")
            .remove("sub_id").remove("sub_title").remove("sub_end").remove("sub_fired")
            .apply();
    }

    /** 任务级收尾：结束点设到现在，下一个 tick 即走任务结束检查页（无子任务时的原有逻辑）。 */
    public static void finishNow(Context c) {
        sp(c).edit().putLong("endAt", System.currentTimeMillis())
            .putBoolean("endFired", false).apply();
    }

    /** 子任务检查页在显（sub_fired）时的最小同步：只更新计数，不动正在等确认的边界。 */
    public static void touchSubCount(Context c, int count) {
        sp(c).edit().putInt("sub_count", count).apply();
    }

    // ---------- 暂停 / 继续（全局只留一个暂停槽） ----------
    public static void setPaused(Context c, long remainMs) {
        SharedPreferences p = sp(c);
        SharedPreferences.Editor e = p.edit()
            .putString("p_taskId", p.getString("taskId", ""))
            .putString("p_title", p.getString("taskTitle", ""))
            .putInt("p_totalSec", p.getInt("totalSec", 0))
            .putLong("p_remainMs", Math.max(0, remainMs))
            .remove("p_reason").remove("p_emoji").remove("p_silent"); // 新暂停从无目的开始，卡选完再写
        // 子任务进度一并冻进暂停槽：恢复时按剩余继续当前子任务
        if (p.getBoolean("sub_active", false)) {
            e.putBoolean("p_subActive", true)
             .putInt("p_subIndex", p.getInt("sub_index", 0))
             .putInt("p_subCount", p.getInt("sub_count", 0))
             .putString("p_subId", p.getString("sub_id", ""))
             .putString("p_subTitle", p.getString("sub_title", ""))
             .putLong("p_subRemainMs", Math.max(0, p.getLong("sub_end", 0) - System.currentTimeMillis()));
        } else {
            e.remove("p_subActive").remove("p_subIndex").remove("p_subCount")
             .remove("p_subId").remove("p_subTitle").remove("p_subRemainMs");
        }
        e.apply();
    }

    public static void clearPaused(Context c) {
        sp(c).edit().remove("pause_expired").remove("pause_expired_at")
            .remove("p_taskId").remove("p_title").remove("p_totalSec").remove("p_remainMs")
            .remove("p_reason").remove("p_emoji").remove("p_silent")
            .remove("p_subActive").remove("p_subIndex").remove("p_subCount")
            .remove("p_subId").remove("p_subTitle").remove("p_subRemainMs")
            .apply();
    }

    // 暂停槽里的子任务快照（恢复/镜像用）
    public static boolean pausedSubActive(Context c) { return sp(c).getBoolean("p_subActive", false); }
    public static String pausedSubId(Context c) {
        String s = sp(c).getString("p_subId", "");
        return s == null ? "" : s;
    }
    public static int pausedSubIndex(Context c) { return sp(c).getInt("p_subIndex", 0); }
    public static int pausedSubCount(Context c) { return sp(c).getInt("p_subCount", 0); }
    public static String pausedSubTitle(Context c) {
        String s = sp(c).getString("p_subTitle", "");
        return s == null ? "" : s;
    }
    public static long pausedSubRemainMs(Context c) { return sp(c).getLong("p_subRemainMs", 0); }

    /**
     * 暂停中子任务队列重建落点（v1.13.0）：改写暂停槽的子任务快照＋任务级剩余/总时长。
     * 只动这几处键，暂停目的三件套（p_reason/p_emoji/p_silent）与叫回提醒不受影响；
     * 恢复计时（startResumed）读到的就是新队列，恢复后按新队列走。
     */
    public static void rewritePausedSub(Context c, int index, String subId, String subTitle,
                                        int count, long subRemainMs, long taskRemainMs, int totalSec) {
        sp(c).edit()
            .putBoolean("p_subActive", true)
            .putInt("p_subIndex", index)
            .putInt("p_subCount", count)
            .putString("p_subId", subId == null ? "" : subId)
            .putString("p_subTitle", subTitle == null ? "" : subTitle)
            .putLong("p_subRemainMs", Math.max(0, subRemainMs))
            .putLong("p_remainMs", Math.max(0, taskRemainMs))
            .putInt("p_totalSec", totalSec)
            .apply();
    }

    /** 暂停槽退出子任务模式：恢复后按任务级剩余走完（p_remainMs/p_totalSec 不动）。 */
    public static void clearPausedSub(Context c) {
        sp(c).edit().remove("p_subActive").remove("p_subIndex").remove("p_subCount")
            .remove("p_subId").remove("p_subTitle").remove("p_subRemainMs")
            .apply();
    }

    // ---------- 暂停目的（v1.10.0 预留 p_reason，v1.11.0 暂停模式正式启用） ----------
    // 暂停卡选的目的三件套，随暂停槽同生同灭（setPaused/setPausedRemote 新暂停时清、
    // clearPaused/startResumed 时一并清），并镜像进 Supabase timer_state（老端忽略未知字段）：
    // p_reason＝目的显示名（预设名或自定义文本）、p_emoji＝目的 emoji、p_silent＝静音模式。
    public static void setPauseReason(Context c, String reason) {
        SharedPreferences p = sp(c);
        if (reason == null) p.edit().remove("p_reason").apply();
        else p.edit().putString("p_reason", reason).apply();
    }

    /** 一次写入暂停目的三件套（暂停卡确认、EDIT「换个目的」都走这里）。 */
    public static void setPauseMeta(Context c, String reason, String emoji, boolean silent) {
        SharedPreferences.Editor e = sp(c).edit();
        if (reason == null) e.remove("p_reason"); else e.putString("p_reason", reason);
        if (emoji == null) e.remove("p_emoji"); else e.putString("p_emoji", emoji);
        e.putBoolean("p_silent", silent);
        e.apply();
    }

    /** 当前暂停槽的目的；没设过（或旧暂停）返回 null。 */
    public static String pauseReason(Context c) {
        String s = sp(c).getString("p_reason", null);
        return s == null || s.isEmpty() ? null : s;
    }

    public static String pauseEmoji(Context c) {
        String s = sp(c).getString("p_emoji", "");
        return s == null ? "" : s;
    }

    public static boolean pauseSilent(Context c) { return sp(c).getBoolean("p_silent", false); }

    public static String pausedTaskId(Context c) {
        String s = sp(c).getString("p_taskId", "");
        return s == null ? "" : s;
    }

    public static boolean isPaused(Context c) { return !pausedTaskId(c).isEmpty(); }
    public static long pausedRemainMs(Context c) { return sp(c).getLong("p_remainMs", 0); }
    public static int pausedTotalSec(Context c) { return sp(c).getInt("p_totalSec", 0); }

    public static String pausedTitle(Context c) {
        String s = sp(c).getString("p_title", "");
        return s == null ? "" : s;
    }

    /** 继续：按原总时长与剩余秒数重建绝对时间点；过半标记沿用暂停前的状态（或已过半则视为已触发）。 */
    public static void startResumed(Context c, int totalSec, int remainSec, String taskId, String taskTitle) {
        SharedPreferences p = sp(c);
        long now = System.currentTimeMillis();
        long elapsedSec = Math.max(0, totalSec - remainSec);
        boolean half = p.getBoolean("halfFired", false) || elapsedSec * 2 >= totalSec;
        p.edit()
            .remove("pause_expired").remove("pause_expired_at") // 恢复计时：到期标记消费掉（v1.19.0）
            .putBoolean("running", true)
            .putLong("startedAt", now - elapsedSec * 1000L)
            .putLong("halfAt", now - elapsedSec * 1000L + totalSec * 500L)
            .putLong("endAt", now + remainSec * 1000L)
            .putInt("totalSec", totalSec)
            .putString("taskId", taskId == null ? "" : taskId)
            .putString("taskTitle", taskTitle == null ? "" : taskTitle)
            .putBoolean("halfFired", half)
            .putBoolean("endFired", false)
            .remove("p_taskId").remove("p_title").remove("p_totalSec").remove("p_remainMs")
            .remove("p_reason").remove("p_emoji").remove("p_silent")
            .apply();
        // 子任务进度恢复：暂停槽里冻着的子任务按剩余继续（边界重算为 now＋冻存剩余）
        if (p.getBoolean("p_subActive", false)) {
            sp(c).edit()
                .putBoolean("sub_active", true)
                .putInt("sub_index", p.getInt("p_subIndex", 0))
                .putInt("sub_count", p.getInt("p_subCount", 0))
                .putString("sub_id", p.getString("p_subId", ""))
                .putString("sub_title", p.getString("p_subTitle", ""))
                .putLong("sub_end", now + Math.max(0, p.getLong("p_subRemainMs", 0)))
                .putBoolean("sub_fired", false)
                .putBoolean("halfFired", true)
                .remove("p_subActive").remove("p_subIndex").remove("p_subCount")
                .remove("p_subId").remove("p_subTitle").remove("p_subRemainMs")
                .apply();
        }
    }

    /**
     * 跨设备镜像（v1.7.0）：另一台设备正在跑的同一倒计时在本机的落点。
     * startedAt 存「虚拟起点」＝真实结束点 − 总时长，本机的 halfAt/endAt 现算口径不变。
     */
    public static void mirrorStart(Context c, int totalSec, long startedAtMs, long endAtMs,
                                   String taskId, String taskTitle, boolean halfFired) {
        sp(c).edit()
            .putBoolean("running", true)
            .putLong("startedAt", startedAtMs)
            .putLong("halfAt", startedAtMs + totalSec * 500L)
            .putLong("endAt", endAtMs)
            .putInt("totalSec", totalSec)
            .putString("taskId", taskId == null ? "" : taskId)
            .putString("taskTitle", taskTitle == null ? "" : taskTitle)
            .putBoolean("halfFired", halfFired)
            .putBoolean("endFired", false)
            .apply();
    }

    /** 远端暂停的镜像：把暂停槽设成任意任务（setPaused 是从本机运行态转存的，不适用）。 */
    public static void setPausedRemote(Context c, String taskId, String title, int totalSec, long remainMs) {
        setPausedRemote(c, taskId, title, totalSec, remainMs,
                -1, "", 0, 0, null, null, false);
    }

    /** 远端暂停镜像（完整版）：带子任务快照与暂停目的三件套（远端没带时按无处理，老端兼容）。 */
    public static void setPausedRemote(Context c, String taskId, String title, int totalSec, long remainMs,
                                       int subIndex, String subTitle, int subCount, long subRemainMs,
                                       String reason, String emoji, boolean silent) {
        SharedPreferences.Editor e = sp(c).edit()
            .remove("pause_expired").remove("pause_expired_at") // 远端新暂停镜像：旧到期标记作废（v1.19.0；镜像暂停口径本身不变）
            .putString("p_taskId", taskId == null ? "" : taskId)
            .putString("p_title", title == null ? "" : title)
            .putInt("p_totalSec", totalSec)
            .putLong("p_remainMs", Math.max(0, remainMs));
        if (reason == null) e.remove("p_reason"); else e.putString("p_reason", reason);
        if (emoji == null) e.remove("p_emoji"); else e.putString("p_emoji", emoji);
        e.putBoolean("p_silent", silent);
        if (subIndex >= 0) {
            e.putBoolean("p_subActive", true)
             .putInt("p_subIndex", subIndex)
             .putInt("p_subCount", subCount)
             .putString("p_subTitle", subTitle == null ? "" : subTitle)
             .putLong("p_subRemainMs", Math.max(0, subRemainMs));
        } else {
            e.remove("p_subActive").remove("p_subIndex").remove("p_subCount")
             .remove("p_subId").remove("p_subTitle").remove("p_subRemainMs");
        }
        e.apply();
    }

    public static boolean isRunning(Context c) { return sp(c).getBoolean("running", false); }
    public static long startedAt(Context c) { return sp(c).getLong("startedAt", 0); }
    public static long endAt(Context c) { return sp(c).getLong("endAt", 0); }
    public static long halfAt(Context c) { return sp(c).getLong("halfAt", 0); }
    public static int totalSec(Context c) { return sp(c).getInt("totalSec", 0); }
    public static boolean halfFired(Context c) { return sp(c).getBoolean("halfFired", false); }
    public static boolean endFired(Context c) { return sp(c).getBoolean("endFired", false); }

    public static void markHalfFired(Context c) { sp(c).edit().putBoolean("halfFired", true).apply(); }
    public static void markEndFired(Context c) { sp(c).edit().putBoolean("endFired", true).apply(); }

    // ---------- 暂停已到期标记（v1.19.0，根因修复） ----------
    // 背景：旧实现里 ResumeReceiver 到点把 r_at 清零后，IdleGuard 把「暂停槽还在＋r_at==0」
    // 恒判为无期限暂停生效中，空档永不成立、催促周期永不启动。现在普通（非静音）暂停
    // 到点时置位本标记：暂停槽仍在（任务可继续），但状态机按「已到期＝空档」处理，
    // 空档起算点＝到期时刻（pause_expired_at）。清零时机：开始计时（start）、恢复计时
    // （startResumed）、清除暂停槽（clearPaused）、「再歇一会」写入新叫回时间（setRemind）。
    // 静音暂停不走这里（仍走 SilentResume 亮屏链，不进出声催促）。
    public static void setPauseExpired(Context c, long expiredAtMs) {
        sp(c).edit().putBoolean("pause_expired", true)
            .putLong("pause_expired_at", expiredAtMs).apply();
    }

    public static boolean pauseExpired(Context c) { return sp(c).getBoolean("pause_expired", false); }

    /** 到期时刻（空档锚点）；未置位时为 0。 */
    public static long pauseExpiredAt(Context c) { return sp(c).getLong("pause_expired_at", 0); }

    public static void clearPauseExpired(Context c) {
        sp(c).edit().remove("pause_expired").remove("pause_expired_at").apply();
    }

    // ---------- 暂停提醒（暂停时约好多久后叫回来） ----------
    public static void setRemind(Context c, String taskId, long atMs) {
        // 「再歇一会」写入新的叫回时间＝重新进入定时暂停，到期标记清零（v1.19.0）
        sp(c).edit().remove("pause_expired").remove("pause_expired_at")
            .putString("r_taskId", taskId == null ? "" : taskId).putLong("r_at", atMs).apply();
    }

    public static void clearRemind(Context c) {
        sp(c).edit().remove("r_taskId").remove("r_at")
            .remove("sn_active").remove("sn_taskId").apply();
    }

    // ---------- 静音暂停的亮屏提醒链（v1.11.0） ----------
    // 静音暂停到点后不弹全屏：只亮屏＋通知，无反馈每 5 分钟重复，直到用户点开处理。
    // sn_active 标记链是否在跑；ResumeReceiver 静音分支置位，处理/恢复/清除提醒时清掉。
    public static void setSilentNudge(Context c, String taskId) {
        sp(c).edit().putBoolean("sn_active", true)
            .putString("sn_taskId", taskId == null ? "" : taskId).apply();
    }

    public static boolean silentNudgeActive(Context c) { return sp(c).getBoolean("sn_active", false); }

    public static String silentNudgeTaskId(Context c) {
        String s = sp(c).getString("sn_taskId", "");
        return s == null ? "" : s;
    }

    public static void clearSilentNudge(Context c) {
        sp(c).edit().remove("sn_active").remove("sn_taskId").apply();
    }

    // ---------- 暂停叫回的闹铃升级（v1.11.0） ----------
    // 普通暂停到点弹叫回页时武装：60 秒无反馈由 PauseEscalationReceiver 起渐强闹铃（只一轮）。
    public static void armEscalation(Context c, String taskId, long atMs) {
        sp(c).edit().putString("esc_taskId", taskId == null ? "" : taskId)
            .putLong("esc_at", atMs).apply();
    }

    public static String escalationTaskId(Context c) {
        String s = sp(c).getString("esc_taskId", "");
        return s == null ? "" : s;
    }

    public static void clearEscalation(Context c) {
        sp(c).edit().remove("esc_taskId").remove("esc_at").apply();
    }

    public static long remindAt(Context c) { return sp(c).getLong("r_at", 0); }

    public static String remindTaskId(Context c) {
        String s = sp(c).getString("r_taskId", "");
        return s == null ? "" : s;
    }

    /** 把用户的每次点按结果记成一行日志，测试后回主界面可核对。 */
    public static void logEvent(Context c, String text) {
        SharedPreferences p = sp(c);
        String old = p.getString("log", "");
        String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date());
        p.edit().putString("log", old + time + "  " + text + "\n").apply();
        // 同步落盘，保证进程被回收后日志不丢
        p.edit().commit();
    }

    public static String log(Context c) { return sp(c).getString("log", ""); }
    public static void clearLog(Context c) { sp(c).edit().remove("log").apply(); }
}
