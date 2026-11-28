package com.ti.tasktimer;

import android.content.Context;

/**
 * 集中字符串表（v1.8.0）：中/英两套，按 Ui 里的语言设置在每个界面 onCreate 时 init。
 * 覆盖范围：主页、抽屉菜单、设置页、帮助页（全文）、检查页、日历面板标题区与列表标题。
 * 未覆盖的深层弹窗（任务字段编辑、专注规则、日历块操作菜单等）仍为中文，见 v1.8.0 变更记录。
 */
public final class Strings {
    private static boolean en;

    private Strings() { }

    public static void init(Context c) { en = Ui.en(c); }
    public static boolean en() { return en; }
    private static String s(String zh, String enText) { return en ? enText : zh; }

    // ---------------- 通用 / 主页 ----------------
    public static String appWordmark() { return s("知行", "KnowDo"); }
    public static String appFull() { return "知行 KnowDo"; }
    public static String calendar() { return s("日历", "Calendar"); }
    public static String addTask() { return s("新增任务", "New task"); }
    public static String remain() { return s("还剩", "left"); }
    public static String plannedMin(int min) { return en ? min + " min planned" : "计划 " + min + " 分"; }
    public static String pausedTag() { return s("（已暂停）", "(paused)"); }
    public static String skippedTag() { return s("（跳过）", "(skipped)"); }
    public static String fmtMin(int min) { return en ? min + " min" : min + " 分"; }
    public static String fmtDur(int min) {
        if (min >= 60) {
            return en ? (min / 60) + " h " + (min % 60) + " min"
                      : (min / 60) + " 小时 " + (min % 60) + " 分";
        }
        return fmtMin(min);
    }

    // ---------------- 任务表：列 ----------------
    public static String colTask() { return s("任务", "Task"); }
    public static String colStart() { return s("开始时间", "Start"); }
    public static String colUsed() { return s("已用", "Used"); }
    public static String colLeft() { return s("剩余", "Left"); }
    public static String colDur() { return s("时长", "Length"); }
    public static String colDeadline() { return s("Deadline", "Deadline"); }
    public static String colPriority() { return s("优先", "Priority"); }
    public static String colScenarios() { return s("情景", "Context"); }
    public static String columnsTitle() { return s("显示哪些列", "Columns to show"); }
    public static String priMust() { return s("必要", "Must"); }
    public static String priImportant() { return s("重要", "Important"); }
    public static String priSecondary() { return s("次要", "Secondary"); }
    public static String priLeisure() { return s("休息", "Leisure"); }
    public static String scenariosEmpty() { return s("＋情景", "+ Context"); }
    public static String dateDaily() { return s("每日", "Daily"); }
    public static String dateWeekly() { return s("每周", "Weekly"); }
    public static String dateEmpty() { return s("＋日期", "+ Date"); }
    public static String pickWeekdayHint() { return s("请至少选一天", "Pick at least one weekday"); }

    // ---------------- 展开面板 ----------------
    public static String chipStart() { return s("开始", "Start"); }
    public static String chipPause() { return s("暂停", "Pause"); }
    public static String chipResume() { return s("继续", "Resume"); }
    public static String chipEdit() { return s("编辑", "Edit"); }
    public static String chipPlus15() { return s("+15 分", "+15 min"); }
    public static String chipDone() { return s("完成", "Done"); }
    public static String chipUndone() { return s("取消完成", "Undo done"); }
    public static String noNote() { return s("（无笔记）", "(No note)"); }
    public static String metaNotStarted() { return s("还没开始", "Not started yet"); }
    public static String metaStart(String t) { return en ? "Start " + t : "开始 " + t; }
    public static String metaUsed(int min, int segs) {
        return en ? "Used " + min + " min (" + segs + " sessions)"
                  : "已用 " + min + " 分（" + segs + " 段）";
    }
    public static String metaFocus(String sum) { return en ? "Focus: " + sum : "专注：" + sum; }
    public static String editMenuTitle() { return s("编辑任务", "Edit task"); }

    // ---------------- 任务闹钟（v1.9.0） ----------------
    public static String chipAlarm() { return s("闹钟", "Alarm"); }
    public static String alarmTitle() { return s("闹钟", "Alarm"); }
    public static String alarmTimeLabel() { return s("闹钟时间", "Alarm time"); }
    public static String alarmNotSet() { return s("未设置", "Not set"); }
    public static String alarmRingOnEnd() { return s("计时结束时响铃", "Ring when timer ends"); }
    public static String alarmGotIt() { return s("知道了", "Got it"); }
    public static String alarmTimerEnded() { return s("计时结束了", "Time is up"); }
    public static String alarmTimeArrived() { return s("闹钟时间到了", "Alarm time"); }

    // ---------------- 抽屉 ----------------
    public static String notLoggedIn() { return s("未登录", "Not signed in"); }
    public static String loginHint() {
        return s("点此登录 / 注册（云端同步）", "Tap to sign in / sign up (cloud sync)");
    }
    public static String language() { return s("语言", "Language"); }
    public static String appearance() { return s("外观", "Appearance"); }
    public static String light() { return s("亮", "Light"); }
    public static String dark() { return s("暗", "Dark"); }
    public static String help() { return s("帮助", "Help"); }
    public static String settings() { return s("设置", "Settings"); }
    /** 版本行带构建提交短号（v1.13.0 起）：短号由构建脚本从 git 注入 BuildInfo，不许手写。 */
    public static String versionLine() { return "知行 KnowDo v" + BuildInfo.VERSION_NAME + " · " + BuildInfo.GIT_SHORT; }

    // ---------------- 设置页 ----------------
    public static String settingsTitle() { return s("设置", "Settings"); }
    public static String grpAccount() { return s("账号", "Account"); }
    public static String grpDisplay() { return s("显示", "Display"); }
    public static String grpGuards() { return s("守卫", "Guards"); }
    public static String grpGeneral() { return s("通用", "General"); }
    public static String rowAccount() { return s("账号（登录 / 注册 / 退出）", "Account (sign in / up / out)"); }
    public static String rowSyncNow() { return s("立即同步", "Sync now"); }
    public static String rowRealtime() { return s("实时同步", "Realtime sync"); }
    public static String rowColumns() { return s("显示哪些列", "Columns to show"); }
    public static String rowAppearance() { return s("外观", "Appearance"); }
    public static String rowLanguage() { return s("语言", "Language"); }
    public static String themeFollow() { return s("跟随系统", "System"); }
    public static String themeLight() { return s("亮", "Light"); }
    public static String themeDark() { return s("暗", "Dark"); }
    public static String langFollow() { return s("跟随系统", "System"); }
    public static String langZh() { return "中文"; }
    public static String langEn() { return "English"; }
    // ---------------- 任务衔接守卫 + 暂停菜单（v1.10.0） ----------------
    public static String nextTitle() { return s("接下来干什么？", "What's next?"); }
    public static String nextNameHint() { return s("任务名称", "Task name"); }
    public static String nextRest() { return s("休息", "Rest"); }
    public static String nextDayUnit() { return s("天", "Days"); }
    public static String nextHourUnit() { return s("小时", "Hours"); }
    public static String nextMinuteUnit() { return s("分钟", "Minutes"); }
    public static String nextTotal(int totalMin, String endsAt) {
        return en ? "Total " + totalMin + " min · ends at " + endsAt
                  : "合计 " + totalMin + " 分钟 · 到 " + endsAt + " 结束";
    }
    public static String nextStart() { return s("开始", "Start"); }
    public static String nextTodayTitle() { return s("今日未完成（点一下直接开始）", "Not done today (tap to start)"); }
    public static String nextNoneToday() { return s("（今天没有未完成的任务）", "(Nothing left today)"); }
    public static String findNextTitle() { return s("找下一个任务", "Find next task"); }
    public static String findNextSummary(String remain) {
        return s("找下一个任务 " + remain, "Find next task " + remain);
    }
    public static String findNextHint() { return s("正在找下一件", "Finding the next task"); }
    public static String rowNextGuard() { return s("任务衔接守卫", "Next-task guard"); }
    public static String rowNextGuardSub() {
        return s("没有任务在计时时，全屏弹出「下一个任务」页",
                "When nothing is timing, a full-screen page asks what's next");
    }
    public static String pauseAskTitle(String title) {
        return en ? "Pause \"" + title + "\" — remind me in…"
                  : "暂停「" + title + "」\n多久之后提醒你回来？";
    }
    public static String pauseCustom() { return s("自定义…", "Custom…"); }
    public static String pauseCustomTitle() { return s("自定义提醒时间", "Custom remind time"); }

    // ---------- 暂停模式（v1.11.0）：暂停卡 ----------
    public static String pauseCardTitle(String title) {
        return en ? "Pause \"" + title + "\" — what are you pausing for?"
                  : "暂停「" + title + "」，去干什么？";
    }
    /** 暂停目的预设名，与 PauseCard.PURPOSE_EMOJIS 一一对应。 */
    public static String[] pausePurposeNames() {
        return en ? new String[]{"Restroom", "Water", "Eat", "Call back", "Go help",
                "Nap", "Go run", "Class", "Custom"}
                  : new String[]{"上厕所", "喝水", "吃饭", "回电话", "喊去帮忙",
                "睡午觉", "换换脑子", "去上课", "自定义"};
    }
    public static String pausePurposeCustomDefault() { return s("自定义", "Custom"); }
    public static String pausePurposeHint() { return s("写清去做什么、怎么做，守卫按这段话判", "Say what you're doing and how — the guard judges by this"); }
    public static String pauseDurationLabel() { return s("去多久", "For how long"); }
    public static String pauseSilent() { return s("静音", "Silent"); }
    public static String pauseSilentSub() {
        return s("到点只亮屏提醒，不震动、不出声 · 点「去上课」自动打开",
                "When time's up: screen lights only, no vibration, no sound · turns on automatically for Class");
    }
    public static String pauseStartBtn() { return s("开始暂停", "Start pause"); }
    public static String pauseSaveBtn() { return s("保存", "Save"); }
    public static String pausePreview(String emoji, String name, String dur, String backAt, boolean silent) {
        return en ? emoji + " " + name + " · " + dur + " · back at " + backAt + (silent ? " · 🔕 silent" : "")
                  : emoji + " " + name + " · " + dur + "，到 " + backAt + " 回来" + (silent ? " · 🔕 静音" : "");
    }
    public static String pausePreviewEmpty() {
        return s("先选去干什么、去多久", "Pick what you're pausing for, and for how long");
    }
    public static String pauseStrip(String emoji, String name, String remain) {
        return en ? "⏸ Paused · " + emoji + name + " · " + remain + " left"
                  : "⏸ 暂停中 · " + emoji + name + " · 还剩 " + remain;
    }
    public static String pauseStripNoRemind(String emoji, String name) {
        return en ? "⏸ Paused · " + emoji + name : "⏸ 暂停中 · " + emoji + name;
    }
    public static String miniPausedTitle(String title) {
        return en ? "⏸ Paused \"" + title + "\"" : "⏸ 暂停「" + title + "」";
    }
    public static String pauseLeftLabel() { return s("还剩", "left"); }
    public static String pauseMore5() { return s("再加 5 分钟", "+5 min"); }
    public static String pauseChangePurpose() { return s("换个目的", "Change purpose"); }

    // ---------- 悬浮窗聊天（v2.2.0） ----------
    public static String chatTalkBtn() { return s("聊一聊", "Chat"); }
    public static String chatOverlayTitle() { return s("和 AI 聊聊", "Chat with AI"); }
    public static String chatOverlayClose() { return s("收起", "Close"); }
    public static String chatResizeHint() { return s("拖动右下角可以改大小", "Drag the corner to resize"); }

    // ---------- 暂停 30 秒无下一步遮罩 ----------
    public static String pauseLockBanner() {
        return s("已暂停，请选下一步", "Still paused — choose a next step");
    }
    public static String pauseLockBody() {
        return s("这个任务暂停后 30 秒没有下一步。这扇悬浮窗现在盖住约 99% 的屏幕，不能划掉、也不能关掉。背后的应用没法用，选一项才能继续",
                "This task has been paused for 30 seconds, and nothing else happened. This floating window now covers about 99% of the screen. It can't be swiped away or closed. The app behind it can't be used until you pick a next step");
    }
    public static String pauseLockContinue() { return s("▶  继续这个任务", "▶  Continue this task"); }
    public static String pauseLockBreak() { return s("先休息一会儿…", "Take a timed break…"); }
    public static String pauseLockDone() { return s("完成", "完成   Mark done"); }
    public static String pauseLockSwitch() {
        return s("换任务  ·  去「行」里选下一件", "Switch task  ·  去「行」里选下一件");
    }
    public static String pauseLockNotifTitle() { return s("该选下一步了", "Choose a next step"); }
    public static String pauseLockNotifText(String title) {
        return en ? "\"" + title + "\" is still paused"
                  : "「" + title + "」还停在暂停里";
    }
    public static String pauseLockTaskGone() { return s("这个任务已经不在了", "This task is gone"); }
    public static String pauseNeedPurposeFirst() {
        return s("先选暂停目的，再加时间", "Pick a purpose before adding time");
    }

    // ---------- 子任务（v1.11.0） ----------
    public static String subtasksTitle() { return s("子任务", "Subtasks"); }
    public static String subAdd() { return s("＋ 加子任务", "＋ Add subtask"); }
    public static String subEditNew() { return s("新增子任务", "New subtask"); }
    public static String subEditTitle() { return s("编辑子任务", "Edit subtask"); }
    public static String subNameHint() { return s("子任务名称", "Subtask name"); }
    public static String subMinutesHint() { return s("分钟", "Minutes"); }
    public static String subDelete() { return s("删除", "Delete"); }
    public static String subDeleteConfirm() { return s("删除这个子任务？", "Delete this subtask?"); }
    public static String subSave() { return s("保存", "Save"); }
    public static String subProgress(int cur, int total, String name) {
        return en ? "Subtask " + cur + "/" + total + " · " + name
                  : "子任务 " + cur + "/" + total + " · " + name;
    }
    public static String subTotalNote(int totalMin) {
        return en ? "Total = sum of subtasks: " + totalMin + " min"
                  : "总时长＝子任务之和：" + totalMin + " 分钟";
    }
    public static String subDurationLocked() {
        return s("有子任务时总时长＝子任务之和，在子任务里改分钟即可",
                "With subtasks, the total is their sum — edit subtask minutes instead");
    }
    public static String subCheckTitle() { return s("✓ 子任务时间到", "✓ Subtask done"); }
    public static String subCheckSub(String task, int cur, int total, String name) {
        return en ? task + " · subtask " + cur + "/" + total + " \"" + name + "\" — done?"
                  : task + " · 子任务 " + cur + "/" + total + "「" + name + "」做完了吗？";
    }
    public static String subNext() { return s("下一个", "Next"); }
    public static String subExtend() { return s("加时", "Extend"); }
    public static String subSkip() { return s("跳过", "Skip"); }
    public static String subWholeDone() { return s("整个任务完成", "Finish whole task"); }
    public static String miniSubCount(int n) {
        return en ? n + " subtasks" : "子任务共 " + n + " 个";
    }
    public static String btnOk() { return s("好", "OK"); }
    public static String btnCancel() { return s("取消", "Cancel"); }

    public static String rowFocusGuard() { return s("专注守卫", "Focus guard"); }
    public static String rowFocusGuardSub() {
        return en ? "Set per task via Edit → Focus rule in the expanded panel; "
                + "it checks the foreground app using usage access"
                  : "逐任务在展开面板的「编辑 → 专注规则」里设置；靠使用情况访问判断前台应用";
    }
    public static String rowScreenGuard() { return s("屏幕内容守卫", "Screen content guard"); }
    public static String rowNsfwOneShot() { return s("黄色类一张即触发", "NSFW: trigger on first hit"); }
    public static String rowPerms() { return s("权限体检", "Permissions check"); }
    public static String rowFloatWin() { return s("悬浮窗", "Floating window"); }
    public static String rowLog() { return s("记录", "Log"); }
    public static String rowAbout() { return s("关于", "About"); }
    public static String aboutBody() {
        return en ? "KnowDo v1.21.1\nThe Unity of Knowledge and Action.\n"
                + "Open source: github.com/gty3310/knowdo (CC BY-NC-SA 4.0)"
                  : "知行 KnowDo v1.21.1\n知行合一。\n"
                + "开源：github.com/gty3310/knowdo（CC BY-NC-SA 4.0）";
    }
    public static String focusReady() { return s("使用情况访问：已开通", "Usage access: granted"); }
    public static String focusNotReady() { return s("使用情况访问：未开通（去权限页开通）", "Usage access: not granted (open Permissions)"); }

