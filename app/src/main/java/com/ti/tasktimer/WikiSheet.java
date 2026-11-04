package com.ti.tasktimer;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
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
 * 双链浮窗编辑（v1.61.0）：底部升起的大半屏小窗，字段与「知」统一编辑页一致，
 * 保存走同一批 Supabase 表、同一 payload 形状（zhi_principles / zhi_questions /
 * zhi_people 的 upsert、zhi_links 规范形增删、日记只经 HabitData.putDiary、
 * 日记标题写 DiaryTitles）——不另起数据口径。新建条目保存后回调把记号插回原文；
 * 已有条目保存后原文按新显示名自动重渲染（WikiLinks.noteSaved 通知各 Hook）。
 */
public final class WikiSheet {
    public interface Done { void saved(String type, String id); }

    private WikiSheet() {}

    private static String s(String zh, String en) { return Strings.en() ? en : zh; }
    private static String nz(String v) { return v == null ? "" : v; }
    private static int dp(Activity a, int v) { return WikiLinks.dp(a, v); }

    private static String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
    private static JSONArray tagsJson(List<String> tags) {
        JSONArray a = new JSONArray();
        for (String t : tags) a.put(t);
        return a;
    }
    private static String tableOf(String type) {
        return WikiLinks.T_PRINCIPLE.equals(type) ? "zhi_principles"
                : WikiLinks.T_QUESTION.equals(type) ? "zhi_questions" : "zhi_people";
    }
    private static String[] sectionsFor(String type) {
        if (WikiLinks.T_PRINCIPLE.equals(type)) return new String[]{WikiLinks.T_PERSON, WikiLinks.T_QUESTION, WikiLinks.T_DIARY};
        if (WikiLinks.T_QUESTION.equals(type)) return new String[]{WikiLinks.T_PERSON, WikiLinks.T_PRINCIPLE};
        if (WikiLinks.T_DIARY.equals(type)) return new String[]{WikiLinks.T_PRINCIPLE, WikiLinks.T_PERSON};
        return new String[]{WikiLinks.T_QUESTION, WikiLinks.T_PRINCIPLE, WikiLinks.T_DIARY};
    }

