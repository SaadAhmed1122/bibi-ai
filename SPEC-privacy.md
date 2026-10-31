# Spec: privacy (P2)

Shared rules: [SPEC-P2.md](SPEC-P2.md). PRD 0.8 "Privacy", 0.9, FR-076.

## Objective
One place to see what Bibi keeps and to remove it, a switch that stops Bibi from acting or remembering, and the legal texts Play needs.

## Scope
- **Privacy dashboard** (Settings → Privacy): counts and sizes of messages, memory items, the audit log and the crash log; the retention setting; Export memory; **Delete everything** (messages, memory, audit and crash logs, settings; the models stay, with a separate Remove models) behind a confirmation dialog. A line saying nothing leaves the phone and the app has no internet permission.
- **Safe mode** (FR-076): a switch in Settings and in the floating card's header. On: no tools (shortcut and router skipped) and no memory writes; chat still works. Shown as a small label while on.
- **Licenses screen:** Gemma terms (P1 notice), LiteRT-LM, MediaPipe, sherpa-onnx, Piper voice, Parakeet model, AndroidX — texts as assets.
- **Privacy policy:** `docs/privacy-policy.md` (on-device processing, what is stored, retention, export and delete, no data collected or shared) and the same text in the app. The owner hosts it at a public URL for Play.
- **Report this reply:** long-press a Bibi message → Report → the share sheet with the reply text and a short template. No network.

## Acceptance criteria
1. Tests: Delete everything empties every store (repository fakes plus a Robolectric DB test); Safe mode blocks tools and memory writes in `SendMessage`.
2. On the vivo: counts match after a few chats; Delete everything leaves an empty app with the models still imported; Safe mode stops "turn on the flashlight". Both orientations.

## Out of scope
Encrypted export, account deletion (no accounts), a hosted report endpoint.
