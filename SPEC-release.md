# Spec: release (P1)

Shared rules: [SPEC.md](SPEC.md). Owner decision 2026-10-31: **the owner builds the signed AAB and uploads it to the Play internal track.** Claude prepares the build setup and runs the exit-gate check, and never handles keystores or passwords.

## Objective
Make `:app:bundleRelease` produce an AAB the owner can sign and upload, and verify the P1 exit gate on the vivo. Success: the owner's upload to the internal track is accepted, and the 1-hour session passes.

## Scope
- **Release build type:**
  - R8 on, with keep rules for LiteRT-LM and Room [VERIFY the needed rules; test the release build on the vivo before the gate].
  - `versionCode` and `versionName` set in `app/build.gradle.kts`, starting at 1 / `0.1.0-p1`.
- **Signing config:** read from Gradle properties outside the repo (`~/.gradle/gradle.properties`: `BIBI_UPLOAD_STORE_FILE`, `BIBI_UPLOAD_STORE_PASSWORD`, `BIBI_UPLOAD_KEY_ALIAS`, `BIBI_UPLOAD_KEY_PASSWORD`). If they are missing, the release build is left unsigned and Gradle warns; it never fails a debug build. The variable names are added to `.env.example` without values.
- **Play App Signing:** the owner's upload key, and Google keeps the app key. A short owner checklist goes in `docs/release.md`:
  - create the app;
  - make the upload key;
  - set the properties;
  - `./gradlew :app:bundleRelease`;
  - upload to Internal testing;
  - add testers.

  Whether the internal track needs a privacy policy URL or data-safety form is [VERIFY] (scope-guard note).
- **The exit gate (PRD 0.6), on the vivo Y17s:**
  - unplugged, release build, model imported, overlay on;
  - over 1 hour, about 20 chats from the overlay card and the app screen (an owner script in TESTING.md), with at least two Stops and one Clear;
  - **pass** = no crash or ANR entries in the local crash log and in `dumpsys activity exit-info`, and the app is still responsive at the end;
  - results go in a new `docs/p1-gate.md` and a Decision line.

## Acceptance criteria
1. `./gradlew :app:bundleRelease` builds with and without the signing properties (unsigned without them).
2. The release APK installed from the AAB (bundletool, `--local-testing`) runs chat with the real model on the vivo, so R8 did not strip what LiteRT-LM needs.
3. `docs/release.md` and the TESTING.md exit-gate script exist.
4. The exit-gate session is recorded with its result.

## Out of scope
Closed and production tracks, the store listing, the licenses screen, and the privacy policy page (P2 gate, PRD 0.9).
