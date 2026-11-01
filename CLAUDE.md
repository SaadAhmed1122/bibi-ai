# CLAUDE.md — Bibi AI project memory

## 1. What Bibi AI is
An offline, on-device AI companion for Android: a small animated character floating over other apps that chats with a local LLM, keeps local memory, and performs a few safe phone actions.
It's a portfolio piece and technical experiment by Saad (solo) plus Claude Code. Funding only if results justify it.
Product name **Bibi AI**; the character is **Bibi**. The PRD in `reference/` uses the old working name "Pip"; treat it as Bibi.

**Current phase: P2 — Companion MVP** (planned 2026-10-31; P1 code-complete, its exit gate pending with the owner). Status: [docs/project_status.md](docs/project_status.md).

## 2. Stack and constraints
- **Platform:** Android only, minSdk = Android 14 (API 34).
- **Stack:** Kotlin, Jetpack Compose, Material 3, Room.
- **LLM runtime:** LiteRT-LM 0.16.1 with Gemma 3 1B int4, chat mode with prefix reuse (S2, 2026-10-30/31).
- **Processes:** a separate `:inference` process for native AI runtimes (bound service).
- **Language:** English first; strings and prompts externalized.
- **Distribution:** Google Play (internal → closed test → production).
- **Floor device:** Samsung Galaxy A23, assumed 4 GB RAM (unconfirmed).
- **Also testing on:** vivo Y17s — 6 GB RAM (ignore the +6 GB "extended RAM"; it is swap), Helio G85, Android 15. Details in project_status.md → Test devices.
- **Network:** offline after the model pack download. No accounts, no cloud inference, no analytics by default.
- **Packs:** each model pack ≤ 2 GB.

## 3. Repository map
```
brainstorm.md               raw ideas, flows, name candidates, voice-note inbox
project_spec.md             unified MVP spec (product + engineering)
SPEC.md, SPEC-P2.md         P1 and P2 capability maps; SPEC-<module>.md per module
tasks/plan.md, plan-p2.md   P1 / P2 task plans (issues #32–#49 / #72–#90)
app/, core/, …              P1 app (Gradle root: settings.gradle.kts)
CLAUDE.md                   this file
.env.example                required variable names (no values)
.env                        local values; git-ignored
.gitignore
TESTING.md                  owner's on-phone test checklist (all spikes)
.mcp.json                   project MCP servers (context7, 21st); keys come from env
.claude/settings.json       agent-skills plugin, MCP approval, graphify hooks
.claude/skills/             graphify + Strix skills (pinned in skills-lock.json)
.claude/agents/             project subagents (see §11)
.claude-plugin/, plugins/  marketplace + agent-board (Agents pane, subagent toasts)
reference/
  Pip_PRD_Final.md          source PRD; Part 0 is binding
  Pip_Spike_Tracker.xlsx    spike measurements (source of truth for results)
docs/
  architecture.md           processes, modules, data flows, scheduler
  changelog.md              Keep a Changelog
  project_status.md         phase, milestones, spikes, open items, decisions
  design/                   character brief, Gemini and Codex art prompts
  linkedin/posts.md         LinkedIn log of test findings (+ media/ charts); first-post.md = first post + answer guide
  tooling.md, local-setup.md  agent tools; same setup on a laptop or cloud
  release.md                owner checklist: upload key, bundleRelease, Play internal track
  play-console.md, tester-guide.md  Play Console answers + 0.9 checklist; model via Drive (+ model-pack/)
  privacy-policy.md         privacy policy (same text in the app; owner hosts it)
  p1-gate.md                P1 exit gate: 1-hour session results
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
- No `TODO`/`FIXME` without a GitHub issue reference, e.g. `TODO(#N)` (see §10).
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
- Tooling setup and limits: [docs/tooling.md](docs/tooling.md)

## 9. Tooling — mandatory during implementation
- **Workflow (Agent Skills plugin):**
  - New feature or spike: `/spec` → `/plan` → `/build` (one task, one commit) → `/test` → `/review`.
  - Release gates: `/ship`.
  - Let auto-triggered skills (TDD, API design, security) run.
- **Context7:** query `resolve-library-id` + `query-docs` before writing code against any library or Android API, pinned to the version in `libs.versions.toml`. Never code library APIs from memory.
- **Graphify:** follow the `## graphify` rules below. Run `graphify update .` after code changes.
- **21st MCP:** UI inspiration search only. It returns React/Tailwind; translate ideas into Compose with `core/designsystem` tokens. Never paste web code into the app.
- **Strix (local CLI):** run the `find-security-vulnerabilities-in-code` skill on changes to the Permission Engine, tool router, export/import, encryption, or overlay touch handling, and at the P2 gate. Fix findings with `fix-security-vulnerabilities-with-strix`. No Strix Cloud uploads without a decision entry.
- **ponytail:** on in every session and subagent (its SessionStart hook): simplest code that works, YAGNI, stdlib first. CLAUDE.md wins on conflict: tests first, structured concurrency and full checks on Permission Engine, tool router and parser code are never "shortcuts". Write a deferred `shortcut:` as `// shortcut(#N): ...` with an issue, like TODOs (§5). Run `/ponytail-review` alongside `/review` before a PR.
- If a tool is unavailable (no key, not installed, server down), say so in the session summary and continue with official docs. Don't silently skip it.

## 10. Repository Etiquette & Git Rules
- **NEVER push directly to `main`.** `main` changes only through merged Pull Requests.
- **One branch per change**, created from an up-to-date `main`, with a descriptive name:
  - `feature/<issue#>-<short-name>` (e.g. `feature/12-overlay-drag-snap`)
  - `bugfix/<issue#>-<short-name>`
  - `spike/<S1..S4>-<short-name>`
  - `docs/<short-name>`
  - `chore/<short-name>`
- **GitHub Issues are the single source of truth** for bugs, features, and milestones (P0–P4 milestones, S1–S4 spike issues).
  - Every branch, commit series, and PR references its issue (`Closes #N`).
  - `docs/project_status.md` keeps phase, decisions, and the session stopping point, and links issue numbers instead of duplicating them.
  - A `TODO` in code must cite an issue: `// TODO(#N): ...`.
- **Pull Requests:** when a feature is complete and verified (tests pass, `/review` done, status and changelog updated), open a PR against `main` with `gh pr create`. Never merge without the owner's explicit confirmation.
- **Commits:** small and focused, imperative subject line (≤ 72 chars), no secrets, no generated outputs. Never force-push to `main`. Never rewrite pushed history on shared branches without asking.

## 11. Subagents (`.claude/agents/`)
| Agent | Use for |
|---|---|
| `spike-analyst` | Device logs and benchmarks → gate verdicts, RESULTS.md, status |
| `android-engineer` | Kotlin, Gradle, manifest and resource changes |
| `scope-guard` | Read-only check of new work against PRD 0.8 before it starts |
| `docs-keeper` | Status, changelog and feature docs at session end |
| `repo-keeper` | Git and GitHub: branches, commits, pushes, syncing with main, conflicts, PRs, issues (never merges) |
| `agent-skills:code-reviewer` / `security-auditor` / `test-engineer` (plugin) | Review before a PR; security pass; test plans |

- Before delegating, tell the owner **which agent** gets **which task**; report each result as it arrives. Run independent agents in parallel.
- Agents follow this file and the git rules (§10); none commits to `main`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.
