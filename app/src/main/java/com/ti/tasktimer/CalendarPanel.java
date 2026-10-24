package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
* 计划来自桥文件顶层 schedule（Muse 排的）；实际来自本地计时段记录 SessionLog。
* 桥上若有 gcal（Google 日历事件，Muse 写入），在计划泳道以独立样式只读渲染。
* 本页在前台时每 10 秒同步一次桥并重画——Muse 在桥端改了 schedule，这里很快就能看到新版。
*
* v1.6.2 交互：计划块可长按拖拽改时间（用户定案：App 对 schedule 的写权限仅此一处，
* 经 SyncManager.updateScheduleBlock 按读-改-写-校验纪律回写）；计划块与未排任务带 ▶，
* 点了立即开始/暂停该任务（与主页同一条计时路径）；计划块长按拾起时有 lift 放大效果。
*/
public class CalendarPanel extends FrameLayout {
private final Activity host;
private final android.content.Context ctx;
private static final int DAY_START_MIN = 5 * 60; // 轴默认 05:00
private static final int DAY_END_MIN = 23 * 60; // 轴默认 23:00
private static final int SLEEP_MIN = 22 * 60 + 30; // 22:30 睡觉，自由时间算到这里
private static final int REQ_CAPTURE = 44; // 与主页同款：开始计时前的截图授权请求

private TaskStore store;
private TextView overviewView;
private TextView doneView;
private TextView hintView;
private FrameLayout canvas;
private LinearLayout listBox;
private final Handler handler = new Handler(Looper.getMainLooper());
private boolean foreground = false;
private boolean syncing = false;
private boolean dragging = false; // 有块正被拖动时，周期 render 不要打断它
private TaskItem pendingGuardTask; // 等截图授权结果的待开始任务（与主页同语义）

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

/** Google 日历事件（桥 gcal，只读渲染）：有起止的是定时事件，allDay 的只在清单里列一行。 */
private static class GcalEvent {
String title = "";
int startMin = -1, endMin = -1;
boolean allDay;
}

/** 读桥上缓存的 gcal；date 不是今天或结构不全返回空列表。 */
private List<GcalEvent> gcalEvents() {
List<GcalEvent> out = new ArrayList<>();
JSONObject g = SyncManager.latestGcal(ctx);
if (g == null) return out;
if (!today().equals(g.optString("date", ""))) return out;
JSONArray arr = g.optJSONArray("events");
if (arr == null) return out;
for (int i = 0; i < arr.length(); i++) {
JSONObject o = arr.optJSONObject(i);
if (o == null) continue;
GcalEvent e = new GcalEvent();
e.title = o.optString("title", "（无标题日程）");
e.allDay = o.optBoolean("allDay", false);
if (!e.allDay) {
e.startMin = parseMin(o.optString("start", ""));
e.endMin = parseMin(o.optString("end", ""));
if (e.startMin < 0 || e.endMin <= e.startMin) { e.allDay = true; e.startMin = e.endMin = -1; }
}
out.add(e);
}
return out;
}

public CalendarPanel(Activity host) {
super(host);
this.host = host;
ctx = host;
Strings.init(host);
store = new TaskStore(ctx);
ScrollView scroll = new ScrollView(ctx);
LinearLayout root = new LinearLayout(ctx);
root.setOrientation(LinearLayout.VERTICAL);
root.setPadding(dp(16), dp(18), dp(16), dp(28));
root.setBackgroundColor(Ui.bg(host));
scroll.addView(root);
addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT));

TextView title = new TextView(ctx);
title.setText(Strings.calTitle(TaskItem.today()));
title.setTextSize(21);
title.setTextColor(Ui.text(ctx));
root.addView(title);

overviewView = new TextView(ctx);
overviewView.setTextSize(14);
overviewView.setTextColor(Ui.text(ctx));
overviewView.setPadding(0, dp(6), 0, 0);
root.addView(overviewView);

doneView = new TextView(ctx);
doneView.setTextSize(12);
doneView.setTextColor(Ui.sub(ctx));
doneView.setPadding(0, dp(2), 0, 0);
root.addView(doneView);

