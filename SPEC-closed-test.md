# Spec: closed-test (P2)

Shared rules: [SPEC-P2.md](SPEC-P2.md). PRD 0.6 exit gate: closed-test ready, PRD 0.9 checklist green. **The owner builds, signs, uploads and manages testers.**

## Objective
Everything Play asks for is written down and true, and a P2 build has passed a 1-hour session on the vivo.

## Scope
- `docs/play-console.md`: Data safety answers ("no data collected or shared"), foreground-service declarations (`specialUse` for floating Bibi, `microphone` for push-to-talk from the card) with the text and a video checklist, the permissions list with reasons, content rating notes, the AI-content report path, the privacy policy URL slot.
- PRD 0.9 checklist with evidence per row (file, test or screenshot), each [VERIFY] item checked against current Play policy pages.
- Version bump (versionCode / versionName).
- P2 gate session (TESTING.md): 1 hour on the vivo using chat, a tool, memory and voice; `dumpsys activity exit-info` clean; results in `docs/p2-gate.md`.

## Acceptance criteria
1. Every 0.9 row is green or has a dated Decision explaining it.
2. The 1-hour P2 session has no crash or ANR.
3. The owner uploads to the closed-test track.

## Out of scope
Production release, tester recruitment, store graphics beyond what exists.
