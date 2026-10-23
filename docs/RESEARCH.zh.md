# 知行 KnowDo（任务计时 App）— 研究报告（2026-10-23）

> 定名（2026-10-23）：中文「知行」，取王阳明知行合一——这个 App 是用户的电子大脑 /
> 个人任务管理中枢，目标是帮他把“知”落成“行”；英文「KnowDo」（Know 对应知、Do 对应行，
> 一看就懂），副标用知行合一的官方译法 The Unity of Knowledge and Action。
> 文件存放：Google Drive 根目录「_电子大脑 / KnowDo」文件夹。

需求一句话：一个像 Excel 一样列任务的安卓 App，点 Play 开始倒计时，
倒计时常驻通知栏；过半和结束时必须弹出**全屏页面**强制确认进度。
本报告回答两件事：①整体怎么做（架构）；②全屏通知到底怎么实现、有什么坑。

目标设备：你的 Google Pixel 10 Pro（原生 Android，当前应为 Android 16）。
这点很重要：Pixel 没有国产厂商那种激进杀后台，方案可以完全按 Google 官方规则走。

---

## 1. 结论先行

1、全屏弹出**能做到**，且只有一条正路：通知的 Full-Screen Intent（FSI）。
   Android 10 起禁止应用在后台直接启动 Activity，任何“到点自己跳页面”的土办法都会被系统拦截。
   闹钟、来电用的就是 FSI，我们这个 App 的用法（计时到点提醒）和闹钟完全同类。

2、但 FSI 在 Android 14+ 被收紧：`USE_FULL_SCREEN_INTENT` 变成“特殊应用权限”，
   非电话/闹钟类应用从 Play 商店安装时默认不给，需要引导用户去设置里手动开。
   **好消息**：你和“时习”一样是 APK 直装（不走 Play），安装时默认授予，不受 Play 的自动回收影响；
   但代码仍必须先用 `canUseFullScreenIntent()` 检查，没权限时跳设置页引导开启，
   否则 Android 14+ 上调用会直接崩溃。

3、倒计时不能只靠 App 进程自己数秒。可靠做法是三件套：
   **前台服务**（保活 + 通知栏常驻倒计时）＋ **存绝对结束时间**（进程被杀也能算出还剩多少）
   ＋ **AlarmManager 精确闹钟**（在过半点、结束点各设一个，作为进程死掉时的兜底触发）。

4、架构用 Kotlin + Jetpack Compose + Room + MVVM/单向数据流（Google 官方推荐栈），
   v1 先单模块、按功能分包，接口先行；以后要加统计、同步 Todoist、多设备时再拆模块，
   不用重写。这就是“可扩展”在安卓上最务实的落地方式。

---

## 2. 全屏通知（FSI）实现细节

### 2.1 触发链（过半 / 结束共用同一条链）

```
TimerService（前台服务，计时中）
  → 到达过半点或结束点
  → 发一条高优先级通知，通知挂 FullScreenIntent(PendingIntent → FullScreenCheckActivity)
  → 系统在锁屏/息屏时直接拉起全屏页面；未锁屏时先显示横幅通知，点击/自动展开为全屏
  → 用户在全屏页做选择 → 结果写回数据库 → 页面关闭，服务按结果继续/结束/加时
```

### 2.2 必须的四件套（缺一不可）

1. **权限**：Manifest 声明 `USE_FULL_SCREEN_INTENT`；
   Android 14+ 运行时用 `NotificationManager.canUseFullScreenIntent()` 检查，
   为 false 时用 `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` 把用户送到
   “设置 > 特殊应用权限 > 全屏通知”页面开启。
2. **通知渠道**：单独建一个 `IMPORTANCE_HIGH` 渠道（如 `timer_alerts`），
   通知设 `setCategory(CATEGORY_ALARM)`、`setPriority(PRIORITY_MAX)`、
   `setVisibility(VISIBILITY_PUBLIC)`（锁屏可见）。
3. **全屏 Intent 本身**：
   `NotificationCompat.Builder.setFullScreenIntent(pendingIntent, true)`，
   第二个参数 true 表示即使亮屏也要全屏（false 则亮屏时只给横幅）。
   你的需求是“一定要全屏”，所以用 true。
