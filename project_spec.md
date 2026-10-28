# Bibi AI — Project Spec (MVP)

Unified product and engineering spec. Derived only from [reference/Pip_PRD_Final.md](reference/Pip_PRD_Final.md) Part 0 and the sections it points to. Part 0 overrides Sections 1–26 wherever they conflict (PRD 0.1, 0.10). Anything not yet measured on a device is marked **[VERIFY]**. Choices not yet made are marked **[OPEN]**.

Product name: **Bibi AI** (owner decision, 2026-10-28). The character is called **Bibi**. The PRD in `reference/` still says "Pip"; read it as Bibi. Trademark and Play clearance is still open (PRD 0.12 item 5).

---

## 1. Product summary

Bibi is an offline, on-device AI companion for Android: a small animated character that floats over other apps.
- Chats by text, and by voice if the S3 gate passes, using a local language model.
- Remembers only what the user chooses to keep, stored locally. Default retention is 90 days; the user can pin, search, export, and delete.
- Performs a small set of safe phone actions.
- No account, no cloud inference, no analytics by default (PRD 0.2).

**Purpose** (PRD 0.3):
1. A credible portfolio product and a measured technical report.
2. A funded release, only if the results justify it.

**Constraints** (PRD 0.4):
- **Team and budget:** one developer plus Claude Code; personal budget, no paid infrastructure beyond Play registration.
- **Platform:** Android only, minimum Android 14 (API 34).
- **Stack:** Kotlin, Jetpack Compose, Material 3, Room.
- **Language:** English first, with strings and prompts externalized.
- **Distribution:** Google Play — internal track, then closed test, then production if gates pass.
- **Floor test device:** Samsung Galaxy A23. RAM variant and Android version are unconfirmed; assume 4 GB.
- **Download size:** each model pack is ≤ 2 GB (the limit applies per pack).
- **Network:** fully offline after the pack download; the pack download is the only network use.

## 2. MVP scope

Source: PRD 0.8. Anything outside "In MVP" needs a dated decision entry in `docs/project_status.md` before work starts (PRD 0.11).

### 2.1 In MVP (P0 priority)

| Area | MVP behavior | Detail |
|---|---|---|
| Overlay | Floating draggable character. Tap opens a compact chat card. Hide, snap to edge, and a persistent notification with a "Hide Bibi" action. | [features/overlay.md](docs/features/overlay.md), PRD §17, §12.4–12.5 |
| Chat | Typed chat with a streaming local LLM. Short answers by default. Stop-generation control. | PRD §13 |
| Model | One Core Pack (small LLM, ≤ 2 GB), downloaded at first run, with resume, checksum verification, and delete. | [features/model-runtime.md](docs/features/model-runtime.md) |
| Memory | Manual "remember this", automatic short summaries, pin, search, delete, export. 90-day default retention, user-adjustable. | [features/memory.md](docs/features/memory.md), PRD §14, §18 |
| Tools | Five Safe actions through direct shortcuts. The LLM can request them through a structured tool call. Every Sensitive action needs a confirmation card. | [features/tools-permissions.md](docs/features/tools-permissions.md), PRD §16 |
| Personality | Two presets (Friendly, Concise). Changes tone and system prompt only. | PRD 0.8 |
| Privacy | Privacy dashboard: what is stored, for how long, export, delete all. No network use except the pack download. | PRD §19, FR-180/181 |
| Distribution | Play internal track, then closed test. | PRD 0.9 |

### 2.2 In MVP only if the gate passes (P1 priority)
- Voice input and output (gate S3).
- Animated emotions beyond four states.
- Import of an exported backup.

### 2.3 Deferred (post-MVP, kept in PRD §1–26)
- Speaker recognition and verification.
- Multiple profiles.
- More than two personalities.
- Accessibility-based automation.
- SMS or call-log access.
- Live2D or 3D avatars.
- Localization.
- Upgrade LLM Pack and Voice Input Pack (Parakeet).
- Any monetization.

The PRD's reasoning: speaker verification, SMS/call permissions, and Accessibility automation each carry significant privacy-policy and Play review risk, and none is needed to prove the core idea (PRD 0.8).

**Non-goals** (PRD 0.3): monetization, multi-language, multi-user profiles, cloud sync, wearables, Live2D/3D, enterprise features, Accessibility automation.

## 3. Spike gates (P0 feasibility)

Source: PRD 0.7. Thresholds are planning targets and can be edited in `Pip_Spike_Tracker.xlsx`. All results are **Not measured**; see `docs/project_status.md`.

