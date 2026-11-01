# Spec: voice (P2)

Shared rules: [SPEC-P2.md](SPEC-P2.md). Owner, 2026-10-31: voice in the in-app chat and the floating card. S3: Piper en_US amy low (RTF 0.248) and Parakeet TDT 0.6B v2 int8 (RTF 0.324, PSS ≈ 947 MB, memory limit overridden by the owner).

## Objective
Hold to talk, let go, and Bibi hears you; Bibi's answer can be read aloud. Typed chat always keeps working.

## Scope
- **Memory check done (Task 1, [results](docs/measurements/2026-10-31-p2-memory-check/RESULTS.md)):** any two models fit, but all four would leave only ~0.6 GB free, and running them together slows everything (Parakeet 0.32 → 0.74). So **voice loads on demand:** Parakeet when the mic button is pressed (the recording overlaps its 10–11 s load), unloaded after 2 minutes idle; Piper while the speaker toggle is on. Transcription never runs while Gemma is writing; Piper may speak while the answer streams (0.45 measured).
- **Runtime:** sherpa-onnx 2.0.1 AAR (the S3 build; not on Maven, downloaded by the owner into `libs/`, git-ignored), engines in `:inference`. New Messenger messages: `STT_CHUNK` (16 kHz PCM, 0.5 s chunks), `STT_END` → transcript, `TTS(text)` → audio played in `:inference`, `TTS_STOP`.
- **Voice pack import:** a folder picker (`OpenDocumentTree`) copies the Parakeet and Piper files into app storage with size and SHA-256 checks (values from S3). Without the pack, the mic button is hidden.
- **Push-to-talk:** a mic button next to Send; hold to record, release to transcribe and send. `RECORD_AUDIO` is asked the first time with a one-line reason; denial keeps typed chat. No background listening.
- **Spoken replies:** a speaker toggle (off by default). When on, each finished sentence is spoken while the rest streams; Stop stops both.
- **Floating card:** the same buttons. While recording from the card, `OverlayService` runs with the `microphone` foreground-service type next to `specialUse` (a new Play declaration). Checked (Task 15): with only the specialUse service the card's recording was silent; the service now adds the microphone type when it is (re)started from the app while it is in front, and the card hears normally. Without the mic permission the card's mic opens the app's chat, where it is asked.

## Acceptance criteria
1. Protocol tests for the new messages; recorder chunking test; sentence splitter tests.
2. On the vivo, in the app and the card, portrait and landscape: 10 spoken questions, transcript shown before sending; reply read aloud; Stop silences it. Peak PSS of `:inference` and kills recorded in the PR.
3. Merged manifest: `RECORD_AUDIO` and `FOREGROUND_SERVICE_MICROPHONE` only; still no `INTERNET`.

## Out of scope
Wake word, streaming recognition while speaking, voice choice, other languages, Kokoro, speaker verification.
