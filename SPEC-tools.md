# Spec: tools (P2)

Shared rules: [SPEC-P2.md](SPEC-P2.md). Background: [docs/features/tools-permissions.md](docs/features/tools-permissions.md), S4 results in `spikes/s4-tools/results/RESULTS.md` (§8: 95/100 right call and values, 0 wrong calls).

## Objective
Bibi does five phone actions when asked in chat: open an app, set an alarm, start a timer, turn the flashlight on or off, and open the dialer with a number filled in. Every action goes through one Permission Engine. Dial is Sensitive (owner, 2026-10-31): a card shows the exact number and nothing happens until Confirm.

## Scope
- **`core:tools` (pure Kotlin, JVM-tested), ported from S4 with its tests:** `ToolCall`, `ToolCallParser`, `ToolValidator` (one repair, scoped to the exposed tool), `PermissionEngine`, `ShortcutParser`, `SlotExtractor`. Tests come first for anything changed (PRD 0.11).
- **Router in `:inference`:** the MediaPipe Decision Maker (`com.google.mediapipe:tasks-decision:1.2.0`, `embeddinggemma-2-text-270m.litertlm`) picks one of the five tools or `none`, threshold 0.25 (S4 §7). A new `ROUTE` Messenger message returns the label and score. The router never runs an action. `datatransport` stays excluded, so no `INTERNET` permission.
- **Router model import:** the P1 import flow takes a second file, with its SHA-256 and size (164,626,432 bytes). Without it, only the shortcut path works and chat is unchanged.
- **`ToolExecutor` (app):** builds intents from validated arguments only. `open_app` uses `<queries>` for launcher intents (no `QUERY_ALL_PACKAGES`); alarm and timer use `AlarmClock` with `com.android.alarm.permission.SET_ALARM` (a normal permission); flashlight uses `CameraManager.setTorchMode`; dial uses `ACTION_DIAL`.
- **Chat path (`SendMessage`):** shortcut match first (no model), then the router, then slot extraction for the chosen tool. A missing or unclear slot gets a short question from Bibi; `none` or low confidence goes to Gemma as normal chat. The router adds about 0.5 s before chat replies (S4) [VERIFY in the app].
- **Confirmation card** in the chat screen and the floating card for Sensitive actions: the resolved target (app label, number), Confirm and Cancel. Cancel after 30 s without an answer (silence is never consent); no Sensitive action while the phone is locked (`KeyguardManager`).
- **Result as a Bibi message** ("Timer set for 5 minutes", "I couldn't find Spotify"), saved in history as text (no schema change).
- **Audit log:** a capped text file in app storage (256 KB, like the crash log) with time, tool, outcome and redacted values (a phone number keeps its last 2 digits). Not a Room table.

## Acceptance criteria
1. The S4 JVM suites pass in `core:tools`, plus Permission Engine tests for every risk class × lock state, and dial = Sensitive.
2. `SendMessage` tests with fakes: shortcut hit runs without the router; `none` goes to chat; a Sensitive call waits for Confirm; Cancel and the timeout run nothing; a tool outside the exposed one is rejected.
3. On the vivo, in portrait and landscape, from the app and the floating card: each of the five actions works by shortcut and by a router phrase; dial shows the card first; shortcut path p95 < 300 ms (PRD 0.7).
4. Merged manifest: no `INTERNET`, no `QUERY_ALL_PACKAGES`, no contacts, SMS or call-log permission.
5. Strix (`find-security-vulnerabilities-in-code`) runs on the tool path, or the session summary says why it couldn't [CLAUDE.md §9].

## Out of scope
More than five actions, contacts lookup ("call mum"), chained actions, actions requested by text the user pasted (taint upgrade to Sensitive is kept in the engine, but MVP reads no outside content).

## Open questions
- App name matching ("open maps"): S4's whole-word match on the launcher list, ask when ambiguous [OPEN].
- Router confidence threshold: S4 tuned 0.25 on confidences of 0.27–0.47, but in the app (Task 3, same model and code) right labels came in at 0.76–1.00 and a chat message at 0.64 for "none". Re-measure on the 110 prompts in Task 5 before trusting 0.25 [VERIFY].
- A bare hour ("alarm at 6") is read as 24-hour; a light request without on/off turns it on (S4 defaults) [OPEN].
