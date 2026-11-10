package com.ti.tasktimer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 全屏检查页：由 AlertHelper 拉起（熄屏走 FSI，亮屏走直接启动），两种状态同一个页面。
 * 过半页：能做完 / 做不完→写下办法。
 * 结束页：做完了→回写任务完成状态并回列表；没做完→加时（+50% / +5 / +10 / +15 分钟 / 自定义）。
 * v1.8.0：只改外观为极简全屏（主题色＋圆角纵排大按钮，文案走 Strings 中/英）；
 * 按钮语义、选项、日志与回写行为与旧版完全一致。另按定稿设计补键盘：回车=主按钮、
 * Q/W/E=前三个加时选项（旧版无键盘处理，本次为纯加法；输入框聚焦时不拦截）。
 */
public class CheckActivity extends Activity {
    public static final String EXTRA_TYPE = "type";

    private int type;
    private LinearLayout root;
    private LinearLayout footer;
    private Runnable primaryAction; // 回车触发的主按钮动作（当前阶段的主按钮）
    private Runnable keyQ, keyW, keyE; // 结束页前三个加时选项
    private ChatPanel endChatPanel; // 结束页内嵌聊天区（v1.24.0，只在 showEnd 里建）
    // 加时动机门（v1.72.0）：本页本次是否已过门、门后要回去的选时长步、过半页加时区状态
    private boolean motivePassed;
    private Runnable extendBack;
    // 原则陪跑问询（v1.75.0，F3）：本页（本次完成）是否已答、答了什么——
    // 答过就只显已记态，同一次完成不重复问；页重建（加时回选时长步）也记得。
    private boolean principleAnswered;
    private String principleAnswerValue = "";
    private LinearLayout halfAddBox;
    private int halfAddedMin;
    private TextView halfAddedLine;

