# Spec: model-import (P1)

Shared rules: [SPEC.md](SPEC.md). Owner decision 2026-10-31: in P1 the model is side-loaded, with a SHA-256 check. Hosting (open item 6) is decided later.

## Objective
Get `gemma3-1b-it-int4.litertlm` from phone storage into the app's private storage, verify it, and expose its state to the rest of the app. Success: after picking the file once, the app knows the model is ready across restarts, and a wrong or damaged file is refused with a clear message.

## Scope
- The user picks the file with the system file picker (`ACTION_OPEN_DOCUMENT`, Storage Access Framework). No storage permission is needed.
- The file is copied to `filesDir/models/gemma3-1b-it-int4.litertlm.part`. The SHA-256 is computed while copying, and on a match the file is renamed into place. Copying runs on `Dispatchers.IO`, shows progress, and can be cancelled (the `.part` file is deleted).
- **Expected file:** size 584,417,280 bytes, SHA-256 `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be` (measured 2026-10-31 from the owner's file in ~/Downloads, the same file soaked in S2). Both are constants in `domain:model`.
- `ModelState` (sealed): `Missing`, `Importing(progress 0..1)`, `Ready(path)`, `Invalid(reason)`. A `ModelRepository` exposes `StateFlow<ModelState>`. At app start it checks that the file exists and that the size matches. The full hash runs only at import, which keeps startup fast; a size mismatch → `Invalid`.
- "Remove model" deletes the file and returns to `Missing`.

## Acceptance criteria
1. JVM tests on `ImportModel` with a fake source and a temp directory:
   - correct bytes → `Ready`, and no `.part` file left behind;
   - one byte changed → `Invalid("checksum")`, and the file is deleted;
   - wrong size → `Invalid("size")`, checked before hashing;
   - cancel mid-copy → `Missing`, and the `.part` file is deleted;
   - progress values go up and end at 1.0.
2. Restart test: with a valid file present, the repository starts in `Ready` without hashing.
3. On the vivo: importing the owner's file from Downloads reaches `Ready` in under 30 s [VERIFY the timing], and importing a copy of a different file (for example a `.task` file) gives an error message.

## Out of scope
Download, resume, mirrors, more than one model, and update handling (P2, Core Pack row in PRD 0.8).

