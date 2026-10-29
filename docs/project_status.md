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

## Test devices

| Device | RAM | Storage | Android | Chipset | Role | Source |
|---|---|---|---|---|---|---|
| Samsung Galaxy A23 | Unconfirmed (assume 4 GB) | Unconfirmed | Unconfirmed | Unconfirmed | Floor reference (PRD 0.4) | PRD |
| vivo Y17s | 6 GB physical, plus 6 GB "extended RAM" (swap on storage, not real RAM) | 128 GB eMMC 5.1 | 15 (owner-reported) | MediaTek Helio G85, 12 nm: 2× Cortex-A75 @ 2.0 GHz + 6× Cortex-A55 @ 1.8 GHz; GPU Mali-G52 MC2 | Second test device; role to be confirmed by owner | Owner; chipset and storage from GSMArena spec page [VERIFY on device] |

Notes on the vivo Y17s:
- **Budget against 6 GB, not 12 GB.** The extra 6 GB is swap on slow eMMC. Any model that only fits by using it will page-thrash. S2 must use the 6 GB physical figure, and the extended-RAM setting should be recorded (on/off) for each run.
- **It doesn't serve as the "better hardware" device for the PRD 0.5 decision rule.** It has 6 GB of RAM but an older mid-range CPU class [VERIFY with benchmarks]. Open item 4 (a stronger 6–8 GB phone) therefore stays partly open.
- **Battery management:** vivo's Funtouch OS is named in PRD §12.4 as an OEM with aggressive background limits. S1 on this device must record results with default battery settings.
- **eMMC 5.1** storage makes model load and mmap page-ins slower than on UFS phones [VERIFY in S2 cold-load timing].

## Open items

| # | Item | Default until answered | Status |
|---|---|---|---|
| 1 | Galaxy A23 RAM variant | Assume 4 GB | Open [VERIFY] |
| 2 | Galaxy A23 Android version | Assume 14+. If lower, the A23 can't run the app (minSdk 34). | Open [VERIFY] |
| 3 | Play account type (personal or organization) | Personal → plan a closed test with about 12 testers for about 14 days [VERIFY] | Open |
| 4 | Second test phone (6–8 GB) | vivo Y17s available (6 GB, Helio G85, Android 15). It covers the 6 GB RAM case, but not a recent chip. | Partly resolved |
| 10 | Is the Galaxy A23 physically available, and which phone is the S1/S2 floor device? | Keep the A23 as the floor per PRD 0.4. Run spikes on the vivo Y17s too, labeled by device. | Open — owner to confirm |
| 5 | Final app name | **Bibi AI** chosen (2026-10-28). Web check done; similar "Bibi" products exist (see brainstorm.md). Formal trademark, Play listing, and domain checks still needed. | Partly resolved [VERIFY] |
| 6 | Model pack hosting | Decide in P1 after checking Play Asset Delivery limits | Open [VERIFY] |
| 7 | `reference/Pip_Spike_Tracker.xlsx` not supplied at scaffold time | Add the file to `reference/` unchanged | Open |
| 8 | Dial action: "Safe" in PRD 0.8, SENSITIVE in §16.3 | Treat as Sensitive (confirmation card) | Open — owner to confirm |
| 9 | MVP memory search: keyword only, or semantic too | FTS5 keyword first | Open |

## Decisions
- 2026-10-29 — Project subagents added in .claude/agents/ (spike-analyst, android-engineer, scope-guard, docs-keeper); CLAUDE.md §11 says when to use each and that delegation is announced to the owner. — Owner request.
- 2026-10-29 — Base MVP avatar states fixed as idle (plus blink frame), thinking, speaking, confused. Generated from the vector master by tools/build_states.py. Resolves open question "which four emotion states" (overlay.md). — Owner approval of master; Claude build.
- 2026-10-29 — Vector master v1 built by Claude instead of Codex (docs/design/character/master/). Hand-built layered SVG from the Gemini master; checked at 64 px. — Owner request.
- 2026-10-29 — Forehead moon patch removed from Bibi's design; the signature is now the violet collar with the speech-bubble charm. 17 Gemini concept images approved (master = 01), except 15-in-context-phone (regenerate). Review in docs/design/bibi-character-gemini.md §6. — Owner decision and review.
- 2026-10-29 — Character changed to a cute hand-drawn cartoon kitten (orange tabby, cream chest, crescent-moon forehead patch, violet collar with a speech-bubble charm that glows to show state). Nothing robotic, so the assistant feels safe. Supersedes the mochi-puff and seed directions. The owner's reference image is used for mood only, not copied. — Owner request.
- 2026-10-29 — Character direction changed to a fluffy mochi puff (lavender-white plush fur, squashy mochi body) with three AI signatures: glossy screen-face with glowing cyan eyes, glowing spark tuft that shows state, mitten paws that hold tools. Supersedes the 2026-10-28 seed direction. In-app art uses stylized fluff (scalloped outline), not rendered fur, to stay inside PRD §17.5 budgets. — Owner request.
- 2026-10-28 — Character direction: the seed concept, restyled as a tech helper (pearl shell, indigo face panel, cyan eyes, glowing sprout that shows state; violet/amber accents; no green or health cues). Brief and Gemini prompts in docs/design/bibi-character-gemini.md. — Owner feedback.
- 2026-10-28 — Product renamed from working name "Pip" to **Bibi AI** (character: Bibi). Repo slug `bibi-ai`; database `bibi.db`; export files `bibimem.json` / `.bibimem`. The PRD stays unchanged in `reference/` and still says Pip. — Owner decision.
- 2026-10-28 — Project scaffold created (structure, planning docs, reference PRD). — Owner request.
- 2026-10-28 — The decision log lives here, in "Decisions", instead of the `DECISIONS.md` named in PRD 0.11. — Owner instruction.
- 2026-10-28 — vivo Y17s (6 GB, 128 GB, Android 15) added as a test device. The floor device is unchanged until the owner decides (open item 10). — Owner message.

## Last session stopping point
2026-10-28: scaffold committed ("Scaffold project structure and planning docs"). No Gradle project or app code exists yet. The spike tracker spreadsheet is still missing from `reference/`.

## Next step
Create the private GitHub repository `bibi-ai` and push the initial history (gh can't authenticate from the cloud session, so the owner creates the empty repo and Claude pushes). Then open issues for milestones P0–P4 and spikes S1–S4.
Run spike S1: foreground-service overlay on the Galaxy A23.
First confirm the A23's RAM and Android version (Settings → About phone), because Android < 14 rules the device out.