| Spike | What to build | Pass if | If it fails |
|---|---|---|---|
| **S1 Overlay** | Foreground service plus a draggable animated sprite over other apps | Stable for 1 hour; battery drain ≤ 3% per hour with screen on | Bubble/Edge mode only; lower animation frame rate; calm mode as default |
| **S2 LLM** | A Gemma 3 1B-class and a 0.5B Qwen-class model, int4, local | ≥ 5 tokens/s; first token ≤ 3 s; zero out-of-memory kills in a 50-prompt soak | Smaller model; shorter answers; typed chat only; apply the 0.5 decision rule |
| **S3a TTS** | Kokoro, with a Piper-style voice as fallback | Real-time factor ≤ 1.0 | Switch to the lighter voice on the low-end tier |
| **S3b STT** | A small streaming recognizer first, Parakeet second | Real-time factor ≤ 0.7; peak memory ≤ 600 MB | Push-to-talk short clips; typed input fallback |
| **S4 Tools** | Five actions (open app, set alarm, set timer, flashlight, dial with prefilled number), via direct shortcuts and via the LLM | Shortcut path under 300 ms | Simplify intent building; precompute the app list |

**Go/no-go** (PRD 0.7):
- P1 starts only when S1 and S2 pass, or when the 0.5 decision rule has been explicitly recorded as the chosen outcome.
- S3 and S4 can fail without stopping the project. They change feature scope instead.

**Planning expectations for the A23** (PRD 0.5, all **[VERIFY]**):
- 1B int4 decode at roughly 3–8 tokens/s.
- Don't rely on GPU or NPU.
- Parakeet is likely too heavy.
- Kokoro may run slower than real time.
- 4 GB RAM leaves little headroom.

**Decision rule (binding, PRD 0.5).** If S2 fails on the A23 but would pass on better hardware:
- (a) The recommended spec becomes 6 GB RAM or more and a recent chip.
- (b) The A23 stays the minimum for **typed chat with the smallest model only**, and voice is marked "not supported on this device".

Quality targets are never lowered silently to make the A23 pass.

## 4. Architecture summary

Full detail: [docs/architecture.md](docs/architecture.md), PRD §11–13.

**Processes** (PRD §11.3 D-05, kept by 0.10):
- `:main` hosts the UI, the overlay, the domain layer, repositories, and tool execution.
- `:inference` hosts all native AI runtimes behind a bound service (AIDL/Binder).
- The Model Scheduler can kill `:inference` so memory is fully returned on 4 GB devices.

**Layers** (PRD §12.1): Clean architecture with MVI.
- Composables observe `StateFlow` from ViewModels.
- Use cases are pure Kotlin.
- Repositories hide Room, files, and engines.
- An explicit **interaction state machine** (PRD §12.2.1) in the Interaction Orchestrator drives the UI and the avatar.

**Key components:**
- Interaction Orchestrator.
- Prompt Builder with a token budgeter (§13.4).
- Model Scheduler (§13.6).
- Tool Router + Permission Engine (§16) — the only path to device actions (§11.1 goal 4).
- Memory Manager (§14).
- Avatar, Emotion, and Behavior engines (§17).

**Storage:** Room in one encrypted database (`bibi.db`) via an SQLCipher-backed factory, with FTS5 for text search (§18.1, §11.5). Migrations are tested, and destructive fallback is disabled.

**Still open:**
- LLM runtime: LiteRT-LM or llama.cpp **[OPEN]** — decided by S2 (PRD §11.5, 0.10 OD-02).
- Avatar rendering: sprite sheet or Rive **[OPEN]** — OD-03 (§11.5).

**Process rule (§12.7):** each engine gets a dedicated single-thread inference dispatcher, all long operations are cancellable, and structured concurrency is required.

## 5. Data and privacy rules

Sources: PRD 0.2, 0.4, 0.8, §12.8, §14, §18, §19.

1. **On-device only.** No account, no cloud inference, no analytics or crash reporter in the default build (FR-183, §12.8). The only network use is the model pack download. This is verified with a network egress test (Appendix D.2).
2. **Encryption at rest.** The database key is generated on first run and wrapped by an Android Keystore key (§18.1).
3. **Retention.** 90 days by default. Options: 1 week, 1 month, 3 months, 1 year, forever (FR-093). Pinned items are excluded from expiry. A nightly job runs only while charging and idle (§14.6).
4. **User control.** Pin, search, edit, delete, export (FR-094, FR-097), plus "Delete all" with secure deletion (FR-181). Import is gate-dependent (2.2 above).
5. **Never stored by default.** Government ID numbers, full card numbers, and passwords are pattern-detected and refused (§14.4).
6. **Logs.** Local only, size-capped, PII-redacted; no raw prompts or responses unless debug mode is enabled for a limited time (FR-184, §12.8).
7. **Diagnostics.** Metrics are computed locally (§12.9). Testers share a diagnostics file manually (0.10 override of §23).
8. **Untrusted content.** Any tool call proposed while untrusted content is in context needs confirmation (FR-124, §16.6).
9. **Model integrity.** Packs are verified against a SHA-256 manifest before load. Hosting is decided in P1 (0.12 item 6).
10. **Secrets.** No secrets in the repo. Keystores and service-account files stay local (see `.env.example`).

## 6. Google Play policy checklist (P2 gate)

Source: PRD 0.9. **Every item is [VERIFY] against current Play policy before release.**

