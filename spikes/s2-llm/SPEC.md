# Spike S2 — Local LLM: spec, plan and test protocol

Issue: [#5](https://github.com/SaadAhmed1122/bibi-ai/issues/5) · Milestone: P0 · Feature doc: [`docs/features/model-runtime.md`](../../docs/features/model-runtime.md) · PRD 0.5, 0.7 (S2), §11.3, §11.5, §13.1–13.3, §13.10

> Spike code is **throwaway**. It answers one question with numbers. Nothing here goes into the P1 codebase without a rewrite.

## 1. Question
Can our floor devices run a Gemma 3 1B-class model and a 0.5B Qwen-class model, int4, fully on the phone, fast enough for typed chat, through a 50-prompt soak, without the system killing the model for lack of memory?

## 2. Pass / fail (PRD 0.7)
| Check | PRD wording | How this spike measures it |
|---|---|---|
| Speed | ≥ 5 tokens/s | **Median** decode speed over the 50 prompts ≥ 5.0 tokens/s. The slowest 10 % (p10) is also reported, for information. |
| First token | first token ≤ 3 s | **p95** time to first token ≤ 3000 ms (at most 2 of 50 prompts may be slower). p50 is also reported. |
| Memory | zero OOM kills in a 50-prompt soak | **0** kills of the `:inference` process during the run (see below). |

**Interpretation [VERIFY with owner].** The PRD gives one number per check, not a statistic. This spike uses the median for speed and p95 for first token. The summary prints p10 speed too, so a stricter reading can be applied by hand without re-running.

**Definitions:**
- **Time to first token (TTFT):** from the app sending the prompt to the first non-empty streamed text chunk, on the app's clock. It includes creating a fresh conversation and prefilling the system instruction plus the prompt. There's no prefix caching (PRD §13.3), so this is the pessimistic case.
- **Decode speed:** (decode tokens − 1) ÷ (time from the first chunk to the last chunk). The token count comes from the runtime's own counter (`tokens_source=engine`). If the runtime can't report it, the count of streamed chunks is used instead (`tokens_source=chunks`), and a chunk may not be exactly one token [VERIFY].
- **OOM kill:** an `ApplicationExitInfo` record for `<package>:inference`, timestamped during the run (or up to 2 min after its last row), with reason `LOW_MEMORY`, `MEMORY_LIMITER`, or `SIGNALED` with status 9 (SIGKILL). The last one is included because, per the ApplicationExitInfo reference on developer.android.com, devices that can't report `LOW_MEMORY` report a memory-pressure kill as `SIGNALED`/SIGKILL. The start row records `lmk_report=` (`ActivityManager.isLowMemoryKillReportSupported()`). A Java `OutOfMemoryError` in an error row also counts.

**A run is decided per model, per device.** Each model gets its own 50-prompt soak run. The gate passes on a device only if **both** models pass there.

**If it fails (PRD 0.7):** smaller model, shorter answers, typed chat only, and apply the **PRD 0.5 decision rule**:
- If the floor device fails but the same model passes on better hardware: (a) the recommended spec becomes ≥ 6 GB RAM and a recent chip; (b) the floor device stays the minimum for **typed chat with the smallest model only**, and voice is marked "not supported on this device".
- If it also fails on the better device: the PRD 0.7 fallback (smaller model, shorter answers) and re-run S2.
- Quality targets are not lowered silently.

The app's summary prints which branch applies (`Summary.outcome`). Note from `docs/project_status.md` → Test devices: the vivo Y17s has 6 GB but an older chip, so it does **not** count as the "better hardware" device for the 0.5 rule (open item 4). The Galaxy A23 is the floor (open item 10).

## 3. What the spike app does
- **Main screen** (plain Android views, no UI libraries):
  - device line and the model folder path
  - one **Soak 50** and one **Smoke 3** button per `.litertlm` file found
  - **Rescan model files**, **Stop run**, a live progress line
  - **Share newest run (CSV)**, **Share exit reasons**, **Share summary**
  - the newest run's summary.
- **Process split (PRD §11.3).** `InferenceService` runs in `android:process=":inference"`. The activity binds to it with a `Messenger`; during a run the service is also started, so the run doesn't depend on the binding. When `:inference` dies, the UI stays up, writes an `inference_died` row to the run file, and reads the exit reasons 2 s later.
- **Runtime: LiteRT-LM** (PRD §11.5 primary), `com.google.ai.edge.litertlm:litertlm-android:0.16.1`, **CPU backend** (PRD 0.5: don't rely on GPU/NPU on the floor device), default thread count [VERIFY what that is].
  - All library code is in `LiteRtLmEngine.kt`, behind `LlmEngine` (`load(path)`, `generate(prompt, maxTokens, onToken)`, `cancel()`, `close()`).
  - **Version pin:** the LiteRT-LM Kotlin guide says 0.17.0–0.18.1 need Kotlin ≥ 2.3, while AGP 9.0's built-in Kotlin is KGP 2.2.10 (AGP 9.0.0 release notes). 0.16.1 builds with Kotlin 2.2, so no KGP override is needed.
  - **llama.cpp is out of scope** for this spike. A second adapter behind the same `LlmEngine` interface is an [OPEN] follow-up (feature doc: "benchmark both if time allows").
- **Each prompt is a fresh single-turn chat:**
  - the same system instruction ("You are Bibi … at most three short sentences")
  - greedy decoding (topK = 1), so runs are repeatable
  - at most **128 new tokens** (`maxOutputToken`; whether it caps the whole reply is [VERIFY], so the summary prints the largest decode count seen)
  - 300 s timeout per prompt.
- **Prompt set:** `app/src/main/assets/prompts.tsv`, 50 short English assistant prompts (everyday questions, rewrites, short summaries, planning). The longest prompt plus the system instruction is ≤ 110 words, so it stays under 200 tokens; real counts are logged as `prefill_tokens`.
- **Smoke run:** the first 3 prompts. It checks that a model loads and streams. It is never valid for the gate.
- **One CSV per run:** `files/runs/s2_<yyyyMMdd-HHmm>_<device>_<model>_<mode>.csv`.
  - Row kinds:
    - `start`: file, bytes, device, Android, total RAM, `lmk_report`, prompt count, runtime
    - `load`: `load_ms`
    - one `prompt` row per prompt
    - `error`
    - an end row: `stop` (clean), `cancelled` (Stop pressed), `aborted` (load failed) or `inference_died` (written by the UI).
  - Prompt columns: `prompt_idx, prompt_id, prompt_chars, prefill_tokens, decode_tokens, tokens_source, chunks, ttft_ms, decode_tps, total_ms`, plus the runtime's own `eng_ttft_ms, eng_decode_tps` for cross-checking (LiteRT-LM's experimental benchmark counters).
  - Memory of `:inference`: `pss_kb` (`Debug.getPss()`, read between prompts because it is slow), `rss_kb`, and `rss_peak_kb` (VmHWM from `/proc/self/status`, which catches the peak during decoding). System memory: `avail_mem_mb`, `low_memory`.
  - Conditions on every row: `thermal` (PowerManager thermal status), `battery_pct`, `current_ua`, `charging`, `batt_temp_dc`, `screen_on`, `airplane`, `brightness`, `power_save`.
- **Exit reasons:** `files/exit_reasons.csv` (`time, epoch_ms, reason, status, detail, process, importance, pss_kb, rss_kb`), written by the UI process on launch, on resume and after an `:inference` death. Never cleared.
- **Summary and "VALID for gate".** The summary prints:
  - model, device, prompts finished, load time
  - TTFT p50/p95, decode median/p10
  - OOM kills, max PSS, peak RSS, minimum available RAM, max thermal status
  - battery start → end and mean current
  - the largest prefill and decode counts
  - then `VALID for gate: yes` or `no` with each reason, and `GATE S2 (<model>): PASS / FAIL — <failed checks>` or `no verdict`.

  A run is **not valid** if any of these apply:
  - it's a smoke run
  - there's no start row
  - fewer than 50 prompts finished, or no clean `stop` (unless an OOM kill ended it: then the run is valid and fails)
  - `:inference` died for a reason other than OOM
  - an error row
  - the screen was off
  - the phone was charging
  - airplane mode was off
  - battery saver was on
  - the battery was below 60 % at the start.

## 4. Model files (the owner downloads them; Gemma terms must be accepted by the owner)
LiteRT-LM loads **`.litertlm`** files. The app lists only that extension.

| Role | Hugging Face repo | File | Size | Source for name and size |
|---|---|---|---|---|
| Gemma 3 1B-class, int4 | `litert-community/Gemma3-1B-IT` (gated: accept the Gemma terms on the model page while logged in) | `gemma3-1b-it-int4.litertlm` | 584,417,280 bytes (≈ 557 MiB) | Google AI Edge Gallery `model_allowlists/1_0_15.json` (commit `42d538a932e8d5b12e6b3b455f5572560bd60b2c`). That it loads in runtime 0.16.1 is [VERIFY]. |
| 0.5B Qwen-class, int4 | **[VERIFY]** — candidates: `litert-community/Qwen2.5-0.5B-Instruct` or `litert-community/Qwen3-0.6B` | a `.litertlm` file in that repo's *Files* tab, int4/q4 if offered | [VERIFY] | **Not confirmed.** The LiteRT-LM README (tags v0.7.0–v0.16.1 and main) and the Gallery allowlists 1_0_7–1_0_15 list no Qwen model under 1.5B. Hugging Face could not be reached from where this was written. |

**Rules for the Qwen file:**
- Pick a `.litertlm` file, not `.task`. `.task` is the older MediaPipe format; whether runtime 0.16.1 loads it is [VERIFY], and the app ignores it.
- Prefer a 4-bit (`q4`/`int4`) file. If only 8-bit (`q8`) exists, run it anyway and write "q8, no int4 available" in the result notes: it deviates from the PRD's "int4".
- Record the exact file name, size and Hugging Face commit for each model in the results.
- Licences (PRD §13.10): Gemma terms of use; Qwen licence per checkpoint [VERIFY before any release].

**Getting files onto the phone:** `adb push` into the app's own folder on external storage. No storage permission or in-app picker is needed.
- Folder: `/sdcard/Android/data/io.github.saadahmed1122.bibi.spike.s2/files/models/`. The app creates it on first launch and shows the path on screen.
- Fallback if `adb push` is refused there [VERIFY on vivo]: the app also scans `files/models` in private storage. See README.

## 5. Plan (one commit per task)
1. Gradle skeleton (AGP 9.0.1 built-in Kotlin, Gradle 9.1.0 wrapper from S1, compileSdk/targetSdk 36, minSdk 34) and manifest with `:inference`.
2. `LlmEngine` interface and the LiteRT-LM adapter.
3. `InferenceService`, benchmark loop, `BenchLog` CSV, prompt set, timing helpers.
4. `Summary` with JVM tests (`tools/summary-test/run.sh`) and `tools/typecheck.sh`.
5. `MainActivity`: model list, run control, results, sharing.
6. SPEC and README.

## 6. Test protocol (owner runs on the device)
Run on the **Galaxy A23** (floor) if available and on the **vivo Y17s**. Label every result by device.

**Order per device:**
1. Smoke 3 for each model (checks it loads; not a gate run).
2. Soak 50: Gemma.
3. Let the phone cool down until the battery temperature is back near where it started (about 10 min, screen off, not charging).
4. Soak 50: Qwen.
5. Optional, recommended by the feature doc's test plan: repeat the Gemma soak **with S1's overlay running** (start *Bibi Calm 2 fps* in the S1 app first). Label it "with overlay". Whether this run is required for the gate is [OPEN].

**Before every soak:**
- Battery **≥ 60 %**, unplugged. The run is invalid below 60 % or if charging.
- **Airplane mode on**. Wi-Fi and Bluetooth off.
- Battery saver off.
- Same fixed brightness, auto-brightness off.
- Close other apps from Recents (except the S1 app in the optional overlay run).
- vivo: write down whether **extended RAM** is on or off and keep it the same for every run (project_status.md → Test devices).
- Note the room temperature roughly.

**During the soak:**
- Leave the S2 app open in front with the screen on. The app keeps the screen on while a run is going.
- Don't swipe the app away: a swipe-kill reads as SIGKILL and would count as an OOM kill.
- A soak takes roughly 50 × (TTFT + up to 128 tokens ÷ speed). At 5 tokens/s that's about 25 min.

**After each run:**
- Check `VALID for gate` and the `GATE S2` line.
- Tap **Share newest run (CSV)**, then **Share summary**, and attach both to issue #5.
- At the end of the session, tap **Share exit reasons**.
- An invalid run is repeated, not averaged in.

## 7. Result template (goes into `results/RESULTS.md`, `docs/project_status.md` and the spike tracker)
| Device | Model file | Mode | VALID | Prompts | Load ms | TTFT p50 / p95 ms | Decode median / p10 tok/s | OOM kills | Max PSS MB | Peak RSS MB | Min avail MB | Max thermal | Battery start → end | Extended RAM | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

Then apply `Summary.outcome(floorPass, betterPass)`: PASS, PRD 0.5 rule, PRD 0.7 fallback, or "need a better device". Record the decision as a dated line in `docs/project_status.md` → Decisions.

## 8. Known limits of this spike
- **Not built or run.** Google Maven and Hugging Face were unreachable where this was written.
  - Everything except `LiteRtLmEngine.kt` was type-checked against the Android 16 (API 36) framework classes (`tools/typecheck.sh`).
  - `Summary`, `PromptSet` and `Metrics` were tested on a plain JVM with synthetic logs.
  - **`LiteRtLmEngine.kt` is unverified.** It was written from the LiteRT-LM Kotlin guide (`docs/api/kotlin/getting_started.md`) and the v0.16.1 tag sources (`Config.kt`, `Engine.kt`, `Conversation.kt`, `Benchmark.kt`, `ExperimentalFlags.kt`) on raw.githubusercontent.com. Context7 was unreachable.
- **The engine counters** (`eng_*`, and `decode_tokens` when `tokens_source=engine`) come from LiteRT-LM's `@ExperimentalApi` benchmark mode. It may change, and it may add overhead [VERIFY]. The gate uses the app-clock TTFT and the app-clock decode interval.
- **No prefix/KV caching** of the system instruction. P1 would cache it (PRD §13.3), so TTFT here is an upper bound.
- **CPU only, default threads.** Trying a fixed thread count (e.g. 4 on the Helio G85's 2 + 6 cores) is a follow-up [OPEN].
- **mmap vs fully loaded weights** (PRD §13.3) is not compared. The runtime's default is used.
- **Single runtime.** llama.cpp is [OPEN].
- **One device class per run file.** Results are labelled single-device (PRD 0.12 item 4).
