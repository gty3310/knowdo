package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.Transliterator;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * 知页（v1.59.0 大改版，底部第二签）：「知」分三页——原则（zhi_principles，
 * kind 分思维 think／行为 act）、问题（zhi_questions，status 待问 pending／已问
 * asked）、人名（zhi_people）；三类条目经 zhi_links 两两关联（关联对按类型名
 * 字母序存规范形：person &lt; principle &lt; question，'diary' 本版不用）。
 * 每页同一套工具：搜索（字面包含）、标签多选筛选（OR）、复制筛选结果、卡片/列表
 * 双模式（每页各自记住）；人名列表模式按拼音首字母分组＋右侧字母栏。
 * 编辑页三类同骨架：上半字段面板、下半关联区；加关联是输入联想（字面包含过滤
 * 已有条目，点一条立即写 zhi_links），也可新建并关联；关联增删立即落库，字段经
 * 「保存」按钮 upsert。数据全走 Supa REST（用户 JWT，RLS 只见自己的）；本地缓存
 * 四表原始 JSON 于 SharedPreferences，拉取失败展示缓存并提示离线。
 * 旧表 zhi_entries 本版起不再读写（表本身保留不动）。
 */
public final class ZhiPage {
    // 条目类型（与 zhi_links 的 *_type 取值一致）
    static final String T_PRINCIPLE = "principle";
    static final String T_QUESTION = "question";
    static final String T_PERSON = "person";

    private static final int PAGE_PRINCIPLE = 0;
    private static final int PAGE_QUESTION = 1;
    private static final int PAGE_PERSON = 2;
    private static final String[] PAGE_NAMES = {"原则", "问题", "人名"};
    private static final String[] PAGE_NAMES_EN = {"Principles", "Questions", "People"};

    private static final String PREF = "zhi_cache";
    private static final String MODE_PREF = "zhi2_mode";

    // ---------------- 模型 ----------------
    static final class Principle {
        String id, kind = "think", title = "", body = "";
        List<String> tags = new ArrayList<>();
    }
    static final class Question {
        String id, title = "", body = "", status = "pending";
        List<String> tags = new ArrayList<>();
    }
    static final class Person {
        String id, name = "", memo = "";
        List<String> tags = new ArrayList<>();
    }
    static final class Link {
        String aType, aId, bType, bId;
        boolean involves(String type, String id) {
            return (aType.equals(type) && aId.equals(id)) || (bType.equals(type) && bId.equals(id));
        }
        String otherType(String type, String id) {
            return (aType.equals(type) && aId.equals(id)) ? bType : aType;
        }
        String otherId(String type, String id) {
            return (aType.equals(type) && aId.equals(id)) ? bId : aId;
        }
    }
    /** 编辑导航栈的一项；id 为 null 表示尚未保存的新条目。 */
    static final class Ref {
        final String type;
        String id;
        Ref(String type, String id) { this.type = type; this.id = id; }
    }

    private final Activity act;
    private final ViewGroup container;
    private final List<Principle> principles = new ArrayList<>();
    private final List<Question> questions = new ArrayList<>();
    private final List<Person> people = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private boolean offline;
    private boolean fetchFailed;

    // 列表状态（三页各自一份）
    private int page = PAGE_PRINCIPLE;
    private final String[] queries = {"", "", ""};
    private final List<Set<String>> tagSel = new ArrayList<>();
    private FrameLayout root;
    private LinearLayout resultsCol;
    private ScrollView listScroll;
    private LinearLayout rail;
    private View fab;
    private TextView countView;
    private final Map<String, View> groupHeads = new LinkedHashMap<>();

    // 编辑状态
    private boolean editing;
    private final List<Ref> editStack = new ArrayList<>();
    private EditState es;

    public ZhiPage(Activity act, ViewGroup container) {
        this.act = act;
        this.container = container;
        for (int i = 0; i < 3; i++) tagSel.add(new HashSet<>());
        loadCache();
    }

    // ---------------- 小工具 ----------------
    private int dp(int v) { return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }
    private void toast(String t) { Toast.makeText(act, t, Toast.LENGTH_SHORT).show(); }

    private int cardC() { return Ui.dark(act) ? Ui.surface(act) : Color.rgb(255, 253, 247); }
    private int washC() { return Ui.muted(act); }
    private int lineC() { return Ui.line(act); }
    private int inkC() { return Ui.text(act); }
    private int subC() { return Ui.sub(act); }
    private int redC() { return Ui.accent(act); }
    private int weakC() { return Ui.dark(act) ? Ui.sub(act) : Color.rgb(154, 167, 159); }

