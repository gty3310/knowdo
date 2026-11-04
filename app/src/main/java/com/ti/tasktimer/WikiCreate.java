package com.ti.tasktimer;

import android.app.Activity;

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
 * 双链「合上自动新建」的无头建条目（v1.62.0）：用户在正文里选好类型、把原文写完
 * 并打 }} 合上时，按类型当场建条目，不开任何表单。payload 形状与 WikiSheet.saveItem
 * 完全一致（同一批表、同一口径）：人名 name＝原文；原则 title＝原文、kind 默认 think；
 * 问题 title＝原文、status pending、short_title 走 ZhiPage.genShortTitle（chat-proxy
 * 生成、失败截前 10 字兜底）。新建的问题/原则再把原文里字面命中的现有人名写
 * zhi_links 关联（规范形，与 WikiSheet.addLink 同构造）。日记类型不走这里（只许链接）。
 * 全部在后台线程跑；回调在 UI 线程，id 为 null 表示没建上（正文段保持原文不动）。
 */
public final class WikiCreate {
    public interface Done { void created(String type, String id); }

    private WikiCreate() {}

    private static String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    public static void create(final Activity act, final String type, final String text,
                              final Done done) {
        new Thread(() -> {
            String newId = null;
            try {
                final String itemId = UUID.randomUUID().toString();
                final String now = isoNow();
                String shortFinal = "";
                if (WikiLinks.T_QUESTION.equals(type)) {
                    shortFinal = ZhiPage.genShortTitle(act, text, "");   // AI 生成，失败内部兜底
                }
                JSONObject row = new JSONObject();
                row.put("id", itemId);
                row.put("user_id", Supa.userId(act));
                row.put("updated_at", now);
                row.put("created_at", now);
                String table;
                if (WikiLinks.T_PRINCIPLE.equals(type)) {
                    table = "zhi_principles";
                    row.put("kind", "think");
                    row.put("title", text);
                    row.put("body", "");
                    row.put("tags", new JSONArray());
                    row.put("source", "app");
                } else if (WikiLinks.T_QUESTION.equals(type)) {
                    table = "zhi_questions";
                    row.put("title", text);
                    row.put("body", "");
                    row.put("status", "pending");
                    row.put("short_title", shortFinal);
                    row.put("tags", new JSONArray());
                    row.put("source", "app");
                } else if (WikiLinks.T_PERSON.equals(type)) {
                    table = "zhi_people";
                    row.put("name", text);
                    row.put("memo", "");
                    row.put("tags", new JSONArray());
                } else {
                    throw new Exception("type not creatable: " + type);
                }
                JSONArray back = Supa.restUpsertOne(act, table, "id", row);
                if (back.length() == 0) throw new Exception("empty back");
                JSONObject saved = back.getJSONObject(0);
                WikiLinks.Entry se = WikiLinks.T_PRINCIPLE.equals(type) ? WikiLinks.fromPrinciple(saved)
                        : WikiLinks.T_QUESTION.equals(type) ? WikiLinks.fromQuestion(saved)
                        : WikiLinks.fromPerson(saved);
                final WikiLinks.Entry fse = se;
                act.runOnUiThread(() -> WikiLinks.noteSaved(act, fse));
                // 原文里字面命中的现有人名 → 自动关联（仅问题/原则；关联失败不拖垮建条目本身）
                if (WikiLinks.T_QUESTION.equals(type) || WikiLinks.T_PRINCIPLE.equals(type)) {
                    linkMentionedPeople(act, type, itemId, text);
                }
                newId = itemId;
            } catch (Exception ignored) { }
            final String fId = newId;
            act.runOnUiThread(() -> { if (done != null) done.created(type, fId); });
        }).start();
    }

    /** 把 text 里出现的现有人名（快照里的人名，字面包含）逐个写 zhi_links 规范形关联。 */
    private static void linkMentionedPeople(Activity act, String type, String itemId, String text) {
        List<WikiLinks.Entry> people = new ArrayList<>();
        try { people = WikiLinks.entriesOf(act, WikiLinks.T_PERSON); } catch (Exception ignored) { }
        for (WikiLinks.Entry p : people) {
            String nm = p.personName == null ? "" : p.personName.trim();
            if (nm.isEmpty() || !text.contains(nm)) continue;
            // 规范形：类型名字母序在前（person 恒小于 principle/question，与 WikiSheet.addLink 同构造）
            final String aT, aI, bT, bI;
            if (WikiLinks.T_PERSON.compareTo(type) <= 0) {
                aT = WikiLinks.T_PERSON; aI = p.id; bT = type; bI = itemId;
            } else {
                aT = type; aI = itemId; bT = WikiLinks.T_PERSON; bI = p.id;
            }
            try {
                JSONObject row = new JSONObject();
                row.put("id", UUID.randomUUID().toString());
                row.put("user_id", Supa.userId(act));
                row.put("a_type", aT); row.put("a_id", aI);
                row.put("b_type", bT); row.put("b_id", bI);
                row.put("created_at", isoNow());
                Supa.restUpsertOne(act, "zhi_links", "user_id,a_type,a_id,b_type,b_id", row);
            } catch (Exception ignored) { }
        }
    }
}
