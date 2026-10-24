package com.ti.tasktimer;

import java.util.ArrayList;
import java.util.List;

/**
 * 更新日志内容（内置数据，随版维护）：最近 5 个版本，最新在最上。
 * 每版一块：版本名＋发布日期＋「改了什么」（按 新功能／改进／修复 分组的大白话）
 * ＋「为什么改」（一句）＋最新版另加「怎么试」。
 * 维护规矩：每发一版，在 ENTRIES 最前面加新版一块，保留最近至少 5 版；
 * 文案从仓库 CHANGELOG.md 提炼，只写真实做过的功能，不许编造。
 * 弹框（升级后首次打开）与设置页「更新日志」入口共用这份数据（WhatsNew 渲染）。
 */
public final class Changelog {

    private Changelog() {}

    public static final class Group {
        public final String cat;
        public final String[] items;
        Group(String cat, String... items) { this.cat = cat; this.items = items; }
    }

    public static final class Entry {
        public final String version;
        public final String date;
        public final List<Group> groups = new ArrayList<>();
        public String why;
        public String tryIt; // 只有最新版有
        Entry(String version, String date) { this.version = version; this.date = date; }
        Entry add(Group g) { groups.add(g); return this; }
    }

    private static String s(String zh, String en) { return Strings.en() ? en : zh; }

    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();

        Entry e1161 = new Entry("v1.16.1", "2026-10-24");
        e1161.add(new Group(s("修复", "Fixed"),
                s("切换任务时不再被要求先填暂停卡：一个任务在计时中，直接点另一个任务的开始就会切换过去，"
                        + "原任务进度保留。暂停卡只在你对正在计时的任务点「暂停」时出现——"
                        + "那才是去喝水、上厕所这种中间休息。",
                  "Switching tasks no longer forces you through the pause card first: while a task is timing, "
                        + "just tap another task's Start to switch to it — the old task keeps its progress. "
                        + "The pause card only appears when you tap Pause on the task that is timing — "
                        + "that's the mid-task break, for water or the restroom.")));
        e1161.why = s("切换和暂停是两件事：此前实现把切换错误地路由进了暂停流程，"
                        + "换个任务干还要先给上一个任务填暂停的目的和时长。",
                "Switching and pausing are two different things: the switch had been wrongly routed through "
                        + "the pause flow, so moving on to another task first demanded a purpose and a length "
                        + "for pausing the previous one.");
        e1161.tryIt = s("开始一个任务，再到列表里点另一个任务的开始——应该一下子就切过去，不再弹任何卡。",
                "Start a task, then tap Start on another task in the list — it should switch instantly, "
                        + "with no card in between.");
        out.add(e1161);

        Entry e116 = new Entry("v1.16.0", "2026-10-24");
        e116.add(new Group(s("新功能", "New"),
                s("底部多了两个页签：「习惯」和「知行」。",
                  "Two new bottom tabs: \"Habits\" and \"Zhixing\"."),
                s("习惯有四种记法——至少型（每周至少几天，达标就是满分）、至多型（越少越好，超标扣分）、"
                        + "数值型（每天填个数，如步数）、累积型（一件件攒总数，如善事）；"
                        + "可以从默认习惯库里选，也可以自定义，每个习惯能设自己的提醒时间。",
                  "Habits come in four kinds — At-least (so many days a week; hitting the target is full marks), "
                        + "At-most (fewer is better; going over costs points), Number (one value a day, like steps), "
                        + "and Accumulating (a growing total, like good deeds). Pick from the built-in library or "
                        + "create your own; each habit gets its own reminder time."),
                s("知行页：知行合一指数（习惯达成＋任务完成比例＋分神控制三项合成）、本周和上周逐项对照、落差清单、下周目标。",
                  "The Zhixing page: a Unity of Knowledge and Action index (habits + task completion + drift control "
                        + "combined), this week vs last week item by item, a gap list, and next week's goals."),
                s("晨间首填（在设置里开启）：每天先填昨日习惯、看昨日小报、写晨间日记，再开启今天。",
                  "Morning check-in (turn it on in Settings): each day starts by filling in yesterday's habits, "
                        + "reading yesterday's mini report, and writing a morning diary line.")));
        e116.why = s("单个任务做没做只是一天的事；你想看的是这一周「知」与「行」对没对上。"
                        + "把习惯和任务完成汇成一个分数，每天早上先对昨天的账，一天从对齐开始。",
                "Whether a single task got done is one day's business; what you want to see is whether a whole "
                        + "week's knowing matched its doing. Habits and task completion now add up to one score, "
                        + "and every morning starts by settling yesterday's account — the day begins aligned.");
        e116.tryIt = s("点底部「习惯」→「＋添加习惯」从库里选一个（比如步数）→ 去「知行」看指数的构成；"
                        + "再到设置里打开「晨间首填」，明早打开知行会先进晨间页。",
                "Tap Habits at the bottom → \"+ Add a habit\" and pick one from the library (Steps, say) → "
                        + "open Zhixing to see how the index is made; then turn on \"Morning check-in\" in "
                        + "Settings — tomorrow morning, KnowDo opens on the morning page first.");
        out.add(e116);

