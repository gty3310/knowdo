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

当前仓库为 **v0.1.3 全屏验证版**：验证核心机制——倒计时结束/过半时，
在熄屏与亮屏两种状态下都能弹出同一个全屏检查页。

* 熄屏/锁屏：Full-Screen Intent 通知拉起全屏页（已在 Pixel 10 Pro / Android 16 真机验证）。
* 亮屏未锁：FSI 按系统设计只给横幅，故改走官方后台启动豁免路线——
  用户授予“在其他应用上层显示”（SYSTEM_ALERT_WINDOW）后，计时服务直接启动同一个全屏页，不经通知。
* 倒计时由前台服务（specialUse 类型）驱动，只存绝对结束时间，进程被杀不漂移；
  AlarmManager 精确闹钟在过半点/结束点兜底。
* 首页有五项权限体检（通知 / 全屏通知 / 精确闹钟 / 电池优化豁免 / 上层显示），逐项可跳转开启。

完整设计与调研见 [docs/RESEARCH.zh.md](docs/RESEARCH.zh.md)。
正式版（任务表格、情景多选、统计）按该报告的 Kotlin + Compose + Room 可扩展架构开发中。

This repo currently holds the v0.1.3 full-screen verification build (plain Android/Java,
zero dependencies, built with the included `build-manual.sh` against the Android SDK).
The production version follows the scalable architecture in the research doc.

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
