package com.ti.tasktimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
* 日历页（今日时间表）：纵向一天时间轴，三栏并排（v1.12.0 定案）——最左窄栏是 Google 日历
* （桥 gcal，Muse 写入，只读、只在本栏内渲染，不许压到计划栏上）、中间最宽的主栏是计划
* （云端 user_state 的 schedule，Muse 排的）、右窄栏是实际（本地计时段记录 SessionLog，只读）。
* 本页在前台时每 10 秒同步一次桥并重画——Muse 在桥端改了 schedule，这里很快就能看到新版。
*
* v1.6.2 交互：计划块可长按拖拽改时间（用户定案：App 对 schedule 的写权限只走 sanction 入口，
* 经 SyncManager.updateScheduleBlock 按读-改-写-校验纪律回写）；计划块与未排任务带 ▶，
* 点了立即开始/暂停该任务（与主页同一条计时路径）；计划块长按拾起时有 lift 放大效果。
*
* v1.15.0 分屏：顶栏「任务列表」开关——开启后左半屏出任务列表（今日未排的未完成任务，
* 可整行拖进右侧日历，落点即计划块开始时间，块长＝任务计划时长 ×1.5，经
* SyncManager.addScheduleBlock 同一 sanction 纪律落盘）；点右侧任一计划块，左半屏
* 定位并展开该任务（手风琴内容与主页展开区一致：笔记/元信息/操作 chips/子任务，
* 数据与操作入口与主页同一套——TaskStore/TimerService/SubtaskPanel/SbSync.pushDoneAsync）。
*
* v1.18.0 改版（用户真机纠正后定案）：面板回到「日程」Tab 的内容区里（不再是盖住
* 底部三 Tab 的 root 层覆盖物，宿主 MainActivity 负责层级）；v1.15.0 的面板内附属
* 任务列表与「任务列表」开关删除——分屏的左半边就是主页任务列表本体（同一个列表
* 组件、同一份数据），拖拽源也在那边：主页行拖动时经本类的 listDragOver/listDrop
* 外部拖入 API 在本面板的计划栏画预览、落点经 addScheduleBlock 落盘。点计划块的
* 展开去向改由宿主决定：宿主实现 BlockFocusHost 时（如主页）在宿主的列表里定位
* 展开；独立托管（CalendarActivity）时回落到本面板下方清单区内展开（同一套
* expansionView 与操作入口）。
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
private LinearLayout laneHead;
private LinearLayout listBox;
private ScrollView calScroll;
// 点计划块的展开去向（v1.18.0）：宿主实现此接口时在宿主列表里展开；未设置（独立
// CalendarActivity 托管）时回落到本面板下方清单区内展开（focusTaskId 记展开对象）。
public interface BlockFocusHost {
void onCalendarBlockFocus(String taskId);
}
private BlockFocusHost focusHost;
private String focusTaskId = null;
private int lastAxisStart = DAY_START_MIN, lastAxisEnd = DAY_END_MIN;
private int lastScale = 1, lastPlanX = 0, lastPlanW = 0;
private TextView dropPreview; // 拖任务入日历时的落点预览块（canvas 的临时子视图）
private final Handler handler = new Handler(Looper.getMainLooper());
private boolean foreground = false;
private boolean syncing = false;
private boolean dragging = false; // 有块正被拖动时，周期 render 不要打断它
private TaskItem pendingGuardTask; // 等截图授权结果的待开始任务（与主页同语义）

