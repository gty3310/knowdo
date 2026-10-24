package com.ti.tasktimer;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * 子任务（v1.11.0）：大任务拆成一串小段，逐个计时（参考 Smart Countdown Timer 的 queue）。
 * 字段与桥 JSON / Supabase tasks.subtasks（jsonb）对齐：{id, title, minutes}。
 * 任务有子任务时，总时长＝子任务分钟之和（TaskItem.syncDurationFromSubtasks 维护）。
 */
public class Subtask {
    public String id;
    public String title = "";
    public int minutes = 5;

    public static Subtask create(String title, int minutes) {
        Subtask s = new Subtask();
        s.id = UUID.randomUUID().toString();
        s.title = title == null ? "" : title;
        s.minutes = Math.max(1, minutes);
        return s;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title == null ? "" : title);
        o.put("minutes", minutes);
        return o;
    }

    public static Subtask fromJson(JSONObject o) {
        Subtask s = new Subtask();
        s.id = o.optString("id", "");
        if (s.id.isEmpty()) s.id = UUID.randomUUID().toString();
        s.title = o.optString("title", "");
        s.minutes = Math.max(1, o.optInt("minutes", 5));
        return s;
    }

    public Subtask copy() {
        Subtask s = new Subtask();
        s.id = id;
        s.title = title;
        s.minutes = minutes;
        return s;
    }
}
