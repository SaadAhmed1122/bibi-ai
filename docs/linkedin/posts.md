# LinkedIn log: what we learned building an offline AI companion

A running log of Bibi AI's tests, written for LinkedIn: plain words, real numbers, one lesson each. It is updated after every new test or major finding. Every number comes from `spikes/*/results/RESULTS.md` or `docs/project_status.md`; nothing is estimated unless it says so.

- **Phone used for everything:** vivo Y17s, a mid-range phone (6 GB RAM, MediaTek Helio G85, Android 15). Everything runs on the phone, offline.
- **Charts:** `media/` (regenerate with `python make_charts.py media`, needs matplotlib).

---

## Ready-to-post draft (latest)

> I'm building an AI companion that runs fully offline on a mid-range Android phone (6 GB RAM, no cloud). Before writing the app, I tested the four things most likely to fail. Here's what the numbers said:
>
> 1️⃣ **Chat speed.** Gemma 3 1B took 3.5 s to show the first word when every message started a new chat. Keeping one ongoing chat and reusing the system prompt brought it to 2.4 s, under my 3 s limit. Same model, same phone; only the setup changed.
>
> 2️⃣ **Phone actions.** I asked the small model to write the "tool call" for things like "set an alarm for 7". Right answers: 0 out of 100. A tiny router model plus simple rules to pull out the time, number or app name got 87 out of 100, in about half a second.
>
> 3️⃣ **Voice.** One popular voice was 3.8× slower than real time on this phone. A lighter one (Piper) speaks 4× faster than real time. Speech recognition (Parakeet) handles 1 s of audio in 0.32 s.
>
> 4️⃣ **Battery.** The floating animated character costs +28 mA at 12 fps and +12 mA in calm mode. That's about 0.5% battery per hour.
>
> Lessons: measure on a normal phone, not a flagship. Small models are fine at chat, not at structured output. And a simple rule often beats a model.
>
> #OnDeviceAI #Android #LLM #Gemma #EdgeAI #BuildInPublic

Suggested images, in this order: `media/s2-first-word-time.png`, `media/s4-actions-right.png`, `media/s3-voice-speed.png`, `media/s1-battery-cost.png`.

---

## Findings (newest first)

### 2026-10-31: Chatting with Bibi on top of any app, in portrait and landscape
- **Test:** tap floating Bibi and a small chat card opens over whatever app you're in. Type, read the answer as it streams in, close the card. On the vivo, in both portrait and landscape.
- **Numbers:**
  - First word in **1.3–1.7 s** from the card, the same as the full chat screen.
  - **10 open/close cycles in each orientation** left **0 leftover windows** (each one would have kept memory busy).
  - The card closes 3 ways: Back, a tap outside it, or its Close button.
- **Two things only the phone showed:**
  - **Tap outside didn't close the card at first.** A focusable window takes every touch on the screen unless you tell Android otherwise (`FLAG_NOT_TOUCH_MODAL`), so it never hears about taps outside it.
  - **In landscape, the vivo's floating keyboard covers the Send button.** Fix: the keyboard's own Enter key became a Send arrow, so the message can be sent in any layout.
- **Lesson:** rotate the phone during testing. Half of this task's bugs only showed up in landscape or on the real keyboard.
- Images: `media/p1-chat-card-portrait.png`, `media/p1-chat-card-landscape.png`.
- Source: issue #45.

### 2026-10-31: Bibi floats over the phone
- **Test:** the floating character from the S1 battery spike, now inside the real app, on the vivo. Bibi sits over any app; you can drag it, and tapping it opens the chat.
- **What works (checked on the phone):**
  - **Drag and let go:** Bibi slides to the nearer side of the screen in 0.28 s and remembers the spot.
  - **Hide / Show / Turn off** from the notification. Hidden means the window is really removed, so a hidden Bibi uses no drawing time at all.
  - **Rotate the phone:** Bibi stays on screen and on an edge, clear of the camera cutout. Before this fix it could end up halfway off-screen.
  - **Tap Bibi:** the chat opens. The chat card that opens on top of other apps is the next task.
