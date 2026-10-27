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
* （云端 gcal，助手侧写入，只读、只在本栏内渲染，不许压到计划栏上）、中间最宽的主栏是计划
* （云端 user_state 的 schedule）、右窄栏是实际（本地计时段记录 SessionLog，只读）。
* 本页在前台时周期同步并重画——云端改了 schedule，这里很快就能看到新版。
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
*
* v1.20.0 简化（用户对着截图亲手圈定）：顶部「今日日程」标题＋完成汇总＋已完成
* 清单、下方「未排入日程的任务」列表区删除，日历只剩三栏时间轴、现在线与计划块；
* 「AI 重排」保留为日历区右上角的小文字按钮（v1.26.0 改走软件内置聊天通道
* AiReschedule：当场生成提案、点应用才落盘，不再写云端重排请求）；
* 点计划栏空白处经 TaskEditSheet 新建任务（预填所点时间、保存顺手排块）；
* 计划块（含已完成块）拖过现在线自动完成/取消完成（flipDoneByNowLine）。
*/
public class CalendarPanel extends FrameLayout {
private final Activity host;
private final android.content.Context ctx;
private static final int DAY_START_MIN = 5 * 60; // 轴默认 05:00
private static final int DAY_END_MIN = 23 * 60; // 轴默认 23:00

private TaskStore store;
private TextView hintView;
private TextView askAiBtn;
// 重排状态跃迁观察（防重复提示）：记住上次 render 看到的 status 与 requested_at
private boolean aiBusy = false; // AI 重排进行中（v1.26.0 内置通道）
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

// v1.20.0 简化（用户对着截图亲手圈定）：顶部「今日日程」标题＋完成汇总＋
// 「已完成：……」清单整块删除——日历只剩时间轴本身。「问 AI 重排」按钮保留，
// 从头部挪到下方日历区右上角（小文字、低调），管道改走 Supabase 数据库桥，
// Drive 桥时代的整段文案拼装（buildAskAiText）随旧通道一并清除。

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
// 「AI 重排」小文字按钮：低调地挂在日历区右上角一行，不抢眼；
// v1.26.0 起走软件内置 AI 通道当场出提案（AiReschedule），点「应用」才落盘。
LinearLayout askRow = new LinearLayout(ctx);
askRow.setOrientation(LinearLayout.HORIZONTAL);
askRow.setGravity(Gravity.END);
askAiBtn = new TextView(ctx);
askAiBtn.setTextSize(12);
askAiBtn.setPadding(dp(10), dp(3), dp(2), dp(3));
askAiBtn.setOnClickListener(v -> onAskReschedule());
askRow.addView(askAiBtn);
calBox.addView(askRow);
// 三栏标题（日历／计划／实际）：宽度与时间轴三栏逐像素对齐，在 render() 里按当次几何重建
laneHead = new LinearLayout(ctx);
laneHead.setOrientation(LinearLayout.HORIZONTAL);
laneHead.setPadding(dp(46), dp(6), 0, dp(2));
calBox.addView(laneHead);

canvas = new FrameLayout(ctx);
calBox.addView(canvas);
// v1.20.0：点计划栏空白处新建任务（学 Google 日历）。只识别「轻点」：
// 下落点与抬起点位移小、耗时短、手指未滚动——滚动手势由 ScrollView 正常接管。
// 计划块自身带 touch/click 监听会先消费，落不到这里；实际栏/日历栏按 x 栏位过滤掉。
canvas.setOnTouchListener(new View.OnTouchListener() {
float dx, dy;
long dt;
boolean maybeTap;
@Override public boolean onTouch(View v, MotionEvent ev) {
switch (ev.getActionMasked()) {
case MotionEvent.ACTION_DOWN:
dx = ev.getX(); dy = ev.getY(); dt = System.currentTimeMillis();
maybeTap = !dragging;
return false;
case MotionEvent.ACTION_MOVE:
if (Math.abs(ev.getX() - dx) > dp(12) || Math.abs(ev.getY() - dy) > dp(12)) maybeTap = false;
return false;
case MotionEvent.ACTION_CANCEL:
maybeTap = false;
return false;
case MotionEvent.ACTION_UP:
if (maybeTap && System.currentTimeMillis() - dt < 500) onCanvasTap(ev.getX(), ev.getY());
return false;
}
return false;
}
});

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
String r = SbSync.syncNow(ctx, store);
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

// ---------------- AI 重排（v1.26.0：走内置聊天通道，当场提案） ----------------

/** 按钮态：生成中显示「重排中…」并禁用，否则是可点的「AI 重排」。 */
private void refreshAskBtn() {
if (askAiBtn == null) return;
askAiBtn.setText(aiBusy
? (Strings.en() ? "Re-planning…" : "重排中…")
: (Strings.en() ? "AI Re-plan" : "AI 重排"));
askAiBtn.setTextColor(aiBusy ? Ui.sub(ctx) : Ui.link(ctx));
askAiBtn.setEnabled(!aiBusy);
}

/**
 * 点「AI 重排」：走软件内置的 AI 通道（AiReschedule，与「聊聊」同一条）当场
 * 生成一版提案对话框，点「应用」才落盘、「不用」作废。v1.26.0 起不再写
 * 云端重排请求（旧巡查接线已撤除）。
 */
private void onAskReschedule() {
if (aiBusy) return;
if (!(ctx instanceof android.app.Activity)) return;
aiBusy = true;
refreshAskBtn();
AiReschedule.run((android.app.Activity) ctx, store, () -> {
aiBusy = false;
refreshAskBtn();
render();
});
}

// ---------------- 渲染 ----------------

private void render() {
if (canvas == null) return;
if (dragging) return; // 拖拽进行中不重画，免得把手里那块销毁
refreshAskBtn();
store.load();
List<Block> plan = planBlocks();
List<Block> actual = actualBlocks();
List<GcalEvent> gcal = gcalEvents();
List<GcalEvent> gcalTimed = new ArrayList<>();
for (GcalEvent e : gcal) if (!e.allDay) gcalTimed.add(e);

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
nl.setBackgroundColor(Color.rgb(217, 75, 58));
FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(totalW, dp(2));
nlp.topMargin = y;
canvas.addView(nl, nlp);
TextView nt = new TextView(ctx);
nt.setText(Strings.calNowTag() + " " + hhmm(nowMin));
nt.setTextSize(9);
nt.setTextColor(Color.rgb(217, 75, 58));
nt.setTypeface(nt.getTypeface(), Typeface.BOLD);
FrameLayout.LayoutParams ntlp = new FrameLayout.LayoutParams(gutter, dp(12));
ntlp.topMargin = Math.max(0, y - dp(12));
canvas.addView(nt, ntlp);
}

// 下方区域（v1.20.0 简化后）：「未排入日程的任务」列表区整段删除，日历下方
// 只留独立托管时点计划块的展开回落（v1.18.0；嵌入主页时展开发生在主页任务
// 列表里，focusTaskId 在此恒为 null）、全天 Google 事件与今日实际计时两段清单。
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
if (plan == null) {
hintView.setText(Strings.calNoPlanHint());
hintView.setVisibility(View.VISIBLE);
} else {
hintView.setVisibility(View.GONE);
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
}
// v1.20.0：已完成块也可拖（拖回现在线之下要能取消完成）；轻点（未拾起）仍是
// 展开该任务——attachDrag 内部的 tap 路径就是 focusExpand，与旧点击行为一致。
attachDrag(box, label, b, axisStart, scale);
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
 * 拖过现在线＝完成/取消完成（v1.20.0）：计划块拖放落定、时间写回成功后按新位置裁决——
 * 整块位于现在线之上（块结束 ≤ 当前时刻）且任务未完成 → 走与勾选完成完全相同的
 * 写路径标记完成（markDone＋落盘＋pushDoneAsync 定向写云端，每日任务走 doneDates）；
 * 整块位于现在线之下（块开始 ≥ 当前时刻）且任务是完成态 → 同路径取消完成；
 * 块跨现在线则状态不变。只作用于知行计划块；Google 事件块不挂拖拽、不受影响。
 */
private void flipDoneByNowLine(String taskId, int newStartMin, int newEndMin, String title) {
int nowMin = minOfDay(System.currentTimeMillis());
store.load();
TaskItem cur = store.byId(taskId);
if (cur == null || cur.deleted) return;
if (!cur.isDoneToday() && newEndMin <= nowMin) {
cur.markDone();
StartBaseline.markCompleted(ctx, cur.id); // 完成戳：此后再开始从完整时长（v1.21.1）
store.save();
SbSync.pushDoneAsync(ctx, taskId);
toast("「" + title + "」已拖到现在之前，自动标记完成 ✓");
} else if (cur.isDoneToday() && newStartMin >= nowMin) {
cur.markUndone();
StartBaseline.clearCompleted(ctx, cur.id);
store.save();
SbSync.pushDoneAsync(ctx, taskId);
toast("「" + title + "」已拖回现在之后，恢复未完成");
}
}

/**
 * 长按拖拽改计划块时间（v1.6.2）：
 * 长按 400ms 拾起——拾起瞬间 lift 动画（放大到 1.04＋加深阴影＋微微透出，学 iOS/Google 日历）；
 * 拖动中按 5 分钟吸附实时预览新起止，拖动态全程保持放大；松手后短促回落动画恢复原大小，
 * 再经 SyncManager.updateScheduleBlock 写回（App 对 schedule 的 sanction 写入口）。
 * v1.20.0 起已完成块也挂拖拽（拖回现在线之下取消完成，见 flipDoneByNowLine）。
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
flipDoneByNowLine(taskId, ns, ns + dur2, b.title);
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

/**
 * 点计划栏空白处（v1.20.0）：打开统一编辑弹层新建任务，开始时间预填所点时段
 * （5 分钟吸附），保存后由弹层顺手在所点时间生成计划块。只认计划栏 x 范围；
 * 所点分钟按上次渲染的轴几何换算。点在已有计划块上走块自己的触摸/点击，不到这里。
 */
private void onCanvasTap(float x, float y) {
if (dragging || lastPlanW <= 0) return;
if (x < lastPlanX || x > lastPlanX + lastPlanW) return;
int raw = lastAxisStart + Math.round(y / (float) Math.max(1, lastScale));
int startMin = Math.round(raw / 5f) * 5;
startMin = Math.max(DAY_START_MIN, Math.min(startMin, 24 * 60 - 30));
Calendar cal = Calendar.getInstance();
cal.set(Calendar.HOUR_OF_DAY, startMin / 60);
cal.set(Calendar.MINUTE, startMin % 60);
cal.set(Calendar.SECOND, 0);
cal.set(Calendar.MILLISECOND, 0);
TaskEditSheet.showCreate(host, store, cal.getTimeInMillis(), true,
new TaskEditSheet.Host() {
@Override public void onSaved(TaskItem t, boolean created) { render(); }
@Override public void onFocusRule(TaskItem t) { }
@Override public void onResetTimer(TaskItem t) { }
});
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
bg.setColor(Color.argb(90, 217, 75, 58));
bg.setStroke(dp(2), Color.rgb(217, 75, 58));
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
chips.addView(chipCal(Strings.chipEdit(), false, v -> TaskEditSheet.showEdit(host, store, t,
new TaskEditSheet.Host() {
@Override public void onSaved(TaskItem saved, boolean created) { afterTaskEdited(); }
@Override public void onFocusRule(TaskItem task) { dialogFocus(task); }
@Override public void onResetTimer(TaskItem task) { resetTaskFromCal(task); }
})));
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
if (done) { c.markUndone(); StartBaseline.clearCompleted(ctx, c.id); }
else { c.markDone(); StartBaseline.markCompleted(ctx, c.id); }
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
ruleInput.setHint("描述什么情况下你可能会分神，如：读经典时想刷微信、查资料容易逛到购物网站");
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
StartBaseline.markReset(ctx, t.id); // 重置＝抵扣基数清零（v1.21.1），必须在起表前打戳
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

/** 与主页 startTaskTimer 同语义（v1.26.0：截图只走无障碍通道，开始计时不再请求录屏授权）。 */
private void startTaskTimer(final TaskItem t) {
doStartTaskTimer(t);
}

private void doStartTaskTimer(TaskItem t) {
// 今天已计过时按剩余接着倒数（v1.21.1，抵扣收口在 TimerService.startTimerSmart）
if (!TimerService.startTimerSmart(ctx, t)) {
TimerService.startTimer(ctx, Math.max(1, t.durationMinutes) * 60, t.id, t.title);
toast("开始计时：" + t.title + "（" + t.durationMinutes + " 分钟）");
}
handler.postDelayed(this::render, 350);
}

private void addBlock(Block b, int x, int w, int axisStart, int scale, boolean planned) {
LinearLayout col = new LinearLayout(ctx);
col.setOrientation(LinearLayout.VERTICAL);
col.setPadding(dp(3), dp(2), dp(2), dp(2));
int hPx = Math.max(dp(20), (b.endMin - b.startMin) * scale);
GradientDrawable bg = new GradientDrawable();
bg.setCornerRadius(dp(4));
boolean dk3 = Ui.dark(ctx);
if (b.done) {
bg.setColor(dk3 ? Color.rgb(43, 48, 52) : Color.rgb(224, 224, 224));
} else if (b.running) {
bg.setColor(dk3 ? Color.rgb(46, 84, 52) : Color.rgb(129, 199, 132));
bg.setStroke(dp(2), dk3 ? Color.rgb(129, 199, 132) : Color.rgb(27, 94, 32));
} else {
bg.setColor(dk3 ? Color.rgb(30, 56, 37) : Color.rgb(165, 214, 167));
}
col.setBackground(bg);
// 信息层级（v1.26.1，用户定案）：任务名第一（尽量完整、最多 2 行）＞ 优先级
// tag 第二 ＞ 起止时间只作 9sp 灰小字放最后一行。矮块按同一优先序逐项让位：
// 先保名字至少 1 行，再保 tag，最后才轮到时间——绝不出现只有时间没有名字的块。
// 标题取数据层原文，不再拼时间前缀。深色块上标题用米白、浅底用墨青黑 #1F2F2A。
TextView title = new TextView(ctx);
title.setText(b.title + (b.running? " ▶": ""));
title.setTextSize(11);
title.setTypeface(title.getTypeface(), Typeface.BOLD);
title.setMaxLines(hPx >= dp(64)? 2: 1);
title.setEllipsize(android.text.TextUtils.TruncateAt.END);
title.setTextColor(b.done ? Ui.sub(ctx)
: (dk3 ? Color.rgb(247, 243, 236) : Color.rgb(31, 47, 42)));
col.addView(title);
// 优先级标记（v1.26.2 无字版）：颜色与形态本身即轻重——必做朱砂红实心、
// 重要描边空心、次要浅灰实心小块、休息虚线空心。计划栏与实际栏共用本渲染。
TaskItem bt = store.byId(b.taskId);
View tag = bt == null ? null : priorityTag(bt.priority, dk3);
// 起止时间小字：宽栏给全程起止，窄栏只给开始时刻（沿用 v1.12.0 窄栏口径）
TextView timeV = new TextView(ctx);
timeV.setText(w >= dp(64)? hhmm(b.startMin) + "–" + hhmm(b.endMin): hhmm(b.startMin));
timeV.setTextSize(9);
timeV.setMaxLines(1);
timeV.setTextColor(dk3 ? Color.rgb(178, 184, 178) : Color.rgb(105, 112, 105));
if (hPx >= dp(48)) {
// 常规与中块：名（高块 ≤2 行、余者 1 行）／tag／时间小字，三段竖排
if (tag != null) col.addView(tag);
col.addView(timeV);
} else if (hPx >= dp(34)) {
// 矮块：名 1 行／tag 与时间同行（时间贴在 tag 右边）
{
LinearLayout row = new LinearLayout(ctx);
row.setOrientation(LinearLayout.HORIZONTAL);
if (tag != null) {
LinearLayout.LayoutParams tlp = (LinearLayout.LayoutParams) tag.getLayoutParams();
tlp.rightMargin = dp(4);
row.addView(tag);
}
LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
vlp.topMargin = dp(2);
timeV.setLayoutParams(vlp);
row.addView(timeV);
col.addView(row);
}
} else if (hPx >= dp(26)) {
// 更矮：名 1 行／只留 tag，时间省略
if (tag != null) col.addView(tag);
}
// 极矮（<26dp）：只保名字 1 行
FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, hPx);
lp.leftMargin = x;
lp.topMargin = (b.startMin - axisStart) * scale;
canvas.addView(col, lp);
}

/**
 * 优先级标记（v1.26.2 起无字版）：颜色与形态本身就是语言，不写字。
 * 占位与位置沿用 v1.26.0 的 tag 段（任务名下方、topMargin 2dp），统一
 * 18×8dp 小块（圆角 3dp）：必做＝朱砂红 #D94B3A 实心；重要＝墨青黑 1dp
 * 描边空心（深底换米白系）；次要＝浅灰实心、略小（10×8dp）；休息＝
 * 浅灰 1dp 虚线描边空心（线 3dp／空 2dp）。文字版只留在任务列表行
 * 与编辑页的档位 chips，不动。
 */
private View priorityTag(String priority, boolean darkBlock) {
View tag = new View(ctx);
int rank = TaskItem.priorityRank(priority);
int grey = darkBlock ? Color.rgb(178, 184, 178) : Color.rgb(148, 154, 148);
GradientDrawable g = new GradientDrawable();
g.setCornerRadius(dp(3));
int wDp = 18;
if (rank == 0) {
g.setColor(Color.rgb(217, 75, 58)); // 朱砂红实心（品牌点缀色 #D94B3A）
} else if (rank == 1) {
int ink = darkBlock ? Color.rgb(247, 243, 236) : Color.rgb(31, 47, 42); // 墨青黑／深底换米白
g.setColor(Color.TRANSPARENT);
g.setStroke(dp(1), ink);
} else if (rank == 3) {
g.setColor(Color.TRANSPARENT);
g.setStroke(dp(1), grey, dp(3), dp(2)); // 浅灰虚线描边
} else {
g.setColor(grey); // 次要：浅灰实心，尺寸略小
wDp = 10;
}
tag.setBackground(g);
LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(wDp), dp(8));
lp.topMargin = dp(2);
tag.setLayoutParams(lp);
return tag;
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
