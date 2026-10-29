# TESTING.md — what to test on the phone

This is the one file to open when you have time to test Bibi AI on a phone. It lists every feasibility spike (P0), whether its test app is ready, the exact steps, and what to send back. Each spike's full spec stays on its own branch; this file only condenses the owner steps.

## Devices
- **vivo Y17s** (6 GB RAM, Helio G85, Android 15): the device every test runs on first. Ignore the "+6 GB extended RAM"; it is swap.
- **Samsung Galaxy A23** (floor device per PRD 0.4): run the same tests on it if it is available. Its RAM and Android version are unconfirmed [VERIFY] (open items 1, 2 and 10).

Details: [docs/project_status.md](docs/project_status.md) → Test devices.

## Status of all spikes

| Spike | Issue | Branch | App folder | Ready to test? | What to send back |
|---|---|---|---|---|---|
| S1 — Overlay | [#4](https://github.com/SaadAhmed1122/bibi-ai/issues/4) | `spike/S1-overlay` (PR [#10](https://github.com/SaadAhmed1122/bibi-ai/pull/10)) | `spikes/s1-overlay/` | **Yes** | One run CSV per run (four runs), the start % of each, exit reasons at the end, and the kill/restart check files. Post on #4. |
| S2 — Local LLM | [#5](https://github.com/SaadAhmed1122/bibi-ai/issues/5) | `spike/S2-llm` (the S2 draft PR on branch `spike/S2-llm`; number not assigned yet) | `spikes/s2-llm/` | **Yes** — app written; first build happens in Android Studio (not built here) | One run CSV per model (plus its summary) and `exit_reasons.csv`. Post on #5. |
| S3a + S3b — Voice | [#6](https://github.com/SaadAhmed1122/bibi-ai/issues/6), [#7](https://github.com/SaadAhmed1122/bibi-ai/issues/7) | `spike/S3-voice` | Not on the branch yet | No — being built now; steps added when the app lands | Defined when the app lands |
| S4 — Tools and permissions | [#8](https://github.com/SaadAhmed1122/bibi-ai/issues/8) | `spike/S4-tools` | Not on the branch yet | No — being built now; steps added when the app lands | Defined when the app lands |

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
Steps will be filled in from the spike's "Owner test steps" when its app lands on the branch.

## S4 — Tools and permissions (issue #8, branch `spike/S4-tools`)
Steps will be filled in from the spike's "Owner test steps" when its app lands on the branch.

## How to send results
1. In the spike app, tap **Share log** (S1: **Share log (CSV)**, plus **Share exit reasons** at the end) and send the file to yourself or straight to the browser.
2. On the spike's GitHub issue, add a comment: attach the file (or paste its contents) and add the notes asked for above (start %, device, battery setting, anything odd).
3. Tell Claude the results are posted. Claude's `spike-analyst` agent reads the logs, fills the result table, applies the gate rule (pass, fail, or `[VERIFY]` if the checks disagree), and records the verdict in the spike's `results/RESULTS.md`, `docs/project_status.md` → Decisions, and the issue.
