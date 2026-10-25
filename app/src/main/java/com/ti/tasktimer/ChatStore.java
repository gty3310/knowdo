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
 * v1.24.0：聊天升为底部页签「聊聊」，记录改为统一一条主线（key＝"main"）——页签、
 * 任务入口、结束页内嵌聊天区共用同一条；AI 主动消息（分神/超时）也落这条，带
 * origin 标记（distraction / overtime）在气泡旁小字标来源。v1.22/v1.23 的按任务
 * 分键旧记录不迁移、不删除，原样留在 prefs 里。propKind 再加 setting（设置提案）。
 */
public final class ChatStore {
    private static final String PREF = "chat_history";
    public static final String GENERAL = "general";
    /** 统一聊天主线（v1.24.0）：「聊聊」页签展示的就是这一条。 */
    public static final String MAIN = "main";

    /** 主动消息来源标记（Msg.origin）。 */
    public static final String ORIGIN_DISTRACTION = "distraction";
    public static final String ORIGIN_OVERTIME = "overtime";

    /** 主线变更监听：主动消息在别的组件落账时，打开着的聊天界面跟着刷新。 */
    public interface ChangeListener { void onMainChanged(); }

    private static final List<ChangeListener> LISTENERS = new ArrayList<>();

    public static void addChangeListener(ChangeListener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static void removeChangeListener(ChangeListener l) { LISTENERS.remove(l); }

    private static void notifyMainChanged() {
        for (ChangeListener l : new ArrayList<>(LISTENERS)) {
            try { l.onMainChanged(); } catch (Exception ignored) { }
        }
    }

    /** 主动消息来源的小字标签（没有来源返回 null）。 */
    public static String originLabel(String origin) {
        if (ORIGIN_DISTRACTION.equals(origin)) return Strings.en() ? "While distracted" : "分神时";
        if (ORIGIN_OVERTIME.equals(origin)) return Strings.en() ? "At time-up" : "超时时";
        return null;
    }

    public static final int PROP_NONE = 0;
    public static final int PROP_PENDING = 1;
    public static final int PROP_APPLIED = 2;
    public static final int PROP_DISMISSED = 3;

    private ChatStore() { }

    public static final class Msg {
        public String role = "user";   // user / assistant
        public String text = "";
        public String proposal;        // 提案 JSON 原文；null＝无提案
        public String propKind = "task"; // 提案类型：task / schedule / setting（v1.24.0 加 setting）
        public int propState = PROP_NONE;
        public String origin;          // 主动消息来源（v1.24.0）：distraction / overtime；普通消息为 null

        public Msg() { }
        public Msg(String role, String text) { this.role = role; this.text = text; }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("r", role);
            o.put("t", text);
            if (proposal != null) o.put("p", proposal);
            if (!"task".equals(propKind)) o.put("k", propKind);
            o.put("s", propState);
            if (origin != null) o.put("o", origin);
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
            m.origin = o.isNull("o") ? null : o.optString("o", null);
            if (m.origin != null && m.origin.isEmpty()) m.origin = null;
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

    // ---------------- 统一主线（v1.24.0） ----------------

    public static List<Msg> loadMain(Context c) { return load(c, MAIN); }

    /** 存主线并广播变更（写方自己也会收到，靠内容比对认出是自己写的不重画）。 */
    public static void saveMain(Context c, List<Msg> msgs) {
        save(c, MAIN, msgs);
        notifyMainChanged();
    }

    /** 往主线尾部追加一条（主动消息落账走这里）：读最新、追加、裁到最近 120 条、存盘广播。 */
    public static synchronized void appendToMain(Context c, Msg m) {
        List<Msg> ms = load(c, MAIN);
        ms.add(m);
        while (ms.size() > 120) ms.remove(0);
        save(c, MAIN, ms);
        notifyMainChanged();
    }

    /** 设置里的一键清除：删掉全部对话记录（所有任务的 + 通用的 + 主线）。 */
    public static void clearAll(Context c) {
        SharedPreferences.Editor e = sp(c).edit();
        for (String k : sp(c).getAll().keySet()) {
            if (k.startsWith("hist_")) e.remove(k);
        }
        e.apply();
        notifyMainChanged();
    }
}