        Entry e115 = new Entry("v1.15.0", "2026-10-24");
        e115.add(new Group(s("新功能", "New"),
                s("日历里能把任务拖进去了——打开日历的「任务列表」，左边出任务、右边是日历，"
                        + "把任务拖到想排的时刻就生成计划块（块长按计划时长 ×1.5，与排日程的老规矩一致）。",
                  "You can now drag tasks into the calendar — open the calendar's Task list: tasks on the "
                        + "left, calendar on the right. Drag a task to the moment you want and a plan block "
                        + "is created (its length is the task's planned length ×1.5, the usual scheduling rule)."),
                s("在日历里点任何一个任务块，左边会展开那个任务，笔记、子任务、开始/完成都能直接操作，"
                        + "和主页展开任务行是同一套。",
                  "Tap any task block in the calendar and that task expands on the left — note, subtasks, "
                        + "start/finish, all right there, the same expansion as on the home page.")));
        e115.why = s("排一天计划时不用在任务列表和日历之间来回跳；看到日历上的一块就能就地打开它。",
                "So planning a day doesn't mean jumping back and forth between the task list and the "
                        + "calendar — see a block on the calendar, open it right there.");
        e115.tryIt = s("主页点日历图标打开日历 → 点「任务列表」→ 把一个任务拖到右边某个时刻；"
                        + "再点日历上任意一个任务块，看左边是不是展开了它。",
                "Open the calendar from the home page → tap Task list → drag a task onto a time on the "
                        + "right; then tap any task block in the calendar and watch it expand on the left.");
        out.add(e115);

        Entry e114 = new Entry("v1.14.0", "2026-10-24");
        e114.add(new Group(s("新功能", "New"),
                s("没任务时它会出声催你：超过 5 分钟没有任何任务在计时，全屏弹出「接下来干什么」并响起鸟鸣，"
                        + "你不理它，隔 1 分钟再响、之后每 5 分钟响一次，直到你开始一个任务。",
                  "It now nags you out loud when nothing is running: after 5 minutes with no task timing, "
                        + "the full-screen \"What's next?\" pops up with birdsong. Ignore it and it rings again "
                        + "after 1 minute, then every 5 minutes, until you start a task."),
                s("空档催促成了你的个人设置：分温和／标准／严格三档，也可自定义——多久算空档、每轮响多久、"
                        + "隔多久再催、最多催几轮、出不出声，都能自己调；存在云端，手机与电脑共用同一份。"
                        + "（声音是严格档才有的：标准档只弹页面、不出声，温和档什么都不做。）",
                  "Idle nagging is now your personal setting: Gentle / Standard / Strict levels, or fully "
                        + "custom — how long counts as idle, how long each round rings, the gaps between "
                        + "rounds, how many rounds at most, and whether it makes sound. It lives in the cloud, "
                        + "shared between phone and computer. (Sound belongs to Strict only: Standard pops the "
                        + "page silently, Gentle does nothing.)"),
                s("悬浮窗可以定时关：点大悬浮窗的「关闭」，可以选 10／15／30／60 分钟后回来、"
                        + "等当前任务结束回来、自己定时间，或者永不再出现（设置里能再打开）。",
                  "The floating window can close on a timer: tap \"Close\" on the large floating window and "
                        + "pick back in 10/15/30/60 minutes, back when the current task ends, a custom time, "
                        + "or never again (re-enable it in Settings).")));
        e114.add(new Group(s("修复", "Fixed"),
                s("任务点完成后又自己弹回未完成：原因是同步对账拿着你点完成之前的旧快照整表落盘，"
                        + "把完成状态盖了回去，你的完成也一直没写上云。现在对账先读最新状态再合并，"
                        + "完成一点下去还会立刻单独写上云并回读确认。",
                  "A task you checked done flipping back to not-done on its own: sync reconciliation was "
                        + "writing a stale whole-table snapshot — taken before your tap — back over your "
                        + "completion, which also never reached the cloud. Reconciliation now re-reads the "
                        + "latest state before merging, and a completion is written to the cloud on its own "
                        + "right away, with a read-back check.")));
        e114.why = s("光弹页面没声音，你经常注意不到，空档就溜走了；悬浮窗以前只能在设置里开关，"
                        + "想清静一会儿又怕找不回来。催促的轻重也该由你定，而不是写死的；"
                        + "完成状态这种最基础的账，更不能被同步盖掉。",
                "A silent popup is easy to miss, and gaps slipped away; the floating window could only be "
                        + "toggled in Settings, so a quiet moment risked losing it. How hard it nags should "
                        + "be your call, not hardcoded — and something as basic as a completion must never be "
                        + "overwritten by sync.");
        e114.tryIt = s("①什么任务都不开，干等 5 分钟（或结束当前任务后不开始下一个），听它响；"
                        + "②展开大悬浮窗，点「关闭」，选「10 分钟后」；"
                        + "③到设置页守卫组里的「空档催促」，把档位切到「标准」再切回「严格」；"
                        + "④在列表里点完成一个任务，等十来秒，看它不再弹回。",
                "1) Start nothing and wait 5 minutes (or end a task and don't start the next) and hear it "
                        + "ring. 2) Open the large floating window, tap \"Close\", pick \"in 10 minutes\". "
                        + "3) In Settings, under Guards → \"Idle nagging\", switch the level to Standard and "
                        + "back to Strict. 4) Check a task done in the list, wait ten seconds, and see it stay done.");
        out.add(e114);

