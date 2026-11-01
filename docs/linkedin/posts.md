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

### 2026-11-01: Small-model habits you have to design around
- **Test:** chat and voice on the vivo while finishing P2.
- **Numbers:**
  - A 1B model on greedy decoding repeated "Jones-Smith" until its **256-token** limit, about **35 s** of nonsense. A **40-line** check now stops a reply at the third identical repeat.
  - "Close the flashlight" turned the light **on**: the rules knew "off", "kill" and "shut", not "close". The speech model also writes a spoken "off" as "**of**".
  - Asked "tell me about my sister" with nothing saved, it invented one ("Emily"). Memory notes help only when they exist.
- **Lesson:** guard rails around a small model are cheap code, and they're where most of the reliability comes from.
- Source: #107.

### 2026-11-01: The Play Store paperwork for an offline AI app
- **Test:** going through Google Play's policy checklist for Bibi before the closed test.
- **Numbers:**
  - **10** policy items: **7** green, **2** amber, **1** for the account owner.
  - **0** data collected or shared: the app has no internet permission at all, so the Data safety form is short.
  - **2** foreground-service types to justify (floating character, microphone), each with a video.
  - **12** testers for **14** days before a new personal account can go public.
  - Ships **3** model files that a store reviewer can't easily get (585 MB, 165 MB, 730 MB). That's the real blocker, not the code.
- **Lesson:** an offline AI app trades privacy paperwork for distribution paperwork. Nothing to declare about data, a lot to explain about big files.
- Source: #89.

### 2026-11-01: From Material defaults to a designed app
- **Test:** the new design on the vivo, every screen in portrait and landscape.
- **Numbers:**
  - **3** design directions mocked up; the owner picked "Expressive Tabby": tangerine and violet taken from the character's fur and collar.
  - Every text colour pair passes **WCAG AA**. The brand tangerine itself (**3.3:1** on white) failed for button text, so buttons use a darker shade (**5.3:1**); the bright one is only decoration.
  - **0** hard-coded colours or sizes left outside the design module; **2** variable fonts (Bricolage Grotesque, Figtree) add about **470 KB**.
  - In landscape the bottom tab bar took about **a quarter** of the screen height; it became a side rail.
- **Lesson:** take the palette from the character, then measure it. The nicest orange was the one that failed contrast.
- Image: `media/design-expressive-tabby.png`, `media/design-landscape-rail.png`. Source: #93, #94.

### 2026-11-01: One tap, then just talk
- **Test:** tap-to-talk on floating Bibi over the phone's home screen, on the vivo. The questions came from the laptop's speaker, so the runs are repeatable.
- **Numbers:**
  - The mic opens in **80–120 ms**. Speech peaked at **0.058** loudness against a room at **0.001–0.002**.
  - Per turn: transcription **1.1 s**, first word of the answer **1.5–1.7 s**. The first turn also waits for the speech model to load (**13–19 s**) and the voice (**9 s**).
  - **8 runs to get two clean turns:** the chat opened instead while the model loaded, a sentence was cut short, the speech detector calibrated on the speaker's own voice, and the first words were sent before Bibi's AI process had connected, so they were lost. A one-line loudness log found the last two.
  - Gemma 3 1B answered "what is 2 plus 2?" with **"That's six!"** A 1B model is a conversation partner, not a calculator.
- **Lesson:** log the numbers (loudness, timings), never the words. That was enough to debug a voice feature without recording anyone.
- Image: `media/tap-to-talk.png`. Source: #118.

### 2026-11-01: The fine print for a "free" offline app
- **Test:** writing the Licenses screen and the "Report this reply" button.
- **Numbers:**
  - One small offline app ships **4 AI models and 6 libraries**, under **5 different licenses** (Gemma Terms of Use, CC BY 4.0, CC BY-SA 4.0, Apache 2.0, and MIT).
  - Report a reply opens the phone's share menu in **0.66 s**. The app has no internet permission, so the user's own share is the only way a report leaves the phone.
  - From the floating chat bubble, the share menu opened **under** Bibi's card: an overlay window sits above even the system's share sheet. The card now closes first.
