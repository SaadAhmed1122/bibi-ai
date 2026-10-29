# Spike S3 — Bibi voice benchmark app (TTS + STT)

A small standalone Android app that measures on-device text-to-speech (S3a, issue [#6](https://github.com/SaadAhmed1122/bibi-ai/issues/6)) and speech-to-text (S3b, issue [#7](https://github.com/SaadAhmed1122/bibi-ai/issues/7)) speed and memory, so the PRD 0.7 voice gates can be decided with numbers. The full spec, verdict rules, model list and protocol are in [SPEC.md](SPEC.md).

**Throwaway code.** It uses plain Android views and one library, sherpa-onnx 2.0.1 (SPEC.md §3). P1 rewrites everything.

## 1. Get the sherpa-onnx library (once)
sherpa-onnx isn't on Google Maven or Maven Central, so the build uses a local AAR file.

1. Download **`sherpa-onnx-2.0.1.aar`** from the release page https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1.
   - Mirror used by sherpa's CI: https://huggingface.co/csukuangfj2/sherpa-onnx-libs/tree/main/android/aar [VERIFY the file is there].
   - Size not checked [VERIFY].
2. Put it at `spikes/s3-voice/app/libs/sherpa-onnx-2.0.1.aar`. The file name must match exactly.
3. The file is git-ignored; don't commit it. Without it the build stops with a "Missing …aar" message.

## 2. Build and install
**Needs:** Android Studio (any 2026 stable release), Android SDK Platform 36, and USB debugging on the phone.

1. In Android Studio: **File → Open** → select `spikes/s3-voice` (this folder, not the repo root).
2. Let Gradle sync. It downloads Gradle 9.1.0 and AGP 9.0.1, the same as S1.
3. Connect the phone, pick it, and press **Run**. From a terminal in this folder: `./gradlew :app:installDebug`.
4. Open the app once. That creates its models folder (step 3).

**What was verified before this reached you:**
- The Kotlin type-checks against the Android 16 framework classes.
- The sherpa adapter type-checks against sherpa-onnx's v2.0.1 Kotlin API sources.
- The summary and benchmark logic pass 33 JVM tests with fake engines.

It has **not** been built with AGP, linked with the real AAR, or run on a phone. If the build or the first run fails, paste the error into the issue.

## 3. Model files
Download the archives listed in [SPEC.md §5](SPEC.md#5-model-files-verify-names-and-sizes-on-download) from https://github.com/k2-fsa/sherpa-onnx/releases, using the `tts-models` and `asr-models` tags. Unpack them on your computer (e.g. `tar xf kokoro-en-v0_19.tar.bz2`), then:

1. Download `lexicon-en-us.txt` (`tts-models` tag) and copy it into **both** `kokoro-en-v0_19/` and `vits-piper-en_US-amy-low/`.
2. Optional, to save space and push time: delete the `espeak-ng-data/` and `test_wavs/` folders.
3. Push each folder to the phone with adb. The folder names must stay as unpacked:
   ```bash
   M=/sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s3/files/models
   adb shell mkdir -p $M
   adb push kokoro-en-v0_19 $M/
   adb push kokoro-int8-multi-lang-v1_1 $M/
   adb push vits-piper-en_US-amy-low $M/
   adb push sherpa-onnx-streaming-zipformer-en-20M-2023-02-17 $M/
   adb push sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8 $M/
   ```
   Android Studio's **Device Explorer** can upload the same folders instead.
4. Tap **Refresh results**. The status block shows `ready` or `missing N file(s)` per model, and a run button names the missing files.

The documented sizes alone add up to about 1.0 GB (Kokoro v0.19 336 MB, Zipformer 43 MB, Parakeet 631 MB). Kokoro int8, Piper and the lexicon come on top; their sizes aren't documented [VERIFY]. The models stay outside the APK and are removed when you uninstall the app.

## 4. Using the app
- **Settings:** CPU threads (default 2), rounds (default 3; each round covers all sentences or clips, after one warmup), TTS speaker ID (blank = model default), and *Play each sentence after measuring it* (off for gate runs).
- **S3a:** one *Run TTS* button per voice. It synthesizes the three fixed sentences in `assets/tts_sentences.txt`.
- **S3b clips:**
  - *Record clip1/2/3*: tap to start, read the sentence shown, and tap again to stop (30 s max).
  - The first tap asks for the microphone. That is the only time the app asks; it never listens in the background.
  - *Add a WAV file…* imports a 16-bit PCM WAV of any sample rate. Mono is preferred; stereo is averaged.
- **S3b:** one *Run STT* button per recognizer. It transcribes every clip.
- **Results:**
  - The summary shows per model and thread count: median and p95 RTF, peak PSS and RSS, load time, errors, a verdict (PASS, BORDERLINE [VERIFY], INCOMPLETE, FAIL), and the PRD 0.7 recommendation per gate.
  - The share buttons send the newest run CSV, all run CSVs (each after a `# file:` line), the summary, or `exit_reasons.csv`.
- A run takes from under a minute to several minutes. Keep the app on screen; it keeps the screen on by itself.
- After each run the inference process exits on purpose. An `EXIT_SELF` line for `…:inference` in the exit reasons is normal.
- **Death:** if the inference process dies during a run (e.g. an OOM kill), the app says so and the model gets FAIL. Its exit reason (e.g. `LOW_MEMORY`) appears in the exit reasons list a few seconds later.

## Owner test steps
1. Download `sherpa-onnx-2.0.1.aar` into `spikes/s3-voice/app/libs/` (README §1). Build and install the app on the vivo Y17s and open it once (README §2).
2. Download and unpack the five model archives plus `lexicon-en-us.txt`, and push them with adb (README §3). Tap **Refresh results** and check all five models show `ready`.
3. Set up the phone: battery above 50 %, battery saver off, other apps closed, phone cool, app on screen. *Play* stays off for gate runs.
4. **S3a:** with threads `2`, tap *Run TTS: Kokoro en v0.19 (fp32)* and wait for "Finished". Wait about 2 min, set threads to `4`, and run it again.
5. Repeat step 4 for *Kokoro multi-lang v1.1 (int8)*, then for *Piper en_US amy low*.
6. Once per voice, with *Play* on, run with 2 threads and listen. Note any mispronounced or skipped words in issue #6.
7. **S3b:** in a quiet room, tap *Record clip1*, read the sentence shown at normal speed, and tap again. Do the same for `clip2` and `clip3`. Check the clip list shows three clips of about 2, 6 and 10 s.
8. With threads `2`, then `4` (about 2 min apart), run *Run STT: Streaming Zipformer en 20M*, then *Parakeet TDT 0.6B v2*. If Parakeet dies, that is a result, not a problem with the test.
9. If any model reads INCOMPLETE in the summary, repeat that run.
10. Tap *Share all runs (CSV)*, *Share summary* and *Share exit reasons*. Attach the TTS parts to #6 and the STT parts to #7, with the phone model and anything unusual (heat, charging, a call during the run).
11. If the Galaxy A23 is available, repeat steps 1–10 on it. The summary keeps the two phones apart.

## For developers (no Android SDK needed)
- `tools/summary-test/run.sh`: JVM tests of Summary, BenchRunner, RunLog, WavIo and Models, using fake engines.
- `ANDROID_ALL_JAR=… tools/typecheck.sh`: type-checks all Kotlin against Robolectric's android-all (API 36) with the sherpa adapter stubbed.
- Add `SHERPA_API_DIR=<folder with sherpa-onnx v2.0.1 kotlin-api/*.kt>` to type-check the real adapter too.
