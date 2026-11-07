# Spec: P1 walking skeleton (capability map and shared rules)

Approved by the owner on 2026-10-31. Scope was checked by scope-guard against PRD 0.6, 0.8 and 0.9. The P1 exit gate (PRD 0.6): the core loop runs for 1 hour without a crash, and the app can be installed from the Play internal track.

Each module has its own spec, `SPEC-<module id>.md`, and they are built in dependency order. This file is the index; module ids never change.

## Capability map

| Module id | Responsibility | Depends on | Spec |
|---|---|---|---|
| `app-shell` | Gradle project, the `:main` and `:inference` processes, wiring, local crash/ANR log, Gemma terms notice | — | [SPEC-app-shell.md](SPEC-app-shell.md) |
| `model-import` | Pick the `.litertlm` file, copy it into app storage, check its SHA-256, report the model state | app-shell | [SPEC-model-import.md](SPEC-model-import.md) |
| `chat-history` | Room storage of the one conversation | app-shell | [SPEC-chat-history.md](SPEC-chat-history.md) |
| `inference` | Bound service in `:inference`: Gemma 3 1B on LiteRT-LM 0.16.1, one conversation with the system prompt prefilled once, streaming, Stop, recovery | model-import | [SPEC-inference.md](SPEC-inference.md) |
| `chat` | Send-message use case and the in-app chat screen (streaming, Stop, short answers, works without the overlay) | inference, chat-history | [SPEC-chat.md](SPEC-chat.md) |
| `overlay` | Foreground service with Bibi (S1 frames, 12 fps / Calm 2 fps): drag, snap to edge, hide, a notification with Hide, tap opens a chat card | chat | [SPEC-overlay.md](SPEC-overlay.md) |
| `onboarding` | First run: notifications, model import, and the overlay permission asked only when the floating Bibi is turned on | model-import, overlay | [SPEC-onboarding.md](SPEC-onboarding.md) |
| `release` | Release build type, a signing config read from outside the repo, versioning, the 1-hour exit-gate check. **The owner builds the signed AAB and uploads it to the internal track.** | all | [SPEC-release.md](SPEC-release.md) |

Build order: app-shell → model-import, chat-history → inference → chat → overlay → onboarding → release.

Out of P1 (PRD 0.6, 0.8): voice (TTS/STT), tools and phone actions, memory extraction, emotions beyond the S1 frames, the licenses screen, privacy policy, safe mode, model download/hosting (open item 6 is decided during P1, but download is built in P2).

## Tech stack

- Kotlin, Jetpack Compose with Material 3, Room, coroutines and Flow. minSdk 34, target and compile SDK 36, Java 17.
- AGP 9.0.1 and Gradle 9.1.0, the same as the spikes. AGP 9 has built-in Kotlin; the Compose compiler plugin and the Kotlin version are pinned together in `gradle/libs.versions.toml` [VERIFY with Context7 before the first build].
- LiteRT-LM `com.google.ai.edge.litertlm:litertlm-android:0.16.1`, pinned. Versions 0.17+ need Kotlin 2.3 (S2 note).
- Model: `gemma3-1b-it-int4.litertlm` (584,417,280 bytes, side-loaded). SHA-256 in `SPEC-model-import.md`.
- Wiring by hand (an `AppContainer` object). No Hilt or Koin in P1.
- No network permission. No analytics, no crash reporter (project_spec.md rule 1).

## Commands

```
./gradlew :app:assembleDebug                  # debug APK
./gradlew test                                # all JVM unit tests
./gradlew :app:installDebug                   # install on the connected phone
./gradlew lint                                # Android lint
./gradlew :app:bundleRelease                  # release AAB (signing from ~/.gradle/gradle.properties, owner only)
adb push gemma3-1b-it-int4.litertlm /sdcard/Download/   # model for import testing
graphify update .                             # after code changes (CLAUDE.md §9)
```

