# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html) once the app ships.

## [Unreleased] - 2026-10-28

### Fixed
- The floating chat card no longer hides its message box under the keyboard in portrait: it moves above the keyboard and shrinks to fit (#71).
- After Stop or Clear chat, the inference service reports Loading while it reopens the conversation (~7 s), so Send is blocked and the chat shows "Bibi is getting ready again…" (#37).
- Crash log: an exit the system reports twice is now logged once (#55).

### Added
- P2 Task 3: the action router (MediaPipe Decision Maker 1.2.0) runs in `:inference` on its own thread; its model is imported on the model screen under "Phone actions (optional)"; the debug LLM check can route a message. On the vivo 6/6 test requests routed right in about 0.55 s (#74).
- P2 Task 2: `core:tools`, the S4 tool core moved into the app as a pure-Kotlin module (parser, validator, Permission Engine, shortcut matcher, slot extraction), 491 checks under JUnit (#73).
- P2 memory check on the vivo: Gemma with Parakeet, Piper and the router, one pair at a time; voice models will load on demand (#72).
- P2 design module: `SPEC-design.md`, tasks #93 (design system and navigation) and #94 (screen-by-screen restyle).
- P2 plan: capability map `SPEC-P2.md` (owner-approved), module specs for tools, personality, memory, avatar states, voice, privacy and the closed test, and `tasks/plan-p2.md` (issues #72–#90).
- S4 tuned slot extraction measured on the vivo: 95/100 right calls (was 87), no wrong call, 0 calls on "none" (#8).
- P1 Task 18: the 1-hour exit-gate script (TESTING.md) and the results file `docs/p1-gate.md` (#49).
- P1 Task 17: release build with R8 and resource shrinking (LiteRT-LM keep rule), upload signing from `~/.gradle/gradle.properties` (unsigned without it), and the owner's upload checklist `docs/release.md` (#48).
- P1 Task 16: Floating Bibi and Calm mode switches on Home. The overlay permission is asked only when the switch is turned on, after an explainer; without it the switch stays off. The notification's Turn off turns the switch off; Bibi comes back when the app opens if the switch is on (#47).
- P1 Task 15: first-run screens (`feature:onboarding`): Welcome, Model (import or skip, Gemma terms link), Notifications (allow or not); the first permission request in the app is on step 3 (#46).
- P1 Task 14: tapping floating Bibi opens a chat card over any app (type, stream, Stop), closed with Back, a tap outside, or Close; the keyboard's Send key sends (the vivo's landscape keyboard covers the Send button). The app chat and the card share one model connection (#45).
- P1 Task 13: floating Bibi (`companion:overlay`): drag and snap to an edge, saved position, kept on screen after rotation, notification with Hide/Show, Open chat and Turn off; a tap opens the chat. Debug start button on Home until the Floating Bibi switch (#44).
- P1 Task 11: model screen: pick `gemma3-1b-it-int4.litertlm` with the system file picker, copy and SHA-256 check with progress and Cancel, clear messages for a wrong file, Remove model; the chat links to it when the model is missing. On the vivo the 585 MB import takes ~7 s (#42).
- P1 Task 7: after Stop, an error or a full context, the conversation is rebuilt with the recent turns (up to 6, at most ~400 tokens), so Bibi remembers them; the app survives a crash of the `:inference` process and reloads the model by itself (#38).
- P1 Task 6 + 9: the chat screen (`feature:chat`): streaming answers, Send turns into Stop, a model status banner, 2,000-character input cap, Clear chat; every message is saved in Room and survives restarts. The service logs first-word time per turn (`BibiInference`). On the vivo: 10 turns, first word in 1.26–1.31 s (#37, #40).
- P1 Task 5: Gemma 3 1B on LiteRT-LM in the `:inference` process with a debug LLM screen (#36).
- P1 Task 8: Room message history (`core:database`) (#39).
- P1 Tasks 2–4, 10, 12: local crash/ANR log with a debug viewer, Gemma terms notice, the `InferenceClient` contract with Messenger codec and fake, model-import logic (copy, SHA-256, size, cancel), and overlay snap/clamp/frame-schedule logic (#33–#35, #41, #43).
- P1 app project at the repo root (`app`, `core:common`, `core:designsystem`): empty Home screen, Bibi theme, and a build check that fails if any merged manifest requests INTERNET (#32).
- P1 implementation plan `tasks/plan.md`: 18 tasks in 6 phases with checkpoints, riskiest path (model streaming in `:inference`) first.
- P1 specs: `SPEC.md` (capability map, shared stack and rules) and eight module specs (`SPEC-<module>.md`).
- Decision: P1 (walking skeleton) starts; the P0 gate is met on the vivo Y17s.
- Decision: Qwen dropped; Gemma 3 1B int4 is the only LLM (S2 closed on the vivo).
- S1 gate runs on the vivo Y17s (four valid runs, logs in `spikes/s1-overlay/results/`): PASS, Bibi costs 27.8 mA at 12 fps and 11.5 mA in Calm.
- S4 slot tuning: router threshold 0.25, set_alarm → start_timer re-route for durations, and a vendor-name check for apps that are not installed ("google drive"). JVM tests only (559 checks); not yet run on a phone.
- S4 slot-extraction device result on the vivo Y17s: 87/100 correct calls, ~0.5 s, 600 MB.
- S4 pass 2 is deterministic slot extraction (`SlotExtractor`, `SlotToolRouter`) after the Decision Maker label, by owner decision; the Gemma 3 1B tool call stays behind an off-by-default "LLM tool call (experimental)" switch. JVM ceiling on the 110 benchmark prompts with the correct label: 100/100; not yet run on a phone.
- S2 chat-mode soak result on the vivo Y17s: Gemma 3 1B int4 PASS (TTFT p95 2,409 ms, 7.04 tok/s, 0 OOM).
- S2 "Chat soak 50" mode: the 50 prompts as turns of one LiteRT-LM conversation, with a longer Bibi system prompt (`system_bibi.txt`) prefilled once outside the timed region, KV-cache-budget resets, and a separate "chat mode" verdict in the summary (`spikes/s2-llm/SPEC.md` §2.1). First device try showed the Gemma 3 preface was not prefilled (system-only preface renders to BOS); fixed with a scripted user/model exchange in `initialMessages`, and such runs are now invalid ("preface not prefilled"). Fix not yet run on a device.
- S2 results on the vivo Y17s: Gemma 3 1B int4 soak (gate FAIL on TTFT p95 3,493 ms; speed and memory pass) and earlier smoke/invalid runs, in `spikes/s2-llm/results/`.
- S4 real-LLM path (Decision Maker router + LiteRT-LM tool calls) and its vivo Y17s results: shortcut gate PASS; Gemma 3 1B tool calls 0 % valid.
- Project plugin ponytail (DietrichGebert/ponytail) enabled; always on via its hooks; precedence rule in CLAUDE.md §9.
- Project plugin humanizer (blader/humanizer) enabled in `.claude/settings.json`.
- Project plugins impeccable, ui-ux-pro-max and last30days enabled in `.claude/settings.json`; usage rules in `docs/tooling.md`.
- Project scaffold created: `brainstorm.md`, `project_spec.md`, `CLAUDE.md`, `.env.example`, `.gitignore`.
- `reference/Pip_PRD_Final.md` copied unchanged from the source.
- `docs/architecture.md`, `docs/project_status.md`, and feature docs for overlay, model runtime, memory, and tools/permissions.
- `docs/design/codex-design-prompts.md` with prompts for character and UI design work.
- vivo Y17s recorded as a test device in `docs/project_status.md` (new "Test devices" section), `CLAUDE.md`, and `docs/features/overlay.md`.
- Agent tooling: `.mcp.json` (Context7, 21st), `.claude/settings.json` (Agent Skills plugin, Graphify hooks), `.claude/skills/` (Graphify, Strix), `docs/tooling.md`, and CLAUDE.md §9 usage rules.
- Expanded `.gitignore` for Android/Kotlin/Gradle, native builds, model files, Python eval harness, and agent tooling outputs.
- CLAUDE.md §10 "Repository Etiquette & Git Rules" (branch-per-change, Issues as source of truth, PR before merge).
- S3 voice results on the vivo Y17s: `spikes/s3-voice/results/RESULTS.md`, `analyze.py` and raw logs on branch `spike/S3-voice` (Kokoro fails, Piper passes; S3b default recognizer open).
- Decision: Parakeet TDT 0.6B v2 int8 is the S3b default recognizer by owner override of the PRD 0.7 STT memory limit.
- S2 Qwen3-0.6B LiteRT-LM smoke results on the vivo Y17s recorded in `docs/project_status.md` (open item 11; not a gate run).
- 2026-10-30: spike apps merged to `main`: S1 overlay (`spikes/s1-overlay/`, PR #10), S2 local LLM (`spikes/s2-llm/`, PR #12), S4 tools and permissions (`spikes/s4-tools/`, PR #13) and S3 voice (`spikes/s3-voice/`, PR #14); S3 results now on `main` (`spikes/s3-voice/results/RESULTS.md`).

### Changed
- Renamed the product from the working name "Pip" to **Bibi AI** across all docs, config, and the repo folder (`bibi-ai/`). The PRD in `reference/` is unchanged.
- Design: chose the seed character, restyled so it reads as an AI phone helper rather than a plant or health app. New `docs/design/bibi-character-gemini.md` (brief, palette, one-image-per-prompt Gemini prompts). `codex-design-prompts.md` now builds the vector master from the approved Gemini reference.
- Design: character changed to a fluffy mochi puff with an AI screen-face, glowing spark tuft and mitten paws. `docs/design/bibi-character-gemini.md` re-created with the new brief and 17 one-image Gemini prompts; Codex prompts updated to match (layer names, stylized fur).
- Design: Bibi is now a cute cartoon kitten (no robot or AI look). `docs/design/bibi-character-gemini.md` rewritten with the cat brief, palette, state table (ears, tail, pose, charm glow), and 17 one-image Gemini prompts; Codex prompts updated (cat layer names, tail segments).
- Design: 17 Gemini concept images added under `docs/design/character/gemini/`, renamed by state; review table added to the character brief. Forehead moon patch removed at the owner's request.
- Design: Bibi vector master v1 (`docs/design/character/master/`): layered `front_neutral.svg`, `silhouette.svg`, `palette.json` sampled from the Gemini master, 64/128/512 px previews, README with layers and known differences.
- Design: Bibi MVP state SVGs (idle, idle blink, thinking, speaking, confused) in `docs/design/character/states/` with `states.json` and a preview sheet; generator `tools/build_states.py`; SVG render helper `tools/render_svg.js`.
- Claude subagents for the project (`.claude/agents/`) and CLAUDE.md §11.
- `repo-keeper` subagent for git and GitHub housekeeping.
- `docs/project_status.md`: S3 results row, S3a/S3b decisions, P0 milestone status, vivo Y17s device facts confirmed on device (Android 15 / API 35, model V2310), new open items 11–13, local environment notes, stopping point and next steps.
- agent-board plugin in the repo (`plugins/agent-board`, repo marketplace) and `docs/local-setup.md` for laptop setup and resuming from phone or cloud.
- `TESTING.md` at the repo root: the owner's single on-phone testing checklist (devices, spike status table, common settings, S1 overlay protocol and kill/restart check, how to send results). S2–S4 sections get their steps when each spike app lands.
- 2026-10-30: status docs synced with the merged work. `docs/project_status.md`: P0 row shows the P1 gate not met; S2, S3 and S4 rows; S3 results link points to `main`; open item 11 rewritten (Gemma 3 1B soaked, Gemma 4 removed from the phone, Qwen file still open); new open items 14 (S2 chat mode for the gate), 15 (S4 pass-2) and 16 (S1 path); new stopping point and next steps; decision to keep merged branches. `TESTING.md`: status table, spikes built from `main`, S2 Chat soak 50 owner step (PR #23 build), S4 remaining on-screen checks. `docs/local-setup.md`: spike PRs shown as merged. `docs/tooling.md`: tool count.
