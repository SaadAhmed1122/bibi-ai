# First LinkedIn post: Bibi AI (draft, 2026-11-01)

Everything here comes from the measured results in this repo (`docs/project_status.md`, `spikes/*/results/RESULTS.md`, `docs/measurements/`, `docs/linkedin/posts.md`). Where something isn't measured yet, it says so. The repo is **private**, so the post doesn't link to it.

---

## 1. The post (ready to paste)

> I'm building Bibi, an AI companion that runs 100% offline on an ordinary Android phone. No cloud, no account, and the app doesn't even ask for internet permission.
>
> Bibi is a small cartoon kitten that floats over your apps. Tap it and you can chat, ask it to set an alarm or a timer, open an app, switch on the torch or open the dialer, and tell it things to remember.
>
> Everything is tested on a mid-range phone: a vivo Y17s (6 GB RAM, MediaTek Helio G85, Android 15). Not a flagship.
>
> What's inside:
> 🧠 Chat: Google's Gemma 3 1B (4-bit) running on LiteRT-LM, in its own background process. First word in about 1.3 s, about 7 tokens a second.
> 🧭 Actions: a tiny 270M EmbeddingGemma model (MediaPipe Decision Maker) decides which action a message is about, then plain rules read the time, number or app name. 95 of 100 test requests right, 0 wrong actions. Dialing always asks first.
> 🗂️ Memory: "remember that…" saves to on-device SQLite with keyword search. It refuses to store card numbers, passwords, PINs and ID numbers.
> 🐱 The floating kitten costs about 0.5% battery an hour (0.15% in calm mode).
>
> What the numbers taught me:
> 1️⃣ Setup beats model size. Reusing the system prompt cut the first word from 3.5 s to 2.4 s with the same model. In the app it's now 1.3 s.
> 2️⃣ Small models chat well but can't write structured output. Gemma 3 1B produced 0 valid tool calls out of 100. A 270M router plus simple rules got 95.
> 3️⃣ On a phone, memory isn't the only limit. Gemma plus speech-to-text fit in RAM, but sharing the CPU made speech-to-text over 2× slower. So voice models load only when needed.
> 4️⃣ Test on real data and real screens. The phone found bugs the unit tests didn't: a keyboard hiding the message box, and a log that printed a full phone number.
>
> Next: voice (Parakeet for listening, Piper for speaking), a privacy dashboard, and a closed test.
>
> I'm building it with Kotlin and Jetpack Compose, with Claude Code as my pair programmer, and measuring every step.
>
> Would you trust an assistant more if it never left your phone?
>
> #OnDeviceAI #Android #EdgeAI #Gemma #LLM #Kotlin #JetpackCompose #BuildInPublic

Length: about 330 words, which reads fine on LinkedIn. To shorten, drop the "What's inside" emoji lines first; the numbered lessons carry the post.

## 2. Images to attach (in this order, all in `docs/linkedin/media/`)