- **Lesson:** "open models" still come with homework: every model needs its credit line, and a voice dataset's license can be on a different site from the voice.
- Image: `media/licenses-screen.png`. Source: #88.

### 2026-11-01: One switch for "just talk", one button for "forget me"
- **Test:** Safe mode and Delete everything on the vivo.
- **What happened:**
  - With Safe mode on, "turn on the flashlight" first got "Okay! Let's see if we can get some light." from the chat model, with **no light**. The model didn't know actions were off and improvised.
  - Now Bibi says plainly: "Safe mode is on, so I can't do phone actions."
  - Delete everything took **237 messages, 2 memories and both logs to 0** in one tap. The 600 MB of AI models stayed, so nothing has to be re-imported.
- **Lesson:** when a feature is switched off, tell the model, or answer for it. A language model will happily pretend.
- Image: `media/privacy-dashboard.png`. Source: #87.

### 2026-11-01: "Hey Bibi, wake me up at seven" from any app
- **Test:** push-to-talk in Bibi's floating chat card over the phone's home screen, on the vivo.
- **Numbers:**
  - First try: the recording was **perfect silence** (loudness 0.00000, against 0.0042 room noise inside the app). Android mutes the mic for an app that isn't in front, even with an overlay on screen.
  - Fix: Bibi's floating service declares the "microphone" type, set while the app is open.
  - Afterwards: real room noise (0.0047), and a spoken **"Hey BB, can you wake me up at seven?"** turned into **"Alarm set for 07:00."**, all on the phone.
  - The speech model spelled "Bibi" as "BB", but the request still worked.
- **Lesson:** Android's privacy rules are silent failures. Measure the signal, not just "no error".
- Image: `media/card-voice-alarm.png`. Source: #86.

### 2026-11-01: Hold to talk, hear the answer
- **Test:** push-to-talk and read-aloud in Bibi's chat on the vivo.
- **Numbers:**
  - The mic records only while the button is held. Audio goes to the speech model in **0.5 s pieces** and never leaves the phone.
  - The first press loads the 650 MB speech model (**12.5 s**), so the first answer waits; after that it takes about **1.5 s** for 4 s of speech.
  - Read aloud speaks each sentence as soon as it's finished, while the rest is still being written. The first sentence waits for the voice to load (**10 s**); the rest follow straight away.
  - Holding the button in silence correctly gave "I didn't catch that" instead of a made-up sentence.
- **Small catch:** Gemma sometimes writes *markdown*, so asterisks are now removed before speaking.
- **Lesson:** loading time is the real cost of on-device voice, not the speed once it runs.
- Image: `media/push-to-talk.png`. Source: #85.

### 2026-11-01: Bibi can hear and speak (and forgets the voice when it's quiet)
- **Test:** the voice models running inside the app's AI process on the vivo, next to the chat model.
- **Numbers:**
  - A recorded "Set a timer for ten minutes" came back **word for word** in **1.5 s** for 4.4 s of audio. Loading the 650 MB speech model takes **12 s**, so it starts loading the moment you press the mic.
  - Speaking a 55-character sentence took **5.8 s**, including playback.
  - After **2 quiet minutes** the voice models are released and the AI process drops from **1,320 MB to 435 MB**.
  - Limiting the native libraries to 64-bit ARM shrank the test app from **359 MB to 126 MB**.
- **Lesson:** on a 6 GB phone, the best memory is the memory you give back.
- Source: #84.

### 2026-11-01: Bibi gets a face for each moment
- **Test:** floating Bibi and the chat header during real chats on the vivo, in portrait and landscape.
- **What you see:**
  - **thinking** (paw on chin, violet dots) while Bibi works out the answer, about the first 1.5 s;
  - **speaking** (paw up, mouth open) while words stream;
  - **confused** (head tilt, question-mark tail) for about **3 s** when it has to ask "What time should the alarm ring?";
  - then **idle** again.
- **Detail:** the new faces are rendered from the same vector files as the old one, and the old frame came out **pixel-identical**, so nothing jumps between states.
- **Lesson:** a character that reacts makes a 1.3 s wait feel like thinking instead of lag.
- Images: `media/bibi-four-states.png`, `media/bibi-speaking-landscape.png`. Source: #83.