    // ---------------- 检查页 ----------------
    public static String chkHalfTitle() { return s("你的时间过了一半了", "Halfway there"); }
    public static String chkHalfSub(String title) {
        return en ? "Task: " + title + "\nCan you finish in the time left?"
                  : "任务：" + title + "\n剩下的时间还够你做完吗？";
    }
    public static String chkCan() { return s("能做完", "Yes, I can"); }
    public static String chkCannot() { return s("做不完", "Not enough time"); }
    public static String chkHowTitle() { return s("我如何做才能做完？", "How can I still finish?"); }
    public static String chkHowSub() {
        return en ? "Write down your plan; the timer keeps running."
                  : "写下你的办法，写完继续计时（计时不会暂停）。";
    }
    public static String chkHowHint() {
        return en ? "e.g. Skip the details, finish the main part first"
                  : "比如：先跳过细节，先把主体做完";
    }
    public static String chkGo() { return s("想好了，继续", "Got it, keep going"); }
    public static String chkEndTitle() { return s("时间到了", "Time's up"); }
    public static String chkEndSub(String title) {
        return en ? "Let's check: is \"" + title + "\" done?"
                  : "我们看一下：「" + title + "」做完了吗？";
    }
    public static String chkDone() { return s("做完了，做下一个任务", "Done — next task"); }
    public static String chkExtendLabel() { return s("没做完？加一点时间：", "Not done? Add some time:"); }
    public static String chkPlus50(int mins) { return en ? "+50% (+" + mins + " min)" : "+50%（+" + mins + " 分钟）"; }
    public static String chkPlus5() { return s("+5 分钟", "+5 min"); }
    public static String chkPlus10() { return s("+10 分钟", "+10 min"); }
    public static String chkPlus15() { return s("+15 分钟", "+15 min"); }
    public static String chkPlus1() { return s("+1 分钟", "+1 min"); }
    public static String chkPlus3() { return s("+3 分钟", "+3 min"); }
    public static String chkHalfAddLabel() { return s("加时间：", "Add time:"); }
    public static String chkAddedTotal(int mins) { return en ? "Added " + mins + " min in total" : "已加 " + mins + " 分钟"; }
    public static String chkCustomHint() { return s("或输入想加的分钟数", "Or type minutes to add"); }
    public static String chkCustomBtn() { return s("按输入加时", "Add typed minutes"); }

    // ---------------- 日历面板（标题区与列表标题） ----------------
    public static String calTitle(String dateStr) { return en ? "Today · " + dateStr : "今日日程 · " + dateStr; }
    public static String calOverview(int done, int todo, int freeMin) {
        return en ? "Done " + done + " · To do " + todo + " · " + freeMin + " min free until 22:30"
                  : "今日已完成 " + done + " 件 · 未完成 " + todo + " 件 · 还可自由安排 "
                + freeMin + " 分钟（到 22:30）";
    }
    public static String calDonePrefix() { return s("已完成：", "Done: "); }
    public static String calLaneGcal() { return s("日历", "Calendar"); }
    public static String calLanePlan() { return s("计划", "Plan"); }
    public static String calLaneActual() { return s("实际", "Actual"); }
    public static String calAskAi() {
        return s("AI 重排：重新排今天剩下的日程",
                "AI Re-plan: re-plan the rest of today");
    }
    public static String calNoPlanHint() {
        return en ? "No schedule yet today: tap AI Re-plan above, or let the local auto-planner fill in your must-dos — once planned, it shows up here."
                  : "今天还没有日程：点上面的「AI 重排」，或等本地自动排程把必做填进来，排好后这里就会有计划块。";
    }
    public static String calUnscheduledTitle() { return s("未排入日程的任务", "Not scheduled yet"); }
    public static String calTodayTasksTitle() { return s("今天的任务（还没排日程）", "Today's tasks (no schedule yet)"); }
    public static String calFocusBack() { return s("‹ 回到清单", "‹ Back to list"); }
    public static String calPlannedChip(String hhmm) { return s("已排 " + hhmm, "Planned " + hhmm); }
    public static String calEmptyAllDone() { return s("（今天没有未完成的任务）", "(Nothing left today)"); }
    public static String calEmptyAllScheduled() { return s("（未完成的任务都已排入日程）", "(Everything is scheduled)"); }
    public static String calGcalTitle() { return s("今天的 Google 日历日程", "Google Calendar today"); }
    public static String calActualTitle() { return s("今天实际投入（计时记录）", "Actual time today (timer log)"); }
    public static String calRunningTag() { return s("（正在计时）", "(timing)"); }
    public static String calNowTag() { return s("现在", "Now"); }
    public static String calLockTag() { return s("锁时", "Locked"); }
    public static String calSleepTag() { return s("睡觉", "Sleep"); }
    public static String calMustTag() { return s(" · 必做", " · must"); }
    public static String calPlannedFmt(int mins, boolean must) {
        return en ? "(planned " + mins + " min" + (must ? " · must)" : ")")
                  : "（计划 " + mins + " 分钟" + (must ? " · 必做）" : "）");
    }

    // ---------------- 立志常驻 + 磨练自检（v1.12.0） ----------------
    public static String grpAspiration() { return s("我的志", "My aspiration"); }
    public static String aspRowHint() {
        return s("分神被抓、时间到了的时候，这句话会出现在页面上",
                "This line appears on the drift and check pages");
    }
    public static String aspEmpty() { return s("（还没写，点这里写一句）", "(Not written yet — tap to write)"); }
    public static String aspEditTitle() { return s("我的志", "My aspiration"); }
    public static String aspEditHint() { return s("写下你立志的那句话", "Write the line you aspire to live by"); }
    public static String aspSaved() { return s("已保存", "Saved"); }
    public static String scDidRightQ() { return s("刚才在做该做的事吗？", "Were you doing what you should?"); }
    public static String scDid() { return s("做到了", "Yes, I was"); }
    public static String scNot() { return s("没有", "No"); }

    // ---------------- 分神封软件（v1.80.0） ----------------
    public static String lastLookQ() { return s("最后再看多久", "A last look — how long?"); }
    public static String graceBlockNow() { return s("马上封", "Block now"); }
    public static String graceLook1() { return s("再看 1 分钟", "1 more minute"); }
    public static String graceLook3() { return s("再看 3 分钟", "3 more minutes"); }
    public static String graceLook5() { return s("再看 5 分钟", "5 more minutes"); }
    public static String graceActionQ() { return s("宽限到点后，这个软件：", "When the grace runs out, this app:"); }
    public static String graceActQuit() { return s("关掉软件", "Close this app"); }
    public static String graceActBg() { return s("放到后台", "To background"); }
    public static String graceActBlock() { return s("暂停软件 3 分钟", "Pause this app for 3 minutes"); }
    public static String blockTitle(String app) { return s(app + " 先歇一会儿", app + " is taking a break"); }
    public static String blockTaskLine(String t) { return s("当前任务：「" + t + "」", "Current task: \u201c" + t + "\u201d"); }
    public static String backToTask() { return s("回到任务", "Back to the task"); }
    public static String setGraceAction() { return s("宽限到点后", "When the grace runs out"); }
    public static String guardSwitchTask() { return s("切换任务", "Switch task"); }
    public static String setBlockLen() { return s("暂停时长 3 分钟", "Pause length: 3 minutes"); }
    public static String setUnblock() { return s("提前解封", "Unblock early"); }
    public static String unblockBtn() { return s("解封", "Unblock"); }
    public static String unblockUsed() { return s("今天已解封过", "Already used today"); }
    public static String unblockedToast() { return s("已解封", "Unblocked"); }

    // ---------------- 深呼吸门（v1.83.0） ----------------
    public static String breathIn() { return s("吸气", "Breathe in"); }
    public static String breathOut() { return s("呼气", "Breathe out"); }

    // ---------------- 困难档误判判定（v1.83.0） ----------------
    public static String hardExplainHint() { return s("写一句为什么这是误判", "One line on why this is a false alarm"); }
    public static String hardJudgeSubmit() { return s("提交", "Submit"); }
    public static String hardJudging() { return s("判定中", "Checking"); }
    public static String hardJudgeRefused() { return s("未通过", "Not accepted"); }
    public static String addToSystemPrompt() { return s("加到 system prompt 里", "Add to the system prompt"); }
    public static String explainTooThin() { return s("请用一句话说明为什么这是误判", "Please write one sentence explaining the false alarm"); }
    public static String explainRejected() { return s("这个解释不够清楚，请再写一句", "That explanation is not clear enough — please write another sentence"); }
    public static String aiUnavailableWait(int sec) {
        return s("AI 暂时不可用，" + sec + " 秒后可跳过",
                "AI is unavailable. You can skip in " + sec + "s");
    }
    public static String aiUnavailableSkipHint() { return s("AI 不可用，可以跳过这次解释", "AI is unavailable — you can skip this explanation"); }
    public static String skipAiUnavailable() { return s("跳过（AI 不可用）", "Skip (AI unavailable)"); }
    public static String taskDescriptionLabel() { return s("任务说明", "Task description"); }
    public static String taskDescriptionHint() { return s("给守卫看的背景说明，与笔记分开", "Background for the guard, separate from the note"); }
    public static String overturnThis() { return s("这是误判", "False alarm"); }

    // ---------------- 设置页两级（v1.83.0） ----------------
    public static String settingsBack() { return s("‹ 设置", "‹ Settings"); }
    public static String setGuardDifficulty() { return s("守卫难度", "Guard difficulty"); }
    public static String diffEasy() { return s("简单", "Easy"); }
    public static String diffMedium() { return s("中等", "Medium"); }
    public static String diffHard() { return s("困难", "Hard"); }
    public static String setHardQuota() { return s("每日宽限次数", "Daily grace limit"); }
    public static String setHardQuotaSub() { return s("只对困难难度生效", "Applies to Hard only"); }
    public static String setGrpJudge() { return s("判定与名单", "Verdicts & lists"); }
    public static String setGrpRemind() { return s("提醒", "Reminders"); }
    public static String setGrpAcc() { return s("问责", "Accountability"); }
    public static String setGrpUpdateAbout() { return s("更新与关于", "Updates & about"); }
    public static String setGrpMore() { return s("更多", "More"); }

    // ---------------- 更新日志（v1.13.0；用户定的长期规矩：每版首弹＋随时可翻） ----------------
    /** 弹框标题与设置页入口行共用；内容数据在 Changelog（最近 5 版）。 */
    public static String whatsNewEntry() { return s("更新日志", "What's new"); }

    // ---------------- 底部三 Tab + 习惯/知行/晨间（v1.16.0） ----------------
    public static String tabToday() { return s("今天", "Today"); }
    public static String tabSchedule() { return s("行", "Act"); }
    public static String tabZhi() { return s("知", "Know"); }
    public static String tabHabits() { return s("习惯", "Habits"); }
    public static String tabZhixing() { return s("合一", "Oneness"); }
    public static String tabChat() { return s("聊聊", "Chat"); }

    // ---------------- 致良知六组页顶格言（v1.82.0，用户定案六句，出处不上屏） ----------------
    public static String zhiMottoZhi() { return s("志不立，天下无可成之事", "Without a settled resolve, nothing can be accomplished"); }
    public static String zhiMottoMing() { return s("知善知恶是良知", "Knowing good and evil is liangzhi"); }
    public static String zhiMottoJie() { return s("诸恶莫作，众善奉行", "Do no evil, practice all good"); }
    public static String zhiMottoCha() { return s("吾日三省吾身", "Each day I examine myself in three ways"); }
    public static String zhiMottoWen() { return s("敏而好学，不耻下问", "Eager to learn, unashamed to ask"); }
    public static String zhiMottoRen() { return s("泛爱众，而亲仁", "Love all broadly, draw near to the humane"); }
    public static String grpMorning() { return s("晨间", "Morning"); }
    public static String grpHealth() { return s("健康", "Health"); }
    public static String rowMorning() { return s("晨间首填（每天先填习惯）", "Morning check-in (fill habits first)"); }
    public static String rowMorningSub() {
        return s("打开知行先过晨间页，填完才开启今天",
                "Opening KnowDo starts with the morning page until it's filled");
    }
    public static String rowDayStart() { return s("新一天从几点算起", "The new day starts at"); }

    // ---------------- 帮助页 ----------------
    public static String helpTitle() { return s("知行 KnowDo · 帮助", "KnowDo · Help"); }
    public static String helpIntro() {
        return en ? "Version 2.1.0. This page describes the app as it currently works, in layers: everyday use first, expert sections at the end. When a feature slips your mind, look here first."
                  : "版本 v2.1.0。本页按现行版本写，分层排列：前面是日常使用，最后四节是给专家看的。功能记不清时先翻这里。";
    }

    /** [标题, 正文] × 30 节：前 26 节为普通用户层（第一节讲清是什么与第一天怎么用，
     *  其余按五签结构与使用顺序排列，操作内容校订至 v2.0.2 现行形态）；
     *  第 27–30 节为专家层并在标题中标明「给专家看」（行为改变／阳明心学／设计原则／程序员）；英文为对应译文。 */
    public static String[][] helpSections() { return en ? HELP_EN : HELP_ZH; }

