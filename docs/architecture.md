# Architecture

Source: PRD §11 (system), §12 (Android app), §13 (AI), plus Part 0 (0.4, 0.8, 0.10). Where they conflict, Part 0 wins. Undecided choices are marked **[OPEN]**; unmeasured numbers are marked **[VERIFY]**.

## 1. Goals (PRD §11.1)
1. Isolate heavy AI work so the character never stutters while the model decodes.
2. Put every capability (inference, STT, TTS, avatar renderer, memory store) behind a replaceable interface.
3. Make the idle state cheap: only a light overlay and a few event listeners are alive.
4. Centralize safety: one Permission Engine and one Tool Router. No other path to device actions.
5. Make the system testable offline and deterministically, using fake engines.

## 2. Process model (PRD §11.3, decision D-05; kept by 0.10)

| Process | Contents | Why |
|---|---|---|
| `:main` | App UI, overlay host (`CompanionService` foreground service + `WindowManager` windows), domain layer, repositories, Room, tool execution | Standard process for everything that isn't heavy |
| `:inference` | Native AI runtimes: LLM (later STT, TTS, embedding) | An OOM or native crash doesn't take down the overlay. Killing the process is the only reliable way to return native memory on 4 GB devices. |

- **Communication:** a bound service over AIDL/Binder. `SharedMemory` is used for audio buffers once voice ships.
- **Deferred:** a separate `:overlay` process is a v1.1 evaluation (§11.3), not MVP.

```mermaid
flowchart LR
    subgraph MAIN[":main process"]
        UI["Compose app UI<br/>(chat, memory, privacy, settings)"]
        FGS["CompanionService<br/>foreground service (specialUse) [VERIFY]"]
        OVL["Overlay host<br/>character window + interaction card window"]
        ORCH["Interaction Orchestrator<br/>(state machine)"]
        TOOLS["Tool Router + Permission Engine"]
        MEM["Memory Manager"]
        SCHED["Model Scheduler (client side)"]
        DB[("Room · bibi.db<br/>SQLCipher + FTS5")]
    end
    subgraph INF[":inference process"]
        SVC["InferenceService (bound, AIDL)"]
        LLM["InferenceEngine<br/>runtime [OPEN]"]
    end
    FGS --> OVL
    UI --> ORCH
    OVL --> ORCH
    ORCH --> TOOLS
    ORCH --> MEM
    ORCH --> SCHED
    SCHED -- "bind / unbind / kill" --> SVC
    ORCH -- "generate (token stream)" --> SVC
    SVC --> LLM
    MEM --> DB
    TOOLS --> DB
    TOOLS --> AND["Android intents / CameraManager<br/>(allowlisted)"]
```

## 3. Module layout proposal (MVP subset of PRD §11.4)

Start with the smallest set that still enforces the boundaries. Add the other §11.4 modules only when a feature in the MVP scope needs them.

```
:app                     Application, DI wiring, manifest, process declarations
:core:common             Result types, dispatchers, Logger interface
:core:designsystem       Material 3 theme, components, motion tokens
:core:database           Room, DAOs, migrations, encryption factory
:domain:model            Pure Kotlin entities
:domain:usecase          SendMessage, ExecuteTool, Remember, ApplyRetention, ...
:ai:inference-api        InferenceEngine interface + AIDL contract
:ai:inference-<runtime>  Implementation chosen by S2 [OPEN: litert | llamacpp]
:ai:scheduler            Model Scheduler / resource governor
:companion:overlay       WindowManager overlay host, OverlayLifecycleOwner
:companion:avatar        Renderer + animation state machine [OPEN: sprite | Rive]
:feature:chat
:feature:memory
:feature:privacy
:feature:settings
:tools:api               ToolSpec schema, router, permission engine (unit-tested first)
:tools:android           Intent-based tool implementations
:testing                 FakeInferenceEngine and other fakes
```

Deferred from §11.4 until a gate or scope decision needs them: `:ai:stt-*` and `:ai:tts-*` (S3), `:ai:speaker`, `:ai:embedding` (see memory.md open question), `:companion:behavior` beyond the MVP behaviors, `:feature:onboarding` (not designed yet), `:benchmark`.

**Layering rules** (§12.1, §12.3):
- Composables observe `StateFlow<UiState>`.
- Use cases have no Android dependencies.
- Repositories never expose Room entities outside the data layer.

## 4. Data flow — chat turn

