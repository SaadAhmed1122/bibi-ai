# Spike S3 — results (S3a TTS #6, S3b STT #7)

Gates (PRD 0.7, SPEC.md §2): **S3a** TTS real-time factor (RTF) ≤ 1.0. **S3b** STT RTF ≤ 0.7 **and** peak memory ≤ 600 MB.
Verdict rules follow SPEC.md §2 (`Summary.kt`): median RTF over all measured items, p95 by nearest rank, warmup excluded, peak PSS of the `:inference` process.

## 1. Verdicts (vivo Y17s, 2026-10-29)

| Gate | Verdict | Evidence |
|---|---|---|
| **S3a TTS: Kokoro** (en v0.19 fp32, multi-lang v1.1 int8) | **FAIL** | Best Kokoro is en v0.19 at 4 threads: median RTF 3.769 (complete run only). That is about 3.8× slower than real time; the gate is ≤ 1.0. |
| **S3a TTS: Piper en_US amy low** (PRD 0.7 fallback voice) | **PASS** on speed; listening check not done | Median RTF 0.248 at 4 threads (p95 0.271), 0.297 at 2 threads (p95 0.331). Speech quality hasn't been heard yet: no *Play*-on run in the logs. |
| **S3b STT: small streaming Zipformer en 20M** | **PASS** on the numeric gate; **not usable as the default recognizer** | Median RTF 0.177 at 2 threads (p95 0.191), peak PSS 119.3 MiB, peak RSS 196.2 MiB. But the model itself drops the first ~1–2 s of every fresh stream, so it loses the start of each utterance (§4.2). |
| **S3b STT: Parakeet TDT 0.6B v2 int8** | **FAIL** on memory; **default by owner override (2026-10-29)** | RTF passes: median 0.324 at 4 threads, 0.433 at 2 threads. But peak PSS is 903.3 MiB (≈ 947 MB), well above the 600 MB limit. |

**PRD 0.7 outcomes:**
- **S3a:** Kokoro fails, so on this tier we switch to the lighter voice. Piper passes on speed. Kokoro is out for the 6 GB Helio G85 tier, and very likely for the Galaxy A23 too [VERIFY on A23].
- **S3b:** default recognizer is **Parakeet TDT 0.6B v2 int8, by owner override (2026-10-29)** of the PRD 0.7 memory limit: it fails only on memory (peak PSS 903 MiB vs 600 MB), passes RTF and transcribes best. Recorded in `docs/project_status.md` → Decisions.
  - Zipformer 20M passes RTF and memory but can't be the default because it loses utterance starts.
  - Risk accepted with the override: memory headroom on the 4 GB Galaxy A23 and with the S2 LLM loaded at the same time [VERIFY].

Caveat: this is one device and one session, with the phone charging, and only one complete run per model and thread count. The Galaxy A23 floor device wasn't tested (§6).

## 2. Run conditions (from the logs and the owner's report)

| Item | Logged value | Matches owner report? |
|---|---|---|
| Device | `vivo-V2310` on every row (vivo Y17s; Android 15, Helio G85, 6 GB per `docs/project_status.md`) | Yes |
| Extended RAM setting | Not logged | **[VERIFY]**: owner to record the setting |
| Runtime | `sherpa-onnx 2.0.1` in every `start` row | Yes |
| Rounds | `rounds=3` in every `start` row, plus one warmup | Yes |
| Play | `play=false` in every TTS `start` row | Yes (gate runs). The separate *Play*-on listening runs (README step 6) are **not in the logs** |
| Threads | `threads` column matches the `_t2`/`_t4` file name in all 13 files | Yes |
| Charging | `charging=true` on every row of all 13 files | Yes (USB). Charging warms the phone (SPEC §7) |
| Battery | 61–64 % (61 at 22:22, 64 at 23:14) | Yes |
| Thermal | `thermal` = 0 on every row of all 13 files | No heat was flagged. None of the runs reached the repeat threshold (≥ 2) |
| TTS speaker | `sid=2` for Kokoro en v0.19; `sid=0` for Kokoro int8 and Piper | Not reported by the owner. The voices differ, so audio lengths differ for the same sentence (e.g. short: 2.64 s Kokoro en, 2.10 s Kokoro int8) |
| Cool-down between runs | 139–154 s for every run from 22:38 on. Gaps around the abandoned Kokoro runs: 275, 38, 80 and 125 s | Mostly matches the ~2 min protocol. The 38 s gap came before an abandoned run |
| Screen | Kept on with `svc power stayon usb`; runs driven over adb | From the owner's report; the logs don't record it |

