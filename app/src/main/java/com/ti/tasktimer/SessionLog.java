package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 本地计时段记录（sessions）：每次计时开始落一个“打开的段”，暂停/停止时关闭成一段
 * {taskId, title, startMs, endMs}，存 SharedPreferences 的 JSON 数组，只存 App 本地、不进桥文件
 * （与桥 fieldGuide 的口径一致）。同一任务分段做就有多段，日历页按天取段画“实际投入”。
 * 本类 v1.6.0 新增——此前 App 只有文字流水日志，没有结构化的段记录，所以实际段从本版装上后开始积累。
 */
public final class SessionLog {
    private static final String PREF = "sessions";
    private static final String KEY_LIST = "list";
    private static final String KEY_OPEN_TASK = "open_task";
    private static final String KEY_OPEN_TITLE = "open_title";
    private static final String KEY_OPEN_START = "open_start";
    private static final int MAX_SEGS = 500;

    private SessionLog() { }

    public static class Seg {
        public String taskId = "";
        public String title = "";
        public long startMs;
        public long endMs;   // -1 表示进行中（打开的段，endMs 尚未落定）
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 计时开始/继续时调用：打开一段。若已有一段没关（异常路径，如直接切换任务），先以当前时刻关闭它。 */
    public static synchronized void open(Context c, String taskId, String title) {
        close(c);
        sp(c).edit()
                .putString(KEY_OPEN_TASK, taskId == null ? "" : taskId)
                .putString(KEY_OPEN_TITLE, title == null ? "" : title)
                .putLong(KEY_OPEN_START, System.currentTimeMillis())
                .apply();
    }

    /** 计时暂停/停止时调用：把打开的段以当前时刻关闭并存档。没有打开的段就什么都不做。 */
    public static synchronized void close(Context c) {
        SharedPreferences p = sp(c);
        long start = p.getLong(KEY_OPEN_START, 0);
        if (start <= 0) return;
        long now = System.currentTimeMillis();
        Seg s = new Seg();
        s.taskId = p.getString(KEY_OPEN_TASK, "");
        s.title = p.getString(KEY_OPEN_TITLE, "");
        s.startMs = start;
        s.endMs = Math.max(now, start);
        List<Seg> all = readAll(c);
        all.add(s);
        while (all.size() > MAX_SEGS) all.remove(0);
        writeAll(c, all);
        p.edit().remove(KEY_OPEN_TASK).remove(KEY_OPEN_TITLE).remove(KEY_OPEN_START).apply();
    }

    /** 当前打开段的开始时刻；没有打开的段返回 0。 */
    public static long openStart(Context c) {
        return sp(c).getLong(KEY_OPEN_START, 0);
    }

    public static String openTaskId(Context c) {
        String s = sp(c).getString(KEY_OPEN_TASK, "");
        return s == null ? "" : s;
    }

    /** 取与某一天（yyyy-MM-dd，本地时区）有重叠的已关闭段，起止已裁剪到当天范围内，按开始时间排序。 */
    public static List<Seg> segmentsForDay(Context c, String date) {
        long dayStart = dayStartMs(date);
        long dayEnd = dayStart + 24L * 3600 * 1000;
        List<Seg> out = new ArrayList<>();
        for (Seg s : readAll(c)) {
            if (s.endMs <= dayStart || s.startMs >= dayEnd) continue;
            Seg clipped = new Seg();
            clipped.taskId = s.taskId;
            clipped.title = s.title;
            clipped.startMs = Math.max(s.startMs, dayStart);
            clipped.endMs = Math.min(s.endMs, dayEnd);
            out.add(clipped);
        }
        out.sort((a, b) -> Long.compare(a.startMs, b.startMs));
        return out;
    }

    private static long dayStartMs(String date) {
        try {
            String[] p = date.split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        }
    }

    private static List<Seg> readAll(Context c) {
        List<Seg> out = new ArrayList<>();
        String s = sp(c).getString(KEY_LIST, null);
        if (s == null) return out;
        try {
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Seg seg = new Seg();
                seg.taskId = o.optString("taskId", "");
                seg.title = o.optString("title", "");
                seg.startMs = o.optLong("startMs", 0);
                seg.endMs = o.optLong("endMs", 0);
                if (seg.startMs > 0 && seg.endMs >= seg.startMs) out.add(seg);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static void writeAll(Context c, List<Seg> segs) {
        try {
            JSONArray arr = new JSONArray();
            for (Seg s : segs) {
                JSONObject o = new JSONObject();
                o.put("taskId", s.taskId);
                o.put("title", s.title);
                o.put("startMs", s.startMs);
                o.put("endMs", s.endMs);
                arr.put(o);
            }
            sp(c).edit().putString(KEY_LIST, arr.toString()).apply();
        } catch (Exception ignored) { }
    }
}
