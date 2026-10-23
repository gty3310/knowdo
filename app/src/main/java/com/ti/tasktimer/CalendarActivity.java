package com.ti.tasktimer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
* 日历页（今日时间表）：纵向一天时间轴，左泳道是计划、右泳道是实际。
* 计划来自桥文件顶层 schedule（Muse 排的，App 只读）；实际来自本地计时段记录 SessionLog。
* 本页在前台时每 10 秒同步一次桥并重画——Muse 在桥端改了 schedule，这里很快就能看到新版。
*/
public class CalendarActivity extends Activity {
private static final int DAY_START_MIN = 5 * 60; // 轴默认 05:00
private static final int DAY_END_MIN = 23 * 60; // 轴默认 23:00
private static final int SLEEP_MIN = 22 * 60 + 30; // 22:30 睡觉，自由时间算到这里

private TaskStore store;
private TextView overviewView;
private TextView doneView;
private TextView hintView;
private FrameLayout canvas;
private LinearLayout listBox;
private final Handler handler = new Handler(Looper.getMainLooper());
private boolean foreground = false;
private boolean syncing = false;

private final Runnable refresher = new Runnable() {
@Override public void run() {
syncAndRender();
if (foreground) handler.postDelayed(this, 10_000);
}
};

private static class Block {
String taskId = "";
String title = "";
int startMin, endMin;
boolean done, running;
}

@Override
protected void onCreate(Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
store = new TaskStore(this);
ScrollView scroll = new ScrollView(this);
LinearLayout root = new LinearLayout(this);
root.setOrientation(LinearLayout.VERTICAL);
root.setPadding(dp(16), dp(18), dp(16), dp(28));
root.setBackgroundColor(Color.rgb(250, 250, 250));
scroll.addView(root);
setContentView(scroll);

TextView title = new TextView(this);
title.setText("今日日程 · " + TaskItem.today());
title.setTextSize(21);
title.setTextColor(Color.BLACK);
root.addView(title);

overviewView = new TextView(this);
overviewView.setTextSize(14);
overviewView.setTextColor(Color.rgb(33, 33, 33));
overviewView.setPadding(0, dp(6), 0, 0);
root.addView(overviewView);

doneView = new TextView(this);
doneView.setTextSize(12);
doneView.setTextColor(Color.rgb(140, 140, 140));
doneView.setPadding(0, dp(2), 0, 0);
root.addView(doneView);

Button askAi = new Button(this);
askAi.setText("问 AI：让 Muse 帮我重排今天剩下的日程");
askAi.setOnClickListener(v -> {
Intent i = new Intent(this, AiLaunchActivity.class);
i.putExtra(AiLaunchActivity.EXTRA_TEXT, buildAskAiText());
startActivity(i);
});
LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
alp.setMargins(0, dp(10), 0, dp(4));
root.addView(askAi, alp);

hintView = new TextView(this);
hintView.setTextSize(13);
hintView.setTextColor(Color.rgb(183, 28, 28));
hintView.setPadding(0, dp(4), 0, dp(4));
root.addView(hintView);

LinearLayout laneHead = new LinearLayout(this);
laneHead.setOrientation(LinearLayout.HORIZONTAL);
laneHead.setPadding(dp(46), dp(6), 0, dp(2));
TextView lh1 = laneLabel("计划（Muse 排）");
lh1.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
laneHead.addView(lh1);
TextView lh2 = laneLabel("实际（计时）");
lh2.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
laneHead.addView(lh2);
root.addView(laneHead);

canvas = new FrameLayout(this);
root.addView(canvas);

listBox = new LinearLayout(this);
listBox.setOrientation(LinearLayout.VERTICAL);
listBox.setPadding(0, dp(14), 0, 0);
root.addView(listBox);

render();
}

@Override
protected void onResume() {
super.onResume();
foreground = true;
handler.removeCallbacks(refresher);
handler.post(refresher);
}

@Override
protected void onPause() {
foreground = false;
handler.removeCallbacks(refresher);
super.onPause();
}

/** 后台线程同步一次桥（防重复触发），回来后重画。同步本身不阻塞界面。 */
private void syncAndRender() {
if (syncing) { render(); return;}
syncing = true;
new Thread(() -> {
try {
String r = SyncManager.syncNow(this, store);
SyncManager.setLastResult(this, r);
} catch (Exception ignored) {}
handler.post(() -> {
syncing = false;
store.load();
if (foreground) render();
});
}).start();
}

// ---------------- 数据整理 ----------------

private String today() { return TaskItem.today();}

private boolean isTodayTask(TaskItem t) {
if (t.deleted) return false;
if ("daily".equals(t.repeat)) return true;
if (t.deadline == null) return true;
return t.deadline.compareTo(today()) <= 0;
}

/** 读桥上的 schedule；字段缺失或 date 不是今天返回 null。 */
private List<Block> planBlocks() {
JSONObject sch = SyncManager.latestSchedule(this);
if (sch == null) return null;
if (!today().equals(sch.optString("date", ""))) return null;
JSONArray arr = sch.optJSONArray("blocks");
if (arr == null) return null;
List<Block> out = new ArrayList<>();
for (int i = 0; i < arr.length(); i++) {
JSONObject o = arr.optJSONObject(i);
if (o == null) continue;
int s = parseMin(o.optString("start", ""));
int e = parseMin(o.optString("end", ""));
if (s < 0 || e <= s) continue;
Block b = new Block();
b.taskId = o.optString("taskId", "");
b.startMin = s;
b.endMin = e;
TaskItem t = store.byId(b.taskId);
if (t!= null && t.deleted) continue;
b.title = t!= null? t.title: "（未知任务）";
b.done = t!= null && t.isDoneToday();
b.running = TimerState.isRunning(this) && b.taskId.equals(TimerState.taskId(this));
out.add(b);
}
out.sort((a, b) -> a.startMin!= b.startMin? a.startMin - b.startMin: a.endMin - b.endMin);
return out;
}

/** 今天的实际计时段（含正在进行的那一段），来自本地 SessionLog。 */
private List<Block> actualBlocks() {
List<Block> out = new ArrayList<>();
for (SessionLog.Seg s: SessionLog.segmentsForDay(this, today())) {
Block b = new Block();
b.taskId = s.taskId;
TaskItem t = store.byId(s.taskId);
b.title = t!= null? t.title: (s.title.isEmpty()? "（已删除的任务）": s.title);
b.startMin = minOfDay(s.startMs);
b.endMin = Math.max(b.startMin + 1, minOfDay(s.endMs));
b.done = t!= null && t.isDoneToday();
out.add(b);
}
// 正在计时的那一段：从打开段的开始时刻画到现在
if (TimerState.isRunning(this)) {
long startMs = SessionLog.openStart(this);
if (startMs <= 0) startMs = TimerState.startedAt(this);
long now = System.currentTimeMillis();
if (startMs > 0 && startMs < now && sameDay(startMs)) {
Block b = new Block();
b.taskId = TimerState.taskId(this);
TaskItem t = store.byId(b.taskId);
b.title = t!= null? t.title: TimerState.taskTitle(this);
b.startMin = minOfDay(startMs);
b.endMin = Math.max(b.startMin + 1, minOfDay(now));
b.running = true;
out.add(b);
}
}
out.sort((a, b) -> a.startMin!= b.startMin? a.startMin - b.startMin: a.endMin - b.endMin);
return out;
}

/** 从现在到 22:30 之间、没被计划块占用的分钟数。 */
private int freeMinutes(List<Block> plan) {
int nowMin = minOfDay(System.currentTimeMillis());
int from = Math.max(nowMin, 0);
int to = SLEEP_MIN;
if (plan == null || from >= to) return Math.max(0, to - from);
List<int[]> spans = new ArrayList<>();
for (Block b: plan) {
int s = Math.max(b.startMin, from), e = Math.min(b.endMin, to);
if (e > s) spans.add(new int[]{s, e});
}
spans.sort((a, b) -> a[0] - b[0]);
int used = 0, curS = -1, curE = -1;
for (int[] sp: spans) {
if (curS < 0) { curS = sp[0]; curE = sp[1];}
else if (sp[0] <= curE) { curE = Math.max(curE, sp[1]);}
else { used += curE - curS; curS = sp[0]; curE = sp[1];}
}
if (curS >= 0) used += curE - curS;
return Math.max(0, (to - from) - used);
}

// ---------------- 渲染 ----------------

private void render() {
if (canvas == null) return;
store.load();
List<Block> plan = planBlocks();
List<Block> actual = actualBlocks();
List<TaskItem> done = new ArrayList<>();
List<TaskItem> todo = new ArrayList<>();
for (TaskItem t: store.tasks) {
if (!isTodayTask(t)) continue;
if (t.isDoneToday()) done.add(t); else todo.add(t);
}
int free = freeMinutes(plan);
overviewView.setText("今日已完成 " + done.size() + " 件 · 未完成 " + todo.size()
+ " 件 · 还可自由安排 " + free + " 分钟（到 22:30）");
if (done.isEmpty()) {
doneView.setText("");
} else {
StringBuilder sb = new StringBuilder("已完成：");
for (int i = 0; i < done.size(); i++) {
if (i > 0) sb.append("、");
sb.append(done.get(i).title);
}
doneView.setText(sb.toString());
}

// 时间轴范围：默认 05:00–23:00，内容超出就扩
int axisStart = DAY_START_MIN, axisEnd = DAY_END_MIN;
List<Block> all = new ArrayList<>();
if (plan!= null) all.addAll(plan);
all.addAll(actual);
for (Block b: all) {
axisStart = Math.min(axisStart, (b.startMin / 30) * 30);
axisEnd = Math.max(axisEnd, ((b.endMin + 29) / 30) * 30);
}
int nowMin = minOfDay(System.currentTimeMillis());
axisStart = Math.min(axisStart, (nowMin / 60) * 60);
axisEnd = Math.max(axisEnd, Math.min(24 * 60, (nowMin / 60 + 1) * 60));

int totalW = getResources().getDisplayMetrics().widthPixels - dp(32);
int gutter = dp(46), gap = dp(6);
int laneW = Math.max(dp(60), (totalW - gutter - gap) / 2);
int planX = gutter, actX = gutter + laneW + gap;
int scale = dp(1); // 1 分钟 = 1dp
int heightPx = (axisEnd - axisStart) * scale;
canvas.removeAllViews();
canvas.setLayoutParams(new LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT, heightPx));

// 整点横线 + 时刻标签
for (int h = (axisStart + 59) / 60 * 60; h <= axisEnd; h += 60) {
int y = (h - axisStart) * scale;
View line = new View(this);
line.setBackgroundColor(Color.rgb(224, 224, 224));
FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(totalW - gutter, 1);
llp.leftMargin = gutter;
llp.topMargin = y;
canvas.addView(line, llp);
TextView lab = new TextView(this);
lab.setText(String.format(Locale.US, "%02d:00", h / 60));
lab.setTextSize(10);
lab.setTextColor(Color.rgb(140, 140, 140));
FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(gutter - dp(4), dp(14));
tlp.leftMargin = 0;
tlp.topMargin = Math.max(0, y - dp(7));
canvas.addView(lab, tlp);
}
// 计划块（左泳道，浅色描边）
if (plan!= null) for (Block b: plan) addBlock(b, planX, laneW, axisStart, scale, true);
// 实际块（右泳道，实心）
for (Block b: actual) addBlock(b, actX, laneW, axisStart, scale, false);
// 现在线
if (nowMin >= axisStart && nowMin <= axisEnd) {
int y = (nowMin - axisStart) * scale;
View nl = new View(this);
nl.setBackgroundColor(Color.rgb(229, 57, 53));
FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(totalW, dp(2));
nlp.topMargin = y;
canvas.addView(nl, nlp);
TextView nt = new TextView(this);
nt.setText("现在 " + hhmm(nowMin));
nt.setTextSize(9);
nt.setTextColor(Color.rgb(229, 57, 53));
nt.setTypeface(nt.getTypeface(), Typeface.BOLD);
FrameLayout.LayoutParams ntlp = new FrameLayout.LayoutParams(gutter, dp(12));
ntlp.topMargin = Math.max(0, y - dp(12));
canvas.addView(nt, ntlp);
}

// 下方清单：未排入日程的任务 / 无日程时的今日任务
listBox.removeAllViews();
Set<String> scheduledIds = new HashSet<>();
if (plan!= null) for (Block b: plan) scheduledIds.add(b.taskId);
List<TaskItem> unscheduled = new ArrayList<>();
for (TaskItem t: todo) if (!scheduledIds.contains(t.id)) unscheduled.add(t);
if (plan == null) {
hintView.setText("今天还没有排日程：在对话里告诉 Muse 你今天的任务，或点上方「问 AI」，Muse 排好后这里会自动显示。");
hintView.setVisibility(View.VISIBLE);
addListTitle("今天的任务（还没排日程）");
} else {
hintView.setVisibility(View.GONE);
addListTitle("未排入日程的任务");
}
if (unscheduled.isEmpty()) {
addListLine(plan == null? "（今天没有未完成的任务）": "（未完成的任务都已排入日程）", Color.rgb(120, 120, 120));
} else {
for (TaskItem t: unscheduled) {
addListLine("• " + t.title + "（计划 " + t.durationMinutes + " 分钟"
+ ("must".equals(t.priority)? " · 必做": "") + "）", Color.rgb(33, 33, 33));
}
}
if (!actual.isEmpty()) {
addListTitle("今天实际投入（计时记录）");
for (Block b: actual) {
addListLine("• " + hhmm(b.startMin) + "–" + hhmm(b.endMin) + " " + b.title
+ (b.running? "（正在计时）": ""), Color.rgb(46, 125, 50));
}
}
}

