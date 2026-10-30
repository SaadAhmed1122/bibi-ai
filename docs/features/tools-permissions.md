# Feature: Tools and permissions

## Purpose
Let Bibi perform a small set of phone actions safely. Every action goes through one Tool Router and one Permission Engine (PRD §11.1 goal 4). Spike S4 measures the direct-shortcut path.

## Requirements
- **PRD 0.8:**
  - Five Safe actions through direct shortcuts.
  - The LLM can request them through a structured tool call.
  - Every Sensitive action needs a confirmation card.
- **PRD 0.7 S4 — the five MVP actions:**
  1. open app
  2. set alarm
  3. set timer
  4. flashlight
  5. dial with prefilled number

  Pass: shortcut path under 300 ms. If it fails: simplify intent building and precompute the app list.
- **PRD 0.8, 0.9 — not in MVP:**
  - SMS access.
  - Call-log access.
  - Contacts access.
  - Accessibility-service automation.

  Dialing opens the dialer prefilled, and the user presses call.
- **PRD §16.4 policy:**

  | Risk class | Behavior |
  |---|---|
  | Safe | Execute and report |
  | Sensitive | Confirmation card with the exact parameters |
  | Critical | Confirmation plus identity verification |

  Additional rules:
  - Untrusted-content taint upgrades Safe to Sensitive.
  - A timeout cancels; silence is never consent.
  - No chained sensitive actions from one confirmation.
  - No sensitive actions while the device is locked.
- **FR-120/121:** typed schemas and validation. Never execute with invalid parameters.
- **FR-122:** a local audit log with redacted parameters.
- **FR-124:** untrusted content can never trigger tools without confirmation.
- **FR-155:** runtime permissions requested lazily, with a rationale.
- **FR-156:** panic stop within ≤ 300 ms.

## Design notes
- **MVP tool mapping** (PRD §16.3):

  | Action | Mechanism | Android permission | Risk (§16.3) |
  |---|---|---|---|
  | `open_app` | Launch intent; `<queries>` for launcher intents (not `QUERY_ALL_PACKAGES`) | none | SAFE (banking apps → CRITICAL, open only) |
  | `set_alarm` | `AlarmClock.ACTION_SET_ALARM` | `com.android.alarm.permission.SET_ALARM` | SAFE |
  | `start_timer` | `AlarmClock.ACTION_SET_TIMER` | `com.android.alarm.permission.SET_ALARM` (per developer.android.com AlarmClock reference, 2026-10-29) | SAFE |
  | `flashlight` | `CameraManager.setTorchMode` | none | SAFE |
  | `call_contact` (dial prefilled number) | `Intent.ACTION_DIAL` | none | **SENSITIVE** in §16.3, "Safe" in 0.8 — see Open questions |

- **Schema** (§16.1): `ToolSpec(name, description, parameters, risk, androidPermissions, reversible, confirmationTemplate, timeoutMs)`.
- **Shortcut path** (§13.5): a deterministic rules/regex matcher for the top commands. It skips the LLM entirely, so it is instant and costs no battery.
- **LLM path:** constrained `<tool_call>` output (or a two-pass fallback) → schema validation → one repair attempt → a clarifying question.
- **S4 real-LLM path (spike only, Decisions 2026-10-30; details in `spikes/s4-tools/SPEC.md` §2.1, §4):**
  - Order: shortcut matcher first, unchanged; models only run on a miss, so the 300 ms gate path never waits for a model.
  - Router (PRD §13.4 tool-schema router): MediaPipe Decision Maker (`tasks-decision` 1.2.0, `embeddinggemma-2-text-270m.litertlm`) chooses one of the five tools or `none`. `none`, an unknown key or low confidence (< 0.5 [OPEN]) means no action and the clarify/chat path. The router never dispatches anything.
  - Pass 2, arguments (Decision 2026-10-30): **deterministic slot extraction** (`SlotExtractor`) for the chosen tool only: time, duration, on/off, phone number, app name (whole-word match on the launcher list). A missing or ambiguous slot means no call and a short question to the user; nothing is guessed. The slots are serialized to the parser's `{"tool", "args"}` JSON and take the same path below. The LLM stays for chat only. Offline ceiling on the 110 benchmark prompts with the correct label: 100/100 (an upper bound; on-device numbers [VERIFY]).
  - Experimental, off by default: the first try, LiteRT-LM 0.16.1 with Gemma 3 1B int4 making the call (0/100 valid calls on the vivo Y17s, `spikes/s4-tools/results/RESULTS.md`). The conversation sees **only the chosen tool**, `automaticToolCalling = false`, and the `@Tool` methods are inert (they throw).
  - Validation: each slot result or `Message.toolCalls` entry is re-serialized to the parser's `{"tool", "args"}` JSON and goes through `ToolCallParser` → `ToolValidator` (one repair, scoped to the exposed tool) → `PermissionEngine` → `ToolExecutor`, the same as the fake LLM. A call naming a tool that wasn't exposed is rejected outright.
  - Permissions: none added. Dial stays `ACTION_DIAL` with a confirmation card. MediaPipe's transitive `datatransport` (it would add `INTERNET`, `ACCESS_NETWORK_STATE`) is excluded and both permissions are removed in the manifest.
  - Results are provisional until S2 is decided and don't decide the S2 runtime [OPEN].
