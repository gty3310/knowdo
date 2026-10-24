package com.ti.tasktimer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** 日期小工具（v1.16.0，习惯/知行/晨间共用）：全部按本地时区、yyyy-MM-dd 字符串进出。 */
public final class DayUtil {
    private DayUtil() {}

    public static String fmt(Calendar cal) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    public static String today() { return fmt(Calendar.getInstance()); }

    /** 解析 yyyy-MM-dd 为当天 0 点的 Calendar；格式坏返回 null。 */
    public static Calendar parse(String date) {
        if (date == null) return null;
        try {
            Calendar cal = Calendar.getInstance();
            java.util.Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            if (d == null) return null;
            cal.setTime(d);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    public static String addDays(String date, int n) {
        Calendar cal = parse(date);
        if (cal == null) return date;
        cal.add(Calendar.DAY_OF_YEAR, n);
        return fmt(cal);
    }

    /** 某天所在周的周一（周一至周日为一周，与知行页口径一致）。 */
    public static String mondayOf(String date) {
        Calendar cal = parse(date);
        if (cal == null) return date;
        int dow = cal.get(Calendar.DAY_OF_WEEK); // 周日=1 … 周六=7
        int back = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY;
        cal.add(Calendar.DAY_OF_YEAR, -back);
        return fmt(cal);
    }

    /** 一周七天（周一起）的日期列表。 */
    public static List<String> weekDates(String monday) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) out.add(addDays(monday, i));
        return out;
    }

    /** a 到 b 相差几天（b - a，可为负）；解析失败返回 0。 */
    public static int daysBetween(String a, String b) {
        Calendar ca = parse(a), cb = parse(b);
        if (ca == null || cb == null) return 0;
        long diff = cb.getTimeInMillis() - ca.getTimeInMillis();
        return (int) Math.round(diff / 86400000.0);
    }

    /** 某天 0 点的毫秒。 */
    public static long dayStartMs(String date) {
        Calendar cal = parse(date);
        return cal == null ? 0 : cal.getTimeInMillis();
    }

    /** 一年里到某天为止的天数序号（1 月 1 日 = 1）。 */
    public static int dayOfYear(String date) {
        Calendar cal = parse(date);
        return cal == null ? 0 : cal.get(Calendar.DAY_OF_YEAR);
    }

    public static String yearOf(String date) {
        return date != null && date.length() >= 4 ? date.substring(0, 4) : "";
    }

    /** "M 月 d 日" / "Oct 24" 风格的短日期（列表与标题用）。 */
    public static String shortDate(String date) {
        Calendar cal = parse(date);
        if (cal == null) return date == null ? "" : date;
        if (Strings.en()) {
            return new SimpleDateFormat("MMM d", Locale.US).format(cal.getTime());
        }
        return (cal.get(Calendar.MONTH) + 1) + " 月 " + cal.get(Calendar.DAY_OF_MONTH) + " 日";
    }

    /** 星期几的显示名（周一=0 … 周日=6 的序号入参，与 weekDates 下标对齐）。 */
    public static String weekdayName(int idxMon0) {
        String[] zh = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        String[] en = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        if (idxMon0 < 0 || idxMon0 > 6) return "";
        return Strings.en() ? en[idxMon0] : zh[idxMon0];
    }
}