- [ ] `SYSTEM_ALERT_WINDOW`: requested only when the user enables the floating character, with a rationale and a no-overlay fallback (in-app chat).
- [ ] Foreground service: an appropriate type is declared and justified in the Play Console. The PRD proposes `specialUse` (§12.4). No microphone service type until voice ships.
- [ ] Microphone: requested on first use of voice only. No background listening (push-to-talk).
- [ ] Sensitive permissions: no SMS, call-log, contacts, or Accessibility in MVP. The dial action opens the dialer with the number prefilled, and the user presses call.
- [ ] Data safety form: "No data collected or shared". Confirm crash tooling is off by default.
- [ ] Privacy policy: hosted at a public URL; states that processing is on device and what is stored.
- [ ] Large downloads: in-app download from an owner-controlled host, or Play Asset Delivery if size and rules allow **[VERIFY]**. Declared accurately in the listing.
- [ ] Account testing requirement: a personal account may need about 12 testers for about 14 days of closed testing before production **[VERIFY]**. The account type is unconfirmed.
- [ ] Model licenses: review the Gemma terms and the Qwen license for the exact checkpoints used. Include a licenses screen in the app (§13.10).
- [ ] AI-generated content: an in-app way to report or flag problematic output **[VERIFY]**.
- [ ] AI disclosure and age notice at first run (FR-188, §17.7).

## 7. Phases P0–P4

Source: PRD 0.6, which replaces §21 for planning. Durations are part-time solo estimates, all **[VERIFY]**, to be re-planned after P0.

| Phase | Name | Duration [VERIFY] | Output | Exit gate |
|---|---|---|---|---|
| P0 | Feasibility spikes | 3–4 weeks | Measured results in the tracker; go/no-go memo | S1 and S2 pass, or the 0.5 rule is invoked |
| P1 | Walking skeleton | 4–6 weeks | Overlay + typed chat + one model + Room, installable from the Play internal track | Core loop works for 1 hour without a crash |
| P2 | Companion MVP | 6–8 weeks | Memory, five safe actions, two personalities, Privacy dashboard, voice if S3 passes | Ready for closed test; policy checklist (§6) green |
| P3 | Closed test | 2–3 weeks minimum | 10–12 testers, feedback, stability fixes | Crash-free sessions ≥ 99% in the core loop; retention signal recorded |
| P4 | Decision | 1 week | Public write-up and portfolio package | Owner's funding or no-funding decision |

**Working method** (PRD 0.11):
- Order: spikes → walking skeleton → features by risk.
- Unit tests first for the Permission Engine and the tool-call parser.
- An instrumented smoke test for the overlay.
- A manual device checklist for the A23.

## 8. Open items with defaults

Source: PRD 0.12, plus inconsistencies found while writing this spec.

| # | Open item | Default if unanswered | Impact |
|---|---|---|---|
| 1 | A23 RAM variant (4 or 6 GB) | Assume 4 GB | Model size, voice feasibility |
| 2 | A23 Android version | Assume Android 14+. If lower, the A23 cannot run the app. | Whether the A23 is a valid test device |
| 3 | Play account type | Assume personal → closed test with about 12 testers | Release timeline |
| 4 | Second test phone (6–8 GB) | None; results are labeled "single-device" | Credibility of benchmarks |
| 5 | Final app name | **Bibi AI** chosen by owner. A web check found no exact-match app, but similar "Bibi" products exist (see brainstorm.md); formal trademark and Play check still needed [VERIFY] | Branding |
| 6 | Model pack hosting | Decide in P1 after checking Play Asset Delivery limits [VERIFY] | Cost, release path |
| 7 | Dial action risk class: 0.8 calls it "Safe", §16.3 rates `call_contact` SENSITIVE | Treat it as **Sensitive** (confirmation card), since the stricter rule is safer | Tool UX |
| 8 | Decision log location: 0.11 says `DECISIONS.md`; the owner chose `docs/project_status.md` → "Decisions" | Use `docs/project_status.md` (recorded as a decision) | Process |
| 9 | Memory search type: keyword (FTS5) only, or semantic too (§14.3 embedding index) | FTS5 keyword search first. Semantic search needs an embedding model in the RAM budget [VERIFY]. | RAM, scope |
| 10 | Which four emotion states are in base MVP | Proposed: Idle, Thinking, Speaking/Responding, Confused (error), mapped from §20.6.1 [OPEN] | Avatar asset scope |
| 11 | LLM runtime | Decided by S2 [OPEN] | Architecture |
| 12 | Avatar rendering tech (OD-03) | Decide during S1 [OPEN] | Overlay performance |

## 9. Top risks

Source: PRD 0.13.

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| LLM too slow on the A23 | High | High | S2 first; 0.5 rule; smaller model |
| Overlay killed by Samsung battery management | Medium | High | Watchdog; onboarding guide to exempt the app; in-app chat fallback |
| Play review friction | Medium | High | Minimal permissions; accurate declarations; early internal-track upload |
| Scope creep from the long PRD | High | Medium | 0.8 is the only MVP scope; deferrals are logged |
| Solo-developer time | High | Medium | Part-time estimates; gates allow stopping cheaply after P0 |
