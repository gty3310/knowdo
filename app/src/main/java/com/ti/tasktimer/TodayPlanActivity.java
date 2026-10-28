package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 「今天安排」页（v1.28.0 feature 9，用户亲自设计、审稿定稿后开发）：
 * 晨间门填完点「开启今天」后进来，在这里把今天排定——目标是任务总量
 * 绝不超过今天可支配时间。构成（照设计稿，紧凑小字矮卡）：
 * - 吸顶时间账：还剩可用／已安排（已安排＝今日已排块时长合计，块长本身
 *   就是 ×1.5 口径；预算＝填了的可支配，未填按 1440−睡眠−锁时估算），超排变红
 * - 今日时段横条：今晨起床→明晨起床；锁时段可拖拽新建/移位/改界、可命名，
 *   睡眠段可设且长期保存；旁有数字条展开做精细编辑
 * - 快速输入「今天可支配 X 小时」＋ⓘ说明
 * - 任务卡：已用/还剩（还剩可点改）、截止两钮、重要性四钮当场改，点卡展开全字段编辑
 * - 底部「排进今天的日程」：走既有自动排程（避锁时/睡眠），排不下的当页点名
 */
public class TodayPlanActivity extends Activity {
    private TaskStore store;
    private String logicToday;

    private TextView remainVal, arrangedVal, ledgerNote;
    private View barUsed, barOver, barRest;
    private FrameLayout strip;
    private TextView scaleWake, scaleEnd;
    private LinearLayout digitsBox, cardsBox;
    private TextView digitsToggle, overflowNote;
    private EditText freeInput;
    private boolean digitsOpen = false;
    /** 数字条的工作副本（墙钟 HH:mm 字符串行），保存时才落 DayAvail。 */
    private final List<String[]> digitRows = new ArrayList<>(); // {kind,name,start,end} kind: sleep|lock

