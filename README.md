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

### v1.25.0 — 2026-10-27

**Why:** Being judged wrong with nowhere to appeal is the one thing this button exists for — it has to be reachable every single time. Build commit `47041ec` · [compare v1.24.5...v1.25.0](https://github.com/gty3310/knowdo/compare/v1.24.5...v1.25.0) · full notes in [CHANGELOG.md](CHANGELOG.md).

**Fixed**
- **The guard inquiry page's "Analysis error" button sometimes never appeared (user report, on a real device).** Root cause, confirmed in code: `GuardActivity` set a bare `LinearLayout` as its content view with no `ScrollView` anywhere. The page stacks a title, a multi-line explanation, the aspiration line, two self-check questions (2 + 6 chips), the content mode's grace-action row, and seven buttons — roughly 1,000dp tall at default font size and ≈1,200dp on a 360×640dp screen at font scale 1.3. Anything past the screen's edge was simply clipped, with no way to scroll to it — and "Analysis error" sits second from the bottom. Because the total height varies with the task name, the detection detail's length and which optional rows are present, the button came and went — matching the report exactly. The root layout now lives in a `ScrollView` (`fillViewport`, so short pages look unchanged; `SOFT_INPUT_ADJUST_RESIZE` so the keyboard never covers the input), and every button is reachable in every content combination. "Analysis error" and "False alarm, let it pass" stay adjacent as a group right after the grace buttons, and after tapping "Analysis error" the page scrolls itself to the rule input once layout and keyboard have settled, so Save / Skip are always on screen.
- **The same flaw on the "My guard rules" page.** It, too, was a bare `LinearLayout` with no scrolling: rules are capped at 100, and on a small screen or at large font sizes roughly the ninth rule onward was pushed off screen — unreachable and undeletable. It now scrolls end to end as well.

This release also includes a full code-level walk-through of every user-reachable scenario (fullscreen-page reachability, both inquiry modes end to end, the timer state machine, idle and morning gates, every guard-pipeline layer's degradation, chat proposals, sync merging, upgrade/first-run) — everything else checked out and is unchanged. Guard verdicts and timer behaviour are untouched; no new permissions, no Supabase schema changes. Not yet verified on a real device: scrolling an actual inquiry to the bottom (including at maximum system font size), the auto-scroll into the rule input with the keyboard up, and scrolling/deleting late entries on a long rules list.

### v1.24.5 — 2026-10-25

**Why:** The icon and the palette are KnowDo's face — once settled, they stop drifting; the three colors also fit the house rule of low saturation with a single accent. Build commit `5d7d7e1` · [compare v1.24.4...v1.24.5](https://github.com/gty3310/knowdo/compare/v1.24.4...v1.24.5) · full notes in [CHANGELOG.md](CHANGELOG.md).

**Improved**
- **New launcher icon — the finalized "unclosed circle".** The artwork is the user's own selected brand-board primary icon, cropped as-is (505×505, rice-white background baked in) and only ever scaled and aligned — never redrawn or recoloured. As an adaptive icon, the foreground is a 108dp canvas first filled with the artwork's measured background tone, with the artwork centred on the ring's own centre at 85% of the canvas: the ring lands at ≈67dp across, inside the 72dp safe zone, so neither the ring nor the vermillion dot is clipped under round or square masks; the adaptive background colour is the brand rice white #F7F3EC. Legacy square and round icons ship in all five densities. The manifest icon moves from the system alarm-clock drawable to `@mipmap/ic_launcher` (plus `roundIcon`); there was no in-app brand-image slot anywhere in the UI, and per the minimalism rule none was invented.
- **The whole palette moves to the three brand colors.** Rice white #F7F3EC becomes the base (Ui.bg/surface and every page that hardcoded pure white), ink teal-black #1F2F2A the primary (body text everywhere it was pure black or the old near-black, and the dark theme's ground — mirroring the brand board's dark-background variant), and vermillion #D94B3A the single accent, replacing the old amber/gold accent point-for-point: the central Ui accent in both themes, the warm-paper pages' AMBER constant (IdleAlarm / NextTask / Thanks), the floating window's pause strip and gold state text, the pause card's primary button and selected chips, the subtask panel's running tint, the aspiration text, the calendar's drag-preview highlight, and the guard page's selected chip. Tints were re-derived, not hue-shifted (the running-row background is vermillion ≈12% over rice white). A new platform theme carries the brand into system surfaces: rice-white window background, vermillion `colorAccent` for dialogs' system widgets, ink primary text. Semantic colours are deliberately kept and named: link blue, danger red, green; the guard pages' action colours (green = return to task now, blue = wrap-up seconds); the picker's all-blue rows (the same blue as Ui.link — turning a full page vermillion would *add* accent, against the rule); calendar source/state coding (Google-calendar blues, all-day purples, actual-block greens, mode-selected green); the chat delete card's danger; the rules page's delete tint; habit-type category colours and heatmap data colours. The calendar "now" line keeps its semantic-red job but now uses vermillion itself, the two being the same hue family.

Function logic, guard verdicts and timer behaviour are untouched; no new permissions, no Supabase schema changes. Not yet verified on a real device: the icon under round and square launcher masks, light/dark appearance page by page (especially the dark floating window, pause card and guard test/error overlays for leftover gold or weak contrast), system dialogs under the new theme, and long-reading comfort of ink on rice white.

### v1.24.4 — 2026-10-25

**Why:** This model is the local workhorse of the guard's semantic verdicts — while it can't be downloaded, verdicts have to make do with the cruder rules. Build commit `696c842` · [compare v1.24.3...v1.24.4](https://github.com/gty3310/knowdo/compare/v1.24.3...v1.24.4) · full notes in [CHANGELOG.md](CHANGELOG.md).

**Fixed**
- **Semantic mini-model (bge) downloads no longer stall on "part size mismatch".** Root cause, confirmed server-side: the model host's contents API truncates binary payloads at a fixed point (the same file is cut at the same byte count every time, and re-uploading doesn't heal it — four of bge's twelve parts on the server were short, and the app's size check was correctly rejecting corrupt files), while ASCII text transfers are fully reliable. The server-side parts are therefore now shipped as Base64 text (`.b64` files, with `"part_encoding": "base64"` on the manifest entry; part sizes in the manifest remain the binary sizes). The app decodes twice for entries carrying that marker — the contents API's own Base64 layer, then the text layer — and proceeds with the usual per-part size check, ordered assembly, and whole-file MD5 verification. Entries without the marker (NudeNet, single-file and extra-file downloads) keep the existing single-decode path, byte for byte.
- **A damaged part retries itself.** When a downloaded part's size doesn't match (or the download throws), that part alone is deleted and re-downloaded, up to 3 attempts including the first; only then does the download fail through the existing error path (`err[0]` plus the `ErrorReporter` channel, visible with Tester mode on). A whole-file MD5 mismatch still fails outright — parts carry sizes but no per-part hashes, so there is no way to tell which part is wrong, and there is no unbounded retry. Already-installed models are untouched: the readiness check and verified-mark logic are unchanged, nothing on disk is deleted, and NudeNet's ready state carries over.

Guard verdict logic and timer behaviour are untouched (only the model download path changed); no new permissions, no Supabase schema changes. Not yet verified on a real device: re-downloading the semantic mini-model in Settings → Model downloads and watching it finish with status Ready; the assembled file passing MD5 and actually being loaded by the semantic judge; per-part retry behaviour under real truncation; and NudeNet's ready state and download path showing no regression after the upgrade.

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
