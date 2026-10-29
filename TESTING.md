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
| S2 — Local LLM | [#5](https://github.com/SaadAhmed1122/bibi-ai/issues/5) | `spike/S2-llm` | Not on the branch yet | No — being built now; steps added when the app lands | Defined when the app lands |
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
Steps will be filled in from the spike's "Owner test steps" when its app lands on the branch.

## S3a + S3b — Voice (issues #6, #7, branch `spike/S3-voice`)
Steps will be filled in from the spike's "Owner test steps" when its app lands on the branch.

## S4 — Tools and permissions (issue #8, branch `spike/S4-tools`)
Steps will be filled in from the spike's "Owner test steps" when its app lands on the branch.

## How to send results
1. In the spike app, tap **Share log** (S1: **Share log (CSV)**, plus **Share exit reasons** at the end) and send the file to yourself or straight to the browser.
2. On the spike's GitHub issue, add a comment: attach the file (or paste its contents) and add the notes asked for above (start %, device, battery setting, anything odd).
3. Tell Claude the results are posted. Claude's `spike-analyst` agent reads the logs, fills the result table, applies the gate rule (pass, fail, or `[VERIFY]` if the checks disagree), and records the verdict in the spike's `results/RESULTS.md`, `docs/project_status.md` → Decisions, and the issue.
