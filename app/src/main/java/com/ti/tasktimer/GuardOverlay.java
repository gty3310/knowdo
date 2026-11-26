package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 守卫问询页的 99% 大窗内容（Guild Issue #6 收尾，v1.97.0）：与 GuardActivity
 * 同内容、同文案、同动作、同记账——判定上下文（名单/内容两模式）、问询
 * 文案、立志行、宽限四钮＋呼吸门衔接、处置三档、切换任务、误判申诉、
 * AI 聊聊、难度三档与 GuardLedger 口径、recordDriftOnce 记账点、v1.96.0
 * 暂停期分支，逐项从原页搬来，只换容器（Activity → BigOverlay 大窗，
 * 划不走、Home 退不掉）。原 GuardActivity 保留为锁屏/无悬浮权限时的降级页。
 *
 * 与原页的路径差异（如实列明）：
 * 1. 误判申诉（medium/hard 要写解释）：大窗里没有 Activity 宿主承载
 *    OverturnFlow 的输入框与 AI 复核——照 BlockOverlay 先例，点「这是
 *    误判」收窗转回 GuardActivity 原页完成（extras 原样带过去，功能与
 *    出口不变）；easy 点即放行不打字，在窗内直接放行。
 * 2. 问询计数 GuardLedger.noteInquiry 在 onShown 记（prepare 后只记一次，
 *    被抢占复出不重复记，等价原页 onCreate 只记一次）；经大窗转原页时
 *    带 EXTRA_FROM_OVERLAY，原页不再重复记。
 * 3. 呼吸门仍是 BreathingActivity：点宽限先收窗再拉起它（extras 与原页
 *    逐项相同），完成/作废回落与原页一致；转呼吸门即视为问询已交接，
 *    GuardPending 在此刻清除（漂移已记、GuardBreath 标记在岗，进程被杀
 *    后的再触发由两引擎各自的冷却口径管，与原页流程同）。
 * 4. tick 每秒查问询是否仍成立（计时停了/暂停结束了就自动收回），等价
 *    原页随 Activity 生命周期的自然消失；失效收回时清 GuardPending。
 */
public final class GuardOverlay implements BigOverlay.Content {
    private Context ctx;
    private boolean contentMode;
    private String fgPkg = "";
    private String category = "";
    private String detail = "";
    private String reason = "";
    private boolean pauseGuardMode;

    private String taskId = "";
    private String difficulty = ScreenGuardPrefs.DIFF_MEDIUM;
    private boolean hardQuotaExhausted;
    private boolean driftRecorded;
    private boolean inquiryCounted;

    private String graceAction = ScreenGuardPrefs.ACTION_BLOCK;
    private final List<Button> graceModeBtns = new ArrayList<>();

    // 宽松四档形态（v1.98.0）：2＝极简问询、0＝全额停稳；其余走标准形态
    private int leniency = -1;
    private boolean l0Mode;
    private boolean breathDone;
    private boolean reflectDone;
    private long breathEndMs;
    private int breathTotalSec;
    private TextView breathText;
    private final List<Button> l0GateBtns = new ArrayList<>();
    private final List<Button> chipBtns = new ArrayList<>();

    /** 新一次触发先重置（等价原页新开实例）：判定上下文与本页状态全量换新。 */
    void prepare(boolean content, String pkg, String cat, String det, String rsn) {
        contentMode = content;
        fgPkg = pkg == null ? "" : pkg;
        category = cat == null ? "" : cat;
        detail = det == null ? "" : det;
        reason = rsn == null ? "" : rsn;
        driftRecorded = false;
        inquiryCounted = false;
        graceModeBtns.clear();
        leniency = -1;
        l0Mode = false;
        breathDone = false;
        reflectDone = false;
        breathEndMs = 0;
        breathText = null;
        l0GateBtns.clear();
        chipBtns.clear();
    }