- **Number to remember:** from the S1 test, this animation costs about **0.5 % battery per hour** at 12 frames per second, and **0.15 %** in calm mode.
- **Lesson:** a small detail like rotation is easy to miss. The spot that was fine in portrait (y = 1,354) is off-screen in landscape (the screen is only 720 tall), so every saved position has to be checked against the screen it's shown on.
- Images: `media/p1-overlay-home.png`, `media/p1-overlay-rotation.png`. Video: `media/p1-overlay-drag-snap.mp4` (12 s, drag and snap).
- Source: issue #44; S1 results `spikes/s1-overlay/results/RESULTS.md`.

### 2026-10-31: Installing a 585 MB AI model by hand takes 7 seconds
- **Test:** the new "Bibi's model" screen on the vivo. You copy the model file to the phone, pick it in Android's own file picker, and the app copies it to its private storage and checks it. Offline, with no storage permission.
- **Numbers:**
  - **585 MB copied and checked in about 7 s.** The target was under 30 s.
  - The check is a SHA-256 "fingerprint" of the file, so even one wrong byte is refused.
  - A wrong file (a random 100 KB file renamed to look like the model) is refused at once with "This isn't Bibi's model file".
  - After a restart, the app opens in **1.4 s**. It only checks the file size at startup; the full fingerprint runs once, at import.
  - First answer from the imported model: first word in **1.28 s**, the same as before.
- **Lesson:** check a big file thoroughly once, when it arrives, then do a cheap check on every start. Users shouldn't wait for a 585 MB hash every time they open the app.
- Images: `media/p1-model-import.png` (progress at 78 %), `media/p1-model-wrong-file.png` (the wrong-file message). The system file picker isn't shown, because it lists personal files.
- Source: issue #42.

### 2026-10-31: Remembering costs time: 1 minute vs 9 seconds
- **Test:** making Bibi keep the conversation after Stop, after a crash, and when the chat gets too long for the model's memory (4,096 tokens). On the vivo, offline.
- **Numbers:**
  - **Before:** after Stop, Bibi forgot everything said earlier.
  - **Now:** I told Bibi "my cat is called Mango", stopped a story halfway, then asked "What is my cat called?". The answer: **"Mango!"**
  - **The catch:** to remember, the model has to re-read the old messages, at about **50 tokens per second** (roughly 35 words). Carrying 6 long answers made Bibi wait **46–66 s** before the first word. **28 long messages in a row, 0 errors**, but a minute of silence is not OK.
  - **The fix:** carry at most ~400 tokens (6 short messages, or 1 long answer). After a long answer plus Stop: back in **9.4 s**.
  - **Crash test:** I killed the AI process in the middle of an answer. The app stayed open, said "Bibi is getting ready again…", reloaded the model by itself in **about 13 s**, and the next answer came in 1.3 s.
- **Honest limit:** a 1B model sometimes invents memories. Asked "What did we talk about first?" after an old message had been dropped, it said "your love for collecting colorful pebbles" (we never talked about pebbles).
- **Lesson:** on a phone, memory isn't free. Every remembered word is time the user waits, so pick how much to remember by measuring, not by guessing.
- Images: `media/p1-remembers-after-stop.png` (Mango), `media/p1-crash-recovery.png` (the crash recovery screen).
- Source: issue #38; service log `BibiInference conversation reopened … prefill_ms`.

### 2026-10-31: The real chat screen: 10 messages, every first word in about 1.3 s
- **Test:** the new chat screen on the vivo. 10 everyday messages in a row ("What should I cook tonight?", "Give me one tip to sleep better", …), then Stop in the middle of a long story, then Clear chat. Everything offline.
- **Numbers:**
  - **First word in 1.26–1.31 s on all 10 messages.** The limit is 3 s. The slowest message was only 0.06 s slower than the fastest.
  - Full short answers took **2.5–5.3 s** (7–23 pieces of text).
  - Stop keeps what Bibi already wrote, marked "Stopped".
  - After Stop or Clear, Bibi needs **about 7 s** to get ready again (6.8–7.1 s measured).
  - The chat is saved on the phone: it was still there after force-closing and reinstalling the app.
