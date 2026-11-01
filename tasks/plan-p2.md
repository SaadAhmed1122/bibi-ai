# Implementation Plan: P2 companion MVP

## Overview
Build P2 from [SPEC-P2.md](../SPEC-P2.md) and its seven module specs: the five phone actions, two personalities, memory, Bibi's four states, voice in the app and the floating card, the Privacy dashboard and the closed-test paperwork. The P1 plan ([plan.md](plan.md)) is unchanged; its last item (the 1-hour exit gate, #49) is the owner's and runs alongside.

**Tasks are tracked as GitHub Issues** in milestone "P2 — Companion MVP": task N = issue N+71 (#72–#90); the design tasks added later are T20 = #93 and T21 = #94. One task = one branch `feature/<issue>-<name>` = one PR.

## Architecture decisions
- **Measure memory before building voice** (Task 1). Parakeet alone is ≈ 947 MB; it has never run next to Gemma.
- **Port, don't rewrite, S4.** The tool pipeline and its tests move into a pure-Kotlin `core:tools`; only the Android executor and the chat wiring are new.
- **Router and voice engines live in `:inference`** with new Messenger messages, like Gemma, so a native crash never takes down the UI.
- **Audit log is a capped file**, not a table, to keep tools free of schema changes. The only P2 migration is memory (1 → 2).
- **Room `@Fts4`** for keyword search instead of FTS5 (Room generates FTS3/4 only).
- **Major merges wait for the owner** (merge rule): T2 (Permission Engine), T4 (SET_ALARM), T7 (migration), T14 (RECORD_AUDIO), T15 (microphone FGS). Routine tasks are merged after tests, lint and a vivo check in both orientations.
- **Library APIs are checked with Context7** at the pinned versions (MediaPipe tasks-decision 1.2.0, sherpa-onnx 2.0.1, WorkManager) before code.

## Task list

### Phase 1: Risk check
- [x] **Task 1 (#72): Memory check.** Done 2026-10-31: voice on demand ([results](../docs/measurements/2026-10-31-p2-memory-check/RESULTS.md)). Gemma generating + router + Parakeet + Piper on the vivo.
  - Acceptance: PSS per process, MemAvailable minimum and exit reasons recorded; a Decision line says "all resident" or "voice on demand".
  - Verify: `dumpsys meminfo`, `dumpsys activity exit-info`. **S**

### Phase 2: Tools
- [x] **Task 2 (#73): `core:tools` from S4.** Acceptance: SPEC-tools criterion 1. Verify: `./gradlew :core:tools:test`. **M**
- [x] **Task 3 (#74): Router in `:inference` + router model import.** Acceptance: ROUTE round-trip test; on the vivo, "set a timer for 5 minutes" → `start_timer` with a score in the debug log. **M**
- [x] **Task 4 (#75): `ToolExecutor` + the five actions + audit log.** Acceptance: each action fires from a debug button on the vivo; merged-manifest check (SPEC-tools criterion 4). **M**
- [x] **Task 5 (#76): Tools in chat + confirmation card.** Acceptance: SPEC-tools criteria 2–3. **M**

### Checkpoint E1: actions
- [x] All five actions from the app and the card, both orientations; shortcut p95 < 300 ms; chat TTFT with the router recorded. Owner tries it.

### Phase 3: Personality and memory
- [x] **Task 6 (#77): Personality presets.** Acceptance: SPEC-personality. **S**
- [x] **Task 7 (#78): Memory storage, migration 1 → 2.** Acceptance: SPEC-memory criterion 1. **M**
- [x] **Task 8 (#79): Remember this, never-store filter, Memory screen.** **M**
- [x] **Task 9 (#80): Memories in answers.** **M**
- [x] **Task 10 (#81): Automatic summaries while idle.** **M**
- [x] **Task 11 (#82): Retention job + JSON export.** **M**

### Checkpoint E2: memory
- [x] SPEC-memory criterion 3 on the vivo (Sara test, summary after idle, no delayed reply). Owner tries it.

### Phase 4: Bibi's states and voice
- [x] **Task 12 (#83): Avatar states.** Acceptance: SPEC-avatar-states. **M**
- [x] **Task 13 (#84): Voice pack import + sherpa-onnx engines.** **M**
- [x] **Task 14 (#85): Push-to-talk + spoken replies in the chat screen.** **M**
- [x] **Task 15 (#86): Voice in the floating card.** **M**

### Checkpoint E3: voice
- [x] SPEC-voice criterion 2 on the vivo; no kills. Owner tries it.

### Phase 5: Privacy and closed test
- [x] **Task 16 (#87): Privacy dashboard, Delete everything, Safe mode.** **M**
- [ ] **Task 17 (#88): Licenses, privacy policy, report a reply.** **S**
### Phase 5b: Design (owner request, 2026-10-31; [SPEC-design.md](../SPEC-design.md))
- [ ] **Task 20 (#93): Design system and navigation.** Starts with 2–3 mood directions (palette, type, Home and Chat mock-ups) as an artifact; the owner picks one. Then `core/designsystem` tokens light/dark, type, components, and the navigation structure.
  - Acceptance: SPEC-design criteria 1–2; app builds with the new theme; debug screens debug-only. **M**
- [ ] **Task 21 (#94): Screen-by-screen restyle.** Every screen on the new system, with an impeccable audit each.
  - Acceptance: SPEC-design criteria 3–5; before/after on the vivo in portrait and landscape. **L** (one PR per group of screens if it grows)

### Checkpoint E4: design
- [ ] The owner reviews every screen on the phone before the closed-test paperwork.

- [ ] **Task 18 (#89): Play Console notes + 0.9 checklist + version bump.** **S**
- [ ] **Task 19 (#90): P2 gate, 1-hour session (owner).** **S**

### Checkpoint F: P2 done
- [ ] PRD 0.9 checklist green; P2 gate passed; owner uploads to the closed-test track.

## Risks and mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Parakeet + Gemma + router don't fit in 6 GB (worse on a 4 GB A23) | High | Task 1 first; load voice on demand; A23 "voice not supported" per PRD 0.5 |
| Router adds ~0.5 s to every chat reply | Med | Shortcut first; measure at E1; skip the router for long messages if needed |
| Summaries collide with the single Gemma conversation | Med | Idle-only, cancellable; check 0.16.1 second-conversation support first |
| Android won't let the overlay start a microphone service from the background | Med | Check rules first; fallback opens the app's chat |
| sherpa-onnx is a manual AAR download | Low | Same AAR as S3; build fails with a clear message if missing |
| The design plugins (impeccable, ui-ux-pro-max, 21st) are web-oriented, and 21st has no key yet | Low | Ideas and critique only, built in Compose; fall back to Material 3 guidance and say so |
| Slot extraction scored 95/100 on its own tuning set | Med | Fresh prompt set at E1 (open S4 [VERIFY]) |

## Open questions
- See SPEC-P2.md "Open questions" (memory UI types, app-name matching, the A23).
