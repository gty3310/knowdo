package com.ti.tasktimer;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
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

import java.util.Calendar;

/**
 * 「今天」页（v1.85.0，底部第一签）：一天的要紧事收在一屏——晨间打卡入口
 * （v1.82.0 并页时丢掉的旧习惯页页头按钮，在此原样找回）、AI 建议（云端
 * today_advice，本机只读缓存 TodayAdvice）、今日日记（展开全文）、今日
 * 推荐原则（晨间卡今日行第一条 principle）、今天安排入口。
 * 整页单一 ScrollView，自上而下顺序即构造顺序；每次切进本签整页重画
 * （refresh），晨间卡走异步拉取、到达后补画一次。
 */
public class TodayPage {

    private final MainActivity act;
    private final ViewGroup container;
    private FrameLayout root;

    // 晨间卡（推荐原则）异步状态：按 session 日拉一次，到达后重画
    private String recForSession = "";
    private boolean recLoading;
    private String recPrincipleId = "", recPrincipleTitle = "", recPrincipleLine = "";

    // 「给 AI 回复」展开状态（重画不丢）
    private boolean replyOpen;
    private String replyDraft = "";

    public TodayPage(MainActivity act, ViewGroup container) {
        this.act = act;
        this.container = container;
        render();
    }

    public void refresh() { render(); }

    // ---------------- 骨架 ----------------

