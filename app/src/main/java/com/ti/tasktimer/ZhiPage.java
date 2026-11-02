package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
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
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * 知页（v1.50.0，底部第二签）：安放「知」的三类记录——思维原则（think）、行为原则
 * （act）、待问的问题（question）。数据在云端 zhi_entries 表，经 Supa 带当前登录
 * 用户的 JWT 读写（RLS 只见自己的）；本地在 SharedPreferences 留一份缓存，拉取
 * 失败/无网时展示缓存并在页顶小字提示。增删改都先落库、成功后才更新本页与缓存，
 * 失败只 toast，绝不出现"页面上没了但库里也没存"的中间态。
 */
public final class ZhiPage {
    public static final String KIND_THINK = "think";
    public static final String KIND_ACT = "act";
    public static final String KIND_QUESTION = "question";

    private static final String[][] KINDS = {
            {KIND_THINK, "思维原则", "Thinking principles"},
            {KIND_ACT, "行为原则", "Principles for action"},
            {KIND_QUESTION, "待问的问题", "Questions to ask"},
    };
    private static final String PREF = "zhi_cache";
    private static final String KEY_ENTRIES = "entries";

    static final class Entry {
        String id, kind, title, body, target;
    }

    private final Activity act;
    private final ViewGroup container;
    private final List<Entry> entries = new ArrayList<>();
    private boolean offline;     // 正在展示缓存（本轮拉取失败）
    private boolean fetchFailed; // 无缓存且拉取失败（整页空态＋重试）

    public ZhiPage(Activity act, ViewGroup container) {
        this.act = act;
        this.container = container;
        loadCache();
    }

