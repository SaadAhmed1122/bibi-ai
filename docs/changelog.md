# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html) once the app ships.

## [Unreleased] - 2026-10-28

### Added
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
