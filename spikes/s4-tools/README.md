# Spike S4 — Bibi tools test app

A small standalone Android app that runs Bibi's five MVP phone actions (open app, set alarm, set timer, flashlight, dial a prefilled number) through one Permission Engine. Each action can come from a direct shortcut or from a structured tool call. The app times the shortcut path against the 300 ms gate. Issue [#8](https://github.com/SaadAhmed1122/bibi-ai/issues/8); the full spec, what exactly is timed, and the policy are in [SPEC.md](SPEC.md).

**Throwaway code.** Plain Android views. P1 rewrites everything.

**Two LLM paths:** the **fake LLM** ("Fake LLM" switch, fixed text → JSON mappings) stays as the baseline. The **real LLM path** ("Real LLM path" switch) runs after a shortcut miss: MediaPipe Decision Maker picks one tool (or none), Gemma 3 1B on LiteRT-LM is shown only that tool and returns a tool call, and the call goes through the same parser, validator and Permission Engine. Its numbers are provisional until S2 is decided [OPEN] (Decisions 2026-10-30 in `docs/project_status.md`). The models are **not** in the APK; you push them (below).

## Build and install (on your computer)
**Needs:** Android Studio (any 2026 stable release), the Android SDK Platform 36, and USB debugging enabled on the phone.

1. In Android Studio: **File → Open** → select `spikes/s4-tools` (this folder, not the repo root).
2. Let Gradle sync. It downloads Gradle 9.1.0 and Android Gradle Plugin 9.0.1, the same as S1.
3. Connect the phone, pick it in the device menu, and press **Run**.

Or from a terminal in this folder: `./gradlew :app:installDebug`

**What was verified before this reached you:** `./gradlew :app:assembleDebug` builds (AGP 9.0.1), the merged manifest has only `SET_ALARM`, and the pure-Kotlin core passes its JVM tests (`tools/core-test/run.sh`: 439 checks). The real-LLM path has **not** run on a phone yet. If anything fails, paste the error into issue #8.

## Models (real LLM path only)
Two files, pushed into the app's folder on shared storage (install the app first so the folder exists; the screen prints the same path):
```bash
adb shell mkdir -p /sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s4/files/models
adb push embeddinggemma-2-text-270m.litertlm /sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s4/files/models/
adb push gemma3-1b-it-int4.litertlm         /sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s4/files/models/
```
- `embeddinggemma-2-text-270m.litertlm` is the Decision Maker model (164,626,432 bytes per the Decision). `gemma3-1b-it-int4.litertlm` is the same file S2 uses.
- Then tap **Rescan model files**: both lines must say `present`.
- Uninstalling the app deletes this folder, so push again after a reinstall from scratch. `installDebug` over an existing install keeps it.
- If `adb push` is refused for that folder [VERIFY on vivo], tell the parent session; S2's README has the `run-as` fallback, but this app only reads the external folder.

## The screen
- **Text box + Submit**, and quick-command buttons.
- **Real LLM path:** the shortcut matcher still goes first. Only text it doesn't match goes to the Decision Maker and Gemma. The first request loads the models (several seconds [VERIFY]); **Load models** does it in advance. The model status line shows the push folder, whether each file is present, and the load time and memory (PSS).
- **Fake LLM path:** send the text through the fake model and the tool-call parser instead of the shortcut matcher. Phrases it knows are listed on screen, e.g. "it's dark in here", "broken timer test" (malformed first answer, repaired once), "send a text to mum" (unknown tool, rejected), "injection test" (dials a number). It also understands every quick command.
- **Untrusted content in context:** pretends notification text is in the conversation. Every action then needs a confirmation.
- **Dry run:** build and resolve the intent, don't launch it.
- **Disable all actions:** the kill switch; everything is refused.
- **Benchmark:** about 30 s. It does 20 dry runs each of open settings, set alarm, set timer and dial, plus 20 real flashlight on/off toggles, all on the shortcut path. Then 6 of each through the fake LLM. Don't touch the phone while it runs; leaving the app stops it.
- **LLM benchmark:** 110 fixed prompts (20 per action, 10 that ask for nothing) through the real LLM path, as a **dry run**: each one stops at the Permission Engine decision, so nothing opens, no alarm, timer, dialer or torch. It takes minutes [VERIFY]; press the button again to stop. Leaving the app stops it too.
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

**Real LLM path (after steps 1–10; needs the two model files, see Models):**

11. Push both models, tap **Rescan model files**, then **Load models**. Wait for `Models: loaded: Decision Maker … ms, Gemma … ms, PSS … MB` and **write it down**. If it says `load FAILED`, copy the message into issue #8 and stop here.
12. Close other apps, press **LLM benchmark**, and don't touch the phone until the button reads "LLM benchmark …" again. Nothing should open or light up; if anything does, stop and report it. Then tap **Refresh results** and note the LLM table and the `Peak PSS` line. The `S4 shortcut gate:` line must be the same as after step 3.
13. Turn **Real LLM path** on (Dry run off) and try once each: "I need to get up at 6:45 tomorrow" (alarm 06:45), "count down ten minutes for the eggs" (10-minute timer), "it's too dark to see anything" (torch on), "bring up google maps for me" (Maps), "get 020 7946 0018 up on the dialer" (confirmation card; tap **Open dialer**, the dialer shows the number and **does not call**; go back without calling), and "tell me a joke" (no action). Note for each what happened on screen and the result line. "open settings" must still go through the shortcut (instant, no model).
14. With **Real LLM path** on, turn **Untrusted content in context** on and say "it's too dark to see anything": a confirmation card must appear; cancel it. Turn it off. Then turn **Disable all actions** on and repeat: it must be refused. Turn it off.
15. **Share log (CSV)** and attach it to issue #8 with the notes from steps 11–14.

## Tests without a phone
```bash
spikes/s4-tools/tools/core-test/run.sh            # JVM tests of the pure-Kotlin core
ANDROID_ALL_JAR=/path/to/android-all.jar EXTRA_CP=<litertlm, tasks-decision, tasks-core, coroutines jars> spikes/s4-tools/tools/typecheck.sh   # type-check only, no APK
```
