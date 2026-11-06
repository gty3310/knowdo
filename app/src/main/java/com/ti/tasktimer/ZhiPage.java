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
import java.util.Iterator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * 知页（底部第二签）：「知」分四页——原则（zhi_principles，kind 分思维
 * think／行为 act）、问题（zhi_questions，status 待问 pending／已问 asked）、
 * 人名（zhi_people）、日记（v1.60.0 补的第四页签）；条目经 zhi_links 关联
 * （关联对按类型名字母序存规范形：diary &lt; person &lt; principle &lt; question &lt; task）。
 * 日记不搬家：文本仍住 HabitData 的 diary 映射，读走 HabitData.diary／
 * diaryDatesDesc，写只走 HabitData.putDiary（与晨间页、DiaryActivity 同一条路），
 * zhi_links 里 diary 恒为 a 侧、a_id＝日期字符串。日记页签只有搜索与列表（日期
 * 倒序、只列有文本的日期）；日记详情页可改全文（经 putDiary 保存）并与原则、
 * 人名双向关联，原则/人名编辑页也各有一组「日记」关联区（问题页不加）。
 * 前三页同一套工具：搜索（字面包含）、标签多选筛选（OR）、复制筛选结果、卡片/列表
 * 双模式（每页各自记住）；人名列表模式按拼音首字母分组＋右侧字母栏。
 * 编辑页三类同骨架：上半字段面板、下半关联区；加关联是输入联想（字面包含过滤
 * 已有条目，点一条立即关联），也可新建并关联。数据全走 Supa REST（用户 JWT，
 * RLS 只见自己的）。v1.67.0 起：写操作全部本地先行＋待同步队列（见队列一节），
 * 拉取逐表独立成败（失败表在页签留可点重试的提示，不再静默用旧缓存），四表接
 * Realtime 自动刷新；人名/问题/原则/日记都带重要性（importance：0 无星／1 一星
 * ／2 二星，日记的存 user_state.diary_importance 按日期），列表星级降序、筛选行
 * 钉 ★★/★。
 * 旧表 zhi_entries 本版起不再读写（表本身保留不动）。
 */
public final class ZhiPage {
    // 条目类型（与 zhi_links 的 *_type 取值一致）
    static final String T_PRINCIPLE = "principle";
    static final String T_QUESTION = "question";
    static final String T_PERSON = "person";
    static final String T_DIARY = "diary";
    static final String T_TASK = "task";   // 任务（v1.71.0）：只在 zhi_links 里出现，不进知页页签

    // 云端表名（v1.67.0：逐表拉取/补传/实时订阅都以表名为键）
    static final String TB_PRINCIPLES = "zhi_principles";
    static final String TB_QUESTIONS = "zhi_questions";
    static final String TB_PEOPLE = "zhi_people";
    static final String TB_LINKS = "zhi_links";

    private static final int PAGE_PRINCIPLE = 0;
    private static final int PAGE_QUESTION = 1;
    private static final int PAGE_PERSON = 2;
    private static final int PAGE_DIARY = 3;
    private static final String[] PAGE_NAMES = {"原则", "问题", "人名", "日记"};
    private static final String[] PAGE_NAMES_EN = {"Principles", "Questions", "People", "Diary"};

    private static final String PREF = "zhi_cache";
    private static final String MODE_PREF = "zhi2_mode";

    // ---------------- 模型 ----------------
    static final class Principle {
        String id, kind = "think", title = "", body = "";
        List<String> tags = new ArrayList<>();
        int importance;                 // 重要性（v1.67.0）：0 无星／1 一星／2 二星
        String updatedAt = "";          // 云端 updated_at（补传冲突判定基准）
    }
    static final class Question {
        String id, title = "", body = "", status = "pending";
        String shortTitle = "";   // 短题（v1.61.0）：引用时显示，≤10 字，zhi_questions.short_title
        List<String> tags = new ArrayList<>();
        int importance;                 // 重要性（v1.67.0）：0 无星／1 一星／2 二星
        String updatedAt = "";          // 云端 updated_at（补传冲突判定基准）
    }
    static final class Person {
        String id, name = "", memo = "";
        List<String> tags = new ArrayList<>();
        int importance;                 // 重要性（v1.67.0）：0 无星／1 一星／2 二星
        String updatedAt = "";          // 云端 updated_at（补传冲突判定基准）
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
    // 逐表拉取状态（v1.67.0）：拉失败的表名集合，非空即该表在用旧缓存
    private final Set<String> staleTables = new HashSet<>();
    // 页顶一次性提示（如冲突后「这条已在别处更新」），refresh 时清掉
    private String topNotice;
    // 星筛选（三页各一份，v1.67.0）：0 不筛／1 只看一星／2 只看二星
    private final int[] starSel = {0, 0, 0};
    // 日记重要性（本地 DiaryImportance 快照＋详情页当前选择）
    private JSONObject diaryImpRoot;
    private int diaryImpSel;
    // Realtime 防抖（同表 1 秒内的多次事件只重拉一次）
    private final android.os.Handler rtHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Map<String, Runnable> rtPending = new HashMap<>();

    // 列表状态（三页各自一份）
    private int page = PAGE_PRINCIPLE;
    private final String[] queries = {"", "", "", ""};
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

    // 日记详情状态（v1.60.0；日记不进三类条目的编辑栈，自成一套轻状态）
    private boolean diaryDetail;
    private boolean diaryFromEditor;   // 从原则/人名编辑页跳进来：返回时回编辑页
    private boolean returnToDiary;     // 从日记详情跳进条目编辑页：编辑页返回到底时回日记详情
    private String diaryDate;
    private String diaryAddingType;    // 日记详情里哪个关联区的添加面板开着
    private EditText diaryIn;
    private EditText diaryTitleIn;     // 日记标题（v1.61.0，DiaryTitles）
    private JSONObject diaryTitlesRoot;   // 渲染期取的 DiaryTitles 快照（标题显示用）
    private WikiLinks.Hook diaryHook;  // 日记详情正文的双链挂钩（v1.61.0）
    private LinearLayout diaryCandBox;
    private JSONObject habitRoot;      // 最近一次 HabitData.load 的结果（渲染期取最新）
    /** 双链浮窗改动数据后的回调：静默刷新模型（编辑中不重画、不丢草稿，与 fetch 同纪律）。 */
    private WikiLinks.Host wikiHost;

    public ZhiPage(Activity act, ViewGroup container) {
        this.act = act;
        this.container = container;
        this.wikiHost = () -> { if (Supa.isLoggedIn(this.act)) fetch(); };
        for (int i = 0; i < 3; i++) tagSel.add(new HashSet<>());
        loadCache();
        // 「知」四表 Realtime（v1.67.0）：云端有变更（含别处写入）→ 防抖重拉对应表
        SupaRealtime.setZhiListener(table -> onRealtimeZhi(table));
    }

    /** Realtime 变更事件：同表防抖 1 秒后重拉该表（拉取本身逐表独立成败）。 */
    private void onRealtimeZhi(final String table) {
        Runnable prev = rtPending.get(table);
        if (prev != null) rtHandler.removeCallbacks(prev);
        Runnable r = () -> {
            rtPending.remove(table);
            fetchTable(table);
        };
        rtPending.put(table, r);
        rtHandler.postDelayed(r, 1000);
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
        topNotice = null;
        renderCurrent();          // 先画缓存（或空区），不白屏
        fetch();                  // 切回知页强制重拉四表（逐表独立成败）
        flushQueue();             // 顺手补传待同步队列（回前台触发点之一）
    }

    private void renderCurrent() {
        if (diaryDetail) renderDiaryDetail();
        else if (editing && es != null) renderEdit();
        else renderList();
    }

    private void fetch() {
        fetchTable(TB_PRINCIPLES);
        fetchTable(TB_QUESTIONS);
        fetchTable(TB_PEOPLE);
        fetchTable(TB_LINKS);
        fetchDiaryImportance();
    }