/** 任务数据一变（主页/悬浮窗/同步改的都算）就重画日历——与主页同一条 TaskStore 通知。 */
private final TaskStore.ChangeListener storeListener = () -> {
if (foreground && !dragging) { render(); }
};

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
setBackgroundColor(Ui.bg(host));
LinearLayout root = new LinearLayout(ctx);
root.setOrientation(LinearLayout.VERTICAL);
root.setPadding(dp(16), dp(18), dp(16), dp(10));
addView(root, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
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

// 主体：日历栏（v1.18.0 起分屏的左半边是主页任务列表本体，在宿主那边；
// 本面板只剩日历一栏。v1.15.0 的「任务列表」开关与面板内附属列表已删除。）
LinearLayout bodyRow = new LinearLayout(ctx);
bodyRow.setOrientation(LinearLayout.HORIZONTAL);
root.addView(bodyRow, new LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

LinearLayout calBox = new LinearLayout(ctx);
calBox.setOrientation(LinearLayout.VERTICAL);
calBox.setPadding(0, 0, 0, dp(28));
// 三栏标题（日历／计划／实际）：宽度与时间轴三栏逐像素对齐，在 render() 里按当次几何重建
laneHead = new LinearLayout(ctx);
laneHead.setOrientation(LinearLayout.HORIZONTAL);
laneHead.setPadding(dp(46), dp(6), 0, dp(2));
calBox.addView(laneHead);

canvas = new FrameLayout(ctx);
calBox.addView(canvas);

listBox = new LinearLayout(ctx);
listBox.setOrientation(LinearLayout.VERTICAL);
listBox.setPadding(0, dp(14), 0, 0);
calBox.addView(listBox);

calScroll = new ScrollView(ctx);
calScroll.addView(calBox);
bodyRow.addView(calScroll, new LinearLayout.LayoutParams(0,
LinearLayout.LayoutParams.MATCH_PARENT, 100f));

render();
}

/** 设置点块展开的去向宿主（主页在嵌入时设置；独立托管不设）。 */
public void setBlockFocusHost(BlockFocusHost h) {
focusHost = h;
}

/**
 * 点计划块 → 展开该任务：宿主（主页）接管时在宿主的任务列表里定位展开；
 * 独立托管时回落到本面板下方清单区内展开（render() 里 focusTaskId 一节）。
 */
private void focusExpand(String taskId) {
if (focusHost != null) {
focusHost.onCalendarBlockFocus(taskId);
return;
}
focusTaskId = taskId;
render();
if (calScroll != null) {
calScroll.post(() -> calScroll.smoothScrollTo(0, Math.max(0, listBox.getTop() - dp(6))));
}
}

@Override protected void onSizeChanged(int w, int h, int ow, int oh) {
super.onSizeChanged(w, h, ow, oh);
if (w != ow && w > 0 && foreground && !dragging) {
handler.post(this::render);
}
}

/** 面板打开 / 宿主 onResume 时调用：开始周期刷新并立刻重画一次。 */
public void panelStart() {
foreground = true;
TaskStore.removeChangeListener(storeListener);
TaskStore.addChangeListener(storeListener);
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
TaskStore.removeChangeListener(storeListener);
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
if (foreground) { render(); }
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

// 宽度按日历栏的实际宽度算（嵌入主页分屏时不再占满整宽）；未布局时先估算，布局后会重画
int pw = calScroll != null ? calScroll.getWidth() : 0;
if (pw <= 0) {
int panelW = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
pw = panelW - dp(32);
}
int totalW = pw;
int gutter = dp(46), gap = dp(6);
// 三栏版式（v1.12.0 用户定案，宽度写死）：日历窄栏 ≈17%｜计划主栏 ≈58%｜实际窄栏 ≈25%。
// Google 日历事件只许在自己的窄栏里渲染，绝不压到计划栏上。
int colsW = Math.max(dp(150), totalW - gutter - gap * 2);
int gcalW = Math.max(dp(38), (int) (colsW * 0.17f));
int actW = Math.max(dp(46), (int) (colsW * 0.25f));
int planW = Math.max(dp(56), colsW - gcalW - actW);
int gcalX = gutter, planX = gcalX + gcalW + gap, actX = planX + planW + gap;
// 栏标题与三栏逐像素对齐重建
laneHead.removeAllViews();
TextView lhG = laneLabel(Strings.calLaneGcal());
lhG.setLayoutParams(new LinearLayout.LayoutParams(gcalW, LinearLayout.LayoutParams.WRAP_CONTENT));
laneHead.addView(lhG);
TextView lhP = laneLabel(Strings.calLanePlan());
LinearLayout.LayoutParams lhPp = new LinearLayout.LayoutParams(planW, LinearLayout.LayoutParams.WRAP_CONTENT);
lhPp.leftMargin = gap;
lhP.setLayoutParams(lhPp);
laneHead.addView(lhP);
TextView lhA = laneLabel(Strings.calLaneActual());
LinearLayout.LayoutParams lhAp = new LinearLayout.LayoutParams(actW, LinearLayout.LayoutParams.WRAP_CONTENT);
lhAp.leftMargin = gap;
lhA.setLayoutParams(lhAp);
laneHead.addView(lhA);
int scale = dp(1); // 1 分钟 = 1dp
int heightPx = (axisEnd - axisStart) * scale;
// 缓存本次几何：拖任务入日历时按它换算落点分钟（v1.15.0）
lastAxisStart = axisStart;
lastAxisEnd = axisEnd;
lastScale = scale;
lastPlanX = planX;
lastPlanW = planW;
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
// Google 日历事件（最左窄栏，独立灰紫样式，只读：不可拖、无 ▶、点一下看全文；只在本栏内渲染）
for (GcalEvent e : gcalTimed) addGcalBlock(e, gcalX, gcalW, axisStart, scale);
// 计划块（中间主栏，浅色描边，可拖、带 ▶——行为只在本栏内，口径不变）
if (plan!= null) for (Block b: plan) addPlanBlock(b, planX, planW, axisStart, scale);
// 实际块（右窄栏，实心，只读展示 SessionLog 计时段）
for (Block b: actual) addBlock(b, actX, actW, axisStart, scale, false);
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

// 下方清单：未排入日程的任务 / 无日程时的今日任务。
// 独立托管且刚点了计划块时，先在清单区顶部内展开该任务（v1.18.0 回落路径；
// 嵌入主页时展开发生在主页任务列表里，focusTaskId 在此恒为 null）。
listBox.removeAllViews();
if (focusHost == null && focusTaskId != null) {
TaskItem ft = store.byId(focusTaskId);
if (ft != null && !ft.deleted) {
TextView back = new TextView(ctx);
back.setText(Strings.calFocusBack());
back.setTextSize(13);
back.setTextColor(Ui.link(ctx));
back.setPadding(0, dp(8), 0, dp(6));
back.setOnClickListener(v -> { focusTaskId = null; render(); });
listBox.addView(back);
listBox.addView(expansionView(ft));
} else {
focusTaskId = null; // 任务已不在了，收起展开节
}
}
Set<String> scheduledIds = new HashSet<>();
if (plan!= null) for (Block b: plan) scheduledIds.add(b.taskId);
List<TaskItem> unscheduled = new ArrayList<>();
for (TaskItem t: todo) if (!scheduledIds.contains(t.id)) unscheduled.add(t);
if (plan == null) {
hintView.setText(Strings.calNoPlanHint());
hintView.setVisibility(View.VISIBLE);
} else {
hintView.setVisibility(View.GONE);
}
addListTitle(plan == null? Strings.calTodayTasksTitle(): Strings.calUnscheduledTitle());
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
} else {
// 已完成块没有拖拽：轻点整块 → 展开该任务（去向由 focusExpand 按宿主决定，v1.18.0）
box.setOnClickListener(v -> focusExpand(b.taskId));
}
int hPx = Math.max(dp(20), (b.endMin - b.startMin) * scale);
FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, hPx);
lp.leftMargin = x;
lp.topMargin = (b.startMin - axisStart) * scale;
canvas.addView(box, lp);
}

