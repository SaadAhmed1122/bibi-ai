# Spec: voice (P2)

Shared rules: [SPEC-P2.md](SPEC-P2.md). Owner, 2026-10-31: voice in the in-app chat and the floating card. S3: Piper en_US amy low (RTF 0.248) and Parakeet TDT 0.6B v2 int8 (RTF 0.324, PSS ≈ 947 MB, memory limit overridden by the owner).

## Objective
Hold to talk, let go, and Bibi hears you; Bibi's answer can be read aloud. Typed chat always keeps working.

## Scope
- **Memory check first (plan Task 1):** Gemma generating + router + Parakeet + Piper on the vivo. If all can't stay loaded, voice models load on first use and unload after 2 minutes idle [VERIFY].
- **Runtime:** sherpa-onnx 2.0.1 AAR (the S3 build; not on Maven, downloaded by the owner into `libs/`, git-ignored), engines in `:inference`. New Messenger messages: `STT_CHUNK` (16 kHz PCM, 0.5 s chunks), `STT_END` → transcript, `TTS(text)` → audio played in `:inference`, `TTS_STOP`.
- **Voice pack import:** a folder picker (`OpenDocumentTree`) copies the Parakeet and Piper files into app storage with size and SHA-256 checks (values from S3). Without the pack, the mic button is hidden.
- **Push-to-talk:** a mic button next to Send; hold to record, release to transcribe and send. `RECORD_AUDIO` is asked the first time with a one-line reason; denial keeps typed chat. No background listening.
- **Spoken replies:** a speaker toggle (off by default). When on, each finished sentence is spoken while the rest streams; Stop stops both.
- **Floating card:** the same buttons. While recording from the card, `OverlayService` runs with the `microphone` foreground-service type next to `specialUse` (a new Play declaration). Whether Android 14/15 lets the overlay start a microphone service while another app is in front is checked first [VERIFY]; if not, the card's mic opens the app's chat screen instead.

## Acceptance criteria
1. Protocol tests for the new messages; recorder chunking test; sentence splitter tests.
2. On the vivo, in the app and the card, portrait and landscape: 10 spoken questions, transcript shown before sending; reply read aloud; Stop silences it. Peak PSS of `:inference` and kills recorded in the PR.
3. Merged manifest: `RECORD_AUDIO` and `FOREGROUND_SERVICE_MICROPHONE` only; still no `INTERNET`.

## Out of scope
Wake word, streaming recognition while speaking, voice choice, other languages, Kokoro, speaker verification.
