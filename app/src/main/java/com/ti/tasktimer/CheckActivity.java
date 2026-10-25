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
    private Runnable primaryAction; // 回车触发的主按钮动作（当前阶段的主按钮）
    private Runnable keyQ, keyW, keyE; // 结束页前三个加时选项
    private ChatPanel endChatPanel; // 结束页内嵌聊天区（v1.24.0，只在 showEnd 里建）

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
        setContentView(sv);

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
        root.addView(ok);
        spacer();
        Button no = bigButton(Strings.chkCannot(), false, null);
        no.setOnClickListener(v -> showHalfStage2());
        root.addView(no);
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
        Button go = bigButton(Strings.chkGo(), true, null);
        go.setOnClickListener(v -> {
            TimerState.logEvent(this, "过半检查[" + taskName() + "]：做不完，办法=" + input.getText().toString().trim());
            finish();
        });
        root.addView(go);
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
        root.addView(next);
        spacer();
        Button extend = bigButton(Strings.subExtend(), false, null);
        extend.setOnClickListener(v -> showSubExtend());
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
        keyQ = () -> askStateThenExtend(halfSec);
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus5(), 300, "W"));
        keyW = () -> askStateThenExtend(300);
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus10(), 600, "E"));
        keyE = () -> askStateThenExtend(600);
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
                if (min > 0) askStateThenExtend(min * 60);
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
        // 结束页催促（v1.21.1）：页面真展示出来了——经响铃页转入的结束在此武装
        // 升级链（锚点＝展示时刻）；直达路径在 fire 时已武装，这里只续排无-op
        EndNag.noteEndPageShown(this);
        // 超时陪聊（v1.24.0）：真到点了才往聊天主线落一条 AI 开场消息
        // （同一轮结束只落一条，手动提前打开本页不落），下方再内嵌聊天区当场聊
        ChatHooks.onTaskEndPageShown(this);
        title(Strings.chkEndTitle());
        subtitle(Strings.chkEndSub(taskName()));
        addAspiration();
        addChatZone();
        Runnable doneAct = this::completeWholeTask;
        Button done = bigButton(Strings.chkDone(), true, "Enter");
        done.setOnClickListener(v -> doneAct.run());
        primaryAction = doneAct;
        root.addView(done);
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
        keyQ = () -> askStateThenExtend(halfSec);
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus5(), 300, "W"));
        keyW = () -> askStateThenExtend(300);
        spacerSmall();
        root.addView(extendButton(Strings.chkPlus10(), 600, "E"));
        keyE = () -> askStateThenExtend(600);
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
                if (min > 0) askStateThenExtend(min * 60);
            } catch (NumberFormatException ignored) { }
        });
        root.addView(customBtn);
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
        b.setOnClickListener(v -> askStateThenExtend(addSec));
        return b;
    }

    private void doExtend(int addSec) {
        TimerState.logEvent(this, "结束检查[" + taskName() + "]：加时 " + (addSec / 60) + " 分钟");
        TimerService.extendTimer(this, addSec);
        finish();
    }

    // ---------- 磨练自检：加时前先记状态（v1.12.0） ----------
    /**
     * 所有加时入口（结束页/子任务页的按钮、键盘 Q/W/E、自定义分钟）都先过这一页：
     * 点一个当前状态（平静/烦/躁/昏沉/贪多/求快）→ 记录一条 extend 自检 → 真正加时；
     * 点不显眼的「直接加时」→ 不记录、照常加时。
     */
    private void askStateThenExtend(final int addSec) {
        clear();
        primaryAction = null;
        keyQ = keyW = keyE = null;
        title(Strings.scExtendAsk());
        subtitle(taskName() + " · +" + Math.max(1, addSec / 60) + (Strings.en() ? " min" : " 分钟"));
        String[] states = Strings.selfCheckStates();
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            root.addView(row);
            for (int k = 0; k < 3; k++) {
                final int i = r * 3 + k;
                Button chip = scStateChip(states[i]);
                chip.setOnClickListener(v -> {
                    // 存标准存值（与界面语言无关）
                    SelfChecks.add(this, "extend", null, SelfChecks.STATES[i], TimerState.taskId(this));
                    doExtend(addSec);
                });
                row.addView(chip);
            }
            spacerSmall();
        }
        Button direct = new Button(this);
        direct.setText(Strings.scDirectExtend());
        direct.setTextSize(14);
        direct.setAllCaps(false);
        direct.setTextColor(Ui.sub(this));
        direct.setBackgroundColor(Color.TRANSPARENT);
        direct.setOnClickListener(v -> doExtend(addSec));
        root.addView(direct);
    }

    private Button scStateChip(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(17);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(Ui.surface(this));
        g.setStroke(dp(1), Ui.line(this));
        b.setBackground(g);
        b.setTextColor(Ui.text(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
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
    private void clear() { root.removeAllViews(); }

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