/** Google 日历事件块（v1.12.0 起只在自己的窄栏内渲染）：灰紫描边，只读——窄栏里只放开始时间＋标题（可截断），点一下 Toast 看全名与完整时间，不能拖、没有 ▶。 */
private void addGcalBlock(final GcalEvent e, int x, int w, int axisStart, int scale) {
TextView v = new TextView(ctx);
v.setText(hhmm(e.startMin) + "\n" + e.title);
v.setTextSize(9);
v.setPadding(dp(3), dp(2), dp(2), dp(2));
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
if (!lifted) {
// 没拾起就松手＝轻点整块 → 展开该任务（focusExpand，v1.18.0）
if (ev.getActionMasked() == MotionEvent.ACTION_UP) focusExpand(b.taskId);
return true;
}
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

// ---------------- 外部拖入（v1.18.0）：主页任务列表 → 本面板计划栏 ----------------
// v1.15.0 的面板内附属列表已删：拖拽源是主页任务列表本体（宿主 MainActivity 的行），
// 宿主在拖动中逐帧调 listDragOver 在本面板计划栏画落点预览，松手调 listDrop 落盘
// 或 listDragCancel 取消。几何换算、预演块与落盘（addScheduleBlock）都留在本类，
// 与 v1.15.0 左栏拖拽同一套口径：5 分钟吸附、块长＝时长 ×1.5、00:00–24:00 夹取。

/**
 * 拖动中逐帧调用：手指在日历画布上时画/更新计划栏落点预览块，并返回吸附＋夹取后的
 * 开始分钟；不在画布上返回 -1（预览收起）。拖动全程置 dragging，挡住周期 render
 * 与数据变更重画，免得几何（lastAxisStart/lastPlanX 等）与预览块被重建冲掉。
 */
public int listDragOver(TaskItem t, float rawX, float rawY) {
dragging = true;
if (!pointInCanvas(rawX, rawY)) { hideDropPreview(); return -1; }
int durMin = Math.max(5, Math.round(t.durationMinutes * 1.5f));
int startMin = Math.max(DAY_START_MIN, Math.min(dropMinuteFor(rawY), 24 * 60 - durMin));
updateDropPreview(t, rawX, rawY);
return startMin;
}

/** 拖动取消（拖回列表/日历外松手，或手势被取消）：收预览，不写任何东西。 */
public void listDragCancel() {
dragging = false;
clearDropPreview();
}

/** 松手落定：经 sanction 入口 addScheduleBlock 落盘；完成后先重画本面板，再回调宿主。 */
public void listDrop(final TaskItem t, int startMin, final Runnable onDone) {
dragging = false;
clearDropPreview();
commitDrop(t, startMin, onDone);
}

private void clearDropPreview() {
if (dropPreview != null) { canvas.removeView(dropPreview); dropPreview = null; }
}

/** 落点是否落在日历画布上（屏幕坐标）。 */
private boolean pointInCanvas(float rawX, float rawY) {
if (canvas == null || canvas.getWidth() <= 0) return false;
int[] cl = new int[2];
canvas.getLocationOnScreen(cl);
return rawX >= cl[0] && rawX <= cl[0] + canvas.getWidth()
&& rawY >= cl[1] && rawY <= cl[1] + canvas.getHeight();
}

/** 手指 Y → 落点开始分钟（按上次渲染的轴几何，5 分钟吸附，未夹取）。 */
private int dropMinuteFor(float rawY) {
if (canvas == null) return -1;
int[] cl = new int[2];
canvas.getLocationOnScreen(cl);
int raw = lastAxisStart + Math.round((rawY - cl[1]) / (float) Math.max(1, lastScale));
int snapped = Math.round(raw / 5f) * 5;
return snapped;
}

/** 拖动中在计划栏画/更新落点预览块；手指不在日历上则收起预览。 */
private void updateDropPreview(TaskItem t, float rawX, float rawY) {
int durMin = Math.max(5, Math.round(t.durationMinutes * 1.5f));
if (!pointInCanvas(rawX, rawY)) { hideDropPreview(); return; }
int startMin = dropMinuteFor(rawY);
startMin = Math.max(DAY_START_MIN, Math.min(startMin, 24 * 60 - durMin));
if (dropPreview == null) {
dropPreview = new TextView(ctx);
dropPreview.setTextSize(10);
dropPreview.setPadding(dp(4), dp(2), dp(2), dp(2));
GradientDrawable bg = new GradientDrawable();
bg.setCornerRadius(dp(4));
bg.setColor(Color.argb(90, 255, 193, 7));
bg.setStroke(dp(2), Color.rgb(255, 143, 0));
dropPreview.setBackground(bg);
dropPreview.setTextColor(Ui.text(ctx));
canvas.addView(dropPreview);
}
dropPreview.setText(hhmm(startMin) + "–" + hhmm(startMin + durMin) + "\n" + t.title);
FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
lastPlanW > 0 ? lastPlanW : dp(56), Math.max(dp(18), durMin * lastScale));
lp.leftMargin = lastPlanX;
lp.topMargin = (startMin - lastAxisStart) * lastScale;
dropPreview.setLayoutParams(lp);
dropPreview.setVisibility(View.VISIBLE);
dropPreview.bringToFront();
}

private void hideDropPreview() {
if (dropPreview != null) dropPreview.setVisibility(View.GONE);
}

/** 松手落定：块长＝计划时长 ×1.5（用户一贯的占时口径），经 sanction 入口 addScheduleBlock 落盘。 */
private void commitDrop(final TaskItem t, int startMinRaw, final Runnable onDone) {
int durMin = Math.max(5, Math.round(t.durationMinutes * 1.5f));
final int startMin = Math.max(DAY_START_MIN, Math.min(startMinRaw, 24 * 60 - durMin));
final String newStart = hhmm(startMin), newEnd = hhmm(startMin + durMin);
new Thread(() -> {
String err = SyncManager.addScheduleBlock(ctx, t.id, newStart, newEnd);
handler.post(() -> {
if (err == null) {
toast("「" + t.title + "」已排到 " + newStart + "–" + newEnd);
} else {
toast(err);
}
render();
if (onDone != null) onDone.run();
});
}).start();
}


/**
 * 展开区：内容与操作逐项对齐主页 expansionView——笔记、元信息（开始时间/已用分段/
 * 专注/闹钟）、操作 chips（开始暂停/编辑/闹钟/+15/完成）、子任务面板。数据走同一 TaskStore，
 * 开始/暂停走本页 togglePlayTask（与主页 onPlay 同一条路径），完成走 SbSync.pushDoneAsync，
 * 子任务走同一 SubtaskPanel 组件——不另起一套状态。保存一律 freshCopyOf（本页 render 会周期性
 * store.load() 换对象，捕获的渲染对象随时可能过期）。v1.18.0 起本展开区只在独立托管
 * （CalendarActivity）的点块回落路径里出现；嵌入主页时展开的是主页列表里的行。
 */
