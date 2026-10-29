# S1 spike v2: todo

Plan: [plan.md](plan.md) · Issue #4 · Branch `spike/S1-overlay`

- [x] T1 Run modes fixed at start (3 start buttons; no Calm toggle) — android-engineer
- [x] T2 One log file per run + run_id + persistent exit reasons — android-engineer
- [x] T3 Conditions columns + 10 s sampling — android-engineer
- [x] T4 Summary: mean current, %/h, VALID for gate — android-engineer
- [x] Checkpoint A: type-check + synthetic summary tests + push + owner update
- [x] T5 Restart and start-failure fixes (+ T1 follow-up: bad-mode start must call startForeground before stopSelf) — android-engineer
- [x] T6 Renderer timing and position fixes — android-engineer
- [x] T7 SPEC/README/status/changelog — docs-keeper
- [ ] T8 Final review — agent-skills:code-reviewer

## Checkpoint A notes (2026-10-29)
- JVM tests: ALL PASS. Type-check: OK.
- Real vivo log through the new Summary: "VALID for gate: no — shorter than 60 min (53.0 min); screen off during run" (correct). Mean current 928 mA.
- Current-based %/h (18.6 at 5000 mAh) vs percent-based (15.8 %/h) differ by ~15%: current_ua scale or usable capacity is off on this device [VERIFY]. The gate compares Bibi vs baseline on the same phone, so the delta still holds.
