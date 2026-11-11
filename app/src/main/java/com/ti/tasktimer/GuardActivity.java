package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 专注守卫全屏问询页：计时中打开了无关软件时弹出。
 * 按钮：立即回到任务（跳回主软件/当前任务页）/「最后再看多久」四钮（马上封／
 * 再看 1、3、5 分钟，点任一钮即记一次分神，宽限到点按处置设置执行）/
 * 这是误判，本次放行 / 和 AI 聊聊。（v1.80.0 起本页不再有自检行。）
 */
public class GuardActivity extends Activity {
    public static final String EXTRA_PKG = "fg_pkg";
    /** 屏幕内容守卫模式（EXTRA_SOURCE="content"）：按内容类别问询，证据在 EXTRA_DETAIL。 */
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_DETAIL = "detail";
    /** 名单黑问询的理由（v1.26.0）：一句完整的话，点明该软件在当前任务黑名单里；缺省走原文案。 */
    public static final String EXTRA_REASON = "reason";

    private String fgPkg;
    private boolean contentMode;
    private String category = "";

    // 分神记录（v1.80.0）：点任一宽限钮＝承认分神，在这里记一次等价于旧
    // 「没有」的自检记录（didRight=false）；每次问询只记一次
    private boolean driftRecorded;

    // 宽限到点处理方式（v1.21.3 布尔 → v1.80.0 三档枚举，仅 content 模式显示选择行）
    private String graceAction = ScreenGuardPrefs.ACTION_BLOCK;
    private final List<Button> graceModeBtns = new ArrayList<>();

