# Spec: stability (P3)

Shared rules: [SPEC-P3.md](SPEC-P3.md). PRD 0.6 P3: feedback and stability fixes; the gate is crash-free sessions ≥ 99 % and a retention signal recorded.

## Objective
The testers' reports turn into the gate numbers, and their bugs turn into fixed 0.3.x builds.

## Scope
- **Report tool (`tools/p3-report/`):** a small script with no dependencies beyond the standard library. It reads the pasted usage-number reports (one file per report, kept out of git in `tools/p3-report/reports/`). It keeps the latest report per tester and writes `docs/p3-results.md` with:
  - crash-free sessions ÷ sessions overall and per phone model;
  - the active-days distribution;
  - day-7+ use;
  - the feature counts.
  Testers are identified only as T1…Tn.
- **Triage:** each tester email or report with a problem becomes a GitHub issue labeled `p3` (plus `bug` or `enhancement`), with no tester name or email in it. Crashes come with the crash-log text if the tester shares it from the debug screen [OPEN: the debug screen isn't in release builds; decide whether release gets a "Share crash log"].
- **Fix builds:** versions 0.3.x, one PR per issue, built and checked the same way as P2 builds. The owner signs and uploads each one.
- **Weekly status:** one line in `docs/project_status.md` each week: testers active, reports received, crash-free % so far, open `p3` bugs.

## Acceptance criteria
1. The report tool has tests on sample reports: duplicates, a tester's day-14 report replacing their day-7 one, and a malformed report that is skipped and named.
2. `docs/p3-results.md` states the gate result with the number of testers and sessions behind it. If there are fewer than 10 testers or the test ran under 14 days, it says so.
3. The P3 gate verdict is recorded in Decisions.

## Out of scope
Crash reporting services, automatic report collection, and a beta channel outside Play.
