package com.ti.tasktimer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
* 「AI」按钮的中转页（透明、瞬间关闭）：在悬浮窗里点 AI 后由它执行——
* 1) 把当前任务的全部信息整理成一段文字，复制到剪贴板（必须由前台 Activity 做，系统才允许）；
* 2) 打开用户设定的 AI 助手（现在是权限页里选的 Muse 应用）：
* 先试「分享文本」直达（对方若支持，文字会直接进输入框），不行就普通打开，粘贴即可。
*/
public class AiLaunchActivity extends Activity {
public static final String EXTRA_TASK_ID = "task_id";

@Override
protected void onCreate(Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
String taskId = getIntent().getStringExtra(EXTRA_TASK_ID);
TaskItem t = new TaskStore(this).byId(taskId);
if (t == null) {
Toast.makeText(this, "没有可发送的任务", Toast.LENGTH_SHORT).show();
finish();
return;
}
String info = buildInfo(t);
ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
if (cm!= null) cm.setPrimaryClip(ClipData.newPlainText("知行任务", info));

String pkg = FocusGuard.musePackage(this);
if (pkg == null) {
Toast.makeText(this, "任务信息已复制。先去「权限」页选一下 Muse 是哪个应用，我再帮你直接打开它",
Toast.LENGTH_LONG).show();
Intent p = new Intent(this, PermActivity.class);
p.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
startActivity(p);
finish();
return;
}
// 先试分享直达：Muse 若接收 ACTION_SEND，文字直接进它的输入框
Intent send = new Intent(Intent.ACTION_SEND);
send.setType("text/plain");
send.putExtra(Intent.EXTRA_TEXT, info);
send.setPackage(pkg);
boolean sent = false;
try {
if (send.resolveActivity(getPackageManager())!= null) {
startActivity(send);
sent = true;
}
} catch (Exception ignored) {}
if (!sent) {
Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
if (launch!= null) {
launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
try { startActivity(launch);} catch (Exception ignored) {}
}
Toast.makeText(this, "任务信息已复制到剪贴板，在 Muse 里粘贴即可", Toast.LENGTH_LONG).show();
} else {
Toast.makeText(this, "任务信息已同时复制到剪贴板（若没自动填入，粘贴即可）", Toast.LENGTH_SHORT).show();
}
finish();
}

private String buildInfo(TaskItem t) {
StringBuilder sb = new StringBuilder();
sb.append("\n");
sb.append("任务：").append(t.title).append('\n');
if (t.scenarios!= null &&!t.scenarios.isEmpty()) {
sb.append("情景：").append(String.join("、", t.scenarios)).append('\n');
}
if ("daily".equals(t.repeat)) sb.append("截止：每日重复\n");
else if (t.deadline!= null) {
    sb.append("截止：").append(t.deadline);
    if (t.deadlineTime != null) sb.append(" ").append(t.deadlineTime).append(" 前");
    sb.append('\n');
}
sb.append("计划时长：").append(t.durationMinutes).append(" 分钟\n");
if (t.actualMinutes > 0) sb.append("实际用时：").append(t.actualMinutes).append(" 分钟\n");
sb.append("优先级：").append("must".equals(t.priority)? "必要": "次要").append('\n');
if (t.focusRule!= null &&!t.focusRule.isEmpty()) {
sb.append("专注规则：").append(t.focusRule).append('\n');
}
// 当前计时状态
if (TimerState.isRunning(this) && t.id.equals(TimerState.taskId(this))) {
long remain = Math.max(0, (TimerState.endAt(this) - System.currentTimeMillis()) / 1000);
sb.append("状态：正在计时，剩余 ").append(remain / 60).append(" 分 ").append(remain % 60).append(" 秒\n");
} else if (t.id.equals(TimerState.pausedTaskId(this))) {
long remain = Math.max(0, TimerState.pausedRemainMs(this) / 1000);
sb.append("状态：已暂停，剩余 ").append(remain / 60).append(" 分 ").append(remain % 60).append(" 秒\n");
} else {
sb.append("状态：未开始\n");
}
sb.append("今日：").append(t.isDoneToday()? "已完成": "未完成").append('\n');
if (t.note!= null &&!t.note.trim().isEmpty()) {
sb.append("笔记：\n").append(t.note).append('\n');
}
return sb.toString();
}
}