        Entry e113 = new Entry("v1.13.0", "2026-10-24");
        e113.add(new Group(s("修复", "Fixed"),
                s("子任务立刻生效：正在计时的任务中途加子任务，马上开始计这个子任务的时间，不再等下次启动；"
                        + "删掉正在计的子任务会立即推进到下一个，改时长按「新时长 − 已用」重算剩余，拖动排序不打断当前子任务。"
                        + "两个悬浮窗第一行随之即刻换成子任务名和它的倒计时，「总时长」也恒等于子任务之和。",
                  "Subtasks take over instantly: add a subtask to a task that is already timing and it starts counting "
                        + "right away — no waiting for the next start. Deleting the running subtask advances to the next one, "
                        + "changing a length recomputes the remainder as new length minus time used, and reordering never "
                        + "interrupts the current subtask. Both floating windows switch their first line to the subtask and "
                        + "its countdown at once, and the total always equals the sum of the subtasks.")));
        e113.add(new Group(s("新功能", "New"),
                s("更新日志弹框：每次升级后第一次打开知行会弹这个日志（就是你正在看的这个）；"
                        + "设置页里也有入口，随时能翻看最近 5 个版本。",
                  "What's-new popup: the first time you open KnowDo after an update, this changelog pops up "
                        + "(the one you are reading). There is also an entry in Settings, so you can flip through "
                        + "the latest 5 versions any time.")));
        e113.add(new Group(s("改进", "Improved"),
                s("日历页「问 AI」的提示词改了口径：Google 日历事件只供查看，排日程时不用避让、可以和它们重叠。",
                  "The calendar's \"Ask AI\" prompt now matches the current rule: Google Calendar events are "
                        + "view-only reference — scheduling does not work around them and may overlap them.")));
        e113.why = s("子任务是你在实际使用中临时想到的拆分方式，之前要等任务重新开始才生效，不符合你「设了就开始」的预期。",
                "Subtasks are how you split work on the fly while using the app; having to wait for the task to "
                        + "start over broke your \"set it and it starts\" expectation.");
        e113.tryIt = s("开始一个任务计时 → 打开大悬浮窗，点「＋加子任务」加一个 → "
                        + "看中号和大号悬浮窗第一行是不是变成子任务名和它的倒计时。",
                "Start timing a task → open the large floating window, tap \"+ Add subtask\" and add one → "
                        + "check that the first line of both the medium and large floating windows now shows "
                        + "the subtask name and its countdown.");
        out.add(e113);

        return out;
    }
}