## 3. S3a TTS — numbers

All values were computed from the raw CSVs by `results/analyze.py` (`python3 -I analyze.py raw/2026-10-29-vivo-y17s`). "MiB" = KiB from `pss_peak_kb`/`vmhwm_kb` ÷ 1024; that is the same unit `Summary.kt` prints as "MB".

| Model | Threads | Runs used | Items | Median RTF | p95 RTF | Min–max RTF | Peak PSS | Peak RSS (VmHWM) | Load | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| Kokoro en v0.19 (fp32) | 2 | 1 complete (22:22) | 9 | **4.305** | 4.529 | 4.056–4.529 | 717.2 MiB | 785.9 MiB | 7.20 s | FAIL |
| Kokoro en v0.19 (fp32) | 4 | 1 complete (22:38) | 9 | **3.769** | 3.879 | 3.473–3.879 | 717.5 MiB | 794.9 MiB | 6.42 s | FAIL |
| Kokoro multi-lang v1.1 (int8) | 2 | 1 | 9 | **4.936** | 5.075 | 4.747–5.075 | 506.2 MiB | 585.6 MiB | 7.71 s | FAIL |
| Kokoro multi-lang v1.1 (int8) | 4 | 1 | 9 | **4.853** | 5.058 | 4.600–5.058 | 506.7 MiB | 585.5 MiB | 7.71 s | FAIL |
| Piper en_US amy low | 2 | 1 | 9 | **0.297** | 0.331 | 0.290–0.331 | 311.5 MiB | 390.8 MiB | 7.70 s | PASS |
| Piper en_US amy low | 4 | 1 | 9 | **0.248** | 0.271 | 0.241–0.271 | 326.0 MiB | 405.4 MiB | 7.31 s | PASS |

**Per sentence (median compute time / audio length):**
- **Kokoro en v0.19, 4 threads:**
  - short: 10.0 s for 2.63 s of audio
  - medium: 24.8 s / 6.56 s
  - long: 56.6 s / 16.25 s

  Time to first audio (SPEC §9 proxy, short sentence) is about 10 s.
- **Piper, 4 threads:**
  - short: 0.7 s / 2.74 s
  - medium: 1.4 s / 5.81 s
  - long: 3.7 s / 14.98 s

  The short-sentence proxy for first audio is about 0.7 s. That is close to the PRD Appendix's "first audio ≤ 800 ms" target, but this spike doesn't measure first audio [VERIFY].

### 3.1 Kokoro en v0.19: incomplete runs excluded, verdict unchanged
Three Kokoro en files have no `stop` row:

| File | Threads | Measured items | Why it stopped |
|---|---|---|---|
| `s3_20261029-223234_…_t4.csv` | 4 | 1 | Owner used the phone: `USER_REQUESTED [REMOVE TASK]` for the app and `:inference` at 22:33:17 |
| `s3_20261029-223337_…_t2.csv` | 2 | 2 | `run_abandoned` ("activity destroyed") + `REMOVE TASK` at 22:35:43 |
| `s3_20261029-223605_…_t2.csv` | 2 | 1 | `run_abandoned` + `REMOVE TASK` at 22:37:07 |

None has a `process_died` row, and the exit reasons are `USER_REQUESTED` rather than `LOW_MEMORY` or a crash. **These are not model deaths.**

| Kokoro en v0.19 | All files (app summary) | Complete runs only |
|---|---|---|
| 2 threads | median 4.386, p95 5.072, 12 items, 3 runs (2 unfinished) | median **4.305**, p95 4.529, 9 items |
| 4 threads | median 3.716, p95 3.879, 10 items, 2 runs (1 unfinished) | median **3.769**, p95 3.879, 9 items |

