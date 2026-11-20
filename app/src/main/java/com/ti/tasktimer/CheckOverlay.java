package com.ti.tasktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 检查页的 99% 大窗内容（Guild Issue #6 第二批）：与 CheckActivity 同画面、
 * 同文案、同动作、同记录——过半页（能做完／做不完→写办法＋页内加时）、
 * 子任务页、结束页（做完了／加时／原则陪跑问询／内嵌聊天区）三种形态，
 * 加时动机门页内状态（motivePassed）、原则问询已答状态、过半页累计加时
 * 都由本内容对象持有（等价原页的实例字段），阶段用 stage 记录、被抢占
 * 收回后复出时按当前阶段重建。按钮回调走与原页相同的处理函数：
 * completeWholeTask 的完成回写＋pushDoneAsync、doExtend 的 extendTimer、
 * ZhiPage.recordPrincipleCheck 的 principle_check 记录、MotiveGate 的
 * motive_check 记录，触发点与口径一律不动。
 *
 * 与原页的差异（路径适配，如实列明）：
 * 1. 物理键盘快捷键（回车/Q/W/E）是 Activity 的 onKeyDown，原页按钮上的
 *    快捷键小字提示在大窗里不画（窗口层没有等价的按键分发）。
 * 2. 跨设备跟随：原页注册的 ACTION_TIMER_REMOTE 广播接收器改在 onShown
 *    注册、onDismissed 注销，判定逻辑逐字相同；另加 tick 每秒查
 *    TimerState.isRunning 兜底（与 IdleOverlay 同套路）。
 * 3. 输入框（写办法／自定义分钟／聊天区）在大窗里点按时走
 *    FloatingWindow.focusOverlayInput 弹键盘（悬浮窗既有的输入焦点机制）。
 * 4. 被抢占重建时输入框里未提交的草稿文字不保留（原页是 Activity 常驻
 *    实例，没有这个情形）；已落盘的记录与状态都不受影响。
 */
public final class CheckOverlay implements BigOverlay.Content {
    private Context ctx;
    private int type;
    private LinearLayout root;
    private LinearLayout footer;
    private ChatPanel endChatPanel;
    private boolean motivePassed;
    private Runnable extendBack;
    private boolean principleAnswered;
    private String principleAnswerValue = "";
    private LinearLayout halfAddBox;
    private int halfAddedMin;
    private TextView halfAddedLine;
    private Runnable stage;
    private boolean receiverRegistered;