### 2026-11-01: Bibi writes its own diary (and refuses to write down a password)
- **Test:** after a quiet spell, Bibi summarizes the new chat into a one-line memory, using a second, temporary conversation next to the main one, on the vivo.
- **Numbers:**
  - First try: "**Okay...**". Gemma 3 has no "system" slot, so the instruction was silently dropped.
  - With the instruction inside the message: "**Bibi's** sister was Sara". Labelling the chat lines "You:" swapped who's who.
  - With "User:" / "Bibi:": "You asked about your trip planning… You want hiking experiences with your friend Ali." That took **14.8 s**, only while idle.
  - A summary of an older chat mentioned a password and was **refused** by the same filter that guards "remember that…".
  - Honest catch: if you write while a summary is running, it stops, but **about 8 s late**.
- **Lesson:** small models are literal. Where the instruction sits and how the lines are labelled changes everything.
- Source: `docs/measurements/2026-11-01-summaries/RESULTS.md`, #81.

### 2026-11-01: Teaching a 1B model who "my" is
- **Test:** Bibi answering with a saved memory ("My sister is Sara"), each run from an empty chat, on the vivo.
- **Numbers:**
  - Without memory, Gemma 3 1B **invented a sister called "Lily"** and once looped "Smith-Jones-Smith-Jones…" for 256 tokens.
  - With the note, **2 of 3** answers were right ("Sara was your sister!").
  - The one miss: "what is my sister called?" got "**You're** called Sara!". "My" in the note confused it, so notes are now rewritten as "**Your** sister is Sara".
  - A note placed next to the question beat the same note in the system prompt: "Green!" vs "You can be blue!".
  - It costs no extra time: the first word still came in about **1.3 s**.
- **Lesson:** with small models, *where* and *how* you put a fact matters as much as having it.
- Source: `docs/measurements/2026-11-01-memory-recall/RESULTS.md`, #80.

### 2026-11-01: "Remember that…" and the things Bibi refuses to remember
- **Test:** saving memories on the vivo, typed and by long-press, then editing them in the new Memory screen.
- **Numbers:**
  - "remember that my sister is Sara" is saved with **0 AI models involved** (a phrase rule), and Bibi replies instantly.
  - "remember that my password is hunter2" is **refused**.
  - Card numbers are checked with the Luhn formula, so a 16-digit order number isn't mistaken for a card.
  - An edit can't sneak a PIN in later.
  - **13 new tests.**
- **Lesson:** the safest memory is the one that never gets written. Filter before saving, not after.
- Images: `media/remember-this.png`, `media/memory-edit-blocked.png`. Source: #79.

### 2026-11-01: Giving Bibi a memory without losing a single message
- **Test:** the first database upgrade on a real phone. Bibi's chat database went from version 1 to version 2, adding memory tables and a keyword search index.
- **Numbers:** **191 of 191** chat messages kept, the new tables and **4** sync triggers in place, chat still saving. **14** database tests, including the upgrade itself on a real version-1 file.
- **Gotcha:** the search index ignored `"sister"*` but found `sister*`. One quote character, zero results.
- **Lesson:** test the upgrade on real data before anyone else installs the update.
- Source: #78.

### 2026-11-01: Two personalities, one tiny model
- **Test:** the same 3 questions to Bibi as "Friendly" and as "Concise", each from an empty conversation, on the vivo.
- **Numbers:**
  - Friendly: **70** chunks of text in total. Concise: **43**, about **39 % shorter**.
  - "A good way to start the morning": Friendly 19 chunks, Concise 7 ("Get some sunlight and drink water!").
  - Switching takes **10.6 s** and keeps the recent messages.
- **Gotchas:**
  - Asked inside the same conversation, "Concise" just repeated its earlier Friendly answers word for word. A small model copies what is in its context.
  - The 1B model ignores "no exclamation marks".
  - The first Concise prompt ("one or two sentences") saved only 27 %. "One short sentence, no extra tips" did better.
