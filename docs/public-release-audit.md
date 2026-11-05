# Public-release audit

P4 Task 10 (#135), [SPEC-writeup.md](../SPEC-writeup.md) criterion 1. Audit run 2026-11-05 on `main` at `3562b0a` (309 commits, 596 tracked files, pack size 62.4 MiB).

**Visibility note:** the GitHub repository `SaadAhmed1122/bibi-ai` was already **public** when this audit started (`gh repo view` → `PUBLIC`). The spec planned the audit before the flip, so every finding below has been public since the flip. The owner should confirm the repository is meant to stay public. The date it was made public is [VERIFY: owner].

## Method
- **Secrets:** a regex scan of the full history (`git log --all -p`) for AWS, GitHub, Google, OpenAI/Anthropic, Slack, Hugging Face and Context7 key formats, private-key blocks, and hard-coded `password`/`token`/`secret`/`api_key` values. A file-name scan of every file ever added (`.env`, keystores, `.p12`, `.pem`, `local.properties`, service-account JSON, `google-services.json`). gitleaks and trufflehog are not installed. A second pass with one of them is advised [VERIFY].
- **Personal data:** `git grep` for email addresses, phone numbers, device serials, home-folder paths and Google Drive links. A check of every file deleted in history. A look at the headers and contents of every spike and measurement CSV. Each of the 47 PNG screenshots and frames from the 5 videos checked by eye (contact sheets).
- **Binaries:** every blob in history over 1 MB, and every model-type file name ever added (`.litertlm`, `.onnx`, `.tflite`, `.task`, `.bin`, `.aar`, `.apk`, `.aab`, `.so`, `.gguf`, `.zip`).
- **Licenses:** the bundled fonts, the in-app `licenses.txt`, the vendored tooling and the character art.

## Findings

| # | Area | Finding | Resolution | Status |
|---|---|---|---|---|
| 1 | Secrets | No keys, tokens or passwords in the working tree or in any of the 309 commits. No `.env`, keystore, `local.properties` or service-account file was ever committed. Release signing reads `BIBI_UPLOAD_*` from `~/.gradle/gradle.properties`; `.mcp.json` takes its keys from the environment. | None needed | Resolved |
| 2 | Personal data | The owner's personal Gmail address was in `docs/project_status.md` (Play Console note, 2026-11-04). | Replaced with "the owner's personal Google account" in this PR | Resolved in the tree |
| 3 | Personal data | The same address is in git history: in the old text of `project_status.md` and as the author email of the owner's commits made from this machine. Removing it needs a history rewrite and a force-push to `main` (CLAUDE.md §10). The repo has been public, so copies may already exist. | Recommend **accept**: commit author emails show on every public GitHub commit, and a rewrite would break every open PR and fork. Optionally, set the local `user.email` to the GitHub noreply address for future commits | **Owner decision** |
| 4 | Personal data | The feedback address `swdeveloper.sa@gmail.com` is in `strings.xml`, specs and TESTING.md. | Intended public contact (owner, 2026-11-04); also on the Play listing | Accepted (owner choice) |
| 5 | Personal data | Absolute paths with the macOS username in `docs/project_status.md`. | Replaced with `~/bibi-wt/` and "the main checkout" | Resolved |
| 6 | Personal data | The Google Drive link to the chat model is in `core/designsystem` `strings.xml` and `docs/tester-guide.md`, so anyone can now download the file. The Drive folder carries the Gemma NOTICE, README and terms that redistribution requires (Decision 2026-11-01). | Accept if the owner is fine with public downloads; otherwise limit the Drive sharing to testers' accounts | **Owner decision** |
| 7 | Personal data | Screenshots and videos show only test content: the fake number 0123 456 789, blocked-secret tests ("My sister is Sara and her PIN is 4821", "hunter2") and test facts ("I am allergic to peanuts", "My gym is on Mall Road", "my cat is called Milo"). Home-screen shots show the vivo's installed app icons. No notifications with personal content, no contacts or messages. | Owner to confirm that the memory facts in `memory-retention-export.png` are made up [VERIFY: owner] | Accepted pending that check |
| 8 | Personal data | Spike and measurement CSVs hold scripted prompts and model answers, timings, battery and memory figures, and only the app's own and system package names (`com.android.deskclock`, `com.android.settings`, `com.google.android.dialer`). The device is named only by model (vivo V2310); there are no serials or IMEI. | None needed | Resolved |
| 9 | Personal data | Two files were deleted in history: `docs/design/bibi-character-gemini.md` (art prompts) and `docs/design/character/Images.txt` (empty). | None needed | Resolved |
| 10 | Binaries | No model file, APK, AAB or AAR was ever committed. The largest blob is 1.6 MB (Gemini concept art). `.gitignore` covers model files and signing files, and the git-ignored `libs/` folder holds the sherpa-onnx AAR. | None needed | Resolved |
| 11 | Licenses | The repo ships the font files `bricolage_grotesque.ttf` and `figtree.ttf` (SIL OFL 1.1). The OFL asks for the copyright notice and license to go with the fonts. The app's `licenses.txt` names them, but the repo had no license text. | Added the official OFL files from `google/fonts` in `licenses/fonts/` | Resolved |
| 12 | Licenses | Vendored tooling: the three Strix skills come from `usestrix/strix` (Apache-2.0, checked with the GitHub API) and are pinned in `skills-lock.json`. The `graphify` skill comes from `Graphify-Labs/graphify` (Apache-2.0, checked with the GitHub API). `plugins/agent-board` is the owner's own. | None needed; both upstreams allow redistribution | Resolved |
| 13 | Licenses | The character art in `docs/design/character/gemini/` was generated with Google Gemini from the owner's prompts. The vector and app assets were made from it. | Recommend accept; under Google's generative AI terms the user may use outputs [VERIFY current terms] | **Owner decision** |
| 14 | Licenses | The repository has no `LICENSE` file, so the public code is "all rights reserved" by default. | The license choice is [OPEN] (SPEC-writeup) and goes into the README PR (#136) | **Owner decision** |

## What the owner decides
1. Keep the repository public (it already is).
2. Finding 3: accept the email in history (recommended) or rewrite history.
3. Finding 6: keep the Drive model link public or limit it to testers.
4. Finding 7: confirm the memory facts in the screenshots are made up.
5. Finding 13: accept the Gemini-generated art.
6. Finding 14: pick a code license (for example Apache-2.0 or MIT), or none.

No technical items are open. The second scan with gitleaks or trufflehog is advised, not required.
