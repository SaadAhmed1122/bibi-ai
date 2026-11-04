# Spec: test-report (P3)

Shared rules: [SPEC-P3.md](SPEC-P3.md). PRD 0.6 P3 exit gate: crash-free sessions ≥ 99 % in the core loop, and a retention signal recorded. Owner answer 2026-11-04: the feature stays in production as an optional "Share usage numbers".

## Objective
The phone counts what the P3 gate needs: sessions, crash-free sessions and active days. The tester can share those numbers as plain text. Nothing leaves the phone unless the tester sends it.

## Scope
- **Sessions:** a session starts when the main activity comes to the front or a tap-to-talk conversation starts. It ends after 30 s in the background or when the talk ends. The 30 s value is an assumption [VERIFY against tester use]. Two overlapping starts count as one session.
- **Crash-free:** a session is crash-free when no `CRASH`, `CRASH_NATIVE` or `ANR` exit of any Bibi process (main or `:inference`) falls between its start and 30 s after its end. The exits come from the existing `ExitInfoCollector` and the uncaught-exception handler. `LOW_MEMORY` kills are counted separately and don't break a session, because the system did the kill, not Bibi.
- **Active days:** a set of local dates (yyyy-MM-dd) when at least one session started, plus the first-use date.
- **Feature counts:** chat messages sent, talks, actions run (by tool name only), memories saved. These are counts only.
- **Storage:** one small store in the main process, holding numbers and dates only. It is capped (dates beyond 120 days are dropped).
- **Share:** Settings > About > "Share usage numbers" shows the full report text on screen first, then sends it through the share sheet (`ACTION_SEND`, `text/plain`). The report holds: app version, phone model, Android version, first-use date, active days, day-7+ use (yes/no), sessions, crash-free sessions, crashes, ANRs, low-memory kills and the feature counts.
- **Privacy:** the Privacy screen lists "usage numbers". Delete everything clears them. `docs/privacy-policy.md` and the in-app copy describe them. Data safety stays "none collected" [VERIFY on the day].

## Acceptance criteria
1. Tests first (JVM):
   - session start/end and overlap rules;
   - crash attribution by time window, across both processes;
   - `LOW_MEMORY` doesn't break a session;
   - active days and the day-7 flag;
   - the report formatter output is fixed and holds no free text (a fake message, memory and transcript containing a marker string never appear in the report);
   - Delete everything empties the counters.
2. On the vivo in portrait and landscape: three sessions and a forced crash (debug-only crash button) give "3 sessions, 2 crash-free". The share sheet opens with the shown text.
3. The merged manifest still has no `INTERNET` permission.

## Out of scope
Automatic upload, per-message timing, any content in the report, and a dashboard in the app.
