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

        Entry e112 = new Entry("v1.12.0", "2026-10-24");
        e112.add(new Group(s("新功能", "New"),
                s("「我的志」：在设置页最上方亲手写下立志的那句话，它会出现在分神问询页、任务/子任务结束检查页、"
                        + "暂停叫回页上（金色衬线一行，给你看的，不是按钮）；登录后随账号同步。",
                  "\"My aspiration\": write your line at the very top of Settings. It appears — in soft gold serif, "
                        + "to read, not to tap — on the drift inquiry page, the task and subtask end check pages and "
                        + "the pause call-back page, and syncs with your account."),
                s("「磨练自检」：被分神问询时先自答「刚才在做该做的事吗」和现在的状态"
                        + "（平静／烦／躁／昏沉／贪多／求快）；给任务加时前也要先点一下状态。"
                        + "只记录、同步保存，供日后的每日省察对账。",
                  "\"Self-check\": the drift inquiry page first asks whether you were doing what you should, plus "
                        + "your current state (Calm / Irritated / Restless / Groggy / Overreaching / Rushing); "
                        + "extending a task asks for your state first, too. Records only, synced, saved up for a "
                        + "future daily review.")));
        e112.add(new Group(s("改进", "Improved"),
                s("日历页改成三栏：Google 日历一条窄栏（约 17%）、计划主栏最宽（约 58%）、实际窄栏（约 25%）；"
                        + "日历事件只在自己栏里显示，不再盖住计划块。",
                  "The calendar is now three columns: a narrow Google Calendar lane (about 17%), the plan lane "
                        + "widest (about 58%), and a narrow actual lane (about 25%). Calendar events stay in their "
                        + "own lane and never cover plan blocks."),
                s("悬浮窗子任务排版统一：有子任务时第一行是子任务（最大字）＋它的倒计时，"
                        + "第二行是母任务（细体小字），中号、大号两窗一致。",
                  "Floating-window subtask layout unified: with subtasks, line one is the subtask in the largest "
                        + "type plus its countdown, line two the parent task in small thin type — the same in the "
                        + "medium and large windows.")));
        e112.add(new Group(s("修复", "Fixed"),
                s("暂停卡的时长按钮改成等宽网格：修好「10 分钟」被折成两行、「60 分钟」被挤成竖排；"
                        + "标签也简化了（30 分钟／1 小时／2 小时）。",
                  "The pause card's length buttons are now an equal-width grid — no more \"10 min\" wrapped onto "
                        + "two lines or \"60 min\" squeezed vertical; labels are simpler too (30 min / 1 h / 2 h).")));
        e112.why = s("知行合一就是把「知道的」去「做」：立志让你的目标在最容易分神的时刻看得见，"
                        + "自检把每一次分神和加时都记成一笔日后能对账的材料。",
                "Knowing-and-doing means acting on what you know: the aspiration keeps your aim visible at the "
                        + "exact moments you are most likely to drift, and self-checks turn every drift and every "
                        + "extension into a record you can later review.");
        out.add(e112);

        Entry e111 = new Entry("v1.11.0", "2026-10-24");
        e111.add(new Group(s("新功能", "New"),
                s("「子任务」：大任务拆成一串子任务逐个计时——主页展开区和大号悬浮窗里都能加、改、拖动排序；"
                        + "每个子任务到点弹检查页逐个推进（下一个／加时／跳过／整个完成）；"
                        + "任务总时长自动等于子任务之和。",
                  "\"Subtasks\": split a big task into a queue of small timed pieces — add, edit and drag to "
                        + "reorder them in the home expansion or the large floating window. Each subtask's end "
                        + "pops a check page to advance one by one (next / extend / skip / finish whole task), "
                        + "and the task total is always the sum of its subtasks."),
                s("「暂停模式」：点暂停先选目的（emoji 大格子：上厕所、喝水、吃饭、回电话、喊去帮忙、睡午觉、"
                        + "换换脑子🏃、去上课，或自定义）和时长，还能开静音（选「去上课」自动开）。"
                        + "普通模式到点先短震，60 秒没反馈就响 60 秒由小渐大的鸟鸣；"
                        + "静音模式只亮屏＋通知，没反馈每 5 分钟再提醒一次。",
                  "\"Pause mode\": pausing first asks what for — big emoji tiles (bathroom, water, meal, call back, "
                        + "help out, nap, go for a run 🏃, class, or custom) — and for how long, with an optional "
                        + "silent mode (\"class\" turns it on automatically). A normal pause vibrates at the end and, "
                        + "after 60 seconds without a response, rings a 60-second fade-in birdsong; silent mode only "
                        + "lights the screen with a notification, repeating every 5 minutes until you respond.")));
        e111.why = s("大任务容易拖，拆成小段更容易开始、更容易推进；暂停也不该是无底洞——"
                        + "它本身就是一件有名字、有时长的临时任务。",
                "Big tasks invite delay; small pieces are easier to start and easier to finish. And a pause "
                        + "shouldn't be a void — it is a small named task with a length of its own.");
        out.add(e111);

        return out;
    }
}
