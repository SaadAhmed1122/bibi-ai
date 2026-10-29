# S1 overlay spike v2: implementation plan

**Goal:** make the next vivo Y17s runs **decide the S1 battery gate**, with nothing left to misread.
**Scope:** `spikes/s1-overlay/` only (throwaway spike code). Issue [#4](https://github.com/SaadAhmed1122/bibi-ai/issues/4), PR [#10](https://github.com/SaadAhmed1122/bibi-ai/pull/10).
**Source:** the code-reviewer findings of 2026-10-29 and the first device results (`results/RESULTS.md`).

## Dependency graph

```
T1 run modes ──► T2 per-run logs ──► T3 conditions + 10 s sampling ──► T4 summary & validity ─┐
   │                                                                                           ├─► T7 docs ─► T8 final review
   └──────────► T5 restart & start-failure fixes ──────────────────────────────────────────────┤
T6 renderer timing & position fixes (independent; runs after T5 to avoid file clashes) ────────┘
```

- **T1 → T2:** each log file is named after the run mode.
- **T2 → T3 → T4:** the summary reads the new columns.
- **T5:** builds on T1's modes (baseline vs Bibi start failures).
- **T6:** touches `BibiView` only, but it runs in order with the others because every task shares one branch.

## Tasks (vertical: each one works end to end, from button to log to summary)

### T1. Run modes fixed at start
- **Change:** replace the Calm toggle with three buttons: **Start baseline**, **Start Bibi 12 fps**, **Start Bibi Calm 2 fps**. The mode is fixed for the whole run. Remove the notification's Calm action and the in-run Calm switch.
- **Accept when:**
  - The service takes the mode from the start intent.
  - Every log row's `mode` is one of `baseline`, `bibi12`, `bibiCalm`.
  - There's no way to change Calm during a run.
  - The status line shows the running mode.
- **Verify:** `tools/typecheck.sh`. Read through: no remaining reference to `ACTION_TOGGLE_CALM` / `ACTION_SET_CALM`.

### T2. One log file per run
- **Change:** each start creates `files/runs/s1_<yyyyMMdd-HHmm>_<model>_<mode>.csv`, with a `run_id` column. *Share* sends the latest run; the activity shows the latest run's summary. Exit reasons go to `files/exit_reasons.csv` (written from both the activity and the service `onCreate`) and are never deleted. Remove *Clear log*.
- **Accept when:** two runs give two files; exit reasons survive; sharing picks the newest run.
- **Verify:** type-check. Run the `Summary` logic on a synthetic file (T4 extends this).

### T3. Test conditions in every row + 10 s sampling
- **Change:** add columns:
  - `voltage_mv` and `batt_temp_dc` (from the sticky `ACTION_BATTERY_CHANGED` intent)
  - `brightness` and `brightness_mode` (`Settings.System`)
  - `power_save` (`PowerManager.isPowerSaveMode`)
  - `refresh_hz` (`Display.refreshRate`)

  Sample every **10 s** in all modes. Read PSS only on `sample` rows.
- **Accept when:** the header and rows have the new columns; the sample interval is 10 s.
- **Verify:** type-check; check the header and row column counts match.

### T4. Summary measures the gate and says whether the run counts
- **Change:**
  - Drop the charge-counter line.
  - Report the **time-weighted mean |current| (mA)** from `sample` rows only, and %/h = mA ÷ 5000 × 100 (rated capacity [VERIFY]).
  - Print **"VALID for gate: yes/no"**, with reasons when no: Calm changed, hidden, screen off, charging, restart or restart blocked, under 60 min, mixed modes.
  - Show the conditions (brightness, refresh rate, power save, temperature range).
- **Accept when:** a synthetic valid 60-min log reads **valid**; logs with screen_off, charging or 50 min read **invalid** with the right reason.
- **Verify:** compile `Summary.kt` and run it with a JVM harness on synthetic logs (no Android needed).

### — Checkpoint A (after T4)
Type-check passes; the synthetic tests pass; commits are pushed. Report to the owner.

### T5. Restart and start-failure fixes
- **Change:**
  - Bibi mode **fails the start** (logged `start_blocked: no_overlay_permission`) when the overlay permission is missing.
  - `addView` is inside `try`, catching `BadTokenException` / `WindowManager` errors.
  - On a restart, call `startForeground` **after Bibi's first draw**, so the overlay is visible first (Android 15 exemption [VERIFY on device]). Keep a fallback timer so the service still calls `startForeground` within Android's time limit.
  - A baseline restart logs `baseline_restart` / `baseline_restart_blocked`.
- **Accept when:** these code paths exist; no unguarded `addView`; each label is distinct.
- **Verify:** type-check; read through the order of events.

### T6. Renderer timing and position fixes
- **Change:**
  - Schedule frames from `frameTimeNanos`, so 12 fps really means ~12 fps.
  - `snapAnimator.cancel()` doesn't save a half-finished position.
  - Clamp the saved x/y to the current screen when the overlay is added.
- **Accept when:** the frame due-time logic doesn't add the interval on top of vsync; cancel skips `onMoved`; the clamp is applied.
- **Verify:** type-check.

### T7. Docs
- **Change:** `SPEC.md` §2/§5/§6:
  - The gate is measured as the mean-current delta (mA).
  - Alternating run order (baseline → Bibi 12 → baseline → Bibi Calm, each starting within ±5 % charge of the others).
  - Screen timeout at max; no touches during measurement.
  - A conditions checklist (vivo background-power and refresh-rate settings).
  - The results template uses mean current.

  Update `README.md` for the new buttons and log files, plus `docs/project_status.md` and `docs/changelog.md`.
- **Accept when:** the docs match the T1–T6 behavior; no placeholders; `[VERIFY]` where unmeasured.

### T8. Final review
- A five-axis review of the whole diff, with fixes for anything important. Then mark PR #10 ready for the owner's device runs (still a draft until results exist).

## Who does what
| Task | Agent |
|---|---|
| T1–T6 | `android-engineer` (one task per run, one commit each) |
| T7 | `docs-keeper` |
| T8 | `agent-skills:code-reviewer` |
| Checkpoints, commits review, owner updates | main Claude |

## Risks
- **No APK build here** (Google Maven is unreachable): only type-checking and JVM-harness tests. The owner's build in Android Studio is the real check.
- **Android 15 restart rule:** "after first draw" is the documented condition's best reading; only a device test proves it [VERIFY].
