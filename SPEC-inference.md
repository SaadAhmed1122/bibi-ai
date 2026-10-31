# Spec: inference (P1)

Shared rules: [SPEC.md](SPEC.md). Based on spike S2 (`spikes/s2-llm/`, RESULTS.md §1b) and the Decision that chat mode with prefix reuse is required in P1 (docs/features/model-runtime.md).

## Objective
Run Gemma 3 1B int4 in the separate `:inference` process and give the UI process a small streaming chat API. Success: on the vivo, a typed message gets its first token in ≤ 3 s at p95 (S2 chat-mode result: 2,409 ms), and the answer streams, can be stopped, and a crash or kill of `:inference` never takes down the UI.

## Scope
- **`ai:inference-api`** (pure Kotlin plus Android `Message` codecs):
  ```kotlin
  interface InferenceClient {
      val state: StateFlow<EngineState>          // Unbound, Loading, Ready, Generating, Failed(reason)
      fun send(text: String): Flow<ReplyEvent>   // Token(text) … then Done | Stopped | Error(reason)
      fun stop()
      fun resetConversation()
  }
  ```
  It also holds `FakeInferenceClient` for tests, and the Messenger protocol (message codes and bundle keys) in one file with an encode/decode test.
- **`ai:inference-litert`**:
  - `InferenceService` runs in `android:process=":inference"` and is bound with a `Messenger`, as in the S2 spike.
  - `LiteRtLmEngine` is the only file that imports `com.google.ai.edge.litertlm`, ported from `spikes/s2-llm`: CPU backend, `EngineConfig.maxNumTokens = 4096`, one dispatcher on a single background thread.
- **One conversation, prefix reused:**
  - On load, the service opens a conversation with the Bibi system prompt (`assets/system_bibi.txt`, from S2) and the scripted primer exchange. Gemma 3 has no system turn, so the primer is needed for the prefill (S2 finding).
  - Each `send` prefills only the new user text.
  - Before a turn could overflow the context (the `needsContextReset` rule from S2), the service reopens the conversation with the system prompt plus the last N turns from history. N is 6 (tasks/plan.md). The same rebuild is used after Stop or a failed turn, because a cancelled turn leaves 0.16.1's conversation unusable.
- **Short answers by default:** a sentence in the system prompt plus a max-new-token cap of 256 [VERIFY that the cap is supported on 0.16.1; otherwise stop at the token count in the stream].
- **Stop:** calls `Conversation.cancelProcess()`. The partial text so far is kept and reported as `Stopped`.
- **Recovery:** if the binder dies (`DeathRecipient` / `onServiceDisconnected`), the client goes to `Failed`, rebinds once, and reloads. The UI shows "Bibi is restarting".
- **Process start:** the model loads when the chat is first opened, not at app start. Unloading when idle is P2 (scheduler).

## Acceptance criteria
1. Protocol encode/decode round-trip test for every message type.
2. `FakeInferenceClient` contract tests: token stream then `Done`; `stop()` mid-stream gives `Stopped` with the partial text; `Error` on failure.
3. Context-reset rule: a JVM test that the reset triggers before the budget is exceeded and rebuilds with the system prompt and the last N turns.
4. On the vivo with the real model: load, then 10 turns in one conversation. TTFT p95 ≤ 3,000 ms, turn 1 included (the prefix is prefilled). Measured with the S2-style timing log in debug builds.
5. On the vivo: `adb shell am kill` or `kill` of the `:inference` pid during an answer leaves the UI alive, shows "restarting", and the next message works.
6. The manifest declares the service in `:inference`. ApplicationExitInfo for `:inference` goes into the crash log (app-shell).

## Out of scope
STT, TTS, embedding, the model scheduler, GPU backend, tool calling.

## Open questions
- None. N = 6 turns, and fewer when long turns would not leave room for the next message (`ContextBudget`). The 256-token cap works on 0.16.1 through `ConversationConfig.maxOutputToken` (vivo, 2026-10-31).
