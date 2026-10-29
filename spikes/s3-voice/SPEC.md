# Spike S3 — Voice (S3a TTS, S3b STT): spec, plan and test protocol

Issues: [#6](https://github.com/SaadAhmed1122/bibi-ai/issues/6) (S3a TTS), [#7](https://github.com/SaadAhmed1122/bibi-ai/issues/7) (S3b STT) · Milestone: P0 · PRD 0.5, 0.7 (S3a/S3b rows), 0.9 (microphone), §15 (voice pipeline; "Pip" = Bibi)

> Spike code is **throwaway**. It answers two questions with numbers and is not the app. Nothing here goes into P1 without a rewrite.

## 1. Questions
- **S3a:** can Kokoro speak faster than real time on our phones? If not, can a lighter Piper-style voice?
- **S3b:** can a small streaming recognizer transcribe at RTF ≤ 0.7 within 600 MB? And Parakeet, as the second option?

## 2. Pass / fail (PRD 0.7)
| Gate | Pass if | If it fails (PRD 0.7) |
|---|---|---|
| S3a TTS | Real-time factor ≤ 1.0 | Switch to the lighter voice on the low-end tier |
| S3b STT | Real-time factor ≤ 0.7 **and** peak memory ≤ 600 MB | Push-to-talk short clips; typed input fallback |

S3 can fail without stopping the project; it decides whether voice ships in P2 (PRD 0.7, 0.8).

**Definitions used by this spike:**
- **RTF** = compute time ÷ audio duration, per item. TTS: time of one `synthesize` call ÷ length of the audio it returns. STT: time to transcribe a clip ÷ clip length. Below 1.0 is faster than real time.
- **Model load time** is logged but isn't part of RTF.
- **Warmup:** the first item after loading is logged as `warmup` and never counted.
- **Peak memory** = the highest PSS of the `:inference` process during the run: model load, warmup and every item.
  - PSS comes from `Debug.getPss()`, sampled every 500 ms, plus one reading right after each item.
  - The kernel's peak RSS (VmHWM) is logged too. Sampling can miss a short spike, but VmHWM can't. It counts shared pages in full, so it reads higher than PSS.
- **Verdict per model and thread count**, over all items of all its runs on one device (`Summary.kt`):
  - **FAIL:** median RTF above the gate, or (STT) peak PSS above 600 MB, or the inference process died during a run (likely an OOM kill; the cause is checked in `exit_reasons.csv` [VERIFY per case]).
  - **BORDERLINE [VERIFY]:**
    - the median passes but p95 (nearest rank) doesn't, or
    - (STT) PSS is within 600 MB but peak RSS is above it.
    
    Repeat the run before deciding.
  - **INCOMPLETE:** a run without a clean stop, any error row, fewer than 6 measured items, or (STT) no memory reading.
  - **PASS:** none of the above.
- **Recommendation:** per family, the summary picks the best thread count and prints the PRD 0.7 outcome.
  - **TTS:** keep Kokoro, switch to Piper, or no spoken replies on this tier.
  - **STT:** the small streaming model as default, Parakeet as an optional upgrade or off, or push-to-talk short clips with a typed fallback.

## 3. Runtime choice: sherpa-onnx 2.0.1
**Chosen:** one runtime, [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (k2-fsa, on ONNX Runtime). It covers all four model types through one Kotlin API:

| Model type | sherpa-onnx class |
|---|---|
| Kokoro | `OfflineTts`, Kokoro config |
| Piper-style voice | `OfflineTts`, VITS config |
| Small streaming transducer | `OnlineRecognizer` |
| Parakeet TDT 0.6B | `OfflineRecognizer`, `nemo_transducer` |

PRD §15.3 already names "an ONNX Runtime Mobile or sherpa-onnx–style runtime". Version 2.0.1 is the latest release according to PyPI (`sherpa-onnx` 2.0.1) and the repo's `CMakeLists.txt` (`SHERPA_ONNX_VERSION "2.0.1"`).

**Licensing note (PRD §15.4):** sherpa-onnx 2.0.0 removed espeak-ng and piper-phonemize ("Remove espeak-ng and piper-phonemize from sherpa-onnx (#4055)", CHANGELOG.md at v2.0.1). English text for Kokoro and Piper now goes through a lexicon file (`lexicon-en-us.txt`), so the GPL espeak-ng question doesn't arise for this runtime.
- Cost: words missing from the lexicon may be dropped or mispronounced [VERIFY by listening with *Play* on].
- sherpa-onnx's own license is Apache-2.0 [VERIFY before any release].

**Packaging:** no Maven artifact. The release page ships `sherpa-onnx-2.0.1.aar`, which bundles the JNI libraries and is compiled from `sherpa-onnx/kotlin-api/*.kt` (the AAR project's sources are symlinks to that folder).
- The owner downloads it into `app/libs/` (README).
- The build fails with a clear message if it's missing.
- Only `SherpaEngines.kt` imports the library. The rest of the app uses the `TtsEngine` and `SttEngine` interfaces (`Engines.kt`).

**Not chosen for this spike:** separate runtimes per model (e.g. a Kokoro-specific ONNX port plus whisper.cpp). That would mean more native code and two memory profiles to compare, and PRD §15 doesn't require it.

## 4. What the app does
**Main screen** (plain Android views, no androidx):
- **Status:** device, whether the sherpa classes are present, the models folder, and ready/missing per model.
- **Settings:** CPU threads (default 2), rounds (default 3), TTS speaker ID override, and *Play each sentence after measuring it* (off by default).
- **S3a:** one *Run TTS* button per TTS model.
- **S3b clips:** *Record clip1/2/3* toggles with the sentence to read, and *Add a WAV file…* (system file picker).
- **S3b:** one *Run STT* button per STT model.
- **Results:** *Refresh*, *Share newest run (CSV)*, *Share all runs (CSV)*, *Share summary*, *Share exit reasons*, and the summary text.

**Process split:**
- `InferenceService` is a bound service with `android:process=":inference"` and Messenger IPC.
- The activity binds it for one run and keeps the screen on.
- After replying "done", the inference process exits, so every run starts in a fresh process. VmHWM then belongs to one model, and model A's memory can't leak into model B's numbers. Android records this exit as `EXIT_SELF` for `…:inference`, which is expected.
- If the binding drops before "done", the main process appends a `process_died` row to the run file and an `inference_died` row to `exit_reasons.csv`, then copies Android's `ApplicationExitInfo` (e.g. `LOW_MEMORY`, `CRASH_NATIVE`).

**TTS run:**
- Sentences come from `assets/tts_sentences.txt`: short (8 words), medium (20 words) and long (48 words, 4 sentences).
- Order: load → warmup on the shortest sentence → *rounds* × all sentences.
- Only `synthesize` is timed. Playback, when on, happens after timing.

**STT run:**
- Clips are every WAV in `files/clips/`: the three recordings plus any added files.
- Each clip is decoded into memory before loading the model. Then: load → warmup on the first clip → *rounds* × all clips.
- The streaming model gets each clip in 100 ms chunks, as fast as it can take them, plus 0.6 s of silence to flush (sherpa's Kotlin example). Parakeet gets the whole clip at once.
- The transcript and the reference text (the prompt the owner read) are stored per item. Accuracy is judged by hand, not by the app.

**Recording (PRD 0.9, §15.2):**
- `RECORD_AUDIO` is requested only on the first tap of *Record*. Recording happens only while the app is on screen; no service and no background listening.
- Audio: `AudioRecord`, `VOICE_RECOGNITION` source, 16 kHz mono 16-bit, saved as `files/clips/clipN.wav`, at most 30 s.
- Clips are recorded once and re-used for every model, so all models hear the same audio. The owner's own voice avoids licensing questions about bundled speech.

**Files (app private storage):**
- `files/runs/s3_<yyyyMMdd-HHmmss>_<device>_<tts|stt>_<model>_t<threads>.csv`: one per run.
  - Columns: `time, epoch_ms, run_id, kind, device, task, model, family, threads, item, round, chars, audio_s, compute_ms, rtf, pss_peak_kb, vmhwm_kb, thermal, battery_pct, charging, detail, reference`.
  - Kinds: `start`, `load` (`compute_ms` = load time), `warmup`, `item`, `error`, `stop`, `process_died`.
  - `detail` holds the transcript or the error message. Commas inside text become `;`.
- `files/exit_reasons.csv`: `time, epoch_ms, reason, detail, process`. Never cleared.

## 5. Model files [VERIFY names and sizes on download]
Each model goes in its own folder under the app's models folder: `/sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s3/files/models/<folder>/`.

| Model (button) | Download (sherpa-onnx GitHub releases) | Files the app uses | Size per docs |
|---|---|---|---|
| Kokoro en v0.19 (fp32) | `tts-models/kokoro-en-v0_19.tar.bz2`, **plus** `tts-models/lexicon-en-us.txt` into the same folder | `model.onnx`, `voices.bin`, `tokens.txt`, `lexicon-en-us.txt` | model 330 MB, voices 5.5 MB; lexicon size not documented [VERIFY] |
| Kokoro multi-lang v1.1 (int8) | `tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2` | `model.int8.onnx` (or `model.onnx`), `voices.bin`, `tokens.txt`, `lexicon-us-en.txt` | not listed in the docs [VERIFY]; names taken from the v1.0 listing [VERIFY] |
| Piper en_US amy low (fallback voice) | `tts-models/vits-piper-en_US-amy-low.tar.bz2`, **plus** `tts-models/lexicon-en-us.txt` into the same folder | `en_US-amy-low.onnx`, `tokens.txt`, `lexicon-en-us.txt` | not listed [VERIFY]; other Piper voices in the RTF table are 61–75 MB |
| Streaming Zipformer en 20M (small) | `asr-models/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17.tar.bz2` | `encoder-epoch-99-avg-1.int8.onnx`, `decoder-epoch-99-avg-1.onnx`, `joiner-epoch-99-avg-1.int8.onnx`, `tokens.txt` | encoder 41 MB, decoder 2.0 MB, joiner 253 KB |
| Parakeet TDT 0.6B v2 (int8) | `asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2` | `encoder.int8.onnx`, `decoder.int8.onnx`, `joiner.int8.onnx`, `tokens.txt` | encoder 622 MB, decoder 6.9 MB, joiner 1.7 MB |

Full URLs start with `https://github.com/k2-fsa/sherpa-onnx/releases/download/`. The app shows the exact missing file names if a folder is incomplete.

**Not needed:**
- the `espeak-ng-data/` folders inside the TTS archives (sherpa-onnx 2.x ignores them; the v2.0.1 Android TTS example says to delete them)
- `test_wavs/` folders
- the fp32 variants of the Zipformer files

**Expectations from the docs, not measurements:**
- The docs' Raspberry Pi 4 table gives Kokoro en v0.19 RTF 2.77 with 4 threads, and Piper-style voices 0.35–0.49. The Helio G85 and the Pi 4 are different CPUs, so this only says Kokoro may fail S3a [VERIFY on device].
- Parakeet's encoder file alone is 622 MB, so Parakeet may fail the 600 MB memory gate on file size alone [VERIFY on device].

**Licenses: not checked for this spike.** The upstream license pages weren't read (Hugging Face was blocked in the authoring environment). Check each one before any release; the spike itself doesn't ship models.
- Kokoro-82M (hexgrad): its package includes a `LICENSE` file (kokoro.rst listing).
- Parakeet TDT 0.6B v2 (NVIDIA): see the `nvidia/parakeet-tdt-0.6b-v2` model card.
- Piper voices: licensed per voice.
- Zipformer 20M: icefall model trained on LibriSpeech.

## 6. Plan (one commit per task)
1. Gradle skeleton: AGP 9.0.1 with built-in Kotlin, S1's Gradle 9.1.0 wrapper, compileSdk/targetSdk 36, minSdk 34, arm64-v8a, local AAR with a missing-file check.
2. Pure-Kotlin core and JVM tests: model catalog, WAV I/O, run log, engine interfaces, BenchRunner, Summary; `tools/summary-test/run.sh`.
3. sherpa-onnx adapter (`SherpaEngines.kt`).
4. `:inference` bound service and memory probe.
5. Control panel, recorder, exit log, assets, `tools/typecheck.sh`.
6. SPEC.md and README.md.

## 7. Test protocol (owner runs on the device)
Use the **vivo Y17s**, and the Galaxy A23 if available. Each phone keeps its own results; the summary groups by device.

**Conditions (the same for every run; write them down once):**
- [ ] Battery above 50 %. Note whether it's charging; charging warms the phone, and the log records `charging`.
- [ ] Battery saver off. Other apps closed.
- [ ] Phone cool: wait about 2 min between runs. The log records `thermal` (0 = none); a run with thermal ≥ 2 should be repeated after cooling [VERIFY threshold].
- [ ] Screen on, app in front. The app keeps the screen on during a run. Leaving the app during a run can let Android kill the inference process, which the summary counts as a death.
- [ ] *Play* off for gate runs. Turn it on once per TTS model in a separate run to hear the quality; that run's RTF is still valid, because playback isn't timed.

**Order:**
1. **S3a, Kokoro first:** *Run TTS: Kokoro en v0.19* with 2 threads, then again with 4 threads.
2. Repeat step 1 for *Kokoro multi-lang v1.1 (int8)*.
3. Repeat step 1 for *Piper en_US amy low*.
4. **S3b clips:** record `clip1`–`clip3` once, in a quiet room, at normal speaking speed (about 2, 6 and 10 s).
5. **S3b:** *Run STT: Streaming Zipformer* with 2 and with 4 threads, then *Parakeet* with 2 and with 4 threads. Parakeet may die from an OOM kill; the app records it.
6. After each run, check that the summary line for that model isn't INCOMPLETE; repeat a run if it is. At the end, tap *Share all runs (CSV)*, *Share summary* and *Share exit reasons*, and attach them to #6 (TTS) and #7 (STT).

## 8. Sources relied on
**sherpa-onnx** (read on raw.githubusercontent.com; the k2-fsa.github.io docs site and the GitHub API were blocked in the authoring environment):
- `k2-fsa/sherpa-onnx` at tag `v2.0.1`:
  - Kotlin API: `sherpa-onnx/kotlin-api/` — `Tts.kt`, `OnlineRecognizer.kt`, `OfflineRecognizer.kt`, `OnlineStream.kt`, `OfflineStream.kt`, `FeatureConfig.kt`
  - `kotlin-api-examples/test_online_asr.kt` (tail padding) and `test_offline_asr.kt`
  - `android/SherpaOnnxTts/.../MainActivity.kt` (lexicon frontend and model examples)
  - `android/SherpaOnnxAar/README.md` and `.github/workflows/android.yaml` (AAR name and release)
  - `CHANGELOG.md`
  - `sherpa-onnx/csrc/offline-recognizer-transducer-nemo-impl.h` (Parakeet feature size is read from the model)
- `k2-fsa/sherpa` docs sources under `docs/source/onnx/`: `tts/pretrained_models/kokoro.rst`, `vits.rst` and `rtf.rst`; `pretrained_models/online-transducer/zipformer-transducer-models.rst`; `pretrained_models/offline-transducer/nemo/parakeet-tdt-0.6b-v2.rst`.

**developer.android.com:**
- reference pages: `AudioRecord`, `AudioTrack`, `Debug` (`getPss`), `ApplicationExitInfo`
- guide "Bound services" (Messenger)
- "Create an Android library" (local AAR dependency)
- AGP 9.0 DSL `Ndk.abiFilters`

Context7 was unreachable.

## 9. Known limits of this spike
- **Not built or run.**
  - The authoring environment can't reach Google's Maven repository or download the AAR or models.
  - The code is type-checked against Android 16 framework classes (Robolectric android-all). The adapter was checked against the sherpa-onnx v2.0.1 Kotlin API sources, not the AAR binary.
  - Summary and BenchRunner are tested on a JVM with fake engines.
  - The owner's Android Studio build is the first real build.
- **Speed is measured, not quality.** RTF says nothing about naturalness or word error rate; listen with *Play* and read the transcripts.
- **No end-to-end latency.** Time to first audio and streaming partial latency aren't measured. Kokoro's RTF on the short sentence is a proxy for time to first audio.
- **CPU only.** No NNAPI or GPU (PRD 0.5: don't rely on them).
- **Measurement overhead.** The PSS sampler adds some CPU load (smaps reads every 500 ms) to every run equally.
