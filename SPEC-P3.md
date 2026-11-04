# Spec: P3 closed test and P4 decision (capability map and shared rules)

**Draft 2026-11-01; owner answered the open questions 2026-11-04.** Module specs: [SPEC-test-report.md](SPEC-test-report.md), [SPEC-feedback.md](SPEC-feedback.md), [SPEC-stability.md](SPEC-stability.md), [SPEC-writeup.md](SPEC-writeup.md), [SPEC-decision.md](SPEC-decision.md). Plan: [tasks/plan-p3.md](tasks/plan-p3.md). Nothing is built yet.

PRD 0.6:
- **P3, closed test:** 10–12 testers, feedback, stability fixes. Exit gate: crash-free sessions ≥ 99 % in the core loop, and a retention signal recorded.
- **P4, decision:** a public write-up and portfolio package, then the owner's funding / no-funding decision.

The P1 and P2 rules still apply ([SPEC.md](SPEC.md), [SPEC-P2.md](SPEC-P2.md)).

## Starting point
- **P2 is code-complete.** Its exit needs owner items first: the 1-hour P2 gate (#90), the privacy policy URL, the foreground-service video, the Drive link, and the signed 0.2.0-p2 upload to the closed-test track. P3 testing can't start before that upload.
- **The P3 gate can't be measured today.** It needs numbers, and Bibi has no `INTERNET` permission and no analytics. Whatever measures it must stay on the phone and leave only when the tester chooses to share it.

## Assumptions (correct any of these before the plan)
1. **Crash-free sessions:**
   - A session is one stretch of use: the app in front, or a tap-to-talk conversation, until it ends.
   - A session is crash-free when none of Bibi's processes crashed or hit an ANR during it. The local crash log and Android's exit records already tell which.
   - The gate is crash-free sessions ÷ all sessions, summed over the testers' reports.
2. **Retention signal:** for each tester, the number of days Bibi was used in their first 14 days, and whether it was used on day 7 or later. Counted on the phone as dates only.
3. **Getting the numbers out:** a tester taps "Share test report" and the share sheet sends a short plain-text report. It holds only counts and dates: sessions, crashes, ANRs, active days, how many chats, talks, actions and memories, plus the app version and phone model. Never any text, voice or memory content. Testers send it on day 7 and day 14.
4. **Feedback:** a "Send feedback" item in Settings opens an email template with the app version and phone model filled in. Bugs go to GitHub Issues with a `p3` label, entered by the owner or Claude.
5. **Privacy:**
   - The counters are new data stored on the phone, so the privacy policy and the Privacy screen list them, and "Delete everything" removes them.
   - Data safety stays "none collected": sharing is user-initiated [VERIFY on the day, as in docs/play-console.md].
6. **Builds during P3:** fixes ship as 0.3.x builds; the owner signs and uploads each one.
7. **P4 write-up:** built from the LinkedIn findings log, the spike results, the P3 numbers and the docs. The portfolio package is a public case-study page, the demo video script and storyboard, screenshots and an architecture diagram. The repository is private today; it becomes public for the portfolio after a secrets and history audit (owner, 2026-11-04).

## Capability map

| Module id | Responsibility | Depends on |
|---|---|---|
| `test-report` | On-phone counters (sessions, crash-free sessions from the crash log and exit records, active days, feature counts) and Settings > About > "Share test report" as plain text through the share sheet. No content, no network. Privacy policy and Privacy screen updated; Delete everything clears the counters. | P2 app (crash log, exit-info, Privacy) |
| `feedback` | Settings > "Send feedback" opens a prefilled email (version, phone, Android version). The tester invite text and a short recruiting plan to reach 12 opted-in testers: how to install (docs/tester-guide.md), what to try each week, when to send the test report. | test-report |
| `stability` | P3 operations: a script that combines the shared reports into the gate numbers (crash-free %, retention) and writes `docs/p3-results.md`; triage of tester bugs into `p3` issues; fixes as 0.3.x builds; a weekly status line. | test-report, feedback, the closed test running |
| `writeup` | P4: a secrets and history audit, then the repository made public; the public write-up as a LinkedIn article (problem, approach, measured results, what didn't work, privacy design) and the portfolio package (case-study page, demo storyboard, screenshots without personal data, architecture diagram). Drafted by Claude; the owner edits and publishes. | stability (final numbers) |
| `decision` | P4: a one-page decision memo with the gate results, what it would take to reach production (open items, costs, risks), and the options (continue solo, seek funding, stop), each with what would change the call. **The owner decides**; the result goes in Decisions. | writeup |

Build order: test-report → feedback → (closed test runs 14+ days) → stability → writeup → decision.

Why this order: the counters must be in the build testers install on day 1, or the first two weeks of the gate can't be measured. Feedback rides in the same build. Stability work is driven by what testers report. The write-up needs the final numbers.

## Shared rules for P3/P4
- **Still no `INTERNET` permission, analytics or crash reporter.** Numbers leave the phone only through a share the tester starts, and the report shows its full text before sending.
- **Counters hold numbers and dates only.** Their unit tests check that no message, memory or transcript text can reach the report.
- **Each P3 build** keeps the P2 checks: unit tests, lint, the vivo in portrait and landscape, the changelog and the LinkedIn log.
- **Public material** (write-up, case study) shows no tester data beyond aggregate numbers, and no personal data in screenshots.

## Owner answers (2026-11-04)
1. **Feedback channel:** email, to swdeveloper.sa@gmail.com (owner, 2026-11-04), set in one string resource.
2. **Testers:** some lined up, more needed. `feedback` includes a recruiting plan (invite text and a LinkedIn call for testers).
3. **Test report after the closed test:** kept in production as an optional "Share usage numbers".
4. **Write-up venue:** a LinkedIn article, and the GitHub repository made public as the portfolio, after a secrets and history audit.
5. **Funding criteria:** not answered yet; asked again when the decision memo is drafted (P4).
