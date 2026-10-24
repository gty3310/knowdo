package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/**
 * 任务闹钟调度（v1.9.0）：每个任务可设 alarmAt（本地时间字符串），到点由
 * TaskAlarmReceiver 拉起响铃页。与 StartReminder 的单槽设计不同，闹钟按任务各占一个
 * PendingIntent（requestCode 由任务 id 派生），互不影响、逐个可取消。
 * 触发优先 setAlarmClock（系统闹钟同款：免精确闹钟权限、Doze 下也准时），异常时
 * 退回与开始提醒一致的精确/普通闹钟。
 * 每日重复任务：nextFireMs 按墙钟逐日推进到未来，Receiver 触发后再调 rescheduleAll
 * 即自动续排到下一天同一时间；当天已完成的每日任务，本次先跳到明天，别响已做完的事。
 * 调用点与 StartReminder.reschedule 对齐：MainActivity 回到前台、两个同步引擎合并后、
 * BootReceiver 开机后、TaskAlarmReceiver 触发后、闹钟设置保存后（dialogAlarm）。
 */
public class AlarmScheduler {
    public static final String ACTION = "com.ti.tasktimer.TASK_ALARM";
    public static final String EXTRA_TASK_ID = "task_id";
    private static final int REQ_SHOW = 991;

    private static PendingIntent alarmIntent(Context c, TaskItem t) {
        Intent i = new Intent(c, TaskAlarmReceiver.class);
        i.setAction(ACTION);
        i.putExtra(EXTRA_TASK_ID, t.id);
        return PendingIntent.getBroadcast(c, t.id.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 下一次应响的时刻（毫秒）；没设闹钟、或一次性闹钟时间已过，返回 0。 */
    public static long nextFireMs(TaskItem t, long now) {
        long base = t.alarmMs();
        if (base == 0) return 0;
        if (!"daily".equals(t.repeat)) return base > now ? base : 0;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(base);
        int guard = 0;
        while (cal.getTimeInMillis() <= now && guard++ < 400) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        if (t.isDoneToday() && isSameDay(cal.getTimeInMillis(), now)) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    private static boolean isSameDay(long a, long b) {
        Calendar x = Calendar.getInstance();
        x.setTimeInMillis(a);
        Calendar y = Calendar.getInstance();
        y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR)
                && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    /** 扫描全部任务：逐个先撤旧闹钟，再按当前设置重排（已删除/一次性已完成的不排）。 */
    public static synchronized void rescheduleAll(Context c, TaskStore store) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis();
        for (TaskItem t : store.tasks) {
            PendingIntent pi = alarmIntent(c, t);
            try { am.cancel(pi); } catch (Exception ignored) { }
            if (t.deleted) continue;
            if (!"daily".equals(t.repeat) && "done".equals(t.status)) continue;
            long fire = nextFireMs(t, now);
            if (fire == 0) continue;
            setClock(c, am, fire, pi);
        }
    }

    private static void setClock(Context c, AlarmManager am, long fireMs, PendingIntent pi) {
        try {
            Intent show = new Intent(c, MainActivity.class);
            PendingIntent showPi = PendingIntent.getActivity(c, REQ_SHOW, show,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(fireMs, showPi), pi);
            return;
        } catch (Exception ignored) { }
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMs, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMs, pi);
    }
}