private void addBlock(Block b, int x, int w, int axisStart, int scale, boolean planned) {
TextView v = new TextView(this);
String label = hhmm(b.startMin) + "–" + hhmm(b.endMin) + "\n" + b.title
+ (b.running? " ▶": "");
v.setText(label);
v.setTextSize(11);
v.setPadding(dp(5), dp(2), dp(3), dp(2));
v.setMaxLines(3);
v.setEllipsize(android.text.TextUtils.TruncateAt.END);
GradientDrawable bg = new GradientDrawable();
bg.setCornerRadius(dp(4));
if (planned) {
if (b.done) {
bg.setColor(Color.rgb(238, 238, 238));
bg.setStroke(dp(1), Color.rgb(158, 158, 158));
v.setTextColor(Color.rgb(130, 130, 130));
} else if (b.running) {
bg.setColor(Color.rgb(187, 222, 251));
bg.setStroke(dp(2), Color.rgb(25, 118, 210));
v.setTextColor(Color.rgb(13, 71, 161));
} else {
bg.setColor(Color.rgb(227, 242, 253));
bg.setStroke(dp(1), Color.rgb(25, 118, 210));
v.setTextColor(Color.rgb(13, 71, 161));
}
} else {
if (b.done) {
bg.setColor(Color.rgb(224, 224, 224));
v.setTextColor(Color.rgb(120, 120, 120));
} else if (b.running) {
bg.setColor(Color.rgb(129, 199, 132));
bg.setStroke(dp(2), Color.rgb(27, 94, 32));
v.setTextColor(Color.rgb(20, 60, 24));
} else {
bg.setColor(Color.rgb(165, 214, 167));
v.setTextColor(Color.rgb(27, 94, 32));
}
}
v.setBackground(bg);
int hPx = Math.max(dp(20), (b.endMin - b.startMin) * scale);
FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, hPx);
lp.leftMargin = x;
lp.topMargin = (b.startMin - axisStart) * scale;
canvas.addView(v, lp);
}

