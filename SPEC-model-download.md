# Spec: model-download (P3)

Shared rules: [SPEC-P2.md](SPEC-P2.md). Owner decision 2026-11-07 (issue #161): users download the chat model inside the app after installing it. Reverses the 2026-10-31 deferral; implements the PRD 0.8 "Model" row (download with resume, checksum, delete).

Extended 2026-11-07 (issue #165) to the phone-actions model and the voice pack: see "Packs" below.

## Objective
After install, one tap fetches `gemma3-1b-it-int4.litertlm` (584,417,280 bytes) from the owner's server, checks it, and makes it Ready, with no browser and no file picker. Choosing a file stays as the fallback.

## Design
- **Android DownloadManager** (`SystemModelDownloader`, app module). It resumes after network drops, keeps going when Bibi is closed or killed, and shows its own notification. No new dependency and no foreground-service type. The app needs `INTERNET` for it (DownloadManager reference: "the application must have the INTERNET permission").
- The file lands in the app's external files folder as `<name>.download`. It is read back through `DownloadManager.openDownloadedFile` (not by path), then goes through the existing `ImportModel`: size and SHA-256 must match `ModelFile`, and only then is it moved into `filesDir/models`. The download entry and its file are removed afterwards, pass or fail.
- `ModelDownloadViewModel` (feature/chat) polls the status every 500 ms while a screen shows it. The download id is kept in SharedPreferences, so a new launch picks up a running or finished download.
- **URL:** one server folder, Gradle property or environment variable `BIBI_MODEL_BASE_URL`, laid out as below. Without it the chat model comes from the gated Hugging Face file (answers 401 to anyone not logged in; testing only), the action model from its public Hugging Face file, and the voice pack has no download button. A token is never put in the app.

## Packs
| Pack | Files under `BIBI_MODEL_BASE_URL` | Without a base URL |
|---|---|---|
| Chat (needed) | `gemma3-1b-it-int4.litertlm` | `litert-community/Gemma3-1B-IT` (gated, 401) |
| Phone actions | `embeddinggemma-2-text-270m.litertlm` | `litert-community/embeddinggemma-2-text-270m-litert-lm` (public; size and SHA-256 match `RouterModelFile`, checked 2026-11-07) |
| Voice | `voice/parakeet-tdt-0.6b-v2-int8/{encoder,decoder,joiner}.int8.onnx, tokens.txt`, `voice/piper-en_US-amy-low/{en_US-amy-low.onnx, tokens.txt, lexicon-en-us.txt}` | None: the Hugging Face Parakeet export (`csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8`) is a different build (encoder 652,282,309 bytes, other hashes) from the one S3 measured, which ships only in a `.tar.bz2` |

- Each pack is one `ModelDownloadViewModel` with its URLs and expected sizes (exact progress from the start), and one `SystemModelDownloader` with its own saved ids (`chat`, `router`, `voice`). A pack installs only when every file is done; one failed file stops the pack. The files go through the existing imports (`ModelRepository`, `VoicePackRepository`), so every file's size and SHA-256 is checked.
- Errors: 401/403 "needs a login", 404/410 "not found", 1006 "not enough space" (about 1.2 GB needed for a while: download plus copy), anything else "check the connection". Each has Try again, and "Choose model file" stays available.
- Debug builds allow plain HTTP to `127.0.0.1` only (`app/src/debug`), for testing from a laptop through `adb reverse`. Release builds are HTTPS only.
- The build checks the merged manifest against a permission allowlist (replaces the "no INTERNET" check), so a library still can't add a permission unnoticed.

## Privacy
- Chats, memory and voice never use the network. The download server sees the IP address and the file requested.
- MediaPipe (the optional action router) has a built-in Google usage logger (`play.googleapis.com/log`; found in `libmediapipe_tasks_decision_jni.so` and `libmediapipe_tasks_jni.so`, class `ClearcutLoggingClient`). With `INTERNET` it can now send. It runs only after the user adds the router model. The router card, the privacy policy and the Play Data safety form say so.

## Acceptance criteria
1. JVM tests (`ModelDownloadViewModelTest`): progress, waiting, finished → Ready and removed, damaged → Invalid("checksum") and removed, 401 → Failed with NeedsLogin, picks up an earlier download, cancel, no second start, unknown id → Idle.
2. Packs (#165): progress adds up the files; install gets all files in URL order once all are done; one failure stops the pack.
3. On the vivo, portrait and landscape: the 401 message with the default URL; a full download from a test server reaches Ready; killing the app mid-download and reopening it ends Ready.

## Out of scope
Several mirrors, model updates, Wi-Fi-only setting, unpacking archives.