private View expansionView(final TaskItem t) {
final boolean done = t.isDoneToday();
final boolean isRunningThis = TimerState.isRunning(ctx) && t.id.equals(TimerState.taskId(ctx));
final boolean isPausedThis = !TimerState.isRunning(ctx) && t.id.equals(TimerState.pausedTaskId(ctx));
LinearLayout exp = new LinearLayout(ctx);
exp.setOrientation(LinearLayout.VERTICAL);
exp.setBackgroundColor(Ui.muted(ctx));
exp.setPadding(dp(10), dp(10), dp(10), dp(12));
LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
elp.setMargins(0, 0, 0, dp(10));
exp.setLayoutParams(elp);
boolean hasNote = t.note != null && !t.note.trim().isEmpty();
TextView note = new TextView(ctx);
note.setText(hasNote ? t.note : Strings.noNote());
note.setTextSize(13);
note.setTextColor(hasNote ? Ui.text(ctx) : Ui.sub(ctx));
note.setOnClickListener(v -> dialogNote(t));
exp.addView(note);

int usedMin = (int) (SessionLog.totalMsForTask(ctx, t.id) / 60000);
int segs = SessionLog.countForTask(ctx, t.id)
+ (t.id.equals(SessionLog.openTaskId(ctx)) ? 1 : 0);
String startPart = t.nextStartMs() > 0
? Strings.metaStart(startCellText(t)) : Strings.metaNotStarted();
String alarmPart = "";
if (t.alarmMs() > 0) alarmPart += " · ⏰ " + t.alarmAt.substring(5).replace('T', ' ');
if (t.alarmOnTimerEnd) {
alarmPart += " · " + (Strings.en() ? "Rings at end" : "结束响铃");
}
TextView meta = new TextView(ctx);
meta.setText(startPart + " · " + Strings.metaUsed(usedMin, segs)
+ " · " + Strings.metaFocus(focusCellText(t).toString()) + alarmPart);
meta.setTextSize(12);
meta.setTextColor(Ui.sub(ctx));
meta.setPadding(0, dp(6), 0, 0);
exp.addView(meta);

LinearLayout chips = new LinearLayout(ctx);
chips.setOrientation(LinearLayout.HORIZONTAL);
chips.setPadding(0, dp(10), 0, 0);
String primary = isRunningThis ? Strings.chipPause()
: isPausedThis ? Strings.chipResume() : Strings.chipStart();
chips.addView(chipCal(primary, true, v -> togglePlayTask(t.id)));
chips.addView(chipCal(Strings.chipEdit(), false, v -> dialogEditMenu(t)));
chips.addView(chipCal(Strings.chipAlarm(), false, v -> dialogAlarm(t)));
if (isRunningThis) {
chips.addView(chipCal(Strings.chipPlus15(), false, v -> {
TimerService.extendTimer(ctx, 15 * 60);
toast(Strings.en() ? "Extended by 15 minutes" : "已加时 15 分钟");
handler.postDelayed(this::afterTaskEdited, 650);
}));
}
chips.addView(chipCal(done ? Strings.chipUndone() : Strings.chipDone(), false, v -> {
TaskItem c = freshCopyOf(t);
if (done) c.markUndone(); else c.markDone();
store.save();
SbSync.pushDoneAsync(ctx, c.id);
if (!done) focusTaskId = null;
afterTaskEdited();
if (!done) handler.postDelayed(() -> IdleGuard.fireNow(ctx), 1500);
}));
exp.addView(chips);

// 子任务区：与主页、大悬浮窗共用同一面板组件
View line2 = new View(ctx);
line2.setBackgroundColor(Ui.line(ctx));
LinearLayout.LayoutParams l2lp = new LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT, 1);
l2lp.setMargins(0, dp(12), 0, dp(8));
line2.setLayoutParams(l2lp);
exp.addView(line2);
SubtaskPanel panel = new SubtaskPanel(ctx, false, false);
panel.bind(t, calSubHost);
exp.addView(panel);
return exp;
}

/** 展开区 chips：与主页 chip() 同样式。 */
private TextView chipCal(String label, boolean filled, View.OnClickListener cl) {
TextView c = new TextView(ctx);
c.setText(label);
c.setTextSize(13);
c.setGravity(Gravity.CENTER);
c.setPadding(dp(14), dp(7), dp(14), dp(7));
GradientDrawable g = new GradientDrawable();
g.setCornerRadius(dp(17));
if (filled) {
g.setColor(Ui.accent(ctx));
c.setTextColor(Ui.onAccent(ctx));
} else {
g.setColor(Ui.surface(ctx));
g.setStroke(1, Ui.line(ctx));
c.setTextColor(Ui.text(ctx));
}
c.setBackground(g);
LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
lp.setMargins(0, 0, dp(8), 0);
c.setLayoutParams(lp);
c.setOnClickListener(cl);
return c;
}

/** 保存前按 id 重取最新副本（与主页 freshCopyOf 同纪律，防旧副本整表覆盖）。 */
private TaskItem freshCopyOf(TaskItem t) {
store.load();
TaskItem c = store.byId(t.id);
return c != null ? c : t;
}

private void afterTaskEdited() {
render();
}

private String mmss(long ms) {
long s = Math.max(0, ms / 1000);
return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
}