private TextView laneLabel(String s) {
TextView t = new TextView(this);
t.setText(s);
t.setTextSize(11);
t.setTextColor(Color.rgb(120, 120, 120));
return t;
}

private void addListTitle(String s) {
TextView t = new TextView(this);
t.setText(s);
t.setTextSize(14);
t.setTextColor(Color.BLACK);
t.setTypeface(t.getTypeface(), Typeface.BOLD);
t.setPadding(0, dp(10), 0, dp(3));
listBox.addView(t);
}

private void addListLine(String s, int color) {
TextView t = new TextView(this);
t.setText(s);
t.setTextSize(13);
t.setTextColor(color);
t.setPadding(0, dp(1), 0, dp(1));
listBox.addView(t);
}

// ---------------- 问 AI 文案 ----------------

private String buildAskAiText() {
store.load();
String today = today();
List<Block> plan = planBlocks();
List<Block> actual = actualBlocks();
List<TaskItem> done = new ArrayList<>();
List<TaskItem> todo = new ArrayList<>();
for (TaskItem t: store.tasks) {
if (!isTodayTask(t)) continue;
if (t.isDoneToday()) done.add(t); else todo.add(t);
}
StringBuilder sb = new StringBuilder();
sb.append("").append(today)
.append("，现在 ").append(new SimpleDateFormat("HH:mm", Locale.US).format(new Date())).append('\n');
sb.append("已完成（").append(done.size()).append("）：")
.append(done.isEmpty()? "无": joinTitles(done)).append('\n');
sb.append("未完成（").append(todo.size()).append("）：")
.append(todo.isEmpty()? "无": joinTitles(todo)).append('\n');
if (plan!= null) {
sb.append("当前计划安排（桥文件 schedule）：\n");
for (Block b: plan) {
TaskItem t = store.byId(b.taskId);
sb.append(hhmm(b.startMin)).append('–').append(hhmm(b.endMin)).append(' ')
.append(b.title)
.append(b.done? "（已完成）": b.running? "（正在计时）": "（未完成）");
if (t!= null) sb.append("［id:").append(t.id).append('］');
sb.append('\n');
}
} else {
sb.append("当前计划安排：今天还没有排日程。\n");
}
Set<String> scheduledIds = new HashSet<>();
if (plan!= null) for (Block b: plan) scheduledIds.add(b.taskId);
StringBuilder un = new StringBuilder();
for (TaskItem t: todo) {
if (scheduledIds.contains(t.id)) continue;
if (un.length() > 0) un.append('、');
un.append(t.title).append("（计划 ").append(t.durationMinutes).append(" 分钟")
.append("must".equals(t.priority)? "，必做": "").append("）［id:").append(t.id).append('］');
}
sb.append("未排入日程的任务：").append(un.length() == 0? "无": un.toString()).append('\n');
if (!actual.isEmpty()) {
sb.append("今天实际计时记录：");
for (int i = 0; i < actual.size(); i++) {
Block b = actual.get(i);
if (i > 0) sb.append("；");
sb.append(hhmm(b.startMin)).append('–').append(hhmm(b.endMin)).append(' ').append(b.title)
.append(b.running? "（正在计时）": "");
}
sb.append('\n');
}
sb.append("我 22:30 睡觉；从现在到 22:30 还可自由安排 ")
.append(freeMinutes(plan)).append(" 分钟。\n");
sb.append("请按这个规则帮我更新今天剩下的安排，并写回桥文件 tasks.json 的顶层 schedule 字段：\n")
.append("1）必做任务（priority=must 或截止在今天）必须排入；次要任务按重要程度穿插进空档；\n")
.append("2）每件任务按计划时长 ×1.5 占时段（留 50% 缓冲）；\n")
.append("3）已经过去的时段和已完成任务的时段不要动，未完成的从现在往后排，不要排到 22:30 之后；排不下请直接告诉我。\n")
.append("schedule 格式：{\"date\":\"").append(today)
.append("\",\"blocks\":[{\"taskId\":\"任务id\",\"start\":\"HH:mm\",\"end\":\"HH:mm\"}],\"updatedAtMs\":毫秒时间戳,\"updatedBy\":\"Muse\"}。\n")
.append("写好后我这边日历页会自动刷新显示新版。");
return sb.toString();
}

