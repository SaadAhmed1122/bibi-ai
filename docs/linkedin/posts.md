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
