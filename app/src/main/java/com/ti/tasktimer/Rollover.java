package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * 昨日未完成滚入今日（v1.23.0 立制，v1.26.2 改口径）：
 * 昨天没干完的，第二天一早把截止日挪到今天、挂「昨」字提醒——拖延最怕
 * 任务掉进黑洞，滚到今天它就跑不掉；但层级一格都不动，升不升必做
 * 只能用户自己来（v1.26.2：层级永不自动变）。
 *
 * 口径：
 * - 日界与习惯系统同一条：HabitData.sessionDate（dayStart 默认 04:00 之前还算前一天），
 *   所以凌晨 04:00 之前不提前滚，04:00 之后第一次启动/回前台才滚。
 * - 对象：非每日重复、未完成、且（deadline 已过逻辑今天之前的日子）或
 *   （出现在最近一份往日 schedule 的块里）的任务。每日重复任务除外——它自己会再生。
 *   deadline 用「早于逻辑今天」而不是只认昨天：App 关了几天，攒下的过期任务一次滚齐，
 *   不会因为错过某一天就永远漏掉。
 * - 动作：deadline 改逻辑今天（走 TaskStore.save 既有写路径，云端同步随后跟上）；
 *   priority 一律不改（v1.26.2 定案：优先级与截止日是两条独立的轴，
 *   Todoist/Motion/Sunsama 均如此；滚入只改日期——废除 v1.23.0「次要滚入
 *   升必做」，v1.26.0 对重要「只滚日期不升级」的做法自本版扩到全部档位）；
 *   休息（leisure）是娱乐类，永不参与滚入——不过期处理、不改日期、不挂
 *   标记。被滚任务的 id 记入本机 prefs
 *   （按日期一组），列表里名字旁挂一个小小的「昨」字（当天有效）。
 * - 每个逻辑日只滚一次（prefs 记 last_date 把关）：用户手动把滚入的任务又改回去，
 *   同一天内不会再被滚回来跟他打架；第二天它若还没做完，会再滚一次、再挂标记。
 */
public final class Rollover {
    private static final String PREF = "rollover";
    private static final String KEY_LAST_DATE = "last_date";
    private static final String KEY_MARKS_DATE = "marks_date";
    private static final String KEY_MARKS = "marks";

    private Rollover() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 逻辑今天（习惯系统同一日界口径）；习惯数据读不到时退回日历今天。 */
    public static String logicalToday(Context c) {
        try {
            String d = HabitData.sessionDate(HabitData.load(c));
            if (d != null && !d.isEmpty()) return d;
        } catch (Exception ignored) { }
        return DayUtil.today();
    }

    /**
     * 日界跨过去了就滚一次。返回是否真有任务被滚（调用方据此决定要不要补一次同步）。
     * 必须在 store.load() 之后调；内部改完直接 store.save()。
     */
    public static synchronized boolean maybeRoll(Context c, TaskStore store) {
        final String today = logicalToday(c);
        SharedPreferences sp = prefs(c);
        if (today.equals(sp.getString(KEY_LAST_DATE, null))) return false;

        // 往日 schedule 里出现过的任务 id（本地缓存只留最近一份；它的日期早于逻辑今天才算）
        Set<String> schedIds = new HashSet<>();
        try {
            JSONObject sch = SyncManager.latestSchedule(c);
            if (sch != null) {
                String d = sch.optString("date", "");
                if (!d.isEmpty() && d.compareTo(today) < 0) {
                    JSONArray blocks = sch.optJSONArray("blocks");
                    if (blocks != null) for (int i = 0; i < blocks.length(); i++) {
                        JSONObject o = blocks.optJSONObject(i);
                        if (o != null) schedIds.add(o.optString("taskId", ""));
                    }
                }
            }
        } catch (Exception ignored) { }

        Set<String> rolled = new HashSet<>();
        for (TaskItem t : store.tasks) {
            if (t.deleted || "daily".equals(t.repeat)) continue;
            if (t.isDoneToday()) continue; // 非 daily 的完成＝status done
            if (TaskItem.isLeisure(t.priority)) continue; // 休息档永不滚入（v1.26.0）
            boolean overdue = t.deadline != null && t.deadline.compareTo(today) < 0;
            if (!overdue && !schedIds.contains(t.id)) continue;
            // 层级永不自动变（v1.26.2）：只挪日期，priority 原样不动
            t.deadline = today; // deadlineTime（当天几点前做完）保留原值，不动用户的钟点
            t.touch();
            rolled.add(t.id);
        }
        if (!rolled.isEmpty()) store.save();
        sp.edit()
                .putString(KEY_LAST_DATE, today)
                .putString(KEY_MARKS_DATE, today)
                .putStringSet(KEY_MARKS, rolled)
                .apply();
        marksCacheDate = today;
        marksCache = rolled;
        return !rolled.isEmpty();
    }

    // ---- 「昨」标记（列表渲染每行都问，缓存一组避免逐行读 prefs） ----
    private static String marksCacheDate;
    private static Set<String> marksCache;

    /** 这个任务是不是今天被滚进来的（标记只在滚入当天有效）。 */
    public static synchronized boolean isRolled(Context c, String taskId) {
        if (taskId == null) return false;
        String today = logicalToday(c);
        if (marksCache == null || !today.equals(marksCacheDate)) {
            SharedPreferences sp = prefs(c);
            if (today.equals(sp.getString(KEY_MARKS_DATE, null))) {
                marksCache = new HashSet<>(sp.getStringSet(KEY_MARKS, new HashSet<>()));
            } else {
                marksCache = new HashSet<>();
            }
            marksCacheDate = today;
        }
        return marksCache.contains(taskId);
    }
}
