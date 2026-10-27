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

## Features (Android, v1.27.0)

Current version: **v1.27.0**.

- **Excel-like task table** — columns you can show or hide, resize, and edit in place: task name, context tags (multi-select, custom tags allowed), deadline (date picker, or daily repeat), planned duration, priority, planned start time, time used / remaining. Tap a row to expand it in place and act on it; a checkbox marks it done; the row currently being timed is highlighted.
- **Focus timer with full-screen check-ins** — a countdown per task, driven by a foreground service that stores an absolute end time, so the countdown does not drift if the process is killed; exact alarms back up the half-time and end points. At half-time and at the deadline, a full-screen page takes over — via full-screen intent from the lock screen, or an overlay path on an unlocked screen — asking whether you can still finish and whether you did. Not done? Add +50% / +5 / +10 / +15 minutes or a custom extension, or write down your plan for finishing.
- **Always-on floating window** — a small overlay (docked tab / mini card / full edit card) that always shows your current task and its remaining time and follows you across apps. Pause from it (with a "remind me in N minutes" menu), jump back to your assistant, or edit the task note in place without opening the app.
- **Focus guard** — each task can carry a focus rule: a natural-language description plus a structured block/allow app list and a primary app. While a timer runs, the foreground app is checked every couple of seconds (via usage access); open something unrelated and a full-screen page asks "are you working on the right task?" — jump back to the task's primary app (or to a built-in current-task page), take a 30/60-second grace period, after which it jumps back for you, or dismiss a false alarm for the rest of that session.
- **Per-task app lists (v1.26.0)** — when a task starts, your frequently used apps are sorted for that task into white / grey / black tiers in the background: whitelisted apps no longer trigger focus-rule inquiries during that run, opening a blacklisted app raises the inquiry immediately (the page names the list it came from), and grey adds no new interruptions. Until a task's list is ready, a built-in generic blacklist (short video, social, shopping and game apps) stands in. Open "App list" in a task's details to see the tiers and re-tier any app — your changes are never overwritten by later generations. There is a master switch in Settings → Guards. (Automatic classification currently serves the author's own account only — see the section for new users below.)
- **Screen-content guard** — for the distractions the focus guard cannot see: shopping or short videos inside an allowed browser, or adult content. Opt-in, and on-device only. While a timer runs on an unlocked screen, screenshots are sampled at random intervals and analyzed locally — ML Kit OCR (Chinese / Japanese / English) plus keyword rules, foreground-app category, and a small on-device image classifier (ONNX). Two consecutive hits (one, for adult content, if you set it so) raise the same inquiry page as the focus guard. Screenshots are analyzed in memory and destroyed immediately: never saved, never uploaded; only the verdict events sync. Screenshots come only from the accessibility channel (v1.26.0: the screen-recording channel was removed entirely — no recording permission is ever requested; with accessibility off, the guard simply does not sample).
- **Calendar panel** — a day view that opens inside the Schedule page beside the task list itself (the bottom tabs stay reachable): three lanes — a narrow Google Calendar lane (view-only reference; scheduling does not work around it), the day's plan lane (widest), and a narrow actual lane; drag tasks straight from the list into the calendar to plan them, long-press plan blocks to drag them to a new time, an "AI Re-plan" button that has the built-in AI draft a new plan for the rest of the day on the spot — it only lands when you tap Apply — and, since v1.27.0, a "Quick Re-plan" button beside it that re-plans instantly with a fixed algorithmic order (short due-and-important tasks first, then Must, due Important, due Secondary, not-due Important, not-due Secondary; Leisure never), leaving running, past, done and hand-dragged blocks untouched, with a one-tap Undo. Both planners and the automatic gap-filling share one ordering implementation, and the AI prompt now also receives that same rule text, today's Google Calendar events (reference only) and what has already been done today. Blocks read in a fixed order: the task name first (up to two lines), then its priority marker — colour and shape only since v1.26.2 (solid vermilion for Must, an ink outline for Important, a small grey chip for Secondary, a dashed outline for Leisure) — then the start–end time as small grey print; short blocks drop the time before ever dropping the name.
- **Account and realtime sync (Supabase)** — sign in with an email account and tasks sync in realtime between devices; merges align by task id with newest-wins and tombstone deletes, so nothing resurrects and nothing is silently overwritten. There is a **single logical timer across devices**: state lives in the cloud, both ends render it, and either end can control it. A task written with an `autoStart` flag starts its own countdown as soon as it arrives. (The legacy Google Drive file bridge was retired and removed in v1.26.0; Supabase is the only sync path.)
- **Per-task alarms** — any task can have an alarm time (it rings whether or not a timer is running; a daily task re-arms for the next day) and/or ring when its timer ends, before the end-of-task check-in. The default sound is a clear nature recording that fades in from silence to full volume over 60 seconds, played on the alarm audio stream, so it rings even when the phone is muted.
- **Start-time reminders** — when a task's planned start time arrives, a full-screen reminder pops up: start now, snooze 10 minutes, or ignore.
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

### v1.27.0 — 2026-10-27

**Why:** AI re-planning is slow, so the user wanted a one-tap algorithmic re-plan (keeping the AI one — both); fetching every update from a cloud drive by hand is a chore, so the app should update itself; and the ordering rules should be one single standard shared by automatic gap-filling, Quick Re-plan and the AI's prompt. Build commit `bba2a65` · [compare v1.26.3...v1.27.0](https://github.com/gty3310/knowdo/compare/v1.26.3...v1.27.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Quick Re-plan.** A second button beside "AI Re-plan" on the calendar page: one tap re-plans the rest of today immediately — no proposal card. One shared ordering implementation (`LocalAutoScheduler.candidateOrder`, now also used by automatic gap-filling): first the short ones (≤15 min, Important or above, due today; shortest first), then the remaining Must-dos, Important due today, Secondary due today, not-yet-due Important, not-yet-due Secondary; Leisure never takes part. Locked blocks stay put — the running task, past blocks, done blocks and blocks the user dragged into place (drag saves now stamp `manual: true`) — and the rest packs from now at ×1.5 until 22:30; anything that doesn't fit is named on the spot. The previous schedule is kept as a one-slot snapshot with an "Undo re-plan" row on the calendar.
- **Self-update.** New versions ship as ~5 MB shards attached to a release in the private Gitee resources repo, with a small `update/update.json` manifest (version code, sizes, per-shard and whole-file MD5s). The app checks at startup (throttled to once a day, switchable) or from Settings → App Updates, downloads shards through the API download endpoint (following the redirect to the file CDN) with the same read-only token the model downloads use, verifies each shard and the assembled APK by size and MD5 — already-verified shards are kept, so an interrupted download resumes — and then shows one plain dialog; installing goes through the system installer via FileProvider, with a one-time walkthrough for the "install unknown apps" permission. Progress shows only on the settings row. The download network (Wi-Fi only by default, or Wi-Fi & mobile data) syncs via cloud `user_state.update_prefs`, probed and merged exactly like `prompt_repop`; the author's account is preset to allow mobile data. Note: v1.27.0 itself still installs by hand once — self-update takes effect from the next release.
- **Chat unread badge.** The Chat tab shows a small vermilion badge with the count of AI messages not yet seen on the main thread (a local seen-count; history is never retroactively badged). Entering the tab marks everything seen and clears it.

**Improved**
- **AI Re-plan sees the whole day.** Its prompt now embeds the exact Quick Re-plan rule text as the baseline (with the red line restated: it may adjust, but never change any task's priority — levels never change automatically), today's Google Calendar events from the app's existing cache (titles and times, reference only — scheduling still does not avoid them), and what has already been done today: finished tasks plus the actual execution segments (real start–end times and durations, the same data as the calendar's actual lane). The proposal card and Apply flow are unchanged.

Not yet verified on a real device: quick re-plan placement/undo on a live day, the self-update chain end to end (check → shard download → install prompt → system installer, including the first-time permission walkthrough), AI Re-plan with the enlarged context, and the chat badge across a proactive distraction message.

### v1.26.3 — 2026-10-27

**Why:** The user ruled that any full-screen prompt swiped away unresolved must come back after 15 seconds, not five minutes — and that the interval should be one personal, cloud-synced setting rather than four hard-coded numbers. Same-day extras: the calendar plan-block renderer had been missed by the v1.26.1/v1.26.2 layout work (user screenshot), and the What's New popup often never appeared (root cause confirmed in code). Build commit `ae54b22` · [compare v1.26.2...v1.26.3](https://github.com/gty3310/knowdo/compare/v1.26.2...v1.26.3) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **One re-pop interval for every full-screen prompt.** A new setting, "Prompt re-pop interval" (Settings → Guards; 15 sec / 30 sec / 1 min / 5 min), stored locally and in the cloud `user_state.prompt_repop` object (`{"sec": …}`, probed like the other optional columns, defaulting silently to 300 sec when unavailable; the author's account is preset to 15). It now drives all four "swiped away unresolved" re-pops: the morning-gate nag chain (was a fixed 5 min), guard inquiry re-fire in both modes (focus 12 sec debounce and overturn cooldown, content 60 sec cooldown), the next-task page's after-Back quiet period (was 3 min), and the silent pause call-back chain (was 5 min). Chosen snooze/grace lengths, sound staging and every other behaviour are untouched.