    private ScrollView scrollView;
    private LinearLayout actionsBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fgPkg = getIntent().getStringExtra(EXTRA_PKG);
        if (fgPkg == null) fgPkg = "";
        contentMode = "content".equals(getIntent().getStringExtra(EXTRA_SOURCE));
        if (contentMode) {
            String cat = getIntent().getStringExtra(EXTRA_CATEGORY);
            category = cat == null ? "" : cat;
        }
        AlertHelper.cancelGuard(this);
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib != null) vib.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 243, 236));
        int pad = dp(28);
        root.setPadding(pad, dp(56), pad, pad);
        // 整页可滚动（v1.25.0 修复）：本页内容＝标题＋多行说明＋立志行＋宽限钮
        // ＋多个按钮，总高在小屏/大字体下可能超出一屏；裸 LinearLayout 直接
        // setContentView、无 ScrollView 时，超出的底部按钮会被挤出屏幕且滑不到。
        // 套上 ScrollView 后任何内容组合下所有按钮都可达；fillViewport 让短内容
        // （专注模式无宽限行时）视觉与原来一致。
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(247, 243, 236));
        scrollView.setFillViewport(true);
        scrollView.addView(root);
        setContentView(scrollView);

        TextView title = new TextView(this);
        title.setText("你是在做正确的任务吗？");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(31, 47, 42));
        root.addView(title);

        TextView sub = new TextView(this);
        if (contentMode) {
            String detail = getIntent().getStringExtra(EXTRA_DETAIL);
            sub.setText("当前任务：「" + TimerState.taskTitle(this) + "」\n屏幕内容守卫检测到「"
                    + ContentAnalyzer.label(category) + "」："
                    + (detail == null ? "" : detail)
                    + "\n截图只在本机分析、已经销毁，不会保存或上传。");
        } else {
            String reason = getIntent().getStringExtra(EXTRA_REASON);
            if (reason != null && !reason.isEmpty()) {
                // 名单黑问询（v1.26.0）：理由本身已点明「在黑名单里」，直接作副文案
                sub.setText("当前任务：「" + TimerState.taskTitle(this) + "」\n" + reason + "。");
            } else {
                String appLabel = AppLister.labelOf(this, fgPkg);
                sub.setText("当前任务：「" + TimerState.taskTitle(this) + "」\n你打开了「" + appLabel + "」，它不在这个任务的专注规则里。");
            }
        }
        sub.setTextSize(18);
        sub.setTextColor(Color.rgb(31, 47, 42));
        sub.setPadding(0, dp(12), 0, dp(30));
        root.addView(sub);

        // 立志常驻（v1.12.0）：设了志就在问询页上多一行金字，没设不显示
        TextView asp = Aspiration.makeView(this, false);
        if (asp != null) {
            asp.setPadding(0, 0, 0, dp(22));
            root.addView(asp, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }


        // 应答按钮区
        actionsBox = new LinearLayout(this);
        actionsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(actionsBox);

        Button now = bigButton("立即回到任务", Color.rgb(46, 125, 50));
        now.setOnClickListener(v -> {
            if (contentMode) VerdictEngine.contentImmediate(this);
            else FocusGuard.chooseImmediate(this);
            finish();
        });
        actionsBox.addView(now);
        spacer(actionsBox, 16);

        if (contentMode) buildGraceModeRow(actionsBox);

        TextView lastLook = new TextView(this);
        lastLook.setText(Strings.lastLookQ());
        lastLook.setTextSize(17);
        lastLook.setTextColor(Color.rgb(31, 47, 42));
        lastLook.setPadding(0, dp(4), 0, dp(8));
        actionsBox.addView(lastLook);

        LinearLayout gr1 = scRow(actionsBox);
        gr1.addView(graceButton(Strings.graceBlockNow(), 0));
        gr1.addView(graceButton(Strings.graceLook1(), 60));
        LinearLayout gr2 = scRow(actionsBox);
        gr2.addView(graceButton(Strings.graceLook3(), 180));
        gr2.addView(graceButton(Strings.graceLook5(), 300));
        spacer(actionsBox, 24);

        Button allow = bigButton(contentMode ? "这是误判，本次放行这一类" : "这是误判，本次放行它", Color.rgb(117, 117, 117));
        allow.setOnClickListener(v -> {
            if (contentMode) VerdictEngine.contentAllow(this, category);
            else FocusGuard.chooseAllow(this, fgPkg);
            finish();
        });
        actionsBox.addView(allow);
        spacer(actionsBox, 12);

        // 和 AI 聊聊（v1.24.0）：直达「聊聊」页签并带上当前任务上下文。
        // 只是换个地方说话——问询本身不做任何裁决（回到任务/宽限/放行都还没选），
        // 聊天里 AI 已收到分神主动消息（内容守卫确认分神时落账），可以接着聊。
        Button chat = bigButton(Strings.en() ? "Chat with AI about this" : "和 AI 聊聊", Color.rgb(90, 90, 90));
        chat.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(this, MainActivity.class);
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_CHAT);
            String tid = TimerState.taskId(this);
            if (tid != null && !tid.isEmpty()) i.putExtra(MainActivity.EXTRA_CHAT_TASK, tid);
            try { startActivity(i); } catch (Exception ignored) { }
            finish();
        });
        actionsBox.addView(chat);

        // 诊断行（v1.21.4）：只在 diag_mode 开启、且本次问询是语义判定在
        // 降级状态下触发时，才在页底显示这一行；普通用户永不出现
        if (contentMode && ScreenGuardPrefs.diagMode(this)
                && VerdictEngine.lastInquirySemanticDegraded(this)) {
            TextView diag = new TextView(this);
            diag.setText("大模型无法连接，用本地小模型");
            diag.setTextSize(13);
            diag.setTextColor(Color.GRAY);
            diag.setPadding(0, dp(20), 0, 0);
            root.addView(diag);
        }
    }

    // ---------------- 宽限到点处理方式（v1.21.3，仅内容守卫模式） ----------------
    /**
     * 选宽限时长时一并选到点怎么办：退掉这个软件／只放到后台／盖住 3 分钟
     * （v1.80.0 起三档，默认盖住）。点选即存进 ScreenGuardPrefs，下次问询
     * 默认沿用上次的选择；名单模式不显示本行、共用同一份设置（设置页可
     * 改）；到点由 VerdictEngine.applyEnforcement 按这个选择执行。
     */
    private void buildGraceModeRow(LinearLayout root) {
        graceAction = ScreenGuardPrefs.graceAction(this);
        TextView label = new TextView(this);
        label.setText(Strings.graceActionQ());
        label.setTextSize(17);
        label.setTextColor(Color.rgb(31, 47, 42));
        root.addView(label);
        LinearLayout row = scRow(root);
        final String[] acts = {ScreenGuardPrefs.ACTION_QUIT, ScreenGuardPrefs.ACTION_BACKGROUND,
                ScreenGuardPrefs.ACTION_BLOCK};
        String[] labels = {Strings.graceActQuit(), Strings.graceActBg(), Strings.graceActBlock()};
        for (int i = 0; i < acts.length; i++) {
            final String act = acts[i];
            Button b = scButton(labels[i]);
            b.setOnClickListener(v -> {
                graceAction = act;
                ScreenGuardPrefs.setGraceAction(this, act);
                refreshGraceModeStyles();
            });
            graceModeBtns.add(b);
            row.addView(b);
        }
        refreshGraceModeStyles();
        spacer(root, 14);
    }

    private void refreshGraceModeStyles() {
        String[] acts = {ScreenGuardPrefs.ACTION_QUIT, ScreenGuardPrefs.ACTION_BACKGROUND,
                ScreenGuardPrefs.ACTION_BLOCK};
        for (int i = 0; i < graceModeBtns.size(); i++) {
            styleScButton(graceModeBtns.get(i), acts[i].equals(graceAction));
        }
    }

    // ---------------- 分神记录＋宽限钮（v1.80.0） ----------------
    /**
     * 点任一宽限钮（包括「马上封」）即承认分神：先记一次自检记录
     * （SelfChecks.begin+answer，didRight=false，与旧版在问询页点「没有」
     * 逐字段等价——知行指数的分神计数就靠这条记录衔接），每次问询只记
     * 一次；再按所选时长安排宽限。「立即回到任务」「误判放行」不记分神。
     */
    private void recordDriftOnce() {
        if (driftRecorded) return;
        driftRecorded = true;
        String id = SelfChecks.begin(this, "drift", TimerState.taskId(this));
        SelfChecks.answer(this, id, Boolean.FALSE, null);
    }

    private Button graceButton(String label, final int seconds) {
        Button b = scButton(label);
        b.setOnClickListener(v -> {
            recordDriftOnce();
            if (seconds == 0) {
                // 马上封：不等宽限闹钟/下一拍，当场按处置设置执行
                if (contentMode) VerdictEngine.contentEnforceNow(this, category);
                else FocusGuard.chooseGraceImmediate(this, fgPkg);
            } else if (contentMode) {
                VerdictEngine.contentGrace(this, category, seconds);
            } else {
                FocusGuard.chooseGrace(this, fgPkg, seconds);
            }
            finish();
        });
        return b;
    }

    private LinearLayout scRow(LinearLayout root) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(r);
        return r;
    }

    private Button scButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(16);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.setLayoutParams(lp);
        styleScButton(b, false);
        return b;
    }

    private void styleScButton(Button b, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        if (selected) {
            g.setColor(Color.rgb(217, 75, 58));
            b.setTextColor(Color.WHITE);
        } else {
            g.setColor(Color.rgb(247, 243, 236));
            g.setStroke(dp(1), Color.rgb(211, 204, 188));
            b.setTextColor(Color.rgb(31, 47, 42));
        }
        b.setBackground(g);
    }

    @Override
    protected void onDestroy() {
        FloatingWindow.setSuppressed(false);
        super.onDestroy();
    }

    private Button bigButton(String s, int bg) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));
        return b;
    }

    private void spacer(LinearLayout root, int h) {
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        root.addView(v);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
