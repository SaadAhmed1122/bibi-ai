# Spike S1 — results

## Run log

| Date | Device | Run | Log | Duration | Battery | Drain | Avg current | fps | PSS (median / max) | Kills / restarts | Thermal |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 2026-10-29 | vivo Y17s, Android 15, 6 GB | Owner run A, mode not logged (reported as Calm on) | not shared | ~60 min | 69 → 53 % | ~16 %/h | n/a | n/a | n/a | none reported | n/a |
| 2026-10-29 | vivo Y17s, Android 15, 6 GB | Run B, **Calm on** (2 fps) | [csv](2026-10-29_vivo-y17s_calm-on_53min.csv) | 53.0 min | 53 → 39 % | **15.8 %/h** | ~930 mA (720–1200) | 1.95 (target 2) | 75 / 116 MB | **0 / 0**, no exit reasons | 0 (none) |

## What run B shows
- **Stability: good so far.** 53 minutes in the foreground with the screen on: no crash, no OS kill, no restart, no recorded exit reason. That's not yet the full 60-minute logged run the gate asks for.
- **The renderer works as designed.** Calm mode held 117 frames/min (1.95 fps vs a 2 fps target). The minutes with 3.2–3.5 fps match the taps and drags (window moves redraw the view). The first minute (9.5 fps) ran at 12 fps before Calm was switched on.
- **Memory:** about 75 MB PSS steady, briefly 116 MB after the first taps. The two Bibi frames take about 0.66 MB.
- **Thermal:** status 0 (none) for the whole run.

## What it can't show yet
- **Bibi's own battery cost.** The phone drew about 930 mA with the screen on (about 16 %/h). The gate is Bibi's *extra* drain (SPEC.md §2), which needs a **baseline run without Bibi** under the same conditions. Not done yet.
- **Calm vs normal (12 fps).** Run B's log shows Calm switched **on** 15 s after start (`calm,true`), although it was reported as the Calm-off hour. The Calm button toggles, so it was probably pressed to turn Calm off while it was already off. Run A's mode wasn't logged. Both runs drained about the same (~16 %/h), which suggests frame rate isn't a big factor next to the screen [VERIFY with a logged 12 fps run].

## Device finding: the charge counter is not usable on this vivo
`charge_uah` only moves in fixed 28,510 µAh steps, exactly one per 1 % battery, which implies a 2,851 mAh battery. The Y17s is rated at 5,000 mAh (GSMArena). The counter is derived from the percentage, so it adds no precision here. Use **`current_ua`** (instantaneous current) and the percentage instead [VERIFY on other devices].

## Verdict so far
| Check | Status |
|---|---|
| Stable 1 h | **Likely pass.** 53 min clean in run B; needs one full 60-min logged run |
| Bibi's extra drain ≤ 3 %/h | **Not measured.** Needs a baseline run |

## Next runs needed
Protocol and conditions checklist: [SPEC.md §5](../SPEC.md#5-test-protocol-owner-runs-on-the-device). Rebuild the app first (v2: fixed run modes, one file per run, `VALID for gate` line). Order:
1. **Baseline:** *Start baseline (no Bibi)*.
2. **Bibi 12:** *Start Bibi 12 fps*.
3. **Baseline:** *Start baseline (no Bibi)*.
4. **Bibi Calm:** *Start Bibi Calm 2 fps*.

At ~16 %/h each run uses about 16 points, so the four runs don't fit one charge; spread them over a day or two. Before **every** run: charge to 85 %, unplug, 10 min with the screen on in the spike app to settle, note the start %, then press start. Screen timeout at max, no touching Bibi or the screen during the run (drag/tap checks in a separate short session), stop with the app's **Stop** button at about 61 min.

Run B doesn't count toward the gate: it was 53 min, had a screen-off, and its mode changed during the run. Separately, a short kill/restart check (not a gate run).
