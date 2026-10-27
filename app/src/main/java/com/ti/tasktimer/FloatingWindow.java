package com.ti.tasktimer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 常驻悬浮窗（交互模板来自 Floating Notes，用户打开过知行后由 FloatService 一直维持）：
 * 永远显示「当前第一要务」——正在计时的任务 ＞ 刚暂停的任务 ＞ 列表第一名（未完成、必要优先）。
 * 三种模式——
 *  吸附 DOCK：贴屏幕侧边的小标签，只显示分钟数（计时/暂停=剩余分钟，待开始=计划分钟，封顶 99+）；
 *            没有任何按钮，不能暂停；点它或拖离边缘展开。
 *  小展开 MINI：半透明小窗：任务名 + 时间 + ▶/⏸ + ✎；点内容区或 ✎ 进编辑；
 *            拖到屏幕左/右边缘松手吸附成 DOCK。
 *  编辑 EDIT：更不透明的大卡片：开始/暂停、AI（复制任务信息并打开助手）、完成、打开知行、收起；
 *            笔记就是输入框本身，点开直接写、停笔自动保存。时间行：左=剩余（大字），
 *            右=已用 / 计划总（小字，v1.6.1）；笔记区右上角常驻「复制」按钮（只复制不开助手，v1.6.1）。
 * 暂停不直接生效（MINI/EDIT 点 ⏸ 时）：先过暂停卡（PauseCard：选目的＋选时长＋静音开关）——
 * v1.10.0 起暂停必须带时长、v1.11.0 起还要带目的，没有「立刻暂停」了。
 * 到点由 ResumeRemindActivity 全屏叫回（静音暂停走亮屏通知链）。
 * v1.11.0：MINI 计时中有子任务时主行显示当前子任务＋其剩余、次行小字任务名；
 * 暂停时 MINI 两行（暂停「任务名」／目的＋还剩）；EDIT 加暂停条、子任务区与暂停按钮行。
 * v1.12.0 追加规格：两窗主次统一——计时中有当前子任务时 MINI 子任务名放大加粗（与倒计时并列最大档）、
 * EDIT 头部第一行换成子任务名＋时间大字换成子任务剩余、母任务名退到头部第二行细体小字；无子任务两窗原样。
 */
public class FloatingWindow {
    public static final int MODE_EDIT = 0;
    public static final int MODE_MINI = 1;
    public static final int MODE_DOCK = 2;

    public static final int ST_NONE = 0;
    public static final int ST_IDLE = 1;
    public static final int ST_RUNNING = 2;
    public static final int ST_PAUSED = 3;

    private static WindowManager wm;
    private static WindowManager.LayoutParams lp;
    private static LinearLayout rootView;
    private static LinearLayout bodyBox;
    private static LinearLayout miniBox;
    private static TextView dockView;
    private static TextView titleView;
    private static TextView headerSubView;
    private static TextView timeView;
    private static TextView miniTitle;
    private static TextView miniTime;
    private static TextView miniPlay;
    private static TextView miniSub;
    private static TextView stateView;
    private static TextView usedView;
    private static TextView saveStateView;
    private static Button playPauseBtn;
    private static Button doneBtn;
    private static Button aiBtnRef;
    private static LinearLayout pauseStrip;
    private static TextView pauseStripText;
    private static LinearLayout pausedBtns;
    private static SubtaskPanel subPanel;
    private static LinearLayout subFormBox;
    private static String subSignature = "";
    private static boolean subEditing = false;
    private static boolean pauseCardForRepurpose = false;
    private static EditText noteEdit;
    private static boolean editing = false;
    private static boolean settingText = false;
    private static boolean suppressed = false;
    private static boolean noteDirty = false;
    private static String cachedTaskId = null;
    private static String boundNoteText = null; // 笔记框上次与数据源对齐的文字（v1.6.2 同步修复）
    private static String displayTaskId = null;
    private static TaskStore.ChangeListener storeListener = null; // 数据一变就重渲染（v1.6.2）
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static Runnable pendingSave;

    // ---------- 显示对象：当前第一要务 ----------
    public static class Display {
        public int state = ST_NONE;
        public TaskItem task;
        public String title = "暂无任务";
        public long remainMs = 0;
    }

    public static Display resolveDisplay(Context c) {
        Display d = new Display();
        TaskStore store = new TaskStore(c);
        if (TimerState.isRunning(c)) {
            d.state = ST_RUNNING;
            d.task = store.byId(TimerState.taskId(c));
            d.title = TimerState.taskTitle(c);
            d.remainMs = Math.max(0, TimerState.endAt(c) - System.currentTimeMillis());
            return d;
        }
        if (TimerState.isPaused(c)) {
            d.state = ST_PAUSED;
            d.task = store.byId(TimerState.pausedTaskId(c));
            d.title = TimerState.pausedTitle(c);
            if (d.title == null || d.title.isEmpty()) d.title = d.task != null ? d.task.title : "已暂停的任务";
            d.remainMs = TimerState.pausedRemainMs(c);
            return d;
        }
        for (TaskItem t : store.visibleSorted()) {
            if (!t.isDoneToday()) {
                d.state = ST_IDLE;
                d.task = t;
                d.title = t.title;
                return d;
            }
        }
        return d;
    }

    public static String displaySummary(Context c) {
        Display d = resolveDisplay(c);
        if (d.state == ST_RUNNING) return "「" + d.title + "」计时中，剩余 " + fmt(d.remainMs);
        if (d.state == ST_PAUSED) return "「" + d.title + "」已暂停，剩余 " + fmt(d.remainMs);
        if (d.state == ST_IDLE) return "下一件：「" + d.title + "」（" + d.task.durationMinutes + " 分钟）";
        return "暂无未完成任务";
    }

