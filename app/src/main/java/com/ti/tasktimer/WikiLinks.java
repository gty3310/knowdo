package com.ti.tasktimer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「{{引用}}」双链组件（v1.61.0）：各编辑面共用。
 *
 * 正文存储记号 {{类型:编号}}，类型 person/principle/question/diary，编号为知三表
 * uuid 或日记日期 yyyy-MM-dd。编辑框里记号渲染成显示名链接（朱砂字＋下划线，
 * 显示名按编号现查：人名→name、原则→title、问题→short_title 空则 title、
 * 日记→DiaryTitles 的「YYMMDD＋标题」）；编号查不到＝断链（灰字＋虚线下划线）。
 * 点链接开 WikiSheet 浮窗编辑；输入 {{ 或 ｛｛ 弹选择窗插入记号。
 *
 * 存回正文时经 extractText 把链接段还原成记号（链接段内的文字改动不算数，
 * 以记号为准）；各编辑面的保存路径一律走 extractText，不要直接 getText()。
 *
 * 显示名快照：知三表走 Supabase 拉取＋本地缓存（wiki_cache），日记部分由本地
 * HabitData＋DiaryTitles 现拼；快照更新后所有活着的 Hook 自动重渲染。
 */
public final class WikiLinks {
    public static final String T_PERSON = "person";
    public static final String T_PRINCIPLE = "principle";
    public static final String T_QUESTION = "question";
    public static final String T_DIARY = "diary";

    static final Pattern TOKEN = Pattern.compile("\\{\\{(person|principle|question|diary):([0-9A-Za-z\\-]{4,})\\}\\}");
    private static final String PREF = "wiki_cache";
    private static final long FRESH_MS = 60_000;

    private WikiLinks() {}

    private static String s(String zh, String en) { return Strings.en() ? en : zh; }
    private static String nz(String v) { return v == null ? "" : v; }
    static String typeName(String type) {
        return T_PRINCIPLE.equals(type) ? s("原则", "Principle")
                : T_QUESTION.equals(type) ? s("问题", "Question")
                : T_DIARY.equals(type) ? s("日记", "Diary") : s("人名", "Person");
    }

    // ---------------- 词条与快照 ----------------
    public static final class Entry {
        public String type, id;
        public String name = "";      // 显示名（引用渲染用）
        public String sub = "";       // 选择窗副信息
        public String hay = "";       // 搜索全文
        // 完整字段（浮窗编辑用）
        public String kind = "think", status = "pending";
        public String title = "", body = "", shortTitle = "";
        public String personName = "", memo = "";
        public List<String> tags = new ArrayList<>();
    }

    private static List<Entry> entries;
    private static Map<String, Entry> byKey;
    private static long fetchedAt;
    private static boolean fetching;
    private static final List<WeakReference<Hook>> hooks = new ArrayList<>();

    private static String key(String type, String id) { return type + ":" + id; }

    private static List<String> parseTags(JSONObject o) {
        List<String> out = new ArrayList<>();
        JSONArray a = o.optJSONArray("tags");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            String t = a.optString(i, "").trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }
    private static String firstLine(String body) {
        String b = nz(body).trim();
        int nl = b.indexOf('\n');
        return nl >= 0 ? b.substring(0, nl).trim() : b;
    }
    private static String displayOfText(String title, String body) {
        return title != null && !title.trim().isEmpty() ? title.trim() : firstLine(body);
    }

    static Entry fromPrinciple(JSONObject o) {
        Entry e = new Entry();
        e.type = T_PRINCIPLE;
        e.id = o.optString("id", "");
        e.kind = o.optString("kind", "think");
        e.title = nz(o.optString("title", ""));
        e.body = nz(o.optString("body", ""));
        e.tags = parseTags(o);
        e.name = displayOfText(e.title, e.body);
        e.sub = ("think".equals(e.kind) ? s("思维原则", "Thinking") : s("行为原则", "Action"));
        e.hay = e.title + "\n" + e.body;
        return e;
    }
    static Entry fromQuestion(JSONObject o) {
        Entry e = new Entry();
        e.type = T_QUESTION;
        e.id = o.optString("id", "");
        e.title = nz(o.optString("title", ""));
        e.body = nz(o.optString("body", ""));
        e.shortTitle = nz(o.optString("short_title", ""));
        e.status = o.optString("status", "pending");
        e.tags = parseTags(o);
        e.name = !e.shortTitle.trim().isEmpty() ? e.shortTitle.trim() : displayOfText(e.title, e.body);
        e.sub = ("asked".equals(e.status) ? s("已问", "Asked") : s("待问", "To ask"))
                + (e.shortTitle.trim().isEmpty() ? "" : " · " + displayOfText(e.title, e.body));
        e.hay = e.title + "\n" + e.body + "\n" + e.shortTitle;
        return e;
    }
    static Entry fromPerson(JSONObject o) {
        Entry e = new Entry();
        e.type = T_PERSON;
        e.id = o.optString("id", "");
        e.personName = nz(o.optString("name", ""));
        e.memo = nz(o.optString("memo", ""));
        e.tags = parseTags(o);
        e.name = e.personName;
        e.sub = !e.tags.isEmpty() ? String.join(" · ", e.tags) : firstLine(e.memo);
        e.hay = e.personName + "\n" + e.memo;
        return e;
    }

    /** 用知三表行＋本地日记数据重建快照（日记部分永远现拼本地最新）。 */
    private static synchronized void buildSnapshot(Context c, JSONArray pRows, JSONArray qRows, JSONArray peRows) {
        List<Entry> all = new ArrayList<>();
        Map<String, Entry> map = new HashMap<>();
        try {
            if (pRows != null) for (int i = 0; i < pRows.length(); i++) {
                Entry e = fromPrinciple(pRows.getJSONObject(i));
                if (!e.id.isEmpty()) { all.add(e); map.put(key(e.type, e.id), e); }
            }
            if (qRows != null) for (int i = 0; i < qRows.length(); i++) {
                Entry e = fromQuestion(qRows.getJSONObject(i));
                if (!e.id.isEmpty()) { all.add(e); map.put(key(e.type, e.id), e); }
            }
            if (peRows != null) for (int i = 0; i < peRows.length(); i++) {
                Entry e = fromPerson(peRows.getJSONObject(i));
                if (!e.id.isEmpty()) { all.add(e); map.put(key(e.type, e.id), e); }
            }
        } catch (Exception ignored) { }
        // 日记：日期倒序，只列有文本的（与「知·日记」页签同口径）
        try {
            JSONObject root = HabitData.load(c);
            JSONObject titles = DiaryTitles.load(c);
            for (String date : HabitData.diaryDatesDesc(root)) {
                String text = HabitData.diary(root, date);
                if (text.trim().isEmpty()) continue;
                Entry e = new Entry();
                e.type = T_DIARY;
                e.id = date;
                e.name = DiaryTitles.displayLabel(titles, date);
                e.body = text;
                e.sub = ZhiPageWeekday.label(c, date);
                e.hay = date + "\n" + text + "\n" + DiaryTitles.title(titles, date);
                all.add(e);
                map.put(key(e.type, e.id), e);
            }
        } catch (Exception ignored) { }
        entries = all;
        byKey = map;
    }

    /** 懒加载：先用本地缓存建快照（秒开），随后后台刷新。 */
    public static synchronized void ensureSnapshot(Context c) {
        if (entries != null) return;
        try {
            android.content.SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String p = sp.getString("principles", null), q = sp.getString("questions", null);
            String pe = sp.getString("people", null);
            buildSnapshot(c,
                    p == null ? null : new JSONArray(p),
                    q == null ? null : new JSONArray(q),
                    pe == null ? null : new JSONArray(pe));
        } catch (Exception ignored) {
            if (entries == null) buildSnapshot(c, null, null, null);
        }
        refreshAsync(c);
    }

    /** 后台拉知三表刷新快照（节流 60 秒）；完成（含失败）后通知所有 Hook 重渲染。 */
    public static void refreshAsync(final Context c) {
        synchronized (WikiLinks.class) {
            if (fetching) return;
            if (entries != null && System.currentTimeMillis() - fetchedAt < FRESH_MS) return;
            fetching = true;
        }
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            try {
                if (Supa.isLoggedIn(app)) {
                    JSONArray p = Supa.restGet(app, "zhi_principles?select=*&order=created_at.asc");
                    JSONArray q = Supa.restGet(app, "zhi_questions?select=*&order=created_at.asc");
                    JSONArray pe = Supa.restGet(app, "zhi_people?select=*&order=created_at.asc");
                    app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                            .putString("principles", p.toString())
                            .putString("questions", q.toString())
                            .putString("people", pe.toString()).apply();
                    synchronized (WikiLinks.class) { buildSnapshot(app, p, q, pe); }
                    ok = true;
                }
            } catch (Exception ignored) { }
            synchronized (WikiLinks.class) {
                fetching = false;
                if (ok) fetchedAt = System.currentTimeMillis();
                else if (entries == null) buildSnapshot(app, null, null, null);
                else buildSnapshotKeepZhi(app);   // 失败也重拼一次日记部分（本地可能刚写过）
            }
            notifyHooks();
        }).start();
    }

    /** 只重拼日记部分、保留知三表条目（本地日记/标题刚变时用）。 */
    private static synchronized void buildSnapshotKeepZhi(Context c) {
        if (entries == null) { buildSnapshot(c, null, null, null); return; }
        List<Entry> kept = new ArrayList<>();
        Map<String, Entry> map = new HashMap<>();
        for (Entry e : entries) {
            if (T_DIARY.equals(e.type)) continue;
            kept.add(e); map.put(key(e.type, e.id), e);
        }
        try {
            JSONObject root = HabitData.load(c);
            JSONObject titles = DiaryTitles.load(c);
            for (String date : HabitData.diaryDatesDesc(root)) {
                String text = HabitData.diary(root, date);
                if (text.trim().isEmpty()) continue;
                Entry e = new Entry();
                e.type = T_DIARY; e.id = date;
                e.name = DiaryTitles.displayLabel(titles, date);
                e.body = text;
                e.sub = ZhiPageWeekday.label(c, date);
                e.hay = date + "\n" + text + "\n" + DiaryTitles.title(titles, date);
                kept.add(e); map.put(key(e.type, e.id), e);
            }
        } catch (Exception ignored) { }
        entries = kept;
        byKey = map;
    }

    /** 浮窗保存一条后：把该条并入快照并重拼日记部分，通知 Hook 重渲染。 */
    public static synchronized void noteSaved(Context c, Entry saved) {
        ensureSnapshot(c);
        if (saved != null && !T_DIARY.equals(saved.type)) {
            List<Entry> all = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
            boolean done = false;
            for (int i = 0; i < all.size(); i++) {
                Entry e = all.get(i);
                if (e.type.equals(saved.type) && e.id.equals(saved.id)) { all.set(i, saved); done = true; break; }
            }
            if (!done) all.add(saved);
            entries = all;
            Map<String, Entry> map = new HashMap<>();
            for (Entry e : all) map.put(key(e.type, e.id), e);
            byKey = map;
        }
        buildSnapshotKeepZhi(c);
        notifyHooks();
    }

    /** 日记正文或标题刚变：只重拼日记部分。 */
    public static synchronized void noteDiaryChanged(Context c) {
        ensureSnapshot(c);
        buildSnapshotKeepZhi(c);
        notifyHooks();
    }

    public static Entry resolve(Context c, String type, String id) {
        ensureSnapshot(c);
        synchronized (WikiLinks.class) {
            if (T_DIARY.equals(type)) {
                // 日记以本地为准现查（快照可能还没重拼到这一天）
                try {
                    JSONObject root = HabitData.load(c);
                    String text = HabitData.diary(root, id);
                    if (!text.trim().isEmpty()) {
                        Entry e = new Entry();
                        e.type = T_DIARY; e.id = id;
                        e.name = DiaryTitles.displayLabel(DiaryTitles.load(c), id);
                        e.body = text;
                        return e;
                    }
                    return null;
                } catch (Exception ignored) { return null; }
            }
            return byKey == null ? null : byKey.get(key(type, id));
        }
    }

    public static synchronized List<Entry> entriesOf(Context c, String type) {
        ensureSnapshot(c);
        List<Entry> out = new ArrayList<>();
        if (entries != null) for (Entry e : entries) if (e.type.equals(type)) out.add(e);
        return out;
    }

    /** 全部标签（浮窗标签编辑的候选全集）。 */
    public static synchronized List<String> allTags(Context c) {
        ensureSnapshot(c);
        List<String> out = new ArrayList<>();
        if (entries != null) for (Entry e : entries) {
            for (String t : e.tags) if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    /** 纯文本展示（列表摘要用）：记号换成显示名，解不出换占位，绝不露原始记号。 */
    public static String plainDisplay(Context c, String text) {
        if (text == null || !text.contains("{{")) return nz(text);
        ensureSnapshot(c);
        Matcher m = TOKEN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String type = m.group(1), id = m.group(2);
            String rep;
            if (T_DIARY.equals(type)) {
                rep = DiaryTitles.displayLabel(DiaryTitles.load(c), id);
            } else {
                Entry e;
                synchronized (WikiLinks.class) { e = byKey == null ? null : byKey.get(key(type, id)); }
                rep = e != null ? e.name : s("〔引用〕", "[link]");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ---------------- 链接段 ----------------
    /** 编辑框里的链接段：携带记号本身；段内显示文字以 shown 为准，存回时只认记号。 */
    public static final class LinkSpan {
        public String type, id, shown;
        public boolean exists;
        LinkSpan(String type, String id, boolean exists, String shown) {
            this.type = type; this.id = id; this.exists = exists; this.shown = shown;
        }
        public String token() { return "{{" + type + ":" + id + "}}"; }
    }

    /** 虚线下划线（断链用）。 */
    static final class DashedUnderlineSpan implements LineBackgroundSpan {
        private final int color;
        DashedUnderlineSpan(int color) { this.color = color; }
        @Override public void drawBackground(Canvas c, Paint p, int left, int right, int top,
                                             int baseline, int bottom, CharSequence text,
                                             int start, int end, int lnum) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setPathEffect(new DashPathEffect(new float[]{7f, 5f}, 0));
            float y = bottom - 2f;
            c.drawLine(left, y, right, y, paint);
        }
    }

    /** 把编辑框当前内容还原成存储正文（链接段→记号，其余原样）。 */
    public static String extractText(EditText et) {
        if (et == null) return "";
        Editable ed = et.getText();
        if (ed == null) return "";
        LinkSpan[] spans = ed.getSpans(0, ed.length(), LinkSpan.class);
        if (spans.length == 0) return ed.toString();
        java.util.Arrays.sort(spans, (a, b) ->
                Integer.compare(ed.getSpanStart(a), ed.getSpanStart(b)));
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (LinkSpan sp : spans) {
            int st = ed.getSpanStart(sp), en = ed.getSpanEnd(sp);
            if (st < pos) continue;   // 重叠防御
            sb.append(ed.subSequence(pos, st));
            sb.append(sp.token());
            pos = en;
        }
        sb.append(ed.subSequence(pos, ed.length()));
        return sb.toString();
    }

    // ---------------- Hook：挂到一个 EditText 上 ----------------
    public interface Host { void onWikiDataChanged(); }

    public static Hook attach(Activity act, EditText et, Host host) {
        Hook h = new Hook(act, et, host);
        h.install();
        return h;
    }

    private static void notifyHooks() {
        List<Hook> live = new ArrayList<>();
        synchronized (WikiLinks.class) {
            Iterator<WeakReference<Hook>> it = hooks.iterator();
            while (it.hasNext()) {
                Hook h = it.next().get();
                if (h == null) it.remove(); else live.add(h);
            }
        }
        for (final Hook h : live) {
            h.et.post(() -> h.decorate(true));
        }
    }

    public static final class Hook {
        final Activity act;
        final EditText et;
        final Host host;
        boolean applying;
        private String lastRaw;

        Hook(Activity act, EditText et, Host host) {
            this.act = act; this.et = et; this.host = host;
        }

        void install() {
            synchronized (WikiLinks.class) { hooks.add(new WeakReference<>(this)); }
            ensureSnapshot(act);
            // 点链接：触摸定位到链接段就开浮窗，其余照常编辑（不换 movementMethod，不扰光标）
            et.setOnTouchListener((v, ev) -> {
                if (ev.getAction() != MotionEvent.ACTION_UP) return false;
                try {
                    int off = et.getOffsetForPosition(ev.getX(), ev.getY());
                    LinkSpan sp = spanAt(et.getText(), off);
                    if (sp == null) return false;
                    openSpan(sp);
                    return true;
                } catch (Exception ignored) { return false; }
            });
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable e) {
                    if (applying) return;
                    if (checkTrigger()) return;
                    checkIntegrity();
                }
            });
            decorate(true);
            refreshAsync(act);
        }

        private LinkSpan spanAt(Editable ed, int off) {
            if (ed == null) return null;
            LinkSpan[] spans = ed.getSpans(0, ed.length(), LinkSpan.class);
            for (LinkSpan sp : spans) {
                int st = ed.getSpanStart(sp), en = ed.getSpanEnd(sp);
                if ((off > st && off <= en) || off == st) return sp;
            }
            return null;
        }

        private void openSpan(final LinkSpan sp) {
            if (sp.exists) {
                WikiSheet.open(act, sp.type, sp.id, null, (t, id) -> {
                    decorate(true);
                    if (host != null) host.onWikiDataChanged();
                });
            } else {
                // 断链：走创建浮窗，建好后把这个段的编号换成新条目的
                WikiSheet.open(act, sp.type, null, "", (t, newId) -> {
                    sp.id = newId;
                    sp.exists = true;
                    decorate(true);
                    if (host != null) host.onWikiDataChanged();
                });
            }
        }

        /** 刚输入 {{ 或 ｛｛ → 删掉触发符、弹选择窗。返回 true 表示已处理。 */
        private boolean checkTrigger() {
            Editable ed = et.getText();
            if (ed == null) return false;
            int sel = et.getSelectionStart();
            if (sel < 2 || sel > ed.length()) return false;
            String two = ed.subSequence(sel - 2, sel).toString();
            if (!"{{".equals(two) && !"｛｛".equals(two)) return false;
            applying = true;
            try { ed.delete(sel - 2, sel); } finally { applying = false; }
            lastRaw = extractText(et);
            final int rawPos = displayToRaw(ed, sel - 2);
            showPicker(act, null, true, (type, id) -> insertToken(rawPos, type, id),
                    (type, q) -> WikiSheet.open(act, type, null, q, (t, newId) -> {
                        insertToken(rawPos, t, newId);
                        if (host != null) host.onWikiDataChanged();
                    }));
            return true;
        }

        private void insertToken(int rawPos, String type, String id) {
            Editable ed = et.getText();
            if (ed == null) return;
            int dPos = rawToDisplay(ed, Math.max(0, Math.min(rawPos, extractText(et).length())));
            applying = true;
            try { ed.insert(dPos, "{{" + type + ":" + id + "}}"); } finally { applying = false; }
            decorate(true);
            int after = rawToDisplay(et.getText(), rawPos + ("{{" + type + ":" + id + "}}").length());
            try { et.setSelection(Math.min(after, et.getText().length())); } catch (Exception ignored) { }
            et.requestFocus();
        }

        /** 链接段被改坏（段内文字≠显示名）或正文里出现未渲染记号时重渲染。 */
        private void checkIntegrity() {
            Editable ed = et.getText();
            if (ed == null) return;
            LinkSpan[] spans = ed.getSpans(0, ed.length(), LinkSpan.class);
            if (spans.length == 0) {
                if (TOKEN.matcher(ed.toString()).find()) decorate(true);
                return;
            }
            for (LinkSpan sp : spans) {
                int st = ed.getSpanStart(sp), en = ed.getSpanEnd(sp);
                if (st < 0 || en < st || en > ed.length()) { decorate(true); return; }
                if (!ed.subSequence(st, en).toString().equals(sp.shown)) { decorate(true); return; }
            }
        }

        /** 用当前快照把正文重渲染成显示名链接（存储记号不变）。 */
        public void decorate(boolean force) {
            Editable ed = et.getText();
            if (ed == null) return;
            String raw = extractText(et);
            if (!force && raw.equals(lastRaw)) return;
            int selS = displayToRaw(ed, Math.max(0, et.getSelectionStart()));
            int selE = displayToRaw(ed, Math.max(0, et.getSelectionEnd()));
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            Matcher m = TOKEN.matcher(raw);
            int pos = 0;
            int accent = Ui.accent(act);
            int weak = Ui.dark(act) ? Ui.sub(act) : Color.rgb(154, 167, 159);
            while (m.find()) {
                ssb.append(raw, pos, m.start());
                String type = m.group(1), id = m.group(2);
                Entry e = resolve(act, type, id);
                int start = ssb.length();
                if (e != null) {
                    ssb.append(e.name);
                    ssb.setSpan(new LinkSpan(type, id, true, e.name), start, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new ForegroundColorSpan(accent), start, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new UnderlineSpan(), start, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    String tok = m.group(0);
                    ssb.append(tok);
                    ssb.setSpan(new LinkSpan(type, id, false, tok), start, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new ForegroundColorSpan(weak), start, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new DashedUnderlineSpan(weak), start, ssb.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                pos = m.end();
            }
            ssb.append(raw, pos, raw.length());
            applying = true;
            try {
                et.setText(ssb);
            } finally { applying = false; }
            lastRaw = raw;
            try {
                int ns = rawToDisplay(et.getText(), selS);
                int ne = rawToDisplay(et.getText(), selE);
                et.setSelection(Math.min(ns, et.getText().length()), Math.min(ne, et.getText().length()));
            } catch (Exception ignored) { }
        }

        // 显示坐标 ↔ 存储坐标换算（链接段在显示侧是显示名、在存储侧是记号）
        private int displayToRaw(Editable ed, int dpos) {
            LinkSpan[] spans = ed.getSpans(0, ed.length(), LinkSpan.class);
            java.util.Arrays.sort(spans, (a, b) ->
                    Integer.compare(ed.getSpanStart(a), ed.getSpanStart(b)));
            int raw = 0, cur = 0;
            for (LinkSpan sp : spans) {
                int st = ed.getSpanStart(sp), en = ed.getSpanEnd(sp);
                if (dpos <= st) return raw + (dpos - cur);
                if (dpos >= en) { raw += (st - cur) + sp.token().length(); cur = en; continue; }
                return raw + (st - cur) + sp.token().length();   // 光标在段内→记号之后
            }
            return raw + (Math.min(dpos, ed.length()) - cur);
        }
        private int rawToDisplay(Editable ed, int rpos) {
            if (ed == null) return 0;
            LinkSpan[] spans = ed.getSpans(0, ed.length(), LinkSpan.class);
            java.util.Arrays.sort(spans, (a, b) ->
                    Integer.compare(ed.getSpanStart(a), ed.getSpanStart(b)));
            int disp = 0, cur = 0;   // cur 是存储坐标
            for (LinkSpan sp : spans) {
                int st = ed.getSpanStart(sp), en = ed.getSpanEnd(sp);
                int tokStart = cur + (st - disp);
                int tokEnd = tokStart + sp.token().length();
                if (rpos <= tokStart) return disp + (rpos - cur);
                if (rpos >= tokEnd) { disp = en; cur = tokEnd; continue; }
                return en;   // 存储光标落在记号内部→显示段之后
            }
            return disp + (rpos - cur);
        }
    }

    // ---------------- 选择窗 ----------------
    public interface PickCb { void picked(String type, String id); }
    public interface CreateCb { void create(String type, String query); }

    /**
     * 选择窗：底部升起。四类页签（singleType 非空时只一类）＋搜索＋结果＋创建按钮。
     * allowCreate 为 false 时不显示创建按钮（如浮窗里加关联只选已有）。
     */
    public static void showPicker(final Activity act, final String singleType,
                                  final boolean allowCreate, final PickCb pickCb,
                                  final CreateCb createCb) {
        ensureSnapshot(act);
        refreshAsync(act);
        final Dialog dlg = new Dialog(act);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(act, 16), dp(act, 14), dp(act, 16), dp(act, 18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.bg(act));
        bg.setCornerRadii(new float[]{dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16), 0, 0, 0, 0});
        col.setBackground(bg);

        TextView title = new TextView(act);
        title.setText(singleType == null ? s("引 用", "Cite") : s("选一个", "Pick one") + typeName(singleType));
        title.setTextSize(13);
        title.setTextColor(Ui.sub(act));
        title.setLetterSpacing(0.3f);
        col.addView(title);

        final String[] curType = {singleType == null ? T_PERSON : singleType};
        final LinearLayout tabs = new LinearLayout(act);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(act, 10), 0, dp(act, 4));
        if (singleType == null) col.addView(tabs);

        final EditText search = new EditText(act);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setHint(s("打字搜索", "Type to search"));
        search.setTextColor(Ui.text(act));
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(Ui.surface(act));
        sb.setCornerRadius(dp(act, 9));
        sb.setStroke(Math.max(1, dp(act, 1)), Ui.line(act));
        search.setBackground(sb);
        search.setPadding(dp(act, 12), dp(act, 8), dp(act, 12), dp(act, 8));
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(act, 8);
        col.addView(search, sLp);

        final LinearLayout results = new LinearLayout(act);
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(act);
        scroll.addView(results);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(act, 300));
        scLp.topMargin = dp(act, 6);
        col.addView(scroll, scLp);

        final TextView create = new TextView(act);
        create.setGravity(Gravity.CENTER);
        create.setTextSize(14);
        create.setTextColor(Ui.accent(act));
        create.setPadding(0, dp(act, 12), 0, dp(act, 4));
        if (allowCreate) col.addView(create);

        final Runnable[] refill = new Runnable[1];
        refill[0] = () -> {
            results.removeAllViews();
            String q = search.getText().toString().trim();
            List<Entry> list = entriesOf(act, curType[0]);
            int shown = 0;
            for (final Entry e : list) {
                if (!q.isEmpty() && !e.hay.contains(q)) continue;
                if (shown++ >= 50) break;
                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(act, 6), dp(act, 9), dp(act, 6), dp(act, 9));
                TextView nm = new TextView(act);
                nm.setText(e.name);
                nm.setTextSize(14.5f);
                nm.setTypeface(Typeface.SERIF, Typeface.BOLD);
                nm.setTextColor(Ui.text(act));
                row.addView(nm);
                if (!e.sub.isEmpty()) {
                    TextView sbv = new TextView(act);
                    sbv.setText(e.sub);
                    sbv.setTextSize(11.5f);
                    sbv.setTextColor(Ui.sub(act));
                    row.addView(sbv);
                }
                row.setOnClickListener(v -> { dlg.dismiss(); pickCb.picked(e.type, e.id); });
                results.addView(row);
                View line = new View(act);
                line.setBackgroundColor(Ui.line(act));
                results.addView(line, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(act, 1) / 2)));
            }
            if (shown == 0) {
                TextView none = new TextView(act);
                none.setText(s("没有现成的", "Nothing yet"));
                none.setTextSize(13);
                none.setTextColor(Ui.sub(act));
                none.setPadding(dp(act, 6), dp(act, 12), 0, 0);
                results.addView(none);
            }
            if (allowCreate) {
                if (T_DIARY.equals(curType[0])) {
                    create.setText(s("＋ 打开今天的日记", "+ Open today's diary"));
                } else {
                    create.setText(q.isEmpty()
                            ? s("＋ 新建一个" + typeName(curType[0]), "+ New " + typeName(curType[0]))
                            : s("＋ 创建『" + q + "』", "+ Create \"" + q + "\""));
                }
            }
        };
        create.setOnClickListener(v -> {
            String q = search.getText().toString().trim();
            dlg.dismiss();
            if (createCb != null) createCb.create(curType[0], q);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { refill[0].run(); }
        });
        if (singleType == null) {
            final String[] types = {T_PERSON, T_PRINCIPLE, T_QUESTION, T_DIARY};
            for (final String t : types) {
                TextView tab = new TextView(act);
                tab.setText(typeName(t));
                tab.setTextSize(13.5f);
                tab.setGravity(Gravity.CENTER);
                tab.setPadding(0, dp(act, 7), 0, dp(act, 7));
                boolean on = t.equals(curType[0]);
                tab.setTextColor(on ? Ui.bg(act) : Ui.sub(act));
                GradientDrawable tb = new GradientDrawable();
                tb.setColor(on ? Ui.text(act) : Color.TRANSPARENT);
                tb.setCornerRadius(dp(act, 8));
                tab.setBackground(tb);
                tab.setOnClickListener(v -> {
                    curType[0] = t;
                    showPickerRefreshTabs(act, tabs, types, curType);
                    refill[0].run();
                });
                tabs.addView(tab, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
        }
        refill[0].run();
        dlg.setContentView(col);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        dlg.show();
        // 快照可能还在后台刷新：稍后按最新快照再填一次结果（搜索词不变）
        search.postDelayed(() -> { if (dlg.isShowing()) refill[0].run(); }, 1500);
    }

    private static void showPickerRefreshTabs(Activity act, LinearLayout tabs, String[] types, String[] curType) {
        for (int i = 0; i < tabs.getChildCount() && i < types.length; i++) {
            TextView tab = (TextView) tabs.getChildAt(i);
            boolean on = types[i].equals(curType[0]);
            tab.setTextColor(on ? Ui.bg(act) : Ui.sub(act));
            GradientDrawable tb = new GradientDrawable();
            tb.setColor(on ? Ui.text(act) : Color.TRANSPARENT);
            tb.setCornerRadius(dp(act, 8));
            tab.setBackground(tb);
        }
    }

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 日记副信息：短日期＋星期（与「知」页同款式，独立小工具避免互相依赖）。 */
    static final class ZhiPageWeekday {
        static String label(Context c, String date) {
            java.util.Calendar cal = DayUtil.parse(date);
            String wk = "";
            if (cal != null) {
                int idx = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7;
                wk = DayUtil.weekdayName(idx);
            }
            return DayUtil.shortDate(date) + " " + wk;
        }
    }
}