4. **目标 Activity 的锁屏属性**：全屏页 Activity 在 Manifest 里声明
   `android:showWhenLocked="true"` + `android:turnScreenOn="true"`，
   这样息屏时能点亮屏幕、压在锁屏之上显示（不需要先解锁）。

### 2.3 「弹专用全屏页」还是「把 App 切到前台」？（2026-10-23 晚讨论补记）

结论：用 FSI 弹一个**专用检查页**，不把整个 App 切前台。理由：

1. 这两个效果底层是同一个机制。Android 10+ 禁止后台直接启动 Activity，
   所谓“自动切前台”没有第二条正路，也得靠 FSI 的 PendingIntent——权限和条件完全一样，
   换个目标并不能绕开任何限制。
2. FSI 的 Intent 指向哪个页面是我们可以选的。指向检查页：屏幕亮起后直接就是
   问题 + 两个大按钮，不用解锁、不用在任务列表里找，点完页面自动关闭、回到他正在做的事。
   指向主界面：要先过任务列表一层，还可能打乱他原来的浏览位置，点错风险也高。
3. 唯一需要回主界面的场景（结束页点“做完了，做下一个任务”）可以由检查页按钮
   在用户点击后顺势打开任务列表——此时 App 已在前台交互中，跳转完全合法。
4. 旁门方案对比：悬浮窗（SYSTEM_ALERT_WINDOW）已被系统和 Play 持续收紧、耗电且易被杀；
   无障碍服务模拟操作更脆弱。都不如 FSI 正路。
5. 仍未实测的不确定性只有一个：你 Pixel 的 Android 16 上 FSI 的实际弹出表现，
   这正是最小验证版要息屏实测的原因。实测不过的保底是“响铃+振动+横幅通知，
   点一下进检查页”，只多一步点击，不会静默失败。

### 2.4 亮屏时 FSI 只给横幅，怎么办（2026-10-23 晚真机实测后补记）

真机实测结论：熄屏/锁屏时 FSI 完美（自动点亮屏幕、全屏页弹出，与设计一致）。
但**亮屏未锁**时，系统按其设计只显示横幅通知（heads-up），不会自动拉起全屏页——
这是 FSI 的既定行为，不是实现错误。用户明确不接受亮屏走通知。

解法（即同类自律/闹钟 App 的通行做法，且有官方依据）：Android 官方
《后台启动 Activity 限制》页面的豁免清单明确列有：
“应用已被用户授予 SYSTEM_ALERT_WINDOW（在其他应用上层显示）权限”时，
可以从后台直接启动 Activity。因此 v0.1.3 起改为双路径、同一个 CheckActivity：
• 亮屏未锁 + 已授予上层显示 → 计时服务直接 startActivity 弹全屏页，不经任何通知；
• 熄屏/锁屏 → 继续沿用已验证通过的 FSI 路径；
• 权限未授予 → 退回 FSI（亮屏降级横幅），权限体检页新增第五项负责引导开启。
两条路渲染的是同一个页面类，布局与按钮天然完全一致，满足“两种状态页面一模一样”的要求。

来源：https://developer.android.com/guide/components/activities/background-starts
（Exceptions 一节：The app has the SYSTEM_ALERT_WINDOW permission granted by the user）

### 2.5 两个容易踩的坑

1. **从后台启动前台服务受限**（Android 12+）：但我们是用户在前台亲手点 Play 启动服务，
   属于合法起点；过半/结束时服务已在运行，发 FSI 通知不受此限制。合规。
2. **精确时间要精确闹钟权限**：Android 14 起 `SCHEDULE_EXACT_ALARM` 新安装默认不给，
   需要引导用户去“闹钟和提醒”设置里开。没有它，兜底触发会退化为不精确（可能迟几分钟），
   所以首次启动要做一个**权限体检页**：逐项列出
   通知权限 / 全屏通知 / 精确闹钟 / 电池优化豁免，每项显示已开/未开并可一键跳转开启。
   这是这类 App 可靠性的最大决定因素，比任何代码技巧都重要。

### 2.6 降级方案