## Project structure (repo root; `spikes/` stays as it is)

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
app/                      Application, AppContainer, manifest (both processes), MainActivity, nav
core/common/              Dispatchers, Result types, Logger, crash log writer (pure Kotlin where possible)
core/designsystem/        Material 3 theme, colors, type, shared composables
core/database/            Room database, entities, DAOs
domain/model/             Pure Kotlin entities (Message, ModelState, …)
domain/usecase/           SendMessage, ImportModel, … (pure Kotlin, JVM-tested)
ai/inference-api/         InferenceClient interface, Messenger protocol, FakeInferenceClient
ai/inference-litert/      InferenceService (:inference) + LiteRtLmEngine
companion/overlay/        OverlayService, BibiView, snap/drag logic
feature/chat/             Chat screen, chat card, ChatViewModel
feature/onboarding/       First-run screens
```
The modules follow `docs/architecture.md` §3, restricted to what P1 needs. Tests go in each module's `src/test` (JVM) and `src/androidTest` (only where a device is needed).

## Code style

Kotlin official style (CLAUDE.md §5). MVI: a ViewModel exposes `StateFlow<UiState>`, the UI sends intents, and composables are stateless with `Modifier` as the first optional parameter.

```kotlin
data class ChatUiState(
    val messages: List<MessageUi> = emptyList(),
    val draft: String = "",
    val generating: Boolean = false,
    val modelState: ModelState = ModelState.Missing,
)

sealed interface ChatIntent {
    data class Edit(val text: String) : ChatIntent
    data object Send : ChatIntent
    data object Stop : ChatIntent
}

class ChatViewModel(private val sendMessage: SendMessage, …) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    fun onIntent(intent: ChatIntent) { … }
}
```
- Strings and prompts live in resources or assets, never inline.
- `TODO(#N)` and `// shortcut(#N):` always cite an issue.
- Coroutines are structured, long work can be cancelled, and each inference engine has one single-thread dispatcher (PRD §12.7).

## Testing strategy

- **JVM unit tests (JUnit 4, kotlinx-coroutines-test, Turbine for Flow)** for use cases, ViewModels, SHA-256 checking, snap-to-edge maths, the Messenger protocol encode/decode, and the crash-log writer. Tests are written first (CLAUDE.md §5, ponytail precedence).
- **Room:** DAO tests with an in-memory database (Robolectric or androidTest [OPEN: choose in plan]).
- **FakeInferenceClient** in `ai/inference-api` so the chat logic is tested without the model.
- **On device (vivo Y17s):** each module's spec lists a manual check. The P1 exit gate is a 1-hour core-loop session (SPEC-release.md).
- No coverage number is set. Every acceptance criterion has a test or a named manual check.

## Boundaries

- **Always:** start from an up-to-date `main` with one branch per task and a PR; never push to `main` (CLAUDE.md §10). Check library APIs with Context7 at the pinned version. Run `./gradlew test` before every commit. Update status and the changelog after each step. Run `graphify update .` after code changes.
- **Ask first:** adding any dependency not listed above; any new permission; Room schema changes after the first release; changing the LiteRT-LM version; anything outside this map.
- **Never:** commit keystores, `local.properties`, `.env` or model files; add analytics or a crash reporter, or use the network for anything but the model download (SPEC-model-download.md); add SMS, call-log, contacts or Accessibility permissions; build voice, tools or memory in P1; merge without the owner's OK.

## Success criteria (P1 as a whole)

1. On the vivo Y17s, with the model imported, you can open the app, type a message, see a streamed Gemma answer, and press Stop mid-answer. The same works from the overlay chat card.
2. The conversation is still there after the app is killed and reopened.
3. The overlay can be dragged, snaps to the nearest edge, hides from the notification, and comes back.
4. Chat works with the overlay permission denied.
5. The 1-hour core-loop session on the vivo has no crash and no ANR (the local crash log is empty, and `dumpsys activity exit-info` shows no crash).
6. `./gradlew test lint :app:assembleDebug` passes, and `:app:bundleRelease` builds with the owner's signing config.

## Open questions

- Room DAO tests on Robolectric or on a device: decided in the plan.
- Model hosting (open item 6): decided during P1 and recorded in Decisions; it does not block these specs.
- The Galaxy A23 run of the exit gate (open item 10).