- **A bug the phone test found:** during those 7 s the Send button looked ready, but a message sent then would have failed. Unit tests with a fake model couldn't see it, because the fake model has no 7-second pause. Now the screen says "Bibi is getting ready again…" and Send waits.
- **Lesson:** test on the real phone with the real model. Fake models are fast, and the bugs hide in the slow parts.
- Images: `media/p1-chat-10-turns.png`, `media/p1-chat-stop-restart.png`. Videos: `media/p1-chat-10-turns.mp4` (60 s, 10 messages), `media/p1-chat-stop.mp4` (30 s, Stop and restart).
- Source: issue #37; service log `BibiInference turn ttft_ms`.

### 2026-10-31: First chat inside the real app: first word in 1.3 s
- **Test:** Gemma 3 1B running in its own background process inside the new app. A few chat turns on the vivo, plus Stop in the middle of an answer.
- **Numbers:**
  - **First word in 1.3–1.4 s** (turns 1–4); 1.7 s on one fresh start. The S2 prototype took 2.4 s.
  - **5.2–5.9 tokens per second.**
  - Loading the model takes 3–4 s, or 11 s right after the phone starts.
  - Answers are capped at 256 tokens: a "write 1000 words" request stopped at exactly 256.
- **Two surprises:**
  - **Warm-up:** a one-token warm-up at load cut the first answer from **3.4 s to 1.3 s**. The runtime sets up its "write the answer" step the first time it's used, so do that before the user is waiting.
  - **Stop:** pressing Stop in LiteRT-LM 0.16.1 breaks the conversation (the library's own docs call the session "poisoned"). Reopening it costs **about 7 s**, and the earlier turns are lost. The fix (rebuild with the last turns) is the next task.
- **Lesson:** warm up the model before the user's first message, and test Stop, not only Send.
- Image: `media/p1-first-chat.png`. Video: `media/p1-first-chat.mp4` (debug screen, 25 s).
- Source: PR #59, issue #36.

### 2026-10-31: Tooling: newest libraries don't always fit together
- **What happened:** the newest Android libraries needed a newer build tool (AGP 9.1) and Android SDK 37. The AI runtime (LiteRT-LM 0.16.1) needs Kotlin 2.2.21. Room's code generator for Kotlin 2.2.21 (KSP 2.2.21-x) refused the new built-in Kotlin, and the newer KSP 2.3.12 worked.
- **Number:** 3 version conflicts before the first screen showed up.
- **Lesson:** pin the AI runtime's Kotlin version first, then pick every other library to fit around it.
- **Post angle (short):** "On-device AI apps: your model runtime decides your Kotlin version. Everything else follows."
- Source: `docs/project_status.md` Decisions (2026-10-31), PRs #50, #57.

### 2026-10-31: Android can report the same crash twice
- **What happened:** one test crash appeared twice in the phone's own crash history (`ApplicationExitInfo`).
- **Number:** 1 crash → 2 identical records. After the fix: 1 → 1.
- **Lesson:** if you build your own crash log from Android's exit history, remove duplicates.
- Source: issue #55, PR #56.

### 2026-10-31: Battery cost of a floating animated character (S1)
- **Test:** four 1-hour runs, screen on, unplugged. The phone alone vs. the phone with the character on top.
- **Numbers:**
  - Phone alone: 148 and 151 mA.
  - With the character at 12 fps: 176 mA, so **+28 mA**, about **0.5 % battery per hour**.
  - Calm mode (2 fps): **+12 mA**, about **0.15 % per hour**.
  - No crashes, and the system never killed the app.
- **Lesson:** a small animated overlay is cheap. The screen itself is the big cost.
- **Caveat:** low screen brightness (15 of 255), one phone.
- Image: `media/s1-battery-cost.png`. Source: `spikes/s1-overlay/results/RESULTS.md`.

### 2026-10-30: Phone actions: small LLM vs. simple rules (S4)
- **Test:** 100 requests like "wake me at 6:45", "open Maps", "flashlight off", plus 10 chat messages that should do nothing.
- **Numbers:**
  - **Gemma 3 1B writing the tool call: 0/100 correct.** It wrote broken JSON, the wrong format, or the wrong values (for example hour 0 for "seven thirty"), and took 4–14 s.
  - **Router model (MediaPipe Decision Maker) + rule-based slot extraction: 87/100 correct**, about **0.5 s**, using **600 MB**. 0 of the 10 chat messages triggered an action.
  - The direct shortcut path (tap a button) took 6–19 ms.
- **Lesson:** a 1B model that isn't trained for tool use shouldn't fill in tool arguments. Pick the action with a small classifier, then pull the values out with plain code you can test.
- Image: `media/s4-actions-right.png`. Source: `spikes/s4-tools/results/RESULTS.md`.

### 2026-10-30: Chat speed: reuse the system prompt (S2)
- **Test:** 50 messages to Gemma 3 1B (int4, 584 MB file) on LiteRT-LM, CPU only.
- **Numbers:**
  - **New chat for every message:** first word in 2.4 s typically, **3.5 s for the slowest 5 %**, which fails a 3 s limit. Each extra word of prompt adds about 50 ms.
  - **One ongoing chat with the system prompt processed once:** **2.4 s for the slowest 5 %**, which passes. Speed 7.0 tokens per second (roughly 5 words), memory 1.19 GB, 0 crashes.
- **Gotcha:** Gemma 3's template has no "system" turn, so the runtime didn't pre-process the system prompt on its own. A scripted first exchange fixed it.
- **Lesson:** on a slow phone, how you feed the prompt matters as much as which model you choose.
- Images: `media/s2-first-word-time.png`, `media/s2-test-app-models.png`. Source: `spikes/s2-llm/results/RESULTS.md`.

### 2026-10-30: Bigger isn't usable (S2 side notes)
- **Gemma 4 E2B:** 2.5 GB file, about 3.0–3.2 s to the first word, 5.3 tokens/s, 2.0 GB memory (short test). Too heavy for this phone class.
- **Qwen3 0.6B:** first word 4.4–6.0 s in short tests, slower than Gemma 3 1B despite being smaller. **Dropped from the project.**
- **Lesson:** a smaller model isn't always faster to start answering; the runtime and file format matter.
- Source: `spikes/s2-llm/results/RESULTS.md` §3.

### 2026-10-29: Voice: fast enough, but memory-hungry (S3)
- **Speech out:**
  - **Kokoro 3.77 s per 1 s of audio** (3.8× slower than real time), so it fails.
  - **Piper 0.25 s** per 1 s of audio, so it passes.
- **Speech in:**
  - **Zipformer (20M)** was fast (0.18) but cut off the first 1–2 s of every sentence.
  - **Parakeet 0.6B** was accurate and fast (**0.32**) but used about **900 MB**. It was kept by owner decision.
- **Lesson:** check what the model *drops*, not only how fast it is.
- Image: `media/s3-voice-speed.png`. Source: `spikes/s3-voice/results/RESULTS.md`.

---

## How to add a finding
1. Add a dated section at the top of **Findings** with: Test, Numbers, Lesson, Image, Source.
2. Make a chart or screenshot in `media/`.
   - Phone screenshots: `adb exec-out screencap -p > media/<name>.png`.
   - Recordings: `adb shell screenrecord --time-limit 20 /sdcard/rec.mp4` then `adb pull`.
   - Check that no notifications, accounts or phone numbers are visible.
3. Refresh the ready-to-post draft if the finding is worth leading with.
