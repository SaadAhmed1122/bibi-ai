# TESTING.md — what to test on the phone

This is the one file to open when you have time to test Bibi AI on a phone. It lists every feasibility spike (P0), whether its test app is ready, the exact steps, and what to send back. Each spike's full spec stays on its own branch; this file only condenses the owner steps.

## Devices
- **vivo Y17s** (6 GB RAM, Helio G85, Android 15): the device every test runs on first. Ignore the "+6 GB extended RAM"; it is swap.
- **Samsung Galaxy A23** (floor device per PRD 0.4): run the same tests on it if it is available. Its RAM and Android version are unconfirmed [VERIFY] (open items 1, 2 and 10).

Details: [docs/project_status.md](docs/project_status.md) → Test devices.

## Status of all spikes

| Spike | Issue | Branch | App folder | Ready to test? | What to send back |
|---|---|---|---|---|---|
| S1 — Overlay | [#4](https://github.com/SaadAhmed1122/bibi-ai/issues/4) | `spike/S1-overlay` (PR [#10](https://github.com/SaadAhmed1122/bibi-ai/pull/10)) | `spikes/s1-overlay/` | **Partly done** — 2 runs recorded; the baseline run is still missing | One run CSV per run (four runs), the start % of each, exit reasons at the end, and the kill/restart check files. Post on #4. |
| S2 — Local LLM | [#5](https://github.com/SaadAhmed1122/bibi-ai/issues/5) | `spike/S2-llm` (PR [#12](https://github.com/SaadAhmed1122/bibi-ai/pull/12)) | `spikes/s2-llm/` | **In progress** — smoke runs done; Gemma 3 1B soak in progress | One run CSV per model (plus its summary) and `exit_reasons.csv`. Post on #5. |
| S3a + S3b — Voice | [#6](https://github.com/SaadAhmed1122/bibi-ai/issues/6), [#7](https://github.com/SaadAhmed1122/bibi-ai/issues/7) | `spike/S3-voice` (PR [#14](https://github.com/SaadAhmed1122/bibi-ai/pull/14)) | `spikes/s3-voice/` | **Done** — results in `spikes/s3-voice/results/RESULTS.md` on `main` (after PR #14) | The run CSVs for each model (*Share all runs (CSV)*), the summary, `exit_reasons.csv`, and the transcripts (in the STT CSVs' `detail` column). Post the TTS parts on #6 and the STT parts on #7. |
| S4 — Tools and permissions | [#8](https://github.com/SaadAhmed1122/bibi-ai/issues/8) | `spike/S4-tools` (PR [#13](https://github.com/SaadAhmed1122/bibi-ai/pull/13)) | `spikes/s4-tools/` | **Yes** — app written; not measured yet | The benchmark CSV and a note of what each action did on the phone (which clock app opened, whether the confirmation showed for dial). Post on #8. |

## Before any test
- [ ] **Android Studio** installed (any 2026 stable release). It offers to install the Android SDK platform a spike needs.
- [ ] **Get the spike code:** check out the spike's branch (for S1: `git checkout spike/S1-overlay`).
- [ ] **Open the spike folder, not the repo root:** File → Open → `spikes/<spike folder>` (for S1: `spikes/s1-overlay`). Let Gradle sync finish.
- [ ] **Connect the phone:** enable Developer options (Settings → About phone → tap the build number 7 times), turn on **USB debugging**, plug in by USB, accept the "Allow USB debugging" prompt, then pick the phone in Studio's device menu and press **Run**.
- [ ] **Common phone settings, the same for every run:**
  - [ ] Brightness fixed at one level, **auto-brightness off**.
  - [ ] **Battery saver off.**
  - [ ] Same **refresh-rate** setting every run (Settings → Display, e.g. 60 Hz or 90 Hz).
  - [ ] **Screen timeout at the maximum** the phone allows.
  - [ ] Same Wi-Fi state, no downloads or updates running.

If the build fails, paste the error into the spike's issue.

## S1 — Overlay (issue #4, branch `spike/S1-overlay`)
Question: can Bibi float over other apps for 1 hour, stably, at ≤ 3 % battery per hour? Full protocol: `spikes/s1-overlay/SPEC.md` §5 and `spikes/s1-overlay/README.md` on the branch.

**One-time setup in the app**
- [ ] Tap **1. Allow drawing over other apps** and enable it for "Bibi S1 spike".
- [ ] Tap **2. Allow notifications**.
- [ ] Optional: try Bibi in a separate short session (start a Bibi mode, drag, release, tap, then **Stop**). Never do this inside a measured run.
- [ ] Leave vivo's background power setting for the app at **default**, and note it. Keep it the same in every run.

**Four measured runs, in this order** (about 61 min each; spread over a day or two, they can't share one charge):
1. Baseline: **Start baseline (no Bibi)**
2. Bibi 12: **Start Bibi 12 fps**
3. Baseline again: **Start baseline (no Bibi)**
4. Bibi Calm: **Start Bibi Calm 2 fps**

**For each run, every time:**
1. [ ] Charge to **85 %**, then unplug. Stay unplugged for the whole run.
2. [ ] Open the spike app and leave the screen on in it for **10 min** to settle. Don't start yet.
3. [ ] Note the battery **start %**.
4. [ ] Open the same foreground screen every run (e.g. one static web page in Chrome), then press this run's start button in the spike app.
5. [ ] **Don't touch the screen or Bibi** for the whole hour. Taps, drags, hiding Bibi or the screen going off make the run invalid.
6. [ ] After about **61 min**, press **Stop** (in the app or in the notification). Under 60.0 min, or no Stop, reads invalid.
7. [ ] Check the summary says **`VALID for gate: yes`**. If it says `no`, repeat that run.
8. [ ] Tap **Share log (CSV)** and attach the file to issue #4 with the start % you noted.

**After the fourth run**
- [ ] Tap **Share exit reasons** and attach `exit_reasons.csv` to issue #4.

**Kill/restart check (separate, about 5 min, not a gate run)**
- [ ] Start a Bibi mode.
- [ ] Swipe the app away from Recents.
- [ ] Lock and unlock the phone.
- [ ] Note whether Bibi survived or came back.
- [ ] Share the run file and exit reasons on issue #4. This run reads invalid (restart, screen off); that's expected.

If Bibi gets killed with default battery settings, note it, then repeat with the app allowed under vivo's background power consumption management, and post both results.

## S2 — Local LLM (issue #5, branch `spike/S2-llm`)
Question: can the phone run a Gemma 3 1B-class model and a 0.5B Qwen-class model (int4, CPU only, fully offline) at ≥ 5 tokens/s, first token ≤ 3 s, with zero out-of-memory kills over a 50-prompt soak? Full protocol: `spikes/s2-llm/SPEC.md` §2, §4 and §6, and `spikes/s2-llm/README.md` on the branch.

> **Build warning.** The LiteRT-LM adapter (`spikes/s2-llm/app/src/main/java/io/github/saadahmed1122/bibi/spike/s2/LiteRtLmEngine.kt`, the only file that touches the library) has **never been compiled**: the library couldn't be downloaded where the app was written, and nothing has been built with Gradle or run on a phone. The first Android Studio build may show errors there. Paste them on issue #5. Don't let Studio upgrade `litertlm-android` past 0.16.1 (0.17+ needs Kotlin 2.3, which AGP 9.0 doesn't provide).

**One-time setup**
1. [ ] Check out `spike/S2-llm`, open `spikes/s2-llm` in Android Studio, let Gradle sync, press **Run**. Open the app once: it creates the model folder and shows its path.
2. [ ] Download the Gemma file: on Hugging Face open `litert-community/Gemma3-1B-IT`, log in, **accept the Gemma terms**, then download `gemma3-1b-it-int4.litertlm` (584,417,280 bytes).
3. [ ] Download a 0.5B Qwen-class `.litertlm` file (not `.task`) from `litert-community/Qwen2.5-0.5B-Instruct` or `litert-community/Qwen3-0.6B`, int4/q4 if offered. The exact file is not confirmed yet [VERIFY]. If only q8 exists, use it and write "q8, no int4 available". Note the exact file names and sizes of both models.
4. [ ] Push both files from the download folder:
   ```bash
   adb push gemma3-1b-it-int4.litertlm /sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s2/files/models/
   adb push <qwen-file>.litertlm      /sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s2/files/models/
   ```
   If `adb push` is refused for that folder [VERIFY on vivo], use the `run-as` fallback in the S2 README ("Put them on the phone").
5. [ ] In the app, tap **Rescan model files**. Each file gets a **Soak 50** and a **Smoke 3** button.
6. [ ] Tap **Smoke 3** for each model. Each should finish 3 prompts (smoke runs never count for the gate). If one fails, share its run CSV on #5 and stop here.

**Before every soak (the run reads invalid otherwise)**
- [ ] Battery **≥ 60 %**, unplugged for the whole run.
- [ ] **Airplane mode on**, Wi-Fi and Bluetooth off.
- [ ] **Battery saver off.**
- [ ] Same fixed brightness, auto-brightness off.
- [ ] Other apps closed from Recents.
- [ ] vivo: note whether **extended RAM** is on or off, and keep it the same for every run.
- [ ] Note the rough room temperature.

**Soaks: one model per run, in this order**
1. [ ] Tap **Soak 50** for Gemma. Leave the app in front with the screen on until it says *Done* (about 25 min at 5 tokens/s). Don't touch it or swipe it away: a swipe-kill reads as an out-of-memory kill.
2. [ ] Read the summary:
   - `VALID for gate: yes` means the run met the conditions above and finished all 50 prompts; only valid runs count. `no` lists the reasons: fix them and repeat the run (an invalid run is repeated, not averaged in).
   - `GATE S2 (<model>): PASS` means this model met all three checks (median speed ≥ 5.0 tokens/s, p95 first token ≤ 3000 ms, 0 out-of-memory kills). `FAIL` names the failed checks and which PRD 0.5 / 0.7 outcome applies. `no verdict` appears when the run isn't valid.
3. [ ] Tap **Share newest run (CSV)** and **Share summary**, and attach both to issue #5.
4. [ ] Let the phone cool for about 10 min (screen off, unplugged), then repeat the "Before every soak" checks and steps 1–3 for the Qwen file.
5. [ ] Optional: repeat the Gemma soak with S1's **Bibi Calm 2 fps** overlay running and label it "with overlay". Whether this run is required for the gate is [OPEN].

**At the end of the session**
- [ ] Tap **Share exit reasons** and attach `exit_reasons.csv` to issue #5, with the device name, both model file names and the extended-RAM setting.
- [ ] Repeat everything on each test phone (Galaxy A23 first if available, then the vivo Y17s). The gate passes on a device only if both models pass there.

## S3a + S3b — Voice (issues #6, #7, branch `spike/S3-voice`)
Question: can the phone speak a reply (S3a TTS, real-time factor ≤ 1.0) and transcribe speech (S3b STT, real-time factor ≤ 0.7 **and** peak memory ≤ 600 MB) fully offline? S3 can fail without stopping the project; it decides whether voice ships in P2. Full protocol: `spikes/s3-voice/SPEC.md` §2, §5 and §7, and `spikes/s3-voice/README.md` → "Owner test steps" on the branch.

> **Build warning.** The sherpa-onnx adapter (`SherpaEngines.kt`) has only been type-checked against sherpa-onnx's v2.0.1 Kotlin API sources. Nothing has been built with Gradle, linked with the real AAR, or run on a phone. The first Android Studio build may show errors: paste them on issue #6. Model licenses have not been reviewed yet [VERIFY]; the spike doesn't ship models, but check them before any release.

**One-time setup**
1. [ ] Download **`sherpa-onnx-2.0.1.aar`** from the sherpa-onnx [v2.0.1 GitHub release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1) and put it at `spikes/s3-voice/app/libs/sherpa-onnx-2.0.1.aar` (exact name; it is git-ignored, don't commit it). Its size is not checked [VERIFY].
2. [ ] Check out `spike/S3-voice`, open `spikes/s3-voice` in Android Studio, let Gradle sync, press **Run** on the vivo Y17s. Open the app once: it creates its models folder.
3. [ ] From https://github.com/k2-fsa/sherpa-onnx/releases download these five archives (names per SPEC §5 [VERIFY names and sizes on download]) and unpack each (e.g. `tar xf kokoro-en-v0_19.tar.bz2`):
   - `tts-models/kokoro-en-v0_19.tar.bz2`
   - `tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2`
   - `tts-models/vits-piper-en_US-amy-low.tar.bz2`
   - `asr-models/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17.tar.bz2`
   - `asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2`
4. [ ] Download `tts-models/lexicon-en-us.txt` and copy it into **both** `kokoro-en-v0_19/` and `vits-piper-en_US-amy-low/`. Optional: delete the `espeak-ng-data/` and `test_wavs/` folders to save space.
5. [ ] Push the five folders, keeping their unpacked names (Android Studio's Device Explorer works too):
   ```bash
   M=/sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s3/files/models
   adb shell mkdir -p $M
   adb push kokoro-en-v0_19 $M/
   adb push kokoro-int8-multi-lang-v1_1 $M/
   adb push vits-piper-en_US-amy-low $M/
   adb push sherpa-onnx-streaming-zipformer-en-20M-2023-02-17 $M/
   adb push sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8 $M/
   ```
6. [ ] Tap **Refresh results**: all five models must show `ready` (otherwise the run button names the missing files).
7. [ ] Record the 3 test clips in the app, in a quiet room at normal speaking speed: tap **Record clip1**, read the sentence shown, tap again to stop; same for `clip2` and `clip3`. The first tap asks for the microphone; that is the only time the app asks, and it never listens in the background. The clip list should show about 2, 6 and 10 s.

**Before every run**
- [ ] Battery **above 50 %**; note whether it is charging.
- [ ] **Battery saver off**, other apps closed.
- [ ] Phone cool: wait about **2 min** between runs.
- [ ] Screen on, app in front for the whole run (leaving it can let Android kill the inference process, which counts as a death).
- [ ] **Play off** for gate runs.

**Runs: one model at a time, threads `2` then `4`**
1. [ ] **S3a TTS:** *Run TTS: Kokoro en v0.19 (fp32)* with threads `2`, wait for "Finished", wait 2 min, set threads `4`, run again.
2. [ ] Repeat step 1 for *Kokoro multi-lang v1.1 (int8)*, then for *Piper en_US amy low*.
3. [ ] Once per voice, turn **Play** on and run with 2 threads to listen. Note any mispronounced or skipped words on issue #6.
4. [ ] **S3b STT:** *Run STT: Streaming Zipformer en 20M* with threads `2`, then `4` (2 min apart); then the same for *Parakeet TDT 0.6B v2*. If Parakeet dies (likely an out-of-memory kill), that is a result, not a broken test.

**Read the summary after each run**
- [ ] Each model and thread count shows median and p95 RTF, peak memory (PSS, plus RSS for reference) and a verdict:
  - `PASS`: median RTF within the gate (TTS ≤ 1.0, STT ≤ 0.7) and, for STT, peak PSS ≤ 600 MB.
  - `FAIL`: RTF or memory above the gate, or the inference process died.
  - `BORDERLINE [VERIFY]`: median passes but p95 doesn't, or (STT) PSS passes but RSS is above 600 MB. Repeat the run.
  - `INCOMPLETE`: repeat that run.
- [ ] The summary also prints the PRD 0.7 recommendation per gate (keep Kokoro / switch to Piper / no spoken replies; small streaming STT / Parakeet optional / push-to-talk with typed fallback).

**Share**
- [ ] Tap **Share all runs (CSV)**, **Share summary** and **Share exit reasons** (`exit_reasons.csv`). The transcripts are in the STT runs' `detail` column.
- [ ] Post the TTS parts on issue **#6** and the STT parts on issue **#7**, with the phone model and anything unusual (heat, charging, a call during the run).
- [ ] If the Galaxy A23 is available, repeat everything on it; the summary keeps the two phones apart.

## S4 — Tools and permissions (issue #8, branch `spike/S4-tools`)
Full steps: `spikes/s4-tools/README.md` → "Owner test steps" on the S4 branch. Gate: each of the five actions on the shortcut path, 20 benchmark runs, **p95 < 300 ms** (SPEC §2–3). The real-LLM path is **not** part of this test yet; it waits for S2. The **Fake LLM path** switch stands in for it.

1. [ ] Install **Bibi S4 spike** from Android Studio. Open it, wait for `Launcher apps: N` and the handler line, and **write down** the handler line (`NONE` means no app handles that action).
2. [ ] With **Dry run** and **Fake LLM path** off, try each of the five commands with the quick buttons (3 times each, Back to return): open settings, wake me at 7:30, set a timer for 5 minutes, flashlight on/off, call 0123 456 789. Note which clock app opened and whether it showed a screen.
3. [ ] Try the same five commands by typing them in the text box and pressing Submit.
4. [ ] Dial check: tapping **call 0123 456 789** must show a confirmation card with the number. **Open dialer** fills in the number and does **not** call.
5. [ ] Tap call again and **Cancel**: nothing is dialed. Tap it once more and leave the card alone for 30 s: it must close by itself, say it was cancelled, and **not** dial.
6. [ ] Turn **Fake LLM path** on and repeat the commands, plus "it's dark in here", "broken timer test" (timer after one repair) and "send a text to mum" (must be rejected). Then do the taint and kill-switch checks (README steps 7–8).
7. [ ] Close other apps and press **Benchmark** (about 30 s, don't touch the phone).
8. [ ] Read the results panel: the `S4 shortcut gate:` line (PASS, FAIL or INCOMPLETE, with its reason) and the p50/p95 of the five `bench shortcut` rows against 300 ms.
9. [ ] Tap **Refresh results**, then **Share log (CSV)**.
10. [ ] Post on issue #8: the CSV, the phone, the handler line and a note of what each action did (which clock app opened, whether the dial confirmation showed, any safety check that did not behave as above). Then delete the "Bibi S4 test" alarms and timers.

## How to send results
1. In the spike app, tap **Share log** (S1: **Share log (CSV)**, plus **Share exit reasons** at the end) and send the file to yourself or straight to the browser.
2. On the spike's GitHub issue, add a comment: attach the file (or paste its contents) and add the notes asked for above (start %, device, battery setting, anything odd).
3. Tell Claude the results are posted. Claude's `spike-analyst` agent reads the logs, fills the result table, applies the gate rule (pass, fail, or `[VERIFY]` if the checks disagree), and records the verdict in the spike's `results/RESULTS.md`, `docs/project_status.md` → Decisions, and the issue.