    private FrameLayout ensureRoot() {
        if (root == null) {
            root = new FrameLayout(act);
            root.setBackgroundColor(Ui.bg(act));
            container.removeAllViews();
            container.addView(root, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        return root;
    }

    private void render() {
        FrameLayout r = ensureRoot();
        r.removeAllViews();
        JSONObject habitRoot = HabitData.load(act);
        final String logicToday = Rollover.logicalToday(act);
        final String session = HabitData.sessionDate(habitRoot);

        ScrollView scroll = new ScrollView(act);
        scroll.setBackgroundColor(Ui.bg(act));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(16), dp(20), dp(28));
        scroll.addView(col);
        r.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // 页头：日期行＋标题
        TextView dateLine = tv(dateLabel(logicToday), 12.5f, subC());
        dateLine.setLetterSpacing(0.12f);
        col.addView(dateLine);
        TextView title = new TextView(act);
        title.setText(s("今天", "Today"));
        title.setTextSize(27);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(inkC());
        title.setPadding(0, dp(4), 0, 0);
        col.addView(title);

        col.addView(morningCard(habitRoot, session));
        View advice = adviceCard(logicToday);
        if (advice != null) col.addView(advice);
        col.addView(diaryCard(habitRoot, logicToday));
        View rec = principleCard();
        if (rec != null) col.addView(rec);
        col.addView(planButton());
        maybeLoadMorningCard(session);
    }

    private String dateLabel(String date) {
        try {
            Calendar cal = DayUtil.parse(date);
            if (Strings.en()) {
                return new java.text.SimpleDateFormat("MMM d · EEE", java.util.Locale.US)
                        .format(cal.getTime());
            }
            String[] wd = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            int idxMon0 = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            return (cal.get(Calendar.MONTH) + 1) + " 月 " + cal.get(Calendar.DAY_OF_MONTH)
                    + " 日 · " + wd[idxMon0];
        } catch (Exception e) {
            return date;
        }
    }

    // ---------------- 1. 晨间打卡卡 ----------------

    private View morningCard(JSONObject habitRoot, final String session) {
        boolean done = HabitData.morningDone(habitRoot, session);
        LinearLayout card = cardBox();
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(act);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(label(s("每日习惯", "Daily habits")));
        TextView t = new TextView(act);
        t.setText(s("晨间打卡", "Morning check-in"));
        t.setTextSize(19);
        t.setTypeface(Typeface.SERIF);
        t.setTextColor(inkC());
        t.setPadding(0, dp(5), 0, 0);
        left.addView(t);
        TextView sub = tv(s("昨天的习惯与日记，在晨间页里填",
                "Yesterday's habits and diary live on the morning page"), 12.5f, subC());
        sub.setPadding(0, dp(4), 0, 0);
        left.addView(sub);
        row.addView(left, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView chip = new TextView(act);
        chip.setText(done ? s("今日已填", "Done today") : s("今日未填", "Not yet today"));
        chip.setTextSize(12);
        chip.setPadding(dp(11), dp(5), dp(11), dp(5));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(999));
        g.setStroke(dp(1), done ? Ui.line(act) : redC());
        chip.setTextColor(done ? subC() : redC());
        chip.setBackground(g);
        row.addView(chip);
        card.addView(row);
        // 整卡点击＝旧习惯页页头按钮的原跳转：晨间页编辑模式
        card.setOnClickListener(v -> {
            Intent i = new Intent(act, MorningActivity.class);
            i.putExtra(MorningActivity.EXTRA_MODE, MorningActivity.MODE_EDIT);
            act.startActivity(i);
        });
        return card;
    }

    // ---------------- 2. AI 建议卡 ----------------

    private View adviceCard(final String logicToday) {
        final JSONObject advice = TodayAdvice.load(act);
        if (advice == null) return null;
        if (!logicToday.equals(TodayAdvice.date(advice))) return null; // 不是今天的整卡不显示
        final String text = TodayAdvice.text(advice);
        if (text.isEmpty()) return null;

        LinearLayout card = cardBox();
        // 标题行：左标题＋更新时间，右「给 AI 回复」小钮
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        String hm = TodayAdvice.updatedHm(advice);
        TextView lab = label(s("AI 建议", "AI advice")
                + (hm.isEmpty() ? "" : s(" · " + hm + " 更新", " · updated " + hm)));
        head.addView(lab, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView replyBtn = new TextView(act);
        replyBtn.setText(s("给 AI 回复", "Reply to AI"));
        replyBtn.setTextSize(12);
        replyBtn.setTextColor(subC());
        replyBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable rg = new GradientDrawable();
        rg.setCornerRadius(dp(999));
        rg.setStroke(dp(1), Ui.line(act));
        replyBtn.setBackground(rg);
        head.addView(replyBtn);
        card.addView(head);

        // 正文：引用了原则时，原则名第一次出现处渲染为朱砂可点（点开走
        // MainActivity.openZhiPrinciple，与合一页跳原则同一条路径）
        TextView body = new TextView(act);
        body.setTextSize(14);
        body.setTextColor(inkC());
        body.setLineSpacing(0, 1.55f);
        body.setPadding(0, dp(9), 0, 0);
        final String pid = TodayAdvice.principleId(advice);
        String pTitle = "";
        if (pid != null) {
            for (ZhiPage.Principle p : ZhiPage.principlesCache(act)) {
                if (pid.equals(p.id)) { pTitle = p.title == null ? "" : p.title.trim(); break; }
            }
        }
        int at = pTitle.isEmpty() ? -1 : text.indexOf(pTitle);
        if (at >= 0) {
            SpannableString ss = new SpannableString(text);
            final String fPid = pid;
            ss.setSpan(new ClickableSpan() {
                @Override public void onClick(View w) { act.openZhiPrinciple(fPid); }
                @Override public void updateDrawState(android.text.TextPaint ds) {
                    ds.setColor(redC());
                    ds.setUnderlineText(true);
                }
            }, at, at + pTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            body.setText(ss);
            body.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            body.setText(text); // 匹配不到就不渲染链接、不报错
        }
        card.addView(body);

        // 主推任务小卡（已删/已完成不挂）
        String tid = TodayAdvice.taskId(advice);
        if (tid != null) {
            TaskItem t = new TaskStore(act).byId(tid);
            if (t != null && !t.deleted && !"done".equals(t.status)
                    && !t.doneDates.contains(logicToday)) {
                card.addView(taskMiniCard(t));
            }
        }

        // 回复区：「给 AI 回复」点开后在卡下就地展开（不跳页）
        final LinearLayout replyZone = new LinearLayout(act);
        replyZone.setOrientation(LinearLayout.VERTICAL);
        replyZone.setVisibility(replyOpen ? View.VISIBLE : View.GONE);
        final EditText in = new EditText(act);
        in.setText(replyDraft);
        in.setTextSize(14);
        in.setTextColor(inkC());
        in.setHint(s("接着这条建议说", "Say it here, following the advice"));
        in.setHintTextColor(subC());
        in.setMinLines(2);
        in.setGravity(Gravity.TOP | Gravity.START);
        in.setBackground(roundBg(cardBg(), Ui.line(act), 12));
        in.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inLp.topMargin = dp(10);
        replyZone.addView(in, inLp);
        TextView send = new TextView(act);
        send.setText(s("发送", "Send"));
        send.setTextSize(13.5f);
        send.setTextColor(Color.WHITE);
        send.setGravity(Gravity.CENTER);
        send.setPadding(0, dp(9), 0, dp(9));
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(redC());
        sg.setCornerRadius(dp(10));
        send.setBackground(sg);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(8);
        replyZone.addView(send, sLp);
        card.addView(replyZone);
        in.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int n) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int n) {
                replyDraft = cs == null ? "" : cs.toString();
            }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });
        replyBtn.setOnClickListener(v -> {
            replyOpen = !replyOpen;
            replyZone.setVisibility(replyOpen ? View.VISIBLE : View.GONE);
            if (replyOpen) in.requestFocus();
        });
        send.setOnClickListener(v -> {
            String reply = in.getText() == null ? "" : in.getText().toString().trim();
            if (reply.isEmpty()) {
                Toast.makeText(act, s("先写一句回复", "Write a reply first"),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            // 建议正文＋用户回复拼成一段投递（AiReplyFill：填入对方输入框，
            // 失败退回剪贴板；发送钮不自动点，用户自己点发送）
            String composed = text + "\n\n" + reply;
            boolean launched = AiReplyFill.send(act, composed);
            Toast.makeText(act, launched
                    ? s("已打开 Muse：填好了就点发送，没填上长按粘贴",
                        "Muse is open: send it if filled, long-press to paste if not")
                    : s("没找到 Muse，文字已复制，长按粘贴即可",
                        "Muse not found — the text is copied, just paste it"),
                    Toast.LENGTH_LONG).show();
            if (launched) { replyOpen = false; replyDraft = ""; }
        });
        return card;
    }

    private View taskMiniCard(final TaskItem t) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(roundBg(Ui.bg(act), Ui.line(act), 12));
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(11);
        box.setLayoutParams(lp);
        LinearLayout left = new LinearLayout(act);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(act);
        name.setText(t.title);
        name.setTextSize(14);
        name.setTextColor(inkC());
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        left.addView(name);
        StringBuilder meta = new StringBuilder(priorityLabel(t.priority));
        if (t.durationMinutes > 0) {
            meta.append(s(" · ", " · ")).append(t.durationMinutes)
                    .append(s(" 分钟", " min"));
        }
        TextView m = tv(meta.toString(), 11.5f, subC());
        m.setPadding(0, dp(2), 0, 0);
        left.addView(m);
        box.addView(left, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView start = new TextView(act);
        start.setText("▶");
        start.setTextSize(13);
        start.setTextColor(Color.WHITE);
        start.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(redC());
        start.setBackground(g);
        // 点开始钮＝行页开始的同一条路径（MainActivity.startTaskNow）
        start.setOnClickListener(v -> act.startTaskFromToday(t));
        box.addView(start, new LinearLayout.LayoutParams(dp(34), dp(34)));
        return box;
    }

    private String priorityLabel(String p) {
        if ("must".equals(p)) return Strings.priMust();
        if ("important".equals(p)) return Strings.priImportant();
        if ("leisure".equals(p)) return Strings.priLeisure();
        return Strings.priSecondary();
    }

    // ---------------- 3. 今日日记卡 ----------------

    private View diaryCard(JSONObject habitRoot, final String logicToday) {
        String text = HabitData.diary(habitRoot, logicToday);
        LinearLayout card = cardBox();
        card.addView(label(s("今天的日记", "Today's diary")));
        TextView body = new TextView(act);
        if (text == null || text.trim().isEmpty()) {
            body.setText(s("还没写，点开写今天的日记", "Nothing yet — tap to write today's diary"));
            body.setTextColor(subC());
            body.setTextSize(13);
        } else {
            body.setText(text);
            body.setTextColor(inkC());
            body.setTextSize(14.5f);
            body.setTypeface(Typeface.SERIF);
            body.setLineSpacing(0, 1.6f);
        }
        body.setPadding(0, dp(9), 0, 0);
        card.addView(body);
        // 点卡进日记编辑：复用察组日记详情编辑入口（同一份 HabitData.diary 数据）
        card.setOnClickListener(v -> act.openZhiDiary(logicToday));
        return card;
    }

    // ---------------- 4. 今日推荐原则卡 ----------------

    private View principleCard() {
        if (recPrincipleId.isEmpty()) return null;
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setBackground(roundBg(cardBg(), Ui.line(act), 16));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(14);
        wrap.setLayoutParams(wlp);
        View bar = new View(act);
        bar.setBackgroundColor(redC());
        wrap.addView(bar, new LinearLayout.LayoutParams(dp(3),
                LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout inner = new LinearLayout(act);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(14), dp(14), dp(16), dp(13));
        inner.addView(label(s("今日推荐 · 原则", "Today's principle")));
        TextView name = new TextView(act);
        name.setText(recPrincipleTitle);
        name.setTextSize(20);
        name.setTypeface(Typeface.SERIF);
        name.setTextColor(inkC());
        name.setPadding(0, dp(7), 0, 0);
        inner.addView(name);
        if (!recPrincipleLine.isEmpty()) {
            TextView one = tv(recPrincipleLine, 13, subC());
            one.setLineSpacing(0, 1.5f);
            one.setPadding(0, dp(5), 0, 0);
            inner.addView(one);
        }
        wrap.addView(inner, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final String pid = recPrincipleId;
        wrap.setOnClickListener(v -> act.openZhiPrinciple(pid));
        return wrap;
    }

    /** 拉晨间卡今日行（与晨间页同一张表、同一个 session 日口径），取第一条
     *  principle 条目（kind=act/think）；无卡/无条目整卡不显示。 */
    private void maybeLoadMorningCard(final String session) {
        if (session.equals(recForSession) || recLoading) return;
        if (!Supa.isLoggedIn(act)) return;
        recLoading = true;
        new Thread(() -> {
            String pid = "", pTitle = "", pLine = "";
            try {
                JSONArray rows = Supa.restGet(act,
                        "morning_cards?date=eq." + session + "&select=items");
                if (rows != null && rows.length() > 0) {
                    JSONArray items = rows.optJSONObject(0) == null ? null
                            : rows.optJSONObject(0).optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject it = items.optJSONObject(i);
                            if (it == null) continue;
                            String kind = it.optString("kind", "");
                            if (!"act".equals(kind) && !"think".equals(kind)) continue;
                            String eid = it.optString("entry_id", "");
                            String ttl = it.optString("title", "").trim();
                            if (eid.isEmpty() || ttl.isEmpty()) continue;
                            pid = eid; pTitle = ttl;
                            pLine = it.optString("body", "").trim();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) { }
            final String fPid = pid, fTitle = pTitle, fLine = pLine;
            act.runOnUiThread(() -> {
                recLoading = false;
                recForSession = session;
                recPrincipleId = fPid;
                recPrincipleTitle = fTitle;
                recPrincipleLine = fLine;
                if (!fPid.isEmpty()) render();
            });
        }).start();
    }

    // ---------------- 5. 今天安排按钮 ----------------

    private View planButton() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(inkC());
        g.setCornerRadius(dp(16));
        box.setBackground(g);
        box.setPadding(dp(16), dp(15), dp(16), dp(15));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        box.setLayoutParams(lp);
        LinearLayout left = new LinearLayout(act);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(act);
        t.setText(s("今天的安排", "Today's plan"));
        t.setTextSize(18);
        t.setTypeface(Typeface.SERIF);
        t.setTextColor(Ui.bg(act));
        left.addView(t);
        TextView sub = new TextView(act);
        sub.setText(s("点开去排今天的日程", "Tap to arrange today's schedule"));
        sub.setTextSize(12);
        sub.setTextColor(Ui.bg(act));
        sub.setAlpha(0.65f);
        sub.setPadding(0, dp(3), 0, 0);
        left.addView(sub);
        box.addView(left, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView arr = new TextView(act);
        arr.setText("→");
        arr.setTextSize(19);
        arr.setTextColor(Ui.bg(act));
        arr.setAlpha(0.85f);
        box.addView(arr);
        box.setOnClickListener(v -> act.startActivity(new Intent(act, TodayPlanActivity.class)));
        return box;
    }

    // ---------------- 样式帮手 ----------------

    private LinearLayout cardBox() {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundBg(cardBg(), Ui.line(act), 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        card.setLayoutParams(lp);
        return card;
    }

    private int cardBg() {
        return Ui.dark(act) ? Ui.surface(act) : Color.rgb(255, 253, 248);
    }

    private GradientDrawable roundBg(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView label(String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(11.5f);
        t.setTextColor(subC());
        t.setLetterSpacing(0.14f);
        return t;
    }

    private TextView tv(String text, float sizeSp, int color) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) { return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }
    private int inkC() { return Ui.text(act); }
    private int subC() { return Ui.sub(act); }
    private int redC() { return Ui.accent(act); }
}
