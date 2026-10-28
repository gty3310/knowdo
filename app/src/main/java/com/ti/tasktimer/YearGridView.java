package com.ti.tasktimer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONObject;

/**
 * 年格（v1.16.0）：GitHub 式 365 天格子，列＝周、行＝周一…周日，
 * 最后一格对齐到今天所在周的周日（未来格画空格）。
 * 着色口径（按习惯类型）：
 *   至少型：做到＝深绿；记了没做到＝浅灰红；没记＝空格
 *   至多型：没发生＝深绿（好）；发生了＝红；没记＝空格
 *   数值型：按 实际÷目标 分三档绿；记了 0＝浅灰红；没记＝空格
 *   累积型：按当日增量 1–2/3–5/6+ 分三档绿；没记＝空格
 *   提醒型：同至少型——读过＝深绿；记了没读＝浅灰红；没记＝空格
 * 点格回调日期（未来日期不回调），供习惯页打开补记/改记。
 */
public class YearGridView extends View {
    public interface OnDayTap { void onDay(String date); }

    private static final int COLS = 53;
    private static final int ROWS = 7;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private JSONObject root;
    private HabitData.Habit habit;
    private OnDayTap cb;
    private String startMonday; // 第一格（某周一）
    private float cell, gap;

    public YearGridView(Context c) {
        super(c);
        gap = 2.5f * c.getResources().getDisplayMetrics().density;
    }

    public void bind(JSONObject root, HabitData.Habit habit, OnDayTap cb) {
        this.root = root;
        this.habit = habit;
        this.cb = cb;
        String today = DayUtil.today();
        String monday = DayUtil.mondayOf(today);
        String endSunday = DayUtil.addDays(monday, 6);
        this.startMonday = DayUtil.addDays(endSunday, -(COLS * 7 - 1));
        invalidate();
    }

    /** 等级：0 空（没记）/ 1..3 绿深浅 / -1 记了但没做到（数值型 0）/ -2 至多型发生了（红）。 */
    static int levelFor(JSONObject root, HabitData.Habit h, String date) {
        Long v = HabitData.logValue(root, date, h.id);
        switch (h.type) {
            case HabitData.TYPE_MIN:
            case HabitData.TYPE_REMIND:
                if (v == null) return 0;
                return v > 0 ? 3 : -1;
            case HabitData.TYPE_MAX:
                if (v == null) return 0;
                return v > 0 ? -2 : 3;
            case HabitData.TYPE_NUM: {
                if (v == null) return 0;
                if (v <= 0) return -1;
                long t = HabitData.effectiveTarget(root, h, DayUtil.mondayOf(date));
                if (t <= 0) return 3;
                double r = v * 1.0 / t;
                return r >= 1 ? 3 : r >= 0.6 ? 2 : 1;
            }
            default: { // sum
                if (v == null || v <= 0) return 0;
                return v >= 6 ? 3 : v >= 3 ? 2 : 1;
            }
        }
    }

    private static int mix(int color, int base, float fracOfBase) {
        int r = (int) (Color.red(color) * (1 - fracOfBase) + Color.red(base) * fracOfBase);
        int g = (int) (Color.green(color) * (1 - fracOfBase) + Color.green(base) * fracOfBase);
        int b = (int) (Color.blue(color) * (1 - fracOfBase) + Color.blue(base) * fracOfBase);
        return Color.rgb(r, g, b);
    }

    private int colorFor(int level) {
        Context c = getContext();
        int surface = Ui.surface(c);
        int green = Ui.green(c);
        switch (level) {
            case 1: return mix(green, surface, 0.55f);
            case 2: return mix(green, surface, 0.25f);
            case 3: return green;
            case -1: return mix(Ui.sub(c), surface, 0.45f);
            case -2: return mix(Ui.danger(c), surface, 0.15f);
            default: return Ui.dark(c) ? Ui.muted(c) : Color.rgb(234, 229, 216);
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        if (w <= 0) w = (int) (340 * getResources().getDisplayMetrics().density);
        cell = (w - (COLS - 1) * gap) / COLS;
        int h = (int) (ROWS * cell + (ROWS - 1) * gap + 4);
        setMeasuredDimension(w, h);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (root == null || habit == null || startMonday == null) return;
        String today = DayUtil.today();
        float radius = Math.max(1.5f, cell * 0.22f);
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                String date = DayUtil.addDays(startMonday, col * 7 + row);
                float x = col * (cell + gap);
                float y = row * (cell + gap) + 2;
                if (date.compareTo(today) > 0) {
                    paint.setColor(Ui.dark(getContext()) ? Ui.bg(getContext()) : Color.rgb(243, 240, 232));
                } else {
                    paint.setColor(colorFor(levelFor(root, habit, date)));
                }
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(x, y, x + cell, y + cell, radius, radius, paint);
                if (date.equals(today)) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(1.2f, cell * 0.12f));
                    paint.setColor(Ui.accent(getContext()));
                    canvas.drawRoundRect(x, y, x + cell, y + cell, radius, radius, paint);
                }
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP || cb == null || startMonday == null) {
            return super.onTouchEvent(e);
        }
        int col = (int) (e.getX() / (cell + gap));
        int row = (int) (e.getY() / (cell + gap));
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return true;
        // 落在格子间隙里不算
        float lx = e.getX() - col * (cell + gap);
        float ly = e.getY() - row * (cell + gap);
        if (lx > cell || ly > cell) return true;
        String date = DayUtil.addDays(startMonday, col * 7 + row);
        if (date.compareTo(DayUtil.today()) <= 0) cb.onDay(date);
        return true;
    }
}