Button askAi = new Button(ctx);
askAi.setText(Strings.calAskAi());
askAi.setOnClickListener(v -> {
Intent i = new Intent(ctx, AiLaunchActivity.class);
i.putExtra(AiLaunchActivity.EXTRA_TEXT, buildAskAiText());
getContext().startActivity(i);
});
LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
alp.setMargins(0, dp(10), 0, dp(4));
root.addView(askAi, alp);

hintView = new TextView(ctx);
hintView.setTextSize(13);
hintView.setTextColor(Ui.danger(ctx));
hintView.setPadding(0, dp(4), 0, dp(4));
root.addView(hintView);

LinearLayout laneHead = new LinearLayout(ctx);
laneHead.setOrientation(LinearLayout.HORIZONTAL);
laneHead.setPadding(dp(46), dp(6), 0, dp(2));
TextView lh1 = laneLabel(Strings.calLanePlan());
lh1.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
laneHead.addView(lh1);
TextView lh2 = laneLabel(Strings.calLaneActual());
lh2.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
laneHead.addView(lh2);
root.addView(laneHead);

canvas = new FrameLayout(ctx);
root.addView(canvas);

listBox = new LinearLayout(ctx);
listBox.setOrientation(LinearLayout.VERTICAL);
listBox.setPadding(0, dp(14), 0, 0);
root.addView(listBox);

render();
}

/** 面板打开 / 宿主 onResume 时调用：开始周期刷新并立刻重画一次。 */
public void panelStart() {
foreground = true;
handler.removeCallbacks(refresher);
handler.post(refresher);
store.load();
render();
}

/** 对外重画（宿主改变面板宽度后调用）。 */
public void refreshNow() {
store.load();
render();
}

/** 面板关闭 / 宿主 onPause 时调用：停掉周期刷新。 */
public void panelStop() {
foreground = false;
handler.removeCallbacks(refresher);
}

