# P1 exit gate: 1 hour of the core loop without a crash

The P1 exit gate (PRD 0.6, SPEC-release.md): the core loop runs for 1 hour without a crash, and the app installs from the Play internal track. Owner script: TESTING.md → "P1 exit gate". Results are filled in after the owner's session; until then the result is **not run yet**.

## Setup (to record)
| Item | Value |
|---|---|
| Date and start time | not run yet |
| Phone | vivo Y17s (6 GB RAM, Helio G85, Android 15) |
| Build | release `0.1.0-p1` (versionCode 1), installed from the Play internal track, or else the signed release APK (say which) |
| Battery at start / end | not run yet |
| Unplugged the whole hour | not run yet |
| Floating Bibi on, Calm mode | not run yet |

## Session (to record)
| Check | Target | Result |
|---|---|---|
| Duration | ≥ 60 min | not run yet |
| Chats sent (app screen + chat card) | about 20 | not run yet |
| Stops | ≥ 2 | not run yet |
| Clear chat | ≥ 1 | not run yet |
| Crashes or ANRs in `dumpsys activity exit-info` since the start | 0 | not run yet |
| App responsive at the end (a new message gets an answer) | yes | not run yet |
| Anything odd (slow, wrong, stuck) | — | not run yet |

## How Claude reads the result
After the session, plug the phone in. Claude runs `adb shell dumpsys activity exit-info io.github.saadahmed1122.bibi` and keeps only the exits after the start time. Any reason `CRASH`, `CRASH_NATIVE` or `ANR` in either process (`io.github.saadahmed1122.bibi` or `:inference`) fails the gate. The release build hides the in-app Crash log screen, and the system's exit-info is the same source that log is built from (core:common `ExitInfoCollector`).

## Verdict
Not run yet. Pass or fail goes here and in docs/project_status.md → Decisions as a dated line.
