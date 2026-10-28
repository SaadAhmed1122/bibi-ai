# CLAUDE.md — Pip project memory

## 1. What Pip is
An offline, on-device AI companion for Android: a small animated character floating over other apps that chats with a local LLM, keeps local memory, and performs a few safe phone actions.
It's a portfolio piece and technical experiment by Saad (solo) plus Claude Code. Funding only if results justify it.
Working name "Pip"; the final name is undecided.

**Current phase: P0 — Feasibility spikes.** No app code until S1 and S2 are measured. Status: [docs/project_status.md](docs/project_status.md).

## 2. Stack and constraints
- **Platform:** Android only, minSdk = Android 14 (API 34).
- **Stack:** Kotlin, Jetpack Compose, Material 3, Room.
- **LLM runtime:** chosen by spike S2 [OPEN].
- **Processes:** a separate `:inference` process for native AI runtimes (bound service).
- **Language:** English first; strings and prompts externalized.
- **Distribution:** Google Play (internal → closed test → production).
- **Floor device:** Samsung Galaxy A23, assumed 4 GB RAM (unconfirmed).
- **Network:** offline after the model pack download. No accounts, no cloud inference, no analytics by default.
- **Packs:** each model pack ≤ 2 GB.

## 3. Repository map
```
brainstorm.md               raw ideas, flows, name candidates, voice-note inbox
project_spec.md             unified MVP spec (product + engineering)
CLAUDE.md                   this file
.env.example                required variable names (no values)
.env                        local values; git-ignored
.gitignore
reference/
  Pip_PRD_Final.md          source PRD; Part 0 is binding
  Pip_Spike_Tracker.xlsx    spike measurements (source of truth for results)
docs/
  architecture.md           processes, modules, data flows, scheduler
  changelog.md              Keep a Changelog
  project_status.md         phase, milestones, spikes, open items, decisions
  design/codex-design-prompts.md   prompts for character + UI design work
  features/overlay.md       floating character, foreground service (S1)
  features/model-runtime.md LLM runtime, gates, decision rule (S2)
  features/memory.md        memory, retention, export/delete
  features/tools-permissions.md  tool router, permission engine (S4)
```

## 4. Working rules
- **Spike-first.** Order is S1 → S2 → S3/S4 → walking skeleton → features by risk. P1 starts only after S1 and S2 pass, or after the PRD 0.5 decision rule is recorded.
- **Scope control.** Anything outside PRD Part 0.8 "In MVP" needs a dated entry in `docs/project_status.md` → Decisions *before* work starts.
- **Secrets.** Never commit `.env`, keystores (`*.jks`, `*.keystore`, `*.p12`), service-account JSON, or `local.properties`. Never write real secrets in any file.
- **Truthfulness.** Mark every unmeasured or unverified claim `[VERIFY]` and every undecided choice `[OPEN]`. Don't invent numbers.
- **No placeholder text in docs** (no "lorem", "TBD" without a reason, or empty sections). If something is unknown, say what is unknown and why.
- **Single path to device actions.** All actions go through the Tool Router + Permission Engine (PRD §11.1, §16).
- **Permissions:** no SMS, call-log, contacts, or Accessibility in MVP (PRD 0.9).

## 5. Code style
- Kotlin official code style (`kotlin.code.style=official`).
- Compose conventions:
  - Stateless composables with state hoisting.
  - `Modifier` is the first optional parameter.
  - Previews for each screen state.
  - No business logic in composables.
- Architecture: MVI. `StateFlow<UiState>` from ViewModels, pure-Kotlin use cases, repositories at the data boundary (PRD §12).
- Coroutines: structured concurrency, cancellable long operations, a single-thread dispatcher per inference engine (PRD §12.7).
- No `TODO`/`FIXME` without a matching note in `docs/project_status.md` (Open items or Next step).
- Tests first for the Permission Engine and the tool-call parser (PRD 0.11).

## 6. Session protocol
**At session start:**
1. Read `docs/project_status.md`.
2. Read the relevant `docs/features/*.md` file for the task.
3. Check the spec or PRD section it references before changing behavior.

**At session end:**
1. Update `docs/project_status.md`: done, in progress, exact stopping point (file/function/step), next step.
2. Append an entry to `docs/changelog.md`.

## 7. Decision log rule
Gate results (S1–S4 pass/fail with numbers), scope additions, and any override of the PRD or the spec go in `docs/project_status.md` → **Decisions** as a dated line: `YYYY-MM-DD — decision — reason — source`.
Gate measurements also go in `reference/Pip_Spike_Tracker.xlsx`.

## 8. Pointers
- PRD (Part 0 binding): [reference/Pip_PRD_Final.md](reference/Pip_PRD_Final.md)
- Spec: [project_spec.md](project_spec.md)
- Architecture: [docs/architecture.md](docs/architecture.md)
- Status: [docs/project_status.md](docs/project_status.md)