    private static final String[][] HELP_ZH = {
        {"一、知行是什么（先看这一节就够开始用了）",
         "一句话：知行是一个帮你「说了就去做、做了就记账」的任务软件。你把要做的事写下来、定好做多久，点开始，它就倒计时盯着你做完；你分神了，它会把你拉回来；一天结束，它把你「说过的」和「做到的」对一遍账。\n为什么要对账？因为大多数人不是不知道该做什么，而是知道却没做。知行维护两本账：「知」是你承诺要做的（任务、习惯、你立的志和原则），「行」是你实际做的（计时、完成、分神记录）。它专门找两本账对不上的地方，在出问题的当下提醒你补上。\n第一天怎么用，五步：\n• 第一步，在「行」页点右下角「＋」，写下今天要做的一件事，定个时长（比如 25 分钟）；\n• 第二步，点这件事的「开始」，倒计时就走了，把手机放一边，专心做；\n• 第三步，时间过半和结束时，屏幕会全屏问你「做完了吗」——做完就点完成，没做完就加几分钟接着做；\n• 第四步，一件事做完，它会问你「接下来干什么」——别空着，马上选下一件，或者选休息几分钟；\n• 第五步，明天早上打开手机，先填晨间页（昨天的习惯做没做到、写一句日记），再开始新的一天。\n就这么多。下面的节都是用到哪查到哪，不必一次读完。最后四节是给专家看的，普通使用可以跳过。\n本地优先：数据先存手机，断网照常用；登录账号后同步到你自己的云端，手机与电脑共用同一份清单。已开源：github.com/gty3310/knowdo，许可 CC BY-NC-SA 4.0（谁都可以继续开发，衍生版本必须同样免费且非商业；动手前先知会作者一声是礼仪约定，不是许可的强制条款）。"},
        {"二、这个软件想帮你什么",
         "知行是为四种深度的人做的，同一条路由浅入深，你从自己所在的那一层开始用就行，往下的层都在那里等着，不必一次全开：\n• 刹得住——容易分神、注意力易散的大脑（类 ADHD，但不贴诊断标签）：靠外置结构控制手机的使用，守卫是你的「外置刹车」，不是行为控制。\n• 收得住——想解决分神的效率人群：任务之间不散、做事时不飘，不把时间花在刷软件上。\n• 立得住——想养成好习惯的人：习惯系统、晨间页与知行指数，把「想养成的」变成每天对账的。\n• 明得住——想把修身用到生活里的人：两本账、原则陪跑与每日省察，让修身不停在读经听课，落到每件具体事上。"},
        {"三、五个页签与今天页",
         "底部五个页签，顺序固定：今天 / 行 / 知 / 合一 / 聊聊。软件打开在「今天」页。\n• 今天：晨间打卡卡（点开填今天的晨间记录）、今日建议卡（今天先做哪件、什么不该等、重要的未回邮件；卡上引用的任务可以直接开始，回复可以拟好交给你的 AI 应用——不会替你发出）、今天的日记全文、今晨推荐的一条原则、健康总览（见第十六节）、进日程的入口。\n• 行：任务表本体（见第四节），顶栏一行是页名、两项简数（必做 · 空闲）和日程 / 日历按钮。\n• 知：收纳你的志、原则、习惯、日记、问题与人的页面（见第十二节）。\n• 合一：一周知与行的对账页，知行指数、趋势与落差清单（见第十五节）。\n• 聊聊：内置 AI 聊天（见第二十一节），有未读消息时带朱砂红小徽标，打开即清。\n左上角三横线抽屉在前四个页签都有：账号与同步状态、语言（中文 / English）、外观（亮 / 暗，可跟随系统）、帮助与设置入口都在里面；设置页也能改。"},
        {"四、任务表怎么用",
         "点任务名就地展开这一行（手风琴，一次只展开一行），展开区里有笔记、开始时间、已用时长与专注规则摘要，和「开始 / 暂停 / 继续」「编辑」「+15 分」「完成」按钮：\n• 「编辑」菜单集中了全部字段：名称与优先级（★ 必要，排最前）、情景（多选标签：有网/无网/跟 AI 聊天……，可自由新增）、截止与开始时间（选日期，或勾“每日重复”；还可再设一个完成时间＝当天几点前做完；同一弹窗里还能给任务设「下次开始时间」，到点会全屏提醒你开始这个任务，可直接开始计时、延后 10 分钟、或忽略）、时长（这个任务打算做多少分钟；弹窗里还能填“实际用时”，填了会在列表里显示“实X”）、专注规则（见第十七节）、笔记、重置计时；\n• 表格的格子也能直接点：开始时间、Deadline、时长、优先、情景各格点的都是对应的编辑弹窗；最左边的勾选框勾上即完成（整行变灰、划掉）；\n• 列可以自己调：按住列头右缘横拖能改列宽；列头最右侧的齿轮按钮（或设置页「显示」组）可以选哪些列显示（任务、开始时间、已用、剩余、时长、Deadline、优先、情景），两样都记在本机；\n• 长按一行：标记完成 / 删除任务；\n• 排序：未完成在前、必要在前、截止近在前。每日重复任务每天单独记完成，不会因昨天做完今天消失。"},
        {"五、计时与全屏检查页",
         "在展开区点「开始」计时；已有任务在计时时，点另一个任务的「开始」会直接切换过去：原任务立即停表、已用进度保留，它不算暂停，之后点它的「开始」随时接着计。暂停（去喝水、上厕所那种中间休息）只在对正在计时的那个任务点「暂停」时出现，要先说清目的和时长（见第八、第十九节）。\n计时中通知栏常驻倒计时；倒计时存的是绝对结束时刻，进程被杀也不漂，精确闹钟在过半点与结束点兜底。时间过半、结束时都会全屏弹出检查页：熄屏时靠全屏通知顶到锁屏上，亮屏解锁时靠“上层显示”直接弹出，是同一页。\n结束页上确认“做完了”会回写完成状态；挂了主原则的任务会多问一句「照它做了吗」（见第十二节）。没做完可以加时：+50%、+5/+10/+15 分钟或自定义分钟数——加时要先过动机门（见第二十节）。检查页支持键盘：回车是主按钮，Q / W / E 对应前三个加时选项。\n任务还能设闹铃：定点响（与计时无关，每日任务次日自动续排），或计时结束时先响再进检查页；默认铃声是 60 秒由静渐强的大自然录音，走闹钟通道，手机静音也响。下一次开始时间到了也会全屏提醒：开始、延后 10 分钟、或忽略。"},
        {"六、子任务",
         "大任务可以拆成一串子任务，按队列逐个计时。\n• 怎么拆：主页展开任务，下方有「子任务」区；大号悬浮窗里也有同一区。点「＋ 加子任务」新增一行，填名字和分钟数；点任意一行可以改名、改分钟或删除；按住行首的拖动手柄上下拖，可以改顺序。\n• 总时长＝子任务之和：只要任务有子任务，它的计划时长就自动等于各子任务分钟数加起来，改子任务分钟，总时长和日程安排跟着变；这时直接改任务时长会被拦下，去改子任务即可。计时中途加子任务会立刻开始计它，队列当场跟着变，不用等下一次开始。\n• 检查跟着子任务走：开始计时后逐个跑子任务，每个子任务到点就弹全屏检查页——「下一个」开始下一个，「加时」给当前子任务加时间，「跳过」跳到下一个，「整个任务完成」提前结束整个任务。有子任务时不再弹任务级过半检查；最后一个子任务结束，就是整个任务的结束检查页。\n• 呈现：计时中悬浮窗中号窗主行显示当前子任务名和它的剩余时间，次行小字是任务名；主页展开区能看到「子任务 2/3 · 当前名字」的进度，没开始时列出整个队列。暂停/恢复时当前子任务的剩余会冻住、恢复后继续。日历和日程仍按整个任务一块显示，不拆块。"},
        {"七、悬浮窗（常驻，三种模式）",
         "悬浮窗是常驻的：只要你打开过知行（且权限页“上层显示”是 ✓、悬浮窗开关是开），它就一直在，永远显示「当前第一要务」——正在计时的任务优先，其次是刚暂停的任务，再次是任务列表排第一的未完成任务（必要优先、截止近的优先）。没在计时也能直接在悬浮窗里开始/继续这个任务。全屏检查页弹出时它会让位，检查页关掉自动回来。三种模式互相切换，模式会被记住：\n• 吸附：贴在屏幕左/右边缘的小标签，只显示分钟数（计时/暂停=剩余分钟，待开始=计划分钟，向上取整、最多 99+）。上面没有任何按钮、不能暂停。点它展开；沿边缘上下拖可换高度；往屏幕中间拖离边缘也会展开。\n• 小展开：半透明小窗：任务名 + 时间 + ▶/⏸ + 浅灰色的极简字符“✎”。点 ▶ 开始/继续；点 ⏸ 不会直接暂停——会先弹出菜单让你选多久后提醒（1/3/5/10/15/20/30/60 分钟、2 小时、自定义），到点全屏叫你回来继续。点内容区、或点“✎”，直接进入编辑模式并弹键盘；按住拖动可以移动，拖到屏幕左/右边缘松手就吸附成标签。\n• 编辑：更不透明的完整卡片——任务名、大字时间、状态、笔记输入框（笔记就是输入框本身）。点笔记区直接打字，停笔约 1 秒自动保存（联网时同步到云端）；时间行左边的大字是剩余时间，右边小字是「已用 / 计划总」：已用是这个任务在本机累计计时过的时间，计划总是当前目标总时长（含加时后的）；按钮：开始/暂停/继续（同上，暂停要选提醒时间）、AI、完成（计时中才显示，弹出结束检查页）、打开知行、收起。\n• AI 按钮：把这个任务的全部信息（名称、情景、截止、时长、优先级、专注规则、当前状态、笔记）复制到剪贴板，并打开你在权限页选定的 AI 助手：若对方支持分享直达，文字会直接进输入框；否则打开助手后粘贴即可。\n• 复制按钮：笔记编辑区右上角常驻一个「复制」，点的效果和 AI 按钮的前半步一样——把任务全部信息复制到剪贴板——但不打开助手。主界面里弹出的笔记编辑框、专注规则编辑框右上角也有同样的「复制」，复制时用的是编辑框里正在写的内容。\n权限页有“预览悬浮窗”，不用开始计时也能看 10 秒效果（常驻服务在跑时预览会被实时内容覆盖，属正常）。\n• 定时关闭：大卡片上有「关闭」——选 10/15/30/60 分钟后回来、等当前任务结束回来、自定义分钟数，或者「不再出现」（等于关掉设置里的悬浮窗开关，在设置里能再打开）。定时项到点自动再现，关机重启也照算；隐藏期间只是没了这块窗，计时、守卫弹页、闹铃、通知全都照常。"},
        {"八、不留空档：衔接守卫与暂停",
         "知行的规矩是：任何时刻都要有一个任务在跑，不允许出现「没有状态的空档」。只有三种状态：正在计时一个任务；暂停中（必带时长，到点全屏叫回）；正在填「下一个任务」页。\n• 空档全屏问下一个：检查页确认完成、手动完成一个任务、打开 App，或者悬浮窗巡检发现——只要没在计时、也没有带时长的暂停在生效，就会全屏弹出「接下来干什么？」：填名称、选时长（快捷按钮 2/3/5/10/15/20/30 分钟，或用天/时/分三个转轴自定义），页面实时显示合计分钟数与结束时刻，点「开始」就建好任务并立刻开始计时；页面下方列着今天未完成的任务，点一条直接按它的计划时长开始，不用重建。\n• 休息也要建任务：点「休息」快捷钮把名称填成休息、选个时长开始计时——睡觉也一样，建一个「睡觉」任务占位，没有静默时段。\n• 暂停必选时长，还要先过暂停卡：先选去干什么（上厕所、喝水、吃饭、回电话、喊去帮忙、睡午觉、换换脑子、去上课，或自定义），再选去多久，底部有预览，点「开始暂停」才停。暂停期间守卫仍在工作，按你写的目的判定（见第十九节）。没有「无限期暂停」。\n• 暂停到期＝空档：叫回页全屏问你回不回来，60 秒没有任何反馈（继续/再歇/换任务），会响起 60 秒由小渐大的鸟鸣闹铃，你一操作就停；选不继续当前任务时可以直接转到「下一个任务」页，闭环不留空档。静音暂停是例外：卡上打开静音后，到点只亮屏＋给一条通知、不出声，你没回复就每 5 分钟再亮一次（点「去上课」时静音会自动打开）。\n• 空档催促是个人设置：设置页「守卫」组里有「空档催促」——温和＝任务结束就结束，不弹页、不出声；标准＝全屏提醒＋有限轮（默认最多 3 轮）后安静，全程没有声音；严格＝全屏页先出现、震动一段、没反应才让声音由小变大，不停催直到你开始任务，只有这一档有声音开关；自定义＝每个数字自己定。这份设置存进你的云端账号，手机与电脑共用同一份。\n• 两台设备互通：在另一台设备（如 Mac）上计时或定时暂停时，这台不会误弹；在设置页「守卫」组可以关掉「任务衔接守卫」，关掉后不再自动弹（暂停规则不受影响）。守卫页按返回键退出后会自动安静 3 分钟，让你临时翻任务列表或改设置；完成任务这类明确时刻触发的弹窗不受这 3 分钟限制。\n• 路人静音：闹铃页最下方有个大按钮「如果您不是手机主人 请点击这里静音」——手机响个不停时旁边的人点一下就静音，一直静到下一次屏幕亮起，他碰不到你的任务；你下次解锁会先看到致谢页，可以直接继续刚才那个任务，或去任务页选新的。\n• 拿起手机先选任务：没有任务在计时、也没有带倒计时的暂停时，你一解锁手机就全屏问「接下来，做点什么」（晨间页、致谢页优先于它；通话中不抢，挂断后补上）。点今日任务一行就开始，也可以新建，或选「休息一会」——休息也是正式安排，选个时长占位计时，到点叫你回来。"},
        {"九、日程与日历页",
         "你今天的日程存在你账号的云端里，日历页读它来画「计划」栏。排法有三条路：\n• 本地自动排程：今天没排上的必做任务，App 自己按每件时长 ×1.5 填进空档（留 50% 缓冲），不排到 22:30 之后；已经排好的块、已经过去的块、已完成任务的块都不动。\n• 日历页的「AI 重排」：点它，软件内置的 AI 当场按同一套规矩给一版新排程提案，摆给你看——点「应用」才落盘，点「不用」整版作废。旁边还有「快速重排」：不经 AI、按固定顺序（到期的短任务优先，其次必做、到期重要、到期次要、未到期重要、未到期次要；休息永不自动排）当场重排，正在计时的、已完成的、你亲手拖过的块都不动，排完可以一键撤销。\n• 你亲手拖：长按计划块拖到新时间，或把未排任务拖进日历，拖过的块会被当作固定块。\n三条路都遵守同一套硬规矩：每件按计划时长 ×1.5 占时段；优先级序固定（必做＞到期重要＞到期次要＞未到期重要＞未到期次要），层级永不自动变更；不排到 22:30 之后；排不下要明说，不许硬塞。\n日历在「行」页里展开：左边就是任务列表、右边是日历，底部五个页签始终都在，随时可以切走再回来（日历保持展开）；日历左缘拉手可拖动定宽，拖到很窄松手自动关上。时间轴是三栏并排：最左一条窄栏是「日历」（你的 Google 日历日程，只放开始时间＋标题，只读参考，排程不用避让它），中间最宽的一栏是「计划」（排好的时段，浅色描边块，是这页的主角），右边一条窄栏是「实际」（你真正计时的时段，实心块）；同一件任务分段做的，就分几段显示；做完的任务变灰，正在计时的高亮，还有一条红线标着现在几点。顶部写着今天已完成几件、未完成几件、从现在到 22:30（睡觉）还可自由安排多少分钟，一眼就知道今天还剩多少事、时间紧不紧。\n计划块可以亲手挪时间：长按一块把它拾起来（会微微放大），上下拖到新位置松手，按 5 分钟对齐，松手就存回云端——只改被拖那一块的起止，之后重排时会把你手动拖过的块当作固定块，不再挪回去。按住一个还没排的任务往右拖进日历，拖动时计划栏会出现预览块并标起止时间，松手落在哪个时刻就排到哪个时刻（5 分钟对齐）；块长按任务计划时长 ×1.5 占时。拖回左边或拖到日历外松手就是取消，不会写任何东西。已经排好的任务，行上会多一个「已排 HH:mm」小标记，这种行不能再拖。\n计划块右上角和未排入日程的任务行上都有一个 ▶：点一下就立即开始这个任务计时（悬浮窗会自动切换到它），正在计时的那个显示 ⏸，点它暂停，和主界面的开始/暂停是同一套。在日历里点任何一个计划块（任务块），左边的任务列表会定位并展开那个任务，开始/暂停、编辑、闹钟、加时、完成都能直接操作。Google 日历的块点开仍只是看全名，不展开。"},
        {"十、同步、账号与跨设备计时",
         "知行的同步走云端数据库 Supabase：在三横线菜单或设置页「账号」组点「账号」，用邮箱＋密码注册或登录。第一次登录时，本机现有任务会全量成为你在云端的数据（不会丢失、不会变空），之后手机与电脑共用同一份任务清单，改哪台、另一台几秒内就跟上；每 30 秒还有一次全量对账兜底。合并规则是同一任务按修改时间新者胜，删除用墓碑标记防复活。没网时在本机照常用，改动先排队，联网后自动先推后拉合并。抽屉的账号区和设置页会显示同步状态（未登录 / 已同步 / 待同步 N 条 / 同步中）。没登录时只有本机功能可用，不同步。\n计时也跟着跨设备：你在任何一台设备上开始、暂停、继续、加时或结束，另一台立刻镜像出同一个倒计时（开始时间点严格对齐，两边同一时刻到点弹检查页）；哪一台先处理了检查页（确认做完或加时），另一台跟着变——它开着的检查页会自动关掉，不会两边各弹各的。\n“立即开始”：任务带上“立即开始”标记同步过来时（比如你在对话里说了“接下来 N 分钟做 X”、由软件之外的助手记进你的云端），知行收到后自动开始计时；正有计时在跑时直接切换过去。\n每台设备只看得到自己账号的数据（服务端按账号隔离）；退出登录后本机数据都还在，只是不再同步。日历页的 Google 日历事件是同步到云端的只读展示。Mac 版是姊妹项目，与手机共用同一账号、同一份任务与同一台逻辑计时器。"},
        {"十一、我的志与磨练自检",
         "我的志：在设置页最上方的「我的志」里，亲手写下你立志的那句话——每人各自设置，只属于你自己。\n• 什么时候出现：分神被抓的问询页、任务结束检查页、子任务检查页、暂停叫回页——页面上会多出一行金色的字，就是你的志。它不是按钮，点不动，只是给你看的。\n• 没写时，这些页面什么都不多显示，不留占位、也不催你填；想改随时回设置页改。\n• 登录后，这句话会同步进你账号的云端数据并跟着账号走：换一台设备登录同一账号、本机还没写过时，会自动采用云端的那句；你在 App 里改了，也会回写云端。\n磨练自检：把每一次「被抓分神」都变成一次对自己的察问。\n• 分神问询页顶部有一问：「刚才在做该做的事吗？」点[做到了]或[没有]。点了立刻记下，不点也不会拦着你——页面原有的回到任务、宽限、放行按钮照常可用，不必先答题（现行问询页里，选宽限本身也会记一次分神，见第十九节）。\n• 记录存本机并同步到你账号名下的云端：答「没有」的次数会进合一页的分神统计与知行指数的分神控制项——哪天最容易知而未行，一看便知。"},
        {"十二、知页：志、明、戒、察、问、人",
         "「知」页把你的「知」收成六组，写东西时可以用 {{ }} 把各组互相引用：\n• 志：你的志（见第十一节）与价值观，方向只有这一处。\n• 明：思维原则——你想事情的标准。\n• 戒：习惯（见第十三节）与行为原则——你做事的标准。\n• 察：日记、动机统计（见第二十节）、被抓分神的只读清单——回看自己。\n• 问：还没想明白、想找人问的问题，可以标星排优先级。\n• 人：与这些事相关的人。\n原则陪跑：每个任务可以挂一条主原则，任务行上和计时中都会显示；完成这个任务时，结束页会轻声问一句「你照它做了吗」——照做了、没考虑到、还是没做到。答不答都不拦着你完成；但答「没考虑」时，这条原则与这个任务的关联会撤下、不进统计：做事时根本没想起的原则，不算你真的知道它。原则从哪来：你先在日记里写，AI 从日记里提炼出候选，你逐条改定后才进「知」页成为正式原则——没改定的永远不算数。"},
        {"十三、习惯",
         "习惯在「知」页的戒组里，是你的长年账本：单个任务做没做只是一天的事，习惯看的是你这个人。\n• 五种目标类型：至少型——每周至少做到 X 天，达标就是 100%，多做也 100%（封顶）；至多型——这种事每周至多 X 次，越少越好，达标 100%，超标按 目标÷实际 扣分；数值型——每天填一个数（如步数 6000），单日按 实际÷目标 算（封顶 100%），一周取平均；累积型——每天记一个增量（一件件攒，如善事），核心是从起始日以来的累计总和，可设总量目标看进度，合一页里按「本周增量 ÷ 周增量目标」算达成率（没设周目标时本周有增量记 100%），累积型还可以带一个起始数，以前攒的接着算；每日提醒型——没有次数目标，核心是一段写给自己的话：每天读过就算完成（读过 100%、没读 0%，周达成率按已读天数 ÷ 本周已过去的天数算）。\n• 添加习惯：点「＋ 添加习惯」，先从默认习惯库选（早起、站桩、静坐、冥想、读经、写日记、运动、戒晚睡、少刷手机、步数、善事，都预置好了类型与默认目标），或点「自定义习惯」自己建；第二步选类型、填目标（每日提醒填提醒的话，必填）、设提醒时间。添加第一个习惯后，页面顶部会出现一次建议卡，问要不要顺手开启晨间首填，点「不用」后不再出现。\n• 每个习惯能设自己的提醒时间：到点发一条本地通知，点开直接进习惯页；改时间或关掉立即生效，重启也会续排。\n• 习惯卡上有年格（最近 365 天，列是周、行是周一到周日）：点任何一格可以给那天补记/改记；点习惯名可以改名称、目标、提醒，或删除习惯（记录会一起删）。卡上还有本周进度与统计（本年天数/连续/最长等，按类型略有不同）。设置页「习惯显示窗口」可以统一选每张卡的主数按今天、本周、上周还是本月算；戒组还有紧凑模式：一行一个习惯，本周七个点，右边写还差多少达标。\n• 习惯数据（定义、每天的记录、晨间日记、晨间设置）存在你账号的云端，手机与电脑共用一份，离线先存本机、联网自动合并，本机优先，绝不按时间删除。"},
        {"十四、晨间页：先意义，后数据",
         "每天的开始先过晨间页。在设置页「晨间」组开启后，每天「新一天从几点算起」（默认凌晨 4:00，可调）之后，你早上第一次拿起手机（解锁）时，晨间页就会全屏出现、盖住其他应用，不必先打开知行（先打开知行也一样会先进这一页；通话中不抢，挂断后或下次解锁补上）。页面没有返回键，填完才能开启今天。从 v2.0.0 起顺序是先意义、后数据：\n• 意义区（最先）：你的志，和「给今晚的话」——今晚的一句小计划（比如 22:30 还在刷手机就去洗漱），每周一会轻轻提醒你重写一次；\n• 昨晚的数字：昨晚几点睡、睡了多久、达没达标（有健康授权时自动读到，见第十六节；没有就手填入睡与起床时间，照样记账）；\n• 今晚的确认：今晚几点上床，只改今晚，不动设置里的目标；\n• HRV 一格（手填，手表软件里看一眼昨晚平均值抄进来，不填就按缺项算）；\n• A 昨日习惯：逐项回想昨天——至少/至多型都点「做到了/没做到」，数值型填昨天的数，累积型记昨天的增量，每日提醒先把全文读一遍再点「读过了/没读」；留空不算数，必须全答。有每日提醒习惯时，A 之后还有「今日提醒」一节：今天的提醒全文摆在那里，读完点「读过了」，今天这个习惯就算完成了；\n• B 昨日小报：只读——按单日口径算出的昨日指数、承诺与做到的件数、漏项、昨日分神次数与自检状态；\n• C 晨间日记：必填，写一句即可，存到新一天名下。\n倒计时：早上第一次打开，晨间打卡自动开始倒计时 10 分钟，可以加时，也可以先 pass 去做别的事；退出页面计时不会停，直到打卡了结。\n填完「开启今天」才点亮：习惯记到昨天、日记记到今天，当天不会再弹。填的过程中每一步都即时存好，中途退出、页面被关，回来都是接着填。\n• 暂停一会儿：早上真有急事（关闹钟、回紧急消息、接电话），点页面下方的「暂停一会儿」，选 5/10/15/20/30/60 分钟或自定义分钟数——这一页会退下，到点全屏自己回来；可以反复暂停，不限次数。暂停期间你打开知行，也能提前进这一页。\n• 没填完、又没选推迟时长时：按 Home、按返回、切到别的 App 都不算推迟——每 5 分钟全屏再弹回来一次，直到你填完点「开启今天」，或明确选了「暂停一会儿」的时长为止。已填的内容照旧即时保存，不会丢。\n• 测试晨间页：设置「晨间」组里有「测试晨间页」按钮，白天点一下就以正式模式打开晨间页（不看时间、不看今天是否已完成），打开的就是真页面真数据。\n• 补记/修改：「知」页戒组有「晨间记录补记 / 修改」入口，打开的是晨间页的编辑版——可以返回退出、不必全填，顶部能切换记录日（今天/昨天/前天，或任选日期）。在这里改过去的记录不会算作完成今天的晨间门；改的正是今天这一轮时，晨间门照常出现，且会预填你已存的内容。"},
        {"十五、知行指数与合一页",
         "「合一」页把一周的「知」与「行」对成一本账，核心是一个 0–100 的知行指数：\n指数 ＝ 习惯达成 ×50% ＋ 任务完成 ×35% ＋ 分神控制 ×15%。它是对账的仪表，不是给你的分数——看的是哪一本账落下了，不是你这个人值多少分。\n• 习惯达成：全部习惯本周达成率的平均（每种习惯按自己的目标类型算，见第十三节）；还没有任何习惯时按满分起算。\n• 任务完成：本周到期/排期的任务里完成数 ÷ 总数——非每日任务按截止日算，每日任务按天算（做到那天记一天），日历里排过的块也算进来；只算必做任务，AI 任务与今天跳过的任务不算；本周没有到期的任务时按满分起算。\n• 分神控制：本周分神次数 ＝ 自检里答「没有」的次数 ＋ 守卫事件数；控制分 ＝ 100 − 5×次数（最低 0），没有数据时是满分。\n• 页面构成：大圆环指数（带较上周的涨跌）、本周构成三项（各带与上周的差）、逐习惯本周 vs 上周对照、知行落差清单（没跟上节奏的习惯、超标的至多型、本周到期的未完成必做任务等），以及下周目标——每个习惯一个步进器，改动即存、下周一自动生效，不改就沿用本周目标。\n• 点指数圆环进趋势页：三张可切换的图（指数本身、原则践行、动机纯正占比），按月或按年看，数据来自软件每天封存的日账。"},
        {"十六、健康与恢复分",
         "从 v2.0.0 起，知行自己读健康数据，不再绕任何中转。在设置 → 健康里授权一次，它就直接读手机里「健康数据共享」（Health Connect）的四样：睡眠（含分期）、静息心率、步数、活动热量。打开晨间页和进入今天页时读取，不在后台轮询。\n• 授权：点「去授权」会弹出系统授权页，把四项打开；知行也会出现在系统「健康数据共享 → 应用权限」列表里，可以随时在那边改或撤。设置页健康组还有「打开健康数据共享设置」一行和一行诊断小字（状态码与最近错误），出问题时先看它。不授权或撤掉授权，其他功能完全不受影响。\n• 睡眠目标你自己设：目标上床（默认 23:00）、起床（06:00）、午睡时长（60 分钟）都在设置 → 健康里，全软件的达标判定都按这一份算；改了只管以后，已经记下的日子不重算。\n• 记账口径：一晚睡眠记到它结束那天的逻辑日（凌晨 4:00 分界，与习惯同一条线）；一天里最长的一段是主睡眠，短的算午睡；同一段时间有多个来源只记一份（佳明来源优先）。达标＝上床不晚于目标、且睡够目标时长的 90%。\n• 没有手表或没授权时：晨间页手填入睡和起床时间，照样记账、照样算恢复分。\n• 恢复分：今天页有昨晚睡眠、恢复分圆环、步数与午睡。恢复分是知行自己的算法，六项加权（睡眠时长 35、入睡时间点 25、深睡占比 15、静息心率 10、HRV 10、昨日活动 5），点圆环能看到完整公式与每一项的得分；缺哪项就把它的权重按比例分给其余项并注明缺了什么，历史不足 7 天时会说明基线还在建立。恢复分不进知行指数，两本账分开。\n• HRV 先手填：佳明不把 HRV 传给健康数据共享，晨间页留了一格，抄一眼昨晚平均值即可。\n• 隐私：原始记录与睡眠分期只存本机、永不上传；上云的只有每天一行汇总（几点睡、睡多久、达没达标），存在你自己的账号下。每周汇总（达标晚数、平均上床、平均时长）只出数据，供你写周记时用。"},
        {"十七、专注守卫（看你在用哪个应用）",
         "计时中，知行每 2 秒借“使用情况访问”看一眼你正在用的应用（窗口一变还会立即复查），与当前任务的专注规则对不上，就弹全屏问询页。\n规则在任务的专注规则里设（展开区「编辑 → 专注规则」）：一句自然语言规则（写给人看的，如“读经典只用弦歌”）＋ 主软件 ＋ 任务软件名单。先说清：从 v1.94.0 起知行没有黑名单了——没有哪个软件是「一开就拦」的，名单只有白、灰两档（见下），判定看的是屏幕内容与语义，不是软件名字。\n• 任务软件名单：开始一个任务时，你的常用软件会被按这个任务在后台分成白、灰两档——白档软件在本次计时内不再触发专注规则问询；灰档不新增打扰，照旧走屏幕内容守卫（见第十八节）。在任务详情里点「软件名单」能看两档、点某个软件改档，你改过的不会再被覆盖。名单只存手机本地、不上云。设置页「判定与名单」组有总开关，关了就不发名单请求、不按名单放行。注意：名单的自动分类目前只对作者本人的账号开放，其他账号暂时没有名单（见第二十六节）。\n• 被拦下时「关掉」的意思不是回桌面，而是把你送回应该用的软件：设了主软件就跳它（如读经典跳弦歌），没设就跳知行自己的“当前任务页”。\n• 问询页的选择：立即回到任务 / 换一个任务 / 拿一段宽限（现在就封掉这个应用，或再用 1 / 3 / 5 分钟——时间一到，你选的处置就落地：退出应用、放后台，或盖住 3 分钟，那一页上没法提前退出）。难度不同，问询页的规矩也不同，见第十九节。每拿一次宽限，都如实记一次分神。\n• 不拦的：知行自己、桌面、输入法、在权限页选定的 AI 助手应用（跟 AI 助手对话算元操作）。\n• 局限：靠轮询，最长约 2 秒延迟。"},
        {"十八、屏幕内容守卫（看屏幕上是什么）",
         "专注守卫只能看“在用哪个应用”，看不见“在浏览器里购物、刷短视频网页版、看黄色内容”——这个守卫补这一块。在权限页「屏幕内容守卫」一节打开总开关（默认关；设置页「守卫」组也有同一个开关）。\n• 采样：只在计时中 + 亮屏 + 已解锁时工作；每隔一段随机时间截一张图，时刻猜不到。截图只走无障碍通道（在权限页开启）：开启后计时中自动采样，不再弹任何录屏授权；没开启无障碍时整场不采样，只在记录里留一行说明。知行从不申请录屏权限。\n• 怎么判：先在手机本机筛——小模型看图抓黄色/暴露内容（按你的定义：衣服穿得很少的照片也算违规），文字识别读屏幕上的字（中文/日文/英文），本地语义模型把屏幕文字与当前任务比对。本机能判清的当场判清；判不清的中间带，只有提取出的文字特征会送云端终判（供应商不得留存、不得拿去训练；截图本身从不出手机）。云端判「不相关」但把握不足（置信度低于 0.70）时改判放行，不冤枉人。从 v1.94.0 起，应用类别与购物、短视频关键词都不再直接定罪。\n• 防误判：同一类连续命中 2 张才弹问询页（黄色类可在设置里改成 1 张即触发）；问询页和专注守卫是同一页（见第十七、十九节）。每次判定都会进记录，并以事件形式同步到云端（只有时间、类别、置信度、一句证据文字）。\n• 隐私红线：截图只在内存里分析、分析完立刻销毁——不保存、不上传、不发给任何人（唯一例外是下面这段的黄图问责邮件，也只发打码图）。\n• 已知盲区：银行类应用、部分付费视频、浏览器无痕模式的界面受系统保护，截出来是纯黑图，只能记一次“不可见”、判不了；游泳、健身等画面小模型可能误报；“跟 AI 聊的是不是正题”是最难判的，判不了就放过，不硬判。\n• 黄图问责邮件（默认关，在设置页「守卫」组里开）：这是上面隐私红线的唯一例外。打开后，守卫终判为黄色内容时，会把那一张截图当场在手机内存里重度模糊＋降分辨率打码，只把打码图发到你自己填的问责邮箱，让对方督促你；原图照旧立刻销毁、从不保存、从不上传。同类事件 10 分钟内只发一封、每天最多 5 封，超限就不发但会记下来。现在是测试版，这些邮件借用作者的邮箱通道发送，发送内容作者可以看得见。"},
        {"十九、守卫的难度、宽松与申诉",
         "守卫不是一刀切：有多严，你在清醒时先定好；判得对不对，事后有正式的申诉通道。\n• 难度三档（设置 → 守卫）：简单＝经典流程，点「误判」即放行；中等＝每次拿宽限先过一道呼吸门（静下心呼吸一段，时长随这个任务今天拿宽限的次数翻倍，15 秒起、封顶 120 秒，离开呼吸页宽限作废）；困难＝「误判」要写一句解释、由 AI 判定，判定同意才放行，且宽限有每日配额（默认 1 次）。三档里「误判」按钮每次问询都在——真误判发生在哪一次，都能申诉。\n• 误判申诉：简单档点即放行；中/难档写一句解释走 AI 复核（复核会带上任务说明，任务本身有特殊情况时更容易判对），盖屏页上也能申诉；AI 通道不可用时等 5 秒可跳过。被拒了可以改解释再交。你的解释会喂回判定，让它越来越准。\n• 真分神与假分神（v1.98.0）：守卫不再只看一帧，它看最近 3 小时的活动流水（你在各应用里做什么、做了多久，本地拼成文字骨架，不花额外的模型费用）。假分神＝主线做得好好的，偶尔岔去处理另一件重要的事、去得短回得快——这相当于临时换了个任务，应放行；真分神＝主线被不断打断、跳来跳去，哪怕每件单看都是正事，也算分神。据此给宽松四档：L3 假分神·明确＝不弹窗、不记分神；L2 假分神·大概＝极简问询页，一个大按钮一键放行、不用解释、不过呼吸门、不记分神；L1 拿不准/轻度＝标准问询流程；L0 真分神·明确＝全额停稳：先呼吸、再答一句反思小问，宽限收紧——目的不是罚时，是打断自动驾驶状态。\n• 两道把关：你清醒时自设的难度档是预先承诺、是地板——AI 只分真假，不赦免真分神，L0 的时长不许缩水；本地还有硬护栏（近 1 小时主线占比低于一半、或 1 小时内已判 L0 两次，宽松封顶 L1），护栏只封顶、不加严。\n• 如实说明：宽松四档要靠服务端判定输出「宽松程度」才完整生效；服务端那一半目前尚未部署，在部署之前，判定一律落 L1 标准流程，与之前的版本行为一致。客户端这一半已经就位，部署后自动生效，不用再更新 App。\n• 暂停期间守卫（v1.96.0）：暂停不再是守卫的盲区。按下暂停必须先填目的——而且要写清怎么做（方式＋范围＋边界，不是只选个名目）。暂停期间守卫继续采样、按这个目的判定：抓到只留痕＋弹问询，不写自检、不计入分神数；暂停期不启用名单模式；恢复或结束暂停即关。\n• 问询页是大窗（v1.97.0）：问询页与盖屏、叫回同级，是划不走、按返回也关不掉的悬浮大窗，必须选下一步才收回；进程被杀后，未决的问询会在服务重启后短时内、状态仍相符时重新出现。锁屏或没有悬浮窗权限时回落到普通全屏页。"},
        {"二十、AI 任务与加时动机门",
         "• AI 任务：一个任务可以指派给「我的 AI」而不是你自己。AI 任务不进自动排程、不进时间账、不滚到明天；作者的 AI 助手在后台做它（查资料、写草稿、整理、核对），永远停在最后一步之前——发消息、发邮件、提交、付款这类不可逆的一步，必须等你确认才做。做完或卡住，结果会写回任务备注并把状态置为待确认或待你回答。对其他用户：这是作者个人的 AI 设置在干活，不是软件自带功能（见第二十六节）。\n• 加时动机门：任何任务、任何入口的加时，先过一问——「动机至善，私心了无」了吗？怎么答都放行你加时，但答案如实记下，进合一页的动机统计（趋势页里有「动机纯正占比」）。它拦的不是加时，是不经心的加时。"},
        {"二十一、聊聊与批量加任务",
         "「聊聊」是软件内置的 AI 聊天，开箱就能用，走项目方的免费模型通道，软件里所有 AI 按钮用的都是它（见第二十六节）。\n• 批量加任务（v1.99.0）：一次把几件事说给它，它会整理成一张编号汇总卡——每件的标题、时长、优先级、截止、重复都列清楚，缺的字段按默认值补并在卡上标明，哪件是给 AI 做的也标出来；你点一次「全部建好」，整批一次建完。之后想改其中一件，在聊天里报卡上的编号就能单独改它。\n• 未读徽标：聊聊页签上有朱砂红小徽标，数的是你还没看的 AI 消息（主要是分神陪伴类的主动消息）；打开页签即清。"},
        {"二十二、权限页的项目都是干什么的",
         "• 通知权限：常驻倒计时与提醒的基础；\n• 全屏通知（熄屏弹出）：把检查页顶到锁屏上靠它；\n• 上层显示：亮屏时直接弹全屏页、悬浮窗，都靠它；\n• 精确闹钟：App 进程被系统杀掉时，过半/结束仍能准时触发的兜底；\n• 电池优化豁免：防止系统把计时、同步服务冻掉；\n• 使用情况访问：两个守卫判断前台应用靠它；\n• 无障碍：屏幕内容守卫的截图通道靠它，要你先在系统设置里亲手开启；\n• 健康数据共享授权：在设置 → 健康里给，管的是睡眠等四项健康数据（见第十六节）；\n• 另有：在这里选“回到 AI 助手”指向哪个应用、开关悬浮窗与预览、屏幕内容守卫的开关。\n建议设置：电池优化豁免、悬浮窗、无障碍截图通道、全屏通知、精确闹钟、通知权限六项，新装或升级后首次打开会呈现一次，设置页里有常设入口随时复查。都是可选的——每一项管一个功能，用得上就开、用不上不开，不开不影响其他功能使用，只是系统可能在后台把知行停掉，晨间和提醒可能不准时；小米、华为等机型还需在系统里另开自启动与后台弹出（应用内检测不到，清单底部有一行说明）。"},
        {"二十三、记录页、更新日志与数据安全",
         "• 记录页：每次开始/过半/结束/加时、两个守卫的每次动作、每次同步结果，都带时间戳记在这里，方便回看对账。\n• 更新日志：每次升级到新版本后，第一次打开知行会弹一个「更新日志」框：最近 5 个版本纵向排列——每版改了什么（按新功能／改进／修复分组）、为什么改、最新版还有「怎么试」，下滑就能翻完。只在升级后的第一次打开弹一次；全新安装不会弹。平时想翻：设置页「通用」组有「更新日志」入口。这本帮助也会随版本更新，但如实说：它写的是现行形态的说明，不是逐版流水账，逐版细节看更新日志本身。\n• 数据安全：换手机/重装 App 后，用同一个账号重新登录，云端数据会自动同步回来。手机重启后实时同步会自动恢复；若发现没跟上，打开一次知行即可。任务数据有两份：手机本地一份、云端一份，以修改时间新者胜合并；云端那份就是备份。有问题或想要新功能，可以先在「聊聊」里问问，或联系作者。"},
        {"二十四、提到过但暂不做 / 决定不做的功能",
         "1. 更快的推送通道——暂缓。云端实时推送加 30 秒对账目前够用。\n2. 把截图发给云端 AI 判读——决定不做。隐私上截图不出手机；且云端模型的内容政策会拒绝分析黄色截图，最需要它判的那一类恰恰判不了。\n3. 保留截图原图作证据 / 原图上云——决定不做（红线）。判了分神也只留文字结论。\n4. 截图不走无障碍、还想全隐形采样——做不到也不做。截图只走无障碍通道，要你先在系统设置里亲手开启。\n5. 屏幕内容守卫全天开启（不计时也采样）——暂不做。电量与隐私成本都翻倍，先只守计时时段。\n6. 罚钱、分神公示这类惩罚手段——决定不做。知行只对账、不惩罚：催促有档位、静音有出口，严厉程度由你自己选。\n7. 端侧多模态大模型深判（3GB 起的大模型）——只留了接口、未启用。它专治“AI 聊天跑题”这类语义题，但耗电、发热都大，等真需要再接。"},
        {"二十五、下一步的方向（还没有做出来）",
         "下面这些都还在设计里、没有做出来，写在这里只是让你知道方向，不要当成现有功能：\n知行下一步想让「聊聊」里的 AI 更像一个住在软件里的同伴：它能在合适的时刻主动开口问你一句（比如每晚固定时间问你今晚打算几点睡，你的回答就是今晚的承诺，第二天对账），也能在算出某个任务快完不成时，把对的页面递到你面前商量怎么办。再往后，是把「什么情况下该怎么想、怎么做」做成一个个可以打开或关掉的训练计划，你开哪个，AI 就按哪个陪你练。更远的终局，是软件内建的 AI 能看着你的行为流水，在该提醒时提醒、该聊时聊。这些都还没有实现，实现了会写进更新日志。"},
        {"二十六、新用户必读：AI 功能的边界",
         "这个软件的 AI 分两部分。一部分在软件里面：『聊聊』聊天机器人，开箱就能用，走的是项目方的免费模型通道。另一部分在软件外面：把任务说给软件之外的助手记下来、在云端替你盯着重排的机会、给任务分软件名单，这些是作者个人的 AI 助手在作者自己的云端巡查着完成的，是作者个人的设置在干活，不是软件自带的功能。\n软件里面的各个 AI 按钮，对所有用户都走同一个内置聊天机器人，没有给作者单开的通道；作者和大家在软件里用的是同一个 AI。\n对新用户把话说死：能连上的 AI 有且只有软件自带的『聊聊』这一种，没有别的选择；把自己的外部助手连进来这条路，目前不存在。你的外部助手进不了这个软件的云端数据库，也没有巡查在为你跑。对你实际能用的，是软件自带的本地功能（本地自动排程、本地守卫判定），加上『聊聊』；日历页的「AI 重排」也走内置 AI，当场给提案、你点应用才生效。软件名单的自动分类同理，是作者个人的设置：由作者自己的助手在后台为作者的账号生成；其他用户的账号暂时没有这项功能，也就没有软件名单。\n数据方面请放心：你的任务、名单和记录只存在你自己的账号下面，作者看不到，作者的助手也碰不到；任何人的 AI 都读不到别人的数据。这是数据库层面的隔离，不是口头承诺。"},
        {"二十七、给专家看（一）：行为改变的设计",
         "这一节是给研究行为改变的人看的，讲机制背后的设计取舍；普通使用不需要读。\n• 外置结构补内在结构：知行的基本假设是，靠意志力在当下硬扛是不可靠的，所以把结构外置——倒计时存绝对结束时刻、无空档三态、晨间门、跨设备一台逻辑计时器，都是让环境替你守规矩，而不是让你每一刻都自律。作者本人曾长期用付费人工监督的方式维持专注，这套软件是那套办法的最新一代。\n• 预先承诺（precommitment）：难度档、志、原则、暂停目的，都是用户清醒时写下的承诺；临场时 AI 与界面只能在承诺划定的地板之上给宽松，不能赦免。呼吸门、宽限配额、L0 不许缩水，都是预先承诺的执行机构。\n• 摩擦的校准：摩擦只加在刀刃上——该拦的拦到底（全屏检查、问询大窗划不走），不该拦的一步放行（假分神 L3 不弹窗、误判一键申诉）。每一次打扰都要挣得它的注意力；误判申诉与解释喂回，是让摩擦随使用收敛的校准回路。\n• 真假分神：判定单位从「单个动作」升级为「3 小时行为模式」——偶尔岔去办正事是任务切换，频繁跳切才是分神，哪怕每件都是正事。这与只看当前屏幕的传统专注软件是根本区别。\n• 原子习惯的映射：晨间门对应「让它显而易见＋固定触发」，习惯叠加在晨间流程里；给今晚的话是 if-then 实施意图；知行指数与年格是可见的进度反馈；立志常驻对应身份认同（先成为什么样的人，再谈做多少）。软件有意不用连续打卡断签羞辱、不用罚钱——反馈只对账，不惩罚。\n• 意义优先于弱点手段：被看见、摩擦、问责都是脚手架，现阶段可用；北极星是通过意义改变行为——让用户自己的志与原则去劝他，软件做意义的使者，不做数据的主人。"},
        {"二十八、给专家看（二）：阳明心学与知行（修行人）",
         "这一节是给儒释道修行背景的读者看的，讲知行的机制与王阳明心学的对应；口径以《传习录》为准，对应不上的地方如实说。\n阳明说：「未有知而不行者。知而不行，只是未知。」「知是行之始，行是知之成。」知行之所以分成两截，是被私欲隔断了。所以知行记的落差，从来不是知识缺口，而是被隔断的次数：分神、自检答「没有」、动机门答「没有」——记的全是隔断。\n对应得实的有五处：\n• 两本账＋知行指数，是上面这个命题的工程版：账上只记隔断，不记不懂。\n• 立志：「志不立，天下无可成之事。」（《教条示龙场诸生》）知行的志只出现在分神问询、结束检查、子任务检查、暂停叫回这四个最容易被隔断的时刻，是提撕，不是装饰。\n• 致良知：软件只摆问题、下判的是用户自家良知。完成页问「照这条原则做了吗」，答「没考虑」即撤关联、不进统计——「只是未知」的产品形态。软件不替你致良知，它只做提撕；说它「帮你致良知」是借名，本页不用这个说法。指数同理：它是对账的仪表，本页不用「量化知行合一程度」这类说法。\n• 省察克治：自检是省察的日常版；克治对的是私欲之念而非一切离题——假分神放行、真分神才拦；严的一面由清醒时的预先承诺定地板，与阳明补偏救弊同构。「一念发动处，便即是行了」——全屏检查与问询大窗抢的就是念头刚发动、还没成势的那一刻。\n• 事上磨练：「人须在事上磨，方立得住。」习惯系统就是事上磨练的排期与记账，在指数里占一半权重，次序与心学相符。\n也要诚实指出缺口与借名：\n• 良知层最薄：践诺（把承诺做到）已成，省察只有雏形，良知层——看清这些落差共同指向哪条「知」尚未真——按现行定案尚无功能；「知」页目前是收纳与索引，还不是回望层。\n• 一念记未落地：每晚对念头账的省察还没有账可对，现有的省察只靠被守卫抓时被动产生的自检，战场停在行为层、没进到念头层。（按：记念头成簿是刘宗周等后学的发展，不是阳明原说，本页不冒充。）\n• 克治的「克」仍靠外力：全链校准的是判定模型准不准，不是用户良知的敏锐度；以「被抓次数下降」为目标的回望设计还没有。\n• 两处不冒充：晨间页是收心与日省，不是静坐（阳明明说静坐只是初学权法）；任务配原则是 AI 的外部匹配，与「良知不假外求」方向相反，只能称良知的外置索引与提撕工具。"},
        {"二十九、给专家看（三）：设计原则",
         "这一节收知行全部设计原则的简版，是给想理解（或想接着做）这款软件的人看的：\n1、意义先行：通过意义改变行为——让用户的志与原则去劝他，软件做意义的使者，不做数据的主人；被看见、摩擦、问责只作脚手架。\n2、两本账、一件事：知与行不是两个功能模块，检出落差、补上落差，才是产品本体。\n3、诚实记账：分神就是分神——宽限、自检、误判申诉都如实留痕；计数口径写成契约，不随界面改版漂移；改目标不翻旧账（睡眠目标、习惯目标皆然）。\n4、预先承诺高于临场判断：清醒时定的难度档、志、原则是地板，AI 只能在地板之上分真假、给宽松，不能赦免真分神。\n5、摩擦用在刀刃上：该拦的拦到底，不该拦的一步放行；每一次打扰都要挣得它的注意力。\n6、隐私红线：截图原图从不落盘、从不上传，只留文字事件（唯一例外：用户自开的黄图问责邮件，只发内存打码图给自设收件人）；判定只许文字出手机，且供应商不得留存、不得训练；每个账号的数据在数据库层隔离。\n7、本地先行：没网先存本地、进队列、有网补传，绝不让一次网络失败丢掉用户输入；用户亲手的数据（任务、日记、习惯、原则）优先级最高、绝不按时间删除。\n8、AI 只提案、人拍板：AI 重排点「应用」才生效；AI 任务做到最后一步就停，不可逆的一步等用户确认；AI 改设置、删任务一律先出提案卡。\n9、不惩罚、只对账：没有罚钱、没有羞辱性公示；催促有档位、静音有出口，严厉程度由用户自己选。\n10、东方极简：宣纸米白、墨青黑、朱砂红一点；字大而疏、留白——界面本身要让人静下来，而不是又一个让人焦虑的效率工具。\n11、开源与可接续：CC BY-NC-SA 4.0，衍生必须同样免费非商业；数据格式跨实现稳定，换实现不丢账。"},
        {"三十、给专家看（四）：程序员——架构与数据设计",
         "这一节是给程序员看的；普通使用不需要读。\n• 客户端：原生 Android，Java / Views（无 Compose；健康读取有一座官方 connect-client 的 Kotlin 小桥，是全仓唯一的 Kotlin 文件），Gradle 8.7 + AGP 8.5.2，JDK 17，SDK 34。分层为 TaskStore（数据）/ SbSync 等同步层 / TimerService（计时）/ UI，数据格式跨实现保持稳定。\n• 计时可靠性：倒计时存绝对结束时刻而非剩余秒数，前台服务保活，AlarmManager 精确闹钟在过半/结束点兜底；全屏检查走全屏意图（熄屏）＋上层显示（亮屏）双路径。\n• 同步：Supabase（PostgreSQL + Realtime），任务按 id 对齐、新者胜合并、墓碑删除防复活；跨设备只有一台逻辑计时器，状态在云端、两端渲染、任一端可控。写入一律字段级更新，不整行覆盖。流量纪律：免费版出流量很紧，发现与筛选一律服务端过滤＋只取轻字段。\n• 守卫判定：端侧预筛（OCR、端侧图像分类、本地语义模型）＋云端小模型终判（只收文字特征、供应商不留存不训练）＋无网时本地兜底的混合架构；判定留痕只增字段，不改既有事件语义。计数与语义口径有六个红线文件（知行指数、时间账、自检、语义判定、习惯数据、逻辑日界），任何版本零改动或经作者亲批。\n• 多 Agent 协作开发：这款软件由多个 AI Agent 协作写成——作者是产品与最终裁决者，Muse 任秘书、审 PR 并负责发版，Grok 团队负责大部分功能代码，协作走 Agent Guild（一个话题一个 Issue）。工程纪律写在仓库 AGENTS.md：每个主要功能单独一版、动手前先列任务清单、修 bug 的版本必须在模拟器里实跑验证、交付分「亲自验过的／开发方报告的／还没验的」三层写。\n• 自更新：App 每天至多自查一次新版，从私有仓库下载约 5MB 分片、逐片与整包双重校验（大小＋MD5）后才提示安装。\n• 姊妹项目：Mac 版（github.com/gty3310/knowdo-mac）与安卓共用同一账号、同一份任务与同一台逻辑计时器；改数据契约前必须先想 Mac 端会不会被打坏。立项期的完整设计研究在仓库 docs/RESEARCH.zh.md，守卫的设计迭代史在 docs/GUARD-DESIGN.zh.md。"},
    };

