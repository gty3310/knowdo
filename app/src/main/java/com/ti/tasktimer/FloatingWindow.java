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
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
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
    public static final int ST_FINDING = 4; // 「找下一个任务」3 分钟倒计时（不是任务）

    private static WindowManager wm;
    private static WindowManager.LayoutParams lp;
    private static LinearLayout rootView;
    private static LinearLayout bodyBox;
    private static LinearLayout miniBox;
    private static TextView dockView;
    private static EditText titleView;
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
    private static LinearLayout lockBox;
    private static LinearLayout overlayBox;
    private static boolean overlayShowing = false;
    private static TextView lockTitle;
    private static TextView lockBanner;
    private static TextView lockTime;
    private static TextView lockState;
    private static TextView lockUsed;
    private static TextView lockBody;
    private static boolean lockShowing = false;
    private static int savedFlags, savedWidth, savedHeight, savedX, savedY, savedGravity;
    private static boolean savedLp = false;
    private static SubtaskPanel subPanel;
    private static LinearLayout subFormBox;
    private static String subSignature = "";
    private static boolean subEditing = false;
    // 暂停卡「正当开启」的豁免自 v1.97.0 起由 PauseCard.openSource() 登记
    // 承载（卡自身状态），不再在本类记私有标记——旧标记只有悬浮窗两个入口
    // 会置位，PauseLock 遮罩入口漏置导致卡被每秒节拍关掉（闪烁 bug）
    private static EditText noteEdit;
    private static boolean editing = false;
    private static boolean settingText = false;
    private static boolean suppressed = false;
    private static boolean layoutFailed = false; // addView/updateViewLayout 失败或视图已脱离窗口
    private static long builtAt = 0L; // 上次 addView 成功的时刻：刚加上的视图要等下一帧才 attach，宽限期内不当成被卸
    private static boolean noteDirty = false;
    private static boolean titleDirty = false;
    private static String cachedTaskId = null;
    private static String boundNoteText = null; // 笔记框上次与数据源对齐的文字（v1.6.2 同步修复）
    private static String boundTitleText = null;
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
        // 找下一件倒计时优先于「列表第一要务」：显示「找下一个任务 02:41」，不挂任何任务
        if (FindNextTimer.isActive(c)) {
            d.state = ST_FINDING;
            d.title = Strings.findNextTitle();
            d.remainMs = FindNextTimer.remainMs(c);
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
        if (d.state == ST_FINDING) return Strings.findNextSummary(fmt(d.remainMs));
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
        if (lockShowing) { applyLockLayout(c); return; }
        if (overlayShowing) { applyBigLayout(c, overlayBox); return; }
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
        updateLayout();
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

    /** 守卫/封禁/呼吸页等在前台时让位；离开前台后由 unsuppressAndShow 立刻恢复。 */
    public static void setSuppressed(boolean s) {
        suppressed = s;
        if (s) hideInternal();
    }

    /** 让位结束：清抑制并立刻重建（页 onPause/onDestroy 调用，不等下一秒节拍）。 */
    public static void unsuppressAndShow(Context c) {
        setSuppressed(false);
        if (c != null) ensureShown(c);
    }

    // ---------- 显示/隐藏/刷新 ----------
    public static void ensureShown(Context c) {
        if (suppressed) return;
        if (!enabled(c) && !PauseLock.isDue(c)) return;
        // 已到期的 99% 遮罩优先于 snooze：不能靠关窗躲开
        if (FloatSnooze.isActive(c) && !PauseLock.isDue(c)) { hide(); return; }
        if (!AlertHelper.canDrawOverlays(c)) return;
        // 看门狗：系统把 overlay 卸掉后 rootView 可能仍非空，不重建就会「永远消失」
        if (rootView != null && (layoutFailed || (!rootView.isAttachedToWindow()
                && System.currentTimeMillis() - builtAt > 3_000L))) {
            hideInternal();
        }
        if (rootView == null) build(c.getApplicationContext());
        if (PauseLock.isDue(c) && !GuardDispatch.inCall(c)) showLock(c);
    }

    /** 更新已添加的悬浮窗布局；失败则打标，下一拍 ensureShown 会拆掉重建。 */
    private static void updateLayout() {
        if (wm == null || rootView == null || lp == null) return;
        try {
            wm.updateViewLayout(rootView, lp);
            layoutFailed = false;
        } catch (Exception e) {
            layoutFailed = true;
        }
    }

    /** TimerService 的旧入口：统一走 refresh 渲染。 */
    public static void update(Context c, String remainText) {
        refresh(c);
    }

    public static void refresh(Context c) {
        if (rootView == null) return;
        // 暂停卡 overlay 的收尾：没有「正当开启」登记（PauseCard.openSource）
        // 的卡、且已不在计时中时，状态已变就关掉它。有登记的卡（悬浮窗「换个
        // 目的」、PauseLock 遮罩「先休息一会儿」）由其所属流程自己关——v1.97.0
        // 前登记只存在本类的私有标记里、遮罩入口漏置，卡被这里每秒关掉（闪烁）
        if (PauseCard.overlayOpen() && !TimerState.isRunning(c)
                && PauseCard.openSource() == null) PauseCard.closeOverlay();
        Display d = resolveDisplay(c);
        displayTaskId = d.task != null ? d.task.id : null;
        String timeText;
        if (d.state == ST_RUNNING || d.state == ST_PAUSED || d.state == ST_FINDING) timeText = fmt(d.remainMs);
        else if (d.state == ST_IDLE) timeText = d.task.durationMinutes + "分";
        else timeText = "--:--";
        boolean subRun = d.state == ST_RUNNING && d.task != null
                && d.task.id.equals(TimerState.taskId(c)) && TimerState.subActive(c);
        if (titleView != null && timeView != null) {
            // 用户正在改任务名时不要用 refresh 冲掉输入
            if (!(titleView.hasFocus() || titleDirty) || subRun) {
                settingText = true;
                if (subRun) {
                    titleView.setText(TimerState.subTitle(c));
                    titleView.setEnabled(false);
                    titleView.setFocusable(false);
                    titleView.setFocusableInTouchMode(false);
                    timeView.setText(fmt(Math.max(0, TimerState.subEndAt(c) - System.currentTimeMillis())));
                    if (headerSubView != null) {
                        headerSubView.setText(d.title);
                        headerSubView.setVisibility(View.VISIBLE);
                    }
                } else {
                    titleView.setEnabled(true);
                    titleView.setFocusable(true);
                    titleView.setFocusableInTouchMode(true);
                    titleView.setText(d.title);
                    boundTitleText = d.title == null ? "" : d.title;
                    if (headerSubView != null) headerSubView.setVisibility(View.GONE);
                }
                settingText = false;
            }
            if (!subRun && timeView != null) timeView.setText(timeText);
        } else if (timeView != null) {
            timeView.setText(timeText);
        }
        // MINI 两行呈现（v1.11.0）
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
        // 头部主次（计时中有当前子任务）已在上面与标题刷新一并处理。
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
        if (miniPlay != null) {
            miniPlay.setText(d.state == ST_RUNNING ? "⏸" : "▶");
            miniPlay.setVisibility(d.state == ST_FINDING ? View.GONE : View.VISIBLE);
        }
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
        if (aiBtnRef != null) aiBtnRef.setVisibility(
                d.state == ST_PAUSED || d.state == ST_FINDING ? View.GONE : View.VISIBLE);
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
                    : d.state == ST_FINDING ? Strings.findNextHint()
                    : d.state == ST_IDLE ? "第一要务，点 ▶ 开始" : "今天的任务都完成了");
        }
        // EDIT 卡时间行右侧（v1.6.1）：已用 / 计划总。
        // 已用 = SessionLog 全部段累计 + 正在跑的这一段（口径见 SessionLog.totalMsForTask）；
        // 每日重复任务的已用改只算今天（口径见 SessionLog.todayMsForTask，v1.65.0，
        // 正在跑的这一段如何计入沿用该方法的原有处理）；
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
                long usedMs = d.task.isRecurring()
                        ? SessionLog.todayMsForTask(c, d.task.id)
                        : SessionLog.totalMsForTask(c, d.task.id);
                usedView.setText(fmt(usedMs) + " / " + fmt(totalMs));
            }
        }
        if (lockShowing && lockBox != null && lockBox.getVisibility() == View.VISIBLE) {
            if (lockTitle != null) lockTitle.setText(d.title);
            if (lockBanner != null) lockBanner.setText(Strings.pauseLockBanner());
            if (lockTime != null) lockTime.setText(timeText);
            if (lockState != null) lockState.setText(d.state == ST_PAUSED
                    ? (Strings.en() ? "Paused" : "已暂停") : "");
            if (lockBody != null) lockBody.setText(Strings.pauseLockBody());
            if (lockUsed != null) lockUsed.setText(usedView != null ? usedView.getText() : "");
        }
        if (playPauseBtn != null) {
            playPauseBtn.setText(d.state == ST_RUNNING ? "⏸ 暂停"
                    : d.state == ST_PAUSED ? "▶ 继续" : "▶ 开始");
            playPauseBtn.setEnabled(d.state != ST_NONE && d.state != ST_FINDING);
            playPauseBtn.setVisibility(d.state == ST_FINDING ? View.GONE : View.VISIBLE);
        }
        if (doneBtn != null) doneBtn.setVisibility(d.state == ST_RUNNING ? View.VISIBLE : View.GONE);
        if (dockView != null) {
            long mins;
            if (d.state == ST_RUNNING || d.state == ST_PAUSED || d.state == ST_FINDING)
                mins = Math.max(1, (d.remainMs + 59_999) / 60_000);
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
        // 有「正当开启」登记的暂停卡归其所属流程管，悬浮窗拆建不顺手关它
        if (PauseCard.openSource() == null) PauseCard.closeOverlay();
        ChatOverlay.hide(); // 聊天窗是小窗的附属窗，小窗拆了它不独存（v2.2.0）
        subSignature = "";
        subEditing = false;
        if (pendingSave != null) { ui.removeCallbacks(pendingSave); pendingSave = null; }
        if (storeListener != null) {
            TaskStore.removeChangeListener(storeListener);
            storeListener = null;
        }
        lockShowing = false;
        overlayShowing = false;
        overlayBox = null;
        savedLp = false;
        if (wm != null && rootView != null) {
            try { wm.removeView(rootView); } catch (Exception ignored) { }
        }
        rootView = null;
        editing = false;
        noteDirty = false;
        titleDirty = false;
        cachedTaskId = null;
        boundNoteText = null;
        boundTitleText = null;
        displayTaskId = null;
    }

    // ---------- 开始 / 暂停 / 继续 ----------
    private static void onPlayPause(Context c) {
        Display d = resolveDisplay(c);
        if (d.state == ST_FINDING) return; // 不是任务，不能暂停或当作第一要务开始
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
        // 「聊一聊」钮（v2.2.0）：朱砂红小 pill，在小窗上一眼找得到；点它开/收
        // 悬浮窗聊天（ChatOverlay），聊天窗是独立窗口，不占小窗自己的形态
        TextView chatBtn = new TextView(c);
        chatBtn.setText(Strings.chatTalkBtn());
        chatBtn.setTextColor(Color.WHITE);
        chatBtn.setTextSize(11.5f);
        GradientDrawable chatBg = new GradientDrawable();
        chatBg.setColor(Color.rgb(217, 75, 58)); // 朱砂红 #D94B3A
        chatBg.setCornerRadius(dp(c, 9));
        chatBtn.setBackground(chatBg);
        chatBtn.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        LinearLayout.LayoutParams chatLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chatLp.leftMargin = dp(c, 6);
        chatBtn.setLayoutParams(chatLp);
        chatBtn.setOnClickListener(v -> ChatOverlay.toggle(c));
        miniRow.addView(chatBtn);
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

        titleView = new EditText(c);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setMaxLines(2);
        titleView.setBackgroundColor(Color.TRANSPARENT);
        titleView.setPadding(0, dp(c, 2), 0, dp(c, 2));
        titleView.setHint(Strings.en() ? "Task name" : "任务名");
        titleView.setHintTextColor(Color.rgb(150, 150, 150));
        titleView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        titleView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        titleView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN && titleView.isEnabled()) {
                beginEditFocus(c, titleView);
            }
            return false;
        });
        titleView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void afterTextChanged(Editable s) {
                if (settingText || !titleView.isEnabled()) return;
                titleDirty = true;
            }
        });
        titleView.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && ev.getAction() == KeyEvent.ACTION_DOWN)) {
                persistTitle(c, true);
                titleView.clearFocus();
                return true;
            }
            return false;
        });
        titleView.setOnFocusChangeListener((v, has) -> {
            if (!has) persistTitle(c, true);
        });
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
            // 检查页开着也不藏悬浮窗（与到点不藏同一口径）
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
            // 没有目的的暂停不能靠 +5 分钟继续挂着，先补目的
            if (TimerState.pauseReason(c) == null) {
                android.widget.Toast.makeText(c, Strings.pauseNeedPurposeFirst(), android.widget.Toast.LENGTH_SHORT).show();
                Display dd = resolveDisplay(c);
                PauseCard.showOverlay(c, dd.title, Strings.pauseSaveBtn(), PauseCard.SRC_REPURPOSE, ch -> {
                    TimerState.setPauseMeta(c, ch.reason, ch.emoji, ch.silent);
                    ResumeReminder.schedule(c, TimerState.pausedTaskId(c), ch.delayMs);
                    SbTimerSync.onLocalTimerChanged(c);
                    PauseLock.onPauseStateChanged(c);
                    refresh(c);
                });
                return;
            }
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
            PauseCard.showOverlay(c, dd.title, Strings.pauseSaveBtn(), PauseCard.SRC_REPURPOSE, ch -> {
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
            Display dd = resolveDisplay(c);
            if (dd.state == ST_IDLE || dd.state == ST_FINDING) MainActivity.openXing(c);
            else {
                Intent i = new Intent(c, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            }
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
            layoutFailed = false;
            builtAt = System.currentTimeMillis();
        } catch (Exception e) {
            layoutFailed = true;
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
        buildLockBox(c);
        buildOverlayBox(c);
        applyMode(c);
        refresh(c);
    }

    /** 通用大窗内容盒（BigOverlay 用）：与 lockBox 并列，同一时刻只显一个。 */
    private static void buildOverlayBox(Context c) {
        overlayBox = new LinearLayout(c);
        overlayBox.setOrientation(LinearLayout.VERTICAL);
        overlayBox.setVisibility(View.GONE);
        overlayBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        rootView.addView(overlayBox);
    }

    /** 进入编辑：展开卡片 + 笔记输入框立刻获得焦点并弹键盘。 */
    private static void openEdit(Context c) {
        setMode(c, MODE_EDIT);
        ui.postDelayed(() -> beginEditFocus(c), 60);
    }

    private static void beginEditFocus(Context c) {
        beginEditFocus(c, noteEdit);
    }

    private static void beginEditFocus(Context c, android.widget.EditText focus) {
        if (rootView == null || focus == null) return;
        if (mode(c) != MODE_EDIT) setMode(c, MODE_EDIT);
        editing = true;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
        updateLayout();
        focus.requestFocus();
        focus.setSelection(focus.getText().length());
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) ui.postDelayed(() -> imm.showSoftInput(focus, InputMethodManager.SHOW_IMPLICIT), 80);
    }

    private static void endEditFocus(Context c) {
        if (!editing) return;
        if (pendingSave != null) { ui.removeCallbacks(pendingSave); pendingSave = null; }
        persistNote(c, true);
        persistTitle(c, true);
        editing = false;
        noteDirty = false;
        titleDirty = false;
        if (lp == null || rootView == null) return;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = 0;
        updateLayout();
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
    }

    private static void persistTitle(Context c, boolean fromEnd) {
        if (titleView == null || displayTaskId == null) return;
        if (!titleView.isEnabled()) { titleDirty = false; return; }
        String v = titleView.getText().toString().trim();
        if (v.isEmpty()) {
            // 不允许空标题：退回已对齐的名字
            settingText = true;
            titleView.setText(boundTitleText == null ? "" : boundTitleText);
            settingText = false;
            titleDirty = false;
            return;
        }
        TaskStore store = new TaskStore(c);
        TaskItem t = store.byId(displayTaskId);
        if (t == null) return;
        if (!v.equals(t.title == null ? "" : t.title)) {
            t.title = v;
            t.touch();
            store.save();
            TimerState.updateTitleIfCurrent(c, t.id, v);
            if (fromEnd) {
                final Context app = c.getApplicationContext();
                new Thread(() -> SbSync.syncNow(app, new TaskStore(app))).start();
            }
        }
        boundTitleText = v;
        titleDirty = false;
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

    /** 让通用大窗（BigOverlay）内容里的输入框获得焦点并弹键盘：与 focusInput 同一套窗口标志处理。 */
    public static void focusOverlayInput(Context c, EditText target) {
        focusInput(c, target);
    }

    /** 通用大窗收回时退出输入态（恢复窗口标志、收键盘）；没进过输入态时是 no-op。 */
    public static void endOverlayInputFocus(Context c) {
        endEditFocus(c);
    }

    /** 让任意输入框在悬浮窗里获得焦点并弹键盘（与笔记编辑同一套窗口标志处理）。 */
    private static void focusInput(Context c, EditText target) {
        if (rootView == null || lp == null) return;
        editing = true;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
        updateLayout();
        target.requestFocus();
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) ui.postDelayed(() -> imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT), 80);
    }

    // ---------- 暂停 30 秒 99% 遮罩 ----------
    public static boolean isLockShowing() { return lockShowing && rootView != null; }

    public static void showLock(Context c) {
        if (c == null) return;
        if (suppressed) return; // 守卫/封禁页在前：等让位后再盖
        if (!AlertHelper.canDrawOverlays(c)) return;
        endEditFocus(c);
        // 有「正当开启」登记的暂停卡不关（v1.97.0）：遮罩自己的「先休息一会儿」
        // 开的卡就活在遮罩在岗期间，此前这里无条件关卡是闪烁的杀手之一
        if (PauseCard.openSource() == null) PauseCard.closeOverlay();
        if (rootView == null) {
            ensureShown(c);
            if (rootView == null) return;
        }
        boolean wasBig = lockShowing || overlayShowing;
        if (overlayShowing) {
            // 暂停遮罩优先级最高：抢占通用大窗（其内容由 BigOverlay 排队，遮罩收回后复出）
            overlayShowing = false;
            if (overlayBox != null) overlayBox.setVisibility(View.GONE);
        }
        if (!lockShowing) {
            if (lp != null && !wasBig) {
                savedFlags = lp.flags;
                savedWidth = lp.width;
                savedHeight = lp.height;
                savedX = lp.x;
                savedY = lp.y;
                savedGravity = lp.gravity;
                savedLp = true;
            }
            lockShowing = true;
        }
        applyLockLayout(c);
        refresh(c);
    }

    public static void retractLock(Context c) {
        if (!lockShowing) return;
        lockShowing = false;
        if (lockBox != null) lockBox.setVisibility(View.GONE);
        restoreSavedLp();
        if (c != null && rootView != null) applyMode(c);
    }

    private static void restoreSavedLp() {
        if (lp != null && savedLp) {
            lp.flags = savedFlags;
            lp.width = savedWidth;
            lp.height = savedHeight;
            lp.x = savedX;
            lp.y = savedY;
            lp.gravity = savedGravity;
            savedLp = false;
        }
    }

    private static void saveCurrentLp() {
        if (lp == null) return;
        savedFlags = lp.flags;
        savedWidth = lp.width;
        savedHeight = lp.height;
        savedX = lp.x;
        savedY = lp.y;
        savedGravity = lp.gravity;
        savedLp = true;
    }

    // ---------- 通用 99% 大窗（BigOverlay 壳的窗口层） ----------
    public static boolean isOverlayShowing() { return overlayShowing && rootView != null; }

    public static boolean isSuppressed() { return suppressed; }

    /**
     * 把 content 装进通用内容盒并进入 99% 大窗模式。暂停遮罩在显、被守卫页
     * 抑制中或无悬浮权限时拒绝（返回 false，调用方排队等下一拍）。
     * 已在通用大窗模式时调用＝换内容（不重存 LayoutParams）。
     */
    public static boolean showOverlay(Context c, View content) {
        if (c == null || content == null) return false;
        if (suppressed) return false;
        if (lockShowing) return false;
        if (!AlertHelper.canDrawOverlays(c)) return false;
        endEditFocus(c);
        PauseCard.closeOverlay();
        if (rootView == null) {
            ensureShown(c);
            if (rootView == null) return false;
        }
        if (lockShowing || overlayBox == null) return false; // ensureShown 可能刚拉起暂停遮罩
        overlayBox.removeAllViews();
        overlayBox.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        if (!overlayShowing) {
            saveCurrentLp();
            overlayShowing = true;
        }
        applyBigLayout(c, overlayBox);
        return true;
    }

    /** 只收视觉（通话让位）：内容留在盒里、窗口恢复普通形态，挂断后 resumeOverlayVisual 复原。 */
    public static void hideOverlayVisual() {
        if (!overlayShowing) return;
        overlayShowing = false;
        if (overlayBox != null) overlayBox.setVisibility(View.GONE);
        restoreSavedLp();
        Context c = appCtx();
        if (c != null && rootView != null) applyMode(c);
    }

    /** 通话结束：把盒里现成的内容重新扩成 99% 大窗。盒是空的（窗口被重建过）返回 false。 */
    public static boolean resumeOverlayVisual(Context c) {
        if (c == null || rootView == null || overlayBox == null) return false;
        if (lockShowing || overlayShowing) return false;
        if (overlayBox.getChildCount() == 0) return false;
        saveCurrentLp();
        overlayShowing = true;
        applyBigLayout(c, overlayBox);
        return true;
    }

    /** 收回通用大窗并清掉内容（与 retractLock 对称；暂停遮罩在显时不动其状态）。 */
    public static void retractOverlay(Context c) {
        if (overlayBox != null) {
            overlayBox.setVisibility(View.GONE);
            overlayBox.removeAllViews();
        }
        if (!overlayShowing) return;
        overlayShowing = false;
        restoreSavedLp();
        if (c != null && rootView != null) applyMode(c);
    }

    private static void applyLockLayout(Context c) {
        applyBigLayout(c, lockBox);
    }

    /** 99% 大窗布局：全屏尺寸＋深色底＋只显指定内容盒，其余盒全藏，触摸/按键不漏到背后。 */
    private static void applyBigLayout(Context c, View box) {
        if (rootView == null || lp == null) return;
        float d = c.getResources().getDisplayMetrics().density;
        int sw = c.getResources().getDisplayMetrics().widthPixels;
        int sh = c.getResources().getDisplayMetrics().heightPixels;
        lp.width = sw;
        lp.height = sh;
        lp.x = 0;
        lp.y = 0;
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        // 不设 FLAG_NOT_FOCUSABLE / FLAG_NOT_TOUCH_MODAL：触摸和按键都不漏到背后
        int pad = Math.max(4, (int) (Math.min(sw, sh) * 0.005f));
        rootView.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(242, 24, 36, 31));
        bg.setCornerRadius(18 * d);
        rootView.setBackground(bg);
        if (dockView != null) dockView.setVisibility(View.GONE);
        if (miniBox != null) miniBox.setVisibility(View.GONE);
        if (bodyBox != null) bodyBox.setVisibility(View.GONE);
        if (lockBox != null) lockBox.setVisibility(box == lockBox ? View.VISIBLE : View.GONE);
        if (overlayBox != null) overlayBox.setVisibility(box == overlayBox ? View.VISIBLE : View.GONE);
        if (noteEdit != null) {
            noteEdit.clearFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(noteEdit.getWindowToken(), 0);
        }
        updateLayout();
    }

    private static void buildLockBox(final Context c) {
        lockBox = new LinearLayout(c);
        lockBox.setOrientation(LinearLayout.VERTICAL);
        lockBox.setVisibility(View.GONE);
        int pad = dp(c, 20);
        lockBox.setPadding(pad, pad, pad, pad);

        TextView tag = new TextView(c);
        tag.setText("知行 · 当前第一要务");
        tag.setTextColor(Color.rgb(176, 176, 176));
        tag.setTextSize(13);
        lockBox.addView(tag);

        lockTitle = new TextView(c);
        lockTitle.setTextColor(Color.WHITE);
        lockTitle.setTextSize(20);
        lockTitle.setTypeface(lockTitle.getTypeface(), android.graphics.Typeface.BOLD);
        lockTitle.setPadding(0, dp(c, 8), 0, dp(c, 10));
        lockBox.addView(lockTitle);

        lockBanner = new TextView(c);
        lockBanner.setTextColor(Color.rgb(247, 164, 148));
        lockBanner.setTextSize(15);
        lockBanner.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(Color.argb(50, 217, 75, 58));
        sb.setCornerRadius(12 * c.getResources().getDisplayMetrics().density);
        sb.setStroke(dp(c, 1), Color.argb(140, 217, 75, 58));
        lockBanner.setBackground(sb);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lockBanner.setLayoutParams(blp);
        lockBox.addView(lockBanner);

        LinearLayout timeRow = new LinearLayout(c);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        lockTime = new TextView(c);
        lockTime.setTextColor(Color.WHITE);
        lockTime.setTextSize(40);
        lockTime.setTypeface(lockTime.getTypeface(), android.graphics.Typeface.BOLD);
        timeRow.addView(lockTime);
        lockState = new TextView(c);
        lockState.setTextColor(Color.rgb(176, 176, 176));
        lockState.setTextSize(14);
        lockState.setPadding(dp(c, 10), 0, 0, 0);
        lockState.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        timeRow.addView(lockState);
        lockUsed = new TextView(c);
        lockUsed.setTextColor(Color.rgb(200, 200, 200));
        lockUsed.setTextSize(14);
        timeRow.addView(lockUsed);
        LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        trlp.topMargin = dp(c, 12);
        timeRow.setLayoutParams(trlp);
        lockBox.addView(timeRow);

        lockBody = new TextView(c);
        lockBody.setTextColor(Color.rgb(190, 200, 195));
        lockBody.setTextSize(15);
        lockBody.setPadding(0, dp(c, 14), 0, dp(c, 12));
        lockBox.addView(lockBody);

        lockBox.addView(lockBtn(c, Strings.pauseLockContinue(), Color.rgb(46, 122, 72),
                v -> PauseLock.chooseContinue(c)));
        lockBox.addView(lockBtn(c, Strings.pauseLockBreak(), Color.rgb(55, 66, 60),
                v -> PauseLock.chooseTimedBreak(c)));
        lockBox.addView(lockBtn(c, Strings.pauseLockDone(), Color.rgb(55, 66, 60),
                v -> PauseLock.chooseMarkDone(c)));
        lockBox.addView(lockBtn(c, Strings.pauseLockSwitch(), Color.rgb(55, 66, 60),
                v -> PauseLock.chooseSwitchTask(c)));

        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        lockBox.setLayoutParams(llp);
        rootView.addView(lockBox);
    }

    private static Button lockBtn(Context c, String label, int color, android.view.View.OnClickListener click) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(14 * c.getResources().getDisplayMetrics().density);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 52));
        lp.topMargin = dp(c, 10);
        b.setLayoutParams(lp);
        b.setOnClickListener(click);
        return b;
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
            if (lockShowing || overlayShowing) return true; // 大窗时不能拖、不能划掉
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
                    updateLayout();
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
                    updateLayout();
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