    @Override
    public View createView(final Context c) {
        ctx = c;
        // 自防御（v1.98.1）：壳的复出路径不经 prepare 直接 createView，按钮
        // 表会在每次重建时累积——开头先清，防 refreshGraceModeStyles 索引
        // 定长 acts 表越界（v1.97.0 潜伏必崩点）
        graceModeBtns.clear();
        Strings.init(c);
        // 难度与计数快照（与原页 onCreate 同点位）：暂停期判定与原页同表达式
        pauseGuardMode = contentMode && TimerState.isPaused(c) && !TimerState.isRunning(c);
        String tid0 = TimerState.taskId(c);
        taskId = tid0 == null ? "" : tid0;
        difficulty = ScreenGuardPrefs.guardDifficulty(c);
        hardQuotaExhausted = ScreenGuardPrefs.DIFF_HARD.equals(difficulty)
                && GuardLedger.hardGraceUsed(c) >= ScreenGuardPrefs.hardGraceQuota(c);
        graceAction = ScreenGuardPrefs.graceAction(c);
        // 宽松档（v1.98.0）：只对内容模式生效；名单模式恒标准形态
        leniency = contentMode ? VerdictEngine.lastInquiryLeniency(c) : -1;
        l0Mode = contentMode && leniency == LeniencyGate.L0;

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 243, 236));
        int pad = dp(28);
        root.setPadding(pad, dp(56), pad, pad);
        ScrollView sv = new ScrollView(c);
        sv.setBackgroundColor(Color.rgb(247, 243, 236));
        sv.setFillViewport(true);
        sv.addView(root);
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(247, 243, 236));
        page.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(c);
        title.setText("你是在做正确的任务吗？");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(31, 47, 42));
        root.addView(title);

        TextView sub = new TextView(c);
        if (contentMode) {
            if (pauseGuardMode) {
                String rsn = TimerState.pauseReason(c);
                sub.setText("暂停去做：「" + (rsn == null ? "" : rsn) + "」\n屏幕内容守卫检测到「"
                        + ContentAnalyzer.label(category) + "」："
                        + detail
                        + "\n截图只在本机分析、已经销毁，不会保存或上传");
            } else {
                sub.setText("当前任务：「" + TimerState.taskTitle(c) + "」\n屏幕内容守卫检测到「"
                        + ContentAnalyzer.label(category) + "」："
                        + detail
                        + "\n截图只在本机分析、已经销毁，不会保存或上传。");
            }
        } else {
            if (!reason.isEmpty()) {
                sub.setText("当前任务：「" + TimerState.taskTitle(c) + "」\n" + reason + "。");
            } else {
                String appLabel = AppLister.labelOf(c, fgPkg);
                sub.setText("当前任务：「" + TimerState.taskTitle(c) + "」\n你打开了「" + appLabel + "」，它不在这个任务的专注规则里。");
            }
        }
        sub.setTextSize(18);
        sub.setTextColor(Color.rgb(31, 47, 42));
        sub.setPadding(0, dp(12), 0, dp(30));
        root.addView(sub);

        TextView asp = Aspiration.makeView(c, false);
        if (asp != null) {
            asp.setPadding(0, 0, 0, dp(22));
            root.addView(asp, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        // 极简问询（v1.98.0，宽松 L2 假分神·大概）：一个大按钮一键放行——
        // 不用写解释、不过呼吸门、不记分神；放行即把当前流水段标成临时
        // 任务段。下面另留一个「回到任务」出口（同样不记分神）。
        if (contentMode && leniency == LeniencyGate.L2) {
            LinearLayout simple = new LinearLayout(c);
            simple.setOrientation(LinearLayout.VERTICAL);
            root.addView(simple);
            Button busy = bigButton("在忙正事", Color.rgb(46, 125, 50));
            busy.setOnClickListener(v -> {
                resolve();
                ActivityStream.markCurrentTempTask(ctx);
                finish();
            });
            simple.addView(busy);
            spacer(simple, 16);
            Button back = bigButton(pauseGuardMode ? "回到要做的事" : "立即回到任务", Color.rgb(31, 47, 42));
            back.setOnClickListener(v -> {
                resolve();
                if (pauseGuardMode) { finish(); return; }
                VerdictEngine.contentImmediate(ctx);
                finish();
            });
            simple.addView(back);
            return page;
        }

        // 全额停稳（v1.98.0，宽松 L0 真分神·明确）：先呼吸、再反思一问，
        // 两样都做完宽限钮才解禁（收紧）；回到任务/马上封/切换/申诉不受门控
        if (l0Mode) buildL0Block(root);

        LinearLayout actionsBox = new LinearLayout(c);
        actionsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(actionsBox);

        Button now = bigButton(pauseGuardMode ? "回到要做的事" : "立即回到任务", Color.rgb(46, 125, 50));
        now.setOnClickListener(v -> {
            resolve();
            if (pauseGuardMode) { finish(); return; }
            if (contentMode) VerdictEngine.contentImmediate(ctx);
            else FocusGuard.chooseImmediate(ctx);
            finish();
        });
        actionsBox.addView(now);
        spacer(actionsBox, 16);

        Button switchTask = bigButton(Strings.guardSwitchTask(), Color.rgb(31, 47, 42));
        switchTask.setOnClickListener(v -> {
            resolve();
            try { MainActivity.openXing(ctx); }
            catch (Exception ignored) { }
            finish();
        });
        actionsBox.addView(switchTask);
        spacer(actionsBox, 16);

        if (!hardQuotaExhausted) {
            TextView lastLook = new TextView(c);
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

            if (contentMode) buildGraceModeRow(actionsBox);

            spacer(actionsBox, 24);
        }

        // 「这是误判」出口：三档每次都显示。easy 点即放行（窗内完成）；
        // medium/hard 要写解释走 AI 复核——收窗转回原页完成（见类注释 1）
        Button allow = bigButton(contentMode ? "这是误判，本次放行这一类" : "这是误判，本次放行它", Color.rgb(117, 117, 117));
        allow.setOnClickListener(v -> {
            if (ScreenGuardPrefs.DIFF_EASY.equals(difficulty)) {
                resolve();
                if (contentMode) VerdictEngine.contentAllow(ctx, category);
                else FocusGuard.chooseAllow(ctx, fgPkg);
                AppBlock.clear(ctx);
                finish();
                return;
            }
            // 不 resolve：问询只是换个容器继续，GuardPending 保持未决
            BigOverlay.dismissQuietly(ctx, BigOverlay.KEY_GUARD);
            try {
                Intent i = new Intent(ctx, GuardActivity.class);
                i.putExtra(GuardActivity.EXTRA_PKG, fgPkg);
                if (contentMode) {
                    i.putExtra(GuardActivity.EXTRA_SOURCE, "content");
                    i.putExtra(GuardActivity.EXTRA_CATEGORY, category);
                    i.putExtra(GuardActivity.EXTRA_DETAIL, detail);
                }
                if (!reason.isEmpty()) i.putExtra(GuardActivity.EXTRA_REASON, reason);
                i.putExtra(GuardActivity.EXTRA_FROM_OVERLAY, true);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) { }
        });
        actionsBox.addView(allow);
        spacer(actionsBox, 12);

        Button chat = bigButton(Strings.en() ? "Chat with AI about this" : "和 AI 聊聊", Color.rgb(90, 90, 90));
        chat.setOnClickListener(v -> {
            resolve();
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_CHAT);
            String tid = TimerState.taskId(ctx);
            if (tid != null && !tid.isEmpty()) i.putExtra(MainActivity.EXTRA_CHAT_TASK, tid);
            try { ctx.startActivity(i); } catch (Exception ignored) { }
            finish();
        });
        actionsBox.addView(chat);

        if (contentMode && ScreenGuardPrefs.diagMode(c)
                && VerdictEngine.lastInquirySemanticDegraded(c)) {
            TextView diag = new TextView(c);
            diag.setText("大模型无法连接，用本地小模型");
            diag.setTextSize(13);
            diag.setTextColor(Color.GRAY);
            diag.setPadding(0, dp(20), 0, 0);
            root.addView(diag);
        }
        return page;
    }

    @Override
    public void onShown(Context c) {
        AlertHelper.cancelGuard(c); // 与原页 onCreate 同一步
        if (!inquiryCounted) {
            inquiryCounted = true;
            GuardLedger.noteInquiry(c, taskId);
        }
        Vibrator vib = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        if (vib != null) vib.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    @Override
    public boolean tick(Context c) {
        tickBreath();
        // 问询成立的前提没了（计时停了／暂停结束了）就自动收回，等价原页
        // 随 Activity 生命周期的自然消失；失效收回时未决问询一并作废
        boolean valid = pauseGuardMode
                ? TimerState.isPaused(c) && !TimerState.isRunning(c)
                : TimerState.isRunning(c);
        if (!valid) GuardPending.clear(c);
        return valid;
    }

    @Override
    public void onDismissed(Context c) {
        FloatingWindow.endOverlayInputFocus(c);
    }

    // ---------------- 与原页同口径的动作 ----------------

    /** 问询已解决：未决落盘清除（各终局动作共用）。 */
    private void resolve() {
        if (ctx != null) GuardPending.clear(ctx);
    }

    private void finish() { BigOverlay.dismiss(ctx, BigOverlay.KEY_GUARD); }

    /** 与 GuardActivity.recordDriftOnce 同口径复刻：暂停期跳过、同页只记一次。 */
    private void recordDriftOnce() {
        if (pauseGuardMode) return;
        if (driftRecorded) return;
        driftRecorded = true;
        String id = SelfChecks.begin(ctx, "drift", TimerState.taskId(ctx));
        SelfChecks.answer(ctx, id, Boolean.FALSE, null);
    }

    private Button graceButton(String label, final int seconds) {
        Button b = scButton(label);
        b.setOnClickListener(v -> {
            recordDriftOnce();
            if (seconds == 0) {
                resolve();
                if (contentMode) VerdictEngine.contentEnforceNow(ctx, category);
                else FocusGuard.chooseGraceImmediate(ctx, fgPkg);
                finish();
                return;
            }
            if (ScreenGuardPrefs.DIFF_EASY.equals(difficulty)) {
                resolve();
                if (contentMode) VerdictEngine.contentGrace(ctx, category, seconds);
                else FocusGuard.chooseGrace(ctx, fgPkg, seconds);
                GuardLedger.noteGrace(ctx, taskId);
                finish();
                return;
            }
            // medium / hard：先过深呼吸门——收窗后拉起 BreathingActivity，
            // extras 与原页逐项相同；呼吸完成才执行宽限并记账（原页口径）
            int n = GuardLedger.graceCount(ctx, taskId);
            // 注：L0 形态下宽限钮另有呼吸＋反思前置门（见 buildL0Block），
            // 走到这里说明两道前置都已完成，本处呼吸门是难度档原有口径、不变
            int breath = Math.min(120, 15 << Math.min(n, 3));
            GuardBreath.start(ctx, breath * 1000L);
            resolve(); // 问询已交接给呼吸门（见类注释 3）
            finish();
            Intent i = new Intent(ctx, BreathingActivity.class);
            i.putExtra(BreathingActivity.EXTRA_MODE, contentMode ? "content" : "list");
            i.putExtra(BreathingActivity.EXTRA_PKG, fgPkg);
            i.putExtra(BreathingActivity.EXTRA_CATEGORY, category);
            i.putExtra(BreathingActivity.EXTRA_GRACE_SECONDS, seconds);
            i.putExtra(BreathingActivity.EXTRA_BREATH_SECONDS, breath);
            i.putExtra(BreathingActivity.EXTRA_TASK_ID, taskId);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { ctx.startActivity(i); } catch (Exception ignored) { }
        });
        // L0 全额停稳：宽限时长钮先上锁，呼吸＋反思都做完才解禁（收紧口径）；
        // 「马上封」是立即处置不是宽限，不上锁
        if (l0Mode && seconds > 0) {
            l0GateBtns.add(b);
            b.setEnabled(false);
            b.setAlpha(0.45f);
        }
        return b;
    }

    // ---------------- L0 全额停稳：呼吸＋反思（v1.98.0） ----------------

    /**
     * 呼吸块＋反思一问：呼吸用大窗自身的每秒 tick 计时（15/30/45 秒按难度
     * 档，地板口径——不因任何返回缩水）；反思一问「刚才是什么把你带走的」
     * 点标签即可、不强制打字，选择只记一条备注留痕（不改既有事件字段）。
     * 两样都完成才解禁宽限钮；目的不是罚时，是打断自动驾驶状态。
     */
    private void buildL0Block(LinearLayout root) {
        breathTotalSec = ScreenGuardPrefs.DIFF_HARD.equals(difficulty) ? 45
                : ScreenGuardPrefs.DIFF_EASY.equals(difficulty) ? 15 : 30;
        breathText = new TextView(ctx);
        breathText.setText("先停下来，跟着呼吸一会儿");
        breathText.setTextSize(17);
        breathText.setTextColor(Color.rgb(31, 47, 42));
        root.addView(breathText);
        Button breathBtn = bigButton("先呼吸（" + breathTotalSec + " 秒）", Color.rgb(90, 90, 90));
        breathBtn.setOnClickListener(v -> {
            if (breathEndMs > 0) return;
            breathEndMs = System.currentTimeMillis() + breathTotalSec * 1000L;
            v.setEnabled(false);
            ((Button) v).setText("呼吸中");
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        blp.setMargins(0, dp(10), 0, dp(20));
        root.addView(breathBtn, blp);

        TextView q = new TextView(ctx);
        q.setText("刚才是什么把你带走的？");
        q.setTextSize(17);
        q.setTextColor(Color.rgb(31, 47, 42));
        root.addView(q);
        String[] tags = {"手痒点开", "消息牵走", "停不下来", "累了想歇", "其实是正事"};
        for (int i = 0; i < tags.length; i += 3) {
            LinearLayout row = scRow(root);
            for (int j = i; j < Math.min(i + 3, tags.length); j++) {
                final String tag = tags[j];
                Button chip = scButton(tag);
                chip.setOnClickListener(v -> {
                    if (reflectDone) return;
                    reflectDone = true;
                    recordReflection(tag);
                    for (Button b : chipBtns) styleScButton(b, b == v);
                    refreshL0Gates();
                });
                chipBtns.add(chip);
                row.addView(chip);
            }
        }
        spacer(root, 20);
    }

    /** 反思选择留痕：一条备注事件（不计分神），只增记录、不动既有事件取值。 */
    private void recordReflection(String tag) {
        try {
            TaskItem t = new TaskItem();
            t.id = taskId;
            t.title = TimerState.taskTitle(ctx);
            GuardEvents.addNoteTraced(ctx, t, category, 0f,
                    "真分神反思：" + tag, null, null, null, LeniencyGate.L0);
        } catch (Exception ignored) { }
    }

    private void refreshL0Gates() {
        boolean open = breathDone && reflectDone;
        for (Button b : l0GateBtns) {
            b.setEnabled(open);
            b.setAlpha(open ? 1f : 0.45f);
        }
    }

    /** L0 呼吸计时（大窗每秒 tick 驱动）：4 秒吸/4 秒呼交替，到点解禁一半。 */
    private void tickBreath() {
        if (!l0Mode || breathDone || breathEndMs <= 0 || breathText == null) return;
        long remainMs = breathEndMs - System.currentTimeMillis();
        if (remainMs <= 0) {
            breathDone = true;
            breathText.setText("呼吸完成");
            refreshL0Gates();
            return;
        }
        long elapsed = breathTotalSec - (remainMs + 999) / 1000;
        boolean inhale = (elapsed / 4) % 2 == 0;
        breathText.setText("呼吸：" + (inhale ? "吸气" : "呼气")
                + " · 还剩 " + (remainMs + 999) / 1000 + " 秒");
    }

    // ---------------- 宽限到点处理方式（仅内容模式，与原页同） ----------------

    private void buildGraceModeRow(LinearLayout root) {
        graceAction = ScreenGuardPrefs.graceAction(ctx);
        TextView label = new TextView(ctx);
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
                ScreenGuardPrefs.setGraceAction(ctx, act);
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
        int n = Math.min(graceModeBtns.size(), acts.length);
        for (int i = 0; i < n; i++) {
            styleScButton(graceModeBtns.get(i), acts[i].equals(graceAction));
        }
    }

    // ---------------- 小件（与 GuardActivity 同视觉） ----------------

    private LinearLayout scRow(LinearLayout root) {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(r);
        return r;
    }

    private Button scButton(String s) {
        Button b = new Button(ctx);
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

    private Button bigButton(String s, int bg) {
        Button b = new Button(ctx);
        b.setText(s);
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));
        return b;
    }

    private void spacer(LinearLayout root, int h) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        root.addView(v);
    }

    private int dp(int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
