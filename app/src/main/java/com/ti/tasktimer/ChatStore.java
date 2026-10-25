package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 聊天记录（v1.22.0）：只存本机 SharedPreferences，永不上云（v1 口径）。
 * 按对话分键存：任务对话 key＝任务 id，通用对话 key＝"general"。
 * 每条消息：role（user/assistant）、text（展示正文，提案块已剥离）、
 * proposal（提案 JSON 原文，可空）、propKind（提案类型：task / schedule，v1.23.0 加，
 * 旧记录无此键一律按 task 读）、propState（0 无提案 / 1 待决定 / 2 已应用 / 3 已忽略）。
 * 提案状态随消息一起存，历史重开时卡片按存下时的状态渲染，应用过的不再重复应用。
 */
public final class ChatStore {
    private static final String PREF = "chat_history";
    public static final String GENERAL = "general";

    public static final int PROP_NONE = 0;
    public static final int PROP_PENDING = 1;
    public static final int PROP_APPLIED = 2;
    public static final int PROP_DISMISSED = 3;

    private ChatStore() { }

    public static final class Msg {
        public String role = "user";   // user / assistant
        public String text = "";
        public String proposal;        // 提案 JSON 原文；null＝无提案
        public String propKind = "task"; // 提案类型：task / schedule
        public int propState = PROP_NONE;

        public Msg() { }
        public Msg(String role, String text) { this.role = role; this.text = text; }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("r", role);
            o.put("t", text);
            if (proposal != null) o.put("p", proposal);
            if (!"task".equals(propKind)) o.put("k", propKind);
            o.put("s", propState);
            return o;
        }

        static Msg fromJson(JSONObject o) {
            Msg m = new Msg();
            m.role = o.optString("r", "user");
            m.text = o.optString("t", "");
            m.proposal = o.isNull("p") ? null : o.optString("p", null);
            if (m.proposal != null && m.proposal.isEmpty()) m.proposal = null;
            m.propKind = o.optString("k", "task");
            m.propState = o.optInt("s", m.proposal == null ? PROP_NONE : PROP_PENDING);
            return m;
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Msg> load(Context c, String key) {
        List<Msg> out = new ArrayList<>();
        String s = sp(c).getString("hist_" + key, null);
        if (s == null || s.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) out.add(Msg.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    public static void save(Context c, String key, List<Msg> msgs) {
        try {
            JSONArray arr = new JSONArray();
            for (Msg m : msgs) arr.put(m.toJson());
            sp(c).edit().putString("hist_" + key, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** 设置里的一键清除：删掉全部对话记录（所有任务的 + 通用的）。 */
    public static void clearAll(Context c) {
        SharedPreferences.Editor e = sp(c).edit();
        for (String k : sp(c).getAll().keySet()) {
            if (k.startsWith("hist_")) e.remove(k);
        }
        e.apply();
    }
}
