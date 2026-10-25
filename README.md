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

### v1.24.2 — 2026-10-25

**Why:** Guard test mode is the most direct tool you have for verifying the guard's verdicts on the spot — while it is broken, there is no way to see with your own eyes whether the guard judges rightly. Build commit `e2eb395` · [compare v1.24.1...v1.24.2](https://github.com/gty3310/knowdo/compare/v1.24.1...v1.24.2) · full notes in [CHANGELOG.md](CHANGELOG.md).

**Fixed**
- **Guard test mode no longer opens to a full white screen.** Root cause, pinned to the code: `GuardTestPickActivity` — the ruler picker added in v1.21.3 — never called `setContentView`. Its `onCreate` built the entire view tree (white root layout, instructions, task buttons, the temporary-task form) and never attached it to the window, so the activity showed nothing but the app theme's (`Theme.Material.Light.NoActionBar`) white window background. Tapping the test-mode switch in Settings passed every pre-check (accessibility channel on, overlay permission granted) and launched the picker — into a full-screen blank: no ruler could be picked, sampling never started, and the test window never appeared. The picker now attaches its root view first and builds the rest inside a try/catch: any failure is reported through `ErrorReporter` (visible with Tester mode on) and the page falls back to a plain message plus a Back button instead of a silent white screen; a failed test-mode start likewise reports and stays on the page instead of pretending success and closing.
- **Test-window hardening.** `GuardTestWindow.show()` no longer returns silently when overlay permission is missing — it reports the reason. `build()` now validates the window parameters before attaching (width must be a positive fixed size narrower than the screen, height must be `WRAP_CONTENT`, background must not be opaque white) and aborts with a report rather than ever covering the screen; an `addView` failure is reported instead of swallowed.

Guard verdict logic and timer behaviour are untouched (only the test mode's display path changed); no new permissions, no Supabase schema changes. Not yet verified on a real device: the picker appearing after tapping Guard test mode, the test window coming up after a ruler is picked with readings refreshing every 10 seconds, and a clean restore with no white residue after leaving test mode; the failure-fallback page and the parameter check are defensive paths not deliberately triggered on a device.

### v1.24.1 — 2026-10-25

**Why:** When something breaks and the cause stays invisible, all anyone can do is guess. Show the error code and you can pinpoint it yourself — and I can fix the actual problem straight from the code you report. Build commit `1d48906` · [compare v1.24.0...v1.24.1](https://github.com/gty3310/knowdo/compare/v1.24.0...v1.24.1) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Tester-mode error overlay (a central `ErrorReporter` channel).** Modules report internal errors to one place — source (Chat / Guard verdict / Sync / Model download / Auto-schedule), HTTP status when there is one, and a short reason (the proxy's `error` field, or an exception class + message digest, truncated to 120 chars). It does anything at all only while Tester mode is on; with the switch off, `report()` is a complete no-op and every existing friendly message and silent degradation behaves exactly as before. Error details appear on this device's screen only — never persisted locally, never written into any field that syncs to the cloud — and the channel itself can never throw. Display is a small overlay near the top of the screen for about five seconds (WindowManager overlay when overlay permission is granted, so background failures like guard verdicts are visible even while another app is in front; otherwise a banner attached to the foreground activity's decor view; if neither is available the report is dropped): line one is "source · HTTP status", line two the reason (at most two lines), styled like the guard test window (dark warm charcoal, cream text). A newer error replaces the older one and restarts the timer; tapping dismisses immediately. The overlay hides itself during guard-test-mode frame captures, like the test window does, so its own text never pollutes a reading.
- **Chat is wired end to end (the priority path).** Non-2xx responses report their status code; a proxy JSON error reports its `error` field (falling back to `message`, then the friendly text) — e.g. `all_models_unavailable` / `proxy_not_configured`; an empty response body reports the status; an SSE stream that breaks mid-read reports the exception digest; a stream that ends with no content and no `[DONE]` reports exactly that; connect failures and timeouts report the exception digest. The friendly user-facing wording is unchanged. Also wired: the semantic judge's cloud call (status code, the `fallback:true` reason, network digests — success and backoff behaviour untouched, and failures are naturally throttled by the existing backoff); Supabase sync (`SbSync.syncNow` — `Supa.Err` status, network vs HTTP distinguished, other exceptions digested); model downloads (the `err` reason, which already carries the HTTP code for download failures, plus the unconfigured-source case); and auto-schedule write-backs (both `autoFillSchedule` implementations and the scheduler's catch-all, including the bridge mode's rollback-on-write-failure). Rollover has no silent network write-back of its own — it persists locally and pushes through sync, which is covered.

**Improved**
- **The diagnostics switch is renamed "Tester mode".** Settings row and toasts, the chat setting-proposal card name, the chat system prompt and the manual digest now all say Tester mode (formerly "Test mode"); the internal key `diag_mode` and all existing diagnostic behaviour are unchanged, the Settings description now also mentions the error overlay, and the in-app changelog's v1.21.4 entry was renamed along with a note of the former name. Not to be confused with guard *test mode* (`test_mode`) — a different feature, not renamed.

Guard verdicts and timer behaviour are untouched; no new permissions, no Supabase schema changes. Not yet verified on a real device: the overlay's actual rendering on the task page, Chat tab, end page and inquiry page, its five-second dismissal and tap-to-dismiss, the activity-banner fallback without overlay permission; the exact overlay content for each chat failure branch (401/429/500, `all_models_unavailable`, `proxy_not_configured`, timeout, broken or empty SSE) on a real phone; the semantic-fallback, sync-failure, model-download and auto-schedule reports; and that with Tester mode off everything behaves exactly as before.

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
