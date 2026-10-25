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

        Entry e119 = new Entry("v1.19.0", "2026-10-25");
        e119.add(new Group(s("修复", "Fixed"),
                s("暂停结束后不再没人管：以前暂停时间一到、叫回一次之后就再也不催了——内部有个状态判错，"
                        + "把到期的暂停一直当成「还在暂停中」。现在暂停到期就算空档，照常进入催促。",
                  "A finished pause is no longer a dead end: before, once a pause ran out and called you back once, "
                        + "it never nagged again — an internal state was misjudged and the expired pause counted as "
                        + "\"still pausing\" forever. Now an expired pause counts as idle time and the nagging applies as usual.")));
        e119.add(new Group(s("新功能", "New"),
                s("空档催促变三段：先震动三十秒不出声，没反应再让声音在一分钟里由小变大，到顶停下，过五分钟再来一轮，直到你开始任务。",
                  "Idle nagging now comes in three stages: vibrate for thirty seconds with no sound first; if nothing happens, "
                        + "the sound fades in over a minute and stops at the top; five quiet minutes, then the next round — until you start a task."),
                s("alarm 页底部多了个大按钮，路人点一下就静音：手机响个不停时，旁边的人能帮你关掉，又碰不到你的任务；"
                        + "你下次亮屏会先看到谢谢页，可以直接继续刚才的任务。",
                  "The alarm page has a big button at the bottom that anyone can tap to mute it: if your phone keeps ringing, "
                        + "someone nearby can silence it without ever touching your tasks — and on your next unlock you get a "
                        + "thank-you page first, where you can jump straight back into the task you were on."),
                s("拿起手机时没有任务在进行，会直接全屏问你接下来做点什么，点一下就开始。",
                  "Pick up your phone with nothing running and it asks you, full-screen, what to do next — one tap starts it."),
                s("建议设置清单：电池优化、悬浮窗、全屏通知、精确闹钟、通知权限，逐项能查能开。",
                  "A Suggested settings list — battery optimization, display over other apps, full-screen notifications, "
                        + "exact alarms, and notifications — each one checkable and openable right from the list.")));
        e119.why = s("你反馈暂停完把手机晾着永远没动静，根子在那个判错的状态；先留个不出声的台阶，真没听见才出声，也不一上来就吵；"
                        + "不给空档留缝，拿起手机就得先定下一个任务；那五项设置不开，系统可能把知行在后台停掉，晨间和提醒就不准时——不开也能用，所以叫建议。",
                "You reported that after a pause the phone just sat there in silence forever — the root was that misjudged state. "
                        + "A silent first step before any sound; no gap left unasked — pick up the phone and you name the next task first. "
                        + "And without those five settings the system may stop KnowDo in the background, so the morning page and reminders "
                        + "run late — but everything still works without them, which is why they're suggestions.");
        e119.tryIt = s("①开始一个任务，暂停一分钟，全程别碰手机，到期后看它会不会接着催；"
                        + "②让它响起来，点底部大按钮，再灭屏、亮屏；"
                        + "③停掉所有任务，灭屏再解锁；④设置里点「建议设置」。",
                "1) Start a task, pause it for one minute, and don't touch the phone — after it expires, watch whether it keeps nagging. "
                        + "2) Let it ring, tap the big button at the bottom, then turn the screen off and on again. "
                        + "3) Stop everything, lock and unlock. 4) In Settings, open \"Suggested settings\".");
        out.add(e119);

        Entry e118 = new Entry("v1.18.0", "2026-10-25");
        e118.add(new Group(s("改进", "Improved"),
                s("日历改版：展开时不再盖住底部三个页签，随时可以切换；左边就是任务列表，右边是日历。",
                  "Calendar reworked: opening it no longer covers the three bottom tabs — switch any time. The task list is on the left, the calendar on the right."),
                s("排计划的方式变了：直接按住任务列表里的任务，拖进右侧日历松手就生成计划块，落点有预览并标起止时间；日历里原来单独的小列表去掉了。",
                  "Planning works differently now: hold a task in the list and drag it into the calendar on the right — a preview with start–end times follows, and letting go creates the plan block. The calendar's separate little list is gone.")));
        e118.add(new Group(s("新功能", "New"),
                s("日历分屏里，列表中每个任务都带开始按钮，点一下就开始计时，不用展开、不用回主页。",
                  "In the calendar split view, every task in the list has its own start button — one tap starts timing, no expanding and no going back to the home page.")));
        e118.why = s("上一版把能拖的任务放在日历旁边的附属列表里，多了一层中转。你要的是：任务在哪、日历在哪，手一拖就过去。",
                "The last version put the draggable tasks in a side list next to the calendar — one extra hop. What you asked for: the tasks where they are, the calendar where it is, one drag across.");
        e118.tryIt = s("主页点开日历 → 按住一个没排的任务拖到右边某个时刻松手；再点列表里任务的开始按钮试一下。",
                "Open the calendar on the home page → hold an unscheduled task, drag it to a time on the right and let go; then tap the start button on a task in the list.");
        out.add(e118);

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



        return out;
    }
}
