package com.ti.tasktimer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 聊天界面组件（v1.24.0）：「聊聊」页签与结束页内嵌聊天区共用这一个实现，
 * ChatActivity 只是它的薄宿主（从任务编辑页进来时用）。三处共用同一条聊天
 * 主线（ChatStore.MAIN）、同一套发送/流式/提案卡代码，不另写第二份。
 *
 * 沿用 v1.22.0 起的纪律：AI 永远不能静默改任何东西——它给的任务增删改、
 * 排程、改设置都渲染成提案卡，用户点「应用」才走既有写路径落盘。
 * v1.24.0 扩展：knowdo-proposal 加 action（create/update/delete，删除卡红色
 * 「删除」钮）；新增 knowdo-setting 设置提案（白名单见 SettingProposal）；
 * system 提示注入《知行使用手册》摘要，能回答软件用法问题。
 *
 * compact＝结束页内嵌模式：消息区定高、只显示主线最近几条；否则整页模式。
 */
public class ChatPanel {
    private static final int COMPACT_VISIBLE = 6;
    private static final int DANGER = Color.parseColor("#B3261E");

    private final Activity act;
    private final boolean compact;
    private final TaskStore store;
    private String taskId;                 // 当前在聊的任务（上下文），null＝没带任务
    private List<ChatStore.Msg> msgs = new ArrayList<>();

    private LinearLayout msgCol;
    private ScrollView scroll;
    private EditText input;
    private TextView sendBtn;
    private TextView modelLine;
    private TextView ctxLine;
    private TextView hintView;
    private boolean streaming = false;
    private boolean pendingReload = false;

    private final ChatStore.ChangeListener mainListener = () -> {
        if (streaming) { pendingReload = true; return; }
        reloadIfChanged();
    };

    public ChatPanel(Activity act, ViewGroup container, boolean compact) {
        this.act = act;
        this.compact = compact;
        this.store = new TaskStore(act);
        msgs = ChatStore.loadMain(act);
        buildUi(container);
        renderAll();
        ChatStore.addChangeListener(mainListener);
    }

    /** 组件不再使用时调（宿主销毁）：摘掉主线监听。 */
    public void detach() { ChatStore.removeChangeListener(mainListener); }

    /** 切到这个任务上下文（问询页/编辑页带任务进来时）；传 null 清掉。 */
    public void setTaskContext(String id) {
        if (id != null && store.byId(id) == null) id = null; // 任务已删则不带上下文
        taskId = id;
        refreshCtxLine();
    }

    public String taskContext() { return taskId; }

    /** 页签切回来时调：主线若被别处（主动消息/结束页）改过，跟上最新。 */
    public void refresh() {
        reloadIfChanged();
        refreshCtxLine();
    }

    private void reloadIfChanged() {
        List<ChatStore.Msg> fresh = ChatStore.loadMain(act);
        if (sameTail(fresh)) return;
        msgs = fresh;
        renderAll();
    }

