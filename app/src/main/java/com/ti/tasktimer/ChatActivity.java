package com.ti.tasktimer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话页（v1.22.0）：「和 AI 聊聊这个任务」。
 * 两种进法：①任务编辑弹层带任务 id 进入——system 提示注入这个任务的标题／备注／
 * 计划时长／deadline 与重复／今日已用／子任务，AI 陪用户把任务聊成型，它提出的
 * 结构化改法渲染成「提案卡」，用户点「应用」才走既有任务写路径真正改，点「不用」
 * 丢弃，AI 永远不能静默改任务；②通用入口（主界面侧边栏）不带任务，只注入今日
 * 任务概览，想到什么聊什么，不产生提案卡。
 * 对话经 Supabase 代理 chat-proxy 走 OpenRouter 免费模型（服务端自动挑最强、
 * 失败顺延），顶部小字显示本轮实际用的模型 id，换了跟着变。
 * 历史只存本机（ChatStore），按任务 id 分开，设置里可一键清除，不上云。
 */
public class ChatActivity extends Activity {
    public static final String EXTRA_TASK_ID = "task_id";

    private TaskStore store;
    private String taskId;                 // null＝通用对话
    private String histKey;
    private List<ChatStore.Msg> msgs = new ArrayList<>();

    private LinearLayout msgCol;
    private ScrollView scroll;
    private EditText input;
    private TextView sendBtn;
    private TextView modelLine;
    private TextView hintView;
    private boolean streaming = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        store = new TaskStore(this);
        taskId = getIntent().getStringExtra(EXTRA_TASK_ID);
        if (taskId != null && store.byId(taskId) == null) taskId = null; // 任务已删则退化为通用
        histKey = taskId == null ? ChatStore.GENERAL : taskId;
        msgs = ChatStore.load(this, histKey);
        buildUi();
        for (ChatStore.Msg m : msgs) renderMsg(m);
        refreshHint();
        scrollToBottom();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private boolean en() { return Strings.en(); }

    // ---------------- 界面 ----------------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));
        setContentView(root);

