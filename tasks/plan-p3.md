# Implementation Plan: P3 closed test and P4 decision

## Overview
Build P3 and P4 from [SPEC-P3.md](../SPEC-P3.md) and its five module specs. The P2 plan's last item, the 1-hour P2 gate (#90), and the owner's Play items (privacy policy URL, FGS video, Drive link, signed upload) still come first. P3 testing starts with the upload.

**Tasks are tracked as GitHub Issues** in milestone "P3 — Closed test", with the label `p3`. Task N = issue N+125 (#126–#137). One task = one branch `feature/<issue>-<name>` (or `docs/…`) = one PR.

## Architecture decisions
- **The counters ship in the first closed-test build.** Testers' first 14 days are the gate window, so the build the owner uploads should be 0.3.0-p3 with Tasks 1–3, not 0.2.0-p2. If the owner uploads 0.2.0-p2 first, the first week of the gate can't be measured.
- **Counters live in the main process** and read exits from the existing `ExitInfoCollector`. No new process, no new permission.
- **The report is formatted from numbers only.** The formatter takes a data class of `Int`, `Boolean` and date fields, so no text can reach it by type. A test with marker strings checks the same thing.
- **Report tool is a stand-alone script** in `tools/p3-report/`. Tester reports stay out of git.
- **Major merges wait for the owner:** Task 2 (changes the privacy policy and the Privacy screen) and Task 10 (making the repository public). The other task PRs are merged after tests, lint and a check on the vivo in both orientations.

## Task list

### Phase 1: The closed-test build
- [x] **Task 1 (#126): Usage counters** (SPEC-test-report criterion 1, tests first). Session tracker, crash attribution, active days, feature counts, store. **M**
- [x] **Task 2 (#127): Share usage numbers + privacy** (SPEC-test-report criteria 2–3). About screen entry with preview, share sheet, Privacy screen line, Delete everything step, policy text in the app and `docs/privacy-policy.md`. **M**
- [x] **Task 3 (#128): Send feedback** (SPEC-feedback criteria 1–2). Mailto with prefilled body to the owner's address. **S**
- [x] **Task 4 (#129): Tester invite + recruiting plan** (SPEC-feedback criterion 3). `docs/tester-invite.md`, LinkedIn call for testers in docs/linkedin/posts.md. **S**
- [x] **Task 5 (#130): 0.3.0-p3 release check.** Version bump, `play-console.md` and the Data safety answers rechecked for the new counters, TESTING.md steps for the counters. **S**

### Checkpoint F: closed-test build ready
- [ ] On the vivo, both orientations: three sessions and a forced crash give the right numbers; share and feedback work; no `INTERNET` permission. The owner tries it, then signs and uploads 0.3.0-p3 to the closed-test track.

### Phase 2: Closed test (14+ days with ≥ 12 testers opted in)
- [ ] **Task 6 (#131): Report tool** (SPEC-stability criterion 1). **S**
- [ ] **Task 7 (#132): Release crash-log share decision.** Owner picks: add "Share crash log" to release builds, or rely on tester descriptions. Built only if chosen. **S**
- [ ] **Task 8 (#133): Triage and 0.3.x fixes.** Rolling; each bug is its own `p3` issue. A weekly status line goes in `docs/project_status.md`.
- [ ] **Task 9 (#134): P3 gate results** (SPEC-stability criteria 2–3). `docs/p3-results.md` and a Decision line.

### Checkpoint G: P3 exit
- [ ] Crash-free sessions ≥ 99 % and the retention signal are recorded, or a dated Decision explains a miss.

### Phase 3: P4
- [ ] **Task 10 (#135): Public-release audit** (SPEC-writeup criterion 1). The owner flips the visibility after the audit. **M**
- [ ] **Task 11 (#136): README, LinkedIn article, portfolio package** (SPEC-writeup criteria 2–3). **M**
- [ ] **Task 12 (#137): Decision memo** (SPEC-decision). It asks the owner for funding criteria first. **S**

## Risks
| Risk | Mitigation |
|---|---|
| Fewer than 12 testers stay opted in for 14 days | Recruit 14 (Task 4); restart the 14-day window if the count drops [VERIFY Play rule] |
| Testers don't send reports | Remind on day 7 and day 14 (invite text); the owner's own phone report is the minimum |
| Galaxy A23-class testers run out of memory | Low-memory kills are counted separately; voice on demand already (P2 Task 1) |
| A P3 build needs a fix during the 14 days | 0.3.x builds; the counters survive an update (store test) |
