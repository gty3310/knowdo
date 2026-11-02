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

## Features (Android, v1.50.0)

Current version: **v1.50.0**.

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

### v1.50.0 — 2026-11-03

**Why:** Knowing and doing are two ledgers. Tasks have always been the doing side; this release stands up the knowing side — a place for the principles he thinks by, the principles he acts by, and the questions he still means to ask. Build commit `f43a9a3` · [compare v1.49.0...v1.50.0](https://github.com/gty3310/knowdo/compare/v1.49.0...v1.50.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Five bottom tabs: Act / Know / Habits / Zhixing / Chat.** The first tab is the same schedule page, renamed from Schedule to Act; Know is inserted second and the rest shift down one place, their pages untouched. Tab indices moved with named constants, so existing deep links (the guard page and habit reminders opening a specific tab) follow automatically. The Know tab's icon is simply the character 知.
- **The Know page.** Three sections — thinking principles, principles for action, questions to ask — each with its entries and an "Add one" row. An entry shows its title (or the first line of its body) plus a one-line preview; a question with a named addressee shows a small "for: X" beside the title. Tapping an entry opens the editor (title, body, and "who to ask" for questions); deleting asks for confirmation first, in the app's usual wording.
- **Backed by the `zhi_entries` table.** Entries live in Supabase and are read and written through the app's existing Supa channel with the signed-in user's JWT, so the table's owner-only row-level security applies. The page caches the last successful fetch on-device: offline it shows the cache with a small "Offline — showing the last copy" note, with no cache it shows an empty state with retry, and signed out it simply asks for sign-in. Every create, edit and delete lands on the server first and only then refreshes the page and cache, so a failed save never leaves the page showing something the database does not have. `Supa` gains one small public `restDelete` helper for this; nothing else in the sync layer changed.

Not yet verified on a real device: the five-tab order and renaming, tab switching and state retention (including notification/guard deep links landing on the right tab), the Know page's first fetch and three sections, creating/editing/deleting entries and reading them back from the cloud, the "for: X" addressee display, the offline cache and its note, the no-cache failure state with retry, the signed-out hint, the changelog popup, and the 1.49.0 → 1.50.0 instant-update chain.

### v1.49.0 — 2026-11-03

**Why:** Some texts — a morning admonition above all — are not tasks to check off; they are passages to open and read every day. The user made that a habit type of its own: the passage lives inside the habit, one tap opens it in full, and a single "Read" button at the bottom is the whole completion ritual. Build commit `35b0616` · [compare v1.48.0...v1.49.0](https://github.com/gty3310/knowdo/compare/v1.48.0...v1.49.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **An eighth habit type: Reading.** On the Habits page it is a single row — the habit's name and a small "Reading" label, with no done-or-not state and no check-in controls (the row never even looks at the log). Tapping it opens the reading page: the habit's full text (stored in the standard reminder-text field), set large with roomy line spacing under a serif title, and one fixed button at the bottom — "Read it", or "Read today" once today's tap is recorded. Tapping it writes the day's habit log through the same putLog path every other type uses, so the Zhixing index counts it exactly like the weekly and diary types; tapping again is harmless. The passage can be set when creating the habit (a multi-line field in the form; it may be left empty and filled later) and changed any time in Edit habit, which shows the full-text field for this type and skips the target field. An empty passage shows a placeholder on the reading page and the button still works. In the morning page's yesterday section the type appears as a read-only status line, like the weekly and diary types, and it never blocks the morning gate; the Daily Reminder type's existing behavior is untouched. Switching the user's own "Daily Reading" habit over to the new type happens in the cloud after this release, not in code.

Not yet verified on a real device: creating a reading habit end to end, the stateless habit-page row and opening the reading page, the full-text and empty-text states, tapping Read and its log/Zhixing-index effect, editing the passage afterwards, the morning page's read-only row and gate completing as before, the changelog popup, and the 1.48.0 → 1.49.0 instant-update chain.

### v1.48.0 — 2026-11-03

**Why:** Checking off "journaling" and actually writing the diary used to be two separate things, and past entries had no good place to be read back. The user folded them into one text-type habit: open it and you are in the diary. Build commit `574b506` · [compare v1.47.0...v1.48.0](https://github.com/gty3310/knowdo/compare/v1.47.0...v1.48.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **A seventh habit type: Diary.** On the Habits page it is a single row — the habit's name and today's state ("written · N chars" or "not yet") — and tapping it opens the diary page: today's entry editor on top (saved as you type; writing anything marks the day done through the same habit log every other type uses, with no length requirement), and below it the full history in reverse date order, each day showing its date, weekday, length and first line. Tapping a past day amends it, and the amendment keeps that day's completion mark in sync. The diary text is the app's one existing diary — the same text the morning page writes — not a new store; the morning page's diary box and its completion rules for the existing types are unchanged (in the morning page's yesterday section the new type appears as a read-only status line, like the weekly type). Deleting a diary habit removes only the habit, never the diary text.

Not yet verified on a real device: creating a diary habit end to end, the habit-page row in both states, writing today's entry and its completion/Zhixing-index effect, the morning page writing the same diary and the habit row agreeing, browsing history and amending a past day (with its log following), the morning page's read-only row and gate completing as before, deleting a diary habit leaving the diary intact, the changelog popup, and the 1.47.0 → 1.48.0 instant-update chain.

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
