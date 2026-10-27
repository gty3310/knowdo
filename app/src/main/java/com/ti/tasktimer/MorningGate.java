package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;

import org.json.JSONObject;

/**
 * 晨间门状态机（v1.17.0）：晨间首填开启后，晨间页不再只等用户打开知行——
 * 每天 dayStart 之后、当前 session 未完成时，用户早上第一次实际使用手机
 * （解锁 USER_PRESENT）就经 AlertHelper.fireMorning 全屏拉起；在晨间页点
 * 「暂停一会儿」可以把这一页推后 N 分钟，到点由 MorningReceiver 再次全屏拉起。
 *
 * 本类只存「门行为」状态（本机 prefs morning_gate，不进同步——与 FloatSnooze 同类）：
 * - snooze_until：暂停推后的截止时刻（ms）。持久化：杀进程/重启后以它为准续排闹钟；
 * - pulled_session：首触自动路径已经拉起过的 session 日（同一 session 只自动拉一次，
 *   之后靠推后闹钟或用户自己打开知行走 MainActivity.onResume 入口）；
 * - nag_session / nag_next_at：重弹循环（v1.26.0，间隔 v1.26.3 起读 PromptRepop）——晨间页一旦以门模式
 *   展示过（engageNag），未提交、又没选「暂停一会儿」推迟时，每隔重弹间隔经
 *   AlertHelper.fireMorning 全屏再拉一次；按 Home/返回/切走一律不算推迟，
 *   照样弹回。提交（onCompleted）当天永久停；点推迟（snooze）循环暂停，
 *   推后到点由 MorningReceiver 拉回页面、展示时重新 engage。两个字段都落盘，
 *   杀进程/重启后由 ensureScheduled 按 nag_next_at 续排（已过点补一拍）。
 * 「完成与否」的内容状态仍在 HabitData.morningDone（habit_data 同步），本类不碰内容。
 */
public final class MorningGate {
    private static final String PREF = "morning_gate";
    private static final String ACTION_DEFER = "com.ti.tasktimer.MORNING_DEFER";
    static final String ACTION_NAG = "com.ti.tasktimer.MORNING_NAG";
    private static final int REQ_DEFER = 779;
    private static final int REQ_NAG = 780;
    /** 重弹节奏（v1.26.3 起）：读 PromptRepop（提示页重弹间隔，云端同步的个人
     *  设置，作者账号 15 秒）；v1.26.0 初版为固定 5 分钟，现统一归该设置管。 */

    /** 晨间页点「暂停一会儿」退下后，紧接着的那次 MainActivity.onResume 不要立刻把页拉回来。 */
    private static volatile boolean suppressResumePull = false;

    private MorningGate() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---------------- 推后（暂停一会儿） ----------------

    public static long snoozeUntil(Context c) { return sp(c).getLong("snooze_until", 0L); }

    public static boolean snoozeActive(Context c) {
        return snoozeUntil(c) > System.currentTimeMillis();
    }

    /** 点「暂停一会儿」选定分钟数：落盘截止时刻＋排精确闹钟＋标记下一次 onResume 不拉回。
     *  同时暂停重弹循环（nag_session 保留，推后到点拉回页面时重新 engage）。 */
    public static void snooze(Context c, long delayMs) {
        long until = System.currentTimeMillis() + Math.max(60_000L, delayMs);
        sp(c).edit().putLong("snooze_until", until).apply();
        suppressResumePull = true;
        cancelNagAlarm(c);
        scheduleAlarm(c, until);
        TimerState.logEvent(c, "晨间页暂停一会儿：" + (delayMs / 60000) + " 分钟后再次拉起");
    }