private String joinTitles(List<TaskItem> ts) {
StringBuilder sb = new StringBuilder();
for (int i = 0; i < ts.size(); i++) {
if (i > 0) sb.append("、");
sb.append(ts.get(i).title);
}
return sb.toString();
}

// ---------------- 小工具 ----------------

private static int parseMin(String hhmm) {
try {
String[] p = hhmm.trim().split(":");
int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
if (h < 0 || h > 24 || m < 0 || m > 59) return -1;
return h * 60 + m;
} catch (Exception e) {
return -1;
}
}

private static String hhmm(int min) {
return String.format(Locale.US, "%02d:%02d", min / 60, min % 60);
}

private static int minOfDay(long ms) {
java.util.Calendar c = java.util.Calendar.getInstance();
c.setTimeInMillis(ms);
return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
}

private static boolean sameDay(long ms) {
java.util.Calendar c = java.util.Calendar.getInstance();
c.setTimeInMillis(ms);
java.util.Calendar n = java.util.Calendar.getInstance();
return c.get(java.util.Calendar.YEAR) == n.get(java.util.Calendar.YEAR)
&& c.get(java.util.Calendar.DAY_OF_YEAR) == n.get(java.util.Calendar.DAY_OF_YEAR);
}

private int dp(int v) {
return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
}
}
