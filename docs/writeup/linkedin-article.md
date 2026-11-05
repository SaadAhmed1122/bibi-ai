# LinkedIn article (draft for the owner)

P4 Task 11 (#136), [SPEC-writeup.md](../../SPEC-writeup.md) criterion 2. The owner edits and publishes it; the publish date then goes in `docs/project_status.md` → Decisions (criterion 3).

**Rules for this draft:** every number has a source in the table at the end. The P1 1-hour gate and the P3 closed-test numbers (crash-free sessions, retention) haven't been measured yet ([p1-gate.md](../p1-gate.md), [p3-results.md](../p3-results.md)), so the article leaves them out. Add them, with their sources, if the article goes out after the closed test.

Suggested images, in order: `docs/play-store/feature-1024x500.png` (cover), `docs/linkedin/media/s2-first-word-time.png`, `s4-actions-right.png`, `p2-sharing-the-cpu.png`, `privacy-dashboard.png`.

---

## I built an AI companion that never touches the internet. Here's what a mid-range phone can really do.

Most "AI on your phone" demos run on a flagship, or quietly call a server. I wanted to know what's possible on an ordinary phone with no cloud at all. So I built **Bibi**: a small cartoon kitten that floats over your apps, chats, listens, remembers what you ask it to, and does a few phone actions. The app doesn't even have the internet permission.

Everything below was measured on a **vivo Y17s**: 6 GB of RAM, a MediaTek Helio G85, Android 15. That's a normal mid-range phone, not a flagship.

### The approach: test the scary parts first
Before writing the app, I ran four small test apps ("spikes") for the four things most likely to kill the idea: the floating character's battery cost, chat speed, voice and phone actions. Each had a pass or fail number set in advance. Only after they passed did the real app start.

### 1. Chat: the setup mattered more than the model
Gemma 3 1B (int4, a 584 MB file) on Google's LiteRT-LM, CPU only, 50 messages:
- Starting a fresh chat for every message: the slowest 5 % of first words took **3.5 s**, over my 3 s limit.
- One ongoing chat with the system prompt processed once: **2.4 s**. **7.0 tokens per second**, **1.19 GB** of memory, no crashes.

Same model, same phone. Only how the prompt was fed changed.

Bigger or "smaller" wasn't better either. Gemma 4 E2B needed about **2 GB** of memory and **3.0–3.2 s** to its first word. Qwen3 0.6B, half the size, took **4.4–6.0 s** to start answering. I dropped it.

### 2. Phone actions: a 1B model can't fill in a form
I asked Gemma 3 1B to write the "tool call" for 100 requests like "wake me at 6:45" or "open Maps". **0 out of 100** were usable: broken JSON, the wrong format, or the wrong values, in 4–14 s each.

What worked: a small router model picks *which* action, and plain code pulls out the values (the time, the app name). That got **87/100** in about **0.5 s**. After three small fixes it got **95/100**, with **0 wrong actions** and **0 of 10** chat messages mistaken for an action. The 5 misses were all safe: Bibi asked or did nothing.

One honest caveat: I tuned on the same 100 requests, so new phrasings will score lower.

### 3. Voice: check what a model drops, not just its speed
- One popular voice (Kokoro) needed **3.77 s** to make 1 s of speech. Too slow. Piper needs **0.25 s**.
- A tiny speech recognizer was fast (**0.18**) but cut off the first words of every sentence. Parakeet is accurate and fast (**0.32**) but uses about **900 MB**.

### 4. Memory fits. The CPU doesn't.
I ran the chat model, speech recognition, the voice and the action router together. Memory was fine: at least **1.6 GB** stayed free, and nothing was killed. But they share eight slow cores. Speech recognition slowed from **0.32 to 0.74** (over its limit), the router from **489 to 815 ms**, and chat answers took **33–50 %** longer.

So the voice models load only when you press the mic, and they're released after **2 quiet minutes**: the AI process drops from **1,320 MB to 435 MB**.

### 5. The floating character is cheap
Four 1-hour runs with the screen on: the animated kitten costs **+28 mA** at 12 frames per second (about **0.5 % battery per hour**) and **+12 mA** in Calm mode. The screen itself costs about 150 mA.

### What a small model taught me
- **Where you put a fact matters.** A memory note next to the question worked; the same note in the system prompt was ignored. "My sister is Sara" confused it ("You're called Sara!"), so notes are rewritten as "Your sister is Sara".
- **It will pretend.** With actions switched off, it answered "Let's see if we can get some light", and no light came on. Now the app answers for it.
- **It repeats itself.** Once it looped "Jones-Smith" for 256 tokens, about 35 s. A 40-line check stops that now.
- **It's not a calculator.** "What is 2 plus 2?" → "That's six!"

The pattern: most of the reliability comes from cheap, testable code around the model, not from the model.

### Privacy by design, not by policy
- The app has **no internet permission**, so the Play Store's Data safety form says "no data collected or shared".
- Memories that look like a password, PIN, card or ID number are **refused before they're saved**. Card numbers are checked with the Luhn formula, so an order number isn't mistaken for a card.
- The action log hides phone numbers except the last 2 digits. A test caught the full number leaking into the result line.
- To measure crashes in the closed test, the phone counts sessions and crashes itself (numbers only, never words), and testers choose whether to share the report.
- "Delete everything" took **237 messages, 2 memories and both logs to zero** in one tap.

### Where it is now
Bibi is in a closed test on Google Play. The test's gate is **99 % crash-free sessions** in the core loop, plus whether testers come back after a week. I'll share those numbers when the test ends, including any misses.

What I'd tell anyone building on-device AI: **test on a normal phone, decide the pass/fail numbers before you test, and let a simple rule beat a model whenever it can.**

The code and every measurement are on GitHub: github.com/SaadAhmed1122/bibi-ai

#OnDeviceAI #Android #LLM #Gemma #EdgeAI #Privacy #BuildInPublic

---

## Sources for every number

| Number(s) in the article | Source file |
|---|---|
| vivo Y17s: 6 GB, Helio G85, Android 15 | [docs/project_status.md](../project_status.md) → Test devices |
| 584 MB file; 3.5 s vs 2.4 s; 7.0 tok/s; 1.19 GB; 50 messages | [spikes/s2-llm/results/RESULTS.md](../../spikes/s2-llm/results/RESULTS.md) (chat-mode soak: p95 2,409 ms, 7.04 tok/s, PSS 1,189 MB; per-prompt soak p95 3,493 ms) |
| Gemma 4 E2B 2 GB, 3.0–3.2 s; Qwen3 0.6B 4.4–6.0 s | [spikes/s2-llm/results/RESULTS.md](../../spikes/s2-llm/results/RESULTS.md) §3; [docs/linkedin/posts.md](../linkedin/posts.md) "Bigger isn't usable" |
| 0/100, 4–14 s; 87/100, 0.5 s; 95/100, 0 wrong, 0 of 10; 5 misses | [spikes/s4-tools/results/RESULTS.md](../../spikes/s4-tools/results/RESULTS.md) (incl. §8) |
| Kokoro 3.77, Piper 0.25, Zipformer 0.18, Parakeet 0.32, ~900 MB | [spikes/s3-voice/results/RESULTS.md](../../spikes/s3-voice/results/RESULTS.md) (Parakeet PSS 903 MiB) |
| 1.6 GB free; 0.32 → 0.74; 489 → 815 ms; 33–50 % | [docs/measurements/2026-10-31-p2-memory-check/RESULTS.md](../measurements/2026-10-31-p2-memory-check/RESULTS.md) |
| 2 quiet minutes; 1,320 MB → 435 MB | [docs/linkedin/posts.md](../linkedin/posts.md) "Bibi can hear and speak" (#84) |
| +28 mA, +12 mA, 0.5 %/h, 12 fps; phone alone 148–151 mA | [spikes/s1-overlay/results/RESULTS.md](../../spikes/s1-overlay/results/RESULTS.md) (27.8 mA, 11.5 mA) |
| Memory note placement; "You're called Sara!" | [docs/measurements/2026-11-01-memory-recall/RESULTS.md](../measurements/2026-11-01-memory-recall/RESULTS.md) |
| Safe mode "Let's see if we can get some light"; 237 messages, 2 memories | [docs/linkedin/posts.md](../linkedin/posts.md) "One switch for just talk" (#87) |
| 256 tokens, ~35 s, 40-line check | [docs/linkedin/posts.md](../linkedin/posts.md) "Small-model habits" (#107) |
| "That's six!" | [docs/linkedin/posts.md](../linkedin/posts.md) "One tap, then just talk" (#118) |
| Luhn check; last 2 digits in the action log | [docs/linkedin/posts.md](../linkedin/posts.md) (#79, #75) |
| Data safety "no data collected or shared" | [docs/play-console.md](../play-console.md) |
| 99 % crash-free gate | [SPEC-stability.md](../../SPEC-stability.md), [docs/project_status.md](../project_status.md) Milestones (P3) |