private String hmOf(long ms) {
Calendar c = Calendar.getInstance();
c.setTimeInMillis(ms);
return String.format(Locale.US, "%02d:%02d",
c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
}

private String startCellText(TaskItem t) {
long ms = t.nextStartMs();
if (ms <= 0) return "—";
Calendar c = Calendar.getInstance();
c.setTimeInMillis(ms);
String day = String.format(Locale.US, "%04d-%02d-%02d",
c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
return day.equals(TaskItem.today()) ? hmOf(ms) : day.substring(5) + " " + hmOf(ms);
}

private String focusCellText(TaskItem t) {
if ("block".equals(t.focusMode)) {
return t.blockedApps.isEmpty() ? "禁（未选）"
: "禁:" + AppLister.labelOf(ctx, t.blockedApps.get(0)) + (t.blockedApps.size() > 1 ? "+" + (t.blockedApps.size() - 1) : "");
}
if ("allow".equals(t.focusMode)) {
return t.allowedApps.isEmpty() ? "仅（未选）"
: "仅:" + AppLister.labelOf(ctx, t.allowedApps.get(0)) + (t.allowedApps.size() > 1 ? "+" + (t.allowedApps.size() - 1) : "");
}
return (t.focusRule == null || t.focusRule.isEmpty()) ? "＋专注" : "规则";
}

// ---------------- 展开区用的编辑对话框（字段与保存路径逐项对齐主页） ----------------

private void dialogNote(final TaskItem t) {
EditText input = new EditText(ctx);
input.setText(t.note == null ? "" : t.note);
input.setSelection(input.getText().length());
input.setMinLines(3);
input.setGravity(Gravity.TOP);
new AlertDialog.Builder(host)
.setTitle("笔记 · " + t.title)
.setView(wrapWithCopyButton(t, input, "note"))
.setPositiveButton("保存", (dg, w) -> {
TaskItem cur = freshCopyOf(t);
cur.note = input.getText().toString().trim();
cur.touch();
store.save();
afterTaskEdited();
})
.setNegativeButton("取消", null)
.show();
}

private void dialogEditMenu(final TaskItem t) {
boolean en = Strings.en();
String[] items = {
en ? "Name & priority" : "名称与优先级",
en ? "Contexts" : "情景",
en ? "Deadline & start time" : "截止与开始时间",
en ? "Length" : "时长",
en ? "Focus rule" : "专注规则",
en ? "Note" : "笔记",
Strings.alarmTitle(),
en ? "Reset timer" : "重置计时",
};
new AlertDialog.Builder(host)
.setTitle(Strings.editMenuTitle())
.setItems(items, (d, which) -> {
switch (which) {
case 0: dialogTitle(t); break;
case 1: dialogScenarios(t); break;
case 2: dialogDeadline(t); break;
case 3: dialogDuration(t); break;
case 4: dialogFocus(t); break;
case 5: dialogNote(t); break;
case 6: dialogAlarm(t); break;
case 7: resetTaskFromCal(t); break;
default: break;
}
})
.show();
}

private void dialogTitle(final TaskItem t) {
final EditText input = new EditText(ctx);
input.setText(t.title);
input.setSelectAllOnFocus(true);
new AlertDialog.Builder(host)
.setTitle("任务名称")
.setView(input)
.setPositiveButton("保存", (d, w) -> {
String name = input.getText().toString().trim();
if (!name.isEmpty()) {
TaskItem cur = freshCopyOf(t);
cur.title = name; cur.touch(); store.save(); afterTaskEdited();
}
})
.setNeutralButton("设为必要/取消必要", (d, w) -> {
TaskItem cur = freshCopyOf(t);
cur.priority = "must".equals(cur.priority) ? null : "must";
cur.touch(); store.save(); afterTaskEdited();
})
.setNegativeButton("取消", null)
.show();
}

private void dialogScenarios(final TaskItem t) {
LinkedHashSet<String> knownSet = new LinkedHashSet<>();
knownSet.add("有网");
knownSet.add("无网");
knownSet.add("跟 AI 聊天");
knownSet.addAll(store.allScenarios());
knownSet.addAll(t.scenarios);
final List<String> known = new ArrayList<>(knownSet);
final Set<String> sel = new LinkedHashSet<>(t.scenarios);
boolean[] checked = new boolean[known.size()];
for (int i = 0; i < known.size(); i++) checked[i] = sel.contains(known.get(i));
new AlertDialog.Builder(host)
.setTitle("情景（可多选）")
.setMultiChoiceItems(known.toArray(new String[0]), checked,
(d, which, isChecked) -> {
if (isChecked) sel.add(known.get(which));
else sel.remove(known.get(which));
})
.setPositiveButton("保存", (d, w) -> {
TaskItem cur = freshCopyOf(t);
cur.scenarios = new ArrayList<>(sel);
cur.touch(); store.save(); afterTaskEdited();
})
.setNeutralButton("＋新增情景", (d, w) -> {
final EditText input = new EditText(ctx);
input.setHint("如：在书院、早上、跟 AI 聊天");
new AlertDialog.Builder(host)
.setTitle("新增情景")
.setView(input)
.setPositiveButton("添加", (d2, w2) -> {
String s = input.getText().toString().trim();
TaskItem cur = freshCopyOf(t);
if (!s.isEmpty() && !cur.scenarios.contains(s)) {
cur.scenarios.add(s);
cur.touch(); store.save(); afterTaskEdited();
}
})
.setNegativeButton("取消", null)
.show();
})
.setNegativeButton("取消", null)
.show();
}

private void dialogDeadline(final TaskItem t) {
LinearLayout box = new LinearLayout(ctx);
box.setOrientation(LinearLayout.VERTICAL);
box.setPadding(dp(16), dp(8), dp(16), 0);
final CheckBox repeatBox = new CheckBox(ctx);
repeatBox.setText("每日重复任务");
repeatBox.setChecked("daily".equals(t.repeat));
box.addView(repeatBox);
final DatePicker picker = new DatePicker(ctx);
Calendar cal = Calendar.getInstance();
if (t.deadline != null) {
try {
String[] p = t.deadline.split("-");
cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
} catch (Exception ignored) {}
}
picker.init(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), null);
box.addView(picker);
repeatBox.setOnCheckedChangeListener((b, isChecked) -> picker.setEnabled(!isChecked));
picker.setEnabled(!repeatBox.isChecked());
final String[] timeHolder = {t.deadlineTime};
final Button timeBtn = new Button(ctx);
final Runnable refreshTimeBtn = () -> timeBtn.setText(
timeHolder[0] == null ? "完成时间（可选）：未设置" : "完成时间：" + timeHolder[0] + " 前");
refreshTimeBtn.run();
timeBtn.setOnClickListener(v -> {
int hh = 18, mm = 0;
if (timeHolder[0] != null) {
try {
String[] p = timeHolder[0].split(":");
hh = Integer.parseInt(p[0]);
mm = Integer.parseInt(p[1]);
} catch (Exception ignored) {}
}
android.app.TimePickerDialog tp = new android.app.TimePickerDialog(host,
(view, h2, m2) -> {
timeHolder[0] = String.format(Locale.US, "%02d:%02d", h2, m2);
refreshTimeBtn.run();
}, hh, mm, true);
tp.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "清除时间",
(d2, w2) -> { timeHolder[0] = null; refreshTimeBtn.run(); });
tp.show();
});
box.addView(timeBtn);
final String[] startHolder = {t.nextStartAt};
final Button startBtn = new Button(ctx);
final Runnable refreshStartBtn = () -> startBtn.setText(
startHolder[0] == null ? "下次开始时间（可选）：未设置"
: "下次开始时间：" + startHolder[0].replace('T', ' '));
refreshStartBtn.run();
startBtn.setOnClickListener(v -> {
Calendar sc = Calendar.getInstance();
if (startHolder[0] != null && startHolder[0].length() >= 10) {
try {
String[] dp2 = startHolder[0].substring(0, 10).split("-");
sc.set(Integer.parseInt(dp2[0]), Integer.parseInt(dp2[1]) - 1, Integer.parseInt(dp2[2]));
} catch (Exception ignored) {}
}
new android.app.DatePickerDialog(host, (dv, y, mo, da) -> {
int sh = 9, sm = 0;
if (startHolder[0] != null && startHolder[0].length() >= 16) {
try {
String[] tp2 = startHolder[0].substring(11, 16).split(":");
sh = Integer.parseInt(tp2[0]);
sm = Integer.parseInt(tp2[1]);
} catch (Exception ignored) {}
}
android.app.TimePickerDialog stp = new android.app.TimePickerDialog(host,
(tv, h3, m3) -> {
startHolder[0] = String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d", y, mo + 1, da, h3, m3);
refreshStartBtn.run();
}, sh, sm, true);
stp.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "清除时间",
(d3, w3) -> { startHolder[0] = null; refreshStartBtn.run(); });
stp.show();
}, sc.get(Calendar.YEAR), sc.get(Calendar.MONTH), sc.get(Calendar.DAY_OF_MONTH)).show();
});
box.addView(startBtn);
new AlertDialog.Builder(host)
.setTitle("截止日期")
.setView(box)
.setPositiveButton("保存", (d, w) -> {
TaskItem cur = freshCopyOf(t);
if (repeatBox.isChecked()) {
cur.repeat = "daily";
cur.deadline = null;
cur.deadlineTime = null;
} else {
cur.repeat = "none";
cur.deadline = String.format(Locale.US, "%04d-%02d-%02d",
picker.getYear(), picker.getMonth() + 1, picker.getDayOfMonth());
cur.deadlineTime = timeHolder[0];
}
cur.nextStartAt = startHolder[0];
cur.touch(); store.save(); afterTaskEdited();
StartReminder.reschedule(ctx, store);
})
.setNeutralButton("清除日期", (d, w) -> {
TaskItem cur = freshCopyOf(t);
cur.repeat = "none";
cur.deadline = null;
cur.deadlineTime = null;
cur.nextStartAt = null;
cur.touch(); store.save(); afterTaskEdited();
StartReminder.reschedule(ctx, store);
})
.setNegativeButton("取消", null)
.show();
}

