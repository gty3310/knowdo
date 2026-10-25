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

### v1.21.0 — 2026-10-25

**Why:** After reading the Truple research, the user asked three things in a row — whether sampling could be as dense as Truple's (about one frame every 30 seconds), how much battery that would cost, and two power-saving rules of his own (an unchanged frame doesn't count; read the text already on screen before running the expensive analysis). Underneath it all, verdict quality had never been checkable with his own eyes: when the guard never prompted, nobody could say whether there was no drift or whether sparse sampling plus the two-hit confirmation had simply missed it. The same research pinned the screen-capture channel's top cause of death on Pixel to institutional limits (one grant per session, projection stops on lock screen). This release answers all of it at once: an accessibility one-shot screenshot channel, denser sampling, and a test mode the user can verify by hand. Build commit `28bb5dd` · [compare v1.20.0...v1.21.0](https://github.com/gty3310/knowdo/compare/v1.20.0...v1.21.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **Accessibility screenshot channel (the new primary).** A new `KnowDoAccessibilityService` (declared per spec: `BIND_ACCESSIBILITY_SERVICE`, `canTakeScreenshot=true`, `canRetrieveWindowContent=true`, window-state events — all verified in the shipped APK) captures one-shot screenshots via `takeScreenshot()`: the `HardwareBuffer` is wrapped and immediately copied to a software bitmap (ML Kit and ONNX cannot read hardware bitmaps), then closed. The sampling loop now lives in the accessibility service, which the system rebinds after a process kill — on reconnect mid-timer it resumes sampling and stops the old channel so the two never sample at once. Failures branch by error code: interval-limit backs off and retries once; secure windows follow the existing black-frame rule (logged as invisible, never judged); loss of accessibility access logs once and stops for the session. The MediaProjection channel stays as an automatic fallback (no accessibility → old grant flow), and both channels share the single `PipelineAnalyzer`/`VerdictEngine` — there is no second implementation of analysis or verdicts. The Permissions page gains an accessibility status row with a jump to the system settings.
- **Guard test mode — the user's own acceptance tool.** A new switch in Permissions → Screen-content guard (off by default; it first guides you to enable accessibility and the overlay permission if either is missing). While on, every 10 seconds it borrows a frame from the accessibility channel, runs the exact production pipeline, and renders the reading into its own draggable floating window: time, foreground app, the text it read (node text or OCR, first ~120 chars), the verdict (category + confidence, or "no drift" / "no drift (task-related)"), the evidence, the frame status (analyzed / unchanged, skipped / secure window / capture failure), and the consecutive-hit streak in the same terms the real engine uses (second hit confirms; NSFW confirms on the first when one-shot is set). Test mode works with no timer running, calls only the analysis layer — never the verdict engine's action path, so it can never pop the inquiry page, jump back, or grant grace — and never interferes with a live guard session. The ✕ on the window turns it off.

**Improved**
- **Denser sampling, both channels.** The in-timer sampling interval drops from a random 45–170 seconds to a random 15–45 seconds (about 30 on average, Truple's density): a brief drift can now produce the two consecutive same-category frames confirmation requires instead of falling between samples entirely.
- **Unchanged frames skipped; analysis ordered cheap-first.** A new `FrameUtil` 8×8 average-hash lets every path (both channels and test mode) skip analysis when the frame is identical to the previous one; the existing black-frame check moved into the same shared helper. The pipeline order is now: exemptions → foreground-app category (free) → accessibility node-text pre-screen (visible window text, keyword hits conclude immediately) → NudeNet image check → text rules. The text rules were factored out of the OCR analyzer (`judgeKeywords` / `relevantToTask` / `judgeText`) so node text and OCR share one keyword table: when node text is plentiful (≥30 compact chars) the full rules — including the "unrelated" verdict — run on it directly with no OCR at all; OCR is only the fallback for text-poor screens. NSFW priority is preserved: any frame the package and keyword stages leave undecided still goes through NudeNet, and the "task-related" pass takes effect only after NudeNet, so adult content on a page that happens to contain task words is not waved through.
- **Sign-up email note, corrected in place.** The account dialog now says the confirmation email arrives from a tiguo.net address (sender `noreply@tiguo.net`) — matching the Resend + Supabase custom-SMTP path that went live the same day — instead of naming Supabase as the sender.

The privacy line is unchanged: screenshots are still analyzed in memory only, never saved, never uploaded — test mode included; it displays text results only. No Supabase changes, no new network calls. Not yet verified on a real device: the accessibility enable flow, channel hand-off, test-window readings, and a battery A/B (one hour each with dense sampling on/off) are still to be run.

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
