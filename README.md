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

## Features (Android, v1.26.1)

Current version: **v1.26.1**.

- **Excel-like task table** — columns you can show or hide, resize, and edit in place: task name, context tags (multi-select, custom tags allowed), deadline (date picker, or daily repeat), planned duration, priority, planned start time, time used / remaining. Tap a row to expand it in place and act on it; a checkbox marks it done; the row currently being timed is highlighted.
- **Focus timer with full-screen check-ins** — a countdown per task, driven by a foreground service that stores an absolute end time, so the countdown does not drift if the process is killed; exact alarms back up the half-time and end points. At half-time and at the deadline, a full-screen page takes over — via full-screen intent from the lock screen, or an overlay path on an unlocked screen — asking whether you can still finish and whether you did. Not done? Add +50% / +5 / +10 / +15 minutes or a custom extension, or write down your plan for finishing.
- **Always-on floating window** — a small overlay (docked tab / mini card / full edit card) that always shows your current task and its remaining time and follows you across apps. Pause from it (with a "remind me in N minutes" menu), jump back to your assistant, or edit the task note in place without opening the app.
- **Focus guard** — each task can carry a focus rule: a natural-language description plus a structured block/allow app list and a primary app. While a timer runs, the foreground app is checked every couple of seconds (via usage access); open something unrelated and a full-screen page asks "are you working on the right task?" — jump back to the task's primary app (or to a built-in current-task page), take a 30/60-second grace period, after which it jumps back for you, or dismiss a false alarm for the rest of that session.
- **Per-task app lists (v1.26.0)** — when a task starts, your frequently used apps are sorted for that task into white / grey / black tiers in the background: whitelisted apps no longer trigger focus-rule inquiries during that run, opening a blacklisted app raises the inquiry immediately (the page names the list it came from), and grey adds no new interruptions. Until a task's list is ready, a built-in generic blacklist (short video, social, shopping and game apps) stands in. Open "App list" in a task's details to see the tiers and re-tier any app — your changes are never overwritten by later generations. There is a master switch in Settings → Guards. (Automatic classification currently serves the author's own account only — see the section for new users below.)
- **Screen-content guard** — for the distractions the focus guard cannot see: shopping or short videos inside an allowed browser, or adult content. Opt-in, and on-device only. While a timer runs on an unlocked screen, screenshots are sampled at random intervals and analyzed locally — ML Kit OCR (Chinese / Japanese / English) plus keyword rules, foreground-app category, and a small on-device image classifier (ONNX). Two consecutive hits (one, for adult content, if you set it so) raise the same inquiry page as the focus guard. Screenshots are analyzed in memory and destroyed immediately: never saved, never uploaded; only the verdict events sync. Screenshots come only from the accessibility channel (v1.26.0: the screen-recording channel was removed entirely — no recording permission is ever requested; with accessibility off, the guard simply does not sample).
- **Calendar panel** — a day view that opens inside the Schedule page beside the task list itself (the bottom tabs stay reachable): three lanes — a narrow Google Calendar lane (view-only reference; scheduling does not work around it), the day's plan lane (widest), and a narrow actual lane; drag tasks straight from the list into the calendar to plan them, long-press plan blocks to drag them to a new time, and an "AI Re-plan" button that has the built-in AI draft a new plan for the rest of the day on the spot — it only lands when you tap Apply. Blocks read in a fixed order (v1.26.1): the task name first (up to two lines), then its priority tag, then the start–end time as small grey print; short blocks drop the time before ever dropping the name.
- **Account and realtime sync (Supabase)** — sign in with an email account and tasks sync in realtime between devices; merges align by task id with newest-wins and tombstone deletes, so nothing resurrects and nothing is silently overwritten. There is a **single logical timer across devices**: state lives in the cloud, both ends render it, and either end can control it. A task written with an `autoStart` flag starts its own countdown as soon as it arrives. (The legacy Google Drive file bridge was retired and removed in v1.26.0; Supabase is the only sync path.)
- **Per-task alarms** — any task can have an alarm time (it rings whether or not a timer is running; a daily task re-arms for the next day) and/or ring when its timer ends, before the end-of-task check-in. The default sound is a clear nature recording that fades in from silence to full volume over 60 seconds, played on the alarm audio stream, so it rings even when the phone is muted.
- **Start-time reminders** — when a task's planned start time arrives, a full-screen reminder pops up: start now, snooze 10 minutes, or ignore.
- **Bilingual UI and themes** — Chinese / English interface and light / dark themes, switchable from the drawer or settings. The top bar stays minimal: a drawer (account, language, theme, help, settings) on the left, the calendar on the right.
- **Help page** — the full manual lives inside the app, including the design rationale and an explicit list of things considered and deliberately not built.

The UI is native Java/Views, layered as TaskStore (data) / SyncManager (sync) / TimerService (timing) / UI, so the data format stays stable across implementation changes. The full design research (in Chinese) is in [docs/RESEARCH.zh.md](docs/RESEARCH.zh.md).

## For new users: what the AI can and cannot do

KnowDo's AI comes in two parts, and the difference matters:

- **The built-in chat works out of the box.** It runs on the project's free model channel, and every AI button inside the app uses this same built-in chatbot for all users — there is no special channel for the author; in the app, the author uses the same AI as everyone else. The calendar's "AI Re-plan" is built-in too: it drafts a new plan on the spot, and the plan only lands when you tap Apply.
- **The patrol features are the author's personal AI setup, not app features.** Filing tasks dictated in a conversation outside the app, watching the cloud for a chance to re-plan, and classifying per-task app lists are done by the author's own assistant, patrolling the author's own cloud account in the background. For new users, bluntly: the built-in chat is the one and only AI you can connect to — there is no other option, and there is currently no way to connect your own external assistant. It cannot enter this app's cloud database, and no patrol is running for you. App-list classification is the same story: lists are generated in the background for the author's account only; other accounts, for now, use the built-in generic blacklist, local auto-planning, on-device guard verdicts, and the built-in chat.
- **Your data is isolated per account at the database level.** Your tasks, lists and records live only under your own account. The author cannot access them, and neither can the author's assistant; nobody's AI can read anyone else's data. That is enforced by the database, not by a verbal promise.

## Changelog

The full history is in [CHANGELOG.md](CHANGELOG.md) (Keep a Changelog format; every release commit is tagged, and the version shown in the app carries the build commit's short hash). Latest release:

### v1.26.1 — 2026-10-27

**Why:** Calendar blocks used to lead with the time range and squeeze it together with the task name into two shared lines, so the name was often cut off entirely (user report, with a screenshot) — the settled reading order is: task name first and as complete as possible, then the priority level, and the time only in small print. Build commit `26249ee` · [compare v1.26.0...v1.26.1](https://github.com/gty3310/knowdo/compare/v1.26.0...v1.26.1) · full notes in [CHANGELOG.md](CHANGELOG.md).

**Improved**
- **Calendar blocks lead with the task name.** Both the plan and actual lanes (one shared renderer) now stack: the task name on top (bold, up to two lines on tall blocks, taken verbatim from the data layer — no time prefix), the v1.26.0 priority tag under it (shapes unchanged), and the start–end time as 9sp grey small print on the last line (narrow lanes keep the start-only form). Short blocks yield in the same order: at 34–47dp the tag and the time share a row; at 26–33dp only the tag stays; below that only the name — a block showing a time but no name can no longer occur. Nothing else changed: no semantics, schema or permission changes. Not yet verified on a real device: name/tag/time legibility across light and dark blocks at the user's font size, and the short-block tiers on very brief tasks.

### v1.26.0 — 2026-10-27

**Why:** With apps sorted per task up front, the guard's blacklist needs no guessing and reacts fast, and the whitelist stops false blocks; this release also clears out two pieces of legacy (the old sync bridge and the recording permission) and states the AI's boundaries once and for all. Build commit `8046afd` · [compare v1.25.0...v1.26.0](https://github.com/gty3310/knowdo/compare/v1.25.0...v1.26.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Per-task app lists.** When you start a task that has no list yet, the app sends the task name, its note and your frequently used apps (launchable apps ∩ usage stats, top 100 by the last 30 days; by name without usage access) to the cloud as a generation request, and the author's own AI assistant classifies them into white / grey / black for that task. Black apps pop the guard inquiry the moment they open during the task (3-minute de-dup per app per task, never during a call); grey adds no new interruptions; white exempts the app from focus-rule inquiries for that session. Until a list exists, a built-in generic blacklist stands in (Douyin, Kuaishou, Xiaohongshu, Weibo, Bilibili, Taobao, Pinduoduo, JD, Honor of Kings, Game for Peace and their lite variants — black tier only). Open a task's details to view its list in three tiers with a one-line reason each, and tap any app to re-tier it: manual edits are pinned per package, synced back and never overwritten. A Settings → Guard toggle (on by default) turns the whole feature off. For now the classification service only covers the author's account; other accounts get the generic blacklist (see "For new users" above).
- **Four priority tiers, with tags on calendar blocks.** Priorities are now Must, Important, Secondary and Leisure (old data needs no migration). Auto-planning fills Must → Important → Secondary at duration × 1.5 with the 22:30 cut-off; the top time account still counts Must only; overnight roll-in never upgrades Important and never touches Leisure; Leisure is never auto-scheduled at all, and surfaces as a "For rest time" group on the next-task page when you choose a break. Every calendar block carries a small tag under the task name, same size and place for all four tiers, told apart by shape alone: solid vermilion with white text for Must, a thin ink outline for Important, plain light grey for Secondary, a dashed grey outline for Leisure (on dark blocks the outline/text switch to the rice-white family; the solid red never changes).
- **Help gains a section for new users, "What the AI can and cannot do",** stating the boundaries in plain words (also summarised above).

**Improved**
- **"AI Re-plan" runs on the built-in AI.** The calendar button stays, but it now drafts the re-plan on the spot through the in-app chat backend under the standing rules (×1.5, nothing past 22:30, past and finished blocks untouched), as an apply-or-discard proposal. Nothing is written to any external patrol queue anymore.
- **No user-visible copy names an outside assistant anymore** — interface, settings, help, changelog, empty states and error messages were all reworded or trimmed.

**Fixed**
- **The morning gate now actually re-pops every 5 minutes.** Root cause, confirmed in code: the repeat loop was never implemented — the gate was pulled at most once per process session, and the only re-pull was the one-shot snooze alarm. An unfinished check-in with no chosen pause length now re-arms a 5-minute alarm chain that brings the page back full-screen (through the usual dual channel, never during a call) until you submit it or pick a pause length; answers keep autosaving as before.

**Removed**
- **The Google Drive bridge, entirely.** The retired bridge's remaining code — the file-based sync implementation, its 30-second polling mode, the file picker entry points and every mention in settings and account copy — is gone; sync is cloud-only (the read-only Google Calendar cache the author writes into the cloud account is untouched).
- **MediaProjection, entirely.** The recording channel's classes, permission and wiring are deleted; guard screenshots come only from the accessibility channel (same 15–45s random sampling, same backoff), and the system "record one app or the whole screen" dialog can never appear again.

Guard verdicts and timer semantics are unchanged apart from the points named above; no schema changes. Not yet verified on a real device: the app-list round trip, the blacklist instant inquiry, the morning 5-minute re-pop, the in-app re-plan proposal flow, sync after bridge removal, a full guard day with no recording dialog, and the four-tier planning order with calendar tags.

### v1.25.0 — 2026-10-27

**Why:** Being judged wrong with nowhere to appeal is the one thing this button exists for — it has to be reachable every single time. Build commit `47041ec` · [compare v1.24.5...v1.25.0](https://github.com/gty3310/knowdo/compare/v1.24.5...v1.25.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**Fixed**
- **The guard inquiry page's "Analysis error" button sometimes never appeared (user report, on a real device).** Root cause, confirmed in code: `GuardActivity` set a bare `LinearLayout` as its content view with no `ScrollView` anywhere. The page stacks a title, a multi-line explanation, the aspiration line, two self-check questions (2 + 6 chips), the content mode's grace-action row, and seven buttons — roughly 1,000dp tall at default font size and ≈1,200dp on a 360×640dp screen at font scale 1.3. Anything past the screen's edge was simply clipped, with no way to scroll to it — and "Analysis error" sits second from the bottom. Because the total height varies with the task name, the detection detail's length and which optional rows are present, the button came and went — matching the report exactly. The root layout now lives in a `ScrollView` (`fillViewport`, so short pages look unchanged; `SOFT_INPUT_ADJUST_RESIZE` so the keyboard never covers the input), and every button is reachable in every content combination. "Analysis error" and "False alarm, let it pass" stay adjacent as a group right after the grace buttons, and after tapping "Analysis error" the page scrolls itself to the rule input once layout and keyboard have settled, so Save / Skip are always on screen.
- **The same flaw on the "My guard rules" page.** It, too, was a bare `LinearLayout` with no scrolling: rules are capped at 100, and on a small screen or at large font sizes roughly the ninth rule onward was pushed off screen — unreachable and undeletable. It now scrolls end to end as well.

This release also includes a full code-level walk-through of every user-reachable scenario (fullscreen-page reachability, both inquiry modes end to end, the timer state machine, idle and morning gates, every guard-pipeline layer's degradation, chat proposals, sync merging, upgrade/first-run) — everything else checked out and is unchanged. Guard verdicts and timer behaviour are untouched; no new permissions, no Supabase schema changes. Not yet verified on a real device: scrolling an actual inquiry to the bottom (including at maximum system font size), the auto-scroll into the rule input with the keyboard up, and scrolling/deleting late entries on a long rules list.

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