- The all-files columns reproduce the app's `Summary.kt` output exactly. Peak PSS stays the same (717.2 / 717.5 MiB), because the complete runs hold the maximum.
- The two abandoned 2-thread runs measured RTF 4.66–5.07, slower than the clean run (4.06–4.53). The owner was using the phone at the time, so CPU contention is the likely cause [VERIFY]. That is one more reason to exclude them.
- **The verdict is unchanged: FAIL at both thread counts.** The median is ≥ 3.7× the gate either way.

### 3.2 Observations
- **Kokoro int8 isn't faster than fp32 here.** At 4 threads int8 measured 4.853 against fp32's 3.769; at 2 threads, 4.936 against 4.305. int8 only saves memory: 506 MiB PSS against 717 MiB. The reason (int8 kernels on the Cortex-A55/A75 cores, or the different v1.1 model) is not established [VERIFY].
- **Kokoro's memory is a concern even apart from speed.** 717 MiB PSS for TTS alone, next to an LLM on a 4 GB Galaxy A23, is unlikely to fit [VERIFY on A23]. PSS isn't a gate for S3a.
- **4 threads helps Kokoro fp32 (−12.5 %) and Piper (−16 %)** but barely changes Kokoro int8 (−2 %).
- **Piper's audio length varies a little between runs** for the same sentence (short: 2.67 s at 2 threads vs 2.74 s at 4 threads). That fits VITS's stochastic duration predictor; RTF normalizes for it.
- **Words missing from the lexicon** may be dropped or mispronounced (SPEC §3). Nobody has listened yet [VERIFY with a *Play*-on run].

## 4. S3b STT — numbers

| Model | Threads | Items | Median RTF | p95 RTF | Min–max RTF | Peak PSS | Peak RSS (VmHWM) | Load | Verdict |
|---|---|---|---|---|---|---|---|---|---|
| Streaming Zipformer en 20M | 2 | 9 | **0.177** | 0.191 | 0.163–0.191 | 119.3 MiB (≈ 125 MB) | 196.2 MiB | 1.72 s | PASS (numeric); loses utterance starts |
| Streaming Zipformer en 20M | 4 | 9 | **0.212** | 0.252 | 0.194–0.252 | 120.7 MiB (≈ 127 MB) | 196.3 MiB | 1.53 s | PASS (numeric); loses utterance starts |
| Parakeet TDT 0.6B v2 int8 | 2 | 9 | **0.433** | 0.441 | 0.413–0.441 | **902.7 MiB (≈ 947 MB)** | 976.5 MiB | 11.35 s | FAIL (memory) |
| Parakeet TDT 0.6B v2 int8 | 4 | 9 | **0.324** | 0.347 | 0.313–0.347 | **903.3 MiB (≈ 947 MB)** | 976.6 MiB | 10.11 s | FAIL (memory) |

- **Unit check:** the gate says "600 MB". Parakeet fails it in both MiB and MB, and Zipformer passes it in both, so the unit question doesn't change any verdict.
- **Zipformer is slower with 4 threads (0.212) than with 2 (0.177).**
  - The Helio G85 has 2 big Cortex-A75 cores and 6 little A55 cores, so the 3rd and 4th threads probably land on little cores [VERIFY].
  - Any streaming recognizer measured next should be tried at 2 threads first [VERIFY per model].
- **Parakeet's PSS rises across the run,** from 731 MiB after load to 903 MiB on the 12.9 s clip. On this device memory is the blocker, not speed.
- **Every item has a memory reading and a `stop` row.** No STT run died.

### 4.1 Clips
| Clip | Logged length | SPEC target (§7) | Reference prompt |
|---|---|---|---|
| clip1 | 3.680 s | about 2 s | "Set a timer for ten minutes." |
| clip2 | 4.720 s | about 6 s | "What is the weather going to be like tomorrow morning, and should I take an umbrella?" |
| clip3 | 12.920 s | about 10 s | "Remind me to buy milk, bread and eggs on the way home, then open my music app and play something calm while I cook dinner tonight." |

