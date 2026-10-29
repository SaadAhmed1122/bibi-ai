# Spike S2 — Bibi local LLM benchmark app

A small standalone Android app that runs a local LLM with LiteRT-LM in a separate `:inference` process. It measures first-token time, decode speed, memory and OOM kills over a fixed 50-prompt set, so gate S2 can be decided with numbers. Issue [#5](https://github.com/SaadAhmed1122/bibi-ai/issues/5). The full spec, model list and protocol are in [SPEC.md](SPEC.md).

**Throwaway code.** It uses plain Android views and one library (LiteRT-LM). P1 rewrites everything.

## Build and install (on your computer)
**Needs:** Android Studio (any 2026 stable release), Android SDK Platform 36 (Studio offers to install it), and USB debugging on the phone.

1. In Android Studio, choose **File → Open** and select `spikes/s2-llm` (this folder, not the repo root).
2. Let Gradle sync. It downloads Gradle 9.1.0, Android Gradle Plugin 9.0.1 and `litertlm-android` 0.16.1 from Google Maven.
   - Don't let Studio bump `litertlm-android` past 0.16.1. Versions 0.17+ need Kotlin 2.3 or newer, which AGP 9.0's built-in Kotlin (2.2.10) doesn't provide (see the comment in `app/build.gradle.kts`).
3. Connect the phone, pick it in the device menu, and press **Run**.

Or, from a terminal in this folder:
```bash
./gradlew :app:installDebug
```

**What was verified before this reached you:**
- The app code type-checks against the Android 16 framework classes.
- The summary maths was tested on synthetic logs.
- **Not verified:**
  - The LiteRT-LM adapter (`LiteRtLmEngine.kt`) has never been compiled. The library couldn't be downloaded where this was written.
  - Nothing has been built with Gradle or run on a phone.

If the build fails in `LiteRtLmEngine.kt`, paste the error into issue #5. That file is the only one that touches the library.

## Get the model files
Details and sources are in [SPEC.md §4](SPEC.md#4-model-files-the-owner-downloads-them-gemma-terms-must-be-accepted-by-the-owner).
- **Gemma:** on Hugging Face, open `litert-community/Gemma3-1B-IT`, log in, and accept the Gemma terms. Then download `gemma3-1b-it-int4.litertlm` (584,417,280 bytes).
- **Qwen 0.5B-class:** look in `litert-community/Qwen2.5-0.5B-Instruct` or `litert-community/Qwen3-0.6B` for a `.litertlm` file, int4/q4 if there is one. This file name is not confirmed yet [VERIFY]; write down which one you used.

## Put them on the phone
1. Install the app and open it once. This creates the model folder; its path is shown on screen.
2. From the folder where you downloaded the files:
   ```bash
   adb push gemma3-1b-it-int4.litertlm /sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s2/files/models/
   adb push <qwen-file>.litertlm      /sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s2/files/models/
   ```
3. In the app, tap **Rescan model files**. Each file gets a **Soak 50** and a **Smoke 3** button.

**If `adb push` is refused** for that folder [VERIFY on vivo], use the app's private folder instead (debug builds only):
```bash
adb push gemma3-1b-it-int4.litertlm /data/local/tmp/
adb shell run-as io.github.saadahmed1122.bibi.spike.s2 sh -c 'mkdir -p files/models && cp /data/local/tmp/gemma3-1b-it-int4.litertlm files/models/'
adb shell rm /data/local/tmp/gemma3-1b-it-int4.litertlm
```

## Using the app
- **Smoke 3: <file>:** loads the model and runs 3 prompts. Use it first, to check that the model works. It never counts for the gate.
- **Soak 50: <file>:** the gate run. Loads the model, then runs all 50 prompts, each as a fresh chat, with up to 128 new tokens.
- **Stop run:** ends the run early. The run is then marked invalid.
- The progress line shows the current prompt and the last prompt's first-token time and speed.
- The results panel shows the newest run's summary:
  - `VALID for gate: yes/no` (with reasons)
  - `GATE S2 (<model>): PASS / FAIL / no verdict`
  - on a fail, which PRD 0.5 / 0.7 outcome applies.
- **Share newest run (CSV)**, **Share exit reasons** and **Share summary** send the text with any app (mail, Drive, a chat with yourself).

**Files (in the app's private storage):**
- `files/runs/s2_<yyyyMMdd-HHmm>_<device>_<model>_<mode>.csv`: one file per run. The columns are listed in [SPEC.md §3](SPEC.md#3-what-the-spike-app-does).
- `files/exit_reasons.csv`: Android's record of process deaths, including OOM kills of `:inference`. It is kept across runs.

## Owner test steps
1. Build and install the app from Android Studio (`spikes/s2-llm`), then open it once.
2. Download `gemma3-1b-it-int4.litertlm` (accept the Gemma terms on Hugging Face first) and a 0.5B Qwen-class `.litertlm` file (int4 if offered). Note the exact file names.
3. `adb push` both files into the folder the app shows, then tap **Rescan model files**.
4. Tap **Smoke 3** for each model. Each should finish with 3 prompts. If one fails, share the run CSV and stop here.
5. Before each soak:
   - charge to at least 60 % and unplug
   - turn airplane mode on and battery saver off
   - set a fixed brightness with auto-brightness off
   - close other apps
   - on the vivo, note whether extended RAM is on.
6. Tap **Soak 50** for Gemma. Keep the app open with the screen on, and don't touch or swipe it away until it says *Done* (about 25 min at 5 tokens/s).
7. Check `VALID for gate: yes`, then tap **Share newest run (CSV)** and **Share summary**, and attach both to issue #5. If it isn't valid, repeat the run.
8. Let the phone cool for about 10 min (screen off, unplugged), then repeat steps 5–7 for the Qwen file.
9. Optional: repeat the Gemma soak with S1's *Bibi Calm 2 fps* overlay running, and label it "with overlay".
10. At the end, tap **Share exit reasons** and attach it to issue #5. Do this on each test phone (Galaxy A23 first, if available, then the vivo Y17s).
