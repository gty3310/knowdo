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

## Features (Android, v1.81.0)

Current version: **v1.81.0**.

- **Excel-like task table** — columns you can show or hide, resize, and edit in place: task name, context tags (multi-select, custom tags allowed), deadline (date picker, or daily repeat), planned duration, priority, planned start time, time used / remaining. Tap a row to expand it in place and act on it; a checkbox marks it done; the row currently being timed is highlighted.
- **Focus timer with full-screen check-ins** — a countdown per task, driven by a foreground service that stores an absolute end time, so the countdown does not drift if the process is killed; exact alarms back up the half-time and end points. At half-time and at the deadline, a full-screen page takes over — via full-screen intent from the lock screen, or an overlay path on an unlocked screen — asking whether you can still finish and whether you did. Not done? Add +50% / +5 / +10 / +15 minutes or a custom extension, or write down your plan for finishing.
- **Always-on floating window** — a small overlay (docked tab / mini card / full edit card) that always shows your current task and its remaining time and follows you across apps. Pause from it (with a "remind me in N minutes" menu), jump back to your assistant, or edit the task note in place without opening the app.
- **Focus guard** — each task can carry a focus rule: a natural-language description plus a structured block/allow app list and a primary app. While a timer runs, the foreground app is checked every couple of seconds (via usage access); open something unrelated and a full-screen page asks "are you working on the right task?" — jump back to the task's primary app (or to a built-in current-task page), take a 30/60-second grace period, after which it jumps back for you, or dismiss a false alarm for the rest of that session.
- **Per-task app lists (v1.26.0)** — when a task starts, your frequently used apps are sorted for that task into white / grey / black tiers in the background: whitelisted apps no longer trigger focus-rule inquiries during that run, opening a blacklisted app raises the inquiry immediately (the page names the list it came from), and grey adds no new interruptions. Until a task's list is ready, a built-in generic blacklist (short video, social, shopping and game apps) stands in. Open "App list" in a task's details to see the tiers and re-tier any app — your changes are never overwritten by later generations. There is a master switch in Settings → Guards. (Automatic classification currently serves the author's own account only — see the section for new users below.)
- **Screen-content guard** — for the distractions the focus guard cannot see: shopping or short videos inside an allowed browser, or adult content. Opt-in, and on-device only. While a timer runs on an unlocked screen, screenshots are sampled at random intervals and analyzed locally — ML Kit OCR (Chinese / Japanese / English) plus keyword rules, foreground-app category, and a small on-device image classifier (ONNX). Two consecutive hits (one, for adult content, if you set it so) raise the same inquiry page as the focus guard. Screenshots are analyzed in memory and destroyed immediately: never saved, never uploaded; only the verdict events sync. Screenshots come only from the accessibility channel (v1.26.0: the screen-recording channel was removed entirely — no recording permission is ever requested; with accessibility off, the guard simply does not sample).
- **Calendar panel** — a day view that opens inside the Schedule page beside the task list itself (the bottom tabs stay reachable): three lanes — a narrow Google Calendar lane (view-only reference; scheduling does not work around it), the day's plan lane (widest), and a narrow actual lane; drag tasks straight from the list into the calendar to plan them, long-press plan blocks to drag them to a new time, an "AI Re-plan" button that has the built-in AI draft a new plan for the rest of the day on the spot — it only lands when you tap Apply — and, since v1.27.0, a "Quick Re-plan" button beside it that re-plans instantly with a fixed algorithmic order (short due-and-important tasks first, then Must, due Important, due Secondary, not-due Important, not-due Secondary; Leisure never), leaving the running task, done blocks and hand-dragged blocks untouched — and, since v1.27.1, recycling an unfinished task's expired block so the task is re-planned from now instead of being stranded in the past — with a one-tap Undo. Both planners and the automatic gap-filling share one ordering implementation, and the AI prompt now also receives that same rule text, today's Google Calendar events (reference only) and what has already been done today. Blocks read in a fixed order: the task name first (up to two lines), then its priority marker — colour and shape only since v1.26.2 (solid vermilion for Must, an ink outline for Important, a small grey chip for Secondary, a dashed outline for Leisure) — then the start–end time as small grey print; short blocks drop the time before ever dropping the name.
- **Account and realtime sync (Supabase)** — sign in with an email account and tasks sync in realtime between devices; merges align by task id with newest-wins and tombstone deletes, so nothing resurrects and nothing is silently overwritten. There is a **single logical timer across devices**: state lives in the cloud, both ends render it, and either end can control it. A task written with an `autoStart` flag starts its own countdown as soon as it arrives. (The legacy Google Drive file bridge was retired and removed in v1.26.0; Supabase is the only sync path.)
- **Per-task alarms** — any task can have an alarm time (it rings whether or not a timer is running; a daily task re-arms for the next day) and/or ring when its timer ends, before the end-of-task check-in. The default sound is a clear nature recording that fades in from silence to full volume over 60 seconds, played on the alarm audio stream, so it rings even when the phone is muted.
- **Start-time reminders** — when a task's planned start time arrives, a full-screen reminder pops up: start now, snooze 10 minutes, or ignore.
- **Habits and the morning gate** — a Habits tab of self-chosen habits in five goal types (at least N days a week, at most N times, a daily number, a cumulative total toward a goal, or — since v1.31.0 — a daily reminder: a passage you write to yourself), each with its own reminder time, a year grid for back-filling, and progress toward the week on every card; habits feed half of the Zhixing index on the KnowDo tab. The day opens with a fullscreen morning gate: yesterday's habits answered one by one, a short read-only report on yesterday, and a morning diary, all before "Start today" unlocks the day. Since v1.28.0, a cumulative habit can carry a starting number, so a count begun before KnowDo continues from where it actually reached instead of from zero. Since v1.29.0, one global setting (Settings → Habit display window) chooses whether every card's main count reckons by today, this week, last week or this month. Since v1.30.0 the tab also has a compact mode — one line per habit with this week's seven dots and, on the right, how far the habit still is from this week's goal. A daily reminder's full text appears in the morning gate — yesterday's in the must-answer section, today's in a reading section — and tapping "Read" completes it for the day. Since v1.32.0, tapping the Zhixing index ring on the KnowDo tab opens a detail page — the index and its three components recomputed from the raw records over the past week, month and year.
- **Bilingual UI and themes** — Chinese / English interface and light / dark themes, switchable from the drawer or settings. The top bar stays minimal: a drawer (account, language, theme, help, settings) on the left, the calendar on the right.
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

### v1.81.0 — 2026-11-11

**Why:** A repeating task skipped for today should stop posing as today's work in the task list. The user's spec: in the list, a skipped task carries a "(skipped)" mark beside its due date, and the ranking sorts by effective date — once skipped, the task's next real occurrence is what counts, so what is truly due today sits at the very top. Only the task-list page changes; everything else keeps its existing rules. Build commit `55fe1a3` · [compare v1.80.0...v1.81.0](https://github.com/gty3310/knowdo/compare/v1.80.0...v1.81.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**Improved**
- **The task list sorts by effective date.** A repeating task skipped today now sorts by its next occurrence (tomorrow for a daily task) instead of today, so tasks genuinely due today rank above it. The shared ordering behind the floating window, the next-task page and chat is untouched, and so are Plan today and the unity index — the index has excluded skipped dates since 1.79; this release only teaches the list itself.
- **Skipped tasks are marked in the list.** A task skipped for today shows "(skipped)" right after its due-date text on the task-list page.

Not yet verified on a real device: the mark and the new ordering after an actual skip on the phone, and the 1.80.0 to 1.81.0 in-app update chain. Not in this release: diary filters and the calendar re-plan line removal, the floating card changes, the semantic engine split, and the always-on floating window.

### v1.80.0 — 2026-11-11

**Why:** The user settled the drift response in one pass and made it the top priority: when the guard catches you drifting, picking a grace length already admits the drift — so the inquiry page stops asking "did you do right?" altogether. What it asks now is how long a last look you want, and what happens when it runs out: the app is covered for a fixed 3 minutes (other apps stay usable; the cover returns the moment you switch back). Early release exists exactly once a day and only from Settings. Build commit `e4cbe14` · [compare v1.79.0...v1.80.0](https://github.com/gty3310/knowdo/compare/v1.79.0...v1.80.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Block for 3 minutes.** The at-expiry response grows from two choices to three — quit the app, background only, or block it: a full-screen cover in the inquiry page's own style showing the app's name, a large countdown and the current task, with no unblock control anywhere on it. The cover state survives process death and reboot (an exact alarm clears it at the deadline), the two guard modes share the one setting, and an old two-way choice migrates across (a stored "quit" stays quit, "background" stays background; never touched it and the new default is block). The 3 minutes are a constant, not a setting.
- **Unblock early, in Settings.** While an app is covered, Settings shows the covered app's name with an Unblock row — tap it and the cover lifts, and that was today's one use; afterwards the row just says so. A static row records the fixed block length.

**Improved**
- **The inquiry page.** The self-check row is gone; in its place, "A last look — how long?" with four buttons: Block now, 1, 3 or 5 more minutes. Tapping any of them records the drift exactly as the old "No" answer did — the focus score's drift count reads that same record, and its formula is untouched this release (the calculator file has an empty diff). Return to task, false alarm and chat are unchanged.

Not yet verified on a real device: the reworked inquiry page, grace expiry into the cover in both guard modes, Block now, cover behaviour when switching apps, suppression of new inquiries while covered, the once-a-day Settings unblock, reboot persistence, and the 1.79.0 to 1.80.0 in-app update chain. Not in this release: diary filters, the calendar re-plan line removal, the floating card's button row, the semantic engine split, and the always-on floating window — all queued behind this one by the user's call.

### v1.79.0 — 2026-11-11

**Why:** Daily repeating things sometimes need a day off — deleting them is too much, forcing through drags the day (and the score) down — so repeating tasks gain a "skip today": not done today, not counted as missed, back tomorrow as usual. The same release pins the page's four action buttons in one row at the bottom (they used to live at the top, a full scroll away), fixes the Add-tasks-with-AI panel opening to just its title line, and removes the two diagnostic lines 1.77 added to localise the blank-page fault (closed in 1.78 and verified on the user's phone). Build commit `4ee2e8e` · [compare v1.78.0...v1.79.0](https://github.com/gty3310/knowdo/compare/v1.78.0...v1.79.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Skip today for repeating tasks.** Cards in Plan today carry a Skip button for repeating tasks (next to Done / Delete). Skipping takes the task out of today's candidates and scheduling input (one shared snapshot filters it by logical day), deletes its already-planned blocks for today — locally at once, and in the cloud through a read-modify-write-back-verified removal — so the time returns to the ledger, and parks the task in Other important tasks with a Skipped marker and a Restore button; restoring clears the skip without re-planning blocks. A skipped date counts neither as done nor as missed in the unity index's task score (the one approved scoring addition this release) and does not count toward today's must-do time. The skip set lives beside the done set (`skippedDates`; cloud column `skipped_dates`, the same date-array type as `done_dates`, added by a migration verified lossless over all 168 rows) and syncs with the same merge rules and old-backend probing as the other optional columns.

**Improved**
- **Four buttons, one row, pinned to the bottom.** Add task / Add with AI / Plan into today / Start today are now a single equal-width row in a bottom bar floating over the page (a FrameLayout bottom layer — no weight-based height splitting anywhere in the page structure), on screen wherever the page is scrolled; the content column pads itself by the bar's real height plus 12dp so the last card is never covered, and with the page's adjustResize the bar rides above the keyboard. The old in-page button rows are gone. The time ledger still scrolls with the page — only the bar sticks (the user's call).

**Fixed**
- **The Add-tasks-with-AI panel opens whole again.** On the user's phone (1.78.0) the panel showed only its title row floating mid-page — no chat area, no input row, and a height plainly not 92%. The cause is the same family as the 1.77 blank page, whose DBG numbers were measured on that phone: a height-0-plus-weight scroll view can collapse to nothing while a wrap-content sibling inflates to fill the parent. Inside the panel, the weighted chat scroll view collapsed and the header — a wrap-content row with vertically centred content — inflated to the panel's full height, which put the title in the panel's middle and pushed the input row off the panel's bottom edge. The chat area now gets an explicit height: when the panel is built, the header and input row are measured at the current font scale, and the scroll area takes what is left of the panel's 92%-of-screen height — no weight distribution anywhere. Chat and proposal-card logic untouched; spacing around the header, message list and input row tidied to the app's usual measure.

**Removed**
- **The two diagnostic lines at the top of Plan today** — the 1.77 DBG line and the 1.70 self-check line ("Loaded … tasks · … blocks · … cards") — together with all their code (height fields, the sampler, the self-check updater). The per-section guard rails and their vermilion fallback rows stay.

Not yet verified on a real device: skip/restore end to end, the pinned bar over a keyboard, and the AI panel fix — the on-host emulator could not bring its system UI up in this environment (no hardware acceleration, throttled CPU; its package service kept dropping during boot), so the panel fix in particular awaits the emulator or the user's phone; an instrumented diagnostic build that logs the panel's layer heights is kept ready for that check. Not in this release: diary filters and the calendar split-view re-plan line removal (v1.80), the floating card's add-time button (v1.81).

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
