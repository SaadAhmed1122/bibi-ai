# Project status

Updated at the end of every session (see CLAUDE.md §6).

## Current phase
**P0 — Feasibility spikes.** No app code yet. Goal: measure S1 and S2 on the Galaxy A23 (PRD 0.7).

## Milestones

| Phase | Name | Exit gate (PRD 0.6) | Status |
|---|---|---|---|
| P0 | Feasibility spikes | S1 and S2 pass, or the 0.5 decision rule is recorded | In progress: S1–S4 spike apps built; S3 measured on vivo Y17s; S1 partial; S2 and S4 not measured |
| P1 | Walking skeleton | Core loop works for 1 hour without a crash; installable from the Play internal track | Not started |
| P2 | Companion MVP | Closed-test ready; policy checklist (PRD 0.9) green | Not started |
| P3 | Closed test | Crash-free sessions ≥ 99% in the core loop; retention signal recorded | Not started |
| P4 | Decision | Write-up published; funding or no-funding decision | Not started |

## Spike results
Thresholds from PRD 0.7. Measurements also go in `reference/Pip_Spike_Tracker.xlsx`.

| Spike | Pass threshold | Result | Device | Date |
|---|---|---|---|---|
| S1 Overlay | Stable 1 h; battery ≤ 3%/h, screen on | **Partial:** 53 min Calm run with no kill (stability likely pass); Bibi's extra drain not measured yet (baseline run missing). Total drain 15.8 %/h at ~930 mA. [Details](../spikes/s1-overlay/results/RESULTS.md) | vivo Y17s | 2026-10-29 |
| S2 LLM | ≥ 5 tokens/s; first token ≤ 3 s; 0 OOM kills in a 50-prompt soak | Not measured. Smoke runs only (not gate), see open item 11 | vivo Y17s (smoke) | 2026-10-29 |
| S3a TTS / S3b STT | TTS RTF ≤ 1.0 / STT RTF ≤ 0.7, peak ≤ 600 MB | **S3a:** Kokoro FAIL (best median RTF 3.769, en v0.19 t4); Piper PASS (0.248 t4, p95 0.271). **S3b:** Zipformer 20M passes numbers (RTF 0.177 t2, PSS 119 MiB) but drops utterance starts (model limit), so not usable as default; Parakeet FAIL memory (PSS 903 MiB, RTF 0.324); **owner override 2026-10-29: Parakeet is the S3b default despite memory**. [Details](https://github.com/SaadAhmed1122/bibi-ai/blob/spike/S3-voice/spikes/s3-voice/results/RESULTS.md) (branch `spike/S3-voice`) | vivo Y17s (charging; A23 not tested) | 2026-10-29 |
| S4 Tools | Shortcut path < 300 ms | Not measured | — | — |

## Test devices

| Device | RAM | Storage | Android | Chipset | Role | Source |
|---|---|---|---|---|---|---|
| Samsung Galaxy A23 | Unconfirmed (assume 4 GB) | Unconfirmed | Unconfirmed | Unconfirmed | Floor reference (PRD 0.4) | PRD |
| vivo Y17s (model V2310) | 6 GB physical (device reports 5744 MB total), plus 6 GB "extended RAM" (swap on storage, not real RAM) | 128 GB eMMC 5.1 | 15 (API 35), confirmed on device | MediaTek Helio G85, 12 nm: 2× Cortex-A75 @ 2.0 GHz + 6× Cortex-A55 @ 1.8 GHz; GPU Mali-G52 MC2 | Second test device; role to be confirmed by owner | Owner; chipset and storage from GSMArena spec page [VERIFY on device] |

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
| 7 | `reference/Pip_Spike_Tracker.xlsx` not supplied at scaffold time | Add the file to `reference/` unchanged. S1 and S3 numbers are not entered in it yet; they live in the RESULTS.md files. | Open |
| 8 | Dial action: "Safe" in PRD 0.8, SENSITIVE in §16.3 | Treat as Sensitive (confirmation card) | Open — owner to confirm |
| 9 | MVP memory search: keyword only, or semantic too | FTS5 keyword first | Open |
| 11 | S2 model files (SPEC [VERIFY]) | Smoke runs on vivo Y17s, LiteRT-LM 0.16.1: `litert-community/Qwen3-0.6B` `qwen3_0_6b_mixed_int4.litertlm` (497,516,544 bytes) loads; decode 7.2–8.1 tok/s, engine TTFT 4.4–6.0 s, PSS ~2.1 GB (smoke, not gate). `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`: ~2.2 tok/s, TTFT 7.6–8.6 s, not recommended. Proposed soak file: mixed_int4. A Soak 50 of the dynamic_wi4b32 file (started 23:40, not by Claude) hung on prompt 4: `TimeoutException: no onDone within 300 s`; its `:inference` stayed resident (~1.48 GiB) after the error [VERIFY whether intended]. Gemma 3 1B int4 needs the owner to accept the Gemma terms and download it. The phone also holds non-SPEC models (gemma-4-E2B-it 2.59 GB, Qwen2.5-1.5B q8); the earlier Qwen2.5-1.5B soak is invalid (25/50 prompts, 3.7 tok/s). See #5. | Open [OPEN] — owner to confirm the Qwen file |
| 12 | S3b default recognizer | Parakeet TDT 0.6B v2 int8, by owner override of the 600 MB limit (Decisions, 2026-10-29). vivo check with a Qwen3 LLM resident (idle): no kills, min MemAvailable 2,023 MB. Still to check: LLM generating, Gemma 3 1B, Galaxy A23. See #7. | Resolved (override) [VERIFY on A23] |
| 13 | Spike JVM test scripts (`tools/*/run.sh` on the spike branches) hard-code `/opt/gradle-*/lib` (cloud path) | Locally run via a copy pointed at `~/.gradle/wrapper/dists/gradle-9.1.0-bin/.../lib` plus trove4j from gradle-8.11 | Open — small chore |

## Local environment (2026-10-29)
- graphify installed and graph built (`graphify-out/`, git-ignored); gh CLI installed and logged in; Context7 MCP works without a key.
- Not set: `CONTEXT7_API_KEY`, `API_KEY_21ST`, `STRIX_LLM`, `LLM_API_KEY`. Strix CLI not installed. Docker installed but not running.
- Spike JVM suites pass locally (S2 summary ALL PASS, S3 ALL PASS, S4 213 checks ALL PASS) using the workaround in open item 13. S2, S3 and S4 build with `./gradlew assembleDebug` (AGP/Gradle 9.1.0, JDK 17).

## Decisions
- 2026-10-29 — S3b STT default recognizer: **Parakeet TDT 0.6B v2 int8**, accepted although it fails the PRD 0.7 STT memory limit (peak PSS 903 MiB ≈ 947 MB > 600 MB); it passes RTF (median 0.324 t4, 0.433 t2) and gives near-perfect transcripts, while Zipformer 20M loses utterance starts. Override of PRD 0.7 for the memory part of S3b only. Risks: may be killed or crowd out the LLM on the 4 GB Galaxy A23 floor device and when loaded alongside the S2 model in `:inference` [VERIFY on A23 and with LLM loaded]; load 10–11 s. Supersedes the [OPEN] default in the S3b line below. — Owner decision ("if it fails only on memory then ignore and use it") — #7
- 2026-10-29 — S3b STT: small streaming Zipformer en 20M (2023-02-17) passes the numeric gate on vivo Y17s (median RTF 0.177 t2, p95 0.191; peak PSS 119 MiB) but is NOT usable as the default recognizer: the model drops the first ~1–2 s of every fresh stream (cold encoder state; desktop repro with sherpa-onnx 2.0.1/1.12.34/1.13.8, matches upstream docs; adapter correct). Parakeet TDT 0.6B v2 int8 FAILS on memory (peak PSS 903 MiB ≈ 947 MB > 600 MB; RTF 0.324 t4 would pass) and is off by default on this tier. S3b default recognizer [OPEN]; until a candidate passes on device, the PRD 0.7 fallback (push-to-talk short clips, typed input) applies. Swapping to another streaming model (e.g. streaming-zipformer-en-2023-06-26) needs an owner decision and its own Decisions entry before measuring. — spikes/s3-voice/results/RESULTS.md (branch `spike/S3-voice`), #7
- 2026-10-29 — S3a TTS: Kokoro FAILS on vivo Y17s (en v0.19 fp32 median RTF 4.305 t2 / 3.769 t4; multi-lang v1.1 int8 4.936 t2 / 4.853 t4; gate ≤ 1.0). Per PRD 0.7, switch to the lighter voice on this tier: Piper en_US amy low PASSES (median RTF 0.248 t4, p95 0.271; 0.297 t2; PSS 326 MiB). Kokoro-en verdict computed from complete runs only; 3 runs the owner interrupted (REMOVE TASK) excluded, verdict unchanged. Listening check and Galaxy A23 not done [VERIFY]. — spikes/s3-voice/results/RESULTS.md (branch `spike/S3-voice`), #6
- 2026-10-29 — agent-board mod moved into the repo (plugins/agent-board, listed by .claude-plugin/marketplace.json, enabled in .claude/settings.json) so it loads on the owner's laptop and in cloud sessions; docs/local-setup.md added. — Owner request.
- 2026-10-29 — Added `repo-keeper` subagent for all git/GitHub work under CLAUDE.md §10 (REST-only GitHub API in cloud sessions; never merges or force-pushes main). — Owner request.
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
2026-10-29 local session: S3 run on the vivo Y17s; results committed to `spike/S3-voice` (`spikes/s3-voice/results/`); issues #5, #6, #7 commented. S2: smoke runs only (open item 11). Worktrees at `/Users/saadahmed/bibi-wt/{S2-llm,S3-voice,S4-tools}`.

## Next step
1. S3: done on the vivo: clip1 re-recorded, Parakeet re-run (t4 0.336), battery runs (Parakeet t4 0.346–0.358; Piper Play-on 0.297, owner heard it, no problems reported), and Parakeet with a Qwen3 LLM resident (RTF 0.349, min MemAvailable 2,023 MB, no kills) — RESULTS.md §4.3–4.4. Still to do: Parakeet while the LLM is generating, with Gemma 3 1B, and on the Galaxy A23 [VERIFY].
2. Owner: download `gemma3-1b-it-int4.litertlm`, confirm the Qwen file (open item 11), and run S2 Soak 50 for both under soak conditions (#5).
3. S4 on-phone steps and benchmark.
4. S1: four measured runs (app on `main`, `spikes/s1-overlay`, #4).
5. Make the `tools/*/run.sh` scripts find a local Gradle lib, not only `/opt` (open item 13).
