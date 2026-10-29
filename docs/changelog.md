# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html) once the app ships.

## [Unreleased] - 2026-10-28

### Added
- Project scaffold created: `brainstorm.md`, `project_spec.md`, `CLAUDE.md`, `.env.example`, `.gitignore`.
- `reference/Pip_PRD_Final.md` copied unchanged from the source.
- `docs/architecture.md`, `docs/project_status.md`, and feature docs for overlay, model runtime, memory, and tools/permissions.
- `docs/design/codex-design-prompts.md` with prompts for character and UI design work.
- vivo Y17s recorded as a test device in `docs/project_status.md` (new "Test devices" section), `CLAUDE.md`, and `docs/features/overlay.md`.
- Agent tooling: `.mcp.json` (Context7, 21st), `.claude/settings.json` (Agent Skills plugin, Graphify hooks), `.claude/skills/` (Graphify, Strix), `docs/tooling.md`, and CLAUDE.md §9 usage rules.
- Expanded `.gitignore` for Android/Kotlin/Gradle, native builds, model files, Python eval harness, and agent tooling outputs.
- CLAUDE.md §10 "Repository Etiquette & Git Rules" (branch-per-change, Issues as source of truth, PR before merge).

### Changed
- Renamed the product from the working name "Pip" to **Bibi AI** across all docs, config, and the repo folder (`bibi-ai/`). The PRD in `reference/` is unchanged.
- Design: chose the seed character, restyled so it reads as an AI phone helper rather than a plant or health app. New `docs/design/bibi-character-gemini.md` (brief, palette, one-image-per-prompt Gemini prompts). `codex-design-prompts.md` now builds the vector master from the approved Gemini reference.
- Design: character changed to a fluffy mochi puff with an AI screen-face, glowing spark tuft and mitten paws. `docs/design/bibi-character-gemini.md` re-created with the new brief and 17 one-image Gemini prompts; Codex prompts updated to match (layer names, stylized fur).
