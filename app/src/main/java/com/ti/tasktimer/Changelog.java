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

        Entry e1690 = new Entry("v1.69.0", "2026-11-05");
        e1690.add(new Group(s("改进", "Improved"),
                s("检查变密了：随机 10–20 秒查一次（原来 15–45 秒）",
                  "Checks are denser now: one every 10–20 seconds at random (was 15–45)"),
                s("有网时，屏幕内容跟任务合不合拍一律由云端 AI 终判；本地小模型只在没网时兜底",
                  "While you're online, whether the screen fits your task is always settled by the cloud AI; the on-device model only steps in offline"),
                s("本地小模型下载的毛病修了：哪一片坏了会写明数字并单独重下，下好还要自检能用才算数",
                  "The on-device model download is fixed: a bad shard now says exactly which one and by how much, retries on its own, and the model must pass a self-check before it counts as ready"),
                s("每次判定都留了痕：是本地判的、云端判的、还是断网时兜底判的，日志里分得清清楚楚",
                  "Every verdict now leaves a trace: judged on-device, by the cloud, or by the offline fallback — the log tells them apart")));
        e1690.why = s("你说分神时守卫常常察觉不到——这版把检查变密、终判换成云端优先，并让每次判定都留痕，漏判能查账",
                "You said the guard often misses your distractions — this release densifies the checks, makes the cloud the first judge, and leaves a trace for every verdict so misses can be audited");
        e1690.tryIt = s("开个任务去刷购物软件，看守卫多快有反应；再开飞行模式刷一会儿，关掉飞行模式后到守卫日志里能看到标着离线裁决的记录",
                "Start a task and browse a shopping app to see how fast the guard reacts; then browse a while in airplane mode, turn it off, and look for the offline-verdict records in the guard log");
        out.add(e1690);

        Entry e1680 = new Entry("v1.68.0", "2026-11-05");
        e1680.add(new Group(s("新增", "New"),
                s("晨间页的日记框下面多了一块「本周目标反思」：本周目标还没定、哪周的反思还没写，都会直接摆在那里，就地写、写完就存，跟写日记一样",
                  "The morning page gains a Weekly goal & reflection section right under the diary box: a goal for this week that isn't set yet and a weekly reflection that isn't written yet are simply there — write in place, save in place, just like the diary")));
        e1680.add(new Group(s("改进", "Improved"),
                s("不用再绕去习惯页补了；习惯页的详情页照旧能看全部历史",
                  "No more detour to the habits page to fill these in; the habit's detail page still keeps the full history")));
        e1680.why = s("你说每周目标反思跟日记是同一个概念——就该在晨间页日记旁边就地写，不该再绕去习惯页",
                "You said the weekly goal & reflection is the same kind of thing as the diary — it belongs right beside the diary on the morning page, not on a separate trip to the habits page");
        e1680.tryIt = s("明早打开晨间页，滑到日记框下面，写两句本周目标存一下，退出重进还在，就是对了",
                "Tomorrow morning, open the morning page, scroll below the diary box, write a couple of lines of this week's goal and save; leave and come back — still there, that's it");
        out.add(e1680);

        Entry e1670 = new Entry("v1.67.0", "2026-11-05");
        e1670.add(new Group(s("改进", "Improved"),
                s("「知」里编辑人名、问题、原则改成先存手机：网络不好也先存下来，恢复后自动补传，不会再出现改了半天没存上",
                  "Editing people, questions and principles in Know now saves to the phone first: a bad connection no longer loses your edit — it syncs up automatically once you're back online"),
                s("「知」页会自动跟上最新数据；万一拉取失败，页面上会写明并能点着重试，不再悄悄显示旧的",
                  "The Know page now keeps itself up to date on its own; if a load fails it says so on the page with a tap-to-retry, instead of quietly showing stale data"),
                s("「今天安排」打开时会先自检：日程不是今天的，就拿手机里的任务当场补排，不再出现一页空白",
                  "Plan today now checks itself when opened: if the schedule isn't today's, it fills the day in from the tasks on the phone right away — no more blank page"),
                s("底部第四个签改名为「合一」",
                  "The fourth tab at the bottom is renamed to Oneness")));
        e1670.add(new Group(s("新增", "New"),
                s("人名、问题、原则、日记都能标重要性了：编辑时选无星、一星、二星；列表里带星的排在前面、显示朱砂红的星；筛选行最前面钉着 ★★ 和 ★，点一下就只看这一档",
                  "People, questions, principles and diary entries can now carry importance: pick no star, one star or two when editing; starred entries sort first and show vermilion stars; ★★ and ★ are pinned at the front of the filter row — tap one to see only that tier")));
        e1670.why = s("昨晚到今早连着出事：知页只显示旧的一半、编辑存不上、今天安排一片空白——根子都是拉取一失败就装作没事、拿旧数据糊弄。这一版让写入先落手机、失败必须出声",
                "Last night and this morning kept going wrong: Know showed only half the people, an edit wouldn't save, Plan today was blank — all because failed loads silently pretended nothing was wrong. This release saves to the phone first and makes failures speak up");
        e1670.tryIt = s("给任意一个人标二星，回列表看它排到最前、筛选行点 ★★ 能单独筛出来；再打开「今天安排」，应直接看到今天的安排",
                "Give any person two stars, then check the list: they sort to the front, and tapping ★★ in the filter row shows only that tier; then open Plan today — today's plan should just be there");
        out.add(e1670);

        Entry e1660 = new Entry("v1.66.0", "2026-11-04");
        e1660.add(new Group(s("修复", "Fixed"),
                s("晨间页「戒晚睡」的按钮问反了，已翻正；过去记反的记录一并对调",
                  "The morning page's Late-sleep question was backwards and is now flipped; the records it got wrong have been swapped to match")));
        e1660.add(new Group(s("改进", "Improved"),
                s("晨间页自读直接显示全文，读完点按钮就勾上；晨间卡不再拦着日记框；写日记不再在上头重复一行",
                  "On the morning page, readings now show the full text with a done button right there; the morning card no longer blocks the diary box; writing a diary no longer repeats as a row above")));
        e1660.why = s("你说按钮一直反着答、数据全记反了；晨间页也要少绕一步",
                "You said the button had been answered backwards all along and the data was all wrong; and the morning page should take one less detour");
        e1660.tryIt = s("明早晨间页：自读全文就在页上，戒晚睡按「做到了/没做到」答",
                "Tomorrow morning: the reading is right on the page, and answer Late-sleep with Done / Not done");
        out.add(e1660);

        Entry e1650 = new Entry("v1.65.0", "2026-11-04");
        e1650.add(new Group(s("修复", "Fixed"),
                s("每日重复的任务，「已用」改为只算今天，到了新的一天自动清零",
                  "For daily repeating tasks, Used now counts today only and resets to zero on a new day")));
        e1650.why = s("你说每日任务跨天还挂着以前的累计，跟旁边的「剩余」对不上",
                "You said a daily task still showed the accumulated total from earlier days, which didn't match the Left column next to it");
        e1650.tryIt = s("看太极这类每日任务：已用只剩今天做的分钟数，明天打开是 0",
                "Check a daily task like Taiji: Used shows only the minutes you did today, and tomorrow it starts at 0");
        out.add(e1650);

        Entry e1640 = new Entry("v1.64.0", "2026-11-04");
        e1640.add(new Group(s("改进", "Improved"),
                s("底部图标线条加粗；未选中时图标里的红色也保留红色，不再全灰",
                  "Bottom tab icons get thicker strokes; unselected icons keep their red parts red instead of going all grey")));
        e1640.why = s("你说线条太细看不出意思，没选中时纯灰也把意思盖住了",
                "You said the strokes were too thin to read, and the all-grey unselected state hid what the icons mean");
        e1640.tryIt = s("看底部签栏，没点中的签也能认出图标里的红点和灯焰",
                "Look at the bottom tab bar — the tabs you haven't tapped still show the red dot and the flame");
        out.add(e1640);

        Entry e1630 = new Entry("v1.63.0", "2026-11-04");
        e1630.add(new Group(s("新增", "New"),
                s("底部五签换上正式图标：行是路上的脚印、知是书上心灯、习惯是循环、知行是半圆、聊聊是话泡",
                  "The bottom five tabs get their finished icons: Act is footprints along a path, Know is a heart-lamp over an open book, Habits is a cycle, Unity is a half circle, Chat is a speech bubble")));
        e1630.why = s("图标定稿入库，更新不会再丢；知的图标里，书是明理、上头一点灯焰是本心",
                "The finished icons now ship inside the app, so updates can't lose them; in Know's icon, the book is understanding and the small flame above it is the original heart");
        e1630.tryIt = s("看底部签栏，点每个签切换，选中和未选中的图标深浅不同",
                "Look at the bottom tab bar and tap through the tabs — selected and unselected icons differ in depth");
        out.add(e1630);

        Entry e1620 = new Entry("v1.62.0", "2026-11-04");
        e1620.add(new Group(s("新增", "New"),
                s("快捷编辑栏：编辑框下方常驻，｛｛ 与 ｝｝ 是同一个键，开启引用后自动变合上；另带全选、复制、粘贴／引用合上自动新建：选好类型后把想法直接写完，打 ｝｝ 合上就自动存成新的人名、原则或问题，文中提到的人会自动关联上",
                  "Quick edit bar: it now sits under every editor — ｛｛ and ｝｝ are the same key, which flips to close once a citation is open; select-all, copy and paste sit beside it / Close to create: pick a type, write your thought out in full, then close with ｝｝ and it is saved as a new person, principle or question — people mentioned in the text are linked automatically")));
        e1620.add(new Group(s("改进", "Improved"),
                s("引用选择窗改版：打出 ｛｛ 后先在光标上方的小窗里选类型，接着在正文里打字就是搜索，选中现成的点一下就好；小窗半透明、不抢光标，不理它可以接着写",
                  "Citation picker rebuilt: after ｛｛, pick the type in the small popup above your cursor, then keep typing in the text itself to search; tap a match to insert it. The popup is translucent and never steals your cursor — ignore it and keep writing")));
        e1620.why = s("写作时光标不该离开正文，找词条和新建都不该把人拽进大弹窗",
                "Your cursor should never leave the text while writing; finding and creating entries should not drag you into a big popup");
        e1620.tryIt = s("在写日记或任务笔记时打 ｛｛，在小窗点一类，接着打字找现成的；找不到就把想法写完，点快捷栏右边的 ｝｝ 合上，它会自动存进「知」",
                "While writing a diary entry or a task note, type ｛｛, tap a type in the popup, then type to find an existing entry; if there is none, finish the thought and tap the ｝｝ key on the quick bar to close — it files itself into Know");
        out.add(e1620);

        Entry e1610 = new Entry("v1.61.0", "2026-11-04");
        e1610.add(new Group(s("新增", "New"),
                s("引用：在日记、任务描述、原则、问题、备忘里输入｛｛，就能把人名、原则、问题、别的日记选进来，正文里变成一个红色的链接／点链接改词条：点正文里的链接，会升起一个小窗，直接改那个词条，改完正文里的名字自动跟着变／搜不到就新建：选择窗底下有创建按钮，建好自动插回正文／日记标题：每篇日记有了标题，6 位日期加一句主题，在「知 · 日记」里一眼看清哪篇是哪篇（标题由 AI 生成，也能自己改）／问题短题：每个问题多了一个 10 字以内的短题，引用问题时显示短题，不占地方",
                  "Citations: type {{ in a diary entry, a task note, a principle, a question or a memo to pick a person, principle, question or another day's diary — it lands in the text as a red link / Tap a link to edit its entry: a small sheet rises and you edit that entry right there; rename it and the name in your text follows / Create when search comes up empty: the picker has a create button at the bottom, and the new entry is inserted back into your text / Diary titles: every diary day now has a title — the 6-digit date plus a one-line theme — so in Know · Diary you can tell at a glance which day is which (titles are AI-generated and editable) / Question short titles: every question gains a short title of up to 10 characters, shown when the question is cited so it takes no room")));
        e1610.why = s("让「知」里的东西在写作时互相连起来，写着写着就把人和事、原则和问题织成一张网",
                "So the things in Know connect while you write — people and events, principles and questions woven into one web as you go");
        e1610.tryIt = s("写今天的日记时，输入｛｛，选一个认识的人插进去，再点一下那个红名字",
                "While writing today's diary, type {{, pick someone you know, then tap the red name");
        out.add(e1610);

        Entry e1600 = new Entry("v1.60.0", "2026-11-03");
        e1600.add(new Group(s("新增", "New"),
                s("「知」里加了日记页签／日记可以和原则、人名互相挂上关联／在原则下面能看到它是从哪几天的日记里来的",
                  "The Know tab gains a Diary page / a day's diary can be linked to principles and people, both ways / under a principle you can see which days' diaries it grew out of")));
        e1600.why = s("原则大多是从某一天的日记里长出来的，把出处连起来，日后回看才知道当时为什么这么想",
                "Most principles grow out of one particular day's diary. Link the source back in, and when you look again later you will know why you thought it then");
        e1600.tryIt = s("打开「知」点「日记」，点开任意一天往下看，给它挂一条原则／再打开那条原则，日记已经在它下面了",
                "Open Know, tap Diary, open any day and scroll down to link a principle to it / then open that principle — the diary is already listed under it");
        out.add(e1600);

        Entry e1590 = new Entry("v1.59.0", "2026-11-03");
        e1590.add(new Group(s("新增", "New"),
                s("「知」改版：原则、问题、人名三页，互相打通／每条原则分思维与行为／问题可标待问、已问／人名页可按拼音首字母找人／三页都有搜索、标签筛选、复制整表、卡片与列表两种看法",
                  "The Know tab is rebuilt: Principles, Questions and People on three pages, all linked together / every principle is marked thinking or action / questions carry a to-ask or asked state / People can be found by the pinyin initial of a name / all three pages have search, tag filters, copy-the-whole-table, and card or list views")));
        e1590.why = s("知要沉淀成能互相牵引的网，不是一堆散条目／找人、找问题、找原则都该一步到位",
                "What you know should settle into a web that pulls itself together, not a pile of loose entries / finding a person, a question or a principle should take one step");
        e1590.tryIt = s("点开「知」，在人名里点开一个人看他的问题／在问题里打几个字试试搜索／长按或点开一条给它加一条关联",
                "Open Know and tap a person under People to see their questions / type a few characters under Questions to try search / open any entry and add a link to it");
        out.add(e1590);

        Entry e1580 = new Entry("v1.58.0", "2026-11-03");
        e1580.add(new Group(s("改进", "Improved"),
                s("行页换上新样子：「行」页按新的全软件设计换了外观：米白纸面、宋体标题、任务是一张张细线卡片；优先级标记全软件统一（必做朱砂实心点、重要墨色空心点、次要灰点）；开始按钮改成圆形描边",
                  "The Act tab has a new face: it now follows the new app-wide design - rice-paper background, serif titles, and tasks as thin-bordered cards; priority marks are unified app-wide (a solid vermilion dot for must-do, a hollow ink dot for important, a small grey dot for the rest); the start button is a circular outline")));
        e1580.why = s("全软件的 UI 要统一成一套，这是第一页，后面几页会陆续跟上",
                "The whole app is moving to one design system; this is the first page, the others will follow");
        e1580.tryIt = s("打开「行」页看一眼就知道，功能和用法跟原来完全一样，只是长相变了",
                "Just open the Act tab: everything works exactly as before, it only looks different");
        out.add(e1580);

        Entry e1570 = new Entry("v1.57.0", "2026-11-03");
        e1570.add(new Group(s("改进", "Improved"),
                s("行页按日子排，未排期的标出来：「行」页的任务改成以日期为主轴排序：逾期的在最前，然后今天、明天，一天一天往后，跟日历的看法一致；截止是今天、却还没被排进今日日程的任务，行里会挂一个「未排期」小标",
                  "The Act list now runs by day, and unscheduled tasks are marked: tasks on the Act tab are sorted by date first - overdue at the very top, then today, tomorrow and onwards, the way the calendar sees them; a task due today that has no block in today's schedule yet carries a small Unscheduled tag in its row")));
        e1570.why = s("列表和日历看法一致，排今天时一眼就知道哪些任务还没落位",
                "The list and the calendar should see the day the same way, so when you plan today you can spot at a glance which tasks have not been given a place yet");
        e1570.tryIt = s("打开「行」页从上往下看，任务应该按日子排下来；今天要做又还没排时间的任务，旁边有「未排期」",
                "Open the Act tab and read down: tasks should be ordered by day; a task due today with no time planned yet shows Unscheduled next to it");
        out.add(e1570);

        Entry e1560 = new Entry("v1.56.0", "2026-11-03");
        e1560.add(new Group(s("新增", "New"),
                s("其他重要任务也看得见了：今天安排页底部多了一个可以展开的区域，装着必做和重要、但截止日不是今天的任务，按截止日从近到远排；看中哪件，点它旁边的「改到今天」就拉进今天",
                  "Other important tasks are visible now: a collapsible section at the bottom of Plan today holds must-do and important tasks whose deadline is not today, nearest first; tap Move to today next to one and it joins today's list")));
        e1560.why = s("排今天的时候，也该顺手看看后面几天压着的大事，别等到了跟前才发现",
                "While planning today you should also see the big things waiting in the next few days, instead of meeting them only when they are already due");
        e1560.tryIt = s("展开「其他重要任务」，挑一件点「改到今天」，它就进今天的清单了",
                "Expand Other important tasks, pick one and tap Move to today - it lands on today's list");
        out.add(e1560);

        Entry e1550 = new Entry("v1.55.0", "2026-11-03");
        e1550.add(new Group(s("新增", "New"),
                s("今天安排里直接加任务：页面底部多了「添加任务」和「跟 AI 一起添加任务」。前者点开就能新建今天的任务；后者展开一个聊天框，你把今天想做的几件事说给 AI，它整理成一张清单，你确认了才添加",
                  "Add tasks right from Plan today: two new buttons at the bottom - Add task opens the usual new-task sheet with today prefilled; Add with AI opens a chat panel where you tell the AI several things you want to do today, it shapes them into a list, and nothing is added until you confirm")));
        e1550.why = s("早上排今天时，想到什么就该当场记下、排进去，不用再切去别的页面",
                "When you are planning the day, whatever comes to mind should be captured and planned on the spot - no switching to another page");
        e1550.tryIt = s("点「跟 AI 一起添加任务」，一口气说三四件今天要做的事，等它给出清单，核对时长和优先级，点「就这样添加」，任务就进今天的清单了",
                "Tap Add with AI, say three or four of today's tasks in one go, wait for its list, check the lengths and priorities, tap Add them, and the tasks land on today's list");
        out.add(e1550);

        Entry e1540 = new Entry("v1.54.0", "2026-11-03");
        e1540.add(new Group(s("改进", "Improved"),
                s("任务卡的截止钮多了「明天」「周六」「下周一」「挑选日期」；推迟的任务不再消失，只是变灰留在原地，点「改到今天」随时拉回来；在这页改日期、改重要性都不会把列表顺序打乱，想重排点右上角「重新排序」；底部红字只点名今天排不下的任务",
                  "Task cards gain Tomorrow, Saturday, Next Monday and Pick-a-date deadline buttons. A postponed task no longer vanishes - it just turns grey where it sits, and Move to today brings it back any time. Changing dates or priorities on this page never reshuffles the list; tap Re-sort at the top right when you do want a new order. The red no-room line at the bottom now names only today's tasks")));
        e1540.why = s("早上排今天时，要的是稳住一盘棋慢慢挑，而不是每改一个、名单就跳一次",
                "Morning planning is about holding the whole board steady while you pick - not watching the list jump every time you change one thing");
        e1540.tryIt = s("展开一张卡点「明天」，卡变灰但还在原位；再点「改到今天」就恢复。随便改几个任务的重要性，顺序纹丝不动；点右上角「重新排序」才会重排",
                "Expand a card and tap Tomorrow: the card turns grey but stays put; tap Move to today and it is back. Change a few priorities and watch the order hold; then tap Re-sort at the top right to reorder");
        out.add(e1540);

        Entry e1530 = new Entry("v1.53.0", "2026-11-03");
        e1530.add(new Group(s("修复", "Fixed"),
                s("更新下载修好了：以前下新版时一切到别的软件，下载就会断、还报一句英文错。现在下载会在后台接着下，切走、锁屏都不怕；断了也能从断点接着下，不用从头来",
                  "Update downloads are fixed: starting a download and switching to another app used to kill it and leave a raw English error behind. Downloads now keep going in the background - switch away, lock the screen, it does not matter - and a broken download resumes from where it stopped instead of starting over")));
        e1530.why = s("新版是所有新功能的入口，这条路必须稳——你不该坐着等它下载",
                "A new version is the doorway to every new feature, so this road has to be solid - you should not have to sit and watch it download");
        e1530.tryIt = s("有新版时点下载，然后切去用别的软件；下好后会有一条通知告诉你，点通知就能安装；回到软件里也会弹安装提示",
                "When a new version shows up, tap download, then go use other apps. A notification tells you when it is ready - tap it to install. Coming back to KnowDo also pops the install prompt");
        out.add(e1530);

        Entry e1520 = new Entry("v1.52.0", "2026-11-03");
        e1520.add(new Group(s("修复", "Fixed"),
                s("修了一个同步口径的问题——在云端改过的习惯类型和日记续写，会被手机本地的旧数据覆盖。这版加了一条「云端补丁」通道，改动能在手机上真正落地",
                  "Fixed a sync-semantics problem: habit types and diary continuations edited from the cloud side were being overwritten by the phone's older local data. This release adds a cloud-patch channel, so such edits actually land on the phone")));
        e1520.why = s("知行的数据以手机为准是定案口径，但云端协助整理也得有路可走——补丁就是那条路",
                "KnowDo's data answers to the phone first - that ruling stands. But help from the cloud side needs a road in, too; the patch is that road");
        e1520.tryIt = s("更新后打开一次 App、等它同步完；被覆盖回的习惯类型会自己改回来，日记续写也会接回去",
                "After updating, open the app once and let it finish syncing: the habit types that were rolled back will set themselves right, and the diary continuation will be joined back on");
        out.add(e1520);

        Entry e1510 = new Entry("v1.51.0", "2026-11-03");
        e1510.add(new Group(s("新增", "New"),
                s("每天凌晨，作者的 AI 助手会看你昨天的任务和日记，写一小段状态总结，再挑几条对得上的原则和问题，做成晨间卡，放在晨间页写日记的上方",
                  "Every night, the author's AI assistant reads yesterday's tasks and diary, writes a short note on where you stand, and picks a few matching principles and questions - a morning card, set above the diary on the morning page")));
        e1510.why = s("写日记之前，先看一眼昨天的自己站在哪里——知与行，在清晨对一次账",
                "Before the diary, one look at where yesterday's self stood - knowing and doing, reconciled at dawn");
        e1510.tryIt = s("早上打开晨间页，有卡时先读卡、点「读过了」，日记框才会展开；没有卡的日子，日记照常写，不拦",
                "Open the morning page in the morning: when a card is there, read it and tap Read - only then does the diary box open. On days without a card, the diary works exactly as before");
        out.add(e1510);

        Entry e1500 = new Entry("v1.50.0", "2026-11-03");
        e1500.add(new Group(s("新增", "New"),
                s("底部第一个 tab 改名「行」，新增第二个 tab「知」。知页里安放三类东西：思维原则、行为原则、待问的问题",
                  "The first bottom tab is renamed Act, and a new second tab, Know, is added. The Know page keeps three kinds of entries: thinking principles, principles for action, and questions still to ask")));
        e1500.why = s("知行要合一，先把「知」立起来。任务是行，原则与问题是知——两本账，从此都在手边",
                "Knowing and doing belong together, so Know gets its own ledger first. Tasks are the doing; principles and questions are the knowing — both books, now at hand");
        e1500.tryIt = s("点底部「知」，每一类底下点「加一条」就能写；点已有条目可以改、可以删；问题还能记下是问谁的",
                "Tap Know at the bottom: under each kind, tap Add one to write; tap an existing entry to edit or delete it; a question can also record who it is for");
        out.add(e1500);

        Entry e1490 = new Entry("v1.49.0", "2026-11-03");
        e1490.add(new Group(s("新增", "New"),
                s("习惯多了一个新类型「自读」——点开就是要读的全文，读完点「读过了」就记完成；习惯页那一行不显示做了没做，干干净净",
                  "Habits gain a new type, Reading: tap it and the passage opens in full; finish reading and tap Read to log the day. Its row on the Habits page shows no done-or-not state at all — just the name")));
        e1490.why = s("自警文这类东西，要的是每天打开读一遍，不是打卡。把打卡的样子拿掉，只留下读本身",
                "A morning admonition is meant to be opened and read every day, not checked off. The check-in shape is removed; only the reading remains");
        e1490.tryIt = s("习惯页点「每日自读」那一行，全文打开，读完点底部「读过了」。全文可以在编辑习惯里改",
                "On the Habits page tap the Daily Reading row; the passage opens in full — read it, then tap Read at the bottom. The text can be changed in Edit habit");
        out.add(e1490);

        Entry e1480 = new Entry("v1.48.0", "2026-11-03");
        e1480.add(new Group(s("新增", "New"),
                s("「写日记」升级成文字型习惯——在习惯页点它，直接进日记页：写今天的日记、翻看和补改过去的日记，都在一个地方",
                  "Journaling becomes a text habit: tap it on the Habits page and you land on the diary page — write today's entry, read back and amend past ones, all in one place")));
        e1480.why = s("以前打卡和写字是分开的两件事，历史日记也没地方好好看。现在写日记、看日记、补日记是一件事",
                "Checking in and writing used to be two separate things, and past entries had no good place to be read. Now writing, reading and amending the diary are one thing");
        e1480.tryIt = s("习惯页点「写日记」那一行；写了字当天就算完成，没有字数要求。晨间页的日记框照旧保留，两边写的是同一份日记",
                "Tap the Journaling row on the Habits page; any entry written counts the day as done, with no length requirement. The morning page's diary box stays as it was — both places write into the same diary");
        out.add(e1480);

        Entry e1470 = new Entry("v1.47.0", "2026-11-03");
        e1470.add(new Group(s("新增", "New"),
                s("习惯多了一个新类型「每周目标反思」。它有一个每周目标，每天写一句反省；到周日晚上 8 点，如果下周目标还没填，App 会先请你填好才放行",
                  "Habits gain a new kind: Weekly Goal & Reflection. It carries one goal per week and a short reflection each day; come Sunday 8 pm, if next week's goal is still empty, the app asks you to fill it in before letting you through")));
        e1470.why = s("目标如果只躺在清单里，很快就忘了。把每周目标和每天反省绑在一起，每周日晚还有一道门提醒你先想清楚下周往哪走",
                "A goal that only sits in a list is soon forgotten. Tying the weekly goal to a daily reflection — with a Sunday-evening gate that makes you think through where next week is heading — keeps it in front of you");
        e1470.tryIt = s("习惯页添加习惯时选「每周目标反思」，或点开这类习惯进详情页——改本周目标、提前填下周目标、写今天反省、翻看过去各周都在那里",
                "When adding a habit on the Habits page choose Weekly Goal & Reflection, or tap one to open its page — change this week's goal, set next week's early, write today's reflection and browse past weeks, all in one place");
        out.add(e1470);

        Entry e1460 = new Entry("v1.46.0", "2026-11-01");
        e1460.add(new Group(s("改进", "Improved"),
                s("今天安排页的时间账改成从现在算起：已安排只算现在还没到的日程（正进行中的只算剩下的那截），还剩可用按从现在到今天结束的可用时间算；顶部时段条上现在之前的时间变灰，现在的位置有一条朱砂红线",
                  "The Today Plan page now reckons from this moment: Arranged counts only the schedule still ahead (a block in progress counts only its remaining part), and Time left is reckoned against the usable time from now to the end of today. On the period bar at the top, everything before now is greyed and a vermilion line marks the present moment")));
        e1460.why = s("之前按一整天算，半天过去了还显示一整天的账，数字和实际剩下的时间对不上",
                "It used to reckon by the whole day, so with half the day gone the page still showed a full day's accounts, and the numbers did not match the time actually left");
        e1460.tryIt = s("下午打开「安排今日任务」，看已安排是不是只算后面的日程、还剩可用是不是变小了；时段条上现在之前是灰的，红线对着当前时间",
                "Open Plan Today's Tasks in the afternoon and check: Arranged should count only the schedule still ahead and Time left should be smaller; on the period bar, everything before now is grey and the red line sits at the current time");
        out.add(e1460);

        Entry e1450 = new Entry("v1.45.0", "2026-11-01");
        e1450.add(new Group(s("新增", "New"),
                s("新版一发布，知行会立刻自己发现、自己下载，下好后弹窗提醒你安装；打开知行、切回知行时也会顺手查一次更新，不再只靠手动检查",
                  "The moment a new version is published, KnowDo now notices on its own, downloads it by itself, and pops up a prompt asking you to install; opening KnowDo or coming back to it also checks for updates in passing, instead of relying on a manual check")));
        e1450.why = s("你希望我一发新版你手机马上就能更新；之前自动检查最长要等大半天，或者得手动去点检查，还可能被旧包卡住",
                "You want your phone to update the moment I publish a new version. Before, the automatic check could take the better part of a day to notice, or you had to check manually — and a stale package could block even that");
        e1450.tryIt = s("这版装上后不用专门试；等下一版发布时留意，知行开着的情况下应该很快自己下好并弹窗喊你安装",
                "Nothing to try once this version is on; watch for the next release — with KnowDo open it should download itself within moments and pop up asking you to install");
        out.add(e1450);

        Entry e1440 = new Entry("v1.44.0", "2026-11-01");
        e1440.add(new Group(s("修复", "Fixed"),
                s("修复软件更新被旧待安装包卡住的问题：如果手机里存着一个不比当前版本新的待安装包（比如还在旧版时下好的、后来又从别处装上了同版），它现在会自动作废——设置页不再拿它冒充新版让你反复安装，点「检查更新」会重新联网查真正的最新版。比当前版本新的待安装包不受影响，下好照样一点就装",
                  "Fixed the updater getting stuck behind a stale downloaded package: if the phone holds a pending install that is no newer than the version already running (downloaded while on an older release, say, then overtaken by installing the same version from elsewhere), it now voids itself — Settings stops presenting it as a new version to install over and over, and tapping Check for updates goes back to the server for the real latest. A pending package that is genuinely newer is unaffected: once downloaded it still installs in one tap")));
        e1440.why = s("你在 1.38.0 上点检查更新时，它只让你反复安装同一个 1.38.0——旧待安装记录一直没被清掉，又正好挡在真正的检查入口前面",
                "On 1.38.0, tapping Check for updates only offered to install 1.38.0 itself, over and over: the stale pending record was never cleared, and it sat exactly where the real check needed to go");
        e1440.tryIt = s("已经装到 1.43.0 的不用专门试；还停在旧版的，升到这一版以后去设置 → 软件更新点「检查更新」，应该能正常查到并下载后面的新版，不再被旧包拦住",
                "If you are already on 1.43.0 there is nothing special to try; if you are still on an older version, after updating to this one go to Settings, Software update, and tap Check for updates — it should find and download later versions normally, no longer blocked by the old package");
        out.add(e1440);

        Entry e1430 = new Entry("v1.43.0", "2026-11-01");
        e1430.add(new Group(s("新增", "New"),
                s("任务编辑页的固定底栏里多了「删除任务」：编辑一个已有任务时，三钮下方有一行朱砂红小字，点它先弹确认，确认后就删——和在任务列表里长按删除是同一个确认弹窗、同一种删除方式。删的正是正在计时或暂停中的那个任务时，会先把表停好再删。新建任务时没有这一行",
                  "The task editor's fixed footer gains a Delete task row: when editing an existing task, a quiet vermilion line under the three buttons asks for the same confirmation as the task list's long-press delete, then deletes the same way. If the task being deleted is the one currently timing or paused, the timer is stopped properly first. The row never appears when creating a task")));
        e1430.why = s("你之前删任务得退回任务列表去长按，编辑到一半想删很绕——在编辑页里就该能当场删掉",
                "Deleting used to mean backing out to the task list and long-pressing; midway through editing a task you no longer want, that detour made no sense — the editor itself should offer the delete");
        e1430.tryIt = s("打开任意一个已有任务的编辑页，点底部的「删除任务」，看是不是先弹确认；再新建一个任务，看底部没有这一行",
                "Open any existing task's editor and tap Delete task at the bottom — a confirmation should appear first; then create a new task and check the row is not there");
        out.add(e1430);

        Entry e1420 = new Entry("v1.42.0", "2026-11-01");
        e1420.add(new Group(s("新增", "New"),
                s("今天安排页的任务卡上多了「完成」和「删除」两个操作，口径和任务列表里一模一样；卡里还能直接看到这个任务的子任务——名字和分钟数，最多列三个，其余用省略号收尾",
                  "Task cards on the today-plan page gain Done and Delete actions, working exactly as they do in the task list, and a card now shows the task's subtasks inline — name and minutes, up to three, the rest tailed off with an ellipsis")));
        e1420.why = s("你在安排今天的时候，经常顺手就想把某个任务办掉或删掉，之前还得退回任务列表里翻找——在安排页里就该能当场处理",
                "While planning today you often want to finish or drop a task on the spot, and having to go back and hunt it down in the task list broke the flow — the planning page should handle it right there");
        e1420.tryIt = s("进今天安排页，在任意一张任务卡上点「完成」或「删除」；再找一张带子任务的卡，看看它卡里列出的子任务",
                "Open the today-plan page and tap Done or Delete on any task card; then find a card with subtasks and see them listed inside it");
        out.add(e1420);

        Entry e1410 = new Entry("v1.41.0", "2026-11-01");
        e1410.add(new Group(s("新增", "New"),
                s("今天安排页底部多了「开始今天」按钮：排没排满都由你说了算，点它就结束安排、直接进入今天，不会被「还没排完」拦住",
                  "The today-plan page gains a Start today button at the bottom: whether the plan is full or not is entirely your call — tapping it ends planning and takes you straight into today, with nothing blocking the way out")));
        e1410.why = s("你说安排页缺一个明确的收尾出口：排满没排满该由你决定，之前只能靠返回键离开",
                "The planning page had no clear way to be done: whether the plan was full was for you to decide, yet the only way out was the back key");
        e1410.tryIt = s("进今天安排页，什么都不排，直接点底部「开始今天」，应当立刻回到今天的日程页",
                "Open the today-plan page, plan nothing at all, and tap Start today at the bottom: you should land right back on today's schedule page");
        out.add(e1410);

        Entry e1400 = new Entry("v1.40.0", "2026-11-01");
        e1400.add(new Group(s("改进", "Improved"),
                s("所有要打字的页面都改了：键盘一弹出来，页面会自动变矮，保存、开始、取消这些按钮停在键盘上方、钉在页面最底部，页面再长也不会被滚走——任务编辑、晨间页、过半与结束检查页、下一个任务页、聊聊都包括",
                  "Every page with typing is reworked: when the keyboard opens, the page shrinks to fit, and Save / Start / Cancel hold right above the keyboard, pinned to the bottom of the page — task editing, the morning page, the halfway and end check pages, the next-task page and chat are all covered. Long content scrolls above the buttons; the buttons never scroll away")));
        e1400.why = s("以前编辑任务时，按钮埋在内容最底下，键盘一弹出来就盖住，内容一长还得先收起键盘、滚到底才能保存",
                "Editing a task used to bury the buttons at the very bottom of the content: the keyboard covered them, and with long content you had to dismiss the keyboard and scroll all the way down before you could save");
        e1400.tryIt = s("打开任意一个任务的编辑页，点任务名打几个字，看底部三个按钮稳稳停在键盘上方；再把内容拉到最长，确认按钮不随内容滚动",
                "Open any task's edit page and tap the task name to type: the three buttons at the bottom should hold steady above the keyboard. Then make the content as long as it gets and check the buttons do not scroll with it");
        out.add(e1400);

        Entry e1390 = new Entry("v1.39.0", "2026-11-01");
        e1390.add(new Group(s("新增", "New"),
                s("过半检查点了「做不完」后的写办法页，多了一排加时间：+1、+3、+5、+10 分钟、+50% 和自定义，点一下当场加进当前计时，可以连着点、越加越多，加了多少页面上有一行累计显示；不加也能照常写完办法继续",
                  "The method page after \u201cNot enough time\u201d at the halfway check gains an add-time row: +1, +3, +5, +10 minutes, +50% and a custom amount. Each tap adds straight into the running timer and taps accumulate; a line on the page keeps the running total of what you added. Adding nothing and just writing your method works exactly as before")));
        e1390.why = s("你发现写办法时其实还差一点时间，光写办法、不加时间不够用；以前在这一页没法加时，只能等任务到点后的结束页才能加",
                "Writing the method is only half of it — you were still short on time, and this page offered no way to add any. Extending used to have to wait for the end page");
        e1390.tryIt = s("任意任务计时到过半页，点「做不完」，在写办法页点 +5 分钟：计时应当当场多出 5 分钟、页面出现「已加 5 分钟」；再试试 +50% 与自定义输入",
                "Let any task reach its halfway page and tap \u201cNot enough time\u201d. On the method page tap +5 min: the timer should gain 5 minutes on the spot and the page should show the added total. Then try +50% and a custom amount");
        out.add(e1390);

        Entry e1382 = new Entry("v1.38.2", "2026-11-01");
        e1382.add(new Group(s("修复", "Fixed"),
                s("今天安排页填「今天可支配」时多了个上限：填得比今天墙上剩下的时间（从现在到今天结束、再扣掉锁时段）还多，会自动按墙上剩下的时间算，并弹一句提示；填在上限以内和以前完全一样、不弹提示，被卡住的值回显也只留两位小数",
                  "The Today Plan page's free-time field gains a ceiling: enter more than the wall-clock time actually left today (from now until the day ends, minus locked segments) and it is automatically counted as the time left, with a short hint saying so. Anything within the limit behaves exactly as before with no hint, and a capped value echoes back with at most two decimals")));
        e1382.why = s("你发现填 13 小时、墙上其实只剩 12 小时 23 分时，页面一边显示「还剩 37 分钟」、一边又说有任务排不下，自相矛盾；可支配本来就不该比墙上有的时间多，所以从填进去的那一刻就把上限卡住",
                "You spotted the contradiction: free time set to 13 hours while only 12 hours 23 minutes remained on the wall made the page claim 37 minutes left while also saying tasks could not fit. Free time can never exceed the time the wall actually holds, so the limit is enforced at the moment the number is entered");
        e1382.tryIt = s("打开今天安排页，把「今天可支配」改成一个明显偏大的数（比如 20）：它应该自动变小（按今天剩下的时间算），并弹出「可支配时间不可以多于时间墙上可用的时间」；再填一个小数字（比如 2），应该没有任何提示",
                "Open the Today Plan page and set free time to something plainly too big (20, say): it should shrink on its own to the time left today and show the hint that free time cannot exceed the time left on today's wall. Then enter a small number (2, say) - no hint should appear");
        out.add(e1382);

        Entry e1381 = new Entry("v1.38.1", "2026-11-01");
        e1381.add(new Group(s("修复", "Fixed"),
                s("修好晨间页「暂停一会儿」点了没反应的问题：现在点它，选分钟的面板会直接在按钮下面展开，并自动滚到你眼前，按钮也会变色、箭头翻上来示意已经展开；不管这一页填没填、填了多少，这个按钮都随时能用",
                  "Fixed the morning page's \"Pause for a bit\" button appearing dead: tapping it now opens the minutes panel directly beneath the button and scrolls it into view, and the button changes colour with its arrow flipped up to show it is open. Whatever you have or haven't filled in on this page, the button always works")));
        e1381.why = s("之前选分钟的面板展开在说明文字的下面、屏幕之外，点了画面一点变化都没有，看着就像按钮坏了；你说这个按钮无论填没填东西都必须能用，就按这个标准修",
                "The minutes panel used to open below the note text, off the bottom of the screen, so tapping changed nothing on screen and the button looked broken. You said this button must work no matter what has been filled in, so that is the standard it was fixed to");
        e1381.tryIt = s("早上晨间页弹出时，什么都不填先点「暂停一会儿」，看面板是不是在按钮下面展开、选 5 分钟后页面是不是退下并到点自己回来；再试填一半日记、键盘还开着时点它，应该同样能展开，键盘会先收起",
                "When the morning page pops up, tap \"Pause for a bit\" before filling in anything: the panel should open right under the button, and choosing 5 minutes should step the page aside and bring it back on time. Then try it with a half-written diary and the keyboard still open - it should open just the same, with the keyboard folding away first");
        out.add(e1381);

        Entry e1380 = new Entry("v1.38.0", "2026-10-29");
        e1380.add(new Group(s("新增", "New"),
                s("日程页任务列表上方多了一个「安排今日任务」按钮，点它随时进「今天安排」页，不用再等晨间门；在里面排好今天之后返回，过一会儿想改，再点一次还能进去",
                  "The schedule page gains a permanent \"Plan today's tasks\" button above the task table: tap it any time to open the Plan Today page, no need to wait for the morning gate. Plan your day, go back, and tap it again later to adjust - it can be opened as often as you like")));
        e1380.why = s("这页原来只有晨间门填完那一个入口，错过就一整天进不去；你说记得任务列表里该有个这样的按钮，那就把它补在这里",
                "That page used to have exactly one entrance - finishing the morning gate - and missing it meant no way back in for the whole day. You remembered there should be a button like this in the task list, so now there is");
        e1380.tryIt = s("打开知行，在日程页点任务列表上方的「安排今日任务」，看是不是直接进了今天安排页；在里面排好后返回，再点一次还能再进",
                "Open KnowDo, and on the schedule page tap \"Plan today's tasks\" above the task table - it should take you straight to the Plan Today page. Go back after planning, then tap it once more to re-enter");
        out.add(e1380);

        Entry e1370 = new Entry("v1.37.0", "2026-10-29");
        e1370.add(new Group(s("改进", "Improved"),
                s("开机后的头 3 分钟，知行先不启动屏幕守卫的采样，几个本地 AI 模型（算屏幕内容与任务搭不搭的向量模型、看图的黄图模型、认字的 OCR）也就不会在这段时间里第一次加载；计时、提醒、悬浮窗、晨间门全都照常。开机时自动拉起的云端同步改到开机 2 分钟后再启动；你自己打开知行时，同步照常立刻走",
                  "For the first 3 minutes after the phone boots, KnowDo no longer starts screen-guard sampling, so the local AI models (the embedding model that matches screen content to your task, the image model, and OCR) are not first-loaded in that window either; timers, reminders, the floating window and the morning gate all work as usual. The cloud sync that used to start by itself at boot now starts 2 minutes after boot; opening KnowDo yourself still syncs right away")));
        e1370.why = s("刚开机的几分钟手机本来就忙，你又正好在这个时点打开知行输入今天的任务，而守卫模型偏偏在开始第一个任务时才第一次加载，几件事撞在一起，手机和键盘就一起卡住。把 AI 的重活往后挪几分钟，先让你顺畅把任务输完；代价是开机后 3 分钟内开始的任务，这一段时间没有屏幕守卫采样，3 分钟一到守卫自己开始",
                "In the first minutes after boot the phone is busy anyway, and that is exactly when you open KnowDo to type in today's tasks - while the guard models happened to first-load the moment the first task started. Everything collided, and the phone and keyboard stalled together. Moving the AI heavy lifting a few minutes later lets you finish entering tasks smoothly first; the cost is that a task started within 3 minutes of boot has no screen-guard sampling for that stretch - the guard starts by itself once the 3 minutes are up");
        e1370.tryIt = s("重启手机，开机后立刻打开知行，新建一个任务并开始计时，输入应该顺畅不卡；运行日志里先会有一行「刚开机，先等一会儿再开始采样」，3 分钟后守卫开始采样，日志里出现「开始随机采样」一行。再重启一次、开机后先别打开知行：等两三分钟，同步的常驻通知会自己出现",
                "Restart the phone and, right after boot, open KnowDo, create a task and start the timer - typing should stay smooth. The run log first shows a line saying the guard waits because the phone just booted; after 3 minutes sampling starts and the usual sampling line appears. On another restart, do not open KnowDo at all: after two to three minutes the sync notification shows up by itself");
        out.add(e1370);

        Entry e1360 = new Entry("v1.36.0", "2026-10-29");
        e1360.add(new Group(s("新增", "New"),
                s("新增「今天安排」页：晨间门里把日记和习惯都填完、点「开启今天」之后，会先进这一页。页顶吸着一条时间账：还剩多少＝你填的可支配时间减去已经安排掉的，安排超了「还剩」会变红。每张任务小卡写着名字、已用时间、还剩时间（点一下能改）、截止的两个快捷钮「改到今天」「取消截止日期」、重要性四档当场选，展开能改全部字段。上面有一条从今早起床到明早起床的时段横条：睡眠段长期保存；白天被占住的时段可以拖出来命名为「锁时」，也能展开成数字时间条精细改；不想逐段设就直接填「今天可支配 X 小时」。自动排、快速重排、AI 重排和聊天排程都会躲开睡眠和锁时，日历页上也会把这两段画出来",
                  "A new Today Plan page: after you finish the diary and habits in the morning gate and tap Start today, you land here first. A time account sticks to the top: what is left equals the disposable time you entered minus what is already planned, and it turns red when you over-plan. Each compact task card shows the name, time used, and time left (tap to edit), two deadline shortcuts - Move to today and Clear deadline - and the four importance levels to pick on the spot; expand a card to edit every field. Above is a strip running from this morning's wake-up to tomorrow morning's: the sleep segment is saved long-term; daytime segments that are taken can be dragged out and named as locked time, or expanded into a numeric strip for fine editing; or skip the segments and just enter today's disposable hours directly. Local auto-scheduling, quick reschedule, AI reschedule and chat schedule cards all avoid sleep and locked segments, and the calendar page draws both segments too")));
        e1360.why = s("一天真正能安排的时间就那么多，早上花几分钟先排定、总量不超过可支配时间，比排满了再一路欠债强；睡眠和锁时先划出去，剩下的安排才是真的能做到的",
                "There is only so much time in a day that can really be planned. Spending a few minutes in the morning fixing the plan - with the total kept inside your disposable time - beats overfilling the day and carrying the debt forward. Sleep and locked time are fenced off first, so what remains planned is what can actually be done");
        e1360.tryIt = s("明早走完晨间门，看它是不是进了「今天安排」页；拖出一段锁时，给它起个名，再随便排一下，看任务是不是自动躲开那一段",
                "Tomorrow morning, finish the morning gate and check you land on the Today Plan page; drag out a locked segment, give it a name, then plan a little and see whether tasks automatically avoid that segment");
        out.add(e1360);

        Entry e1350 = new Entry("v1.35.0", "2026-10-29");
        e1350.add(new Group(s("新增", "New"),
                s("新建/编辑任务页底部变成三个按钮——取消（左）／立刻开始（中）／保存（右）。「立刻开始」＝先按和保存完全一样的规则存好（名字为空照样拦），然后当场开始给这个任务计时：在主页新建、主页编辑、日历里新建、日历里改任务，四个入口都一样；如果这个任务正在计时，不会重启它；正暂停着，就接着剩下的时间继续；是别的任务在计时，就直接切换过来",
                  "The bottom of the create/edit task sheet is now three buttons: Cancel (left) / Start now (centre) / Save (right). Start now first saves under exactly the same rules as Save (an empty name is still blocked), then immediately starts timing that task. It works the same from all four entry points: create or edit on the home page, create or edit in the calendar. If that task is already timing, it is not restarted; if it is paused, it resumes with its remaining time; if another task is timing, it switches straight over")));
        e1350.why = s("想做一件事的时候就该立刻开始——先保存、再回列表找任务、再点开始，中间这几步最容易拖掉、岔掉",
                "When you want to do something, you should start it right away: save, go back to the list, find the task, tap start - those steps in between are exactly where things get delayed and dropped");
        e1350.tryIt = s("打开任意一个任务的编辑页，点「立刻开始」，看它是不是保存好并马上进入计时",
                "Open any task edit sheet, tap Start now, and check it saves and goes straight into timing");
        out.add(e1350);

        Entry e1340 = new Entry("v1.34.0", "2026-10-29");
        e1340.add(new Group(s("新增", "New"),
                s("设置 → 守卫新增「黄图问责」块（测试版），开关默认关。打开后，守卫确认截到黄图时，会把图只在内存里打码模糊（原图从不保存、不上传），经邮件发给预先填好的问责朋友邮箱（可多个；只有作者账号会预填母亲的两个有效地址，别人不预填），标题和正文模板都能改。防滥发：同类事件 10 分钟内合并成一封，每天上限 5 封。设置里有醒目说明：「现在是测试版，这些邮件借用作者郭天宇的邮箱通道发送，发送内容作者可以看得见」。发信还差作者补最后一把 Resend Key（用户决定后补），补好并验通投递之前，邮件不会真正发出——本版先把 App 侧全部环节备好",
                  "Settings \u2192 Guards gains an NSFW accountability block (beta), off by default. When it is on and the guard confirms an explicit image, the frame is blurred in memory only (the original is never saved or uploaded) and emailed to the accountability contacts filled in beforehand (several allowed; only the author's own account is prefilled, with his mother's two valid addresses). The subject and body templates are editable. Anti-abuse: events of the same kind within 10 minutes merge into one mail, capped at 5 a day. A prominent note in Settings says: this is a beta, these mails are sent through author Guo Tianyu's own mail channel, and the author can see what is sent. One piece is still missing: the author has yet to add the final Resend key (his call, coming later) \u2014 until it is added and delivery is verified, no mail will actually be sent; this release readies everything on the app side first")));
        e1340.why = s("有些底线光靠自己提醒自己不够，需要一位真实的人知情在场——问责是为了让分神有真实代价，帮自己守住自己定的规矩",
                "Some lines cannot be held by reminding yourself alone; a real person needs to know and be present. Accountability gives distraction a real cost, and helps you keep the rule you set for yourself");
        e1340.tryIt = s("打开设置 → 守卫，找到「黄图问责」块，看一遍说明、核对收件人邮箱对不对（开关先不开也可以）。真正的测试邮件要等发信配置补完后才发，而且第一次只发作者本人邮箱",
                "Open Settings \u2192 Guards and find the NSFW accountability block: read the note and check the recipient addresses (leaving the switch off for now is fine). A real test mail goes out only once the sending setup is completed, and the first test goes to the author's own mailbox only");
        out.add(e1340);

        Entry e1330 = new Entry("v1.33.0", "2026-10-28");
        e1330.add(new Group(s("改进", "Improved"),
                s("守卫问询页变简洁了：去掉了「现在的状态」六个选项和「分析错误」按钮，问询只剩一个问题——「刚才在做该做的事吗」；给任务加时也不再先问状态，点加时直接选时长就生效。和「分析错误」连着的那套申诉机制（页内的规则收集、设置里的「守卫规则」页）也一并撤了；以前申诉过的记录还留着，分神计数照旧认它，已经记下的状态自检历史也不删",
                  "The guard inquiry page is leaner: the six \u201ccurrent state\u201d chips and the \u201cAnalysis error\u201d button are gone, and the inquiry is back to its one question \u2014 \u201cWere you doing what you should?\u201d Extending a task no longer asks your state first either: tap extend, pick a duration, done. The appeal machinery that hung off the removed button (the in-page rule box, the Guard rules page in Settings) is withdrawn with it; appeals already recorded stay on record and still count in the drift tally, and the self-check history is kept")));
        e1330.why = s("问询越短越愿意认真答，加时是常用动作不该多绕一步",
                "The shorter the inquiry, the more seriously it gets answered \u2014 and extending a task is an everyday action that should not take a detour");
        e1330.tryIt = s("下次守卫问询弹出时，看它是不是只剩一个问题；任务结束页点一次加时，看是不是直接选时长就加上了",
                "Next time the guard inquiry pops up, check it asks just the one question; on a task's end page, tap extend once and see the duration picker come straight up");
        out.add(e1330);

        Entry e1320 = new Entry("v1.32.0", "2026-10-28");
        e1320.add(new Group(s("新增", "New"),
                s("知行页的指数圆环可以点了：点开是指数详情页，能看一周、一月、一年的指数和三项构成（习惯、任务、分神）的变化——大数字、构成表和趋势都在一页。详情页的数字全部由习惯记录、任务记录和守卫事件这些原始记录当场回算；某个时段没有记录就写「数据不足」，不拿别的值填",
                  "The index ring on the KnowDo tab is tappable now: it opens an index detail page showing the index and its three components (habits, tasks, distraction) over the past week, month and year \u2014 the big number, the breakdown table and the trend on one page. Every figure is recomputed on the spot from the raw records (habit logs, task records, guard events); a stretch with no records reads \u201cnot enough data\u201d instead of being filled in")));
        e1320.why = s("只看今天的指数看不出自己是在变好还是变差，拉长到一周、一月、一年才看得清",
                "Today's index alone cannot tell you whether you are getting better or worse. Stretch the window to a week, a month, a year, and the direction becomes visible");
        e1320.tryIt = s("打开知行页，点指数圆环进详情页，切换一周／一月／一年，看表格和趋势怎么变",
                "Open the KnowDo tab, tap the index ring to enter the detail page, and switch between week, month and year to watch the table and the trend change");
        out.add(e1320);

        Entry e1310 = new Entry("v1.31.0", "2026-10-28");
        e1310.add(new Group(s("新增", "New"),
                s("新习惯类型「每日提醒」：新建习惯时选它，写一段提醒自己的话，长短不限。每天早上，晨间门里会完整显示这段话，读完点「读过了」就算当天完成；在晨间门的昨日必答里，昨天的这段话也会连全文一起问你读过没有。习惯页的卡片显示全文，精简模式只露一行。它和其它习惯一样计入习惯达成率",
                  "A new habit type, \u201cDaily Reminder\u201d: pick it when creating a habit and write a passage to yourself, any length. Every morning the morning gate shows the passage in full; read it and tap \u201cRead\u201d and today counts as done. In the gate's must-answer section for yesterday, yesterday's passage appears in full too, asking whether you read it. Habit cards show the full text (one truncated line in Compact mode), and the type counts toward the habit rate like the others")));
        e1310.why = s("有些话需要每天看一遍提醒自己，光记次数记不住话本身——让这段话每天早上自己出现在你面前",
                "Some words need to be read again every day, not counted. A reminder is only kept if the passage itself shows up in front of you each morning; counting alone cannot carry the words");
        e1310.tryIt = s("新建一个习惯，类型选「每日提醒」，填一句话保存；明早打开晨间门，昨日必答之后会有这段话的全文，读完点「读过了」",
                "Create a habit, choose the \u201cDaily Reminder\u201d type, write one sentence and save. Tomorrow morning the morning gate will show the passage in full — read it and tap \u201cRead\u201d");
        out.add(e1310);

        Entry e1300 = new Entry("v1.30.0", "2026-10-28");
        e1300.add(new Group(s("新增", "New"),
                s("习惯页顶部多了「展开｜精简」切换：精简模式下每个习惯只占一行——名字、本周七个小圆点（点哪天就补记哪天）、右边直接写本周还差多少（达标写「已达标」；至多型超了写「超了 N 次」，不会出现负数）。点一行进编辑；习惯多了，一页就能看完，第一眼就知道哪个还没达标",
                  "The Habits tab gains a Full | Compact switch at the top. In Compact mode each habit takes one line: its name, this week's seven dots (tap a dot to back-fill that day), and on the right how far it still is from this week's goal (\u201cDone\u201d when reached; an at-most habit over its limit says \u201cN over\u201d, never a negative \u201cleft\u201d). Tap a row to edit it. With many habits, one page shows them all, and the one falling behind is obvious at a glance")));
        e1300.why = s("习惯攒多了，展开模式一页看不完、还得往下翻；精简模式一眼扫过去，哪个没达标清清楚楚",
                "As habits pile up, the full cards no longer fit on one page. Compact mode lets one glance show which habit is falling behind");
        e1300.tryIt = s("打开习惯页，点顶部的「精简」：看每个习惯是不是变成一行、右边写着本周还差多少；点某个小圆点能补记那一天",
                "Open the Habits tab and tap \u201cCompact\u201d at the top: each habit should become one line with this week's remaining distance on the right, and tapping a dot back-fills that day");
        out.add(e1300);

        Entry e1290 = new Entry("v1.29.0", "2026-10-28");
        e1290.add(new Group(s("新增", "New"),
                s("设置里多了「习惯显示口径」：一个设置管全部习惯卡的主行数字——按今天、本周、上周或本月显示，不用再一个个习惯去切。默认本周，和你现在看到的一样；累积型习惯的累计总数不受这个设置影响，知行指数的算法也没有动",
                  "Settings gains a \u201cHabit display window\u201d: one setting governs the main count on every habit card — reckoned by today, this week, last week or this month — instead of switching each habit one by one. The default is this week, just what you see now. Cumulative totals are not affected by it, and the Zhixing index math is untouched")));
        e1290.why = s("你定的：显示口径要一个总设置管全部，省得每个习惯单独设、还容易设得各不一样",
                "Your call: one global setting for the display window, instead of a per-habit switch that is fiddly and easy to leave inconsistent");
        e1290.tryIt = s("打开设置 → 习惯显示口径，切到「本月」，回习惯页看卡片主行的数字是不是按本月算了；再切回「本周」",
                "Open Settings \u2192 Habit display window, switch to \u201cThis month\u201d, go back to the Habits tab and see the cards' main counts reckoned by the month; then switch back to \u201cThis week\u201d");
        out.add(e1290);

        Entry e1280 = new Entry("v1.28.0", "2026-10-28");
        e1280.add(new Group(s("新增", "New"),
                s("累积型习惯可以填「起始数字」了：编辑一个累积型习惯（比如「做善事」），填上起始数字，卡片上的累计总数、总量目标的进度和「还差多少」，都从这个数开始往上加；新建累积型习惯时也能直接填。不填就是 0，和以前一样",
                  "Cumulative habits can take a starting number now: edit a cumulative habit (good deeds, say), enter a starting number, and the card's running total, its progress toward the total goal and its \u201chow far left\u201d all count upward from that number. You can set it when creating the habit too; leave it empty and it is 0, exactly as before")));
        e1280.why = s("像做善事这种、你用知行之前就已经在做的事，累计不该从零开始数——从你真正已经做到的那个数接着数，才对得起已经做过的那些",
                "Some things — good deeds above all — were already being done before KnowDo existed, and their count must not restart at zero. It continues from the number you had actually reached, so what you already did still counts");
        e1280.tryIt = s("打开习惯页，编辑你的累积型习惯（比如做善事），把起始数字填成之前已经累计的件数，保存后看卡片上的总数是不是从这个数起算",
                "Open the Habits tab, edit a cumulative habit (good deeds, say), set its starting number to what you had already accumulated, save, and check the card's total now counts from that number");
        out.add(e1280);

        Entry e1271 = new Entry("v1.27.1", "2026-10-27");
        e1271.add(new Group(s("修复", "Fixed"),
                s("快速重排不再把没做完的事留在过去：以前只要一块计划的时间已经过了，重排就把它锁死不动——任务明明没做完，日程却卡在过去，没法用。现在改成：没做完的任务，计划时间过了就把那块收走，从现在这一刻起重新往后排；真正不动的只剩做完的、正在计时的、你亲手拖定的，还有已经实际做过的部分（它在实际栏里，不受影响）",
                  "Quick Re-plan no longer strands unfinished work in the past: before, once a planned block's time had passed, re-planning locked it in place — the task was unfinished, yet the schedule stayed stuck in the past and unusable. Now an unfinished task whose planned time has passed gets its block taken away and is re-planned from this moment on. The only things that stay put are finished tasks, the one currently timing, blocks you dragged into place yourself, and work actually done (that lives in the actual column and is untouched)")));
        e1271.why = s("你说得很明确：没做完的事不能留在过去，重排就是从现在重新安排。快速重排是你每天顺手用的，没有它你今天的任务都不方便用，所以这一版只修这一件",
                "You put it plainly: unfinished work must not be left in the past — a re-plan means arranging from now. Quick Re-plan is something you reach for every day, so this release fixes only this");
        e1271.tryIt = s("找一件没做完、计划时间已经过了的任务，打开日历页点「快速重排」：它应该被挪到现在之后重新排好，过去不再留它的块；排完仍可点右上角「撤销重排」回到重排前",
                "Find an unfinished task whose planned time has already passed and tap \u201cQuick Re-plan\u201d on the calendar page: it should land after the current moment, with no block of it left in the past. \u201cUndo re-plan\u201d at the top right still restores the previous layout");
        out.add(e1271);

        Entry e1270 = new Entry("v1.27.0", "2026-10-27");
        e1270.add(new Group(s("新增", "New"),
                s("日历页多了「快速重排」：在「AI 重排」旁边，点一下就按固定排序立刻把今天剩下的日程重排好，不用等 AI、不用确认。顺序是——先排「15 分钟以内、重要以上、今天到期」的小事（短的先），然后其余必做、今天到期的重要、今天到期的次要、未到期的重要、未到期的次要；休息类永不自动排。正在计时的、已经过去的、做完的、你亲手拖定的块都不动，从现在起按每件 ×1.5 占时填到 22:30，排不下的会当场点名。排完日历右上角有「撤销重排」，点一下回到重排前（只留最近一次）",
                  "The calendar page gains \u201cQuick Re-plan\u201d next to \u201cAI Re-plan\u201d: one tap re-plans the rest of today instantly with a fixed order — no AI, no confirmation. Order: first the short ones (15 minutes or less, Important or above, due today; shortest first), then the remaining Must-dos, Important due today, Secondary due today, not-yet-due Important, not-yet-due Secondary; Leisure is never auto-scheduled. The running task, past blocks, done blocks and blocks you dragged into place stay untouched; everything else packs from now at \u00d71.5 per task until 22:30, and anything that doesn't fit is named on the spot. An \u201cUndo re-plan\u201d link appears at the top right of the calendar afterwards (keeps the latest one)"),
                s("知行能自己更新了：以后有新版，它会自己查、自己下载（每天至多自动查一次），下好后弹一个普通对话框，你点「安装」就装上，不用再去网盘手动找安装包。设置里新增「软件更新」一组：自动检查开关、下载网络（仅 Wi-Fi／Wi-Fi 与流量，你的账号已设为流量也可下）、当前版本、检查更新按钮；下载进度只在设置页那一行小字里显示，不打扰",
                  "KnowDo can update itself now: when a new version is out it checks and downloads it on its own (auto-check at most once a day), then shows a plain dialog — you tap Install and it's on. No more hunting for the installer in a cloud drive. Settings gains an \u201cApp Updates\u201d group: an auto-check switch, the download network (Wi-Fi only / Wi-Fi & mobile data — your account is set to allow mobile data), the current version and a Check for updates button. Download progress shows only as small print on that settings row"),
                s("底部「聊聊」页签有了未读角标：有你没看的 AI 消息（尤其是分神时它主动发来陪你的那条）时，页签右上角挂一个朱砂红小角标写着条数；点进聊聊页签看过就清零",
                  "The Chat tab at the bottom gains an unread badge: when there are AI messages you haven't seen — above all the proactive one it sends when you get distracted — a small vermilion badge with the count sits on the tab. Opening the Chat tab clears it")));
        e1270.add(new Group(s("改进", "Improved"),
                s("「AI 重排」看得更全了：它现在会拿到和快速重排同一套排序规则当基准，还能看到你今天 Google 日历上的约会（标题和时间，只作参考、排任务不用绕开）和你今天已经做过的事（做完的任务、实际几点到几点做了什么），排出来的提案更贴合你今天真实的样子。老规矩不变：它只是提案，点「应用」才生效；任何任务的优先级它都不许改",
                  "\u201cAI Re-plan\u201d sees more now: it receives the same ordering rules Quick Re-plan uses as its baseline, plus today's Google Calendar events (titles and times, reference only — scheduling doesn't have to avoid them) and what you have already done today (finished tasks and the actual times you worked). Its proposals fit your real day better. The old rules stand: it's only a proposal until you tap Apply, and it may never change any task's priority")));
        e1270.why = s("两件事你都催过：一是等 AI 慢慢排太慢，想要一个点一下就排好的纯算法版（但 AI 版别删，两个都要）；二是每次更新都要去网盘手动下安装包太折腾。快速重排和自动排程、AI 重排共用同一套排序规则，一个口径排到底；更新改走发布分片通道，软件自己下、逐片校验、拼好再验整件，下错了装不上。聊聊红点是顺手补的：分神陪聊的消息发出来你经常没看见，等想起来已经凉了",
                "Two things you asked for: AI re-planning is slow, so you wanted a one-tap algorithmic version (and to keep the AI one — both); and fetching every update from a cloud drive by hand is a chore. Quick Re-plan, auto-scheduling and AI Re-plan now share one set of ordering rules — a single standard end to end. Updates travel as verified shards the app downloads itself, checks piece by piece and again as a whole, so a bad download can't install. The chat badge is a small extra: the proactive distraction messages were easy to miss until they went stale");
        e1270.tryIt = s("四处可以试：1）日历页点「快速重排」——日程应该立刻重排，右上角出现「撤销重排」，点它能回到重排前。2）设置 → 软件更新：看当前版本行，点「检查更新」试一次；下载网络可以改成「仅 Wi-Fi」。3）下次有新版时留意：它会自己下好并弹「新版已下载好」，点安装即可（第一次会引导你给一次「安装未知应用」授权）。4）等一条你没看的 AI 消息（比如分神陪聊），看底部「聊聊」页签有没有红色角标，点进去应该清零",
                "Try four things: 1) Tap \u201cQuick Re-plan\u201d on the calendar page — the day re-plans instantly and \u201cUndo re-plan\u201d appears at the top right; tapping it restores the previous layout. 2) Settings \u2192 App Updates: check the version line and tap \u201cCheck for updates\u201d once; the download network can be switched to Wi-Fi only. 3) Next time a new version ships, watch for it downloading itself and showing \u201cNew version downloaded\u201d — tap Install (the first time it walks you through a one-time \u201cinstall unknown apps\u201d permission). 4) Wait for an AI message you haven't seen (e.g. a distraction companion message) and check the Chat tab for the red badge; opening the tab clears it");
        out.add(e1270);

        Entry e1263 = new Entry("v1.26.3", "2026-10-27");
        e1263.add(new Group(s("新增", "New"),
                s("设置里多了一项「提示页重弹间隔」：全屏提示页被你划走、还没处理完时，隔多久再弹回来——15 秒、30 秒、1 分钟、5 分钟四档可选，你的账号已经设为 15 秒。晨间页、守卫问询、选下一个任务的页面、静音暂停的叫回，四处统一按这个间隔回来，不再各弹各的",
                  "Settings gains a \u201cPrompt re-pop interval\u201d: when you swipe a full-screen prompt away without finishing it, how long before it pops back — 15 seconds, 30 seconds, 1 minute or 5 minutes. Your account is already set to 15 seconds. The morning check-in, guard inquiries, the next-task page and the silent pause call-back now all come back on this one interval instead of each having its own")));
        e1263.add(new Group(s("修复", "Fixed"),
                s("日历页的计划块（蓝色那些）排版跟上了：以前只有实际块改成了「任务名在前」，计划块还是时间在第一行、名字跟在后面，还没有优先级标记。现在计划块也是任务名第一（粗体、最多两行）、下面是无字优先级标记、最后才是小字时间，矮块同样先保名字",
                  "Plan blocks on the calendar page (the blue ones) caught up: only the actual blocks had been reordered to lead with the task name, while plan blocks still led with the time, put the name after it, and had no priority marker. Plan blocks now also lead with the task name (bold, up to two lines), then the textless priority marker, then the time in small print — and short blocks still protect the name first"),
                s("更新日志弹框经常不弹，修好了：以前它排在建议设置后面、每次升级都被抢先，还没等补上就被下一次升级盖过去；而且记录是「还没显示就先记成弹过」，被别的全屏页盖住一次就永远不再弹。现在改成回到主界面时补弹，且真正显示出来之后才记账，被盖住不算数",
                  "The What's New popup often never appeared — fixed. It used to queue behind the suggested-settings page on every upgrade and get skipped, then be superseded by the next upgrade before it ever showed; and it recorded itself as shown before it was actually displayed, so one interruption by another full-screen page consumed it forever. It now pops when you are back on the main screen, and only counts as shown once it has really been displayed")));
        e1263.why = s("你说提示页划走后等 5 分钟太久、应该 15 秒就回来，顺手报了计划块和更新日志弹框两个毛病。重弹间隔做成一个你能自己改的设置（存云端、手机电脑共用一份），四处提示统一听它的，免得以后各改各的又乱掉",
                "You said five minutes is far too long for a swiped-away prompt to come back — it should be 15 seconds — and reported the plan-block layout and the missing What's New popup along the way. The re-pop interval is now one setting you control (synced via the cloud, shared by phone and computer), and all four prompt kinds obey it, so they can't drift apart again");
        e1263.tryIt = s("三处可以试：1）早上晨间页出来时按返回划走它，数 15 秒——应该自己全屏回来（在「设置 → 守卫 → 提示页重弹间隔」里能改这个数字）。2）打开日历页看蓝色计划块：先读任务名、再认标记、最后才是小字时间。3）这一版的更新日志弹框本身就是验收：升级后回到主界面，它应该稳稳地弹出来",
                "Try three things: 1) When the morning page appears, swipe it away with Back and count 15 seconds — it should come back full-screen on its own (the number lives in Settings \u2192 Guards \u2192 Prompt re-pop interval). 2) Open the calendar and read a blue plan block: task name first, then the marker, then the time in small print. 3) This very popup is the third check — after upgrading and returning to the main screen, it should reliably appear");
        out.add(e1263);

        Entry e1262 = new Entry("v1.26.2", "2026-10-27");
        e1262.add(new Group(s("改进", "Improved"),
                s("任务的优先级层级从此永不自动变：以前没做完的次要任务滚到第二天会被自动升成必做，现在滚入只把截止日挪到今天、挂个「昨」字，层级一格不动——重要、次要都一样，升不升必做只能你自己改",
                  "A task's priority level never changes on its own anymore: an unfinished Secondary task used to be promoted to Must when it rolled into the next day; rolling now only moves the deadline to today and adds the \u201cyesterday\u201d mark, for Important and Secondary alike. Only you can raise or lower a level"),
                s("自动排日程的顺序变了：必做无条件先排，然后是今天到期的重要、今天到期的次要，最后才轮到还没到期的重要和次要——到期日就是该今天做的信号，排在没到期的前面",
                  "Auto-scheduling order changed: Must first, unconditionally; then Important tasks due today, Secondary tasks due today, and only then not-yet-due Important and Secondary — a deadline falling today is the signal it belongs to today, ahead of anything not yet due"),
                s("顶部时间账下面多了一行小字：今天到期（非必做）共几件、大约要占多少时间（按 ×1.5 算）。主账还是只算必做的；到期的排不进今天，也会在点名行里被点出来",
                  "The time account at the top gains a small line under it: how many non-must tasks are due today and roughly how much time they take (at \u00d71.5). The main account still counts Must only, and due tasks that don't fit are now named in the overflow line too"),
                s("日历块上的优先级标签去掉了文字，只剩下颜色和形状：朱砂红实心小块是必做，描边空心是重要，浅灰实心小点是次要，虚线空心是休息。颜色和形状本身就说明了轻重，不用再读字",
                  "Priority tags on calendar blocks lost their text — only colour and shape remain: a solid vermilion chip is Must, a hollow outline is Important, a small solid grey chip is Secondary, and a dashed hollow outline is Leisure. Colour and shape say the level on their own, no reading needed")));
        e1262.why = s("你定的原则是层级只能人来改；我去查了 Todoist、Motion、Sunsama 这些主流任务软件，它们全都是优先级和截止日各管各的一条轴、谁也不自动改谁，逾期只动日期。我们就照这个行业共识靠拢，顺手把「截止日就是今天、但不是必做」的任务待遇一次定清：进今天的日程（排在必做后面）、进时间账的小字、排不下被点名、做不完只滚日期",
                "Your principle was that only you may change a level. I checked the mainstream task apps — Todoist, Motion, Sunsama — and all of them keep priority and due date as two independent axes that never rewrite each other; being overdue only moves the date. We aligned with that consensus and settled how a \u201cdue today but not must-do\u201d task is treated: it joins today's schedule after the must-dos, shows in the account's small print, gets named when it doesn't fit, and only its date rolls when unfinished");
        e1262.tryIt = s("找一件昨天没做完的次要任务：看它今天还在不在、层级是不是还是次要（名字旁有「昨」字）；打开日历页认一认新标记——红实心必做、描边重要、灰点次要、虚线休息；再看任务页最上方时间账主数字下面那行到期小字对不对得上",
                "Find a Secondary task you didn't finish yesterday: check it is still here today and still Secondary (with the \u201cyesterday\u201d mark by its name). On the calendar page, read the new markers — solid red Must, outline Important, grey chip Secondary, dashed Leisure. Then check the small due-today line under the time account at the top of the task page");
        out.add(e1262);

        Entry e1261 = new Entry("v1.26.1", "2026-10-27");
        e1261.add(new Group(s("改进", "Improved"),
                s("日历块的排版顺序换了：第一行就是任务名（字大、最多显示 2 行，尽量让你先读到是哪件任务），名字下面是必做/重要/次要/休息的小标签，起止时间挪到最后一行、只作小灰字。块太矮时按这个顺序让位——先保任务名，再保标签，时间缩成只写开始时刻、或者干脆不显示，不会再出现只看得见时间、看不见任务名的块",
                  "Calendar blocks are reordered: the task name comes first (larger, up to two lines, so you read which task it is before anything else), the Must / Important / Secondary / Leisure tag sits under the name, and the start–end time moves to the last line as small grey text. Short blocks yield in that same order — name first, tag second, the time shrinks to just the start or drops out — so a block that shows only a time and no name can no longer happen")));
        e1261.why = s("之前日历块把时间排在第一行、还跟任务名挤在一个框里共两行，名字经常被截得看不见——你说这个页面太难读，首先得显示任务的名字，其次是重要性级别，时间小字标明就够了",
                "Calendar blocks used to lead with the time range and squeeze it together with the task name into two shared lines, so names were often cut off — you said the page was hard to read: the task name has to come first, then its priority level, and the time only needs to be there in small print");
        e1261.tryIt = s("打开日历页：每个块第一眼先读任务名，第二眼看名字下面的小标签认轻重，最底下一行小灰字是起止时间；再找一个时长很短的小块，看它是不是也先显示名字（时间可能只剩开始时刻或不显示，这是故意的）",
                "Open the calendar page: each block now reads task name first, then the small priority tag under it, with the start–end time as small grey text on the last line. Find a very short block and check it also leads with its name (its time may shrink to just the start, or drop out — that is deliberate)");
        out.add(e1261);

        Entry e1260 = new Entry("v1.26.0", "2026-10-27");
        e1260.add(new Group(s("新功能", "New"),
                s("任务软件名单：开始一个任务时，AI 会按这个任务把你的常用软件分成三档——白：放心用；灰：可能有用，先不打扰你；黑：跟任务明显无关，一打开就弹出来问你是不是分神。名单没生成好之前，先用一份通用黑名单（抖音、快手、小红书这类）兜底",
                  "Per-task app lists: when you start a task, the AI sorts your frequently used apps for that task into three tiers — white: safe to use; grey: might be useful, no interruptions for now; black: clearly unrelated to the task, and opening one pops up the inquiry asking whether you're drifting. Until a task's list is ready, a built-in generic blacklist (Douyin, Kuaishou, Xiaohongshu and the like) stands in"),
                s("「AI 重排」改走软件内置的 AI：点日历页的「AI 重排」，内置 AI 当场按老规矩（每件 ×1.5、不排过 22:30、已过和已完成的块不动）给一版新排程提案，摆给你看，点「应用」才落盘、点「不用」整版作废",
                  "\"AI Re-plan\" now runs on the app's built-in AI: tap it on the calendar page and the built-in AI drafts a new plan on the spot under the standing rules (each task ×1.5, nothing past 22:30, past and finished blocks untouched), shows it to you, and it only lands when you tap Apply — No, thanks discards the whole draft"),
                s("优先级多了两档——重要和休息：现在是必做、重要、次要、休息四档。必做今天必须排上日历，重要有空就排，次要最后填；休息是娱乐类的事，只在你休息的时候才做，永远不会被自动排进日程。日历上每个任务下面多了一个小标签：必做是朱砂红实心，重要是细描边，次要是浅灰小字，休息是灰色虚线边，一眼看出轻重",
                  "Priorities gain two levels — Important and Leisure. There are now four: Must (goes on today's calendar), Important (scheduled when there is room), Secondary (fills what is left) and Leisure (fun stuff for rest time only, never auto-scheduled). Each calendar block now carries a small tag under the task name: solid vermilion for Must, a thin outline for Important, plain grey for Secondary and a dashed grey outline for Leisure")));
        e1260.add(new Group(s("修复", "Fixed"),
                s("修好晨间提醒：早上 check-in 没做完、又没说推迟多久的，每 5 分钟全屏弹回，直到你做完或选了推迟时长",
                  "Fixes the morning reminder: if the morning check-in isn't finished and you haven't picked a pause length, the page comes back full-screen every 5 minutes until you finish it or choose how long to pause"),
                s("守卫不再向你要屏幕录制授权：录屏通道整套拿掉，截图只走无障碍一条路；无障碍没开时守卫就静默不采样，不会再弹出「录哪个软件还是整个屏幕」的系统框",
                  "The guard never asks for screen-recording permission again: the recording channel is removed entirely and screenshots come only from the accessibility channel; with accessibility off, the guard silently skips sampling instead of popping the system's \"record one app or the whole screen\" dialog")));
        e1260.add(new Group(s("改进", "Improved"),
                s("旧的同步桥彻底移除：同步只剩云端一条路，设置和各个页面里再没有旧桥的入口和字样；断网重连后同步照常走云端",
                  "The old sync bridge is gone for good: sync is cloud-only now, and no page or setting mentions the old bridge anymore; reconnecting after being offline still syncs through the cloud as usual"),
                s("软件里不再提外部助手：排程由本地自动排程和内置 AI 完成，帮助里新增了「AI 功能的边界」一节，把哪些 AI 是软件自带的、哪些是作者个人的设置、数据谁能看，都写明白了",
                  "The app no longer mentions any outside assistant: planning is done by local auto-planning and the built-in AI, and Help has a new section, \"What the AI can and cannot do\", spelling out which AI ships with the app, which parts are the author's personal setup, and who can see your data")));
        e1260.why = s("以前守卫只能看屏幕内容猜你是不是分神；先按任务把软件分好档，黑名单就不用猜、反应也快，白名单也不会再误拦。这一版也把两件历史包袱清干净（旧同步桥、录屏授权），并把 AI 的边界一次说清",
                "The guard used to have to guess from screen content whether you were drifting; with apps sorted per task up front, the blacklist needs no guessing and reacts fast, and the whitelist stops false blocks. This release also clears out two pieces of legacy (the old sync bridge and the recording permission) and states the AI's boundaries once and for all");
        e1260.tryIt = s("1）开始一个任务（比如读经典），等几分钟名单生成；2）任务详情里点「软件名单」看三档分得对不对，点某个软件能改档，你改过的不会再被覆盖；3）计时中打开抖音，应该立刻弹问询；打开 AI 聊天软件则不会因为名单被拦；4）明早晨间 check-in 先别提交、也别点推迟，把页面切走，看 5 分钟内是否弹回；5）打开日历页点「AI 重排」，看它当场给出的提案，点「不用」不会动日程；6）翻设置与各页面，确认再没有旧桥字样；守卫开着用一天，确认不再出现任何录屏授权弹窗；7）设置→帮助，翻到「新用户必读：AI 功能的边界」那一节；8）把一个任务改成『重要』：主列表点优先级那格选重要，或在编辑页点『重要』，看自动排程是不是排在必做后面、日历块下面有没有『重要』标签；再把一个娱乐任务设成『休息』，确认它不会被自动排进日程，在衔接页选『休息一会』时它会出现在『休息时做』一组里",
                "1) Start a task (say, reading the classics) and wait a few minutes for its list to be generated. 2) In the task's details, tap \"App list\" to check the three tiers; tap any app to re-tier it — your changes are never overwritten. 3) While timing, open Douyin: the inquiry should pop up at once; opening an AI chat app should not be blocked by the list. 4) Tomorrow morning, don't submit the check-in and don't pick a pause; switch away and see whether the page comes back within 5 minutes. 5) Open the calendar and tap \"AI Re-plan\" to see the on-the-spot draft; \"No, thanks\" leaves your schedule untouched. 6) Look through Settings and the pages and confirm the old bridge is nowhere to be seen; run the guard for a day and confirm no recording-permission dialog ever appears. 7) Settings → Help, scroll to \"For new users: what the AI can and cannot do\"");
        out.add(e1260);

        Entry e1250 = new Entry("v1.25.0", "2026-10-27");
        e1250.add(new Group(s("修复", "Fixed"),
                s("修好问询页有时看不到「分析错误」：页面太长时底部按钮被挤出屏幕、又不能滚动——现在整页可滑，「分析错误」和「误判放行」挨在一起，就在宽限按钮后面；点开「分析错误」后页面会自动滑到纪律输入框，保存和跳过都点得到",
                  "Fixes the inquiry page sometimes not showing \"Analysis error\": when the page ran long, the bottom buttons were pushed off screen and there was no way to scroll — the whole page now scrolls, \"Analysis error\" sits right next to \"False alarm, let it pass\", just after the grace buttons; and after tapping it, the page scrolls itself to the rule input so Save and Skip are always reachable"),
                s("「我的纪律」页同样的毛病一并修了：纪律攒多了之后，靠后的条目此前滑不到、删不掉，现在整页可滑",
                  "The same flaw on the \"My guard rules\" page is fixed too: once you had saved many rules, the later ones could not be scrolled to or deleted — the page now scrolls end to end")));
        e1250.why = s("判错了却没地方喊冤，这个按钮必须永远够得着",
                "Being judged wrong with nowhere to appeal is the one thing this button exists for — it has to be reachable every single time");
        e1250.tryIt = s("1）触发一次分神问询，上下滑页面，确认「分析错误」和「和 AI 聊聊」都能点到；2）把系统字体调到最大再触发一次，同样能滑到底",
                "1) Trigger a distraction inquiry, scroll the page up and down, and check that both \"Analysis error\" and \"Chat with AI\" can be tapped. 2) Set the system font to the largest size, trigger it again, and check you can still scroll all the way to the bottom");
        out.add(e1250);

        Entry e1245 = new Entry("v1.24.5", "2026-10-25");
        e1245.add(new Group(s("改进", "Improved"),
                s("换新颜：桌面图标换成定稿的「未闭合的圆」——墨青黑的圆环右上留缺口，一颗朱砂红点落在圆上；App 配色统一成三基色：米白打底、墨青黑为主、朱砂红点缀",
                  "A new face: the launcher icon is now the finalized \"unclosed circle\" — an ink teal-black ring left open at the upper right, with a single vermillion dot resting on the ring; the app's palette is unified into the three brand colors: rice white as the base, ink teal-black as the primary, vermillion as the accent")));
        e1245.why = s("图标和配色是知行的脸面，定下来就不飘了；三基色也正合「低饱和、只许一个点缀色」的规矩",
                "The icon and the palette are KnowDo's face — once settled, they stop drifting; the three colors also fit the house rule of low saturation with a single accent");
        e1245.tryIt = s("1）装完看桌面图标是不是新圆环；2）打开 App，看底色是不是米白、点缀是不是朱砂红；3）留意悬浮窗等深色场景，看有没有旧颜色残留不协调",
                "1) After installing, check the home-screen icon is the new ring. 2) Open the app and check the base is rice white and the accent is vermillion. 3) Keep an eye on dark scenes like the floating window for any leftover old colors that clash");
        out.add(e1245);

        Entry e1244 = new Entry("v1.24.4", "2026-10-25");
        e1244.add(new Group(s("修复", "Fixed"),
                s("语义小模型下载总是卡在「分片大小不对」。原因是模型托管方的接口在传这类二进制文件时会把内容截断，改成文本编码中转下发后就能完整到达；同时每个分片下坏了会自动重试几次，不用你反复点",
                  "Fixes the semantic mini-model download always stalling on \"part size mismatch\". The model host's interface truncates this kind of binary content in transit, so the parts are now delivered via a text encoding that arrives intact; and if a part does come down damaged, it retries a few times on its own — no need to keep tapping")));
        e1244.why = s("这个模型是守卫语义判定的本地主力，下不下来，判定就只能靠更粗的规则顶着",
                "This model is the local workhorse of the guard's semantic verdicts — while it can't be downloaded, verdicts have to make do with the cruder rules");
        e1244.tryIt = s("去设置 → 模型下载，重新下载语义小模型，看它这次能不能装完、状态变成「已就绪」",
                "Go to Settings → Model downloads, download the semantic mini-model again, and see whether it finishes installing this time and its status turns to Ready");
        out.add(e1244);

        Entry e1243 = new Entry("v1.24.3", "2026-10-25");
        e1243.add(new Group(s("新功能", "New"),
                s("守卫判错时，问询页多了「分析错误」按钮——点它，这次分神不算数，还能顺手留一条 20 字以内的纪律，比如「查资料用浏览器算正事」；往后联网判定会带着你的纪律一起判，同类误报会变少；纪律在设置里随时能看、能删",
                  "When the guard gets a verdict wrong, the inquiry page now has an \"Analysis error\" button — tap it and that drift doesn't count; you can also leave a rule of up to 20 characters, e.g. \"looking things up in the browser counts as work\". From then on, online verdicts judge with your rules included, so the same kind of false alarm happens less; your rules can be viewed and deleted any time in Settings")));
        e1243.why = s("守卫再准也有判错的时候，让你一键纠正、顺手教它一条规矩，它越用越懂你",
                "However accurate the guard gets, it will still be wrong sometimes. One tap corrects it, and leaving a rule teaches it a standard of yours — the more you use it, the better it knows you");
        e1243.tryIt = s("1）下次被判分神时，在问询页点「分析错误」；2）写一条纪律保存，不写也行，只标误报；3）去设置 → 屏幕守卫 → 我的纪律，看纪律在不在、能不能删",
                "1) Next time you're judged as drifting, tap \"Analysis error\" on the inquiry page. 2) Write a rule and save it — or skip it and just mark the false alarm. 3) Go to Settings → Guards → My guard rules, and check the rule is there and can be deleted");
        out.add(e1243);

        Entry e1242 = new Entry("v1.24.2", "2026-10-25");
        e1242.add(new Group(s("修复", "Fixed"),
                s("修复在设置里点「守卫测试模式」时整个屏幕变白、测试窗不出来的问题。现在点它会正常先出选尺子页、再出测试窗，判定读数照常刷新；万一哪一步出问题，也会直接把错误亮出来，不再白屏把你晾在那",
                  "Fixes the whole screen turning white — with no test window appearing — when you tap Guard test mode in Settings. Now it properly shows the ruler picker first, then the test window, and the verdict readings refresh as usual; and if any step does go wrong, the error is shown to you directly instead of a white screen leaving you stranded")));
        e1242.why = s("守卫测试模式是你当场验收守卫判定最直接的工具，它一坏，守卫到底判得对不对就没法亲眼验",
                "Guard test mode is the most direct tool you have for verifying the guard's verdicts on the spot — while it is broken, there is no way to see with your own eyes whether the guard judges rightly");
        e1242.tryIt = s("1）设置里点「守卫测试模式」，看选尺子页是否正常出现；2）选一个任务当尺子，看测试窗是否出来、判定读数有没有在刷新；3）退出测试模式，看界面是否完好恢复、没有白色残留",
                "1) Tap Guard test mode in Settings and check that the ruler picker appears. 2) Pick a task as the ruler, and check that the test window comes up and its verdict readings keep refreshing. 3) Leave test mode and check the interface is fully restored, with no white residue");
        out.add(e1242);

        Entry e1241 = new Entry("v1.24.1", "2026-10-25");
        e1241.add(new Group(s("新功能", "New"),
                s("设置里的「测试模式」正式改名叫「测试者模式」，还是原来那个开关。打开它之后，软件内部任何报错都会额外用一个悬浮窗显示五秒：哪个功能出的错、服务器回了几零几、错误是什么，一眼就能看见。尤其是聊天，代理或者模型那边回了 401、429、500 哪一种，都会原样亮出来，不再只有一句「过一会儿再聊」",
                  "The Settings switch formerly called \"Test mode\" is now officially \"Tester mode\" — the same switch as before. With it on, any internal error in the app also pops up in a small overlay for five seconds: which feature failed, the exact status code the server sent back, and what the error was, all visible at a glance. Chat above all: whether the proxy or the model side answered 401, 429 or 500, the code shows as-is — no longer just a \"try again later\"")));
        e1241.why = s("出了问题看不见原因就只能瞎猜；把错误码亮出来，你自己能定位，我也能凭你报的码直接对症修",
                "When something breaks and the cause stays invisible, all anyone can do is guess. Show the error code and you can pinpoint it yourself — and I can fix the actual problem straight from the code you report");
        e1241.tryIt = s("1）设置里打开「测试者模式」；2）去「聊聊」发一条消息，等它失败，看悬浮窗有没有跳出来、上面写的是几零几；3）把那个码告诉我，我来对症处理",
                "1) Turn on Tester mode in Settings. 2) Send a message in Chat, wait for it to fail, and watch whether the overlay pops up and which code it shows. 3) Tell me the code, and I'll fix the actual cause");
        out.add(e1241);

        Entry e1240 = new Entry("v1.24.0", "2026-10-25");
        e1240.add(new Group(s("新功能", "New"),
                s("聊天机器人正式成为底部的一个页签，叫「聊聊」。一，进页签就能跟 AI 聊天：它能帮你新建、修改、删除任务（老规矩，先出提案卡，你点应用才生效，删除也不例外），能教你这个软件怎么用，也能帮你改设置。二，你明显分神时，它会主动发来一条消息：「你已经很分神了啊，让我来帮帮你」，陪你聊这个任务怎么改才做得下去。三，任务超时到了结束页，页面里直接有聊天区，AI 先开口问你做得怎么样，你当场聊完再决定下一步。四，所有对话，包括它主动发的，都存在「聊聊」页签里，随时能翻",
                  "The chat companion is now a bottom tab of its own, called Chat. One: open the tab and talk — it can suggest creating, changing or deleting tasks (same rule as always: a suggestion card first, nothing happens until you tap Apply, deletions included), it can teach you how to use this app, and it can change settings for you. Two: when you are clearly distracted, it speaks first — \"you've been pretty distracted, let me help you with that\" — and talks through how this task could be reshaped so you can actually do it. Three: when a task runs out of time, the end page now has a chat area right inside it; the AI opens by asking how it went, you talk it through on the spot, then decide what comes next. Four: every conversation, including the ones it starts, lives in the Chat tab, ready to scroll back through")));
        e1240.why = s("聊天不该是藏在角落里的功能，它应该是你随时能说话的伙伴；分神和超时正是最需要有人拉一把的时刻，它在那时出现，比事后的统计有用",
                "Chat shouldn't be a feature tucked in a corner — it should be the companion you can talk to any time. Distraction and time-up are exactly the moments a hand helps most; showing up then beats any after-the-fact statistic");
        e1240.tryIt = s("1）点底部「聊聊」，说「帮我建个明天读书的任务」，看提案卡再点应用；2）问它「屏幕守卫在哪关」，看答得对不对；3）让一个任务超时，在结束页的聊天区跟它说话，回页签看对话在不在；4）开守卫故意分神一次，看聊天里有没有它主动发的消息（真正开口聊天要等聊天代理部署好，没部署时它会如实提示）",
                "1) Open the Chat tab at the bottom, say \"create a reading task for tomorrow\", check the suggestion card, then tap Apply. 2) Ask it \"where do I turn off the screen guard\" and see whether the answer is right. 3) Let a task run out of time and talk to it in the chat area on the end page, then open the Chat tab and check the conversation is there. 4) Turn the guard on and get distracted once — see whether its message shows up in the chat (real replies need the chat proxy deployed; until then it will honestly say so)");
        out.add(e1240);

        Entry e1230 = new Entry("v1.23.0", "2026-10-25");
        e1230.add(new Group(s("新功能", "New"),
                s("三件。一，任务页最上方多了「今日时间账」：今天还剩多少小时、必干的活占多少小时、还空余多少小时，一眼看清今天装不装得下，数字随你完成任务实时变。二，昨天没干完的任务会自动滚到今天变成必干：昨天到期或排进昨天日程、没做完的，今天打开就升为必干、排进今天，名字旁有个小小的「昨」字（每天重复的任务除外，它自己会再生）。三，聊天能排程了：跟 AI 说「帮我把今天重新排一下」，它按你的规矩（每件按时长乘一点五留缓冲、不排过晚上十点半、排不下会明说）给出新日程，出一张排程卡，你点「应用」才生效；新加的和滚进来的必干任务，App 会自己填进今天的空档，不用你开口",
                  "Three things. One, the task page now opens with a time account for today: how many hours are left today, how many the must-do work takes, and how much is still free — you see at a glance whether today can hold it all, and the numbers move as you finish tasks. Two, yesterday's unfinished tasks roll into today as must-dos: anything that was due yesterday or sat in yesterday's schedule and didn't get done opens today already promoted to must-do and placed into today's plan, with a small marker next to its name (daily repeating tasks are the exception — they regenerate on their own). Three, chat can schedule now: tell the AI \"replan my day\", and it lays out a new schedule by your rules (every task counts at one-and-a-half times its length for buffer, nothing past 22:30, and anything that doesn't fit is said out loud), presented as a plan card that only takes effect when you tap Apply; newly added and rolled-in must-dos are slotted into today's gaps by the app itself, no asking needed")));
        e1230.why = s("先看见今天装不装得下，再决定接不接新活，心里有数就不慌；拖延最怕任务掉进黑洞没人管，滚到今天变必干，它就跑不掉；排程这种事说一句比拖十下快，但最终拍板还是你",
                "See whether today can hold it before you take on anything new, and there is nothing to be anxious about; procrastination's worst trick is a task falling into a black hole where nobody watches it — rolled into today as a must-do, it can't get away; and saying one sentence is faster than dragging ten blocks around, while the final call stays yours");
        e1230.tryIt = s("1）打开任务页看最上面的时间账，对一下和你心里的数合不合，再完成一个必干任务，看数字有没有跟着变；2）找一个昨天到期没做完的任务，看它今天是不是变成必干、名字旁有「昨」字、并且排进了今天的日历；3）新建一个今天要做的必干任务，什么都别点，去日历页看它是不是自己出现在空档里；4）等聊天代理部署后，在对话里说「帮我把今天重新排一下」，检查排程卡，再点「应用」确认日历真的变了",
                "1) Open the task page and check the time account at the very top against the numbers in your head, then finish one must-do task and watch the numbers move. 2) Find a task that was due yesterday and never got done: today it should be a must-do, carry the small marker next to its name, and sit inside today's calendar. 3) Create a new must-do task for today and touch nothing else — open the calendar and see whether it placed itself into a gap. 4) Once the chat proxy is deployed, say \"replan my day\" in a chat, check the plan card, then tap Apply and confirm the calendar really changed");
        out.add(e1230);

        Entry e1220 = new Entry("v1.22.0", "2026-10-25");
        e1220.add(new Group(s("新功能", "New"),
                s("新增「和 AI 聊聊这个任务」。任务编辑里点进去就能跟 AI 聊这个任务——它知道这个任务的标题、备注、计划时长和今天已经做了多久。你觉得任务哪里别扭，跟它说，它会提出具体改法（改标题、改时长、拆成子任务、挪时间），变成一张提案卡摆在对话里，你点「应用」才真正改，点「不用」就当没提过。任务页也有一个不带任务的聊天入口，想到什么都能聊",
                  "New: \"Chat with AI about this task\". Open it from a task's edit sheet and talk the task through with an AI that knows its title, note, planned length and how much you have already done on it today. Say what feels off about the task and it proposes a concrete fix — rename it, change its length, split it into subtasks, move its time — as a suggestion card inside the chat. Nothing changes until you tap Apply; tap \"No, thanks\" and it is as if it never suggested it. The task page also has a chat entry with no task attached, for anything on your mind")));
        e1220.add(new Group(s("改进", "Improved"),
                s("任务名列可以拉宽了：列表里拖动就能把任务名那一列拓宽，宽度会记住；名字太长时在行里最多换成两行显示；点开任务时名字完整显示、不再被截断",
                  "The task-name column can be widened: drag in the list to make the name column broader and the width is remembered; a long name now wraps to at most two lines inside its row; and when you open a task, its name shows in full, never cut off")));
        e1220.why = s("任务没做好，很多时候不是不想做，是任务本身没想清楚——太大、太糊、或者时机不对。先花两分钟聊清楚再动手，比硬撑、拖着、自责都管用。另外，任务名是认任务的第一眼，列太窄、名字被截断时找任务要多点好几下",
                "A task undone is often not about willpower — the task itself was never thought through: too big, too vague, or at the wrong time. Two minutes of talking it clear first beats gritting your teeth, dragging it along, or blaming yourself. Also, the task name is the first thing you recognise a task by — when the column is too narrow and names are cut off, finding the right task takes several extra taps.");
        e1220.tryIt = s("1）随便打开一个任务的编辑页，点「和 AI 聊聊这个任务」，说一句「这个任务我一直拖着，不知道从哪下手」，看它怎么接话；2）等它给出提案卡，点一次「应用」，回任务列表确认改动真的生效；再聊一次、点「不用」，确认任务没被动过；3）看对话页顶部小字，那是现在实际在用的模型，系统会自动挑当时最强的免费模型，它失败了会自动换下一个",
                "1) Open any task's edit sheet, tap \"Chat with AI about this task\", and say \"I keep putting this off and don't know where to start\" — see how it picks the conversation up. 2) When it offers a suggestion card, tap Apply once and check the task list to confirm the change really landed; chat once more, tap \"No, thanks\" this time, and confirm the task is untouched. 3) Look at the small line at the top of the chat page: that is the model actually answering right now — the system picks the strongest free model of the moment automatically, and if it fails, moves on to the next one by itself.");
        out.add(e1220);

        Entry e1214 = new Entry("v1.21.4", "2026-10-25");
        e1214.add(new Group(s("新功能", "New"),
                s("模型改为按需下载：黄色识别模型和语义小模型不再装进安装包，安装包瘦了约 10 MB；谁开哪个功能，就在设置「模型下载」里把对应模型下到手机上，下完自动校验、坏了会自己报错让你重下",
                  "Models are downloaded on demand now: the image model and the semantic mini-model no longer ride inside the install package, which is about 10 MB slimmer. Whatever feature you turn on, download its model in Settings → Model downloads; each one is verified after downloading, and a bad file reports itself and asks to be downloaded again"),
                s("守卫能判「内容和任务合不合」了：以前守卫只认应用类别和关键词——任务是看张雪机车的视频、你却在看美食视频，它多半看不出来。现在粗判拿不准的画面会多问一层：先用手机本地的小模型比一比内容和任务像不像，还拿不准才把文字（敏感信息已自动打码，绝不发图）发给云端大模型终判。云端连不上时自动改用本地模型接着判，不耽误守卫工作",
                  "The guard can now judge whether content matches the task: before, it only knew app categories and keywords — if the task was watching Zhangxue motorcycle videos and you were watching food videos instead, it mostly couldn't tell. Now, a frame the quick checks can't decide gets one more layer: an on-device mini-model first compares how alike the content and the task are, and only if that is still unsure does it send the text (sensitive bits auto-masked, never an image) to a cloud model for the final call. If the cloud is unreachable it quietly keeps judging with the local model, so the guard never stops working"),
                s("设置里多了两个开关：「语义判定」关掉后只用本地判定、任何数据不出手机；「测试者模式」（当时暂名「测试模式」，v1.24.1 正式改名）是给愿意帮忙查错的用户准备的，打开后判定结果会显示来源等诊断信息",
                  "Two new switches in Settings: turn \"Semantic verdicts\" off and judging stays fully on-device — nothing leaves the phone. \"Tester mode\" (introduced as \"Test mode\", formally renamed in v1.24.1) is for people willing to help debug: with it on, verdicts show where each call came from and other diagnostic info")));
        e1214.why = s("三件其实是一件——让守卫从「认应用」进到「认内容」，又不把安装包撑大、不把你的屏幕文字随便送出去。模型按需下载是这个平衡的支点：包里只留引擎，模型谁用谁下；语义判定只传打码后的文字、还留了彻底关掉的开关，判得准和护得住隐私不该二选一",
                "The three are really one: move the guard from recognising apps to recognising content, without bloating the install package or sending your screen text around carelessly. On-demand models are the pivot of that balance — the package keeps only the engine, and whoever uses a feature downloads its model. Semantic judging sends only masked text and keeps a switch that turns it off completely: judging well and guarding privacy should never be a trade-off.");
        e1214.tryIt = s("1）装好后先去设置「模型下载」，把两个模型都下下来（黄色识别约 12 MB、语义小模型约 24 MB），留意下载完都有校验通过的提示；删掉包里旧模型后安装包应明显变小。2）开计时做一个任务（比如「看张雪机车视频」），去微信视频号看一个美食视频：以前这种多半放行，现在语义层应能判出不相关并走问询；再切回机车视频，应判相关放行。3）把设置里「语义判定」关掉，重复一次：判定只剩本地分数，测试窗里会标明来源是本地。4）断网或开飞行模式再触发一次拿不准的画面：守卫应照常给出判定（本地降级），界面上不出现任何报错字样；若你打开了设置里的「测试者模式」，判定底部才会多一行「大模型无法连接，用本地小模型」",
                "1) After installing, go to Settings → Model downloads and download both models (image model about 12 MB, semantic mini-model about 24 MB) — each should report verified when done; the install package itself should be visibly smaller now. 2) Start a timer on a task (say \"watch Zhangxue motorcycle videos\"), then watch a food video in WeChat Channels: this used to slip through, now the semantic layer should call it unrelated and start an inquiry; switch back to a motorcycle video and it should pass as relevant. 3) Turn \"Semantic verdicts\" off in Settings and repeat: judging uses only the local score, and the test window labels the source as local. 4) Go offline or into airplane mode and trigger another unsure frame: the guard should still reach a verdict (local fallback) with no error text anywhere; only if you also turned on \"Tester mode\" in Settings does a line appear at the bottom of the verdict: the cloud model is unreachable, judging with the local mini-model.");
        out.add(e1214);

        Entry e1213 = new Entry("v1.21.3", "2026-10-25");
        e1213.add(new Group(s("新功能", "New"),
                s("守卫测试模式开之前，先选一个任务当「尺子」：点开测试模式会先弹一个选择页——选你现有的任务（今天的排在前面）、临时建一个（只填标题、备注可选，只在这次测试里用，永远不会进你的任务列表）、或者不选。判定本来就是拿屏幕内容跟任务比对：以前没计时就没任务可比，只能按内容类别粗判、几乎总显「不分神」，你没法验收它判得对不对。现在选好尺子再开测，悬浮窗里还会写明当前尺子是哪条任务；关掉测试模式，选的尺子就清掉，下次重选。无障碍服务自动续开测试时沿用你上次选的尺子，不会半路弹页打断你。",
                  "Guard test mode now asks for a ruler task first: turning test mode on opens a picker — choose one of your existing tasks (today's first), create a temporary one (title only, note optional; it lives only for this test session and never enters your task list), or pick none. A verdict is fundamentally \\\"does what's on screen match the task\\\": with no timer running there used to be no task to compare against, so it could only judge by content category and almost always said \\\"not distracted\\\" — impossible to verify. Now you pick the ruler before testing, the floating window names the ruler in use, closing test mode clears the pick, and when the accessibility service resumes test mode on its own it reuses your last pick instead of popping the picker at you."),
                s("宽限可以选到点怎么办了，而且到点真的会办：屏幕内容守卫的问询页上，宽限按钮上方多了一行单选——「退掉这个软件」（默认）或「只放到后台」，选一次下次默认沿用。以前宽限到点只是不再拦你、等下一次采样再抓到才跳回；现在你点宽限的那一刻就排好闹钟，到点它先复查你是不是还停在当时那个分神软件里：还在，就按你选的方式处理（退掉＝把那个软件切到后台并跳回你该用的软件；只放后台＝只切后台、不往回拉）；已经离开了，就什么都不做。处理发生时会在守卫记录和任务日志里留一条，写明用了哪种方式。先说清安卓的边界：一个应用杀不掉另一个应用，所谓「退掉」就是按 HOME 把它切到后台——不是卸载、不是强行停止。",
                  "Grace now has a chosen consequence, and it actually fires on time: on the screen-content guard's inquiry page, above the grace buttons there is a new two-way choice — \\\"Quit that app\\\" (default) or \\\"Background only\\\" — remembered for next time. Before, an expired grace merely stopped shielding you and jumped back only if a later sample caught the same category again; now, the moment you take grace, an alarm is set for its expiry. When it fires, the guard first checks whether you are still in the app that triggered the inquiry: if you are, it does what you chose (quit = send that app to the background via HOME and jump back to the app you should be using; background only = HOME and nothing more); if you have already left, it does nothing. Each enforcement is written into the guard events and the task log, naming the mode used. One Android limit, stated plainly: one app cannot kill another, so \\\"quit\\\" means pressing HOME to background it — not an uninstall, not a force-stop.")));
        e1213.add(new Group(s("改进", "Improved"),
                s("测试模式恢复「画面未变，跳过」：这是你上一版之后改定的——该跳就跳，跟正式守卫一致，省电也真实。但跳过时不再只剩一行状态：上一帧的判定正文会原样保留在窗口里（标着「上一帧判定」），你停在一个画面上，依然能看着它上次判了什么。正式守卫的采样和跳帧这版没动。",
                  "Test mode skips unchanged frames again — your call after the last release: skip what should be skipped, same as the real guard, saving battery honestly. But a skipped frame no longer leaves a lone status line: the previous frame's full verdict stays on the window (marked \\\"previous frame's verdict\\\"), so while you sit on one screen you can still see what it concluded last. The real guard's sampling and skipping are untouched in this release.")));
        e1213.why = s("两件都是你说完我记下、这版落地的。一件是验收的前提：测试模式总显「不分神」，不是判定太宽，而是它根本没有任务可比——你举的例子最能说明问题：任务是「回肖诗婷的微信消息」，你在回别人的消息，这该不该判分神，全看它拿哪条任务当尺子。没尺子的判定，测了也白测。另一件是你批准的守卫闭环：问询时你亲口给的宽限时长，以前到点全靠「下一次采样再抓到」才算数，那不是守信，是碰运气；到点主动复查、按你选的方式处理、处理完留记录，这个环才闭上。",
                "Both items are things you said and this release lands. One is the precondition for verifying anything: test mode always said \\\"not distracted\\\", not because the judging was lax but because it had no task to compare against — your own example proves it: if the task is \\\"reply to Xiaoshiting on WeChat\\\" and you are replying to someone else, whether that counts as drift depends entirely on which task is the ruler. A verdict without a ruler is untestable. The other closes the guard loop you approved: the grace length you grant in person used to count only if a later sample happened to catch you again — that is luck, not keeping a promise. Checking proactively at expiry, acting the way you chose, and leaving a record is what closes the loop.");
        e1213.tryIt = s("两处都可以试：1）去权限体检页打开守卫测试模式——会先弹选尺子页：随便选一个你现有的任务（或者临时建一个，比如标题写「看张雪机车视频」），再去微信视频号里看别的视频，测试窗应能判出分神嫌疑并写明尺子是哪条；停在一个画面别动，下一帧会显示「画面未变，跳过」但上一帧的判定还留在窗里。关掉测试模式再开，尺子要重新选。2）正式守卫里试宽限：开着守卫计时、故意触发一次问询，在问询页把到点处理切到「只放到后台」，点「给我 30 秒收尾」，到点后若你还停在那个软件里，它会被切到后台、但不会把你拉回任务软件；再触发一次、改回「退掉这个软件」，到点应被切后台并直接跳回任务。两次处理在任务日志里都能查到记录。",
                "Two things to try: 1) On the Permissions page turn on guard test mode — the ruler picker appears first: pick any existing task (or make a temporary one, say titled \\\"watch Zhangxue motorcycle videos\\\"), then go watch something else in WeChat Channels; the test window should flag a distraction suspicion and name its ruler. Stay on one screen and the next frame shows \\\"frame unchanged, skipped\\\" with the previous verdict still on the window. Turn test mode off and on again — the ruler must be picked afresh. 2) Try grace in the real guard: with the guard on and a timer running, trigger an inquiry on purpose, switch the consequence to \\\"Background only\\\", tap \\\"30 more seconds\\\" — at expiry, if you are still in that app it is sent to the background without pulling you back. Trigger once more, switch back to \\\"Quit that app\\\": at expiry it should be backgrounded and you should land back on your task. Both enforcements are recorded in the task log.");
        out.add(e1213);

        Entry e1212 = new Entry("v1.21.2", "2026-10-25");
        e1212.add(new Group(s("改进", "Improved"),
                s("「建议设置」页多了一项「无障碍截图通道」：新装或升级后第一次打开时会呈现的那一页，现在把无障碍也列在里面（排在悬浮窗后面），点它就直接跳到系统的无障碍设置去开——以前这一项只藏在权限体检页里。这一项的说明也写明白了：它是给屏幕内容守卫用的，守卫开着计时时靠它定期截一眼屏幕、看你做的是不是任务相关的事；不用屏幕守卫就不用开它，不开只是守卫没法看屏幕、守卫测试模式也用不了。页面开头也改直白了：这些都是可选的，每一项说明下面都写清了是用来干什么的，按你用不用对应功能决定，不开不影响其他功能使用。",
                  "The Suggested settings page gains an \"Accessibility screenshot channel\" item: the page shown once after install or upgrade now lists accessibility too (right after the overlay item), and tapping it jumps straight to the system accessibility settings — before, this switch only lived on the Permissions page. The item says plainly what it is for: it powers the screen-content guard — while a timer runs with the guard on, it screenshots the screen now and then to check you are on task. If you don't use the screen-content guard, don't turn it on; without it the guard simply can't see the screen, and guard test mode is unavailable too. The page intro now says it outright as well: all of these are optional, each item explains what it is for, you decide by whether you use that feature, and skipping one never affects the others.")));
        e1212.add(new Group(s("修复", "Fixed"),
                s("守卫测试模式现在能逐帧验收了：以前它沿用正式守卫的「画面没变就跳过」，你停在一个页面不动，第二帧起就只剩一行「画面未变」，再也看不到识别和判定——没法验收的正是这个。现在测试模式每一帧都跑完整分析、刷出新的读数（同一画面重复显示相同判定是正常的）；截图前会先把测试悬浮窗自己藏起来、帧到手再恢复，免得窗里的文字被拍进画面、污染「读到」的内容；窗口一打开先显示「测试模式已开，正在截第一帧…」，让你立刻知道它在工作。正式守卫的采样和跳过逻辑一点没动。",
                  "Guard test mode can now be verified frame by frame: it used to inherit the real guard's \"skip if the frame didn't change\", so if you stayed on one screen, from the second frame on you got a single \"frame unchanged\" line and never saw recognition or verdicts again — the very thing you wanted to check. Test mode now runs the full analysis on every frame and refreshes the readings (seeing the same verdict again on an unchanged screen is expected); it hides its own floating window before each capture and restores it once the frame arrives, so the window's own text no longer lands in the frame and pollutes what it \"reads\"; and the moment the window opens it shows \"Test mode is on, capturing the first frame…\" so you know it's alive. The real guard's sampling and skipping are untouched."),
                s("计时中点「预览悬浮窗」不再闪一下消失：以前计时中点预览，样例内容（预览任务／24:59）会被正在跑的悬浮窗不到一秒覆盖回真实任务，看着就像预览坏了。现在计时中点预览会直接弹一句说明：正在计时，悬浮窗显示的就是当前任务，预览只在没计时时看；没计时时的 10 秒样例预览不变。",
                  "Tapping \"Preview floating window\" while a timer runs no longer flashes and vanishes: before, the sample content (\"preview task / 24:59\") was overwritten by the live floating window in under a second, which looked like the preview was broken. Now it shows a plain message instead — a timer is running, the floating window is already showing the current task, and preview is for when nothing is timing. The 10-second sample preview when idle is unchanged.")));
        e1212.why = s("无障碍是屏幕守卫和守卫测试模式的前提，却没出现在安装后第一页的清单里：新用户照着清单一页开完，守卫还是截不了图，因为最关键的那一项根本没在清单上。这版把它补进首启页，并把「可选、用不上就别开」说清楚，装完一次就能把自己要用的功能配齐。另外两处修的是验收工具自己的毛病：测试模式沿用了正式守卫的省电跳帧，停在一个画面上验收时反而什么都看不到；计时中点预览又被实时悬浮窗瞬间覆盖，看着像坏了——拿来验收的工具先得自己可信，才谈得上验收判定。",
                "Accessibility is a prerequisite for the screen-content guard and its test mode, yet it was missing from the first-run checklist: a new user could work through the whole list and the guard still couldn't screenshot, because the one switch it needs was never on the page. This release adds it to the first-run page and states plainly that it is optional — skip it if you don't use the feature — so a fresh install can be set up in one pass. The other two fixes are the verification tool's own faults: test mode had inherited the real guard's power-saving frame skip, so staying on one screen to check it showed nothing at all; and preview during a running timer was instantly overwritten by the live window, which looked broken. A tool you verify with has to be trustworthy itself first.");
        e1212.tryIt = s("从设置页进「建议设置」：第六项「无障碍截图通道」排在悬浮窗后面。没开时点它，直接跳到系统无障碍设置，找到知行的服务开启，返回本页状态会自动变成「已开启」。全新安装或升级后第一次打开，呈现的也是这同一页，点「以后再说」就能关掉，不拦你用其他功能。再试两处：1）打开守卫测试模式，停在任意一个页面别动——每 10 秒都应刷出一整帧新的读数（时间、读到的文字、判定），不再只剩「画面未变」一行，且「读到」里不该出现测试窗自己的文字；2）随便开始一个任务计时，再去权限体检页点「预览悬浮窗」——应弹出一句文字说明，而不是样例闪一下消失。",
                "Open Settings → Suggested settings: the sixth item, \"Accessibility screenshot channel\", sits right after the overlay item. While it is off, tap it to jump to the system accessibility settings, enable KnowDo's service, and come back — the status refreshes to \"On\" by itself. A fresh install or the first open after an upgrade shows this same page, and \"Later\" dismisses it — nothing blocks the rest of the app. Two more checks: 1) Turn on guard test mode and stay on any screen without touching it — every 10 seconds a full new reading should appear (time, text read, verdict), not a lone \"frame unchanged\" line, and the text it \"reads\" should not contain the test window's own words. 2) Start any task timer, then go to the Permissions page and tap \"Preview floating window\" — you should get a short text message, not a sample that flashes and disappears.");
        out.add(e1212);

        Entry e1211 = new Entry("v1.21.1", "2026-10-25");
        e1211.add(new Group(s("修复", "Fixed"),
                s("结束页没人理时不再永远安静：任务到点、「时间到了」页面弹出后，如果你把手机放一边既不确认完成也不加时，60 秒后它会自己开始催——先震动，无反应再响渐强铃，之后每隔几分钟一轮，直到你回来点任意按钮处置为止（点完成或加时立刻停）。节奏和轮数跟随你的催促档位：严格档无限催、标准档催满轮数就停、温和档不催；每个任务自己的 alarm 模式照样说了算——设了静默就只震不响，设了关闭就完全不催。",
                  "The end page no longer stays silent forever when ignored: after a timer ends and the \"time's up\" page appears, if the phone sits untouched — neither confirmed done nor extended — after 60 seconds it starts nagging on its own: vibrate first, then the fade-in ring if there's still no response, repeating every few minutes until you come back and tap anything (done or extend stops it at once). Pace and rounds follow your nagging profile — strict nags forever, standard stops at its round cap, gentle never nags — and each task's own alarm mode still rules: silent means vibrate-only, off means never."),
                s("切走再回来的任务不再从头倒数：以前暂停一个任务、去做别的、再回来点它的开始，它会从完整时长重新倒计时，之前做过的时间全作废。现在所有开始入口都按今天已经计过的时间抵扣——回来时从剩余时间接着倒数，并提示你还剩多久。已经做满总时长的、标记完成后再开始的、以及你明确「重置」过的，都仍然从完整时长开始；带子任务的任务暂不抵扣（它的进度不只看总时长）。",
                  "A task you return to no longer restarts from zero: before, if you paused a task, went off to do something else, then came back and tapped its start, it counted down from the full length again and the time already spent was thrown away. Now every start entry deducts what you have already timed today — you pick up from the remaining time, with a hint of how much is left. A task already done in full, one started again after being marked done, or one you explicitly reset still starts from the full length; tasks with subtasks are not deducted for now (their progress is more than a total).")));
        e1211.add(new Group(s("改进", "Improved"),
                s("注册时把「确认邮件可能进垃圾箱」说在前面了：注册页上单独一段加粗提醒——知行处于初期测试阶段，确认邮件很有可能被邮箱放进垃圾箱，收件箱里找不到就先去垃圾箱里找；注册提交后的提示里也再带一句短提醒。",
                  "Sign-up now warns up front that the confirmation email may land in spam: a separate bold note on the sign-up form explains that KnowDo is in early testing and the confirmation email is quite likely to be filtered into the spam folder — if it is not in the inbox, look there first. The message shown after submitting sign-up carries a short reminder too.")));
        e1211.why = s("到点后的结束页是个没人管的状态：计时在系统眼里还没结束，空档催促看不见它，页面自己又不会升级提醒——所以你把手机放一边，它就三五分钟、乃至永远安安静静。这一版给结束页补上和空档催促同节奏的升级链，把这个盲区堵上。",
                "The post-deadline end page was a state nobody owned: the timer still counts as running, so the idle nagging can't see it, and the page itself had no escalation — put the phone down and it stayed quiet for three minutes, five minutes, forever. This release gives the end page the same escalation rhythm as the idle nagging and closes that blind spot.");
        e1211.tryIt = s("三处都可以试：1）随便建一个 1 分钟任务开始计时，到点后把手机放一边、不碰它：约 60 秒后应先震动，接着铃声渐强，之后循环催；回到页面点「做完了」或任意加时，催促立刻停。2）开始一个长任务，做几分钟后暂停，去开始另一个任务，再回来开始前一个——应弹出「继续计时（剩余约 …）」，从剩余时间接着倒数。3）退出登录打开注册页，能看到加粗的垃圾箱提醒；注册一个新账号，提交后的提示里也有「还没收到？多半在垃圾箱里」。",
                "Three things to try: 1) Create any 1-minute task and start it; when it ends, put the phone down and don't touch it: after about 60 seconds it should vibrate, then the ring should fade in, and the rounds should keep coming; back on the page, tap \"Done\" or any extension and the nagging stops at once. 2) Start a long task, pause it after a few minutes, start another task, then come back and start the first one — you should see a \"resuming (about … left)\" hint and it counts down from the remaining time. 3) Log out and open the sign-up form to see the bold spam-folder note; sign up a new account and the post-submit message reminds you to check spam too.");
        out.add(e1211);

        Entry e121 = new Entry("v1.21.0", "2026-10-25");
        e121.add(new Group(s("新功能", "New"),
                s("守卫测试模式：打开后每 10 秒截一帧跑一遍完整判定，把读到的文字、判定结论和连续命中张数都写在一个小悬浮窗里——你自己操控屏幕，就能亲眼看它判得对不对。测试模式只显示，永远不会真的弹窗或跳回。",
                  "Guard test mode: when on, it captures a frame every 10 seconds, runs the full verdict pipeline, and shows what it read, what it concluded, and the running hit streak in a small floating window — drive the screen yourself and watch whether it judges right. Test mode only displays; it never pops the inquiry page or jumps you back."),
                s("无障碍截图通道：守卫改用无障碍服务一次性截图，不用再每次计时点一次录屏授权；服务被系统杀掉后会自己回来续上。没开无障碍时自动回退原来的录屏通道。",
                  "An accessibility capture channel: the guard now screenshots through the accessibility service, so there is no per-timer screen-capture grant to tap; if the system kills the service it comes back and resumes on its own. With accessibility off, it falls back to the old capture channel automatically.")));
        e121.add(new Group(s("改进", "Improved"),
                s("采样变密了：从平均一百多秒一张改成随机 15–45 秒、平均 30 秒一张，短暂刷一下也会被逮住；画面没变的那一帧直接跳过不分析，省电。分析顺序改成先便宜后贵——先看是哪个应用、屏幕上现成的文字，判不了才跑看图模型和 OCR。",
                  "Sampling is denser: from one frame every hundred-odd seconds to a random 15–45 seconds, about 30 on average, so a brief drift gets caught; a frame identical to the last is skipped outright to save battery. Analysis is ordered cheap-first — which app it is and the text already on screen first; the image model and OCR only run when those can't decide."),
                s("注册页的验证邮件说明改了：现在会明确告诉你，确认邮件是从 tiguo.net 邮箱发来的（发件人 noreply@tiguo.net）。",
                  "The sign-up note about the verification email now says plainly that it arrives from a tiguo.net address (sender noreply@tiguo.net).")));
        e121.why = s("你问采样能不能像 Truple 那样密，又担心耗电；更要紧的是判定质量一直没法亲眼核——提示从没出现过，到底是没分神还是没逮住，你无从知道。所以先把「看得见」做出来：测试模式就是给你验收判定用的。",
                "You asked whether sampling could be as dense as Truple's, and worried about battery; more importantly, verdict quality could never be checked with your own eyes — when no prompt ever appeared, there was no way to tell \"no drift\" from \"never caught\". So visibility comes first: test mode exists so you can verify the judging yourself.");
        e121.tryIt = s("设置 → 权限体检 → 屏幕内容守卫 → 先点「去开启」把无障碍截图通道打开（在系统无障碍设置里找到「知行屏幕守卫」开启），回来打开「守卫测试模式」；然后随便切换几个应用、刷一会儿手机，看悬浮窗每 10 秒的读数：应用名对不对、读到的文字对不对、判定合不合理。看完点悬浮窗右上角 ✕ 关闭。",
                "Settings → Permissions → Screen-content guard → tap \"Enable\" to turn on the accessibility capture channel (find \"KnowDo screen guard\" in the system accessibility settings), come back and switch on \"Guard test mode\"; then switch between a few apps and browse for a while, watching the floating window update every 10 seconds — is the app name right, is the text it read right, does the verdict make sense? Tap the ✕ at the window's top-right corner to stop.");
        out.add(e121);

        Entry e120 = new Entry("v1.20.0", "2026-10-25");
        e120.add(new Group(s("改进", "Improved"),
                s("日历变简单了：顶部的今日汇总和下面的未排程任务列表都去掉了，日历只剩时间轴本身；「问 AI 重排」保留，改走数据库直接同步，不再绕 Drive。",
                  "The calendar is simple now: the today summary at the top and the unscheduled-task list below are gone — the calendar is just the timeline itself. \"Ask AI to re-plan\" stays, now syncing straight through the database instead of via Drive."),
                s("新建和编辑任务统一成一个弹层：在哪儿建都长一样，时间、重要性、重复和这个任务自己的 alarm 模式（有声、静默、关闭，或跟随全局）一次设好。",
                  "Creating and editing a task now share one sheet that looks the same everywhere: date and start time, importance, repeat, and this task's own alarm mode (sound, silent, off, or follow the global setting) all in one place.")));
        e120.add(new Group(s("新功能", "New"),
                s("点日历空白处就能加任务：开始时间自动填你点的那个点，建好顺手排进日程。",
                  "Tap an empty spot on the calendar to add a task: the start time is filled in with the point you tapped, and the task is planned into the day right away."),
                s("把计划块拖到现在线上面，任务自动变完成；再拖回来，又变回未完成。",
                  "Drag a plan block above the now-line and the task completes itself; drag it back below and it is unfinished again.")));
        e120.add(new Group(s("改进", "Improved"),
                s("新用户默认变温和：不再默认每几分钟催一轮，任务结束也不默认出声；想要严格的，去设置里自己开。已在用的设置原样保留，不受影响。",
                  "New users get gentler defaults: no nagging round every few minutes and no sound when a timer ends, unless you turn them on in Settings. Existing setups are kept exactly as they are."),
                s("注册时要输两遍密码防输错；注册页也会提醒，验证邮件是从 Supabase 发来的，记得去垃圾邮件里找。",
                  "Sign-up now asks for the password twice so a typo can't lock you out, and the sign-up page explains that the verification email comes from Supabase — check your spam folder for it.")));
        e120.why = s("你对着日历截图把三块地方圈了出来，说它们让日历显得乱；点空白建任务是学 Google 日历，少绕一步；"
                        + "默认太猛会吓到第一次用的人，所以狠的只留给自己在设置里开。",
                "You circled three areas on a calendar screenshot and said they make it feel cluttered. Tapping an empty spot "
                        + "to create is borrowed from Google Calendar — one step less. Defaults that hit hard scare first-time users, "
                        + "so the strict behaviour is now something you switch on yourself in Settings.");
        e120.tryIt = s("①打开日程页展开日历，看它是不是只剩时间轴（右上角留了个小小的「问 AI 重排」，点它就走数据库请 AI 重排）；②在日历下午的空白处点一下建个任务；"
                        + "③把一个计划块拖到上午去，再拖回来；④分别从日历和任务列表各建一个任务，对比弹层是不是长一样。",
                "1) Open the day page and expand the calendar — is it just the timeline now? "
                        + "2) Tap an empty afternoon spot on the calendar to create a task. "
                        + "3) Drag a plan block into the morning, then drag it back. "
                        + "4) Create a task from the calendar and one from the task list, and compare the sheets.");
        out.add(e120);

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
