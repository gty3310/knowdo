package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * 守卫难度三档的计数账（v1.83.0）：全部只存本机 prefs，绝不上云。
 * 一本账、逻辑日口径（Rollover.logicalToday，04:00 日界，与全 App 同一工具）：
 * - per (taskId, 逻辑日) inquiryCount：问询页开页次数（medium 误判钮显隐用）；
 * - per (taskId, 逻辑日) graceCount：已真正生效的宽限次数（呼吸阶梯用）；
 * - 全局（不分任务）hardUsed：困难档当日已占用的宽限配额。
 * 日期滚动即自然归零：读时先比对存的日期、不是今天一律按 0 读；写 per-task
 * 计数时顺手清掉旧日期的 per-task 键（hard 配额有自己的日期键、互不清）。
 */
public final class GuardLedger {
    private static final String PREF = "guard_counts";

    private GuardLedger() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String day(Context c) { return Rollover.logicalToday(c); }

    private static String key(String kind, String taskId) {
        return kind + "_" + (taskId == null ? "" : taskId);
    }

    // ---- per-task 计数（问询/宽限共用 task_day 一本日期账） ----

    public static int inquiryCount(Context c, String taskId) {
        SharedPreferences p = prefs(c);
        if (!day(c).equals(p.getString("task_day", ""))) return 0;
        return p.getInt(key("inq", taskId), 0);
    }

    public static void noteInquiry(Context c, String taskId) {
        SharedPreferences p = prefs(c);
        int next = inquiryCount(c, taskId) + 1;
        SharedPreferences.Editor e = p.edit();
        rollTaskDay(c, p, e);
        e.putInt(key("inq", taskId), next).apply();
    }

    public static int graceCount(Context c, String taskId) {
        SharedPreferences p = prefs(c);
        if (!day(c).equals(p.getString("task_day", ""))) return 0;
        return p.getInt(key("grc", taskId), 0);
    }

    /** 宽限真正生效（easy 点钮即执行／呼吸完成执行）时记一次；呼吸放弃不记。 */
    public static void noteGrace(Context c, String taskId) {
        SharedPreferences p = prefs(c);
        int next = graceCount(c, taskId) + 1;
        SharedPreferences.Editor e = p.edit();
        rollTaskDay(c, p, e);
        e.putInt(key("grc", taskId), next).apply();
    }

    /** 写 per-task 计数前调用：日期已滚动就清掉旧日期的 per-task 键并推进日期。 */
    private static void rollTaskDay(Context c, SharedPreferences p, SharedPreferences.Editor e) {
        String today = day(c);
        if (today.equals(p.getString("task_day", ""))) return;
        Map<String, ?> all = p.getAll();
        for (String k : all.keySet()) {
            if (k.startsWith("inq_") || k.startsWith("grc_")) e.remove(k);
        }
        e.putString("task_day", today);
    }

    // ---- 困难档每日配额（全局，不分任务） ----

    public static int hardGraceUsed(Context c) {
        SharedPreferences p = prefs(c);
        if (!day(c).equals(p.getString("hard_day", ""))) return 0;
        return p.getInt("hard_used", 0);
    }

    /** 困难档宽限真正生效时占用一次配额（与 noteGrace 在同一完成点调用）。 */
    public static void noteHardGrace(Context c) {
        SharedPreferences p = prefs(c);
        int cur = hardGraceUsed(c);
        p.edit().putString("hard_day", day(c)).putInt("hard_used", cur + 1).apply();
    }
}