- **Confused-deputy defenses** (§16.6):
  - Provenance tags on every string in the prompt.
  - The executor builds intents from validated arguments only.
  - URL schemes are allowlisted.
  - The confirmation card shows the resolved target, not the model's paraphrase.

## Risks
- **Tool-call formatting** from a small int4 model may be unreliable (§13.3, §13.5). The shortcut path and validation are the mitigation.
- **Gemma 3 1B tool support:** measured on 2026-10-30, no structured `toolCalls` and 0/100 valid calls, so pass 2 is slot extraction now. FunctionGemma is not pursued for now (owner decision).
- **Slot extraction coverage [VERIFY]:** English, pattern-based; unusual phrasings ask the user. Defaults still [OPEN]: a bare hour ("alarm at 6") is taken as 24-hour; a light request with no on/off word turns the torch on.
- **Router mistakes are final for that request:** only the chosen tool is exposed, so a wrong router choice can't be corrected by the LLM. This is intended (less surface for the model), and the S4 LLM benchmark measures how often it happens.
- **Prompt injection** (FR-124): low exposure in MVP because there is no notification or file reading, but keep taint tracking in the design.
- **Package visibility on Android 11+** may hide apps from `open_app` without the correct `<queries>` entries.
- **Play policy:** adding any restricted permission later (SMS, call log, Accessibility) changes the review risk profile (0.8 reasoning).

## Open questions
- **Dial action risk class:** PRD 0.8 says the five actions are Safe, but §16.3 rates `call_contact` as SENSITIVE. Default: **Sensitive** (show a confirmation card) until the owner decides [OPEN].
- Is the `SET_ALARM` permission declaration enough for every clock app on Samsung? [VERIFY]
- Matching rules for app names ("open maps" → which package?) [OPEN].

## Test plan
- **Unit tests (highest priority, PRD 0.11):**
  - Permission Engine policy table: every risk class × taint × lock state.
  - Tool-call parser and validator: valid, malformed, and missing-parameter cases; repair once, then ask.
  - Shortcut matcher: phrase coverage.
- **S4 timing (A23):** shortcut path from submit to intent launch, p50 and p95, over 20 runs per action. Pass: < 300 ms.
- **LLM path:** 20 prompts per action. Record valid-JSON %, correct-tool %, and correct-argument %. This is a reduced version of the §13.9 harness. Built in S4 as the **LLM benchmark** (110 prompts incl. 10 "none", dry run; adds router accuracy, p50/p95 latency and peak PSS) [VERIFY on device].
- **JVM tests for the real-LLM path (S4 `tools/core-test`):** router label → exposed tool (incl. `none` and low confidence), LiteRT-LM call → parser JSON, rejection of tools outside the exposed subset, one repair, prompt set (no shortcut matches), per-action summary.
- **Safety:** an injected instruction in pasted text never executes without confirmation.