    private GradientDrawable roundBg(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }
    private GradientDrawable circleBg(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private TextView tv(String text, float size, int color) {
        TextView v = new TextView(act);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }
    private TextView serif(String text, float size, int color, boolean bold) {
        TextView v = tv(text, size, color);
        v.setTypeface(Typeface.SERIF, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }
    private static String nz(String v) { return v == null ? "" : v; }
    private static String firstLine(String body) {
        String b = nz(body).trim();
        int nl = b.indexOf('\n');
        return nl >= 0 ? b.substring(0, nl).trim() : b;
    }
    private static String oneLine(String body) {
        return nz(body).replace('\n', ' ').trim();
    }
    private static String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
    private static List<String> parseTags(JSONObject o) {
        List<String> out = new ArrayList<>();
        JSONArray a = o.optJSONArray("tags");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            String t = a.optString(i, "").trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }
    private static JSONArray tagsJson(List<String> tags) {
        JSONArray a = new JSONArray();
        for (String t : tags) a.put(t);
        return a;
    }

    // ---------------- 数据：拉取与缓存 ----------------
    public void refresh() {
        if (!Supa.isLoggedIn(act)) { renderLoginHint(); return; }
        fetchFailed = false;
        renderCurrent();          // 先画缓存（或空区），不白屏
        fetch();
    }

    private void renderCurrent() {
        if (editing && es != null) renderEdit();
        else renderList();
    }

    private void fetch() {
        new Thread(() -> {
            try {
                JSONArray pRows = Supa.restGet(act, "zhi_principles?select=*&order=created_at.asc");
                JSONArray qRows = Supa.restGet(act, "zhi_questions?select=*&order=created_at.asc");
                JSONArray peRows = Supa.restGet(act, "zhi_people?select=*&order=created_at.asc");
                JSONArray lRows = Supa.restGet(act, "zhi_links?select=*&order=created_at.asc");
                act.runOnUiThread(() -> {
                    applyRows(pRows, qRows, peRows, lRows);
                    offline = false;
                    fetchFailed = false;
                    saveCache(pRows, qRows, peRows, lRows);
                    if (!editing) renderList();   // 编辑中不打断未保存的字段，数据静默更新
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> {
                    boolean have = !principles.isEmpty() || !questions.isEmpty() || !people.isEmpty();
                    if (have) { offline = true; if (!editing) renderList(); }
                    else { fetchFailed = true; renderList(); }
                });
            }
        }).start();
    }

    private void applyRows(JSONArray pRows, JSONArray qRows, JSONArray peRows, JSONArray lRows) {
        try {
            principles.clear();
            for (int i = 0; i < pRows.length(); i++) {
                JSONObject o = pRows.getJSONObject(i);
                Principle p = new Principle();
                p.id = o.optString("id", "");
                p.kind = o.optString("kind", "think");
                p.title = nz(o.optString("title", ""));
                p.body = nz(o.optString("body", ""));
                p.tags = parseTags(o);
                principles.add(p);
            }
            questions.clear();
            for (int i = 0; i < qRows.length(); i++) {
                JSONObject o = qRows.getJSONObject(i);
                Question q = new Question();
                q.id = o.optString("id", "");
                q.title = nz(o.optString("title", ""));
                q.body = nz(o.optString("body", ""));
                q.status = o.optString("status", "pending");
                q.tags = parseTags(o);
                questions.add(q);
            }
            people.clear();
            for (int i = 0; i < peRows.length(); i++) {
                JSONObject o = peRows.getJSONObject(i);
                Person pe = new Person();
                pe.id = o.optString("id", "");
                pe.name = nz(o.optString("name", ""));
                pe.memo = nz(o.optString("memo", ""));
                pe.tags = parseTags(o);
                people.add(pe);
            }
            links.clear();
            for (int i = 0; i < lRows.length(); i++) {
                JSONObject o = lRows.getJSONObject(i);
                Link l = new Link();
                l.aType = o.optString("a_type", "");
                l.aId = o.optString("a_id", "");
                l.bType = o.optString("b_type", "");
                l.bId = o.optString("b_id", "");
                links.add(l);
            }
        } catch (Exception ignored) { }
    }

    private void loadCache() {
        try {
            SharedPreferences sp = act.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String p = sp.getString("principles", null), q = sp.getString("questions", null);
            String pe = sp.getString("people", null), l = sp.getString("links", null);
            if (p == null) return;
            applyRows(new JSONArray(p), new JSONArray(q == null ? "[]" : q),
                    new JSONArray(pe == null ? "[]" : pe), new JSONArray(l == null ? "[]" : l));
        } catch (Exception ignored) { }
    }

    private void saveCache() {
        try {
            JSONArray pa = new JSONArray(), qa = new JSONArray(), pea = new JSONArray(), la = new JSONArray();
            for (Principle p : principles) {
                JSONObject o = new JSONObject();
                o.put("id", p.id); o.put("kind", p.kind); o.put("title", p.title);
                o.put("body", p.body); o.put("tags", tagsJson(p.tags));
                pa.put(o);
            }
            for (Question q : questions) {
                JSONObject o = new JSONObject();
                o.put("id", q.id); o.put("title", q.title); o.put("body", q.body);
                o.put("status", q.status); o.put("tags", tagsJson(q.tags));
                qa.put(o);
            }
            for (Person pe : people) {
                JSONObject o = new JSONObject();
                o.put("id", pe.id); o.put("name", pe.name); o.put("memo", pe.memo);
                o.put("tags", tagsJson(pe.tags));
                pea.put(o);
            }
            for (Link l : links) {
                JSONObject o = new JSONObject();
                o.put("a_type", l.aType); o.put("a_id", l.aId);
                o.put("b_type", l.bType); o.put("b_id", l.bId);
                la.put(o);
            }
            saveCacheRaw(pa.toString(), qa.toString(), pea.toString(), la.toString());
        } catch (Exception ignored) { }
    }

    private void saveCache(JSONArray p, JSONArray q, JSONArray pe, JSONArray l) {
        saveCacheRaw(p.toString(), q.toString(), pe.toString(), l.toString());
    }

    private void saveCacheRaw(String p, String q, String pe, String l) {
        act.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("principles", p).putString("questions", q)
                .putString("people", pe).putString("links", l).apply();
    }

    // ---------------- 关联查询 ----------------
    private List<Link> linksOf(String type, String id) {
        List<Link> out = new ArrayList<>();
        for (Link l : links) if (l.involves(type, id)) out.add(l);
        return out;
    }
    /** 与 (type,id) 相连、且对端类型为 otherType 的对端 id 列表。 */
    private List<String> linkedIds(String type, String id, String otherType) {
        List<String> out = new ArrayList<>();
        for (Link l : links) {
            if (!l.involves(type, id)) continue;
            if (l.otherType(type, id).equals(otherType)) out.add(l.otherId(type, id));
        }
        return out;
    }
    private boolean isLinked(String t1, String id1, String t2, String id2) {
        for (Link l : links) {
            if (l.involves(t1, id1) && l.involves(t2, id2)) return true;
        }
        return false;
    }
    private Principle principleById(String id) {
        for (Principle p : principles) if (p.id.equals(id)) return p;
        return null;
    }
    private Question questionById(String id) {
        for (Question q : questions) if (q.id.equals(id)) return q;
        return null;
    }
    private Person personById(String id) {
        for (Person pe : people) if (pe.id.equals(id)) return pe;
        return null;
    }
    private String displayOf(String type, String id) {
        if (T_PRINCIPLE.equals(type)) { Principle p = principleById(id); return p == null ? "" : displayOf(p); }
        if (T_QUESTION.equals(type)) { Question q = questionById(id); return q == null ? "" : displayOf(q); }
        Person pe = personById(id);
        return pe == null ? "" : pe.name;
    }
    private static String displayOf(Principle p) {
        return !p.title.trim().isEmpty() ? p.title.trim() : firstLine(p.body);
    }
    private static String displayOf(Question q) {
        return !q.title.trim().isEmpty() ? q.title.trim() : firstLine(q.body);
    }
    /** 全部标签（三页通用标签池：三表标签的并集，保持出现序）。 */
    private List<String> allTags() {
        List<String> out = new ArrayList<>();
        for (Principle p : principles) for (String t : p.tags) if (!out.contains(t)) out.add(t);
        for (Question q : questions) for (String t : q.tags) if (!out.contains(t)) out.add(t);
        for (Person pe : people) for (String t : pe.tags) if (!out.contains(t)) out.add(t);
        return out;
    }
    private List<String> pageTags() {
        List<String> out = new ArrayList<>();
        if (page == PAGE_PRINCIPLE) {
            for (Principle p : principles) for (String tg : p.tags) if (!out.contains(tg)) out.add(tg);
        } else if (page == PAGE_QUESTION) {
            for (Question q : questions) for (String tg : q.tags) if (!out.contains(tg)) out.add(tg);
        } else {
            for (Person pe : people) for (String tg : pe.tags) if (!out.contains(tg)) out.add(tg);
        }
        return out;
    }

    // ---------------- 写库（关联立即生效；字段经保存 upsert） ----------------
    private interface Done { void ok(); }
    private interface DoneVal<T> { void ok(T v); }

    private void addLink(final String t1, final String id1, final String t2, final String id2,
                         final Done done) {
        // 规范形：类型名字母序在前（person < principle < question）
        final String aT, aI, bT, bI;
        if (t1.compareTo(t2) <= 0) { aT = t1; aI = id1; bT = t2; bI = id2; }
        else { aT = t2; aI = id2; bT = t1; bI = id1; }
        new Thread(() -> {
            try {
                JSONObject row = new JSONObject();
                row.put("id", UUID.randomUUID().toString());
                row.put("user_id", Supa.userId(act));
                row.put("a_type", aT); row.put("a_id", aI);
                row.put("b_type", bT); row.put("b_id", bI);
                row.put("created_at", isoNow());
                Supa.restUpsertOne(act, "zhi_links", "user_id,a_type,a_id,b_type,b_id", row);
                act.runOnUiThread(() -> {
                    Link l = new Link();
                    l.aType = aT; l.aId = aI; l.bType = bT; l.bId = bI;
                    links.add(l);
                    saveCache();
                    if (done != null) done.ok();
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> toast(s("没关联上，再试一次", "Couldn't link — try again")));
            }
        }).start();
    }

    private void removeLink(final Link l, final Done done) {
        new Thread(() -> {
            try {
                Supa.restDelete(act, "zhi_links?a_type=eq." + l.aType + "&a_id=eq." + l.aId
                        + "&b_type=eq." + l.bType + "&b_id=eq." + l.bId);
                act.runOnUiThread(() -> {
                    links.remove(l);
                    saveCache();
                    if (done != null) done.ok();
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> toast(s("没解除，再试一次", "Couldn't unlink — try again")));
            }
        }).start();
    }

    private void deleteItem(final String type, final String id, final Done done) {
        final String table = T_PRINCIPLE.equals(type) ? "zhi_principles"
                : T_QUESTION.equals(type) ? "zhi_questions" : "zhi_people";
        new Thread(() -> {
            try {
                Supa.restDelete(act, "zhi_links?a_type=eq." + type + "&a_id=eq." + id);
                Supa.restDelete(act, "zhi_links?b_type=eq." + type + "&b_id=eq." + id);
                Supa.restDelete(act, table + "?id=eq." + id);
                act.runOnUiThread(() -> {
                    links.removeIf(l -> l.involves(type, id));
                    if (T_PRINCIPLE.equals(type)) principles.removeIf(p -> p.id.equals(id));
                    else if (T_QUESTION.equals(type)) questions.removeIf(q -> q.id.equals(id));
                    else people.removeIf(pe -> pe.id.equals(id));
                    saveCache();
                    if (done != null) done.ok();
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> toast(s("没删掉，再试一次", "Couldn't delete — try again")));
            }
        }).start();
    }

    // ---------------- 列表页 ----------------
    private FrameLayout ensureRoot() {
        if (root == null) {
            root = new FrameLayout(act);
            root.setBackgroundColor(Ui.bg(act));
            container.removeAllViews();
            container.addView(root, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        return root;
    }

    private void renderLoginHint() {
        FrameLayout r = ensureRoot();
        r.removeAllViews();
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(24));
        col.addView(serif(s("知", "Know"), 23, inkC(), true));
        TextView hint = tv(s("先登录，这里才有你的知", "Sign in first — your principles and questions live here"), 15, inkC());
        hint.setPadding(0, dp(22), 0, 0);
        col.addView(hint);
        TextView sub = tv(s("到日程页右上角的设置里登录你的账号", "Sign in from Settings on the Schedule page"), 12.5f, subC());
        sub.setPadding(0, dp(6), 0, 0);
        col.addView(sub);
        r.addView(col);
    }

    private String modeOf(int p) {
        return act.getSharedPreferences(MODE_PREF, Context.MODE_PRIVATE)
                .getString("page" + p, "card");
    }
    private void setModeOf(int p, String m) {
        act.getSharedPreferences(MODE_PREF, Context.MODE_PRIVATE).edit()
                .putString("page" + p, m).apply();
    }

    private void renderList() {
        editing = false;
        FrameLayout r = ensureRoot();
        r.removeAllViews();
        groupHeads.clear();

        ScrollView scroll = new ScrollView(act);
        scroll.setBackgroundColor(Ui.bg(act));
        listScroll = scroll;
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(90));
        scroll.addView(col);
        r.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        col.addView(serif(s("知", "Know"), 23, inkC(), true));
        TextView sub = tv(s("想明白的、要照着做的、还没问出口的，都放在这里",
                "What you have figured out, what you mean to live by, and what you still want to ask"), 12, subC());
        sub.setPadding(0, dp(4), 0, 0);
        col.addView(sub);

        if (fetchFailed) {
            TextView t = tv(s("连不上，暂时看不到你的知", "Can't connect — your entries are out of reach for now"), 15, inkC());
            t.setPadding(0, dp(22), 0, 0);
            col.addView(t);
            TextView retry = tv(s("重试", "Retry"), 14.5f, redC());
            retry.setPadding(0, dp(12), 0, 0);
            retry.setOnClickListener(v -> refresh());
            col.addView(retry);
            return;
        }
        if (offline) {
            TextView off = tv(s("离线，显示上次的", "Offline — showing the last copy"), 12, redC());
            off.setPadding(0, dp(6), 0, 0);
            col.addView(off);
        }

        // 三段切换：原则 / 问题 / 人名
        LinearLayout seg = new LinearLayout(act);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(roundBg(washC(), 0, 9));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        segLp.topMargin = dp(14);
        col.addView(seg, segLp);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = tv(s(PAGE_NAMES[i], PAGE_NAMES_EN[i]), 13, idx == page ? inkC() : subC());
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(7), 0, dp(7));
            if (idx == page) {
                t.setBackground(roundBg(cardC(), 0, 7));
                t.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
            }
            t.setOnClickListener(v -> { page = idx; renderList(); });
            seg.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }

        // 搜索（输字只重画结果区，不丢焦点）
        final EditText search = new EditText(act);
        search.setHint(s("搜索" + PAGE_NAMES[page] + "…", "Search…"));
        search.setText(queries[page]);
        search.setTextSize(13.5f);
        search.setSingleLine(true);
        search.setBackground(roundBg(cardC(), lineC(), 10));
        search.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(12);
        col.addView(search, sLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                queries[page] = e.toString();
                renderResults();
            }
        });

        // 标签筛选（多选，OR）
        List<String> ptags = pageTags();
        if (!ptags.isEmpty()) {
            FlowLayout flow = new FlowLayout(act, dp(6), dp(6));
            LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            fLp.topMargin = dp(10);
            col.addView(flow, fLp);
            for (final String tag : ptags) {
                boolean on = tagSel.get(page).contains(tag);
                TextView chip = tv(tag, 11.5f, on ? cardC() : subC());
                chip.setPadding(dp(11), dp(3), dp(11), dp(3));
                chip.setBackground(roundBg(on ? inkC() : Color.TRANSPARENT, on ? 0 : weakC(), 20));
                chip.setOnClickListener(v -> {
                    if (tagSel.get(page).contains(tag)) tagSel.get(page).remove(tag);
                    else tagSel.get(page).add(tag);
                    renderList();
                });
                flow.addView(chip);
            }
        }

        // 工具行：计数｜复制｜模式切换
        LinearLayout tools = new LinearLayout(act);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = dp(12);
        tLp.bottomMargin = dp(4);
        col.addView(tools, tLp);
        countView = tv("", 11.5f, weakC());
        tools.addView(countView);
        View spacer = new View(act);
        tools.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        TextView copy = tv(s("复制", "Copy"), 12, inkC());
        copy.setPadding(dp(12), dp(5), dp(12), dp(5));
        copy.setBackground(roundBg(cardC(), subC(), 8));
        copy.setOnClickListener(v -> copyFiltered());
        tools.addView(copy);
        LinearLayout mode = new LinearLayout(act);
        mode.setBackground(roundBg(washC(), 0, 7));
        mode.setPadding(dp(2), dp(2), dp(2), dp(2));
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mLp.leftMargin = dp(8);
        tools.addView(mode, mLp);
        String cur = modeOf(page);
        for (final String m : new String[]{"card", "list"}) {
            boolean on = m.equals(cur);
            TextView mt = tv(m.equals("card") ? s("卡片", "Cards") : s("列表", "List"), 11.5f, on ? inkC() : subC());
            mt.setPadding(dp(10), dp(4), dp(10), dp(4));
            if (on) {
                mt.setBackground(roundBg(cardC(), 0, 5));
                mt.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
            }
            mt.setOnClickListener(v -> { setModeOf(page, m); renderList(); });
            mode.addView(mt);
        }

        resultsCol = new LinearLayout(act);
        resultsCol.setOrientation(LinearLayout.VERTICAL);
        col.addView(resultsCol);

        // 人名字母栏（仅人名页列表模式，由 renderResults 按分组填充/显隐）
        rail = new LinearLayout(act);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams rLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        rLp.rightMargin = dp(2);
        r.addView(rail, rLp);

        // 右下红色 ＋ 新建
        TextView fabBtn = tv("＋", 24, Color.WHITE);
        fabBtn.setGravity(Gravity.CENTER);
        fabBtn.setBackground(circleBg(redC(), 0));
        fabBtn.setElevation(dp(4));
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.BOTTOM | Gravity.END);
        fabLp.rightMargin = dp(18);
        fabLp.bottomMargin = dp(18);
        fabBtn.setOnClickListener(v -> openNew(pageType()));
        r.addView(fabBtn, fabLp);
        fab = fabBtn;

        renderResults();
    }

    private String pageType() {
        return page == PAGE_PRINCIPLE ? T_PRINCIPLE : page == PAGE_QUESTION ? T_QUESTION : T_PERSON;
    }

    private boolean hitTags(List<String> tags) {
        Set<String> sel = tagSel.get(page);
        if (sel.isEmpty()) return true;
        for (String t : tags) if (sel.contains(t)) return true;
        return false;
    }
    private List<Principle> filteredPrinciples() {
        String q = queries[page].trim();
        List<Principle> out = new ArrayList<>();
        for (Principle p : principles) {
            if (!hitTags(p.tags)) continue;
            if (!q.isEmpty() && !(p.title.contains(q) || p.body.contains(q))) continue;
            out.add(p);
        }
        return out;
    }
    private List<Question> filteredQuestions() {
        String q = queries[page].trim();
        List<Question> out = new ArrayList<>();
        for (Question qs : questions) {
            if (!hitTags(qs.tags)) continue;
            if (!q.isEmpty() && !(qs.title.contains(q) || qs.body.contains(q))) continue;
            out.add(qs);
        }
        return out;
    }
    private List<Person> filteredPeople() {
        String q = queries[page].trim();
        List<Person> out = new ArrayList<>();
        for (Person pe : people) {
            if (!hitTags(pe.tags)) continue;
            if (!q.isEmpty() && !(pe.name.contains(q) || pe.memo.contains(q))) continue;
            out.add(pe);
        }
        return out;
    }

    private void renderResults() {
        if (resultsCol == null) return;
        resultsCol.removeAllViews();
        groupHeads.clear();
        if (rail != null) { rail.removeAllViews(); rail.setVisibility(View.GONE); }
        if (page == PAGE_PRINCIPLE) renderPrincipleResults();
        else if (page == PAGE_QUESTION) renderQuestionResults();
        else renderPeopleResults();
    }

    private TextView groupLabel(String t) {
        TextView v = tv(t, 11.5f, weakC());
        v.setLetterSpacing(0.25f);
        v.setPadding(dp(2), dp(14), 0, dp(8));
        return v;
    }
    private TextView tagChip(String tag) {
        TextView c = tv(tag, 11, subC());
        c.setPadding(dp(10), dp(2), dp(10), dp(2));
        c.setBackground(roundBg(washC(), 0, 20));
        return c;
    }
    private FlowLayout metaRow() {
        FlowLayout f = new FlowLayout(act, dp(6), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        f.setLayoutParams(lp);
        return f;
    }
    private LinearLayout cardBox() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundBg(cardC(), lineC(), 10));
        box.setPadding(dp(13), dp(12), dp(13), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(9);
        box.setLayoutParams(lp);
        return box;
    }
    private TextView bodyPreview(String body) {
        TextView p = tv(firstLine(body), 12, subC());
        p.setMaxLines(2);
        p.setEllipsize(TextUtils.TruncateAt.END);
        p.setLineSpacing(0, 1.35f);
        p.setPadding(0, dp(3), 0, 0);
        return p;
    }
    private void addTagChips(FlowLayout meta, List<String> tags) {
        for (String t : tags) meta.addView(tagChip(t));
    }

    // ---- 原则 ----
    private void renderPrincipleResults() {
        List<Principle> list = filteredPrinciples();
        countView.setText(s(list.size() + " 条", list.size() + " entries"));
        if (list.isEmpty()) { resultsCol.addView(emptyHint()); return; }
        boolean card = "card".equals(modeOf(page));
        if (card) {
            for (String kind : new String[]{"think", "act"}) {
                List<Principle> group = new ArrayList<>();
                for (Principle p : list) if (kind.equals(p.kind)) group.add(p);
                if (group.isEmpty()) continue;
                resultsCol.addView(groupLabel(kind.equals("think") ? s("思维原则", "Thinking") : s("行为原则", "Action")));
                for (final Principle p : group) resultsCol.addView(principleCard(p));
            }
        } else {
            for (final Principle p : list) resultsCol.addView(principleRow(p));
        }
    }
    private View principleCard(final Principle p) {
        LinearLayout box = cardBox();
        TextView kind = tv("think".equals(p.kind) ? s("思维", "Thinking") : s("行为", "Action"), 10.5f, redC());
        box.addView(kind);
        TextView title = serif(displayOf(p), 15.5f, inkC(), true);
        title.setPadding(0, dp(2), 0, 0);
        box.addView(title);
        String prev = firstLine(p.body);
        if (!prev.isEmpty() && !prev.equals(displayOf(p))) box.addView(bodyPreview(p.body));
        FlowLayout meta = metaRow();
        addTagChips(meta, p.tags);
        for (String pid : linkedIds(T_PRINCIPLE, p.id, T_PERSON)) {
            Person pe = personById(pid);
            if (pe != null) { TextView c = tagChip(pe.name); meta.addView(c); }
        }
        int qn = linkedIds(T_PRINCIPLE, p.id, T_QUESTION).size();
        if (qn > 0) meta.addView(tagChip(s("问题 " + qn, "Questions " + qn)));
        if (meta.getChildCount() > 0) box.addView(meta);
        box.setOnClickListener(v -> openEdit(T_PRINCIPLE, p.id));
        return box;
    }
    private View principleRow(final Principle p) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(9), dp(2), dp(9));
        View dot = new View(act);
        boolean think = "think".equals(p.kind);
        dot.setBackground(circleBg(think ? redC() : weakC(), 0));
        row.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView title = tv(displayOf(p), 14.5f, inkC());
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tLp.leftMargin = dp(10);
        row.addView(title, tLp);
        if (!p.tags.isEmpty()) {
            TextView tg = tv(TextUtils.join(" ", p.tags), 11, weakC());
            tg.setMaxLines(1);
            row.addView(tg);
        }
        row.setOnClickListener(v -> openEdit(T_PRINCIPLE, p.id));
        return row;
    }

    // ---- 问题 ----
    private void renderQuestionResults() {
        List<Question> list = filteredQuestions();
        countView.setText(s(list.size() + " 条", list.size() + " entries"));
        if (list.isEmpty()) { resultsCol.addView(emptyHint()); return; }
        boolean card = "card".equals(modeOf(page));
        for (final Question q : list) {
            resultsCol.addView(card ? questionCard(q) : questionRow(q));
        }
    }
    private View questionCard(final Question q) {
        LinearLayout box = cardBox();
        box.addView(serif(displayOf(q), 15.5f, inkC(), true));
        String prev = firstLine(q.body);
        if (!prev.isEmpty() && !prev.equals(displayOf(q))) box.addView(bodyPreview(q.body));
        FlowLayout meta = metaRow();
        List<String> pids = linkedIds(T_QUESTION, q.id, T_PERSON);
        if (!pids.isEmpty()) {
            TextView who = tv(s("问", "Ask"), 11.5f, subC());
            who.setPadding(0, dp(2), 0, dp(2));
            meta.addView(who);
            for (String pid : pids) {
                Person pe = personById(pid);
                if (pe != null) meta.addView(tagChip(pe.name));
            }
        }
        addTagChips(meta, q.tags);
        TextView st = tv("pending".equals(q.status) ? s("待问", "To ask") : s("已问", "Asked"),
                11, "pending".equals(q.status) ? redC() : weakC());
        st.setPadding(0, dp(2), 0, dp(2));
        meta.addView(st);
        box.addView(meta);
        box.setOnClickListener(v -> openEdit(T_QUESTION, q.id));
        return box;
    }
    private View questionRow(final Question q) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(9), dp(2), dp(9));
        TextView title = tv(displayOf(q), 14.5f, inkC());
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        List<String> pids = linkedIds(T_QUESTION, q.id, T_PERSON);
        if (!pids.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String pid : pids) { Person pe = personById(pid); if (pe != null) names.add(pe.name); }
            TextView who = tv(TextUtils.join("、", names), 11, weakC());
            who.setMaxLines(1);
            who.setPadding(dp(8), 0, dp(8), 0);
            row.addView(who);
        }
        TextView st = tv("pending".equals(q.status) ? s("待问", "To ask") : s("已问", "Asked"),
                11, "pending".equals(q.status) ? redC() : weakC());
        row.addView(st);
        row.setOnClickListener(v -> openEdit(T_QUESTION, q.id));
        return row;
    }

    // ---- 人名 ----
    private void renderPeopleResults() {
        List<Person> list = filteredPeople();
        countView.setText(s(list.size() + " 人", list.size() + " people"));
        if (list.isEmpty()) { resultsCol.addView(emptyHint()); return; }
        boolean card = "card".equals(modeOf(page));
        if (card) {
            for (final Person pe : list) resultsCol.addView(personCard(pe));
            return;
        }
        // 列表：按拼音首字母分组
        Map<String, List<Person>> groups = new LinkedHashMap<>();
        List<Person> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> {
            String ia = initialOf(a.name), ib = initialOf(b.name);
            int c = ia.compareTo(ib);
            return c != 0 ? c : a.name.compareTo(b.name);
        });
        for (Person pe : sorted) {
            String ini = initialOf(pe.name);
            if (!groups.containsKey(ini)) groups.put(ini, new ArrayList<>());
            groups.get(ini).add(pe);
        }
        for (Map.Entry<String, List<Person>> e : groups.entrySet()) {
            TextView head = groupLabel(e.getKey());
            groupHeads.put(e.getKey(), head);
            resultsCol.addView(head);
            for (final Person pe : e.getValue()) resultsCol.addView(personRow(pe));
        }
        // 右侧字母栏
        if (rail != null && groups.size() > 1) {
            rail.setVisibility(View.VISIBLE);
            for (final String letter : groups.keySet()) {
                TextView lt = tv(letter, 10.5f, subC());
                lt.setGravity(Gravity.CENTER);
                lt.setPadding(dp(6), dp(2), dp(6), dp(2));
                lt.setOnClickListener(v -> {
                    View head = groupHeads.get(letter);
                    if (head != null && listScroll != null) {
                        int y = 0;
                        View cur = head;
                        while (cur != null && cur != listScroll.getChildAt(0)) {
                            y += cur.getTop();
                            cur = (View) cur.getParent();
                        }
                        listScroll.smoothScrollTo(0, Math.max(0, y - dp(8)));
                    }
                });
                rail.addView(lt);
            }
        }
    }
    private View personCard(final Person pe) {
        LinearLayout box = cardBox();
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView seal = serif(pe.name.isEmpty() ? "？" : pe.name.substring(0, 1), 17, redC(), true);
        seal.setGravity(Gravity.CENTER);
        seal.setBackground(roundBg(Color.TRANSPARENT, redC(), 4));
        row.addView(seal, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView name = serif(pe.name, 16, inkC(), true);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nLp.leftMargin = dp(10);
        row.addView(name, nLp);
        box.addView(row);
        if (!pe.memo.trim().isEmpty()) {
            TextView m = tv(firstLine(pe.memo), 12, subC());
            m.setMaxLines(1);
            m.setEllipsize(TextUtils.TruncateAt.END);
            m.setPadding(0, dp(7), 0, 0);
            box.addView(m);
        }
        FlowLayout meta = metaRow();
        addTagChips(meta, pe.tags);
        int qn = linkedIds(T_PERSON, pe.id, T_QUESTION).size();
        if (qn > 0) meta.addView(tagChip(s("问题 " + qn, "Questions " + qn)));
        if (meta.getChildCount() > 0) box.addView(meta);
        box.setOnClickListener(v -> openEdit(T_PERSON, pe.id));
        return box;
    }
    private View personRow(final Person pe) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(8), dp(14), dp(8));
        TextView name = tv(pe.name, 14.5f, inkC());
        row.addView(name);
        if (!pe.tags.isEmpty()) {
            TextView tg = tv(pe.tags.get(0), 10.5f, subC());
            tg.setPadding(dp(7), dp(1), dp(7), dp(1));
            tg.setBackground(roundBg(washC(), 0, 4));
            LinearLayout.LayoutParams tgLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tgLp.leftMargin = dp(8);
            row.addView(tg, tgLp);
        }
        View spacer = new View(act);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        if (!pe.memo.trim().isEmpty()) {
            TextView m = tv(firstLine(pe.memo), 11, weakC());
            m.setMaxLines(1);
            m.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            row.addView(m, mLp);
        }
        int qn = linkedIds(T_PERSON, pe.id, T_QUESTION).size();
        if (qn > 0) {
            TextView qc = tv(s("问题 " + qn, "Q" + qn), 11, subC());
            qc.setPadding(dp(8), 0, 0, 0);
            row.addView(qc);
        }
        row.setOnClickListener(v -> openEdit(T_PERSON, pe.id));
        return row;
    }

    private TextView emptyHint() {
        TextView none = tv(s("还没有", "Nothing yet"), 12.5f, subC());
        none.setPadding(0, dp(10), 0, dp(2));
        return none;
    }

    /** 拼音首字母：ICU Han-Latin 转写取首字母；非汉字拉丁字母直接取；其余归 '#'。 */
    private final Map<String, String> initialCache = new HashMap<>();
    private String initialOf(String name) {
        String n = nz(name).trim();
        if (n.isEmpty()) return "#";
        if (initialCache.containsKey(n)) return initialCache.get(n);
        String out = "#";
        char c0 = n.charAt(0);
        if ((c0 >= 'a' && c0 <= 'z') || (c0 >= 'A' && c0 <= 'Z')) {
            out = String.valueOf(Character.toUpperCase(c0));
        } else {
            try {
                String lat = Transliterator.getInstance("Han-Latin").transliterate(n);
                if (lat != null) {
                    for (int i = 0; i < lat.length(); i++) {
                        char c = lat.charAt(i);
                        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                            out = String.valueOf(Character.toUpperCase(c));
                            break;
                        }
                    }
                }
            } catch (Exception ignored) { }
        }
        initialCache.put(n, out);
        return out;
    }

    // ---------------- 复制筛选结果 ----------------
    private void copyFiltered() {
        List<String> lines = new ArrayList<>();
        if (page == PAGE_PRINCIPLE) {
            for (Principle p : filteredPrinciples()) {
                lines.add(("think".equals(p.kind) ? "思维" : "行为") + "｜" + displayOf(p)
                        + "｜" + oneLine(p.body) + "｜" + TextUtils.join("，", p.tags));
            }
        } else if (page == PAGE_QUESTION) {
            for (Question q : filteredQuestions()) {
                List<String> names = new ArrayList<>();
                for (String pid : linkedIds(T_QUESTION, q.id, T_PERSON)) {
                    Person pe = personById(pid);
                    if (pe != null) names.add(pe.name);
                }
                lines.add(displayOf(q) + "｜问：" + (names.isEmpty() ? "无" : TextUtils.join("、", names))
                        + "｜" + ("pending".equals(q.status) ? "待问" : "已问")
                        + "｜" + TextUtils.join("，", q.tags));
            }
        } else {
            for (Person pe : filteredPeople()) {
                lines.add(pe.name + "｜" + TextUtils.join("，", pe.tags) + "｜" + oneLine(pe.memo)
                        + "｜" + linkedIds(T_PERSON, pe.id, T_QUESTION).size());
            }
        }
        if (lines.isEmpty()) { toast(s("没有可复制的", "Nothing to copy")); return; }
        ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("zhi", TextUtils.join("\n", lines)));
        toast(s("已复制 " + lines.size() + " 条", "Copied " + lines.size()));
    }

    /** 简单换行容器（标签/ chips 用）。 */
    static final class FlowLayout extends ViewGroup {
        private final int hGap, vGap;
        FlowLayout(Context c, int hGap, int vGap) {
            super(c);
            this.hGap = hGap;
            this.vGap = vGap;
        }
        @Override protected void onMeasure(int wSpec, int hSpec) {
            int width = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                measureChild(ch, wSpec, hSpec);
                int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
                MarginLayoutParams mlp = (MarginLayoutParams) ch.getLayoutParams();
                cw += mlp.leftMargin + mlp.rightMargin;
                chh += mlp.topMargin + mlp.bottomMargin;
                if (x > 0 && x + cw > width) { x = 0; y += rowH + vGap; rowH = 0; }
                x += cw + hGap;
                rowH = Math.max(rowH, chh);
            }
            setMeasuredDimension(MeasureSpec.getSize(wSpec),
                    y + rowH + getPaddingTop() + getPaddingBottom());
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l - getPaddingLeft() - getPaddingRight();
            int x = getPaddingLeft(), y = getPaddingTop(), rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                MarginLayoutParams mlp = (MarginLayoutParams) ch.getLayoutParams();
                int cw = ch.getMeasuredWidth() + mlp.leftMargin + mlp.rightMargin;
                int chh = ch.getMeasuredHeight() + mlp.topMargin + mlp.bottomMargin;
                if (x > getPaddingLeft() && x - getPaddingLeft() + cw > width) {
                    x = getPaddingLeft();
                    y += rowH + vGap;
                    rowH = 0;
                }
                ch.layout(x + mlp.leftMargin, y + mlp.topMargin,
                        x + mlp.leftMargin + ch.getMeasuredWidth(),
                        y + mlp.topMargin + ch.getMeasuredHeight());
                x += cw + hGap;
                rowH = Math.max(rowH, chh);
            }
        }
        @Override protected LayoutParams generateDefaultLayoutParams() {
            return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        @Override protected LayoutParams generateLayoutParams(LayoutParams p) {
            return new MarginLayoutParams(p);
        }
        @Override public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
            return new MarginLayoutParams(getContext(), attrs);
        }
        @Override protected boolean checkLayoutParams(LayoutParams p) {
            return p instanceof MarginLayoutParams;
        }
    }

    // ---------------- 统一编辑页 ----------------
    static final class EditState {
        Ref ref;
        // 草稿（文本字段从视图回收；其余状态点选即存这里，重画不丢）
        String title = "", body = "", name = "", memo = "";
        String kind = "think", status = "pending";
        List<String> tags = new ArrayList<>();
        String addingType;               // 哪个关联区的添加面板开着（null=都关）
        EditText titleIn, bodyIn, nameIn, memoIn, addIn;
        LinearLayout candBox;
    }

    private static String typeName(String type) {
        return T_PRINCIPLE.equals(type) ? s("原则", "Principle")
                : T_QUESTION.equals(type) ? s("问题", "Question") : s("人名", "Person");
    }
    private int pageOfType(String type) {
        return T_PRINCIPLE.equals(type) ? PAGE_PRINCIPLE : T_QUESTION.equals(type) ? PAGE_QUESTION : PAGE_PERSON;
    }

    private EditState stateFromModel(Ref ref) {
        EditState st = new EditState();
        st.ref = ref;
        if (ref.id == null) return st;
        if (T_PRINCIPLE.equals(ref.type)) {
            Principle p = principleById(ref.id);
            if (p != null) { st.title = p.title; st.body = p.body; st.kind = p.kind; st.tags = new ArrayList<>(p.tags); }
        } else if (T_QUESTION.equals(ref.type)) {
            Question q = questionById(ref.id);
            if (q != null) { st.title = q.title; st.body = q.body; st.status = q.status; st.tags = new ArrayList<>(q.tags); }
        } else {
            Person pe = personById(ref.id);
            if (pe != null) { st.name = pe.name; st.memo = pe.memo; st.tags = new ArrayList<>(pe.tags); }
        }
        return st;
    }

    private void collectDraft() {
        if (es == null) return;
        if (es.titleIn != null) es.title = es.titleIn.getText().toString();
        if (es.bodyIn != null) es.body = es.bodyIn.getText().toString();
        if (es.nameIn != null) es.name = es.nameIn.getText().toString();
        if (es.memoIn != null) es.memo = es.memoIn.getText().toString();
    }

    private void openEdit(String type, String id) {
        editStack.clear();
        Ref ref = new Ref(type, id);
        editStack.add(ref);
        es = stateFromModel(ref);
        editing = true;
        page = pageOfType(type);
        renderEdit();
    }
    private void openNew(String type) {
        editStack.clear();
        Ref ref = new Ref(type, null);
        editStack.add(ref);
        es = stateFromModel(ref);
        editing = true;
        page = pageOfType(type);
        renderEdit();
    }
    private void pushEdit(String type, String id) {
        Ref ref = new Ref(type, id);
        editStack.add(ref);
        es = stateFromModel(ref);
        editing = true;
        renderEdit();
    }
    private void backFromEdit() {
        if (!editStack.isEmpty()) editStack.remove(editStack.size() - 1);
        if (editStack.isEmpty()) {
            es = null;
            editing = false;
            renderList();
        } else {
            es = stateFromModel(editStack.get(editStack.size() - 1));
            renderEdit();
        }
    }

    private TextView fieldLabel(String t) {
        TextView v = tv(t, 11.5f, weakC());
        v.setLetterSpacing(0.3f);
        v.setPadding(0, dp(15), 0, dp(7));
        return v;
    }
    private EditText fieldBox(String text, String hint, int minLines) {
        EditText e = new EditText(act);
        e.setText(text);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(inkC());
        e.setBackground(roundBg(cardC(), lineC(), 10));
        e.setPadding(dp(13), dp(11), dp(13), dp(11));
        if (minLines > 1) { e.setMinLines(minLines); e.setGravity(Gravity.TOP); }
        else e.setSingleLine(true);
        return e;
    }
    /** 二选一段控（原则类型/问题状态共用）。 */
    private View twoChoice(final String[] values, final String[] labels, String current,
                           final java.util.function.Consumer<String> onPick) {
        LinearLayout seg = new LinearLayout(act);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(roundBg(washC(), 0, 9));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < values.length; i++) {
            final String val = values[i];
            boolean on = val.equals(current);
            TextView t = tv(labels[i], 13, on ? inkC() : subC());
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(7), 0, dp(7));
            if (on) {
                t.setBackground(roundBg(cardC(), 0, 7));
                t.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
            }
            t.setOnClickListener(v -> { onPick.accept(val); collectDraft(); renderEdit(); });
            seg.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        return seg;
    }

    private void renderEdit() {
        FrameLayout r = ensureRoot();
        r.removeAllViews();
        groupHeads.clear();
        final EditState st = es;
        final String type = st.ref.type;

        ScrollView scroll = new ScrollView(act);
        scroll.setBackgroundColor(Ui.bg(act));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(16), dp(18), dp(40));
        scroll.addView(col);
        r.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView back = tv("‹ " + typeName(type), 13, subC());
        back.setPadding(0, dp(4), 0, dp(2));
        back.setOnClickListener(v -> backFromEdit());
        col.addView(back);

        // ---- 字段面板 ----
        if (T_PERSON.equals(type)) {
            col.addView(fieldLabel(s("名 字", "Name")));
            st.nameIn = fieldBox(st.name, s("名字", "Name"), 1);
            col.addView(st.nameIn);
            col.addView(fieldLabel(s("记 忆 点", "Memo")));
            st.memoIn = fieldBox(st.memo, s("帮你记住这个人的一段话", "A note that helps you remember this person"), 3);
            col.addView(st.memoIn);
        } else {
            if (T_PRINCIPLE.equals(type)) {
                col.addView(fieldLabel(s("类 型", "Kind")));
                col.addView(twoChoice(new String[]{"think", "act"},
                        new String[]{s("思维", "Thinking"), s("行为", "Action")}, st.kind,
                        v -> st.kind = v));
            }
            col.addView(fieldLabel(s("标 题", "Title")));
            st.titleIn = fieldBox(st.title, s("标题", "Title"), 1);
            if (T_PRINCIPLE.equals(type)) st.titleIn.setTypeface(Typeface.SERIF, Typeface.BOLD);
            col.addView(st.titleIn);
            col.addView(fieldLabel(s("内 容", "Body")));
            st.bodyIn = fieldBox(st.body, T_QUESTION.equals(type)
                    ? s("写下这个问题", "Write the question") : s("写下这条原则", "Write the principle"), 4);
            col.addView(st.bodyIn);
            if (T_QUESTION.equals(type)) {
                col.addView(fieldLabel(s("状 态", "Status")));
                col.addView(twoChoice(new String[]{"pending", "asked"},
                        new String[]{s("待问", "To ask"), s("已问", "Asked")}, st.status,
                        v -> st.status = v));
            }
        }

        // ---- 标签编辑：已有标签点选＋新建 ----
        col.addView(fieldLabel(s("标 签", "Tags")));
        FlowLayout tagFlow = new FlowLayout(act, dp(6), dp(6));
        col.addView(tagFlow);
        List<String> universe = allTags();
        for (String t : st.tags) if (!universe.contains(t)) universe.add(t);
        for (final String tag : universe) {
            boolean on = st.tags.contains(tag);
            TextView chip = tv(tag, 11.5f, on ? cardC() : subC());
            chip.setPadding(dp(11), dp(3), dp(11), dp(3));
            chip.setBackground(roundBg(on ? inkC() : Color.TRANSPARENT, on ? 0 : weakC(), 20));
            chip.setOnClickListener(v -> {
                collectDraft();
                if (st.tags.contains(tag)) st.tags.remove(tag); else st.tags.add(tag);
                renderEdit();
            });
            tagFlow.addView(chip);
        }
        LinearLayout newTagRow = new LinearLayout(act);
        newTagRow.setOrientation(LinearLayout.HORIZONTAL);
        newTagRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ntLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ntLp.topMargin = dp(8);
        col.addView(newTagRow, ntLp);
        final EditText newTagIn = new EditText(act);
        newTagIn.setHint(s("新标签", "New tag"));
        newTagIn.setTextSize(13);
        newTagIn.setSingleLine(true);
        newTagIn.setBackground(roundBg(cardC(), lineC(), 8));
        newTagIn.setPadding(dp(10), dp(6), dp(10), dp(6));
        newTagRow.addView(newTagIn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView addTag = tv(s("添加", "Add"), 13, redC());
        addTag.setPadding(dp(12), dp(6), dp(4), dp(6));
        addTag.setOnClickListener(v -> {
            String t = newTagIn.getText().toString().trim();
            if (t.isEmpty()) return;
            collectDraft();
            if (!st.tags.contains(t)) st.tags.add(t);
            renderEdit();
        });
        newTagRow.addView(addTag);

        // ---- 保存 ----
        TextView save = tv(s("保 存", "Save"), 14.5f, cardC());
        save.setGravity(Gravity.CENTER);
        save.setLetterSpacing(0.3f);
        save.setBackground(roundBg(inkC(), 0, 10));
        save.setPadding(0, dp(12), 0, dp(12));
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        svLp.topMargin = dp(20);
        svLp.bottomMargin = dp(6);
        col.addView(save, svLp);
        save.setOnClickListener(v -> saveItem());

        // ---- 关联区 ----
        if (st.ref.id == null) {
            TextView note = tv(s("先保存，再加关联", "Save first, then add links"), 12.5f, subC());
            note.setPadding(0, dp(14), 0, 0);
            col.addView(note);
        } else {
            for (String other : sectionsFor(type)) col.addView(linkSection(other));
        }

        // ---- 删除 ----
        if (st.ref.id != null) {
            TextView del = tv(s("删除", "Delete"), 13.5f, Ui.danger(act));
            del.setGravity(Gravity.CENTER);
            del.setPadding(0, dp(22), 0, dp(4));
            del.setOnClickListener(v -> confirmDeleteItem());
            col.addView(del);
        }
    }

    private String[] sectionsFor(String type) {
        if (T_PRINCIPLE.equals(type)) return new String[]{T_PERSON, T_QUESTION};
        if (T_QUESTION.equals(type)) return new String[]{T_PERSON, T_PRINCIPLE};
        return new String[]{T_QUESTION, T_PRINCIPLE};
    }
    private String sectionTitle(String otherType) {
        return s("关联的", "Linked ") + (T_PERSON.equals(otherType) ? s("人名", "people")
                : T_QUESTION.equals(otherType) ? s("问题", "questions") : s("原则", "principles"));
    }

    private View linkSection(final String otherType) {
        final EditState st = es;
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundBg(Ui.dark(act) ? Ui.surface(act) : Color.rgb(251, 248, 239), lineC(), 10));
        box.setPadding(dp(13), dp(11), dp(13), dp(11));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bLp.topMargin = dp(12);
        box.setLayoutParams(bLp);

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        List<String> ids = linkedIds(st.ref.type, st.ref.id, otherType);
        TextView h = tv(sectionTitle(otherType) + (ids.isEmpty() ? "" : "（" + ids.size() + "）"), 12, subC());
        h.setLetterSpacing(0.15f);
        head.addView(h);
        View spacer = new View(act);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        TextView plus = tv(s("＋ 添加", "＋ Add"), 12, redC());
        plus.setPadding(dp(10), dp(2), dp(10), dp(2));
        plus.setBackground(roundBg(Color.TRANSPARENT, redC(), 14));
        plus.setOnClickListener(v -> {
            collectDraft();
            st.addingType = otherType.equals(st.addingType) ? null : otherType;
            renderEdit();
        });
        head.addView(plus);
        box.addView(head);

        for (final String oid : ids) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));
            TextView name = tv(displayOf(otherType, oid), 13.5f, inkC());
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setOnClickListener(v -> pushEdit(otherType, oid));
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView x = tv("✕", 12, weakC());
            x.setPadding(dp(10), dp(2), dp(2), dp(2));
            x.setOnClickListener(v -> {
                Link found = null;
                for (Link l : linksOf(st.ref.type, st.ref.id)) {
                    if (l.otherType(st.ref.type, st.ref.id).equals(otherType)
                            && l.otherId(st.ref.type, st.ref.id).equals(oid)) { found = l; break; }
                }
                if (found != null) {
                    final Link fl = found;
                    removeLink(fl, () -> { collectDraft(); renderEdit(); });
                }
            });
            row.addView(x);
            box.addView(row);
        }

        if (otherType.equals(st.addingType)) box.addView(addPanel(otherType));
        return box;
    }

    /** 输入联想面板：打字过滤已有条目，点一条立即关联；底部可新建并关联。 */
    private View addPanel(final String otherType) {
        final EditState st = es;
        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pLp.topMargin = dp(8);
        panel.setLayoutParams(pLp);

        final EditText in = new EditText(act);
        in.setHint(T_PERSON.equals(otherType) ? s("输入名字找已有的人", "Type a name to find people")
                : s("打几个字找已有的", "Type to find existing entries"));
        in.setTextSize(14);
        in.setSingleLine(true);
        in.setBackground(roundBg(cardC(), subC(), 10));
        in.setPadding(dp(12), dp(9), dp(12), dp(9));
        panel.addView(in);
        st.addIn = in;

        final LinearLayout cand = new LinearLayout(act);
        cand.setOrientation(LinearLayout.VERTICAL);
        cand.setBackground(roundBg(cardC(), lineC(), 10));
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cLp.topMargin = dp(6);
        panel.addView(cand, cLp);
        st.candBox = cand;

        in.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { fillCandidates(otherType, e.toString().trim()); }
        });
        fillCandidates(otherType, "");
        return panel;
    }

    private void fillCandidates(final String otherType, final String q) {
        final EditState st = es;
        if (st == null || st.candBox == null) return;
        LinearLayout cand = st.candBox;
        cand.removeAllViews();
        List<String[]> cands = new ArrayList<>();   // {id, display, haystack}
        if (T_PRINCIPLE.equals(otherType)) {
            for (Principle p : principles) cands.add(new String[]{p.id, displayOf(p), p.title + "\n" + p.body});
        } else if (T_QUESTION.equals(otherType)) {
            for (Question qs : questions) cands.add(new String[]{qs.id, displayOf(qs), qs.title + "\n" + qs.body});
        } else {
            for (Person pe : people) cands.add(new String[]{pe.id, pe.name, pe.name + "\n" + pe.memo});
        }
        int shown = 0;
        for (final String[] c : cands) {
            if (c[0].equals(st.ref.id) && otherType.equals(st.ref.type)) continue;
            if (isLinked(st.ref.type, st.ref.id, otherType, c[0])) continue;
            if (!q.isEmpty() && !c[2].contains(q)) continue;
            if (shown >= 30) break;
            shown++;
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(13), dp(10), dp(13), dp(10));
            TextView name = tv(c[1], 13.5f, inkC());
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView lk = tv(s("点一下直接关联 ›", "Tap to link ›"), 11, redC());
            row.addView(lk);
            row.setOnClickListener(v -> addLink(st.ref.type, st.ref.id, otherType, c[0], () -> {
                collectDraft();
                st.addingType = null;
                renderEdit();
            }));
            cand.addView(row);
        }
        if (!q.isEmpty()) {
            TextView create = tv(s("没有合适的？新建" + typeName(otherType) + "「" + q + "」并关联",
                    "Create new: " + q), 13, subC());
            create.setPadding(dp(13), dp(10), dp(13), dp(10));
            create.setOnClickListener(v -> createAndLink(otherType, q));
            cand.addView(create);
        }
        cand.setVisibility(cand.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    /** 新建一条（以输入文字为标题/名字）并立即与当前条目关联。 */
    private void createAndLink(final String newType, final String text) {
        final EditState st = es;
        final String newId = UUID.randomUUID().toString();
        final String now = isoNow();
        new Thread(() -> {
            try {
                JSONObject row = new JSONObject();
                row.put("id", newId);
                row.put("user_id", Supa.userId(act));
                row.put("created_at", now);
                row.put("updated_at", now);
                String table;
                if (T_PRINCIPLE.equals(newType)) {
                    table = "zhi_principles";
                    row.put("kind", "think");
                    row.put("title", text);
                    row.put("body", "");
                    row.put("tags", new JSONArray());
                    row.put("source", "app");
                } else if (T_QUESTION.equals(newType)) {
                    table = "zhi_questions";
                    row.put("title", text);
                    row.put("body", "");
                    row.put("status", "pending");
                    row.put("tags", new JSONArray());
                    row.put("source", "app");
                } else {
                    table = "zhi_people";
                    row.put("name", text);
                    row.put("memo", "");
                    row.put("tags", new JSONArray());
                }
                JSONArray back = Supa.restUpsertOne(act, table, "id", row);
                if (back.length() == 0) throw new Exception("empty back");
                JSONObject saved = back.getJSONObject(0);
                // 规范形关联
                String aT, aI, bT, bI;
                if (st.ref.type.compareTo(newType) <= 0) { aT = st.ref.type; aI = st.ref.id; bT = newType; bI = newId; }
                else { aT = newType; aI = newId; bT = st.ref.type; bI = st.ref.id; }
                JSONObject lr = new JSONObject();
                lr.put("id", UUID.randomUUID().toString());
                lr.put("user_id", Supa.userId(act));
                lr.put("a_type", aT); lr.put("a_id", aI);
                lr.put("b_type", bT); lr.put("b_id", bI);
                lr.put("created_at", now);
                Supa.restUpsertOne(act, "zhi_links", "user_id,a_type,a_id,b_type,b_id", lr);
                act.runOnUiThread(() -> {
                    if (T_PRINCIPLE.equals(newType)) principles.add(parsePrinciple(saved));
                    else if (T_QUESTION.equals(newType)) questions.add(parseQuestion(saved));
                    else people.add(parsePerson(saved));
                    Link l = new Link();
                    l.aType = aT; l.aId = aI; l.bType = bT; l.bId = bI;
                    links.add(l);
                    saveCache();
                    collectDraft();
                    st.addingType = null;
                    renderEdit();
                    toast(s("建好并关联上了", "Created and linked"));
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> toast(s("没建上，再试一次", "Couldn't create — try again")));
            }
        }).start();
    }

    private static Principle parsePrinciple(JSONObject o) {
        Principle p = new Principle();
        p.id = o.optString("id", "");
        p.kind = o.optString("kind", "think");
        p.title = nz(o.optString("title", ""));
        p.body = nz(o.optString("body", ""));
        p.tags = parseTags(o);
        return p;
    }
    private static Question parseQuestion(JSONObject o) {
        Question q = new Question();
        q.id = o.optString("id", "");
        q.title = nz(o.optString("title", ""));
        q.body = nz(o.optString("body", ""));
        q.status = o.optString("status", "pending");
        q.tags = parseTags(o);
        return q;
    }
    private static Person parsePerson(JSONObject o) {
        Person pe = new Person();
        pe.id = o.optString("id", "");
        pe.name = nz(o.optString("name", ""));
        pe.memo = nz(o.optString("memo", ""));
        pe.tags = parseTags(o);
        return pe;
    }

    private void saveItem() {
        final EditState st = es;
        collectDraft();
        final String type = st.ref.type;
        final boolean isNew = st.ref.id == null;
        if (T_PERSON.equals(type)) {
            if (st.name.trim().isEmpty()) { toast(s("先写下名字", "Write a name first")); return; }
        } else if (st.title.trim().isEmpty() && st.body.trim().isEmpty()) {
            toast(s("先写点什么再存", "Write something first"));
            return;
        }
        final String id = isNew ? UUID.randomUUID().toString() : st.ref.id;
        final String now = isoNow();
        new Thread(() -> {
            try {
                JSONObject row = new JSONObject();
                row.put("id", id);
                row.put("user_id", Supa.userId(act));
                row.put("updated_at", now);
                if (isNew) { row.put("created_at", now); row.put("source", "app"); }
                String table;
                if (T_PRINCIPLE.equals(type)) {
                    table = "zhi_principles";
                    row.put("kind", st.kind);
                    row.put("title", st.title.trim());
                    row.put("body", st.body.trim());
                    row.put("tags", tagsJson(st.tags));
                } else if (T_QUESTION.equals(type)) {
                    table = "zhi_questions";
                    row.put("title", st.title.trim());
                    row.put("body", st.body.trim());
                    row.put("status", st.status);
                    row.put("tags", tagsJson(st.tags));
                } else {
                    table = "zhi_people";
                    row.remove("source");
                    row.put("name", st.name.trim());
                    row.put("memo", st.memo.trim());
                    row.put("tags", tagsJson(st.tags));
                }
                JSONArray back = Supa.restUpsertOne(act, table, "id", row);
                if (back.length() == 0) throw new Exception("empty back");
                final JSONObject saved = back.getJSONObject(0);
                act.runOnUiThread(() -> {
                    if (T_PRINCIPLE.equals(type)) {
                        Principle np = parsePrinciple(saved);
                        replacePrinciple(np);
                    } else if (T_QUESTION.equals(type)) {
                        replaceQuestion(parseQuestion(saved));
                    } else {
                        replacePerson(parsePerson(saved));
                    }
                    offline = false;
                    saveCache();
                    st.ref.id = id;
                    // 以落库值刷新草稿，再重画（关联区随之可用）
                    EditState fresh = stateFromModel(st.ref);
                    fresh.addingType = st.addingType;
                    es = fresh;
                    renderEdit();
                    toast(s("存好了", "Saved"));
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> toast(s("没存上，内容还在，再试一次",
                        "Couldn't save — nothing was changed, try again")));
            }
        }).start();
    }

    private void replacePrinciple(Principle np) {
        for (int i = 0; i < principles.size(); i++) {
            if (principles.get(i).id.equals(np.id)) { principles.set(i, np); return; }
        }
        principles.add(np);
    }
    private void replaceQuestion(Question nq) {
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).id.equals(nq.id)) { questions.set(i, nq); return; }
        }
        questions.add(nq);
    }
    private void replacePerson(Person np) {
        for (int i = 0; i < people.size(); i++) {
            if (people.get(i).id.equals(np.id)) { people.set(i, np); return; }
        }
        people.add(np);
    }

    private void confirmDeleteItem() {
        final EditState st = es;
        String head = T_PERSON.equals(st.ref.type) ? st.name : displayOfText(st.title, st.body);
        new AlertDialog.Builder(act)
                .setTitle(s("删除「" + head + "」？", "Delete \"" + head + "\"?"))
                .setMessage(s("它在各处的关联也会一起解除", "Its links will be removed too"))
                .setPositiveButton(s("删除", "Delete"), (d, w) -> {
                    final String type = st.ref.type, id = st.ref.id;
                    deleteItem(type, id, () -> {
                        editStack.removeIf(rf -> id.equals(rf.id) && type.equals(rf.type));
                        es = null;
                        editing = false;
                        renderList();
                        toast(s("删掉了", "Deleted"));
                    });
                })
                .setNegativeButton(Strings.btnCancel(), null)
                .show();
    }
    private static String displayOfText(String title, String body) {
        return title != null && !title.trim().isEmpty() ? title.trim() : firstLine(body);
    }
}
