# Spike S1 — results

## Run log

| Date | Device | Run | Log | Duration | Battery | Drain | Avg current | fps | PSS (median / max) | Kills / restarts | Thermal |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 2026-10-29 | vivo Y17s, Android 15, 6 GB | Owner run A, mode not logged (reported as Calm on) | not shared | ~60 min | 69 → 53 % | ~16 %/h | n/a | n/a | n/a | none reported | n/a |
| 2026-10-29 | vivo Y17s, Android 15, 6 GB | Run B, **Calm on** (2 fps) | [csv](2026-10-29_vivo-y17s_calm-on_53min.csv) | 53.0 min | 53 → 39 % | **15.8 %/h** | ~930 mA (720–1200) | 1.95 (target 2) | 75 / 116 MB | **0 / 0**, no exit reasons | 0 (none) |
| 2026-10-30 20:48 | vivo Y17s | Practice, **Bibi 12**, not a gate run | [csv](2026-10-30_vivo-y17s_bibi12-practice-invalid.csv) | 16 min (taps, drags, plugged in mid-run) | 85 → 84 % | n/a | n/a | 12 | n/a | 0 / 0 | 0 |
| 2026-10-30 21:15 | vivo Y17s | **Gate 1: baseline** (no Bibi) | [csv](2026-10-30_vivo-y17s_baseline-1.csv) | 66.0 min | 86 → 83 % | 2.73 %/h | **148.4 mA** | — | 86 / 92 MB (service only) | 0 / 0 | 0 |
| 2026-10-30 22:37 | vivo Y17s | **Gate 2: Bibi 12 fps** | [csv](2026-10-30_vivo-y17s_bibi12.csv) | 89.3 min unplugged | 87 → 82 % | 3.29 %/h | **176.2 mA** | 12.00 | 97 / 100 MB | 0 / 0 | 0 |
| 2026-10-31 00:15 | vivo Y17s | **Gate 3: baseline** (no Bibi) | [csv](2026-10-31_vivo-y17s_baseline-2.csv) | 62.5 min | 85 → 83 % | 1.92 %/h | **150.9 mA** | — | 89 / 97 MB (service only) | 0 / 0 | 0 |
| 2026-10-31 01:25 | vivo Y17s | **Gate 4: Bibi Calm 2 fps** | [csv](2026-10-31_vivo-y17s_bibi-calm.csv) | 62.2 min | 85 → 82 % | 2.89 %/h | **162.4 mA** | 2.00 | 90 / 95 MB | 0 / 0 | 0 |

## Gate verdict (runs 1–4, 2026-10-30/31): **PASS on the vivo Y17s**

Conditions in all four runs, read from the logs: unplugged, screen on the whole run, brightness 15 (manual), refresh rate 60 Hz (peak and min refresh set to 60 over adb before the runs), battery saver off, thermal status 0, battery 26.5–29.1 °C. No taps or drags logged during any gate run. Every run ended with **Stop**; `dumpsys activity exit-info` lists no exit for the app (no kill, no crash).

| Check | Bibi 12 vs baseline 1 | Bibi Calm vs baseline 2 | Gate | |
|---|---|---|---|---|
| Bibi's extra current | 176.2 − 148.4 = **27.8 mA** | 162.4 − 150.9 = **11.5 mA** | ≤ 150 mA | PASS |
| Relative (cost × baseline `pct_per_ma`) | 27.8 × 0.0184 = **0.51 %/h** | 11.5 × 0.0127 = **0.15 %/h** | ≤ 3 %/h | PASS |
| Stable ≥ 60 min, clean Stop, no kill | 89 min | 62 min | yes | PASS |

Means use the unplugged samples only (current < 0). In runs 2 and 3 the phone was plugged in a few seconds to 2 minutes before Stop; those samples are left out. The two baselines agree within 2.5 mA, so the phone's own drain was steady.

**Caveats [VERIFY]:**
- **Settle step changed.** SPEC §5 asks for 10 min in the app before Start. In run 1 the owner pressed Start about 1 min after unplugging, so runs 2–4 did the same, to keep the pairs comparable. This is recorded as a protocol change, not a skipped step.
- **Low brightness.** At brightness 15 the whole phone drew about 150 mA. At the default brightness of the 2026-10-29 runs it drew about 930 mA. Bibi's cost in mA should not depend much on brightness, but the %/h figure would be smaller at higher brightness.
- The percent-based %/h comes from 1 % steps over about an hour, so it is coarse (±1 %/h). The mA figures are from `current_ua`.
- One phone (vivo Y17s, not the floor device). The Galaxy A23 is untested. The kill/restart check (SPEC §5, separate short test) is not done.

## Earlier runs (2026-10-29): what run B showed
- **Stability: good so far.** 53 minutes in the foreground with the screen on: no crash, no OS kill, no restart, no recorded exit reason. That's not yet the full 60-minute logged run the gate asks for.
- **The renderer works as designed.** Calm mode held 117 frames/min (1.95 fps vs a 2 fps target). The minutes with 3.2–3.5 fps match the taps and drags (window moves redraw the view). The first minute (9.5 fps) ran at 12 fps before Calm was switched on.
- **Memory:** about 75 MB PSS steady, briefly 116 MB after the first taps. The two Bibi frames take about 0.66 MB.
- **Thermal:** status 0 (none) for the whole run.

## Device finding: the charge counter is not usable on this vivo
`charge_uah` only moves in fixed 28,510 µAh steps, exactly one per 1 % battery, which implies a 2,851 mAh battery. The Y17s is rated at 5,000 mAh (GSMArena). The counter is derived from the percentage, so it adds no precision here. Use **`current_ua`** (instantaneous current) and the percentage instead [VERIFY on other devices]. In the 2026-10-30/31 logs the counter moved about 47,000 µAh per 1 % instead, so its step size is not stable either; the gate uses `current_ua`.

## Next
- Optional: kill/restart check (SPEC §5), not a gate run.
- Galaxy A23, if it becomes available (open item 10).

Run B doesn't count toward the gate: it was 53 min, had a screen-off, and its mode changed during the run.