    /** 只比条数与最后一条的关键字段：自己刚存盘的那份与内存一致，不重画。 */
    private boolean sameTail(List<ChatStore.Msg> fresh) {
        if (fresh.size() != msgs.size()) return false;
        if (fresh.isEmpty()) return true;
        ChatStore.Msg a = fresh.get(fresh.size() - 1);
        ChatStore.Msg b = msgs.get(msgs.size() - 1);
        return eq(a.text, b.text) && eq(a.proposal, b.proposal)
                && a.propState == b.propState && eq(a.origin, b.origin);
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private int dp(int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) { Toast.makeText(act, s, Toast.LENGTH_LONG).show(); }
    private boolean en() { return Strings.en(); }

    // ---------------- 界面 ----------------

    private void buildUi(ViewGroup container) {
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(act));
        container.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 上下文行：带任务进来时小字注明在聊哪个任务，可点 ✕ 清掉（整页模式才有意义，紧凑模式也留）
        ctxLine = new TextView(act);
        ctxLine.setTextSize(12);
        ctxLine.setTextColor(Ui.link(act));
        ctxLine.setPadding(dp(20), dp(8), dp(20), 0);
        root.addView(ctxLine);
        refreshCtxLine();

        modelLine = new TextView(act);
        modelLine.setText(en() ? "The model is picked automatically — the strongest free one right now"
                : "模型由系统自动挑选当时最强的免费模型");
        modelLine.setTextSize(11);
        modelLine.setTextColor(Ui.sub(act));
        modelLine.setPadding(dp(20), dp(3), dp(20), dp(8));
        root.addView(modelLine);

        View line = new View(act);
        line.setBackgroundColor(Ui.line(act));
        root.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // 消息区
        scroll = new ScrollView(act);
        if (compact) {
            scroll.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(176)));
        } else {
            scroll.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        msgCol = new LinearLayout(act);
        msgCol.setOrientation(LinearLayout.VERTICAL);
        msgCol.setPadding(dp(20), dp(12), dp(20), dp(16));
        scroll.addView(msgCol);
        root.addView(scroll);

        hintView = new TextView(act);
        hintView.setTextSize(14);
        hintView.setTextColor(Ui.sub(act));
        hintView.setLineSpacing(0, 1.35f);

        // 输入行
        View line2 = new View(act);
        line2.setBackgroundColor(Ui.line(act));
        root.addView(line2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        LinearLayout inputRow = new LinearLayout(act);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.BOTTOM);
        inputRow.setPadding(dp(16), dp(10), dp(16), dp(14));
        input = new EditText(act);
        input.setHint(en() ? "Say whatever is on your mind" : "想到什么就说什么");
        input.setTextSize(16);
        input.setMaxLines(4);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        input.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { onSend(); return true; }
            return false;
        });
        inputRow.addView(input);
        sendBtn = new TextView(act);
        sendBtn.setText(en() ? "Send" : "发送");
        sendBtn.setTextSize(16);
        sendBtn.setTextColor(Ui.accent(act));
        sendBtn.setTypeface(sendBtn.getTypeface(), Typeface.BOLD);
        sendBtn.setPadding(dp(14), dp(8), dp(2), dp(8));
        sendBtn.setOnClickListener(v -> onSend());
        inputRow.addView(sendBtn);
        root.addView(inputRow);
    }

    private void refreshCtxLine() {
        if (ctxLine == null) return;
        TaskItem t = taskId == null ? null : store.byId(taskId);
        if (t == null) {
            taskId = null;
            ctxLine.setVisibility(View.GONE);
            ctxLine.setOnClickListener(null);
            return;
        }
        ctxLine.setVisibility(View.VISIBLE);
        ctxLine.setText((en() ? "Talking about: " : "正在聊：") + t.title
                + (en() ? "  (tap to clear)" : "（点这里取消）"));
        ctxLine.setOnClickListener(v -> { taskId = null; refreshCtxLine(); });
    }

    private void refreshHint() {
        if (msgs.isEmpty() && hintView.getParent() == null) {
            hintView.setText(en()
                    ? "Say what feels off — a task too big, too vague, at the wrong time. "
                      + "It can suggest creating, changing or deleting tasks, rescheduling your day, "
                      + "or changing a setting: everything lands as a suggestion card first, "
                      + "and nothing changes unless you tap Apply. You can also ask how anything in this app works"
                    : "把别扭的地方说给它听——任务太大、太糊、时机不对，都行。"
                      + "它能建议新建、修改、删除任务，能帮你重排今天，也能帮你改设置；"
                      + "但都先出提案卡，你点「应用」才生效，它不会自己动你的东西。"
                      + "软件哪里不会用，也可以直接问它");
            msgCol.addView(hintView, 0);
        } else if (!msgs.isEmpty() && hintView.getParent() != null) {
            msgCol.removeView(hintView);
        }
    }

    private void scrollToBottom() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    // ---------------- 消息渲染 ----------------

    private void renderAll() {
        msgCol.removeAllViews();
        if (hintView.getParent() != null) ((ViewGroup) hintView.getParent()).removeView(hintView);
        int from = compact ? Math.max(0, msgs.size() - COMPACT_VISIBLE) : 0;
        for (int i = from; i < msgs.size(); i++) renderMsg(msgs.get(i));
        refreshHint();
        scrollToBottom();
    }

    private TextView bubbleUser(String text) {
        TextView tv = new TextView(act);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(Ui.text(act));
        tv.setLineSpacing(0, 1.3f);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Ui.muted(act));
        tv.setBackground(g);
        tv.setPadding(dp(14), dp(10), dp(14), dp(10));
        return tv;
    }

    private void renderMsg(ChatStore.Msg m) {
        if ("user".equals(m.role)) {
            LinearLayout wrap = new LinearLayout(act);
            wrap.setGravity(Gravity.END);
            wrap.setPadding(0, dp(6), 0, dp(6));
            TextView tv = bubbleUser(m.text);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(56);
            tv.setLayoutParams(lp);
            wrap.addView(tv);
            msgCol.addView(wrap);
        } else {
            // 主动消息的来源小字（分神时/超时时）：极小、次级色，不抢眼
            String originLabel = ChatStore.originLabel(m.origin);
            if (originLabel != null) {
                TextView tag = new TextView(act);
                tag.setText(originLabel);
                tag.setTextSize(10.5f);
                tag.setTextColor(Ui.sub(act));
                tag.setPadding(dp(2), dp(8), 0, 0);
                msgCol.addView(tag);
            }
            TextView tv = new TextView(act);
            tv.setText(m.text);
            tv.setTextSize(16);
            tv.setTextColor(Ui.text(act));
            tv.setLineSpacing(0, 1.35f);
            tv.setPadding(dp(2), dp(4), dp(24), dp(6));
            msgCol.addView(tv);
            if (m.proposal != null) {
                if ("schedule".equals(m.propKind)) msgCol.addView(buildScheduleCard(m));
                else if ("setting".equals(m.propKind)) msgCol.addView(buildSettingCard(m));
                else msgCol.addView(buildProposalCard(m));
            }
        }
    }

    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Ui.surface(act));
        g.setStroke(1, Ui.line(act));
        card.setBackground(g);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, dp(4), 0, dp(10));
        card.setLayoutParams(clp);
        return card;
    }

    private TextView cardHead(String s, int color) {
        TextView head = new TextView(act);
        head.setText(s);
        head.setTextSize(13);
        head.setTextColor(color);
        head.setTypeface(head.getTypeface(), Typeface.BOLD);
        return head;
    }

    private void cardLine(LinearLayout card, String s, boolean bold) {
        TextView line = new TextView(act);
        line.setText(s);
        line.setTextSize(15);
        line.setTextColor(Ui.text(act));
        if (bold) line.setTypeface(line.getTypeface(), Typeface.BOLD);
        line.setPadding(0, dp(3), 0, 0);
        card.addView(line);
    }

    private TextView stateText(String s) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(Ui.sub(act));
        return t;
    }

    private TextView cardBtn(String s, boolean primary) {
        return cardBtn(s, primary ? Ui.accent(act) : 0, primary);
    }

    private TextView cardBtnDanger(String s) {
        return cardBtn(s, DANGER, true);
    }

    private TextView cardBtn(String s, int fill, boolean primary) {
        TextView b = new TextView(act);
        b.setText(s);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(17));
        if (primary) {
            g.setColor(fill);
            b.setTextColor(Ui.onAccent(act));
        } else {
            g.setColor(Ui.surface(act));
            g.setStroke(1, Ui.line(act));
            b.setTextColor(Ui.text(act));
        }
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(10), 0);
        b.setLayoutParams(lp);
        return b;
    }

    /** 卡片底部按钮区＋状态渲染（三类卡共用）：待决定出双钮，应用/忽略后出状态字。 */
    private LinearLayout cardBtnRow(final ChatStore.Msg m, final String appliedText,
                                    final String dismissedText, final String applyLabel,
                                    final boolean danger, final CardApply applyFn) {
        final LinearLayout btnRow = new LinearLayout(act);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(10), 0, 0);
        final Runnable[] renderState = new Runnable[1];
        renderState[0] = () -> {
            btnRow.removeAllViews();
            if (m.propState == ChatStore.PROP_APPLIED) {
                btnRow.addView(stateText(appliedText));
            } else if (m.propState == ChatStore.PROP_DISMISSED) {
                btnRow.addView(stateText(dismissedText));
            } else {
                TextView apply = danger ? cardBtnDanger(applyLabel) : cardBtn(applyLabel, true);
                apply.setOnClickListener(v -> {
                    if (applyFn.run()) {
                        m.propState = ChatStore.PROP_APPLIED;
                        ChatStore.saveMain(act, msgs);
                    }
                    renderState[0].run();
                });
                btnRow.addView(apply);
                TextView dismiss = cardBtn(en() ? "No, thanks" : "不用", false);
                dismiss.setOnClickListener(v -> {
                    m.propState = ChatStore.PROP_DISMISSED;
                    ChatStore.saveMain(act, msgs);
                    renderState[0].run();
                });
                btnRow.addView(dismiss);
            }
        };
        renderState[0].run();
        return btnRow;
    }

    private interface CardApply { boolean run(); }

    // ---------------- 任务提案卡（新建/修改/删除） ----------------

    /** 任务提案卡：action 决定卡面与应用动作。删除卡红色「删除」钮＋任务名加粗，一眼知道是删除。 */
    private View buildProposalCard(final ChatStore.Msg m) {
        final LinearLayout card = newCard();
        String action = proposalAction(m.proposal);
        if ("delete".equals(action)) {
            card.addView(cardHead(en() ? "Suggested deletion" : "它建议删掉这个任务", DANGER));
            TaskItem target = proposalTarget(m.proposal);
            cardLine(card, (en() ? "Delete \"" : "删除「")
                    + (target != null ? target.title : proposalTitle(m.proposal))
                    + (en() ? "\"" : "」"), true);
            cardLine(card, en() ? "Deleting removes it from the list (it stays in sync records as deleted)"
                    : "删掉后它就从任务列表里消失", false);
            card.addView(cardBtnRow(m,
                    en() ? "Deleted" : "已删除",
                    en() ? "Kept — the task is still there" : "没删，任务还在",
                    en() ? "Delete" : "删除", true,
                    () -> applyProposal(m)));
            return card;
        }
        if ("create".equals(action)) {
            card.addView(cardHead(en() ? "Suggested new task" : "它建议新建一个任务", Ui.accent(act)));
            for (String s : proposalSummary(m.proposal, true)) cardLine(card, "· " + s, false);
            card.addView(cardBtnRow(m,
                    en() ? "Created" : "已建好，回任务列表就能看到",
                    en() ? "Not created" : "没建，什么也没动",
                    en() ? "Apply" : "应用", false,
                    () -> applyProposal(m)));
            return card;
        }
        card.addView(cardHead(en() ? "Suggested change" : "它建议这样改", Ui.accent(act)));
        for (String s : proposalSummary(m.proposal, false)) cardLine(card, "· " + s, false);
        card.addView(cardBtnRow(m,
                en() ? "Applied" : "已按这个改过了",
                en() ? "Left as it was" : "没采用，任务没动",
                en() ? "Apply" : "应用", false,
                () -> applyProposal(m)));
        return card;
    }

    private String proposalAction(String proposalJson) {
        try {
            String a = new JSONObject(proposalJson).optString("action", "update");
            if ("create".equals(a) || "delete".equals(a)) return a;
        } catch (Exception ignored) { }
        return "update";
    }

    private String proposalTitle(String proposalJson) {
        try {
            return new JSONObject(proposalJson).optString("title", "");
        } catch (Exception ignored) { return ""; }
    }

    /** 提案的目标任务：解析时已把 taskId 规范化进提案 JSON；缺失时退回本面板的上下文任务。 */
    private TaskItem proposalTarget(String proposalJson) {
        String tid = null;
        try { tid = new JSONObject(proposalJson).optString("taskId", ""); } catch (Exception ignored) { }
        if (tid == null || tid.isEmpty()) tid = taskId;
        if (tid == null || tid.isEmpty()) return null;
        return store.byId(tid);
    }

    /** 把提案 JSON 读成人话概要（标题／时长／子任务／开始／截止／优先级，只列真要改的项）。 */
    private List<String> proposalSummary(String proposalJson, boolean creating) {
        List<String> lines = new ArrayList<>();
        try {
            JSONObject p = new JSONObject(proposalJson);
            if (p.has("title") && !p.optString("title").trim().isEmpty()) {
                lines.add(creating
                        ? (en() ? "Title: \"" : "标题：「") + p.optString("title").trim() + (en() ? "\"" : "」")
                        : (en() ? "Rename to \"" : "标题改为「") + p.optString("title").trim() + (en() ? "\"" : "」"));
            }
            if (p.has("priority") && !p.optString("priority").trim().isEmpty()) {
                boolean must = "must".equals(p.optString("priority").trim());
                lines.add((en() ? "Priority: " : "优先级：") + (must
                        ? (en() ? "Must-do" : "必做") : (en() ? "Secondary" : "次要")));
            }
            JSONArray subs = p.optJSONArray("subtasks");
            if (subs != null && subs.length() > 0) {
                int total = 0;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < subs.length(); i++) {
                    JSONObject s = subs.optJSONObject(i);
                    if (s == null) continue;
                    int min = Math.max(1, s.optInt("minutes", 5));
                    total += min;
                    if (sb.length() > 0) sb.append(en() ? "; " : "；");
                    sb.append(s.optString("title", "")).append(" ").append(min)
                            .append(en() ? " min" : " 分钟");
                }
                lines.add((en() ? "Split into subtasks (" : "拆成子任务（共 ") + total
                        + (en() ? " min): " : " 分钟）：") + sb);
            } else if (p.has("durationMinutes")) {
                lines.add((en() ? "Length: " : "时长改为 ") + Math.max(1, p.optInt("durationMinutes", 0))
                        + (en() ? " min" : " 分钟"));
            }
            if (p.has("nextStartAt") && !p.optString("nextStartAt").isEmpty()) {
                lines.add((en() ? "Start at " : "开始时间改为 ")
                        + p.optString("nextStartAt").replace('T', ' '));
            }
            if (p.has("deadline") && !p.optString("deadline").isEmpty()) {
                lines.add((en() ? "Deadline: " : "截止日期改为 ") + p.optString("deadline"));
            }
        } catch (Exception ignored) { }
        if (lines.isEmpty()) lines.add(en() ? "A small change to this task" : "对这个任务做一点调整");
        return lines;
    }

    /**
     * 应用任务提案：create＝按提案字段新建；delete＝走 TaskStore.remove（软删，与长按删除同口径）；
     * update＝按 id 重取最新副本再改（与 TaskEditSheet 同一纪律），touch 后落盘，
     * 开始时间变了重排 StartReminder。返回是否真的改上了。
     */
    private boolean applyProposal(ChatStore.Msg m) {
        final JSONObject p;
        try { p = new JSONObject(m.proposal); } catch (Exception e) { return false; }
        String action = proposalAction(m.proposal);
        store.load();
        if ("create".equals(action)) {
            String title = p.optString("title", "").trim();
            if (title.isEmpty()) {
                toast(en() ? "That suggestion has no title — nothing created" : "这条建议没写标题，没建");
                return false;
            }
            TaskItem t = TaskItem.create(title);
            applyFieldsTo(t, p, true);
            store.add(t);
            StartReminder.reschedule(act, store);
            toast(en() ? "Created — find it in the task list" : "已建好，回任务列表就能看到");
            return true;
        }
        TaskItem t = proposalTarget(m.proposal);
        if (t == null) {
            toast(en() ? "That task is gone" : "这个任务已经不在了");
            return false;
        }
        if ("delete".equals(action)) {
            store.remove(t);
            StartReminder.reschedule(act, store);
            toast(en() ? "Deleted" : "已删除");
            return true;
        }
        applyFieldsTo(t, p, false);
        t.touch();
        store.save();
        StartReminder.reschedule(act, store);
        toast(en() ? "Applied" : "已应用，回任务列表就能看到");
        return true;
    }

    /** 提案字段往任务上落（create 与 update 共用；与 v1.22.0 的字段口径一致）。 */
    private void applyFieldsTo(TaskItem t, JSONObject p, boolean creating) {
        if (!creating && p.has("title") && !p.optString("title").trim().isEmpty()) {
            t.title = p.optString("title").trim();
        }
        if (p.has("priority")) {
            String pr = p.optString("priority", "").trim();
            if ("must".equals(pr) || "secondary".equals(pr)) t.priority = pr;
        }
        JSONArray subs = p.optJSONArray("subtasks");
        if (subs != null && subs.length() > 0) {
            List<Subtask> list = new ArrayList<>();
            for (int i = 0; i < subs.length(); i++) {
                JSONObject s = subs.optJSONObject(i);
                if (s == null) continue;
                String st = s.optString("title", "").trim();
                if (st.isEmpty()) continue;
                list.add(Subtask.create(st, Math.max(1, s.optInt("minutes", 5))));
            }
            if (!list.isEmpty()) {
                t.subtasks = list;
                t.syncDurationFromSubtasks(); // 总时长＝子任务之和（既有口径）
            }
        } else if (p.has("durationMinutes") && !t.hasSubtasks()) {
            // 有子任务的任务时长由子任务决定，不单独改（与编辑弹层的锁定时长同口径）
            t.durationMinutes = Math.max(1, p.optInt("durationMinutes", t.durationMinutes));
        }
        String newStart = p.has("nextStartAt") ? p.optString("nextStartAt", "") : null;
        if (newStart != null && !newStart.isEmpty()) {
            t.nextStartAt = newStart;
            // 与编辑弹层同口径：选了开始时间，截止日期跟到同一天（每日任务除外）
            if (!"daily".equals(t.repeat) && newStart.length() >= 10) {
                t.deadline = newStart.substring(0, 10);
                t.deadlineTime = null;
            }
        }
        if (p.has("deadline")) {
            String dl = p.optString("deadline", "");
            if ("daily".equals(t.repeat)) {
                // 每日任务不带单日截止（既有口径）：这一项不应用
            } else if (dl.isEmpty()) {
                t.deadline = null;
                t.deadlineTime = null;
            } else {
                t.deadline = dl;
            }
        }
    }

    // ---------------- 设置提案卡 ----------------

    /** 设置提案卡：一句话说清「把 X 改成 Y」，点应用走 SettingProposal 的既有写路径。 */
    private View buildSettingCard(final ChatStore.Msg m) {
        final LinearLayout card = newCard();
        card.addView(cardHead(en() ? "Suggested setting change" : "它建议改一个设置", Ui.accent(act)));
        String key = "", value = "";
        try {
            JSONObject p = new JSONObject(m.proposal);
            key = p.optString("key", "");
            value = p.optString("value", "");
        } catch (Exception ignored) { }
        final String fKey = key, fValue = value;
        cardLine(card, SettingProposal.describe(key, value), false);
        card.addView(cardBtnRow(m,
                en() ? "Changed — see it in Settings" : "已改好，去设置里能看到",
                en() ? "Not changed" : "没改，设置没动",
                en() ? "Apply" : "应用", false,
                () -> {
                    String err = SettingProposal.apply(act, store, fKey, fValue);
                    if (err == null) {
                        toast(en() ? "Changed" : "已改好");
                        return true;
                    }
                    toast(err);
                    return false;
                }));
        return card;
    }

    // ---------------- 排程卡（v1.23.0，逻辑沿用） ----------------

    /**
     * 排程卡：把 AI 给的一版新排程摆成人看的清单——逐块「几点到几点 做什么」，
     * 排不下的单列一行。点「应用」才落盘：后台走 SyncManager 的排程写入口
     * （先回读云端合并、已过去/已完成的块原样保留、只按卡落盘），成功卡片转「已应用」。
     */
    private View buildScheduleCard(final ChatStore.Msg m) {
        final LinearLayout card = newCard();
        card.addView(cardHead(en() ? "Suggested plan for today" : "它建议这样排今天", Ui.accent(act)));
        for (String s : scheduleSummaryLines(m.proposal)) cardLine(card, s, false);
        final LinearLayout btnRow = new LinearLayout(act);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(10), 0, 0);
        card.addView(btnRow);
        final Runnable[] renderState = new Runnable[1];
        renderState[0] = () -> {
            btnRow.removeAllViews();
            if (m.propState == ChatStore.PROP_APPLIED) {
                btnRow.addView(stateText(en() ? "Plan applied" : "已按这个排好了"));
            } else if (m.propState == ChatStore.PROP_DISMISSED) {
                btnRow.addView(stateText(en() ? "Left as it was" : "没采用，日程没动"));
            } else {
                TextView apply = cardBtn(en() ? "Apply" : "应用", true);
                apply.setOnClickListener(v -> applyScheduleCard(m, renderState[0]));
                btnRow.addView(apply);
                TextView dismiss = cardBtn(en() ? "No, thanks" : "不用", false);
                dismiss.setOnClickListener(v -> {
                    m.propState = ChatStore.PROP_DISMISSED;
                    ChatStore.saveMain(act, msgs);
                    renderState[0].run();
                });
                btnRow.addView(dismiss);
            }
        };
        renderState[0].run();
        return card;
    }

    /** 排程提案 JSON → 卡面人话清单（块逐条列，排不下的单列；无效块在解析时已剔除）。 */
    private List<String> scheduleSummaryLines(String proposalJson) {
        List<String> lines = new ArrayList<>();
        for (LocalAutoScheduler.Block b : parseScheduleBlocks(proposalJson)) {
            TaskItem t = store.byId(b.taskId);
            lines.add(LocalAutoScheduler.hhmm(b.startMin) + "–" + LocalAutoScheduler.hhmm(b.endMin)
                    + "  " + (t != null ? t.title : b.taskId));
        }
        for (String name : scheduleUnscheduled(proposalJson)) {
            lines.add((en() ? "No room for: " : "排不下：") + name);
        }
        if (lines.isEmpty()) {
            lines.add(en() ? "Nothing schedulable in this plan" : "这版排程里没有能落盘的块");
        }
        return lines;
    }

    /**
     * 排程提案里的块做落盘前校验：任务必须真实存在、时间合法、不排过 22:30、
     * 开始时间不许早于现在（已经过去的块落不了盘，应用时既有块由合并逻辑保留）。
     */
    private List<LocalAutoScheduler.Block> parseScheduleBlocks(String proposalJson) {
        List<LocalAutoScheduler.Block> out = new ArrayList<>();
        try {
            JSONObject p = new JSONObject(proposalJson);
            JSONArray arr = p.optJSONArray("blocks");
            if (arr == null) return out;
            int nowMin = DayAccount.nowMinutes();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String tid = o.optString("taskId", "");
                if (tid.isEmpty() || store.byId(tid) == null) continue;
                int s = LocalAutoScheduler.parseHhmm(o.optString("start", ""));
                int e = LocalAutoScheduler.parseHhmm(o.optString("end", ""));
                if (s < 0 || e <= s || e > DayAccount.DAY_END_MIN) continue;
                if (s < nowMin) continue;
                out.add(new LocalAutoScheduler.Block(tid, s, e));
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** 排程提案里「排不下」的任务名清单。 */
    private List<String> scheduleUnscheduled(String proposalJson) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONObject(proposalJson).optJSONArray("unscheduled");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** 排程卡点「应用」：后台走 sanction 写入口落盘，回来按结果翻卡状态。 */
    private void applyScheduleCard(final ChatStore.Msg m, final Runnable renderState) {
        final List<LocalAutoScheduler.Block> blocks = parseScheduleBlocks(m.proposal);
        if (blocks.isEmpty()) {
            toast(en() ? "Nothing in this plan can be placed now (its times may have passed)"
                    : "这张排程卡里没有现在能落盘的块（时间可能已经过去了）");
            return;
        }
        store.load();
        toast(en() ? "Saving the plan…" : "正在保存排程…");
        new Thread(() -> {
            final String err = SyncManager.applyScheduleProposal(
                    act, store, blocks, "KnowDo App（聊天排程）");
            act.runOnUiThread(() -> {
                if (err == null) {
                    m.propState = ChatStore.PROP_APPLIED;
                    ChatStore.saveMain(act, msgs);
                    toast(en() ? "Plan applied — check the calendar" : "已按这个排好了，去日历页就能看到");
                } else {
                    toast(err);
                }
                renderState.run();
            });
        }).start();
    }

    // ---------------- 发送与流式 ----------------

    private void onSend() {
        if (streaming) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        ChatStore.Msg userMsg = new ChatStore.Msg("user", text);
        msgs.add(userMsg);
        trimHistory();
        ChatStore.saveMain(act, msgs);
        renderAll();

        // 流式占位：先放一个空的回复视图，增量到了整段替换
        final TextView live = new TextView(act);
        live.setText("…");
        live.setTextSize(16);
        live.setTextColor(Ui.text(act));
        live.setLineSpacing(0, 1.35f);
        live.setPadding(dp(2), dp(10), dp(24), dp(6));
        msgCol.addView(live);
        scrollToBottom();
        streaming = true;
        sendBtn.setTextColor(Ui.sub(act));

        JSONArray payload;
        try {
            payload = ChatClient.toMessagesJson(buildSystemPrompt(), msgs, null);
        } catch (Exception e) {
            finishStreamError(live, en() ? "Something went wrong putting the message together"
                    : "消息没拼好，再试一次");
            return;
        }
        ChatClient.send(act, payload, new ChatClient.Callback() {
            @Override public void onModel(String modelId) {
                modelLine.setText((en() ? "Current model: " : "当前模型：") + modelId);
            }
            @Override public void onDelta(String fullText) {
                live.setText(displayPart(fullText));
                scrollToBottom();
            }
            @Override public void onDone(String fullText) {
                streaming = false;
                sendBtn.setTextColor(Ui.accent(act));
                msgCol.removeView(live);
                ChatStore.Msg am = parseAssistant(fullText);
                msgs.add(am);
                trimHistory();
                ChatStore.saveMain(act, msgs);
                renderAll();
                afterStreamEnd();
            }
            @Override public void onError(String friendlyMsg) {
                finishStreamError(live, friendlyMsg);
            }
        });
    }

    private void finishStreamError(TextView live, String friendlyMsg) {
        streaming = false;
        sendBtn.setTextColor(Ui.accent(act));
        msgCol.removeView(live);
        toast(friendlyMsg);
        afterStreamEnd();
    }

    /** 流结束后，若期间有主动消息落账（监听时推迟了），这时再跟上。 */
    private void afterStreamEnd() {
        if (pendingReload) {
            pendingReload = false;
            reloadIfChanged();
        }
    }

    /** 展示正文：三种提案代码块都不给用户看原文，流式中出现任一块开头即截断显示。 */
    private static String displayPart(String fullText) {
        int cut = -1;
        for (String f : new String[]{FENCE, FENCE_SCHED, FENCE_SET}) {
            int i = fullText.indexOf(f);
            if (i >= 0 && (cut < 0 || i < cut)) cut = i;
        }
        if (cut >= 0) return fullText.substring(0, cut).trim();
        return fullText;
    }

    /**
     * 流结束：剥离提案块、解析提案 JSON，组装成要落盘的助手消息。
     * 三种围栏谁先出现算谁的（一次回复至多收一个提案，与 system 提示的口径一致）：
     * knowdo-proposal＝任务增删改（v1.24.0 起两种对话都收，目标由 action/taskId 定）；
     * knowdo-schedule＝排程提案；knowdo-setting＝设置提案（过白名单＋值校验才收）。
     * 任务提案收下前把 action 与 taskId 规范化写回 JSON，历史重开时应用目标不漂移。
     */
    private ChatStore.Msg parseAssistant(String fullText) {
        ChatStore.Msg m = new ChatStore.Msg("assistant", "");
        String proposal = null;
        String kind = "task";
        String text = fullText == null ? "" : fullText;
        int ti = text.indexOf(FENCE);
        int si = text.indexOf(FENCE_SCHED);
        int ki = text.indexOf(FENCE_SET);
        int start = -1;
        if (ti >= 0) { start = ti; kind = "task"; }
        if (si >= 0 && (start < 0 || si < start)) { start = si; kind = "schedule"; }
        if (ki >= 0 && (start < 0 || ki < start)) { start = ki; kind = "setting"; }
        if (start >= 0) {
            int jsonStart = text.indexOf('\n', start);
            int end = jsonStart < 0 ? -1 : text.indexOf(FENCE_MARK, jsonStart);
            if (jsonStart >= 0 && end > jsonStart) {
                String json = text.substring(jsonStart + 1, end).trim();
                proposal = normalizeProposal(kind, json);
                text = text.substring(0, start) + text.substring(end + 3);
            } else {
                text = text.substring(0, start);
            }
        }
        m.text = text.trim();
        if (m.text.isEmpty()) {
            m.text = proposal != null
                    ? (en() ? "Here is my suggestion:" : "我的建议在这张卡里：")
                    : (en() ? "(No reply came back — try again)" : "（这次没收到回复，再试一次）");
        }
        if (proposal != null) {
            if ("schedule".equals(kind)) {
                // 排程提案：校验后一个能落盘的块都没有、又没报排不下的，就不当提案收
                if (!parseScheduleBlocks(proposal).isEmpty()
                        || !scheduleUnscheduled(proposal).isEmpty()) {
                    m.proposal = proposal;
                    m.propKind = "schedule";
                    m.propState = ChatStore.PROP_PENDING;
                }
            } else {
                m.proposal = proposal;
                m.propKind = kind;
                m.propState = ChatStore.PROP_PENDING;
            }
        }
        return m;
    }

    /** 提案 JSON 校验＋规范化：不合法返回 null（当普通回复收，代码块原文也不展示）。 */
    private String normalizeProposal(String kind, String json) {
        try {
            JSONObject p = new JSONObject(json);
            if ("setting".equals(kind)) {
                String key = p.optString("key", "");
                if (!SettingProposal.isAllowed(key)) return null;
                Object raw = p.has("value") ? p.get("value") : null;
                String v = SettingProposal.normalize(key, raw);
                if (v == null) return null;
                JSONObject out = new JSONObject();
                out.put("key", key);
                out.put("value", v);
                out.put("label", p.optString("label", ""));
                return out.toString();
            }
            if ("schedule".equals(kind)) return json; // 块的校验在卡面/应用时做（沿用 v1.23.0）
            // task：action 规范化；update/delete 补齐目标 taskId（AI 没给时用当前上下文任务）
            String action = p.optString("action", "update");
            if (!"create".equals(action) && !"delete".equals(action)) action = "update";
            p.put("action", action);
            if (!"create".equals(action)) {
                String tid = p.optString("taskId", "");
                if (tid.isEmpty() && taskId != null) {
                    p.put("taskId", taskId);
                }
                if (p.optString("taskId", "").isEmpty()) return null; // 没目标的改/删收不了
                if (store.byId(p.optString("taskId", "")) == null) return null; // 目标不存在
            }
            if ("create".equals(action) && p.optString("title", "").trim().isEmpty()) return null;
            return p.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void trimHistory() {
        // 历史只在本机，但也别无限涨：主线最多留最近 120 条
        while (msgs.size() > 120) msgs.remove(0);
    }

    private static final String FENCE_MARK = "`" + "`" + "`";
    private static final String FENCE = FENCE_MARK + "knowdo-proposal";
    private static final String FENCE_SCHED = FENCE_MARK + "knowdo-schedule";
    private static final String FENCE_SET = FENCE_MARK + "knowdo-setting";

    // ---------------- 上下文注入 ----------------

    /** 每次发送都现拼 system：任务字段与今日已用取当下最新值，多轮聊着数据不旧。 */
    private String buildSystemPrompt() {
        store.load();
        StringBuilder sb = new StringBuilder();
        sb.append("你是知行 KnowDo 里的 AI 伙伴，陪用户把没想清楚的任务聊清楚。")
          .append("用用户说话的语言回复；语气平静、简短、具体，不说教，不用夸张的鼓励，先听他说卡在哪里，必要时只追问一句。")
          .append("用户没做好的任务，多半不是不想做，是任务本身没想清楚：太大、太糊、或者时机不对。你的活是陪他想清楚，而不是替他做。");
        sb.append("\n\n").append(en() ? MANUAL_EN : MANUAL_ZH);
        if (taskId == null) {
            sb.append("\n\n这次用户没有带具体任务，想到什么聊什么。下面是他今天的任务（改/删时从这里选真实 taskId，不要主动逐条点评）：\n");
            List<TaskItem> vis = store.visibleSorted();
            int n = 0;
            for (TaskItem t : vis) {
                sb.append("- 〔").append(t.id).append("〕").append(t.title)
                  .append(t.isDoneToday() ? "（今天已完成）" : "（未完成）").append("\n");
                if (++n >= 20) break;
            }
            if (n == 0) sb.append("（今天还没有任务）\n");
        } else {
            TaskItem t = store.byId(taskId);
            if (t != null) {
                long usedMs = 0;
                try { usedMs = SessionLog.todayMsForTask(act, t.id); } catch (Exception ignored) { }
                int usedMin = (int) Math.round(usedMs / 60000.0);
                sb.append("\n\n当前在聊的任务：\n");
                sb.append("标题：").append(t.title).append("〔taskId：").append(t.id).append("〕\n");
                if (t.note != null && !t.note.trim().isEmpty()) {
                    sb.append("备注：").append(t.note.trim()).append("\n");
                }
                sb.append("计划时长：").append(t.durationMinutes).append(" 分钟\n");
                sb.append("重复：").append("daily".equals(t.repeat) ? "每天" : "不重复").append("\n");
                if (t.deadline != null) sb.append("截止日期：").append(t.deadline).append("\n");
                if (t.nextStartAt != null) sb.append("计划开始：").append(t.nextStartAt.replace('T', ' ')).append("\n");
                sb.append("今天已经做了约 ").append(usedMin).append(" 分钟\n");
                sb.append("今天状态：").append(t.isDoneToday() ? "已完成" : "未完成").append("\n");
                if (t.hasSubtasks()) {
                    sb.append("子任务：\n");
                    for (Subtask s : t.subtasks) {
                        sb.append("- ").append(s.title).append("（").append(s.minutes).append(" 分钟）\n");
                    }
                }
                sb.append("今天日期：").append(TaskItem.today())
                  .append("（涉及日期时间时以此为准）");
            }
        }
        sb.append(proposalSection());
        sb.append(settingSection());
        sb.append(scheduleSection());
        return sb.toString();
    }

    /** 任务增删改的出块格式（v1.24.0，两种对话都拼）。 */
    private String proposalSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n如果你想建议新建、修改或删除一个任务，在回复的最后另起一段，输出一个代码块，格式严格如下（正文里把理由讲清，不要再复述代码块内容）：\n")
          .append(FENCE).append("\n")
          .append("{\"action\":\"update\",\"taskId\":\"任务id\",\"title\":\"新标题\",\"durationMinutes\":25,\"deadline\":\"2026-10-26\",\"nextStartAt\":\"2026-10-26T09:00\",\"priority\":\"must\",\"subtasks\":[{\"title\":\"第一步\",\"minutes\":10}]}\n")
          .append(FENCE_MARK).append("\n")
          .append("action 三种：create＝新建（不要带 taskId）；update＝修改（带 taskId）；delete＝删除（带 taskId，除此之外不要放别的字段）。");
        if (taskId != null) {
            sb.append("当前在聊的任务就是上面那个：改它或删它时 taskId 可以省略，系统会自动补上。");
        } else {
            sb.append("update/delete 的 taskId 只能从上面列出的任务里选真实 id，不许编造。");
        }
        sb.append("其余字段全部可选，只放你真要改的：title 任务标题；durationMinutes 计划时长（分钟整数）；")
          .append("deadline 截止日期 YYYY-MM-DD；nextStartAt 计划开始时间 YYYY-MM-DDTHH:mm；")
          .append("priority 优先级，must＝必做、secondary＝次要；")
          .append("subtasks 子任务数组，给出即整体替换子任务，总时长按子任务之和重算（此时不要再同时给 durationMinutes）。")
          .append("一次回复最多给一个任务提案。提案只是建议：用户点「应用」才生效，删除也不例外；点「不用」就当没提过。")
          .append("只是闲聊或追问时不要输出代码块。");
        return sb.toString();
    }

    /** 设置提案的出块格式（v1.24.0，两种对话都拼；白名单与 SettingProposal 一致）。 */
    private String settingSection() {
        return "\n\n如果你想建议改一个设置，在回复的最后另起一段，输出一个代码块，格式严格如下：\n"
                + FENCE_SET + "\n"
                + "{\"key\":\"idle_nag_tier\",\"value\":\"strict\",\"label\":\"把空档催促改成严格\"}\n"
                + FENCE_MARK + "\n"
                + "key 只许四个：semantic_enabled（语义判定开关，value 用 true 或 false）、"
                + "diag_mode（测试模式开关，value 用 true 或 false）、"
                + "guard_enabled（屏幕内容守卫总开关，value 用 true 或 false）、"
                + "idle_nag_tier（空档催促档位，value 用 gentle、standard 或 strict）。label 用一句人话写清改什么。"
                + "名单外的设置你改不了，不要假装能改。一次回复最多一个设置提案，且不要与任务/排程提案同时出。"
                + "同样只是建议：用户点「应用」才生效。只是聊天时不要输出代码块。";
    }

    /**
     * 排程上下文＋提案格式（v1.23.0，两种对话都拼）：今日已排的块（带 taskId）、
     * 还没排进今天的必做、今日全部未完成任务、时间账三个数、现在时间，
     * 以及排程提案的输出格式与硬规矩。每次发送现拼，数字不旧。
     */
    private String scheduleSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n你还能帮他排今天的日程。下面是今天当下的情况：\n");
        String today = TaskItem.today();
        Set<String> scheduledIds = new HashSet<>();
        List<LocalAutoScheduler.Block> todayBlocks =
                LocalAutoScheduler.blocksOf(SyncManager.latestSchedule(act), today);
        if (todayBlocks.isEmpty()) {
            sb.append("今天还没有排任何块。\n");
        } else {
            sb.append("今天已经排好的块（开始–结束 任务名〔taskId〕）：\n");
            for (LocalAutoScheduler.Block b : todayBlocks) {
                TaskItem bt = store.byId(b.taskId);
                sb.append("- ").append(LocalAutoScheduler.hhmm(b.startMin)).append("–")
                  .append(LocalAutoScheduler.hhmm(b.endMin)).append(" ")
                  .append(bt != null ? bt.title : b.taskId)
                  .append("〔").append(b.taskId).append("〕\n");
                scheduledIds.add(b.taskId);
            }
        }
        StringBuilder unscheduledMust = new StringBuilder();
        StringBuilder allOpen = new StringBuilder();
        int open = 0;
        for (TaskItem t : store.visibleSorted()) {
            if (t.isDoneToday()) continue;
            boolean must = "must".equals(t.priority);
            if (must && !scheduledIds.contains(t.id)) {
                unscheduledMust.append("- 〔").append(t.id).append("〕").append(t.title)
                        .append("（").append(t.durationMinutes).append(" 分钟）\n");
            }
            if (open < 25) {
                allOpen.append("- 〔").append(t.id).append("〕").append(t.title)
                        .append("（").append(t.durationMinutes).append(" 分钟，")
                        .append(must ? "必做" : "次要").append("）\n");
                open++;
            }
        }
        sb.append("还没排进今天的必做任务：\n")
          .append(unscheduledMust.length() > 0 ? unscheduledMust.toString() : "（没有）\n");
        sb.append("今天全部未完成的任务（排程只能引用这里的 taskId）：\n")
          .append(allOpen.length() > 0 ? allOpen.toString() : "（没有）\n");
        DayAccount.Result a = DayAccount.compute(store);
        sb.append("今日时间账：还剩约 ").append(DayAccount.fmtDur(a.remainMin))
          .append("，必干占约 ").append(DayAccount.fmtDur(a.mustBufferedMin))
          .append("，空余约 ").append(a.freeMin < 0
                  ? "−" + DayAccount.fmtDur(-a.freeMin) + "（已经超了）"
                  : DayAccount.fmtDur(a.freeMin))
          .append("。现在时间约 ")
          .append(LocalAutoScheduler.hhmm(DayAccount.nowMinutes())).append("。\n");
        sb.append("\n如果用户让你排或重排今天的日程，在回复的最后另起一段，输出一个代码块，格式严格如下（正文里把思路讲清，不要再复述代码块内容）：\n")
          .append(FENCE_SCHED).append("\n")
          .append("{\"blocks\":[{\"taskId\":\"任务id\",\"start\":\"09:00\",\"end\":\"10:30\"}],\"unscheduled\":[\"排不下的任务名\"]}\n")
          .append(FENCE_MARK).append("\n")
          .append("blocks 给出你建议的、尚未开始的块；unscheduled 放实在排不下的任务名，没有就给空数组。")
          .append("排程硬规矩，必须遵守：")
          .append("每件任务按它的计划时长×1.5 留缓冲来占时间；")
          .append("必做任务优先排；")
          .append("已经过去的块和已完成任务的块不许动，也不要把它们写进 blocks（应用时它们会被原样保留）；")
          .append("块的开始时间不许早于现在；")
          .append("不许排过 22:30；")
          .append("排不下就放进 unscheduled 并在正文里明说，不许硬塞；")
          .append("只能用上面列出的真实 taskId，不许编造任务或 id。")
          .append("排程卡只是建议：用户点「应用」才生效，点「不用」就当没提过。")
          .append("只是聊天、用户没让你排程时，不要输出这个代码块。一次回复最多给一个排程提案。");
        return sb.toString();
    }

    // ---------------- 《知行使用手册》摘要（回答用法问题以此为准，不许编造功能） ----------------

    private static final String MANUAL_ZH =
            "《知行使用手册》摘要（用户问软件怎么用时以此为准，不要编造不存在的功能）：\n"
            + "知行是任务计时应用。日程页是 Excel 式任务表（任务/情景标签/截止日期或每日重复/计划时长/优先级），点行展开编辑，点行首 ▶ 开始计时；计时中可暂停，暂停要选目的和时长；有子任务的任务按子任务逐个倒数，总时长等于子任务之和。计时过半和结束时有全屏检查页：结束页可选「做完了」、加时（+50%、+5/+10/+15 分钟或自定义），加时前可以记一下当前状态。任务结束还没开始下一个时，会弹「接下来，做点什么」页，选下一个任务或休息一会。底部页签：日程、习惯、知行、聊聊（就是这里）。日程页右上「日历」展开日历面板：任务行能拖进日历排时间；必做任务和昨天滚进来的未完成任务，App 会自动填进今天的空档，排不下会在时间账下方点名。今日时间账在日程页最上方：今天还剩多少小时、必干的活占多少（按计划时长×1.5 算）、空余多少。习惯：每天早上有晨间页，填昨天的习惯记录和日记；习惯页看长年累月的账。知行页看知行指数＝习惯达成×50%＋任务完成×35%＋分神控制×15%。屏幕内容守卫：在设置里开总开关；计时中会采样屏幕内容，判和当前任务合不合，不合就弹问询页（立即回到任务／给我 30 或 60 秒宽限／这是误判本次放行），截图只在本机分析、随即销毁、不保存不上传。语义判定是守卫的一层（设置里有独立开关）：本地拿不准时，把打码后的屏幕文字发云端大模型终判，只发文字不发图；关掉语义判定就全部本地判。模型下载在设置里：黄色识别模型和语义小模型按需下载，谁用谁下，装机包里不带。跟我聊天时，我给的改任务、排日程、改设置建议都会先出提案卡，你点应用才生效；在聊天里能改的设置只有四个：语义判定、测试模式、屏幕内容守卫总开关、空档催促档位（温和/标准/严格），其余设置要去设置页。设置里还有：更新日志、清除聊天记录、账号与同步。";

    private static final String MANUAL_EN =
            "KnowDo manual digest (when the user asks how the app works, go by this — never invent features):\n"
            + "KnowDo is a task timer. The Schedule page is an Excel-like task table (task / context tags / deadline or daily repeat / planned length / priority): tap a row to expand and edit it, tap the ▶ at the row start to begin timing. You can pause mid-timer (a pause asks for a purpose and a length). Tasks with subtasks count down subtask by subtask, and the total length is the sum of the subtasks. At half-time and at the end a full-screen check page appears: on the end page choose Done, or extend (+50%, +5/+10/+15 minutes, or custom); before extending you can note how you feel. When a task ends and the next hasn't started, a \"What's next\" page asks you to pick the next task or rest a while. Bottom tabs: Schedule, Habits, Zhixing, and Chat (this page). The Calendar button at the top right of the Schedule page opens the calendar panel: drag task rows into the calendar to plan them; must-do tasks and yesterday's rolled-over unfinished tasks are slotted into today's gaps automatically, and anything that doesn't fit is named under the time account. The time account sits at the top of the Schedule page: hours left today, hours the must-do work takes (planned length × 1.5), and hours still free. Habits: a morning page each day collects yesterday's habit records and diary; the Habits tab keeps the long-term ledger. The Zhixing tab shows the Zhixing index = habits 50% + task completion 35% + distraction control 15%. The screen-content guard is switched on in Settings: while a timer runs it samples the screen and judges whether the content matches the current task; a mismatch raises an inquiry page (return to the task now / 30 or 60 seconds of grace / a false alarm, allowed for this session). Screenshots are analysed on-device only and destroyed immediately — never saved, never uploaded. Semantic verdicts are one layer of the guard (own switch in Settings): when the on-device checks can't decide, masked screen text — never an image — goes to a cloud model for the final call; turn the switch off and everything is judged locally. Model downloads live in Settings: the image model and the semantic mini-model download on demand, only for whoever uses them. In chat, my suggestions for tasks, schedules and settings always arrive as proposal cards and take effect only when you tap Apply. Only four settings can be changed from chat: semantic verdicts, test mode, the screen-content guard master switch, and the idle-nudging level (gentle / standard / strict); everything else lives in Settings, which also holds the changelog, clearing chat history, and account & sync.";
}
