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