    /**
     * 逐表独立拉取（v1.67.0，F2）：一表失败只标该表 stale、其余照常更新，不再
     * 「四表全成才算数、任一失败静默用旧缓存」。失败表对应的页签会在列表页留
     * 可点重试的提示；四表全败且本地一无所有才走整页失败态。
     */
    private void fetchTable(final String table) {
        new Thread(() -> {
            try {
                JSONArray rows = Supa.restGet(act, table + "?select=*&order=created_at.asc");
                act.runOnUiThread(() -> {
                    applyTable(table, rows);
                    staleTables.remove(table);
                    offline = !staleTables.isEmpty();
                    saveCache();
                    afterDataChanged();
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> {
                    staleTables.add(table);
                    offline = true;
                    afterDataChanged();
                });
            }
        }).start();
    }

    private void afterDataChanged() {
        boolean anyData = !principles.isEmpty() || !questions.isEmpty()
                || !people.isEmpty() || !links.isEmpty();
        fetchFailed = staleTables.size() >= 4 && !anyData;
        // 编辑中/日记详情中不打断未保存的字段，数据静默更新（既有纪律不变）
        if (!editing && !diaryDetail) renderList();
    }

    /** 日记重要性云端值并入本地（有待同步 op 的日期以本地为准，不被云端盖掉）。 */
    private void fetchDiaryImportance() {
        new Thread(() -> {
            try {
                JSONArray us = Supa.restGet(act, "user_state?select=diary_importance");
                if (us.length() == 0) return;
                JSONObject remote = us.getJSONObject(0).optJSONObject("diary_importance");
                if (remote == null) return;
                act.runOnUiThread(() -> {
                    JSONObject local = DiaryImportance.load(act);
                    Set<String> pendingDates = pendingDiaryImpDates();
                    boolean changed = false;
                    Iterator<String> ks = remote.keys();
                    while (ks.hasNext()) {
                        String k = ks.next();
                        if (pendingDates.contains(k)) continue;
                        int rv = remote.optInt(k, 0);
                        if (DiaryImportance.of(local, k) != rv) {
                            DiaryImportance.put(local, k, rv);
                            changed = true;
                        }
                    }
                    if (changed) {
                        DiaryImportance.saveQuiet(act, local);
                        diaryImpRoot = local;
                        if (diaryDetail || page == PAGE_DIARY) renderCurrent();
                    }
                });
            } catch (Exception ignored) { }
        }).start();
    }

    private void applyTable(String table, JSONArray rows) {
        if (TB_PRINCIPLES.equals(table)) applyPrinciples(rows);
        else if (TB_QUESTIONS.equals(table)) applyQuestions(rows);
        else if (TB_PEOPLE.equals(table)) applyPeople(rows);
        else applyLinks(rows);
    }

    private void applyPrinciples(JSONArray rows) {
        try {
            principles.clear();
            for (int i = 0; i < rows.length(); i++) principles.add(parsePrinciple(rows.getJSONObject(i)));
        } catch (Exception ignored) { }
    }
    private void applyQuestions(JSONArray rows) {
        try {
            questions.clear();
            for (int i = 0; i < rows.length(); i++) questions.add(parseQuestion(rows.getJSONObject(i)));
        } catch (Exception ignored) { }
    }
    private void applyPeople(JSONArray rows) {
        try {
            people.clear();
            for (int i = 0; i < rows.length(); i++) people.add(parsePerson(rows.getJSONObject(i)));
        } catch (Exception ignored) { }
    }
    private void applyLinks(JSONArray rows) {
        try {
            links.clear();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject o = rows.getJSONObject(i);
                Link l = new Link();
                l.aType = o.optString("a_type", "");
                l.aId = o.optString("a_id", "");
                l.bType = o.optString("b_type", "");
                l.bId = o.optString("b_id", "");
                links.add(l);
            }
        } catch (Exception ignored) { }
    }

