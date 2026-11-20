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

1. **Subtasks in the floating window** — shipped (v1.11.0): split a task into subtasks, give each its own duration, and keep the current subtask's remaining time front and center.
2. **Between-task guard** — shipped (v1.10.0): the moment a task ends without a successor, a full-screen prompt asks what is next, for how long, and until when.
3. **Mac screen-context guard** — the Mac companion has shipped (same account, task list and cross-device timer); judging the Mac's screen against the current task is still to come. On the phone, the screen-content guard below already does this job.

Later: real phone calls for task transitions, monetary fines for going off-plan, an all-day conversational body-doubling companion, and Motion-style automatic rescheduling.

## Features (Android, v1.89.0)

Current version: **v1.89.0**.

- **Excel-like task table** — columns you can show or hide, resize, and edit in place: task name, context tags (multi-select, custom tags allowed), deadline (date picker, or daily repeat), planned duration, priority, planned start time, time used / remaining. Tap a row to expand it in place and act on it; a checkbox marks it done; the row currently being timed is highlighted.
- **Focus timer with full-screen check-ins** — a countdown per task, driven by a foreground service that stores an absolute end time, so the countdown does not drift if the process is killed; exact alarms back up the half-time and end points. At half-time and at the deadline, a full-screen page takes over — via full-screen intent from the lock screen, or an overlay path on an unlocked screen — asking whether you can still finish and whether you did. Not done? Add +50% / +5 / +10 / +15 minutes or a custom extension, or write down your plan for finishing.
- **Always-on floating window** — a small overlay (docked tab / mini card / full edit card) that always shows your current task and its remaining time and follows you across apps. Pause from it (with a "remind me in N minutes" menu), jump back to your assistant, or edit the task note in place without opening the app.
- **Focus guard** — each task can carry a focus rule: a natural-language description plus a structured block/allow app list and a primary app. While a timer runs, the foreground app is checked every few seconds (via usage access, with a faster recheck whenever the window changes); open something unrelated and a full-screen page asks "are you working on the right task?" — return to the task at once, switch to another task, or take a grace: block the app now, or keep it for 1 / 3 / 5 more minutes, after which the disposition you picked lands — quit the app, send it to the background, or block it behind a 3-minute countdown page with no early exit on the page itself. Three difficulty levels (Settings → Guards, since v1.83.0): Easy is the classic flow; Medium shows the false-alarm button only on the day's first inquiry for that task and gates every grace behind a breathing pause that doubles with each use that day (15s, capped at 120s; leaving the breathing page voids the grace); Hard replaces the false-alarm button with a written explanation judged by an AI — only an "agree" verdict lets you off — and rations grace to a small daily quota. Every grace choice records one honest drift event for the Zhixing index.
- **Per-task app lists** — for each task, your installed apps are sorted into white / grey / black tiers: whitelisted apps no longer trigger focus-rule inquiries during that task, opening a blacklisted app raises the inquiry immediately (the page names the list it came from), and grey adds no new interruptions. Until a task's list is ready, a built-in generic blacklist (short video, social, shopping and game apps) stands in. Open "App list" in a task's details to see the tiers and re-tier any app — hand edits are marked as yours and never overwritten by later generations. Since v1.84.0 the lists live only on the phone (a local file, never synced to the cloud); a fresh list is requested through a one-slot cloud mailbox and filed locally when it arrives. The master switch is Settings → Judging & lists → "Task app lists"; with it off, no requests are sent and no list-based inquiries fire. (Automatic classification currently serves the author's own account only — see the section for new users below.)
- **Screen-content guard** — for the distractions the focus guard cannot see: shopping or short videos inside an allowed browser, or adult content. Opt-in, and on-device only. While a timer runs on an unlocked screen, screenshots are sampled at random intervals and screened on-device — ML Kit OCR (Chinese / Japanese / English) plus keyword rules, foreground-app category, a small on-device image classifier (ONNX), and a local semantic model that scores the screen text against the current task. Clear passes and clear violations settle locally; the uncertain middle band escalates to a cloud judge (tried first whenever the phone is online, since v1.69.0), and only extracted text features ever leave the phone. Two consecutive hits (one, for adult content, if you set it so) raise the same inquiry page as the focus guard. Screenshots are analyzed in memory and destroyed immediately: never saved, never uploaded; only the verdict events sync. Screenshots come only from the accessibility channel (v1.26.0: the screen-recording channel was removed entirely — no recording permission is ever requested; with accessibility off, the guard simply does not sample).
- **Calendar panel** — a day view that opens inside the Schedule page beside the task list itself (the bottom tabs stay reachable): three lanes — a narrow Google Calendar lane (view-only reference; scheduling does not work around it), the day's plan lane (widest), and a narrow actual lane; drag tasks straight from the list into the calendar to plan them, long-press plan blocks to drag them to a new time, an "AI Re-plan" button that has the built-in AI draft a new plan for the rest of the day on the spot — it only lands when you tap Apply — and, since v1.27.0, a "Quick Re-plan" button beside it that re-plans instantly with a fixed algorithmic order (short due-and-important tasks first, then Must, due Important, due Secondary, not-due Important, not-due Secondary; Leisure never), leaving the running task, done blocks and hand-dragged blocks untouched — and, since v1.27.1, recycling an unfinished task's expired block so the task is re-planned from now instead of being stranded in the past — with a one-tap Undo. Both planners and the automatic gap-filling share one ordering implementation, and the AI prompt now also receives that same rule text, today's Google Calendar events (reference only) and what has already been done today. Blocks read in a fixed order: the task name first (up to two lines), then its priority marker — colour and shape only since v1.26.2 (solid vermilion for Must, an ink outline for Important, a small grey chip for Secondary, a dashed outline for Leisure) — then the start–end time as small grey print; short blocks drop the time before ever dropping the name.
- **Account and realtime sync (Supabase)** — sign in with an email account and tasks sync in realtime between devices; merges align by task id with newest-wins and tombstone deletes, so nothing resurrects and nothing is silently overwritten. There is a **single logical timer across devices**: state lives in the cloud, both ends render it, and either end can control it. A task written with an `autoStart` flag starts its own countdown as soon as it arrives. (The legacy Google Drive file bridge was retired and removed in v1.26.0; Supabase is the only sync path.)
- **Per-task alarms** — any task can have an alarm time (it rings whether or not a timer is running; a daily task re-arms for the next day) and/or ring when its timer ends, before the end-of-task check-in. The default sound is a clear nature recording that fades in from silence to full volume over 60 seconds, played on the alarm audio stream, so it rings even when the phone is muted.
- **Start-time reminders** — when a task's planned start time arrives, a full-screen reminder pops up: start now, snooze 10 minutes, or ignore.
- **Habits and the morning gate** — self-chosen habits in five goal types (since v1.82.0 they live in the Know tab's Precepts group, alongside the conduct principles) (at least N days a week, at most N times, a daily number, a cumulative total toward a goal, or — since v1.31.0 — a daily reminder: a passage you write to yourself), each with its own reminder time, a year grid for back-filling, and progress toward the week on every card; habits feed half of the Zhixing index on the Oneness tab. The day opens with a fullscreen morning gate: yesterday's habits answered one by one, a short read-only report on yesterday, and a morning diary, all before "Start today" unlocks the day. Since v1.28.0, a cumulative habit can carry a starting number, so a count begun before KnowDo continues from where it actually reached instead of from zero. Since v1.29.0, one global setting (Settings → Habit display window) chooses whether every card's main count reckons by today, this week, last week or this month. Since v1.30.0 the tab also has a compact mode — one line per habit with this week's seven dots and, on the right, how far the habit still is from this week's goal. A daily reminder's full text appears in the morning gate — yesterday's in the must-answer section, today's in a reading section — and tapping "Read" completes it for the day. Tapping the Zhixing index ring on the Oneness tab opens a trend page (v1.76.0): three switchable charts — the index itself, principle practice, and the "pure motive" share — by month or year, backed by per-day score records the app seals locally each day and syncs to the cloud.
- **Bilingual UI and themes** — Chinese / English interface and light / dark themes, switchable from the drawer or settings. Since v1.86.0 the ☰ drawer button (account, language, theme, help, settings) sits at the top-left of each of the first four tabs, and the Act header is a single row: the page name, two compact figures (Must-do · Free), and icon-only Plan / Calendar buttons at the right.
- **Today tab (v1.85.0)** — the bottom bar has five tabs (Today / Act / Know / Oneness / Chat) and the app opens on Today: a morning check-in card (tap to fill today's morning record), one AI advice card for the day (a main task to start first, anything that should not wait, important unanswered mail; the cited task can be started from the card, and a reply can be composed and handed to your AI app with the text filled in — never sent for you), today's diary in full, this morning's recommended principle, and the entry to the day planner.
- **The Know tab — 致良知 (v1.82.0)** — one page in six groups: Aspiration （志： your aspiration and values), Insight （明： thinking principles), Precepts （戒： the habits, plus conduct principles), Reflection （察： diaries, motive tallies, a read-only list of slips), Questions （问） and People （人）, all cross-linked with {{ }} references while you write. A task can carry a main principle (shown on its row and while it runs); the completion page then quietly asks whether you lived it — answering is optional and never blocks finishing (v1.75.0).
- **AI tasks and the motive gate** — a task can be assigned to "my AI" instead of yourself (v1.74.0): it never enters auto-scheduling, the time accounts or roll-over, and the author's assistant works it in the background, always stopping before the final irreversible step for the author's confirmation. And every timer extension, on any task, first passes a one-question gate (v1.72.0): is the motive pure — "wholly for good, with no selfish intent"? Any answer lets you through; the answer is recorded honestly for the Oneness tab's tallies.
- **Self-update (v1.27.0)** — the app checks for new versions itself (at most once a day, switchable), downloads the new build in verified ~5 MB shards from the same private Gitee repository that serves its models, checks every shard and the assembled file by size and MD5, then shows one plain "ready to install" dialog — installing is a single tap on the system installer (with a one-time "install unknown apps" permission walkthrough). Download progress lives only on the Settings → App Updates row; the download network (Wi-Fi only / Wi-Fi & mobile data) is a personal setting synced via the cloud.
- **Chat unread badge (v1.27.0)** — the Chat tab carries a small vermilion badge counting AI messages you haven't seen yet (above all the proactive distraction-companion messages); opening the tab clears it.
- **Help page** — the full manual lives inside the app, including the design rationale and an explicit list of things considered and deliberately not built.

