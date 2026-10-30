# Spike S2 — results (issue #5)

Gate (PRD 0.7, SPEC.md §2): median decode ≥ 5 tokens/s, p95 time to first token (TTFT) ≤ 3,000 ms, 0 out-of-memory kills, over a valid 50-prompt soak. LiteRT-LM 0.16.1, CPU. Raw logs: `raw/2026-10-30-vivo-y17s/`.

## 1. Verdict: Gemma 3 1B int4 on vivo Y17s

| Check | Result | Gate | |
|---|---|---|---|
| Valid run | yes: 50/50 prompts, unplugged (75 → 73 %), airplane mode on, battery saver off, clean stop | — | ✓ |
| Decode median | **6.54 tok/s** (p10 6.44) | ≥ 5 | PASS |
| OOM kills | **0** | 0 | PASS |
| TTFT p95 | **3,493 ms** (p50 2,425 ms) | ≤ 3,000 | **FAIL** |

**GATE S2 (gemma3-1b-it-int4, vivo Y17s): FAIL — p95 TTFT 3,493 ms > 3,000 ms.** Computed by the app's `Summary.kt`.

Other numbers: load 11.3 s; max PSS 1,161 MB (`:inference`, between prompts); peak RSS 1,238 MB; minimum system available RAM 2,828 MB; thermal 0; mean current 254 mA. File `gemma3-1b-it-int4.litertlm`, 584,417,280 bytes. Run started 2026-10-30 01:17 by the owner.

## 1b. Verdict in chat mode (PR #23): Gemma 3 1B int4 on vivo Y17s — PASS

Chat soak 50 (2026-10-30 02:01, started by the owner): one conversation, `system_bibi.txt` (310 tokens incl. a scripted greeting exchange) prefilled once before timing, then the 50 prompts as consecutive turns. Unplugged (78 → 78 %), airplane mode on, battery saver off.

| Check | Result | Gate | |
|---|---|---|---|
| Valid run | yes: 50/50, clean stop, preface prefilled (310 tokens, 9,929 ms, untimed), 0 conversation resets | — | ✓ |
| TTFT p95 | **2,409 ms** (p50 1,305 ms) | ≤ 3,000 | PASS |
| Decode median | **7.04 tok/s** (p10 6.54) | ≥ 5 | PASS |
| OOM kills | **0** | 0 | PASS |

**GATE S2 (gemma3-1b-it-int4, chat mode): PASS** (app `Summary.kt`). Max PSS 1,189 MB; peak RSS 1,262 MB; min available RAM 2,790 MB; thermal 0; load 10.3 s. The slowest turns (2.39–2.45 s) had the longest user messages (44–58 tokens) late in the conversation (context 1,209–2,196 tokens); the context reached 2,261 of 4,096 tokens.

Whether this chat-mode result decides the S2 gate (instead of the per-prompt soak in §1) is an **owner decision [OPEN]**. Earlier chat-mode files `…0141…` (flag only; preface not prefilled, turn 1 TTFT 10.3 s) and `…0159…` (plugged-in check after the fix) are device checks, not gate runs.

## 2. Why TTFT fails

TTFT grows with prompt length: about **50 ms per prefill token** on this phone (~20 tokens/s prefill).

| Prefill tokens | n | TTFT median | TTFT max |
|---|---|---|---|
| 40–60 | 44 | 2,421 ms | 2,662 ms |
| 72–86 | 6 | 3,440 ms | 3,719 ms |

Only the six longest prompts break 3 s. A real Bibi prompt adds a system prompt, persona and memory, so prefill will be **longer** than in this soak unless the fixed part is prefilled once and reused (KV cache / prefix reuse) [VERIFY whether LiteRT-LM 0.16.1 keeps the conversation prefix between turns].

## 3. Other runs on this phone (not gate runs)

| Run | Kind | Result | Why it doesn't count |
|---|---|---|---|
| Gemma 3 1B int4, 2026-10-30 00:11 | smoke 3 | TTFT 2.1–2.5 s, 6.5–6.7 tok/s, PSS 1.1 GB | smoke |
| Qwen3-0.6B `qwen3_0_6b_mixed_int4` (497,516,544 bytes), 10-29 23:24 | smoke 3 | engine TTFT 4.4–6.0 s, 7.2–8.1 tok/s, PSS ~2.1 GB | smoke; charging, airplane off |
| Qwen3-0.6B `dynamic_wi4b32_afp32`, 10-29 23:20 / 23:40 | smoke / soak | ~2.2 tok/s, TTFT 7.6–8.6 s; soak hung on prompt 4 (`no onDone within 300 s`) | not valid |
| Qwen2.5-1.5B q8, 10-29 20:56 | soak | 25/50 prompts, 3.73 tok/s, TTFT p95 3,820 ms | swiped away, airplane off, battery 29 % |
| Gemma 4 E2B, 10-30 00:06 | smoke 3 | TTFT ~3.0–3.2 s, ~5.3 tok/s, PSS ~2.0 GB, 2.47 GB file | smoke; removed from the phone at the owner's request |

No Qwen 0.5B-class soak has been run, so the SPEC's second model is **not measured**. The only Qwen candidate that loaded well (mixed int4) had a *slower* first token than Gemma 3 1B in its smoke test.

## 4. What PRD 0.7 / 0.5 say next (owner decision) [OPEN]

PRD 0.7 on S2 fail: smaller model, shorter answers, typed chat only. PRD 0.5: if the floor device fails and the same model passes on better hardware, apply the decision rule. Options, roughly by cost:
1. **Prefix reuse:** prefill the fixed system prompt once and keep it, so per-turn TTFT only covers the user's message. Measure with the same 50 prompts plus a realistic system prompt.
2. **Shorter prompts:** cap per-turn prefill (the soak's ≤ 60-token prompts all passed, max 2,662 ms).
3. **GPU backend** (Mali-G52 MC2) for prefill [VERIFY support on 0.16.1].
4. **Accept a 3.5 s p95 on this device** as an override of PRD 0.7, recorded as a Decision.
5. **Galaxy A23** (floor device per PRD 0.4) is still untested; it may be slower.

## 5. Not covered [VERIFY]

- Only the vivo Y17s; extended-RAM setting not recorded.
- No Qwen 0.5B-class soak.
- LLM co-resident with Parakeet STT was checked only with an idle Qwen3 (S3 RESULTS §4.4).
