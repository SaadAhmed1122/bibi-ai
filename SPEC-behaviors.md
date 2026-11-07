# Spec: behaviors (P3)

Shared rules: [SPEC-P2.md](SPEC-P2.md). Owner request 2026-11-07 (issue #162): give floating Bibi behaviors from PRD §17.3 with the four existing poses and no new art. Builds on [SPEC-avatar-states.md](SPEC-avatar-states.md).

## Objective
Bibi feels alive without asking for attention: it answers touch, greets on unlock, notices the charger and a low battery, and sleeps at night. It never pressures the user (PRD §17.2.3).

## Behaviors
| Act | Trigger | Look | Gating |
|---|---|---|---|
| HOP | Tap | 7 % hop, slight squash, 600 ms | Not in Calm |
| Grabbed | Dragging | 6 % flatter and wider while held | — |
| LAND | Drop after a drag | Squash and settle, 400 ms | Not in Calm |
| GREET (B-02) | `USER_PRESENT` | Paw raised (speaking pose), two 5 % hops, 1.8 s | 30-min cooldown, not 23:00–07:00 |
| CHARGING (B-05) | `ACTION_POWER_CONNECTED` | Paw raised, one 6 % hop, 1.2 s | Once per connect |
| LOW_BATTERY (B-06) | Battery ≤ 15 % and not charging | Puzzled pose, 3 % sink, 2.5 s | Once until charging or > 20 % |
| Sleep (B-03) | 23:00–07:00 and no touch for 2 min | Eyes closed, 6 s breath | Tap wakes it |
| Mirror | Snapped to the right half | Drawn mirrored | Thought dots and the raised tail point into the screen |

- **Scheduler** (`Behaviors`, pure Kotlin, JVM tests): touch acts (priority 100) beat reactions (40); a reaction never cuts off a touch act. At most 6 spontaneous acts per rolling hour. Spontaneous acts wait while Bibi is busy: not idle (thinking, speaking, confused), the chat card or talk window is open, or a call is on (`AudioManager` mode).
- **Calm mode:** no acts and no sleep frame swap, so it stays at 2 fps.
- **Frame rate:** 24 fps only while an act plays (at most 2.5 s), otherwise 12 fps as measured in S1.
- **Headroom:** the frames have 26–35 px of 288 above the ears, so lift plus stretch stays under 9 % and nothing is clipped.
- The chat pose wins over an act's pose; the motion still plays.
- Quiet hours are fixed at 23:00–07:00 (a setting is out of scope).

## Acceptance criteria
1. `BehaviorsTest` (11 tests): touch acts, Calm, greeting cooldown and quiet hours, busy gating, charging, low battery once per cycle, hourly budget, priority, sleep and wake, motion starts and ends at rest.
2. On the vivo, portrait and landscape: drag (grabbed), drop (land), tap (hop), charger (`dumpsys battery set ac 1`), low battery (`dumpsys battery set level 10`), mirror on the right edge.

## Not checked on the device
- GREET: `USER_PRESENT` needs a real unlock (the test phone has a PIN, and shell can't send the protected broadcast) [VERIFY by the owner].
- Sleep: needs 23:00–07:00 [VERIFY by the owner at night].
- Battery cost of the extra acts: at most 6 short acts per hour at 24 fps; not measured [VERIFY with the S1 battery method if needed].

## Out of scope
New poses or art (wave, sleep, celebrate), walking (B-01), notification reactions (B-08), birthdays (B-07), sounds, haptics, a quiet-hours setting.
