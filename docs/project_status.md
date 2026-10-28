# Project status

Updated at the end of every session (see CLAUDE.md §6).

## Current phase
**P0 — Feasibility spikes.** No app code yet. Goal: measure S1 and S2 on the Galaxy A23 (PRD 0.7).

## Milestones

| Phase | Name | Exit gate (PRD 0.6) | Status |
|---|---|---|---|
| P0 | Feasibility spikes | S1 and S2 pass, or the 0.5 decision rule is recorded | In progress (scaffold only) |
| P1 | Walking skeleton | Core loop works for 1 hour without a crash; installable from the Play internal track | Not started |
| P2 | Companion MVP | Closed-test ready; policy checklist (PRD 0.9) green | Not started |
| P3 | Closed test | Crash-free sessions ≥ 99% in the core loop; retention signal recorded | Not started |
| P4 | Decision | Write-up published; funding or no-funding decision | Not started |

## Spike results
Thresholds from PRD 0.7. Measurements also go in `reference/Pip_Spike_Tracker.xlsx`.

| Spike | Pass threshold | Result | Device | Date |
|---|---|---|---|---|
| S1 Overlay | Stable 1 h; battery ≤ 3%/h, screen on | Not measured | — | — |
| S2 LLM | ≥ 5 tokens/s; first token ≤ 3 s; 0 OOM kills in a 50-prompt soak | Not measured | — | — |
| S3a TTS / S3b STT | TTS RTF ≤ 1.0 / STT RTF ≤ 0.7, peak ≤ 600 MB | Not measured | — | — |
| S4 Tools | Shortcut path < 300 ms | Not measured | — | — |

## Open items

| # | Item | Default until answered | Status |
|---|---|---|---|
| 1 | Galaxy A23 RAM variant | Assume 4 GB | Open [VERIFY] |
| 2 | Galaxy A23 Android version | Assume 14+. If lower, the A23 can't run the app (minSdk 34). | Open [VERIFY] |
| 3 | Play account type (personal or organization) | Personal → plan a closed test with about 12 testers for about 14 days [VERIFY] | Open |
| 4 | Second test phone (6–8 GB) | None; benchmarks labeled "single-device" | Open |
| 5 | Final app name | Working name "Pip"; candidates in brainstorm.md; trademark and Play check needed | Open [VERIFY] |
| 6 | Model pack hosting | Decide in P1 after checking Play Asset Delivery limits | Open [VERIFY] |
| 7 | `reference/Pip_Spike_Tracker.xlsx` not supplied at scaffold time | Add the file to `reference/` unchanged | Open |
| 8 | Dial action: "Safe" in PRD 0.8, SENSITIVE in §16.3 | Treat as Sensitive (confirmation card) | Open — owner to confirm |
| 9 | MVP memory search: keyword only, or semantic too | FTS5 keyword first | Open |

## Decisions
- 2026-10-28 — Project scaffold created (structure, planning docs, reference PRD). — Owner request.
- 2026-10-28 — The decision log lives here, in "Decisions", instead of the `DECISIONS.md` named in PRD 0.11. — Owner instruction.

## Last session stopping point
2026-10-28: scaffold committed ("Scaffold project structure and planning docs"). No Gradle project or app code exists yet. The spike tracker spreadsheet is still missing from `reference/`.

## Next step
Run spike S1: foreground-service overlay on the Galaxy A23.
First confirm the A23's RAM and Android version (Settings → About phone), because Android < 14 rules the device out.
