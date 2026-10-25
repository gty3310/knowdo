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

### v1.21.3 — 2026-10-25

**Why:** Two decisions the user made on the spot while verifying and using v1.21.2. (1) Guard test mode almost always said "not distracted" — not because judging was lax, but because with no timer running there was no task to compare the screen against, and a verdict is exactly that comparison. In the user's own example — the task is "reply to Xiaoshiting on WeChat" while he replies to someone else — whether that is drift depends entirely on which task is the ruler, so test mode now picks its ruler before it starts. (2) The grace granted on the inquiry page used to expire silently and only bite if a later sample caught the same category again; the user approved closing that loop — choose at grace time what happens at expiry (quit the app by default, or background only), and have the guard check and act on time instead of waiting for another hit. A framing note reversed along the way: the frame-skip taken out of test mode in v1.21.2 for verification was re-decided by the user ("skip what should be skipped"), so it is back — with the previous frame's verdict kept on screen. Build commit `e1ebfba` · [compare v1.21.2...v1.21.3](https://github.com/gty3310/knowdo/compare/v1.21.2...v1.21.3) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Guard test mode picks a ruler task first.** Turning test mode on now opens a picker (GuardTestPickActivity): choose an existing task (the store's canonical ordering — unfinished, must-do and nearest-deadline first, daily tasks counting as today), create a temporary one (title plus optional note, kept only in preferences with a sentinel id and assembled in memory — never written to the task store, the cloud, or the task list), or pick none and keep the old behavior. Verdict comparison priority is: picked task > currently timing task > none; the test window gains a "ruler:" line (temporary picks are marked, and "none" says so). The pick is cleared when test mode stops — it lives only for the session — while automatic resumes (accessibility-service rebind, returning to the Permissions page) reuse the stored pick instead of popping the picker, so both entry paths judge identically.
- **Grace expiry is enforced proactively, the way you chose.** The content guard's inquiry page gains a two-way choice above the grace buttons — "Quit that app" (default) or "Background only" — persisted so the next inquiry defaults to your last choice; the app-level guard's inquiry is unchanged. When an inquiry triggers, the engine records the foreground package; taking grace stores it with the grace and GraceEnforcer sets an exact alarm for the expiry (boot-rescheduled from persisted state). At expiry — or if a post-expiry sample hits the same category first — one shared path, VerdictEngine.applyEnforcement, clears the grace, re-checks the foreground, and only acts if you are still in the app that triggered the inquiry: "quit" presses HOME via the accessibility service to background that app and then jumps back to the task's app, "background only" just presses HOME. Android cannot kill another app, so "quit" honestly means backgrounded, not force-stopped. Every enforcement is logged to the guard events and the task log, naming the mode; if you have already left the app, nothing happens and that is logged too. Inquiry triggers, category judging, false-positive release and cooldowns are untouched, and test mode still never enforces anything.

**Improved**
- **Test mode skips unchanged frames again — with the previous verdict kept.** The test path's own perceptual-hash skip is restored (the production guard's two sampling channels were never touched): an unchanged frame updates only the time/app/status line — "frame unchanged, skipped" — and re-displays the previous frame's stored verdict body (verdict / evidence / streak) under a "previous frame's verdict" marker, instead of reducing the window to a single status line. Skipped frames do not advance the display streak.

No Supabase schema changes and no new permissions (the grace alarm reuses the exact-alarm capability the app already declares); two new screens at most — a task picker for test mode and one radio row on the content-guard inquiry page. Not yet verified on a real device: the picker's three paths and switch state echo, distraction verdicts with a ruler set (including inside WeChat), the skip line with the retained verdict, grace enforcement at +30s in both modes (and the no-op when the app was already left), and boot rescheduling of a pending grace.

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
