package com.ti.tasktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 习惯提醒调度（v1.16.0）：每个习惯可设自己的提醒时间（"HH:mm"）。
 * 每习惯一个闹钟槽：requestCode 按 habitId 分配并存 prefs（稳定不漂移），
 * PendingIntent 带 habitId。到点由 HabitReceiver 发本地通知并续排下一天。
 * 改/关即时生效：任何习惯定义变化后调 rescheduleAll 全量重排（先按已存的
 * code 表逐个撤，再按当前定义逐个排）。重启由 BootReceiver 调 rescheduleAll 续排。
 */
public final class HabitReminder {
    public static final String ACTION = "com.ti.tasktimer.HABIT_REMIND";
    public static final String EXTRA_HABIT_ID = "habit_id";
    private static final String PREF = "habit_remind";

    private HabitReminder() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static synchronized int codeFor(Context c, String habitId) {
        try {
            String s = prefs(c).getString("codes", null);
            JSONObject map = s == null ? new JSONObject() : new JSONObject(s);
            if (map.has(habitId)) return map.getInt(habitId);
            int next = prefs(c).getInt("next_code", 61000);
            map.put(habitId, next);
            prefs(c).edit().putString("codes", map.toString())
                    .putInt("next_code", next + 1).apply();
            return next;
        } catch (Exception e) {
            return 61000 + Math.abs(habitId.hashCode() % 5000);
        }
    }

    private static Map<String, Integer> allCodes(Context c) {
        Map<String, Integer> out = new HashMap<>();
        try {
            String s = prefs(c).getString("codes", null);
            if (s == null) return out;
            JSONObject map = new JSONObject(s);
            Iterator<String> it = map.keys();
            while (it.hasNext()) {
                String k = it.next();
                out.put(k, map.getInt(k));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static PendingIntent intentFor(Context c, String habitId, int code) {
        Intent i = new Intent(c, HabitReceiver.class);
        i.setAction(ACTION);
        i.putExtra(EXTRA_HABIT_ID, habitId);
        return PendingIntent.getBroadcast(c, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 全量重排：撤掉所有已知槽位，再按当前习惯定义逐个排下一次。 */
    public static synchronized void rescheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (Map.Entry<String, Integer> e : allCodes(c).entrySet()) {
            try { am.cancel(intentFor(c, e.getKey(), e.getValue())); } catch (Exception ignored) { }
        }
        JSONObject root = HabitData.load(c);
        long now = System.currentTimeMillis();
        for (HabitData.Habit h : HabitData.habits(root)) {
            if (h.reminder == null) continue;
            long at = nextTriggerMs(h.reminder, now);
            if (at <= 0) continue;
            PendingIntent pi = intentFor(c, h.id, codeFor(c, h.id));
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            try {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Exception ignored) { }
        }
    }

    /** "HH:mm" 的下一次触发时刻：今天该时刻未过用今天，已过用明天；格式坏返回 0。 */
    static long nextTriggerMs(String hhmm, long now) {
        try {
            String[] p = hhmm.split(":");
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(now);
            cal.set(java.util.Calendar.HOUR_OF_DAY, h);
            cal.set(java.util.Calendar.MINUTE, m);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= now) cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }
}
