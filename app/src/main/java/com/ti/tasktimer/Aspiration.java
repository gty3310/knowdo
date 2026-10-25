package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * 立志常驻（v1.12.0）：用户亲手写的一句话「我的志」，每人各自设置。
 * 本地存 SharedPreferences aspiration；同步走 Supabase user_state.aspiration，
 * 合并纪律在 SbSync.syncUserState：本机为空且云端有值→采用云端值；
 * 本机有值且与云端不同→回写云端（只动 aspiration 一个键，不覆盖其他键）。
 * 桥模式下没有 user_state，志只存本地。
 */
public final class Aspiration {
    private static final String PREF = "aspiration";

    private Aspiration() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String get(Context c) {
        String s = sp(c).getString("text", "");
        return s == null ? "" : s.trim();
    }

    public static void setLocal(Context c, String text) {
        sp(c).edit().putString("text", text == null ? "" : text.trim()).apply();
    }

    /**
     * 从云端 user_state 行里取 aspiration：两种形态都认——
     * 纯字符串（jsonb 里存一句话），或对象 {"text": "..."}。取不到返回空串。
     */
    static String fromCloudRow(JSONObject row) {
        try {
            if (row == null || !row.has("aspiration") || row.isNull("aspiration")) return "";
            Object o = row.get("aspiration");
            if (o instanceof String) return ((String) o).trim();
            if (o instanceof JSONObject) return ((JSONObject) o).optString("text", "").trim();
        } catch (Exception ignored) { }
        return "";
    }

    /**
     * 展示视图：未设置返回 null（页面不留占位、不催填）。
     * 衬线字体、柔和金色、居中、不可点——它只是给用户看的一句话，不是按钮。
     */
    public static TextView makeView(Context c, boolean onDarkBg) {
        String text = get(c);
        if (text.isEmpty()) return null;
        TextView t = new TextView(c);
        t.setText(text);
        t.setTypeface(Typeface.SERIF);
        t.setTextSize(19);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(onDarkBg ? Color.rgb(243, 156, 140) : Color.rgb(217, 75, 58));
        t.setLineSpacing(0, 1.25f);
        t.setClickable(false);
        t.setFocusable(false);
        return t;
    }
}
