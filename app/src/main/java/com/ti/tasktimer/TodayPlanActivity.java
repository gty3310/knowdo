package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *
 * v1.54.0（用户亲定）：截止钮加明天/周六/下周一/挑选日期；推迟（deadline
 * 改到未来）的卡不消失、只置灰，灰卡不计时间账、不进本页排程输入、不进
 * 红字；本页卡序在页面打开时冻住，页内编辑一律不重排，只有标题行右上角
 * 「重新排序」按快速重排同一 candidateOrder 重排一次；红字只点名今天的。
 *
 * v1.55.0（用户亲定）：固定底上方加一行两钮——「添加任务」（复用
 * TaskEditSheet 新建层、截止预填逻辑今天）与「跟 AI 一起添加任务」
 * （本页内展开近全屏聊天面板：临时会话只存内存、绝不写「聊聊」主线；
 * 传输复用 ChatClient 同一 chat-proxy 通道；AI 整理出清单必须先出
 * knowdo-addtasks 提案卡、用户点「就这样添加」才经 TaskStore 建任务）。
 *
 * v1.56.0（用户亲定）：内容区末尾加「其他重要任务」可展开区——默认收起、
 * 展开态只存内存；收必做/重要两档、未完成、非每日重复、截止不在今天
 * （晚于逻辑今天或没定日期）、且不在本页主清单里的任务，按截止近的在前
 * （没定日期最后、同截止必做在前）排；每件一张紧凑小卡，只带「改到今天」
 * 一个动作（走 persistTask 设 deadline＝逻辑今天，任务按冻结口径追加主
 * 清单末尾）；区内为空时整区不渲染。
 */
public class TodayPlanActivity extends Activity {
    private TaskStore store;
    private String logicToday;

    private TextView remainVal, arrangedVal, ledgerNote;
    /** 开页自检补排的页顶小字（v1.67.0，F5）：补排/拉不到云端都要明示，不静默空白。 */
    private TextView healNoteView;
    private String healNote;
    private View barUsed, barOver, barRest;
    private FrameLayout strip;
    private TextView scaleWake, scaleEnd;
    private LinearLayout digitsBox, cardsBox;
    private TextView digitsToggle, overflowNote;
    private EditText freeInput;
    private boolean digitsOpen = false;
    /** 「其他重要任务」区容器（v1.56.0）；展开态只存内存、每次开页默认收起。 */
    private LinearLayout otherBox;
    private boolean otherExpanded = false;
    private TextView resortBtn;
    /** 本页卡序（v1.54.0）：页面打开时按既有口径（todayCandidates）定下并冻住；
     * 此后页内编辑不重排、同步新到的候选追加末尾并入冻结序；完成/删除的摘掉。
     * 只有「重新排序」按 candidateOrder 重排并刷新冻结序。 */
    private final List<String> frozenOrder = new ArrayList<>();
    private boolean orderFrozen = false;

