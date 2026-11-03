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

## Features (Android, v1.57.0)

Current version: **v1.57.0**.

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

### v1.57.0 — 2026-11-03

**Why:** The Act list was ordered priority-first, so a must-do due next week sat above a minor task due today — not the way the calendar sees the day. The user ruled: the list should run by date first, one day after another, and a task due today that has not been given a time yet should say so in its row. Build commit `4e74106` · [compare v1.56.0...v1.57.0](https://github.com/gty3310/knowdo/compare/v1.56.0...v1.57.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**Improved**
- **The Act list runs by day now.** Inside the unchanged split (unfinished first, today's finished below, history groups after), tasks are ordered by their effective date: overdue first, then today, then the coming days, with daily repeats counting as today and no-deadline tasks in their own group at the end. Within a day, the established priority order holds (must, important, secondary, leisure), then creation order — the same tiers the calendar planner uses, so a day holds the same tasks in the same order in both views. The shared `visibleSorted` ordering used by the floating window, the next-task page and the chat task pickers is deliberately untouched.
- **An "Unscheduled" tag for today.** A task that is unfinished, due today (logical day), not leisure and not a daily repeat, and that has no block in today's schedule gets a small vermilion tag under its date in the deadline column. The check is read-only against the local schedule and re-runs on every redraw, so the tag disappears the moment the task is planned. Daily repeats are excluded on purpose: their due date is virtual, and tagging them would cry wolf every day.

Not yet verified on a real device: the by-day reading order on a real list (overdue on top, no-deadline group last, same-day priority order), finished tasks and history groups unchanged, the tag appearing for due-today unplanned tasks and vanishing once planned, leisure and daily-repeat tasks never tagged, the tag hiding with a hidden deadline column, split view with the planned chips intact, the changelog popup, and the 1.56.0 to 1.57.0 in-app update chain.

### v1.56.0 — 2026-11-03

**Why:** While planning today, the big tasks due later in the week were invisible — easy to discover only when their deadline was already on top of you. The user asked for a quiet place on the Plan today page that shows them, without disturbing today's list. Build commit `6c70c6b` · [compare v1.55.0...v1.56.0](https://github.com/gty3310/knowdo/compare/v1.55.0...v1.56.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Other important tasks, collapsed by default.** At the very end of the Plan today content sits a collapsible section, "Other important tasks (N)". It collects unfinished must/important tasks whose deadline is not today (a future date, or no date at all), sorted nearest deadline first with no-date tasks last and must ahead of important on the same day. Daily repeats and anything already on today's list (including greyed postponed cards) never appear; overdue tasks stay on today's list where they belong. When nothing qualifies, the section does not render at all. Expanded state is memory-only — the page always opens collapsed.
- **Pull one into today with one tap.** Each item is a compact row — title, its priority marker, and a plain-language deadline (Tomorrow, a month/day date, or No date set) — with a single action, Move to today. It goes through the existing persist path (deadline = the page's logical today, saved and synced); the item leaves the section and joins today's list at the end of the frozen order in normal colour, and the time ledger recomputes. The rows themselves do not open the editor.

Not yet verified on a real device: the section opening collapsed with the right count, nothing rendering when the section is empty, the inclusion boundaries (postponed grey cards not duplicated, overdue and daily tasks excluded, no-date tasks last), the ordering, the deadline labels, Move to today moving the row into today's list with the ledger recomputed, rows not opening the editor, the changelog popup, and the 1.55.0 to 1.56.0 in-app update chain.

### v1.55.0 — 2026-11-03

**Why:** Planning the day used to break stride: think of a task and you had to leave for the Tasks tab to create it. The user asked for adding right where planning happens — manually, or by telling an AI several tasks at once and confirming its list. Build commit `9b83b60` · [compare v1.54.0...v1.55.0](https://github.com/gty3310/knowdo/compare/v1.54.0...v1.55.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Add task.** A lighter two-button row now sits above Plan into today. Add task reuses the existing new-task sheet (new `TaskEditSheet.showCreateWithDeadline` entry) with the deadline prefilled to the page's logical today and no start time; saving goes through the normal store path, the new card appends at the end of the page's frozen order, and the time ledger recomputes.
- **Add with AI.** Tapping it opens a near-full-height chat panel inside the page (92% of the screen, anchored to the bottom) with a Close button that returns to the untouched page. The conversation is a throwaway session kept only in memory — it never touches the Chat tab's stored history. It rides the exact same transport as the Chat tab (`ChatClient` over the chat-proxy, same model chain) with a scene-specific system prompt: settle each item's title, minutes and priority, estimating lengths openly when unstated.
- **Proposal first, always.** When the list is complete the AI ends its reply with a `knowdo-addtasks` JSON block; the app validates and normalizes it (non-empty titles, minutes clamped 1–480, priority whitelisted) and renders a proposal card — Add them / Keep editing. Only Add them creates the tasks (deadline = today, normal store path, sync kicked), the card locks against double-taps, and a chat line confirms the count. Unparseable replies simply render as plain chat; when the AI or network is down, a plain-language line lands in the chat and manual adding is unaffected.

Not yet verified on a real device: the two-button row's look, the prefilled deadline and append/ledger behaviour after manual add, panel height and page-state preservation, keyboard behaviour in the panel, multi-turn AI collecting with estimated lengths, the proposal card's two buttons (creation + sync, no double-apply, edit-again flow), unparseable replies rendering as plain chat, offline behaviour, the changelog popup, and the 1.54.0 to 1.55.0 in-app update chain.

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