**Fixed**
- **Plan blocks caught up with actual blocks.** The v1.26.1 hierarchy and the v1.26.2 textless markers had only landed in the actual-block renderer; plan blocks still led with the time range in a single text view and had no priority marker. `addPlanBlock` now stacks exactly like `addBlock` — task name first (bold, up to two lines, verbatim title), then the shared textless priority marker, then the start–end time as 9sp small print — with the same short-block tiers, while keeping the plan lane's blue colour system, the ▶/⏸ button, tap-to-expand and drag (including its preview text) untouched.
- **The What's New popup reliably appears.** It used to be attempted only once, in `onCreate`, behind the suggested-settings page that pre-empts it on every upgrade — and it wrote its "shown" record before displaying, so one cover by another full-screen page consumed the version forever. It is now attempted when the main screen returns to the foreground (after suggested settings and the morning gate have had their turn), and the record is only written once the dialog has actually been displayed (OnShow listener, once per process); a covered or failed attempt simply retries on the next foregrounding. Fresh installs still never see it, and the Settings entry still opens it any time.

Not yet verified on a real device: the 15-second re-pop rhythm in all four places, plan-block legibility across its three states and short-block tiers, and the What's New catch-up across the upgrade → suggested-settings → back flow.

### v1.26.2 — 2026-10-27

