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

### v1.22.0 — 2026-10-25

**Why:** A task undone is often not about willpower — the task itself was never thought through: too big, too vague, or at the wrong time. Two minutes of talking it clear first beats gritting your teeth, dragging it along, or blaming yourself. So KnowDo gains an AI you can talk to: it helps you talk a task into shape (it never edits behind your back), and any change it suggests becomes a proposal card that only counts when you tap Apply. Build commit `2705e33` · [compare v1.21.4...v1.22.0](https://github.com/gty3310/knowdo/compare/v1.21.4...v1.22.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**New**
- **"Chat with AI about this task" (ChatActivity + ChatClient).** From a task's edit sheet, one tap opens a full-screen chat that already knows the task — title, note, planned length, deadline/repeat, subtasks, and how much you have already done on it today (context is rebuilt fresh on every send). Say what feels off and the AI proposes one concrete fix at a time — rename, change the length, split into subtasks, move the start — rendered as a proposal card inside the conversation. Apply writes through the exact same path as the edit sheet (fresh copy by id, touch, save, start-reminder reschedule; subtask splits re-derive the total length; daily tasks never gain a one-day deadline); "No, thanks" discards it, and the AI has no path to silently edit a task. Card state (pending / applied / dismissed) is stored with the message, so reopening a chat never double-applies. A second, task-free entry lives in the main page's side drawer and injects only today's task overview. Replies stream token by token; a small line at the top names the model actually answering, and follows it when the server switches models. Chat history is stored only on the phone (per task, plus one general thread, capped at 120 messages each) — never uploaded — and Settings gains a one-tap "Clear AI chats".
- **Server-side chat proxy (code only, not yet deployed).** A new Supabase Edge Function `chat-proxy` (kept outside this repo, mirroring the glm-judge proxy) holds the OpenRouter key in Supabase secrets — it never enters the APK. The proxy caches OpenRouter's model list for an hour, keeps only genuinely free chat models (id ending in `:free` with zero prompt/completion pricing), puts an env-configurable priority list (`OR_PRIORITY_IDS`) first, and tries candidates in order: a 429, 5xx, or timeout moves on to the next model automatically; only when every candidate fails does the app show a friendly error. The first SSE event is a meta frame carrying the model actually used, which the app displays. Deployment waits on the owner putting the OpenRouter key into Supabase secrets; until then, sending a chat message returns a friendly error rather than a reply.

**Improved**
- **Task names get room to breathe.** The name column's default width grows from 150dp to 200dp (dragging any column's header edge to resize, persisted on the phone, already existed and is unchanged — a width you set yourself always wins over the new default). A long name now wraps to at most two lines inside its row instead of being cut after one; expanding the row shows the full name with no line cap, and the edit sheet's name field wraps instead of scrolling sideways, so the whole name is visible while you edit it.

Guard, timer, and task write paths are otherwise untouched; the only new write path is the proposal card's Apply, which reuses the edit sheet's. No new permissions, no Supabase schema changes. Not yet verified on a real device: end-to-end streaming against the (undeployed) proxy, model failover and the model line updating, all three proposal paths (apply lands, dismiss leaves the task untouched, subtask split re-derives the total), per-task history separation and the settings clear, and the chat page in dark mode.

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