- **Lesson:** test prompts from a clean start, and write the instruction as a measurable rule.
- Image: `media/settings-personality.png`. Source: #77.

### 2026-11-01: "It's dark in here" turns the torch on
- **Test:** typing requests into Bibi's chat on the vivo, in the app and in the floating card, in portrait and landscape.
- **Numbers:**
  - "turn on the flashlight" matches a simple phrase rule and acts in **under 0.1 s** with no AI model.
  - "it is dark in here, give me some light" goes through the router, which picked the flashlight in **0.5 s**.
  - "set an alarm" gets a question back ("What time should the alarm ring?") instead of a guess.
  - "what is a good name for a cat" goes to the chat model: **0.5 s** to rule out an action, then the first word in **1.3 s**.
  - "call 0123 456 789" shows a card with the exact number. Nothing happens until Confirm, and after **30 s** of silence it cancels itself.
- **Lesson:** an assistant that acts needs three answers besides "done": "I need a value", "I need your OK", and "you didn't answer, so I didn't".
- Images: `media/chat-confirm-portrait.png`, `media/card-confirm-landscape.png`. Source: #76.

### 2026-11-01: Bibi can now do things: 5 actions, 1 gate
- **Test:** each of the five actions on the vivo, through the same gate chat will use: Permission Engine first, then the action, then a line in a local log.
- **Numbers:** **5 of 5 actions worked**: the torch went on and off, a 10 s timer rang, Settings opened, an alarm was set, and dial asked first, then opened the dialer with the number filled in. **0 calls were placed.** Cancel ran nothing.
- **Bug caught on the phone:** the log hid the number in the request but printed it in full in the result line. Now every part of a dial line shows only the last 2 digits, and a test checks it.
- **Lesson:** privacy bugs hide in the "boring" fields. Test the log, not only the action.
- Images: `media/actions-check.png`, `media/dial-prefilled.png`. Source: #75.

### 2026-11-01: The action router moves into the app
- **Test:** the small router model (165 MB) that decides "is this a timer, an alarm, an app, the torch, a call, or just chat?" now runs inside Bibi's AI process, next to Gemma, on its own thread.
- **Numbers:** it imports in seconds with a checksum check and loads in **12.5 s**. **6 out of 6** test requests went to the right action, each in **about 0.55 s**: "set a timer for 5 minutes" → timer, "wake me up at seven" → alarm, "tell me a joke" → none. The app still has **no internet permission**.
- **Lesson:** keep each model on its own thread, so asking "which action?" never waits behind a long chat answer.
- Image: `media/router-model-section.png`. Source: #74.

### 2026-10-31: Four AI models on one phone: memory fits, the CPU doesn't
- **Test:** Gemma (chat) kept writing long answers while, one at a time, speech-to-text (Parakeet), the voice (Piper) and the action router ran in other apps, on the 6 GB vivo.
- **Numbers:**
  - Memory: the worst pair still left **1.6 GB free**, and **no app was killed**.
  - Speed: everything slowed down.
    - Speech-to-text went from 0.32 to **0.74 s of compute per second of audio**, over its 0.7 limit.
    - The router went from 489 to **815 ms**.
    - Gemma's answers took **33–50 % longer**.
    - The voice stayed faster than real time (0.30 → 0.45).
  - All four loaded at once would take **about 3.25 GB**.
- **Decision:** the voice models load only when they are needed, and listening never runs while Bibi is writing.
- **Lesson:** on a phone, "does it fit in RAM?" is only half the question. Eight slow cores shared by four models is the other half.
- Image: `media/p2-sharing-the-cpu.png`. Source: `docs/measurements/2026-10-31-p2-memory-check/RESULTS.md`, #72.

### 2026-10-31: The keyboard that hid the message box
- **Test:** floating Bibi's chat card in portrait on the vivo, with the keyboard open.
- **Numbers:** the card is 967 px tall; the space above the keyboard is 925 px. Android pushed the card up to the status bar but didn't shrink it, so **41 px of the message box stayed under the keyboard**, and the window was then told the keyboard covered **0 px**.
- **Fix:** the card now measures where the keyboard is and moves above it, shrinking to fit (5 unit tests). Landscape uses a floating keyboard, so nothing changes there.
- **Lesson:** overlay windows over other apps don't get the keyboard handling normal screens get; measure, don't trust the default.
- Images: `media/card-keyboard-before.png`, `media/card-keyboard-after.png`. Source: #71.

