# Spike S1 — Overlay: spec, plan and test protocol

Issue: [#4](https://github.com/SaadAhmed1122/bibi-ai/issues/4) · Milestone: P0 · Feature doc: [`docs/features/overlay.md`](../../docs/features/overlay.md) · PRD 0.7 (S1), §12.4, §12.5, §12.10, §17.5

> Spike code is **throwaway**. It exists to answer one question with numbers, not to become the app. Nothing here goes into the P1 codebase without a rewrite.

## 1. Question
Can a foreground service keep an animated Bibi floating over other apps on our floor devices **for 1 hour, stably, at ≤ 3% battery per hour**?

## 2. Pass / fail (PRD 0.7)
| Check | Pass |
|---|---|
| Stability | 1-hour session: no crash, no OS kill, overlay still on screen at the end |
| Battery | ≤ 3% per hour with the screen on |

**How "≤ 3% per hour" is measured [VERIFY with owner].** A phone with its screen on drains far more than 3 %/h by itself, so the gate can only mean **Bibi's extra drain**. The protocol (§5) alternates **baseline hours without Bibi** with **Bibi hours** under the same conditions, and the gate is the **mean-current delta**:

`Bibi cost (mA) = mean current of the Bibi run − mean current of the baseline run`

- **Mean current** is the time-weighted mean of |`current_ua`| over the `sample` rows of one run file (each sample weighted by the time to the next one). The app's results panel computes it.
- **Threshold:** 3 %/h of a 5000 mAh battery ≈ **150 mA**. The 5000 mAh figure is the vivo Y17s rated capacity from GSMArena [VERIFY capacity]; the app converts with %/h = mA ÷ 5000 × 100.
- **Percent-based drain is secondary.** `battery_pct` moves in 1 % steps, so over 60 min it is only accurate to about ±1–2 %/h, too coarse for a 3 %/h gate.
- **The charge counter is not used:** on the vivo it is derived from the percentage ([RESULTS.md](results/RESULTS.md)).
- **Checkpoint A finding [VERIFY]:** the 53-min vivo Calm log gives 18.6 %/h from current (mean 928 mA at 5000 mAh) but 15.8 %/h from percent steps. Either `current_ua` is scaled differently on this device or the usable capacity isn't 5000 mAh. Both runs of a comparison come from the same phone and the same counter, so the **delta** in mA is consistent within the device, but converting it to %/h at 5000 mAh may be off by about 15 % [VERIFY].
- **`pct_per_ma`:** for every run of ≥ 60 min the summary also reports `pct_per_ma` = percent-based drain (%/h) ÷ mean current (mA). It calibrates this phone's current reading against its own percentage, without assuming the capacity.

**Gate decision uses two checks:**
1. **Absolute:** Bibi mean mA − baseline mean mA ≤ **150 mA**.
2. **Relative:** Bibi cost %/h ≈ (Bibi mean mA − baseline mean mA) × the **baseline run's** `pct_per_ma` ≤ **3 %/h**.

If both pass, the battery check passes; if both fail, it fails. **If they disagree, record both numbers and mark the battery gate `[VERIFY]`, not pass**, until the cause (current scaling or capacity) is resolved.

**If it fails:** Bubble/Edge mode only, lower frame rate, calm mode as the default (PRD 0.7). The decision is recorded in `docs/project_status.md`.

## 3. What the spike app does
- **Main screen** (plain Android views, no libraries):
  - **1. Allow drawing over other apps**, **2. Allow notifications**
  - three start buttons, one per fixed run mode: **Start baseline (no Bibi)**, **Start Bibi 12 fps**, **Start Bibi Calm 2 fps**
  - **Stop**, **Refresh results**, **Share log (CSV)**, **Share exit reasons**
  - a status line (device, permissions, running mode) and the newest run's summary.
- **Run modes are fixed at start.** The mode (`baseline`, `bibi12`, `bibiCalm`) comes from the start intent and can't change during a run; to switch, press *Stop* and start again. A Bibi mode won't start without the overlay permission (logged as `start_blocked`).
- **`OverlayService`**: a foreground service of type `specialUse` (Android 14+). Its low-importance notification shows the mode and offers **Hide/Show** (Bibi modes only) and **Stop**.
- **Bibi overlay** (Bibi modes only): a 96 dp window (`TYPE_APPLICATION_OVERLAY`, not focusable, touches outside pass through):
  - draws pre-rendered Bibi frames (idle + blink, from `docs/design/character/states/`) on a `Canvas`, driven by a `Choreographer` clock
  - **12 fps** in `bibi12`, **2 fps** in `bibiCalm`, **no drawing while the screen is off** (PRD §12.10, §17.5)
  - breathing scale (3.5 s cycle, off in Calm) and a random blink every 3–6 s (Calm: every 8 s) (PRD §20.13)
  - drag to move, release to snap to the nearest side edge (~280 ms), position remembered; tap says hi
- **One log file per run:** `files/runs/s1_<yyyyMMdd-HHmm>_<model>_<mode>.csv`. A `sample` row every **10 s** in every mode, plus event rows. Columns:
  - `time, epoch_ms, run_id, kind, detail`
  - battery: `battery_pct, charge_uah, current_ua, charging`
  - state: `screen_on, thermal, frames, fps, pss_kb` (PSS on `sample` rows only), `calm, mode`
  - conditions: `voltage_mv, batt_temp_dc` (tenths of °C), `brightness, brightness_mode, power_save, refresh_hz`
  - event kinds: `start`, `start_baseline`, `stop`, `screen_on`, `screen_off`, `tap`, `hide`, `show`, `restart`, `restart_blocked`, `baseline_restart`, `baseline_restart_blocked`, `overlay_error`
- **Exit reasons:** `files/exit_reasons.csv` (`time, epoch_ms, reason, detail, process`), never cleared. On every activity launch and service start the app appends Android's recorded process exits (`ApplicationExitInfo`), so OS kills are visible. Blocked starts go here too, with `process` = `app_event`, and never create a run file.
- **Restart path:** the service is `START_STICKY` and resumes the same mode in the same run file.
  - **Bibi modes:** the overlay is added first, and `startForeground()` is called after Bibi's first frame is committed, with a 2 s fallback timer. Apps targeting Android 15+ may start a foreground service from the background only while they have a visible overlay window (developer.android.com, "Restrictions on starting a foreground service from the background"). Whether "after first frame" satisfies this on the vivo is [VERIFY on device].
  - **Baseline:** no overlay, so no exemption applies. The restart is logged `baseline_restart` or `baseline_restart_blocked`.
- **Summary and "VALID for gate".** The results panel reads the newest run file and shows the mode, duration, mean current (mA), %/h from current and from percent, battery start → end, average fps (screen on), max PSS, event counts and the conditions (brightness range and mode, refresh rate, whether power save was ever on, battery temperature and voltage ranges). It then prints `VALID for gate: yes`, or `no` with every reason that applies:
  - shorter than 60 min
  - screen off during the run
  - charging during the run
  - a restart or blocked start
  - an overlay window error
  - mixed modes in one file
  - Bibi hidden during the run
  - Bibi touched (tap or drag) during the run
  - no clean stop (the run didn't end with *Stop*, so the process may have died)

## 4. Plan (one commit per task)
1. Gradle project skeleton (AGP 9.0.1 with built-in Kotlin, Gradle 9.1.0, compileSdk/targetSdk 36, minSdk 34, no other dependencies).
2. Manifest: permissions, `specialUse` service with the subtype property, launcher activity.
3. Bibi frames: render `idle.svg` and `idle_blink.svg` to 288 px PNGs (96 dp at 3x).
4. `BibiView`: Canvas renderer, Choreographer clock, fps cap, breathing, blink, drag and edge snap.
5. `OverlayService`: window, notification and actions, screen on/off, restart path.
6. `SpikeLog` + `MainActivity`: metrics, results summary, share.
7. README with build steps and the test protocol (below).

v2 (fixed run modes, per-run logs, conditions, mean-current summary, restart and frame-timing fixes) is planned in [tasks/plan.md](tasks/plan.md).

## 5. Test protocol (owner runs on the device)
Do each run on **vivo Y17s** (and Galaxy A23 if available). Rebuild and install the current app first.

**Run order (alternating, about 61 min each):**
1. **Baseline:** *Start baseline (no Bibi)*.
2. **Bibi 12:** *Start Bibi 12 fps*.
3. **Baseline:** *Start baseline (no Bibi)* again.
4. **Bibi Calm:** *Start Bibi Calm 2 fps*.

The runs don't have to be back to back; spread them over a day or two. At about 16 %/h with the screen on ([RESULTS.md](results/RESULTS.md)), each run uses roughly 16 points of battery, so four runs can't share one charge.

**Before every run (same steps each time):**
1. Charge to **85 %**, then unplug.
2. Open the spike app and leave the screen on in it for **10 min**, so battery temperature and current settle. Don't start a run yet.
3. Note the battery **start %** (it should be close to 85 %, after the settle drain).
4. Press the start button for this run's mode.

**During the run:** don't touch Bibi or the phone's screen. The app marks taps and drags as invalid. Drag and tap checks happen in a **separate short session** before the measured run (start a Bibi mode, try it, press *Stop*), not inside it.

**Ending the run:** after about **61 min**, press **Stop** (the button in the spike app or the notification's Stop; both write the same clean `stop` row). A run under 60.0 min reads invalid, and a run that doesn't end with *Stop* has no clean stop, which the summary treats as a possible process death. Comparing each Bibi run with the baseline before it, each starting from the same charge level after the same settle time, keeps battery level and temperature comparable.

**Conditions checklist (same for every run; write it down once per sequence):**
- [ ] Unplugged for the whole run.
- [ ] **Screen timeout at the maximum** the phone allows. A run where the screen goes off is invalid (the summary says so).
- [ ] **No touches during the measured hour.** Drag and tap tests happen in a separate short session. Touches redraw the window, add fps, and make the run invalid.
- [ ] Charged to 85 %, unplugged, 10 min settle in the app before *Start*.
- [ ] Brightness fixed, **auto-brightness off**, same level every run (the summary shows the brightness range).
- [ ] Same **refresh-rate setting** (e.g. 60 Hz or 90 Hz, Settings → Display) every run; the log records `refresh_hz`.
- [ ] Same foreground app and screen (e.g. a static web page in Chrome).
- [ ] Same Wi-Fi state, no downloads or updates running.
- [ ] **Battery saver off** (the log records `power_save`).
- [ ] Record vivo's **background power consumption** setting for the spike app (default or Allow); keep it the same in every run.
- [ ] Room temperature; phone not in a case if possible.

**After each run:** check the summary says `VALID for gate: yes`, then tap *Share log (CSV)* (it sends the newest run) and attach the file to issue #4 with the start % you noted. If it says `no`, repeat that run; don't average invalid runs in. Tap *Share exit reasons* at the end of the sequence.

**Optional second signal [VERIFY on vivo]:** with USB debugging, run `adb shell dumpsys batterystats --reset` right before *Start*, and `adb shell dumpsys batterystats` after *Stop*; note the per-app estimated power for the spike app. Whether vivo's build reports useful per-app figures is not known yet.

**Kill/restart check (separate, about 5 min, NOT a gate run):** start a Bibi mode, swipe the app away from Recents, then lock and unlock. Note whether Bibi survives or comes back, and share the run file and exit reasons. This run will read invalid (restart and screen off), which is expected.

## 6. Result template (goes into `results/RESULTS.md`, `docs/project_status.md` → Spike results, and issue #4)
| Device | Run | Mode | Start % | Duration | Mean current mA | %/h (current) | %/h (percent) | pct_per_ma | Avg fps | Max PSS MB | VALID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|

Then: Bibi 12 cost = mean mA (Bibi 12) − mean mA (baseline before it); Bibi Calm cost = mean mA (Bibi Calm) − mean mA (baseline before it). For each cost also give the relative figure: cost mA × `pct_per_ma` of that baseline run (%/h). Battery passes if the cost is ≤ 150 mA **and** the relative figure is ≤ 3 %/h; if only one of them passes, record both and mark the gate `[VERIFY]`. Stability passes if every run is valid (ended with *Stop*) with no kill.

## 7. Known limits of this spike
- v1 was built and run by the owner on the vivo Y17s (results in [results/RESULTS.md](results/RESULTS.md)). v2 has only been type-checked and its summary tested on synthetic logs on a JVM; the container can't reach Google's Maven repository, so the owner's Android Studio build is the first real build of v2.
- Rendering uses plain Views and a Canvas, matching PRD §17.5's guidance. A Compose-in-overlay host (PRD §12.5) is not tested here; that's P1 [OPEN].
- One device class only (mid-range MediaTek). Results are labeled single-device (PRD 0.12 item 4).
