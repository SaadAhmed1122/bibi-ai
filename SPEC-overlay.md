# Spec: overlay (P1)

Shared rules: [SPEC.md](SPEC.md). Based on spike S1 (`spikes/s1-overlay/`, RESULTS.md: PASS, Bibi costs 27.8 mA at 12 fps and 11.5 mA in Calm on the vivo).

## Objective
Bibi floats over other apps as a small animated character. You can drag it, it snaps to an edge, you can hide it, and tapping it opens a chat card. Success: the success criterion 3 in SPEC.md, and the 1-hour exit-gate session runs with the overlay on.

## Scope
- **`companion:overlay`:**
  - `OverlayService` is a foreground service. Its type is `specialUse` [VERIFY against PRD 0.9 and the Play foreground-service declaration; not `microphone`].
  - It uses a `TYPE_APPLICATION_OVERLAY` window, with `BibiView` ported from S1: the `bibi_idle` and `bibi_idle_blink` frames, a `FrameSchedule` at 12 fps, and a Calm mode at 2 fps (a setting, default off).
- **Drag and snap:** dragging moves the window. On release, Bibi snaps to the nearest left or right edge in about 280 ms (S1 behavior), kept inside the screen insets. The last position is saved and restored.
- **Tap** opens the chat card: a second overlay window with `ChatCard` (SPEC-chat.md), focusable for typing, closed with a back gesture, a tap outside, or a close button. Compose in a service needs a lifecycle, view-model store and saved-state owner on the window (`OverlayLifecycleOwner`, docs/architecture.md) [VERIFY the setup with Context7].
- **Notification:** a persistent notification ("Bibi is here") with **Hide** and **Open chat** actions. Hide removes the windows but keeps the service and the notification ("Show Bibi" brings it back). Turning the floating character off in the app stops the service.
- **Hide for a set time** (15 min, 1 h) is FR-031, which the PRD gives P1 priority. It is out of P1; plain Hide/Show is enough [OPEN: owner may add].
- **Rotation and configuration changes:** Bibi stays on screen and is clamped to the new bounds.

## Acceptance criteria
1. JVM tests for the pure logic:
   - `snapTarget(x, width, screenWidth, insets)` → left or right edge;
   - clamping after rotation;
   - the frame schedule at 12 and 2 fps (port the S1 tests if present).
2. A service state test: Show → Hide → Show, and Stop, without leaking windows (a fake WindowManager wrapper).
3. On the vivo:
   - drag and snap feel the same as S1;
   - tap opens the card, a message streams in it, and the card closes;
   - Hide from the notification hides Bibi, and Show brings it back;
   - turning Bibi off removes the notification.
4. On the vivo: 1 hour with the overlay on and occasional chats, no crash (part of the exit gate, SPEC-release.md).

## Out of scope
More animation states and emotions, Rive, context-aware hiding, start on boot (FR-028, P1 priority in the PRD, deferred), and multi-display support.

## Open questions
- The foreground service type [VERIFY].
- Timed hide (FR-031) [OPEN].