    /** 清掉推后状态并撤闹钟（晨间页被拉起展示、或晨间已完成时调用）。 */
    public static void clearSnooze(Context c) {
        sp(c).edit().remove("snooze_until").apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarmIntent(c));
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, MorningReceiver.class);
        i.setAction(ACTION_DEFER);
        return PendingIntent.getBroadcast(c, REQ_DEFER, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleAlarm(Context c, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(c));
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(c));
    }

    // ---------------- 重弹循环（v1.26.0，间隔 v1.26.3 起读 PromptRepop） ----------------

    private static PendingIntent nagAlarmIntent(Context c) {
        Intent i = new Intent(c, MorningReceiver.class);
        i.setAction(ACTION_NAG);
        return PendingIntent.getBroadcast(c, REQ_NAG, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleNag(Context c, long at) {
        sp(c).edit().putLong("nag_next_at", at).apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, nagAlarmIntent(c));
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, nagAlarmIntent(c));
    }

    private static void cancelNagAlarm(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(nagAlarmIntent(c));
    }

    /**
     * 晨间页以门模式展示出来时调用：本 session 进入重弹循环——从这一刻起，
     * 未提交、未选推迟，就每隔重弹间隔全屏再拉一次（onNagTick）。落盘 nag_session，
     * 进程被杀/重启后由 ensureScheduled 续排。
     */
    public static void engageNag(Context c, String session) {
        if (session == null || session.isEmpty()) return;
        sp(c).edit().putString("nag_session", session).apply();
        scheduleNag(c, System.currentTimeMillis() + PromptRepop.millis(c));
    }

    /** 彻底停掉重弹循环并清状态（晨间完成、或 session 已变时调用）。 */
    public static void stopNag(Context c) {
        sp(c).edit().remove("nag_session").remove("nag_next_at").apply();
        cancelNagAlarm(c);
    }

    /**
     * 重弹闹钟到点（MorningReceiver 转发）：核对状态后决定这一拍拉不拉——
     * 晨间已完成/session 已变 → 停循环；正在推后期 → 顺延到推后到点之后；
     * 晨间页正在显、知行自己的页面在前台、正在通话 → 不抢，顺延一个重弹间隔再试；
     * 其余情况（计时/暂停等一律不豁免，晨间门优先级最高）→ 全屏拉起并排下一拍。
     */
    public static void onNagTick(Context c) {
        try {
            JSONObject root = HabitData.load(c);
            String session = HabitData.sessionDate(root);
            if (!session.equals(sp(c).getString("nag_session", ""))) { stopNag(c); return; }
            if (!HabitData.shouldShowMorning(root)) { stopNag(c); return; }
            long now = System.currentTimeMillis();
            if (snoozeActive(c)) {
                ensureScheduled(c); // 推后闹钟续上，推后到点拉回后展示时会重新 engage
                scheduleNag(c, Math.max(now + PromptRepop.millis(c), snoozeUntil(c)));
                return;
            }
            if (TopPages.morningShown || KnowDoApp.ownActivityVisible() || inCall(c)) {
                scheduleNag(c, now + PromptRepop.millis(c));
                return;
            }
            sp(c).edit().putString("pulled_session", session).apply();
            TimerState.logEvent(c, "晨间门：未提交也未选推迟，重弹间隔到点再次全屏拉起");
            AlertHelper.fireMorning(c);
            scheduleNag(c, now + PromptRepop.millis(c));
        } catch (Exception ignored) { }
    }

    /**
     * 开机/服务启动时按持久化状态续排：闹钟不跨重启保留。
     * 未到点 → 按原时刻重排；已过点（推后期间关机/被杀没响成）→ 1 秒后补响当前轮，
     * 由 MorningReceiver 再核对晨间是否仍未完成。
     */
    public static void ensureScheduled(Context c) {
        long until = snoozeUntil(c);
        if (until != 0L) {
            long now = System.currentTimeMillis();
            scheduleAlarm(c, until > now ? until : now + 1_000L);
        }
        // 重弹循环续排（v1.26.0）：nag_session 仍是当前 session、晨间未完成、
        // 不在推后期 → 按落盘的 nag_next_at 续排（已过点 1 秒后补一拍，由 onNagTick 复核）
        try {
            JSONObject root = HabitData.load(c);
            String ns = sp(c).getString("nag_session", "");
            if (!ns.isEmpty() && ns.equals(HabitData.sessionDate(root))
                    && HabitData.shouldShowMorning(root) && !snoozeActive(c)) {
                long next = sp(c).getLong("nag_next_at", 0);
                long now = System.currentTimeMillis();
                scheduleNag(c, next > now ? next : now + 1_000L);
            }
        } catch (Exception ignored) { }
    }

    // ---------------- 首触自动拉起 ----------------

    private static String pulledSession(Context c) { return sp(c).getString("pulled_session", ""); }

    /**
     * 自动拉起检查（FloatService 的 USER_PRESENT 接收与约 30 秒巡检共用）：
     * 晨间未完成、当前不在推后期、本 session 还没被自动拉起过、本应用没有页面在
     * 前台、晨间页不在显、没有正在进行的通话 → 经统一出口全屏拉起，并记下本
     * session 已拉过。巡检这条路主要兜两种情况：解锁那一刻本服务还没起来
     *（进程被杀后 START_STICKY 重启稍晚）、以及通话结束之后（通话中不抢，
     * 挂断后由下一次巡检补拉）。
     */
    /** @return 本次是否拉起了晨间页（v1.19.0 起供 GuardDispatch 按优先级调度：拉起晨间页后，致谢页/选任务页本轮不再拉）。 */
    public static boolean autoCheck(Context c) {
        try {
            JSONObject root = HabitData.load(c);
            if (!HabitData.shouldShowMorning(root)) return false;
            if (snoozeActive(c)) { ensureScheduled(c); return false; }
            if (TopPages.morningShown) return false;
            if (KnowDoApp.ownActivityVisible()) return false;
            String session = HabitData.sessionDate(root);
            if (session.equals(pulledSession(c))) return false;
            if (inCall(c)) return false; // 通话/来电界面在上时不抢，不标记已拉，事后巡检补
            sp(c).edit().putString("pulled_session", session).apply();
            TimerState.logEvent(c, "晨间门：本 session 首次用机，全屏拉起晨间页");
            AlertHelper.fireMorning(c);
            return true;
        } catch (Exception ignored) { }
        return false;
    }

    /** 通话状态粗判（无需电话权限）：系统音频模式处于通话/通讯/响铃即视为通话中。 */
    private static boolean inCall(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            int mode = am.getMode();
            return mode == AudioManager.MODE_IN_CALL
                    || mode == AudioManager.MODE_IN_COMMUNICATION
                    || mode == AudioManager.MODE_RINGTONE;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- 与页面/主页的衔接 ----------------

    /** 晨间页（门/测试模式）展示出来：推后闹钟的使命已达，清掉推后状态与闹钟。 */
    public static void onGateShown(Context c) { clearSnooze(c); }

    /** 晨间完成：推后状态、全屏通知与重弹循环一并收尾（当天永久停）。 */
    public static void onCompleted(Context c) {
        clearSnooze(c);
        stopNag(c);
        AlertHelper.cancelMorning(c);
    }

    /** 设置页「测试晨间页」：不看 dayStart、不看当日是否已完成，直接以门模式打开正式页。 */
    public static void launchTest(Context c) {
        Intent i = new Intent(c, MorningActivity.class);
        i.putExtra(MorningActivity.EXTRA_MODE, MorningActivity.MODE_TEST);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** MainActivity.onResume 消费「刚点过暂停退下」标记：消费一次即清。 */
    public static boolean consumeSuppressResumePull() {
        boolean v = suppressResumePull;
        suppressResumePull = false;
        return v;
    }
}