    // 横条拖拽状态
    private static final int DRAG_NONE = 0, DRAG_CREATE = 1, DRAG_MOVE = 2, DRAG_LEFT = 3, DRAG_RIGHT = 4;
    private int dragMode = DRAG_NONE;
    private int dragIdx = -1;         // 被拖的锁时下标（locksFor 排序后）
    private int dragAnchor = 0;       // 按下时的轴分钟
    private int dragGrabOff = 0;      // move 时按下点距段首的偏移
    private DayAvail.Seg dragOrig;    // 被拖段的原值
    private View draftView;           // 拖拽中的预览块
    private boolean dragMoved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        store = new TaskStore(this);
        store.load();
        logicToday = Rollover.logicalToday(this);
        buildUi();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.load();
        logicToday = Rollover.logicalToday(this);
        refreshAll();
    }

    private boolean en() { return Strings.en(); }
    private String s(String zh, String en) { return en() ? en : zh; }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private int cardBg() { return Ui.dark(this) ? Ui.muted(this) : Color.rgb(255, 253, 248); }

    private GradientDrawable round(int fill, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable roundStroke(int fill, int stroke, int radiusDp) {
        GradientDrawable g = round(fill, radiusDp);
        g.setStroke(dp(1), stroke);
        return g;
    }

    // ---------------- 骨架 ----------------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));

        // ---- 吸顶时间账 ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(18), dp(8), dp(18), dp(6));
        TextView title = new TextView(this);
        title.setText(s("今天安排", "Plan today"));
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(Ui.text(this));
        head.addView(title);

        LinearLayout ledger = new LinearLayout(this);
        ledger.setOrientation(LinearLayout.HORIZONTAL);
        ledger.setPadding(0, dp(5), 0, 0);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView labL = smallLabel(s("还剩可用", "Left"));
        remainVal = bigNum();
        left.addView(labL);
        left.addView(remainVal);
        ledger.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView labR = smallLabel(s("已安排", "Planned"));
        labR.setGravity(Gravity.END);
        arrangedVal = bigNum();
        arrangedVal.setGravity(Gravity.END);
        right.addView(labR);
        right.addView(arrangedVal);
        ledger.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(ledger);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(round(Ui.line(this), 2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        blp.topMargin = dp(4);
        barUsed = new View(this);
        barUsed.setBackgroundColor(Ui.text(this));
        barOver = new View(this);
        barOver.setBackgroundColor(Ui.accent(this));
        barRest = new View(this);
        bar.addView(barUsed);
        bar.addView(barOver);
        bar.addView(barRest);
        head.addView(bar, blp);

        ledgerNote = new TextView(this);
        ledgerNote.setTextSize(10);
        ledgerNote.setTextColor(Ui.sub(this));
        ledgerNote.setPadding(0, dp(2), 0, 0);
        head.addView(ledgerNote);
        root.addView(head);
        View headLine = new View(this);
        headLine.setBackgroundColor(Ui.line(this));
        root.addView(headLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // ---- 滚动区 ----
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(4), dp(18), dp(22));
        scroll.addView(col);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 今日时段
        LinearLayout secRow = new LinearLayout(this);
        secRow.setOrientation(LinearLayout.HORIZONTAL);
        secRow.setGravity(Gravity.CENTER_VERTICAL);
        secRow.setPadding(0, dp(8), 0, dp(5));
        TextView secT = new TextView(this);
        secT.setText(s("今日时段", "Today's hours"));
        secT.setTextSize(11);
        secT.setTextColor(Ui.sub(this));
        secRow.addView(secT, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        digitsToggle = new TextView(this);
        digitsToggle.setText(s("展开数字条 ▾", "Numbers ▾"));
        digitsToggle.setTextSize(12);
        digitsToggle.setTextColor(Ui.text(this));
        digitsToggle.setPadding(dp(10), dp(3), dp(10), dp(3));
        digitsToggle.setBackground(roundStroke(cardBg(), Ui.line(this), 20));
        digitsToggle.setOnClickListener(v -> {
            digitsOpen = !digitsOpen;
            digitsToggle.setText(digitsOpen ? s("收起数字条 ▴", "Numbers ▴") : s("展开数字条 ▾", "Numbers ▾"));
            digitsBox.setVisibility(digitsOpen ? View.VISIBLE : View.GONE);
            if (digitsOpen) rebuildDigitRows();
        });
        secRow.addView(digitsToggle);
        col.addView(secRow);

        strip = new FrameLayout(this);
        strip.setBackground(roundStroke(cardBg(), Ui.line(this), 12));
        col.addView(strip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        attachStripTouch();

        LinearLayout scale = new LinearLayout(this);
        scale.setOrientation(LinearLayout.HORIZONTAL);
        scale.setPadding(0, dp(3), 0, 0);
        scaleWake = scaleLabel(Gravity.START);
        scale.addView(scaleWake, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView sc12 = scaleLabel(Gravity.CENTER);
        sc12.setText("12:00");
        scale.addView(sc12, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView sc18 = scaleLabel(Gravity.CENTER);
        sc18.setText("18:00");
        scale.addView(sc18, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        scaleEnd = scaleLabel(Gravity.END);
        scale.addView(scaleEnd, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(scale);

        digitsBox = new LinearLayout(this);
        digitsBox.setOrientation(LinearLayout.VERTICAL);
        digitsBox.setBackground(roundStroke(cardBg(), Ui.line(this), 12));
        digitsBox.setPadding(dp(14), dp(10), dp(14), dp(10));
        digitsBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(8);
        col.addView(digitsBox, dlp);

        // 可支配快速输入
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.setPadding(0, dp(8), 0, 0);
        TextView qLab = new TextView(this);
        qLab.setText(s("今天可支配", "Free today"));
        qLab.setTextSize(12);
        qLab.setTextColor(Ui.text(this));
        quick.addView(qLab);
        freeInput = new EditText(this);
        freeInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        freeInput.setTextSize(15);
        freeInput.setTypeface(freeInput.getTypeface(), Typeface.BOLD);
        freeInput.setGravity(Gravity.CENTER);
        freeInput.setTextColor(Ui.text(this));
        freeInput.setBackground(roundStroke(Color.TRANSPARENT, Ui.text(this), 4));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.leftMargin = dp(8);
        quick.addView(freeInput, flp);
        TextView qUnit = new TextView(this);
        qUnit.setText(s("小时", "hours"));
        qUnit.setTextSize(12);
        qUnit.setTextColor(Ui.text(this));
        qUnit.setPadding(dp(8), 0, 0, 0);
        quick.addView(qUnit);
        TextView info = new TextView(this);
        info.setText("i");
        info.setTextSize(12);
        info.setGravity(Gravity.CENTER);
        info.setTextColor(Ui.sub(this));
        info.setBackground(roundStroke(Color.TRANSPARENT, Ui.sub(this), 20));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(18), dp(18));
        ilp.leftMargin = dp(10);
        info.setLayoutParams(ilp);
        info.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(s("可支配时间就是今天能用来安排任务的时间",
                        "Your free time is the time today that can hold tasks"))
                .setPositiveButton(s("知道了", "OK"), null)
                .show());
        quick.addView(info);
        col.addView(quick);
        freeInput.setOnFocusChangeListener((v, has) -> { if (!has) applyFreeInput(); });
        freeInput.setOnEditorActionListener((v, id, e) -> { applyFreeInput(); return false; });

        // 任务
        LinearLayout taskSec = new LinearLayout(this);
        taskSec.setOrientation(LinearLayout.HORIZONTAL);
        taskSec.setGravity(Gravity.CENTER_VERTICAL);
        taskSec.setPadding(0, dp(8), 0, 0);
        TextView tt = new TextView(this);
        tt.setText(s("任务", "Tasks"));
        tt.setTextSize(11);
        tt.setTextColor(Ui.sub(this));
        taskSec.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView th = new TextView(this);
        th.setText(s("点卡片可展开编辑", "Tap a card to edit"));
        th.setTextSize(12);
        th.setTextColor(Ui.sub(this));
        taskSec.addView(th);
        col.addView(taskSec);

        cardsBox = new LinearLayout(this);
        cardsBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(cardsBox);

        overflowNote = new TextView(this);
        overflowNote.setTextSize(12);
        overflowNote.setTextColor(Ui.accent(this));
        overflowNote.setPadding(0, dp(8), 0, 0);
        overflowNote.setVisibility(View.GONE);
        col.addView(overflowNote);

        // ---- 固定底 ----
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(dp(18), dp(6), dp(18), dp(10));
        TextView btn = new TextView(this);
        btn.setText(s("排进今天的日程", "Plan into today"));
        btn.setTextSize(13);
        btn.setGravity(Gravity.CENTER);
        btn.setTextColor(Color.WHITE);
        btn.setPadding(0, dp(7), 0, dp(7));
        btn.setBackground(round(Ui.text(this), 10));
        btn.setOnClickListener(v -> onPlanIntoToday());
        foot.addView(btn);
        root.addView(foot);

        setContentView(root);
    }

    private TextView smallLabel(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(11);
        v.setTextColor(Ui.sub(this));
        return v;
    }

    private TextView bigNum() {
        TextView v = new TextView(this);
        v.setTextSize(16);
        v.setTypeface(v.getTypeface(), Typeface.BOLD);
        v.setTextColor(Ui.text(this));
        return v;
    }

    private TextView scaleLabel(int gravity) {
        TextView v = new TextView(this);
        v.setTextSize(10);
        v.setTextColor(Ui.sub(this));
        v.setGravity(gravity);
        return v;
    }

    // ---------------- 刷新 ----------------

    private void refreshAll() {
        refreshLedger();
        refreshStrip();
        if (digitsOpen) rebuildDigitRows();
        refreshCards();
        double fh = DayAvail.freeHours(this, logicToday);
        String cur = freeInput.getText().toString().trim();
        String want = fh >= 0 ? trimNum(fh) : "";
        if (!cur.equals(want) && !freeInput.hasFocus()) freeInput.setText(want);
    }

    private static String trimNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** 时间账：已安排＝今日 schedule 块时长合计；还剩＝预算−已安排，负数变红。 */
    private void refreshLedger() {
        List<LocalAutoScheduler.Block> blocks = LocalAutoScheduler.blocksOf(
                SyncManager.latestSchedule(this), TaskItem.today());
        int arranged = 0;
        for (LocalAutoScheduler.Block b : blocks) arranged += b.endMin - b.startMin;
        int budget = DayAvail.budgetMin(this, logicToday);
        int remain = budget - arranged;
        arrangedVal.setText(DayAccount.fmtDur(arranged));
        remainVal.setText(remain < 0 ? "−" + DayAccount.fmtDur(-remain) : DayAccount.fmtDur(remain));
        remainVal.setTextColor(remain < 0 ? Ui.accent(this) : Ui.text(this));
        int usedW = Math.min(arranged, budget);
        int overW = Math.max(0, arranged - budget);
        int restW = Math.max(0, budget - arranged);
        setWeight(barUsed, usedW);
        setWeight(barOver, overW);
        setWeight(barRest, restW);
        if (remain < 0) {
            ledgerNote.setText(s("已经排超了，减一点或把可支配填大些", "Over budget — trim tasks or raise free time"));
            ledgerNote.setTextColor(Ui.accent(this));
        } else if (DayAvail.freeHours(this, logicToday) < 0) {
            ledgerNote.setText(s("可支配还没填，按全天可用估算", "Free time not set — estimated from the whole day"));
            ledgerNote.setTextColor(Ui.sub(this));
        } else {
            ledgerNote.setText("");
        }
    }

    private void setWeight(View v, int w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0, w));
        v.setLayoutParams(lp);
        v.setVisibility(w > 0 ? View.VISIBLE : View.GONE);
    }

    private void applyFreeInput() {
        String raw = freeInput.getText().toString().trim();
        if (raw.isEmpty()) {
            if (DayAvail.freeHours(this, logicToday) >= 0) {
                DayAvail.setFreeHours(this, logicToday, -1);
                refreshLedger();
            }
            return;
        }
        try {
            double h = Double.parseDouble(raw);
            if (h < 0 || h > 24) throw new NumberFormatException();
            DayAvail.setFreeHours(this, logicToday, h);
            refreshLedger();
        } catch (Exception e) {
            Toast.makeText(this, s("可支配填 0 到 24 的小时数", "Enter hours between 0 and 24"),
                    Toast.LENGTH_SHORT).show();
            refreshAll();
        }
    }

    // ---------------- 时段横条 ----------------

    /** 睡眠在轴上的起点（轴零点＝起床，睡眠恒在轴尾）。 */
    private int sleepAxisStart() {
        return 1440 - DayAvail.sleepDurMin(this);
    }

    private void refreshStrip() {
        strip.removeAllViews();
        draftView = null;
        int w = strip.getWidth();
        if (w <= 0) {
            strip.post(this::refreshStrip);
            return;
        }
        List<DayAvail.Seg> locks = DayAvail.locksFor(this, logicToday);
        int sleepS = sleepAxisStart();
        // 组装全轴分段：空闲 / 锁时 / 睡眠
        List<int[]> spans = new ArrayList<>(); // {s, e, kind, lockIdx} kind 0 空闲 1 锁时 2 睡眠
        int cur = 0;
        List<DayAvail.Seg> sorted = new ArrayList<>(locks);
        for (int i = 0; i < sorted.size(); i++) {
            DayAvail.Seg g = sorted.get(i);
            if (g.s > cur) spans.add(new int[]{cur, Math.min(g.s, sleepS), 0, -1});
            spans.add(new int[]{g.s, g.e, 1, i});
            cur = Math.max(cur, g.e);
        }
        if (cur < sleepS) spans.add(new int[]{cur, sleepS, 0, -1});
        spans.add(new int[]{sleepS, 1440, 2, -1});
        for (int[] sp : spans) {
            if (sp[1] - sp[0] < 8) continue; // 太窄不画（拖拽命中仍走数据判定）
            TextView seg = new TextView(this);
            seg.setTextSize(9);
            seg.setGravity(Gravity.CENTER);
            seg.setSingleLine(true);
            seg.setEllipsize(android.text.TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    Math.max(dp(6), (int) ((sp[1] - sp[0]) * (long) w / 1440)), dp(16));
            lp.leftMargin = (int) (sp[0] * (long) w / 1440);
            lp.topMargin = dp(3);
            if (sp[2] == 2) {
                seg.setText(s("睡觉", "Sleep"));
                seg.setTextColor(Ui.bg(this));
                seg.setBackground(round(Ui.text(this), 6));
            } else if (sp[2] == 1) {
                DayAvail.Seg g = sorted.get(sp[3]);
                seg.setText(g.name.isEmpty() ? s("锁时", "Locked") : g.name);
                seg.setTextColor(Ui.text(this));
                seg.setBackground(round(Ui.dark(this) ? Color.rgb(70, 82, 74) : Color.rgb(223, 227, 218), 6));
            } else {
                seg.setText(s("空闲", "Free"));
                seg.setTextColor(Ui.sub(this));
                GradientDrawable d = round(Color.TRANSPARENT, 6);
                d.setStroke(dp(1), Ui.dark(this) ? Color.rgb(96, 108, 100) : Color.rgb(201, 194, 176),
                        dp(4), dp(3));
                seg.setBackground(d);
            }
            strip.addView(seg, lp);
        }
        int wake = DayAvail.wakeMin(this);
        scaleWake.setText(s("起床 ", "Up ") + LocalAutoScheduler.hhmm(wake));
        scaleEnd.setText(s("明早 ", "Next ") + LocalAutoScheduler.hhmm(wake));
    }

    private int xToAxis(float x) {
        int w = strip.getWidth();
        if (w <= 0) return 0;
        int a = (int) (x * 1440f / w);
        return Math.max(0, Math.min(1440, a));
    }

    private int snap5(int axis) { return Math.round(axis / 5f) * 5; }

    private void attachStripTouch() {
        strip.setOnTouchListener((v, ev) -> {
            int axis = xToAxis(ev.getX());
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    dragMoved = false;
                    dragAnchor = axis;
                    List<DayAvail.Seg> locks = DayAvail.locksFor(this, logicToday);
                    dragIdx = -1;
                    dragMode = DRAG_NONE;
                    int edge = dp(12) * 1440 / Math.max(1, strip.getWidth());
                    for (int i = 0; i < locks.size(); i++) {
                        DayAvail.Seg g = locks.get(i);
                        if (axis >= g.s && axis <= g.e) {
                            dragIdx = i;
                            dragOrig = new DayAvail.Seg(g.s, g.e, g.name);
                            if (axis - g.s <= edge) dragMode = DRAG_LEFT;
                            else if (g.e - axis <= edge) dragMode = DRAG_RIGHT;
                            else { dragMode = DRAG_MOVE; dragGrabOff = axis - g.s; }
                            break;
                        }
                    }
                    if (dragMode == DRAG_NONE && axis < sleepAxisStart()) dragMode = DRAG_CREATE;
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (dragMode == DRAG_NONE) return true;
                    if (Math.abs(axis - dragAnchor) > 10) dragMoved = true;
                    if (!dragMoved) return true;
                    int s, e;
                    if (dragMode == DRAG_CREATE) {
                        s = Math.min(dragAnchor, axis);
                        e = Math.max(dragAnchor, axis);
                    } else if (dragMode == DRAG_MOVE) {
                        int len = dragOrig.e - dragOrig.s;
                        s = Math.max(0, Math.min(sleepAxisStart() - len, axis - dragGrabOff));
                        e = s + len;
                    } else if (dragMode == DRAG_LEFT) {
                        s = Math.min(axis, dragOrig.e - 15);
                        e = dragOrig.e;
                    } else {
                        s = dragOrig.s;
                        e = Math.max(axis, dragOrig.s + 15);
                    }
                    showDraft(snap5(s), snap5(Math.max(snap5(s), e)));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    boolean wasDrag = dragMoved && dragMode != DRAG_NONE;
                    int a0 = dragAnchor;
                    if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                        if (wasDrag) commitDrag(); // 先落定（读 draftView 几何），再藏草稿
                        else handleStripTap(a0);
                    }
                    hideDraft();
                    if (ev.getActionMasked() == MotionEvent.ACTION_CANCEL) refreshStrip();
                    dragMode = DRAG_NONE;
                    dragIdx = -1;
                    dragMoved = false;
                    return true;
                }
            }
            return true;
        });
    }

    private void showDraft(int s, int e) {
        int w = strip.getWidth();
        if (w <= 0) return;
        if (draftView == null) {
            draftView = new View(this);
            draftView.setBackground(round(Color.argb(120, 31, 47, 42), 6));
            strip.addView(draftView);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.max(dp(4), (int) ((e - s) * (long) w / 1440)), dp(16));
        lp.leftMargin = (int) (s * (long) w / 1440);
        lp.topMargin = dp(3);
        draftView.setLayoutParams(lp);
        draftView.setVisibility(View.VISIBLE);
    }

    private void hideDraft() {
        if (draftView != null) draftView.setVisibility(View.GONE);
    }

    /** 拖拽落定：草稿段的当前几何在 draftView 上，反推轴分钟后校验落盘。 */
    private void commitDrag() {
        if (draftView == null) { refreshStrip(); return; }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) draftView.getLayoutParams();
        int w = strip.getWidth();
        int s = (int) (lp.leftMargin * 1440L / Math.max(1, w));
        int e = s + (int) (lp.width * 1440L / Math.max(1, w));
        s = snap5(Math.max(0, s));
        e = snap5(Math.min(sleepAxisStart(), e));
        if (e - s < 15) {
            if (dragMode == DRAG_CREATE) {
                Toast.makeText(this, s("太短了，按住拖出一段再松手", "Too short — drag a longer stretch"),
                        Toast.LENGTH_SHORT).show();
            }
            refreshStrip();
            return;
        }
        List<DayAvail.Seg> locks = DayAvail.locksFor(this, logicToday);
        if (dragMode == DRAG_CREATE) {
            if (overlapsAny(locks, s, e, -1)) {
                Toast.makeText(this, s("和别的锁时撞了", "Overlaps another locked stretch"),
                        Toast.LENGTH_SHORT).show();
                refreshStrip();
                return;
            }
            askLockName(s, e);
            return;
        }
        if (dragIdx < 0 || dragIdx >= locks.size()) { refreshStrip(); return; }
        if (overlapsAny(locks, s, e, dragIdx)) {
            Toast.makeText(this, s("和别的锁时撞了", "Overlaps another locked stretch"),
                    Toast.LENGTH_SHORT).show();
            refreshStrip();
            return;
        }
        DayAvail.Seg g = locks.get(dragIdx);
        g.s = s;
        g.e = e;
        DayAvail.setLocks(this, logicToday, locks);
        refreshAll();
    }

    private boolean overlapsAny(List<DayAvail.Seg> locks, int s, int e, int skipIdx) {
        for (int i = 0; i < locks.size(); i++) {
            if (i == skipIdx) continue;
            DayAvail.Seg g = locks.get(i);
            if (s < g.e && g.s < e) return true;
        }
        return false;
    }

    private void handleStripTap(int axis) {
        if (axis >= sleepAxisStart()) {
            showSleepDialog();
            return;
        }
        List<DayAvail.Seg> locks = DayAvail.locksFor(this, logicToday);
        for (int i = 0; i < locks.size(); i++) {
            DayAvail.Seg g = locks.get(i);
            if (axis >= g.s && axis <= g.e) {
                showLockDialog(i);
                return;
            }
        }
        Toast.makeText(this, s("按住拖一段可以新建锁时", "Press and drag to add a locked stretch"),
                Toast.LENGTH_SHORT).show();
    }

    private void askLockName(final int s, final int e) {
        final EditText input = new EditText(this);
        input.setHint(s("给这段起个名，如课程名", "Name it, e.g. a class"));
        input.setTextSize(14);
        new AlertDialog.Builder(this)
                .setTitle(s("新锁时", "New locked stretch"))
                .setView(input)
                .setPositiveButton(s("建好", "Add"), (d, w) -> {
                    List<DayAvail.Seg> locks = DayAvail.locksFor(this, logicToday);
                    locks.add(new DayAvail.Seg(s, e, input.getText().toString().trim()));
                    DayAvail.setLocks(this, logicToday, locks);
                    refreshAll();
                })
                .setNegativeButton(s("不建了", "Cancel"), (d, w) -> refreshStrip())
                .setOnCancelListener(d -> refreshStrip())
                .show();
    }

    /** 时间输入小行：标签＋起＋止两个 HH:mm 输入框。 */
    private LinearLayout timeRow(String label, EditText inS, EditText inE) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextSize(13);
        lab.setTextColor(Ui.text(this));
        row.addView(lab, new LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(inS, new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView dash = new TextView(this);
        dash.setText(" – ");
        dash.setTextColor(Ui.sub(this));
        row.addView(dash);
        row.addView(inE, new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private EditText timeInput(String hhmm) {
        EditText e = new EditText(this);
        e.setText(hhmm);
        e.setTextSize(14);
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        return e;
    }

    private void showLockDialog(final int idx) {
        List<DayAvail.Seg> locks = DayAvail.locksFor(this, logicToday);
        if (idx < 0 || idx >= locks.size()) return;
        final DayAvail.Seg g = locks.get(idx);
        int wake = DayAvail.wakeMin(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText nameIn = new EditText(this);
        nameIn.setText(g.name);
        nameIn.setHint(s("名字，如课程名", "Name"));
        nameIn.setTextSize(14);
        box.addView(nameIn);
        final EditText inS = timeInput(LocalAutoScheduler.hhmm((wake + g.s) % 1440));
        final EditText inE = timeInput(LocalAutoScheduler.hhmm((wake + g.e) % 1440));
        box.addView(timeRow(s("锁时", "Locked"), inS, inE));
        new AlertDialog.Builder(this)
                .setTitle(s("锁时段", "Locked stretch"))
                .setView(box)
                .setPositiveButton(s("保存", "Save"), (d, w) -> {
                    int ws = LocalAutoScheduler.parseHhmm(inS.getText().toString().trim());
                    int we = LocalAutoScheduler.parseHhmm(inE.getText().toString().trim());
                    int as = wallToAxis(ws), ae = wallToAxis(we);
                    if (ws < 0 || we < 0 || ae <= as || ae - as < 15) {
                        Toast.makeText(this, s("时间不对：结束要晚于开始、至少 15 分钟",
                                "Bad times — end must be after start, at least 15 min"), Toast.LENGTH_LONG).show();
                        return;
                    }
                    List<DayAvail.Seg> ls = DayAvail.locksFor(this, logicToday);
                    if (overlapsAny(ls, as, ae, idx)) {
                        Toast.makeText(this, s("和别的锁时撞了", "Overlaps another locked stretch"),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DayAvail.Seg t = ls.get(idx);
                    t.s = as;
                    t.e = ae;
                    t.name = nameIn.getText().toString().trim();
                    DayAvail.setLocks(this, logicToday, ls);
                    refreshAll();
                })
                .setNeutralButton(s("删除", "Delete"), (d, w) -> {
                    List<DayAvail.Seg> ls = DayAvail.locksFor(this, logicToday);
                    ls.remove(idx);
                    DayAvail.setLocks(this, logicToday, ls);
                    refreshAll();
                })
                .setNegativeButton(s("取消", "Cancel"), null)
                .show();
    }

    private void showSleepDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText inS = timeInput(LocalAutoScheduler.hhmm(DayAvail.sleepStartMin(this)));
        final EditText inE = timeInput(LocalAutoScheduler.hhmm(DayAvail.sleepEndMin(this)));
        box.addView(timeRow(s("睡眠", "Sleep"), inS, inE));
        TextView note = new TextView(this);
        note.setText(s("睡眠每天沿用，不用每天重填", "Sleep carries over every day"));
        note.setTextSize(11);
        note.setTextColor(Ui.sub(this));
        note.setPadding(0, dp(6), 0, 0);
        box.addView(note);
        new AlertDialog.Builder(this)
                .setTitle(s("睡觉时间", "Sleep hours"))
                .setView(box)
                .setPositiveButton(s("保存", "Save"), (d, w) -> {
                    int ss = LocalAutoScheduler.parseHhmm(inS.getText().toString().trim());
                    int se = LocalAutoScheduler.parseHhmm(inE.getText().toString().trim());
                    if (ss < 0 || se < 0 || ss == se) {
                        Toast.makeText(this, s("时间不对", "Bad times"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DayAvail.setSleep(this, ss, se);
                    refreshAll();
                })
                .setNegativeButton(s("取消", "Cancel"), null)
                .show();
    }

    /** 墙钟分钟 → 轴分钟（以起床为零点回绕）。 */
    private int wallToAxis(int wallMin) {
        int wake = DayAvail.wakeMin(this);
        return ((wallMin - wake) % 1440 + 1440) % 1440;
    }

    // ---------------- 数字条 ----------------

    /** 从 DayAvail 现值重建数字条工作副本并渲染（睡眠行恒在第一行）。 */
    private void rebuildDigitRows() {
        digitRows.clear();
        digitRows.add(new String[]{"sleep", "",
                LocalAutoScheduler.hhmm(DayAvail.sleepStartMin(this)),
                LocalAutoScheduler.hhmm(DayAvail.sleepEndMin(this))});
        int wake = DayAvail.wakeMin(this);
        for (DayAvail.Seg g : DayAvail.locksFor(this, logicToday)) {
            digitRows.add(new String[]{"lock", g.name,
                    LocalAutoScheduler.hhmm((wake + g.s) % 1440),
                    LocalAutoScheduler.hhmm((wake + g.e) % 1440)});
        }
        renderDigitRows();
    }

    private void renderDigitRows() {
        digitsBox.removeAllViews();
        for (int i = 0; i < digitRows.size(); i++) {
            final int idx = i;
            final String[] r = digitRows.get(i);
            boolean isSleep = "sleep".equals(r[0]);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(3), 0, dp(3));
            TextView lab = new TextView(this);
            lab.setText(isSleep ? s("睡眠", "Sleep") : s("锁时", "Lock"));
            lab.setTextSize(12);
            lab.setTextColor(Ui.sub(this));
            row.addView(lab, new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT));
            if (!isSleep) {
                EditText nameIn = digitInput(r[1], false);
                nameIn.setHint(s("名字", "Name"));
                nameIn.addTextChangedListener(new SimpleTextWatcher() {
                    @Override public void onChanged(String t) { r[1] = t; }
                });
                row.addView(nameIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            EditText inS = digitInput(r[2], true);
            inS.addTextChangedListener(new SimpleTextWatcher() {
                @Override public void onChanged(String t) { r[2] = t; }
            });
            row.addView(inS, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView dash = new TextView(this);
            dash.setText("–");
            dash.setTextColor(Ui.sub(this));
            dash.setPadding(dp(2), 0, dp(2), 0);
            row.addView(dash);
            EditText inE = digitInput(r[3], true);
            inE.addTextChangedListener(new SimpleTextWatcher() {
                @Override public void onChanged(String t) { r[3] = t; }
            });
            row.addView(inE, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
            if (!isSleep) {
                TextView del = new TextView(this);
                del.setText(s("删", "Del"));
                del.setTextSize(12);
                del.setTextColor(Ui.accent(this));
                del.setPadding(dp(8), dp(2), 0, dp(2));
                del.setOnClickListener(v -> {
                    digitRows.remove(idx);
                    renderDigitRows();
                });
                row.addView(del);
            }
            digitsBox.addView(row);
        }
        LinearLayout footRow = new LinearLayout(this);
        footRow.setOrientation(LinearLayout.HORIZONTAL);
        footRow.setPadding(0, dp(6), 0, 0);
        TextView add = new TextView(this);
        add.setText(s("＋ 加一段锁时", "＋ Add locked stretch"));
        add.setTextSize(12);
        add.setTextColor(Ui.text(this));
        add.setPadding(dp(8), dp(3), dp(8), dp(3));
        add.setBackground(roundStroke(cardBg(), Ui.line(this), 20));
        add.setOnClickListener(v -> {
            digitRows.add(new String[]{"lock", "", "", ""});
            renderDigitRows();
        });
        footRow.addView(add, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView save = new TextView(this);
        save.setText(s("保存数字条", "Save"));
        save.setTextSize(12);
        save.setTextColor(Color.WHITE);
        save.setPadding(dp(12), dp(3), dp(12), dp(3));
        save.setBackground(round(Ui.text(this), 20));
        save.setOnClickListener(v -> saveDigitRows());
        footRow.addView(save);
        digitsBox.addView(footRow);
    }

    private EditText digitInput(String text, boolean time) {
        EditText e = new EditText(this);
        e.setText(text);
        e.setTextSize(13);
        e.setGravity(Gravity.CENTER);
        e.setTextColor(Ui.text(this));
        if (time) {
            e.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        }
        return e;
    }

    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        public abstract void onChanged(String t);
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { onChanged(s.toString()); }
        @Override public void afterTextChanged(android.text.Editable e) { }
    }

    /** 数字条保存：整表校验（时间合法、锁时不重叠、长 ≥15 分）后一次落盘。 */
    private void saveDigitRows() {
        int wake = DayAvail.wakeMin(this);
        Integer sleepS = null, sleepE = null;
        List<DayAvail.Seg> locks = new ArrayList<>();
        for (String[] r : digitRows) {
            int ws = LocalAutoScheduler.parseHhmm(r[2].trim());
            int we = LocalAutoScheduler.parseHhmm(r[3].trim());
            if (ws < 0 || we < 0) {
                Toast.makeText(this, s("有时间没填对，格式如 09:30", "A time is off — use 09:30 format"),
                        Toast.LENGTH_LONG).show();
                return;
            }
            if ("sleep".equals(r[0])) {
                if (ws == we) {
                    Toast.makeText(this, s("睡眠起止不能一样", "Sleep start and end can't match"),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                sleepS = ws;
                sleepE = we;
            } else {
                int as = ((ws - wake) % 1440 + 1440) % 1440;
                int ae = ((we - wake) % 1440 + 1440) % 1440;
                if (ae <= as || ae - as < 15) {
                    Toast.makeText(this, s("锁时结束要晚于开始、至少 15 分钟",
                            "Locked stretch: end after start, at least 15 min"), Toast.LENGTH_LONG).show();
                    return;
                }
                locks.add(new DayAvail.Seg(as, ae, r[1].trim()));
            }
        }
        locks.sort(Comparator.comparingInt(g -> g.s));
        for (int i = 1; i < locks.size(); i++) {
            if (locks.get(i).s < locks.get(i - 1).e) {
                Toast.makeText(this, s("锁时段互相撞了", "Locked stretches overlap each other"),
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (sleepS != null) DayAvail.setSleep(this, sleepS, sleepE);
        DayAvail.setLocks(this, logicToday, locks);
        digitsOpen = true;
        refreshAll();
        Toast.makeText(this, s("时段已保存", "Hours saved"), Toast.LENGTH_SHORT).show();
    }

    // ---------------- 任务卡 ----------------

    private final TaskEditSheet.Host editHost = new TaskEditSheet.Host() {
        @Override public void onSaved(TaskItem t, boolean created) { refreshAll(); }
        @Override public void onFocusRule(TaskItem t) { } // 与主页 FAB 新建宿主同口径：本页不接专注规则对话框
        @Override public void onResetTimer(TaskItem t) { }
    };

    /** 今日候选：未删、未完成、（每日重复 ∥ deadline ≤ 逻辑今天），按 candidateOrder 排。 */
    private List<TaskItem> todayCandidates() {
        List<LocalAutoScheduler.Candidate> cands = LocalAutoScheduler.snapshot(store);
        java.util.Map<String, LocalAutoScheduler.Candidate> byId = new java.util.HashMap<>();
        for (LocalAutoScheduler.Candidate cd : cands) byId.put(cd.id, cd);
        List<TaskItem> out = new ArrayList<>();
        for (TaskItem t : store.tasks) {
            if (t.deleted || t.isDoneToday()) continue;
            boolean daily = "daily".equals(t.repeat);
            boolean due = t.deadline != null && logicToday != null
                    && t.deadline.compareTo(logicToday) <= 0;
            if (daily || due) out.add(t);
        }
        Comparator<LocalAutoScheduler.Candidate> ord = LocalAutoScheduler.candidateOrder(logicToday);
        out.sort((a, b) -> {
            LocalAutoScheduler.Candidate ca = byId.get(a.id), cb = byId.get(b.id);
            if (ca == null || cb == null) return 0;
            return ord.compare(ca, cb);
        });
        return out;
    }

    private void refreshCards() {
        cardsBox.removeAllViews();
        List<TaskItem> list = todayCandidates();
        if (list.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(s("今天没有要排的任务", "Nothing to plan for today"));
            none.setTextSize(12);
            none.setTextColor(Ui.sub(this));
            none.setPadding(0, dp(10), 0, 0);
            cardsBox.addView(none);
            return;
        }
        for (TaskItem t : list) cardsBox.addView(buildCard(t));
    }

    private View buildCard(final TaskItem t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundStroke(cardBg(), Ui.line(this), 10));
        card.setPadding(dp(9), dp(6), dp(9), dp(6));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(6);
        card.setLayoutParams(clp);

        TextView nm = new TextView(this);
        nm.setText(t.title);
        nm.setTextSize(13);
        nm.setTypeface(nm.getTypeface(), Typeface.BOLD);
        nm.setTextColor(Ui.text(this));
        card.addView(nm);

        final int used = (int) (SessionLog.todayMsForTask(this, t.id) / 60000);
        boolean daily = "daily".equals(t.repeat);
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setPadding(0, dp(2), 0, 0);
        TextView m1 = new TextView(this);
        m1.setTextSize(11);
        m1.setTextColor(Ui.sub(this));
        m1.setText(daily ? s("每日重复", "Daily") : s("已用 ", "Used ") + used + s(" 分", " min"));
        meta.addView(m1);
        TextView m2 = new TextView(this);
        m2.setTextSize(11);
        m2.setTextColor(Ui.sub(this));
        m2.setText(s("　还剩 ", "　Left "));
        meta.addView(m2);
        final int remain = Math.max(0, t.durationMinutes - used);
        TextView m3 = new TextView(this);
        m3.setText(remain + s(" 分", " min"));
        m3.setTextSize(11);
        m3.setTypeface(m3.getTypeface(), Typeface.BOLD);
        m3.setTextColor(Ui.text(this));
        m3.setPaintFlags(m3.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        m3.setOnClickListener(v -> editRemaining(t, used, remain));
        meta.addView(m3);
        card.addView(meta);

        if (!daily) {
            LinearLayout row = chipRow(s("截止日期", "Due"));
            row.addView(chip(s("改到今天", "Move to today"),
                    logicToday != null && logicToday.equals(t.deadline),
                    v -> {
                        t.deadline = logicToday;
                        persistTask(t);
                    }));
            TextView cancel = chip(s("取消截止日期", "Clear due date"), false,
                    v -> {
                        t.deadline = null;
                        persistTask(t);
                    });
            if (t.deadline == null) cancel.setTextColor(Ui.sub(this));
            row.addView(cancel);
            card.addView(row);
        }

        LinearLayout prow = chipRow(s("重要性", "Priority"));
        String[] keys = {"must", "important", "secondary", "leisure"};
        for (final String k : keys) {
            boolean on = TaskItem.priorityRank(t.priority) == TaskItem.priorityRank(k);
            prow.addView(chip(TaskItem.priorityLabel(k, en()), on, v -> {
                t.priority = k;
                persistTask(t);
            }));
        }
        card.addView(prow);

        card.setOnClickListener(v -> TaskEditSheet.showEdit(this, store, t, editHost));
        return card;
    }

    private LinearLayout chipRow(String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, 0);
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextSize(11);
        lab.setTextColor(Ui.sub(this));
        lab.setPadding(0, 0, dp(2), 0);
        row.addView(lab);
        return row;
    }

    private TextView chip(String text, boolean on, View.OnClickListener click) {
        TextView c = new TextView(this);
        c.setText(text);
        c.setTextSize(10);
        c.setPadding(dp(7), dp(2), dp(7), dp(2));
        boolean mustOn = on && s("必做", "Must").equals(text);
        if (on) {
            c.setBackground(round(mustOn ? Ui.accent(this) : Ui.text(this), 20));
            c.setTextColor(Color.WHITE);
        } else {
            c.setBackground(roundStroke(Color.TRANSPARENT, Ui.line(this), 20));
            c.setTextColor(Ui.text(this));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(5);
        c.setLayoutParams(lp);
        c.setOnClickListener(click);
        return c;
    }

    /** 还剩可点改：新时长＝已用＋新还剩（与主页「还剩」同一口径）；有子任务时总时长由子任务定，去展开里改。 */
    private void editRemaining(final TaskItem t, final int used, final int remain) {
        if (t.hasSubtasks()) {
            Toast.makeText(this, s("这个任务有子任务，展开卡片在子任务里改时长",
                    "This task has subtasks — change its length in the expanded editor"),
                    Toast.LENGTH_LONG).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(remain));
        input.setSelection(input.getText().length());
        input.setTextSize(15);
        input.setGravity(Gravity.CENTER);
        new AlertDialog.Builder(this)
                .setTitle(s("还剩多少分钟", "Minutes left"))
                .setView(input)
                .setPositiveButton(s("保存", "Save"), (d, w) -> {
                    try {
                        int v = Integer.parseInt(input.getText().toString().trim());
                        if (v < 0) throw new NumberFormatException();
                        t.durationMinutes = Math.max(1, used + v);
                        persistTask(t);
                    } catch (Exception e) {
                        Toast.makeText(this, s("填一个分钟数", "Enter minutes"), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(s("取消", "Cancel"), null)
                .show();
    }

    /** 卡上快捷改动落盘：touch＋save（变更监听会通知主页等）＋踢一次同步。 */
    private void persistTask(TaskItem t) {
        t.touch();
        store.save();
        GuardRules.kickSync(this);
        refreshAll();
    }

    // ---------------- 底部排程 ----------------

    private void onPlanIntoToday() {
        LocalAutoScheduler.Plan p = LocalAutoScheduler.computeLocal(this, store);
        showOverflow(p.overflow);
        if (p.additions.isEmpty() && p.overflow.isEmpty()) {
            Toast.makeText(this, s("今天的任务都已经排好了", "Everything for today is already planned"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        LocalAutoScheduler.runAsync(this, store, () -> {
            LocalAutoScheduler.Plan after = LocalAutoScheduler.computeLocal(this, store);
            showOverflow(after.overflow);
            refreshLedger();
            Toast.makeText(this, after.overflow.isEmpty()
                            ? s("都排进今天的日程了", "All planned into today")
                            : s("排好了，点名的这几件实在排不下", "Planned — the named ones just don't fit"),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void showOverflow(List<LocalAutoScheduler.Candidate> overflow) {
        if (overflow == null || overflow.isEmpty()) {
            overflowNote.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder(s("排不下：", "No room for: "));
        for (int i = 0; i < overflow.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(overflow.get(i).title);
        }
        overflowNote.setText(sb.toString());
        overflowNote.setVisibility(View.VISIBLE);
    }
}