/** 后台线程同步一次桥（防重复触发），回来后重画。同步本身不阻塞界面。 */
private void syncAndRender() {
if (syncing) { render(); return;}
syncing = true;
new Thread(() -> {
try {
String r = SyncManager.syncNowSmart(ctx, store);
SyncManager.setLastResult(ctx, r);
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
JSONObject sch = SyncManager.latestSchedule(ctx);
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
b.running = TimerState.isRunning(ctx) && b.taskId.equals(TimerState.taskId(ctx));
out.add(b);
}
out.sort((a, b) -> a.startMin!= b.startMin? a.startMin - b.startMin: a.endMin - b.endMin);
return out;
}

/** 今天的实际计时段（含正在进行的那一段），来自本地 SessionLog。 */
private List<Block> actualBlocks() {
List<Block> out = new ArrayList<>();
for (SessionLog.Seg s: SessionLog.segmentsForDay(ctx, today())) {
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
if (TimerState.isRunning(ctx)) {
long startMs = SessionLog.openStart(ctx);
if (startMs <= 0) startMs = TimerState.startedAt(ctx);
long now = System.currentTimeMillis();
if (startMs > 0 && startMs < now && sameDay(startMs)) {
Block b = new Block();
b.taskId = TimerState.taskId(ctx);
TaskItem t = store.byId(b.taskId);
b.title = t!= null? t.title: TimerState.taskTitle(ctx);
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
if (dragging) return; // 拖拽进行中不重画，免得把手里那块销毁
store.load();
List<Block> plan = planBlocks();
List<Block> actual = actualBlocks();
List<GcalEvent> gcal = gcalEvents();
List<GcalEvent> gcalTimed = new ArrayList<>();
for (GcalEvent e : gcal) if (!e.allDay) gcalTimed.add(e);
List<TaskItem> done = new ArrayList<>();
List<TaskItem> todo = new ArrayList<>();
for (TaskItem t: store.tasks) {
if (!isTodayTask(t)) continue;
if (t.isDoneToday()) done.add(t); else todo.add(t);
}
int free = freeMinutes(plan);
overviewView.setText(Strings.calOverview(done.size(), todo.size(), free));
if (done.isEmpty()) {
doneView.setText("");
} else {
StringBuilder sb = new StringBuilder(Strings.calDonePrefix());
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
for (GcalEvent e : gcalTimed) {
axisStart = Math.min(axisStart, (e.startMin / 30) * 30);
axisEnd = Math.max(axisEnd, ((e.endMin + 29) / 30) * 30);
}
int nowMin = minOfDay(System.currentTimeMillis());
axisStart = Math.min(axisStart, (nowMin / 60) * 60);
axisEnd = Math.max(axisEnd, Math.min(24 * 60, (nowMin / 60 + 1) * 60));

int pw = getWidth();
int totalW = (pw > 0 ? pw : getResources().getDisplayMetrics().widthPixels) - dp(32);
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
View line = new View(ctx);
line.setBackgroundColor(Ui.line(ctx));
FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(totalW - gutter, 1);
llp.leftMargin = gutter;
llp.topMargin = y;
canvas.addView(line, llp);
TextView lab = new TextView(ctx);
lab.setText(String.format(Locale.US, "%02d:00", h / 60));
lab.setTextSize(10);
lab.setTextColor(Ui.sub(ctx));
FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(gutter - dp(4), dp(14));
tlp.leftMargin = 0;
tlp.topMargin = Math.max(0, y - dp(7));
canvas.addView(lab, tlp);
}
// 计划块（左泳道，浅色描边，可拖、带 ▶）
if (plan!= null) for (Block b: plan) addPlanBlock(b, planX, laneW, axisStart, scale);
// Google 日历事件（左泳道，独立灰紫样式，只读：不可拖、无 ▶、点一下看全文）
for (GcalEvent e : gcalTimed) addGcalBlock(e, planX, laneW, axisStart, scale);
// 实际块（右泳道，实心）
for (Block b: actual) addBlock(b, actX, laneW, axisStart, scale, false);
// 现在线
if (nowMin >= axisStart && nowMin <= axisEnd) {
int y = (nowMin - axisStart) * scale;
View nl = new View(ctx);
nl.setBackgroundColor(Color.rgb(229, 57, 53));
FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(totalW, dp(2));
nlp.topMargin = y;
canvas.addView(nl, nlp);
TextView nt = new TextView(ctx);
nt.setText(Strings.calNowTag() + " " + hhmm(nowMin));
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
hintView.setText(Strings.calNoPlanHint());
hintView.setVisibility(View.VISIBLE);
addListTitle(Strings.calTodayTasksTitle());
} else {
hintView.setVisibility(View.GONE);
addListTitle(Strings.calUnscheduledTitle());
}
if (unscheduled.isEmpty()) {
addListLine(plan == null? Strings.calEmptyAllDone(): Strings.calEmptyAllScheduled(), Ui.sub(ctx));
} else {
for (TaskItem t: unscheduled) addTaskRow(t);
}
boolean hasAllDay = false;
for (GcalEvent e : gcal) if (e.allDay) { hasAllDay = true; break; }
if (hasAllDay) {
addListTitle(Strings.calGcalTitle());
for (GcalEvent e : gcal) {
if (e.allDay) addListLine("📅 全天 · " + e.title, Ui.dark(ctx) ? Color.rgb(186, 170, 240) : Color.rgb(94, 53, 177));
else addListLine("📅 " + hhmm(e.startMin) + "–" + hhmm(e.endMin) + " · " + e.title, Ui.dark(ctx) ? Color.rgb(186, 170, 240) : Color.rgb(94, 53, 177));
}
}
if (!actual.isEmpty()) {
addListTitle(Strings.calActualTitle());
for (Block b: actual) {
addListLine("• " + hhmm(b.startMin) + "–" + hhmm(b.endMin) + " " + b.title
+ (b.running? Strings.calRunningTag(): ""), Ui.green(ctx));
}
}
}

/** 计划块（v1.6.2）：容器＋文字＋右上角 ▶；未完成的块可长按拖拽改时间。 */
private void addPlanBlock(final Block b, int x, int w, final int axisStart, final int scale) {
FrameLayout box = new FrameLayout(ctx);
GradientDrawable bg = new GradientDrawable();
bg.setCornerRadius(dp(4));
boolean dk = Ui.dark(ctx);
if (b.done) {
bg.setColor(dk ? Color.rgb(38, 43, 47) : Color.rgb(238, 238, 238));
bg.setStroke(dp(1), dk ? Color.rgb(90, 100, 106) : Color.rgb(158, 158, 158));
} else if (b.running) {
bg.setColor(dk ? Color.rgb(38, 66, 99) : Color.rgb(187, 222, 251));
bg.setStroke(dp(2), dk ? Color.rgb(100, 165, 230) : Color.rgb(25, 118, 210));
} else {
bg.setColor(dk ? Color.rgb(28, 47, 66) : Color.rgb(227, 242, 253));
bg.setStroke(dp(1), dk ? Color.rgb(100, 165, 230) : Color.rgb(25, 118, 210));
}
box.setBackground(bg);
TextView label = new TextView(ctx);
label.setText(hhmm(b.startMin) + "–" + hhmm(b.endMin) + "\n" + b.title);
label.setTextSize(11);
label.setTextColor(b.done? Ui.sub(ctx): (Ui.dark(ctx) ? Color.rgb(147, 197, 253) : Color.rgb(13, 71, 161)));
label.setPadding(dp(5), dp(2), dp(26), dp(2));
label.setMaxLines(3);
label.setEllipsize(android.text.TextUtils.TruncateAt.END);
box.addView(label, new FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
if (!b.done) {
TextView play = new TextView(ctx);
boolean isRunningThis = TimerState.isRunning(ctx) && b.taskId.equals(TimerState.taskId(ctx));
play.setText(isRunningThis? "⏸": "▶");
play.setTextSize(13);
play.setTextColor(Ui.dark(ctx) ? Color.rgb(147, 197, 253) : Color.rgb(13, 71, 161));
play.setGravity(Gravity.CENTER);
play.setPadding(dp(4), dp(1), dp(4), dp(1));
FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(24), dp(22), Gravity.TOP | Gravity.END);
box.addView(play, plp);
play.setOnClickListener(v -> togglePlayTask(b.taskId));
attachDrag(box, label, b, axisStart, scale);
}
int hPx = Math.max(dp(20), (b.endMin - b.startMin) * scale);
FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, hPx);
lp.leftMargin = x;
lp.topMargin = (b.startMin - axisStart) * scale;
canvas.addView(box, lp);
}

/** Google 日历事件块：灰紫描边、标题带 📅，只读——点一下 Toast 看全名与时间，不能拖、没有 ▶。 */
private void addGcalBlock(final GcalEvent e, int x, int w, int axisStart, int scale) {
TextView v = new TextView(ctx);
v.setText(hhmm(e.startMin) + "–" + hhmm(e.endMin) + "\n📅 " + e.title);
v.setTextSize(11);
v.setPadding(dp(5), dp(2), dp(3), dp(2));
v.setMaxLines(3);
v.setEllipsize(android.text.TextUtils.TruncateAt.END);
GradientDrawable bg = new GradientDrawable();
bg.setCornerRadius(dp(4));
boolean dk2 = Ui.dark(ctx);
bg.setColor(dk2 ? Color.rgb(46, 40, 66) : Color.rgb(237, 231, 246));
bg.setStroke(dp(1), dk2 ? Color.rgb(150, 132, 205) : Color.rgb(126, 87, 194));
v.setTextColor(dk2 ? Color.rgb(206, 190, 250) : Color.rgb(69, 39, 160));
v.setBackground(bg);
v.setOnClickListener(view -> Toast.makeText(ctx,
"📅 " + e.title + "\n" + hhmm(e.startMin) + "–" + hhmm(e.endMin) + "（Google 日历）",
Toast.LENGTH_LONG).show());
int hPx = Math.max(dp(20), (e.endMin - e.startMin) * scale);
FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, hPx);
lp.leftMargin = x;
lp.topMargin = (e.startMin - axisStart) * scale;
canvas.addView(v, lp);
}

/**
 * 长按拖拽改计划块时间（v1.6.2）：
 * 长按 400ms 拾起——拾起瞬间 lift 动画（放大到 1.04＋加深阴影＋微微透出，学 iOS/Google 日历）；
 * 拖动中按 5 分钟吸附实时预览新起止，拖动态全程保持放大；松手后短促回落动画恢复原大小，
 * 再经 SyncManager.updateScheduleBlock 写回桥（App 对 schedule 的唯一写入口）。
 * 已完成的块不挂拖拽（addPlanBlock 只给未完成块调用本方法）。
 */
private void attachDrag(final FrameLayout box, final TextView label, final Block b,
                        final int axisStart, final int scale) {
box.setOnTouchListener(new View.OnTouchListener() {
float downRawY;
int downStartMin;
int curStartMin;
boolean lifted = false;
boolean moved = false;
final Runnable lift = () -> {
lifted = true;
moved = false;
dragging = true;
curStartMin = b.startMin;
box.requestDisallowInterceptTouchEvent(true);
box.bringToFront();
box.animate().scaleX(1.04f).scaleY(1.04f).alpha(0.96f).setDuration(120).start();
box.setElevation(dp(10));
try { box.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); } catch (Exception ignored) {}
};
@Override public boolean onTouch(View v, MotionEvent ev) {
switch (ev.getActionMasked()) {
case MotionEvent.ACTION_DOWN:
downRawY = ev.getRawY();
downStartMin = b.startMin;
handler.postDelayed(lift, 400);
return true;
case MotionEvent.ACTION_MOVE:
if (!lifted) return true;
float dyPx = ev.getRawY() - downRawY;
int raw = downStartMin + Math.round(dyPx / scale);
int snapped = Math.round(raw / 5f) * 5;
int dur = b.endMin - b.startMin;
snapped = Math.max(DAY_START_MIN, Math.min(snapped, DAY_END_MIN - dur));
curStartMin = snapped;
FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
lp.topMargin = (snapped - axisStart) * scale;
box.setLayoutParams(lp);
if (snapped!= downStartMin) moved = true;
label.setText(hhmm(snapped) + "–" + hhmm(snapped + dur) + "\n" + b.title);
return true;
case MotionEvent.ACTION_UP:
case MotionEvent.ACTION_CANCEL:
handler.removeCallbacks(lift);
if (!lifted) return true;
lifted = false;
// 回落动画：缩放/透明度/阴影都收回，再落定写回
box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start();
box.setElevation(0);
final int dur2 = b.endMin - b.startMin;
final int ns = curStartMin;
final String taskId = b.taskId;
final String oldStart = hhmm(b.startMin);
handler.postDelayed(() -> {
dragging = false;
if (!moved || ns == downStartMin) { render(); return; }
String newStart = hhmm(ns), newEnd = hhmm(ns + dur2);
new Thread(() -> {
String err = SyncManager.updateScheduleBlock(
ctx, taskId, oldStart, newStart, newEnd);
handler.post(() -> {
if (err == null) {
toast("「" + b.title + "」已改到 " + newStart + "–" + newEnd);
} else {
toast(err);
}
render();
});
}).start();
}, 160);
return true;
}
return false;
}
});
}

/** 未排入日程的任务行：标题＋时长，右侧一个 ▶/⏸，点法与计划块完全一致。 */
private void addTaskRow(final TaskItem t) {
LinearLayout row = new LinearLayout(ctx);
row.setOrientation(LinearLayout.HORIZONTAL);
row.setGravity(Gravity.CENTER_VERTICAL);
row.setPadding(0, dp(2), 0, dp(2));
TextView lab = new TextView(ctx);
lab.setText("• " + t.title + Strings.calPlannedFmt(t.durationMinutes, "must".equals(t.priority)));
lab.setTextSize(13);
lab.setTextColor(Ui.text(ctx));
lab.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
row.addView(lab);
TextView play = new TextView(ctx);
boolean isRunningThis = TimerState.isRunning(ctx) && t.id.equals(TimerState.taskId(ctx));
play.setText(isRunningThis? "⏸": "▶");
play.setTextSize(16);
play.setTextColor(Ui.dark(ctx) ? Color.rgb(147, 197, 253) : Color.rgb(13, 71, 161));
play.setGravity(Gravity.CENTER);
play.setPadding(dp(10), dp(2), dp(6), dp(2));
play.setOnClickListener(v -> togglePlayTask(t.id));
row.addView(play);
listBox.addView(row);
}

// ---------------- 开始 / 暂停（与主页 ▶ 同一条路径） ----------------

/**
 * 与主页任务行 ▶ 完全相同的切换语义（同一个 TimerService 入口）：
 * 计时中的就是它→暂停；暂停中的就是它→继续；别的任务在计时→先确认「暂停并切换」；
 * 都没有→开始。悬浮窗的当前任务由 TimerState 推导（FloatService 每秒重渲染），
 * 这里开始/暂停后它会自动跟着切换，无需额外通知。
 */
private void togglePlayTask(String taskId) {
store.load();
final TaskItem t = store.byId(taskId);
if (t == null) { toast("任务已不在列表里"); render(); return; }
if (TimerState.isRunning(ctx)) {
if (t.id.equals(TimerState.taskId(ctx))) {
TimerService.pauseTimer(ctx);
toast("已暂停：「" + t.title + "」，点 ▶ 继续");
handler.postDelayed(this::render, 350);
return;
}
String warn = TimerState.isPaused(ctx) && !TimerState.pausedTaskId(ctx).equals(TimerState.taskId(ctx))
? "\n（注意：之前暂停的「" + TimerState.pausedTitle(ctx) + "」的进度会被覆盖）" : "";
new AlertDialog.Builder(ctx)
.setTitle("已有任务在计时")
.setMessage("「" + TimerState.taskTitle(ctx) + "」正在计时。切换会先把它暂停（进度保留），再开始「" + t.title + "」。" + warn)
.setPositiveButton("暂停并切换", (d, w) -> {
TimerService.pauseTimer(ctx);
handler.postDelayed(() -> startTaskTimer(t), 350);
})
.setNegativeButton("取消", null)
.show();
return;
}
if (t.id.equals(TimerState.pausedTaskId(ctx))) {
int total = TimerState.pausedTotalSec(ctx);
if (total <= 0) total = Math.max(1, t.durationMinutes) * 60;
int remain = (int) Math.max(1, TimerState.pausedRemainMs(ctx) / 1000);
TimerService.resumeTimer(ctx, total, remain, t.id, t.title);
toast("继续计时：" + t.title + "（剩余约 " + (remain / 60) + " 分 " + (remain % 60) + " 秒）");
handler.postDelayed(this::render, 350);
return;
}
startTaskTimer(t);
}

/** 与主页 startTaskTimer 同语义：屏幕内容守卫未授权时先走一次截图授权，拿到结果都照常开始。 */
private void startTaskTimer(final TaskItem t) {
if (ScreenGuardPrefs.enabled(ctx) && !ScreenCaptureService.isAuthorized(ctx)) {
android.media.projection.MediaProjectionManager mpm =
(android.media.projection.MediaProjectionManager) getContext().getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE);
if (mpm != null) {
pendingGuardTask = t;
toast("屏幕内容守卫已开：请在系统弹窗里允许这一次截图授权（每个任务一次）");
host.startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
return;
}
}
doStartTaskTimer(t);
}

private void doStartTaskTimer(TaskItem t) {
TimerService.startTimer(ctx, Math.max(1, t.durationMinutes) * 60, t.id, t.title);
toast("开始计时：" + t.title + "（" + t.durationMinutes + " 分钟）");
handler.postDelayed(this::render, 350);
}

public void onCaptureResult(int resultCode, Intent data) {
if (true) {
TaskItem t = pendingGuardTask;
pendingGuardTask = null;
if (resultCode == Activity.RESULT_OK && data != null) {
ProjectionAuth.store(resultCode, data);
} else {
toast("未获得截图授权：本次计时不做屏幕内容采样，其余功能照常");
}
if (t != null) doStartTaskTimer(t);
}
}

private void addBlock(Block b, int x, int w, int axisStart, int scale, boolean planned) {
TextView v = new TextView(ctx);
String label = hhmm(b.startMin) + "–" + hhmm(b.endMin) + "\n" + b.title
+ (b.running? " ▶": "");
v.setText(label);
v.setTextSize(11);
v.setPadding(dp(5), dp(2), dp(3), dp(2));
v.setMaxLines(3);
v.setEllipsize(android.text.TextUtils.TruncateAt.END);
GradientDrawable bg = new GradientDrawable();
bg.setCornerRadius(dp(4));
boolean dk3 = Ui.dark(ctx);
if (b.done) {
bg.setColor(dk3 ? Color.rgb(43, 48, 52) : Color.rgb(224, 224, 224));
v.setTextColor(Ui.sub(ctx));
} else if (b.running) {
bg.setColor(dk3 ? Color.rgb(46, 84, 52) : Color.rgb(129, 199, 132));
bg.setStroke(dp(2), dk3 ? Color.rgb(129, 199, 132) : Color.rgb(27, 94, 32));
v.setTextColor(dk3 ? Color.rgb(200, 235, 203) : Color.rgb(20, 60, 24));
} else {
bg.setColor(dk3 ? Color.rgb(30, 56, 37) : Color.rgb(165, 214, 167));
v.setTextColor(dk3 ? Color.rgb(165, 226, 170) : Color.rgb(27, 94, 32));
}
v.setBackground(bg);
int hPx = Math.max(dp(20), (b.endMin - b.startMin) * scale);
FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, hPx);
lp.leftMargin = x;
lp.topMargin = (b.startMin - axisStart) * scale;
canvas.addView(v, lp);
}