private void dialogDuration(final TaskItem t) {
LinearLayout box = new LinearLayout(ctx);
box.setOrientation(LinearLayout.VERTICAL);
box.setPadding(dp(16), dp(8), dp(16), 0);
TextView planLabel = new TextView(ctx);
planLabel.setText("计划时长（分钟）");
box.addView(planLabel);
final EditText planInput = new EditText(ctx);
planInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
planInput.setText(String.valueOf(t.durationMinutes));
planInput.setSelectAllOnFocus(true);
box.addView(planInput);
if (t.hasSubtasks()) {
planInput.setEnabled(false);
TextView lockNote = new TextView(ctx);
lockNote.setText(Strings.subDurationLocked());
lockNote.setTextSize(12);
box.addView(lockNote);
}
TextView actualLabel = new TextView(ctx);
actualLabel.setText("实际用时（分钟，可空）");
box.addView(actualLabel);
final EditText actualInput = new EditText(ctx);
actualInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
if (t.actualMinutes > 0) actualInput.setText(String.valueOf(t.actualMinutes));
actualInput.setHint("做完后填实际花了多少分钟");
box.addView(actualInput);
new AlertDialog.Builder(host)
.setTitle("时长")
.setView(box)
.setPositiveButton("保存", (d, w) -> {
TaskItem cur = freshCopyOf(t);
try {
int m = Integer.parseInt(planInput.getText().toString().trim());
if (m > 0) cur.durationMinutes = m;
} catch (NumberFormatException ignored) {}
String a = actualInput.getText().toString().trim();
if (a.isEmpty()) {
cur.actualMinutes = 0;
} else {
try {
int am = Integer.parseInt(a);
if (am >= 0) cur.actualMinutes = am;
} catch (NumberFormatException ignored) {}
}
cur.touch(); store.save(); afterTaskEdited();
})
.setNegativeButton("取消", null)
.show();
}

private void dialogFocus(final TaskItem t) {
final List<AppLister.AppInfo> apps = AppLister.launchableApps(ctx);
final String[] labels = new String[apps.size()];
for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;
final String[] mode = {t.focusMode == null ? "off" : t.focusMode};
final List<String> blocked = new ArrayList<>(t.blockedApps);
final List<String> allowed = new ArrayList<>(t.allowedApps);
final String[] primary = {t.primaryApp};

LinearLayout box = new LinearLayout(ctx);
box.setOrientation(LinearLayout.VERTICAL);
box.setPadding(dp(16), dp(8), dp(16), 0);

final EditText ruleInput = new EditText(ctx);
ruleInput.setHint("描述什么情况下你可能会分神，如：读经典时想刷微信、查资料容易逛到购物网站（Muse 会据此翻译成要拦/只许用的软件名单）");
ruleInput.setText(t.focusRule == null ? "" : t.focusRule);
ruleInput.setMinLines(2);
ruleInput.setTextSize(15);
box.addView(wrapWithCopyButton(t, ruleInput, "rule"));

final TextView summary = new TextView(ctx);
summary.setTextSize(13);
summary.setTextColor(Color.DKGRAY);
summary.setPadding(0, dp(8), 0, dp(4));

final Button[] modeBtns = new Button[3];
final String[] modeVals = {"off", "block", "allow"};
final String[] modeNames = {"不监测", "黑名单", "白名单"};
final Runnable[] refresh = new Runnable[1];
refresh[0] = () -> {
for (int i = 0; i < 3; i++) {
modeBtns[i].setBackgroundColor(modeVals[i].equals(mode[0])
? Color.rgb(46, 125, 50) : Color.rgb(189, 189, 189));
modeBtns[i].setTextColor(Color.WHITE);
}
summary.setText("拦截 " + blocked.size() + " 个 · 允许 " + allowed.size() + " 个 · 主软件："
+ (primary[0] == null ? "无（跳回当前任务页）" : AppLister.labelOf(ctx, primary[0])));
};
LinearLayout modeRow = new LinearLayout(ctx);
modeRow.setOrientation(LinearLayout.HORIZONTAL);
for (int i = 0; i < 3; i++) {
final int idx = i;
modeBtns[i] = new Button(ctx);
modeBtns[i].setText(modeNames[i]);
modeBtns[i].setTextSize(14);
modeBtns[i].setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
modeBtns[i].setOnClickListener(v -> { mode[0] = modeVals[idx]; refresh[0].run(); });
modeRow.addView(modeBtns[i]);
}
box.addView(modeRow);
box.addView(summary);

Button pickBlocked = new Button(ctx);
pickBlocked.setText("选择要拦截的软件（黑名单）");
pickBlocked.setOnClickListener(v -> pickAppsMulti("拦截这些软件", apps, labels, blocked, refresh[0]));
box.addView(pickBlocked);
Button pickAllowed = new Button(ctx);
pickAllowed.setText("选择只允许用的软件（白名单）");
pickAllowed.setOnClickListener(v -> pickAppsMulti("只允许这些软件", apps, labels, allowed, refresh[0]));
box.addView(pickAllowed);
Button pickPrimary = new Button(ctx);
pickPrimary.setText("选择主软件（被拦下时跳回它）");
pickPrimary.setOnClickListener(v -> {
String[] items = new String[labels.length + 1];
items[0] = "无（跳回知行的当前任务页）";
System.arraycopy(labels, 0, items, 1, labels.length);
new AlertDialog.Builder(host)
.setTitle("主软件")
.setItems(items, (d, which) -> {
primary[0] = which == 0 ? null : apps.get(which - 1).pkg;
refresh[0].run();
})
.show();
});
box.addView(pickPrimary);
refresh[0].run();

new AlertDialog.Builder(host)
.setTitle("专注规则")
.setView(box)
.setPositiveButton("保存", (d, w) -> {
TaskItem cur = freshCopyOf(t);
cur.focusRule = ruleInput.getText().toString().trim();
cur.focusMode = mode[0];
cur.blockedApps = new ArrayList<>(blocked);
cur.allowedApps = new ArrayList<>(allowed);
cur.primaryApp = primary[0];
cur.touch();
store.save();
afterTaskEdited();
})
.setNegativeButton("取消", null)
.show();
}