**Why:** The user's principle: a task's importance level may only ever be changed by hand, never automatically. Checked against the mainstream apps (Todoist, Motion, Sunsama): all of them keep priority and due date as two independent axes that never rewrite each other, and being overdue only ever moves the date. This release aligns with that consensus and settles, once, how a "due today but not must-do" task is treated — plus the user's other call: a priority marker needs no text, its colour and shape already say the level. Build commit `723f3ac` · [compare v1.26.1...v1.26.2](https://github.com/gty3310/knowdo/compare/v1.26.1...v1.26.2) · full notes in [CHANGELOG.md](CHANGELOG.md).

**Improved**
- **Priority levels never change on their own.** Overnight roll-in used to promote an unfinished Secondary task to Must (the v1.23.0 rule; v1.26.0 had already exempted Important). Roll-in now moves only the deadline to today and adds the "yesterday" mark — Must, Important and Secondary all keep their level, and Leisure still never rolls. The promotion code is deleted, not bypassed.
- **Auto-scheduling order: Must → due Important → due Secondary → not-due Important → not-due Secondary.** "Due" means deadline ≤ logical today, including just-rolled-in tasks. Must still fills first unconditionally, at ×1.5 with the 22:30 cut-off and existing blocks untouched. The in-app "AI Re-plan" prompt states the same order, now lists each task's deadline so due-ness can be judged, and is told levels never change automatically.
- **A due-today line in the time account.** Under the main account line (still Must-only), a small line gives the count of unfinished Important/Secondary tasks due today (deadline ≤ logical today) and their ×1.5 total; it hides when there are none, and Leisure never enters the account, as before. Overflow naming now covers both groups — must-dos and due-today tasks that don't fit are named by group.
- **Calendar priority markers are textless.** The marker under a block's task name keeps its slot and footprint but drops the word: an 18×8dp chip — solid vermilion for Must, a 1dp ink outline (rice-white on dark blocks) for Important, a smaller solid grey chip for Secondary, a dashed grey outline for Leisure. Text labels remain where a level is chosen: the task list and the editor's tier chips.

The Zhixing gap list and index still count Must only; nothing else changed. Not yet verified on a real device: levels surviving roll-in, due-first placement, the due line and naming copy on device, and marker legibility on light and dark blocks.

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