private TextView laneLabel(String s) {
TextView t = new TextView(ctx);
t.setText(s);
t.setTextSize(11);
t.setTextColor(Ui.sub(ctx));
return t;
}

private void addListTitle(String s) {
TextView t = new TextView(ctx);
t.setText(s);
t.setTextSize(14);
t.setTextColor(Ui.text(ctx));
t.setTypeface(t.getTypeface(), Typeface.BOLD);
t.setPadding(0, dp(10), 0, dp(3));
listBox.addView(t);
}

private void addListLine(String s, int color) {
TextView t = new TextView(ctx);
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
List<GcalEvent> gcal = gcalEvents();
if (!gcal.isEmpty()) {
sb.append("已有日程（Google 日历，这些时段我已经有安排了，是忙碌时段）：\n");
for (GcalEvent e : gcal) {
if (e.allDay) sb.append("全天 · ").append(e.title).append('\n');
else sb.append(hhmm(e.startMin)).append('–').append(hhmm(e.endMin)).append(' ').append(e.title).append('\n');
}
}
if (!actual.isEmpty()) {
sb.append("今天实际计时记录：");
for (int i = 0; i < actual.size(); i++) {
Block b = actual.get(i);
if (i > 0) sb.append("；");
sb.append(hhmm(b.startMin)).append('–').append(hhmm(b.endMin)).append(' ').append(b.title)
.append(b.running? Strings.calRunningTag(): "");
}
sb.append('\n');
}
sb.append("我 22:30 睡觉；从现在到 22:30 还可自由安排 ")
.append(freeMinutes(plan)).append(" 分钟。\n");
sb.append("请按这个规则帮我更新今天剩下的安排，并写回桥文件 tasks.json 的顶层 schedule 字段：\n")
.append("1）必做任务（priority=must 或截止在今天）必须排入；次要任务按重要程度穿插进空档；\n")
.append("2）每件任务按计划时长 ×1.5 占时段（留 50% 缓冲）；\n")
.append("3）已经过去的时段和已完成任务的时段不要动，未完成的从现在往后排，不要排到 22:30 之后；排不下请直接告诉我。\n")
.append("4）上面「已有日程（Google 日历）」是真实日历上的忙碌时段，任务不要排上去、也不要和它们重叠，必须避让；我在日历页手动拖动改过的块也当作固定块，不要再挪回去。\n")
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

private void toast(String s) { Toast.makeText(ctx, s, Toast.LENGTH_LONG).show(); }

private int dp(int v) {
return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
}
}
