# Spike S1 — Bibi overlay test app

A small standalone Android app that floats Bibi over other apps and logs battery, frame rate, memory and OS kills, so we can decide gate S1 with numbers. Issue [#4](https://github.com/SaadAhmed1122/bibi-ai/issues/4); full spec, plan and protocol in [SPEC.md](SPEC.md).

**Throwaway code.** It uses plain Android views and no libraries on purpose. P1 rewrites everything.

## Build and install (on your computer)

**Needs:** Android Studio (any 2026 stable release), the Android SDK Platform 36 (Studio offers to install it), and USB debugging enabled on the phone.

1. In Android Studio: **File → Open** → select `spikes/s1-overlay` (this folder, not the repo root).
2. Let Gradle sync. It downloads Gradle 9.1.0 and Android Gradle Plugin 9.0.1.
   - If Studio suggests upgrading AGP, it's fine to accept.
   - The wrapper has no `distributionSha256Sum` yet, because the checksum couldn't be fetched where this was written. To pin it, add the value for `gradle-9.1.0-bin.zip` from https://gradle.org/release-checksums/ to `gradle/wrapper/gradle-wrapper.properties`.
3. Connect the vivo Y17s, pick it in the device menu, and press **Run**.

Or from a terminal in this folder:
```bash
./gradlew :app:installDebug
```

**What was verified before this reached you:** v1 was built and run on the vivo Y17s. The v2 changes (fixed run modes, per-run logs, the new summary) type-check against the Android 16 framework classes, and the summary was tested on synthetic logs and the real vivo log. v2 has **not** been built with AGP or run on a device yet. If the build fails, paste the error into the issue.

## Using the app
1. Tap **1. Allow drawing over other apps** and enable it for "Bibi S1 spike".
2. Tap **2. Allow notifications**.
3. Start **one** run with one of the three buttons. The mode is fixed until **Stop**:
   - **Start baseline (no Bibi):** same service and logging, no overlay.
   - **Start Bibi 12 fps:** Bibi appears on the right edge and animates at 12 fps.
   - **Start Bibi Calm 2 fps:** the same at 2 fps, no breathing.
4. To try Bibi, drag it, release it (it snaps to the nearest side) and tap it (it says hi). Do this in a **separate short session** (start, try, *Stop*), never during a measured run: taps and drags make that run invalid.
5. The notification shows the mode and has **Hide/Show** (Bibi modes) and **Stop**. Hiding Bibi makes the run invalid for the gate.

**Files (in the app's private storage):**
- `files/runs/s1_<yyyyMMdd-HHmm>_<model>_<mode>.csv`: one file per run, a sample row every 10 s plus events, including the test conditions (brightness, refresh rate, battery saver, battery temperature and voltage). Columns are listed in [SPEC.md §3](SPEC.md#3-what-the-spike-app-does).
- `files/exit_reasons.csv`: Android's recorded process exits (OS kills, crashes) and blocked starts, kept across runs.

**Buttons for results:**
- **Refresh results:** recomputes the summary of the newest run.
- **Share log (CSV):** sends the **newest** run file (what's written so far, if it's still running).
- **Share exit reasons:** sends `exit_reasons.csv`.

## Running the measurements
Follow [SPEC.md §5](SPEC.md#5-test-protocol-owner-runs-on-the-device): four runs of about 61 minutes in the order **baseline → Bibi 12 → baseline → Bibi Calm**, spread over a day or two if needed. Before **every** run: charge to 85 %, unplug, leave the screen on in the spike app for 10 min to settle, note the start %, then press the start button. Screen timeout at max, no touching Bibi or the screen during the hour (taps and drags make the run invalid), the same conditions every time, and end each run with **Stop** (no clean stop reads invalid). The kill/restart check is a separate short test, not a gate run. After each run, tap **Share log (CSV)** and attach it to issue #4.

**Reading the summary:**
- **Mean current** (mA, time-weighted over sample rows) is the number the gate uses. **Drain (current)** converts it to %/h at 5000 mAh [VERIFY capacity]; **Drain (pct)** is the coarse 1 %-step figure.
- **Conditions** lists brightness, refresh rate, battery saver, temperature and voltage. Compare them between runs.
- **`VALID for gate: yes`** means the run counts. **`no`** lists why: shorter than 60 min, screen off, charging, a restart or blocked start, an overlay window error, mixed modes, Bibi hidden, Bibi touched (tap or drag), or no clean stop. Repeat an invalid run.

- **`pct_per_ma`** (runs ≥ 60 min) = percent-based %/h ÷ mean mA; it calibrates the vivo's current reading, which may be about 15 % off [VERIFY].

**Gate:** Bibi's cost = mean current of the Bibi run − mean current of the baseline run. It must be ≤ 150 mA **and** cost × the baseline's `pct_per_ma` must be ≤ 3 %/h. If the two disagree, record both and mark the gate `[VERIFY]`, not pass. Plus every run valid, ended with **Stop**, with no kill.

## vivo notes
vivo's Funtouch/OriginOS limits background apps aggressively (PRD §12.4). Run everything with the **default** battery settings first. If Bibi gets killed, note it, then repeat with *Settings → Battery → Background power consumption management → Bibi S1 spike → Allow* (menu names vary by version), and record both results.