    private static final String[][] HELP_EN = {
        {"1. What KnowDo is (read this one section and you can start)",
         "In one sentence: KnowDo is a task app that helps you do what you said you would do — and keeps the books on it. You write down what you will do and how long it gets; tap Start and a countdown holds you to it; drift, and it brings you back; at the end of the day it lines up what you promised against what you actually did.\nWhy the bookkeeping? Because most people do not lack knowledge of what to do — they know, and still do not do it. KnowDo keeps two ledgers: Knowing — what you have committed to (tasks, habits, your aspiration and principles); Doing — what actually happened (timing, completions, drift records). Its one core job is to find where the two ledgers disagree, show you the gap the moment it opens, and help you close it.\nYour first day, in five steps:\n• One: on the Act tab, tap + and write down one thing you will do today, with a length (say 25 minutes);\n• Two: tap Start on it — the countdown runs; put the phone down and work;\n• Three: at half-time and at the end, a full-screen page asks \"Did you finish?\" — tap done, or add a few minutes and keep going;\n• Four: when a task ends, it asks \"What next?\" — do not leave the gap empty: pick the next task, or pick a timed rest;\n• Five: tomorrow morning, fill in the morning page first (yesterday's habits, one line of diary), then start the new day.\nThat is all. The sections below are a reference to look things up as you need them, not a book to finish. The last four sections are written for experts; everyday use can skip them.\nLocal first: data lives on the phone first and everything works offline; sign in and it syncs to your own cloud account, shared between phone and computer. Open source: github.com/gty3310/knowdo, licensed CC BY-NC-SA 4.0 (anyone may build on it; derivatives must stay free and non-commercial; letting the author know first is a courtesy, not a license condition)."},
        {"2. Who KnowDo is for",
         "KnowDo is built for four depths of the same road. Start at your own depth; the deeper layers wait for you, nothing has to be switched on all at once:\n• Hold the brake — a distraction-prone, easily scattered mind (ADHD-like, without pinning a diagnosis on anyone): external structure that helps you control phone use; the guard is an external brake, not behaviour control.\n• Keep it together — efficiency-minded people who want the drifting to stop: no scattering between tasks, no floating during them, no time lost to scrolling apps.\n• Make it stand — people building good habits: the habit system, the morning page and the Zhixing index turn \"what I want to become\" into a daily reckoning.\n• See it through — people who want to bring self-cultivation into daily life: the two ledgers, principle companionship and daily reflection, so cultivation does not stop at reading and lectures but lands on each concrete task."},
        {"3. The five tabs and the Today tab",
         "The bottom bar has five tabs, in a fixed order: Today / Act / Know / Oneness / Chat. The app opens on Today.\n• Today: a morning check-in card (tap to fill today's morning record), one advice card for the day (what to start first, what should not wait, important unanswered mail; the cited task can be started from the card, and a reply can be drafted and handed to your AI app — never sent for you), today's diary in full, this morning's recommended principle, the health overview (see section 16), and the entry to the day planner.\n• Act: the task table itself (see section 4); its header is one row — the page name, two compact figures (Must-do · Free), and Plan / Calendar buttons.\n• Know: the page that holds your aspiration, principles, habits, diaries, questions and people (see section 12).\n• Oneness: the weekly reckoning of knowing and doing — the Zhixing index, trends and the gap list (see section 15).\n• Chat: the built-in AI chat (see section 21), with a small vermilion badge counting unread AI messages; opening the tab clears it.\nThe ☰ drawer at the top-left of the first four tabs holds account and sync status, language (Chinese / English), theme (light / dark, can follow the system), and the Help and Settings entries; language and theme can also be changed in Settings."},
        {"4. Using the task table",
         "Tap a task name to expand its row in place (accordion — one row at a time). The expanded area shows the note, start time, time used, a summary of the focus rule, and the Start / Pause / Resume, Edit, +15 min and Done buttons:\n• Edit gathers every field: name and priority (★ Must, sorted first), context tags (multi-select — Online / Offline / With AI… — custom tags allowed), deadline and start time (pick a date, or tick Daily repeat; you can also set a finish-by time for the day, and a \"next start time\" — when it arrives, a full-screen reminder offers Start now, Snooze 10 minutes, or Ignore), planned length (you can also enter actual time used, shown in the list as \"实X\"), the focus rule (see section 17), the note, and a timer reset;\n• Table cells are tappable too: Start, Deadline, Length, Priority and Context each open their own editor; the leftmost checkbox marks the task done (the row greys out and is struck through);\n• Columns are yours: drag a header's right edge to resize; the gear at the far right of the header (or Settings → Display) chooses which columns show (Task, Start, Used, Left, Length, Deadline, Priority, Context). Both are remembered on this phone;\n• Long-press a row: mark done / delete the task;\n• Order: unfinished first, Must first, nearest deadline first. Daily-repeat tasks record completion per day — finishing yesterday's does not make today's disappear."},
        {"5. Timing and the full-screen check pages",
         "Tap Start in the expanded row to start timing. If another task is already timing, tapping Start on a new one switches straight over: the old timer stops, its progress is kept, and that is not a pause — tap its Start any time to continue. A pause (water, bathroom — a break in the middle) only exists for the task currently timing, and it asks for a purpose and a length first (see sections 8 and 19).\nWhile timing, a countdown lives in the notification bar. The countdown stores an absolute end time, so it does not drift if the process is killed, and exact alarms back up the half-time and end points. At half-time and again at the end, a full-screen check page takes over — via full-screen intent from the lock screen, or an overlay path on an unlocked screen; it is the same page either way.\nOn the end page, confirming \"Done\" writes back the completion; a task carrying a main principle also asks whether you lived it (see section 12). Not done? Extend: +50% / +5 / +10 / +15 minutes or a custom amount — every extension first passes the motive gate (see section 20). The check pages support a keyboard: Enter is the main button, Q / W / E are the first three extension options.\nA task can also carry an alarm: ring at a set time (independent of any timer; daily tasks re-arm for the next day), or ring when its timer ends, before the check-in. The default sound is a nature recording that fades in from silence over 60 seconds on the alarm audio stream, so it rings even when the phone is muted. When a task's planned start time arrives, a full-screen reminder offers Start now, Snooze 10 minutes, or Ignore."},
        {"6. Subtasks",
         "A big task can be split into a queue of subtasks, timed one by one.\n• How to split: expand the task on the home page — there is a Subtasks area; the large floating window has the same area. Tap \"+ Add subtask\" for a new row, give it a name and minutes; tap any row to rename, change its minutes, or delete it; drag the handle at the row's start to reorder.\n• Total = the sum of the subtasks: once a task has subtasks, its planned length is automatically their sum; change a subtask's minutes and the total and the schedule follow. Editing the task's length directly is refused then — edit the subtasks. Adding a subtask mid-timing starts timing it at once; the queue adjusts on the spot.\n• Checks follow the subtask: each subtask's end raises its own full-screen check page — Next starts the next one, Extend adds time to the current one, Skip jumps ahead, Finish task ends the whole task early. With subtasks there is no task-level half-time check; the last subtask's end is the task's end check.\n• Display: while timing, the medium floating window's main line shows the current subtask and its remaining time, with the task name small underneath; the expanded row shows \"Subtask 2/3 · current name\", or the whole queue before starting. Pausing freezes the current subtask's remainder; resuming continues it. Calendar and schedule still show the task as one block."},
        {"7. The floating window (always on, three modes)",
         "The floating window is always on: once you have opened KnowDo (and \"Display over other apps\" is ✓ on the Permissions page and the floating-window switch is on), it stays, always showing your current first priority — the task being timed first, then a just-paused task, then the top unfinished task in the list (Must first, nearest deadline first). With nothing timing, you can start or resume straight from the window. When a full-screen check page appears, the window steps aside and returns when it closes. The three modes switch into each other and the mode is remembered:\n• Docked: a small tab on the left/right screen edge showing only minutes (timing/paused = minutes left, waiting = planned minutes, rounded up, capped at 99+). No buttons, no pausing. Tap to expand; drag along the edge to change height; drag away from the edge to expand as well.\n• Mini: a translucent small window — task name + time + ▶/⏸ + a light-grey \"✎\". ▶ starts/resumes; ⏸ does not pause directly — it opens a menu to pick when to be reminded (1/3/5/10/15/20/30/60 minutes, 2 hours, or custom), and at that time a full-screen page calls you back. Tapping the content area or \"✎\" enters edit mode with the keyboard up; drag to move; drag to a screen edge and release to dock.\n• Edit: a fuller, more opaque card — task name, big time, status, and the note field (the field is the note). Type in the note and it saves about a second after you stop (and syncs when online). On the time row, the big number is time left; the small text is \"used / planned total\" — used is this task's accumulated timed time on this phone, planned total is the current goal including extensions. Buttons: Start/Pause/Resume (pausing asks for a reminder time), AI, Done (only while timing; raises the end check page), Open KnowDo, Collapse.\n• The AI button copies the task's full information (name, contexts, deadline, length, priority, focus rule, current state, note) to the clipboard and opens the AI assistant you chose on the Permissions page: if it supports share targets the text lands in its input box, otherwise paste after it opens.\n• The Copy button at the note field's top-right does the first half of the AI button — copies the task's full information — without opening the assistant. The note and focus-rule editors in the main UI have the same Copy button, copying what is in the editor at that moment.\nThe Permissions page has \"Preview floating window\" — a 10-second look without starting a timer (if the resident service is running, the preview is covered by live content; that is normal).\n• Timed close: the large card has a Close option — come back in 10/15/30/60 minutes, when the current task ends, after a custom number of minutes, or \"Never show again\" (the same as turning the floating window off in Settings; turn it back on there). Timed returns fire on schedule even across reboots; while hidden, timing, guard pages, alarms and notifications all continue as usual."},
        {"8. No gaps: the between-task guard and pauses",
         "KnowDo's rule: at any moment some task is running — there is no such thing as a stateless gap. Only three states exist: a task is timing; a pause is running (always with a length, called back full-screen when it ends); or the \"Next task\" page is being filled in.\n• The gap asks what's next, full-screen: after a check page confirms completion, after you finish a task by hand, when you open the app, or when the floating window's patrol notices — whenever nothing is timing and no timed pause is active, \"What's next?\" appears full-screen: a name, a length (quick buttons 2/3/5/10/15/20/30 minutes, or day/hour/minute wheels), the total minutes and end time shown live; tap Start and the task is created and timing. Below, today's unfinished tasks are listed — tap one to start it at its planned length without recreating it.\n• Rest is a task too: the Rest shortcut fills the name in as Rest — pick a length and time it. Sleep is the same: create a \"Sleep\" task to hold the night. There are no silent stretches.\n• A pause needs a length — and first passes the pause card: pick what you are going to do (bathroom, water, food, a call back, called away to help, a nap, a change of scene, class, or custom), then for how long, with a preview at the bottom; only \"Start pause\" stops the timer. During a pause the guard keeps working, judging against the purpose you wrote (see section 19). There is no indefinite pause.\n• A pause ending is a gap: the call-back page asks full-screen whether you are coming back; 60 seconds with no response (resume / rest more / switch task) and a 60-second birdsong alarm fades up — any action stops it. Choosing not to continue drops you straight onto the \"Next task\" page, closing the loop. A muted pause is the exception: with mute on the card, the end only lights the screen with a notification, no sound, repeating every 5 minutes until you respond (choosing \"Class\" turns mute on automatically).\n• Gap nagging is a personal setting (Settings → Guards → \"Gap nagging\"): Gentle = a finished task just ends, no page, no sound; Standard = full-screen reminders for a limited number of rounds (3 by default), then quiet, never any sound; Strict = the page appears, vibration first, sound fading up only if you do not react, repeating until you start a task — only this tier has a sound switch; Custom = every number is yours. The setting syncs to your account and is shared with the computer.\n• Devices cooperate: if another device (say the Mac) is timing or on a timed pause, this one will not pop by mistake. The between-task guard can be turned off under Settings → Guards (the pause rule is unaffected). Backing out of a guard page buys 3 quiet minutes to browse the list or change a setting; pages triggered by definite moments like finishing a task ignore those 3 minutes.\n• Passer-by mute: at the bottom of the alarm page is a big button, \"If you are not the phone's owner, tap here to mute\" — anyone nearby can silence a ringing phone until the next screen-on, without touching your tasks; on your next unlock you see a thank-you page and can continue the task or pick a new one.\n• Pick a task on unlock: with nothing timing and no timed pause running, unlocking the phone asks full-screen \"What's next?\" (the morning page and the thank-you page outrank it; during a call it waits and catches up after). Tap a today-task row to start it, create a new one, or choose \"Rest a while\" — rest is a real arrangement too: pick a length, it times, and it calls you back."},
        {"9. The schedule and the calendar page",
         "Your day's schedule lives in your cloud account; the calendar page reads it to draw the Plan lane. There are three ways it gets made:\n• Automatic planning: must-do tasks not yet placed today are fitted into gaps at planned length ×1.5 (a 50% buffer), never past 22:30; blocks already placed, already past, or belonging to finished tasks are never moved.\n• \"AI Re-plan\" on the calendar page: the built-in AI drafts a new plan on the spot under the same rules and shows it to you — it lands only when you tap Apply; No discards the whole draft. Beside it, \"Quick Re-plan\" re-plans instantly without AI in a fixed order (short due tasks first, then Must, due Important, due Secondary, not-due Important, not-due Secondary; Leisure is never auto-planned), leaving the running task, done blocks and hand-dragged blocks untouched, with one-tap Undo.\n• By hand: long-press a plan block to drag it to a new time, or drag an unplanned task into the calendar; hand-dragged blocks become fixed blocks.\nAll three obey the same hard rules: every task occupies planned length ×1.5; the priority order is fixed (Must > due Important > due Secondary > not-due Important > not-due Secondary) and tiers never change automatically; nothing is scheduled past 22:30; if something does not fit, the app says so instead of squeezing it in.\nThe calendar opens inside the Act page: the task list on the left, the calendar on the right, the five bottom tabs always reachable — switch away and back and the calendar stays open; drag its left edge to set its width, drag it very narrow and it closes. The timeline has three lanes: a narrow Calendar lane on the left (your Google Calendar events, start time + title only, read-only reference — planning does not work around it), the widest Plan lane in the middle (the scheduled blocks, light outlined blocks — the star of this page), and a narrow Actual lane on the right (the blocks you really timed, solid); a task done in pieces shows as several blocks; finished tasks grey out, the timing one is highlighted, and a red line marks the current time. The header states how many of today's tasks are done and not done, and how many free minutes remain between now and 22:30 — one glance tells you how tight the day is.\nPlan blocks can be moved by hand: long-press to pick one up (it grows slightly), drag up or down, release — snapped to 5 minutes and saved to the cloud at once; only that block's start and end change, and later re-plans treat hand-dragged blocks as fixed. Drag an unplanned task rightward into the calendar: an preview block with start and end times follows your finger, and where you release is where it is scheduled (5-minute snap), occupying planned length ×1.5. Drag back out to the list or off the calendar to cancel — nothing is written. A task already scheduled shows a small \"Planned HH:mm\" mark on its row and can no longer be dragged; move its block in the calendar instead.\nPlan blocks and unplanned task rows carry a ▶: one tap starts that task's timer (the floating window switches to it); the timing one shows ⏸, tapping pauses — the same start/pause as the main UI. Tapping any plan block (task block) locates and expands that task in the list on the left, where Start/Pause, Edit, Alarm, Extend and Done all work. Google Calendar blocks only show their full name when tapped."},
        {"10. Sync, accounts and the cross-device timer",
         "KnowDo syncs through the Supabase cloud database: in the drawer or under Settings → Account, tap Account and register or sign in with email + password. On first sign-in, this phone's existing tasks become your cloud data in full (nothing is lost, nothing is emptied); after that, phone and computer share one task list — change either one and the other follows within seconds, with a full reconciliation every 30 seconds as a backstop. Merges align by task id with newest-wins, and deletes use tombstones so nothing resurrects. Offline, everything keeps working on this phone; changes queue and merge automatically (push first, then pull) when the network returns. The drawer's account area and Settings show the sync state (Not signed in / Synced / N changes pending / Syncing). Signed out, only local features work; nothing syncs.\nTiming is cross-device too: start, pause, resume, extend or end on any device and the other immediately mirrors the same countdown (start instants align strictly; both ends raise the check page at the same moment); whichever end handles the check page first (confirms done or extends) settles it for both — the other end's open check page closes itself instead of double-asking.\n\"Auto-start\": when a task arrives carrying an auto-start flag (for example, you said \"for the next N minutes do X\" in a conversation and an assistant outside the app filed it into your cloud), KnowDo receives it and starts timing at once; if a timer is already running, it switches straight over.\nEach device only ever sees its own account's data (isolated server-side per account); signing out keeps the local data, it just stops syncing. The calendar page's Google Calendar events are a read-only display synced to the cloud. The Mac app is a sibling project sharing the same account, the same task list and the same single logical timer with the phone."},
        {"11. My aspiration and the self-check",
         "My aspiration: under \"My aspiration\" at the top of Settings, write your aspiration in your own words — each person sets their own; it belongs to you alone.\n• When it appears: on the drift inquiry page, the task-end check page, the subtask check page and the pause call-back page — a line of gold text, your aspiration. It is not a button and cannot be tapped; it is simply there for you to see.\n• If you have not written one, those pages show nothing extra — no placeholder, no nagging. Change it any time in Settings.\n• Signed in, the line syncs into your account's cloud data and follows the account: sign in on another device that has none and the cloud line is adopted; edit it in the app and it writes back to the cloud.\nThe self-check turns every caught drift into a question to yourself.\n• At the top of the drift inquiry page sits one question: \"Were you just doing what you should be doing?\" Tap [I was] or [I wasn't]. The answer is recorded at once; not answering never blocks you — the page's return, grace and release buttons work as usual, no quiz first (on the current inquiry page, taking a grace itself also records one drift — see section 19).\n• Records live on this phone and sync to your account's cloud: the count of \"I wasn't\" answers feeds the Oneness tab's drift statistics and the Zhixing index's drift-control part — which days you are most likely to know and not do becomes visible at a glance."},
        {"12. The Know tab: Aspiration, Insight, Precepts, Reflection, Questions, People",
         "The Know tab gathers your \"knowing\" into six groups; while writing, you can cross-link them with {{ }} references:\n• Aspiration （志）: your aspiration (see section 11) and your values — direction lives in exactly one place.\n• Insight （明）: thinking principles — your standards for how to think.\n• Precepts （戒）: habits (see section 13) and conduct principles — your standards for how to act.\n• Reflection （察）: diaries, motive tallies (see section 20), and a read-only list of slips — looking back at yourself.\n• Questions （问）: questions you have not settled and want to ask someone, starrable by priority.\n• People （人）: the people connected with these things.\nPrinciple companionship: a task can carry one main principle, shown on its row and while it runs; when you finish the task, the end page quietly asks whether you lived it — you did, you did not consider it, or you did not do it. Answering is optional and never blocks finishing; but answer \"didn't consider it\" and that principle's link to the task is withdrawn and never enters the statistics: a principle you never thought of while doing the work does not count as knowledge you have. Where principles come from: you write your diary first; the AI distils candidates from it; only after you edit and settle each one does it enter the Know tab as a formal principle — unsettled candidates never count."},
        {"13. Habits",
         "Habits live in the Know tab's Precepts group — your long-term ledger: whether one task got done is a day's business; habits are about the person.\n• Five goal types: At-least — do it on at least X days a week; meeting the goal is 100%, more is still 100% (capped). At-most — at most X times a week; fewer is better, meeting it is 100%, overshooting scores goal ÷ actual. Daily number — enter a number each day (e.g. 6,000 steps); a day scores actual ÷ goal (capped at 100%), the week averages. Cumulative — log an increment each day (one good deed at a time); the heart of it is the running total since the start date, with an optional grand total to progress toward; the Oneness tab reckons the week as this week's increment ÷ weekly increment goal (any increment at all counts 100% when no weekly goal is set); a cumulative habit can also carry a starting number, so a count begun before KnowDo continues from where it actually reached. Daily reminder — no count goal at all; its heart is a passage you write to yourself: read it and the day counts as done (read 100%, unread 0%; the weekly rate is days read ÷ days elapsed this week).\n• Adding a habit: tap \"+ Add habit\" and pick from the default library (early rising, standing practice, sitting meditation, meditation, scripture reading, diary writing, exercise, no late nights, less phone scrolling, steps, good deeds — types and default goals prefilled), or build a custom habit; then choose the type, set the goal (a daily reminder's passage is required), and set a reminder time. After you add your first habit, a one-time suggestion card at the top asks whether to enable the morning check-in as well; tap \"No\" and it never appears again.\n• Each habit can have its own reminder time: at that time a local notification arrives and tapping it opens the habit page; changing or turning off the time takes effect at once and survives reboots.\n• Each habit card carries a year grid (the last 365 days; columns are weeks, rows Monday to Sunday): tap any cell to back-fill or correct that day; tap the habit's name to change its name, goal or reminder, or delete the habit (its records go with it). Cards also show this week's progress and statistics (days this year / current streak / longest, varying a little by type). One global setting (Settings → Habit display window) chooses whether every card's main count reckons by today, this week, last week or this month. The Precepts group also has a compact mode — one line per habit with this week's seven dots and, on the right, how far the habit still is from this week's goal.\n• Habit data (definitions, daily records, morning diaries, morning settings) lives in your account's cloud, shared between phone and computer; offline it is stored on the phone first and merged when back online, local copy first, and it is never deleted by time."},
        {"14. The morning page: meaning first, numbers after",
         "Every day starts at the morning gate. Enable it under Settings → Morning; after the \"new day starts at\" time (04:00 by default, adjustable) each day, the first time you pick up the phone (unlock it), the morning page appears full-screen over everything — no need to open KnowDo first (opening KnowDo first lands you on the same page; during a call it does not intrude and catches up after you hang up or at the next unlock). The page has no Back button; the day unlocks only when it is filled. Since v2.0.0 the order is meaning first, numbers after:\n• The meaning section (first): your aspiration, and your \"line for tonight\" — one small if-then plan for tonight (e.g. \"if I am still scrolling at 22:30, I go wash up\"), with a gentle nudge to rewrite it every Monday;\n• Last night's numbers: when you got to bed, how long you slept, whether the goal was met (read automatically with health permission — see section 16; otherwise fill bed and wake times in by hand and it still books);\n• Tonight's confirmation: tonight's bedtime — tonight only, it never writes your settings;\n• One HRV field (typed in by hand from your watch app; leave it blank and it counts as missing);\n• A — Yesterday's habits: recall each one — At-least and At-most types tap \"Did it / Didn't\", number types enter yesterday's number, cumulative types log yesterday's increment, daily reminders read the full passage first, then tap \"Read / Didn't read\"; blanks do not count, everything must be answered. With a daily-reminder habit, a \"Today's reminder\" section follows A: today's passage is right there — read it, tap \"Read\", and today's habit counts as done;\n• B — Yesterday's mini-report: read-only — yesterday's index on a single-day basis, promises kept and missed, yesterday's drift count and self-check state;\n• C — Morning diary: required, one sentence is enough, filed under the new day.\nThe countdown: the first time you open the phone in the morning, a 10-minute morning check-in countdown starts automatically; you can extend it, or pass and do something else first — leaving the page never stops it, and it runs until the check-in is settled.\nWhen everything is filled, \"Start today\" lights up: habits are booked to yesterday, the diary to today, and the page will not appear again today. Every step saves immediately as you go — leave midway, or have the page closed, and you resume where you stopped.\n• Pause a while: a real emergency in the morning (an alarm to silence, an urgent message, a call) — tap \"Pause a while\" at the bottom and pick 5/10/15/20/30/60 minutes or a custom length; the page steps down and returns full-screen when the time is up, as many times as you need. Opening KnowDo during the pause also brings the page up early.\n• Not finished and no pause length chosen: Home, Back, or switching to another app do not count as postponing — the page returns full-screen every 5 minutes until you finish and tap \"Start today\", or explicitly choose a pause length. What you filled in stays saved.\n• Test the morning page: Settings → Morning has a \"Test morning page\" button — open the real page in formal mode any time of day (ignoring the time and whether today is done), with real data; finishing it counts as a normal completion, or leave via \"Pause a while\".\n• Back-fill / edit: the Know tab's Precepts group has a \"Morning records: back-fill / edit\" entry — the morning page in edit mode: you can leave with Back, need not fill everything, and can switch the record day at the top (today / yesterday / the day before, or any date). Editing past records there does not count as completing today's gate; editing today's round there still lets the gate appear as usual, prefilled with what you saved."},
        {"15. The Zhixing index and the Oneness tab",
         "The Oneness tab lines up a week's knowing and doing into one account, centred on a 0–100 Zhixing index:\nIndex = Habits ×50% + Task completion ×35% + Drift control ×15%. It is a reckoning instrument, not a grade of you — it shows which ledger fell behind, not what you are worth.\n• Habits: the average of all habits' weekly attainment rates (each habit scored by its own goal type — see section 13); with no habits yet, it starts at full marks.\n• Task completion: completions ÷ total among this week's due/scheduled tasks — non-daily tasks count by deadline, daily tasks by day (a day done counts a day), blocks placed in the calendar count too; only Must tasks count, AI tasks and tasks skipped today are excluded; with nothing due this week, it starts at full marks.\n• Drift control: this week's drift count = self-check \"I wasn't\" answers + guard events; the score is 100 − 5×count (floor 0); with no data, full marks.\n• The page: the big index ring (with its change vs last week), the three components (each with its change vs last week), a per-habit this-week vs last-week comparison, a gap list (habits falling behind, overshot At-most habits, unfinished Must tasks due this week, and the like), and next week's goals — one stepper per habit, saved as you change it, effective next Monday; leave it alone and this week's goals carry over.\n• Tap the index ring for the trend page: three switchable charts — the index itself, principle practice, and the \"pure motive\" share — by month or by year, backed by per-day score records the app seals locally each day and syncs to the cloud."},
        {"16. Health and the recovery score",
         "Since v2.0.0, KnowDo reads health data itself, with no relay in between. Authorize once under Settings → Health and it reads four things straight from Health Connect on your phone: sleep (with stages), resting heart rate, steps, and active calories. Reads happen when the morning page opens and when you enter the Today page; there is no background polling.\n• Authorization: tap Authorize and the system grant page opens — switch the four on. KnowDo also appears in the system's Health Connect app-permissions list, where you can change or revoke the grant at any time. The Health section in Settings also has an \"Open Health Connect settings\" row and a small diagnostics line (status code and most recent error) — look there first when something is wrong. Declining or revoking permission leaves every other feature untouched.\n• Sleep goals are yours to set: bedtime (default 23:00), wake-up (06:00) and nap length (60 minutes) under Settings → Health, and every goal judgement in the app reads that one setting. Changes count from now on — recorded days are never re-scored.\n• Booking rules: a night's sleep belongs to the logical day (04:00 boundary, the same line as habits) of its end time; the longest session of the day is the main sleep, shorter ones count as nap; duplicate sources for the same stretch keep one record, Garmin Connect preferred. Goal met = in bed no later than the goal and at least 90% of the goal window slept.\n• No watch or no permission: fill bed and wake times in by hand on the morning page — it still books, and still scores.\n• The recovery score: the Today page shows last night's sleep, a recovery ring, steps and the nap. The score is KnowDo's own algorithm — six weighted parts (sleep duration 35, bedtime 25, deep-sleep share 15, resting heart rate 10, HRV 10, yesterday's activity 5); tap the ring for the full formula and each part's points. A missing part has its weight redistributed across the rest and is named as missing; with under 7 days of history it says the baseline is still building. The recovery score never feeds the Zhixing index — the two accounts stay separate.\n• HRV is hand-filled for now: Garmin does not pass HRV to Health Connect, so the morning page has one field for last night's average from the Garmin app.\n• Privacy: raw records and sleep stages live only on this phone and are never uploaded; the only thing that syncs is a one-line daily summary (bedtime, duration, goal met or not) into your own account. A weekly rollup (goal-met nights, average bedtime, average duration) is computed as data only, for your own weekly writing."},
        {"17. Focus guard (watches which app you're in)",
         "While a timer runs, KnowDo checks the foreground app every 2 seconds via usage access (with a faster recheck whenever the window changes); if it does not match the current task's focus rule, a full-screen inquiry page appears.\nThe rule is set in the task's focus rule (expanded row → Edit → Focus rule): a natural-language rule (written for humans, e.g. \"Scripture reading only in the recitation app\") + a primary app + the per-task app list. Be clear on this: since v1.94.0 KnowDo has no blacklist at all — no app is \"blocked on sight\"; the list has only white and grey tiers (below), and verdicts rest on screen content and semantics, not on app names.\n• Per-task app lists: when a task starts, your commonly used apps are sorted for this task, in the background, into white and grey tiers — whitelisted apps no longer trigger focus-rule inquiries during that timing; grey adds no new interruptions and is judged by the screen-content guard as usual (see section 18). Open \"App list\" in a task's details to see the two tiers and re-tier any app — hand edits are marked as yours and never overwritten by later generations. The lists live only on this phone (a local file, never synced to the cloud). The master switch is Settings → Judging & lists → \"Task app lists\"; with it off, no requests are sent and no list-based release applies. Note: automatic classification currently serves the author's own account only; other accounts have no app lists for now (see section 26).\n• When you are stopped, \"close it\" does not mean going home — it sends you back to the app you should be in: the primary app if one is set (scripture reading jumps to the recitation app), otherwise KnowDo's own \"current task page\" (task name + countdown + note + ways back to the task list or your AI assistant).\n• The inquiry page's choices: return to the task at once / switch to another task / take a grace — block the app now, or keep it for 1 / 3 / 5 more minutes, after which the disposition you picked lands: quit the app, send it to the background, or block it behind a 3-minute countdown page with no early exit on the page itself. The rules differ by difficulty — see section 19. Every grace taken records one honest drift event.\n• Never blocked: KnowDo itself, the launcher, input methods, and the AI assistant app you chose on the Permissions page (talking to your AI assistant counts as a meta-operation).\n• Limitation: it polls, so the delay is up to about 2 seconds."},
        {"18. Screen-content guard (watches what's on the screen)",
         "The focus guard can only see which app you are in — not shopping or short videos inside an allowed browser, or adult content. This guard covers that gap. Turn its master switch on in the \"Screen content guard\" section of the Permissions page (off by default; Settings → Guards has the same switch).\n• Sampling: it works only while a timer runs, the screen is on, and the phone is unlocked; it captures a frame at random intervals, at unpredictable moments. Screenshots come only from the accessibility channel (enabled on the Permissions page): once on, sampling during timing is automatic and never asks for a screen-recording permission again; with accessibility off, a whole session goes unsampled, with one line left in the log saying so. KnowDo never requests screen-recording permission at all.\n• How verdicts are made: screening starts on the phone — a small on-device image model catches adult/exposed content (by your definition, photos of people wearing very little count as violations), OCR reads the screen text (Chinese / Japanese / English), and a local semantic model scores the screen text against the current task. What the phone can settle, it settles on the spot; for the uncertain middle band, only extracted text features go to a cloud judge (whose provider may not retain the data or train on it; the screenshot itself never leaves the phone). A cloud verdict of \"unrelated\" with low confidence (below 0.70) is flipped to a pass — better than convicting the innocent. Since v1.94.0, app category and shopping/short-video keywords no longer convict directly.\n• False-alarm defences: two consecutive hits of the same kind raise the inquiry page (adult content can be set to trigger on one). The inquiry page is the same as the focus guard's (see sections 17 and 19). Every verdict enters the log and syncs as an event (time, category, confidence, one line of evidence text).\n• The privacy red line: screenshots are analyzed in memory and destroyed immediately — never saved, never uploaded, never sent to anyone (the sole exception is the accountability mail below, and it sends only a mosaicked image).\n• Known blind spots: banking apps, some paid video, and browser incognito surfaces are protected by the system and capture as pure black frames — the guard can only log \"not visible\" and cannot judge; swimming or fitness imagery may false-positive the small model; \"is this AI chat on-topic\" is the hardest call of all — when it cannot be judged, it is let go, never forced.\n• Accountability mail (off by default; enable under Settings → Guards): the one exception to the red line above. When the guard's final verdict is adult content, that one screenshot is heavily blurred and downscaled in memory, on the spot, and only the mosaicked image is sent to the accountability address you set, so someone can hold you to account; the original is still destroyed immediately, never saved, never uploaded. At most one mail per kind per 10 minutes and 5 per day; beyond that, events are logged but not sent. This is a test feature for now: the mails go out through the author's mail channel, and the author can see what is sent."},
        {"19. Guard difficulty, leniency and appeals",
         "The guard is not one-size-fits-all: how strict it is, you decide while clear-headed; when it gets a verdict wrong, there is a formal appeal channel.\n• Three difficulty levels (Settings → Guards): Easy is the classic flow — tap \"False alarm\" and you are released. Medium gates every grace behind a breathing pause (breathe quietly for a stretch; its length doubles with each grace you take on that task today, from 15 seconds, capped at 120; leaving the breathing page voids the grace). Hard replaces the false-alarm button with a written explanation judged by an AI — only an \"agree\" verdict lets you off — and rations grace to a small daily quota (1 by default). On all three levels the false-alarm route appears at every inquiry — whenever a real false alarm happens, you can appeal it.\n• Appeals: Easy releases on the tap. Medium and Hard send your written explanation to an AI review (the review includes the task's description, so tasks with special circumstances are judged more fairly); appeals work on the block page too; if the AI channel is unavailable, you may skip after waiting 5 seconds. Rejected? Rewrite the explanation and submit again. Your explanations are fed back into the judging, so it gets more accurate over time.\n• Real drift vs false drift (v1.98.0): the guard no longer looks at a single frame — it reads your activity stream over the last 3 hours (what you did in which app, for how long, stitched locally into a text skeleton at no extra model cost). A false drift = your main line was progressing well and you briefly stepped aside to handle another important thing, going short and returning fast — effectively a temporary task switch, and it should be let through. A real drift = the main line keeps getting interrupted, hopping around — even if every hop looks like proper business, it still counts as drift. Hence four leniency tiers: L3 false drift, clear = no popup, no drift recorded; L2 false drift, likely = a minimal inquiry page, one big button releases you — no explanation, no breathing gate, no drift recorded; L1 unsure / mild = the standard inquiry flow; L0 real drift, clear = a full stop: breathing first, then one small reflection question to tap an answer to, graces tightened — the point is not to fine you time, it is to break the autopilot state.\n• Two safeguards: the difficulty level you set while clear-headed is a pre-commitment and a floor — the AI only tells real from false; it cannot pardon a real drift, and L0's durations may not shrink. A local hard guardrail (zero model cost) caps leniency at L1 when your main-line share in the last hour falls below half, or after two L0 verdicts within an hour; the guardrail only caps, never tightens.\n• Stated plainly: full leniency needs the server-side judge to output a leniency level; that server half is not deployed yet. Until it is, verdicts fall back to L1, the standard flow — behaviour identical to earlier versions. The client half is already in place and will take effect automatically once the server side is deployed, with no app update needed.\n• The guard during pauses (v1.96.0): a pause is no longer a blind spot. Pressing Pause requires a purpose first — and it must say how (method + scope + boundary, not just a label). During the pause the guard keeps sampling and judges against that purpose: a catch is logged and raises an inquiry, but writes no self-check and does not count into the drift total; list mode is off during pauses; resuming or ending the pause switches it off.\n• The inquiry page is a big overlay (v1.97.0): it sits at the same level as the block and call-back overlays — it cannot be swiped away or dismissed with Back; you must choose a next step. If the process is killed, an unresolved inquiry reappears shortly after the service restarts, while the state still matches. On the lock screen, or without overlay permission, it falls back to the regular full-screen page."},
        {"20. AI tasks and the motive gate",
         "• AI tasks: a task can be assigned to \"my AI\" instead of yourself. An AI task never enters auto-scheduling, the time accounts or roll-over; the author's AI assistant works it in the background (research, drafting, tidying, checking) and always stops before the final step — sending a message or mail, submitting, paying, anything irreversible waits for your confirmation. When it finishes or gets stuck, the result is written back into the task's note and its state becomes awaiting your confirmation or your answer. For other users: this is the author's personal AI setup at work, not a built-in app feature (see section 26).\n• The motive gate: every extension, from any entry point, first passes one question — is the motive \"wholly for good, with no selfish intent\"? Any answer lets the extension through, but the answer is recorded honestly and feeds the Oneness tab's motive tallies (the trend page has a \"pure motive\" share). What it stops is not extension — it is unthinking extension."},
        {"21. Chat and batch-adding tasks",
         "Chat is the app's built-in AI conversation, working out of the box on the project's free model channel; every AI button in the app uses this same chatbot (see section 26).\n• Batch-adding (v1.99.0): name several things in one message and it lays them out on one numbered summary card — title, length, priority, deadline and repeat for each; fields you left out are filled with defaults and marked as such on the card; tasks meant for the AI are marked too. One tap on \"Create all\" builds the whole batch. Later, to change one item, quote its number from the card in chat and it can be edited on its own.\n• The unread badge: the Chat tab carries a small vermilion badge counting AI messages you have not seen (above all the proactive distraction-companion messages); opening the tab clears it."},
        {"22. What the Permissions page items are for",
         "• Notifications: the basis of the resident countdown and reminders;\n• Full-screen notifications (pop over the lock screen): what puts check pages on top of the lock screen;\n• Display over other apps: what lets full-screen pages appear directly on an unlocked screen, and the floating window;\n• Exact alarms: the backstop that still fires on time at half-time and end points if the system kills the app process;\n• Battery-optimization exemption: keeps the system from freezing the timing and sync services;\n• Usage access: what both guards use to see the foreground app;\n• Accessibility: the screen-content guard's screenshot channel — you switch it on yourself in system settings first;\n• Health Connect authorization: given under Settings → Health; covers the four kinds of health data (see section 16);\n• Also here: choosing which app \"Back to AI assistant\" points to, the floating-window switch and preview, and the screen-content guard's switch.\nSuggested settings: six items — battery-optimization exemption, floating window, the accessibility screenshot channel, full-screen notifications, exact alarms, notifications — are presented once after a fresh install or an upgrade, with a standing entry in Settings to re-check any time. All are optional: each one serves one feature, switch on what you use; leaving one off breaks nothing else, though the system may stop KnowDo in the background and mornings and reminders may be late. Xiaomi, Huawei and similar phones also need autostart and background-popup enabled in system settings (the app cannot detect those; a note at the bottom of the checklist says so)."},
        {"23. The Log page, what's-new, and your data",
         "• The Log page: every start / half / end / extension, every action of both guards, and every sync result is timestamped here, for looking back and reconciling.\n• What's new: after an upgrade, the first open pops a \"What's new\" box — the latest 5 versions stacked: what changed in each (grouped as New / Improved / Fixed), why, and for the newest, how to try it; scroll to read them all. It pops only once, on the first open after upgrading; a fresh install never sees it. Any time later: Settings → General has a \"What's new\" entry opening the same page. This help is kept current with the app, but stated honestly: it describes the app as it currently works, not a version-by-version changelog — for per-version detail, read the what's-new entries themselves.\n• Your data: change phones or reinstall, sign in with the same account, and your cloud data syncs back automatically. After a phone reboot, realtime sync resumes on its own; if it looks stuck, open KnowDo once. Task data exists twice — once on the phone, once in the cloud — merged newest-wins; the cloud copy is your backup. Questions or feature wishes: ask in Chat first, or contact the author."},
        {"24. Considered and deliberately not built",
         "1. A faster push channel — postponed. Realtime push plus a 30-second reconciliation is enough for now.\n2. Sending screenshots to a cloud AI for judging — decided against. Screenshots do not leave the phone, on privacy grounds; and cloud models' content policies refuse exactly the adult screenshots that would need judging most.\n3. Keeping original screenshots as evidence / uploading originals — decided against (a red line). A drift verdict leaves only a text conclusion.\n4. Sampling invisibly without the accessibility channel — cannot be done, and will not be. Screenshots come only through accessibility, switched on by your own hand in system settings.\n5. The screen-content guard running all day (sampling when not timing) — not for now. Battery and privacy costs both double; it guards timing sessions first.\n6. Money fines, public shaming and other punishments — decided against. KnowDo reconciles; it does not punish. Nagging has tiers, mute has exits, and how strict things get is your own choice.\n7. A large on-device multimodal model for deep judging (models from 3 GB up) — interface reserved, not enabled. It targets semantic calls like off-topic AI chat, but its power and heat costs are high; it will be connected when truly needed."},
        {"25. What's next (not built yet)",
         "Everything in this section is still at the design stage — none of it exists yet. It is here only so you know the direction; do not mistake it for current features.\nNext, KnowDo wants the AI in Chat to feel more like a companion living inside the app: able to speak first at the right moment (for example, asking you at a fixed time each evening what time you plan to sleep tonight — your answer becomes tonight's commitment, reckoned the next day), and able to bring the right page in front of you to talk things over when it works out that a task is about to miss its time. Further out: turning \"how to think and how to act in a given situation\" into training plans you can switch on or off — switch one on, and the AI practises it with you. Furthest out: a built-in AI that can watch your activity stream and prompt you when a prompt is due, and chat with you about it. None of this is implemented; when it is, it will appear in What's new."},
        {"26. For new users: what the AI can and cannot do",
         "KnowDo's AI comes in two parts. One part is inside the app: the Chat chatbot, working out of the box on the project's free model channel. The other part is outside the app: dictating tasks to an assistant outside the app and having them filed for you, having the cloud watched for a chance to re-plan, and having per-task app lists classified — those are done by the author's personal AI assistant, patrolling the author's own cloud account in the background. They are the author's personal setup at work, not features the app ships with.\nEvery AI button inside the app, for every user, uses the same built-in chatbot; there is no special channel for the author — in the app, the author uses the same AI as everyone else.\nFor new users, bluntly: the one and only AI you can connect to is the built-in Chat — there is no other option, and there is currently no way to connect your own external assistant. It cannot enter this app's cloud database, and no patrol is running for you. What you actually can use is the app's own local features (local auto-planning, on-device guard verdicts), plus Chat; the calendar's \"AI Re-plan\" is built-in too — it drafts a proposal on the spot, and the plan only lands when you tap Apply. App-list classification is the same story: it is the author's personal setup, generated in the background by the author's own assistant for the author's account; other accounts, for now, have no app lists.\nAs for data, rest assured: your tasks, lists and records live only under your own account. The author cannot see them, and neither can the author's assistant; nobody's AI can read anyone else's data. That is enforced by the database, not by a verbal promise."},
        {"27. For experts (1): the behaviour-change design",
         "This section is for readers who study behaviour change; it explains the design trade-offs behind the mechanisms. Everyday use does not need it.\n• External structure for an unreliable inner one: KnowDo's basic assumption is that white-knuckling it in the moment is unreliable, so the structure is moved outside — absolute end times, the no-gap three states, the morning gate, one logical timer across devices. The environment keeps the rules so you do not have to be disciplined every minute. The author himself once maintained focus for years with paid human supervision; this app is the latest generation of that strategy.\n• Pre-commitment: difficulty levels, the aspiration, principles and pause purposes are all commitments written while clear-headed; in the moment, the AI and the interface may only grant leniency above the floor those commitments set — they cannot pardon. The breathing gate, grace quotas and un-shrinkable L0 durations are pre-commitment's enforcement machinery.\n• Calibrated friction: friction is spent only where it cuts — what must be stopped is stopped fully (full-screen checks, an inquiry overlay that cannot be swiped away), and what should pass passes in one step (an L3 false drift never pops up; a false alarm is one tap to appeal). Every interruption must earn its attention; appeals and fed-back explanations are the calibration loop that makes friction converge with use.\n• Real vs false drift: the unit of judgement is upgraded from a single act to a 3-hour behaviour pattern — briefly stepping aside to handle something important is a task switch; constant hopping is drift, even when every hop is proper business. That is the fundamental difference from traditional focus apps that only look at the current screen.\n• The Atomic Habits mapping: the morning gate is \"make it obvious\" with a fixed trigger, habits stacked into the morning flow; tonight's line is an if-then implementation intention; the index and the year grids are visible progress feedback; the ever-present aspiration is identity (\"become this kind of person first, then count the doing\"). Deliberately absent: broken-streak shaming and money fines — feedback reconciles, it does not punish.\n• Meaning before weakness levers: being seen, friction and accountability are scaffolding, usable at this stage; the north star is changing behaviour through meaning — letting the user's own aspiration and principles persuade them, with the software as meaning's messenger, not the master of their data."},
        {"28. For experts (2): the Yangming mapping (for practitioners)",
         "This section is for readers with a Confucian, Buddhist or Daoist practice background; it maps KnowDo's mechanisms onto Wang Yangming's philosophy, checked against the Chuanxilu (传习录）, and says plainly where the mapping does not hold.\nYangming: \"There has never been knowing without doing; knowing without doing is simply not yet knowing.\" And: \"Knowing is the beginning of doing; doing is the completion of knowing.\" Knowing and doing fall into two halves only because selfish desire cuts them apart. So the gaps KnowDo records are never knowledge gaps — they are counts of the cut: drifts, self-check answers of \"I wasn't\", motive-gate answers of \"no\".\nFive places where the mapping genuinely holds:\n• The two ledgers + the Zhixing index are that proposition engineered: the books record only the cut, never ignorance.\n• Aspiration: \"If the aspiration is not established, nothing under heaven can be accomplished.\" (Jiaotiao shi Longchang zhusheng — not the Chuanxilu.) KnowDo's aspiration appears only at the four moments most easily cut off — the drift inquiry, the end check, the subtask check, the pause call-back. It is a reminder raised at the right moment, not decoration.\n• Extending innate knowledge (致良知）: the software only poses the question; the verdict is the user's own innate knowledge. On the completion page, answering \"didn't consider it\" withdraws the link and excludes it from the statistics — the product form of \"simply not yet knowing\". The software does not extend your innate knowledge for you; it only reminds. This page will not say it \"helps you attain innate knowledge\". The index, likewise, is a reckoning instrument; this page will not call it \"quantifying your unity of knowledge and action\".\n• Reflection and overcoming （省察克治）: the self-check is reflection's daily form. What is overcome is the selfish thought, not every departure from the task — a false drift is released, a real drift is stopped; and the strict side is floored by clear-headed pre-commitment, the same shape as Yangming's remedying of one-sidedness. \"The moment a thought stirs, that is already doing\" — the full-screen checks and the inquiry overlay compete for exactly that moment, before the thought gathers force.\n• Cultivation in affairs （事上磨练）: \"One must be tempered in affairs to stand firm.\" The habit system is cultivation-in-affairs, scheduled and booked — and it carries half the index's weight, in the same order as the philosophy.\nThe gaps and borrowed names, stated honestly:\n• The conscience layer is the thinnest: practice (doing what you promised) is built; reflection has only a prototype; the conscience layer — seeing which piece of \"knowing\" all these gaps jointly point to as not yet real — has, by the current ruling, no feature yet. The Know tab is storage and index, not yet a looking-back layer.\n• The one-thought journal is not built: there is no ledger yet for an evening reckoning of thoughts; existing reflection leans on self-checks produced passively when the guard catches you — the battlefield is still at the level of behaviour, not of thoughts. (Note: keeping a journal of thoughts is a later development of Liu Zongzhou and others, not Yangming's own teaching; this page does not borrow his name for it.)\n• The \"overcoming\" still leans on outside force: the whole chain calibrates how accurately the judging model sees, not how keen the user's own innate knowledge becomes; a looking-back design aimed at \"being caught less often\" does not exist yet.\n• Two things not claimed: the morning page is gathering the heart + daily reflection, not seated meditation (Yangming is explicit that quiet-sitting is only a beginner's expedient); and principle-matching for tasks is an external AI match, running against \"innate knowledge needs no outside seeking\" — it can only be called an external index and reminder tool for innate knowledge."},
        {"29. For experts (3): the design principles",
         "This section collects the short form of all of KnowDo's design principles, for readers who want to understand — or build on — this app:\n1. Meaning first: behaviour changes through meaning — let the user's aspiration and principles persuade them; the software is meaning's messenger, not the master of their data; being seen, friction and accountability are scaffolding only.\n2. Two ledgers, one thing: knowing and doing are not two feature modules; finding the gap and closing it is the product itself.\n3. Honest books: a drift is a drift — graces, self-checks and appeals are all recorded truthfully; the counting rules are written as contracts and do not drift with UI redesigns; changing a goal never re-scores the past (sleep goals and habit goals alike).\n4. Pre-commitment outranks in-the-moment judgement: the difficulty level, aspiration and principles set while clear-headed are the floor; the AI may only tell real from false and grant leniency above that floor — it cannot pardon a real drift.\n5. Friction where it cuts: what must be stopped is stopped fully; what should pass passes in one step; every interruption must earn its attention.\n6. The privacy red line: original screenshots are never written to disk and never uploaded; only text events remain (sole exception: the user-enabled accountability mail, which sends only an in-memory mosaicked image to the user's own chosen recipient); judging lets only text leave the phone, and providers may not retain it or train on it; every account's data is isolated at the database level.\n7. Local first: offline, save locally and queue; sync when the network returns — a network failure must never lose user input; data the user made by hand (tasks, diaries, habits, principles) has the highest priority and is never deleted by time.\n8. The AI proposes, the human decides: an AI re-plan lands only when you tap Apply; an AI task stops before the final step and irreversible steps wait for the user's confirmation; AI changes to settings or deletions always go through a proposal card first.\n9. No punishment, only reckoning: no money fines, no shaming publicity; nagging has tiers, mute has exits, and how strict things get is the user's own choice.\n10. Eastern minimalism: rice-paper off-white, ink blue-black, one touch of vermilion red; large, sparse type with generous whitespace — the interface itself should quiet you down, not become one more anxiety-making efficiency tool.\n11. Open and continuable: CC BY-NC-SA 4.0, derivatives must stay free and non-commercial; data formats stay stable across implementations, so changing implementations never loses the books."},
        {"30. For experts (4): for programmers — architecture and data design",
         "This section is for programmers; everyday use does not need it.\n• Client: native Android, Java / Views (no Compose; health reading uses one small Kotlin bridge to the official connect-client — the only Kotlin file in the repo), Gradle 8.7 + AGP 8.5.2, JDK 17, SDK 34. Layered as TaskStore (data) / SbSync and the sync layer / TimerService (timing) / UI, with data formats kept stable across implementations.\n• Timing reliability: countdowns store an absolute end time rather than remaining seconds; a foreground service keeps timing alive; AlarmManager exact alarms back up the half-time and end points; full-screen checks take a dual path — full-screen intent (screen off) and overlay (screen on).\n• Sync: Supabase (PostgreSQL + Realtime); tasks align by id with newest-wins merges and tombstone deletes; there is a single logical timer across devices — state in the cloud, rendered at both ends, controllable from either. Writes are always field-level updates, never whole-row overwrites. Traffic discipline: the free tier's egress is tight, so discovery and filtering are always server-side with light fields only.\n• Guard judging: a hybrid pipeline — on-device pre-screening (OCR, an on-device image classifier, a local semantic model), a cloud small-model final judge (text features only; the provider may not retain or train on them), and a local fallback when offline. Judging traces only ever gain fields; the semantics of existing events never change. Six red-line files hold the counting and semantics contracts (the Zhixing index, the time account, self-checks, semantic judging, habit data, the logical day boundary) — zero changes in any release, or changed only with the author's personal approval.\n• Built by multiple AI agents: this app is written collaboratively by several AI agents — the author is the product owner and final arbiter; Muse serves as secretary, reviews every PR and owns releases; the Grok team writes most feature code; collaboration runs through the Agent Guild (one topic per Issue). The engineering rules live in the repo's AGENTS.md: every major feature ships as its own version, a serious task list comes before any work, bug-fix releases must actually run in an emulator before anyone may call them fixed, and delivery reports separate \"personally verified / reported by the developer / not yet verified\".\n• Self-update: the app checks for a new version at most once a day, downloads ~5 MB shards from a private repository, and verifies every shard and the assembled file (size + MD5) before offering the install.\n• Sibling project: the Mac app (github.com/gty3310/knowdo-mac) shares the same account, the same task list and the same single logical timer with Android; before changing any data contract, consider whether the Mac end breaks. The founding design research is in docs/RESEARCH.zh.md in the repo; the guard's design-iteration history is in docs/GUARD-DESIGN.zh.md."},
    };
}
