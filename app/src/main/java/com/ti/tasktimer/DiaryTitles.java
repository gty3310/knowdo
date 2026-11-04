package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * 日记标题数据层（v1.61.0）：每篇日记一个标题（AI 生成、用户可改），供双链引用
 * 与日记列表显示「YYMMDD＋主题」。本地存应用私有文件 diary_titles.json，并经
 * Supabase user_state 的 diary_titles 列（jsonb 对象）同步；同步纪律与 weekly_data
 * 同一套（SbSync：列先探测再碰、本机从未存过且云端有值→整份采用、本机有值→
 * 并集合并本机优先、Realtime 推来只合并不回写）。标题不进 habit_data，日记正文
 * 仍只住 HabitData.diary、仍只经 putDiary 写。
 *
 * 结构（云端与本地同一份 JSON）：{ "<yyyy-MM-dd>": "标题文本" }
 */
public final class DiaryTitles {
    private static final String FILE = "diary_titles.json";
    private static final String PREF = "diary_titles_store";

    private DiaryTitles() {}

    // ---------------- 本地读写（仿 WeeklyData） ----------------
    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean hasLocal(Context c) { return prefs(c).getBoolean("has_local", false); }

    public static synchronized JSONObject load(Context c) {
        try {
            File f = file(c);
            if (f.exists()) {
                String s = TaskStore.readAll(new FileInputStream(f));
                return new JSONObject(s);
            }
        } catch (Exception ignored) { }
        return new JSONObject();
    }

    /** 保存（写盘＋标记本机有值＋触发一次后台同步；未登录/列未就绪时同步层自己短路）。 */
    public static synchronized void save(Context c, JSONObject root) {
        writeFile(c, root);
        requestSync(c);
    }

    /** 只写盘、不触发同步（逐字保存与同步流程内部落盘用，避免递归触发）。 */
    public static synchronized void saveQuiet(Context c, JSONObject root) {
        writeFile(c, root);
    }

    /** 采用云端整份（本机从未存过时）；不触发同步。 */
    public static synchronized void adopt(Context c, JSONObject remote) {
        if (remote == null) return;
        try {
            writeFile(c, new JSONObject(remote.toString()));
        } catch (Exception ignored) { }
    }

    private static void writeFile(Context c, JSONObject root) {
        try {
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
            prefs(c).edit().putBoolean("has_local", true).apply();
        } catch (Exception ignored) { }
    }

    private static void requestSync(final Context c) {
        if (!Supa.isLoggedIn(c)) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try { SbSync.syncNow(app, new TaskStore(app)); } catch (Exception ignored) { }
        }).start();
    }

    // ---------------- 条目读写 ----------------
    public static String title(JSONObject root, String date) {
        if (root == null || date == null) return "";
        return root.optString(date, "").trim();
    }

    /** 写某日标题；空文本＝清掉这一天的标题。 */
    public static void putTitle(JSONObject root, String date, String text) {
        if (root == null || date == null) return;
        try {
            String t = text == null ? "" : text.trim();
            if (t.isEmpty()) root.remove(date); else root.put(date, t);
        } catch (Exception ignored) { }
    }

    /** "2026-11-04" → "261104"（YYMMDD）。 */
    public static String yymmdd(String date) {
        if (date == null || date.length() != 10) return date == null ? "" : date;
        return date.substring(2, 4) + date.substring(5, 7) + date.substring(8, 10);
    }

    /** 日记的引用显示名：YYMMDD＋标题（无标题只显示 YYMMDD）。 */
    public static String displayLabel(JSONObject root, String date) {
        String t = title(root, date);
        return t.isEmpty() ? yymmdd(date) : yymmdd(date) + " " + t;
    }

    // ---------------- 合并（同步纪律核心，仿 WeeklyData.merge） ----------------
    /** 本机与云端合并：按日期并集、同日标题本机优先（标题在设备上改，本机最权威）。纯函数，不落盘。 */
    public static JSONObject merge(JSONObject local, JSONObject remote) {
        if (remote == null) return local;
        if (local == null) return remote;
        try {
            JSONObject out = new JSONObject(local.toString());
            Iterator<String> ks = remote.keys();
            while (ks.hasNext()) {
                String k = ks.next();
                if (!out.has(k)) out.put(k, remote.get(k));
            }
            return out;
        } catch (Exception e) {
            return local;
        }
    }
}