### 2026-10-31: Phone actions: from 87 to 95 out of 100, and 0 wrong actions
- **Test:** the same 100 requests as before ("wake me at 6:45", "open Maps", "flashlight off", …) plus 10 chat messages that should do nothing, on the vivo, after 3 small fixes. Dry run: nothing really opens.
- **Numbers:**
  - **95 of 100 right**, up from 87, in about **0.5 s**, using about **600 MB**.
  - **0 wrong actions.** Last time one request ("get me into google drive") opened the wrong app. Now, when unsure, Bibi asks or does nothing.
  - **0 of 10** chat messages triggered an action.
- **The 3 fixes:**
  - The router may answer with lower confidence (0.5 → 0.25), because the next step checks the details anyway.
  - "Let me know when 3 minutes are up" sounded like an alarm to the router. A duration without a clock time now becomes a timer.
  - "Google Drive" when only "Google" is installed: ask instead of guessing.
- **The 5 misses were all safe:** 2 apps that aren't installed (Spotify, Instagram), 1 not installed (Drive), 1 the router wasn't sure about ("calculator"), and "WhatsApp", which it mistook for a phone call, so it asked for a number.
- **Honest caveat:** I tuned on the same 100 requests, so new phrasings will score lower. That's the next test.
- **Lesson:** for actions on a phone, "ask when unsure" beats "always do something". A wrong action costs more than a question.
- Source: `spikes/s4-tools/results/RESULTS.md` §8, issue #8.

### 2026-10-31: Shrinking the release app: the AI library needs a rule
- **Test:** the release build (the one that goes to Google Play) with R8 turned on. R8 shrinks the app by removing and renaming code it thinks is unused. Tested on the vivo in portrait and landscape.
- **Numbers:**
  - Release app bundle: **44 MB** (most of it is the AI runtime's native code; the 585 MB model is separate).
  - With 1 keep rule, everything worked: first word in **1.26–1.29 s**, the same as the debug build; Stop, floating Bibi and the chat card all fine.
- **Surprises:**
  - The on-device AI library (LiteRT-LM 0.16.1) ships **no shrink rules**. Its native code calls back into Java classes by name, so without a rule R8 could remove or rename them. Fix: keep the library's whole package.
  - R8 also renamed my own classes in the logs: a reply that ended normally showed up as `end=r`. Fix: log fixed words instead of class names.
- **Lesson:** always test the shrunk release build on a real phone before uploading. Debug builds hide this class of problem.
- Source: issue #48, PR #67.

### 2026-10-31: Ask for a permission only when it's needed
- **Test:** the first run and the "Floating Bibi" switch on the vivo, in portrait and landscape.
- **What happens now:**
  - A new user sees 3 short screens (Welcome, Model, Notifications). **No permission pop-up appears until step 3**, and saying "no" is fine: chat still works.
  - "Display over other apps" (needed for floating Bibi) is asked **only when you turn the switch on**, after a short explanation. If you come back without allowing it, the switch simply stays off.
- **Numbers:**
  - **0** permission pop-ups before the user asks for a feature that needs one.
  - Calm mode measured on the phone: **123 frames in 10 s (12.3 per second) → 20 frames (2.0 per second)**. From the S1 battery test, that's about **0.5 % → 0.15 % battery per hour**.
- **Small surprises:**
  - On this vivo, "open the permission page for my app" opens the **full list of apps** instead, so the explanation says "find Bibi and turn it on".
  - Android's own permission page warns the app "will be able to see where you tap", so our explanation now says what Bibi does: it "doesn't read or record what's on your screen".
- **Lesson:** ask for a permission at the moment it makes sense, explain it in one sentence, and make "no" a normal answer.
- Image: `media/p1-floating-permission-explainer.png`.
- Source: issues #46, #47.

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
