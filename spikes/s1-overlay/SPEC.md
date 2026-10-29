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

**How "≤ 3% per hour" is measured [VERIFY with owner].** A phone with its screen on drains far more than 3%/h by itself, so the gate can only mean **Bibi's extra drain**. The protocol therefore runs a **baseline hour without Bibi** and a **Bibi hour** under the same conditions:

`Bibi cost (%/h) = drain with Bibi − baseline drain`

The app logs both the battery percentage and the more precise charge counter (µAh) where the device supports it.

**If it fails:** Bubble/Edge mode only, lower frame rate, calm mode as the default (PRD 0.7). The decision is recorded in `docs/project_status.md`.

## 3. What the spike app does
- **Main screen** (plain Android views, no libraries): grant the overlay permission, grant notifications, start/stop Bibi, toggle Calm mode, show live results, share the log, clear the log.
- **`OverlayService`**: a foreground service of type `specialUse` (Android 14+), with a low-importance notification offering Hide/Show, Calm and Stop actions.
- **Bibi overlay**: a 96 dp window (`TYPE_APPLICATION_OVERLAY`, not focusable, touches outside pass through):
  - draws pre-rendered Bibi frames (idle + blink, from `docs/design/character/states/`) on a `Canvas`, driven by a `Choreographer` clock
  - **12 fps** normally, **2 fps** in Calm mode, **no drawing while the screen is off** (PRD §12.10, §17.5)
  - a gentle breathing scale (3.5 s cycle) and a random blink every 3–6 s (Calm: every 8 s) (PRD §20.13)
  - drag to move, release to snap to the nearest side edge (~280 ms), position remembered; tap says hi
- **Metrics log** (`files/s1_log.csv`, every 60 s plus events):
  - time, battery %, charge counter (µAh), current (µA), charging state
  - screen on/off, thermal status, frames drawn and average fps, process PSS (KB), Calm on/off
  - events: start, stop, restart, start blocked, screen on/off
  - on every launch: Android's recorded exit reasons for previous runs (`ApplicationExitInfo`), so OS kills are visible
- **Restart check**: the service is `START_STICKY`. If Android restarts it after a kill, it adds the overlay window **before** calling `startForeground()`, because apps targeting Android 15+ may only start a foreground service from the background while they have a visible overlay window (developer.android.com, "Restrictions on starting a foreground service from the background"). Success or failure is logged as `restart` or `restart_blocked`.

## 4. Plan (one commit per task)
1. Gradle project skeleton (AGP 9.0.1 with built-in Kotlin, Gradle 9.1.0, compileSdk/targetSdk 36, minSdk 34, no other dependencies).
2. Manifest: permissions, `specialUse` service with the subtype property, launcher activity.
3. Bibi frames: render `idle.svg` and `idle_blink.svg` to 288 px PNGs (96 dp at 3x).
4. `BibiView`: Canvas renderer, Choreographer clock, fps cap, breathing, blink, drag and edge snap.
5. `OverlayService`: window, notification and actions, screen on/off, restart path.
6. `SpikeLog` + `MainActivity`: metrics, results summary, share.
7. README with build steps and the test protocol (below).

## 5. Test protocol (owner runs on the device)
Do each run on **vivo Y17s** (and Galaxy A23 if available). Record the device, Android version and battery-optimization setting.

**Common conditions:**
- battery between 90% and 60% for the whole run
- unplugged
- brightness fixed at 50% (auto-brightness off)
- screen timeout 30 min, tap the screen now and then
- Wi-Fi on, no downloads
- the same foreground app throughout (for example a static web page in Chrome)
- room temperature, phone not in a case if possible

**Runs:**
1. **Baseline (60 min):** Bibi stopped. Press *Clear log*, *Start baseline*, then *Stop* after 60 min.
2. **Bibi normal (60 min):** *Clear log*, *Start Bibi*, drag Bibi a few times, then use the phone normally in the chosen app. *Stop* after 60 min.
3. **Bibi Calm (60 min):** same as run 2 with Calm mode on.
4. **Kill/restart check (5 min):** with Bibi running, swipe the app away from Recents, then lock and unlock. Note whether Bibi survives or comes back.

After each run, tap *Share log* and send the CSV (or paste it into the issue).

## 6. Result template (goes into `docs/project_status.md` → Spike results, and issue #4)
| Device | Run | Duration | Drain %/h | Charge µAh/h | Avg fps | Max PSS MB | Kills / restarts | Verdict |
|---|---|---|---|---|---|---|---|---|

## 7. Known limits of this spike
- It hasn't been built or run yet. The container used to write it can't reach Google's Maven repository, so the first build happens in Android Studio on the owner's machine.
- Rendering uses plain Views and a Canvas, matching PRD §17.5's guidance. A Compose-in-overlay host (PRD §12.5) is not tested here; that's P1 [OPEN].
- One device class only (mid-range MediaTek). Results are labeled single-device (PRD 0.12 item 4).
