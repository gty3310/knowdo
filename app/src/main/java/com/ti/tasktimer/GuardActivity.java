package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 专注守卫全屏问询页：计时中打开了无关软件时弹出。
 * 按钮：立即回到任务（跳回主软件/当前任务页）/ 给我 30 秒 / 给我 60 秒（到期自动跳回）/ 这是误判，本次放行。
 */
public class GuardActivity extends Activity {
    public static final String EXTRA_PKG = "fg_pkg";
    /** 屏幕内容守卫模式（EXTRA_SOURCE="content"）：按内容类别问询，证据在 EXTRA_DETAIL。 */
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_DETAIL = "detail";

    private String fgPkg;
    private boolean contentMode;
    private String category = "";

    // 磨练自检（v1.12.0）：两问的点选状态与本页的自检记录 id（首次点选时才建条）
    private String scEventId;
    private int scDidSel = -1;
    private int scStateSel = -1;
    private final List<Button> scDidBtns = new ArrayList<>();
    private final List<Button> scStateBtns = new ArrayList<>();

    // 宽限到点处理方式（v1.21.3，仅 content 模式）：true=退掉这个软件（默认），false=只放到后台
    private boolean graceQuit = true;
    private final List<Button> graceModeBtns = new ArrayList<>();

    // 分析错误（v1.24.3）：应答按钮区与纪律输入区（二选一显示，原页切换）
    private LinearLayout actionsBox;
    private LinearLayout ruleBox;
    private EditText ruleInput;
    private TextView ruleCounter;

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
        setContentView(root);

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
            String appLabel = AppLister.labelOf(this, fgPkg);
            sub.setText("当前任务：「" + TimerState.taskTitle(this) + "」\n你打开了「" + appLabel + "」，它不在这个任务的专注规则里。");
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

        buildSelfCheck(root);

        // 应答按钮区：点「分析错误」后整区收起、原页展开纪律输入区（见 ruleBox）
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

        Button g30 = bigButton("给我 30 秒收尾", Color.rgb(21, 101, 192));
        g30.setOnClickListener(v -> {
            if (contentMode) VerdictEngine.contentGrace(this, category, 30);
            else FocusGuard.chooseGrace(this, fgPkg, 30);
            finish();
        });
        actionsBox.addView(g30);
        spacer(actionsBox, 12);

        Button g60 = bigButton("给我 60 秒收尾", Color.rgb(21, 101, 192));
        g60.setOnClickListener(v -> {
            if (contentMode) VerdictEngine.contentGrace(this, category, 60);
            else FocusGuard.chooseGrace(this, fgPkg, 60);
            finish();
        });
        actionsBox.addView(g60);
        spacer(actionsBox, 24);

        Button allow = bigButton(contentMode ? "这是误判，本次放行这一类" : "这是误判，本次放行它", Color.rgb(117, 117, 117));
        allow.setOnClickListener(v -> {
            if (contentMode) VerdictEngine.contentAllow(this, category);
            else FocusGuard.chooseAllow(this, fgPkg);
            finish();
        });
        actionsBox.addView(allow);
        spacer(actionsBox, 12);

        // 分析错误（v1.24.3）：与「误判放行」是两回事——放行只是本次计时内不再判
        // 这一类，这次判定照样计分神；分析错误是把本次判定按误报撤销（不计分神、
        // 事件打 overturned 标记），并在原页展开纪律输入区，可留一条 ≤20 字的
        // 纪律，往后云端终判带上它判。样式克制：白底描边的次级按钮。
        Button overturn = plainButton(Strings.en() ? "Analysis error" : "分析错误");
        overturn.setOnClickListener(v -> {
            if (contentMode) VerdictEngine.contentOverturn(this);
            else FocusGuard.chooseOverturn(this, fgPkg);
            showRuleBox();
        });
        actionsBox.addView(overturn);
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

        buildRuleBox(root);

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