private void pickAppsMulti(String title, List<AppLister.AppInfo> apps, String[] labels,
List<String> target, Runnable onDone) {
final Set<String> sel = new LinkedHashSet<>(target);
boolean[] checked = new boolean[apps.size()];
for (int i = 0; i < apps.size(); i++) checked[i] = sel.contains(apps.get(i).pkg);
new AlertDialog.Builder(host)
.setTitle(title)
.setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
if (isChecked) sel.add(apps.get(which).pkg);
else sel.remove(apps.get(which).pkg);
})
.setPositiveButton("确定", (d, w) -> {
target.clear();
target.addAll(sel);
onDone.run();
})
.setNegativeButton("取消", null)
.show();
}

/** 闹钟设置：与主页 dialogAlarm 同字段（alarmAt 闹钟时间＋结束响铃开关）。 */
private void dialogAlarm(final TaskItem t) {
boolean en = Strings.en();
LinearLayout box = new LinearLayout(ctx);
box.setOrientation(LinearLayout.VERTICAL);
box.setPadding(dp(16), dp(8), dp(16), 0);
final String[] alarmHolder = {t.alarmAt};
final Button alarmBtn = new Button(ctx);
final Runnable refreshAlarmBtn = () -> alarmBtn.setText(
alarmHolder[0] == null
? Strings.alarmTimeLabel() + "：" + Strings.alarmNotSet()
: Strings.alarmTimeLabel() + "：" + alarmHolder[0].replace('T', ' '));
refreshAlarmBtn.run();
alarmBtn.setOnClickListener(v -> {
final String init = alarmHolder[0] != null ? alarmHolder[0] : t.nextStartAt;
Calendar ac = Calendar.getInstance();
if (init != null && init.length() >= 10) {
try {
String[] dp2 = init.substring(0, 10).split("-");
ac.set(Integer.parseInt(dp2[0]), Integer.parseInt(dp2[1]) - 1, Integer.parseInt(dp2[2]));
} catch (Exception ignored) {}
}
new android.app.DatePickerDialog(host, (dv, y, mo, da) -> {
int ah = 7, am2 = 0;
if (init != null && init.length() >= 16) {
try {
String[] tp2 = init.substring(11, 16).split(":");
ah = Integer.parseInt(tp2[0]);
am2 = Integer.parseInt(tp2[1]);
} catch (Exception ignored) {}
}
android.app.TimePickerDialog atp = new android.app.TimePickerDialog(host,
(tv, h3, m3) -> {
alarmHolder[0] = String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d", y, mo + 1, da, h3, m3);
refreshAlarmBtn.run();
}, ah, am2, true);
atp.setButton(android.content.DialogInterface.BUTTON_NEUTRAL,
en ? "Clear" : "清除时间",
(d3, w3) -> { alarmHolder[0] = null; refreshAlarmBtn.run(); });
atp.show();
}, ac.get(Calendar.YEAR), ac.get(Calendar.MONTH), ac.get(Calendar.DAY_OF_MONTH)).show();
});
box.addView(alarmBtn);
final CheckBox endBox = new CheckBox(ctx);
endBox.setText(Strings.alarmRingOnEnd());
endBox.setChecked(t.alarmOnTimerEnd);
box.addView(endBox);
new AlertDialog.Builder(host)
.setTitle(Strings.alarmTitle())
.setView(box)
.setPositiveButton(en ? "Save" : "保存", (d, w) -> {
TaskItem cur = freshCopyOf(t);
cur.alarmAt = alarmHolder[0];
cur.alarmOnTimerEnd = endBox.isChecked();
cur.touch(); store.save(); afterTaskEdited();
AlarmScheduler.rescheduleAll(ctx, store);
})
.setNegativeButton(en ? "Cancel" : "取消", null)
.show();
}

/** 重置计时：与主页 resetTask 同处理（别的任务在跑→直接切换＋重置，v1.16.1 定案，不经暂停卡）。 */
private void resetTaskFromCal(final TaskItem t) {
startTaskTimer(t);
toast("已重置，从 0 重新开始：" + t.title);
}

/** 子任务编辑（新增 index=-1 / 改名改分钟 / 删除带二次确认）：与主页 dialogSubtask 同字段同落盘。 */
private void dialogSubtask(final TaskItem t, final int index) {
final Subtask cur = index >= 0 && index < t.subtasks.size() ? t.subtasks.get(index) : null;
LinearLayout box = new LinearLayout(ctx);
box.setOrientation(LinearLayout.VERTICAL);
box.setPadding(dp(16), dp(8), dp(16), 0);
final EditText nameIn = new EditText(ctx);
nameIn.setHint(Strings.subNameHint());
if (cur != null) nameIn.setText(cur.title);
box.addView(nameIn);
final EditText minIn = new EditText(ctx);
minIn.setHint(Strings.subMinutesHint());
minIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
minIn.setText(cur != null ? String.valueOf(cur.minutes) : "5");
box.addView(minIn);
AlertDialog.Builder b = new AlertDialog.Builder(host)
.setTitle(cur == null ? Strings.subEditNew() : Strings.subEditTitle())
.setView(box)
.setPositiveButton(Strings.subSave(), (d, w) -> {
TaskItem task = freshCopyOf(t);
String name = nameIn.getText().toString().trim();
if (name.isEmpty()) name = Strings.subtasksTitle() + (task.subtasks.size() + 1);
int mins = 5;
try {
mins = Integer.parseInt(minIn.getText().toString().trim());
} catch (NumberFormatException ignored) {}
if (mins <= 0) mins = 5;
if (cur == null) {
task.subtasks.add(Subtask.create(name, mins));
} else {
Subtask s = null;
for (Subtask x : task.subtasks) if (x.id.equals(cur.id)) s = x;
if (s == null) return;
s.title = name;
s.minutes = mins;
}
task.syncDurationFromSubtasks();
task.touch();
store.save();
afterTaskEdited();
})
.setNegativeButton(Strings.btnCancel(), null);
if (cur != null) {
b.setNeutralButton(Strings.subDelete(), (d, w) -> {
new AlertDialog.Builder(host)
.setMessage(Strings.subDeleteConfirm())
.setPositiveButton(Strings.subDelete(), (d2, w2) -> {
TaskItem task = freshCopyOf(t);
for (int i = 0; i < task.subtasks.size(); i++) {
if (task.subtasks.get(i).id.equals(cur.id)) {
task.subtasks.remove(i);
break;
}
}
task.syncDurationFromSubtasks();
task.touch();
store.save();
afterTaskEdited();
})
.setNegativeButton(Strings.btnCancel(), null)
.show();
});
}
b.show();
}