        // 顶栏：返回＋标题＋当前模型小字
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20), dp(14), dp(20), dp(10));
        TextView back = new TextView(this);
        back.setText("‹ " + (en() ? "Back" : "返回"));
        back.setTextSize(14);
        back.setTextColor(Ui.link(this));
        back.setPadding(0, dp(2), dp(8), dp(6));
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = new TextView(this);
        TaskItem t = taskId == null ? null : store.byId(taskId);
        title.setText(t != null ? t.title : (en() ? "Chat with AI" : "和 AI 聊聊"));
        title.setTextSize(20);
        title.setTextColor(Ui.text(this));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        top.addView(title);
        modelLine = new TextView(this);
        modelLine.setText(en() ? "The model is picked automatically — the strongest free one right now"
                : "模型由系统自动挑选当时最强的免费模型");
        modelLine.setTextSize(11);
        modelLine.setTextColor(Ui.sub(this));
        modelLine.setPadding(0, dp(4), 0, 0);
        top.addView(modelLine);
        root.addView(top);
        View line = new View(this);
        line.setBackgroundColor(Ui.line(this));
        root.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // 消息区
        scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        msgCol = new LinearLayout(this);
        msgCol.setOrientation(LinearLayout.VERTICAL);
        msgCol.setPadding(dp(20), dp(14), dp(20), dp(18));
        scroll.addView(msgCol);
        root.addView(scroll);

        hintView = new TextView(this);
        hintView.setTextSize(14);
        hintView.setTextColor(Ui.sub(this));
        hintView.setLineSpacing(0, 1.35f);

        // 输入行
        View line2 = new View(this);
        line2.setBackgroundColor(Ui.line(this));
        root.addView(line2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.BOTTOM);
        inputRow.setPadding(dp(16), dp(10), dp(16), dp(14));
        input = new EditText(this);
        input.setHint(taskId != null
                ? (en() ? "Say where this task feels stuck" : "说说这个任务哪里别扭")
                : (en() ? "Say whatever is on your mind" : "想到什么就说什么"));
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
        sendBtn = new TextView(this);
        sendBtn.setText(en() ? "Send" : "发送");
        sendBtn.setTextSize(16);
        sendBtn.setTextColor(Ui.accent(this));
        sendBtn.setTypeface(sendBtn.getTypeface(), Typeface.BOLD);
        sendBtn.setPadding(dp(14), dp(8), dp(2), dp(8));
        sendBtn.setOnClickListener(v -> onSend());
        inputRow.addView(sendBtn);
        root.addView(inputRow);
    }

    private void refreshHint() {
        if (msgs.isEmpty() && hintView.getParent() == null) {
            hintView.setText(taskId != null
                    ? (en() ? "Tell it where this task feels off — too big, too vague, wrong time. "
                            + "It will talk it through with you and suggest concrete changes. "
                            + "Nothing changes unless you tap Apply on its suggestion card"
                            : "把别扭的地方说给它听——太大、太糊、时机不对，都行。"
                            + "它会陪你把这个任务聊清楚，给出具体的改法；"
                            + "改不改由你点提案卡上的「应用」说了算，它不会自己动你的任务")
                    : (en() ? "No task attached here — say whatever is on your mind"
                            : "这里不带具体任务，想到什么都能聊"));
            msgCol.addView(hintView, 0);
        } else if (!msgs.isEmpty() && hintView.getParent() != null) {
            msgCol.removeView(hintView);
        }
    }

    private void scrollToBottom() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    // ---------------- 消息渲染 ----------------

    private TextView bubbleUser(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(Ui.text(this));
        tv.setLineSpacing(0, 1.3f);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Ui.muted(this));
        tv.setBackground(g);
        tv.setPadding(dp(14), dp(10), dp(14), dp(10));
        return tv;
    }

    private void renderMsg(ChatStore.Msg m) {
        if ("user".equals(m.role)) {
            LinearLayout wrap = new LinearLayout(this);
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
            TextView tv = new TextView(this);
            tv.setText(m.text);
            tv.setTextSize(16);
            tv.setTextColor(Ui.text(this));
            tv.setLineSpacing(0, 1.35f);
            tv.setPadding(dp(2), dp(10), dp(24), dp(6));
            msgCol.addView(tv);
            if (m.proposal != null) msgCol.addView(buildProposalCard(m));
        }
    }

    /** 提案卡：一张卡说清要改什么，应用/不用两个按钮；状态随消息存盘，重开按存的状态渲染。 */
    private View buildProposalCard(final ChatStore.Msg m) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Ui.surface(this));
        g.setStroke(1, Ui.line(this));
        card.setBackground(g);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, dp(4), 0, dp(10));
        card.setLayoutParams(clp);

        TextView head = new TextView(this);
        head.setText(en() ? "Suggested change" : "它建议这样改");
        head.setTextSize(13);
        head.setTextColor(Ui.accent(this));
        head.setTypeface(head.getTypeface(), Typeface.BOLD);
        card.addView(head);

        for (String s : proposalSummary(m.proposal)) {
            TextView line = new TextView(this);
            line.setText("· " + s);
            line.setTextSize(15);
            line.setTextColor(Ui.text(this));
            line.setPadding(0, dp(3), 0, 0);
            card.addView(line);
        }

        final LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(10), 0, 0);
        card.addView(btnRow);
        final Runnable[] renderState = new Runnable[1];
        renderState[0] = () -> {
            btnRow.removeAllViews();
            if (m.propState == ChatStore.PROP_APPLIED) {
                btnRow.addView(stateText(en() ? "Applied" : "已按这个改过了"));
            } else if (m.propState == ChatStore.PROP_DISMISSED) {
                btnRow.addView(stateText(en() ? "Left as it was" : "没采用，任务没动"));
            } else {
                TextView apply = cardBtn(en() ? "Apply" : "应用", true);
                apply.setOnClickListener(v -> {
                    if (applyProposal(m)) {
                        m.propState = ChatStore.PROP_APPLIED;
                        ChatStore.save(ChatActivity.this, histKey, msgs);
                    }
                    renderState[0].run();
                });
                btnRow.addView(apply);
                TextView dismiss = cardBtn(en() ? "No, thanks" : "不用", false);
                dismiss.setOnClickListener(v -> {
                    m.propState = ChatStore.PROP_DISMISSED;
                    ChatStore.save(ChatActivity.this, histKey, msgs);
                    renderState[0].run();
                });
                btnRow.addView(dismiss);
            }
        };
        renderState[0].run();
        return card;
    }

    private TextView stateText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(Ui.sub(this));
        return t;
    }

    private TextView cardBtn(String s, boolean primary) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(17));
        if (primary) {
            g.setColor(Ui.accent(this));
            b.setTextColor(Ui.onAccent(this));
        } else {
            g.setColor(Ui.surface(this));
            g.setStroke(1, Ui.line(this));
            b.setTextColor(Ui.text(this));
        }
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(10), 0);
        b.setLayoutParams(lp);
        return b;
    }

    // ---------------- 提案：概要与应用 ----------------

    /** 把提案 JSON 读成人话概要（标题／时长／子任务／开始／截止，只列真要改的项）。 */
    private List<String> proposalSummary(String proposalJson) {
        List<String> lines = new ArrayList<>();
        try {
            JSONObject p = new JSONObject(proposalJson);
            if (p.has("title") && !p.optString("title").trim().isEmpty()) {
                lines.add((en() ? "Rename to \"" : "标题改为「") + p.optString("title").trim()
                        + (en() ? "\"" : "」"));
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
     * 应用提案：按 id 重取最新副本再改（与 TaskEditSheet 同一纪律），touch 后落盘，
     * 开始时间变了重排 StartReminder。返回是否真的改上了。
     */
    private boolean applyProposal(ChatStore.Msg m) {
        if (taskId == null) return false;
        store.load();
        TaskItem t = store.byId(taskId);
        if (t == null) {
            toast(en() ? "That task is gone" : "这个任务已经不在了");
            return false;
        }
        try {
            JSONObject p = new JSONObject(m.proposal);
            if (p.has("title") && !p.optString("title").trim().isEmpty()) {
                t.title = p.optString("title").trim();
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
            t.touch();
            store.save();
            StartReminder.reschedule(this, store);
            toast(en() ? "Applied" : "已应用，回任务列表就能看到");
            return true;
        } catch (Exception e) {
            toast(en() ? "That suggestion could not be applied" : "这条建议应用失败，任务没动");
            return false;
        }
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
        ChatStore.save(this, histKey, msgs);
        renderMsg(userMsg);
        refreshHint();
        scrollToBottom();

        // 流式占位：先放一个空的回复视图，增量到了整段替换
        final TextView live = new TextView(this);
        live.setText("…");
        live.setTextSize(16);
        live.setTextColor(Ui.text(this));
        live.setLineSpacing(0, 1.35f);
        live.setPadding(dp(2), dp(10), dp(24), dp(6));
        msgCol.addView(live);
        scrollToBottom();
        streaming = true;
        sendBtn.setTextColor(Ui.sub(this));

        JSONArray payload;
        try {
            payload = ChatClient.toMessagesJson(buildSystemPrompt(), msgs, null);
        } catch (Exception e) {
            finishStreamError(live, en() ? "Something went wrong putting the message together"
                    : "消息没拼好，再试一次");
            return;
        }
        ChatClient.send(this, payload, new ChatClient.Callback() {
            @Override public void onModel(String modelId) {
                modelLine.setText((en() ? "Current model: " : "当前模型：") + modelId);
            }
            @Override public void onDelta(String fullText) {
                live.setText(displayPart(fullText));
                scrollToBottom();
            }
            @Override public void onDone(String fullText) {
                streaming = false;
                sendBtn.setTextColor(Ui.accent(ChatActivity.this));
                msgCol.removeView(live);
                ChatStore.Msg am = parseAssistant(fullText);
                msgs.add(am);
                trimHistory();
                ChatStore.save(ChatActivity.this, histKey, msgs);
                renderMsg(am);
                scrollToBottom();
            }
            @Override public void onError(String friendlyMsg) {
                finishStreamError(live, friendlyMsg);
            }
        });
    }

    private void finishStreamError(TextView live, String friendlyMsg) {
        streaming = false;
        sendBtn.setTextColor(Ui.accent(this));
        msgCol.removeView(live);
        toast(friendlyMsg);
    }

    /** 展示正文：提案代码块不给用户看原文，流式中出现块开头即截断显示。 */
    private static String displayPart(String fullText) {
        int i = fullText.indexOf(FENCE);
        if (i >= 0) return fullText.substring(0, i).trim();
        return fullText;
    }

    /** 流结束：剥离提案块、解析提案 JSON，组装成要落盘的助手消息。 */
    private ChatStore.Msg parseAssistant(String fullText) {
        ChatStore.Msg m = new ChatStore.Msg("assistant", "");
        String proposal = null;
        String text = fullText == null ? "" : fullText;
        int start = text.indexOf(FENCE);
        if (start >= 0) {
            int jsonStart = text.indexOf('\n', start);
            int end = jsonStart < 0 ? -1 : text.indexOf(FENCE_MARK, jsonStart);
            if (jsonStart >= 0 && end > jsonStart) {
                String json = text.substring(jsonStart + 1, end).trim();
                try {
                    new JSONObject(json); // 校验是合法 JSON 才当提案收下
                    proposal = json;
                } catch (Exception ignored) { proposal = null; }
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
        if (proposal != null && taskId != null) {
            m.proposal = proposal;
            m.propState = ChatStore.PROP_PENDING;
        }
        return m;
    }

    private void trimHistory() {
        // 历史只在本机，但也别无限涨：每路对话最多留最近 120 条
        while (msgs.size() > 120) msgs.remove(0);
    }

    private static final String FENCE_MARK = "`" + "`" + "`";
    private static final String FENCE = FENCE_MARK + "knowdo-proposal";

    // ---------------- 上下文注入 ----------------

    /** 每次发送都现拼 system：任务字段与今日已用取当下最新值，多轮聊着数据不旧。 */
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是知行 KnowDo 里的 AI 伙伴，陪用户把一个没想清楚的任务聊清楚。")
          .append("用用户说话的语言回复；语气平静、简短、具体，不说教，不用夸张的鼓励，先听他说卡在哪里，必要时只追问一句。")
          .append("用户没做好的任务，多半不是不想做，是任务本身没想清楚：太大、太糊、或者时机不对。你的活是陪他想清楚，而不是替他做。");
        if (taskId == null) {
            sb.append("\n\n这次用户没有带具体任务，想到什么聊什么。下面是他今天的任务概览，仅供你了解背景，不要主动逐条点评：\n");
            List<TaskItem> vis = store.visibleSorted();
            int n = 0;
            for (TaskItem t : vis) {
                sb.append("- ").append(t.title)
                  .append(t.isDoneToday() ? "（今天已完成）" : "（未完成）").append("\n");
                if (++n >= 20) break;
            }
            if (n == 0) sb.append("（今天还没有任务）\n");
            sb.append("这一路对话不要输出 knowdo-proposal 代码块（没有目标任务，提了也应用不了）。");
            return sb.toString();
        }
        TaskItem t = store.byId(taskId);
        sb.append("\n\n如果你想建议修改下面这个任务，在回复的最后另起一段，输出一个代码块，格式严格如下（正文里把理由讲清，不要再复述代码块内容）：\n")
          .append(FENCE).append("\n")
          .append("{\"title\":\"新标题\",\"durationMinutes\":25,\"deadline\":\"2026-10-26\",\"nextStartAt\":\"2026-10-26T09:00\",\"subtasks\":[{\"title\":\"第一步\",\"minutes\":10}]}\n")
          .append(FENCE_MARK).append("\n")
          .append("字段全部可选，只放你真要改的：title 任务标题；durationMinutes 计划时长（分钟整数）；")
          .append("deadline 截止日期 YYYY-MM-DD；nextStartAt 计划开始时间 YYYY-MM-DDTHH:mm；")
          .append("subtasks 子任务数组，给出即整体替换子任务，总时长按子任务之和重算（此时不要再同时给 durationMinutes）。")
          .append("一次回复最多给一个提案。提案只是建议：用户点「应用」才生效，点「不用」就当没提过。")
          .append("只是闲聊或追问时不要输出代码块。");
        if (t != null) {
            long usedMs = 0;
            try { usedMs = SessionLog.todayMsForTask(this, t.id); } catch (Exception ignored) { }
            int usedMin = (int) Math.round(usedMs / 60000.0);
            sb.append("\n\n当前任务：\n");
            sb.append("标题：").append(t.title).append("\n");
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
        return sb.toString();
    }

    /** 从任务编辑弹层打开带任务的对话页。 */
    public static void openForTask(Activity act, String taskId) {
        Intent i = new Intent(act, ChatActivity.class);
        i.putExtra(EXTRA_TASK_ID, taskId);
        act.startActivity(i);
    }

    /** 通用入口（不带任务）。 */
    public static void openGeneral(Activity act) {
        act.startActivity(new Intent(act, ChatActivity.class));
    }
}
