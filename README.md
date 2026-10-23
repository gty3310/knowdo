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

当前版本 **v1.2.0**（验证版 v0.1.x 的全屏机制已全部并入）：

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
* **计时悬浮窗**（v1.2.0，借鉴 Floating Notes 一类悬浮笔记应用）：计时开始后在任何界面之上
  显示一张可拖动的深色卡片——任务名、大字倒计时、任务笔记；可收起成只显示时间的小药丸；
  点「笔记」可直接在悬浮窗里写并保存（笔记是任务字段 `note`，随桥同步）；
  点「完成」直达结束检查页；全屏检查页弹出时悬浮窗自动让位、检查页关闭后自动回来。
  权限页可开关此功能（依赖“上层显示”授权）。
* **实时同步与自动开始**（v1.1.0）：前台同步服务每 30 秒轮询桥文件（低打扰常驻通知，
  主界面可开关，开机自启），桥端变化即自动合并、界面自动刷新，无需手动同步；
  桥协议含 `autoStart` 标记——外部（如 Muse）写入带标记的任务，App 在下一轮轮询
  （≤30 秒）即自动开始其倒计时（已有计时在跑时排队等空闲），启动后回写清除标记防重复。
* 倒计时由前台服务（specialUse 类型）驱动，只存绝对结束时间，进程被杀不漂移；
  AlarmManager 精确闹钟在过半点/结束点兜底。权限体检独立成页（五项逐项可跳转开启）。

完整设计与调研见 [docs/RESEARCH.zh.md](docs/RESEARCH.zh.md)。

实现说明：为在无 Gradle 守护进程的环境中可复现构建，v1 仍用零依赖的原生 Java/Views 实现，
但分层与研究报告一致（TaskStore 数据层 / SyncManager 同步层 / TimerService 计时层 / 界面层），
日后迁移 Kotlin + Compose + Room 时只换实现、不动数据格式。

v1.0.0: spreadsheet-like task table (editable columns, scenario multi-select,
per-task timer), the verified dual-path full-screen check-in pages, and two-way sync
with a Google Drive `tasks.json` bridge via the Storage Access Framework.

## 构建 / Build

```sh
./build-manual.sh   # 需要 JDK 17 与 Android SDK platform 34 / build-tools 34.0.0
```

产物为 debug 签名的可安装 APK（`build/manual/task-timer.apk`）。

## 许可 / License

[CC BY-NC-SA 4.0](LICENSE)（署名—非商业性使用—相同方式共享）。
任何人可以自由使用、修改和再开发，但**不得用于商业用途**，衍生版本必须以相同许可免费开放。

礼仪约定（非许可强制）：动手继续开发前，请先知会原作者一声。

Licensed under CC BY-NC-SA 4.0: free to use, modify and build upon,
**non-commercial only**, derivatives must be shared alike and remain free.
As a courtesy (not a license condition), please let the author know before you build on it.