万一 FSI 权限被用户关掉：系统会自动把全屏通知降级为锁屏上的展开横幅（不会丢），
同时我们加振动 + 闹钟音效（`USAGE_ALARM` 音频通道，能穿透静音模式的勿扰设置需另授权，
v1 先做振动+响铃）。即“最坏情况也有响铃横幅”，不会静默失败。

---

## 3. 倒计时怎么做才准、才不死

1. **不存“还剩 X 秒”，存“结束时刻”**：开始时算出 `endAt = 当前时间 + duration`，
   连同 `halfWarned`（过半是否已提醒）、`extendedMinutes`（累计加时）一起存进 Room。
   界面和通知的剩余时间永远用 `endAt - now` 现算。
   这样进程被杀、手机重启（开机广播恢复）、改倍速之类的事都不影响准确性。
   这和“时习”v1.8.6 把提前量改为运行时现算是同一个思想：存原值、现算现用。

2. **通知栏倒计时**：前台服务的常驻通知用
   `setUsesChronometer(true) + setChronometerCountDown(true) + setWhen(endAt)`，
   系统自己每秒刷新显示，不用 App 每秒发通知（省电且不卡）。
   通知上带“暂停/结束”按钮，用户不打开 App 也能操作。

3. **双保险触发**：服务内协程计时是主触发；同时用 AlarmManager 在过半点、结束点
   各设一个精确闹钟（`setExactAndAllowWhileIdle`，能穿透 Doze 打盹模式）作兜底。
   两边谁先到都走同一段处理逻辑，处理完把另一个取消，保证只弹一次。

4. **前台服务类型**：Android 14+ 必须声明类型。计时器没有专属类型，
   用 `specialUse`（权限 `FOREGROUND_SERVICE_SPECIAL_USE`，
   并在 Manifest 里写明用途说明）。这是番茄钟类 App 的通行做法。

---

## 4. 可扩展架构

### 4.1 技术栈（全部 Google 官方推荐，无冷门依赖）

• 语言/UI：Kotlin + Jetpack Compose，单 Activity
• 架构：MVVM + 单向数据流（状态向下、事件向上），三层：UI / Domain / Data
• 数据库：Room（本地唯一数据源）+ DataStore（设置项）
• 异步：Coroutines + Flow
• 依赖注入：Hilt（模块多了以后不乱；v1 也可先手动注入，接口不变）

注：和“时习”的零依赖纯框架路线不同——时习是播放器、逻辑自闭环；
这个 App 是数据驱动的表格 + 后台计时 + 以后要长出统计和同步，
用官方栈的长期维护成本最低。

### 4.2 分包（v1 单模块，按功能分包，日后可直接拆成多模块）

```
feature/tasks/     任务列表（表格）、编辑、情景筛选
feature/timer/     计时界面、全屏检查页（过半/结束）、TimerService
core/data/         Room：Task / ScenarioTag / TaskTag / TimerSession
core/domain/       用例：StartTimer、ExtendTimer、CompleteTask、GenerateDailyRepeats
core/ui/           通用组件（标签多选器、日期选择器等）
core/notify/       通知渠道、FSI 封装、权限体检
```

可扩展点都留成接口：`TaskRepository`（以后接 Todoist 同步只换实现）、
`AlertDispatcher`（以后加手表/语音提醒只加实现）、
`TimerSession` 全量落库（以后做“预估 vs 实际”日报、周报不用补数据）。

### 4.3 数据模型

• **Task**：id、标题、deadline 日期（可空）、repeat 模式（NONE / DAILY）、
  durationMinutes、状态（待办/已完成）、创建时间、排序
• **ScenarioTag**：id、名称（如“有网”“无网”“跟 AI 聊天”）——独立成表，
  才能做到多选 + 随时新增 + 按情景筛选
• **TaskTagCrossRef**：task 与 tag 多对多关联表
• **TimerSession**：id、taskId、计划分钟数、开始时刻、过半检查结果（能做完/做不完+用户写的办法）、
  加时记录、实际结束时刻、完成状态——这是以后日报和复盘的原料，v1 就存好

Repeat 的实现：不是到点改日期，而是每天生成当天实例（打开 App / 开机时检查补生成），
原任务作为模板保留。这样历史记录不会被覆盖。

### 4.4 表格 UI 怎么做“像 Excel”

