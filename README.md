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

### v1.20.0 — 2026-10-25

**Why:** The user circled three areas on real-device screenshots of the calendar and asked for it to become "truly simple"; he also asked for Google-Calendar-style tap-to-create and for dragging a plan block across the now-line to toggle completion. Plus the batch settled the same day: gentler factory defaults for new users, a confirm-password field, and a plain explanation of where the verification email comes from. "Ask AI to re-plan" was first circled for deletion, then settled the same day as kept — with its pipe swapped.

**Improved**
- **The calendar is just the timeline now.** The header block (today's title, done/todo counts, free-minutes line, completed list) and the unscheduled-tasks list below the calendar are gone. What remains: the three-lane timeline (Google Calendar / Plan / Actual), the now-line, and plan blocks — bottom tabs and the split-screen task list are untouched.
- **"Ask AI to re-plan" stays — on a new pipe.** The button moved out of the deleted header into a small, quiet text button at the calendar's top-right. It no longer goes through the retired Drive bridge: tapping it writes `reschedule_request = {date, requested_at, status: "pending"}` to `user_state` in Supabase; the button turns into a disabled "Re-planning…", and the app watches the same field through its existing user_state sync/realtime — `done` brings a quiet "Your day has been re-planned" toast, `failed` shows the reason, and the new schedule itself arrives through the existing `schedule` sync as before. The column is probed on every sync (the established additional-column discipline): until it exists in the database, the app never touches the key and the button simply reports "not ready yet".
- **Gentler factory defaults — fresh states only.** With no stored `idle_nag` preference locally and none in the cloud, the fallback defaults are now Standard profile, at most 1 round, sound off, vibration off: one full-screen reminder round, then quiet; timer-end reminders stay silent by default too. Anyone with a stored preference or a cloud value keeps exactly what they had — upgrades reset nothing, and everything can still be cranked back to strict in Settings.
- **Sign-up asks twice.** A confirm-password field joins the account dialog: mismatched passwords are flagged inline and block submission (the dialog stays open); the login path ignores the field. A standing note under the form explains that the verification email is sent by the database service (Supabase), may come from an unfamiliar sender, and should be looked for in the inbox and the spam folder.

**New**
- **Tap empty calendar space to create.** A light tap on empty space in the Plan lane (blocks keep their own tap/drag behaviour; scrolling is unaffected) opens the task editor with the start time prefilled to the tapped slot (5-minute snap). Saving puts the task in the list and plants a plan block at that time (length = duration × 1.5, through the existing sanctioned `addScheduleBlock` write path).
- **One editor sheet everywhere.** Creating and editing tasks converge on a single bottom sheet — calendar tap-to-create, the home FAB (replacing the old name-only dialog), and the Edit action on both home and calendar (the old edit menus and the four dialogs they duplicated are removed; cell-tap field editing, notes, the alarm dialog and subtasks remain). Fields: name; date & start time (lands on the existing deadline + nextStartAt model); length (locked when subtasks define it); importance (must / secondary); repeat (none / daily — the only two the model has); context tags (multi-select); and a per-task alarm mode (next bullet). Saving follows the fresh-copy discipline and reschedules start reminders; cancelling writes nothing.
- **Per-task alarm mode.** Four modes — Follow global (default), Sound, Silent, Off — governing only this task's end-of-timer and pause call-back sounds; idle nagging still follows the global setting, and the standalone `alarmAt` alarm clock is out of its scope. One decision point at timer end (silent/off never ring even with ring-on-end enabled; sound always rings even without it; follow keeps the old switch behaviour) and one at the pause call-back escalation ring. Stored as `alarmMode` on the task (always present in the local task JSON) and wired for a formal `alarm_mode` column with the established probe-and-preserve discipline: once the column exists in Supabase it syncs automatically; until then sync merges preserve the local value and never lose it, and older clients and the Mac app are unaffected.
- **Drag across the now-line to complete / un-complete.** When a plan-block drag lands and the time write succeeds: a block ending at or before now completes its task through the exact same write path as ticking it done (daily tasks record `doneDates`); a completed task whose block starts at or after now is un-completed the same way; a block straddling the line changes nothing — with a small toast either way. Completed blocks are draggable now (that is what makes dragging back possible); a plain tap still expands the task. Google Calendar blocks are never draggable and never completed.

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
