# P2 Task 1 (#72): memory check, Gemma with the router and voice models

**Date:** 2026-10-31, 22:18–22:46 phone time (IST).
**Device:** vivo Y17s (V2310), Android 15. MemTotal 5,744 MB, plugged in over USB, screen on.
**Question:** can Gemma 3 1B (while writing), the Decision Maker router, Parakeet and Piper stay loaded together, or should the voice models load on demand (SPEC-voice.md)?

## Method

- **Gemma:** the P1 debug build's `:inference` process, kept writing through the floating chat card while the other apps stayed in front. The prompt was "Tell me a long story about a lighthouse keeper and his cat" (answers of 142–256 chunks).
- **Parakeet and Piper:** the S3 spike app, using its own run buttons and run CSVs.
  - Parakeet TDT 0.6B v2 int8, 4 threads, 3 rounds of 3 clips.
  - Piper en_US amy low, 2 threads, 10 rounds.
- **Router:** the S4 spike app. "Load models" loads the Decision Maker only (Gemma is not loaded in S4), then the 110-request LLM benchmark runs in dry-run mode.
- **Memory:** sampled from the laptop about every 10 s, as `MemAvailable` plus each Bibi process's PSS (`dumpsys meminfo -c`), in `memory_samples.csv`. Its `time` column is the laptop clock, 4 h 30 min behind the phone.
- **Limits:**
  - The models ran in three apps, not one `:inference` process.
  - The phone was charging.
  - Other apps were open as usual.
  - Each pair was measured separately (Gemma + Parakeet, Gemma + Piper, Gemma + router), not all four at once.
  - This is the 6 GB vivo, not the 4 GB Galaxy A23 [VERIFY].

## Results

| Running together | Lowest MemAvailable | Peak PSS (MB) | Speed alone → together |
|---|---|---|---|
| Gemma writing only | 2,146 MB | Gemma `:inference` 1,115; app 226 | first word 1.43–1.59 s; 186 chunks in 34–36 s |
| Gemma + Parakeet | **1,604 MB** | Parakeet 940 (load 23.2 s, was 10–11 s) | Parakeet median **0.32 → 0.74** (max 1.32; gate 0.7). Gemma: 146 chunks in 52.8 s; a conversation reopen took 39.9 s (≈ 8 s alone) |
| Gemma + Piper | 2,443 MB | Piper 376 | Piper **0.30 → 0.40–0.49** during the overlap (all 30 items: median 0.39, max 0.49). Gemma: first word 3.4 s, 187 chunks in 48.2 s |
| Gemma + router | 2,310 MB | S4 with the router 672 (bench PSS 593) | router median **489 → 815 ms**, p95 513 → 963 ms; same accuracy (95 calls, 9 no action, 6 asks). Gemma: first word 2.6 s, 256 chunks in 72.0 s |

The speeds compare against the earlier runs alone: S3 RESULTS §4 (Parakeet 0.32–0.35, Piper 0.297 t2) and S4 RESULTS §8 (router 489/513 ms).

**Exits:** no Bibi, S3 or S4 process was killed during the check. The only exits were Bibi's `PACKAGE UPDATED` at 22:13, from the install before the check, and S3 `:inference` `EXIT_SELF` after each run. S4 has a `LOW_MEMORY` exit at 16:29, hours before the check.

## Verdict

1. **Memory: any two fit.** The worst pair (Gemma + Parakeet) left 1.6 GB free and nothing was killed.
2. **All four at once would be tight.** Adding the peaks gives about 1,115 + 940 + 376 + 593 + 226 ≈ **3.25 GB**, from roughly 3.9 GB that was free with nothing loaded. About 0.6–0.7 GB would be left, which is the low-memory-kill zone. On a 4 GB A23 it doesn't fit.
3. **The CPU is the real limit.** Running at the same time slows everything:
   - Parakeet falls past its 0.7 gate.
   - Gemma's answers take 33–50 % longer.
   - A conversation reopen goes from about 8 s to 40 s.
   - Piper stays faster than real time (≤ 0.49), so speaking while the answer streams works.

## Decision for SPEC-voice (voice on demand)

- **Router:** stays loaded with Gemma (needed for every message; 0.6 GB).
- **Parakeet:** loads when the mic button is pressed, and the recording overlaps the 10–11 s load. It unloads after 2 minutes idle. Transcription never runs while Gemma is writing: Send and the mic are already one-at-a-time.
- **Piper:** loads when the speaker toggle is on and unloads when it goes off. Speaking during streaming is fine.
- **Idle summaries (SPEC-memory):** they must not overlap a voice turn either, which is already the idle-only rule.
- **Not measured:**
  - all four models in one process;
  - Parakeet's load time when the mic is pressed during real use;
  - the A23 [VERIFY].
