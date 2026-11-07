# Spec: P2 companion MVP (capability map and shared rules)

**Approved by the owner on 2026-10-31.** Module specs: `SPEC-<module id>.md`. Plan: [tasks/plan-p2.md](tasks/plan-p2.md).

P2 (PRD 0.6) delivers memory, the five safe actions, two personalities, a Privacy dashboard and voice. Exit gate: closed-test ready, and the Play policy checklist (PRD 0.9) green. The P1 index is [SPEC.md](SPEC.md); its modules, commands, structure and code style still apply.

## Owner answers (2026-10-31)

| Question | Answer |
|---|---|
| Model pack download (PRD 0.8 "Model") | **Side-load for now.** The P1 file import stays for the closed test; no `INTERNET` permission. Download is built once hosting is decided (open item 6). Superseded 2026-11-07: in-app download, SPEC-model-download.md. |
| Database encryption (PRD §18) | **Plain Room for MVP**, protected by Android file-based encryption. Recorded as a PRD override; revisit before production. |
| Voice | **In the in-app chat and in the floating card.** Adds the `microphone` foreground-service type and its Play declaration. A Parakeet + Gemma memory check on the vivo comes first. |
| Dial risk class (open item 8) | **Sensitive:** a confirmation card with the exact number; nothing opens until Confirm. |

## Capability map

| Module id | Responsibility | Depends on |
|---|---|---|
| [`tools`](SPEC-tools.md) | Port the S4 pipeline into the app: shortcut matcher, Decision Maker router, slot extraction, `ToolCallParser` → `ToolValidator` → `PermissionEngine` → `ToolExecutor`. The five actions (open app, alarm, timer, flashlight, dial). Confirmation card in the chat screen and the floating card. Local audit log with redacted values. Import of the router model (`embeddinggemma-2-text-270m.litertlm`, 164,626,432 bytes) next to Gemma. | chat, model-import (P1) |
| [`personality`](SPEC-personality.md) | Two presets, Friendly and Concise (PRD 0.8). Each is a system prompt; the choice is saved and takes effect when the conversation next opens. | inference, chat (P1) |
| [`memory`](SPEC-memory.md) | Room tables plus FTS5 keyword search. "Remember this" (a button on a message, and phrases like "remember that…"); a never-store filter (ID numbers, card numbers, passwords); short automatic summaries made by Gemma when idle, never during a reply; pinned and top matches added to the prompt within a token budget; pin, edit, delete, search; retention with a 90-day default (WorkManager); JSON export. | chat, inference (P1) |
| [`avatar-states`](SPEC-avatar-states.md) | Bibi's four states (idle with blink, thinking, speaking, confused) on floating Bibi and in the app, driven by the chat and voice state. | overlay, chat (P1) |
| [`voice`](SPEC-voice.md) | Push-to-talk with Parakeet TDT 0.6B v2 int8 (STT) and spoken replies with Piper en_US amy low (TTS), in the in-app chat and the floating card. Microphone permission asked on first use. `microphone` foreground-service type while the card is listening. Voice models side-loaded like Gemma. | chat, overlay, avatar-states |
| [`privacy`](SPEC-privacy.md) | Privacy dashboard: what is stored and for how long, export, delete all. Safe mode toggle (FR-076: no tools, no memory writes). Licenses screen (Gemma terms, LiteRT-LM, MediaPipe, Piper, Parakeet). Privacy policy screen plus the text for the owner to host. "Report this reply" on a Bibi message through the share sheet (PRD 0.9; no network). | memory, tools, personality, voice |
| [`design`](SPEC-design.md) | Design system in `core/designsystem` (tokens light/dark, type, components), navigation, and a screen-by-screen restyle of every screen; a modern look built around Bibi that doesn't read as AI-made. Owner picks a direction from mock-ups first. | all screens above |
| [`closed-test`](SPEC-closed-test.md) | PRD 0.9 checklist evidence, Data safety answers ("no data collected or shared"), foreground-service declarations (`specialUse`, `microphone`), store listing text, version bump. **The owner builds, signs and uploads.** | all |

Build order: tools → personality → memory → avatar-states → voice → privacy → design → closed-test (design added at the owner's request, 2026-10-31).

Why this order: tools is mostly a port of measured S4 code; personality and memory both change what goes into the prompt, so they are built one after the other; voice is the biggest memory risk, so its RAM check runs first (as the plan's Task 1) even though the module is built later.

## Shared design rules for P2

- **One path to actions.** Every action, from a shortcut or the router, goes through `PermissionEngine` (CLAUDE.md §4). Tests first for the engine, the parser and the validator (PRD 0.11). Strix runs on the tools and export code (CLAUDE.md §9).
- **One Gemma conversation.** The preset and pinned memories are part of the system prompt, set when the conversation opens; a change applies at the next reopen (about 7 s on the vivo). Keyword-matched memories ride along with each user turn, so they need no reopen. Summaries use the engine only while the user is idle.
- **Models in `:inference`.** The router, Parakeet and Piper run in the `:inference` process next to Gemma. Whether all of them fit at once on the vivo is measured first [VERIFY]; if not, the plan loads voice models on demand.
- **Schema changes** (memory tables, audit log) are Room migrations with a `MigrationTestHelper` test. Under the merge rule they are major merges: the owner approves those PRs.
- **Still no `INTERNET` permission** (superseded 2026-11-07 for the model download only, SPEC-model-download.md), no analytics, no crash reporter. No SMS, call log, contacts or Accessibility.

## Out of P2

Model download and hosting (owner answer above), database encryption (owner answer above), semantic memory search (FTS5 only; open item 9), import of an exported backup (PRD 0.8 "only if the gate passes"), emotions beyond the four states, encrypted export (plain JSON first), the 24-hour "Recently deleted" window, more than two personalities, wake word.

## Open questions

- Memory UI shows facts and summaries only, not the six PRD §14 memory types [OPEN, default: facts and summaries].
- App name matching for `open_app` ("open maps" → which package) [OPEN, default: S4's whole-word match on the launcher list, ask when ambiguous].
- Galaxy A23 is still untested (open items 1, 2, 10); P2 numbers come from the vivo Y17s.
- The P1 exit gate (1 hour, owner) and the Play internal-track upload are still open; P2 work starts alongside them at the owner's request.
