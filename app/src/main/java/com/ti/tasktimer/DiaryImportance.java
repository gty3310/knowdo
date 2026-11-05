package com.ti.tasktimer;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 日记重要性数据层（v1.67.0）：每篇日记一个星级（0 无星／1 一星／2 二星），
 * 本地存应用私有文件 diary_importance.json；云端存 user_state.diary_importance
 * 列（jsonb 对象 {日期: 星级}）。本地读写仿 DiaryTitles；云端写入不走 SbSync
 * 整份同步，而是走 ZhiPage 的待同步队列：补传时先拉该列最新值、只改该日期键
 * 再整列写回（只动 diary_importance 这一列），防冲掉其他日期的星级。
 */
public final class DiaryImportance {
    private static final String FILE = "diary_importance.json";

    private DiaryImportance() {}

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

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

    /** 只写盘（同步/队列路径内部落盘用）。 */
    public static synchronized void saveQuiet(Context c, JSONObject root) {
        try {
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) { }
    }

    /** 某日的星级（无记录＝0 无星）。 */
    public static int of(JSONObject root, String date) {
        if (root == null || date == null) return 0;
        return root.optInt(date, 0);
    }

    /** 写某日星级；0＝清掉该日期键（与云端口径一致：键不存在即无星）。 */
    public static void put(JSONObject root, String date, int v) {
        if (root == null || date == null) return;
        try {
            if (v <= 0) root.remove(date);
            else root.put(date, v);
        } catch (Exception ignored) { }
    }
}
