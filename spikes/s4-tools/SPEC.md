# Spike S4 — Tools: spec, plan and test protocol

Issue: [#8](https://github.com/SaadAhmed1122/bibi-ai/issues/8) · Milestone: P0 · Feature doc: [`docs/features/tools-permissions.md`](../../docs/features/tools-permissions.md) · PRD 0.7 (S4), 0.8, 0.9, 0.11, §13.5, §16.1–16.6

> Spike code is **throwaway**. It answers one question with numbers. Nothing here goes into the P1 codebase without a rewrite (P1 uses Compose, MVI and the module layout of PRD §11.4).

## 1. Question
Can Bibi run the five MVP actions (open app, set alarm, set timer, flashlight, dial with a prefilled number) through **one Permission Engine**, both from a **direct shortcut** and from a **structured tool call**, with the shortcut path **under 300 ms**?

## 2. Pass / fail (PRD 0.7)
| Check | Pass |
|---|---|
| Shortcut path timing | For each of the five actions, 20 benchmark runs on the shortcut path, all successful, **p95 < 300 ms** (so p50 < 300 ms too) |
| Functional, shortcut path | Each action works for real from a quick command (3 real launches per action, owner checks the result on screen) |
| Functional, tool-call path | Each action works through the tool-call path (parse → validate → one repair → Permission Engine → executor). **With the fake LLM only**: the real model path waits for S2 [OPEN] |
| Safety | Dial always shows a confirmation card; with "untrusted content" on, every action needs confirmation; a timeout cancels |

The results panel prints `S4 shortcut gate: PASS`, `FAIL` (some action's p95 ≥ 300 ms) or `INCOMPLETE` (fewer than 20 successful runs for an action, a failed attempt, or an action missing).

**If it fails:** simplify intent building and precompute the app list (PRD 0.7). The app list is already precomputed here, so a failure points at intent resolution or the handler app.

## 3. What is timed
All times come from `SystemClock.elapsedRealtimeNanos()` on the main thread. `t0` is the moment the Submit or quick-command tap handler starts.

| Column | From → to |
|---|---|
| `parse_ms` | `t0` → `ToolCall` produced (shortcut matcher, or fake LLM + JSON parse + validation + repair) |
| `policy_ms` | → Permission Engine decision (includes resolving an `open_app` name on the precomputed launcher list for the confirmation text) |
| `build_ms` | → intent built by `ToolExecutor` (for `open_app`: `getLaunchIntentForPackage`) |
| `dispatch_ms` | → `startActivity()` returned (**real**), or `resolveActivity(…, MATCH_DEFAULT_ONLY)` returned (**dry run**), or `setTorchMode()` returned (**torch**) |
| `total_ms` | `t0` → dispatch, except **torch**: `t0` → the matching `onTorchModeChanged` callback |

- **Gate rows** are the benchmark's shortcut-path rows: `dry_run` for open_app, set_alarm, start_timer and dial; `torch` for the flashlight (20 alternating on/off toggles, real).
- **Why a dry run:** a real alarm, timer, dialer or app launch leaves the spike app, so it can't be repeated 20 times unattended. The dry run does everything except the launch: it builds the same intent and resolves it the way `startActivity` would. It doesn't include the system's activity start or the other app drawing its first frame. With several handlers and no default, `resolveActivity` can return the system resolver instead of the final app (PackageManager reference).
- **Real launches** (3 per action, by the owner, kind `live`, mode `real`) measure `t0` → `startActivity()` returned. That's the time until Android accepted the start, **not** until the other app is visible. They're reported as info, not gate rows.
- **Dial and confirmation:** in the benchmark the engine's confirmation is answered by the code at once (`auto_confirmed`), so no human time is counted. In live use two numbers are logged: `confirm_shown` (`t0` → dialog shown) and, after the tap, a new `t0` at the tap → dispatch (`after_confirm`).
- **Not timed:** CSV writes happen after each measurement. The launcher list is indexed once at start on a background thread (`app_index` row, with its duration).
- Benchmark runs are 150 ms apart, so each starts from an idle main thread. The first run of each action is included (cold path), not dropped.

## 4. What the spike app does
- **Main screen** (plain Android views, no libraries): a text box with **Submit**, quick-command buttons (`open settings`, `wake me at 7:30`, `set a timer for 5 minutes`, `flashlight on`, `flashlight off`, `call 0123 456 789`), and four switches:
  - **Fake LLM path:** text goes through `FakeLlm` (fixed mappings) instead of the shortcut matcher.
  - **Untrusted content in context:** adds a simulated `NOTIFICATION` provenance, so every action is upgraded to Sensitive.
  - **Dry run:** resolve, don't launch.
  - **Disable all actions:** the kill switch (PRD §16.4).
- A status line lists the launcher app count and which app handles `SET_ALARM`, `SET_TIMER`, `DIAL tel:`, and whether there is a flash. This answers the "is SET_ALARM enough on Samsung" [VERIFY] question on each phone.
- Buttons: **Benchmark**, **Refresh results**, **Share log (CSV)**, **New run file**. The results panel shows p50/p95 per (kind, action, path, mode) and the gate line.

**Pipeline (one path for every action, PRD §16.2):** text → `ShortcutParser` *or* `FakeLlm` → `ToolRouter` (strict JSON parse + validation, one repair message, then a clarifying question) → `PermissionEngine` → confirmation dialog if needed → `ToolExecutor`.

**Pure-Kotlin core** (no `android.*` imports; JVM tests in `tools/core-test/`):

| File | Role |
|---|---|
| `ToolCall.kt` | The five tools, risk classes, typed calls, log redaction (numbers keep the last 2 digits) |
| `ToolValidator.kt` | Ranges from the AlarmClock reference: hour 0..23, minute 0..59, timer 1..86400 s. Literal phone numbers only: optional `+`, then 3..15 digits; no `*`, `#`, letters or URIs. App name or package-name pattern |
| `ShortcutParser.kt` | Anchored English matcher (timer, alarm, flashlight, open, call/dial a literal number). Negations and compound sentences don't match |
| `MiniJson.kt`, `ToolCallParser.kt` | Strict JSON. `{"tool": …, "args": {…}}`, optionally inside `<tool_call>…</tool_call>`. Unknown tools or keys, wrong types and out-of-range values are rejected. Has the repair prompt and `ToolRouter` (at most one repair) |
| `PermissionEngine.kt` | §16.4 policy and the confirmation lifecycle |
| `AppResolver.kt` | `open_app` target → an app on the precomputed launcher list only |
| `FakeLlm.kt` | Stand-in model until S2 |
| `Summary.kt` | p50/p95 (nearest rank), gate verdict |

**Android layer:** `ToolExecutor` (intents built only from validated arguments), `TorchController`, `AppIndex` (launcher list via `<queries>`), `SpikeLog` (CSV), `MainActivity`.

**Tool schema the model sees** (`ToolCallJson.SCHEMA`):
```
open_app {"app": string}            app name or package name
set_alarm {"hour": 0..23, "minute": 0..59}
start_timer {"seconds": 1..86400}
flashlight {"on": true|false}
dial {"number": string}             literal number; the user presses Call
```

**Permission policy as built:**
| Case | Result |
|---|---|
| SAFE (open_app, set_alarm, start_timer, flashlight), trusted context | Execute |
| SAFE with untrusted provenance (`NOTIFICATION`, `FILE`, `WEB`) | Upgraded to SENSITIVE → confirmation |
| SENSITIVE (dial; PRD open item 8 default) | Confirmation card with the normalized number; nothing is called until the user presses Call in the dialer |
| SENSITIVE while the device is locked (`KeyguardManager.isDeviceLocked`) | Refused, also if it locks between the card and the tap |
| SAFE while locked | Executed. The four SAFE actions only start a UI or toggle the torch. Whether SAFE actions should also be blocked while locked is [OPEN] |
| CRITICAL | Refused (identity verification isn't part of this spike; no S4 tool is CRITICAL) |
| Invalid parameters, or kill switch on | Refused |
| Confirmation | Single use, one call per confirmation, **30 s timeout cancels**, a late tap is refused |

**Platform choices (checked on developer.android.com, 2026-10-29):**
- *Common intents → Alarm clock* and the *AlarmClock* reference: `ACTION_SET_ALARM` (`EXTRA_HOUR`, `EXTRA_MINUTES`, `EXTRA_MESSAGE`, `EXTRA_SKIP_UI`) and `ACTION_SET_TIMER` (`EXTRA_LENGTH` 1..86400). **Both need `com.android.alarm.permission.SET_ALARM`.** The feature doc's table says the timer needs no permission; the reference says it does.
- *Common intents → Phone* and the *Intent* reference: `ACTION_DIAL` with a `tel:` URI; no permission; the user taps Call. `ACTION_CALL`/`CALL_PHONE` are not used. The URI is built with `Uri.fromParts("tel", number, null)`.
- *CameraManager* reference: `setTorchMode(cameraId, enabled)` doesn't open the camera, needs no permission listed, throws `CameraAccessException`/`IllegalArgumentException`; `registerTorchCallback(Executor, TorchCallback)`; flash found via `CameraCharacteristics.FLASH_INFO_AVAILABLE`.
- *`<queries>` element* and *Declaring package visibility needs*: `<intent>` entries for `MAIN`+`LAUNCHER`, `SET_ALARM`, `SET_TIMER`, `DIAL` with scheme `tel`. **No `QUERY_ALL_PACKAGES`.**
- *PackageManager* reference: `getLaunchIntentForPackage`, `queryIntentActivities(Intent, ResolveInfoFlags)`, `resolveActivity(Intent, ResolveInfoFlags)` with `MATCH_DEFAULT_ONLY`.
- *KeyguardManager* reference: `isDeviceLocked()` (secure lock only; a swipe lock counts as unlocked).
- Context7 was unreachable from this environment, so these official pages were used instead.

**Not used (PRD 0.9):** SMS, call log, contacts, Accessibility, `CALL_PHONE`, `QUERY_ALL_PACKAGES`. "Call mom" doesn't match; only literal numbers are dialed.

## 5. Plan (one commit per task)
1. Gradle skeleton and manifest (AGP 9.0.1 with built-in Kotlin, the Gradle 9.1.0 wrapper from S1, compileSdk/targetSdk 36, minSdk 34, no dependencies).
2. JVM tests first (PRD 0.11): shortcut phrases, tool-call parser and validator, Permission Engine table and lifecycle, app resolver, summary.
3. Pure-Kotlin core until the tests pass.
4. Android layer: executor, torch, app index, CSV log, screen, benchmark, confirmation dialog.
5. SPEC.md and README.md.

## 6. Test protocol (owner runs on the device)
Do it on the **vivo Y17s**, and on the **Galaxy A23** when available (it is the floor device). Steps are in [README.md → Owner test steps](README.md#owner-test-steps).

## 7. Result template (goes into `results/RESULTS.md`, `docs/project_status.md` → Spike results, and issue #8)
| Device | Action | Path | Mode | n | Failed | p50 ms | p95 ms | Verdict | Notes |
|---|---|---|---|---|---|---|---|---|---|

Also record: the handler line (`SET_ALARM → …` etc.), the launcher index time, and for the real launches whether each action did the right thing on screen. Note whether the alarm and timer were set without UI (`EXTRA_SKIP_UI`) or the clock app showed a screen. For the tool-call path, whether each action worked through the fake LLM, and the safety checks in steps 6–8 of the owner steps.

## 8. Known limits of this spike
- **Not built or run yet.** The container can't reach Google's Maven repository, so this was only type-checked against the API 36 framework classes (Robolectric `android-all`) with the Kotlin 2.0.21 compiler, and the pure-Kotlin core was tested on a plain JVM. The owner's Android Studio build is the first real build.
- **The LLM path uses a fake model.** It shows that parsing, validation, repair and policy work. It says nothing about whether a small int4 model emits valid calls or how long that takes. The 20-prompts-per-action LLM measurement in the feature doc's test plan waits for S2 [OPEN].
- Dry-run timing ends at intent resolution, and real-launch timing ends when `startActivity()` returns. Neither includes the other app becoming visible.
- `EXTRA_SKIP_UI` is set, so real alarm/timer launches create real alarms and timers labelled "Bibi S4 test". Delete them afterwards. Whether each clock app honors `SKIP_UI` is [VERIFY on device].
- Alarm phrases without am/pm are taken as 24-hour ("alarm at 6" → 06:00). Asking instead is [OPEN].
- App-name matching (exact label → alias → unique prefix → unique word) and the alias package list are first guesses [OPEN]/[VERIFY]. vivo's own apps aren't in the alias table.
- Asking the flashlight for the state it is already in isn't timed (no callback to wait for). That attempt is logged as failed.
- Plain views, main thread only, no `:inference` process. P1 architecture is not tested here.