    private void applyRows(JSONArray pRows, JSONArray qRows, JSONArray peRows, JSONArray lRows) {
        applyPrinciples(pRows);
        applyQuestions(qRows);
        applyPeople(peRows);
        applyLinks(lRows);
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
                o.put("importance", p.importance); o.put("updated_at", p.updatedAt);
                pa.put(o);
            }
            for (Question q : questions) {
                JSONObject o = new JSONObject();
                o.put("id", q.id); o.put("title", q.title); o.put("body", q.body);
                o.put("status", q.status); o.put("short_title", q.shortTitle); o.put("tags", tagsJson(q.tags));
                o.put("importance", q.importance); o.put("updated_at", q.updatedAt);
                qa.put(o);
            }
            for (Person pe : people) {
                JSONObject o = new JSONObject();
                o.put("id", pe.id); o.put("name", pe.name); o.put("memo", pe.memo);
                o.put("tags", tagsJson(pe.tags));
                o.put("importance", pe.importance); o.put("updated_at", pe.updatedAt);
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
    // 编辑页渲染期的任务快照（renderEdit 每次重画时新取）：任务关联区的显示名、
    // 完成标记与联想候选都读它；删掉的任务视同不存在（删任务时关联已一并解除）
    private List<TaskItem> editTasks = new ArrayList<>();
    private TaskItem taskById(String id) {
        for (TaskItem t : editTasks) if (t.id.equals(id) && !t.deleted) return t;
        return null;
    }
    private String displayOf(String type, String id) {
        if (T_TASK.equals(type)) { TaskItem t = taskById(id); return t == null ? "" : t.title; }
        if (T_DIARY.equals(type)) return dateLabel(id);
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
    /** 日记的显示名＝短日期＋星期（如「11 月 3 日 周二」）。 */
    private static String dateLabel(String date) {
        return DayUtil.shortDate(date) + " " + weekdayOf(date);
    }
    private static String weekdayOf(String date) {
        java.util.Calendar cal = DayUtil.parse(date);
        if (cal == null) return "";
        int idx = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7; // 周一=0 … 周日=6
        return DayUtil.weekdayName(idx);
    }
    /** 日记摘录：首段（空行分段），段内换行折成空格，视图侧再截两行。 */
    private static String excerptOf(String text) {
        String t = nz(text).trim();
        int blank = t.indexOf("\n\n");
        String first = blank >= 0 ? t.substring(0, blank) : t;
        return first.replace('\n', ' ').trim();
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

    // ---------------- 写库（v1.67.0 起：本地先行＋待同步队列，见下节） ----------------
    private interface Done { void ok(); }
    private interface DoneVal<T> { void ok(T v); }

    // ---------------- 待同步队列（v1.67.0，F1 本地先行＋防冲突） ----------------
    // 知页所有写操作（条目新建/编辑、关联增删、删除条目、日记重要性）先落本地
    // 模型＋缓存、立即生效，再把操作排进落盘队列（SharedPreferences，进程被杀
    // 不丢）；队列按序补传：新建 POST 同 id、编辑字段级 PATCH（只发改过的列）；
    // 网络一断就停、op 留队首，下次保存/回前台/切回知页再试。编辑补传前先读云端
    // updated_at：比编辑所基于的版本新 → 放弃该 op、拉回云端版本覆盖本地、
    // 页顶小字明示（全软件「本地先行＋写入防冲突」规矩的知页实现）。
    private static final String OPS_KEY = "pending_ops";
    // 补传互斥：实例 flushQueue 与静态 flushLinkOps 共用一面旗，同一时刻只许
    // 一个补传者动队首（link op 执行本身幂等，但「执行→摘队首」必须配对成串）
    private static final Object QUEUE_LOCK = new Object();
    private static boolean queueFlushing;

    /** 补传时发现云端已在别处更新（与网络失败相区分：网络失败留队重试）。 */
    private static final class Conflict extends Exception { }

    private JSONObject newOp(String kind) {
        JSONObject op = new JSONObject();
        try { op.put("k", kind); } catch (Exception ignored) { }
        return op;
    }
    private static synchronized JSONArray loadOps(Context c) {
        try {
            String raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(OPS_KEY, "[]");
            return new JSONArray(raw == null ? "[]" : raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }
    private static synchronized void saveOps(Context c, JSONArray ops) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(OPS_KEY, ops.toString()).apply();
    }
    private void enqueueOp(JSONObject op) { enqueueOp(act, op); }
    private static void enqueueOp(Context c, JSONObject op) {
        synchronized (ZhiPage.class) {
            JSONArray ops = loadOps(c);
            ops.put(op);
            saveOps(c, ops);
        }
    }
    private int pendingCount() {
        synchronized (ZhiPage.class) { return loadOps(act).length(); }
    }
    private Set<String> pendingDiaryImpDates() {
        Set<String> out = new HashSet<>();
        try {
            JSONArray ops = loadOps(act);
            for (int i = 0; i < ops.length(); i++) {
                JSONObject op = ops.getJSONObject(i);
                if ("diary_imp".equals(op.optString("k"))) out.add(op.optString("date"));
            }
        } catch (Exception ignored) { }
        return out;
    }
    private static void dropHeadOp(Context c) {
        synchronized (ZhiPage.class) {
            JSONArray ops = loadOps(c);
            JSONArray rest = new JSONArray();
            for (int i = 1; i < ops.length(); i++) rest.put(ops.opt(i));
            saveOps(c, rest);
        }
    }
    private static String tableOfType(String type) {
        return T_PRINCIPLE.equals(type) ? TB_PRINCIPLES
                : T_QUESTION.equals(type) ? TB_QUESTIONS : TB_PEOPLE;
    }
    private static JSONObject copyJson(JSONObject o) {
        try { return new JSONObject(o.toString()); }
        catch (Exception e) { return new JSONObject(); }
    }
    /** 知页顶部的待同步小字（队列非空时渲染进列表页/编辑页）。 */
    private void addPendingLine(LinearLayout col) {
        int n = pendingCount();
        if (n <= 0) return;
        TextView t = tv(s("有 " + n + " 条改动待同步", n + " change(s) waiting to sync"), 11.5f, subC());
        t.setPadding(0, dp(6), 0, 0);
        col.addView(t);
    }

    /** 队列补传（后台线程按序执行；任一 op 遇网络/服务端失败即停，留待下次触发）。 */
    private void flushQueue() {
        if (!Supa.isLoggedIn(act)) return;
        synchronized (QUEUE_LOCK) {
            if (queueFlushing) return;
            queueFlushing = true;
        }
        new Thread(() -> {
            boolean conflict = false;
            String conflictTable = null;
            try {
                while (true) {
                    JSONArray ops = loadOps(act);
                    if (ops.length() == 0) break;
                    JSONObject op = ops.getJSONObject(0);
                    try {
                        execOp(op);
                        dropHeadOp(act);
                    } catch (Conflict cf) {
                        dropHeadOp(act);
                        conflict = true;
                        conflictTable = tableOfType(op.optString("type"));
                    }
                }
            } catch (Exception ignored) {
                // 网络等失败：op 留在队首，下次触发再试（页面有「待同步」小字明示）
            }
            synchronized (QUEUE_LOCK) { queueFlushing = false; }
            if (conflictTable != null) fetchTable(conflictTable);
            final boolean cf = conflict;
            act.runOnUiThread(() -> {
                if (cf) topNotice = s("这条已在别处更新，已按最新显示",
                        "This one was updated elsewhere — showing the latest");
                if (!editing && !diaryDetail) renderList();
            });
        }).start();
    }

    /** link_add 的云端执行（实例补传与静态 flushLinkOps 共用同一实现，v1.71.0）。 */
    private static void execLinkAdd(Context c, JSONObject op) throws Exception {
        JSONObject row = new JSONObject();
        row.put("id", op.getString("linkId"));
        row.put("user_id", Supa.userId(c));
        row.put("a_type", op.getString("aType"));
        row.put("a_id", op.getString("aId"));
        row.put("b_type", op.getString("bType"));
        row.put("b_id", op.getString("bId"));
        row.put("created_at", op.getString("at"));
        Supa.restUpsertOne(c, TB_LINKS, "user_id,a_type,a_id,b_type,b_id", row);
    }
    /** link_del 的云端执行（同上共用）。 */
    private static void execLinkDel(Context c, JSONObject op) throws Exception {
        Supa.restDelete(c, "zhi_links?a_type=eq." + op.getString("aType")
                + "&a_id=eq." + op.getString("aId")
                + "&b_type=eq." + op.getString("bType") + "&b_id=eq." + op.getString("bId"));
    }

    private void execOp(JSONObject op) throws Exception {
        String k = op.optString("k");
        if ("upsert".equals(k)) {
            execUpsert(op);
        } else if ("link_add".equals(k)) {
            execLinkAdd(act, op);
        } else if ("link_del".equals(k)) {
            execLinkDel(act, op);
        } else if ("delete".equals(k)) {
            String type = op.getString("type"), id = op.getString("id");
            Supa.restDelete(act, "zhi_links?a_type=eq." + type + "&a_id=eq." + id);
            Supa.restDelete(act, "zhi_links?b_type=eq." + type + "&b_id=eq." + id);
            Supa.restDelete(act, tableOfType(type) + "?id=eq." + id);
        } else if ("diary_imp".equals(k)) {
            // 只动 user_state.diary_importance 一列、只改该日期键：先拉最新再合并写回
            String date = op.getString("date");
            int val = op.getInt("value");
            JSONArray us = Supa.restGet(act, "user_state?select=diary_importance");
            JSONObject root = us.length() > 0
                    ? us.getJSONObject(0).optJSONObject("diary_importance") : null;
            if (root == null) root = new JSONObject();
            DiaryImportance.put(root, date, val);
            JSONObject write = new JSONObject();
            write.put("user_id", Supa.userId(act));
            write.put("diary_importance", root);
            Supa.restUpsertOne(act, "user_state", "user_id", write);
        }
    }

    private void execUpsert(JSONObject op) throws Exception {
        final String type = op.getString("type");
        final String table = tableOfType(type);
        final String id = op.getString("id");
        final JSONObject fields = op.getJSONObject("fields");
        if (op.optBoolean("isNew", false)) {
            JSONObject row = copyJson(fields);
            if (row.has("wantShort")) {
                // 短题：本地先存的是截断兜底，补传时才走 AI 生成（失败内部仍兜底）
                row.remove("wantShort");
                row.put("short_title", genShortTitle(act,
                        row.optString("title", ""), row.optString("body", "")));
            }
            row.put("id", id);
            row.put("user_id", Supa.userId(act));
            row.put("created_at", op.getString("at"));
            row.put("updated_at", isoNow());
            if (!T_PERSON.equals(type)) row.put("source", "app");
            JSONArray back = Supa.restUpsertOne(act, table, "id", row);
            if (back.length() == 0) throw new Exception("empty back");
            applySavedRow(type, back.getJSONObject(0));
            return;
        }
        // 编辑：先核云端 updated_at，没被别处更新才字段级 PATCH
        String base = op.optString("base", "");
        JSONArray cur = Supa.restGet(act, table + "?id=eq." + id + "&select=updated_at");
        if (cur.length() == 0) {
            // 云端这条已不在（别处删了）：以本地整条为准建回
            JSONObject row = fullRowOf(type, id);
            if (row == null) return;   // 本地也没有（其后被删，队列里有 delete op 兜底）
            row.put("updated_at", isoNow());
            JSONArray back = Supa.restUpsertOne(act, table, "id", row);
            if (back.length() > 0) applySavedRow(type, back.getJSONObject(0));
            return;
        }
        String cloudUpd = cur.getJSONObject(0).optString("updated_at", "");
        if (!base.isEmpty() && !cloudUpd.isEmpty() && cloudUpd.compareTo(base) > 0) {
            throw new Conflict();
        }
        JSONObject patch = copyJson(fields);
        patch.put("updated_at", isoNow());
        JSONArray back = Supa.restPatch(act, table + "?id=eq." + id, patch);
        if (back.length() > 0) applySavedRow(type, back.getJSONObject(0));
    }

    /** 服务端回行并入本地模型（模型只在 UI 线程动：投递过去并等它落完）。 */
    private void applySavedRow(final String type, final JSONObject rowJson) {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        act.runOnUiThread(() -> {
            try {
                if (T_PRINCIPLE.equals(type)) replacePrinciple(parsePrinciple(rowJson));
                else if (T_QUESTION.equals(type)) replaceQuestion(parseQuestion(rowJson));
                else replacePerson(parsePerson(rowJson));
                saveCache();
            } catch (Exception ignored) { }
            latch.countDown();
        });
        try { latch.await(15, java.util.concurrent.TimeUnit.SECONDS); }
        catch (Exception ignored) { }
    }

    /** 本地模型整条拼成云端行（云端行丢失时建回用）。 */
    private JSONObject fullRowOf(String type, String id) {
        try {
            JSONObject row = new JSONObject();
            row.put("id", id);
            row.put("user_id", Supa.userId(act));
            row.put("created_at", isoNow());
            if (T_PRINCIPLE.equals(type)) {
                Principle p = principleById(id);
                if (p == null) return null;
                row.put("kind", p.kind); row.put("title", p.title); row.put("body", p.body);
                row.put("tags", tagsJson(p.tags)); row.put("importance", p.importance);
                row.put("source", "app");
            } else if (T_QUESTION.equals(type)) {
                Question q = questionById(id);
                if (q == null) return null;
                row.put("title", q.title); row.put("body", q.body); row.put("status", q.status);
                row.put("short_title", q.shortTitle);
                row.put("tags", tagsJson(q.tags)); row.put("importance", q.importance);
                row.put("source", "app");
            } else {
                Person pe = personById(id);
                if (pe == null) return null;
                row.put("name", pe.name); row.put("memo", pe.memo);
                row.put("tags", tagsJson(pe.tags)); row.put("importance", pe.importance);
            }
            return row;
        } catch (Exception e) {
            return null;
        }
    }

    /** 规范形：类型名字母序在前（diary < person < principle < question < task，日记恒为 a 侧）。
     *  返回 {aType, aId, bType, bId}；知页内外共用，不许另起一套。 */
    private static String[] normLink(String t1, String id1, String t2, String id2) {
        if (t1.compareTo(t2) <= 0) return new String[]{t1, id1, t2, id2};
        return new String[]{t2, id2, t1, id1};
    }
    private static JSONObject linkAddOp(String aT, String aI, String bT, String bI) {
        try {
            JSONObject op = new JSONObject();
            op.put("k", "link_add");
            op.put("linkId", UUID.randomUUID().toString());
            op.put("aType", aT); op.put("aId", aI);
            op.put("bType", bT); op.put("bId", bI);
            op.put("at", isoNow());
            return op;
        } catch (Exception e) { return null; }
    }
    private static JSONObject linkDelOp(String aT, String aI, String bT, String bI) {
        try {
            JSONObject op = new JSONObject();
            op.put("k", "link_del");
            op.put("aType", aT); op.put("aId", aI);
            op.put("bType", bT); op.put("bId", bI);
            return op;
        } catch (Exception e) { return null; }
    }

    private void addLink(final String t1, final String id1, final String t2, final String id2,
                         final Done done) {
        String[] n = normLink(t1, id1, t2, id2);
        final String aT = n[0], aI = n[1], bT = n[2], bI = n[3];
        if (isLinked(t1, id1, t2, id2)) { if (done != null) done.ok(); return; }
        // 本地先行：先并入本地关联、立即生效，再排队补传
        Link l = new Link();
        l.aType = aT; l.aId = aI; l.bType = bT; l.bId = bI;
        links.add(l);
        saveCache();
        JSONObject op = linkAddOp(aT, aI, bT, bI);
        if (op != null) enqueueOp(op);
        if (done != null) done.ok();
        flushQueue();
    }

    private void removeLink(final Link l, final Done done) {
        // 本地先行：先解除本地关联、立即生效，再排队补传
        links.remove(l);
        saveCache();
        JSONObject op = linkDelOp(l.aType, l.aId, l.bType, l.bId);
        if (op != null) enqueueOp(op);
        if (done != null) done.ok();
        flushQueue();
    }

    private void deleteItem(final String type, final String id, final Done done) {
        // 本地先行：先从本地模型与缓存摘掉（含它的全部关联），再排队补传删除
        links.removeIf(l -> l.involves(type, id));
        if (T_PRINCIPLE.equals(type)) principles.removeIf(p -> p.id.equals(id));
        else if (T_QUESTION.equals(type)) questions.removeIf(q -> q.id.equals(id));
        else people.removeIf(pe -> pe.id.equals(id));
        saveCache();
        try {
            JSONObject op = newOp("delete");
            op.put("type", type);
            op.put("id", id);
            enqueueOp(op);
        } catch (Exception ignored) { }
        if (done != null) done.ok();
        flushQueue();
    }

    // ---------------- 跨界面关联接口（v1.71.0：任务编辑层／任务行／删任务共用） ----------------
    // 知页之外的界面读写关联走这一组静态入口：读的是知页同一份本地缓存
    // （zhi_cache 里的四表 JSON），写的是同一条待同步队列（同 op 形状、同
    // execLinkAdd/execLinkDel 执行）——本地先行口径与知页内完全一致：
    // 先改缓存、再入队、再试补传；不许另起直连云端的写。
    private static JSONArray cacheArray(Context c, String key) {
        try {
            String raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(key, null);
            return raw == null ? new JSONArray() : new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }
    private static void saveLinksCache(Context c, JSONArray linksArr) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("links", linksArr.toString()).apply();
    }
    /** 本地缓存里的全部关联（缓存从未建过时为空表——调用方据此不显示，绝不阻塞）。 */
    public static List<Link> linksCache(Context c) {
        List<Link> out = new ArrayList<>();
        try {
            JSONArray arr = cacheArray(c, "links");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Link l = new Link();
                l.aType = o.optString("a_type", "");
                l.aId = o.optString("a_id", "");
                l.bType = o.optString("b_type", "");
                l.bId = o.optString("b_id", "");
                out.add(l);
            }
        } catch (Exception ignored) { }
        return out;
    }
    public static List<Person> peopleCache(Context c) {
        List<Person> out = new ArrayList<>();
        try {
            JSONArray arr = cacheArray(c, "people");
            for (int i = 0; i < arr.length(); i++) out.add(parsePerson(arr.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }
    public static List<Principle> principlesCache(Context c) {
        List<Principle> out = new ArrayList<>();
        try {
            JSONArray arr = cacheArray(c, "principles");
            for (int i = 0; i < arr.length(); i++) out.add(parsePrinciple(arr.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }
    private static boolean cacheHasLink(JSONArray arr, String aT, String aI, String bT, String bI) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (aT.equals(o.optString("a_type")) && aI.equals(o.optString("a_id"))
                    && bT.equals(o.optString("b_type")) && bI.equals(o.optString("b_id"))) return true;
        }
        return false;
    }
    /** 外部加关联（任务编辑层用）：规范形→先落缓存→入队→试补传，与知页 addLink 同口径。 */
    public static void addLinkExternal(Context c, String t1, String id1, String t2, String id2) {
        String[] n = normLink(t1, id1, t2, id2);
        try {
            JSONArray arr = cacheArray(c, "links");
            if (!cacheHasLink(arr, n[0], n[1], n[2], n[3])) {
                JSONObject o = new JSONObject();
                o.put("a_type", n[0]); o.put("a_id", n[1]);
                o.put("b_type", n[2]); o.put("b_id", n[3]);
                arr.put(o);
                saveLinksCache(c, arr);
            }
        } catch (Exception ignored) { }
        JSONObject op = linkAddOp(n[0], n[1], n[2], n[3]);
        if (op != null) enqueueOp(c, op);
        flushLinkOps(c);
    }
    /** 外部解除关联（入参任意顺序，内部规范化）。 */
    public static void removeLinkExternal(Context c, String t1, String id1, String t2, String id2) {
        String[] n = normLink(t1, id1, t2, id2);
        try {
            JSONArray arr = cacheArray(c, "links");
            JSONArray kept = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (n[0].equals(o.optString("a_type")) && n[1].equals(o.optString("a_id"))
                        && n[2].equals(o.optString("b_type")) && n[3].equals(o.optString("b_id"))) continue;
                kept.put(o);
            }
            saveLinksCache(c, kept);
        } catch (Exception ignored) { }
        JSONObject op = linkDelOp(n[0], n[1], n[2], n[3]);
        if (op != null) enqueueOp(c, op);
        flushLinkOps(c);
    }
    /** 某条目（如被删的任务）的全部关联一并解除并入队，不留孤儿关联。 */
    public static void removeAllLinksFor(Context c, String type, String id) {
        List<Link> mine = new ArrayList<>();
        for (Link l : linksCache(c)) if (l.involves(type, id)) mine.add(l);
        for (Link l : mine) removeLinkExternal(c, l.aType, l.aId, l.bType, l.bId);
    }
    /**
     * 只补传队首的 link op：遇到其他 kind 就停（全局 FIFO 不乱序——排在 upsert
     * 之后的 link 等知页 flushQueue 整队补传，与既有触发点口径一致）。与实例
     * flushQueue 共用 queueFlushing 旗，两个补传者不会同时动队首。
     */
    public static void flushLinkOps(final Context c) {
        if (!Supa.isLoggedIn(c)) return;
        synchronized (QUEUE_LOCK) {
            if (queueFlushing) return;
            queueFlushing = true;
        }
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                while (true) {
                    JSONArray ops = loadOps(app);
                    if (ops.length() == 0) break;
                    JSONObject op = ops.getJSONObject(0);
                    String k = op.optString("k");
                    if ("link_add".equals(k)) execLinkAdd(app, op);
                    else if ("link_del".equals(k)) execLinkDel(app, op);
                    else break;
                    dropHeadOp(app);
                }
            } catch (Exception ignored) {
                // 网络等失败：op 留在队首，下次触发再试
            }
            synchronized (QUEUE_LOCK) { queueFlushing = false; }
        }).start();
    }
    /** 任务关联到的对象名（人名姓名／原则标题），按缓存里的关联顺序；解析不到的跳过。 */
    public static List<String> taskLinkNames(Context c, String taskId) {
        List<String> out = new ArrayList<>();
        List<Person> ppl = null;
        List<Principle> prs = null;
        for (Link l : linksCache(c)) {
            if (!l.involves(T_TASK, taskId)) continue;
            String ot = l.otherType(T_TASK, taskId), oid = l.otherId(T_TASK, taskId);
            if (T_PERSON.equals(ot)) {
                if (ppl == null) ppl = peopleCache(c);
                for (Person pe : ppl) if (pe.id.equals(oid) && !pe.name.isEmpty()) { out.add(pe.name); break; }
            } else if (T_PRINCIPLE.equals(ot)) {
                if (prs == null) prs = principlesCache(c);
                for (Principle p : prs) if (p.id.equals(oid)) {
                    String d = displayOf(p);
                    if (!d.isEmpty()) out.add(d);
                    break;
                }
            }
        }
        return out;
    }
    /** F4 小字一行：无关联返回 null（调用方整行不画）；超 2 个显示前 2 个＋总数。 */
    public static String taskLinkLine(Context c, String taskId) {
        List<String> names = taskLinkNames(c, taskId);
        if (names.isEmpty()) return null;
        if (names.size() <= 2) return String.join("、", names);
        return names.get(0) + "、" + names.get(1)
                + (Strings.en() ? " and " + names.size() + " linked" : " 等 " + names.size() + " 个");
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
        if (topNotice != null) {
            TextView nt = tv(topNotice, 12, redC());
            nt.setPadding(0, dp(6), 0, 0);
            nt.setOnClickListener(v -> { topNotice = null; renderList(); });
            col.addView(nt);
        }
        addPendingLine(col);
        String staleTbl = staleTableForPage();
        if (staleTbl != null) {
            // 失败必须明示（v1.67.0）：这一页的数据没拉到最新，给可点的重试
            final String tbl = staleTbl;
            TextView hint = tv(s("没拉到最新，点这里重试", "Couldn't load the latest — tap to retry"), 12, redC());
            hint.setPadding(0, dp(6), 0, 0);
            hint.setOnClickListener(v -> fetchTable(tbl));
            col.addView(hint);
        } else if (offline) {
            TextView off = tv(s("离线，显示上次的", "Offline — showing the last copy"), 12, redC());
            off.setPadding(0, dp(6), 0, 0);
            col.addView(off);
        }

        // 四段切换：原则 / 问题 / 人名 / 日记
        LinearLayout seg = new LinearLayout(act);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(roundBg(washC(), 0, 9));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        segLp.topMargin = dp(14);
        col.addView(seg, segLp);
        for (int i = 0; i < PAGE_NAMES.length; i++) {
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

        // 筛选行（v1.67.0）：最前面永远钉 ★★ 与 ★（独立星筛选维度，与 tag 筛选
        // 是 AND；tag 之间仍 OR）；点已选中的星取消。日记页无筛选行。
        if (page != PAGE_DIARY) {
            FlowLayout flow = new FlowLayout(act, dp(6), dp(6));
            LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            fLp.topMargin = dp(10);
            col.addView(flow, fLp);
            for (final int sv : new int[]{2, 1}) {
                boolean on = starSel[page] == sv;
                TextView chip = tv(sv == 2 ? "★★" : "★", 11.5f, on ? cardC() : redC());
                chip.setPadding(dp(11), dp(3), dp(11), dp(3));
                chip.setBackground(roundBg(on ? redC() : Color.TRANSPARENT, on ? 0 : redC(), 20));
                chip.setOnClickListener(v -> {
                    starSel[page] = starSel[page] == sv ? 0 : sv;
                    renderList();
                });
                flow.addView(chip);
            }
            for (final String tag : pageTags()) {
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
        if (page != PAGE_DIARY) {   // 日记页工具行只有计数（无复制、无卡片/列表双模式）
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
        if (page != PAGE_DIARY) r.addView(fabBtn, fabLp);   // 日记不在这里新建（晨间页与习惯页才是写日记的入口）
        fab = fabBtn;

        renderResults();
    }

    private String pageType() {
        return page == PAGE_PRINCIPLE ? T_PRINCIPLE : page == PAGE_QUESTION ? T_QUESTION
                : page == PAGE_DIARY ? T_DIARY : T_PERSON;
    }

    private boolean hitTags(List<String> tags) {
        Set<String> sel = tagSel.get(page);
        if (sel.isEmpty()) return true;
        for (String t : tags) if (sel.contains(t)) return true;
        return false;
    }
    /** 星筛选（v1.67.0）：starSel 为 0 不筛，否则只收该星级；与 tag 筛选是 AND。 */
    private boolean hitImportance(int imp) {
        int sel = starSel[page];
        return sel == 0 || imp == sel;
    }
    private static String stars(int imp) {
        return imp >= 2 ? "★★" : imp == 1 ? "★" : "";
    }
    /** 当前页对应的表若没拉到最新（stale）返回表名；关联表 stale 对所有页都算。 */
    private String staleTableForPage() {
        String t = page == PAGE_PRINCIPLE ? TB_PRINCIPLES : page == PAGE_QUESTION ? TB_QUESTIONS
                : page == PAGE_PERSON ? TB_PEOPLE : null;
        if (t != null && staleTables.contains(t)) return t;
        if (staleTables.contains(TB_LINKS)) return TB_LINKS;
        return null;
    }
    private List<Principle> filteredPrinciples() {
        String q = queries[page].trim();
        List<Principle> out = new ArrayList<>();
        for (Principle p : principles) {
            if (!hitTags(p.tags)) continue;
            if (!hitImportance(p.importance)) continue;
            if (!q.isEmpty() && !(p.title.contains(q) || p.body.contains(q))) continue;
            out.add(p);
        }
        // 星级降序为第一排序键，同星级保持原有次序（List.sort 稳定）
        out.sort((a, b) -> Integer.compare(b.importance, a.importance));
        return out;
    }
    private List<Question> filteredQuestions() {
        String q = queries[page].trim();
        List<Question> out = new ArrayList<>();
        for (Question qs : questions) {
            if (!hitTags(qs.tags)) continue;
            if (!hitImportance(qs.importance)) continue;
            if (!q.isEmpty() && !(qs.title.contains(q) || qs.body.contains(q))) continue;
            out.add(qs);
        }
        out.sort((a, b) -> Integer.compare(b.importance, a.importance));
        return out;
    }
    private List<Person> filteredPeople() {
        String q = queries[page].trim();
        List<Person> out = new ArrayList<>();
        for (Person pe : people) {
            if (!hitTags(pe.tags)) continue;
            if (!hitImportance(pe.importance)) continue;
            if (!q.isEmpty() && !(pe.name.contains(q) || pe.memo.contains(q))) continue;
            out.add(pe);
        }
        out.sort((a, b) -> Integer.compare(b.importance, a.importance));
        return out;
    }

    private void renderResults() {
        if (resultsCol == null) return;
        resultsCol.removeAllViews();
        groupHeads.clear();
        if (rail != null) { rail.removeAllViews(); rail.setVisibility(View.GONE); }
        if (page == PAGE_PRINCIPLE) renderPrincipleResults();
        else if (page == PAGE_QUESTION) renderQuestionResults();
        else if (page == PAGE_DIARY) renderDiaryResults();
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
        // 摘要纯文本：记号换成显示名，不露原始记号（v1.61.0；点击打开在编辑面）
        TextView p = tv(WikiLinks.plainDisplay(act, firstLine(body)), 12, subC());
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
        LinearLayout kindRow = new LinearLayout(act);
        kindRow.setOrientation(LinearLayout.HORIZONTAL);
        kindRow.setGravity(Gravity.CENTER_VERTICAL);
        kindRow.addView(tv("think".equals(p.kind) ? s("思维", "Thinking") : s("行为", "Action"), 10.5f, redC()));
        if (p.importance > 0) {
            View ksp = new View(act);
            kindRow.addView(ksp, new LinearLayout.LayoutParams(0, 1, 1));
            kindRow.addView(tv(stars(p.importance), 12, redC()));
        }
        box.addView(kindRow);
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
        if (p.importance > 0) {
            TextView stv = tv(stars(p.importance), 12, redC());
            stv.setPadding(dp(8), 0, 0, 0);
            row.addView(stv);
        }
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
        if (q.importance > 0) {
            TextView stv = tv(stars(q.importance), 12, redC());
            stv.setPadding(0, dp(2), 0, dp(2));
            meta.addView(stv);
        }
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
        if (q.importance > 0) {
            TextView stv = tv(stars(q.importance), 12, redC());
            stv.setPadding(dp(8), 0, 0, 0);
            row.addView(stv);
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
            if (c != 0) return c;   // 拼音分组不破组
            c = Integer.compare(b.importance, a.importance);   // 组内星级降序（v1.67.0）
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
        if (pe.importance > 0) {
            TextView stv = tv(stars(pe.importance), 12.5f, redC());
            LinearLayout.LayoutParams sLp2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sLp2.leftMargin = dp(8);
            row.addView(stv, sLp2);
        }
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
        if (pe.importance > 0) {
            TextView stv = tv(stars(pe.importance), 12, redC());
            stv.setPadding(dp(8), 0, 0, 0);
            row.addView(stv);
        }
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


    // ---------------- 日记页签（v1.60.0） ----------------
    // 日记文本住 HabitData：读经 HabitData.diary / diaryDatesDesc，写只经 putDiary＋save，
    // 与晨间页、DiaryActivity 同一条路；关联在 zhi_links 里 diary 恒为 a 侧、a_id＝日期串。

    private void renderDiaryResults() {
        habitRoot = HabitData.load(act);
        diaryTitlesRoot = DiaryTitles.load(act);
        diaryImpRoot = DiaryImportance.load(act);
        List<String> dates = filteredDiaryDates();
        countView.setText(s(dates.size() + " 篇", dates.size() + " entries"));
        if (dates.isEmpty()) { resultsCol.addView(emptyHint()); return; }
        for (final String date : dates) resultsCol.addView(diaryRow(date));
    }

    /** 有文本的日期倒序；搜索＝正文字面包含（输字只重画结果区，与三页同款）。 */
    private List<String> filteredDiaryDates() {
        String q = queries[PAGE_DIARY].trim();
        List<String> out = new ArrayList<>();
        for (String date : HabitData.diaryDatesDesc(habitRoot)) {
            String text = HabitData.diary(habitRoot, date);
            if (text.trim().isEmpty()) continue;
            if (!q.isEmpty() && !text.contains(q)) continue;
            out.add(date);
        }
        return out;
    }

    private View diaryRow(final String date) {
        String text = HabitData.diary(habitRoot, date);
        LinearLayout box = cardBox();
        // 标题行：有标题显示「YYMMDD 主题」，没标题只显示日期（v1.61.0）
        String dTitle = diaryTitlesRoot == null ? "" : DiaryTitles.title(diaryTitlesRoot, date);
        LinearLayout titleRow = new LinearLayout(act);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView dTitleV = serif(dTitle.isEmpty() ? dateLabel(date)
                : DiaryTitles.yymmdd(date) + " " + dTitle, 15.5f, inkC(), true);
        titleRow.addView(dTitleV, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        int dImp = diaryImpRoot == null ? 0 : DiaryImportance.of(diaryImpRoot, date);
        if (dImp > 0) titleRow.addView(tv(stars(dImp), 12.5f, redC()));
        box.addView(titleRow);
        TextView ex = tv(WikiLinks.plainDisplay(act, excerptOf(text)), 12, subC());
        ex.setMaxLines(2);
        ex.setEllipsize(TextUtils.TruncateAt.END);
        ex.setLineSpacing(0, 1.35f);
        ex.setPadding(0, dp(3), 0, 0);
        box.addView(ex);
        int pn = linkedIds(T_DIARY, date, T_PRINCIPLE).size();
        int hn = linkedIds(T_DIARY, date, T_PERSON).size();
        if (pn > 0 || hn > 0) {
            List<String> parts = new ArrayList<>();
            if (pn > 0) parts.add(s("原则 ", "Principles ") + pn);
            if (hn > 0) parts.add(s("人 ", "People ") + hn);
            TextView c = tv(TextUtils.join(" · ", parts), 11, weakC());
            c.setPadding(0, dp(6), 0, 0);
            box.addView(c);
        }
        box.setOnClickListener(v -> openDiaryDetail(date));
        return box;
    }

    private void openDiaryDetail(String date) {
        diaryFromEditor = editing && es != null;
        diaryDate = date;
        diaryDetail = true;
        diaryImpRoot = DiaryImportance.load(act);
        diaryImpSel = DiaryImportance.of(diaryImpRoot, date);
        diaryAddingType = null;
        diaryIn = null;
        renderDiaryDetail();
    }

    private void backFromDiaryDetail() {
        diaryDetail = false;
        diaryAddingType = null;
        diaryIn = null;
        if (diaryFromEditor && es != null) {
            diaryFromEditor = false;
            renderEdit();
        } else {
            diaryFromEditor = false;
            page = PAGE_DIARY;
            renderList();
        }
    }

    private void renderDiaryDetail() {
        final String date = diaryDate;
        // 重画（加/解除关联、同步刷新）不丢手头没存的文字；正文按存储记号回收（v1.61.0）
        final String keep = diaryIn != null ? WikiLinks.extractText(diaryIn) : null;
        final String keepTitle = diaryTitleIn != null ? diaryTitleIn.getText().toString() : null;
        habitRoot = HabitData.load(act);
        diaryTitlesRoot = DiaryTitles.load(act);
        FrameLayout r = ensureRoot();
        r.removeAllViews();
        groupHeads.clear();

        ScrollView scroll = new ScrollView(act);
        scroll.setBackgroundColor(Ui.bg(act));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(16), dp(18), dp(40));
        scroll.addView(col);
        r.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView back = tv("‹ " + s("日记", "Diary"), 13, subC());
        back.setPadding(0, dp(4), 0, dp(2));
        back.setOnClickListener(v -> backFromDiaryDetail());
        col.addView(back);

        // 标题行：有标题显示「YYMMDD 主题」，没标题只显示日期（v1.61.0）
        String dTitle0 = DiaryTitles.title(diaryTitlesRoot, date);
        TextView title = serif(dTitle0.isEmpty() ? dateLabel(date)
                : DiaryTitles.yymmdd(date) + " " + dTitle0, 19, inkC(), true);
        title.setPadding(0, dp(10), 0, 0);
        col.addView(title);

        col.addView(fieldLabel(s("日 记 标 题", "Entry title")));
        diaryTitleIn = fieldBox(keepTitle != null ? keepTitle : dTitle0,
                s("主题，10 字以内（可空）", "Theme, up to 10 characters (optional)"), 1);
        col.addView(diaryTitleIn);

        // 重要性（v1.67.0）：与三类条目同一口径的三段选择器，随下方「保存」一起
        // 落本地 DiaryImportance 并排队对云端 diary_importance 列读改写
        if (diaryImpRoot == null) diaryImpRoot = DiaryImportance.load(act);
        col.addView(fieldLabel(s("重 要 性", "Importance")));
        LinearLayout impSeg = new LinearLayout(act);
        impSeg.setOrientation(LinearLayout.HORIZONTAL);
        impSeg.setBackground(roundBg(washC(), 0, 9));
        impSeg.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (final int sv : new int[]{0, 1, 2}) {
            boolean on = diaryImpSel == sv;
            TextView t = tv(sv == 0 ? s("无星", "No star")
                    : sv == 1 ? s("★ 一星", "★ 1 star") : s("★★ 二星", "★★ 2 stars"),
                    13, on ? inkC() : subC());
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(7), 0, dp(7));
            if (on) {
                t.setBackground(roundBg(cardC(), 0, 7));
                t.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
            }
            t.setOnClickListener(v -> { diaryImpSel = sv; renderDiaryDetail(); });
            impSeg.addView(t, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        col.addView(impSeg);

        diaryIn = fieldBox(keep != null ? keep : HabitData.diary(habitRoot, date),
                s("写下这一天的日记", "Write this day's entry"), 8);
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inLp.topMargin = dp(14);
        col.addView(diaryIn, inLp);
        diaryHook = WikiLinks.attach(act, diaryIn, wikiHost);

        TextView save = tv(s("保 存", "Save"), 14.5f, cardC());
        save.setGravity(Gravity.CENTER);
        save.setLetterSpacing(0.3f);
        save.setBackground(roundBg(inkC(), 0, 10));
        save.setPadding(0, dp(12), 0, dp(12));
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        svLp.topMargin = dp(16);
        svLp.bottomMargin = dp(6);
        col.addView(save, svLp);
        save.setOnClickListener(v -> {
            // 写日记只经 putDiary（完成位同生同灭的口径与晨间页一致），再经 HabitData.save 落盘＋同步
            JSONObject root = HabitData.load(act);
            HabitData.putDiary(root, date, WikiLinks.extractText(diaryIn).trim());
            HabitData.save(act, root);
            habitRoot = root;
            // 标题写 DiaryTitles（独立列同步，不进 habit_data）
            JSONObject tRoot = DiaryTitles.load(act);
            DiaryTitles.putTitle(tRoot, date, diaryTitleIn.getText().toString());
            DiaryTitles.save(act, tRoot);
            diaryTitlesRoot = tRoot;
            // 重要性（v1.67.0）：变了才写——本地 DiaryImportance 先落盘，再排队补传
            if (diaryImpRoot == null) diaryImpRoot = DiaryImportance.load(act);
            if (DiaryImportance.of(diaryImpRoot, date) != diaryImpSel) {
                DiaryImportance.put(diaryImpRoot, date, diaryImpSel);
                DiaryImportance.saveQuiet(act, diaryImpRoot);
                try {
                    JSONObject op = newOp("diary_imp");
                    op.put("date", date);
                    op.put("value", diaryImpSel);
                    enqueueOp(op);
                } catch (Exception ignored) { }
                flushQueue();
            }
            WikiLinks.noteDiaryChanged(act);
            toast(s("存好了", "Saved"));
            renderDiaryDetail();
        });

        col.addView(diaryLinkSection(T_PRINCIPLE));
        col.addView(diaryLinkSection(T_PERSON));
    }

    /** 日记详情的关联区（与编辑页 linkSection 同款式）：点条目跳它的编辑页，✕ 立即解除，＋ 添加走联想（不提供新建）。 */
    private View diaryLinkSection(final String otherType) {
        final String date = diaryDate;
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
        List<String> ids = linkedIds(T_DIARY, date, otherType);
        TextView h = tv(sectionTitle(otherType) + (ids.isEmpty() ? "" : "（" + ids.size() + "）"), 12, subC());
        h.setLetterSpacing(0.15f);
        head.addView(h);
        View spacer = new View(act);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        TextView plus = tv(s("＋ 添加", "＋ Add"), 12, redC());
        plus.setPadding(dp(10), dp(2), dp(10), dp(2));
        plus.setBackground(roundBg(Color.TRANSPARENT, redC(), 14));
        plus.setOnClickListener(v -> {
            diaryAddingType = otherType.equals(diaryAddingType) ? null : otherType;
            renderDiaryDetail();
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
            name.setOnClickListener(v -> jumpToEditorFromDiary(otherType, oid));
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView x = tv("✕", 12, weakC());
            x.setPadding(dp(10), dp(2), dp(2), dp(2));
            x.setOnClickListener(v -> {
                Link found = null;
                for (Link l : linksOf(T_DIARY, date)) {
                    if (l.otherType(T_DIARY, date).equals(otherType)
                            && l.otherId(T_DIARY, date).equals(oid)) { found = l; break; }
                }
                if (found != null) removeLink(found, () -> renderDiaryDetail());
            });
            row.addView(x);
            box.addView(row);
        }

        if (otherType.equals(diaryAddingType)) box.addView(diaryAddPanel(otherType));
        return box;
    }

    private View diaryAddPanel(final String otherType) {
        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pLp.topMargin = dp(8);
        panel.setLayoutParams(pLp);

        final EditText in = new EditText(act);
        in.setHint(s("打几个字找已有的", "Type to find existing entries"));
        in.setTextSize(14);
        in.setSingleLine(true);
        in.setBackground(roundBg(cardC(), subC(), 10));
        in.setPadding(dp(12), dp(9), dp(12), dp(9));
        panel.addView(in);

        final LinearLayout cand = new LinearLayout(act);
        cand.setOrientation(LinearLayout.VERTICAL);
        cand.setBackground(roundBg(cardC(), lineC(), 10));
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cLp.topMargin = dp(6);
        panel.addView(cand, cLp);
        diaryCandBox = cand;

        in.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { fillDiaryCandidates(otherType, e.toString().trim()); }
        });
        fillDiaryCandidates(otherType, "");
        return panel;
    }

    private void fillDiaryCandidates(final String otherType, final String q) {
        if (diaryCandBox == null) return;
        LinearLayout cand = diaryCandBox;
        cand.removeAllViews();
        List<String[]> cands = new ArrayList<>();   // {id, display, haystack}
        if (T_PRINCIPLE.equals(otherType)) {
            for (Principle p : principles) cands.add(new String[]{p.id, displayOf(p), p.title + "\n" + p.body});
        } else {
            for (Person pe : people) cands.add(new String[]{pe.id, pe.name, pe.name + "\n" + pe.memo});
        }
        int shown = 0;
        for (final String[] c : cands) {
            if (isLinked(T_DIARY, diaryDate, otherType, c[0])) continue;
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
            row.setOnClickListener(v -> addLink(T_DIARY, diaryDate, otherType, c[0], () -> {
                diaryAddingType = null;
                renderDiaryDetail();
            }));
            cand.addView(row);
        }
        cand.setVisibility(cand.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    /** 从日记详情跳到某条原则/人名的编辑页：先把手头日记文字经 putDiary 存好，编辑页返回到底时回本详情页。 */
    private void jumpToEditorFromDiary(final String type, final String id) {
        if (diaryIn != null) {
            JSONObject root = HabitData.load(act);
            HabitData.putDiary(root, diaryDate, WikiLinks.extractText(diaryIn).trim());
            HabitData.save(act, root);
            habitRoot = root;
        }
        diaryDetail = false;
        diaryAddingType = null;
        diaryIn = null;
        returnToDiary = true;
        editStack.clear();
        Ref ref = new Ref(type, id);
        editStack.add(ref);
        es = stateFromModel(ref);
        editing = true;
        page = pageOfType(type);
        renderEdit();
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
        String shortTitle = "";          // 问题短题（v1.61.0）
        String kind = "think", status = "pending";
        int importance;                  // 重要性（v1.67.0）：0 无星／1 一星／2 二星
        List<String> tags = new ArrayList<>();
        String addingType;               // 哪个关联区的添加面板开着（null=都关）
        EditText titleIn, bodyIn, nameIn, memoIn, shortIn, addIn;
        LinearLayout candBox;
    }

    private static String typeName(String type) {
        return T_PRINCIPLE.equals(type) ? s("原则", "Principle")
                : T_QUESTION.equals(type) ? s("问题", "Question")
                : T_DIARY.equals(type) ? s("日记", "Diary")
                : T_TASK.equals(type) ? s("任务", "Task") : s("人名", "Person");
    }
    private int pageOfType(String type) {
        return T_PRINCIPLE.equals(type) ? PAGE_PRINCIPLE : T_QUESTION.equals(type) ? PAGE_QUESTION
                : T_DIARY.equals(type) ? PAGE_DIARY : PAGE_PERSON;
    }

    private EditState stateFromModel(Ref ref) {
        EditState st = new EditState();
        st.ref = ref;
        if (ref.id == null) return st;
        if (T_PRINCIPLE.equals(ref.type)) {
            Principle p = principleById(ref.id);
            if (p != null) { st.title = p.title; st.body = p.body; st.kind = p.kind; st.tags = new ArrayList<>(p.tags); st.importance = p.importance; }
        } else if (T_QUESTION.equals(ref.type)) {
            Question q = questionById(ref.id);
            if (q != null) { st.title = q.title; st.body = q.body; st.status = q.status; st.shortTitle = q.shortTitle; st.tags = new ArrayList<>(q.tags); st.importance = q.importance; }
        } else {
            Person pe = personById(ref.id);
            if (pe != null) { st.name = pe.name; st.memo = pe.memo; st.tags = new ArrayList<>(pe.tags); st.importance = pe.importance; }
        }
        return st;
    }

    private void collectDraft() {
        if (es == null) return;
        if (es.titleIn != null) es.title = es.titleIn.getText().toString();
        // 正文/备注是双链编辑面：回收时把链接段还原成记号再存草稿（v1.61.0）
        if (es.bodyIn != null) es.body = WikiLinks.extractText(es.bodyIn);
        if (es.nameIn != null) es.name = es.nameIn.getText().toString();
        if (es.memoIn != null) es.memo = WikiLinks.extractText(es.memoIn);
        if (es.shortIn != null) es.shortTitle = es.shortIn.getText().toString();
    }

    private void openEdit(String type, String id) {
        returnToDiary = false;
        editStack.clear();
        Ref ref = new Ref(type, id);
        editStack.add(ref);
        es = stateFromModel(ref);
        editing = true;
        page = pageOfType(type);
        renderEdit();
    }
    private void openNew(String type) {
        returnToDiary = false;
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
    /** 任务关联行点开（v1.71.0，F3）：经 MainActivity 的宿主开任务编辑层（立刻开始／
     *  专注规则／重置计时与行页同一套宿主）；不在 MainActivity 时退化为本地宿主
     *  （保存后刷新本页）。任务已删/找不到时小字提示，不硬开空层。 */
    private void openTaskEditor(final String taskId) {
        TaskStore store = new TaskStore(act);
        TaskItem t = store.byId(taskId);
        if (t == null || t.deleted) { toast(s("这个任务不在了", "That task is gone")); return; }
        if (act instanceof MainActivity) {
            ((MainActivity) act).openTaskEditorFromZhi(t);
            return;
        }
        TaskEditSheet.showEdit(act, store, t, new TaskEditSheet.Host() {
            @Override public void onSaved(TaskItem saved, boolean created) {
                editTasks = new TaskStore(act).tasks;
                renderEdit();
            }
            @Override public void onFocusRule(TaskItem task) { }
            @Override public void onResetTimer(TaskItem task) { }
        });
    }

    private void backFromEdit() {
        if (!editStack.isEmpty()) editStack.remove(editStack.size() - 1);
        if (editStack.isEmpty()) {
            es = null;
            editing = false;
            if (returnToDiary && diaryDate != null) {
                returnToDiary = false;
                openDiaryDetail(diaryDate);
                return;
            }
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
        habitRoot = HabitData.load(act);   // 日记关联区要读日记文本（悬空小字/联想候选），每次重画取最新
        editTasks = new TaskStore(act).tasks;   // 任务关联区（v1.71.0）同理每次取最新快照
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
        addPendingLine(col);

        // ---- 字段面板 ----
        if (T_PERSON.equals(type)) {
            col.addView(fieldLabel(s("名 字", "Name")));
            st.nameIn = fieldBox(st.name, s("名字", "Name"), 1);
            col.addView(st.nameIn);
            col.addView(fieldLabel(s("记 忆 点", "Memo")));
            st.memoIn = fieldBox(st.memo, s("帮你记住这个人的一段话", "A note that helps you remember this person"), 3);
            col.addView(st.memoIn);
            WikiLinks.attach(act, st.memoIn, wikiHost);
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
            if (T_QUESTION.equals(type)) {
                col.addView(fieldLabel(s("短 题", "Short title")));
                st.shortIn = fieldBox(st.shortTitle, s("引用时显示，10 字以内", "Shown in citations, up to 10 characters"), 1);
                col.addView(st.shortIn);
            }
            col.addView(fieldLabel(s("内 容", "Body")));
            st.bodyIn = fieldBox(st.body, T_QUESTION.equals(type)
                    ? s("写下这个问题", "Write the question") : s("写下这条原则", "Write the principle"), 4);
            col.addView(st.bodyIn);
            WikiLinks.attach(act, st.bodyIn, wikiHost);
            if (T_QUESTION.equals(type)) {
                col.addView(fieldLabel(s("状 态", "Status")));
                col.addView(twoChoice(new String[]{"pending", "asked"},
                        new String[]{s("待问", "To ask"), s("已问", "Asked")}, st.status,
                        v -> st.status = v));
            }
        }

        // ---- 重要性（v1.67.0）：无星／一星／二星三段点选，随「保存」走本地先行队列 ----
        col.addView(fieldLabel(s("重 要 性", "Importance")));
        col.addView(twoChoice(new String[]{"0", "1", "2"},
                new String[]{s("无星", "No star"), s("★ 一星", "★ 1 star"), s("★★ 二星", "★★ 2 stars")},
                String.valueOf(st.importance),
                v -> st.importance = Integer.parseInt(v)));

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
        if (T_PRINCIPLE.equals(type)) return new String[]{T_PERSON, T_QUESTION, T_DIARY, T_TASK};
        if (T_QUESTION.equals(type)) return new String[]{T_PERSON, T_PRINCIPLE};
        return new String[]{T_QUESTION, T_PRINCIPLE, T_DIARY, T_TASK};
    }
    private String sectionTitle(String otherType) {
        return s("关联的", "Linked ") + (T_PERSON.equals(otherType) ? s("人名", "people")
                : T_QUESTION.equals(otherType) ? s("问题", "questions")
                : T_DIARY.equals(otherType) ? s("日记", "diary")
                : T_TASK.equals(otherType) ? s("任务", "tasks") : s("原则", "principles"));
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
            String label = displayOf(otherType, oid);
            TaskItem linkedTask = T_TASK.equals(otherType) ? taskById(oid) : null;
            if (linkedTask != null && linkedTask.isDoneToday()) label += " ✓";   // 已完成的带完成标记（F3）
            TextView name = tv(label, 13.5f, inkC());
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setOnClickListener(v -> {
                if (T_DIARY.equals(otherType)) { collectDraft(); openDiaryDetail(oid); }
                else if (T_TASK.equals(otherType)) { collectDraft(); openTaskEditor(oid); }
                else pushEdit(otherType, oid);
            });
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            if (T_DIARY.equals(otherType) && habitRoot != null
                    && HabitData.diary(habitRoot, oid).trim().isEmpty()) {
                // 悬空关联：这天的日记后来被清空了，关联不自动删，点进去是空编辑框可补写
                TextView none = tv(s("这天没写", "Nothing written"), 11, weakC());
                none.setPadding(dp(8), 0, 0, 0);
                row.addView(none);
            }
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
                : T_DIARY.equals(otherType) ? s("输入日期或日记里的字找日记", "Type a date or words from an entry")
                : T_TASK.equals(otherType) ? s("输入任务名找已有的任务", "Type to find tasks")
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
        } else if (T_DIARY.equals(otherType)) {
            // 候选＝已有日记的日期（日期倒序，空文本不列）；可输日期或正文关键词过滤
            if (habitRoot == null) habitRoot = HabitData.load(act);
            for (String date : HabitData.diaryDatesDesc(habitRoot)) {
                String text = HabitData.diary(habitRoot, date);
                if (text.trim().isEmpty()) continue;
                cands.add(new String[]{date, dateLabel(date), date + "\n" + text});
            }
        } else if (T_TASK.equals(otherType)) {
            // 候选＝本地未删任务（已完成的也可连）：标题＋笔记做联想底料
            for (TaskItem t : editTasks) {
                if (t.deleted) continue;
                cands.add(new String[]{t.id, t.title, t.title + "\n" + nz(t.note)});
            }
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
        if (!q.isEmpty() && !T_DIARY.equals(otherType) && !T_TASK.equals(otherType)) {   // 日记、任务不从这里新建（无「新建并关联」行）
            TextView create = tv(s("没有合适的？新建" + typeName(otherType) + "「" + q + "」并关联",
                    "Create new: " + q), 13, subC());
            create.setPadding(dp(13), dp(10), dp(13), dp(10));
            create.setOnClickListener(v -> createAndLink(otherType, q));
            cand.addView(create);
        }
        cand.setVisibility(cand.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    /** 新建一条（以输入文字为标题/名字）并立即与当前条目关联（v1.67.0：本地先行＋排队补传）。 */
    private void createAndLink(final String newType, final String text) {
        final EditState st = es;
        final String newId = UUID.randomUUID().toString();
        try {
            JSONObject fields = new JSONObject();
            fields.put("tags", new JSONArray());
            fields.put("importance", 0);
            if (T_PRINCIPLE.equals(newType)) {
                fields.put("kind", "think");
                fields.put("title", text);
                fields.put("body", "");
                Principle np = new Principle();
                np.id = newId; np.kind = "think"; np.title = text;
                principles.add(np);
            } else if (T_QUESTION.equals(newType)) {
                fields.put("title", text);
                fields.put("body", "");
                fields.put("status", "pending");
                String fb = text.length() > 10 ? text.substring(0, 10) : text;
                fields.put("short_title", fb);
                fields.put("wantShort", true);
                Question nq = new Question();
                nq.id = newId; nq.title = text; nq.shortTitle = fb;
                questions.add(nq);
            } else {
                fields.put("name", text);
                fields.put("memo", "");
                Person np = new Person();
                np.id = newId; np.name = text;
                people.add(np);
            }
            JSONObject op = newOp("upsert");
            op.put("type", newType);
            op.put("id", newId);
            op.put("isNew", true);
            op.put("base", "");
            op.put("at", isoNow());
            op.put("fields", fields);
            enqueueOp(op);
            saveCache();
        } catch (Exception ignored) { }
        // 关联随后入队：队列 FIFO 保证条目先于关联落云
        addLink(st.ref.type, st.ref.id, newType, newId, null);
        collectDraft();
        st.addingType = null;
        renderEdit();
        toast(s("建好并关联上了", "Created and linked"));
    }

    private static Principle parsePrinciple(JSONObject o) {
        Principle p = new Principle();
        p.id = o.optString("id", "");
        p.kind = o.optString("kind", "think");
        p.title = nz(o.optString("title", ""));
        p.body = nz(o.optString("body", ""));
        p.tags = parseTags(o);
        p.importance = o.optInt("importance", 0);
        p.updatedAt = nz(o.optString("updated_at", ""));
        return p;
    }
    private static Question parseQuestion(JSONObject o) {
        Question q = new Question();
        q.id = o.optString("id", "");
        q.title = nz(o.optString("title", ""));
        q.body = nz(o.optString("body", ""));
        q.status = o.optString("status", "pending");
        q.shortTitle = nz(o.optString("short_title", ""));
        q.tags = parseTags(o);
        q.importance = o.optInt("importance", 0);
        q.updatedAt = nz(o.optString("updated_at", ""));
        return q;
    }
    private static Person parsePerson(JSONObject o) {
        Person pe = new Person();
        pe.id = o.optString("id", "");
        pe.name = nz(o.optString("name", ""));
        pe.memo = nz(o.optString("memo", ""));
        pe.tags = parseTags(o);
        pe.importance = o.optInt("importance", 0);
        pe.updatedAt = nz(o.optString("updated_at", ""));
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
        // 本地先行（v1.67.0，F1）：id 本地先生成、模型与缓存立即更新、列表即时可见；
        // 改动字段 diff 出来排进待同步队列，云端补传只发改过的列（字段级 PATCH）。
        final String id = isNew ? UUID.randomUUID().toString() : st.ref.id;
        String base = "";
        JSONObject fields = new JSONObject();
        try {
            if (T_PRINCIPLE.equals(type)) {
                Principle cur = isNew ? null : principleById(id);
                if (cur != null) base = cur.updatedAt;
                Principle np = new Principle();
                np.id = id; np.kind = st.kind; np.title = st.title.trim(); np.body = st.body.trim();
                np.tags = new ArrayList<>(st.tags); np.importance = st.importance;
                np.updatedAt = cur != null ? cur.updatedAt : "";
                if (cur == null) {
                    fields.put("kind", np.kind); fields.put("title", np.title);
                    fields.put("body", np.body); fields.put("tags", tagsJson(np.tags));
                    fields.put("importance", np.importance);
                } else {
                    if (!np.kind.equals(cur.kind)) fields.put("kind", np.kind);
                    if (!np.title.equals(cur.title)) fields.put("title", np.title);
                    if (!np.body.equals(cur.body)) fields.put("body", np.body);
                    if (!tagsJson(np.tags).toString().equals(tagsJson(cur.tags).toString()))
                        fields.put("tags", tagsJson(np.tags));
                    if (np.importance != cur.importance) fields.put("importance", np.importance);
                }
                replacePrinciple(np);
            } else if (T_QUESTION.equals(type)) {
                Question cur = isNew ? null : questionById(id);
                if (cur != null) base = cur.updatedAt;
                Question nq = new Question();
                nq.id = id; nq.title = st.title.trim(); nq.body = st.body.trim();
                nq.status = st.status; nq.tags = new ArrayList<>(st.tags);
                nq.importance = st.importance;
                nq.updatedAt = cur != null ? cur.updatedAt : "";
                String shortT = st.shortTitle.trim();
                if (cur == null) {
                    if (shortT.isEmpty()) {
                        // 短题先用截断兜底落本地（不为等 AI 堵住保存），AI 生成留到补传时
                        String fbSrc = !nq.title.isEmpty() ? nq.title : firstLine(nq.body);
                        shortT = fbSrc.length() > 10 ? fbSrc.substring(0, 10) : fbSrc;
                        fields.put("wantShort", true);
                    }
                    nq.shortTitle = shortT;
                    fields.put("title", nq.title); fields.put("body", nq.body);
                    fields.put("status", nq.status); fields.put("short_title", shortT);
                    fields.put("tags", tagsJson(nq.tags)); fields.put("importance", nq.importance);
                } else {
                    nq.shortTitle = shortT;
                    if (!nq.title.equals(cur.title)) fields.put("title", nq.title);
                    if (!nq.body.equals(cur.body)) fields.put("body", nq.body);
                    if (!nq.status.equals(cur.status)) fields.put("status", nq.status);
                    if (!shortT.equals(cur.shortTitle)) fields.put("short_title", shortT);
                    if (!tagsJson(nq.tags).toString().equals(tagsJson(cur.tags).toString()))
                        fields.put("tags", tagsJson(nq.tags));
                    if (nq.importance != cur.importance) fields.put("importance", nq.importance);
                }
                replaceQuestion(nq);
            } else {
                Person cur = isNew ? null : personById(id);
                if (cur != null) base = cur.updatedAt;
                Person np = new Person();
                np.id = id; np.name = st.name.trim(); np.memo = st.memo.trim();
                np.tags = new ArrayList<>(st.tags); np.importance = st.importance;
                np.updatedAt = cur != null ? cur.updatedAt : "";
                if (cur == null) {
                    fields.put("name", np.name); fields.put("memo", np.memo);
                    fields.put("tags", tagsJson(np.tags)); fields.put("importance", np.importance);
                } else {
                    if (!np.name.equals(cur.name)) fields.put("name", np.name);
                    if (!np.memo.equals(cur.memo)) fields.put("memo", np.memo);
                    if (!tagsJson(np.tags).toString().equals(tagsJson(cur.tags).toString()))
                        fields.put("tags", tagsJson(np.tags));
                    if (np.importance != cur.importance) fields.put("importance", np.importance);
                }
                replacePerson(np);
            }
        } catch (Exception ignored) { }
        saveCache();
        if (isNew || fields.length() > 0) {
            try {
                JSONObject op = newOp("upsert");
                op.put("type", type);
                op.put("id", id);
                op.put("isNew", isNew);
                op.put("base", base);
                op.put("at", isoNow());
                op.put("fields", fields);
                enqueueOp(op);
            } catch (Exception ignored) { }
        }
        st.ref.id = id;
        // 以本地新值刷新草稿，再重画（关联区随之可用）
        EditState fresh = stateFromModel(st.ref);
        fresh.addingType = st.addingType;
        es = fresh;
        renderEdit();
        toast(s("存好了", "Saved"));
        flushQueue();
    }

    /**
     * 问题短题生成（v1.61.0）：经现有 chat-proxy 通道让 AI 把问题浓缩成 ≤10 字短题；
     * 失败/无网/超时/产出不合格 → 截原题前 10 字兜底（原题为空则用正文首行）。
     * 在后台线程调用（内部用 latch 等流式回完，上限 25 秒）。
     */
    static String genShortTitle(android.content.Context ctx, String title, String body) {
        String fallback = !title.trim().isEmpty() ? title.trim() : firstLine(body);
        fallback = fallback.length() > 10 ? fallback.substring(0, 10) : fallback;
        try {
            JSONArray msgs = new JSONArray();
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", "你把用户的问题浓缩成一个不超过10个汉字的短标题，只输出短标题本身，不要标点、不要引号、不要解释");
            msgs.put(sys);
            JSONObject usr = new JSONObject();
            usr.put("role", "user");
            usr.put("content", "问题：" + title + (body == null || body.trim().isEmpty() ? "" : "\n补充：" + body));
            msgs.put(usr);
            final String[] got = new String[1];
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            ChatClient.send(ctx, msgs, new ChatClient.Callback() {
                @Override public void onModel(String modelId) { }
                @Override public void onDelta(String fullText) { }
                @Override public void onDone(String fullText) { got[0] = fullText; latch.countDown(); }
                @Override public void onError(String friendlyMsg) { latch.countDown(); }
            });
            latch.await(25, java.util.concurrent.TimeUnit.SECONDS);
            if (got[0] != null) {
                String t = got[0].trim().replace("\n", "").replace(" ", "")
                        .replace("「", "").replace("」", "").replace("『", "").replace("』", "")
                        .replace("《", "").replace("》", "").replace("：", "").replace(":", "")
                        .replace("。", "").replace("？", "").replace("?", "").replace("\"", "").replace("'", "");
                if (!t.isEmpty()) return t.length() > 10 ? t.substring(0, 10) : t;
            }
        } catch (Exception ignored) { }
        return fallback;
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
