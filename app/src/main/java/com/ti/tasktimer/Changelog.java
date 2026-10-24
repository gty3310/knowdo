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

        Entry e110 = new Entry("v1.10.0", "2026-10-24");
        e110.add(new Group(s("新功能", "New"),
                s("「任务衔接守卫」：App 永不允许出现无状态的空档——做完一件事、回到主页，"
                        + "或巡检发现没有任务在跑时，全屏弹出「接下来干什么？」：填名字、选时长、点开始，"
                        + "新任务立刻开始计时；连睡觉也要建一个「睡觉」任务占位。",
                  "\"Next-task guard\": the app never allows a stateless gap — after you finish something, return "
                        + "home, or whenever the watchdog finds nothing running, a full-screen \"What's next?\" "
                        + "asks for a name and a length, and starts timing the new task immediately. Even sleep "
                        + "gets a \"sleep\" task.")));
        e110.add(new Group(s("改进", "Improved"),
                s("暂停在所有入口都必须先选时长（悬浮窗、任务行、展开面板、日历块、暂停并切换、重置），"
                        + "没有「无限期暂停」了；暂停叫回页多了「换下一个任务」，能直达「下一个任务」页。",
                  "Pausing anywhere — floating window, task rows, the expansion panel, calendar blocks, switch, "
                        + "reset — now always asks for a length first; there is no open-ended pause any more. The "
                        + "pause call-back page can also jump straight to the \"What's next?\" page.")));
        e110.why = s("最容易滑走的就是两件事之间的空档：下一件还没定，手就先摸向了手机。"
                        + "让任何时刻只剩计时、带时长的暂停、填下一个任务这三种状态，分神就没有缝可钻。",
                "The gap between two tasks is where time slips away: nothing decided yet, and the hand reaches "
                        + "for the phone. With only three states at any moment — timing, a timed pause, or filling "
                        + "in the next task — distraction has no crack to slip through.");
        out.add(e110);

        Entry e19 = new Entry("v1.9.0", "2026-10-24");
        e19.add(new Group(s("新功能", "New"),
                s("「任务闹钟」：每个任务可单独设一个闹钟时间——到点就响，与计时无关（会议、起床这类怕错过的事；"
                        + "每日任务响完自动续到明天）；也可以设「计时结束时响铃」（午睡这类），响完再进结束检查页。"
                        + "铃声是清晨鸟鸣，60 秒由小渐大，走闹钟通道，手机静音也照样响。",
                  "\"Per-task alarms\": each task can have an alarm time that rings whether or not it is timing "
                        + "(meetings, wake-ups — things you can't afford to miss; daily tasks re-arm for the next "
                        + "day), and/or ring when its timer ends (naps), then flow into the end check. The sound "
                        + "is morning birdsong fading in over 60 seconds on the alarm stream, so it rings even "
                        + "when the phone is muted.")));
        e19.add(new Group(s("改进", "Improved"),
                s("设置页账号区精简，只留一行简洁状态；云端模式的实时同步改成常开，去掉了开关和说明文字。",
                  "The Settings account area is trimmed to one simple status line; in cloud mode realtime sync "
                        + "is now simply always on — the toggle and its explanation are gone.")));
        e19.why = s("有些提醒必须独立于计时存在（怕错过的会、要起的床），有些只需要在倒计时结束时叫醒（午睡）；"
                        + "而同步这种事，默认实时就好，不需要一个开关让人惦记。",
                "Some reminders must live independently of any timer (a meeting you can't miss, a bed you must "
                        + "leave), and some only need to fire when a countdown ends (a nap). Sync, meanwhile, "
                        + "should just be realtime by default — not another switch to think about.");
        out.add(e19);

        return out;
    }
}