The UI is native Java/Views, layered as TaskStore (data) / SyncManager (sync) / TimerService (timing) / UI, so the data format stays stable across implementation changes. The full design research (in Chinese) is in [docs/RESEARCH.zh.md](docs/RESEARCH.zh.md).

## For new users: what the AI can and cannot do

KnowDo's AI comes in two parts, and the difference matters:

- **The built-in chat works out of the box.** It runs on the project's free model channel, and every AI button inside the app uses this same built-in chatbot for all users — there is no special channel for the author; in the app, the author uses the same AI as everyone else. The calendar's "AI Re-plan" is built-in too: it drafts a new plan on the spot, and the plan only lands when you tap Apply.
- **The patrol features are the author's personal AI setup, not app features.** Filing tasks dictated in a conversation outside the app, watching the cloud for a chance to re-plan, and classifying per-task app lists are done by the author's own assistant, patrolling the author's own cloud account in the background. For new users, bluntly: the built-in chat is the one and only AI you can connect to — there is no other option, and there is currently no way to connect your own external assistant. It cannot enter this app's cloud database, and no patrol is running for you. App-list classification is the same story: lists are generated in the background for the author's account only; other accounts, for now, use the built-in generic blacklist, local auto-planning, on-device guard verdicts, and the built-in chat.
- **Your data is isolated per account at the database level.** Your tasks, lists and records live only under your own account. The author cannot access them, and neither can the author's assistant; nobody's AI can read anyone else's data. That is enforced by the database, not by a verbal promise.

