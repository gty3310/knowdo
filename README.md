# KnowDo 知行

**The Unity of Knowledge and Action.**

KnowDo is an Android task manager and focus timer inspired by Wang Yangming's doctrine of the unity of knowledge and action: say what you will do and lay it out clearly (knowing), then actually finish it under the pressure of a countdown (doing). It works as an external brain — an Excel-like task table (task / context tags / deadline or daily repeat / planned duration). Tap ▶ to start a countdown; at half-time and again at the deadline, a full-screen page takes over and asks: can you still finish in time? Did you finish? If not, extend the timer on the spot, or write down how you will finish.

> **Related project — [KnowDo for Mac](https://github.com/gty3310/knowdo-mac):** the macOS companion to KnowDo — a SwiftUI menu-bar and floating-window app that shares the same account, the same task list, and the same single cross-device timer with this Android app. Start a task on your phone and watch it count down on your Mac, or the other way around.

## Vision

KnowDo is built around one job: at any moment, you know exactly what you should be doing — and the moment you drift, it notices and brings you back.

It is designed for an ADHD mind. Long tasks invite drift, and the most dangerous moment is the gap between finishing one task and starting the next. KnowDo attacks both failure points:

- Tasks break into subtasks of about 5–10 minutes each, edited right inside an always-on floating window that counts down the current subtask, not just the whole task.
- When a task ends and the next one hasn't started, KnowDo asks — full-screen — what comes next, how long it will take, and until when. No gap, no drift.
- On the computer, it understands what is on your screen and whether it matches your current task. If you are off-task, it doesn't slam the door: it asks how much longer you want, and then holds you to your own answer.

The long-term goal is a true body-doubling companion, in the spirit of Overlord: aware of your screen, able to talk with you while you work, able to call you when a pop-up is not enough, with real consequences for going off-plan — combined with Motion-style automatic rescheduling and a Sunsama-style daily rhythm of planning the day against your calendar and reviewing it in the evening. One Excel-like task list, on phone and computer, always in sync, so the next action is never in doubt.

## Roadmap

In priority order:

1. **Subtasks in the floating window** — split a task into subtasks, give each its own duration, and keep the current subtask's remaining time front and center.
2. **Between-task guard** — the moment a task ends without a successor, a full-screen prompt asks what is next, for how long, and until when. A phone-call version follows.
3. **Mac screen-context guard** — judge whether what is on screen matches the current task; ask "how much longer?", then close the app when the time you named is up.

Later: real phone calls for task transitions, monetary fines for going off-plan, an all-day conversational body-doubling companion, and Motion-style automatic rescheduling.

## Features (Android, v1.13.0)

Current version: **v1.13.0**.

- **Excel-like task table** — columns you can show or hide, resize, and edit in place: task name, context tags (multi-select, custom tags allowed), deadline (date picker, or daily repeat), planned duration, priority, planned start time, time used / remaining. Tap a row to expand it in place and act on it; a checkbox marks it done; the row currently being timed is highlighted.
- **Focus timer with full-screen check-ins** — a countdown per task, driven by a foreground service that stores an absolute end time, so the countdown does not drift if the process is killed; exact alarms back up the half-time and end points. At half-time and at the deadline, a full-screen page takes over — via full-screen intent from the lock screen, or an overlay path on an unlocked screen — asking whether you can still finish and whether you did. Not done? Add +50% / +5 / +10 / +15 minutes or a custom extension, or write down your plan for finishing.
- **Always-on floating window** — a small overlay (docked tab / mini card / full edit card) that always shows your current task and its remaining time and follows you across apps. Pause from it (with a "remind me in N minutes" menu), jump back to your assistant, or edit the task note in place without opening the app.
- **Focus guard** — each task can carry a focus rule: a natural-language description plus a structured block/allow app list and a primary app. While a timer runs, the foreground app is checked every couple of seconds (via usage access); open something unrelated and a full-screen page asks "are you working on the right task?" — jump back to the task's primary app (or to a built-in current-task page), take a 30/60-second grace period, after which it jumps back for you, or dismiss a false alarm for the rest of that session.
- **Screen-content guard** — for the distractions the focus guard cannot see: shopping or short videos inside an allowed browser, or adult content. Opt-in, and on-device only. While a timer runs on an unlocked screen, screenshots are sampled at random intervals and analyzed locally — ML Kit OCR (Chinese / Japanese / English) plus keyword rules, foreground-app category, and a small on-device image classifier (ONNX). Two consecutive hits (one, for adult content, if you set it so) raise the same inquiry page as the focus guard. Screenshots are analyzed in memory and destroyed immediately: never saved, never uploaded; only the verdict events sync.
- **Calendar panel** — a day view that opens inside the Schedule page beside the task list itself (the bottom tabs stay reachable): three lanes — a narrow Google Calendar lane (view-only reference; scheduling does not work around it), the day's plan lane (widest), and a narrow actual lane; drag tasks straight from the list into the calendar to plan them, long-press plan blocks to drag them to a new time, and an "Ask AI" button that copies a summary of the day for planning in chat.
- **Account and realtime sync (Supabase)** — sign in with an email account and tasks sync in realtime between devices; merges align by task id with newest-wins and tombstone deletes, so nothing resurrects and nothing is silently overwritten. There is a **single logical timer across devices**: state lives in the cloud, both ends render it, and either end can control it. A task written with an `autoStart` flag starts its own countdown as soon as it arrives. A legacy Google Drive file-bridge mode remains available, but Supabase is the primary sync path.
- **Per-task alarms** — any task can have an alarm time (it rings whether or not a timer is running; a daily task re-arms for the next day) and/or ring when its timer ends, before the end-of-task check-in. The default sound is a clear nature recording that fades in from silence to full volume over 60 seconds, played on the alarm audio stream, so it rings even when the phone is muted.
- **Start-time reminders** — when a task's planned start time arrives, a full-screen reminder pops up: start now, snooze 10 minutes, or ignore.
- **Bilingual UI and themes** — Chinese / English interface and light / dark themes, switchable from the drawer or settings. The top bar stays minimal: a drawer (account, language, theme, help, settings) on the left, the calendar on the right.
- **Help page** — the full manual lives inside the app, including the design rationale and an explicit list of things considered and deliberately not built.

The UI is native Java/Views, layered as TaskStore (data) / SyncManager (sync) / TimerService (timing) / UI, so the data format stays stable across implementation changes. The full design research (in Chinese) is in [docs/RESEARCH.zh.md](docs/RESEARCH.zh.md).

## Changelog

The full history is in [CHANGELOG.md](CHANGELOG.md) (Keep a Changelog format; every release commit is tagged, and the version shown in the app carries the build commit's short hash). Latest release:

### v1.21.2 — 2026-10-25

**Why:** Two things. (1) In the user's words: the options presented right after install should include the accessibility switch, that page should explain what each option is for, and anyone who doesn't need a feature shouldn't have to enable its permission. Accessibility is a prerequisite for the screen-content guard and its test mode, yet it only lived on the Permissions page — the first-run "Suggested settings" checklist never mentioned it, so a new user could work through the whole page and the guard still couldn't take a screenshot. (2) Three faults the user hit while verifying guard test mode on a real device: test mode had inherited the real guard's "skip unchanged frames", so staying on one screen showed a single status line from the second frame on and no recognition or verdicts — the very things he wanted to check; the test window itself hung on screen during captures and got photographed into the frame, polluting what the pipeline "read"; and the Permissions page's "Preview floating window", tapped while a timer was running, was overwritten by the live window in under a second and looked broken. Build commit `1277d27` · [compare v1.21.1...v1.21.2](https://github.com/gty3310/knowdo/compare/v1.21.1...v1.21.2) · full notes in [CHANGELOG.md](CHANGELOG.md).

**Improved**
- **The Suggested settings page gains a sixth item: "Accessibility screenshot channel".** It sits right after the overlay item on the page shown once after install or upgrade (and always available in Settings). Its state is read from the same check the Permissions page uses, and tapping it jumps straight to the system accessibility settings, with the page's existing on-resume rebuild refreshing the status when you come back. The item explains itself in the page's usual one-line style: it powers the screen-content guard — while a timer runs with the guard on, it screenshots the screen now and then to check you are on task; if you don't use the screen-content guard, don't turn it on — without it the guard simply can't see the screen and guard test mode is unavailable. The page intro now says the quiet part out loud: all of these are optional, each one powers a feature, decide by whether you use it, and skipping one never affects the others. The Settings entry and the in-app help list were updated from "five" switches to six accordingly. The page stays a suggestion, not a gate — "Later" still dismisses it.

**Fixed**
- **Guard test mode can now be verified frame by frame, and no longer photographs itself.** The test path's own frame-unchanged skip is gone: every 10-second frame runs the full analysis pipeline and refreshes the readings (the same verdict repeated on an unchanged screen is expected). Before each capture the test window hides itself (invisible, position kept) and restores the moment the frame arrives — on error, timeout or interruption too, via a finally path — so its own text no longer lands in the frame and pollutes the "read" text and verdict. And the window opens with "Test mode is on, capturing the first frame…" so you know it's alive before the first reading lands. The real guard's sampling, frame skipping and verdict handling are completely untouched — only the test harness changed.
- **Floating-window preview no longer flashes during a running timer.** Tapping "Preview floating window" while a timer runs now shows a plain message — a timer is running, the floating window is already showing the current task, and preview is for when nothing is timing — instead of building sample content ("preview task / 24:59") that the live window overwrote in under a second. The 10-second sample preview when nothing is timing is unchanged.

No Supabase schema changes, no new permissions, no new pages; the suggested-settings page gains one row. Not yet verified on a real device: the sixth item's state and settings landing spot, the test mode's frame-by-frame refresh while stationary (and that its readings no longer contain the window's own text), and the preview message during a running timer.

## Build

Requires JDK 17 and Android SDK 34 (Gradle 8.7 + AGP 8.5.2):

```sh
./build-gradle.sh
```

The APK lands in `app/build/outputs/apk/release/app-release.apk`. Signing uses a local debug keystore, so new builds install over old ones. `./build-manual.sh` is the retired zero-dependency pipeline from the v1.3.x era, kept for history only; it cannot build current sources.

Cloud sync reads a Supabase **publishable** key at build time from a local, untracked `supabase_public_key.txt` (never committed). Without it, the app still builds and runs with local tasks and the legacy bridge mode.

## License

Licensed under [CC BY-NC-SA 4.0](LICENSE): free to use, modify and build upon, **non-commercial only**, and derivatives must be shared alike and remain free.

Courtesy (not a license condition): please let the author know before you build on it.