    // 跨设备跟随（与 CheckActivity 同逻辑）：另一端已处理时本窗关掉
    private final BroadcastReceiver timerRemoteReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (ctx == null) return;
            if (!TimerState.isRunning(ctx)) {
                BigOverlay.dismiss(ctx, BigOverlay.KEY_CHECK);
            } else if (type == AlertHelper.TYPE_END || type == AlertHelper.TYPE_SUB) {
                BigOverlay.dismiss(ctx, BigOverlay.KEY_CHECK);
            }
        }
    };

    /** 新一次触发先重置（等价原页新开实例）：状态清零、旧聊天区摘监听。 */
    void prepare(int newType) {
        type = newType;
        motivePassed = false;
        extendBack = null;
        principleAnswered = false;
        principleAnswerValue = "";
        halfAddedMin = 0;
        stage = null;
        if (endChatPanel != null) { endChatPanel.detach(); endChatPanel = null; }
    }

    @Override
    public View createView(Context c) {
        ctx = c;
        Strings.init(c);
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(c));
        int pad = dp(28);
        root.setPadding(pad, dp(56), pad, pad);
        ScrollView sv = new ScrollView(c);
        sv.setBackgroundColor(Ui.bg(c));
        sv.setFillViewport(true);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sv.addView(root);
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.bg(c));
        page.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        footer = new LinearLayout(c);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setBackgroundColor(Ui.bg(c));
        footer.setPadding(pad, dp(8), pad, pad);
        footer.setVisibility(View.GONE);
        page.addView(footer);

        if (stage != null) stage.run();
        else if (type == AlertHelper.TYPE_HALF) showHalfStage1();
        else if (type == AlertHelper.TYPE_SUB) showSub();
        else if (TimerState.subActive(c)
                && TimerState.subIndex(c) < TimerState.subCount(c) - 1) {
            // 与原页 onCreate 同分支：手动打开结束页但子任务没跑到最后一个，先过子任务检查
            showSub();
        } else showEnd();
        return page;
    }

    @Override
    public void onShown(Context c) {
        TopPages.checkShown = true; // 与原页 onResume 同登记
        AlertHelper.cancelAlert(c); // 与原页 onCreate 同一步
        vibrate(c);
        if (!receiverRegistered) {
            try {
                IntentFilter tf = new IntentFilter(SbTimerSync.ACTION_TIMER_REMOTE);
                if (Build.VERSION.SDK_INT >= 33) {
                    c.registerReceiver(timerRemoteReceiver, tf, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    c.registerReceiver(timerRemoteReceiver, tf);
                }
                receiverRegistered = true;
            } catch (Exception ignored) { }
        }
    }

    @Override
    public boolean tick(Context c) {
        // 计时已被别处结束/停表：本页没有存在的意义了（接收器逻辑的每秒兜底）
        return TimerState.isRunning(c);
    }

    @Override
    public void onDismissed(Context c) {
        TopPages.checkShown = false;
        if (receiverRegistered) {
            try { c.unregisterReceiver(timerRemoteReceiver); } catch (Exception ignored) { }
            receiverRegistered = false;
        }
        if (endChatPanel != null) { endChatPanel.detach(); endChatPanel = null; }
        FloatingWindow.endOverlayInputFocus(c);
    }

    private void finish() { BigOverlay.dismiss(ctx, BigOverlay.KEY_CHECK); }

    private String taskName() { return TimerState.taskTitle(ctx); }

    // ---------- 过半页 ----------
    private void showHalfStage1() {
        stage = this::showHalfStage1;
        clear();
        title(Strings.chkHalfTitle());
        subtitle(Strings.chkHalfSub(taskName()));
        Button ok = bigButton(Strings.chkCan(), true);
        ok.setOnClickListener(v -> {
            TimerState.logEvent(ctx, "过半检查[" + taskName() + "]：能做完");
            finish();
        });
        pinFooter(ok);
        footerSpacer();
        Button no = bigButton(Strings.chkCannot(), false);
        no.setOnClickListener(v -> showHalfStage2());
        pinFooter(no);
    }

    private void showHalfStage2() {
        stage = this::showHalfStage2;
        clear();
        title(Strings.chkHowTitle());
        subtitle(Strings.chkHowSub());
        final EditText input = new EditText(ctx);
        input.setHint(Strings.chkHowHint());
        input.setTextSize(18);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        input.setTextColor(Ui.text(ctx));
        input.setHintTextColor(Ui.sub(ctx));
        hookInput(input);
        root.addView(input);
        spacer();
        halfAddedMin = 0;
        halfAddBox = new LinearLayout(ctx);
        halfAddBox.setOrientation(LinearLayout.VERTICAL);
        halfAddBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(halfAddBox);
        buildHalfAddArea();
        spacer();
        Button go = bigButton(Strings.chkGo(), true);
        go.setOnClickListener(v -> {
            String ev = "过半检查[" + taskName() + "]：做不完，办法=" + input.getText().toString().trim();
            if (halfAddedMin > 0) ev += "，加时 " + halfAddedMin + " 分钟";
            TimerState.logEvent(ctx, ev);
            finish();
        });
        pinFooter(go);
    }

    /** 过半页加时区的内容（与 CheckActivity.buildHalfAddArea 逐项对应）。 */
    private void buildHalfAddArea() {
        halfAddBox.removeAllViews();
        TextView addLabel = new TextView(ctx);
        addLabel.setText(Strings.chkHalfAddLabel());
        addLabel.setTextSize(16);
        addLabel.setTextColor(Ui.sub(ctx));
        addLabel.setGravity(Gravity.CENTER);
        halfAddBox.addView(addLabel);
        halfAddBox.addView(spacerView(10));
        int totalMin = Math.max(1, TimerState.totalSec(ctx) / 60);
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
        final EditText custom = new EditText(ctx);
        custom.setHint(Strings.chkCustomHint());
        custom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        custom.setTextSize(17);
        custom.setTextColor(Ui.text(ctx));
        custom.setHintTextColor(Ui.sub(ctx));
        custom.setGravity(Gravity.CENTER);
        hookInput(custom);
        halfAddBox.addView(custom);
        Button customBtn = bigButton(Strings.chkCustomBtn(), false);
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
        halfAddedLine = new TextView(ctx);
        halfAddedLine.setTextSize(15);
        halfAddedLine.setTextColor(Ui.sub(ctx));
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
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    /** 过半页的动机门：只换加时区这一块，不动写办法的输入框（与原页同）。 */
    private void gateHalf() {
        halfAddBox.removeAllViews();
        MotiveGate.buildInto(ctx, halfAddBox, () -> {
            motivePassed = true;
            buildHalfAddArea();
        });
    }

    private LinearLayout halfAddRow() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private Button halfAddButton(String text, final int addSec) {
        Button b = bigButton(text, false);
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
        TimerService.extendTimer(ctx, addSec);
        halfAddedMin += addSec / 60;
        halfAddedLine.setText(Strings.chkAddedTotal(halfAddedMin));
        halfAddedLine.setVisibility(View.VISIBLE);
    }

    /** 整个任务完成：回写完成状态＋停表＋衔接守卫（与 CheckActivity.completeWholeTask 逐项对应）。 */
    private void completeWholeTask() {
        TaskStore store = new TaskStore(ctx);
        TaskItem t = store.byId(TimerState.taskId(ctx));
        if (t != null) {
            t.markDone(); StartBaseline.markCompleted(ctx, t.id); store.save();
            SbSync.pushDoneAsync(ctx, t.id);
        }
        TimerState.logEvent(ctx, "结束检查[" + taskName() + "]：做完了");
        TimerService.stopTimer(ctx);
        FindNextTimer.start(ctx);
        MainActivity.openXing(ctx);
        finish();
    }

    // ---------- 子任务页 ----------
    private void showSub() {
        stage = this::showSub;
        clear();
        final int idx = TimerState.subIndex(ctx);
        final int count = TimerState.subCount(ctx);
        final String subName = TimerState.subTitle(ctx);
        title(Strings.subCheckTitle());
        subtitle(Strings.subCheckSub(taskName(), idx + 1, count, subName));
        addAspiration();
        Button next = bigButton(Strings.subNext(), true);
        next.setOnClickListener(v -> {
            TimerState.logEvent(ctx, "子任务检查[" + taskName() + " · " + subName + "]：完成，下一个");
            if (idx >= count - 1) { showEnd(); return; }
            TimerService.advanceSubtask(ctx);
            finish();
        });
        pinFooter(next);
        spacer();
        Button extend = bigButton(Strings.subExtend(), false);
        extend.setOnClickListener(v -> {
            if (motivePassed) showSubExtend();
            else gateThen(this::showSubExtend);
        });
        root.addView(extend);
        spacer();
        Button skip = bigButton(Strings.subSkip(), false);
        skip.setOnClickListener(v -> {
            TimerState.logEvent(ctx, "子任务检查[" + taskName() + " · " + subName + "]：跳过");
            if (idx >= count - 1) { showEnd(); return; }
            TimerService.advanceSubtask(ctx);
            finish();
        });
        root.addView(skip);
        spacer();
        Button whole = bigButton(Strings.subWholeDone(), false);
        whole.setOnClickListener(v -> completeWholeTask());
        root.addView(whole);
    }

    /** 子任务加时：口径与结束页相同（+50% 按当前子任务的计划分钟算）。 */
    private void showSubExtend() {
        stage = this::showSubExtend;
        clear();
        extendBack = this::showSubExtend;
        title(Strings.subExtend());
        subtitle(Strings.subCheckSub(taskName(), TimerState.subIndex(ctx) + 1,
                TimerState.subCount(ctx), TimerState.subTitle(ctx)));
        TextView label = new TextView(ctx);
        label.setText(Strings.chkExtendLabel());
        label.setTextSize(16);
        label.setTextColor(Ui.sub(ctx));
        label.setGravity(Gravity.CENTER);
        root.addView(label);
        spacerSmall();
        int subMin = currentSubMinutes();
        final int halfSec = Math.max(1, subMin / 2) * 60;
        root.addView(extendButton(Strings.chkPlus50(Math.max(1, subMin / 2)), halfSec));
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus5(), 300));
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus10(), 600));
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus15(), 900));
        spacerSmall();
        final EditText custom = new EditText(ctx);
        custom.setHint(Strings.chkCustomHint());
        custom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        custom.setTextSize(17);
        custom.setTextColor(Ui.text(ctx));
        custom.setHintTextColor(Ui.sub(ctx));
        custom.setGravity(Gravity.CENTER);
        hookInput(custom);
        root.addView(custom);
        Button customBtn = bigButton(Strings.chkCustomBtn(), false);
        customBtn.setOnClickListener(v -> {
            try {
                int min = Integer.parseInt(custom.getText().toString().trim());
                if (min > 0) gatedExtend(min * 60);
            } catch (NumberFormatException ignored) { }
        });
        root.addView(customBtn);
    }

    /** 当前子任务的计划分钟（与 CheckActivity.currentSubMinutes 同查法）。 */
    private int currentSubMinutes() {
        TaskItem t = new TaskStore(ctx).byId(TimerState.taskId(ctx));
        if (t != null) {
            String sid = TimerState.subId(ctx);
            for (Subtask s : t.subtasks) if (s.id.equals(sid)) return Math.max(1, s.minutes);
            int i = TimerState.subIndex(ctx);
            if (i >= 0 && i < t.subtasks.size()) return Math.max(1, t.subtasks.get(i).minutes);
        }
        return Math.max(1, TimerState.totalSec(ctx) / 60);
    }

    // ---------- 结束页 ----------
    private void showEnd() {
        stage = this::showEnd;
        clear();
        extendBack = this::showEnd;
        // 与原页 showEnd 同两步：展示即武装结束催促、落超时陪聊开场
        EndNag.noteEndPageShown(ctx);
        ChatHooks.onTaskEndPageShown(ctx);
        title(Strings.chkEndTitle());
        subtitle(Strings.chkEndSub(taskName()));
        addAspiration();
        addPrincipleCheck();
        addChatZone();
        Button done = bigButton(Strings.chkDone(), true);
        done.setOnClickListener(v -> completeWholeTask());
        pinFooter(done);
        spacer();
        TextView label = new TextView(ctx);
        label.setText(Strings.chkExtendLabel());
        label.setTextSize(16);
        label.setTextColor(Ui.sub(ctx));
        label.setGravity(Gravity.CENTER);
        root.addView(label);
        spacerSmall();
        int totalMin = Math.max(1, TimerState.totalSec(ctx) / 60);
        final int halfSec = Math.max(1, totalMin / 2) * 60;
        root.addView(extendButton(Strings.chkPlus50(Math.max(1, totalMin / 2)), halfSec));
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus5(), 300));
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus10(), 600));
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus15(), 900));
        spacerSmall();
        final EditText custom = new EditText(ctx);
        custom.setHint(Strings.chkCustomHint());
        custom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        custom.setTextSize(17);
        custom.setTextColor(Ui.text(ctx));
        custom.setHintTextColor(Ui.sub(ctx));
        custom.setGravity(Gravity.CENTER);
        hookInput(custom);
        root.addView(custom);
        Button customBtn = bigButton(Strings.chkCustomBtn(), false);
        customBtn.setOnClickListener(v -> {
            try {
                int min = Integer.parseInt(custom.getText().toString().trim());
                if (min > 0) gatedExtend(min * 60);
            } catch (NumberFormatException ignored) { }
        });
        root.addView(customBtn);
    }

    /**
     * 原则陪跑问询（与 CheckActivity.addPrincipleCheck 逐项对应）：不答可直接
     * 完成、不留记录；答了走 ZhiPage.recordPrincipleCheck（principle_check
     * 事件，本地先行）；本窗本次只问一次，答后换已记态。
     */
    private void addPrincipleCheck() {
        final String taskId = TimerState.taskId(ctx);
        if (taskId == null || taskId.isEmpty()) return;
        final ZhiPage.TaskPrinciple tp = ZhiPage.mainPrincipleForTask(ctx, taskId);
        if (tp == null || tp.name == null || tp.name.isEmpty()) return;
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        root.addView(box);
        buildPrincipleCheck(box, tp, taskId);
    }

    private void buildPrincipleCheck(final LinearLayout box,
                                     final ZhiPage.TaskPrinciple tp, final String taskId) {
        box.removeAllViews();
        if (principleAnswered) {
            TextView done = new TextView(ctx);
            done.setText((Strings.en() ? "Principle noted · " : "已记下 · ") + principleAnswerValue);
            done.setTextSize(14);
            done.setTextColor(Ui.sub(ctx));
            done.setGravity(Gravity.CENTER);
            done.setPadding(0, dp(4), 0, dp(14));
            box.addView(done);
            return;
        }
        TextView name = new TextView(ctx);
        name.setText(tp.name);
        name.setTextSize(19);
        name.setTypeface(Typeface.SERIF, Typeface.BOLD);
        name.setTextColor(Ui.text(ctx));
        name.setGravity(Gravity.CENTER);
        box.addView(name);
        if (tp.line != null && !tp.line.isEmpty()) {
            TextView line = new TextView(ctx);
            line.setText(tp.line);
            line.setTextSize(14);
            line.setTextColor(Ui.sub(ctx));
            line.setGravity(Gravity.CENTER);
            line.setPadding(0, dp(5), 0, 0);
            box.addView(line);
        }
        TextView q = new TextView(ctx);
        q.setText(Strings.en() ? "On this task, did you live up to it?"
                : "这件事上，你做到这条了吗");
        q.setTextSize(15);
        q.setTextColor(Ui.text(ctx));
        q.setGravity(Gravity.CENTER);
        q.setPadding(0, dp(12), 0, dp(10));
        box.addView(q);
        String[] values = {ZhiPage.PC_DONE, ZhiPage.PC_MISSED, ZhiPage.PC_NOCARE};
        String[] labels = {Strings.en() ? "Did it" : ZhiPage.PC_DONE,
                Strings.en() ? "Didn't" : ZhiPage.PC_MISSED,
                Strings.en() ? "Didn't think of it" : ZhiPage.PC_NOCARE};
        for (int i = 0; i < values.length; i++) {
            final String val = values[i];
            Button b = bigButton(labels[i], i == 0);
            b.setOnClickListener(v -> {
                ZhiPage.recordPrincipleCheck(ctx, tp.id, taskId, val);
                principleAnswered = true;
                principleAnswerValue = val;
                buildPrincipleCheck(box, tp, taskId);
            });
            box.addView(b);
            spacerSmall();
        }
    }

    /** 结束页内嵌聊天区（与 CheckActivity.addChatZone 对应）：同一个 ChatPanel（紧凑模式）。 */
    private void addChatZone() {
        TextView cap = new TextView(ctx);
        cap.setText(Strings.en() ? "Talk it over with AI first" : "先跟 AI 聊聊");
        cap.setTextSize(14);
        cap.setTextColor(Ui.sub(ctx));
        cap.setGravity(Gravity.CENTER);
        cap.setPadding(0, 0, 0, dp(6));
        root.addView(cap);
        LinearLayout holder = new LinearLayout(ctx);
        holder.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Ui.surface(ctx));
        g.setStroke(1, Ui.line(ctx));
        holder.setBackground(g);
        root.addView(holder, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (endChatPanel != null) endChatPanel.detach();
        endChatPanel = new ChatPanel(ctx, holder, true);
        endChatPanel.setTaskContext(TimerState.taskId(ctx));
        hookInputsRecursive(holder);
        spacer();
    }

    private Button extendButton(String text, final int addSec) {
        Button b = bigButton(text, false);
        b.setOnClickListener(v -> gatedExtend(addSec));
        return b;
    }

    /** 加时动机门（与原页 gateThen 同）：整页换成自问层，答完记事件再回选时长步重选。 */
    private void gateThen(final Runnable backToChoices) {
        stage = () -> gateThen(backToChoices);
        clear();
        MotiveGate.buildInto(ctx, root, () -> {
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
        TimerState.logEvent(ctx, "结束检查[" + taskName() + "]：加时 " + (addSec / 60) + " 分钟");
        TimerService.extendTimer(ctx, addSec);
        finish();
    }

    /** 立志常驻：结束页与子任务页副标题下展示「我的志」，未设置时什么都不加。 */
    private void addAspiration() {
        TextView asp = Aspiration.makeView(ctx, Ui.dark(ctx));
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

    private void pinFooter(View v) {
        footer.setVisibility(View.VISIBLE);
        footer.addView(v);
    }

    private void footerSpacer() {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(14)));
        footer.addView(v);
    }

    private void title(String s) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(30);
        t.setTextColor(Ui.text(ctx));
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(10));
        root.addView(t);
    }

    private void subtitle(String s) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(17);
        t.setTextColor(Ui.sub(ctx));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(30));
        root.addView(t);
    }

    /** 极简大按钮（与 CheckActivity.bigButton 同视觉；大窗无键盘，省去快捷键小字）。 */
    private Button bigButton(String s, boolean primary) {
        Button b = new Button(ctx);
        b.setText(s);
        b.setTextSize(20);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        if (primary) {
            g.setColor(Ui.accent(ctx));
            b.setTextColor(Ui.onAccent(ctx));
        } else {
            g.setColor(Ui.surface(ctx));
            g.setStroke(dp(1), Ui.line(ctx));
            b.setTextColor(Ui.text(ctx));
        }
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68));
        b.setLayoutParams(lp);
        return b;
    }

    /** 输入框点按时走悬浮窗的输入焦点机制弹键盘（大窗窗口默认藏键盘）。 */
    private void hookInput(final EditText e) {
        e.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                FloatingWindow.focusOverlayInput(ctx, e);
            }
            return false;
        });
    }

    private void hookInputsRecursive(View v) {
        if (v instanceof EditText) { hookInput((EditText) v); return; }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) hookInputsRecursive(g.getChildAt(i));
        }
    }

    private void spacer() {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        root.addView(v);
    }

    private void spacerSmall() {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(10)));
        root.addView(v);
    }

    private int dp(int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 与 CheckActivity.vibrate 同：结束/子任务双段震，过半单震。 */
    private void vibrate(Context c) {
        Vibrator vib = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        if (vib == null) return;
        if (type == AlertHelper.TYPE_END || type == AlertHelper.TYPE_SUB) {
            vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400}, -1));
        } else {
            vib.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }
}