## Changelog

The full history is in [CHANGELOG.md](CHANGELOG.md) (Keep a Changelog format; every release commit is tagged, and the version shown in the app carries the build commit's short hash). Latest release:

### v1.89.0 — 2026-11-20

**Why:** Repeat was daily-only, so a task that runs on set weekdays could not exist; and the guard delivered its verdicts without knowing a task's special circumstances, while appealing a wrong verdict was restricted. This release merges two PRs by an external coding agent (Grok) — weekly repeat, and task descriptions with an always-available appeal — diff-reviewed item by item and hand-merged before release. Build commit `273bd5a` · [compare v1.88.0...v1.89.0](https://github.com/gty3310/knowdo/compare/v1.88.0...v1.89.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Weekly repeat.** A task can repeat every week on the weekdays you tick (say Mon/Wed/Fri), chosen when editing the task; the unity index and the daily rollover count only the days you picked, and a day you did not pick never counts as missed.
- **Task description.** Editing a task now takes a one-line description; the guard's judgement reads it as background, so fewer verdicts are mistakes.
- **Appeal any time.** "This is a mistake" on the guard check page can be tapped whenever it appears, and appeals work from the block screen too; on Medium and Hard you write a short explanation and an AI re-checks it (the task description goes along). If the AI channel is unavailable, you can skip the re-check after waiting 5 seconds.

**Improved**
- **Rename on the floating window.** Tap the task title on the floating window to edit the name in place; on the call-back page, "switch to next task" jumps straight to the Act page to pick one.

Not verified on a device or emulator: a feature-merge release, shipped directly at the user's instruction; if anything is wrong, roll back to v1.88.0. Data changes: weekday picks and task descriptions are stored on the phone for now; the matching cloud columns (`tasks.repeat_weekdays`, `tasks.description`) are written up in docs/migrations but deliberately not applied yet — sync picks them up automatically once the database is unblocked on Nov 24 and the columns are added, and nothing on the phone is lost in the meantime.

### v1.88.0 — 2026-11-16

**Why:** On the medium guard difficulty, the "This is a mistake" button only appeared on the first drift check of the day — a real misjudgement on the second check had nowhere to appeal. And once a task was paused with no next step chosen, nothing ever called you back: the pause could hang silently all day while you drifted off. This release (two PRs by an external coding agent, diff-reviewed before merge) shows the appeal button on every check and adds a full-screen pause lock as the last resort that pulls you back. Build commit `e26826e` · [compare v1.87.0...v1.88.0](https://github.com/gty3310/knowdo/compare/v1.87.0...v1.88.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Pause lock.** Thirty seconds after a task is paused with no next step chosen, an exact alarm fires and the floating window expands to about 99% of the screen (new `PauseLock` / `PauseLockActivity` / `PauseLockReceiver`, re-armed after reboot), so the pause cannot be ignored; two holes where a pause had no call-back at all are closed along the way.

**Improved**
- **"This is a mistake" shows on every drift check.** The medium-difficulty gate that rendered the appeal button only on the day's first check is gone — all three difficulties render it every time.

Not verified on a device or emulator: the user ordered this release shipped right after the merge. The appeal button on a second same-day check, the 30-second pause lock end to end, and the two call-back fixes have not been run anywhere yet. Data changes: none — no cloud column, no sync change; the six red-line files (unity index, day account, self checks, semantic judge, habit data, rollover) have zero diffs in this release.

### v1.87.0 — 2026-11-15

**Why:** The floating window vanished at the worst possible moment — the instant a timer ran out — and never came back on its own; and the gap between finishing one task and starting the next had no gentle push. This release (two PRs by an external coding agent, diff-reviewed line by line before release) keeps the window alive through the end-of-task confirmation, rebuilds it if the system tears it down, and fills the gap with a 3-minute countdown to find the next task. Build commit `bb2fc3e` · [compare v1.86.0...v1.87.0](https://github.com/gty3310/knowdo/compare/v1.86.0...v1.87.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Find-the-next-task countdown.** Tapping Done on the end check page starts a 3-minute countdown in the floating window — a purely local timer (its own preferences + an exact alarm; never a task: it enters no task list, time account, unity index or cloud table). If nothing has started when it expires, the Next Task page opens by itself; the between-task guard stays quiet during the countdown so nothing double-prompts, and starting, resuming or mirroring any real task cancels it instantly. It survives process death (alarm plus a per-second poll in the float service), and its floating-window state cannot be tapped into starting or pausing anything.

**Fixed**
- **The floating window no longer disappears at time-up.** It stays through the end-pending confirmation (both services used to hide it), and a watchdog now detects a system-torn-down overlay — a detached view or a failed layout update, with a 3-second grace after a fresh build — and rebuilds it. Pages that yield to the full-screen guard, block and breathing pages restore the window the moment they pause, and those pages re-suppress on resume so the window never covers them.
- **No one-second flash of the next task after Done.** The window switches straight to the countdown, and stopping a timer refreshes the window to the next priority instead of hiding it first.
- **The timer notification at time-up deep-links to the ring page or the end check page** (its own PendingIntent request code) instead of opening the home screen and losing the confirmation; if the system reclaims the ring page, it still hands over to the check page, unless the task was already confirmed or stopped elsewhere.
- **The Next Task page no longer offers AI tasks** as candidates — they cannot be timed.

Not verified on a device or emulator: at the user's own call (token budget), this release skipped the emulator run that a bug-fix release would normally get. The behaviours above — the window staying at time-up, the watchdog rebuild, the 3-minute countdown and its expiry page, the flash fix, the notification deep link — have not been run anywhere yet. Data changes: none — no cloud column, no sync change; the six red-line files (unity index, day account, self checks, semantic judge, habit data, rollover) have zero diffs in this release.

## Build

Requires JDK 17 and Android SDK 34 (Gradle 8.7 + AGP 8.5.2):

```sh
./build-gradle.sh
```

The APK lands in `app/build/outputs/apk/release/app-release.apk`. Signing uses a local debug keystore, so new builds install over old ones. `./build-manual.sh` is the retired zero-dependency pipeline from the v1.3.x era, kept for history only; it cannot build current sources.

Cloud sync reads a Supabase **publishable** key at build time from a local, untracked `supabase_public_key.txt` (never committed). Without it, the app still builds and runs with local tasks.

## License

Licensed under [CC BY-NC-SA 4.0](LICENSE): free to use, modify and build upon, **non-commercial only**, and derivatives must be shared alike and remain free.

Courtesy (not a license condition): please let the author know before you build on it.
