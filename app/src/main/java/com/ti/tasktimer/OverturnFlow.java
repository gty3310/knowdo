package com.ti.tasktimer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 误判解释与 AI 复核（issue #4）：easy 不走这里、点即放行；medium/hard
 * 必须写一句解释由 AI 审（过短、乱码、无关则拒绝，没有跳过钮）；
 * AI 不可用时倒计时 5 秒后再允许跳过。可选「加到 system prompt 里」
 * 只在 AI 同意后写入全局 GuardRules。
 */
public final class OverturnFlow {
    public interface Host {
        Activity activity();
        boolean contentMode();
        String category();
        String fgPkg();
        void finishAfterAllow();
    }

    /** 给判定器看的 system prompt：只回 agree/refuse，敷衍解释一律拒绝。 */
    static final String JUDGE_SYSTEM =
            "你是知行 App 的守卫判定器。用户在做任务时打开了被守卫拦下的软件，声称这是误判，并写了一句解释。"
            + "只判断这句解释是否构成合理的误判理由（例如软件与任务相关、系统自动弹出、误触点开、正当查资料）。"
            + "若解释过短、乱码、重复无意义字符、与当前任务或软件无关、或明显在敷衍，一律 refuse。"
            + "只输出一个词：agree 或 refuse，不要输出任何其他内容。";

    private OverturnFlow() {}

    public static void allowNow(Host h) {
        Activity a = h.activity();
        if (h.contentMode()) VerdictEngine.contentAllow(a, h.category());
        else FocusGuard.chooseAllow(a, h.fgPkg());
        AppBlock.clear(a);
        h.finishAfterAllow();
    }

