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
| Functional, tool-call path | Each action works through the tool-call path (parse → validate → one repair → Permission Engine → executor) with the **fake LLM** (baseline). The real-LLM path (§2.1) is measured too, as info only |
| Safety | Dial always shows a confirmation card; with "untrusted content" on, every action needs confirmation; a timeout cancels |

The results panel prints `S4 shortcut gate: PASS`, `FAIL` (some action's p95 ≥ 300 ms) or `INCOMPLETE` (fewer than 20 successful runs for an action, a failed attempt, or an action missing).

**If it fails:** simplify intent building and precompute the app list (PRD 0.7). The app list is already precomputed here, so a failure points at intent resolution or the handler app.

### 2.1 Real-LLM path metrics (info only, never part of the gate)
Added by the Decisions of 2026-10-30 in `docs/project_status.md` (Decision Maker router; LiteRT-LM tool calls). The results are **provisional** until the S2 gate and runtime choice are recorded, and they don't decide the S2 runtime [OPEN].

The **LLM benchmark** button runs a fixed set (`LlmBenchPrompts`): 20 free-form prompts per action plus 10 that ask for no action, 110 in total. None of them is caught by the shortcut matcher (a JVM test checks this). It is a **dry run**: each request stops at the Permission Engine decision. A confirmation request is cancelled at once, no intent is resolved or started, no alarm, timer, dialer or torch. Per expected action the results panel prints:

| Column | Meaning |
|---|---|
| `DM acc` | The router's effective choice (Decision Maker label, "none" below the confidence threshold) equals the expected action |
| `tool ok` | A validated call of the expected tool reached the Permission Engine. For "none": no call reached it |
| `json ok` | The model's first answer was a JSON tool call (structured `toolCalls`, or text that parsed as a JSON object), out of the requests where the LLM ran |
| `args ok` | The first answer passed `ToolCallParser` + `ToolValidator` without a repair |
| `args =` | The final call carries the values the prompt asked for (app names compared loosely) |
| `rep` | Calls that needed the one repair |
| `p50 ms` / `p95 ms` | Submit → Permission Engine decision (nearest rank), Decision Maker + Gemma + parsing included |
| `max PSS` | Highest `Debug.getPss()` after a request; the panel also prints the peak with both models loaded |

Every LLM-path request (benchmark or live) adds one row of kind `llm_bench` or `llm_live` with these extra columns, after the unchanged gate columns: `expected, dm_label, dm_conf, dm_ms, route, llm_ttft_ms, llm_ms, tool_name, args_json, source, json_ok, args_ok, repaired, call_tool, args_match, outcome, decision, e2e_ms, pss_kb`. `llm_ttft_ms` is LiteRT-LM's `BenchmarkInfo.timeToFirstTokenInSecond` for the first message; `llm_ms` is the wall time of all `sendMessage` calls (first answer + repair). `args_json` is the first answer as given to the parser, with `,` stored as `;` and dial numbers masked to their last 2 digits. `outcome` is one of `call`, `no_action`, `no_tool_call`, `ask_user`, `rejected`, `error`. Model load times and PSS after loading are one `llm_load` event row.

There is no pass/fail threshold for these numbers [OPEN]; they feed the S2/P1 runtime discussion.

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
- **Main screen** (plain Android views): a text box with **Submit**, quick-command buttons (`open settings`, `wake me at 7:30`, `set a timer for 5 minutes`, `flashlight on`, `flashlight off`, `call 0123 456 789`), and five switches:
  - **Fake LLM path:** text goes through `FakeLlm` (fixed mappings) instead of the shortcut matcher. Kept as the baseline.
  - **Real LLM path:** the shortcut matcher runs first, unchanged. Only on a miss does the text go to the Decision Maker and Gemma (below). Fake and real are mutually exclusive.
  - **Untrusted content in context:** adds a simulated `NOTIFICATION` provenance, so every action is upgraded to Sensitive.
  - **Dry run:** resolve, don't launch.
  - **Disable all actions:** the kill switch (PRD §16.4).
- A status line lists the launcher app count and which app handles `SET_ALARM`, `SET_TIMER`, `DIAL tel:`, and whether there is a flash. This answers the "is SET_ALARM enough on Samsung" [VERIFY] question on each phone.
- A model line shows the adb push folder and whether each model file is there, and the load result (times, PSS).
- Buttons: **Load models**, **Rescan model files**, **Benchmark**, **LLM benchmark** (press again to stop), **Refresh results**, **Share log (CSV)**, **New run file**. The results panel shows p50/p95 per (kind, action, path, mode), the gate line, and the LLM table (§2.1).

**Pipeline (one path for every action, PRD §16.2):** text → `ShortcutParser` *or* `FakeLlm` → `ToolRouter` (strict JSON parse + validation, one repair message, then a clarifying question) → `PermissionEngine` → confirmation dialog if needed → `ToolExecutor`.

**Real-LLM path** (Decisions 2026-10-30; PRD §13.4 router, §13.5 two-pass fallback):
1. `ShortcutParser` first, unchanged. A match never touches a model; the gate path has no model work in it.
2. On a miss: **Decision Maker** `ChoiceQuestion` over the five tools + `none` → selected key + confidence. `none`, an unknown key, or confidence below `ToolRouting.MIN_CONFIDENCE` (0.5 [OPEN]) → no action, the text goes to the clarify/chat path.
3. Otherwise **LiteRT-LM**: a new `Conversation` whose `ConversationConfig.tools` holds **only the chosen tool** (`tool(ToolSet)`), `automaticToolCalling = false`, greedy sampling, `maxOutputToken = 128`. The `@Tool` methods are inert and throw if called.
4. `LlmToolRouter`: each `Message.toolCalls` entry (`name`, `arguments`) is re-serialized to `{"tool": …, "args": {…}}` and goes through `ToolCallParser` (which runs `ToolValidator`). Invalid → one repair message naming only the exposed tool, sent in the same conversation → still invalid → ask the user. A call naming a tool that wasn't exposed is **rejected** (no repair, nothing runs). If the model writes a call as text instead, `{"name": …, "arguments": …}` or the parser's own format is read from the text; plain text means chat, no action.
5. A validated call → the same `PermissionEngine` → confirmation → `ToolExecutor` as every other path. Dial stays `ACTION_DIAL` with a confirmation card.

Threads: one single-thread dispatcher per engine (`bibi-s4-dm`, `bibi-s4-llm`); both models load once and are reused; the main thread only runs the shortcut matcher, the Permission Engine and the executor. Leaving the app stops the LLM benchmark (`Conversation.cancelProcess()` + coroutine cancel).

**Models are not in the APK** (`app/src/main/assets` has none). Both files are read from `getExternalFilesDir("models")`, i.e. `/sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s4/files/models/`:
- `embeddinggemma-2-text-270m.litertlm` (Decision Maker; 164,626,432 bytes per the Decision)
- `gemma3-1b-it-int4.litertlm` (the S2 file)

**Libraries (override of "no libraries", Decisions 2026-10-30):**
| Library | Version | Why | Checked |
|---|---|---|---|
| `com.google.ai.edge.litertlm:litertlm-android` | 0.16.1 (same pin as S2; 0.17+ needs Kotlin ≥ 2.3, AGP 9.0 has 2.2) | Engine, Conversation, tool calling | javap of the 0.16.1 jar; v0.16.1 sources (`Tool.kt`, `Config.kt`, `Conversation.kt`, `Message.kt`, `Engine.kt`) and `docs/api/kotlin/getting_started.md` §6 |
| `com.google.mediapipe:tasks-decision` | 1.2.0 (AAR minSdk 24) | Decision Maker router | javap of the AAR from Google Maven; Android guide on developers.google.com. 1.2.0 exposes `prewarm`/`evaluate(String, ChoiceQuestion)`, not `prewarmChoice`/`evaluateChoice` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 (what LiteRT-LM already brings) | dispatchers, cancellation | standard API |

`tasks-core` 1.2.0 depends on Google `datatransport` (usage logging), whose manifests add `INTERNET` and `ACCESS_NETWORK_STATE`. It is excluded in `app/build.gradle.kts` and both permissions carry `tools:node="remove"`. The merged manifest has `SET_ALARM` only. No `CALL_PHONE`, `QUERY_ALL_PACKAGES`, contacts, Accessibility or network permission.

**Pure-Kotlin core** (no `android.*` imports; JVM tests in `tools/core-test/`):

| File | Role |
|---|---|
| `ToolCall.kt` | The five tools, risk classes, typed calls, log redaction (numbers keep the last 2 digits) |
| `ToolValidator.kt` | Ranges from the AlarmClock reference: hour 0..23, minute 0..59, timer 1..86400 s. Literal phone numbers only: optional `+`, then 3..15 digits; no `*`, `#`, letters or URIs. App name or package-name pattern |
| `ShortcutParser.kt` | Anchored English matcher (timer, alarm, flashlight, open, call/dial a literal number). Negations and compound sentences don't match |
| `MiniJson.kt`, `ToolCallParser.kt` | Strict JSON. `{"tool": …, "args": {…}}`, optionally inside `<tool_call>…</tool_call>`. Unknown tools or keys, wrong types and out-of-range values are rejected. Has the repair prompt and `ToolRouter` (at most one repair) |
| `PermissionEngine.kt` | §16.4 policy and the confirmation lifecycle |
| `AppResolver.kt` | `open_app` target → an app on the precomputed launcher list only |
| `FakeLlm.kt` | Fixed-mapping model, the baseline |
| `Summary.kt` | p50/p95 (nearest rank), gate verdict |
| `LlmToolPath.kt` | `ToolRouting` (router label → the one exposed tool), `ModelCallEncoder` (LiteRT-LM call → parser JSON), `TextToolCall`, `LlmToolRouter` (parse, one repair, exposed-subset check), log redaction |
| `LlmBenchPrompts.kt` | The 110 benchmark prompts with their expected calls |
| `LlmSummary.kt` | Per-action LLM metrics (§2.1) |

**Android layer:** `ToolExecutor` (intents built only from validated arguments), `TorchController`, `AppIndex` (launcher list via `<queries>`), `SpikeLog` (CSV), `MainActivity`. Library adapters, the only files that import them: `DecisionRouter` (MediaPipe), `LiteRtToolLlm` (LiteRT-LM); `LlmPipeline` runs them on their threads.

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
6. Real-LLM path (Decisions 2026-10-30), on `feature/8-s4-llm-tools`: JVM tests first (router mapping, call re-serialization, subset rejection, repair, prompt set, LLM summary), then the pure core, the dependencies, the two adapters and the pipeline, the screen and the log, then these docs.

## 6. Test protocol (owner runs on the device)
Do it on the **vivo Y17s**, and on the **Galaxy A23** when available (it is the floor device). Steps are in [README.md → Owner test steps](README.md#owner-test-steps).

## 7. Result template (goes into `results/RESULTS.md`, `docs/project_status.md` → Spike results, and issue #8)
| Device | Action | Path | Mode | n | Failed | p50 ms | p95 ms | Verdict | Notes |
|---|---|---|---|---|---|---|---|---|---|

Also record: the handler line (`SET_ALARM → …` etc.), the launcher index time, and for the real launches whether each action did the right thing on screen. Note whether the alarm and timer were set without UI (`EXTRA_SKIP_UI`) or the clock app showed a screen. For the tool-call path, whether each action worked through the fake LLM, and the safety checks in steps 6–8 of the owner steps.

## 8. Known limits of this spike
- **Real-LLM path: built, not run.** `./gradlew :app:assembleDebug` succeeds and the pure core passes its JVM tests, but nothing of the LLM path has run on a phone yet. Every number in §2.1 is [VERIFY on device].
- **The fake LLM** shows that parsing, validation, repair and policy work; it says nothing about a real model. The real-LLM numbers are provisional until the S2 gate and runtime choice are recorded [OPEN].
- **Gemma 3 1B and tool calls [VERIFY]:** the LiteRT-LM guide says tool use "only works with models with tool support" and names FunctionGemma. Whether the `gemma3-1b-it-int4.litertlm` prompt template renders tools and whether LiteRT-LM parses its answer into `Message.toolCalls` is unknown. The system instruction repeats the one tool's schema and the router also reads a JSON call written as text, so a model without structured tool calls can still be measured (`source` = `text_json`/`text`). Constrained decoding (`ExperimentalFlags.enableConversationConstrainedDecoding`) is off [OPEN].
- **Decision Maker confidence [VERIFY]:** the guide doesn't define `ChoiceResult.confidence`. The 0.5 threshold is a first guess [OPEN]; `dm_conf` and the top-3 probabilities are logged so it can be tuned.
- **Memory [VERIFY]:** both models stay loaded (about 165 MB + the S2 model file) on a 4 GB floor device. The app runs them in its own process, not in a separate `:inference` process as P1 will.
- **Datatransport exclusion [VERIFY on device]:** checked statically (DecisionMaker 1.2.0 doesn't reach `TaskRunner`, the only creator of MediaPipe's remote logging client), not at runtime. A `NoClassDefFoundError` naming `com.google.android.datatransport` would mean it is reached after all.
- **Only one tool exposed:** if the router picks the wrong tool (e.g. timer for an alarm), the right one is never offered, by design. The LLM may then call the wrong tool or decline; both are logged.
- **First-use load:** a live request that triggers the model load restarts its `t0` after loading, so request times exclude load time; the load is its own `llm_load` row.
- **APK:** arm64-v8a only (both runtimes' native libraries), about 64 MB without models.
- Dry-run timing ends at intent resolution, and real-launch timing ends when `startActivity()` returns. Neither includes the other app becoming visible.
- `EXTRA_SKIP_UI` is set, so real alarm/timer launches create real alarms and timers labelled "Bibi S4 test". Delete them afterwards. Whether each clock app honors `SKIP_UI` is [VERIFY on device].
- Alarm phrases without am/pm are taken as 24-hour ("alarm at 6" → 06:00). Asking instead is [OPEN].
- App-name matching (exact label → alias → unique prefix → unique word) and the alias package list are first guesses [OPEN]/[VERIFY]. vivo's own apps aren't in the alias table.
- Asking the flashlight for the state it is already in isn't timed (no callback to wait for). That attempt is logged as failed.
- Plain views. The shortcut path and the Permission Engine run on the main thread; only the models run on their own threads. No `:inference` process. P1 architecture is not tested here.