    private static String fmt(long ms) {
        long sec = Math.max(0, ms / 1000);
        return sec >= 3600
                ? String.format("%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
                : String.format("%d:%02d", sec / 60, sec % 60);
    }

    // ---------- 开关与模式 ----------
    public static boolean enabled(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("float_enabled", true);
    }

    public static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("float_enabled", on).apply();
        if (!on) {
            hide();
            FloatService.stop(c);
        } else {
            FloatService.start(c);
        }
    }

    private static int mode(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("float_mode2", MODE_MINI);
    }

    private static void setMode(Context c, int m) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putInt("float_mode2", m).apply();
        if (m != MODE_EDIT) endEditFocus(c);
        applyMode(c);
    }

    private static int dockSide(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("float_dock_side", 1); // 0 左 1 右
    }

    private static void setDockSide(Context c, int s) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putInt("float_dock_side", s).apply();
    }

    private static void applyMode(Context c) {
        if (rootView == null || lp == null) return;
        int m = mode(c);
        float d = c.getResources().getDisplayMetrics().density;
        bodyBox.setVisibility(m == MODE_EDIT ? View.VISIBLE : View.GONE);
        miniBox.setVisibility(m == MODE_MINI ? View.VISIBLE : View.GONE);
        dockView.setVisibility(m == MODE_DOCK ? View.VISIBLE : View.GONE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(m == MODE_EDIT ? Color.argb(240, 24, 36, 31)
                : m == MODE_MINI ? Color.argb(148, 24, 36, 31)
                : Color.argb(170, 24, 36, 31));
        bg.setCornerRadius((m == MODE_DOCK ? 14 : 16) * d);
        rootView.setBackground(bg);
        if (m == MODE_DOCK) rootView.setPadding((int) (4 * d), (int) (10 * d), (int) (4 * d), (int) (10 * d));
        else if (m == MODE_MINI) rootView.setPadding((int) (10 * d), (int) (7 * d), (int) (10 * d), (int) (7 * d));
        else {
            int pad = (int) (12 * d);
            rootView.setPadding(pad, (int) (8 * d), pad, pad);
        }
        lp.width = m == MODE_EDIT ? (int) (292 * d)
                : m == MODE_MINI ? (int) (262 * d)
                : (int) (46 * d);
        if (m == MODE_DOCK) lp.x = dockX(c);
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
    }

    private static int dockX(Context c) {
        int w = c.getResources().getDisplayMetrics().widthPixels;
        float d = c.getResources().getDisplayMetrics().density;
        return dockSide(c) == 0 ? 0 : w - (int) (46 * d);
    }

    /** 预览（权限页用）：无需计时，显示 10 秒样例。计时中不给样例（v1.21.2）：
     *  常驻服务不到 1 秒就会把样例覆盖回真实任务，用户只看到闪一下——改为直接
     *  Toast 说明悬浮窗显示的就是当前任务，预览只在没计时时看。 */
    public static void showPreview(final Context c) {
        if (TimerState.isRunning(c)) {
            android.widget.Toast.makeText(c, "正在计时，悬浮窗显示的就是当前任务；预览只在没计时时看", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        if (!AlertHelper.canDrawOverlays(c)) {
            android.widget.Toast.makeText(c, "先把上面的「上层显示」权限开成 ✓，悬浮窗才能显示", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        hide();
        build(c.getApplicationContext());
        if (rootView == null) {
            android.widget.Toast.makeText(c, "悬浮窗创建失败，请把现象反馈给开发者", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        if (titleView != null) titleView.setText("预览：这里显示当前第一要务");
        if (timeView != null) timeView.setText("24:59");
        if (miniTitle != null) miniTitle.setText("预览任务");
        if (miniTime != null) miniTime.setText("24:59");
        if (dockView != null) dockView.setText("25");
        settingText = true;
        if (noteEdit != null) noteEdit.setText("这是预览。悬浮窗现在是常驻的：没计时时显示第一要务，可以直接开始、暂停（先选多久后提醒）、点 AI 把任务发给助手。");
        settingText = false;
        ui.postDelayed(() -> { if (!TimerState.isRunning(c)) hide(); }, 10_000);
    }

    /** 检查页在前台时让位；检查页销毁后由常驻服务自动恢复显示。 */
    public static void setSuppressed(boolean s) {
        suppressed = s;
        if (s) hideInternal();
    }

    // ---------- 显示/隐藏/刷新 ----------
    public static void ensureShown(Context c) {
        if (!enabled(c) || suppressed || rootView != null) return;
        // snooze 期间（v1.14.0）：只藏悬浮窗，计时/守卫/alarm/通知照常
        if (FloatSnooze.isActive(c)) { hide(); return; }
        if (!AlertHelper.canDrawOverlays(c)) return;
        build(c.getApplicationContext());
    }

    /** TimerService 的旧入口：统一走 refresh 渲染。 */
    public static void update(Context c, String remainText) {
        refresh(c);
    }

    public static void refresh(Context c) {
        if (rootView == null) return;
        // 暂停卡 overlay 的收尾：不是为「换个目的」开的卡、且已不在计时中时，状态已变就关掉它
        if (!PauseCard.overlayOpen()) pauseCardForRepurpose = false;
        else if (!TimerState.isRunning(c) && !pauseCardForRepurpose) PauseCard.closeOverlay();
        Display d = resolveDisplay(c);
        displayTaskId = d.task != null ? d.task.id : null;
        String timeText;
        if (d.state == ST_RUNNING || d.state == ST_PAUSED) timeText = fmt(d.remainMs);
        else if (d.state == ST_IDLE) timeText = d.task.durationMinutes + "分";
        else timeText = "--:--";
        if (titleView != null) titleView.setText(d.title);
        if (timeView != null) timeView.setText(timeText);
        // MINI 两行呈现（v1.11.0）
        boolean subRun = d.state == ST_RUNNING && d.task != null
                && d.task.id.equals(TimerState.taskId(c)) && TimerState.subActive(c);
        if (miniTitle != null && miniTime != null) {
            if (d.state == ST_PAUSED) {
                miniTitle.setText(Strings.miniPausedTitle(d.title));
                miniTitle.setTextSize(13);
                miniTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
                miniTime.setText("");
                miniTime.setVisibility(View.GONE);
            } else if (subRun) {
                // 子任务是主角（v1.12.0 追加规格）：名字放大加粗，与右侧子任务倒计时并列全窗最大档
                miniTitle.setText(TimerState.subTitle(c));
                miniTitle.setTextSize(15);
                miniTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                miniTime.setVisibility(View.VISIBLE);
                miniTime.setText(fmt(Math.max(0, TimerState.subEndAt(c) - System.currentTimeMillis())));
            } else {
                miniTitle.setText(d.title);
                miniTitle.setTextSize(13);
                miniTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
                miniTime.setVisibility(View.VISIBLE);
                miniTime.setText(timeText);
            }
        }
        // EDIT 头部主次（v1.12.0 追加规格，与 MINI 统一）：计时中且有当前子任务时——
        // 第一行＝子任务名（头部最大字号槽）＋时间大字改显子任务剩余；第二行＝母任务名（细体小字弱化）。
        // 无子任务时维持原样；暂停态不动（暂停条自有呈现）。
        if (titleView != null && timeView != null) {
            if (subRun) {
                titleView.setText(TimerState.subTitle(c));
                timeView.setText(fmt(Math.max(0, TimerState.subEndAt(c) - System.currentTimeMillis())));
                if (headerSubView != null) {
                    headerSubView.setText(d.title);
                    headerSubView.setVisibility(View.VISIBLE);
                }
            } else if (headerSubView != null) {
                headerSubView.setVisibility(View.GONE);
            }
        }
        if (miniSub != null) {
            if (d.state == ST_PAUSED) {
                String reason = TimerState.pauseReason(c);
                String emoji = TimerState.pauseEmoji(c);
                long ra = TimerState.remindAt(c);
                if (reason != null) {
                    String txt = (emoji.isEmpty() ? "" : emoji + " ") + reason;
                    if (ra > 0) {
                        txt += " · " + Strings.pauseLeftLabel() + " "
                                + fmt(Math.max(0, ra - System.currentTimeMillis()));
                    }
                    miniSub.setText(txt);
                    miniSub.setTextColor(Color.rgb(247, 164, 148));
                    miniSub.setVisibility(View.VISIBLE);
                } else {
                    miniSub.setVisibility(View.GONE);
                }
            } else if (subRun) {
                miniSub.setText(d.title);
                miniSub.setTextColor(Color.rgb(190, 200, 195));
                miniSub.setVisibility(View.VISIBLE);
            } else if (d.state == ST_IDLE && d.task != null && d.task.hasSubtasks()) {
                miniSub.setText(Strings.miniSubCount(d.task.subtasks.size()));
                miniSub.setTextColor(Color.rgb(190, 200, 195));
                miniSub.setVisibility(View.VISIBLE);
            } else {
                miniSub.setVisibility(View.GONE);
            }
        }
        if (miniPlay != null) miniPlay.setText(d.state == ST_RUNNING ? "⏸" : "▶");
        // EDIT 暂停条（v1.11.0）：暂停时贴在任务内容顶部
        if (pauseStrip != null && pauseStripText != null) {
            if (d.state == ST_PAUSED) {
                String reason = TimerState.pauseReason(c);
                String emoji = TimerState.pauseEmoji(c);
                long ra = TimerState.remindAt(c);
                if (reason != null) {
                    pauseStripText.setText(ra > 0
                            ? Strings.pauseStrip(emoji, reason,
                                fmt(Math.max(0, ra - System.currentTimeMillis())))
                            : Strings.pauseStripNoRemind(emoji, reason));
                } else {
                    pauseStripText.setText(Strings.miniPausedTitle(d.title));
                }
                pauseStrip.setVisibility(View.VISIBLE);
            } else {
                pauseStrip.setVisibility(View.GONE);
            }
        }
        // EDIT 按钮行随暂停态切换：暂停时藏 AI、亮「再加 5 分钟 / 换个目的」
        if (aiBtnRef != null) aiBtnRef.setVisibility(d.state == ST_PAUSED ? View.GONE : View.VISIBLE);
        if (pausedBtns != null) pausedBtns.setVisibility(d.state == ST_PAUSED ? View.VISIBLE : View.GONE);
        // EDIT 子任务区（v1.11.0）：编辑中或签名未变时不重建
        if (subPanel != null && !subEditing) {
            if (d.task == null) {
                subPanel.setVisibility(View.GONE);
                subSignature = "";
            } else {
                subPanel.setVisibility(View.VISIBLE);
                int ri = subRun ? TimerState.subIndex(c) : -1;
                String sig = subPanel.signature(d.task, ri);
                if (!sig.equals(subSignature)) {
                    subSignature = sig;
                    subPanel.bind(d.task, fwSubHost);
                } else {
                    subPanel.tickRemain();
                }
            }
        }
        if (stateView != null) {
            stateView.setText(d.state == ST_RUNNING ? "计时中"
                    : d.state == ST_PAUSED ? "已暂停，点 ▶ 继续"
                    : d.state == ST_IDLE ? "第一要务，点 ▶ 开始" : "今天的任务都完成了");
        }
        // EDIT 卡时间行右侧（v1.6.1）：已用 / 计划总。
        // 已用 = SessionLog 全部段累计 + 正在跑的这一段（口径见 SessionLog.totalMsForTask）；
        // 计划总 = 当前目标总时长（计时/暂停中取计时器目标，含加时；未开始取任务计划时长）。
        if (usedView != null) {
            if (d.task == null) {
                usedView.setText("");
            } else {
                long totalMs;
                if (d.state == ST_RUNNING) {
                    totalMs = TimerState.totalSec(c) * 1000L;
                } else if (d.state == ST_PAUSED) {
                    int ts = TimerState.pausedTotalSec(c);
                    totalMs = (ts > 0 ? ts : Math.max(1, d.task.durationMinutes) * 60) * 1000L;
                } else {
                    totalMs = Math.max(1, d.task.durationMinutes) * 60_000L;
                }
                if (totalMs <= 0) totalMs = Math.max(1, d.task.durationMinutes) * 60_000L;
                usedView.setText(fmt(SessionLog.totalMsForTask(c, d.task.id)) + " / " + fmt(totalMs));
            }
        }
        if (playPauseBtn != null) {
            playPauseBtn.setText(d.state == ST_RUNNING ? "⏸ 暂停"
                    : d.state == ST_PAUSED ? "▶ 继续" : "▶ 开始");
            playPauseBtn.setEnabled(d.state != ST_NONE);
        }
        if (doneBtn != null) doneBtn.setVisibility(d.state == ST_RUNNING ? View.VISIBLE : View.GONE);
        if (dockView != null) {
            long mins;
            if (d.state == ST_RUNNING || d.state == ST_PAUSED) mins = Math.max(1, (d.remainMs + 59_999) / 60_000);
            else if (d.state == ST_IDLE) mins = d.task.durationMinutes;
            else mins = 0;
            dockView.setText(d.state == ST_NONE ? "✓" : mins > 99 ? "99+" : String.valueOf(mins));
        }
        // 笔记与数据源对齐（v1.6.2 修复「悬浮窗与主页改同一条笔记不同步」）：
        // 不只任务变了才重载——同一个任务的笔记在主页/同步里被改了，这里也要跟着变；
        // 唯一例外是自己正在编辑且有未保存的修改（noteDirty），那时以手头的输入为准、不许覆盖。
        if (noteEdit != null) {
            String diskNote = d.task == null || d.task.note == null ? "" : d.task.note;
            boolean taskSwitched = cachedTaskId == null || !cachedTaskId.equals(displayTaskId);
            if (taskSwitched || (!diskNote.equals(boundNoteText) && !(editing && noteDirty))) {
                cachedTaskId = displayTaskId;
                boundNoteText = diskNote;
                settingText = true;
                noteEdit.setText(diskNote);
                settingText = false;
            }
        }
    }

    public static void hide() {
        suppressed = false;
        hideInternal();
    }

    private static void hideInternal() {
        PauseCard.closeOverlay();
        subSignature = "";
        subEditing = false;
        pauseCardForRepurpose = false;
        if (pendingSave != null) { ui.removeCallbacks(pendingSave); pendingSave = null; }
        if (storeListener != null) {
            TaskStore.removeChangeListener(storeListener);
            storeListener = null;
        }
        if (wm != null && rootView != null) {
            try { wm.removeView(rootView); } catch (Exception ignored) { }
        }
        rootView = null;
        editing = false;
        noteDirty = false;
        cachedTaskId = null;
        boundNoteText = null;
        displayTaskId = null;
    }

    // ---------- 开始 / 暂停 / 继续 ----------
    private static void onPlayPause(Context c) {
        Display d = resolveDisplay(c);
        if (d.state == ST_RUNNING) {
            PauseCard.showOverlay(c, d.title, Strings.pauseStartBtn(),
                    ch -> TimerService.pauseWithReminder(c, ch.delayMs, ch.reason, ch.emoji, ch.silent));
        } else if (d.state == ST_PAUSED && d.task != null) {
            int total = TimerState.pausedTotalSec(c);
            if (total <= 0) total = Math.max(1, d.task.durationMinutes) * 60;
            int remain = (int) Math.max(1, TimerState.pausedRemainMs(c) / 1000);
            TimerService.resumeTimer(c, total, remain, d.task.id, d.task.title);
        } else if (d.state == ST_IDLE && d.task != null) {
            // 今天已计过时按剩余接着倒数（v1.21.1，抵扣收口在 TimerService.startTimerSmart）
            if (!TimerService.startTimerSmart(c, d.task)) {
                TimerService.startTimer(c, Math.max(1, d.task.durationMinutes) * 60, d.task.id, d.task.title);
            }
        }
    }

    // ---------- 构建 ----------
    private static void build(final Context c) {
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        float d = c.getResources().getDisplayMetrics().density;

        rootView = new LinearLayout(c);
        rootView.setOrientation(LinearLayout.VERTICAL);

        // —— 吸附模式：边缘分钟数标签（无按钮，不能暂停） ——
        dockView = new TextView(c);
        dockView.setText("25");
        dockView.setTextColor(Color.WHITE);
        dockView.setTextSize(16);
        dockView.setTypeface(dockView.getTypeface(), android.graphics.Typeface.BOLD);
        dockView.setGravity(Gravity.CENTER);
        dockView.setVisibility(View.GONE);
        rootView.addView(dockView);

        // —— 小展开模式：主行（任务名 + 时间 + ▶/⏸ + ✎）＋次行（子任务/暂停信息，小字） ——
        miniBox = new LinearLayout(c);
        miniBox.setOrientation(LinearLayout.VERTICAL);
        miniBox.setVisibility(View.GONE);
        LinearLayout miniRow = new LinearLayout(c);
        miniRow.setOrientation(LinearLayout.HORIZONTAL);
        miniRow.setGravity(Gravity.CENTER_VERTICAL);
        miniBox.addView(miniRow);
        miniTitle = new TextView(c);
        miniTitle.setTextColor(Color.WHITE);
        miniTitle.setTextSize(13);
        miniTitle.setMaxLines(1);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        miniRow.addView(miniTitle);
        miniTime = new TextView(c);
        miniTime.setTextColor(Color.WHITE);
        miniTime.setTextSize(16);
        miniTime.setTypeface(miniTime.getTypeface(), android.graphics.Typeface.BOLD);
        miniTime.setPadding(dp(c, 6), 0, dp(c, 2), 0);
        miniRow.addView(miniTime);
        miniPlay = new TextView(c);
        miniPlay.setText("▶");
        miniPlay.setTextColor(Color.WHITE);
        miniPlay.setTextSize(16);
        miniPlay.setPadding(dp(c, 8), dp(c, 4), dp(c, 4), dp(c, 4));
        miniPlay.setOnClickListener(v -> onPlayPause(c));
        miniRow.addView(miniPlay);
        TextView bang = new TextView(c);
        bang.setText("✎");
        bang.setTextColor(Color.rgb(220, 220, 220));
        bang.setTextSize(17);
        bang.setPadding(dp(c, 6), dp(c, 2), dp(c, 4), dp(c, 2));
        bang.setOnClickListener(v -> openEdit(c));
        miniRow.addView(bang);
        // 次行小字：计时中＝任务名（主行让给当前子任务时）；暂停时＝目的＋还剩；平时隐藏
        miniSub = new TextView(c);
        miniSub.setTextColor(Color.rgb(190, 200, 195));
        miniSub.setTextSize(11.5f);
        miniSub.setSingleLine(true);
        miniSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniSub.setPadding(dp(c, 1), dp(c, 3), 0, 0);
        miniSub.setVisibility(View.GONE);
        miniBox.addView(miniSub);
        rootView.addView(miniBox);

        // —— 编辑模式：完整卡片 ——
        bodyBox = new LinearLayout(c);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        rootView.addView(bodyBox);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = new TextView(c);
        tag.setText("知行 · 当前第一要务");
        tag.setTextColor(Color.rgb(176, 176, 176));
        tag.setTextSize(11);
        tag.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(tag);
        TextView shrinkBtn = smallLabel(c, "收起 —");
        shrinkBtn.setOnClickListener(v -> setMode(c, MODE_MINI));
        head.addView(shrinkBtn);
        bodyBox.addView(head);

        titleView = new TextView(c);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setMaxLines(2);
        bodyBox.addView(titleView);

        // EDIT 头部第二行（v1.12.0 追加规格）：计时中且有当前子任务时，第一行让给子任务名，
        // 母任务名退到这一行——细体、小字、弱化显示；其余状态隐藏
        headerSubView = new TextView(c);
        headerSubView.setTextColor(Color.rgb(190, 200, 195));
        headerSubView.setTextSize(11.5f);
        headerSubView.setSingleLine(true);
        headerSubView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headerSubView.setPadding(0, dp(c, 2), 0, 0);
        headerSubView.setVisibility(View.GONE);
        bodyBox.addView(headerSubView);

        // 暂停条（v1.11.0）：只在暂停时出现，贴在任务内容顶部——大窗主体永远是任务本身
        pauseStrip = new LinearLayout(c);
        pauseStrip.setOrientation(LinearLayout.HORIZONTAL);
        pauseStrip.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable stripBg = new GradientDrawable();
        stripBg.setColor(Color.argb(33, 217, 75, 58));
        stripBg.setCornerRadius(12 * d);
        stripBg.setStroke((int) d, Color.argb(115, 217, 75, 58));
        pauseStrip.setBackground(stripBg);
        pauseStrip.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        LinearLayout.LayoutParams stripLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stripLp.setMargins(0, dp(c, 8), 0, dp(c, 2));
        pauseStrip.setLayoutParams(stripLp);
        pauseStripText = new TextView(c);
        pauseStripText.setTextColor(Color.rgb(247, 164, 148));
        pauseStripText.setTextSize(13.5f);
        pauseStrip.addView(pauseStripText);
        pauseStrip.setVisibility(View.GONE);
        bodyBox.addView(pauseStrip);

        LinearLayout timeRow = new LinearLayout(c);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeView = new TextView(c);
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(32);
        timeView.setTypeface(timeView.getTypeface(), android.graphics.Typeface.BOLD);
        timeRow.addView(timeView);
        stateView = new TextView(c);
        stateView.setTextColor(Color.rgb(176, 176, 176));
        stateView.setTextSize(12);
        stateView.setPadding(dp(c, 10), 0, 0, 0);
        stateView.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        timeRow.addView(stateView);
        // 时间行最右侧（v1.6.1）：已用 / 计划总，字号小于左侧大字剩余时间
        usedView = new TextView(c);
        usedView.setTextColor(Color.rgb(200, 200, 200));
        usedView.setTextSize(13);
        usedView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        timeRow.addView(usedView);
        bodyBox.addView(timeRow);

        // 子任务区（v1.11.0）：与主页展开区共用 SubtaskPanel；编辑走卡内行内表单（subFormBox）
        subPanel = new SubtaskPanel(c, true, true);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp(c, 10), 0, dp(c, 2));
        subPanel.setLayoutParams(subLp);
        bodyBox.addView(subPanel);
        subFormBox = new LinearLayout(c);
        subFormBox.setOrientation(LinearLayout.VERTICAL);
        subFormBox.setVisibility(View.GONE);
        bodyBox.addView(subFormBox);

        noteEdit = new EditText(c);
        noteEdit.setTextColor(Color.WHITE);
        noteEdit.setHintTextColor(Color.rgb(150, 150, 150));
        noteEdit.setHint("点这里直接写笔记（自动保存）");
        noteEdit.setTextSize(14);
        noteEdit.setMinLines(2);
        // 笔记区高度封顶 + 内部滚动：文字再长也只在这个框里上下滚，
        // 不许把下面的按钮行（开始/AI/完成/打开知行/收起）顶出屏幕
        noteEdit.setMaxLines(6);
        noteEdit.setVerticalScrollBarEnabled(true);
        noteEdit.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        noteEdit.setGravity(Gravity.TOP);
        noteEdit.setBackgroundColor(Color.TRANSPARENT);
        // 右侧留位给叠在右上角的「复制」按钮，长文字换行时不钻到它底下
        noteEdit.setPadding(0, dp(c, 4), dp(c, 48), 0);
        noteEdit.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN && !editing) beginEditFocus(c);
            return false;
        });
        noteEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void afterTextChanged(Editable s) {
                if (settingText || !editing) return;
                noteDirty = true;
                if (saveStateView != null) saveStateView.setText("正在保存…");
                if (pendingSave != null) ui.removeCallbacks(pendingSave);
                pendingSave = () -> persistNote(c, false);
                ui.postDelayed(pendingSave, 700);
            }
        });
        // 笔记区套一层 FrameLayout：右上角常驻「复制」按钮（v1.6.1），叠在编辑区之上、
        // 不随文字滚动消失；点它先把正在写的笔记落盘，再经 AiLaunchActivity 只复制
        // （剪贴板写入必须由前台 Activity 做，与 AI 按钮同一个中转页，但不打开助手）。
        FrameLayout noteWrap = new FrameLayout(c);
        noteEdit.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        noteWrap.addView(noteEdit);
        TextView copyBtn = new TextView(c);
        copyBtn.setText("复制");
        copyBtn.setTextColor(Color.WHITE);
        copyBtn.setTextSize(12);
        GradientDrawable copyBg = new GradientDrawable();
        copyBg.setColor(Color.argb(210, 80, 80, 80));
        copyBg.setCornerRadius(6 * d);
        copyBtn.setBackground(copyBg);
        copyBtn.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        FrameLayout.LayoutParams copyLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        copyLp.setMargins(0, dp(c, 2), dp(c, 2), 0);
        copyBtn.setLayoutParams(copyLp);
        copyBtn.setOnClickListener(v -> {
            Display dd = resolveDisplay(c);
            if (dd.task == null) {
                android.widget.Toast.makeText(c, "现在没有可复制的任务", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            if (pendingSave != null) { ui.removeCallbacks(pendingSave); pendingSave = null; }
            persistNote(c, false); // 先落盘，复制的文案里带上刚写的笔记
            Intent i = new Intent(c, AiLaunchActivity.class);
            i.putExtra(AiLaunchActivity.EXTRA_TASK_ID, dd.task.id);
            i.putExtra(AiLaunchActivity.EXTRA_COPY_ONLY, true);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        });
        noteWrap.addView(copyBtn);
        bodyBox.addView(noteWrap);

        saveStateView = new TextView(c);
        saveStateView.setText("点笔记区直接写，停笔自动保存");
        saveStateView.setTextColor(Color.rgb(150, 150, 150));
        saveStateView.setTextSize(11);
        bodyBox.addView(saveStateView);

        LinearLayout btns = new LinearLayout(c);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        playPauseBtn = smallButton(c, "▶ 开始");
        playPauseBtn.setOnClickListener(v -> onPlayPause(c));
        btns.addView(playPauseBtn);
        aiBtnRef = smallButton(c, "AI");
        aiBtnRef.setOnClickListener(v -> {
            Display dd = resolveDisplay(c);
            if (dd.task == null) {
                android.widget.Toast.makeText(c, "现在没有可发送的任务", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(c, AiLaunchActivity.class);
            i.putExtra(AiLaunchActivity.EXTRA_TASK_ID, dd.task.id);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            // 用户定案（v1.6.2）：点「AI」跳去助手后，悬浮窗自动缩回 MINI。
            // setMode 顺带 endEditFocus，先把正在写的笔记落盘，AI 读到的文案带最新笔记。
            setMode(c, MODE_MINI);
        });
        btns.addView(aiBtnRef);
        doneBtn = smallButton(c, "完成");
        doneBtn.setOnClickListener(v -> {
            setSuppressed(true);
            Intent i = new Intent(c, CheckActivity.class);
            i.putExtra(CheckActivity.EXTRA_TYPE, AlertHelper.TYPE_END);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        });
        btns.addView(doneBtn);
        bodyBox.addView(btns);

        // 暂停时的按钮行（v1.11.0）：再加 5 分钟 / 换个目的（继续用上面一行的 ▶ 继续）
        pausedBtns = new LinearLayout(c);
        pausedBtns.setOrientation(LinearLayout.HORIZONTAL);
        pausedBtns.setVisibility(View.GONE);
        Button more5Btn = smallButton(c, Strings.pauseMore5());
        more5Btn.setOnClickListener(v -> {
            if (!TimerState.isPaused(c)) return;
            long remain = Math.max(0, TimerState.remindAt(c) - System.currentTimeMillis()) + 300_000L;
            ResumeReminder.schedule(c, TimerState.pausedTaskId(c), remain);
            android.widget.Toast.makeText(c, Strings.pauseMore5(), android.widget.Toast.LENGTH_SHORT).show();
            refresh(c);
        });
        pausedBtns.addView(more5Btn);
        Button changeBtn = smallButton(c, Strings.pauseChangePurpose());
        changeBtn.setOnClickListener(v -> {
            Display dd = resolveDisplay(c);
            if (dd.state != ST_PAUSED) return;
            pauseCardForRepurpose = true;
            PauseCard.showOverlay(c, dd.title, Strings.pauseSaveBtn(), ch -> {
                pauseCardForRepurpose = false;
                TimerState.setPauseMeta(c, ch.reason, ch.emoji, ch.silent);
                ResumeReminder.schedule(c, TimerState.pausedTaskId(c), ch.delayMs);
                SbTimerSync.onLocalTimerChanged(c);
                refresh(c);
            });
        });
        pausedBtns.addView(changeBtn);
        bodyBox.addView(pausedBtns);

        LinearLayout btns2 = new LinearLayout(c);
        btns2.setOrientation(LinearLayout.HORIZONTAL);
        Button openBtn = smallButton(c, "打开知行");
        openBtn.setOnClickListener(v -> {
            Intent i = new Intent(c, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            // 用户定案（v1.6.2）：点「打开知行」拉起主界面后，悬浮窗自动缩回 MINI，不留大卡占屏。
            setMode(c, MODE_MINI);
        });
        btns2.addView(openBtn);
        Button miniBtn = smallButton(c, "收起");
        miniBtn.setOnClickListener(v -> setMode(c, MODE_MINI));
        btns2.addView(miniBtn);
        // 关闭（v1.14.0）：定时 snooze / 等任务结束 / 不再出现，经中转页选择
        Button closeBtn = smallButton(c, Strings.en() ? "Close" : "关闭");
        closeBtn.setOnClickListener(v -> {
            Intent i = new Intent(c, FloatCloseActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        });
        btns2.addView(closeBtn);
        bodyBox.addView(btns2);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
                (int) (262 * d), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = (int) (14 * d);
        lp.y = (int) (110 * d);

        rootView.setOnTouchListener(new Dragger(c));
        try {
            wm.addView(rootView, lp);
        } catch (Exception e) {
            rootView = null;
            return;
        }
        // 数据变更即时重渲染（v1.6.2）：主页/同步在别处改了任务，这里不等下一秒节拍。
        if (storeListener == null) {
            final Context app = c.getApplicationContext();
            storeListener = () -> {
                refresh(app);
                // 在本窗里给正在计时的任务加/改子任务 → 对账接管（v1.13.0）
                SubtaskResync.check(app);
            };
            TaskStore.addChangeListener(storeListener);
        }
        applyMode(c);
        refresh(c);
    }

    /** 进入编辑：展开卡片 + 笔记输入框立刻获得焦点并弹键盘。 */
    private static void openEdit(Context c) {
        setMode(c, MODE_EDIT);
        ui.postDelayed(() -> beginEditFocus(c), 60);
    }

    private static void beginEditFocus(Context c) {
        if (rootView == null || noteEdit == null) return;
        if (mode(c) != MODE_EDIT) setMode(c, MODE_EDIT);
        editing = true;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
        noteEdit.requestFocus();
        noteEdit.setSelection(noteEdit.getText().length());
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) ui.postDelayed(() -> imm.showSoftInput(noteEdit, InputMethodManager.SHOW_IMPLICIT), 80);
    }

    private static void endEditFocus(Context c) {
        if (!editing) return;
        if (pendingSave != null) { ui.removeCallbacks(pendingSave); pendingSave = null; }
        persistNote(c, true);
        editing = false;
        noteDirty = false;
        if (lp == null || rootView == null) return;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = 0;
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
    }

    private static void persistNote(Context c, boolean fromEnd) {
        if (noteEdit == null || displayTaskId == null) return;
        TaskStore store = new TaskStore(c);
        TaskItem t = store.byId(displayTaskId);
        if (t == null) return;
        String v = noteEdit.getText().toString().trim();
        if (!v.equals(t.note == null ? "" : t.note)) {
            t.note = v;
            t.touch();
            store.save();
            boundNoteText = v; // 已与数据源对齐，refresh 不要再重设一遍输入框
        }
        if (saveStateView != null) saveStateView.setText("已保存 ✓");
        if (fromEnd && noteDirty) {
            TimerState.logEvent(c, "悬浮窗笔记[" + t.title + "]：" + v);
            final Context app = c.getApplicationContext();
            new Thread(() -> SbSync.syncNow(app, new TaskStore(app))).start();
        }
    }

    // ---------- 子任务（EDIT 卡，v1.11.0） ----------
    /** 子任务面板宿主（悬浮窗）：落盘按 id 重取任务改，免得旧副本整表覆盖（同 v1.6.2 纪律）。 */
    private static final SubtaskPanel.Host fwSubHost = new SubtaskPanel.Host() {
        @Override public void onSubtasksEdited(TaskItem bound) {
            Context c = appCtx();
            if (c == null) return;
            TaskStore store = new TaskStore(c);
            TaskItem real = store.byId(bound.id);
            if (real != null) {
                real.subtasks.clear();
                for (Subtask s : bound.subtasks) real.subtasks.add(s.copy());
                real.syncDurationFromSubtasks();
                real.touch();
                store.save();
            }
            refresh(c);
        }
        @Override public void requestEditSubtask(TaskItem t, int index) {
            Context c = appCtx();
            if (c != null) openSubForm(c, t, index);
        }
        @Override public int runningIndex(TaskItem t) {
            Context c = appCtx();
            if (c != null && TimerState.isRunning(c)
                    && t.id.equals(TimerState.taskId(c)) && TimerState.subActive(c)) {
                return TimerState.subIndex(c);
            }
            return -1;
        }
        @Override public String runningRemainText(TaskItem t) {
            Context c = appCtx();
            return c == null ? null
                    : fmt(Math.max(0, TimerState.subEndAt(c) - System.currentTimeMillis()));
        }
        @Override public String headerRightText(TaskItem t) {
            Context c = appCtx();
            if (c == null) return null;
            int ri = runningIndex(t);
            if (ri >= 0) return Strings.subProgress(ri + 1, TimerState.subCount(c), TimerState.subTitle(c));
            if (t.hasSubtasks()) return Strings.subTotalNote(t.subtasksTotalMinutes());
            return null;
        }
    };

    private static Context appCtx() {
        return rootView == null ? null : rootView.getContext();
    }

    /** 卡内行内表单：改子任务名/分钟（或新增），保存/删除/取消都在卡里完成，不跳页。 */
    private static void openSubForm(final Context c, final TaskItem bound, final int index) {
        if (subFormBox == null) return;
        Strings.init(c);
        final Subtask cur = index >= 0 && index < bound.subtasks.size()
                ? bound.subtasks.get(index) : null;
        subEditing = true;
        subFormBox.removeAllViews();
        subFormBox.setVisibility(View.VISIBLE);
        float d = c.getResources().getDisplayMetrics().density;

        TextView formTitle = new TextView(c);
        formTitle.setText(cur == null ? Strings.subEditNew() : Strings.subEditTitle());
        formTitle.setTextColor(Color.rgb(200, 200, 200));
        formTitle.setTextSize(12);
        subFormBox.addView(formTitle);

        final EditText nameIn = new EditText(c);
        nameIn.setHint(Strings.subNameHint());
        nameIn.setTextColor(Color.WHITE);
        nameIn.setHintTextColor(Color.rgb(150, 150, 150));
        nameIn.setTextSize(14);
        nameIn.setSingleLine(true);
        if (cur != null) nameIn.setText(cur.title);
        subFormBox.addView(nameIn);

        final EditText minIn = new EditText(c);
        minIn.setHint(Strings.subMinutesHint());
        minIn.setTextColor(Color.WHITE);
        minIn.setHintTextColor(Color.rgb(150, 150, 150));
        minIn.setTextSize(14);
        minIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minIn.setText(cur != null ? String.valueOf(cur.minutes) : "5");
        subFormBox.addView(minIn);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button saveBtn = smallButton(c, Strings.subSave());
        saveBtn.setOnClickListener(v -> {
            String name = nameIn.getText().toString().trim();
            int mins = 5;
            try { mins = Integer.parseInt(minIn.getText().toString().trim()); }
            catch (NumberFormatException ignored) { }
            if (mins <= 0) mins = 5;
            TaskStore store = new TaskStore(c);
            TaskItem real = store.byId(bound.id);
            if (real != null) {
                if (name.isEmpty()) name = Strings.subtasksTitle() + (real.subtasks.size() + 1);
                if (cur == null) {
                    real.subtasks.add(Subtask.create(name, mins));
                } else {
                    for (Subtask s : real.subtasks) {
                        if (s.id.equals(cur.id)) { s.title = name; s.minutes = mins; break; }
                    }
                }
                real.syncDurationFromSubtasks();
                real.touch();
                store.save();
            }
            closeSubForm(c);
        });
        row.addView(saveBtn);
        if (cur != null) {
            Button delBtn = smallButton(c, Strings.subDelete());
            delBtn.setOnClickListener(v -> {
                TaskStore store = new TaskStore(c);
                TaskItem real = store.byId(bound.id);
                if (real != null) {
                    for (int i = 0; i < real.subtasks.size(); i++) {
                        if (real.subtasks.get(i).id.equals(cur.id)) {
                            real.subtasks.remove(i);
                            break;
                        }
                    }
                    real.syncDurationFromSubtasks();
                    real.touch();
                    store.save();
                }
                closeSubForm(c);
            });
            row.addView(delBtn);
        }
        Button cancelBtn = smallButton(c, Strings.btnCancel());
        cancelBtn.setOnClickListener(v -> closeSubForm(c));
        row.addView(cancelBtn);
        subFormBox.addView(row);
        focusInput(c, nameIn);
    }

    private static void closeSubForm(Context c) {
        subEditing = false;
        if (subFormBox != null) {
            subFormBox.removeAllViews();
            subFormBox.setVisibility(View.GONE);
        }
        endEditFocus(c);
        subSignature = ""; // 强制 refresh 时按最新数据重建面板
        refresh(c);
    }

    /** 让任意输入框在悬浮窗里获得焦点并弹键盘（与笔记编辑同一套窗口标志处理）。 */
    private static void focusInput(Context c, EditText target) {
        if (rootView == null || lp == null) return;
        editing = true;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
        try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
        target.requestFocus();
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) ui.postDelayed(() -> imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT), 80);
    }

    /** 拖动：三模式共用。EDIT 只移动；MINI 点按=进编辑、拖到边缘=吸附；DOCK 点按/拖离=展开。 */
    private static class Dragger implements View.OnTouchListener {
        private final Context c;
        private int startX, startY;
        private float touchX, touchY;
        private boolean moved;

        Dragger(Context c) { this.c = c; }

        @Override public boolean onTouch(View v, MotionEvent e) {
            if (lp == null || rootView == null) return false;
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = lp.x; startY = lp.y;
                    touchX = e.getRawX(); touchY = e.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (e.getRawX() - touchX);
                    int dy = (int) (e.getRawY() - touchY);
                    if (Math.abs(dx) + Math.abs(dy) > 6) moved = true;
                    lp.x = startX + dx;
                    lp.y = startY + dy;
                    try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
                    return true;
                case MotionEvent.ACTION_UP:
                    onUp(lp.x - startX);
                    return true;
                default:
                    return false;
            }
        }

        private void onUp(int dx) {
            int m = mode(c);
            float d = c.getResources().getDisplayMetrics().density;
            int screenW = c.getResources().getDisplayMetrics().widthPixels;
            int viewW = rootView.getWidth();
            if (m == MODE_DOCK) {
                int side = dockSide(c);
                boolean inward = side == 1 ? dx < -(int) (60 * d) : dx > (int) (60 * d);
                if (!moved) expandFromDock(c);
                else if (inward) setMode(c, MODE_MINI);
                else {
                    lp.x = dockX(c);
                    try { wm.updateViewLayout(rootView, lp); } catch (Exception ignored) { }
                }
            } else if (m == MODE_MINI) {
                if (!moved) { openEdit(c); return; }
                if (lp.x <= (int) (18 * d)) {
                    setDockSide(c, 0);
                    setMode(c, MODE_DOCK);
                } else if (lp.x + viewW >= screenW - (int) (18 * d)) {
                    setDockSide(c, 1);
                    setMode(c, MODE_DOCK);
                }
            }
        }

        private void expandFromDock(Context c) {
            float d = c.getResources().getDisplayMetrics().density;
            int screenW = c.getResources().getDisplayMetrics().widthPixels;
            if (dockSide(c) == 1) lp.x = screenW - (int) (262 * d) - (int) (10 * d);
            else lp.x = (int) (10 * d);
            setMode(c, MODE_MINI);
        }
    }

    // ---------- 小件 ----------
    private static TextView smallLabel(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(Color.rgb(176, 176, 176));
        t.setTextSize(12);
        t.setPadding(dp(c, 6), dp(c, 4), dp(c, 2), dp(c, 4));
        return t;
    }

    private static Button smallButton(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setTextSize(13);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, (int) (40 * c.getResources().getDisplayMetrics().density), 1f);
        p.setMargins(dp(c, 2), dp(c, 6), dp(c, 2), 0);
        b.setLayoutParams(p);
        return b;
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
