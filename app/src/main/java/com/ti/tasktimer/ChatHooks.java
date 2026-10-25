package com.ti.tasktimer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * AI 主动陪聊的落账钩子（v1.24.0）：两个时刻，AI 会自己往聊天主线里发一条消息——
 * 都是本地文案直接落账（不经代理，代理没部署也照常出现），用户回「聊聊」页签
 * 就能接着聊。两处都有冷却/去重，绝不轰炸：
 * - 分神：屏幕守卫确认一次分神（VerdictEngine.trigger，分神事件落 GuardEvents 的
 *   同一个点）时发，同一次计时会话（taskId＋startedAt）最多 1 条；
 * - 超时：任务真到点、结束页展示时发，同一轮结束（taskId＋endAt）只落 1 条，
 *   结束页被反复打开/重建不重复。
 */
public final class ChatHooks {
    private static final String PREF = "chat_hooks";

    private ChatHooks() { }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static boolean en() { return Strings.en(); }

    /**
     * 分神确认时调用（VerdictEngine.trigger 里、GuardEvents 落账之后）。
     * 开头是用户定的原话；后面结合当前任务问一句：太难/太长，还是状态不对。
     */
    public static void onDistractionConfirmed(Context c, TaskItem task) {
        try {
            if (task == null) return;
            String session = TimerState.taskId(c) + "@" + TimerState.startedAt(c);
            SharedPreferences p = sp(c);
            if (session.equals(p.getString("distraction_session", ""))) return;
            p.edit().putString("distraction_session", session).apply();
            String title = task.title == null ? "" : task.title;
            int mins = Math.max(1, task.durationMinutes);
            String text = en()
                    ? "You've been pretty distracted — let me help you with that.\n"
                      + "This stretch, \"" + title + "\" doesn't seem to be moving — is it too hard, "
                      + "too long (it's set to " + mins + " minutes), or is it just not the right moment? "
                      + "Tell me, and we'll shrink it, split it, or swap to something else together."
                    : "你已经很分神了啊，让我来帮帮你。\n"
                      + "刚才这段时间，「" + title + "」好像没太推进——是这个任务太难、太长（它现在是 "
                      + mins + " 分钟），还是这会儿状态不对？跟我说说，我们一起把它改小、拆开，或者先换个任务做。";
            ChatStore.Msg m = new ChatStore.Msg("assistant", text);
            m.origin = ChatStore.ORIGIN_DISTRACTION;
            ChatStore.appendToMain(c, m);
            TimerState.logEvent(c, "聊天陪聊：分神确认，已往「聊聊」主线发一条主动消息（本计时会话只发这一条）");
        } catch (Exception ignored) { }
    }

    /**
     * 结束页展示时调用（CheckActivity.showEnd）：只有计时真的已经到点才落
     * （手动提前打开结束页不算超时，不发）。同一轮结束只落一次。
     */
    public static void onTaskEndPageShown(Context c) {
        try {
            long endAt = TimerState.endAt(c);
            if (endAt <= 0 || endAt > System.currentTimeMillis() + 1000L) return;
            String key = TimerState.taskId(c) + "@" + endAt;
            SharedPreferences p = sp(c);
            if (key.equals(p.getString("overtime_key", ""))) return;
            p.edit().putString("overtime_key", key).apply();
            String title = TimerState.taskTitle(c);
            String text = en()
                    ? "Time's up on \"" + title + "\". How did it go just now — finished, "
                      + "nearly there, or stuck? Tell me; if you got stuck, we'll shrink it, "
                      + "split it, or find it a new time together."
                    : "「" + title + "」的时间到了。刚才做得怎么样——做完了、还差一点，还是卡住了？"
                      + "跟我说说，卡住的话我们一起把它改小、拆开，或者重新排个时间。";
            ChatStore.Msg m = new ChatStore.Msg("assistant", text);
            m.origin = ChatStore.ORIGIN_OVERTIME;
            ChatStore.appendToMain(c, m);
            TimerState.logEvent(c, "聊天陪聊：任务到点，已往「聊聊」主线发一条开场消息（本轮结束只发这一条）");
        } catch (Exception ignored) { }
    }
}