    // ---------------- 分析错误：纪律输入区（v1.24.3） ----------------
    /**
     * 点「分析错误」后在原页展开：误报撤销在点按钮那一刻已经生效（见
     * VerdictEngine.contentOverturn / FocusGuard.chooseOverturn），这里只管
     * 可选的纪律输入——写一条 ≤20 字的规矩保存，或跳过只标误报。
     * 保存走 GuardRules.add（本地落盘＋踢同步＋随云端终判带参）。
     */
    private void buildRuleBox(LinearLayout root) {
        ruleBox = new LinearLayout(this);
        ruleBox.setOrientation(LinearLayout.VERTICAL);
        ruleBox.setVisibility(View.GONE);
        root.addView(ruleBox);

        TextView label = new TextView(this);
        label.setText(Strings.en()
                ? "Marked as a false alarm — this one doesn't count. Leave a rule if you like: "
                        + "online verdicts will judge with it from now on (optional)"
                : "已按误报处理，这次不算分神。想留一条纪律也行：往后联网判定会带着它一起判（可跳过）");
        label.setTextSize(17);
        label.setTextColor(Color.rgb(31, 47, 42));
        ruleBox.addView(label);

        ruleInput = new EditText(this);
        ruleInput.setHint(Strings.en() ? "e.g. Looking things up in the browser is work"
                : "例：查资料用浏览器算正事");
        ruleInput.setTextSize(18);
        ruleInput.setSingleLine(true);
        ruleInput.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(GuardRules.MAX_LEN) });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(0, dp(12), 0, 0);
        ruleInput.setLayoutParams(ilp);
        ruleBox.addView(ruleInput);

        ruleCounter = new TextView(this);
        ruleCounter.setText("0/" + GuardRules.MAX_LEN);
        ruleCounter.setTextSize(13);
        ruleCounter.setTextColor(Color.GRAY);
        ruleCounter.setGravity(android.view.Gravity.END);
        ruleBox.addView(ruleCounter);
        ruleInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) {
                ruleCounter.setText(s.length() + "/" + GuardRules.MAX_LEN);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, dp(10), 0, 0);
        row.setLayoutParams(rlp);
        ruleBox.addView(row);

        Button save = bigButton(Strings.en() ? "Save rule" : "保存纪律", Color.rgb(46, 125, 50));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(60), 1f);
        blp.setMargins(0, 0, dp(6), 0);
        save.setLayoutParams(blp);
        save.setOnClickListener(v -> {
            String t = ruleInput.getText() == null ? "" : ruleInput.getText().toString().trim();
            if (!t.isEmpty()) {
                GuardRules.add(this, t);
                Toast.makeText(this, Strings.en() ? "Rule saved — this one doesn't count"
                        : "纪律已保存，这次不算分神", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, Strings.en() ? "Marked as a false alarm"
                        : "已标记误报，这次不算分神", Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        row.addView(save);

        Button skip = plainButton(Strings.en() ? "Skip" : "跳过");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(60), 1f);
        slp.setMargins(dp(6), 0, 0, 0);
        skip.setLayoutParams(slp);
        skip.setOnClickListener(v -> {
            Toast.makeText(this, Strings.en() ? "Marked as a false alarm"
                    : "已标记误报，这次不算分神", Toast.LENGTH_SHORT).show();
            finish();
        });
        row.addView(skip);
    }

    private void showRuleBox() {
        if (actionsBox != null) actionsBox.setVisibility(View.GONE);
        if (ruleBox != null) {
            ruleBox.setVisibility(View.VISIBLE);
            if (ruleInput != null) {
                ruleInput.requestFocus();
                ruleInput.post(() -> {
                    try {
                        InputMethodManager imm = (InputMethodManager)
                                getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showSoftInput(ruleInput, InputMethodManager.SHOW_IMPLICIT);
                    } catch (Exception ignored) { }
                });
            }
        }
    }

    /** 克制的次级按钮（白底描边、深灰字），与 bigButton 的主按钮区分。 */
    private Button plainButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(31, 47, 42));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(247, 243, 236));
        g.setStroke(dp(1), Color.rgb(211, 204, 188));
        g.setCornerRadius(dp(10));
        b.setBackground(g);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));
        return b;
    }

    // ---------------- 宽限到点处理方式（v1.21.3，仅内容守卫模式） ----------------
    /**
     * 选宽限时长时一并选到点怎么办：「退掉这个软件」（默认）或「只放到后台」。
     * 点选即存进 ScreenGuardPrefs，下次问询默认沿用上次的选择；到点由
     * VerdictEngine.applyEnforcement 按这个选择执行。
     */
    private void buildGraceModeRow(LinearLayout root) {
        graceQuit = ScreenGuardPrefs.graceActionQuit(this);
        TextView label = new TextView(this);
        label.setText("宽限到点后，这个软件：");
        label.setTextSize(17);
        label.setTextColor(Color.rgb(31, 47, 42));
        root.addView(label);
        LinearLayout row = scRow(root);
        Button quit = scButton("退掉这个软件");
        quit.setOnClickListener(v -> {
            graceQuit = true;
            ScreenGuardPrefs.setGraceActionQuit(this, true);
            refreshGraceModeStyles();
        });
        Button bg = scButton("只放到后台");
        bg.setOnClickListener(v -> {
            graceQuit = false;
            ScreenGuardPrefs.setGraceActionQuit(this, false);
            refreshGraceModeStyles();
        });
        graceModeBtns.add(quit);
        graceModeBtns.add(bg);
        row.addView(quit);
        row.addView(bg);
        refreshGraceModeStyles();
        spacer(root, 14);
    }

    private void refreshGraceModeStyles() {
        for (int i = 0; i < graceModeBtns.size(); i++) {
            styleScButton(graceModeBtns.get(i), graceQuit == (i == 0));
        }
    }

    // ---------------- 磨练自检（v1.12.0） ----------------
    /**
     * 问询页顶部自检区：问一「刚才在做该做的事吗？」[做到了][没有]；
     * 问二「现在的状态」六个 chips。点了即时记录（同一页共用一条记录），
     * 不点不拦任何原有动作——下面的回到任务/宽限/放行照常可用。
     */
    private void buildSelfCheck(LinearLayout root) {
        TextView q1 = new TextView(this);
        q1.setText(Strings.scDidRightQ());
        q1.setTextSize(17);
        q1.setTextColor(Color.rgb(31, 47, 42));
        root.addView(q1);
        LinearLayout r1 = scRow(root);
        Button did = scButton(Strings.scDid());
        did.setOnClickListener(v -> {
            scDidSel = 0;
            ensureScEvent();
            SelfChecks.answer(this, scEventId, Boolean.TRUE, null);
            refreshScStyles();
        });
        Button not = scButton(Strings.scNot());
        not.setOnClickListener(v -> {
            scDidSel = 1;
            ensureScEvent();
            SelfChecks.answer(this, scEventId, Boolean.FALSE, null);
            refreshScStyles();
        });
        scDidBtns.add(did);
        scDidBtns.add(not);
        r1.addView(did);
        r1.addView(not);

        TextView q2 = new TextView(this);
        q2.setText(Strings.scStateQ());
        q2.setTextSize(17);
        q2.setTextColor(Color.rgb(31, 47, 42));
        q2.setPadding(0, dp(14), 0, 0);
        root.addView(q2);
        String[] states = Strings.selfCheckStates();
        for (int r = 0; r < 2; r++) {
            LinearLayout row = scRow(root);
            for (int k = 0; k < 3; k++) {
                final int i = r * 3 + k;
                Button chip = scButton(states[i]);
                chip.setOnClickListener(v -> {
                    scStateSel = i;
                    ensureScEvent();
                    // 存标准存值（与界面语言无关），日后省察对账才对得齐
                    SelfChecks.answer(this, scEventId, null, SelfChecks.STATES[i]);
                    refreshScStyles();
                });
                scStateBtns.add(chip);
                row.addView(chip);
            }
        }
        spacer(root, 26);
    }

    private void ensureScEvent() {
        if (scEventId == null) {
            scEventId = SelfChecks.begin(this, "drift", TimerState.taskId(this));
        }
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

    private void refreshScStyles() {
        for (int i = 0; i < scDidBtns.size(); i++) styleScButton(scDidBtns.get(i), scDidSel == i);
        for (int i = 0; i < scStateBtns.size(); i++) styleScButton(scStateBtns.get(i), scStateSel == i);
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
