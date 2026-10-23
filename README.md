# 知行 KnowDo

**The Unity of Knowledge and Action.**

知行 KnowDo 是一个安卓任务管理与计时应用，源于王阳明“知行合一”：
把要做的事说出来、列清楚（知），然后在时间的约束下真正做完（行）。
它相当于使用者的“电子大脑”：Excel 式任务表（任务 / 情景标签 / 截止日期或每日重复 / 预估时长），
点 ▶ 开始倒计时；时间过半和结束时，会强制弹出一个全屏页面问你：
“剩下的时间还够吗？”“做完了吗？”——没做完，可以当场加时或写下办法。

KnowDo is an Android task manager + focus timer inspired by Wang Yangming's doctrine of
the unity of knowledge and action. Lay out your tasks in a spreadsheet-like table
(task / context tags / deadline or daily repeat / planned duration), tap ▶ to start a
countdown. At half-time and at the deadline, a full-screen page takes over the screen —
even from the lock screen — and asks whether you can still finish, and whether you did.
If not, extend the timer or write down how you will finish.

## 当前状态 / Status

当前版本 **v1.4.0**（验证版 v0.1.x 的全屏机制已全部并入）：

* **Excel 式任务表**：每行四列可直接点按编辑——任务名（可设“必要”★）、
  情景（多选面板，可自由新增标签）、截止（日期选择，或勾选“每日重复”）、
  时长（分钟）；每行左侧 ▶ 开始该任务的计时；长按一行可标记完成或删除。
* **全屏检查页**（过半 / 结束同一个页面，熄屏与亮屏一致）：
  熄屏/锁屏走 Full-Screen Intent 拉起；亮屏未锁时 FSI 按系统设计只给横幅，
  故走官方后台启动豁免路线——用户授予“在其他应用上层显示”（SYSTEM_ALERT_WINDOW）后，
  计时服务直接启动全屏页，不经通知（两条路径均已在 Pixel 10 Pro / Android 16 真机验证前者、后者待复测）。
  过半页问“还够做完吗”，做不完可写下办法；结束页点“做完了”会回写任务完成状态，
  没做完可 +50% / +5 / +10 / +15 分钟或自定义加时。
* **与 Google Drive 同步（当作“桥”）**：任务清单以 tasks.json（schema 见 `sync/tasks.json`）
  存放在使用者的 Drive「_电子大脑 / task list」文件夹；App 通过系统文件选择器（SAF）
  一次授权后即可双向同步——在 App 里增改，或由 Muse 直接改桥文件，另一端同步即达。
  合并规则：按任务 id 对齐、updatedAtMs 新者胜、删除走墓碑标记防复活。
  计时记录只存本地，不进桥文件。
* **专注守卫**（v1.3.0，轻量版）：每个任务可设一列专注规则——自然语言说明 + 结构化名单
  （黑名单拦 / 白名单只许）+ 主软件。计时中每 2 秒以使用情况访问（UsageStats）检测前台应用，
  打开无关软件即弹全屏问询页「你是在做正确的任务吗？」，可选：立即跳回 /
  宽限 30 秒 / 宽限 60 秒（到期仍在用就自动跳回）/ 误判放行（本次计时内不再拦）。
  “跳回”去向：任务指定了主软件就跳它（如读经典跳时习），没指定就跳知行内置的「当前任务页」
  （极简页：任务名 + 倒计时 + 笔记区 + 「回到任务列表」「回到 Muse」两个按钮）。
  架构为强力版留了扩展点：检测层是 `ForegroundDetector` 接口（现为 UsageStats 轮询实现，
  强力版可换无障碍事件实现），执行层是 `GuardActions`（现为跳转实现，强力版可加
  模拟 Home/返回与全屏遮罩），状态机 `FocusGuard` 不变。
* **计时悬浮窗**（v1.2.0 引入，v1.3.4 起按 Floating Notes 交互重做）：三种模式——
  「吸附」贴屏幕侧边的小标签，只显示剩余分钟数，点它展开、沿边缘拖动换高度；
  「小展开」半透明小窗，任务名 + 倒计时 + 一个浅灰单色字符「✎」，点内容区或 ✎ 直接进编辑并弹键盘，
  拖到屏幕左右边缘松手自动吸附；「编辑」更不透明的完整卡片，笔记就是输入框本身，
  点笔记区直接打字、停笔自动保存（笔记是任务字段 `note`，随桥同步）。
  点「完成」直达结束检查页；全屏检查页弹出时悬浮窗自动让位、检查页关闭后自动回来。
  权限页可开关与预览此功能（依赖“上层显示”授权）。任务若有笔记，主列表任务名旁有小三角可展开查看。