    // 跨设备跟随（v1.7.0）：另一端已处理（结束/加时/换任务）时，本页若还在显就关掉，别停在过期状态上
    private final android.content.BroadcastReceiver timerRemoteReceiver =
            new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context c, Intent i) {
                    if (!TimerState.isRunning(CheckActivity.this)) {
                        finish(); // 远端已结束/停止：本页没有存在的意义了
                    } else if (type == AlertHelper.TYPE_END || type == AlertHelper.TYPE_SUB) {
                        finish(); // 还在跑但检查点已被远端改动（加时/推进）：旧页过期
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        android.content.IntentFilter tf = new android.content.IntentFilter(SbTimerSync.ACTION_TIMER_REMOTE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(timerRemoteReceiver, tf, RECEIVER_NOT_EXPORTED);
        else registerReceiver(timerRemoteReceiver, tf);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        type = getIntent().getIntExtra(EXTRA_TYPE, AlertHelper.TYPE_END);
        AlertHelper.cancelAlert(this);
        vibrate();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));
        int pad = dp(28);
        root.setPadding(pad, dp(56), pad, pad);
        // v1.24.0：整页套一层纵向滚动——结束页要内嵌聊天区，内容变长后
        // 保证「做完了/加时」等原有按钮在小屏上也够得着（短内容时视觉不变）。
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.bg(this));
        sv.setFillViewport(true);
        root.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        sv.addView(root);
        // v1.40.0 固定底栏：各阶段的主行动按钮钉在页面最底部、不随内容滚动；
        // 键盘弹起时窗口压缩、底栏稳停在键盘上方。
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.bg(this));
        page.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setBackgroundColor(Ui.bg(this));
        footer.setPadding(pad, dp(8), pad, pad);
        footer.setVisibility(View.GONE);
        page.addView(footer);
        setContentView(page);

        if (type == AlertHelper.TYPE_HALF) showHalfStage1();
        else if (type == AlertHelper.TYPE_SUB) showSub();
        else if (TimerState.subActive(this)
                && TimerState.subIndex(this) < TimerState.subCount(this) - 1) {
            // 手动从悬浮窗/主页打开结束页、但子任务还没跑到最后一个：先过子任务检查
            showSub();
        } else showEnd();
    }

    @Override protected void onResume() {
        super.onResume();
        TopPages.checkShown = true; // 衔接守卫避让：检查页在显时不弹「下一个任务」页
    }

    @Override protected void onPause() {
        TopPages.checkShown = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (endChatPanel != null) endChatPanel.detach();
        try { unregisterReceiver(timerRemoteReceiver); } catch (Exception ignored) { }
        FloatingWindow.setSuppressed(false); // 检查页关闭，悬浮窗可以回来了（计时还在走时）
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (getCurrentFocus() instanceof EditText) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (primaryAction != null) { primaryAction.run(); return true; }
        } else if (keyCode == KeyEvent.KEYCODE_Q) {
            if (keyQ != null) { keyQ.run(); return true; }
        } else if (keyCode == KeyEvent.KEYCODE_W) {
            if (keyW != null) { keyW.run(); return true; }
        } else if (keyCode == KeyEvent.KEYCODE_E) {
            if (keyE != null) { keyE.run(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    private String taskName() { return TimerState.taskTitle(this); }

    // ---------- 过半页 ----------
    private void showHalfStage1() {
        clear();
        primaryAction = null;
        keyQ = keyW = keyE = null;
        title(Strings.chkHalfTitle());
        subtitle(Strings.chkHalfSub(taskName()));
        Runnable okAct = () -> {
            TimerState.logEvent(this, "过半检查[" + taskName() + "]：能做完");
            finish();
        };
        Button ok = bigButton(Strings.chkCan(), true, "Enter");
        ok.setOnClickListener(v -> okAct.run());
        primaryAction = okAct;
        pinFooter(ok);
        footerSpacer();
        Button no = bigButton(Strings.chkCannot(), false, null);
        no.setOnClickListener(v -> showHalfStage2());
        pinFooter(no);
    }

    private void showHalfStage2() {
        clear();
        primaryAction = null;
        title(Strings.chkHowTitle());
        subtitle(Strings.chkHowSub());
        final EditText input = new EditText(this);
        input.setHint(Strings.chkHowHint());
        input.setTextSize(18);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        input.setTextColor(Ui.text(this));
        input.setHintTextColor(Ui.sub(this));
        root.addView(input);
        spacer();
        // 加时间（v1.39.0）：与结束页同一套 extendTimer 机制；点完不关页、可连点
        // 累加，累计分钟显示在本区下方；不加也能照常点「想好了，继续」。
        // v1.72.0：加时区独立装进 halfAddBox——第一次点加时先过动机门，门只换
        // 这一块，上面写办法的输入框原样不动；过门后加时区重画，照常点按钮累计。
        halfAddedMin = 0;
        halfAddBox = new LinearLayout(this);
        halfAddBox.setOrientation(LinearLayout.VERTICAL);
        halfAddBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(halfAddBox);
        buildHalfAddArea();
        spacer();
        Button go = bigButton(Strings.chkGo(), true, null);
        go.setOnClickListener(v -> {
            String ev = "过半检查[" + taskName() + "]：做不完，办法=" + input.getText().toString().trim();
            if (halfAddedMin > 0) ev += "，加时 " + halfAddedMin + " 分钟";
            TimerState.logEvent(this, ev);
            finish();
        });
        pinFooter(go);
    }

    /** 过半页加时区的内容（标签＋两行小按钮＋自定义行＋累计行），全部装在 halfAddBox 里。 */
    private void buildHalfAddArea() {
        halfAddBox.removeAllViews();
        TextView addLabel = new TextView(this);
        addLabel.setText(Strings.chkHalfAddLabel());
        addLabel.setTextSize(16);
        addLabel.setTextColor(Ui.sub(this));
        addLabel.setGravity(Gravity.CENTER);
        halfAddBox.addView(addLabel);
        halfAddBox.addView(spacerView(10));
        // +50% 与结束页同一基数：当前段总时长（TimerState.totalSec）的一半，
        // 整数分钟除、至少 1 分钟（与 showEnd 的 halfSec 完全同式）。
        int totalMin = Math.max(1, TimerState.totalSec(this) / 60);
        final int halfAddMin = Math.max(1, totalMin / 2);
        LinearLayout row1 = halfAddRow();
        row1.addView(halfAddButton(Strings.chkPlus1(), 60));
        row1.addView(halfAddButton(Strings.chkPlus3(), 180));
        row1.addView(halfAddButton(Strings.chkPlus5(), 300));
        halfAddBox.addView(row1);
        halfAddBox.addView(spacerView(10));
        LinearLayout row2 = halfAddRow();
        row2.addView(halfAddButton(Strings.chkPlus10(), 600));
        row2.addView(halfAddButton(Strings.chkPlus50(halfAddMin), halfAddMin * 60));
        halfAddBox.addView(row2);
        halfAddBox.addView(spacerView(10));
        final EditText custom = new EditText(this);
        custom.setHint(Strings.chkCustomHint());
        custom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        custom.setTextSize(17);
        custom.setTextColor(Ui.text(this));
        custom.setHintTextColor(Ui.sub(this));
        custom.setGravity(Gravity.CENTER);
        halfAddBox.addView(custom);
        Button customBtn = bigButton(Strings.chkCustomBtn(), false, null);
        customBtn.setOnClickListener(v -> {
            try {
                int min = Integer.parseInt(custom.getText().toString().trim());
                if (min > 0) {
                    if (!motivePassed) { gateHalf(); return; }
                    doHalfAdd(min * 60);
                    custom.setText("");
                }
            } catch (NumberFormatException ignored) { }
        });
        halfAddBox.addView(customBtn);
        halfAddBox.addView(spacerView(10));
        halfAddedLine = new TextView(this);
        halfAddedLine.setTextSize(15);
        halfAddedLine.setTextColor(Ui.sub(this));
        halfAddedLine.setGravity(Gravity.CENTER);
        if (halfAddedMin > 0) {
            halfAddedLine.setText(Strings.chkAddedTotal(halfAddedMin));
            halfAddedLine.setVisibility(View.VISIBLE);
        } else {
            halfAddedLine.setVisibility(View.GONE);
        }
        halfAddBox.addView(halfAddedLine);
    }

    private View spacerView(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    /** 过半页的动机门（v1.72.0）：只换加时区这一块，不动写办法的输入框。 */
    private void gateHalf() {
        halfAddBox.removeAllViews();
        MotiveGate.buildInto(this, halfAddBox, () -> {
            motivePassed = true;
            buildHalfAddArea();
        });
    }

    /** 写办法页加时按钮行（一行并排，与本页大按钮同一视觉语言、格内字号略收）。 */
    private LinearLayout halfAddRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private Button halfAddButton(String text, final int addSec) {
        Button b = bigButton(text, false, null);
        b.setTextSize(17);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> {
            if (!motivePassed) { gateHalf(); return; }
            doHalfAdd(addSec);
        });
        return b;
    }

    /** 写办法页加时动作：走结束页同一 extendTimer 机制，只是不关页、累计就地更新。 */
    private void doHalfAdd(int addSec) {
        TimerService.extendTimer(this, addSec);
        halfAddedMin += addSec / 60;
        halfAddedLine.setText(Strings.chkAddedTotal(halfAddedMin));
        halfAddedLine.setVisibility(View.VISIBLE);
    }

    /** 整个任务完成：回写完成状态＋停表＋衔接守卫（结束页与子任务页共用）。 */
    private void completeWholeTask() {
        // 回写完成状态（一次性任务置 done，每日任务记今日完成）
        TaskStore store = new TaskStore(this);
        TaskItem t = store.byId(TimerState.taskId(this));
        if (t != null) {
            t.markDone(); StartBaseline.markCompleted(this, t.id); store.save(); // 完成戳：此后再开始从完整时长（v1.21.1）
            SbSync.pushDoneAsync(this, t.id); // 完成状态立刻定向写上云并回读确认（v1.14.0）
        }
        TimerState.logEvent(this, "结束检查[" + taskName() + "]：做完了");
        TimerService.stopTimer(this);
        // v1.10.0 衔接守卫：确认完成后若没顺势开始下一个，约 0.6 秒后全屏问「下一个任务」
        IdleGuard.fireSoon(CheckActivity.this);
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }

    // ---------- 子任务页（v1.11.0） ----------
    private void showSub() {
        clear();
        keyQ = keyW = keyE = null;
        final int idx = TimerState.subIndex(this);
        final int count = TimerState.subCount(this);
        final String subName = TimerState.subTitle(this);
        title(Strings.subCheckTitle());
        subtitle(Strings.subCheckSub(taskName(), idx + 1, count, subName));
        addAspiration();
        Runnable nextAct = () -> {
            TimerState.logEvent(this, "子任务检查[" + taskName() + " · " + subName + "]：完成，下一个");
            if (idx >= count - 1) { showEnd(); return; }
            TimerService.advanceSubtask(this);
            finish();
        };
        Button next = bigButton(Strings.subNext(), true, "Enter");
        next.setOnClickListener(v -> nextAct.run());
        primaryAction = nextAct;
        pinFooter(next);
        spacer();
        Button extend = bigButton(Strings.subExtend(), false, null);
        extend.setOnClickListener(v -> {
            // 子任务页有独立的「加时」意图按钮：先过动机门，过门才进选时长步（v1.72.0）
            if (motivePassed) showSubExtend();
            else gateThen(this::showSubExtend);
        });
        root.addView(extend);
        spacer();
        Button skip = bigButton(Strings.subSkip(), false, null);
        skip.setOnClickListener(v -> {
            TimerState.logEvent(this, "子任务检查[" + taskName() + " · " + subName + "]：跳过");
            if (idx >= count - 1) { showEnd(); return; }
            TimerService.advanceSubtask(this);
            finish();
        });
        root.addView(skip);
        spacer();
        Button whole = bigButton(Strings.subWholeDone(), false, null);
        whole.setOnClickListener(v -> completeWholeTask());
        root.addView(whole);
    }

    /** 子任务加时：口径与结束页相同（+50% 按当前子任务的计划分钟算）。 */
    private void showSubExtend() {
        clear();
        primaryAction = null;
        keyQ = keyW = keyE = null;
        extendBack = this::showSubExtend;
        title(Strings.subExtend());
        subtitle(Strings.subCheckSub(taskName(), TimerState.subIndex(this) + 1,
                TimerState.subCount(this), TimerState.subTitle(this)));
        TextView label = new TextView(this);
        label.setText(Strings.chkExtendLabel());
        label.setTextSize(16);
        label.setTextColor(Ui.sub(this));
        label.setGravity(Gravity.CENTER);
        root.addView(label);
        spacerSmall();
        int subMin = currentSubMinutes();
        final int halfSec = Math.max(1, subMin / 2) * 60;
        root.addView(extendButton(Strings.chkPlus50(Math.max(1, subMin / 2)), halfSec, "Q"));
        keyQ = () -> gatedExtend(halfSec);
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus5(), 300, "W"));
        keyW = () -> gatedExtend(300);
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus10(), 600, "E"));
        keyE = () -> gatedExtend(600);
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus15(), 900, null));
        spacerSmall();
        final EditText custom = new EditText(this);
        custom.setHint(Strings.chkCustomHint());
        custom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        custom.setTextSize(17);
        custom.setTextColor(Ui.text(this));
        custom.setHintTextColor(Ui.sub(this));
        custom.setGravity(Gravity.CENTER);
        root.addView(custom);
        Button customBtn = bigButton(Strings.chkCustomBtn(), false, null);
        customBtn.setOnClickListener(v -> {
            try {
                int min = Integer.parseInt(custom.getText().toString().trim());
                if (min > 0) gatedExtend(min * 60);
            } catch (NumberFormatException ignored) { }
        });
        root.addView(customBtn);
    }

    /** 当前子任务的计划分钟（从任务的子任务表按运行态 id 查，查不到退回总时长）。 */
    private int currentSubMinutes() {
        TaskItem t = new TaskStore(this).byId(TimerState.taskId(this));
        if (t != null) {
            String sid = TimerState.subId(this);
            for (Subtask s : t.subtasks) if (s.id.equals(sid)) return Math.max(1, s.minutes);
            int i = TimerState.subIndex(this);
            if (i >= 0 && i < t.subtasks.size()) return Math.max(1, t.subtasks.get(i).minutes);
        }
        return Math.max(1, TimerState.totalSec(this) / 60);
    }

    // ---------- 结束页 ----------
    private void showEnd() {
        clear();
        keyQ = keyW = keyE = null;
        extendBack = this::showEnd;
        // 结束页催促（v1.21.1）：页面真展示出来了——经响铃页转入的结束在此武装
        // 升级链（锚点＝展示时刻）；直达路径在 fire 时已武装，这里只续排无-op
        EndNag.noteEndPageShown(this);
        // 超时陪聊（v1.24.0）：真到点了才往聊天主线落一条 AI 开场消息
        // （同一轮结束只落一条，手动提前打开本页不落），下方再内嵌聊天区当场聊
        ChatHooks.onTaskEndPageShown(this);
        title(Strings.chkEndTitle());
        subtitle(Strings.chkEndSub(taskName()));
        addAspiration();
        addPrincipleCheck();
        addChatZone();
        Runnable doneAct = this::completeWholeTask;
        Button done = bigButton(Strings.chkDone(), true, "Enter");
        done.setOnClickListener(v -> doneAct.run());
        primaryAction = doneAct;
        pinFooter(done);
        spacer();
        TextView label = new TextView(this);
        label.setText(Strings.chkExtendLabel());
        label.setTextSize(16);
        label.setTextColor(Ui.sub(this));
        label.setGravity(Gravity.CENTER);
        root.addView(label);
        spacerSmall();
        int totalMin = Math.max(1, TimerState.totalSec(this) / 60);
        final int halfSec = Math.max(1, totalMin / 2) * 60;
        root.addView(extendButton(Strings.chkPlus50(Math.max(1, totalMin / 2)), halfSec, "Q"));
        keyQ = () -> gatedExtend(halfSec);
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus5(), 300, "W"));
        keyW = () -> gatedExtend(300);
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus10(), 600, "E"));
        keyE = () -> gatedExtend(600);
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus15(), 900, null));
        spacerSmall();
        final EditText custom = new EditText(this);
        custom.setHint(Strings.chkCustomHint());
        custom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        custom.setTextSize(17);
        custom.setTextColor(Ui.text(this));
        custom.setHintTextColor(Ui.sub(this));
        custom.setGravity(Gravity.CENTER);
        root.addView(custom);
        Button customBtn = bigButton(Strings.chkCustomBtn(), false, null);
        customBtn.setOnClickListener(v -> {
            try {
                int min = Integer.parseInt(custom.getText().toString().trim());
                if (min > 0) gatedExtend(min * 60);
            } catch (NumberFormatException ignored) { }
        });
        root.addView(customBtn);
    }

    /**
     * 原则陪跑问询（v1.75.0，F3）：结束页在志与聊天区之间加一段——当前任务的
     * 主原则（名＋一句话版）＋一句「这件事上，你做到这条了吗」＋三个按钮
     * （做到了／没做到／没考虑）。**不强制**：不答直接点「做完了」照常完成、
     * 不留记录、不阻塞任何流程；答了走 ZhiPage.recordPrincipleCheck（事件
     * 入待同步队列、本地先行；「没考虑」同时撤掉这条误判关联）。本页本次
     * 只问一次（答后换已记态）；没有主原则的任务整段不出现。
     */
    private void addPrincipleCheck() {
        final String taskId = TimerState.taskId(this);
        if (taskId == null || taskId.isEmpty()) return;
        final ZhiPage.TaskPrinciple tp = ZhiPage.mainPrincipleForTask(this, taskId);
        if (tp == null || tp.name == null || tp.name.isEmpty()) return;
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        root.addView(box);
        buildPrincipleCheck(box, tp, taskId);
    }

    private void buildPrincipleCheck(final LinearLayout box,
                                     final ZhiPage.TaskPrinciple tp, final String taskId) {
        box.removeAllViews();
        if (principleAnswered) {
            TextView done = new TextView(this);
            done.setText((Strings.en() ? "Principle noted · " : "已记下 · ") + principleAnswerValue);
            done.setTextSize(14);
            done.setTextColor(Ui.sub(this));
            done.setGravity(Gravity.CENTER);
            done.setPadding(0, dp(4), 0, dp(14));
            box.addView(done);
            return;
        }
        TextView name = new TextView(this);
        name.setText(tp.name);
        name.setTextSize(19);
        name.setTypeface(Typeface.SERIF, Typeface.BOLD);
        name.setTextColor(Ui.text(this));
        name.setGravity(Gravity.CENTER);
        box.addView(name);
        if (tp.line != null && !tp.line.isEmpty()) {
            TextView line = new TextView(this);
            line.setText(tp.line);
            line.setTextSize(14);
            line.setTextColor(Ui.sub(this));
            line.setGravity(Gravity.CENTER);
            line.setPadding(0, dp(5), 0, 0);
            box.addView(line);
        }
        TextView q = new TextView(this);
        q.setText(Strings.en() ? "On this task, did you live up to it?"
                : "这件事上，你做到这条了吗");
        q.setTextSize(15);
        q.setTextColor(Ui.text(this));
        q.setGravity(Gravity.CENTER);
        q.setPadding(0, dp(12), 0, dp(10));
        box.addView(q);
        String[] values = {ZhiPage.PC_DONE, ZhiPage.PC_MISSED, ZhiPage.PC_NOCARE};
        String[] labels = {Strings.en() ? "Did it" : ZhiPage.PC_DONE,
                Strings.en() ? "Didn't" : ZhiPage.PC_MISSED,
                Strings.en() ? "Didn't think of it" : ZhiPage.PC_NOCARE};
        for (int i = 0; i < values.length; i++) {
            final String val = values[i];
            Button b = bigButton(labels[i], i == 0, null);
            b.setOnClickListener(v -> {
                ZhiPage.recordPrincipleCheck(CheckActivity.this, tp.id, taskId, val);
                principleAnswered = true;
                principleAnswerValue = val;
                buildPrincipleCheck(box, tp, taskId);
            });
            box.addView(b);
            spacerSmall();
        }
    }

    /**
     * 结束页内嵌聊天区（v1.24.0）：跟「聊聊」页签同一个 ChatPanel（紧凑模式，
     * 显示主线最近几条＋输入框），上下文就是刚结束的这个任务。在这里聊的每一句
     * 都实时进同一条主线，回页签能接着看。原有按钮（做完了/加时）位置与行为不动，
     * 聊天区只是夹在志与按钮之间的一段；代理没部署时发送走既有友好报错，不假装发出。
     */
    private void addChatZone() {
        TextView cap = new TextView(this);
        cap.setText(Strings.en() ? "Talk it over with AI first" : "先跟 AI 聊聊");
        cap.setTextSize(14);
        cap.setTextColor(Ui.sub(this));
        cap.setGravity(Gravity.CENTER);
        cap.setPadding(0, 0, 0, dp(6));
        root.addView(cap);
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Ui.surface(this));
        g.setStroke(1, Ui.line(this));
        holder.setBackground(g);
        root.addView(holder, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (endChatPanel != null) endChatPanel.detach();
        endChatPanel = new ChatPanel(this, holder, true);
        endChatPanel.setTaskContext(TimerState.taskId(this));
        spacer();
    }

    private Button extendButton(String text, final int addSec, String kbd) {
        Button b = bigButton(text, false, kbd);
        b.setOnClickListener(v -> gatedExtend(addSec));
        return b;
    }

    /**
     * 加时动机门（v1.72.0）：未过门时把本页换成自问层（MotiveGate 同一层组件）——
     * 点「有/没有」先记一条动机事件（本地先行、立即放行），随后回到原来的
     * 选时长步（extendBack 指向的阶段）重新选时长，这次点选本身不执行。
     * 门内没有关闭/跳过入口；键盘 Q/W/E 在门层同样不生效（keyQ 等已清空）。
     */
    private void gateThen(final Runnable backToChoices) {
        clear();
        primaryAction = null;
        keyQ = keyW = keyE = null;
        MotiveGate.buildInto(this, root, () -> {
            motivePassed = true;
            backToChoices.run();
        });
    }

    /** 选了时长先过门：已过门直接执行原加时；没过门先过门、回选时长步重选。 */
    private void gatedExtend(int addSec) {
        if (motivePassed) { doExtend(addSec); return; }
        gateThen(extendBack);
    }

    private void doExtend(int addSec) {
        TimerState.logEvent(this, "结束检查[" + taskName() + "]：加时 " + (addSec / 60) + " 分钟");
        TimerService.extendTimer(this, addSec);
        finish();
    }

    /** 立志常驻（v1.12.0）：结束页与子任务页副标题下展示「我的志」，未设置时什么都不加。 */
    private void addAspiration() {
        TextView asp = Aspiration.makeView(this, Ui.dark(this));
        if (asp == null) return;
        asp.setPadding(0, 0, 0, dp(24));
        root.addView(asp, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // ---------- 小件 ----------
    private void clear() {
        root.removeAllViews();
        if (footer != null) {
            footer.removeAllViews();
            footer.setVisibility(View.GONE);
        }
    }

    /** 主行动按钮钉进固定底栏（v1.40.0）：内容再长、键盘弹起都稳停页面最底部。 */
    private void pinFooter(View v) {
        footer.setVisibility(View.VISIBLE);
        footer.addView(v);
    }

    private void footerSpacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(14)));
        footer.addView(v);
    }

    private void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(30);
        t.setTextColor(Ui.text(this));
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(10));
        root.addView(t);
    }

    private void subtitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(17);
        t.setTextColor(Ui.sub(this));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(30));
        root.addView(t);
    }

    /** 极简大按钮：主按钮＝强调色填充；次按钮＝描边。kbd 非空时在文案尾部缀一个小字快捷键提示。 */
    private Button bigButton(String s, boolean primary, String kbd) {
        Button b = new Button(this);
        CharSequence label = s;
        if (kbd != null) {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(s + "  · " + kbd);
            int st = s.length();
            sb.setSpan(new android.text.style.RelativeSizeSpan(0.62f), st, sb.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new android.text.style.ForegroundColorSpan(
                    primary ? Ui.onAccent(this) : Ui.sub(this)), st, sb.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            label = sb;
        }
        b.setText(label);
        b.setTextSize(20);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        if (primary) {
            g.setColor(Ui.accent(this));
            b.setTextColor(Ui.onAccent(this));
        } else {
            g.setColor(Ui.surface(this));
            g.setStroke(dp(1), Ui.line(this));
            b.setTextColor(Ui.text(this));
        }
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68));
        b.setLayoutParams(lp);
        return b;
    }

    private void spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        root.addView(v);
    }

    private void spacerSmall() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(10)));
        root.addView(v);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void vibrate() {
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib == null) return;
        if (type == AlertHelper.TYPE_END || type == AlertHelper.TYPE_SUB) {
            vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400}, -1));
        } else {
            vib.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }
}