/** 子任务面板宿主（日历展开区）：与主页 subtaskHost 同口径，落盘后重画日历。 */
private final SubtaskPanel.Host calSubHost = new SubtaskPanel.Host() {
@Override public void onSubtasksEdited(TaskItem t) {
store.save();
afterTaskEdited();
}
@Override public void requestEditSubtask(TaskItem t, int index) {
dialogSubtask(t, index);
}
@Override public int runningIndex(TaskItem t) {
if (TimerState.isRunning(ctx)
&& t.id.equals(TimerState.taskId(ctx))
&& TimerState.subActive(ctx)) {
return TimerState.subIndex(ctx);
}
return -1;
}
@Override public String runningRemainText(TaskItem t) {
return mmss(Math.max(0, TimerState.subEndAt(ctx) - System.currentTimeMillis()));
}
@Override public String headerRightText(TaskItem t) {
int ri = runningIndex(t);
if (ri >= 0) {
return Strings.subProgress(ri + 1, TimerState.subCount(ctx), TimerState.subTitle(ctx));
}
if (t.hasSubtasks()) return Strings.subTotalNote(t.subtasksTotalMinutes());
return null;
}
};

/** 把任务全字段文案复制到剪贴板（与主页 copyTaskInfo 同一份 buildInfo 文案）。 */
private void copyTaskInfo(final TaskItem t, String noteOverride, String ruleOverride) {
String oldNote = t.note, oldRule = t.focusRule;
if (noteOverride != null) t.note = noteOverride;
if (ruleOverride != null) t.focusRule = ruleOverride;
String info = AiLaunchActivity.buildInfo(ctx, t);
t.note = oldNote;
t.focusRule = oldRule;
ClipboardManager cm = (ClipboardManager) ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("知行任务", info));
toast("已复制任务信息");
}

/** 给整段内容编辑框套一层：右上方常驻「复制」按钮（与主页 v1.6.1 同一便利件）。 */
private FrameLayout wrapWithCopyButton(final TaskItem t, final EditText input, final String kind) {
FrameLayout wrap = new FrameLayout(ctx);
wrap.addView(input, new FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
input.setPadding(input.getPaddingLeft(), input.getPaddingTop(), dp(56), input.getPaddingBottom());
TextView btn = new TextView(ctx);
btn.setText("复制");
btn.setTextSize(12);
btn.setTextColor(Color.WHITE);
btn.setBackgroundColor(Color.argb(210, 97, 97, 97));
btn.setPadding(dp(10), dp(4), dp(10), dp(4));
FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
Gravity.TOP | Gravity.END);
blp.setMargins(0, dp(4), dp(6), 0);
btn.setLayoutParams(blp);
btn.setOnClickListener(v -> {
if ("note".equals(kind)) copyTaskInfo(t, input.getText().toString(), null);
else if ("rule".equals(kind)) copyTaskInfo(t, null, input.getText().toString());
else copyTaskInfo(t, null, null);
});
wrap.addView(btn);
return wrap;
}

// ---------------- 开始 / 暂停（与主页 ▶ 同一条路径） ----------------

/**
 * 与主页任务行 ▶ 完全相同的切换语义（同一个 TimerService 入口）：
 * 计时中的就是它→暂停（过暂停卡）；暂停中的就是它→继续；别的任务在计时→直接切换
 * （v1.16.1 定案：原任务立即停表落盘、不进暂停态、无中间页）；都没有→开始。
 * 悬浮窗的当前任务由 TimerState 推导（FloatService 每秒重渲染），
 * 这里开始/暂停后它会自动跟着切换，无需额外通知。
 */
private void togglePlayTask(String taskId) {
store.load();
final TaskItem t = store.byId(taskId);
if (t == null) { toast("任务已不在列表里"); render(); return; }
if (TimerState.isRunning(ctx)) {
if (t.id.equals(TimerState.taskId(ctx))) {
// 暂停必须带时长＋目的——先过暂停卡，确认才真正暂停（与主页同一 PauseFlow）
PauseFlow.ask(ctx, t.title, ch -> {
TimerService.pauseWithReminder(ctx, ch.delayMs, ch.reason, ch.emoji, ch.silent);
toast("已暂停：「" + t.title + "」，到点会提醒你回来");
handler.postDelayed(this::render, 350);
});
return;
}
// 直接切换（v1.16.1 定案，同主页 onPlay）：原任务立即停表落盘、不进暂停态，
// 无对话框/暂停卡；暂停槽里的其他任务原样不动。
startTaskTimer(t);
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
// 实际栏是窄栏（v1.12.0）：栏太窄时时间只放开始时刻，避免时间串被拦腰换行；标题可截断
String time = w >= dp(64)? hhmm(b.startMin) + "–" + hhmm(b.endMin): hhmm(b.startMin);
String label = time + "\n" + b.title
+ (b.running? " ▶": "");
v.setText(label);
v.setTextSize(10);
v.setPadding(dp(3), dp(2), dp(2), dp(2));
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
sb.append("已有日程（Google 日历，仅供我查看参考，排日程时不用管、不用避让）：\n");
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
.append("4）上面「已有日程（Google 日历）」只是给我看的参考，排日程时不用管它们、不用避让，任务该怎么排就怎么排，可以和它们重叠；我在日历页手动拖动改过的块也当作固定块，不要再挪回去。\n")
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
