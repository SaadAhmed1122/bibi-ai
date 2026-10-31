# Spec: avatar-states (P2)

Shared rules: [SPEC-P2.md](SPEC-P2.md). States fixed 2026-10-29: idle (with blink), thinking, speaking, confused; vector sources in `docs/design/character/states/`.

## Objective
Bibi shows what it is doing: thinking while waiting for the first word, speaking while the answer streams or is read aloud, confused after an error or an unclear request, idle otherwise.

## Scope
- PNG frames for thinking, speaking and confused rendered from the SVGs at the S1 size (same pipeline as `bibi_idle.png`), in `companion/overlay` `drawable-nodpi`.
- A pure function `avatarState(chat, voice)`: generating with no text → THINKING; text streaming or TTS playing → SPEAKING; last reply ERROR or a clarifying question → CONFUSED for 3 s; else IDLE.
- `BibiView` draws the current state; frame rate and Calm mode unchanged (12 / 2 fps). The chat screen header shows the same small Bibi.
- TalkBack: the floating Bibi's content description names the state.

## Acceptance criteria
1. Unit tests for `avatarState` and the 3-second confused timeout.
2. On the vivo: each state seen on floating Bibi in portrait and landscape (screenshots in the PR); Calm still about 2 fps.

## Out of scope
More emotions, mouth sync to audio, Rive or Lottie.
