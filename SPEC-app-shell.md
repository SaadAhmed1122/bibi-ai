# Spec: app-shell (P1)

Shared stack, commands, style and boundaries: [SPEC.md](SPEC.md).

## Objective
Set up the empty P1 app that every other module plugs into: the Gradle project at the repo root, the `:main` and `:inference` processes, wiring by hand, a theme, a local crash and ANR log, and the Gemma terms notice. Success: the app installs on the vivo, opens to an empty home screen, and a deliberate test crash leaves a readable entry in the local log.

## Scope
- `settings.gradle.kts` with the modules listed in SPEC.md, created empty (`app`, `core:common`, `core:designsystem`, and the others as each module's task needs them). Versions live in `gradle/libs.versions.toml`.
- `app`: `BibiApplication` holds an `AppContainer` (wiring by hand). `MainActivity` hosts Compose navigation with a Home placeholder.
- Manifest: no INTERNET permission. The `:inference` process is declared by the inference module's service. The application checks the process name, so `:inference` skips the UI wiring.
- `core:designsystem`: a Material 3 theme (light and dark) and the Bibi colors as tokens.
- **Crash log (`core:common`):** an uncaught-exception handler in each process appends one record (time, process, thread, exception and stack, app version) to `files/crash/crash.log`, capped at 256 KB with the oldest lines dropped, then hands over to the previous handler. At startup, `ApplicationExitInfo` (API 30+) records for `CRASH`, `CRASH_NATIVE`, `ANR` and `LOW_MEMORY` since the last check are added to the same log. A debug-only "Crash log" screen shows the file and can copy it (no network, no share intent in P1).
- **Gemma terms notice:** a short screen or dialog from Home ("Model: Gemma 3 1B — Gemma Terms of Use"), with the terms text bundled as an asset [VERIFY the exact notice text required by the Gemma terms before release].

## Acceptance criteria
1. `./gradlew :app:assembleDebug test lint` passes on a clean checkout.
2. The merged manifest has no `android.permission.INTERNET` (checked by a unit test or a Gradle check that reads the merged manifest).
3. Crash log: a JVM test feeds an exception to `CrashLogWriter` and gets one record with the process name and stack; the cap test shows the file stays ≤ 256 KB.
4. `ExitInfoCollector` maps fake `ApplicationExitInfo` records to log lines and skips ones already seen (JVM test with a fake source).
5. On the vivo: the app installs and opens to Home. A debug "Crash now" button crashes it, and on the next launch the Crash log screen shows that crash.
6. Home has a working link to the Gemma terms notice.

## Out of scope
Any chat, model or overlay code. A release signing setup (SPEC-release.md).

## Open questions
- The exact Gemma terms notice wording [VERIFY].
