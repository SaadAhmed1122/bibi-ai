# Implementation Plan: P1 walking skeleton

## Overview
Build the P1 app described in [SPEC.md](../SPEC.md) and the eight module specs: a Gemma 3 1B chat in its own `:inference` process, an in-app chat screen, Room history, a side-loaded model with a checksum, floating Bibi, onboarding, and a release build the owner signs and uploads. The work is ordered so the riskiest path comes first: a typed message streaming back from the model in a separate process. Everything else is added around that working loop.

**Tasks are tracked as GitHub Issues** in milestone "P1 — Walking skeleton" (CLAUDE.md §10: Issues are the source of truth). The list below is the ordered index; issue numbers are filled in when the issues are created after this plan is approved. One task = one branch `feature/<issue>-<name>` = one PR.

## Architecture decisions
- **Walking skeleton first.** Task 4 loads the model from a debug-only path (pushed with `adb` + `run-as`), so the in-process risk (Compose + AGP 9 + LiteRT-LM 0.16.1 in `:inference`) is proven before model import, history and overlay exist. Task 10 removes the debug path.
- **IPC with a `Messenger`**, as in the S2 spike, rather than AIDL. It needs one message protocol file, it is enough for streaming tokens, and S2 already ran it on the vivo.
- **Hand-written wiring** (`AppContainer`); no DI library (SPEC.md).
- **Answer length:** a system-prompt instruction plus a 256-token cap. If 0.16.1 has no max-new-token option, the service stops the stream at 256 tokens itself (checked in Task 4 with Context7 and the 0.16.1 sources).
- **Context reset keeps the last 6 turns** (S2 budget: 4,096 tokens; the system prompt plus primer is about 310; 6 short turns plus a 256-token reply fit with margin). This is the starting value for the open question in SPEC-inference.md; it is checked on the vivo in Task 6.
- **Overlay foreground service type `specialUse`** with a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` description. This resolves the open question in SPEC-overlay.md; it is checked against the Play policy before release [VERIFY].
- **Input cap 2,000 characters** (resolves the open question in SPEC-chat.md).
- **Room DAO tests on Robolectric** (JVM, no device needed). Robolectric, Turbine and kotlinx-coroutines-test are the only test dependencies added.
- **Verifying versions:** every library API is checked with Context7 at the pinned version before code is written (CLAUDE.md §9). The Kotlin and Compose compiler pair that works with AGP 9.0.1 and LiteRT-LM 0.16.1 is settled in Task 1.

## Task list

### Phase 1: Foundation (app-shell)
- [ ] **Task 1: Root Gradle project and empty app.** Adds `settings.gradle.kts`, the version catalog, `app`, `core:common` and `core:designsystem`, plus a Home screen and the theme. LiteRT-LM 0.16.1 must resolve.
  - Acceptance: `./gradlew :app:assembleDebug test lint` passes; the app opens to Home on the vivo; a test fails the build if the merged manifest has INTERNET.
  - Verify: the commands above; install on the vivo.
  - Files: settings/build scripts, `libs.versions.toml`, `app/…/BibiApplication.kt`, `MainActivity.kt`, `core/designsystem/Theme.kt`. **M**
- [ ] **Task 2: Local crash and ANR log.** `CrashLogWriter` (256 KB cap), `ExitInfoCollector`, the handler installed in both processes, and a debug Crash log screen with a Crash now button.
  - Acceptance: JVM tests for the record format, the cap, and exit-info de-duplication; on the vivo a forced crash shows up after relaunch.
  - Verify: `./gradlew :core:common:test`; manual crash.
  - Files: `core/common/crash/*`, `app/…/debug/CrashLogScreen.kt`. **M**
- [ ] **Task 3: Gemma terms notice.** A Home link to a screen showing the bundled terms notice asset.
  - Acceptance: the link opens the notice; the text is in assets.
  - Verify: a preview plus a manual check.
  - Files: `app/…/TermsScreen.kt`, the asset. **XS**

### Checkpoint A: foundation
- [ ] All tests pass and the debug build installs on the vivo. The owner reviews the project layout.

### Phase 2: Core loop (inference + chat, riskiest first)
- [ ] **Task 4: Inference contract.** `ai:inference-api` with `InferenceClient`, `EngineState`, `ReplyEvent`, the Messenger protocol codec, and `FakeInferenceClient`.
  - Acceptance: round-trip tests for every message; fake-client contract tests for Done, Stop and Error.
  - Verify: `./gradlew :ai:inference-api:test`.
  - Files: 4–5 in `ai/inference-api`. **M**
- [ ] **Task 5: LiteRT-LM service.** `ai:inference-litert`:
  - `LiteRtLmEngine`, ported from S2, with the primer prefill and the token cap;
  - `InferenceService` in `:inference`;
  - the bound client (`MessengerInferenceClient`);
  - a debug-only model path.
  - Acceptance: on the vivo, the debug screen loads the model and streams a reply; the `:inference` pid differs from the app pid.
  - Verify: build; manual on the vivo, with the debug log showing TTFT.
  - Files: `ai/inference-litert/*` (engine, service, manifest), the client. **M**
- [ ] **Task 6: Chat slice.** `SendMessage` (in-memory history for now), `ChatViewModel` (MVI), and `ChatScreen` with streaming, Send→Stop, the model banner, and the 2,000-character cap.
  - Acceptance: use-case and ViewModel tests with the fake client (SPEC-chat.md criteria 1–2); previews; on the vivo, type → streamed answer → Stop keeps the partial text.
  - Verify: `./gradlew :feature:chat:test :domain:usecase:test`; manual.
  - Files: `domain/usecase/SendMessage.kt`, `feature/chat/*`. **M**

### Checkpoint B: the core loop works
- [ ] On the vivo:
  - 10 turns, TTFT p95 ≤ 3,000 ms with turn 1 included;
  - Stop works;
  - the owner tries it.

### Phase 3: Robustness, history, model import
- [ ] **Task 7: Context reset and recovery.** `needsContextReset`, ported from S2, with the conversation reopened from the system prompt plus the last 6 turns. Binder death → `Failed` → rebind once → reload, and the UI shows "Bibi is restarting".
  - Acceptance: JVM tests for the reset rule and rebuild; on the vivo, killing `:inference` mid-answer leaves the UI alive and the next message works; a 30-turn chat crosses a reset without an error.
  - Verify: tests; `adb shell kill <pid>`.
  - Files: the service, the client, `ContextBudget.kt`. **M**
- [ ] **Task 8: Room history.** `core:database` with `BibiDatabase`, `MessageEntity`, `MessageDao` and the exported schema, plus `ChatHistoryRepository`.
  - Acceptance: Robolectric DAO tests and mapping tests; the schema JSON is committed.
  - Verify: `./gradlew :core:database:test`.
  - Files: 4–5 in `core/database`. **M**
- [ ] **Task 9: Persist the chat.** `SendMessage` saves the user message and the final DONE/STOPPED/ERROR answer; Clear deletes history and resets the conversation.
  - Acceptance: updated use-case and ViewModel tests; on the vivo, the history survives a force-stop.
  - Verify: tests; manual.
  - Files: `SendMessage.kt`, `ChatViewModel.kt`, `AppContainer.kt`. **S**
- [ ] **Task 10: Model import logic.** `ImportModel` copies to `.part` with SHA-256 and size checks, progress and cancel; the expected-file constants live in `domain:model`.
  - Acceptance: the five JVM tests in SPEC-model-import.md criterion 1.
  - Verify: `./gradlew :domain:usecase:test`.
  - Files: `ImportModel.kt`, `ModelFile.kt`, the test. **S**
- [ ] **Task 11: Model import UI.** `ModelRepository` (`StateFlow<ModelState>`, startup size check), the SAF picker, a progress screen, Remove model, and the chat banner wired to it. The debug model path is removed.
  - Acceptance: the restart test (Ready without hashing); on the vivo, importing from Downloads reaches Ready, a wrong file is refused, and Remove works.
  - Verify: tests; manual.
  - Files: `ModelRepository.kt`, `feature/chat/ModelImportScreen.kt`, the inference service path. **M**

### Checkpoint C: chat is complete in the app
- [ ] All tests pass. On the vivo: import → chat → kill → reopen keeps history; the `:inference` kill recovers. The owner reviews.

### Phase 4: Overlay
- [ ] **Task 12: Overlay logic.** `snapTarget`, clamp on rotation, and `FrameSchedule` (port the S1 logic and tests) in `companion:overlay`, pure Kotlin.
  - Acceptance: JVM tests.
  - Verify: `./gradlew :companion:overlay:test`.
  - Files: `SnapMath.kt`, `FrameSchedule.kt`, the tests. **S**
- [ ] **Task 13: Overlay service.** `OverlayService` (`specialUse` FGS) with `BibiView` (S1 frames, 12 fps / Calm 2 fps), drag and snap, the saved position, and a notification with Hide, Show and Open chat.
  - Acceptance: a Show/Hide/Stop state test with a fake window wrapper; on the vivo, drag and snap work and Hide/Show from the notification work.
  - Verify: tests; manual.
  - Files: `OverlayService.kt`, `BibiView.kt`, `OverlayNotification.kt`, the manifest, the frames. **M**
- [ ] **Task 14: Chat card window.** A tap opens a focusable second overlay window hosting `ChatCard`, with `OverlayLifecycleOwner` (lifecycle, ViewModel store, saved state). It closes on back, an outside tap, or the close button.
  - Acceptance: on the vivo, a message streams in the card; the keyboard works; it closes cleanly; no leaked windows after 10 open/close cycles.
  - Verify: manual, plus a leak check with `dumpsys window`.
  - Files: `ChatCardWindow.kt`, `OverlayLifecycleOwner.kt`, `feature/chat/ChatCard.kt`. **M**

### Checkpoint D: floating Bibi
- [ ] On the vivo, the overlay and chat card work over other apps. The owner tries them.

### Phase 5: Onboarding
- [ ] **Task 15: First run.** Welcome, Model (with the terms link) and Notifications, plus a completed flag.
  - Acceptance: `OnboardingViewModel` tests; on a clean install there is no permission dialog before the Notifications step.
  - Verify: tests; manual clean install.
  - Files: `feature/onboarding/*`, `MainActivity` nav. **M**
- [ ] **Task 16: Floating Bibi switch.** The overlay permission is asked only when the switch is turned on (explainer → system settings → back), and the service starts or the switch stays off.
  - Acceptance: `FloatingToggle` logic tests; on the vivo, chat works with both permissions denied.
  - Verify: tests; manual.
  - Files: `FloatingToggle.kt`, the Home/settings UI. **S**

### Phase 6: Release and exit gate
- [ ] **Task 17: Release build.**
  - R8 with LiteRT-LM and Room keep rules; version 1 / `0.1.0-p1`.
  - Signing from `~/.gradle/gradle.properties`: unsigned when the properties are missing.
  - The variable names added to `.env.example`, and the owner checklist in `docs/release.md`.
  - Acceptance: `bundleRelease` builds with and without signing; the bundletool local-testing install runs chat with the real model on the vivo.
  - Verify: the commands; manual.
  - Files: `app/build.gradle.kts`, `proguard-rules.pro`, `.env.example`, `docs/release.md`. **M**
- [ ] **Task 18: Exit-gate script.** The 1-hour session script in TESTING.md (unplugged, release build, overlay on, about 20 chats, at least 2 Stops and 1 Clear) and the `docs/p1-gate.md` template.
  - Acceptance: the owner runs it; the result and a Decision line are recorded.
  - Verify: crash log empty; `dumpsys activity exit-info` shows no crash or ANR.
  - Files: `TESTING.md`, `docs/p1-gate.md`, `docs/project_status.md`. **S**

### Checkpoint E: P1 done
- [ ] All six success criteria in SPEC.md are met. The owner uploads the AAB to the internal track.

## Parallel work
Tasks 2 and 3 can run alongside Task 4. Tasks 8 and 10 are independent of each other and can run in parallel after Checkpoint B. Task 12 can start any time after Task 1. Everything on the inference path (4 → 5 → 6 → 7) is sequential.

## Risks and mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| The Kotlin/Compose versions under AGP 9.0.1 don't work with LiteRT-LM 0.16.1 (it needs Kotlin < 2.3) | High | Settle this in Task 1 before any feature code; Context7 plus a real build; pin the versions in the catalog. |
| R8 strips LiteRT-LM JNI classes in the release build | High | Task 17 tests the release build with the real model on the vivo before the exit gate. |
| A Compose window in a service leaks or crashes (lifecycle owners) | Medium | Task 14 has a 10-cycle leak check; follow the documented `setViewTreeLifecycleOwner` setup (Context7). |
| The vivo kills the overlay or `:inference` in the background (Funtouch OS) | Medium | The in-app chat fallback, binder-death recovery (Task 7), and exit info in the crash log. |
| TTFT goes above 3 s with real history | Medium | Prefix reuse from S2; a 6-turn reset window; measure at Checkpoint B and in Task 7. |
| The Play internal track asks for a privacy policy or data-safety form | Low | The owner finds out at upload; a short policy page can be added then [VERIFY]. |

## Open questions
- None blocking. The values chosen above (6 turns, `specialUse`, 2,000 characters, Robolectric) are recorded here and can be changed before their task starts.