    private int dp(int v) { return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f); }
    private static String s(String zh, String en) { return Strings.en() ? en : zh; }
    private void toast(String t) { Toast.makeText(act, t, Toast.LENGTH_SHORT).show(); }

    private TextView small(String t, float size) {
        TextView v = new TextView(act);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(Ui.sub(act));
        return v;
    }

    // ---------------- 数据流 ----------------
    public void refresh() {
        if (!Supa.isLoggedIn(act)) { renderLoginHint(); return; }
        fetchFailed = false;
        render();           // 先画缓存（或空区），不白屏
        fetch();
    }

    private void fetch() {
        new Thread(() -> {
            try {
                JSONArray rows = Supa.restGet(act, "zhi_entries?select=*&order=created_at.asc");
                List<Entry> got = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) got.add(parse(rows.getJSONObject(i)));
                act.runOnUiThread(() -> {
                    entries.clear();
                    entries.addAll(got);
                    offline = false;
                    fetchFailed = false;
                    saveCache();
                    render();
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> {
                    if (!entries.isEmpty()) { offline = true; render(); }
                    else { fetchFailed = true; render(); }
                });
            }
        }).start();
    }

    private static Entry parse(JSONObject o) {
        Entry e = new Entry();
        e.id = o.optString("id", "");
        e.kind = o.optString("kind", "");
        e.title = o.optString("title", "");
        e.body = o.optString("body", "");
        e.target = o.optString("target", "");
        return e;
    }

    // ---------------- 缓存 ----------------
    private void loadCache() {
        try {
            String raw = act.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_ENTRIES, null);
            if (raw == null || raw.isEmpty()) return;
            JSONArray arr = new JSONArray(raw);
            entries.clear();
            for (int i = 0; i < arr.length(); i++) entries.add(parse(arr.getJSONObject(i)));
        } catch (Exception ignored) { }
    }

    private void saveCache() {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("kind", e.kind);
                o.put("title", e.title);
                o.put("body", e.body);
                o.put("target", e.target);
                arr.put(o);
            }
            act.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                    .putString(KEY_ENTRIES, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ---------------- 渲染 ----------------
    private LinearLayout pageHead(LinearLayout col) {
        TextView title = new TextView(act);
        title.setText(s("知", "Know"));
        title.setTextSize(23);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setTextColor(Ui.text(act));
        col.addView(title);
        TextView sub = small(s("想明白的、要照着做的、还没问出口的，都放在这里",
                "What you have figured out, what you mean to live by, and what you still want to ask"), 12);
        sub.setPadding(0, dp(4), 0, 0);
        col.addView(sub);
        return col;
    }

    private LinearLayout newCol() {
        container.removeAllViews();
        ScrollView scroll = new ScrollView(act);
        scroll.setBackgroundColor(Ui.bg(act));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(24));
        scroll.addView(col);
        container.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return col;
    }

    private void renderLoginHint() {
        LinearLayout col = newCol();
        pageHead(col);
        TextView hint = new TextView(act);
        hint.setText(s("先登录，这里才有你的知", "Sign in first — your principles and questions live here"));
        hint.setTextSize(15);
        hint.setTextColor(Ui.text(act));
        hint.setPadding(0, dp(22), 0, 0);
        col.addView(hint);
        TextView sub = small(s("到日程页右上角的设置里登录你的账号", "Sign in from Settings on the Schedule page"), 12.5f);
        sub.setPadding(0, dp(6), 0, 0);
        col.addView(sub);
    }

    private void render() {
        LinearLayout col = newCol();
        pageHead(col);
        if (fetchFailed) {
            TextView t = new TextView(act);
            t.setText(s("连不上，暂时看不到你的知", "Can't connect — your entries are out of reach for now"));
            t.setTextSize(15);
            t.setTextColor(Ui.text(act));
            t.setPadding(0, dp(22), 0, 0);
            col.addView(t);
            TextView retry = new TextView(act);
            retry.setText(s("重试", "Retry"));
            retry.setTextSize(14.5f);
            retry.setTextColor(Ui.accent(act));
            retry.setPadding(0, dp(12), 0, 0);
            retry.setOnClickListener(v -> refresh());
            col.addView(retry);
            return;
        }
        if (offline) {
            TextView off = small(s("离线，显示上次的", "Offline — showing the last copy"), 12);
            off.setTextColor(Ui.accent(act));
            off.setPadding(0, dp(6), 0, 0);
            col.addView(off);
        }
        for (String[] k : KINDS) {
            col.addView(sectionTitle(s(k[1], k[2])));
            boolean any = false;
            for (Entry e : entries) {
                if (!k[0].equals(e.kind)) continue;
                any = true;
                col.addView(entryRow(e));
            }
            if (!any) {
                TextView none = small(s("还没有", "Nothing yet"), 12.5f);
                none.setPadding(0, dp(2), 0, dp(2));
                col.addView(none);
            }
            TextView add = new TextView(act);
            add.setText(s("＋ 加一条", "＋ Add one"));
            add.setTextSize(14);
            add.setTextColor(Ui.accent(act));
            add.setPadding(0, dp(9), 0, dp(9));
            final String kind = k[0];
            add.setOnClickListener(v -> editDialog(null, kind));
            col.addView(add);
        }
    }

    private TextView sectionTitle(String t) {
        TextView v = new TextView(act);
        v.setText(t);
        v.setTextSize(16);
        v.setTypeface(Typeface.SERIF, Typeface.BOLD);
        v.setTextColor(Ui.text(act));
        v.setPadding(0, dp(18), 0, dp(4));
        return v;
    }

    private static String firstLine(String body) {
        if (body == null) return "";
        String b = body.trim();
        int nl = b.indexOf('\n');
        return nl >= 0 ? b.substring(0, nl).trim() : b;
    }

    private View entryRow(final Entry e) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));
        String head = e.title != null && !e.title.trim().isEmpty() ? e.title.trim() : firstLine(e.body);
        if (KIND_QUESTION.equals(e.kind) && e.target != null && !e.target.trim().isEmpty()) {
            head = head + s(" · 问：", " · for: ") + e.target.trim();
        }
        TextView t = new TextView(act);
        t.setText(head);
        t.setTextSize(15);
        t.setTextColor(Ui.text(act));
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(t);
        String preview = firstLine(e.body);
        if (!preview.isEmpty() && !preview.equals(e.title != null ? e.title.trim() : "")) {
            TextView p = small(preview, 12.5f);
            p.setMaxLines(1);
            p.setEllipsize(android.text.TextUtils.TruncateAt.END);
            p.setPadding(0, dp(2), 0, 0);
            box.addView(p);
        }
        box.setOnClickListener(v -> editDialog(e, e.kind));
        return box;
    }

    // ---------------- 编辑/新增/删除 ----------------
    private void editDialog(final Entry existing, final String kind) {
        final boolean isQ = KIND_QUESTION.equals(kind);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText titleIn = new EditText(act);
        titleIn.setHint(s("标题，可空", "Title (optional)"));
        titleIn.setText(existing != null ? existing.title : "");
        box.addView(titleIn);
        final EditText bodyIn = new EditText(act);
        bodyIn.setHint(isQ ? s("写下这个问题", "Write the question")
                : s("写下这条原则", "Write the principle"));
        bodyIn.setMinLines(4);
        bodyIn.setGravity(Gravity.TOP);
        bodyIn.setText(existing != null ? existing.body : "");
        box.addView(bodyIn);
        final EditText targetIn = new EditText(act);
        if (isQ) {
            targetIn.setHint(s("问谁，可空", "Who to ask (optional)"));
            targetIn.setText(existing != null ? existing.target : "");
            box.addView(targetIn);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(act)
                .setView(box)
                .setPositiveButton(s("保存", "Save"), (d, w) -> {
                    String title = titleIn.getText().toString().trim();
                    String body = bodyIn.getText().toString().trim();
                    String target = isQ ? targetIn.getText().toString().trim() : "";
                    if (title.isEmpty() && body.isEmpty()) {
                        toast(s("先写点什么再存", "Write something first"));
                        return;
                    }
                    save(existing, kind, title, body, target);
                })
                .setNegativeButton(Strings.btnCancel(), null);
        if (existing != null) {
            b.setNeutralButton(s("删除", "Delete"), (d, w) -> confirmDelete(existing));
        }
        b.show();
    }

    private void confirmDelete(final Entry e) {
        String head = e.title != null && !e.title.trim().isEmpty() ? e.title.trim() : firstLine(e.body);
        new AlertDialog.Builder(act)
                .setTitle(s("删除「" + head + "」？", "Delete \"" + head + "\"?"))
                .setPositiveButton(s("删除", "Delete"), (d, w) -> deleteEntry(e))
                .setNegativeButton(Strings.btnCancel(), null)
                .show();
    }

    private static String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private void save(final Entry existing, final String kind, final String title,
                      final String body, final String target) {
        new Thread(() -> {
            try {
                JSONObject row = new JSONObject();
                String now = isoNow();
                row.put("user_id", Supa.userId(act));
                row.put("kind", kind);
                row.put("title", title);
                row.put("body", body);
                if (KIND_QUESTION.equals(kind)) row.put("target", target);
                row.put("updated_at", now);
                if (existing == null) {
                    row.put("id", UUID.randomUUID().toString());
                    row.put("source", "app");
                    row.put("created_at", now);
                } else {
                    row.put("id", existing.id);
                }
                JSONArray back = Supa.restUpsertOne(act, "zhi_entries", "id", row);
                final Entry saved = back.length() > 0 ? parse(back.getJSONObject(0)) : null;
                act.runOnUiThread(() -> {
                    if (saved != null) {
                        if (existing == null) {
                            entries.add(saved);
                        } else {
                            for (int i = 0; i < entries.size(); i++) {
                                if (entries.get(i).id.equals(saved.id)) { entries.set(i, saved); break; }
                            }
                        }
                        offline = false;
                        saveCache();
                        render();
                    }
                    toast(s("存好了", "Saved"));
                });
            } catch (Exception ex) {
                act.runOnUiThread(() -> toast(s("没存上，内容还在，再试一次",
                        "Couldn't save — nothing was changed, try again")));
            }
        }).start();
    }

    private void deleteEntry(final Entry e) {
        new Thread(() -> {
            try {
                Supa.restDelete(act, "zhi_entries?id=eq." + e.id);
                act.runOnUiThread(() -> {
                    entries.remove(e);
                    saveCache();
                    render();
                    toast(s("删掉了", "Deleted"));
                });
            } catch (Exception ex) {
                act.runOnUiThread(() -> toast(s("没删掉，再试一次", "Couldn't delete — try again")));
            }
        }).start();
    }
}
