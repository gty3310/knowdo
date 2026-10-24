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
    public static String versionLine() { return "知行 KnowDo v1.10.0"; }

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
        return en ? "KnowDo v1.10.0\nThe Unity of Knowledge and Action.\n"
                + "Open source: github.com/gty3310/knowdo (CC BY-NC-SA 4.0)"
                  : "知行 KnowDo v1.10.0\n知行合一。\n"
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
    public static String calLanePlan() { return s("计划（Muse 排）", "Plan (by Muse)"); }
    public static String calLaneActual() { return s("实际（计时）", "Actual (timed)"); }
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

    // ---------------- 帮助页 ----------------
    public static String helpTitle() { return s("知行 KnowDo · 帮助", "KnowDo · Help"); }
    public static String helpIntro() {
        return en ? "Version 1.10.0. This page is updated with each release; when a feature slips your mind, look here first."
                  : "版本 v1.10.0。本页内容随版本更新；功能记不清时先翻这里。";
    }

    /** [标题, 正文] × 16 节：正文逐字沿用原帮助页各节（第二节按 v1.8.0 新交互改写、
     *  第五/十四节仅更新入口说法），第十五节为 v1.8.0 界面说明，第十六节为 v1.10.0 新增；英文为对应译文。 */
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
         "在展开区点「开始」计时；已有任务在计时时会先问你是否切换。\n"
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
         + "权限页有“预览悬浮窗”，不用开始计时也能看 10 秒效果（常驻服务在跑时预览会被实时内容覆盖，属正常）。"},
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
         "主界面点右上角「日历」，日历面板从右侧滑出（左缘拉手可拖动定宽，拖到很窄松手自动关上）："
         + "左边一列是计划（Muse 排的时段，浅色描边块），"
         + "右边一列是实际（你真正计时的时段，绿色实心块）；同一件任务分段做的，就分几段显示；"
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
         + "页面里带 📅 的灰紫色块是你的 Google 日历日程（Muse 同步过来的已有安排）："
         + "只能看、点一下显示全名和时间，不能拖、也没有 ▶；「问 AI」时会把它们列为忙碌时段，"
         + "让 Muse 排日程时绕开它们。"},
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
         + "完成任务这类明确时刻触发的弹窗不受这 3 分钟限制。"},
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
         "Tap Start in the expanded area to time; if another task is timing, you'll be asked whether to switch.\n"
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
         + "The Permissions page has a 10-second floating-window preview (while the resident service runs, the preview is covered by live content — that's normal)."},
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
         "Tap Calendar at the top right of the home page and the panel slides in from the right (drag its left-edge grip to size it; drag it very narrow and let go to close it): "
         + "the left lane is the Plan (slots Muse scheduled, pale outlined blocks); the right lane is the Actual (time you really timed, solid green blocks). "
         + "A task done in segments shows as several segments; finished tasks turn grey, the running one is highlighted, and a red line marks the current time.\n"
         + "The top shows how many are done and unfinished today, and how many free minutes remain from now until 22:30 (bedtime) — one glance tells you what's left and how tight it is.\n"
         + "The schedule is planned by Muse in the bridge file: must-dos (Must, or due today) must be placed; each occupies its planned length ×1.5 (a 50% buffer, your long-standing habit); "
         + "nothing is scheduled past 22:30; secondary tasks fill the gaps by importance. Tell Muse your tasks in the morning and it lays out the day.\n"
         + "If the schedule goes stale (a task overruns), tap Ask AI at the top of the panel: it copies today's schedule, completion state, actual timing records and remaining free time, "
         + "and opens Muse; ask it to re-plan, it writes back to the bridge, and the panel auto-refreshes every 10 seconds — no manual sync while you stay on it.\n"
         + "You can move a plan block yourself: long-press to pick it up (it lifts slightly), drag up/down to the new spot, 5-minute snapping, and letting go saves it back to the bridge — "
         + "the app's only schedule write, changing only that block's start/end; later, Muse treats blocks you dragged as fixed and won't move them back.\n"
         + "Plan blocks and unscheduled task rows carry a ▶: tap to start timing that task immediately (the floating window switches to it); the running one shows ⏸ — tap to pause. Same path as the home page's Start/Pause.\n"
         + "Grey-purple blocks with 📅 are your Google Calendar events (arrangements Muse synced over): view-only — tap for the full name and time; they can't be dragged and have no ▶. "
         + "Ask AI lists them as busy time so Muse plans around them."},
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
         + "pop-ups triggered by finishing a task are not affected by those 3 minutes."},
    };
}