    /** 解释是否过短或明显不是一句话。 */
    public static boolean tooThin(String raw) {
        if (raw == null) return true;
        String t = raw.trim();
        if (t.length() < 4) return true;
        int letters = 0;
        boolean allSame = true;
        char first = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch >= 0x4E00) letters++;
            if (i == 0) first = ch;
            else if (ch != first) allSame = false;
        }
        return letters < 4 || allSame;
    }

    public static final class Box {
        public final LinearLayout root;
        public final EditText input;
        public final CheckBox addToPrompt;
        public final Button submit;
        public final TextView state;
        public Button skip;
        boolean submitted;
        boolean settled;
        final Handler handler = new Handler(Looper.getMainLooper());

        Box(LinearLayout root, EditText input, CheckBox addToPrompt, Button submit, TextView state) {
            this.root = root;
            this.input = input;
            this.addToPrompt = addToPrompt;
            this.submit = submit;
            this.state = state;
        }

        public void show() { root.setVisibility(View.VISIBLE); }
    }

    public static Box build(Host h, LinearLayout parent) {
        final Activity act = h.activity();
        final boolean en = Strings.en();
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setVisibility(View.GONE);

        EditText input = new EditText(act);
        input.setHint(Strings.hardExplainHint());
        input.setTextSize(16);
        input.setTextColor(Color.rgb(31, 47, 42));
        input.setHintTextColor(Color.rgb(150, 150, 150));
        input.setMinLines(2);
        input.setGravity(Gravity.TOP);
        box.addView(input);

        CheckBox add = new CheckBox(act);
        add.setText(Strings.addToSystemPrompt());
        add.setTextSize(15);
        add.setTextColor(Color.rgb(31, 47, 42));
        add.setPadding(0, dp(act, 8), 0, dp(act, 4));
        box.addView(add);

        Button submit = new Button(act);
        submit.setText(Strings.hardJudgeSubmit());
        submit.setTextSize(18);
        submit.setTextColor(Color.WHITE);
        submit.setBackgroundColor(Color.rgb(90, 90, 90));
        submit.setAllCaps(false);
        submit.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(act, 56)));
        box.addView(submit);

        TextView state = new TextView(act);
        state.setTextSize(14);
        state.setTextColor(Color.rgb(117, 117, 117));
        state.setPadding(0, dp(act, 8), 0, 0);
        box.addView(state);

        final Box ui = new Box(box, input, add, submit, state);
        submit.setOnClickListener(v -> submit(h, ui));
        parent.addView(box);
        return ui;
    }

    public static void onMisjudgeClicked(Host h, String difficulty, Box box) {
        if (box == null || ScreenGuardPrefs.DIFF_EASY.equals(difficulty)) {
            allowNow(h);
            return;
        }
        box.show();
    }

    static void submit(Host h, Box ui) {
        if (ui.submitted) return;
        final Activity act = h.activity();
        final String explanation = ui.input.getText() == null
                ? "" : ui.input.getText().toString().trim();
        if (tooThin(explanation)) {
            ui.state.setText(Strings.explainTooThin());
            return;
        }
        ui.submitted = true;
        ui.submit.setEnabled(false);
        ui.input.setEnabled(false);
        ui.addToPrompt.setEnabled(false);
        ui.state.setText(Strings.hardJudging());

        String appName;
        if (h.contentMode()) {
            String lbl = h.fgPkg() == null || h.fgPkg().isEmpty()
                    ? "" : AppLister.labelOf(act, h.fgPkg());
            appName = lbl == null || lbl.isEmpty() ? ContentAnalyzer.label(h.category()) : lbl;
        } else {
            appName = AppLister.labelOf(act, h.fgPkg());
        }
        String catLabel = h.contentMode() ? ContentAnalyzer.label(h.category())
                : (Strings.en() ? "app list (blacklist)" : "任务软件名单（黑名单）");
        String taskTitle = TimerState.taskTitle(act);
        String desc = "";
        String tid = TimerState.taskId(act);
        if (tid != null && !tid.isEmpty()) {
            TaskItem t = new TaskStore(act).byId(tid);
            if (t != null && t.description != null) desc = t.description.trim();
        }
        JSONArray msgs = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", JUDGE_SYSTEM);
            msgs.put(sys);
            JSONObject usr = new JSONObject();
            usr.put("role", "user");
            StringBuilder body = new StringBuilder();
            body.append("任务：「").append(taskTitle).append("」\n");
            if (!desc.isEmpty()) body.append("任务说明：").append(desc).append("\n");
            body.append("软件：「").append(appName).append("」\n")
                    .append("判定类别：").append(catLabel).append("\n")
                    .append("用户解释：").append(explanation);
            usr.put("content", body.toString());
            msgs.put(usr);
        } catch (Exception e) {
            onAiUnavailable(h, ui, explanation);
            return;
        }
        ui.handler.postDelayed(() -> {
            if (!ui.settled) onAiUnavailable(h, ui, explanation);
        }, 25_000);
        ChatClient.send(act, msgs, new ChatClient.Callback() {
            @Override public void onModel(String modelId) { }
            @Override public void onDelta(String fullText) { }
            @Override public void onDone(String fullText) {
                if (ui.settled) return;
                String t = fullText == null ? "" : fullText.trim().toLowerCase(java.util.Locale.US);
                if (t.startsWith("agree")) onAgreed(h, ui, explanation);
                else onRejected(ui);
            }
            @Override public void onError(String friendlyMsg) {
                if (!ui.settled) onAiUnavailable(h, ui, explanation);
            }
        });
    }

    static void onAgreed(Host h, Box ui, String explanation) {
        ui.settled = true;
        if (ui.addToPrompt.isChecked()) {
            GuardRules.add(h.activity(), explanation);
        }
        allowNow(h);
    }

    /** AI 看过并拒绝：让用户再写，不能跳过。 */
    static void onRejected(Box ui) {
        ui.submitted = false;
        ui.submit.setEnabled(true);
        ui.input.setEnabled(true);
        ui.addToPrompt.setEnabled(true);
        ui.state.setText(Strings.explainRejected());
    }

    /** 离线或通道出错：5 秒倒计时后出现跳过，并注明 AI 不可用。不写入 system prompt。 */
    static void onAiUnavailable(Host h, Box ui, String explanation) {
        if (ui.settled) return;
        ui.settled = true;
        ui.state.setText(Strings.aiUnavailableWait(5));
        final int[] left = {5};
        final Runnable tick = new Runnable() {
            @Override public void run() {
                left[0]--;
                if (left[0] > 0) {
                    ui.state.setText(Strings.aiUnavailableWait(left[0]));
                    ui.handler.postDelayed(this, 1000);
                    return;
                }
                ui.state.setText(Strings.aiUnavailableSkipHint());
                if (ui.skip == null) {
                    Activity act = h.activity();
                    ui.skip = new Button(act);
                    ui.skip.setText(Strings.skipAiUnavailable());
                    ui.skip.setTextSize(16);
                    ui.skip.setTextColor(Color.WHITE);
                    ui.skip.setBackgroundColor(Color.rgb(117, 117, 117));
                    ui.skip.setAllCaps(false);
                    ui.skip.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(act, 52)));
                    ui.skip.setOnClickListener(v -> allowNow(h));
                    ui.root.addView(ui.skip);
                }
                ui.skip.setVisibility(View.VISIBLE);
            }
        };
        ui.handler.postDelayed(tick, 1000);
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
