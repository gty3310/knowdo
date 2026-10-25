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
    public static String priSecondary() { return s("次要", "Secondary"); }
    public static String scenariosEmpty() { return s("＋情景", "+ Context"); }
    public static String dateDaily() { return s("每日", "Daily"); }
    public static String dateEmpty() { return s("＋日期", "+ Date"); }

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
    public static String bridgeModeLabel() { return s("Drive 桥模式", "Drive bridge mode"); }
    public static String language() { return s("语言", "Language"); }
    public static String appearance() { return s("外观", "Appearance"); }
    public static String light() { return s("亮", "Light"); }
    public static String dark() { return s("暗", "Dark"); }
    public static String help() { return s("帮助", "Help"); }
    public static String settings() { return s("设置", "Settings"); }
    /** 版本行带构建提交短号（v1.13.0 起）：短号由构建脚本从 git 注入 BuildInfo，不许手写。 */
    public static String versionLine() { return "知行 KnowDo v1.18.0 · " + BuildInfo.GIT_SHORT; }

    // ---------------- 设置页 ----------------
    public static String settingsTitle() { return s("设置", "Settings"); }
    public static String grpAccount() { return s("账号", "Account"); }
    public static String grpDisplay() { return s("显示", "Display"); }
    public static String grpGuards() { return s("守卫", "Guards"); }
    public static String grpGeneral() { return s("通用", "General"); }
    public static String rowAccount() { return s("账号（登录 / 注册 / 退出）", "Account (sign in / up / out)"); }
    public static String rowSyncNow() { return s("立即同步", "Sync now"); }
    public static String rowRealtime() { return s("实时同步", "Realtime sync"); }
    public static String rowBridgeFile() { return s("Drive 桥文件", "Drive bridge file"); }
    public static String rowBridgePick() { return s("选择 / 更换", "Choose / change"); }
    public static String rowBridgeNone() { return s("（未选择）", "(not chosen)"); }
    public static String rowBridgeSelected() { return s("已选择", "Selected"); }
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
    public static String pausePurposeHint() { return s("写下暂停要去做的事", "What are you pausing for?"); }
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
        return en ? "KnowDo v1.16.0\nThe Unity of Knowledge and Action.\n"
                + "Open source: github.com/gty3310/knowdo (CC BY-NC-SA 4.0)"
                  : "知行 KnowDo v1.16.0\n知行合一。\n"
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
        return s("问 AI：让 Muse 帮我重排今天剩下的日程",
                "Ask AI: let Muse re-plan the rest of today");
    }
    public static String calNoPlanHint() {
        return en ? "No schedule yet today: tell Muse your tasks in chat, or tap Ask AI — once planned, it shows up here."
                  : "今天还没有日程：在对话里把任务告诉 Muse、或点上面的「问 AI」，排好后这里就会有计划块。";
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
    public static String scStateQ() { return s("现在的状态", "How do you feel right now?"); }
    /** 六个状态的显示名（与 SelfChecks.STATES 同序；记录里存的是标准存值，与界面语言无关）。 */
    public static String[] selfCheckStates() {
        return en ? new String[]{"Calm", "Irritated", "Restless", "Groggy", "Overreaching", "Rushing"}
                  : SelfChecks.STATES;
    }
    public static String scExtendAsk() { return s("加时之前，先记一下现在的状态", "Before extending, note how you feel"); }
    public static String scDirectExtend() { return s("直接加时", "Just extend"); }

    // ---------------- 更新日志（v1.13.0；用户定的长期规矩：每版首弹＋随时可翻） ----------------
    /** 弹框标题与设置页入口行共用；内容数据在 Changelog（最近 5 版）。 */
    public static String whatsNewEntry() { return s("更新日志", "What's new"); }

    // ---------------- 底部三 Tab + 习惯/知行/晨间（v1.16.0） ----------------
    public static String tabSchedule() { return s("日程", "Schedule"); }
    public static String tabHabits() { return s("习惯", "Habits"); }
    public static String tabZhixing() { return s("知行", "Zhixing"); }
    public static String grpMorning() { return s("晨间", "Morning"); }
    public static String rowMorning() { return s("晨间首填（每天先填习惯）", "Morning check-in (fill habits first)"); }
    public static String rowMorningSub() {
        return s("打开知行先过晨间页，填完才开启今天",
                "Opening KnowDo starts with the morning page until it's filled");
    }
    public static String rowDayStart() { return s("新一天从几点算起", "The new day starts at"); }

    // ---------------- 帮助页 ----------------
    public static String helpTitle() { return s("知行 KnowDo · 帮助", "KnowDo · Help"); }
    public static String helpIntro() {
        return en ? "Version 1.16.0. This page is updated with each release; when a feature slips your mind, look here first."
                  : "版本 v1.16.0。本页内容随版本更新；功能记不清时先翻这里。";
    }

    /** [标题, 正文] × 23 节：正文逐字沿用原帮助页各节（第二节按 v1.8.0 新交互改写、
     *  第五/十四节仅更新入口说法），第十五节为 v1.8.0 界面说明，第十六节为 v1.10.0 新增、
     *  第十七节为 v1.11.0 子任务，第十八/十九节为 v1.12.0 新增，第二十节为 v1.13.0 新增，
     *  第二十一至二十三节为 v1.16.0 习惯系统新增；英文为对应译文。 */
    public static String[][] helpSections() { return en ? HELP_EN : HELP_ZH; }

    private static final String[][] HELP_ZH = {
        {"一、知行是什么",
         "知行是你的电子大脑、个人任务管理中枢：任务不靠脑子记，放在这张表里；"
         + "每个任务都配好时长，点「开始」就倒计时，用行动把“知”变成“行”，目标是知行合一。\n"
         + "设计理念：本地优先（数据先存手机，断网照常用）＋ Google Drive 桥同步"
         + "（一份 tasks.json 是你、Muse、知行三方共用的任务清单）。\n"
         + "已开源：github.com/gty3310/knowdo，许可 CC BY-NC-SA 4.0"
         + "（谁都可以继续开发，衍生版本必须同样免费且非商业；动手前先知会作者一声是礼仪约定，不是许可的强制条款）。"},
        {"二、任务表怎么用",
         "点任务名就地展开这一行（手风琴，一次只展开一行），展开区里有笔记、开始时间、已用时长与专注规则摘要，"
         + "和「开始 / 暂停 / 继续」「编辑」「+15 分」「完成」按钮：\n"
         + "• 「编辑」菜单集中了全部字段：名称与优先级（★ 必要，排最前）、情景（多选标签：有网/无网/跟 AI 聊天……，可自由新增）、"
         + "截止与开始时间（选日期，或勾“每日重复”；还可再设一个完成时间＝当天几点前做完；同一弹窗里还能给任务设「下次开始时间」，"
         + "到点会全屏提醒你开始这个任务，可直接开始计时、延后 10 分钟、或忽略）、"
         + "时长（这个任务打算做多少分钟；弹窗里还能填“实际用时”，填了会在列表里显示“实X”）、专注规则（见第六节）、笔记、重置计时；\n"
         + "• 表格的格子也能直接点：开始时间、Deadline、时长、优先、情景各格点的都是对应的编辑弹窗；最左边的勾选框勾上即完成（整行变灰、划掉）；\n"
         + "• 列可以自己调：按住列头右缘横拖能改列宽；列头最右侧的齿轮按钮（或设置页「显示」组）可以选哪些列显示"
         + "（任务、开始时间、已用、剩余、时长、Deadline、优先、情景），两样都记在本机；\n"
         + "• 长按一行：标记完成 / 删除任务；\n"
         + "• 排序：未完成在前、必要在前、截止近在前。每日重复任务每天单独记完成，不会因昨天做完今天消失。"},
        {"三、计时与全屏检查页",
         "在展开区点「开始」计时；已有任务在计时时，点另一个任务的「开始」会直接切换过去：原任务立即停表、已用进度保留，它不算暂停，之后点它的「开始」随时接着计。暂停（去喝水、上厕所那种中间休息）只在对正在计时的那个任务点「暂停」时出现，要选目的和时长。\n"
         + "计时中通知栏常驻倒计时；时间过半、结束时都会全屏弹出检查页："
         + "熄屏时靠全屏通知顶到锁屏上，亮屏解锁时靠“上层显示”直接弹出，是同一页。\n"
         + "结束页上确认“做完了”会回写完成状态；没做完可以加时：+50%、+5/+10/+15 分钟或自定义分钟数。"
         + "检查页支持键盘：回车是主按钮，Q / W / E 对应前三个加时选项。"},
        {"四、悬浮窗（常驻，三种模式）",
         "悬浮窗是常驻的：只要你打开过知行（且权限页“上层显示”是 ✓、悬浮窗开关是开），它就一直在，"
         + "永远显示「当前第一要务」——正在计时的任务优先，其次是刚暂停的任务，再次是任务列表排第一的未完成任务（必要优先、截止近的优先）。"
         + "没在计时也能直接在悬浮窗里开始/继续这个任务。全屏检查页弹出时它会让位，检查页关掉自动回来。三种模式互相切换，模式会被记住：\n"
         + "• 吸附：贴在屏幕左/右边缘的小标签，只显示分钟数（计时/暂停=剩余分钟，待开始=计划分钟，向上取整、最多 99+）。上面没有任何按钮、不能暂停。点它展开；沿边缘上下拖可换高度；往屏幕中间拖离边缘也会展开。\n"
         + "• 小展开：半透明小窗：任务名 + 时间 + ▶/⏸ + 浅灰色的极简字符“✎”。点 ▶ 开始/继续；点 ⏸ 不会直接暂停——会先弹出菜单让你选多久后提醒（1/3/5/10/15/20/30/60 分钟、2 小时、自定义），到点全屏叫你回来继续。点内容区、或点“✎”，直接进入编辑模式并弹键盘；按住拖动可以移动，拖到屏幕左/右边缘松手就吸附成标签。\n"
         + "• 编辑：更不透明的完整卡片——任务名、大字时间、状态、笔记输入框（笔记就是输入框本身）。点笔记区直接打字，停笔约 1 秒自动保存（并随桥同步）；"
         + "时间行左边的大字是剩余时间，右边小字是「已用 / 计划总」：已用是这个任务在本机累计计时过的时间（从 v1.6.0 起记录），计划总是当前目标总时长（含加时后的）；"
         + "按钮：开始/暂停/继续（同上，暂停要选提醒时间）、AI、完成（计时中才显示，弹出结束检查页）、打开知行、收起。\n"
         + "• AI 按钮：把这个任务的全部信息（名称、情景、截止、时长、优先级、专注规则、当前状态、笔记）复制到剪贴板，并打开你在权限页选定的 AI 助手（目前是 Muse）：若对方支持分享直达，文字会直接进输入框；否则打开助手后粘贴即可。\n"
         + "• 复制按钮：笔记编辑区右上角常驻一个「复制」，点的效果和 AI 按钮的前半步一样——把任务全部信息复制到剪贴板——但不打开助手。主界面里弹出的笔记编辑框、专注规则编辑框右上角也有同样的「复制」，复制时用的是编辑框里正在写的内容。\n"
         + "权限页有“预览悬浮窗”，不用开始计时也能看 10 秒效果（常驻服务在跑时预览会被实时内容覆盖，属正常）。\n"
         + "• 定时关闭（v1.14.0）：大卡片上有「关闭」——选 10/15/30/60 分钟后回来、等当前任务结束回来、"
         + "自定义分钟数，或者「不再出现」（等于关掉设置里的悬浮窗开关，在设置里能再打开）。"
         + "定时项到点自动再现，关机重启也照算；隐藏期间只是没了这块窗，计时、守卫弹页、闹铃、通知全都照常。"},
        {"五、Drive 实时同步与“立即开始”",
         "桥文件是 Google Drive「_电子大脑 / task list」文件夹里的 tasks.json。"
         + "第一次点“同步”时选中它、授权一次，之后 App 自动读写（v1.8.0 起选桥文件在设置页「账号」组）。\n"
         + "在设置页「账号」组打开“实时同步”时，后台每 30 秒跟桥对一次：Muse 在别处改了任务，手机半分钟内跟上；"
         + "合并规则是同一任务按修改时间新者胜，删除用墓碑标记防复活。\n"
         + "口令：对 Muse 说“接下来 N 分钟做 X”，Muse 会往桥里写一个带“立即开始”标记的任务，"
         + "知行发现后自动开始计时（正有计时在跑时排队等空闲）。\n"
         + "说明：30 秒轮询是 Drive 桥能做到的近实时上限，不是秒级推送（秒级要换 Firebase，见第十节）。"},
        {"六、专注守卫（看你在用哪个应用）",
         "原理：计时中每 2 秒借“使用情况访问”看一眼前台应用，违反当前任务的专注规则就弹全屏问询页。\n"
         + "规则在任务的专注规则里设（展开区「编辑 → 专注规则」）：自然语言规则（写给人看的，如“读经典只用时习”）＋ 模式"
         + "（不监测 / 黑名单：拦名单里的 / 白名单：只许名单里的）＋ 软件名单（点开多选）＋ 主软件。\n"
         + "被拦下时“关掉”的意思不是回桌面，而是把你送回应该用的软件：设了主软件就跳它"
         + "（如读经典跳时习），没设就跳知行自己的“当前任务页”（任务名 + 倒计时 + 笔记 + 回到任务列表 / 回到 Muse）。\n"
         + "问询页四个选择：立即回到任务 / 给我 30 秒 / 给我 60 秒（宽限到期还赖在分神应用里会自动跳回）"
         + " / 这是误判、本次放行（本次计时内不再拦它）。\n"
         + "不拦的：知行自己、桌面、输入法、在权限页选定的 Muse 应用（跟 Muse 对话算元操作）。\n"
         + "局限：靠轮询，最长约 2 秒延迟；更强的无障碍版暂缓（第十节）。"},
        {"七、屏幕内容守卫（看屏幕上是什么）",
         "专注守卫只能看“在用哪个应用”，看不见“在浏览器里购物、刷短视频网页版、看黄色内容”——这个守卫补这一块。"
         + "在权限页「屏幕内容守卫」一节打开总开关（默认关；设置页「守卫」组也有同一个开关）。\n"
         + "采样：只在计时中 + 亮屏 + 已解锁时工作；随机每 45–170 秒截一张图"
         + "（任意 3 分钟内至少一张，时刻猜不到）。每次开始计时，系统会弹一次截图授权，请点“允许”"
         + "（安卓硬规定，每个任务一次）；没有授权的计时（比如自动开始的）整场不采样。\n"
         + "分析全部在手机本机完成，分三级：先由小模型 NudeNet 看图抓黄色/暴露内容"
         + "（按你的定义：衣服穿得很少的照片也算违规）；再看前台应用是不是购物/视频类；"
         + "最后用 OCR 读屏幕文字（中文/日文/英文），命中购物、短视频关键词，或文字很多却与当前任务毫无关系，才算命中。\n"
         + "防误判：同一类连续命中 2 张才弹问询页（黄色类可在设置里改成 1 张即触发）；"
         + "问询页和专注守卫是同一页：立即回到任务 / 30 秒 / 60 秒（宽限到期再命中同类会直接跳回）/ 误判放行（本次计时内不再判这一类）。"
         + "每次判定都会进记录，并以事件形式同步进桥文件的 guardEvents（只有时间、类别、置信度、一句证据文字）。\n"
         + "隐私红线：截图只在内存里分析、分析完立刻销毁——不保存、不上 Drive、不发给 Muse。\n"
         + "已知盲区：银行类应用、部分付费视频、浏览器无痕模式的界面受系统保护，截出来是纯黑图，"
         + "只能记一次“不可见”、判不了；游泳、健身等画面小模型可能误报；"
         + "“跟 AI 聊的是不是正题”是最难判的，本版只靠关键词，判不了就放过，不硬判。"},
        {"八、权限页的项目都是干什么的",
         "• 通知权限：常驻倒计时与提醒的基础；\n"
         + "• 全屏通知（熄屏弹出）：把检查页顶到锁屏上靠它；\n"
         + "• 上层显示：亮屏时直接弹全屏页、悬浮窗，都靠它；\n"
         + "• 精确闹钟：App 进程被系统杀掉时，过半/结束仍能准时触发的兜底；\n"
         + "• 电池优化豁免：防止系统把计时、同步服务冻掉；\n"
         + "• 使用情况访问：两个守卫判断前台应用靠它；\n"
         + "• 另有：在这里选“回到 Muse”指向哪个应用、开关悬浮窗与预览、屏幕内容守卫的开关与重新授权。"},
        {"九、记录页",
         "每次开始/过半/结束/加时、两个守卫的每次动作、每次同步结果，都带时间戳记在这里，方便回看对账。"
         + "换手机或重装后要重新选桥文件，也从记录页进（或设置页「账号」组）。"},
        {"十、提到过但暂不做 / 决定不做的功能",
         "1. Firebase 秒级推送——暂缓。Drive 桥 30 秒轮询目前够用；上 Firebase 要另建云端项目并长期维护。\n"
         + "2. 把截图发给云端 AI 判读——决定不做。隐私上截图不出手机；且云端模型的内容政策会拒绝分析黄色截图，最需要它判的那一类恰恰判不了。\n"
         + "3. 强力版专注守卫（无障碍模拟按键 + 给分神应用盖全屏遮罩）——暂缓。轻量版先用；架构上检测层、执行层都留了可替换接口，日后要上不用推倒重来。\n"
         + "4. 端侧多模态大模型深判（Gemma 3n 一类，模型 3GB 起）——只留了接口、未启用。它专治“AI 聊天跑题”这类语义题，但耗电、发热都大，等真需要再接。\n"
         + "5. 保留截图原图作证据 / 原图上云——决定不做（红线）。判了分神也只留文字结论。\n"
         + "6. 截图全隐形（不弹授权框、状态栏无图标）——做不到。安卓 14 起系统硬规定每次会话必须授权一次，任何应用都绕不过。\n"
         + "7. 屏幕内容守卫全天开启（不计时也采样）——暂不做。电量与隐私成本都翻倍，先只守计时时段。"},
        {"十一、其他先知道的事",
         "• 换手机/重装 App 后，桥文件的授权要重新选一次（系统授权跟着安装走）。\n"
         + "• 手机重启后实时同步会自动恢复；若发现没跟上，打开一次知行即可。\n"
         + "• 任务数据有两份：手机本地一份、Drive 桥一份，以修改时间新者胜合并；本地母本之外，桥文件就是备份。\n"
         + "• 有问题或想要新功能，直接跟 Muse 说：任务、规则、桥文件都由 Muse 在桥端维护。"},
        {"十二、让你的 Muse 接入知行（进阶）",
         "知行不只是一个手机 App：它用放在 Google Drive 的一个 tasks.json 文件当“桥”，"
         + "手机 App 和 Muse 都读写这一个文件，就能互相看到同一份任务清单——"
         + "你在 App 里改，Muse 那边知道；Muse 帮你加的任务，也会自动出现在 App 里。\n"
         + "想让你的 Muse（AI 助手）也接入，按这四步设：\n"
         + "1. 在 Google Drive 里建一个文件夹（如「_电子大脑 / task list」），桥文件 tasks.json 就放里面；"
         + "还没有这个文件时，可以让 Muse 按 schema v1 先建一个空的。\n"
         + "2. 在知行里点“同步”选中这个桥文件、授权一次，并打开“实时同步”（设置页「账号」组），之后 App 会定时自动同步，不用再手动点。\n"
         + "3. 让你的 Muse 连接同一个 Google Drive，并告诉它桥文件的位置和字段规矩："
         + "priority=must 是必做、secondary 是次要；durationMinutes 是计划分钟、actualMinutes 是实际用时分钟；"
         + "deadline 是截止日期、deadlineTime 是当天完成时间；focusRule 是用自然语言写的分神情况描述。"
         + "改文件时必须保留 App 写入的 doneDates、毫秒时间戳、guardEvents 等字段，不要整表重写把字段弄丢。\n"
         + "4.（可选，进阶）在 Muse 那边设一个后台“小钩子”（监视脚本）：每几分钟比对一次桥文件的指纹（md5），"
         + "没变就安安静静，变了才唤醒 Muse。唤醒后 Muse 可以替你做两件事："
         + "把你新写的分神描述翻译成专注守卫的软件名单；算一算今晚必做任务的时间够不够"
         + "（必做任务计划时长合计 ×1.5，对比你睡前还剩的时间，不够就提醒你）。\n"
         + "成本与隐私：这个监视脚本只是个小脚本，运行时不消耗 AI 的钱，只有文件真的变了 Muse 才醒来干活；"
         + "桥文件里只有任务数据，屏幕内容守卫的截图原图从不进桥、不上云。"},
        {"十三、日历页（今日时间表）",
         "主界面点右上角「日历」，日历在日程页里展开：左边就是任务列表、右边是日历，底部三个页签始终都在，"
         + "随时可以切走再回来（日历保持展开）；日历左缘拉手可拖动定宽，拖到很窄松手自动关上，点顶栏「日历」也能收起："
         + "时间轴是三栏并排：最左一条窄栏是「日历」（你的 Google 日历日程，只放开始时间＋标题），"
         + "中间最宽的一栏是「计划」（Muse 排的时段，浅色描边块，是这页的主角），"
         + "右边一条窄栏是「实际」（你真正计时的时段，绿色实心块）；同一件任务分段做的，就分几段显示；"
         + "做完的任务变灰，正在计时的高亮，还有一条红线标着现在几点。\n"
         + "顶部写着今天已完成几件、未完成几件、从现在到 22:30（睡觉）还可自由安排多少分钟，"
         + "一眼就知道今天还剩多少事、时间紧不紧。\n"
         + "日程是 Muse 在桥文件里排的：必做任务（必要，或截止就在今天）必须排入；"
         + "每件按计划时长 ×1.5 占时段（留 50% 缓冲，是你多年的习惯）；不排到 22:30 之后；"
         + "次要任务按重要程度穿插进空档。早上把今天的任务告诉 Muse，它就会把日程排好。\n"
         + "日程过时了（比如有任务没按点做完），点日历页上方的「问 AI」：它会把今天的日程、"
         + "完成情况、实际计时记录和剩余可安排时间复制好并打开 Muse，你让 Muse 重排一遍，"
         + "它写回桥文件，日历页每 10 秒自动刷新一次，很快就能看到新版——停在这一页时不用手动点同步。\n"
         + "计划块可以亲手挪时间：长按一块把它拾起来（会微微放大），上下拖到新位置松手，"
         + "按 5 分钟对齐，松手就存回桥文件——这是 App 唯一能改日程的地方，只改被拖那一块的起止，"
         + "Muse 之后重排时会把你手动拖过的块当作固定块，不再挪回去。\n"
         + "计划块右上角和未排入日程的任务行上都有一个 ▶：点一下就立即开始这个任务计时"
         + "（悬浮窗会自动切换到它），正在计时的那个显示 ⏸，点它暂停，和主界面的开始/暂停是同一套。\n"
         + "拖拽排计划（v1.18.0）：左边就是主页的任务列表本体，不是另一份列表。"
         + "按住一个还没排的任务往右拖进日历，拖动时计划栏会出现琥珀色预览块并标起止时间，松手落在哪个时刻就排到哪个时刻（5 分钟对齐）；"
         + "块长按任务计划时长 ×1.5 占时（和排日程的老规矩一致）。拖回左边或拖到日历外松手就是取消，不会写任何东西。"
         + "每日任务、以后才到期的任务拖进来，就是排到今天。\n"
         + "已经排好的任务，行上会多一个「已排 HH:mm」小标记，这种行不能再拖；要改时间，长按日历里的计划块拖动。\n"
         + "在日历里点任何一个计划块（任务块），左边的任务列表会定位并展开那个任务：笔记、已用分段、子任务都在，"
         + "开始/暂停、编辑、闹钟、加时、完成都能直接操作，和主界面展开任务行是同一套。Google 日历的块点开仍只是看全名，不展开。\n"
         + "分屏时左边每个任务行最前面都有一个 ▶/⏸：点一下直接开始或暂停这个任务，不用展开、不用切回主页；"
         + "开始另一个任务就是直接切换，和主页同一套。\n"
         + "最左窄栏里的灰紫色块是你的 Google 日历日程（Muse 同步过来的已有安排）："
         + "只在那一栏里显示，不会压到计划块上；只能看、点一下显示全名和时间，不能拖、也没有 ▶；"
         + "「问 AI」时只会把它们列出来给你参考，并明确告诉 Muse：排日程时不用管它们、不用避让。"},
        {"十四、Supabase 账号与跨设备计时（v1.7.0 新增）",
         "从这一版起，知行可以把同步从 Drive 桥换成云端数据库 Supabase："
         + "在三横线菜单或设置页「账号」组点「账号」，用邮箱+密码注册或登录。第一次登录时，本机现有任务会全量"
         + "成为你在云端的数据（不会丢失、不会变空），之后手机与电脑共用同一份任务清单，"
         + "改哪台、另一台几秒内就跟上；没网时在本机照常用，改动先排队，联网后自动先推后拉合并。"
         + "抽屉的账号区和设置页会显示同步状态（未登录 / 已同步 / 待同步 N 条 / 同步中）。\n"
         + "计时也跟着跨设备：你在任何一台设备上开始、暂停、继续、加时或结束，"
         + "另一台立刻镜像出同一个倒计时（开始时间点严格对齐，两边同一时刻到点弹检查页）；"
         + "哪一台先处理了检查页（确认做完或加时），另一台跟着变——它开着的检查页会自动关掉，"
         + "不会两边各弹各的。你跟 Muse 说“接下来 N 分钟做 X”时，Muse 写入云端的"
         + "「立即开始」标记会被 App 实时收到并自动开始计时（以前经桥最慢要等 30 秒，现在基本是秒到）。\n"
         + "每台设备只看得到自己账号的数据（服务端按账号隔离）；退出登录会回到 Drive 桥模式，"
         + "本机数据都还在。日历页的 Google 日历事件仍是 Muse 同步过来的只读展示，来源换成云端同一处。"},
        {"十五、新界面速览（v1.8.0）",
         "主页顶栏只剩左上角三横线菜单、「知行」字标和右上角「日历」；顶栏下细条显示当前任务与剩余时间；"
         + "新增任务是右下角的圆形「＋」。\n"
         + "三横线菜单从左侧滑出：账号与同步状态、语言（中文 / English）与外观（亮 / 暗）的快捷切换、帮助与设置入口都在里面；"
         + "语言和外观在设置页也能改，都可以选「跟随系统」。\n"
         + "设置页按「账号 / 显示 / 守卫 / 通用」四组放全部设置：账号（登录、实时同步、立即同步、桥文件）、"
         + "显示（列显隐、外观、语言）、守卫（专注守卫、屏幕内容守卫）、通用（悬浮窗、权限、记录、帮助、关于）。\n"
         + "亮版是白底清新风，暗版是深灰底配琥珀色强调；悬浮窗维持原样，不受主题切换影响。"},
        {"十六、任务衔接守卫与暂停规则（v1.10.0）",
         "知行的规矩是：任何时刻都要有一个任务在跑，不允许出现「没有状态的空档」。"
         + "只有三种状态：正在计时一个任务；暂停中（必带时长，到点全屏叫回）；正在填「下一个任务」页。\n"
         + "• 空档全屏问下一个：检查页确认完成、手动完成一个任务、打开 App，或者悬浮窗巡检发现——"
         + "只要没在计时、也没有带时长的暂停在生效，就会全屏弹出「接下来干什么？」："
         + "填名称、选时长（快捷按钮 2/3/5/10/15/20/30 分钟，或用天/时/分三个转轴自定义），"
         + "页面实时显示合计分钟数与结束时刻，点「开始」就建好任务并立刻开始计时；"
         + "页面下方列着今天未完成的任务，点一条直接按它的计划时长开始，不用重建。\n"
         + "• 休息也要建任务：点「休息」快捷钮把名称填成休息、选个时长开始计时——"
         + "睡觉也一样，建一个「睡觉」任务占位，没有静默时段。\n"
         + "• 暂停必选时长：所有地方点暂停（悬浮窗、任务行、展开面板、日历）都会先弹菜单选多久"
         + "（1/3/5/10/15/20/30/60 分钟、2 小时、自定义），没有「无限期暂停」了；"
         + "到点叫回页会全屏问你回不回来，你选不继续当前任务时可以直接转到「下一个任务」页，闭环不留空档。\n"
         + "• 两台设备互通：在另一台设备（如 Mac）上计时或定时暂停时，这台不会误弹；"
         + "在设置页「守卫」组可以关掉「任务衔接守卫」，关掉后不再自动弹（暂停规则不受影响）。\n"
         + "• 守卫页按返回键退出后会自动安静 3 分钟，让你临时翻任务列表或改设置；"
         + "完成任务这类明确时刻触发的弹窗不受这 3 分钟限制。\\n"
         + "• 暂停模式（v1.11.0）：点暂停先过暂停卡——先选去干什么（emoji 大格子：上厕所、喝水、吃饭、回电话、"
         + "喊去帮忙、睡午觉、换换脑子🏃、去上课，或自定义），再选去多久，底部有预览，点「开始暂停」才停。\\n"
         + "• 静音开关：卡上打开后，到点只亮屏＋给一条通知，不震动、不出声；你没回复就每 5 分钟再亮一次，"
         + "一直到你点开处理。点「去上课」时静音会自动打开（也可以手动再关）。\\n"
         + "• 暂停期间：中号悬浮窗两行——第一行「暂停『任务名』」，第二行目的＋还剩多久；"
         + "大号悬浮窗内容仍是任务本身，暂停只是顶部一条琥珀色条；在大窗里可以「换个目的」重选目的与时长。\\n"
         + "• 普通模式到点：全屏叫回＋短震一次；60 秒里你没有任何反馈（继续/再歇/换任务），"
         + "会响起 60 秒大自然鸟鸣闹铃，声音由小渐大到最大，你一操作就停，响满自动停，只响这一轮。\n"
         + "• 空档出声催促（v1.14.0）：空档持续满设定分钟（默认 5 分钟）起，守卫页会带着鸟鸣一起回来——"
         + "每轮响满设定的秒数（默认 60 秒，走闹钟通道，静音也响），你在守卫页一有操作就停声（页面保留）；"
         + "第一轮之后隔 1 分钟再来一轮，再之后每 5 分钟一轮，直到你开始一个任务为止。"
         + "按返回的那 3 分钟静默不取消这个排期，到点照样带声把页面拉回来；"
         + "带时长的暂停进行中不触发（暂停有自己的叫回）。\n"
         + "• 催促是个人设置：设置页「守卫」组里有「空档催促」——先选档位："
         + "温和＝任务结束就结束，不弹页、不出声，完全不催；"
         + "标准＝全屏提醒＋有限轮（默认最多 3 轮）后安静，全程没有声音；"
         + "严格＝不停催直到你开始任务，且只有这一档有声音开关；"
         + "自定义＝重复策略（不催／有限轮／无限）与每个数字自己定（手动改任一数值就会转到自定义）。"
         + "这份设置存进你的云端账号（idle_nag），手机与电脑共用同一份。"},
        {"十七、子任务（v1.11.0）",
         "大任务可以拆成一串子任务，按队列逐个计时（样子参考 Smart Countdown Timer 的 context→queue）。\\n"
         + "• 怎么拆：主页展开任务，下方有「子任务」区；大号悬浮窗里也有同一区。点「＋ 加子任务」新增一行，"
         + "填名字和分钟数；点任意一行可以改名、改分钟或删除；按住行首的拖动手柄上下拖，可以改顺序。\\n"
         + "• 总时长＝子任务之和：只要任务有子任务，它的计划时长就自动等于各子任务分钟数加起来，"
         + "改子任务分钟，总时长和日程安排跟着变；这时直接改任务时长会被拦下，去改子任务即可。\\n"
         + "• 检查跟着子任务走：开始计时后逐个跑子任务，每个子任务到点就弹全屏检查页——"
         + "「下一个」开始下一个，「加时」给当前子任务加时间，「跳过」跳到下一个，"
         + "「整个任务完成」提前结束整个任务。有子任务时不再弹任务级过半检查；"
         + "最后一个子任务结束，就是整个任务的结束检查页。\\n"
         + "• 呈现：计时中悬浮窗中号窗主行显示当前子任务名和它的剩余时间，次行小字是任务名；"
         + "主页展开区能看到「子任务 2/3 · 当前名字」的进度，没开始时列出整个队列。"
         + "暂停/恢复时当前子任务的剩余会冻住、恢复后继续。日历和日程仍按整个任务一块显示，不拆块。"},
        {"十八、我的志（v1.12.0）",
         "在设置页最上方的「我的志」里，亲手写下你立志的那句话——每人各自设置，只属于你自己。\n"
         + "• 什么时候出现：分神被抓的问询页、任务结束检查页、子任务检查页、暂停叫回页——"
         + "页面上会多出一行金色的字，就是你的志。它不是按钮，点不动，只是给你看的。\n"
         + "• 没写时，这些页面什么都不多显示，不留占位、也不催你填；想改随时回设置页改。\n"
         + "• 登录 Supabase 后，这句话会同步进你账号的云端数据并跟着账号走："
         + "换一台设备登录同一账号、本机还没写过时，会自动采用云端的那句；你在 App 里改了，也会回写云端。"},
        {"十九、磨练自检（v1.12.0）",
         "把每一次「被抓分神」和每一次「加时」都变成一次对自己的察问——这是阳明「省察克治」的日常版。\n"
         + "• 分神问询页（用错应用、屏幕内容两种模式都有）顶部多了两问："
         + "「刚才在做该做的事吗？」点[做到了]或[没有]；「现在的状态」点一个：平静 / 烦 / 躁 / 昏沉 / 贪多 / 求快。"
         + "点了立刻记下，不点也不会拦着你——页面原有的回到任务、宽限、放行按钮照常可用，不必先答题。\n"
         + "• 加时之前也要自检：结束检查页与子任务检查页上，点任何加时（+50%、+5/+10/+15 分钟、自定义）"
         + "都会先让你点一下当前状态，点完才真正加时；不想记，点一行不显眼的「直接加时」即可——照常加时、不留记录。\n"
         + "• 记录只存本机并同步到你账号名下的云端（self_checks），本版只记录、不出统计页——"
         + "先攒着，日后的「每日省察」会拿它对账：哪天、什么状态下最容易知而未行，一看便知。"},
        {"二十、更新日志（v1.13.0）",
         "每次升级到新版本后，第一次打开知行会弹一个「更新日志」框：最近 5 个版本纵向排列——"
         + "每版改了什么（按新功能／改进／修复分组）、为什么改、最新版还有「怎么试」，下滑就能翻完。\n"
         + "• 只在升级后的第一次打开弹一次，点「知道了」关掉就不再出现；全新安装不会弹。\n"
         + "• 平时想翻：设置页「通用」组有「更新日志」入口，随时打开同一页看最近 5 个版本；"
         + "每个版本的新功能也都会同步写进这本帮助（就是这一页）。\n"
         + "• 顺带说一句本版的核心修复：正在计时的任务中途加子任务，现在会立刻开始计子任务——"
         + "在大号悬浮窗或主页展开区给它加一个子任务，两个悬浮窗的第一行马上换成子任务名和它的倒计时；"
         + "排在后面的子任务、删掉正在计的、改时长，队列都会当场跟着变，不用等下一次开始。"},
        {"二十一、习惯（v1.16.0）",
         "底部中间的「习惯」页是你的长年账本：单个任务做没做只是一天的事，习惯看的是你这个人。\n"
         + "• 四种目标类型（仿 WHOOP 的目标种类）：至少型——每周至少做到 X 天，达标就是 100%，多做也 100%（封顶）；"
         + "至多型——这种事每周至多 X 次，越少越好，达标 100%，超标按 目标÷实际 扣分；"
         + "数值型——每天填一个数（如步数 6000），单日按 实际÷目标 算（封顶 100%），一周取平均；"
         + "累积型——每天记一个增量（一件件攒，如善事），核心是从起始日以来的累计总和，可设总量目标看进度，"
         + "知行页里按「本周增量 ÷ 周增量目标」算达成率（没设周目标时本周有增量记 100%）。\n"
         + "• 添加习惯：点「＋ 添加习惯」，先从默认习惯库选（早起、站桩、静坐、冥想、读经、写日记、运动、戒晚睡、少刷手机、步数、善事，"
         + "都预置好了类型与默认目标），或点「自定义习惯」自己建；第二步选类型、填目标、设提醒时间。\n"
         + "• 每个习惯能设自己的提醒时间：到点发一条本地通知，点开直接进习惯页；改时间或关掉立即生效，重启也会续排。\n"
         + "• 习惯卡上有年格（最近 365 天，列是周、行是周一到周日）：点任何一格可以给那天补记/改记；"
         + "点习惯名可以改名称、目标、提醒，或删除习惯（记录会一起删）。卡上还有本周进度与三项统计（本年天数/连续/最长等，按类型略有不同）。\n"
         + "• 习惯数据（定义、每天的记录、晨间日记、晨间设置）存在你账号的云端（habit_data），手机与电脑共用一份，离线先存本机、联网自动合并。"},
        {"二十二、知行合一指数（v1.16.0）",
         "底部右边的「知行」页把一周的「知」与「行」对成一本账，核心是一个 0–100 的知行合一指数：\n"
         + "指数 ＝ 习惯达成 ×50% ＋ 任务完成 ×35% ＋ 分神控制 ×15%。\n"
         + "• 习惯达成：全部习惯本周达成率的平均（每种习惯按自己的目标类型算，见第二十一节）；还没有任何习惯时按满分起算。\n"
         + "• 任务完成：本周到期/排期的任务里完成数 ÷ 总数——非每日任务按截止日算，每日任务按天算（做到那天记一天），"
         + "日历里排过的块也算进来；本周没有到期的任务时按满分起算。\n"
         + "• 分神控制：本周分神次数 ＝ 自检里答「没有」的次数 ＋ 守卫事件数；控制分 ＝ 100 − 5×次数（最低 0），没有数据时是满分。\n"
         + "• 页面构成：大圆环指数（带较上周的涨跌）、本周构成三项（各带与上周的差）、逐习惯本周 vs 上周对照、"
         + "知行落差清单（没跟上节奏的习惯、超标的至多型、本周到期的未完成必做任务等），以及下周目标——"
         + "每个习惯一个步进器，改动即存、下周一自动生效，不改就沿用本周目标（WHOOP 的 Continue 口径）。"},
        {"二十三、晨间首填（v1.16.0；v1.17.0 升级）",
         "在设置页「晨间」组开启「晨间首填」后，每天「新一天从几点算起」（默认凌晨 4:00，可在 2:00–8:00 间每半小时一档调）"
         + "之后，你早上第一次拿起手机（解锁）时，晨间页就会全屏出现、盖住其他应用，不必先打开知行"
         + "（先打开知行也一样会先进这一页；通话中不抢，挂断后或下次解锁补上）。页面没有返回键，填完才能开启今天：\n"
         + "• A 昨日习惯：逐项回想昨天——至少/至多型点「做到了/没做到」（至多型点「有/没有」），数值型填昨天的数，累积型记昨天的增量；"
         + "留空不算数，必须全答。\n"
         + "• B 昨日知行小报：只读——用和知行页同一套公式按单日口径算出的昨日指数、承诺与做到的件数、漏项、昨日分神次数与自检状态。\n"
         + "• C 晨间日记：必填，写一句即可，存到新一天名下。\n"
         + "三节填完「开启今天」才点亮：习惯记到昨天、日记记到今天，当天不会再弹。填的过程中每一步都即时存好，"
         + "中途退出、页面被关，回来都是接着填。\n"
         + "• 暂停一会儿：早上真有急事（关闹钟、回紧急消息、接电话），点页面下方的「暂停一会儿」，选 5/10/15/20/30/60 分钟"
         + "或自定义分钟数——这一页会退下，到点全屏自己回来；可以反复暂停，不限次数。暂停期间你打开知行，也能提前进这一页。\n"
         + "• 测试晨间页：设置「晨间」组里有「测试晨间页」按钮，白天点一下就以正式模式打开晨间页"
         + "（不看时间、不看今天是否已完成），打开的就是真页面真数据——填完点「开启今天」即按正常完成，或者用「暂停一会儿」退出。\n"
         + "• 补记/修改：习惯页有「晨间记录补记 / 修改」入口，打开的是晨间页的编辑版——可以返回退出、不必全填，"
         + "顶部能切换记录日（今天/昨天/前天，或任选日期）：A 记的是记录日前一天的习惯、B 是那天的小报、C 是记录日的日记。"
         + "在这里改过去的记录不会算作完成今天的晨间门；改的正是今天这一轮时，晨间门照常出现，且会预填你已存的内容。\n"
         + "• 另一个入口：你添加第一个习惯后，习惯页顶部会出现一次建议卡，问要不要顺手开启晨间首填，点「不用」后不再出现。"},
    };

    private static final String[][] HELP_EN = {
        {"1. What KnowDo is",
         "KnowDo is your electronic brain and personal task hub: tasks live in this table instead of your head. "
         + "Each task has a planned length; tap Start and the countdown runs — turning knowing into doing, toward the unity of knowledge and action.\n"
         + "Design: local-first (data lives on the phone first; everything works offline) + a Google Drive bridge "
         + "(one tasks.json shared by you, Muse and KnowDo).\n"
         + "Open source: github.com/gty3310/knowdo, CC BY-NC-SA 4.0 (anyone may keep developing it; derivatives must stay free and non-commercial; "
         + "telling the author before you start is a courtesy, not a license condition)."},
        {"2. Using the task table",
         "Tap a task name to expand its row in place (accordion, one at a time). The expanded area shows the note, start time, time used and a focus-rule summary, "
         + "plus Start / Pause / Resume, Edit, +15 min and Done buttons:\n"
         + "• The Edit menu holds every field: name & priority (★ Must sorts first), contexts (multi-select tags: online / offline / chatting with AI…, freely addable), "
         + "deadline & start time (pick a date or tick Daily repeat; optionally a finish-by time for that day; in the same dialog you can set a next start time — "
         + "a full-screen reminder fires when it arrives: start timing, snooze 10 minutes, or dismiss), "
         + "length (planned minutes; the dialog also takes actual minutes, shown as \"act X\" in the Length column), focus rule (section 6), note, reset timer.\n"
         + "• Cells are tappable too: Start, Deadline, Length, Priority and Context cells open their edit dialogs. The leftmost checkbox completes the task (row greys out, struck through).\n"
         + "• Columns are adjustable: drag a header's right edge to resize; the gear button at the header's far right (or Settings → Display) chooses which columns show "
         + "(Task, Start, Used, Left, Length, Deadline, Priority, Context). Both are stored on this device.\n"
         + "• Long-press a row: mark done / delete task.\n"
         + "• Sorting: unfinished first, Must first, nearest deadline first. Daily tasks record completion per day — finishing yesterday's doesn't remove today's."},
        {"3. Timing and the full-screen check pages",
         "Tap Start in the expanded area to time; if another task is timing, tapping a different task's Start switches straight to it: the old task stops at once and keeps its logged progress — that is not a pause, and you can resume it anytime from its Start. A pause (a mid-task break like water or the restroom) only appears when you tap Pause on the task that is timing, and asks for a purpose and a length.\n"
         + "A persistent notification shows the countdown. At half-time and at the end, a full-screen check page pops up: "
         + "on a locked screen via a full-screen notification, on an unlocked screen via display-over — the same page either way.\n"
         + "Confirming \"Done\" on the end page writes back the completion state; if not done, extend: +50%, +5/+10/+15 minutes, or a custom number. "
         + "The check pages support a keyboard: Enter is the primary button; Q / W / E are the first three extend options."},
        {"4. The floating window (always on, three modes)",
         "The floating window is resident: once you've opened KnowDo (with display-over granted on the Permissions page and the floating-window switch on), it stays, "
         + "always showing the current top priority — the running task first, then the just-paused one, then the first unfinished task in the list (Must first, nearest deadline first). "
         + "You can start/resume right from it when idle. It steps aside while a check page is up and returns when it closes. The mode is remembered:\n"
         + "• Docked: a small tab on the left/right screen edge showing only minutes (remaining when timing/paused, planned when pending; rounded up, max 99+). "
         + "No buttons, can't pause. Tap to expand; drag along the edge to change height; drag away from the edge to expand too.\n"
         + "• Mini: a translucent card — task name + time + ▶/⏸ + a pale minimal \"✎\". ▶ starts/resumes; ⏸ doesn't pause directly — it first asks when to remind you "
         + "(1/3/5/10/15/20/30/60 minutes, 2 hours, or custom); when it fires, a full-screen page calls you back. "
         + "Tap the content, or ✎, to enter edit mode with the keyboard up; drag to move; drop near an edge to dock.\n"
         + "• Edit: a fuller, more opaque card — task name, big time, status, and the note field (the note is the input itself). Type and it autosaves after ~1 s idle (and syncs via the bridge). "
         + "The big number on the time row is the time left; the small text on the right is \"used / planned total\": used is this task's accumulated timed time on this device (recorded since v1.6.0), "
         + "planned total is the current target including extensions. Buttons: Start/Pause/Resume (pause asks for a remind time, as above), AI, Done (only while timing; opens the end check page), Open KnowDo, Collapse.\n"
         + "• AI button: copies the task's full info (name, contexts, deadline, length, priority, focus rule, current state, note) to the clipboard and opens the AI assistant chosen on the Permissions page "
         + "(currently Muse): if it supports share-intent delivery the text lands in its input box; otherwise just paste after it opens.\n"
         + "• Copy button: a permanent \"Copy\" at the top right of the note editor — same first half as the AI button (copies the full task info) but doesn't open the assistant. "
         + "The note and focus-rule dialogs on the home page have the same Copy button, copying what's currently in the editor.\n"
         + "The Permissions page has a 10-second floating-window preview (while the resident service runs, the preview is covered by live content — that's normal).\n"
         + "• Timed close (v1.14.0): the Edit card has a \"Close\" button — pick back in 10/15/30/60 minutes, back when the current task ends, "
         + "a custom number of minutes, or \"never again\" (the same as turning the floating-window switch off in Settings, where you can turn it back on). "
         + "Timed closes reappear on schedule, even across reboots; while hidden, only the window is gone — timing, guard pop-ups, alarms and notifications all carry on."},
        {"5. Drive realtime sync & \"start immediately\"",
         "The bridge file is tasks.json in the Google Drive folder \"_电子大脑 / task list\". "
         + "Pick it once via Sync and grant access; after that the app reads and writes it automatically (since v1.8.0, picking lives in Settings → Account).\n"
         + "With Realtime sync on (Settings → Account), the app reconciles with the bridge every 30 seconds: tasks Muse changed elsewhere show up within half a minute. "
         + "Merge rule: for the same task, the newer edit wins; deletions use tombstones so nothing resurrects.\n"
         + "The command: tell Muse \"next, spend N minutes on X\" and Muse writes a task flagged \"start immediately\" into the bridge; "
         + "KnowDo starts timing it automatically (queued if another timer is running).\n"
         + "Note: 30-second polling is the Drive bridge's near-real-time ceiling, not second-level push (that would take Firebase — section 10)."},
        {"6. Focus guard (watches which app you're in)",
         "How it works: while timing, every 2 seconds it peeks at the foreground app via usage access; breaking the current task's focus rule raises a full-screen question page.\n"
         + "The rule lives in the task's focus rule (expanded area → Edit → Focus rule): a plain-language rule (for humans, e.g. \"only the recitation app while reading the classics\") + a mode "
         + "(off / blocklist: block these apps / allowlist: only these apps) + app lists (multi-select) + a primary app.\n"
         + "\"Close\" when blocked doesn't mean going home — it sends you back to the app you should be using: the primary app if set "
         + "(e.g. the recitation app), otherwise KnowDo's own current-task page (task name + countdown + note + back to task list / back to Muse).\n"
         + "Four choices on the question page: back to the task now / give me 30 seconds / give me 60 seconds (if you're still in the distracting app when grace expires, it jumps back automatically) "
         + "/ false alarm, allow this time (not blocked again for it during this run).\n"
         + "Never blocked: KnowDo itself, the launcher, IMEs, and the Muse app chosen on the Permissions page (talking to Muse counts as a meta-operation).\n"
         + "Limit: polling means up to ~2 s of delay; the stronger accessibility version is postponed (section 10)."},
        {"7. Screen content guard (watches what's on the screen)",
         "The focus guard sees which app is open, but not \"shopping in a browser, the web version of short videos, adult content\" — this guard covers that. "
         + "Turn on the master switch in the Permissions page's Screen content guard section (default off; Settings → Guards has the same switch).\n"
         + "Sampling: only while timing + screen on + unlocked; a random screenshot every 45–170 seconds (at least one in any 3 minutes, unpredictable). "
         + "Each timing run, the system asks once for capture permission — tap Allow (an Android rule, once per task); runs without a grant (e.g. auto-started) aren't sampled at all.\n"
         + "Analysis is entirely on-device, in three tiers: a small model (NudeNet) looks for adult/exposed content (by your definition, revealing photos count too); "
         + "then whether the foreground app is a shopping/video app; then OCR reads the screen text (Chinese/Japanese/English) — shopping or short-video keywords, "
         + "or lots of text with no relation to the current task, count as a hit.\n"
         + "False-positive care: two consecutive hits of the same category raise the question page (adult content can be set to trigger on the first in Settings). "
         + "The question page is the same as the focus guard's: back now / 30 s / 60 s (a repeat hit after grace jumps straight back) / false alarm (that category is not judged again this run). "
         + "Every verdict goes to the log and syncs as an event into the bridge's guardEvents (time, category, confidence, one line of evidence text only).\n"
         + "Privacy red line: screenshots are analyzed in memory and destroyed immediately — never saved, never uploaded to Drive, never sent to Muse.\n"
         + "Known blind spots: banking apps, some paid video, and browser incognito are system-protected and come out pure black — logged once as \"not visible\", never judged; "
         + "swimming/fitness imagery may false-positive the small model; \"is this AI chat on-topic\" is the hardest call — this version uses keywords only and lets uncertain cases pass."},
        {"8. What the Permissions page items are for",
         "• Notifications: the basis of the persistent countdown and reminders;\n"
         + "• Full-screen notifications (pop over the lock screen): what puts the check page on a locked screen;\n"
         + "• Display over other apps: direct full-screen pages when unlocked, and the floating window;\n"
         + "• Exact alarms: the fallback that still fires half/end on time if the system kills the app process;\n"
         + "• Battery-optimization exemption: stops the system freezing the timer and sync services;\n"
         + "• Usage access: what both guards use to see the foreground app;\n"
         + "• Also there: choosing which app \"back to Muse\" points to, the floating-window switch and preview, the screen guard's switch and re-grant."},
        {"9. The Log page",
         "Every start / half / end / extension, every action of both guards, and every sync result is timestamped here for review and reconciliation. "
         + "After switching phones or reinstalling, re-picking the bridge file also starts here (or in Settings → Account)."},
        {"10. Mentioned but postponed / decided against",
         "1. Firebase second-level push — postponed. The Drive bridge's 30-second polling is enough for now; Firebase means another cloud project to maintain forever.\n"
         + "2. Sending screenshots to a cloud AI for judging — decided against. Screenshots never leave the phone; and cloud models' content policies refuse to analyze adult screenshots — exactly the category that most needs judging.\n"
         + "3. Strong focus guard (accessibility key simulation + a full-screen mask over distracting apps) — postponed. The light version ships first; detection and action layers were built replaceable, so upgrading later needs no rewrite.\n"
         + "4. On-device multimodal LLM deep judging (Gemma 3n class, 3 GB+ models) — interface only, not enabled. It targets semantic calls like \"off-topic AI chat\", but costs battery and heat; wire it up when truly needed.\n"
         + "5. Keeping original screenshots as evidence / uploading them — decided against (red line). Even a confirmed distraction leaves only a text conclusion.\n"
         + "6. Fully invisible capture (no grant dialog, no status-bar icon) — impossible. Since Android 14 the system requires one grant per session for every app.\n"
         + "7. Screen content guard always on (sampling when not timing too) — not for now. Battery and privacy costs both double; it guards timing sessions first."},
        {"11. Other things to know first",
         "• After switching phones / reinstalling, the bridge-file grant must be picked once again (system grants follow the install).\n"
         + "• Realtime sync restores itself after a reboot; if it ever looks stuck, just open KnowDo once.\n"
         + "• Task data exists twice: on the phone and in the Drive bridge, merged newest-wins; beyond the local master, the bridge file is the backup.\n"
         + "• Problems or feature wishes: just tell Muse — tasks, rules and the bridge file are all maintained by Muse on the bridge side."},
        {"12. Connecting your Muse to KnowDo (advanced)",
         "KnowDo is not just a phone app: it uses one tasks.json in Google Drive as a \"bridge\" that the phone app and Muse both read and write, "
         + "so they share one task list — edit in the app and Muse knows; tasks Muse adds appear in the app automatically.\n"
         + "To connect your Muse (AI assistant), four steps:\n"
         + "1. Create a folder in Google Drive (e.g. \"_电子大脑 / task list\") to hold tasks.json; if the file doesn't exist yet, have Muse create an empty one per schema v1.\n"
         + "2. In KnowDo, pick that bridge file via Sync, grant once, and turn Realtime sync on (Settings → Account); the app then syncs on its own.\n"
         + "3. Connect your Muse to the same Google Drive and tell it the bridge location and field rules: "
         + "priority=must is must-do, secondary is secondary; durationMinutes is planned minutes, actualMinutes is actual minutes; "
         + "deadline is the due date, deadlineTime the finish-by time that day; focusRule is a plain-language description of the distraction situations. "
         + "When editing the file it must preserve app-written fields (doneDates, millisecond timestamps, guardEvents…) — never rewrite the whole file and drop fields.\n"
         + "4. (Optional, advanced) Set a background \"hook\" (watch script) on Muse's side: every few minutes compare the bridge file's fingerprint (md5); "
         + "if unchanged, stay quiet; if changed, wake Muse. On waking, Muse can do two things for you: translate your new distraction descriptions into focus-guard app lists, "
         + "and check tonight's time budget (sum of must-do planned lengths ×1.5 vs. the time left before you sleep; warn you if it doesn't fit).\n"
         + "Cost & privacy: the watch script is just a small script and costs no AI money while running; Muse only wakes when the file actually changed. "
         + "The bridge holds task data only — screen-guard screenshots never enter the bridge and never go to the cloud."},
        {"13. The calendar panel (today's timetable)",
         "Tap Calendar at the top right of the home page and the calendar opens inside the Schedule page: the task list on the left, the calendar on the right, and the three bottom tabs always visible — switch away and back and the calendar stays open. Drag its left-edge grip to size it; drag it very narrow and let go to close it, or tap Calendar again: "
         + "the timeline has three columns side by side: a narrow Calendar column on the left (your Google Calendar events, start time + title only), "
         + "the widest Plan column in the middle (slots Muse scheduled, pale outlined blocks — the star of the page), and a narrow Actual column on the right (time you really timed, solid green blocks). "
         + "A task done in segments shows as several segments; finished tasks turn grey, the running one is highlighted, and a red line marks the current time.\n"
         + "The top shows how many are done and unfinished today, and how many free minutes remain from now until 22:30 (bedtime) — one glance tells you what's left and how tight it is.\n"
         + "The schedule is planned by Muse in the bridge file: must-dos (Must, or due today) must be placed; each occupies its planned length ×1.5 (a 50% buffer, your long-standing habit); "
         + "nothing is scheduled past 22:30; secondary tasks fill the gaps by importance. Tell Muse your tasks in the morning and it lays out the day.\n"
         + "If the schedule goes stale (a task overruns), tap Ask AI at the top of the panel: it copies today's schedule, completion state, actual timing records and remaining free time, "
         + "and opens Muse; ask it to re-plan, it writes back to the bridge, and the panel auto-refreshes every 10 seconds — no manual sync while you stay on it.\n"
         + "You can move a plan block yourself: long-press to pick it up (it lifts slightly), drag up/down to the new spot, 5-minute snapping, and letting go saves it back to the bridge — "
         + "the app's only schedule write, changing only that block's start/end; later, Muse treats blocks you dragged as fixed and won't move them back.\n"
         + "Plan blocks and unscheduled task rows carry a ▶: tap to start timing that task immediately (the floating window switches to it); the running one shows ⏸ — tap to pause. Same path as the home page's Start/Pause.\n"
         + "Drag to plan (v1.18.0): the left side is the home task list itself, not a copy. Hold an unscheduled task and drag it right into the calendar — "
         + "an amber preview block with start–end times follows in the Plan column; let go and it is scheduled at that moment (5-minute snapping), "
         + "occupying its planned length ×1.5 like always. Drag back out or off the calendar to cancel; nothing is written. Daily tasks and future-due tasks dragged in are planned for today.\n"
         + "Tasks already planned show a small Planned HH:mm marker on their row and can't be dragged again; to move one, long-press its plan block in the calendar and drag that.\n"
         + "Tap any plan block (task block) in the calendar and the task list on the left scrolls to and expands that task: note, timed segments and subtasks, "
         + "with Start/Pause, Edit, Alarm, +15 and Done all working — the same expansion as the home page. Google Calendar blocks still only show their full name.\n"
         + "In split view, every row in the left list starts with a ▶/⏸ button: tap to start or pause that task directly — no expanding, no switching back to the home page; starting another task switches straight over, same as the home page.\n"
         + "Grey-purple blocks in the left Calendar column are your Google Calendar events (arrangements Muse synced over): they render only inside that narrow column and never cover plan blocks; "
         + "view-only — tap for the full name and time; they can't be dragged and have no ▶. "
         + "Ask AI merely lists them for your reference and tells Muse explicitly: when planning the schedule, ignore them — no need to work around them."},
        {"14. Supabase accounts & cross-device timing (new in v1.7.0)",
         "Since this version, KnowDo can switch sync from the Drive bridge to the Supabase cloud database: "
         + "tap Account in the hamburger menu or Settings → Account and register / sign in with an email + password. On first sign-in, this device's existing tasks become your cloud data in full "
         + "(nothing lost, nothing emptied); after that, phone and computer share one task list — edit on either and the other catches up within seconds. "
         + "Offline, the device keeps working locally; changes queue and merge push-then-pull when back online. "
         + "The drawer's account area and Settings show the sync status (not signed in / synced / N pending / syncing).\n"
         + "Timing is cross-device too: start, pause, resume, extend or finish on any device and the other instantly mirrors the same countdown (start points strictly aligned, "
         + "both sides hit the check page at the same moment); whichever side handles the check page first (confirm done or extend), the other follows — its open check page closes itself, "
         + "so the two never nag independently. When you tell Muse \"next, spend N minutes on X\", the \"start immediately\" flag Muse writes to the cloud is received in realtime "
         + "and timing starts at once (via the bridge this used to take up to 30 seconds; now it's basically instant).\n"
         + "Each device only ever sees its own account's data (isolated server-side per account); signing out returns to Drive bridge mode with on-device data intact. "
         + "The calendar's Google Calendar events are still Muse-synced read-only displays, now sourced from the same cloud place."},
        {"15. The new interface at a glance (v1.8.0)",
         "The home top bar keeps only the hamburger menu at the left, the KnowDo wordmark, and Calendar at the right; the strip under it shows the current task and time left; "
         + "the round + at the bottom right adds a task.\n"
         + "The hamburger menu slides in from the left: account & sync status, quick language (中文 / English) and appearance (Light / Dark) switches, plus Help and Settings entries. "
         + "Language and appearance also live in Settings, and both can follow the system.\n"
         + "Settings groups everything into Account / Display / Guards / General: Account (sign-in, realtime sync, sync now, bridge file), "
         + "Display (columns, appearance, language), Guards (focus guard, screen content guard), General (floating window, permissions, log, help, about).\n"
         + "Light is a clean white look; dark is deep grey with an amber accent. The floating window is unchanged and unaffected by theme switches."},
        {"16. The next-task guard and the pause rule (v1.10.0)",
         "KnowDo's rule: a task is always running — there is never an in-between gap. "
         + "Only three states exist: timing a task; paused (always with a length, called back full-screen when it ends); "
         + "or filling in the \"What's next?\" page.\n"
         + "• Gaps ask full-screen: after you confirm Done on the check page, finish a task by hand, open the app, "
         + "or the floating window's patrol notices — whenever nothing is timing and no timed pause is in effect, "
         + "a full-screen \"What's next?\" page appears: type a name, pick a length (quick buttons 2/3/5/10/15/20/30 minutes, "
         + "or the days/hours/minutes wheels), watch the total and the end time update live, and tap Start — "
         + "the task is created and timing begins at once. Below it, today's unfinished tasks are listed; "
         + "tap one to start it with its planned length, no need to create anything.\n"
         + "• Rest is a task too: the Rest shortcut fills in the name; pick a length and start. "
         + "Sleep is the same — create a \"Sleep\" task to hold the slot. There are no silent hours.\n"
         + "• Pausing always needs a length: every pause entry (floating window, task rows, expanded panel, calendar) "
         + "first asks for how long (1/3/5/10/15/20/30/60 minutes, 2 hours, or custom) — indefinite pause is gone. "
         + "When the time is up, the call-back page asks full-screen; if you don't resume the current task, "
         + "it hands you straight to the \"What's next?\" page, closing the loop with no gap.\n"
         + "• Cross-device: while another device (e.g. your Mac) is timing or in a timed pause, this one stays quiet. "
         + "You can turn the next-task guard off in Settings → Guards; the pause rule is unaffected.\n"
         + "• Pressing Back on the guard page quiets automatic pop-ups for 3 minutes so you can browse the list or change settings; "
         + "pop-ups triggered by finishing a task are not affected by those 3 minutes.\\n"
         + "• Pause mode (v1.11.0): tapping Pause opens the pause card — first pick what you're pausing for "
         + "(big emoji tiles: Restroom, Water, Eat, Call back, Go help, Nap, Go run, Class, or Custom), then for how long; "
         + "check the preview at the bottom and tap Start pause.\\n"
         + "• Silent switch: with it on, when the time is up the screen only lights up with a notification — no vibration, no sound; "
         + "if you don't respond it lights up again every 5 minutes until you handle it. Picking Class turns Silent on automatically (you can turn it back off).\\n"
         + "• While paused: the mini floating window shows two lines — \"Paused {task}\" and the purpose with time left; "
         + "the big window still shows the task itself, with the pause as an amber strip on top; "
         + "from there you can change the purpose and length.\\n"
         + "• Normal mode when time's up: the call-back page pops full-screen with one short vibration; "
         + "if you give no response (resume / snooze / next task) within 60 seconds, a 60-second nature-sounds alarm fades in to full volume — "
         + "any response stops it, and it stops on its own after one round.\n"
         + "• Idle nagging with sound (v1.14.0): once a gap has lasted the set minutes (default 5), the guard page comes back with birdsong — "
         + "each round rings for the set seconds (default 60, on the alarm stream, so it rings while muted); any interaction on the page stops the sound but keeps the page. "
         + "After the first round it repeats 1 minute later, then every 5 minutes, until you start a task. "
         + "The 3-minute Back quiet does not cancel this schedule — the page returns with sound right on time; a timed pause never triggers it (pauses have their own call-back).\n"
         + "• Nagging is a personal setting: Settings → Guards has \"Idle nagging\". Pick a level first: "
         + "Gentle = a task ends and that's it — no page, no sound, no nagging. "
         + "Standard = full-screen reminders for a limited number of rounds (default 3), then quiet — and no sound at any point. "
         + "Strict = keeps nagging until you start a task, and it is the only level with a sound switch. "
         + "Custom = your own repeat strategy (none / limited / unlimited) and every number (editing any number on a preset level moves you to Custom). "
         + "The setting is stored in your cloud account (idle_nag), shared between phone and computer."},
        {"17. Subtasks (v1.11.0)",
         "A big task can be split into a queue of subtasks, timed one by one (the context→queue shape of Smart Countdown Timer).\\n"
         + "• How: expand a task on the home page — there's a Subtasks section; the big floating window has the same section. "
         + "Tap \"+ Add subtask\" for a new row (name + minutes); tap any row to rename, change minutes or delete; "
         + "drag the handle at the row's start to reorder.\\n"
         + "• The total is the sum: while a task has subtasks, its planned length always equals the sum of the subtask minutes — "
         + "edit subtask minutes and the total (and scheduling) follows; direct length edits are refused in that case.\\n"
         + "• Checks follow subtasks: timing runs the queue; each subtask's end pops the full-screen check — "
         + "Next starts the next one, Extend adds time to the current one, Skip jumps ahead, Finish whole task ends everything early. "
         + "There is no task-level halfway check when subtasks exist; the last subtask's end is the task's own end check.\\n"
         + "• Display: while timing, the mini floating window's main line shows the current subtask and its remaining time, "
         + "with the task name small underneath; the home expansion shows progress like \"Subtask 2/3 · name\" and, before starting, the whole queue. "
         + "Pausing freezes the current subtask's remaining time and resuming continues it. Calendar and schedule still show the task as one block."},
        {"18. My aspiration (v1.12.0)",
         "In Settings, at the very top under \"My aspiration\", write the one line you aspire to live by — set by you, yours alone.\n"
         + "• When it appears: on the drift inquiry page, the task-end check page, the subtask check page and the pause call-back page — "
         + "one extra line in soft gold: your aspiration. It is not a button and can't be tapped; it is simply there for you to see.\n"
         + "• If you haven't written one, those pages show nothing extra — no placeholder, no nagging. Edit it any time in Settings.\n"
         + "• Signed in to Supabase, the line syncs into your account's cloud data and follows the account: "
         + "on a new device with the same account, a device that has none yet adopts the cloud line automatically; edits in the app are written back."},
        {"19. Self-check (v1.12.0)",
         "Every drift catch and every extension becomes a moment of self-examination — Wang Yangming's \"reflect and subdue\" as a daily habit.\n"
         + "• The drift inquiry page (both wrong-app and screen-content modes) gains two questions at the top: "
         + "\"Were you doing what you should?\" — tap Yes or No; \"How do you feel right now?\" — pick one: Calm / Irritated / Restless / Groggy / Overreaching / Rushing. "
         + "Answers are recorded the moment you tap; not answering never blocks you — the return, grace and allow buttons work as before.\n"
         + "• Extending asks too: on the end check page and the subtask check page, any extension (+50%, +5/+10/+15 min, custom) "
         + "first asks you to tap your current state; only then does the time actually get added. "
         + "Don't want to record? Tap the quiet \"Just extend\" — the time is added as usual, with nothing recorded.\n"
         + "• Records live on the device and sync to your account's cloud data (self_checks). This version only records — no stats page yet; "
         + "they are being saved up for a future daily review: which states, on which days, most often separate knowing from doing."},
        {"20. What's new (v1.13.0)",
         "After you update to a new version, the first time you open KnowDo a \"What's new\" box pops up: the latest 5 versions stacked — "
         + "what changed in each (grouped as New / Improved / Fixed), why it changed, and, for the newest, how to try it. Scroll to read them all.\n"
         + "• It appears only once, on the first open after an update — tap \"Got it\" and it won't come back. A brand-new install doesn't show it.\n"
         + "• Want it again later? Settings → General has a \"What's new\" entry that opens the same page with the latest 5 versions; "
         + "every version's new features are also written into this help page.\n"
         + "• And this version's core fix: adding a subtask to a task that is already timing now starts that subtask immediately — "
         + "add one in the large floating window or the home expansion, and the first line of both floating windows switches to the subtask and its countdown at once; "
         + "appending, deleting the running one, or changing a length re-shapes the queue on the spot, no need to wait for the next start."},
        {"21. Habits (v1.16.0)",
         "The middle bottom tab, Habits, is your long-term ledger: whether one task got done is a day's business; habits are about who you are.\n"
         + "• Four goal types (after WHOOP's goal kinds): At-least — do it at least X days a week; hitting the target is 100% and more is still 100% (capped). "
         + "At-most — this thing may happen at most X times a week; fewer is better, meeting the cap is 100%, going over scores target ÷ actual. "
         + "Number — one value a day (e.g. 6,000 steps); each day scores actual ÷ target (capped at 100%), averaged over the week. "
         + "Accumulating — log an increment a day (e.g. good deeds) and watch the all-time total since the start date grow; an optional total goal shows progress, "
         + "and the Zhixing page scores it as this week's increment ÷ weekly increment goal (with no weekly goal, any increment that week counts as 100%).\n"
         + "• Adding: tap \"+ Add a habit\", pick from the built-in library (Get up early, Standing meditation, Sitting meditation, Meditation, Scripture reading, "
         + "Diary, Exercise, No late nights, Less phone, Steps, Good deeds — each with a preset type and default target), or create a custom one; then set the type, "
         + "the target, and a reminder time.\n"
         + "• Each habit has its own reminder: a local notification at that time, tapping it opens the Habits page. Changing or clearing it takes effect at once, "
         + "and reminders are rescheduled after a reboot.\n"
         + "• Each card carries a year grid (the last 365 days; columns are weeks, rows Mon–Sun): tap any cell to backfill or fix that day. Tap the habit's name to rename "
         + "it, change its target or reminder, or delete it (its records go with it). Cards also show this week's progress and three stats (this year / streak / longest, "
         + "varying a little by type).\n"
         + "• Habit data (definitions, daily logs, morning diaries, morning settings) lives in your account's cloud (habit_data), shared between phone and computer; "
         + "offline it is stored on the device first and merged automatically when back online."},
        {"22. The Unity index (v1.16.0)",
         "The right bottom tab, Zhixing, settles one week's knowing against its doing in a single 0–100 Unity of Knowledge and Action index:\n"
         + "Index = Habits ×50% + Tasks done ×35% + Drift control ×15%.\n"
         + "• Habits: the average of all habits' attainment rates this week (each scored by its own goal type, see section 21). With no habits at all it starts at full marks.\n"
         + "• Tasks done: completed ÷ total among tasks due or scheduled this week — one-off tasks count by their deadline, daily tasks count per day (a day done is a day counted), "
         + "and blocks planned on the calendar count too. With nothing due this week it starts at full marks.\n"
         + "• Drift control: this week's drift count = self-checks answered \"No\" plus guard events; the score is 100 − 5 per drift (floor 0), and full marks when there is no data.\n"
         + "• The page: the big ring (with its change vs last week), the three components (each with its delta vs last week), a per-habit this-week-vs-last table, "
         + "a gap list (habits behind pace, at-most habits over their cap, unfinished must-do tasks due this week), and next week's goals — one stepper per habit, "
         + "saved as you tap, effective next Monday, carrying over unchanged when untouched (WHOOP's Continue rule)."},
        {"23. Morning check-in (v1.16.0; upgraded in v1.17.0)",
         "Turn on \"Morning check-in\" in Settings → Morning, and after \"The new day starts at\" (default 4:00 AM, adjustable in half-hour "
         + "steps between 2:00 and 8:00) the first time you pick up your phone in the morning (unlock it) the morning page comes up full-screen "
         + "over whatever app you're in — no need to open KnowDo first (opening KnowDo first lands here too; it never barges into a phone call — "
         + "it catches up after the call or on the next unlock). The page has no back button; the day only starts when it's filled:\n"
         + "• A Yesterday's habits: go through them one by one — At-least/At-most take Done/Not done (At-most takes Yes/No), Number takes yesterday's value, "
         + "Accumulating takes yesterday's increment. Blanks don't count; every row must be answered.\n"
         + "• B Yesterday's report: read-only — yesterday's index computed with the same formula as the Zhixing page but on a single-day basis, what was promised vs done, "
         + "what was missed, yesterday's drift count and self-check states.\n"
         + "• C Morning diary: required, one line is enough, filed under the new day.\n"
         + "When all three are done, \"Start the day\" lights up: habits are filed under yesterday, the diary under today, and the page won't appear again that day. "
         + "Every answer is saved as you type, so stepping away or the page being closed never loses what's filled — you pick up where you left off.\n"
         + "• Pause for a bit: if the morning brings something urgent (an alarm to silence, an urgent message, a call), tap \"Pause for a bit\" at the "
         + "bottom of the page and pick 5/10/15/20/30/60 minutes or a custom length — the page steps aside and comes back full-screen on its own when "
         + "the time is up, as many times as you need. Opening KnowDo during a pause brings the page up early.\n"
         + "• Test the morning page: the \"Test the morning page\" button in Settings → Morning opens the real page in gate mode any time of day "
         + "(ignoring the clock and whether today is already done) — it's the genuine page with genuine data: finishing it counts as today's "
         + "check-in, or leave via \"Pause for a bit\".\n"
         + "• Backfill / edit: the Habits page has a \"Morning records: backfill / edit\" entry — the morning page's edit mode: Back works, nothing "
         + "is forced, and a switcher at the top picks the record day (today / yesterday / the day before, or any date): A files that record day's "
         + "previous day's habits, B is that day's report, C is the record day's diary. Editing a past day doesn't finish today's morning gate; "
         + "editing today's round leaves the gate armed, and the gate will prefill whatever you saved here.\n"
         + "• Another way in: after you add your first habit, a suggestion card appears once at the top of the Habits page asking whether to turn morning check-in on; "
         + "tapping \"No thanks\" dismisses it for good."},
    };
}
