# Release: build and upload to the Play internal track (owner)

Owner decision 2026-10-31 (SPEC-release.md): **the owner signs the AAB and uploads it.** Claude prepares the build and never handles the keystore or its passwords.

## One-time setup
1. **Create the app** in Play Console: name "Bibi", app (not game), free. Package name: `io.github.saadahmed1122.bibi` (it can't change after the first upload).
2. **Make an upload key** on your laptop, outside the repo (for example in `~/keys/`):
   ```
   keytool -genkeypair -v -keystore ~/keys/bibi-upload.jks -alias bibi-upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
   Back up the `.jks` file and both passwords somewhere safe. Never put them in the repo.
3. **Use Play App Signing** (the default for new apps). Google keeps the app signing key; your key is only the upload key.
4. **Tell Gradle where the key is.** Add to `~/.gradle/gradle.properties` (your home folder, not the repo):
   ```
   BIBI_UPLOAD_STORE_FILE=/Users/<you>/keys/bibi-upload.jks
   BIBI_UPLOAD_STORE_PASSWORD=...
   BIBI_UPLOAD_KEY_ALIAS=bibi-upload
   BIBI_UPLOAD_KEY_PASSWORD=...
   ```
   Without all four, `bundleRelease` still builds, but unsigned, and Gradle warns.

## Each release
1. Raise `versionCode` (and `versionName` if you like) in `app/build.gradle.kts`. Play rejects a versionCode it has seen before. The first is `1` / `0.1.0-p1`.
2. Build: `./gradlew :app:bundleRelease`
3. Check that it's signed: `jarsigner -verify app/build/outputs/bundle/release/app-release.aab` should print `jar verified.`
4. Play Console → Testing → **Internal testing** → Create release → upload `app/build/outputs/bundle/release/app-release.aab` → release notes → Save → Review → Roll out.
5. **Testers:** add your Google account (and anyone else) to the internal testers list, open the opt-in link on the phone, and install from Play.

## What the release build does
- **R8 shrinking** is on. LiteRT-LM ships no keep rules, so `app/proguard-rules.pro` keeps its whole package (its native code calls back into it by name).
- **Checked on the vivo, 2026-10-31**, with a release APK signed with the local debug key: chat, Stop with conversation rebuild, floating Bibi and the chat card in portrait and landscape. The AAB itself was built signed and unsigned. It was not installed through bundletool: downloading bundletool was blocked in the Claude session, and the Play internal track install is the real check.
- No INTERNET permission. The build fails if any manifest asks for it.
- Debug screens (Crash log, LLM check) are hidden.

## Still to check [VERIFY]
- Whether the internal track already asks for a **privacy policy URL** or the **Data safety** form. The privacy policy page is planned for the P2 gate (PRD 0.9). If Play Console blocks the release without them, record it in docs/project_status.md → Decisions.
- The `specialUse` foreground-service declaration (floating Bibi): Play may ask for a short description and a video of the feature. The text in the manifest is: "Persistent on-screen companion character: a small animated overlay the user can move and tap to talk to the assistant."
- The model file isn't in the app (side-loaded, 585 MB). Internal testers need the file copied to their phone, then Home → Bibi's model.