1. `chat-confirm-portrait.png`: the chat with the dial confirmation card (shows the app and safety at once).
2. `s2-first-word-time.png`: the 3.5 s → 2.4 s chart.
3. `s4-actions-right.png`: small LLM 0/100 vs router + rules (the chart shows the first run, 87; today's number is 95).
4. `p2-sharing-the-cpu.png`: "memory fits, the CPU doesn't".

Before posting, check each screenshot for anything personal: notifications, names, numbers. The test number 0123 456 789 is fake.

---

## 3. Answering comments

Short answers you can paste, then the detail behind each one.

**"Which phone? Will it run on mine?"**
A vivo Y17s: 6 GB RAM, MediaTek Helio G85, Android 15. The app needs Android 14 or newer. The planned floor device is a Samsung Galaxy A23 (about 4 GB), which isn't tested yet, so I don't promise anything below 6 GB.

**"Why not just use ChatGPT / the cloud?"**
Privacy and independence. Nothing you say leaves the phone, it works in airplane mode, and it costs nothing per message. The trade-off is a much smaller model, so Bibi keeps answers short and doesn't pretend to know live news or weather.

**"Which model, and why that one?"**
Gemma 3 1B instruction-tuned, 4-bit (`gemma3-1b-it-int4.litertlm`, 585 MB), on Google's LiteRT-LM runtime. I also tried Qwen3 0.6B (slower to first word: 4.4–6 s) and Gemma 4 E2B (2.5 GB file, too heavy for this phone). Gemma 3 1B met all three targets in its 50-prompt test: at least 5 tokens/s, first word under 3 s at p95, and no out-of-memory crashes. Qwen was slower to the first word in short tests, so I dropped it before running its long test.

**"How fast is it?"**
In the app the first word appears in about 1.3 s and it writes about 7 tokens a second. Over a 50-prompt test the slowest 5 % took 2.4 s to the first word.

**"How does it do actions without an LLM writing tool calls?"**
Three layers:
1. Simple phrases ("set a timer for 5 minutes") match fixed patterns in under 20 ms, with no model at all.
2. Anything else goes to a 270M router (EmbeddingGemma through MediaPipe's Decision Maker). It picks one of five actions or "none" in about 0.5 s.
3. Rules pull out the values (time, number, app name).

Every action then passes a Permission Engine. Dialing always shows a card with the exact number, nothing happens without Confirm, and after 30 s of silence it cancels.

**"Is it safe? Can it call someone by itself?"**
No. It opens the dialer with the number filled in, after you confirm, and you still press Call yourself. It has no permission for SMS, call logs or contacts, and no accessibility service. A local log records every action, with phone numbers masked.

**"Does it remember things?"**
Yes, when you ask: "remember that my sister is Sara", or a long-press on any message. It's stored on the phone and searchable, and you can edit or delete it. It refuses card numbers, passwords, PINs and ID numbers. Automatic summaries and a 90-day expiry are being built.

**"What about voice?"**
It's tested but not in the app yet. Parakeet TDT 0.6B (int8, via sherpa-onnx) processes 1 s of speech in 0.32 s. Piper (en_US "amy", low) speaks 4× faster than real time. Kokoro sounded nice but was 3.8× slower than real time on this phone. Voice models will load only when you use them, because running them next to Gemma slows both.

**"Battery?"**
The floating kitten adds about 28 mA at 12 frames a second (about 0.5 % battery an hour), and about 12 mA in calm mode at 2 frames a second (0.15 %/h). Measured over four one-hour runs.

**"Is it open source? Can I try it?"**
Not yet. The code is private while I build. The plan is a closed test on Google Play. Tell me if you'd like to be a tester.

**"iOS?"**
Android only for now. The on-device runtimes and the floating overlay are Android-specific.

**"How did you build it alone?"**
With Claude Code as a pair programmer. I set the goals and decisions; it writes code, runs tests, and measures on the phone over USB. Every step has a spec, a plan, tests, a pull request, and a check on the real phone in portrait and landscape.

**Someone says "1B models are useless"**
For open-ended knowledge, yes, they're limited. For short friendly chat, deciding "is this an alarm?", and following a fixed format, a small model plus simple rules works, and it's private and free. The numbers above are the evidence.

**Things not to claim**
- Not tested on the Galaxy A23 or any 4 GB phone.
- The 95/100 actions score was tuned on the same 100 requests it was measured on. A fresh set is still to do.
- Voice isn't in the app yet.
- No public release yet.

---

## 4. The whole project in one place

### What Bibi is

- An offline, on-device AI companion for Android: a floating animated kitten (orange tabby, cream chest, violet collar with a speech-bubble charm) that chats with a local LLM, remembers what you ask it to, and does a few safe phone actions.
- Solo portfolio project by Saad, built with Claude Code.
- Name: **Bibi AI**. The original PRD calls it "Pip".

### Hard rules

- No internet permission at all; a build check fails if one sneaks in.
- No accounts, analytics or crash reporter.
- No SMS, call log, contacts or accessibility permissions.
- Every phone action goes through one Tool Router and one Permission Engine.
- Each model pack is at most 2 GB.

### Phases

| Phase | What | Status |
|---|---|---|
| P0 | Four feasibility spikes S1–S4 (throwaway test apps) | Done; gate met on the vivo |
| P1 | Walking skeleton: overlay, typed chat, one model, chat history, release build | Code complete; owner's 1-hour test and Play upload pending |
| P2 | Companion MVP: actions, personality, memory, avatar states, voice, privacy, design, closed-test prep | In progress (actions, personality, memory storage done) |
| P3 | Closed test, 10–12 testers | Not started |
| P4 | Decision and write-up | Not started |

### Phone and tools

- **Test phone:** vivo Y17s, 6 GB RAM (the "+6 GB extended RAM" is swap, ignored), MediaTek Helio G85 (2× Cortex-A75 + 6× A55), Mali-G52 GPU, Android 15. CPU only: no GPU or NPU is relied on.
- **Language and UI:** Kotlin 2.2.21, Jetpack Compose (BOM 2026.06.01), Material 3.
- **Build:** Android Gradle Plugin 9.0.1, compileSdk 36, minSdk 34 (Android 14). Release builds use R8 shrinking.
- **Architecture:** MVI (ViewModels expose `StateFlow`), hand-written wiring (no DI library), coroutines.
- **Development tools:** Claude Code (spec → plan → build → test → review), Context7 for library docs, graphify (code knowledge graph), GitHub issues and PRs for every task.

### The AI models

| Job | Model | File / size | Runtime | Key numbers (vivo) |
|---|---|---|---|---|
| Chat | Gemma 3 1B instruction-tuned, int4 | `gemma3-1b-it-int4.litertlm`, 584,417,280 bytes | Google LiteRT-LM 0.16.1 (pinned; 0.17+ needs Kotlin 2.3) | First word ~1.3 s in the app; p95 2.4 s and 7.04 tokens/s in the 50-prompt soak; ~1.1–1.2 GB memory |
| Action router | EmbeddingGemma 270M text | `embeddinggemma-2-text-270m.litertlm`, 164,626,432 bytes | MediaPipe Tasks Decision Maker 1.2.0 | ~0.5 s per message; 95/100 right with rules; ~0.6 GB; loads in 12–17 s |
| Speech to text (P2, not in app yet) | NVIDIA Parakeet TDT 0.6B v2, int8 | sherpa-onnx model folder | sherpa-onnx 2.0.1 | 0.32 s per 1 s of audio; ~900 MB (owner accepted over the 600 MB target) |
| Text to speech (P2, not in app yet) | Piper en_US "amy" low | sherpa-onnx (VITS) | sherpa-onnx 2.0.1 | 0.25 s per 1 s of audio; ~330 MB |
| Tried and dropped | Qwen3 0.6B (slow first word 4.4–6 s); Gemma 4 E2B (2.5 GB, too heavy); Kokoro TTS (3.8× slower than real time); Zipformer 20M STT (cut the first 1–2 s of speech); Gemma tool calling (0/100 valid) | | | |

### How the app is built

**Processes:** the UI runs in the main process; every native AI model runs in a separate `:inference` process. If a model crashes or Android kills it for memory, the app stays up, shows "Bibi is restarting" and reconnects (about 13 s).

**Chat path**
1. You type.
2. The memory check runs: "remember that …" is saved.
3. The action check runs: shortcut phrase → router → slot rules.
4. If neither applies, Gemma answers, streaming word by word, with a Stop button.
5. The conversation stays open, so the system prompt isn't re-read each time.
6. When the conversation fills up, Bibi reopens it carrying the last turns, capped at about 400 tokens. That cap cut the wait after a reopen from 46–66 s to about 9 s.

**Actions (the five MVP tools)**

| Action | How it's carried out | Risk class |
|---|---|---|
| Open app | Launcher list, read once | Safe |
| Set alarm | Android AlarmClock intent | Safe |
| Start timer | Android AlarmClock intent | Safe |
| Flashlight | CameraManager torch | Safe |
| Dial | Opens the dialer with the number filled in | Sensitive: confirmation card first |

- Every action goes through `ActionRunner`, which runs Permission Engine → executor → audit log.
- Only `SET_ALARM` is added as a permission; it's granted at install with no prompt.

**Personality:** two presets, Friendly and Concise; each is a different system prompt. Measured: Concise answers were 39 % shorter on the same questions.

**Memory**
- Stored with Room (SQLite) plus an FTS4 keyword index; the database moved to version 2 with all 191 chat messages kept.
- Saved by "remember that …", "my name is …" or a long-press "Remember this"; a filter refuses card numbers (checked with the Luhn checksum), passwords and PINs, and ID numbers.
- Memory screen: search, pin, edit, delete. Coming next: memories used in answers, automatic summaries when idle, 90-day expiry, JSON export.

**Floating Bibi (overlay)**
- A foreground service (`specialUse` type) draws the kitten over other apps.
- You can drag it, and it snaps to the screen edge.
- 12 frames a second, or 2 in Calm mode.
- Hide and Turn off are in the notification.
- Tapping it opens a chat card over any app.
- The overlay permission is asked only when you switch Floating Bibi on, after an explanation.

**Other**
- Model import: you pick the file and the app checks its size and SHA-256 (585 MB in about 7 s). Download from a server is deferred, so the app still needs no internet permission.
- Local crash log; the Gemma terms notice; first-run onboarding (Welcome → Model → Notifications).

### Findings worth talking about

| Finding | Numbers |
|---|---|
| Prompt reuse | First word 3.5 s → 2.4 s with the same model; ~1.3 s in the app |
| LLM tool calls | 0/100 valid → router plus rules 87, then 95/100, 0 wrong actions |
| Shortcut phrases | 6–19 ms at p95; no model needed |
| Battery | +27.8 mA (0.51 %/h) at 12 fps; +11.5 mA (0.15 %/h) in calm mode |
| Memory vs CPU | Gemma + Parakeet fit (1.6 GB still free), but Parakeet went 0.32 → 0.74 (over its limit) and Gemma 33–50 % slower → load voice only when needed |
| Context cap | Reopening after long chats: 46–66 s → 9.4 s by carrying at most ~400 tokens |
| Release build | R8 broke the AI library's native callbacks until a keep rule was added |
| Bugs found on the phone, not in tests | The keyboard covered the chat card in portrait (Android moved it but didn't shrink it); the in-app chat counted keyboard space twice; the action log printed a full phone number; everyday questions ("how do I sleep better") got "What time should the alarm ring?"; "open the light" looked for an app called "light" |
| Small-model behaviour | Copies its own earlier answers when they're in context; ignores "no exclamation marks"; follows "one short sentence" better than "one or two sentences" |
| Search detail | The FTS4 index matched `sister*` but not `"sister"*` |

### Honest limits (say these before someone else does)

- One test phone (6 GB). The 4 GB Galaxy A23 is untested.
- The action score was tuned on its own test set; a fresh set is still to do.
- Gemma 3 1B is a small model: it's wrong sometimes and has no live knowledge.
- Voice, the privacy dashboard and the closed test are still to come.
- The database isn't encrypted beyond Android's own storage encryption (owner decision for the MVP, to revisit before production).