Compose 的 LazyColumn 做行，每行就是一条任务，单元格点击即编辑：
第 1 列标题（点击改字）、第 2 列情景（点击弹出多选面板，底部有“+ 新增情景”输入框）、
第 3 列 deadline（点击弹日期选择器，顶部有“日期 / 每日重复”切换）、
第 4 列 duration（点击弹数字输入，单位分钟）。
每行最左边固定一个 ▶ 按钮。另加顶部情景筛选条：点“无网”只看无网能做的任务——
这是你列情景的真正用途，顺手在 v1 就做。

---

## 5. 计时状态机（核心流程）

```
待机 ──点▶──▶ 计时中（通知栏倒计时 + 两个精确闹钟已设）
计时中 ──到过半点──▶ 全屏页 A：“时间过了一半，剩下的时间还够你做完吗？”
   ├─ 点“能做完” → 关页，继续计时（过半点不再重复提醒）
   └─ 点“做不完” → 页内追问：“我如何做才能做完？”（输入框，用户写下办法，存入 Session）
                    → 点“想好了，继续”关页，继续计时
计时中 ──到结束点──▶ 全屏页 B（响铃+振动）：“时间到了，做完了吗？”
   ├─ 点“做完了，开展下一个任务” → Session 记为完成，任务标完成，
   │    停服务，回任务列表
   └─ 没做完 → 页内加时区：+50% / +5 分钟 / +10 分钟 / +15 分钟 / 自定义输入
        → endAt 顺延，重新设结束闹钟，回计时中（过半提醒不再触发）
任意时刻：通知栏按钮可暂停/放弃，放弃也要选“已完成/未完成”才记账
```

待你拍板的 2 个细节（我的建议已写明，回复确认或改即可）：
1. 全屏页 A 弹出时，倒计时**继续走**（建议：继续，时间压力本来就是目的；
   若暂停，人会借检查页拖延）。选“做不完”写办法时也继续走。
2. 全屏页 B 里你原话“我们开展下”我按“我们看一下，时间到了，做完了吗？”理解。

---

## 6. v0.1 范围

做：任务表格（四列全可编辑、情景多选+新增、日期/每日重复、分钟数）、
单任务计时、通知栏倒计时、过半全屏页、结束全屏页、加时、权限体检页、
Session 全量落库。
不做（留接口）：Todoist 同步、统计报表、多任务同时计时、云端备份、桌面小部件。

工作量估计：原型可跑通全流程约 1–2 天开发 + 在你 Pixel 上实测全屏弹出；
最大不确定性不在代码，在 Android 16 对 FSI 的实际表现——所以第一步应先做一个
**最小验证版**：只有一个按钮、一个 1 分钟计时、到点全屏弹页，
在你手机上息屏实测通过，再往上盖表格和完整流程。

---

## 7. 风险清单

1. FSI 权限被关 → 降级为响铃横幅（见 2.6），权限体检页负责提前发现。
2. 精确闹钟没授权 → 兜底触发不准，但服务内计时仍在（服务活着时主触发不受影响）。
3. 电池优化/Doze → 前台服务 + 精确闹钟的组合就是官方给的穿透方案；Pixel 原生系统下风险低。
4. 以后若上架 Play：FSI 和 specialUse 前台服务都要在 Play Console 做声明，
   以“闹钟/计时”为核心功能申报。自用直装则完全不涉及。
5. Android 15/16 对后台启动前台服务继续收紧，但本 App 全程由用户前台操作发起，不受影响。

---

## 8. 来源（官方为主）

• Android Developers — Create a notification（setFullScreenIntent 官方示例）
  https://developer.android.com/develop/ui/compose/notifications/create-notification
• AOSP — Full-screen intent limits（Android 14+ 权限收紧与检查 API）
  http://source.android.com/docs/core/permissions/fsi-limits
• Google Play Help — Understanding foreground service and full-screen intent requirements
  https://support.google.com/googleplay/android-developer/answer/13392821
• Android Developers — Foreground service types（specialUse 声明方式）
  https://developer.android.com/develop/background-work/services/fgs/service-types
• Android Developers — AlarmManager API（canScheduleExactAlarms / 精确闹钟权限）
  https://developer.android.com/reference/kotlin/android/app/AlarmManager