    /** 打开浮窗。id 为 null＝新建（prefill 预填名字/标题；日记时 prefill 若是日期则用它，否则今天）。 */
    public static void open(final Activity act, final String type, final String id,
                            final String prefill, final Done done) {
        if (WikiLinks.T_DIARY.equals(type)) {
            String date = id;
            if (date == null) {
                date = (prefill != null && prefill.matches("\\d{4}-\\d{2}-\\d{2}"))
                        ? prefill : DayUtil.today();
            }
            Ctx ctx = new Ctx(act, type, date, done);
            ctx.isNew = false;   // 日记以日期为键，写即建
            ctx.buildDiary();
            ctx.show();
            return;
        }
        if (id == null) {
            Ctx ctx = new Ctx(act, type, null, done);
            ctx.isNew = true;
            ctx.cur = new WikiLinks.Entry();
            ctx.cur.type = type;
            if (WikiLinks.T_PERSON.equals(type)) ctx.cur.personName = nz(prefill);
            else ctx.cur.title = nz(prefill);
            ctx.tags = new ArrayList<>();
            ctx.build();
            ctx.show();
            return;
        }
        WikiLinks.Entry e = WikiLinks.resolve(act, type, id);
        if (e != null) {
            Ctx ctx = new Ctx(act, type, id, done);
            ctx.cur = e;
            ctx.tags = new ArrayList<>(e.tags);
            ctx.build();
            ctx.show();
            ctx.loadLinks();
            return;
        }
        // 快照里还没有（刚在别处新建）：单条拉取后再开
        new Thread(() -> {
            try {
                JSONArray back = Supa.restGet(act, tableOf(type) + "?id=eq." + id + "&select=*");
                if (back.length() == 0) throw new Exception("empty");
                JSONObject row = back.getJSONObject(0);
                final WikiLinks.Entry fe = WikiLinks.T_PRINCIPLE.equals(type) ? WikiLinks.fromPrinciple(row)
                        : WikiLinks.T_QUESTION.equals(type) ? WikiLinks.fromQuestion(row)
                        : WikiLinks.fromPerson(row);
                act.runOnUiThread(() -> {
                    Ctx ctx = new Ctx(act, type, id, done);
                    ctx.cur = fe;
                    ctx.tags = new ArrayList<>(fe.tags);
                    ctx.build();
                    ctx.show();
                    ctx.loadLinks();
                });
            } catch (Exception ex) {
                act.runOnUiThread(() -> Toast.makeText(act,
                        s("没打开，再试一次", "Couldn't open — try again"), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ---------------- 浮窗实例 ----------------
    private static final class Ctx {
        final Activity act;
        final String type;
        String id;                 // 条目 id；日记＝日期
        final Done done;
        boolean isNew;
        WikiLinks.Entry cur;
        List<String> tags = new ArrayList<>();
        // 视图
        Dialog dlg;
        EditText nameIn, titleIn, shortIn, bodyIn, memoIn, diaryTitleIn;
        LinearLayout tagFlow, linksBox;
        String kind = "think", status = "pending";
        // 关联（zhi_links 全量拉取后本地过滤，与 ZhiPage 同口径）
        final List<String[]> linkRows = new ArrayList<>();   // {aType,aId,bType,bId}

        Ctx(Activity act, String type, String id, Done done) {
            this.act = act; this.type = type; this.id = id; this.done = done;
        }

        private GradientDrawable roundBg(int fill, int stroke, int radiusDp) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(fill);
            g.setCornerRadius(dp(act, radiusDp));
            if (stroke != 0) g.setStroke(Math.max(1, dp(act, 1)), stroke);
            return g;
        }
        private int cardC() { return Ui.dark(act) ? Ui.surface(act) : Color.rgb(255, 253, 247); }
        private TextView tv(String text, float size, int color) {
            TextView v = new TextView(act);
            v.setText(text); v.setTextSize(size); v.setTextColor(color);
            return v;
        }
        private TextView fieldLabel(String t) {
            TextView v = tv(t, 11.5f, Ui.dark(act) ? Ui.sub(act) : Color.rgb(154, 167, 159));
            v.setLetterSpacing(0.3f);
            v.setPadding(0, dp(act, 14), 0, dp(act, 6));
            return v;
        }
        private EditText fieldBox(String text, String hint, int minLines) {
            EditText e = new EditText(act);
            e.setText(text);
            e.setHint(hint);
            e.setTextSize(14);
            e.setTextColor(Ui.text(act));
            e.setBackground(roundBg(cardC(), Ui.line(act), 10));
            e.setPadding(dp(act, 12), dp(act, 10), dp(act, 12), dp(act, 10));
            if (minLines > 1) { e.setMinLines(minLines); e.setGravity(Gravity.TOP); }
            else e.setSingleLine(true);
            return e;
        }
        private View twoChoice(final String[] values, final String[] labels, String current,
                               final java.util.function.Consumer<String> onPick,
                               final LinearLayout host) {
            LinearLayout seg = new LinearLayout(act);
            seg.setOrientation(LinearLayout.HORIZONTAL);
            seg.setBackground(roundBg(Ui.muted(act), 0, 9));
            seg.setPadding(dp(act, 3), dp(act, 3), dp(act, 3), dp(act, 3));
            for (int i = 0; i < values.length; i++) {
                final String val = values[i];
                boolean on = val.equals(current);
                TextView t = tv(labels[i], 13, on ? Ui.text(act) : Ui.sub(act));
                t.setGravity(Gravity.CENTER);
                t.setPadding(0, dp(act, 7), 0, dp(act, 7));
                if (on) {
                    t.setBackground(roundBg(cardC(), 0, 7));
                    t.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
                }
                t.setOnClickListener(v -> {
                    onPick.accept(val);
                    // 只重画这一段控
                    int idx = host.indexOfChild(seg);
                    host.removeView(seg);
                    host.addView(twoChoice(values, labels, val, onPick, host), idx);
                });
                seg.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
            return seg;
        }

        private LinearLayout baseCol(String headText) {
            dlg = new Dialog(act);
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
            ScrollView scroll = new ScrollView(act);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Ui.bg(act));
            bg.setCornerRadii(new float[]{dp(act, 18), dp(act, 18), dp(act, 18), dp(act, 18), 0, 0, 0, 0});
            scroll.setBackground(bg);
            LinearLayout col = new LinearLayout(act);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(act, 18), dp(act, 16), dp(act, 18), dp(act, 30));
            scroll.addView(col);
            TextView head = tv(headText, 12, Ui.accent(act));
            head.setLetterSpacing(0.3f);
            col.addView(head);
            dlg.setContentView(scroll);
            return col;
        }

        void show() {
            Window w = dlg.getWindow();
            if (w != null) {
                w.setGravity(Gravity.BOTTOM);
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            dlg.show();
            if (w != null) {
                int h = (int) (act.getResources().getDisplayMetrics().heightPixels * 0.88);
                w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, h);
            }
        }

        // ---------- 知三类 ----------
        void build() {
            LinearLayout col = baseCol((isNew ? s("新 建 · ", "New · ") : s("编 辑 · ", "Edit · "))
                    + WikiLinks.typeName(type));
            if (WikiLinks.T_PERSON.equals(type)) {
                col.addView(fieldLabel(s("名 字", "Name")));
                nameIn = fieldBox(cur.personName, s("名字", "Name"), 1);
                col.addView(nameIn);
                col.addView(fieldLabel(s("记 忆 点", "Memo")));
                memoIn = fieldBox(cur.memo, s("帮你记住这个人的一段话", "A note that helps you remember this person"), 3);
                col.addView(memoIn);
                WikiLinks.attach(act, memoIn, null);
            } else {
                if (WikiLinks.T_PRINCIPLE.equals(type)) {
                    kind = cur.kind;
                    col.addView(fieldLabel(s("类 型", "Kind")));
                    final LinearLayout kindHost = new LinearLayout(act);
                    kindHost.setOrientation(LinearLayout.VERTICAL);
                    kindHost.addView(twoChoice(new String[]{"think", "act"},
                            new String[]{s("思维", "Thinking"), s("行为", "Action")}, kind,
                            v -> kind = v, kindHost));
                    col.addView(kindHost);
                }
                col.addView(fieldLabel(s("标 题", "Title")));
                titleIn = fieldBox(cur.title, s("标题", "Title"), 1);
                if (WikiLinks.T_PRINCIPLE.equals(type)) titleIn.setTypeface(Typeface.SERIF, Typeface.BOLD);
                col.addView(titleIn);
                if (WikiLinks.T_QUESTION.equals(type)) {
                    status = cur.status;
                    col.addView(fieldLabel(s("短 题", "Short title")));
                    shortIn = fieldBox(cur.shortTitle, s("引用时显示，10 字以内", "Shown in citations, up to 10 characters"), 1);
                    col.addView(shortIn);
                }
                col.addView(fieldLabel(s("内 容", "Body")));
                bodyIn = fieldBox(cur.body, WikiLinks.T_QUESTION.equals(type)
                        ? s("写下这个问题", "Write the question") : s("写下这条原则", "Write the principle"), 4);
                col.addView(bodyIn);
                WikiLinks.attach(act, bodyIn, null);
                if (WikiLinks.T_QUESTION.equals(type)) {
                    col.addView(fieldLabel(s("状 态", "Status")));
                    final LinearLayout stHost = new LinearLayout(act);
                    stHost.setOrientation(LinearLayout.VERTICAL);
                    stHost.addView(twoChoice(new String[]{"pending", "asked"},
                            new String[]{s("待问", "To ask"), s("已问", "Asked")}, status,
                            v -> status = v, stHost));
                    col.addView(stHost);
                }
            }
            buildTags(col);
            buildSave(col, () -> saveItem());
            linksBox = new LinearLayout(act);
            linksBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(linksBox);
            if (isNew) {
                TextView note = tv(s("先保存，再加关联", "Save first, then add links"), 12.5f, Ui.sub(act));
                note.setPadding(0, dp(act, 14), 0, 0);
                linksBox.addView(note);
            } else {
                renderLinks();
            }
        }

        // ---------- 日记 ----------
        void buildDiary() {
            final String date = id;
            JSONObject root = HabitData.load(act);
            JSONObject titles = DiaryTitles.load(act);
            LinearLayout col = baseCol(s("日 记 · ", "Diary · ") + WikiLinks.ZhiPageWeekday.label(act, date));
            col.addView(fieldLabel(s("标 题", "Title")));
            diaryTitleIn = fieldBox(DiaryTitles.title(titles, date),
                    s("主题，10 字以内（可空）", "Theme, up to 10 characters (optional)"), 1);
            col.addView(diaryTitleIn);
            TextView code = tv(DiaryTitles.yymmdd(date), 12, Ui.sub(act));
            code.setPadding(0, dp(act, 4), 0, 0);
            col.addView(code);
            col.addView(fieldLabel(s("正 文", "Entry")));
            bodyIn = fieldBox(HabitData.diary(root, date), s("写下这一天的日记", "Write this day's entry"), 8);
            col.addView(bodyIn);
            WikiLinks.attach(act, bodyIn, null);
            buildSave(col, () -> {
                JSONObject r = HabitData.load(act);
                HabitData.putDiary(r, date, WikiLinks.extractText(bodyIn).trim());
                HabitData.save(act, r);
                JSONObject t = DiaryTitles.load(act);
                DiaryTitles.putTitle(t, date, diaryTitleIn.getText().toString());
                DiaryTitles.save(act, t);
                WikiLinks.noteDiaryChanged(act);
                Toast.makeText(act, s("存好了", "Saved"), Toast.LENGTH_SHORT).show();
                dlg.dismiss();
                if (done != null) done.saved(WikiLinks.T_DIARY, date);
            });
            linksBox = new LinearLayout(act);
            linksBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(linksBox);
            renderLinks();
            loadLinks();
        }

        // ---------- 标签 ----------
        private void buildTags(LinearLayout col) {
            col.addView(fieldLabel(s("标 签", "Tags")));
            tagFlow = new LinearLayout(act);
            tagFlow.setOrientation(LinearLayout.VERTICAL);
            col.addView(tagFlow);
            renderTags();
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rLp.topMargin = dp(act, 8);
            col.addView(row, rLp);
            final EditText newTagIn = new EditText(act);
            newTagIn.setHint(s("新标签", "New tag"));
            newTagIn.setTextSize(13);
            newTagIn.setSingleLine(true);
            newTagIn.setBackground(roundBg(cardC(), Ui.line(act), 8));
            newTagIn.setPadding(dp(act, 10), dp(act, 6), dp(act, 10), dp(act, 6));
            row.addView(newTagIn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView addTag = tv(s("添加", "Add"), 13, Ui.accent(act));
            addTag.setPadding(dp(act, 12), dp(act, 6), dp(act, 4), dp(act, 6));
            addTag.setOnClickListener(v -> {
                String t = newTagIn.getText().toString().trim();
                if (t.isEmpty()) return;
                if (!tags.contains(t)) tags.add(t);
                newTagIn.setText("");
                renderTags();
            });
            row.addView(addTag);
        }

        private void renderTags() {
            if (tagFlow == null) return;
            tagFlow.removeAllViews();
            // 简化成横排自动换行的两段式：用 ZhiPage 同款 FlowLayout
            ZhiPage.FlowLayout flow = new ZhiPage.FlowLayout(act, dp(act, 6), dp(act, 6));
            tagFlow.addView(flow);
            List<String> universe = WikiLinks.allTags(act);
            for (String t : tags) if (!universe.contains(t)) universe.add(t);
            for (final String tag : universe) {
                boolean on = tags.contains(tag);
                TextView chip = tv(tag, 11.5f, on ? cardC() : Ui.sub(act));
                chip.setPadding(dp(act, 11), dp(act, 3), dp(act, 11), dp(act, 3));
                chip.setBackground(roundBg(on ? Ui.text(act) : Color.TRANSPARENT,
                        on ? 0 : (Ui.dark(act) ? Ui.sub(act) : Color.rgb(154, 167, 159)), 20));
                chip.setOnClickListener(v -> {
                    if (tags.contains(tag)) tags.remove(tag); else tags.add(tag);
                    renderTags();
                });
                flow.addView(chip);
            }
        }

        private void buildSave(LinearLayout col, final Runnable onSave) {
            TextView save = tv(s("保 存", "Save"), 14.5f, cardC());
            save.setGravity(Gravity.CENTER);
            save.setLetterSpacing(0.3f);
            save.setBackground(roundBg(Ui.text(act), 0, 10));
            save.setPadding(0, dp(act, 12), 0, dp(act, 12));
            LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            svLp.topMargin = dp(act, 20);
            svLp.bottomMargin = dp(act, 6);
            col.addView(save, svLp);
            save.setOnClickListener(v -> onSave.run());
        }

        // ---------- 知三类保存：与 ZhiPage.saveItem 同 payload ----------
        private void saveItem() {
            final String fTitle = titleIn == null ? "" : titleIn.getText().toString().trim();
            final String fBody = bodyIn == null ? "" : WikiLinks.extractText(bodyIn).trim();
            final String fName = nameIn == null ? "" : nameIn.getText().toString().trim();
            final String fMemo = memoIn == null ? "" : WikiLinks.extractText(memoIn).trim();
            final String fShort = shortIn == null ? "" : shortIn.getText().toString().trim();
            if (WikiLinks.T_PERSON.equals(type)) {
                if (fName.isEmpty()) { Toast.makeText(act, s("先写下名字", "Write a name first"), Toast.LENGTH_SHORT).show(); return; }
            } else if (fTitle.isEmpty() && fBody.isEmpty()) {
                Toast.makeText(act, s("先写点什么再存", "Write something first"), Toast.LENGTH_SHORT).show(); return;
            }
            final boolean wasNew = isNew;
            final String itemId = wasNew ? UUID.randomUUID().toString() : id;
            final String now = isoNow();
            final List<String> fTags = new ArrayList<>(tags);
            final String fKind = kind, fStatus = status;
            new Thread(() -> {
                try {
                    String shortFinal = fShort;
                    if (WikiLinks.T_QUESTION.equals(type) && wasNew && shortFinal.isEmpty()) {
                        shortFinal = ZhiPage.genShortTitle(act, fTitle, fBody);   // AI 生成，失败内部兜底
                    }
                    JSONObject row = new JSONObject();
                    row.put("id", itemId);
                    row.put("user_id", Supa.userId(act));
                    row.put("updated_at", now);
                    if (wasNew) { row.put("created_at", now); row.put("source", "app"); }
                    String table;
                    if (WikiLinks.T_PRINCIPLE.equals(type)) {
                        table = "zhi_principles";
                        row.put("kind", fKind);
                        row.put("title", fTitle);
                        row.put("body", fBody);
                        row.put("tags", tagsJson(fTags));
                    } else if (WikiLinks.T_QUESTION.equals(type)) {
                        table = "zhi_questions";
                        row.put("title", fTitle);
                        row.put("body", fBody);
                        row.put("status", fStatus);
                        row.put("short_title", shortFinal);
                        row.put("tags", tagsJson(fTags));
                    } else {
                        table = "zhi_people";
                        row.remove("source");
                        row.put("name", fName);
                        row.put("memo", fMemo);
                        row.put("tags", tagsJson(fTags));
                    }
                    JSONArray back = Supa.restUpsertOne(act, table, "id", row);
                    if (back.length() == 0) throw new Exception("empty back");
                    final JSONObject saved = back.getJSONObject(0);
                    final WikiLinks.Entry se = WikiLinks.T_PRINCIPLE.equals(type) ? WikiLinks.fromPrinciple(saved)
                            : WikiLinks.T_QUESTION.equals(type) ? WikiLinks.fromQuestion(saved)
                            : WikiLinks.fromPerson(saved);
                    act.runOnUiThread(() -> {
                        WikiLinks.noteSaved(act, se);
                        Toast.makeText(act, s("存好了", "Saved"), Toast.LENGTH_SHORT).show();
                        id = itemId;
                        isNew = false;
                        dlg.dismiss();
                        if (done != null) done.saved(type, itemId);
                    });
                } catch (Exception e) {
                    act.runOnUiThread(() -> Toast.makeText(act,
                            s("没存上，内容还在，再试一次", "Couldn't save — nothing was changed, try again"),
                            Toast.LENGTH_SHORT).show());
                }
            }).start();
        }

        // ---------- 关联区（与 ZhiPage 同口径：规范形、全量拉取本地过滤） ----------
        private void loadLinks() {
            if (id == null) return;
            new Thread(() -> {
                try {
                    JSONArray rows = Supa.restGet(act, "zhi_links?select=*&order=created_at.asc");
                    final List<String[]> mine = new ArrayList<>();
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject o = rows.getJSONObject(i);
                        String aT = o.optString("a_type", ""), aI = o.optString("a_id", "");
                        String bT = o.optString("b_type", ""), bI = o.optString("b_id", "");
                        if ((aT.equals(type) && aI.equals(id)) || (bT.equals(type) && bI.equals(id))) {
                            mine.add(new String[]{aT, aI, bT, bI});
                        }
                    }
                    act.runOnUiThread(() -> {
                        linkRows.clear();
                        linkRows.addAll(mine);
                        renderLinks();
                    });
                } catch (Exception ignored) { }
            }).start();
        }

        private List<String> linkedIdsOf(String otherType) {
            List<String> out = new ArrayList<>();
            for (String[] l : linkRows) {
                if (l[0].equals(type) && l[1].equals(id) && l[2].equals(otherType)) out.add(l[3]);
                else if (l[2].equals(type) && l[3].equals(id) && l[0].equals(otherType)) out.add(l[1]);
            }
            return out;
        }

        private void renderLinks() {
            if (linksBox == null || id == null) return;
            linksBox.removeAllViews();
            for (final String otherType : sectionsFor(type)) {
                LinearLayout box = new LinearLayout(act);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setBackground(roundBg(Ui.dark(act) ? Ui.surface(act) : Color.rgb(251, 248, 239), Ui.line(act), 10));
                box.setPadding(dp(act, 13), dp(act, 11), dp(act, 13), dp(act, 11));
                LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                bLp.topMargin = dp(act, 12);
                box.setLayoutParams(bLp);
                LinearLayout head = new LinearLayout(act);
                head.setOrientation(LinearLayout.HORIZONTAL);
                head.setGravity(Gravity.CENTER_VERTICAL);
                List<String> ids = linkedIdsOf(otherType);
                TextView h = tv(s("关联的", "Linked ") + WikiLinks.typeName(otherType)
                        + (ids.isEmpty() ? "" : "（" + ids.size() + "）"), 12, Ui.sub(act));
                h.setLetterSpacing(0.15f);
                head.addView(h);
                View spacer = new View(act);
                head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
                TextView plus = tv(s("＋ 添加", "＋ Add"), 12, Ui.accent(act));
                plus.setPadding(dp(act, 10), dp(act, 2), dp(act, 10), dp(act, 2));
                plus.setBackground(roundBg(Color.TRANSPARENT, Ui.accent(act), 14));
                plus.setOnClickListener(v -> WikiLinks.showPicker(act, otherType, false,
                        (t, oid) -> addLink(otherType, oid), null));
                head.addView(plus);
                box.addView(head);
                for (final String oid : ids) {
                    LinearLayout row = new LinearLayout(act);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(act, 7), 0, dp(act, 7));
                    WikiLinks.Entry e = WikiLinks.resolve(act, otherType, oid);
                    TextView nm = tv(e != null ? e.name : oid, 13.5f, Ui.text(act));
                    nm.setMaxLines(1);
                    nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    row.addView(nm, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    TextView x = tv("✕", 12, Ui.sub(act));
                    x.setPadding(dp(act, 10), dp(act, 2), dp(act, 2), dp(act, 2));
                    x.setOnClickListener(v -> removeLink(otherType, oid));
                    row.addView(x);
                    box.addView(row);
                }
                linksBox.addView(box);
            }
        }

        private void addLink(final String otherType, final String oid) {
            // 规范形：类型名字母序在前（与 ZhiPage.addLink 同构造）
            final String aT, aI, bT, bI;
            if (type.compareTo(otherType) <= 0) { aT = type; aI = id; bT = otherType; bI = oid; }
            else { aT = otherType; aI = oid; bT = type; bI = id; }
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
                        linkRows.add(new String[]{aT, aI, bT, bI});
                        renderLinks();
                        if (done != null) done.saved(type, id);
                    });
                } catch (Exception e) {
                    act.runOnUiThread(() -> Toast.makeText(act,
                            s("没关联上，再试一次", "Couldn't link — try again"), Toast.LENGTH_SHORT).show());
                }
            }).start();
        }

        private void removeLink(final String otherType, final String oid) {
            String[] found = null;
            for (String[] l : linkRows) {
                boolean hit = (l[0].equals(type) && l[1].equals(id) && l[2].equals(otherType) && l[3].equals(oid))
                        || (l[2].equals(type) && l[3].equals(id) && l[0].equals(otherType) && l[1].equals(oid));
                if (hit) { found = l; break; }
            }
            if (found == null) return;
            final String[] fl = found;
            new Thread(() -> {
                try {
                    Supa.restDelete(act, "zhi_links?a_type=eq." + fl[0] + "&a_id=eq." + fl[1]
                            + "&b_type=eq." + fl[2] + "&b_id=eq." + fl[3]);
                    act.runOnUiThread(() -> {
                        linkRows.remove(fl);
                        renderLinks();
                        if (done != null) done.saved(type, id);
                    });
                } catch (Exception e) {
                    act.runOnUiThread(() -> Toast.makeText(act,
                            s("没解除，再试一次", "Couldn't unlink — try again"), Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }
}
