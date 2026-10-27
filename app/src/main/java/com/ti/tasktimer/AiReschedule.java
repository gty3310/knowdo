package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 日历页「AI 重排」（v1.26.0 改道）：走软件内置的聊天后端（chat-proxy →
 * OpenRouter，与「聊聊」同一条通道），当场生成一版重排提案，先摆成对话框
 * 给用户看——点「应用」才落盘（SyncManager.applyScheduleProposal，既有排程
 * 写入口：已过去/已完成的块原样保留），点「不用」整版作废。
 * v1.26.0 之前这颗按钮写的是云端 reschedule_request、靠软件之外的巡查来排；
 * 那条接线已撤除，本类是唯一的重排请求路径。排程硬规矩与聊天里的排程提案
 * 完全一致（每件按时长×1.5 占时、必做优先、已过与已完成块不动、不排过
 * 22:30、排不下明说），写在发给模型的 system 里，落盘前再按同口径校验一遍。
 */
public final class AiReschedule {

    private AiReschedule() {}

    private static final String FENCE = "`" + "`" + "`knowdo-schedule";
    private static final String FENCE_MARK = "`" + "`" + "`";

    /**
     * 发起一次重排：host 必须是 Activity（对话框要挂在它上面）。
     * onSettled 在整条链结束时回主线程调用（出错、用户点「不用」、或应用完成），
     * 调用方据此恢复按钮态并重画日历。
     */
    public static void run(final Activity host, final TaskStore store, final Runnable onSettled) {
        final boolean en = Strings.en();
        store.load();
        JSONArray messages;
        try {
            JSONArray arr = new JSONArray();
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", buildSystem(host, store));
            arr.put(sys);
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", en ? "Please re-plan the rest of today following the rules above"
                    : "请按上面的规矩，重排今天剩下的日程");
            arr.put(u);
            messages = arr;
        } catch (Exception e) {
            Toast.makeText(host, en ? "Could not put the request together — try again"
                    : "重排请求没拼好，再试一次", Toast.LENGTH_LONG).show();
            if (onSettled != null) onSettled.run();
            return;
        }
        ChatClient.send(host, messages, new ChatClient.Callback() {
            @Override public void onModel(String modelId) { }
            @Override public void onDelta(String fullText) { }
            @Override public void onError(String friendlyMsg) {
                Toast.makeText(host, friendlyMsg, Toast.LENGTH_LONG).show();
                if (onSettled != null) onSettled.run();
            }
            @Override public void onDone(String fullText) {
                String proposal = extractProposal(fullText);
                List<LocalAutoScheduler.Block> blocks = proposal == null
                        ? new ArrayList<>() : parseBlocks(store, proposal);
                List<String> unscheduled = proposal == null
                        ? new ArrayList<>() : parseUnscheduled(proposal);
                if (blocks.isEmpty() && unscheduled.isEmpty()) {
                    Toast.makeText(host, en ? "The AI did not return a usable plan — try again in a bit"
                            : "AI 这次没给出能用的排法，过一会儿再试", Toast.LENGTH_LONG).show();
                    if (onSettled != null) onSettled.run();
                    return;
                }
                showProposal(host, store, blocks, unscheduled, onSettled);
            }
        });
    }

    /** system：与聊天排程提案同源的上下文（今日已排块/未排必做/全部未完成/时间账）＋硬规矩＋出块格式。 */
    private static String buildSystem(Activity host, TaskStore store) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是知行 KnowDo 内置的 AI 助手。用户点了日历页的「AI 重排」，请你重排他今天剩下的日程。")
          .append("用用户的语言回复（默认中文），正文简短交代思路即可，排法本身用文末的代码块给出。");
        sb.append("\n\n今天当下的情况：\n");
        String today = TaskItem.today();
        Set<String> scheduledIds = new HashSet<>();
        List<LocalAutoScheduler.Block> todayBlocks =
                LocalAutoScheduler.blocksOf(SyncManager.latestSchedule(host), today);
        if (todayBlocks.isEmpty()) {
            sb.append("今天还没有排任何块。\n");
        } else {
            sb.append("今天已经排好的块（开始–结束 任务名〔taskId〕）：\n");
            for (LocalAutoScheduler.Block b : todayBlocks) {
                TaskItem bt = store.byId(b.taskId);
                sb.append("- ").append(LocalAutoScheduler.hhmm(b.startMin)).append("–")
                  .append(LocalAutoScheduler.hhmm(b.endMin)).append(" ")
                  .append(bt != null ? bt.title : b.taskId)
                  .append("〔").append(b.taskId).append("〕\n");
                scheduledIds.add(b.taskId);
            }
        }
        StringBuilder unscheduledMust = new StringBuilder();
        StringBuilder allOpen = new StringBuilder();
        int open = 0;
        for (TaskItem t : store.visibleSorted()) {
            if (t.isDoneToday()) continue;
            boolean must = "must".equals(t.priority);
            if (must && !scheduledIds.contains(t.id)) {
                unscheduledMust.append("- 〔").append(t.id).append("〕").append(t.title)
                        .append("（").append(t.durationMinutes).append(" 分钟）\n");
            }
            if (open < 25) {
                allOpen.append("- 〔").append(t.id).append("〕").append(t.title)
                        .append("（").append(t.durationMinutes).append(" 分钟，")
                        .append(TaskItem.priorityLabel(t.priority, false))
                        .append(t.deadline != null ? "，截止 " + t.deadline : "，无截止日")
                        .append("）\n");
                open++;
            }
        }
        sb.append("还没排进今天的必做任务：\n")
          .append(unscheduledMust.length() > 0 ? unscheduledMust.toString() : "（没有）\n");
        sb.append("今天全部未完成的任务（排程只能引用这里的 taskId）：\n")
          .append(allOpen.length() > 0 ? allOpen.toString() : "（没有）\n");
        DayAccount.Result a = DayAccount.compute(store);
        sb.append("今日时间账：还剩约 ").append(DayAccount.fmtDur(a.remainMin))
          .append("，必干占约 ").append(DayAccount.fmtDur(a.mustBufferedMin))
          .append("，空余约 ").append(a.freeMin < 0
                  ? "−" + DayAccount.fmtDur(-a.freeMin) + "（已经超了）"
                  : DayAccount.fmtDur(a.freeMin))
          .append("。现在时间约 ")
          .append(LocalAutoScheduler.hhmm(DayAccount.nowMinutes())).append("。\n");
        // ---- v1.27.0 上下文增强 ①基准排序规则原文（与快速重排共用同一份，LocalAutoScheduler.rulesText）----
        sb.append("\n基准排序逻辑（内置「快速重排」用的就是这套，你以它为基准排）：\n");
        sb.append(LocalAutoScheduler.rulesText());
        sb.append("你可以结合下面的日历约会与今天实际节奏微调顺序，但红线不变：不许改任何任务的优先级档位，优先级层级永不自动变（下面的硬规矩里再申一遍）。\n");
        // ---- v1.27.0 上下文增强 ②今天的 Google 日历事件（已有 gcal 缓存，只作参考、排程不用避让）----
        sb.append("\n今天的其他日程（Google 日历，标题＋起止，只作参考背景）：\n");
        sb.append("按既定口径，排任务不用绕开它们；心里有数即可——若某时段正好压着重要约会，可在正文里提醒一句。\n");
        List<String> gcalLines = todayGcalLines(host, today);
        if (gcalLines.isEmpty()) {
            sb.append("（今天没有 Google 日历事件）\n");
        } else {
            for (String l : gcalLines) sb.append("- ").append(l).append("\n");
        }
        // ---- v1.27.0 上下文增强 ③今天已经做的事（已完成任务＋实际执行段，来自实际栏数据）----
        sb.append("\n今天已经做过的事：\n");
        List<String> doneLines = todayDoneLines(host, store, today);
        if (doneLines.isEmpty()) {
            sb.append("（今天还没有做完的任务，也没有实际执行记录）\n");
        } else {
            for (String l : doneLines) sb.append("- ").append(l).append("\n");
        }
        sb.append("已做完的任务不要再排；实际执行段反映他今天真实的节奏与用时，排剩余时段时参考。\n");
        sb.append("\n在回复的最后另起一段，输出一个代码块，格式严格如下：\n")
          .append(FENCE).append("\n")
          .append("{\"blocks\":[{\"taskId\":\"任务id\",\"start\":\"09:00\",\"end\":\"10:30\"}],\"unscheduled\":[\"排不下的任务名\"]}\n")
          .append(FENCE_MARK).append("\n")
          .append("blocks 给出你建议的、尚未开始的块；unscheduled 放实在排不下的任务名，没有就给空数组。")
          .append("排程硬规矩，必须遵守：")
          .append("每件任务按它的计划时长×1.5 留缓冲来占时间；")
          .append("优先级顺序：必做无条件先排，然后今天到期的重要、今天到期的次要，最后未到期的重要、未到期的次要；优先级层级永不自动变，你也不许改任何任务的优先级；休息（leisure）是娱乐类，永不排进日程；")
          .append("已经过去的块和已完成任务的块不许动，也不要把它们写进 blocks（应用时它们会被原样保留）；")
          .append("块的开始时间不许早于现在；")
          .append("不许排过 22:30；")
          .append("排不下就放进 unscheduled 并在正文里明说，不许硬塞；")
          .append("只能用上面列出的真实 taskId，不许编造任务或 id。")
          .append("这版排程只是建议：用户点「应用」才生效。");
        return sb.toString();
    }

    /** 今天的 Google 日历事件行（标题＋起止）：读 App 已有的 gcal 缓存（SyncManager.latestGcal）。 */
    private static List<String> todayGcalLines(Activity host, String today) {
        List<String> out = new ArrayList<>();
        try {
            JSONObject g = SyncManager.latestGcal(host);
            if (g == null || !today.equals(g.optString("date", ""))) return out;
            JSONArray ev = g.optJSONArray("events");
            if (ev == null) return out;
            for (int i = 0; i < ev.length() && out.size() < 20; i++) {
                JSONObject e = ev.optJSONObject(i);
                if (e == null) continue;
                String title = e.optString("title", "");
                if (title.isEmpty()) continue;
                if (e.optBoolean("allDay", false)) {
                    out.add(title + "（全天）");
                } else {
                    out.add(e.optString("start", "") + "–" + e.optString("end", "") + " " + title);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    /**
     * 今天已经做过的事：已完成任务的标题＋实际执行段（SessionLog，实际栏同源数据）。
     * 行格式：「标题（今天已完成）」与「HH:mm–HH:mm 标题（实际约 N 分钟）」。
     */
    private static List<String> todayDoneLines(Activity host, TaskStore store, String today) {
        List<String> out = new ArrayList<>();
        for (TaskItem t : store.tasks) {
            if (!t.deleted && t.isDoneToday()) out.add(t.title + "（今天已完成）");
        }
        try {
            java.text.SimpleDateFormat hm = new java.text.SimpleDateFormat("HH:mm",
                    java.util.Locale.US);
            List<SessionLog.Seg> segs = SessionLog.segmentsForDay(host, today);
            for (int i = 0; i < segs.size() && i < 40; i++) {
                SessionLog.Seg s = segs.get(i);
                String line = hm.format(new java.util.Date(s.startMs)) + "–"
                        + (s.endMs > 0 ? hm.format(new java.util.Date(s.endMs)) : "进行中")
                        + " " + s.title;
                if (s.endMs > s.startMs) {
                    line += "（实际约 " + Math.max(1, (s.endMs - s.startMs) / 60000) + " 分钟）";
                }
                out.add(line);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** 从回复全文里剥出 knowdo-schedule 代码块的 JSON 原文；没有返回 null。 */
    private static String extractProposal(String fullText) {
        if (fullText == null) return null;
        int start = fullText.indexOf(FENCE);
        if (start < 0) return null;
        int jsonStart = fullText.indexOf('\n', start);
        int end = jsonStart < 0 ? -1 : fullText.indexOf(FENCE_MARK, jsonStart);
        if (jsonStart < 0 || end <= jsonStart) return null;
        return fullText.substring(jsonStart + 1, end).trim();
    }

    /** 落盘前校验（与聊天排程卡同口径）：任务真实存在、时间合法、不排过 22:30、开始不早于现在。 */
    static List<LocalAutoScheduler.Block> parseBlocks(TaskStore store, String proposalJson) {
        List<LocalAutoScheduler.Block> out = new ArrayList<>();
        try {
            JSONObject p = new JSONObject(proposalJson);
            JSONArray arr = p.optJSONArray("blocks");
            if (arr == null) return out;
            int nowMin = DayAccount.nowMinutes();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String tid = o.optString("taskId", "");
                if (tid.isEmpty() || store.byId(tid) == null) continue;
                int s = LocalAutoScheduler.parseHhmm(o.optString("start", ""));
                int e = LocalAutoScheduler.parseHhmm(o.optString("end", ""));
                if (s < 0 || e <= s || e > DayAccount.DAY_END_MIN) continue;
                if (s < nowMin) continue;
                out.add(new LocalAutoScheduler.Block(tid, s, e));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static List<String> parseUnscheduled(String proposalJson) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONObject(proposalJson).optJSONArray("unscheduled");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** 提案对话框：逐块列清「几点到几点 做什么」，排不下的单列；应用才落盘。 */
    private static void showProposal(final Activity host, final TaskStore store,
                                     final List<LocalAutoScheduler.Block> blocks,
                                     List<String> unscheduled, final Runnable onSettled) {
        final boolean en = Strings.en();
        StringBuilder body = new StringBuilder();
        for (LocalAutoScheduler.Block b : blocks) {
            TaskItem t = store.byId(b.taskId);
            body.append(LocalAutoScheduler.hhmm(b.startMin)).append("–")
                .append(LocalAutoScheduler.hhmm(b.endMin)).append("  ")
                .append(t != null ? t.title : b.taskId).append("\n");
        }
        for (String name : unscheduled) {
            body.append(en ? "No room for: " : "排不下：").append(name).append("\n");
        }
        if (body.length() == 0) body.append(en ? "Nothing schedulable" : "没有能落盘的块");
        new AlertDialog.Builder(host)
                .setTitle(en ? "AI suggests this plan for today" : "AI 建议这样排今天")
                .setMessage(body.toString().trim())
                .setPositiveButton(en ? "Apply" : "应用", (d, w) -> {
                    if (blocks.isEmpty()) {
                        Toast.makeText(host, en ? "Nothing in this plan can be placed now"
                                : "这版排程里没有现在能落盘的块", Toast.LENGTH_LONG).show();
                        if (onSettled != null) onSettled.run();
                        return;
                    }
                    store.load();
                    Toast.makeText(host, en ? "Saving the plan…" : "正在保存排程…", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        final String err = SyncManager.applyScheduleProposal(
                                host, store, blocks, "KnowDo App（AI 重排）");
                        host.runOnUiThread(() -> {
                            Toast.makeText(host, err == null
                                            ? (en ? "Plan applied — see the calendar" : "已按这个排好了")
                                            : err,
                                    Toast.LENGTH_LONG).show();
                            if (onSettled != null) onSettled.run();
                        });
                    }).start();
                })
                .setNegativeButton(en ? "No, thanks" : "不用", (d, w) -> {
                    if (onSettled != null) onSettled.run();
                })
                .setOnCancelListener(d -> {
                    if (onSettled != null) onSettled.run();
                })
                .show();
    }
}
