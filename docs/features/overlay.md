# Feature: Overlay (floating character)

## Purpose
Pip's main surface is a small animated character that floats over other apps (PRD §20.1, "presence over screens"). Tapping it opens a compact chat card. Spike S1 tests whether this is stable and cheap enough on the Galaxy A23.

## Requirements
- **PRD 0.8 (MVP behavior):**
  - A floating, draggable character.
  - Tap opens a compact chat card.
  - Hide and snap-to-edge.
  - A persistent notification with a "Hide Pip" action.
- **PRD 0.7 S1 gate:** stable for 1 hour, and battery drain ≤ 3% per hour with the screen on. If it fails: Bubble/Edge mode only, a lower frame rate, and Calm mode as the default.
- **PRD 0.9 policy:**
  - Request `SYSTEM_ALERT_WINDOW` only when the user enables the floating character, with a rationale.
  - Provide an in-app chat fallback when the overlay isn't allowed.
  - Declare and justify a foreground service type.
- **FR-020:** a `TYPE_APPLICATION_OVERLAY` window hosted by a foreground service. It survives app swipe-away and restarts within 5 s after process death.
- **FR-022:** drag, edge snapping, and position saved per orientation.
- **FR-025:** touches pass through outside the character and card.
- **FR-027:** the interaction card opens in ≤ 150 ms [VERIFY on the A23].
- **FR-029:** Calm mode ≤ 2 fps; applied automatically on Battery Saver.
- **FR-030:** a low-importance persistent notification.
- **FR-024:** never draw over secure windows. Don't attempt workarounds (§12.5).
- **Avatar states:** base MVP gets four states. More are allowed only if the gate passes (0.8). Candidates come from the §20.6.1 states table.

## Design notes
- **Service** (§12.4): `CompanionService.startForeground(type = specialUse)` with a property subtype "persistent assistant overlay" [VERIFY current Play requirements]. No microphone foreground-service type until voice ships.
- **Windows** (§12.5): use two windows — a small character window sized to the sprite, and an on-demand interaction card window that becomes focusable only when the keyboard is needed. Flags: `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_NO_LIMITS | FLAG_WATCH_OUTSIDE_TOUCH`.
- **Compose in a service:** needs a custom `OverlayLifecycleOwner` that provides `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner`.
- **Rendering** (§17.5):
  - One `Choreographer` clock, drawn with `Canvas` from a `State` updated on each tick, so recomposition stays out of the animation path.
  - Sprite atlas ≤ 4 MB GPU memory.
  - Frame-rate budget (§12.10): 12 fps idle, 24 fps during interaction, ≤ 2 fps in Calm, zero draws while the screen is off.
- **Motion** (§20.13): release spring with stiffness 400 and damping ratio 0.65; edge snap about 280 ms.
- **Restart** (§12.4): `START_STICKY` plus a 15-minute WorkManager check. A user "Pause" persists.
- **Avatar tech: [OPEN]** — sprite sheet or Rive (OD-03). S1 should measure the sprite-sheet path first because it has fewer dependencies.

## Risks
- **Samsung battery management (PRD 0.13, medium/high):** One UI may put the app to sleep or kill the foreground service.
  - Mitigations: a watchdog, an in-app guide that explains how to exempt Pip from battery optimization (Pip does not request the exemption itself, §12.4), and the in-app chat fallback.
  - The S1 soak must run with default Samsung battery settings first, then with the exemption, and both results must be recorded.
- **vivo Funtouch OS background limits** (PRD §12.4 lists vivo). These apply on the vivo Y17s test device. Run the same soak protocol there, labeled by device.
- **Play review friction** for `specialUse` foreground services and overlays (0.13). Mitigation: upload to the internal track early.
- **4 GB RAM:** the overlay process competes with the model. The overlay must stay light while idle.
- **The OS hiding overlays** on sensitive screens is expected behavior, not a bug.

## Open questions
- Which four emotion states are in base MVP? Proposed: Idle, Thinking, Speaking/Responding, Confused/Error [OPEN].
- Sprite sheet or Rive (OD-03)? [OPEN]
- Is `specialUse` still the right foreground-service type under current Play policy? [VERIFY]
- How aggressive is battery management on the A23's One UI version? [VERIFY]

## Test plan
- **S1 soak (manual, A23):** overlay visible for 1 h with the screen on, while switching between 3–4 common apps. Record battery % at the start and end, crashes, `ApplicationExitInfo` reasons, and jank (`JankStats`). Pass: stable, and ≤ 3%/h.
- **Instrumented smoke test (PRD 0.11):** the service starts, the overlay attaches, a tap opens the card, and touches outside the bounds reach the app underneath.
- **Edge cases:** rotation, keyboard open, Battery Saver on (Calm mode applied), app swiped away, process killed (restarts within 5 s), screen off (no draws).
- Record the results in the spike tracker and in `docs/project_status.md` → Spike results and Decisions.