```mermaid
sequenceDiagram
    participant U as User
    participant C as Overlay card / Chat UI
    participant O as Orchestrator
    participant M as Memory Manager
    participant PB as Prompt Builder
    participant S as Model Scheduler
    participant I as :inference (LLM)
    participant DB as Room

    U->>C: types message
    C->>O: UiEvent.Send(text)
    O->>O: state Idle → Thinking (avatar: think)
    O->>DB: persist user message
    O->>M: retrieve(text)
    M->>DB: pinned + FTS top-K
    M-->>PB: memory block (token-budgeted)
    O->>PB: build(persona, tool schemas, memory, recent turns)
    O->>S: ensure LLM resident
    S->>I: bind / load (if not resident)
    O->>I: generate(request)
    loop token stream
        I-->>O: Token
        O-->>C: partial text (≤ 30 UI updates/s)
    end
    alt user taps Stop
        C->>O: Cancel → I.cancel(requestId)
    end
    I-->>O: Done
    O->>DB: persist assistant message
    O->>O: state → Responding → Idle
    O-)M: (async, deferred) summarize / extract
    S-)I: idle timeout → unload or kill process
```

## 5. Data flow — tool call (PRD §13.5, §16.2)

1. **Shortcut path:** a deterministic NLU matcher ("set a timer for 5 minutes", "flashlight on") bypasses the LLM. S4 target: under 300 ms.
2. **LLM path:** the model emits a structured `<tool_call>`, using constrained decoding if the runtime supports it, otherwise a two-pass fallback (§13.5).
3. The Tool Router validates the call against a JSON Schema. If it's invalid, the model gets one repair prompt; if that fails, the user gets a clarifying question.
4. The Permission Engine checks the risk class (Safe, Sensitive, Critical) and any untrusted-content taint:
   - Safe and untainted → execute.
   - Sensitive or tainted → confirmation card.
5. The executor builds the intent itself from validated parameters. The LLM never supplies raw URIs, actions, or components (§16.6).
6. The audit record is appended to tool history. The result goes back to the model as data.

## 6. Interaction state machine (PRD §12.2.1)

Idle → Thinking → (AwaitingConfirmation | ExecutingTool | Responding | Speaking) → Idle. Cancel or panic-stop from any active state.
- Every transition emits an `InteractionEvent` consumed by the UI, the avatar, and logging, so the character always reflects real system state.
- Listening, Transcribing, and Speaking states exist only if S3 passes.

## 7. Model Scheduler (PRD §13.6)

- **Request flow:** a stage request (LLM; later STT/TTS) checks whether the model is resident. If not, it checks whether the model fits the RAM budget, then loads it (mmap). If it doesn't fit, the scheduler evicts by LRU weighted by reload cost.
- **Full reclaim:** if eviction needs a full memory reclaim, the scheduler restarts the `:inference` process.
- **Idle unload:** T1 (4 GB) unloads the LLM after 20 s idle (§12.10) [VERIFY on the A23].
- **Sequential loading on T1:** only one large model is resident at a time.
- **Memory pressure:** `onTrimMemory` triggers immediate eviction. `ApplicationExitInfo` from the previous run tunes context length and quantization downward after repeated low-memory kills (§12.9).
- **Sizing rule:** weights + KV cache + overhead ≤ 55% of available RAM (§13.2) [VERIFY].

## 8. Storage

- **Database:** Room, one encrypted database `bibi.db`, SQLCipher-backed factory. The key is wrapped by an Android Keystore key (§18.1).
- **Search:** FTS5 for memory and message search.
- **Conventions:** every user-content table carries `profileId` (single profile in MVP), plus `deletedAt` for soft delete and a purge job with `secure_delete` (§18.1).
- **Migrations:** schemas exported to VCS; each migration has a `MigrationTestHelper` test; destructive fallback disabled.
- **Preferences:** DataStore (§11.5).
- **Model packs:** stored in app-private storage and verified against the SHA-256 manifest before load.

## 9. Open choices

| Choice | Options (PRD) | Decided by |
|---|---|---|
| LLM runtime [OPEN] | LiteRT-LM (primary in §11.5) or llama.cpp via JNI | Spike S2 results on the A23 |
| Candidate models [OPEN] | Gemma 3 1B-class and Qwen 0.5B-class, int4 | Spike S2; license review (0.9) |
| Avatar tech [OPEN] | Sprite sheet via Compose Canvas or Rive (OD-03) | Spike S1 frame and battery cost |
| DI framework [OPEN] | Hilt or Koin (§11.5) | P1 setup |
| Embedding model for semantic memory [OPEN] | §14.3 flat vector index | RAM budget after S2 |