* **屏幕内容守卫**（v1.4.0）：专注守卫看不见的分神（在允许用的浏览器里购物、刷短视频网页版、
  看黄色内容）由它补位。仅计时中 + 亮屏解锁时工作，随机每 45–170 秒截一张图
  （开始计时时经系统授权一次）；分析全在端侧：NudeNet 小模型（ONNX）看图抓黄色/暴露内容
  （按作者定义，穿得少也算）→ 前台包名类别 → ML Kit OCR（中/日/英）+ 关键词规则判与任务是否相关。
  同类连续命中 2 张才弹与专注守卫同一张问询页（黄色类可设一张即触发）。
  隐私红线：截图只在内存分析、判完即销毁，不落盘、不上云；同步进桥的只有
  顶层 `guardEvents` 事件记录（时间/类别/置信度/证据文字，上限 200 条）。
* **帮助页**（v1.4.0）：主界面「帮助」入口，把全部功能用法、设计理念与
  “提到过但暂不做 / 决定不做”的清单（Firebase 秒级推送、云端判图、强力版无障碍守卫、
  端侧大模型深判等）都写在 App 里，随时可翻。
* **实时同步与自动开始**（v1.1.0）：前台同步服务每 30 秒轮询桥文件（低打扰常驻通知，
  主界面可开关，开机自启），桥端变化即自动合并、界面自动刷新，无需手动同步；
  桥协议含 `autoStart` 标记——外部（如 Muse）写入带标记的任务，App 在下一轮轮询
  （≤30 秒）即自动开始其倒计时（已有计时在跑时排队等空闲），启动后回写清除标记防重复。
* 倒计时由前台服务（specialUse 类型）驱动，只存绝对结束时间，进程被杀不漂移；
  AlarmManager 精确闹钟在过半点/结束点兜底。权限体检独立成页（六项逐项可跳转开启，
  另有 Muse 应用选择、悬浮窗与屏幕内容守卫设置）。

完整设计与调研见 [docs/RESEARCH.zh.md](docs/RESEARCH.zh.md)；
屏幕内容守卫的设计与缺陷清单见仓库外文档（作者 your_files/知行-屏幕内容守卫-设计.md）。

实现说明：界面仍为原生 Java/Views，分层与研究报告一致（TaskStore 数据层 /
SyncManager 同步层 / TimerService 计时层 / 界面层），日后迁移 Kotlin + Compose + Room 时
只换实现、不动数据格式。构建自 v1.4.0 起改用 Gradle（因 ML Kit / ONNX Runtime 依赖），
见下方构建一节；签名仍用同一把 debug.keystore，可覆盖安装旧版。

v1.0.0: spreadsheet-like task table (editable columns, scenario multi-select,
per-task timer), the verified dual-path full-screen check-in pages, and two-way sync
with a Google Drive `tasks.json` bridge via the Storage Access Framework.

## 构建 / Build

```sh
./build-gradle.sh   # v1.4.0 起的主构建：Gradle 8.7 + AGP 8.5.2，需要 JDK 17 与 Android SDK 34
```

产物 APK 在 `app/build/outputs/apk/release/app-release.apk`，脚本会顺手拷一份到
`~/workspace/your_files/task-timer-probe.apk`。签名配置在 `app/build.gradle`，
指向 `~/workspace/tools/debug.keystore`（与 v1.3.x 手工管线同一把，可覆盖安装）。

`./build-manual.sh` 是 v1.3.x 时代的零依赖手工管线（aapt2/d8 直连），仅作历史备用：
v1.4.0 的源码用了 ML Kit / ONNX Runtime，手工管线编不了。本机沙箱里跑 Gradle 的
若干适配（进程内构建、代理与证书处理）都写在 `build-gradle.sh` 注释里。

## 许可 / License

[CC BY-NC-SA 4.0](LICENSE)（署名—非商业性使用—相同方式共享）。
任何人可以自由使用、修改和再开发，但**不得用于商业用途**，衍生版本必须以相同许可免费开放。

礼仪约定（非许可强制）：动手继续开发前，请先知会原作者一声。

Licensed under CC BY-NC-SA 4.0: free to use, modify and build upon,
**non-commercial only**, derivatives must be shared alike and remain free.
As a courtesy (not a license condition), please let the author know before you build on it.
