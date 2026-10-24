package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import java.util.Locale;

/**
 * UI 偏好与主题（v1.8.0）：语言（跟随系统/中文/English）、外观（跟随系统/亮/暗）、
 * 任务表列显隐与列宽、日历面板展开幅度——全部只存本地 SharedPreferences（ui_prefs），
 * 不进任何同步数据。配色按定稿设计稿：亮＝白底清新；暗＝深灰底＋琥珀强调。
 */
public final class Ui {
    private static final String PREF = "ui_prefs";

    private Ui() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---------------- 语言 ----------------
    /** "auto" / "zh" / "en" */
    public static String langPref(Context c) { return sp(c).getString("lang", "auto"); }

    public static void setLangPref(Context c, String v) {
        sp(c).edit().putString("lang", v).apply();
    }

    /** 解析后的实际语言：true=英文。auto 时按系统语言判定（en 开头算英文，其余中文）。 */
    public static boolean en(Context c) {
        String p = langPref(c);
        if ("en".equals(p)) return true;
        if ("zh".equals(p)) return false;
        String sys = Locale.getDefault().getLanguage();
        return sys != null && sys.toLowerCase(Locale.US).startsWith("en");
    }

    // ---------------- 外观 ----------------
    /** "auto" / "light" / "dark" */
    public static String themePref(Context c) { return sp(c).getString("theme", "auto"); }

    public static void setThemePref(Context c, String v) {
        sp(c).edit().putString("theme", v).apply();
    }

    public static boolean dark(Context c) {
        String p = themePref(c);
        if ("dark".equals(p)) return true;
        if ("light".equals(p)) return false;
        int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    // ---------------- 配色（设计稿色板） ----------------
    public static int bg(Context c)      { return dark(c) ? Color.rgb(19, 23, 25)   : Color.WHITE; }
    public static int surface(Context c) { return dark(c) ? Color.rgb(23, 28, 31)   : Color.WHITE; }
    public static int muted(Context c)   { return dark(c) ? Color.rgb(31, 37, 41)   : Color.rgb(244, 246, 245); }
    public static int text(Context c)    { return dark(c) ? Color.rgb(233, 237, 235) : Color.rgb(29, 37, 33); }
    public static int sub(Context c)     { return dark(c) ? Color.rgb(141, 152, 145) : Color.rgb(124, 135, 127); }
    public static int line(Context c)    { return dark(c) ? Color.rgb(43, 51, 56)   : Color.rgb(228, 233, 230); }
    public static int accent(Context c)  { return dark(c) ? Color.rgb(226, 170, 61) : Color.rgb(179, 121, 29); }
    public static int onAccent(Context c){ return dark(c) ? Color.rgb(25, 18, 4)    : Color.WHITE; }
    /** 可点文字（原蓝色链接位）：亮版沿用原蓝，暗版提亮保证可读。 */
    public static int link(Context c)    { return dark(c) ? Color.rgb(126, 178, 235) : Color.rgb(21, 101, 192); }
    public static int danger(Context c)  { return dark(c) ? Color.rgb(239, 154, 154) : Color.rgb(198, 40, 40); }
    public static int green(Context c)   { return dark(c) ? Color.rgb(129, 199, 132) : Color.rgb(46, 125, 50); }
    /** 正在计时行的底色：强调色约 13% 混底色（设计稿口径，预先算好）。 */
    public static int runningBg(Context c) { return dark(c) ? Color.rgb(49, 46, 35) : Color.rgb(245, 238, 226); }
    /** 已完成行的底色。 */
    public static int doneBg(Context c)  { return dark(c) ? Color.rgb(27, 32, 35) : Color.rgb(245, 245, 245); }

    // ---------------- 任务表：列显隐 / 列宽 ----------------
    public static boolean colVisible(Context c, String colId, boolean def) {
        return sp(c).getBoolean("col_vis_" + colId, def);
    }

    public static void setColVisible(Context c, String colId, boolean v) {
        sp(c).edit().putBoolean("col_vis_" + colId, v).apply();
    }

    public static int colWidthDp(Context c, String colId, int defDp) {
        return sp(c).getInt("col_w_" + colId, defDp);
    }

    public static void setColWidthDp(Context c, String colId, int dp) {
        sp(c).edit().putInt("col_w_" + colId, dp).apply();
    }

    // ---------------- 日历面板展开幅度（占屏宽百分比） ----------------
    public static int calPanelPct(Context c) { return sp(c).getInt("cal_panel_pct", 62); }

    public static void setCalPanelPct(Context c, int pct) {
        sp(c).edit().putInt("cal_panel_pct", pct).apply();
    }

    /** 真齿轮图标（设计稿定案：列设置按钮要画成齿轮）：8 齿＋外圈＋中心孔，用 Canvas 现画。 */
    public static class GearDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int holeColor;

        public GearDrawable(int color, int holeColor) {
            p.setColor(color);
            this.holeColor = holeColor;
        }

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            float r = Math.min(b.width(), b.height()) / 2f;
            float toothW = r * 0.34f, toothH = r * 0.72f;
            canvas.save();
            canvas.translate(cx, cy);
            for (int i = 0; i < 8; i++) {
                canvas.save();
                canvas.rotate(i * 45f);
                canvas.drawRect(-toothW / 2f, -r, toothW / 2f, -r + toothH, p);
                canvas.restore();
            }
            canvas.drawCircle(0, 0, r * 0.78f, p);
            Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
            hole.setColor(holeColor);
            canvas.drawCircle(0, 0, r * 0.32f, hole);
            canvas.restore();
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