    // ---- v1.55.0：AI 添加任务聊天面板（临时会话，只存内存，不落 ChatStore） ----
    private LinearLayout aiPanel;
    private LinearLayout aiMsgCol;
    private ScrollView aiScroll;
    private EditText aiInput;
    private TextView aiSendBtn;
    private final List<ChatStore.Msg> aiMsgs = new ArrayList<>();
    private boolean aiStreaming = false;
    private boolean aiGreeted = false;
    private static final String AI_FENCE = "```knowdo-addtasks";
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
        selfCheckSchedule();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.load();
        logicToday = Rollover.logicalToday(this);
        selfCheckSchedule();
        refreshAll();
    }

    /**
     * 开页自检（v1.67.0，F5）：本地缓存的日程不是今天的 → 先用本地任务当场补排
     * 并落盘渲染（纯本地、不等云端；与自动排程同一份 plan() 口径：×1.5、
     * candidateOrder、不排到 22:30 后、manual 块不动），不再出现一页空白。
     * 同时异步拉一次云端日程：拉到就走既有 autoFillSchedule 合并（以云端为底
     * 只往空档加，manual/已完成/计时中块都躺在底里原样保留）；拉不到就在页顶
     * 小字明示「显示的是本地安排」。日期口径与本页渲染一致——blocksOf 按
     * TaskItem.today() 摘块，自检也比这个日期。
     */
    private void selfCheckSchedule() {
        JSONObject sch = SyncManager.latestSchedule(this);
        String date = sch == null ? "" : sch.optString("date", "");
        if (TaskItem.today().equals(date)) return;
        LocalAutoScheduler.Plan p = LocalAutoScheduler.computeLocal(this, store);
        java.util.List<LocalAutoScheduler.Block> merged =
                new java.util.ArrayList<>(LocalAutoScheduler.blocksOf(sch, TaskItem.today()));
        merged.addAll(p.additions);
        try {
            JSONObject healed = LocalAutoScheduler.scheduleJson(TaskItem.today(), merged);
            healed.put("updatedAtMs", System.currentTimeMillis());
            healed.put("updatedBy", "KnowDo App（今天安排页自检补排）");
            SyncManager.cacheSchedule(this, healed);
        } catch (Exception ignored) { }
        healNote = s("已按本地任务补排今天", "Filled in from the tasks on this phone");
        applyHealNote();
        if (!Supa.isLoggedIn(this)) return;
        new Thread(() -> {
            boolean cloudOk = false;
            try {
                org.json.JSONArray us = Supa.restGet(this, "user_state?select=schedule");
                cloudOk = us.length() > 0;
            } catch (Exception ignored) { }
            if (cloudOk) {
                try {
                    SyncManager.autoFillSchedule(this, LocalAutoScheduler.snapshot(store, logicToday),
                            "KnowDo App（今天安排页自检补排）");
                } catch (Exception ignored) { }
            }
            final boolean ok = cloudOk;
            runOnUiThread(() -> {
                if (!ok) {
                    healNote = s("没拉到云端最新，显示的是本地安排",
                            "Couldn't reach the cloud — showing the local plan");
                    applyHealNote();
                }
                refreshAll();
            });
        }).start();
    }

    private void applyHealNote() {
        if (healNoteView == null) return;
        if (healNote == null) {
            healNoteView.setVisibility(View.GONE);
        } else {
            healNoteView.setText(healNote);
            healNoteView.setVisibility(View.VISIBLE);
        }
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
        head.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout ledger = new LinearLayout(this);
        ledger.setOrientation(LinearLayout.HORIZONTAL);
        ledger.setPadding(0, dp(5), 0, 0);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView labL = smallLabel(s("还剩可用", "Left"));
        remainVal = bigNum();
        left.addView(labL, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        left.addView(remainVal, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ledger.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView labR = smallLabel(s("已安排", "Planned"));
        labR.setGravity(Gravity.END);
        arrangedVal = bigNum();
        arrangedVal.setGravity(Gravity.END);
        right.addView(labR, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        right.addView(arrangedVal, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ledger.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(ledger, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        bar.addView(barUsed, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f));
        bar.addView(barOver, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f));
        bar.addView(barRest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f));
        head.addView(bar, blp);

        ledgerNote = new TextView(this);
        ledgerNote.setTextSize(10);
        ledgerNote.setTextColor(Ui.sub(this));
        ledgerNote.setPadding(0, dp(2), 0, 0);
        head.addView(ledgerNote, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        healNoteView = new TextView(this);
        healNoteView.setTextSize(10.5f);
        healNoteView.setTextColor(Ui.accent(this));
        healNoteView.setPadding(0, dp(3), 0, 0);
        healNoteView.setVisibility(View.GONE);
        head.addView(healNoteView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View headLine = new View(this);
        headLine.setBackgroundColor(Ui.line(this));
        root.addView(headLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // ---- 滚动区 ----
        // v1.78.0 整页重写：不再用「权重 0 高的 ScrollView + 固定底」，整页就是一个
        // ScrollView，内容自然撑高，不依赖窗口可用高度（部分机型上滚动区被压成 0 高）。
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        // 底部 padding 先给一个约数（吸底钮栏高＋12dp），首帧布局后在 buildUi
        // 文末按钮栏实际高精确重设（v1.79.0）
        col.setPadding(dp(18), dp(4), dp(18), dp(76));

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
        secRow.addView(digitsToggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(secRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        col.addView(scale, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        quick.addView(qLab, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        quick.addView(qUnit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView info = new TextView(this);
        info.setText("i");
        info.setTextSize(12);
        info.setGravity(Gravity.CENTER);
        info.setTextColor(Ui.sub(this));
        info.setBackground(roundStroke(Color.TRANSPARENT, Ui.sub(this), 20));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(18), dp(18));
        ilp.leftMargin = dp(10);
        info.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(s("可支配时间就是今天能用来安排任务的时间",
                        "Your free time is the time today that can hold tasks"))
                .setPositiveButton(s("知道了", "OK"), null)
                .show());
        quick.addView(info, ilp);
        col.addView(quick, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        taskSec.addView(th, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        resortBtn = new TextView(this);
        resortBtn.setText(s("重新排序", "Re-sort"));
        resortBtn.setTextSize(12);
        resortBtn.setTextColor(Ui.text(this));
        resortBtn.setPadding(dp(10), dp(3), dp(10), dp(3));
        resortBtn.setBackground(roundStroke(cardBg(), Ui.line(this), 20));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = dp(8);
        resortBtn.setOnClickListener(v -> resortCards());
        taskSec.addView(resortBtn, rlp);
        col.addView(taskSec, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cardsBox = new LinearLayout(this);
        cardsBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(cardsBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        overflowNote = new TextView(this);
        overflowNote.setTextSize(12);
        overflowNote.setTextColor(Ui.accent(this));
        overflowNote.setPadding(0, dp(8), 0, 0);
        overflowNote.setVisibility(View.GONE);
        col.addView(overflowNote, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 「其他重要任务」可展开区（v1.56.0）：内容区末尾（任务卡与红字之下、
        // 固定底之上）；内容由 refreshOtherSection 填，空时整区不渲染。
        otherBox = new LinearLayout(this);
        otherBox.setOrientation(LinearLayout.VERTICAL);
        otherBox.setVisibility(View.GONE);
        col.addView(otherBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // v1.79.0：四个操作钮不再进滚动内容，改走 frame 底部浮层（buildBtnBar，
        // 在下方 frame 组装处挂上）；滚动内容到任务区为止。
        root.addView(col, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // v1.55.0：整页包一层 FrameLayout，AI 聊天面板作底部浮层（近全屏高）
        FrameLayout frame = new FrameLayout(this);
        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setBackgroundColor(Ui.bg(this));
        pageScroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.addView(pageScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // v1.79.0 吸底四钮栏：frame 内的底部浮层（Gravity.BOTTOM），不进滚动
        // 内容、不靠 LinearLayout 权重分配高度（避开 v1.77 的三段高度病）。
        // z 序在 AI 面板之下：面板打开时盖住钮栏即可（openAiPanel 有 bringToFront）。
        final LinearLayout btnBar = buildBtnBar();
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        frame.addView(btnBar, barLp);
        aiPanel = buildAiPanel();
        aiPanel.setVisibility(View.GONE);
        int panelH = (int) (getResources().getDisplayMetrics().heightPixels * 0.92);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, panelH);
        plp.gravity = Gravity.BOTTOM;
        frame.addView(aiPanel, plp);
        setContentView(frame);
        // 内容列底部让位：钮栏实际高＋12dp，最后一张卡不被吸底栏挡住
        btnBar.post(() -> col.setPadding(dp(18), dp(4), dp(18),
                btnBar.getHeight() + dp(12)));
    }

    /** 深底主钮（原页顶大钮的样式，v1.79.0 起用于吸底栏的两个主动作）。 */
    private TextView darkBtn(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(13);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);
        v.setPadding(0, dp(7), 0, dp(7));
        v.setBackground(round(Ui.text(this), 10));
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return v;
    }

    /**
     * 吸底四钮栏（v1.79.0）：添加任务（浅）／跟 AI 一起添加任务（浅）／排进今天
     * 的日程（深）／开始今天（深），四钮等宽一排、单行不折行；点击行为与原
     * addRow 两钮、btn、startBtn 逐一相同（监听原样搬来）。栏体是 FrameLayout
     * 里的底部浮层，高度只由内容决定，不参与任何权重分配；顶部一条细线与
     * 滚动内容分界。本页 windowSoftInputMode=adjustResize（Manifest 既有），
     * 键盘弹起时窗口收缩、钮栏随之贴在键盘上方。
     */
    private LinearLayout buildBtnBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Ui.bg(this));
        View line = new View(this);
        line.setBackgroundColor(Ui.line(this));
        bar.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(10));
        TextView addBtn = lightBtn(s("添加任务", "Add task"));
        addBtn.setSingleLine(true);
        addBtn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addBtn.setOnClickListener(v -> TaskEditSheet.showCreateWithDeadline(
                this, store, logicToday, editHost));
        TextView aiBtn = lightBtn(s("跟 AI 一起添加任务", "Add with AI"));
        aiBtn.setTextSize(12);
        aiBtn.setSingleLine(true);
        aiBtn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        aiBtn.setOnClickListener(v -> openAiPanel());
        TextView planBtn = darkBtn(s("排进今天的日程", "Plan into today"));
        planBtn.setTextSize(12);
        planBtn.setOnClickListener(v -> onPlanIntoToday());
        TextView startBtn = darkBtn(s("开始今天", "Start today"));
        startBtn.setOnClickListener(v -> onStartToday());
        TextView[] btns = {addBtn, aiBtn, planBtn, startBtn};
        for (int i = 0; i < btns.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(8);
            row.addView(btns[i], lp);
        }
        bar.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    /** 描边浅底的轻钮（v1.55.0 加任务两钮用，比底部大钮轻一档）。 */
    private TextView lightBtn(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(13);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Ui.text(this));
        v.setPadding(0, dp(7), 0, dp(7));
        v.setBackground(roundStroke(Ui.surface(this), Ui.line(this), 10));
        return v;
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

    /**
     * 全页刷新（v1.70.0，F2）：分段护栏——时间账／时段条／任务卡／其他重要任务
     * 逐段独立 try/catch，某段抛错不连累其他段；出错段原位渲染一行朱砂小字
     * 「这一区没画出来，点此重试」（点按重跑 refreshAll），异常同时写测试者
     * 日志（ErrorReporter，异常类名＋一句话）。
     */
    private void refreshAll() {
        guardSection("ledger", this::refreshLedger, null);
        guardSection("strip", this::refreshStrip, strip);
        if (digitsOpen) {
            try {
                rebuildDigitRows();
            } catch (Exception e) {
                logSectionError("digits", e);
            }
        }
        guardSection("cards", this::refreshCardsOnly, cardsBox);
        guardSection("other", this::refreshOtherSection, otherBox);
        try {
            double fh = DayAvail.freeHours(this, logicToday);
            String cur = freeInput.getText().toString().trim();
            String want = fh >= 0 ? trimNum(fh) : "";
            if (!cur.equals(want) && !freeInput.hasFocus()) freeInput.setText(want);
        } catch (Exception e) {
            logSectionError("freeInput", e);
        }
    }

    /** 分段护栏执行体：ledger 段成功时顺手清掉占位小字留下的重试点击。 */
    private void guardSection(String name, Runnable body, ViewGroup box) {
        try {
            body.run();
            if (box == null && ledgerNote != null) ledgerNote.setOnClickListener(null);
        } catch (Exception e) {
            logSectionError(name, e);
            showSectionError(box);
        }
    }

    private void logSectionError(String name, Exception e) {
        try {
            ErrorReporter.report(this, s("今天安排", "Today plan"), 0,
                    name + ": " + e.getClass().getSimpleName() + " - "
                            + String.valueOf(e.getMessage()));
        } catch (Exception ignored) { }
    }

    /**
     * 出错段原位明示（F2）：box 为 null 表示时间账段——借页头 ledgerNote 原位
     * 显示（下一次 refreshLedger 成功照常覆写它）；其余段把小字加进该段容器
     * （FrameLayout 的时段条用满幅居中，其余满宽一行）。点按重跑整页刷新。
     */
    private void showSectionError(ViewGroup box) {
        String msg = s("这一区没画出来，点此重试",
                "This section didn't draw - tap to retry");
        if (box == null) {
            ledgerNote.setText(msg);
            ledgerNote.setTextColor(Ui.accent(this));
            ledgerNote.setOnClickListener(v -> refreshAll());
            return;
        }
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(12);
        tv.setTextColor(Ui.accent(this));
        tv.setOnClickListener(v -> refreshAll());
        if (box instanceof FrameLayout) {
            tv.setGravity(Gravity.CENTER);
            box.addView(tv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            tv.setPadding(0, dp(8), 0, dp(8));
            if (box == otherBox) box.setVisibility(View.VISIBLE);
            box.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private static String trimNum(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        // 非整数（如被时间墙上限卡到分钟精度的值）：最多两位小数并去尾零，
        // 不许回显一长串 double 小数；账面计算走分钟精度、不受此显示影响。
        String t = String.format(java.util.Locale.US, "%.2f", v);
        while (t.indexOf('.') >= 0 && (t.endsWith("0") || t.endsWith("."))) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    /** 时间账（v1.46.0 起从此刻算）：已安排＝现在之后的日程块时长（横跨此刻的
     * 块只算现在之后那截）；预算＝填的可支配与从现在到今天结束的墙上可用两者
     * 取小；还剩＝预算−已安排，负数变红。 */
    private void refreshLedger() {
        List<LocalAutoScheduler.Block> blocks = LocalAutoScheduler.blocksOf(
                SyncManager.latestSchedule(this), TaskItem.today());
        int nowMin = DayAccount.nowMinutes();
        Set<String> grey = greyIdSet(); // 灰卡不计时间账（v1.54.0）：它已有的块也不算已安排
        int arranged = 0;
        for (LocalAutoScheduler.Block b : blocks) {
            if (grey.contains(b.taskId)) continue;
            arranged += Math.max(0, b.endMin - Math.max(b.startMin, nowMin));
        }
        int budget = Math.min(DayAvail.budgetMin(this, logicToday),
                DayAvail.wallCapMinToday(this, logicToday));
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
            ledgerNote.setText(s("可支配还没填，按现在到今天结束估算", "Free time not set — estimated from now to day end"));
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
            // 时间墙上限（v1.38.2，用户亲定口径）：可支配不许多于时间墙上可用的
            // 时间；填超了按上限以分钟精度存，并给一句两秒提示；上限内与原来一致。
            int capMin = DayAvail.wallCapMinToday(this, logicToday);
            if ((int) Math.round(h * 60) > capMin) {
                DayAvail.setFreeHours(this, logicToday, capMin / 60.0);
                Toast.makeText(this, s("可支配时间不可以多于时间墙上可用的时间",
                        "Free time cannot exceed the time left on today's wall"),
                        Toast.LENGTH_SHORT).show();
            } else {
                DayAvail.setFreeHours(this, logicToday, h);
            }
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
        // 现在标记（v1.46.0，用户点选提案①）：整日轴保留，轴上现在之前压一层
        // 半透明灰，现在位置画一条朱砂红竖线。两层都不可点击/不可聚焦——触摸
        // 挂在 strip 容器上，既有锁时拖拽/点击命中不受影响。
        int nowAxis = Math.max(0, Math.min(1440,
                DayAccount.nowMinutes() - DayAvail.wakeMin(this)));
        if (nowAxis > 0) {
            View past = new View(this);
            past.setBackgroundColor(Color.argb(150, 128, 128, 128));
            past.setClickable(false);
            past.setFocusable(false);
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                    (int) (nowAxis * (long) w / 1440),
                    ViewGroup.LayoutParams.MATCH_PARENT);
            plp.leftMargin = 0;
            strip.addView(past, plp);
            View mark = new View(this);
            mark.setBackgroundColor(Ui.accent(this));
            mark.setClickable(false);
            mark.setFocusable(false);
            FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                    dp(2), ViewGroup.LayoutParams.MATCH_PARENT);
            mlp.leftMargin = Math.max(0, (int) (nowAxis * (long) w / 1440) - dp(1));
            strip.addView(mark, mlp);
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
                row.addView(del, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            digitsBox.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        footRow.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        digitsBox.addView(footRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        List<LocalAutoScheduler.Candidate> cands = LocalAutoScheduler.snapshot(store, logicToday);
        java.util.Map<String, LocalAutoScheduler.Candidate> byId = new java.util.HashMap<>();
        for (LocalAutoScheduler.Candidate cd : cands) byId.put(cd.id, cd);
        List<TaskItem> out = new ArrayList<>();
        for (TaskItem t : store.tasks) {
            if (t.deleted || t.isDoneToday()) continue;
            if (t.isAi()) continue; // 给 AI 做的任务不进今天安排候选（v1.74.0）
            if (t.isSkippedOn(logicToday)) continue; // 跳过今天：今日不进候选（v1.79.0）
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

    /**
     * 置灰判定（v1.54.0）：非每日重复、deadline 在逻辑今天之后＝被推迟了，整卡
     * 置灰。逾期（deadline＜今天）是滚进来的今日事、不置灰；无 deadline 不置灰
     * （与「取消截止日期」沿用现状一致）；每日重复没有推迟一说，不置灰。
     */
    private boolean isGrey(TaskItem t) {
        if (t == null || "daily".equals(t.repeat)) return false;
        return t.deadline != null && logicToday != null
                && t.deadline.compareTo(logicToday) > 0;
    }

    /** 本页当前的灰卡 id 集（冻结序里已被推迟的任务）；时间账与排程输入共用。 */
    private Set<String> greyIdSet() {
        Set<String> out = new HashSet<>();
        for (String id : frozenOrder) {
            TaskItem t = store.byId(id);
            if (t != null && isGrey(t)) out.add(id);
        }
        return out;
    }

    /**
     * 当前渲染的任务列（v1.54.0 顺序冻结）：冻结序内的任务（未删、未完成今日）
     * 按冻结序出；冻结后新到的候选按既有口径排好追加到末尾并入冻结序；已完成/
     * 已删的从冻结序摘掉。页内改 deadline/重要性只改任务本身，列表纹丝不动。
     */
    private List<TaskItem> currentTasks() {
        List<TaskItem> cands = todayCandidates();
        if (!orderFrozen) {
            frozenOrder.clear();
            for (TaskItem t : cands) frozenOrder.add(t.id);
            orderFrozen = true;
        } else {
            Set<String> have = new HashSet<>(frozenOrder);
            for (TaskItem t : cands) {
                if (!have.contains(t.id)) frozenOrder.add(t.id);
            }
        }
        List<TaskItem> out = new ArrayList<>();
        List<String> keep = new ArrayList<>();
        for (String id : frozenOrder) {
            TaskItem t = store.byId(id);
            if (t == null || t.deleted || t.isDoneToday()) continue;
            // 跳过今天的从今日区摘下（并退出冻结序；恢复时按候选口径追加末尾，v1.79.0）
            if (t.isSkippedOn(logicToday)) continue;
            keep.add(id);
            out.add(t);
        }
        frozenOrder.clear();
        frozenOrder.addAll(keep);
        return out;
    }

    /**
     * 「重新排序」（v1.54.0）：按日历「快速重排」同一套候选排序重排本页全部卡片
     * 并成为新冻结序。快速重排的排序与其打包在 LocalAutoScheduler 里共用唯一
     * 比较器 candidateOrder（plan()/computeReschedule 都走它），本页直接复用
     * 同一比较器，只排卡、不动日程块。灰卡一并参与，排完仍灰（置灰是渲染口径）。
     */
    private void resortCards() {
        List<TaskItem> list = currentTasks();
        List<LocalAutoScheduler.Candidate> cands = LocalAutoScheduler.snapshot(store, logicToday);
        Map<String, LocalAutoScheduler.Candidate> byId = new HashMap<>();
        for (LocalAutoScheduler.Candidate cd : cands) byId.put(cd.id, cd);
        Comparator<LocalAutoScheduler.Candidate> ord = LocalAutoScheduler.candidateOrder(logicToday);
        list.sort((a, b) -> {
            LocalAutoScheduler.Candidate ca = byId.get(a.id), cb = byId.get(b.id);
            if (ca == null || cb == null) return 0;
            return ord.compare(ca, cb);
        });
        frozenOrder.clear();
        for (TaskItem t : list) frozenOrder.add(t.id);
        refreshCards();
    }

    /** 本周六：基准日是周六即基准日；周日取即将到来的周六（不取刚过去的）。 */
    private static String saturdayOf(String date) {
        Calendar cal = DayUtil.parse(date);
        if (cal == null) return date;
        int delta = (Calendar.SATURDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        return DayUtil.addDays(date, delta);
    }

    /** 下一个周一：基准日是周一则 +7 天。 */
    private static String nextMondayOf(String date) {
        Calendar cal = DayUtil.parse(date);
        if (cal == null) return date;
        int delta = (Calendar.MONDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        if (delta == 0) delta = 7;
        return DayUtil.addDays(date, delta);
    }

    /** 「挑选日期」：系统日期选择器，初始停在任务现 deadline（无则今天），选定即设。 */
    private void pickDeadline(final TaskItem t) {
        String base = t.deadline != null ? t.deadline : logicToday;
        Calendar cal = base == null ? null : DayUtil.parse(base);
        if (cal == null) cal = Calendar.getInstance();
        new DatePickerDialog(this, (dv, y, mo, da) -> {
            t.deadline = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, mo + 1, da);
            persistTask(t);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** 任务卡段＋其他重要任务段的组合刷新（既有调用点口径不变）；两段各有
     * 护栏（F2），本段抛错不连累彼段。 */
    private void refreshCards() {
        guardSection("cards", this::refreshCardsOnly, cardsBox);
        guardSection("other", this::refreshOtherSection, otherBox);
    }

    /**
     * 任务卡段（v1.70.0，F3 互济）：有候选照旧画卡（冻结序／灰卡口径不动）；
     * 候选为空但今日日程块非空时，按块逐段画只读行（起止＋任务名，解析不到
     * 的段跳过并计数），有日程就绝不让本段空着；两样都空才显示空态——且本机
     * 一个未删任务都没有时换成「数据还没到」的说法，区分真没任务与没同步到。
     */
    private void refreshCardsOnly() {
        cardsBox.removeAllViews();
        int drawn = 0;
        List<TaskItem> list = currentTasks();
        if (!list.isEmpty()) {
            for (TaskItem t : list) {
                cardsBox.addView(buildCard(t));
                drawn++;
            }
            return;
        }
        List<LocalAutoScheduler.Block> blocks = LocalAutoScheduler.blocksOf(
                SyncManager.latestSchedule(this), TaskItem.today());
        int skipped = 0;
        for (LocalAutoScheduler.Block b : blocks) {
            TaskItem t = store.byId(b.taskId);
            if (t == null) {
                skipped++;
                continue;
            }
            cardsBox.addView(buildBlockRow(b, t));
            drawn++;
        }
        if (drawn > 0) {
            if (skipped > 0) {
                android.util.Log.w("TodayPlan",
                        "block fallback rows skipped (task missing): " + skipped);
            }
            return;
        }
        TextView none = new TextView(this);
        int alive = 0;
        if (store.tasks != null) {
            for (TaskItem t : store.tasks) if (!t.deleted) alive++;
        }
        none.setText(alive == 0
                ? s("本机还没有任务数据，重开本页会再同步一次",
                        "No task data on this phone yet - reopening this page syncs again")
                : s("今天没有要排的任务", "Nothing to plan for today"));
        none.setTextSize(12);
        none.setTextColor(Ui.sub(this));
        none.setPadding(0, dp(10), 0, 0);
        cardsBox.addView(none, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 日程兜底只读行（F3）：起止时间＋任务名，不可点、不展开编辑。 */
    private View buildBlockRow(LocalAutoScheduler.Block b, TaskItem t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(roundStroke(cardBg(), Ui.line(this), 10));
        row.setPadding(dp(9), dp(6), dp(9), dp(6));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(6);
        row.setLayoutParams(rlp);
        TextView tm = new TextView(this);
        tm.setText(LocalAutoScheduler.hhmm(b.startMin) + "–" + LocalAutoScheduler.hhmm(b.endMin));
        tm.setTextSize(12);
        tm.setTextColor(Ui.sub(this));
        row.addView(tm, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView nm = new TextView(this);
        nm.setText(t.title);
        nm.setTextSize(12);
        nm.setTypeface(nm.getTypeface(), Typeface.BOLD);
        nm.setTextColor(Ui.text(this));
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(8);
        row.addView(nm, nlp);
        return row;
    }

    /**
     * 「其他重要任务」候选（v1.56.0）：必做/重要、未完成未删、非每日重复、
     * 截止不在今天（晚于逻辑今天或没定日期），且不在本页主清单里（按 id
     * 排除——推迟出去的灰卡已在主清单，不重复出现）；逾期（deadline＜今天）
     * 的已在主清单当今日正事，本判定也一并挡下。排序：截止近的在前、没定
     * 日期的最后；同截止必做在重要前；再同沿用 store 原序（稳定排序）。
     */
    private List<TaskItem> otherImportantTasks() {
        Set<String> mainIds = new HashSet<>();
        for (TaskItem t : currentTasks()) mainIds.add(t.id);
        List<TaskItem> out = new ArrayList<>();
        for (TaskItem t : store.tasks) {
            if (t.deleted || t.isDoneToday()) continue;
            if (t.isAi()) continue; // 给 AI 做的任务不进本页（含「其他重要任务」区，v1.74.0）
            if ("daily".equals(t.repeat)) continue;
            if (TaskItem.priorityRank(t.priority) > TaskItem.priorityRank("important")) continue;
            if (mainIds.contains(t.id)) continue;
            if (t.deadline != null && logicToday != null
                    && t.deadline.compareTo(logicToday) <= 0) continue;
            out.add(t);
        }
        out.sort((a, b) -> {
            if (a.deadline != null || b.deadline != null) {
                if (a.deadline == null) return 1;
                if (b.deadline == null) return -1;
                int c = a.deadline.compareTo(b.deadline);
                if (c != 0) return c;
            }
            return Integer.compare(TaskItem.priorityRank(a.priority),
                    TaskItem.priorityRank(b.priority));
        });
        // 今日被跳过的任务（v1.79.0）：不论优先级与重复类型都收进本区（恢复的
        // 入口在这里），排在排好序的常规其他任务之后；已在上表里的不重复加。
        List<TaskItem> skipped = new ArrayList<>();
        for (TaskItem t : store.tasks) {
            if (t.deleted || t.isDoneToday() || t.isAi()) continue;
            if (!t.isSkippedOn(logicToday)) continue;
            if (mainIds.contains(t.id)) continue;
            boolean dup = false;
            for (TaskItem o : out) if (o.id.equals(t.id)) { dup = true; break; }
            if (!dup) skipped.add(t);
        }
        out.addAll(skipped);
        return out;
    }

    /** 区内截止标注人话：没定日期／明天／M月d日（英文 MMM d）；解析不了回退原串。 */
    private String otherDeadlineLabel(TaskItem t) {
        if (t.deadline == null) return s("没定日期", "No date set");
        if (logicToday != null && DayUtil.addDays(logicToday, 1).equals(t.deadline)) {
            return s("明天", "Tomorrow");
        }
        Calendar cal = DayUtil.parse(t.deadline);
        if (cal == null) return t.deadline;
        if (en()) {
            return new java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                    .format(cal.getTime());
        }
        return (cal.get(Calendar.MONTH) + 1) + "月" + cal.get(Calendar.DAY_OF_MONTH) + "日";
    }

    /**
     * 「其他重要任务」区渲染（v1.56.0）：随 refreshCards 一同刷新；标题行带
     * 件数与展开/收起箭头，点整行切换（只重渲染本区）；区内为空时整区不
     * 渲染（标题行也不显示）。「改到今天」走 persistTask → refreshAll，本区
     * 件数与主清单、时间账随之重算。
     */
    private void refreshOtherSection() {
        if (otherBox == null) return;
        List<TaskItem> others = otherImportantTasks();
        otherBox.removeAllViews();
        if (others.isEmpty()) {
            otherBox.setVisibility(View.GONE);
            return;
        }
        otherBox.setVisibility(View.VISIBLE);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(12), 0, dp(2));
        TextView ht = new TextView(this);
        ht.setText(s("其他重要任务", "Other important tasks")
                + (en() ? " (" + others.size() + ")" : "（" + others.size() + "）"));
        ht.setTextSize(12);
        ht.setTextColor(Ui.text(this));
        head.addView(ht, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = new TextView(this);
        arrow.setText(otherExpanded ? "▴" : "▾");
        arrow.setTextSize(12);
        arrow.setTextColor(Ui.sub(this));
        head.addView(arrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        head.setOnClickListener(v -> {
            otherExpanded = !otherExpanded;
            guardSection("other", this::refreshOtherSection, otherBox);
        });
        otherBox.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (otherExpanded) {
            for (TaskItem t : others) otherBox.addView(buildOtherCard(t));
        }
    }

    /**
     * 区内紧凑小卡（v1.56.0）：任务名＋优先级标记（与主卡重要性行选中态同款
     * chip 画法）＋截止标注，右侧「改到今天」钮——本区唯一的动作；卡本体不
     * 设点击、不展开编辑。
     */
    private View buildOtherCard(final TaskItem t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(roundStroke(cardBg(), Ui.line(this), 10));
        card.setPadding(dp(9), dp(6), dp(9), dp(6));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(6);
        card.setLayoutParams(clp);
        TextView nm = new TextView(this);
        nm.setText(t.title);
        nm.setTextSize(12);
        nm.setTypeface(nm.getTypeface(), Typeface.BOLD);
        nm.setTextColor(Ui.text(this));
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(nm, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // 被跳过今天的任务（v1.79.0）：带「已跳过」标记，唯一动作是「恢复」
        if (t.isSkippedOn(logicToday)) {
            card.addView(chip(s("已跳过", "Skipped"), true, null));
            card.addView(chip(s("恢复", "Restore"), false, v -> restoreFromOther(t)));
            return card;
        }
        card.addView(chip(TaskItem.priorityLabel(t.priority, en()), true, null));
        TextView dl = new TextView(this);
        dl.setText(otherDeadlineLabel(t));
        dl.setTextSize(11);
        dl.setTextColor(Ui.sub(this));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = dp(8);
        card.addView(dl, dlp);
        card.addView(chip(s("改到今天", "Move to today"), false, v -> {
            t.deadline = logicToday;
            persistTask(t);
        }));
        return card;
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
        // 置灰（v1.54.0）：整卡 alpha 降调——底色、文字与钮一并变灰但仍清晰可读、
        // 照常可点；点「改到今天」deadline 回到今天后重渲染自动恢复正常色。
        if (isGrey(t)) card.setAlpha(0.55f);

        TextView nm = new TextView(this);
        nm.setText(t.title);
        nm.setTextSize(13);
        nm.setTypeface(nm.getTypeface(), Typeface.BOLD);
        nm.setTextColor(Ui.text(this));
        card.addView(nm, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 关联小字（v1.71.0，F4）：读知页本地缓存，有关联才画一行对象名；
        // 没缓存/没关联整行不出现、不阻塞渲染；只显示、不可点（整卡点击仍是编辑）
        String linkLine = ZhiPage.taskLinkLine(this, t.id);
        if (linkLine != null) {
            TextView lv = new TextView(this);
            lv.setText(linkLine);
            lv.setTextSize(11);
            lv.setTextColor(Ui.sub(this));
            lv.setMaxLines(1);
            lv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            lv.setPadding(0, dp(1), 0, 0);
            card.addView(lv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        final int used = (int) (SessionLog.todayMsForTask(this, t.id) / 60000);
        boolean daily = "daily".equals(t.repeat);
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setPadding(0, dp(2), 0, 0);
        TextView m1 = new TextView(this);
        m1.setTextSize(11);
        m1.setTextColor(Ui.sub(this));
        m1.setText(daily ? s("每日重复", "Daily") : s("已用 ", "Used ") + used + s(" 分", " min"));
        meta.addView(m1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView m2 = new TextView(this);
        m2.setTextSize(11);
        m2.setTextColor(Ui.sub(this));
        m2.setText(s("　还剩 ", "　Left "));
        meta.addView(m2, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final int remain = Math.max(0, t.durationMinutes - used);
        TextView m3 = new TextView(this);
        m3.setText(remain + s(" 分", " min"));
        m3.setTextSize(11);
        m3.setTypeface(m3.getTypeface(), Typeface.BOLD);
        m3.setTextColor(Ui.text(this));
        m3.setPaintFlags(m3.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        m3.setOnClickListener(v -> editRemaining(t, used, remain));
        meta.addView(m3, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!daily) {
            // 截止钮（v1.54.0）：改到今天／明天／周六／下周一／挑选日期／取消截止
            // 日期，六钮分两行（标签列定宽对齐）；点击都与「改到今天」同一走法——
            // 设 deadline 后 persistTask 落盘＋同步；高亮口径一致：deadline 正好
            // 等于该钮日期时该钮亮。日期以本页逻辑今天 logicToday 为基准。
            final String today0 = logicToday;
            final String tomorrow = today0 == null ? null : DayUtil.addDays(today0, 1);
            final String saturday = today0 == null ? null : saturdayOf(today0);
            final String nextMon = today0 == null ? null : nextMondayOf(today0);
            LinearLayout row = deadlineRow(s("截止日期", "Due"));
            row.addView(chip(s("改到今天", "Move to today"),
                    today0 != null && today0.equals(t.deadline),
                    v -> {
                        t.deadline = today0;
                        persistTask(t);
                    }));
            row.addView(chip(s("明天", "Tomorrow"),
                    tomorrow != null && tomorrow.equals(t.deadline),
                    v -> {
                        t.deadline = tomorrow;
                        persistTask(t);
                    }));
            row.addView(chip(s("周六", "Saturday"),
                    saturday != null && saturday.equals(t.deadline),
                    v -> {
                        t.deadline = saturday;
                        persistTask(t);
                    }));
            card.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout row2 = deadlineRow("");
            row2.addView(chip(s("下周一", "Next Mon"),
                    nextMon != null && nextMon.equals(t.deadline),
                    v -> {
                        t.deadline = nextMon;
                        persistTask(t);
                    }));
            row2.addView(chip(s("挑选日期", "Pick a date"), false,
                    v -> pickDeadline(t)));
            TextView cancel = chip(s("取消截止日期", "Clear due date"), false,
                    v -> {
                        t.deadline = null;
                        persistTask(t);
                    });
            if (t.deadline == null) cancel.setTextColor(Ui.sub(this));
            row2.addView(cancel);
            card.addView(row2, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        card.addView(prow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (t.hasSubtasks()) {
            LinearLayout subs = new LinearLayout(this);
            subs.setOrientation(LinearLayout.VERTICAL);
            subs.setPadding(0, dp(3), 0, 0);
            int showN = Math.min(3, t.subtasks.size());
            for (int i = 0; i < showN; i++) {
                Subtask st = t.subtasks.get(i);
                TextView sv = new TextView(this);
                sv.setText("· " + st.title + s("（", " (") + Math.max(1, st.minutes) + s(" 分）", " min)"));
                sv.setTextSize(11);
                sv.setTextColor(Ui.sub(this));
                sv.setSingleLine(true);
                sv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                subs.addView(sv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            if (t.subtasks.size() > showN) {
                TextView more = new TextView(this);
                more.setText("…");
                more.setTextSize(11);
                more.setTextColor(Ui.sub(this));
                subs.addView(more, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            card.addView(subs, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        acts.setGravity(Gravity.END);
        acts.setPadding(0, dp(5), 0, 0);
        // 「跳过今天」只对重复任务出（v1.79.0）：非重复任务没有「明天照常」一说
        if (t.repeat != null && !t.repeat.isEmpty() && !"none".equals(t.repeat)) {
            acts.addView(chip(s("跳过", "Skip"), false, v -> skipFromCard(t)));
        }
        acts.addView(chip(s("完成", "Done"), false, v -> completeFromCard(t)));
        TextView delChip = chip(s("删除", "Delete"), false, v -> confirmDeleteFromCard(t));
        delChip.setTextColor(Ui.accent(this));
        acts.addView(delChip);
        card.addView(acts, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> TaskEditSheet.showEdit(this, store, t, editHost));
        return card;
    }

    /** 截止钮专用行：与 chipRow 同款，但标签列定宽，让两行钮上下对齐。 */
    private LinearLayout deadlineRow(String label) {
        LinearLayout row = chipRow(label);
        TextView lab = (TextView) row.getChildAt(0);
        lab.setLayoutParams(new LinearLayout.LayoutParams(
                dp(48), ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
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
        row.addView(lab, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView chip(String text, boolean on, View.OnClickListener click) {
        TextView c = new TextView(this);
        c.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        // setChipSel 用的样式与此一处同源（v1.74.0 提案卡「谁来做」切换重画）
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(5);
        c.setLayoutParams(lp);
        c.setOnClickListener(click);
        return c;
    }

    /** chip 选中态重画（与 chip() 的 on/off 样式一致）：提案卡「谁来做」切换用。 */
    private void setChipSel(TextView c, boolean on) {
        if (on) {
            c.setBackground(round(Ui.text(this), 20));
            c.setTextColor(Color.WHITE);
        } else {
            c.setBackground(roundStroke(Color.TRANSPARENT, Ui.line(this), 20));
            c.setTextColor(Ui.text(this));
        }
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

    /**
     * 卡上「完成」（v1.42.0）：与任务列表完成完全同一条路径——markDone（一次性
     * 置 done、每日重复记今日完成）＋StartBaseline.markCompleted＋store.save 落盘
     * ＋SbSync.pushDoneAsync 完成状态定向推云，四步与列表三处完成入口
     * （MainActivity 复选框／展开 chip／长按菜单）逐一相同。列表侧完成后额外
     * 的 fireGuardAfterManualDone 是主页执行流的衔接守卫，不属于完成口径本身，
     * 安排页不在执行流里，不带。完成后 refreshAll——该任务 isDoneToday 后不再
     * 入候选，卡消失、时间账重算；与列表一样，完成动作本身不动日程里已有的
     * 计划块。
     */
    private void completeFromCard(final TaskItem t) {
        t.markDone();
        StartBaseline.markCompleted(this, t.id);
        store.save();
        SbSync.pushDoneAsync(this, t.id);
        refreshAll();
    }

    /**
     * 卡上「跳过今天」（v1.79.0）：把逻辑今天记进任务的 skippedDates（本地先行，
     * 随任务同步上云）→ 今日已排的块先从本地缓存摘掉（页面与时间账立刻生效），
     * 再后台走 SbSync.removeTaskBlocksToday 把云端那份也摘掉并回读校验，回来
     * 后把本地缓存再摘一遍防同步回声把块带回来。卡从今日区消失（候选与排程
     * 都剔除，见 todayCandidates 与 LocalAutoScheduler.snapshot），去「其他
     * 重要任务」区带「已跳过」标记，可在那里点「恢复」捞回；次日照常出现。
     */
    private void skipFromCard(final TaskItem t) {
        if (logicToday != null && !t.skippedDates.contains(logicToday)) {
            t.skippedDates.add(logicToday);
        }
        removeTodayBlocksLocal(t.id);
        persistTask(t);
        new Thread(() -> {
            String err = SbSync.removeTaskBlocksToday(this, t.id,
                    "KnowDo App（今天安排页跳过今天）");
            if (err == null) {
                runOnUiThread(() -> {
                    removeTodayBlocksLocal(t.id);
                    refreshAll();
                });
            }
        }).start();
    }

    /** 本地日程缓存里摘掉某任务今日的全部块（只动本地缓存，云端走 SbSync 删块）。 */
    private void removeTodayBlocksLocal(String taskId) {
        try {
            JSONObject sch = SyncManager.latestSchedule(this);
            List<LocalAutoScheduler.Block> blocks = LocalAutoScheduler.blocksOf(
                    sch, TaskItem.today());
            List<LocalAutoScheduler.Block> kept = new ArrayList<>();
            boolean changed = false;
            for (LocalAutoScheduler.Block b : blocks) {
                if (taskId.equals(b.taskId)) { changed = true; continue; }
                kept.add(b);
            }
            if (!changed) return;
            JSONObject ns = LocalAutoScheduler.scheduleJson(TaskItem.today(), kept);
            ns.put("updatedAtMs", System.currentTimeMillis());
            ns.put("updatedBy", "KnowDo App（今天安排页跳过今天）");
            SyncManager.cacheSchedule(this, ns);
        } catch (Exception ignored) { }
    }

    /**
     * 「其他重要任务」区里点「恢复」（v1.79.0）：清除今日的 skip，卡回今日区
     * （按候选口径追加到主清单末尾）；日程块不自动补排，用户可再点
     * 「排进今天的日程」。
     */
    private void restoreFromOther(final TaskItem t) {
        if (logicToday != null) t.skippedDates.remove(logicToday);
        persistTask(t);
    }

    /**
     * 卡上「删除」（v1.42.0）：与任务列表长按删除同款确认弹窗（标题／文案／
     * 按钮逐字一致），确认后走同一个 store.remove 墓碑软删（deleted=true＋
     * touch＋save，随同步上云）；删后 refreshAll，卡消失、时间账重算。与列表
     * 一样，删除动作本身不动日程里已有的计划块。
     */
    private void confirmDeleteFromCard(final TaskItem t) {
        new AlertDialog.Builder(this)
                .setTitle(s("删除任务", "Delete task"))
                .setMessage(s("确定删除「", "Delete “") + t.title + s("」吗？", "”?"))
                .setPositiveButton(s("删除", "Delete"), (d, w) -> {
                    store.remove(t);
                    refreshAll();
                })
                .setNegativeButton(s("取消", "Cancel"), null)
                .show();
    }

    // ---------------- 底部排程 ----------------

    private void onPlanIntoToday() {
        // 灰卡不进本页排程输入（v1.54.0）：现算与落盘都走排除版，排块算法本身
        // （LocalAutoScheduler.plan）一字未动。灰卡 id 在按下这一刻现算。
        final Set<String> grey = greyIdSet();
        LocalAutoScheduler.Plan p = LocalAutoScheduler.computeLocalExcluding(this, store, grey);
        showOverflow(p.overflow);
        if (p.additions.isEmpty() && p.overflow.isEmpty()) {
            Toast.makeText(this, s("今天的任务都已经排好了", "Everything for today is already planned"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        LocalAutoScheduler.runAsyncExcluding(this, store, grey, () -> {
            LocalAutoScheduler.Plan after = LocalAutoScheduler.computeLocalExcluding(
                    this, store, greyIdSet());
            List<LocalAutoScheduler.Candidate> named = showOverflow(after.overflow);
            refreshLedger();
            Toast.makeText(this, named.isEmpty()
                            ? s("都排进今天的日程了", "All planned into today")
                            : s("排好了，点名的这几件实在排不下", "Planned — the named ones just don't fit"),
                    Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 「开始今天」（v1.41.0，用户亲定）：排满没排满由用户说了算，点它直接结束
     * 安排、进入今天，不弹窗、不拦截、不动已有排程一字。本页其余编辑都是点按
     * 即落盘；唯一在途的是可支配输入框（仅失焦/回车时保存）：正聚焦时先走同一
     * 落盘路径 applyFreeInput 按现状存下，再退出。两条进入路径栈底都是日程主页
     * （晨间门 complete() 启动本页后已 finish 自身；日程页常驻入口从主页启动），
     * finish() 即回到今天的日程主页。
     */
    private void onStartToday() {
        if (freeInput != null && freeInput.hasFocus()) applyFreeInput();
        finish();
    }

    /**
     * 底部红字（v1.54.0 口径）：只点名「属于今天」的排不下任务——deadline ≤
     * 逻辑今天（到期与逾期滚入），或无 deadline 的必做。改前口径：computeLocal
     * 的计划对全部未排候选（含未来到期、无 deadline 的重要/次要）一并试排，
     * overflow 里有什么就点名什么，非今天的任务也常被点名。改后计划判定不变
     * （既有判定下排不下才算排不下），只是点名先按「属于今天」过滤；非今天的
     * 一律不进红字。返回实际点名的名单（toast 口径与屏上名单对齐）。
     */
    private List<LocalAutoScheduler.Candidate> showOverflow(
            List<LocalAutoScheduler.Candidate> overflow) {
        List<LocalAutoScheduler.Candidate> named = new ArrayList<>();
        if (overflow != null) {
            for (LocalAutoScheduler.Candidate cd : overflow) {
                boolean today = cd.deadline != null
                        ? (logicToday != null && cd.deadline.compareTo(logicToday) <= 0)
                        : cd.must;
                if (today) named.add(cd);
            }
        }
        if (named.isEmpty()) {
            overflowNote.setVisibility(View.GONE);
            return named;
        }
        StringBuilder sb = new StringBuilder(s("排不下：", "No room for: "));
        for (int i = 0; i < named.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(named.get(i).title);
        }
        overflowNote.setText(sb.toString());
        overflowNote.setVisibility(View.VISIBLE);
        return named;
    }

    // ================= v1.55.0：跟 AI 一起添加任务 =================
    // 临时会话：消息只存内存 aiMsgs，退出面板即结束，绝不经 ChatStore 落盘、
    // 绝不写「聊聊」主线。传输复用 ChatClient（同一 chat-proxy 通道与模型链、
    // 同一 SSE 请求/响应处理），system 提示为本场景单独一份。AI 整理出的
    // 清单必须带 knowdo-addtasks 结构块，App 解析成提案卡，用户点「就这样
    // 添加」才经 TaskStore 正常通道建任务（既定提案纪律，AI 不直接落库）。

    private LinearLayout buildAiPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(Ui.bg(this));

        View topLine = new View(this);
        topLine.setBackgroundColor(Ui.line(this));
        p.addView(topLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(18), dp(10), dp(18), dp(8));
        TextView title = new TextView(this);
        title.setText(s("跟 AI 一起添加任务", "Add tasks with AI"));
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(Ui.text(this));
        head.addView(title);
        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText(s("收起", "Close"));
        close.setTextSize(13);
        close.setTextColor(Ui.sub(this));
        close.setPadding(dp(10), dp(4), dp(2), dp(4));
        close.setOnClickListener(v -> closeAiPanel());
        head.addView(close);
        p.addView(head);

        View headLine2 = new View(this);
        headLine2.setBackgroundColor(Ui.line(this));
        p.addView(headLine2, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        aiScroll = new ScrollView(this);
        aiMsgCol = new LinearLayout(this);
        aiMsgCol.setOrientation(LinearLayout.VERTICAL);
        aiMsgCol.setPadding(dp(18), dp(6), dp(18), dp(14));
        // v1.67.0：显式给全宽/自适应高——旧版无参 addView 时它在 ScrollView 里的
        // 测量口径含糊，是面板塌陷的嫌疑路径之一（详见 openAiPanel 的兜底注释）
        aiScroll.addView(aiMsgCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        p.addView(aiScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout inRow = new LinearLayout(this);
        inRow.setOrientation(LinearLayout.HORIZONTAL);
        inRow.setGravity(Gravity.CENTER_VERTICAL);
        inRow.setPadding(dp(14), dp(8), dp(14), dp(12));
        aiInput = new EditText(this);
        aiInput.setHint(s("说说今天想做的事，可以一次说好几件",
                "Tell me today's tasks — several at once is fine"));
        aiInput.setTextSize(15);
        aiInput.setTextColor(Ui.text(this));
        aiInput.setMaxLines(3);
        aiInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        aiInput.setBackground(roundStroke(Ui.surface(this), Ui.line(this), 10));
        aiInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        inRow.addView(aiInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        aiSendBtn = new TextView(this);
        aiSendBtn.setText(s("发送", "Send"));
        aiSendBtn.setTextSize(15);
        aiSendBtn.setTypeface(aiSendBtn.getTypeface(), Typeface.BOLD);
        aiSendBtn.setTextColor(Ui.accent(this));
        aiSendBtn.setPadding(dp(14), dp(8), dp(4), dp(8));
        aiSendBtn.setOnClickListener(v -> onAiSend());
        inRow.addView(aiSendBtn);
        p.addView(inRow);
        return p;
    }

    private void openAiPanel() {
        aiPanel.setVisibility(View.VISIBLE);
        aiPanel.bringToFront();
        // v1.67.0 渲染兜底（F6）：旧版在 setVisibility(VISIBLE) 的同一帧里同步渲染
        // 开场白气泡——此时面板还没走完首帧测量，aiMsgCol 高度为 0，气泡加上去后
        // 没有任何后续事件再触发重排，真机上表现为「打开只有标题栏、没有开场白」
        // （用户在旧版上实见）。改法：先 VISIBLE，post 到首帧布局之后再渲染开场白，
        // 并强制消息列与滚动区各重排一次；气泡与消息列的 LayoutParams 也都写显式
        // （见 buildAiPanel 与 renderAiMsg），把 0 宽/0 高路径堵死。
        aiPanel.post(() -> {
            if (!aiGreeted) {
                aiGreeted = true;
                ChatStore.Msg g = new ChatStore.Msg("assistant",
                        s("把今天想做的事说给我，一次说好几件也行。我帮你整理成一张清单，"
                                + "你确认了才会真的添加。",
                          "Tell me what you'd like to do today — several things at once is "
                                + "fine. I'll shape them into a list, and nothing is added "
                                + "until you confirm it."));
                aiMsgs.add(g);
                renderAiMsg(g);
            }
            aiMsgCol.requestLayout();
            aiScroll.requestLayout();
            aiScrollToBottom();
        });
    }

    private void closeAiPanel() {
        aiPanel.setVisibility(View.GONE);
    }

    private void aiScrollToBottom() {
        aiScroll.post(() -> aiScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ---------------- 面板内消息渲染（气泡口径与 ChatPanel 一致） ----------------

    private void renderAiMsg(ChatStore.Msg m) {
        if ("user".equals(m.role)) {
            LinearLayout wrap = new LinearLayout(this);
            wrap.setGravity(Gravity.END);
            wrap.setPadding(0, dp(6), 0, dp(6));
            TextView tv = new TextView(this);
            tv.setText(m.text);
            tv.setTextSize(16);
            tv.setTextColor(Ui.text(this));
            tv.setLineSpacing(0, 1.3f);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(14));
            g.setColor(Ui.muted(this));
            tv.setBackground(g);
            tv.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(56);
            tv.setLayoutParams(lp);
            wrap.addView(tv);
            wrap.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            aiMsgCol.addView(wrap);
        } else {
            TextView tv = new TextView(this);
            tv.setText(m.text);
            tv.setTextSize(16);
            tv.setTextColor(Ui.text(this));
            tv.setLineSpacing(0, 1.35f);
            tv.setPadding(dp(2), dp(4), dp(24), dp(6));
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            aiMsgCol.addView(tv);
            if (m.proposal != null && "addtasks".equals(m.propKind)) {
                LinearLayout card = newAddCard();
                fillAddCard(card, m);
                aiMsgCol.addView(card);
            }
        }
    }

    private LinearLayout newAddCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Ui.surface(this));
        g.setStroke(1, Ui.line(this));
        card.setBackground(g);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, dp(4), 0, dp(10));
        card.setLayoutParams(clp);
        return card;
    }

    /** 提案卡内容（按 propState 重填：待确认带两钮，已添加/已收起只留状态行）。 */
    private void fillAddCard(final LinearLayout card, final ChatStore.Msg m) {
        card.removeAllViews();
        List<String[]> tasks = parseAddTasks(m.proposal);
        TextView head = new TextView(this);
        head.setText(s("待确认清单", "Proposed list"));
        head.setTextSize(13);
        head.setTextColor(Ui.text(this));
        head.setTypeface(head.getTypeface(), Typeface.BOLD);
        card.addView(head);
        for (int i = 0; i < tasks.size(); i++) {
            String[] t = tasks.get(i);
            TextView line = new TextView(this);
            line.setText((i + 1) + ". " + t[0] + " · " + t[1]
                    + s(" 分钟 · ", " min · ") + priorityLabel(t[2]));
            line.setTextSize(15);
            line.setTextColor(Ui.text(this));
            line.setPadding(0, dp(3), 0, 0);
            card.addView(line);
        }
        if (m.propState == ChatStore.PROP_APPLIED) {
            card.addView(addStateText(s("已添加", "Added")));
            return;
        }
        if (m.propState != ChatStore.PROP_PENDING) {
            card.addView(addStateText(s("已收起，继续聊", "Set aside — keep chatting")));
            return;
        }
        // 谁来做（v1.74.0，F2）：「跟 AI 一起添加任务」面板是独立表单、不落
        // TaskEditSheet，在提案卡上同步加这一行——整批一个选择，与编辑层同款
        // 小 chip；默认给我自己做，选 AI 的整批置 assignee=ai、ai_state=queued。
        final boolean[] addAsAi = {false};
        LinearLayout asgRow = new LinearLayout(this);
        asgRow.setOrientation(LinearLayout.HORIZONTAL);
        asgRow.setGravity(Gravity.CENTER_VERTICAL);
        asgRow.setPadding(0, dp(10), 0, 0);
        TextView asgLabel = new TextView(this);
        asgLabel.setText(s("谁来做", "Who does it"));
        asgLabel.setTextSize(13);
        asgLabel.setTextColor(Ui.sub(this));
        asgRow.addView(asgLabel);
        final TextView asgSelf = chip(s("给我自己做", "I'll do it"), true, null);
        final TextView asgAi = chip(s("给 AI 做", "AI does it"), false, null);
        asgSelf.setOnClickListener(v -> {
            addAsAi[0] = false;
            setChipSel(asgSelf, true);
            setChipSel(asgAi, false);
        });
        asgAi.setOnClickListener(v -> {
            addAsAi[0] = true;
            setChipSel(asgSelf, false);
            setChipSel(asgAi, true);
        });
        LinearLayout.LayoutParams asgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        asgLp.leftMargin = dp(8);
        asgRow.addView(asgSelf, asgLp);
        asgRow.addView(asgAi, asgLp);
        card.addView(asgRow);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        TextView ok = new TextView(this);
        ok.setText(s("就这样添加", "Add them"));
        ok.setTextSize(13);
        ok.setGravity(Gravity.CENTER);
        ok.setTextColor(Color.WHITE);
        ok.setPadding(dp(14), dp(7), dp(14), dp(7));
        ok.setBackground(round(Ui.text(this), 8));
        ok.setOnClickListener(v -> {
            int n = applyAddProposal(m, addAsAi[0]);
            if (n > 0) {
                m.propState = ChatStore.PROP_APPLIED;
                fillAddCard(card, m);
            }
        });
        row.addView(ok);
        TextView no = new TextView(this);
        no.setText(s("再改改", "Keep editing"));
        no.setTextSize(13);
        no.setGravity(Gravity.CENTER);
        no.setTextColor(Ui.text(this));
        no.setPadding(dp(14), dp(7), dp(14), dp(7));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = dp(10);
        no.setLayoutParams(nlp);
        no.setBackground(roundStroke(Ui.surface(this), Ui.line(this), 8));
        no.setOnClickListener(v -> {
            m.propState = ChatStore.PROP_DISMISSED;
            fillAddCard(card, m);
        });
        row.addView(no);
        card.addView(row);
    }

    private TextView addStateText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(Ui.sub(this));
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    private String priorityLabel(String p) {
        if (en()) {
            if ("must".equals(p)) return "Must";
            if ("important".equals(p)) return "Important";
            if ("leisure".equals(p)) return "Leisure";
            return "Secondary";
        }
        if ("must".equals(p)) return "必做";
        if ("important".equals(p)) return "重要";
        if ("leisure".equals(p)) return "休息";
        return "次要";
    }

    // ---------------- 发送与流式（复用 ChatClient 同一通道） ----------------

    private void onAiSend() {
        if (aiStreaming) return;
        String text = aiInput.getText().toString().trim();
        if (text.isEmpty()) return;
        aiInput.setText("");
        ChatStore.Msg userMsg = new ChatStore.Msg("user", text);
        aiMsgs.add(userMsg);
        while (aiMsgs.size() > 60) aiMsgs.remove(0); // 临时会话也别无限涨
        renderAiMsg(userMsg);
        aiScrollToBottom();

        final TextView live = new TextView(this);
        live.setText("…");
        live.setTextSize(16);
        live.setTextColor(Ui.text(this));
        live.setLineSpacing(0, 1.35f);
        live.setPadding(dp(2), dp(10), dp(24), dp(6));
        aiMsgCol.addView(live);
        aiScrollToBottom();
        aiStreaming = true;
        aiSendBtn.setTextColor(Ui.sub(this));

        final JSONArray payload;
        try {
            payload = ChatClient.toMessagesJson(buildAiSystemPrompt(), aiMsgs, null);
        } catch (Exception e) {
            finishAiError(live, s("消息没拼好，再试一次",
                    "Something went wrong putting the message together"));
            return;
        }
        ChatClient.send(this, payload, new ChatClient.Callback() {
            @Override public void onModel(String modelId) { }
            @Override public void onDelta(String fullText) {
                live.setText(aiDisplayPart(fullText));
                aiScrollToBottom();
            }
            @Override public void onDone(String fullText) {
                aiStreaming = false;
                aiSendBtn.setTextColor(Ui.accent(TodayPlanActivity.this));
                aiMsgCol.removeView(live);
                ChatStore.Msg am = parseAiAssistant(fullText);
                aiMsgs.add(am);
                renderAiMsg(am);
                aiScrollToBottom();
            }
            @Override public void onError(String friendlyMsg) {
                finishAiError(live, friendlyMsg);
            }
        });
    }

    /** 失败口径：聊天里落一行人话提示（friendly 已是大白话），手动添加不受影响。 */
    private void finishAiError(TextView live, String friendlyMsg) {
        aiStreaming = false;
        aiSendBtn.setTextColor(Ui.accent(this));
        aiMsgCol.removeView(live);
        ChatStore.Msg em = new ChatStore.Msg("assistant", friendlyMsg);
        aiMsgs.add(em);
        renderAiMsg(em);
        aiScrollToBottom();
    }

    /** 流式展示正文：提案代码块不给用户看原文，出现块开头即截断显示。 */
    private static String aiDisplayPart(String fullText) {
        if (fullText == null) return "";
        int i = fullText.indexOf(AI_FENCE);
        if (i >= 0) return fullText.substring(0, i).trim();
        return fullText;
    }

    // ---------------- 提案解析与应用 ----------------

    /** 流结束：剥离 knowdo-addtasks 块、校验清单，组装成面板内消息。解析不出
     *  结构就只当普通聊天显示（提案为 null），绝不崩溃。 */
    private ChatStore.Msg parseAiAssistant(String fullText) {
        ChatStore.Msg m = new ChatStore.Msg("assistant", "");
        String proposal = null;
        String text = fullText == null ? "" : fullText;
        int start = text.indexOf(AI_FENCE);
        if (start >= 0) {
            int jsonStart = text.indexOf('\n', start);
            int end = jsonStart < 0 ? -1 : text.indexOf("```", jsonStart);
            if (jsonStart >= 0 && end > jsonStart) {
                proposal = normalizeAddProposal(text.substring(jsonStart + 1, end).trim());
                text = text.substring(0, start) + text.substring(end + 3);
            } else {
                text = text.substring(0, start);
            }
        }
        m.text = text.trim();
        if (m.text.isEmpty()) {
            m.text = proposal != null
                    ? s("清单整理好了，你核对一下：", "Here's the list — check it over:")
                    : s("（这次没收到回复，再试一次）", "(No reply came back — try again)");
        }
        if (proposal != null) {
            m.proposal = proposal;
            m.propKind = "addtasks";
            m.propState = ChatStore.PROP_PENDING;
        }
        return m;
    }

    /** 清单 JSON 校验＋规范化：标题非空才收，时长钳到 1–480（缺省 30），
     *  优先级只认四档（之外按次要）；整表为空返回 null（当普通回复收）。 */
    private String normalizeAddProposal(String json) {
        try {
            JSONObject p = new JSONObject(json);
            JSONArray arr = p.optJSONArray("tasks");
            if (arr == null) return null;
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String title = o.optString("title", "").trim();
                if (title.isEmpty()) continue;
                int minutes = o.optInt("minutes", 30);
                if (minutes < 1) minutes = 1;
                if (minutes > 480) minutes = 480;
                String pr = o.optString("priority", "").trim();
                if (!"must".equals(pr) && !"important".equals(pr)
                        && !"secondary".equals(pr) && !"leisure".equals(pr)) {
                    pr = "secondary";
                }
                JSONObject t = new JSONObject();
                t.put("title", title);
                t.put("minutes", minutes);
                t.put("priority", pr);
                out.put(t);
            }
            if (out.length() == 0) return null;
            JSONObject res = new JSONObject();
            res.put("tasks", out);
            return res.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 把（已规范化的）提案 JSON 读成 {标题, 时长, 优先级} 行，供卡面渲染。 */
    private List<String[]> parseAddTasks(String proposalJson) {
        List<String[]> out = new ArrayList<>();
        if (proposalJson == null) return out;
        try {
            JSONArray arr = new JSONObject(proposalJson).optJSONArray("tasks");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                out.add(new String[]{o.optString("title", ""),
                        String.valueOf(o.optInt("minutes", 30)),
                        o.optString("priority", "secondary")});
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** 点「就这样添加」：全部经 TaskStore 正常通道建任务（deadline＝逻辑今天），
     *  聊天里补一条系统行，本页清单刷新（新卡按冻结序口径追加末尾、时间账重算）。
     *  asAi＝提案卡上选了「给 AI 做」（v1.74.0）：整批置 assignee=ai＋ai_state=queued，
     *  deadline 仍写逻辑今天（语义＝希望今天做完），但不进本页候选/排程（F3 口径）。 */
    private int applyAddProposal(ChatStore.Msg m, boolean asAi) {
        List<String[]> tasks = parseAddTasks(m.proposal);
        if (tasks.isEmpty()) return 0;
        store.load();
        int n = 0;
        for (String[] t : tasks) {
            TaskItem item = TaskItem.create(t[0]);
            try { item.durationMinutes = Integer.parseInt(t[1]); }
            catch (Exception ignored) { item.durationMinutes = 30; }
            item.priority = t[2];
            item.deadline = logicToday;
            if (asAi) {
                item.assignee = TaskItem.ASSIGN_AI;
                item.aiState = "queued";
            }
            item.touch();
            store.add(item);
            n++;
        }
        StartReminder.reschedule(this, store);
        GuardRules.kickSync(this);
        ChatStore.Msg done = new ChatStore.Msg("assistant",
                asAi
                        ? s("已添加 " + n + " 件给 AI 做，在任务列表里看它们的状态",
                            "Added " + n + " for the AI — watch their status in the task list")
                        : s("已添加 " + n + " 件，都在今天的清单里了",
                            "Added " + n + " — they're on today's list"));
        aiMsgs.add(done);
        renderAiMsg(done);
        aiScrollToBottom();
        refreshAll();
        return n;
    }

    /** 本面板单独的场景提示：把今天想做的事整理成任务清单＋结构块约定。 */
    private String buildAiSystemPrompt() {
        if (en()) {
            return "You are the add-tasks assistant on KnowDo's Plan-today page. The user "
                    + "tells you, in their own words, several things they want to do today — "
                    + "possibly all at once, possibly over a few turns.\n"
                    + "Your job: settle each thing into a task — a title, a length in "
                    + "minutes, and a priority: must / important / secondary / leisure.\n"
                    + "- If the length is missing, estimate a sensible one and say in your "
                    + "reply that it is an estimate for them to check. If the priority is "
                    + "missing, default to secondary unless it clearly sounds urgent.\n"
                    + "- Never invent tasks the user did not mention. If something is "
                    + "genuinely unclear, ask one short question.\n"
                    + "- When the list is complete (the details are settled, or the user "
                    + "says that's all), give a one-line summary, then output exactly one "
                    + "machine-readable block in this strict format (do not rename fields, "
                    + "no comments inside):\n"
                    + AI_FENCE + "\n"
                    + "{\"tasks\":[{\"title\":\"Task title\",\"minutes\":30,"
                    + "\"priority\":\"secondary\"}]}\n```\n"
                    + "- Do not repeat the list item by item outside the block. If the user "
                    + "asks to change an entry, confirm the change briefly and output the "
                    + "whole updated list as a new block (it replaces the old one).\n"
                    + "- While you are still collecting, do not output the block.\n"
                    + "Today is " + logicToday + "; every task in the list will be added "
                    + "to today. Keep replies calm, short and concrete; no lecturing.";
        }
        return "你是知行 KnowDo「今天安排」页里的添加任务助手。用户会用自己的话把今天想做的"
                + "几件事说给你，可能一次说好几件，也可能分几轮补充。\n"
                + "你的活：把每件事确认成一个任务——标题、时长（分钟）、优先级"
                + "（必做 must／重要 important／次要 secondary／休息 leisure）。\n"
                + "- 时长没说的，按常理估一个，并在正文里点明是估的、请他核对；优先级没说的"
                + "默认次要，除非语气明显是要紧事。\n"
                + "- 不要替他发明他没提过的事；实在拿不准的，只追问一句。\n"
                + "- 当清单完整（信息已齐，或用户表示就这些了）时：先用一句话小结，然后另起"
                + "一个代码块输出机器可读清单，格式严格如下，不要改字段名、不要加注释：\n"
                + AI_FENCE + "\n"
                + "{\"tasks\":[{\"title\":\"任务标题\",\"minutes\":30,"
                + "\"priority\":\"secondary\"}]}\n```\n"
                + "- 代码块之外不要再逐条重复清单；用户说要改某条时，先口头确认改法，再把"
                + "整张更新后的清单作为一个新代码块输出（整张替换）。\n"
                + "- 还没聊完时不要输出代码块。\n"
                + "今天是 " + logicToday + "，清单里的任务都会加到今天。语气平静、简短、"
                + "具体，不说教。";
    }
}
