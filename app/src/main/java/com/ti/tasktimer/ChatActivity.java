package com.ti.tasktimer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * AI 对话页（v1.22.0 起；v1.24.0 改为 ChatPanel 的薄宿主）。
 * 两种进法：①任务编辑弹层带任务 id 进入（本轮对话的上下文任务）；②其他全屏页
 * 需要一个独立聊天页时。界面与逻辑全部在 ChatPanel 里——与「聊聊」页签、结束页
 * 内嵌聊天区共用同一条聊天主线（ChatStore.MAIN）和同一套提案卡，不另写第二份。
 * 历史只存本机（ChatStore），设置里可一键清除，不上云。
 */
public class ChatActivity extends Activity {
    public static final String EXTRA_TASK_ID = "task_id";

    private ChatPanel panel;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Strings.init(this);
        TaskStore store = new TaskStore(this);
        String taskId = getIntent().getStringExtra(EXTRA_TASK_ID);
        if (taskId != null && store.byId(taskId) == null) taskId = null; // 任务已删则不带上下文

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));
        setContentView(root);

        // 顶栏：返回＋标题（聊天本体在 ChatPanel 里）
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20), dp(14), dp(20), dp(4));
        TextView back = new TextView(this);
        back.setText("‹ " + (Strings.en() ? "Back" : "返回"));
        back.setTextSize(14);
        back.setTextColor(Ui.link(this));
        back.setPadding(0, dp(2), dp(8), dp(6));
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = new TextView(this);
        TaskItem t = taskId == null ? null : store.byId(taskId);
        title.setText(t != null ? t.title : (Strings.en() ? "Chat with AI" : "和 AI 聊聊"));
        title.setTextSize(20);
        title.setTextColor(Ui.text(this));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        top.addView(title);
        root.addView(top);

        LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        root.addView(host, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        panel = new ChatPanel(this, host, false);
        if (taskId != null) panel.setTaskContext(taskId);
    }

    @Override protected void onDestroy() {
        if (panel != null) panel.detach();
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 从任务编辑弹层打开带任务的对话页。 */
    public static void openForTask(Activity act, String taskId) {
        Intent i = new Intent(act, ChatActivity.class);
        i.putExtra(EXTRA_TASK_ID, taskId);
        act.startActivity(i);
    }

    /** 通用入口（不带任务）。 */
    public static void openGeneral(Activity act) {
        act.startActivity(new Intent(act, ChatActivity.class));
    }
}
