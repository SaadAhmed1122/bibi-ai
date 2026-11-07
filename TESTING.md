# TESTING.md — what to test on the phone

This is the one file to open when you have time to test Bibi AI on a phone. It lists every feasibility spike (P0), whether its test app is ready, the exact steps, and what to send back. Each spike's full spec stays on its own branch; this file only condenses the owner steps.

## Devices
- **vivo Y17s** (6 GB RAM, Helio G85, Android 15): the device every test runs on first. Ignore the "+6 GB extended RAM"; it is swap.
- **Samsung Galaxy A23** (floor device per PRD 0.4): run the same tests on it if it is available. Its RAM and Android version are unconfirmed [VERIFY] (open items 1, 2 and 10).

Details: [docs/project_status.md](docs/project_status.md) → Test devices.

## Status of all spikes

| Spike | Issue | Branch | App folder | Ready to test? | What to send back |
|---|---|---|---|---|---|
| S1 — Overlay | [#4](https://github.com/SaadAhmed1122/bibi-ai/issues/4) | `main` (merged, PR [#10](https://github.com/SaadAhmed1122/bibi-ai/pull/10)) | `spikes/s1-overlay/` | **PASS** (2026-10-31, vivo): four valid runs; Bibi costs 27.8 mA at 12 fps, 11.5 mA in Calm (gate ≤ 150 mA, ≤ 3 %/h). Optional: kill/restart check | One run CSV per run (four runs), the start % of each, exit reasons at the end, and the kill/restart check files. Post on #4. |
| S2 — Local LLM | [#5](https://github.com/SaadAhmed1122/bibi-ai/issues/5) | `main` (PRs [#12](https://github.com/SaadAhmed1122/bibi-ai/pull/12), [#22](https://github.com/SaadAhmed1122/bibi-ai/pull/22)); chat soak on `feature/5-s2-conversation-soak` (PR [#23](https://github.com/SaadAhmed1122/bibi-ai/pull/23), open) | `spikes/s2-llm/` | **Per-prompt soak FAIL** — Gemma 3 1B int4: 6.54 tok/s PASS, 0 OOM PASS, first-token p95 3,493 ms FAIL. **Chat soak 50 PASS** — first-token p95 2,409 ms, 7.04 tok/s, 0 OOM (owner: chat mode decides the gate → S2 PASS for Gemma on vivo). Qwen dropped (owner, 2026-10-31): done | Chat soak: the run CSV, the summary and `exit_reasons.csv`. Post on #5. |
| S3a + S3b — Voice | [#6](https://github.com/SaadAhmed1122/bibi-ai/issues/6), [#7](https://github.com/SaadAhmed1122/bibi-ai/issues/7) (closed) | `main` (merged, PR [#14](https://github.com/SaadAhmed1122/bibi-ai/pull/14)) | `spikes/s3-voice/` | **Done** — Piper TTS; Parakeet STT by owner override. Results in `spikes/s3-voice/results/RESULTS.md` | Nothing more on the vivo. Galaxy A23 run if it becomes available. |
| S4 — Tools and permissions | [#8](https://github.com/SaadAhmed1122/bibi-ai/issues/8) | `main` (merged, PRs [#13](https://github.com/SaadAhmed1122/bibi-ai/pull/13), [#19](https://github.com/SaadAhmed1122/bibi-ai/pull/19)) | `spikes/s4-tools/` | **Gate PASS** (shortcut p95 6.2–19.4 ms). **Owner on-screen checks pending** (README steps 2–8, 13–14). Pass 2 is now slot extraction (branch `feature/8-s4-slot-extraction`); its LLM benchmark on a phone is pending [VERIFY] | The on-screen notes for those steps and the CSV. Post on #8. |

## Before any test
- [ ] **Android Studio** installed (any 2026 stable release). It offers to install the Android SDK platform a spike needs.
- [ ] **Get the spike code:** all four spike apps are on `main` (`git checkout main && git pull`). Only the S2 chat soak needs the PR #23 branch (`git checkout feature/5-s2-conversation-soak`) until that PR is merged.
- [ ] **Open the spike folder, not the repo root:** File → Open → `spikes/<spike folder>` (for S1: `spikes/s1-overlay`). Let Gradle sync finish.
- [ ] **Connect the phone:** enable Developer options (Settings → About phone → tap the build number 7 times), turn on **USB debugging**, plug in by USB, accept the "Allow USB debugging" prompt, then pick the phone in Studio's device menu and press **Run**.
- [ ] **Common phone settings, the same for every run:**
  - [ ] Brightness fixed at one level, **auto-brightness off**.
  - [ ] **Battery saver off.**
  - [ ] Same **refresh-rate** setting every run (Settings → Display, e.g. 60 Hz or 90 Hz).
  - [ ] **Screen timeout at the maximum** the phone allows.
  - [ ] Same Wi-Fi state, no downloads or updates running.

If the build fails, paste the error into the spike's issue.

## S1 — Overlay (issue #4, on `main`)
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

## S2 — Local LLM (issue #5, on `main`)
Question: can the phone run a Gemma 3 1B-class model and a 0.5B Qwen-class model (int4, CPU only, fully offline) at ≥ 5 tokens/s, first token ≤ 3 s, with zero out-of-memory kills over a 50-prompt soak? Full protocol: `spikes/s2-llm/SPEC.md` §2, §4 and §6, and `spikes/s2-llm/README.md` on the branch.

> **Build note.** The app builds and has run on the vivo Y17s (results in `spikes/s2-llm/results/RESULTS.md`). Don't let Studio upgrade `litertlm-android` past 0.16.1 (0.17+ needs Kotlin 2.3, which AGP 9.0 doesn't provide).

**One-time setup**
1. [ ] On `main`, open `spikes/s2-llm` in Android Studio, let Gradle sync, press **Run**. Open the app once: it creates the model folder and shows its path.
2. [ ] Download the Gemma file (done on the vivo, 2026-10-30): on Hugging Face open `litert-community/Gemma3-1B-IT`, log in, **accept the Gemma terms**, then download `gemma3-1b-it-int4.litertlm` (584,417,280 bytes).
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
4. ~~Repeat for the Qwen file.~~ Not needed: Qwen was dropped from the project (Decision 2026-10-31).
5. [ ] Optional: repeat the Gemma soak with S1's **Bibi Calm 2 fps** overlay running and label it "with overlay". Whether this run is required for the gate is [OPEN].

**Chat soak 50 (next S2 run; needs the PR [#23](https://github.com/SaadAhmed1122/bibi-ai/pull/23) build)**
The per-prompt Gemma soak failed on first-token p95 (3,493 ms). This run tests prefix reuse: one conversation, with the system prompt prefilled once. Whether it counts for the S2 gate is the owner's decision after the run [OPEN].
1. [ ] Check out `feature/5-s2-conversation-soak`, open `spikes/s2-llm`, press **Run**. The button only exists in this build until PR #23 is merged.
2. [ ] Do the "Before every soak" checks above. **Unplug** the phone and turn **airplane mode on**.
3. [ ] Tap **CHAT SOAK 50: GEMMA3-1B-IT-INT4.LITERTLM**. Leave the app in front with the screen on, don't touch it, and wait for *Done*.
4. [ ] Plug the phone back in. Tap **Share newest run (CSV)** and **Share summary**, and attach both to issue #5 (plus **Share exit reasons**).

**At the end of the session**
- [ ] Tap **Share exit reasons** and attach `exit_reasons.csv` to issue #5, with the device name, both model file names and the extended-RAM setting.
- [ ] Repeat everything on each test phone (Galaxy A23 first if available, then the vivo Y17s). The gate passes on a device only if both models pass there.

## S3a + S3b — Voice (issues #6, #7, on `main`) — done
Question: can the phone speak a reply (S3a TTS, real-time factor ≤ 1.0) and transcribe speech (S3b STT, real-time factor ≤ 0.7 **and** peak memory ≤ 600 MB) fully offline? S3 can fail without stopping the project; it decides whether voice ships in P2. Full protocol: `spikes/s3-voice/SPEC.md` §2, §5 and §7, and `spikes/s3-voice/README.md` → "Owner test steps" on the branch.

> **Done on the vivo Y17s** (results in `spikes/s3-voice/results/RESULTS.md`). The steps below stay for a Galaxy A23 run. Model licenses have not been reviewed yet [VERIFY]; the spike doesn't ship models, but check them before any release.

**One-time setup**
1. [ ] Download **`sherpa-onnx-2.0.1.aar`** from the sherpa-onnx [v2.0.1 GitHub release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1) and put it at `spikes/s3-voice/app/libs/sherpa-onnx-2.0.1.aar` (exact name; it is git-ignored, don't commit it). Its size is not checked [VERIFY].
2. [ ] On `main`, open `spikes/s3-voice` in Android Studio, let Gradle sync, press **Run** on the vivo Y17s. Open the app once: it creates its models folder.
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

## S4 — Tools and permissions (issue #8, on `main`)
Full steps: `spikes/s4-tools/README.md` → "Owner test steps". Gate: each of the five actions on the shortcut path, 20 benchmark runs, **p95 < 300 ms** (SPEC §2–3). **Measured: PASS** on the vivo Y17s (p95 6.2–19.4 ms; `spikes/s4-tools/results/RESULTS.md`), and the LLM benchmark has run too.

**Still to do: the on-screen checks** (README numbering; each is a short manual check, not a benchmark):
1. [ ] **Step 2–3:** the shortcut **Benchmark** and the `S4 shortcut gate:` line, only if you reinstall and want to confirm it on screen.
2. [ ] **Step 4:** with **Dry run** and **Fake LLM path** off, tap each quick command 3 times (settings, alarm 7:30, 5-minute timer, flashlight on/off, call 0123 456 789 → confirmation card, **Open dialer** fills the number and does **not** call). Note which clock app opened and whether it showed a screen.
3. [ ] **Step 5:** **Fake LLM path** on: the commands again, plus "it's dark in here", "take me to maps", "broken timer test" and "send a text to mum" (must be rejected).
4. [ ] **Step 6:** confirmation timeout: leave the call card alone for 30 s; it closes, says cancelled, nothing dialed.
5. [ ] **Steps 7–8:** taint (**Untrusted content in context** → flashlight asks for confirmation; "injection test" too) and kill switch (**Disable all actions** → refused).
6. [ ] **Steps 11–14:** pass 2 is now **slot extraction** (Decision 2026-10-30; only the Decision Maker model is needed, **LLM tool call (experimental)** stays off). Load models, run the **LLM benchmark** and note the slot-extraction table, then **Real LLM path** on: the phrases in the README (incl. "set an alarm" → must ask), then the taint and kill-switch checks. The LLM benchmark part was run by Claude on 2026-10-31 (95/100, RESULTS.md §8); the on-screen phrases, taint and kill-switch checks are still yours.
7. [ ] **Share log (CSV)** and post it on issue #8 with what each step did on screen. Then delete the "Bibi S4 test" alarms and timers.

## P1 checkpoint B — chat in the app (issue #37)
Claude's scripted run on the vivo passed: 10 turns, first word in 1.26–1.31 s, Stop and Clear work, history survives a force-stop. The checkpoint also needs **your own try**:
1. [ ] Install the debug build from the Task 6 branch (`./gradlew :app:installDebug`). The model must be at `Android/data/io.github.saadahmed1122.bibi/files/models/` (already there on the vivo).
2. [ ] Home → **Chat with Bibi**. Wait for "Bibi is waking up…" to go away (about 7 s).
3. [ ] Chat for a few turns. Ask for a long story and tap **Stop**: the partial answer stays, marked "Stopped", and "Bibi is getting ready again…" shows for about 7 s.
4. [ ] Rotate the phone, swipe the app away, reopen: the chat is still there.
5. [ ] Note anything odd (wording, slow moments, answers that look wrong) on issue #37. Known: after Stop or Clear, Bibi forgets earlier turns until Task 7 (#38).

## P1 — model import (issue #42)
Claude's run on the vivo passed: wrong file refused, real file imported in ~7 s, chat works, Remove works, restart shows Ready. To try it yourself:
1. [ ] Copy `gemma3-1b-it-int4.litertlm` to the phone's Downloads (USB or Files app).
2. [ ] Home → **Bibi's model** → Remove model, then **Choose model file** and pick it. Watch the percentage; it should reach "The model is ready." in well under a minute.
3. [ ] **Open chat** and send a message.
4. [ ] Optional: pick any other file and check the error message. Delete the copy in Downloads afterwards (the app keeps its own).

## P1 — floating Bibi and the chat card (issues #44, #45, #47) — Checkpoint D
Claude's runs on the vivo passed in portrait and landscape: the permission explainer, drag and snap, tap → chat card, Hide/Show/Turn off, Calm mode (12.3 → 2.0 fps). To try it:
1. [ ] Home → turn on **Floating Bibi**. Read the explainer, tap **Open settings**, find Bibi, allow it, come back: Bibi appears.
2. [ ] Go to another app. Drag Bibi and let go: it slides to the nearer side.
3. [ ] Tap Bibi: the chat card opens. Send a message (the keyboard's arrow key sends too), then close it with Back, a tap outside, or Close.
4. [ ] Try steps 2–3 in landscape too.
5. [ ] Notifications (if allowed): expand "Bibi is here" → **Hide**, **Show Bibi**, **Turn off**. After Turn off the Home switch shows off.
6. [ ] Turn on **Calm mode**: the animation slows down.
7. [ ] Note anything odd on issue #44.

## P2 — tap-to-talk over other apps (issue #118)
- [ ] On the home screen or in any app, tap floating Bibi once. The bubble shows "Waking up…", then "Listening…" and the green mic dot appears.
- [ ] Ask something, then stop talking. After about 1 s Bibi shows "Thinking…", answers out loud, and the answer stays in the bubble.
- [ ] Ask a second question without tapping. Then stay quiet: after about 8 s the talk ends and the bubble fades out.
- [ ] Say "set a timer for two minutes": the timer starts. Say "open chat": the chat card opens. Long-press Bibi: the chat card opens.
- [ ] Repeat once in landscape.

## P2 — licenses, privacy policy, report a reply (issue #88)
- [ ] Settings → Privacy policy and Licenses open and scroll, in portrait and landscape.
- [ ] Long-press one of Bibi's replies → Report this reply → the share sheet shows the reply; pick an app, or press Back to cancel. From the floating card, the card closes first.
- [x] Privacy policy hosted at https://saadahmed1122.github.io/bibi-ai-privacy/ (2026-11-04).

## P3 — usage counters (issue #126)
Checked by Claude on the vivo 2026-11-04 (debug build; read with `adb shell run-as io.github.saadahmed1122.bibi cat files/usage/usage.txt`).
- [x] Open the app, Home, wait 35 s, open it again in landscape: 2 sessions. Back within 30 s, or a rotation: still 2.
- [x] Home → Debug → Crash log → Crash now, then reopen: 3 sessions, 1 crashed session, 1 crash.
- [x] A talk from floating Bibi counts 1 talk; "remember my cat is called Milo" counts 1 message and 1 memory; the file holds no message text.
- [x] Settings → Share usage numbers shows the whole report (portrait and landscape); Share opens the share sheet. Privacy shows "Usage numbers …, sessions: 4" and the Delete everything text names them (Task 2, #127; checked by Claude 2026-11-04).
- [ ] Owner: Delete everything on a phone whose data you don't need, then Share usage numbers shows "First use: not used yet" until the next open. Claude didn't run it on the vivo, because it would erase your chats and memories.

## P3 — Send feedback (issue #128)
- [x] Settings → Send feedback opens Gmail with To swdeveloper.sa@gmail.com, subject "Bibi AI feedback <version>", version, phone and three prompts (vivo, 2026-11-04). The row shows in portrait and landscape.
- [ ] Owner (optional): on a phone with no email app, the tap shows "No email app found. Please write to …". Not checked: the vivo has Gmail.

## P3 — Checkpoint F: the 0.3.0-p3 closed-test build (issue #130)
Claude checked on the vivo (debug build, 2026-11-04): counters, Share usage numbers, Send feedback, both orientations; the release manifest is `0.3.0-p3` / code 3 with the same permissions as P2 and no `INTERNET`.
Owner, before uploading:
- [ ] The P2 items first: the 1-hour P2 gate (#90), the FGS video, the Drive link (docs/tester-guide.md).
- [ ] Build and sign 0.3.0-p3 (docs/release.md), install it and open Settings → Share usage numbers: it says "App version: 0.3.0-p3".
- [ ] Play Console: recheck Data safety against docs/play-console.md (usage numbers and Send feedback are user-initiated sharing [VERIFY]).
- [x] Uploaded and sent for review on Closed testing – Alpha (2026-11-04).
- [ ] When review passes: send docs/tester-invite.md to the testers and post the LinkedIn call.

## P3 — Share crash log (issue #132)
- [x] Settings → Share crash log shows the header (version, phone, Android) and the log; Share opens the chooser (vivo, 2026-11-04).
- [ ] Owner (optional): on a phone with no crashes it says "No crashes recorded".

## P3 — AI process crash after a long freeze (issue #150)
- [x] Fixed build on the vivo (2026-11-05): the log shows `router waited_ms=…` then `router load_ms=…`, so the action model loads only after the chat model. "it is dark in here, give me some light" turned the light on (landscape), "flashlight off" turned it off and a chat question got an answer (portrait).
- [ ] Owner, over the next days: leave Floating Bibi on, use the phone normally, and send Settings > About > Share crash log if Bibi ever stops answering. The crash needed Android to freeze the AI process for minutes or hours mid-load, so it can't be forced on demand.

## P3 — In-app model download (issue #161)
Claude checked on the vivo (debug build, 2026-11-07): with the default Hugging Face URL the model step shows "asked for a login (HTTP 401)" with Try again and Choose model file; with the file served from the laptop (`adb reverse`), the download reached Ready in portrait and in landscape; killing the app mid-download and opening it again ended Ready; the model in private storage is 584,417,280 bytes and the temporary file is gone.

For the owner, after you put the file on your server:
1. [ ] Build with `-PBIBI_MODEL_URL=https://<your server>/gemma3-1b-it-int4.litertlm` (or put `BIBI_MODEL_URL=...` in `~/.gradle/gradle.properties`). Check in a desktop browser first that the link downloads the file without a login page.
2. [ ] Install, go to the model step, tap **Download the model** on mobile data or Wi-Fi. Leave the app while it runs; the system notification shows progress.
3. [ ] Turn on airplane mode halfway: the screen says "Waiting for the internet"; turn it off and the download continues.
4. [ ] It ends with "The model is ready." Send a chat message.

## P1 exit gate — 1 hour without a crash (issue #49)
The last P1 check (PRD 0.6). Results go in `docs/p1-gate.md`.

**Before you start**
1. [ ] Install the **release** build: from the Play internal track if it's set up (docs/release.md), or the signed release APK.
2. [ ] The model is imported (Home → Bibi's model shows "ready"), and **Floating Bibi** is on.
3. [ ] Charge to at least 80 %, then **unplug**. Write down the time and the battery %.

**During the hour** (use the phone normally between these; spread them out)
1. [ ] About **20 messages** in total: some in the app (Home → Chat with Bibi), some in the chat card (tap floating Bibi over another app).
2. [ ] At least **2 times**, ask for a long story and tap **Stop** halfway.
3. [ ] **Clear chat** once (app chat screen, top right).
4. [ ] Rotate to landscape at least once while the card is open.
5. [ ] Hide Bibi from the notification and show it again once.
6. [ ] Note anything odd: a wait over 10 s, a wrong-looking answer, a frozen screen.

**At the end (60 minutes or more)**
1. [ ] Send one more message: it must get an answer.
2. [ ] Write down the time and battery %.
3. [ ] Plug in and tell Claude "gate done". Claude reads the crash and ANR history and fills in `docs/p1-gate.md`.

## How to send results
1. In the spike app, tap **Share log** (S1: **Share log (CSV)**, plus **Share exit reasons** at the end) and send the file to yourself or straight to the browser.
2. On the spike's GitHub issue, add a comment: attach the file (or paste its contents) and add the notes asked for above (start %, device, battery setting, anything odd).
3. Tell Claude the results are posted. Claude's `spike-analyst` agent reads the logs, fills the result table, applies the gate rule (pass, fail, or `[VERIFY]` if the checks disagree), and records the verdict in the spike's `results/RESULTS.md`, `docs/project_status.md` → Decisions, and the issue.
