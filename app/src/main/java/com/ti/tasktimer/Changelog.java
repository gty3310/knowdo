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

        Entry e117 = new Entry("v1.17.0", "2026-10-25");
        e117.add(new Group(s("改进", "Improved"),
                s("晨间页现在会在你早上第一次用手机时就全屏出现，不用先打开知行。",
                  "The morning page now comes up full-screen the first time you use your phone in the morning — no need to open KnowDo first.")));
        e117.add(new Group(s("新功能", "New"),
                s("晨间页可以「暂停一会儿」：有急事先去处理，选个分钟数，它到点会自己回来。",
                  "The morning page can \"pause for a bit\": handle the urgent thing first, pick how many minutes, and it comes back on its own when the time is up."),
                s("设置「晨间」组里加了「测试晨间页」按钮，白天也能随时试一遍完整流程。",
                  "A \"Test the morning page\" button in Settings → Morning lets you run the full flow any time of day."),
                s("习惯页加了「晨间记录补记/修改」入口，可以切换日期，补改今天、昨天、前天的记录。",
                  "The Habits page has a \"Morning records: backfill / edit\" entry — switch the date and fix up today's, yesterday's, or the day before's records.")));
        e117.why = s("晨间首填的意义就是一天的第一件事——只有打开知行才出现，约束力不够；"
                        + "但真有急事时也不能被一页纸堵住，所以给它一个能推后、能补记的完整闭环。",
                "Morning check-in is meant to be the day's very first thing — appearing only when you opened "
                        + "KnowDo wasn't binding enough; but a real emergency shouldn't be blocked by one page "
                        + "either, so it now has the full loop: defer it, and backfill it later.");
        e117.tryIt = s("设置 → 晨间 → 点「测试晨间页」；在页面上点「暂停一会儿」选 5 分钟，看它到点是不是自己回来；"
                        + "再去习惯页点「晨间记录补记/修改」，切换到前天改一笔保存。",
                "Settings → Morning → tap \"Test the morning page\"; on the page, tap \"Pause for a bit\" "
                        + "and pick 5 minutes to watch it come back on its own; then go to Habits → "
                        + "\"Morning records: backfill / edit\", switch to the day before yesterday, change "
                        + "something and save.");
        out.add(e117);

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


        return out;
    }
}