**clip1 seems to contain extra speech.** Both recognizers hear "…morning tomorrow" at its end:
- Parakeet: "See the demo for ten minutes morning tomorrow."
- Zipformer: "S MORNING TO MORROW"

clip2 seems to end with "with me", which both models hear but the reference lacks. Re-record clip1 (and clip2 if the owner didn't say "with me") before any accuracy judgment.

### 4.2 Transcripts (identical across all rounds and both thread counts)
| Clip | Parakeet | Streaming Zipformer |
|---|---|---|
| clip1 | See the demo for ten minutes morning tomorrow. | S MORNING TO MORROW |
| clip2 | What is the weather going to be like tomorrow morning and should I take an umbrella with me? | IN TO BE LAID TO MORROW MORNING AMATEUR TAKING MBRELLA WITH ME |
| clip3 | Remind me to buy milk; bread and eggs to be home; then open my music app and play something calm while I cook dinner tonight. | S TO WEAR HOME THEN OPEN MY MISSY CAP AND PLEASANT THING COME WHILE I GO DINNER TO NIGHT |

**Parakeet is near-perfect** apart from "to be home" for "on the way home", and "See the demo" where the clip1 recording itself is in doubt.

**Zipformer loses the start of each utterance** and keeps mostly the tail ("…S TO WEAR HOME THEN OPEN MY…").
**Root cause: the model, not the adapter.** This comes from android-engineer's desktop investigation on 2026-10-29, not from these device logs. The repro scripts are in the session scratchpad, not in the repo.
- **The model loses the start of a fresh stream.** sherpa-onnx-streaming-zipformer-en-20M-2023-02-17 drops roughly the first 1–2 s of audio from a fresh stream, because its encoder state starts cold.
- **Its own test audio shows the same loss.** On a desktop with Python sherpa-onnx 2.0.1 and the same flow as the app, the model's own `test_wavs/0.wav` loses "AFTER EARLY NIGHTFALL". That matches the upstream k2-fsa/sherpa docs for this model, where the first token appears at 2.04 s.
- **Nothing in the setup changes it:**
  - sherpa-onnx 1.12.34 and 1.13.8
  - fp32 and int8 files
  - 100 ms chunks and the whole clip at once
- **A warm stream works.** Feeding the clip twice into one stream decodes the second copy perfectly. Short clips can come out empty.
- **Workarounds don't fix it reliably:**
  - Leading silence is erratic: 0.3 s gave a partial result, 1.0 s an empty one, 2.0 s a perfect one.
  - `modified_beam_search` recovers only part of it.
- **The adapter is correct.** `SherpaEngines.kt:76–120` matches upstream, with `enableEndpoint = false`. There's no adapter fix to make, so the RTF and PSS above stand as measured.
- **Conclusion:** Zipformer 20M passes the numeric gate but is **not usable as the default recognizer**. Users would lose the first words of every command, and those words carry the command ("Set a timer…", "Remind me…").

**Alternatives seen on desktop only (not measured on device, not part of this spike's model list):**
- `sherpa-onnx-streaming-zipformer-en-2023-06-26`: keeps the start. Its desktop RTF is about 2× the 20M model's. On-device RTF and PSS are unknown [VERIFY].
- `sherpa-onnx-streaming-zipformer-en-kroko-2025-08-06`: keeps the start and adds punctuation, but drops the last word(s). License unchecked [VERIFY].

Measuring either one would change the S3b model list (SPEC §5). That needs the owner's decision and a dated entry in `docs/project_status.md` → Decisions first.

### 4.3 Parakeet re-run with re-recorded clip1 (23:45–23:49)
After the override decision, clip1 was re-recorded (4.4 s, only "Set a timer for ten minutes.") and Parakeet was run again, charging (66 %), same settings:

| Threads | Median RTF | p95 RTF | Peak PSS | Items | Clean stop |
|---|---|---|---|---|---|
| 4 | 0.336 | 0.346 | 902 MiB | 9 | yes |
| 2 | 0.426 | 0.438 | 899 MiB | 9 | yes |

All 9 transcripts per run are identical across rounds; clip1 now reads exactly "Set a timer for ten minutes." Numbers match the first runs (0.324 / 0.433), so the verdict and the override stand. Files: `raw/2026-10-29-vivo-y17s/s3_20261029-234458_…_t4.csv` and `…-234809_…_t2.csv`. The battery run is still not done (the phone stays on USB for adb).

## 5. Process exits and anomalies
`exit_reasons.csv` (copied unchanged):
- **21:20–21:31: two `CRASH` and four `USER_REQUESTED` (REMOVE TASK / FORCE STOP) exits of the main process.** They happened before the first run file (22:22). No run data comes from that window. Most likely setup or debugging [VERIFY with the owner].
- **22:28:00: `EXIT_SELF` for `:inference`.** This is the expected clean exit after the 22:22 run.
- **22:33–22:37: `REMOVE TASK` and `run_abandoned` rows** for the three incomplete Kokoro runs (§3.1).
- **No `LOW_MEMORY`, `CRASH_NATIVE` or `inference_died` entries in any run window.**
- **The exit log has no `EXIT_SELF` rows after 22:28,** even though ten more runs finished cleanly.
  - `ExitLog.logNewExitReasons()` only runs when the activity is created (`MainActivity.kt:83`) or after a death (`:225`). The activity wasn't recreated after 22:37, so later exits were never synced before the export.
  - Every later run has a `stop` row and no `process_died` row, so none of them died.
  - Confirming the exit reasons for 22:38–23:17 would need a fresh export after reopening the app [VERIFY].

## 6. What is not measured
- **Galaxy A23 (floor device): not tested.** This is the device most likely to change the S3a outcome for Kokoro and Piper and the S3b memory headroom, because of its RAM.
- **Not charging:** every run was on USB power. One Piper run on battery (and one of any new STT candidate) would show whether charging affected speed.
- **Repeatability:** one complete run per model and thread count. The rule calls for "repeat before deciding" only on BORDERLINE results, and there were none. A second Piper run at 4 threads would still firm up the chosen voice configuration.
- **Voice quality:** no *Play*-on listening runs.
- **STT accuracy:**
  - Zipformer 20M loses utterance starts. That is a model limit (§4.2), so a re-run won't change it.
  - No alternative streaming model has been measured on the device.
  - clip1 needs re-recording before any accuracy comparison.
- **Latency:** time to first audio and partial-result latency are outside this spike (SPEC §9).
- **Extended-RAM setting** on the vivo isn't recorded [VERIFY].

## 7. Next runs
1. **Parakeet (default by override):** clip1 re-recorded and Parakeet re-run (§4.3, done). Still to do: one run on battery, and Parakeet with the S2 LLM loaded to see the memory headroom. A streaming candidate such as `streaming-zipformer-en-2023-06-26` is now optional; if measured:
   - Re-record clip1 (only "Set a timer for ten minutes.", about 2–4 s) and, if needed, clip2.
   - Run the new candidate at 2 and 4 threads and *Parakeet* at 2 threads, so the new model and the reference transcripts hear the same clips.
   - Check RTF ≤ 0.7, PSS ≤ 600 MB, and that the utterance starts are kept.
2. **Piper:** one *Play*-on run with 2 threads. Note any mispronounced or skipped words in #6.
3. **On battery (not charging):** Piper at 4 threads, plus the new STT candidate if one is approved.
4. **Galaxy A23:** the full protocol, if it's available.
5. **After reopening the app:** *Share exit reasons* again to capture the 22:38–23:17 exits.

## 8. Files
- `raw/2026-10-29-vivo-y17s/`: the 13 run CSVs and `exit_reasons.csv`, as pulled from the phone on 2026-10-29. Unchanged; SHA-256 checked against the pulled copies.
- `analyze.py`: the script that produced every number in this file. Run it as `python3 -I analyze.py raw/2026-10-29-vivo-y17s`. It reproduces the app's `Summary.kt` (all files) and adds complete-runs-only, per-sentence and transcript views.
