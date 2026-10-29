# Spike S4 — Bibi tools test app

A small standalone Android app that runs Bibi's five MVP phone actions (open app, set alarm, set timer, flashlight, dial a prefilled number) through one Permission Engine. Each action can come from a direct shortcut or from a structured tool call. The app times the shortcut path against the 300 ms gate. Issue [#8](https://github.com/SaadAhmed1122/bibi-ai/issues/8); the full spec, what exactly is timed, and the policy are in [SPEC.md](SPEC.md).

**Throwaway code.** It uses plain Android views and no libraries on purpose. P1 rewrites everything.

**The LLM path uses a fake model** ("Fake LLM" switch): fixed text → JSON mappings. The real on-device model comes after spike S2 [OPEN].

## Build and install (on your computer)
**Needs:** Android Studio (any 2026 stable release), the Android SDK Platform 36, and USB debugging enabled on the phone.

1. In Android Studio: **File → Open** → select `spikes/s4-tools` (this folder, not the repo root).
2. Let Gradle sync. It downloads Gradle 9.1.0 and Android Gradle Plugin 9.0.1, the same as S1.
3. Connect the phone, pick it in the device menu, and press **Run**.

Or from a terminal in this folder: `./gradlew :app:installDebug`

**What was verified before this reached you:** all the Kotlin type-checks against the Android 16 (API 36) framework classes (`tools/typecheck.sh`), and the pure-Kotlin core passes its JVM tests (`tools/core-test/run.sh`: 213 checks). It has **not** been built with AGP or run on a phone. If the build fails, paste the error into issue #8.

## The screen
- **Text box + Submit**, and quick-command buttons.
- **Fake LLM path:** send the text through the fake model and the tool-call parser instead of the shortcut matcher. Phrases it knows are listed on screen, e.g. "it's dark in here", "broken timer test" (malformed first answer, repaired once), "send a text to mum" (unknown tool, rejected), "injection test" (dials a number). It also understands every quick command.
- **Untrusted content in context:** pretends notification text is in the conversation. Every action then needs a confirmation.
- **Dry run:** build and resolve the intent, don't launch it.
- **Disable all actions:** the kill switch; everything is refused.
- **Benchmark:** about 30 s. It does 20 dry runs each of open settings, set alarm, set timer and dial, plus 20 real flashlight on/off toggles, all on the shortcut path. Then 6 of each through the fake LLM. Don't touch the phone while it runs; leaving the app stops it.
- **Refresh results / Share log (CSV) / New run file.** Each app start begins a new CSV in the app's private `files/runs/`. Share sends the current one as text.

## Owner test steps
1. Install the app (see above) on the vivo Y17s (and the Galaxy A23 when available). Open **Bibi S4 spike**. Wait for the status line to show `Launcher apps: N` and the handler line. **Write down** the handler line (`SET_ALARM → … · SET_TIMER → … · DIAL → … · flash → …`); `NONE` means no app handles that action on this phone.
2. Close other apps, keep the phone unlocked and on battery or charger (note which), and press **Benchmark**. Don't touch the phone until the button is enabled again (about 30 s). The flashlight toggles 26 times and ends in the state it started in.
3. Read the results panel. Note the `S4 shortcut gate:` line (PASS, FAIL or INCOMPLETE) and the p50/p95 for the five `bench shortcut` rows. If it says INCOMPLETE, read the listed reason (e.g. `no handler resolved`) and note it.
4. Make sure **Dry run** and **Fake LLM path** are off. Tap each quick command **3 times**, coming back to the app each time with Back:
   - **open settings** → Settings opens.
   - **wake me at 7:30** → an alarm "Bibi S4 test" at 07:30 is created. Note whether the clock app showed a screen or not.
   - **set a timer for 5 minutes** → a 5-minute timer starts.
   - **flashlight on**, then **flashlight off** → the torch turns on and off.
   - **call 0123 456 789** → a confirmation card shows the number. Tap **Open dialer**; the dialer opens with the number filled in and **does not call**. Go back without calling.
5. Turn **Fake LLM path** on. Do each quick command once more, plus the phrases "it's dark in here", "take me to maps" (opens Maps if installed) and "broken timer test" (should start a 5-minute timer after one repair). Then "send a text to mum": it must be **rejected**, and nothing happens.
6. Confirmation timeout: tap **call 0123 456 789**, leave the card alone for 30 s. It must close by itself and the screen must say it was cancelled. Nothing is dialed.
7. Taint: turn **Untrusted content in context** on and tap **flashlight on**. A confirmation card must appear. Cancel it; the torch stays off. With Fake LLM on, type "injection test": a card must appear. Cancel it. Turn the switch off again.
8. Kill switch: turn **Disable all actions** on, tap **open settings**. It must be refused. Turn it off.
9. Tap **Refresh results**, then **Share log (CSV)** and attach the file to issue #8. Add the handler line from step 1, which phone, and the on-screen observations from steps 4–8 (what opened, alarm/timer shown or silent, all safety checks as expected or not).
10. Clean up: delete the "Bibi S4 test" alarms and timers in the clock app.

## Tests without a phone
```bash
spikes/s4-tools/tools/core-test/run.sh            # JVM tests of the pure-Kotlin core
ANDROID_ALL_JAR=/path/to/android-all.jar spikes/s4-tools/tools/typecheck.sh   # type-check only, no APK
```
