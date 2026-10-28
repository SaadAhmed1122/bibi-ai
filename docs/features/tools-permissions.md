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
  | `start_timer` | `AlarmClock.ACTION_SET_TIMER` | none | SAFE |
  | `flashlight` | `CameraManager.setTorchMode` | none | SAFE |
  | `call_contact` (dial prefilled number) | `Intent.ACTION_DIAL` | none | **SENSITIVE** in §16.3, "Safe" in 0.8 — see Open questions |

- **Schema** (§16.1): `ToolSpec(name, description, parameters, risk, androidPermissions, reversible, confirmationTemplate, timeoutMs)`.
- **Shortcut path** (§13.5): a deterministic rules/regex matcher for the top commands. It skips the LLM entirely, so it is instant and costs no battery.
- **LLM path:** constrained `<tool_call>` output (or a two-pass fallback) → schema validation → one repair attempt → a clarifying question.
- **Confused-deputy defenses** (§16.6):
  - Provenance tags on every string in the prompt.
  - The executor builds intents from validated arguments only.
  - URL schemes are allowlisted.
  - The confirmation card shows the resolved target, not the model's paraphrase.

## Risks
- **Tool-call formatting** from a small int4 model may be unreliable (§13.3, §13.5). The shortcut path and validation are the mitigation.
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
- **LLM path:** 20 prompts per action. Record valid-JSON %, correct-tool %, and correct-argument %. This is a reduced version of the §13.9 harness.
- **Safety:** an injected instruction in pasted text never executes without confirmation.
