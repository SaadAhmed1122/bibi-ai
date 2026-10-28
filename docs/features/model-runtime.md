# Feature: Model runtime (local LLM)

## Purpose
Run a small language model fully on device, in the `:inference` process, with streamed output. Spike S2 decides whether the Galaxy A23 can support typed chat at all, and which runtime and model ship.

## Requirements
- **PRD 0.7 S2 gate:**
  - Test a Gemma 3 1B-class and a 0.5B Qwen-class model, int4, locally.
  - Pass: **≥ 5 tokens/s**, **first token ≤ 3 s**, and **zero out-of-memory kills in a 50-prompt soak**.
  - If it fails: a smaller model, shorter answers, typed chat only, and apply the 0.5 decision rule.
- **PRD 0.5 decision rule (binding).** If S2 fails on the A23 but would pass on better hardware:
  - (a) The recommended spec becomes ≥ 6 GB RAM and a recent chip.
  - (b) The A23 stays the minimum for typed chat with the smallest model only, and voice is marked "not supported on this device".
  - Quality targets are never lowered silently.
- **PRD 0.10 overrides:**
  - ≥ 5 tokens/s is the floor. 12+ and 20+ tokens/s are aspirations for 6 GB+ and 8 GB+ devices, to be measured, not promised.
  - Tier assignment uses an on-device benchmark, not RAM alone.
  - MVP ships only the Core Pack (≤ 2 GB).
  - One Gemma-class and one Qwen-class model are evaluated, and the winner ships.
- **PRD 0.8:**
  - Streaming chat with a stop-generation control and short answers by default.
  - The Core Pack is downloaded at first run with resume, checksum verification, and delete.
- **PRD §13.1:** the `InferenceEngine` interface — `load`, `generate` returning `Flow<GenerationEvent>`, `countTokens`, `unload`, `cancel`.
- **PRD §13.10, 0.9:** record each model's license. Review the Gemma terms and the Qwen license for the exact checkpoints before any release.

## Design notes
- **Runtime: [OPEN].** LiteRT-LM (primary in §11.5) or llama.cpp via JNI [VERIFY runtime APIs]. S2 should benchmark both if time allows. Choose on measured tokens/s, first-token latency, peak memory, and OOM behavior, not on preference.
- **Process isolation** (§11.3): the engine lives in `:inference` behind an AIDL bound service. The Model Scheduler can kill that process to fully return native memory.
- **Threading** (§12.7): a single-thread dispatcher per engine; every call is cancellable. Token streams to the UI are conflated to ≤ 30 updates/s.
- **Memory** (§13.2–13.3):
  - Weights + KV cache + overhead ≤ 55% of available RAM [VERIFY].
  - Context capped per tier; T1 is 2k–4k tokens.
  - Benchmark mmap against fully loaded weights.
  - Cache the KV state of the static persona and tool prefix to cut first-token time.
- **Idle unload** (§12.10): 20 s on T1 [VERIFY].
- **Acceleration:** CPU only on the A23 (PRD 0.5 says not to rely on the GPU or NPU). Probe for delegates behind a capability check.
- **Download:** resumable, with SHA-256 verification against a manifest (`MODEL_PACK_SHA256_MANIFEST_URL`). Host decided in P1 (`MODEL_PACK_BASE_URL`).
- **Tool calls** (§13.5): constrained decoding if the runtime supports it, otherwise a two-pass fallback. Support for grammar-constrained decoding is an S2 comparison criterion.

## Risks
- **The LLM is too slow on the A23** (PRD 0.13, high/high). The planning estimate of 3–8 tokens/s [VERIFY] straddles the 5 tokens/s gate.
- **OOM kills** on 4 GB while the overlay and the user's other apps compete for RAM.
- **Thermal throttling** during the 50-prompt soak lowers sustained speed.
- **Int4 quantization** may break tool-call formatting (§13.3).
- **License terms** for specific checkpoints may restrict redistribution [VERIFY].

## Open questions
- LiteRT-LM or llama.cpp? [OPEN]
- Exact checkpoints and their file sizes [VERIFY].
- Does the chosen runtime support grammar-constrained decoding on Android? [VERIFY]
- Pack hosting: an owner-controlled host or Play Asset Delivery? [VERIFY limits]

## Test plan
- **S2 benchmark (A23, airplane mode, screen on, record battery and temperature):**
  1. Cold load time and peak RSS for each model and runtime pair.
  2. First-token latency, measured on a fixed prompt set (≤ 200-token prompts).
  3. Decode speed (tokens/s): p50 and p95 over the run.
  4. 50-prompt soak with the overlay running; count low-memory kills via `ApplicationExitInfo`.
- **Unit tests:** `FakeInferenceEngine`, cancellation, stream conflation, and the token budgeter.
- **Integration test:** kill `:inference` during generation. The UI must recover, the overlay must stay alive, and an error state must show.
- Record every number in the spike tracker. Log the gate result and the runtime choice as a dated decision.
