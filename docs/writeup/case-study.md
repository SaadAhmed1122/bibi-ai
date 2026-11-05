# Case study: Bibi AI, an offline AI companion on a mid-range Android phone

P4 Task 11 (#136), [SPEC-writeup.md](../../SPEC-writeup.md) criterion 2 (portfolio package). Numbers trace to the sources table in [linkedin-article.md](linkedin-article.md). Unmeasured results are named as such.

## Summary
| | |
|---|---|
| **What** | An Android app: an animated kitten that floats over other apps, chats with a local LLM, listens and speaks, remembers what you ask it to, and does five phone actions |
| **Constraint** | Fully offline: no `INTERNET` permission, no account, no analytics. Each model pack is 2 GB or less |
| **Test phone** | vivo Y17s: 6 GB RAM, MediaTek Helio G85, Android 15 |
| **Role** | Solo: product, design direction, Android engineering, measurement. Built with Claude Code as the pair programmer |
| **Stack** | Kotlin, Jetpack Compose, Material 3, Room, WorkManager; LiteRT-LM 0.16.1 (Gemma 3 1B int4), MediaPipe (EmbeddingGemma router), sherpa-onnx (Parakeet, Piper) |
| **Status** | Closed test on Google Play (0.3.x-p3). Crash-free and retention numbers come when the test ends |
| **Code** | [github.com/SaadAhmed1122/bibi-ai](https://github.com/SaadAhmed1122/bibi-ai) |

## The problem
On-device AI demos usually run on flagship phones or fall back to a server. The question here: can an ordinary 6 GB phone run a companion that feels responsive, with chat, voice, memory and actions, without any network at all?

## Approach
1. **Spike first.** Four throwaway test apps for the four riskiest parts, each with a pass or fail number set before testing (PRD 0.7):

   | Spike | Gate | Result |
   |---|---|---|
   | S1 floating character | ≤ 3 % battery/h | +28 mA, about 0.5 %/h: **pass** |
   | S2 chat | first word ≤ 3 s (p95) | 3.5 s per-prompt (**fail**) → 2.4 s with one ongoing chat (**pass**) |
   | S3 voice | speech out ≤ 1.0, speech in ≤ 0.7 s per 1 s of audio | Piper 0.25, Parakeet 0.32: **pass** (Parakeet over the memory limit, kept by owner decision) |
   | S4 actions | shortcut path < 300 ms | 6–19 ms: **pass**; tool calls by the LLM: 0/100, so they were replaced by router + rules |

2. **Walking skeleton (P1),** then **features by risk (P2):** actions, personality, memory, character states, voice, privacy, design.
3. **Every device check in portrait and landscape** on the real phone, with screenshots in [docs/linkedin/media/](../linkedin/media/).

## Architecture
Two processes: the UI and the overlay in `:main`, and the native AI runtimes in `:inference`, behind a bound AIDL service. A model crash or running out of memory can't take down the floating character, and killing `:inference` gives the native memory back. Diagram and module map: [README → Architecture](../../README.md#architecture).

## Key decisions
| Decision | Why | Source |
|---|---|---|
| One ongoing chat, system prompt processed once | Cut p95 first word from 3.5 s to 2.4 s | S2 RESULTS |
| Router model + rules instead of LLM tool calls | 0/100 vs 87/100, then 95/100 with 0 wrong actions | S4 RESULTS §8 |
| Voice loads on demand, released after 2 quiet minutes | Four models fit in memory but slow each other down on a shared CPU | P2 memory check |
| No `INTERNET` permission at all | Privacy you can check, and a short Data safety form | docs/play-console.md |
| Secrets filtered before saving | The safest memory is the one never written | #79 |
| Usage numbers counted on the phone, shared only by the tester | Measuring crash-free sessions without telemetry | SPEC-test-report.md |

## What didn't work
- **LLM tool calling:** Gemma 3 1B wrote 0 usable tool calls out of 100.
- **Qwen3 0.6B:** slower to start answering (4.4–6.0 s) than the bigger Gemma 3 1B; dropped.
- **Kokoro voice:** 3.77 s per second of speech; dropped for Piper.
- **Zipformer speech recognizer:** fast, but it dropped the start of every sentence.
- **Running everything at once:** speech recognition went over its limit (0.74) while chat was writing.
- **Small-model habits:** invented facts ("a sister called Lily"), loops of 256 tokens, "That's six!" for 2 + 2, pretending to act with actions switched off. Each got a code-level guard.

## Results not yet measured
- P1 gate: 1 hour of the core loop with no crash ([docs/p1-gate.md](../p1-gate.md)): not run yet.
- P3 gate: crash-free sessions ≥ 99 % and the retention signal ([docs/p3-results.md](../p3-results.md)): the closed test hasn't started collecting reports.
- Galaxy A23 (the 4 GB floor device): untested.

## Screenshots for the portfolio
All are free of personal data (checked in [docs/public-release-audit.md](../public-release-audit.md), finding 7).

| Use | File |
|---|---|
| Cover | `docs/play-store/feature-1024x500.png` |
| App tour (5) | `docs/play-store/screenshot-1.png` … `screenshot-5.png` |
| Floating over the home screen | `docs/linkedin/media/p1-overlay-home.png`, `tap-to-talk.png` |
| Character states | `docs/linkedin/media/bibi-four-states.png` |
| Action confirmation | `docs/linkedin/media/chat-confirm-portrait.png` |
| Secret refused | `docs/linkedin/media/remember-this.png` |
| Charts | `docs/linkedin/media/s1-battery-cost.png`, `s2-first-word-time.png`, `s3-voice-speed.png`, `s4-actions-right.png`, `p2-sharing-the-cpu.png` |

Demo video: [docs/linkedin/media/bibi-demo.mp4](../linkedin/media/bibi-demo.mp4) (130 s); plan and notes in [demo-video.md](demo-video.md).
