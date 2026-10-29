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

**What was verified before this reached you:** the Kotlin sources type-check against the Android 16 framework classes, and the results math was tested on a fake log. It has **not** been built with AGP or run on a device yet. If the first build fails, paste the error into the issue.

## Using the app
1. Tap **1. Allow drawing over other apps** and enable it for "Bibi S1 spike".
2. Tap **2. Allow notifications**.
3. Tap **Start Bibi**. Bibi appears on the right edge. Drag it, release it (it snaps to the nearest side), tap it (it says hi).
4. The notification has **Hide**, **Calm** and **Stop**.

## Running the measurements
Follow [SPEC.md §5](SPEC.md#5-test-protocol-owner-runs-on-the-device): a 60-minute **baseline** run (no Bibi), a 60-minute **Bibi** run, a 60-minute **Calm** run, and a short kill/restart check, all under the same conditions. After each run, tap **Share log (CSV)** and attach it to issue #4.

The results panel shows each run's duration, battery drain (%/h and mAh/h from the charge counter), average fps, peak memory, events, and any OS kill reasons.

**Gate:** Bibi's cost = the Bibi run's drain − the baseline run's drain ≤ 3 %/h, plus 60 minutes with no kill.

## vivo notes
vivo's Funtouch/OriginOS limits background apps aggressively (PRD §12.4). Run everything with the **default** battery settings first. If Bibi gets killed, note it, then repeat with *Settings → Battery → Background power consumption management → Bibi S1 spike → Allow* (menu names vary by version), and record both results.
