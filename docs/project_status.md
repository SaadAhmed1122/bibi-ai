# Project status

Updated at the end of every session (see CLAUDE.md §6).

## Current phase
**P0 — Feasibility spikes.** No app code yet. Goal: measure S1 and S2 on the Galaxy A23 (PRD 0.7).

## Milestones

| Phase | Name | Exit gate (PRD 0.6) | Status |
|---|---|---|---|
| P0 | Feasibility spikes | S1 and S2 pass, or the 0.5 decision rule is recorded | **Gate met on the vivo Y17s** (not the floor device): S1 PASS (2026-10-31); S2 PASS in chat mode (owner decision 2026-10-30); S3 done; S4 gate PASS. Still open: Qwen 0.5B-class soak, Galaxy A23 untested. All four spike apps merged to `main` (PRs #10, #12, #13, #14, #19). |
| P1 | Walking skeleton | Core loop works for 1 hour without a crash; installable from the Play internal track | Not started |
| P2 | Companion MVP | Closed-test ready; policy checklist (PRD 0.9) green | Not started |
| P3 | Closed test | Crash-free sessions ≥ 99% in the core loop; retention signal recorded | Not started |
| P4 | Decision | Write-up published; funding or no-funding decision | Not started |

## Spike results
Thresholds from PRD 0.7. Measurements also go in `reference/Pip_Spike_Tracker.xlsx`.

| Spike | Pass threshold | Result | Device | Date |
|---|---|---|---|---|
| S1 Overlay | Stable 1 h; battery ≤ 3%/h, screen on | **PASS**: four valid runs (baseline, Bibi 12, baseline, Calm), each ≥ 62 min, clean Stop, no kill. Bibi's extra current **27.8 mA** at 12 fps (0.51 %/h), **11.5 mA** in Calm (0.15 %/h); gate ≤ 150 mA and ≤ 3 %/h. Brightness 15, 60 Hz, 1-min settle instead of 10 [VERIFY]. [Details](../spikes/s1-overlay/results/RESULTS.md) | vivo Y17s | 2026-10-31 |
| S2 LLM | ≥ 5 tokens/s; first token ≤ 3 s; 0 OOM kills in a 50-prompt soak | Gemma 3 1B int4: per-prompt soak **FAIL** (TTFT p95 3,493 ms); **chat-mode soak PASS** (system prompt prefilled once): TTFT p95 2,409 ms, 7.04 tok/s, 0 OOM, PSS 1,189 MB. **Chat mode decides the gate (owner, 2026-10-30) → Gemma 3 1B PASS on vivo.** Qwen 0.5B-class not soaked [OPEN]. [Details](../spikes/s2-llm/results/RESULTS.md) | vivo Y17s | 2026-10-30 |
| S3a TTS / S3b STT | TTS RTF ≤ 1.0 / STT RTF ≤ 0.7, peak ≤ 600 MB | **S3a:** Kokoro FAIL (best median RTF 3.769, en v0.19 t4); Piper PASS (0.248 t4, p95 0.271). **S3b:** Zipformer 20M passes numbers (RTF 0.177 t2, PSS 119 MiB) but drops utterance starts (model limit), so not usable as default; Parakeet FAIL memory (PSS 903 MiB, RTF 0.324); **owner override 2026-10-29: Parakeet is the S3b default despite memory**. Battery runs and Parakeet with an idle Qwen3 LLM resident: no kills, min MemAvailable 2,023 MB. [Details](../spikes/s3-voice/results/RESULTS.md) | vivo Y17s (charging; A23 not tested) | 2026-10-29 |
| S4 Tools | Shortcut path < 300 ms | **PASS**: shortcut p95 6.2–19.4 ms per action (20 dry runs each, 0 failures). Info: Decision Maker router 85–100 % per action, ~0.5 s; Gemma 3 1B tool calling 0/100 valid (dropped). **Slot extraction (PR #25): 87/100 right call and values, ~0.5 s, 600 MB, 0 calls on "none"**. Tuning (threshold 0.25, alarm→timer re-route, vendor-name check) in tests only, device re-run pending [VERIFY]. | vivo Y17s (charging) | 2026-10-30 |

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
| 7 | `reference/Pip_Spike_Tracker.xlsx` not supplied at scaffold time | Add the file to `reference/` unchanged. The file is still not in `reference/` (checked 2026-10-30); S1–S4 numbers live in the spikes' `results/RESULTS.md` files instead. | Open |
| 8 | Dial action: "Safe" in PRD 0.8, SENSITIVE in §16.3 | Treat as Sensitive (confirmation card) | Open — owner to confirm |
| 9 | MVP memory search: keyword only, or semantic too | FTS5 keyword first | Open |
| 11 | S2 model files (SPEC [VERIFY]) | **Gemma 3 1B:** `gemma3-1b-it-int4.litertlm` obtained by the owner and soaked (S2 row above). **Qwen 0.5B-class:** proposed file `litert-community/Qwen3-0.6B` `qwen3_0_6b_mixed_int4.litertlm` (497,516,544 bytes); its smoke runs on the vivo (LiteRT-LM 0.16.1) gave decode 7.2–8.1 tok/s but engine TTFT 4.4–6.0 s, slower than Gemma, PSS ~2.1 GB (smoke, not gate). `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`: ~2.2 tok/s, TTFT 7.6–8.6 s, not recommended; its Soak 50 hung on prompt 4 (`TimeoutException: no onDone within 300 s`) with `:inference` left resident (~1.48 GiB) [VERIFY whether intended]. Qwen soak not run. gemma-4-E2B-it removed from the phone at the owner's request (2026-10-30); the earlier Qwen2.5-1.5B q8 soak is invalid (25/50 prompts). See #5. | Open [OPEN] — owner to confirm the Qwen file |
| 12 | S3b default recognizer | Parakeet TDT 0.6B v2 int8, by owner override of the 600 MB limit (Decisions, 2026-10-29). vivo check with a Qwen3 LLM resident (idle): no kills, min MemAvailable 2,023 MB. Still to check: LLM generating, Gemma 3 1B, Galaxy A23. See #7. | Resolved (override) [VERIFY on A23] |
| 13 | Spike JVM test scripts (`spikes/*/tools/*/run.sh`, now on `main`) hard-code `/opt/gradle-*/lib` (cloud path) | Locally run via a copy pointed at `~/.gradle/wrapper/dists/gradle-9.1.0-bin/.../lib` plus trove4j from gradle-8.11 | Open — small chore |
| 14 | S2: does the chat-mode soak (one conversation, system prompt prefilled once; PR #23) count for the S2 gate instead of the per-prompt soak? | Per-prompt soak result stands (FAIL on TTFT p95) | Resolved 2026-10-30: chat mode decides (see Decisions) |
| 15 | S4 pass-2 approach for tool arguments (Gemma 3 1B gave 0/100 valid tool calls) | Shortcut path only; slot extraction recommended over FunctionGemma 270M | Resolved 2026-10-30: deterministic slot extraction (see Decisions, #8) |
| 16 | S1 path: four measured runs (baseline, Bibi 12, baseline, Calm, ~61 min each) or an owner override of the S1 gate | Four runs | Resolved 2026-10-31: four valid runs, S1 PASS (#4) |

## Local environment (2026-10-29)
- graphify installed and graph built (`graphify-out/`, git-ignored); gh CLI installed and logged in; Context7 MCP works without a key.
- Not set: `CONTEXT7_API_KEY`, `API_KEY_21ST`, `STRIX_LLM`, `LLM_API_KEY`. Strix CLI not installed. Docker installed but not running.
- Spike JVM suites pass locally (S2 summary ALL PASS, S3 ALL PASS, S4 213 checks ALL PASS) using the workaround in open item 13. S2, S3 and S4 build with `./gradlew assembleDebug` (AGP/Gradle 9.1.0, JDK 17).

## Decisions
- 2026-10-31 — S1 gate PASS on vivo Y17s: four valid runs (baseline 148.4 mA, Bibi 12 fps 176.2 mA, baseline 150.9 mA, Bibi Calm 162.4 mA; each ≥ 62 min, unplugged, screen on, brightness 15, 60 Hz, clean Stop, no exit reasons). Bibi cost 27.8 mA / 0.51 %/h at 12 fps and 11.5 mA / 0.15 %/h in Calm (gate ≤ 150 mA and ≤ 3 %/h). Protocol change: Start ~1 min after unplugging instead of a 10-min settle, the same for all four runs. Galaxy A23 untested [VERIFY] — spikes/s1-overlay/results/RESULTS.md, #4
- 2026-10-30 — S4 slot tuning: router confidence threshold 0.5 → 0.25; under the set_alarm label a duration with no clock time re-routes to start_timer (the only cross-tool route, still through parser, validator and Permission Engine); a vendor word followed by an uninstalled app name asks instead of opening the vendor's app. JVM tests pass; device re-run pending — Owner approved the follow-up after merging PR #25 — spikes/s4-tools/results/RESULTS.md §7, #8
- 2026-10-30 — S4 slot-extraction pass 2 measured on vivo Y17s (110 prompts, dry run): 87/100 action prompts right call and values (Gemma tool calls: 0/100), ~0.5 s end to end, PSS 600 MB (Decision Maker only); 0 calls on "none" prompts; one wrong-app call (google drive → Google). Remaining misses are a strict 0.5 router threshold and timer/alarm confusion [OPEN] — spikes/s4-tools/results/RESULTS.md §6, #8
- 2026-10-30 — S4 pass 2 (tool arguments) uses deterministic slot extraction after the Decision Maker label, not an LLM tool call: Gemma 3 1B gave 0/100 valid calls; slot extraction is fast and fully testable; the LLM stays for chat only. FunctionGemma not pursued now — Owner decision — spikes/s4-tools/results/RESULTS.md, #8
- 2026-10-30 — The S2 gate is read from the **chat-mode soak** (one conversation, system prompt prefilled once, per-turn TTFT), because it matches how the app will run; the per-prompt FAIL (3,493 ms) stays recorded. Result: **Gemma 3 1B int4 PASSES S2 on the vivo Y17s** (TTFT p95 2,409 ms, 7.04 tok/s, 0 OOM). Requirement for P1: the runtime must keep one conversation and reuse the system-prompt prefix (no per-message conversations). Still open: the SPEC's 0.5B Qwen-class model has no valid soak [OPEN], and the Galaxy A23 is untested [VERIFY] — Owner decision — #5
- 2026-10-30 — S2 chat-mode soak PASS for Gemma 3 1B int4 on vivo Y17s (valid: 50/50, unplugged, airplane on): TTFT p95 2,409 ms, decode 7.04 tok/s, 0 OOM, PSS 1,189 MB; system prompt (310 tokens) prefilled once, untimed, 0 resets. The per-prompt soak FAIL (3,493 ms) stays recorded. Whether chat mode decides the S2 gate is [OPEN] for the owner — spikes/s2-llm/results/RESULTS.md §1b, #5
- 2026-10-30 — S2 adds a chat-mode soak (one conversation, system prompt prefilled once, untimed; per-turn TTFT) to test prefix reuse after the per-prompt soak failed on TTFT p95; the per-prompt result stays recorded; whether chat mode decides the S2 gate is [OPEN] for the owner — Owner request — #5
- 2026-10-30 — Merged branches (spike/*, docs/*, chore/*) are kept for now; none deleted — Owner choice ("nothing deleted yet").
- 2026-10-30 — S2 gate FAIL for Gemma 3 1B int4 on vivo Y17s (valid 50-prompt soak, unplugged, airplane on): decode median 6.54 tok/s (PASS), 0 OOM kills (PASS), TTFT p95 3,493 ms > 3,000 ms (FAIL; p50 2,425 ms). TTFT scales with prompt length (~50 ms/token); only the 6 prompts of 72–86 tokens exceeded 3 s. Per PRD 0.7/0.5 the next step is an owner choice [OPEN]: prefix reuse, shorter prompts, GPU prefill, accept 3.5 s by override, or test the Galaxy A23. Qwen 0.5B-class soak not run — spikes/s2-llm/results/RESULTS.md, #5
- 2026-10-30 — S4 gate PASS on vivo Y17s: shortcut path p95 6.2–19.4 ms per action (gate < 300 ms). LLM path (info only): Decision Maker routes correctly 85–100 % per action in ~0.5 s; Gemma 3 1B int4 via LiteRT-LM 0.16.1 produced 0/100 valid tool calls (text only, malformed JSON, wrong values), so it is not used for tool arguments; pass-2 approach (slot extraction vs tool-trained model) [OPEN], owner to decide — spikes/s4-tools/results/RESULTS.md, #8
- 2026-10-30 — S4 spike adds MediaPipe Decision Maker (`com.google.mediapipe:tasks-decision:1.2.0`, model `embeddinggemma-2-text-270m.litertlm`, 164,626,432 bytes, Apache-2.0) as an intent router over the five MVP tools + "none"; it only selects which tool schema the LLM sees and never dispatches an action; the shortcut gate path is unchanged and runs without it — implements PRD §13.4 tool-schema router / §13.5 two-pass fallback; spike-only, not a commitment to the MVP stack — Owner request.
- 2026-10-30 — S4 real-LLM tool-call path built on LiteRT-LM tool calling (`automaticToolCalling = false`) with Gemma 3 1B int4, provisional until the S2 gate result and runtime choice are recorded; its results do not decide the S2 runtime [OPEN]; every model ToolCall is re-serialized and passes ToolCallParser → ToolValidator → PermissionEngine → ToolExecutor; FakeLlm stays as baseline — overrides spikes/s4-tools/SPEC.md §2 "fake LLM only" and §4 "no libraries" — Owner request.
- 2026-10-30 — Project plugin ponytail (DietrichGebert/ponytail) enabled for every session; it activates itself at session start and in subagents. CLAUDE.md rules win on conflict (tests first, no shortcuts in Permission Engine/tool router/parser); `shortcut:` comments need an issue ref — Owner request.
- 2026-10-30 — Project plugin humanizer (blader/humanizer) enabled in `.claude/settings.json` for writing docs, PR text and store copy; it must not change facts, numbers or `[VERIFY]`/`[OPEN]` markers — Owner request.
- 2026-10-30 — Project plugins impeccable (pbakaus), ui-ux-pro-max (nextlevelbuilder) and last30days (mvanhorn) enabled in `.claude/settings.json` for every session; the two design plugins are web-oriented, so their output is ideas only, built in Compose with `core/designsystem` tokens — Owner request.
- 2026-10-29 — S3b STT default recognizer: **Parakeet TDT 0.6B v2 int8**, accepted although it fails the PRD 0.7 STT memory limit (peak PSS 903 MiB ≈ 947 MB > 600 MB); it passes RTF (median 0.324 t4, 0.433 t2) and gives near-perfect transcripts, while Zipformer 20M loses utterance starts. Override of PRD 0.7 for the memory part of S3b only. Risks: may be killed or crowd out the LLM on the 4 GB Galaxy A23 floor device and when loaded alongside the S2 model in `:inference` [VERIFY on A23 and with LLM loaded]; load 10–11 s. Supersedes the [OPEN] default in the S3b line below. — Owner decision ("if it fails only on memory then ignore and use it") — #7
- 2026-10-29 — S3b STT: small streaming Zipformer en 20M (2023-02-17) passes the numeric gate on vivo Y17s (median RTF 0.177 t2, p95 0.191; peak PSS 119 MiB) but is NOT usable as the default recognizer: the model drops the first ~1–2 s of every fresh stream (cold encoder state; desktop repro with sherpa-onnx 2.0.1/1.12.34/1.13.8, matches upstream docs; adapter correct). Parakeet TDT 0.6B v2 int8 FAILS on memory (peak PSS 903 MiB ≈ 947 MB > 600 MB; RTF 0.324 t4 would pass) and is off by default on this tier. S3b default recognizer [OPEN]; until a candidate passes on device, the PRD 0.7 fallback (push-to-talk short clips, typed input) applies. Swapping to another streaming model (e.g. streaming-zipformer-en-2023-06-26) needs an owner decision and its own Decisions entry before measuring. — spikes/s3-voice/results/RESULTS.md (branch `spike/S3-voice`), #7
- 2026-10-29 — S3a TTS: Kokoro FAILS on vivo Y17s (en v0.19 fp32 median RTF 4.305 t2 / 3.769 t4; multi-lang v1.1 int8 4.936 t2 / 4.853 t4; gate ≤ 1.0). Per PRD 0.7, switch to the lighter voice on this tier: Piper en_US amy low PASSES (median RTF 0.248 t4, p95 0.271; 0.297 t2; PSS 326 MiB). Kokoro-en verdict computed from complete runs only; 3 runs the owner interrupted (REMOVE TASK) excluded, verdict unchanged. Listening check and Galaxy A23 not done [VERIFY]. — spikes/s3-voice/results/RESULTS.md (branch `spike/S3-voice`), #6
- 2026-10-29 — agent-board mod moved into the repo (plugins/agent-board, listed by .claude-plugin/marketplace.json, enabled in .claude/settings.json) so it loads on the owner's laptop and in cloud sessions; docs/local-setup.md added. — Owner request.
- 2026-10-29 — Spikes S2, S3a, S3b, S4 (#5–#8) run in parallel with S1 (#4) as throwaway apps under spikes/<name>/; no P1 code; P1 gate (S1+S2 pass or 0.5 rule) unchanged; S4 dial uses ACTION_DIAL with a literal number, no contacts, treated as Sensitive until open item 8 is decided. — Overrides CLAUDE.md §4 sequential order; PRD 0.7 gates only P1. — Owner decision.
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
2026-10-31 ~02:40: S1 gate runs done on the vivo (four valid runs, PASS), recorded on `spike/S1-gate-runs`. S4 tuning PR #26 open (JVM tests only). Phone settings changed for S1 and still to restore: `screen_off_timeout` (was 1800000), brightness mode (was auto), `peak_refresh_rate` (was 90.0), `min_refresh_rate` (was unset).

2026-10-30 evening: S4 pass 2 (slot extraction) built on `feature/8-s4-slot-extraction` (worktree `/Users/saadahmed/bibi-wt/s4-slots`): `SlotExtractor.kt`, `SlotToolRouter`, pipeline switch, slot summary table, docs. JVM suite 547 checks pass (ceiling 100/100 with the correct label); `assembleDebug` builds; not run on a phone. Stopped at: PR open, device benchmark next.

2026-10-30 ~02:00 local session: PR #23 (`feature/5-s2-conversation-soak`, S2 chat-mode soak) is being fixed for the preface-prefill issue: in LiteRT-LM 0.16.1 `prefillPrefaceOnInit` did not prefill (turn 1 TTFT 10.3 s with all 311 system-prompt tokens), so the fix adds a scripted, untimed first exchange and marks the run invalid if the prefix was not prefilled. A plugged-in device check (not a gate run) showed turns 2–8 TTFT 1.25–1.33 s; the device check of the fix is pending. Worktrees under `/Users/saadahmed/bibi-wt/`. Main checkout `/Users/saadahmed/bibi-ai` is on `main`, up to date.

GitHub: PRs #12–#25 merged; #3 (ImgBot) closed unmerged; #26 open. Issues #4 (S1), #5 (S2), #8 (S4) open; #6, #7 closed. Milestones P0–P4 exist. No branches deleted (owner's choice).

## Next step
1. Merge the S1 results PR and PR #26 (owner), then re-run the S4 slot benchmark on the vivo with the tuning (#8).
2. S2: Qwen 0.5B-class soak (unplugged, airplane on; #5), the last open S2 item.
3. P0 gate is met on the vivo: record the owner's go for P1 (walking skeleton) as a Decision.
4. Optional: S1 kill/restart check; S4 owner on-screen checks (README steps 2–8, 13–14; #8).
5. Run the spikes on the Galaxy A23 if it is available (open item 10).
