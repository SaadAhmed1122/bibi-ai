# Design prompts for Codex (character + UI)

These are copy-paste prompts for generating Bibi's character art and UI design system with Codex (or another coding agent).
- **Name:** the app is **Bibi AI** and the character is **Bibi** (owner decision, 2026-10-28).
- **Grounded in the PRD:** §17 (avatar, emotions), §20 (UX, motion, tone, accessibility).
- **Character:** Bibi is a cute hand-drawn cartoon kitten, deliberately not robotic, so the assistant feels safe and comfortable (owner decision, 2026-10-29). The character brief, palette, and Gemini image prompts are in [bibi-character-gemini.md](bibi-character-gemini.md). Make the concept art there first, then use these prompts to turn the approved images into vector assets.
- **Vector first.** Codex produces code, so the prompts ask for SVG, Compose `ImageVector`, and Canvas output that a coding agent can actually make and that you can edit by hand. Prompt 1b is a plain-language version for an image model if you'd rather explore raster concept art first.
- **Engine-agnostic.** The avatar tech is still [OPEN] (sprite sheet or Rive, OD-03), so these prompts produce assets either path can use.

Run them in order. Start each Codex task with prompt 0.

---

## 0. Shared context (paste at the top of every task)

```
Project: Bibi, an offline on-device AI companion for Android (Kotlin, Jetpack Compose, Material 3, minSdk 34).
Bibi is a small animated character that floats over other apps. Tapping it opens a compact chat card.
Design principles (non-negotiable):
- Cute, warm and calm, never baby-ish or needy. No guilt-tripping or sad faces to pressure the user.
- Readable at small sizes: the character window is about 64–96 dp. Simple silhouette, big eyes, at most 4 body colors plus outline.
- Must work on light and dark backgrounds and over any app (add a soft outline or rim).
- Accessibility: never use color alone to show emotion. Each state also differs in shape or pose. Contrast ≥ 4.5:1 for any text.
- Performance: sprite atlas ≤ 4 MB GPU memory; idle animation 12 fps, interaction 24 fps, Calm mode ≤ 2 fps.
- It is always clearly an AI helper: no realistic human features, no romantic styling.
- Character: Bibi is a small sitting kitten with a big round head: warm orange fur (#F4A54A) with tabby stripes (#E07B2A), cream chest/muzzle/paws (#FFF4E0), no forehead marking, big dark-brown eyes (#4A1E14) with white highlights, pink nose and inner ears (#F2A7A0), a striped curled tail, and a violet collar (#7C5CFF) with a speech-bubble charm whose soft glow shows state (gold idle, light blue listening, violet thinking, amber doing a task). Warm-brown hand-drawn outline (#5A2E1E). No robot, screen, antenna or glowing-eye elements; no sharp teeth or claws. Suggest fur with a few line strokes, never per-hair detail.
Do not add network calls, analytics, or third-party asset sources. All output must be original and license-clean.
```

---

## 1. Master character in vector (from the approved Gemini reference)

```
Using the shared context, recreate Bibi as a hand-editable SVG from the approved concept image docs/design/character/gemini/01-master.jpeg (and 02-three-quarter.jpeg for depth).
Produce in design/character/master/:
- front_neutral.svg (viewBox 0 0 256 256, centered, 16 px padding, anchor bottom-center at 128,240)
- Separate named groups/layers: body, stripes, chest, head, ear-left, ear-right, eye-left, eye-right, eyelids (hidden by default), nose, mouth, whiskers, collar, charm, charm-glow, paw-left, paw-right, tail, ground-shadow, outline. The animator moves ears, eyes, tail and paws independently; the tail needs 3–4 segments so it can curl.
- silhouette.svg (single-color fill, to check readability)
- palette.json with the roles and hex values from the shared context
- 64x64 and 128x128 preview PNGs rendered via a small Node script using sharp or resvg-js (script in tools/, not in the asset folder)
Use only flat shapes and simple gradients (no filters heavier than a soft glow) so the art can be ported to Compose ImageVector or Rive.
Write design/character/master/README.md noting any place where the vector differs from the Gemini image, and check readability at 64 px.
```

### 1b. Concept art

Use the Gemini prompts in [bibi-character-gemini.md](bibi-character-gemini.md): one prompt per image, with the master image attached as a reference for every state.

---

## 2. Expression and state set

```
Using the shared context and design/character/master/front_neutral.svg (keep its layer names), create the state set as SVGs in design/character/states/. Match the approved Gemini state images in docs/design/character/gemini/ where they exist.
Base MVP states (exactly these four first):
1. idle: relaxed, eyes open, slight smile
2. thinking: head tilted, eyes looking up-left, three small thought dots
3. speaking: mouth open (provide mouth_closed / mouth_mid / mouth_open variants for lip flap)
4. confused: "?" bubble, one eyebrow raised (the error state; friendly, not sad)
Gate-dependent extra states (second batch, keep them in states/extra/):
listening (eyes wide, soft ring), happy (smile + small bounce pose), excited, sleeping (eyes closed + "z"), charging (small glow), celebrating, awaiting_confirmation (holding a tiny card/sign), grabbed, dropped.
Rules:
- Same viewBox, anchor point (feet/bottom-center at 128,240) and outline weight across all files, so frames can be swapped without jitter.
- Each state must be distinguishable in grayscale (check with silhouette exports).
- Write design/character/states/states.json listing each state with: id, file(s), group (Ambient/Reaction/Interaction/Status/Direct manipulation per PRD §17.1.2), and a one-line accessibility description (e.g. "Thinking: Bibi tilts its head with thought dots").
```

---

## 3. Animation frames and sprite atlas

```
Using the state SVGs, generate short loop animations as numbered SVG frames, plus a build script that exports a sprite atlas.
Loops:
- idle_breathe: 3.5 s cycle (PRD §20.13), 12 fps → 42 frames; subtle 2–3% vertical scale. Blink overlay every 3–6 s at random (separate 4-frame blink).
- thinking: 1.2 s loop, dots pulse in sequence.
- speaking: mouth cycle closed→mid→open→mid, driven by an amplitude value 0..1 (document the mapping).
- confused: 1 s one-shot, "?" pops in with overshoot, then holds.
- drag: squash on grab, stretch on release (spring: stiffness 400, damping ratio 0.65).
Output:
- design/character/anim/<loop>/frame_###.svg
- tools/build_atlas.(js|py): renders frames at 1x/2x/3x (base 96 px), packs them into power-of-two PNG atlases with premultiplied alpha, and writes atlas.json (frame rects, durations, anchor).
- Report the total atlas memory (width*height*4 bytes) and fail the build if it exceeds 4 MB.
Also produce a Calm-mode variant: static poses only, a blink every 8 s, no particles.
```

---

## 4. Compose renderer prototype (use during spike S1)

```
In a standalone sample module (spikes/overlay-render, not the app), write a Jetpack Compose CharacterRenderer that draws the sprite atlas from prompt 3 on a Canvas.
Requirements (PRD §17.5):
- One Choreographer-based clock; frame index stored in a State updated per tick; the draw lambda reads it. No recomposition per frame.
- Adaptive fps: 24 (interacting) / 12 (idle) / 2 (calm); zero draws when the screen is off.
- A state machine API: setState(idle|thinking|speaking|confused), setMouthAmplitude(Float), with a 150 ms cross-fade and a minimum hold of 600 ms per pose to avoid flicker (§20.13).
- No per-frame allocations (preallocate the Rect/Paint objects).
- Respect system "Remove animations" by switching to static poses.
Include a debug overlay showing current fps and frame time, and a JankStats hook. Add a short README explaining how to measure battery drain over 1 h for S1.
```

---

## 5. Design system: Material 3 theme from the character

```
Create a Material 3 design system for Bibi in a module named core/designsystem (Kotlin, Compose).
Inputs: design/character/master/palette.json. Use violet #7C5CFF as the seed color; cyan and amber are accents.
Deliver:
- Color: light and dark ColorSchemes generated from the character's main color as the seed (tonal palettes), plus dynamic color support (Android 12+) with the brand scheme as the fallback. Keep one "character accent" color constant across dynamic color so Bibi stays recognizable.
- Verify contrast ≥ 4.5:1 for text roles. Write a unit test that computes the contrast ratios and fails below the threshold.
- Typography: a rounded, friendly but readable type scale (use a system font or one bundled OFL font; list its license in LICENSES.md).
- Shapes: rounded corners that echo the character's silhouette (e.g. extraLarge for the interaction card).
- Motion tokens (PRD §20.13): micro 100 ms, short 150–200 ms, medium 300 ms, long 500 ms; emphasizedDecelerate for entering, emphasizedAccelerate for exiting, standard for color changes; spring(stiffness 400, dampingRatio 0.65) for drag release.
- Spacing tokens and a minimum touch target of 48 dp.
- @Preview gallery screens for colors, type, shapes and motion, in light, dark and 200% font scale.
No hardcoded colors or dimensions outside this module.
```

---

## 6. Overlay interaction card

```
Using core/designsystem, build the compact interaction card UI in Compose (UI only, fake ViewModel state), per PRD §20.6.2:
- Width ≈ 320 dp (max 90% of screen width), elevated surface, anchored near the character (left or right side variants).
- Top row: single-line text field that expands to 4 lines, a mic button (hidden when voice is unavailable, with the text "Voice not supported on this device" in the settings preview), and close.
- Below: the last answer (scrollable, max 6 lines, "Open in chat" link) with a streaming-text state and a Stop button while generating.
- Quick-action chips: Timer, Alarm, Flashlight, Open app.
- States to preview: empty, typing, streaming, done, error ("I couldn't do that…" with a next step), keyboard open, 200% font scale, dark mode.
- Accessibility: content descriptions; TalkBack announces only Thinking, Done, Needs confirmation, Error (§17.6).
Microcopy: short sentences, warm, no baby-talk (§20.14).
```

---

## 7. Remaining MVP screens

```
Using core/designsystem, build UI-only Compose screens with previews and fake state for:
1. Chat: message list, streaming assistant bubble, stop button, a small "memory used" chip on replies, and a "Report this reply" action (Play AI-content policy).
2. Confirmation card for Sensitive actions: shows the exact resolved action (e.g. "Dial Anna Rossi (••• 42)?") with Confirm/Cancel, and a countdown that CANCELS on timeout (never confirms).
3. Memory: list with filters, search field, pin/unpin, swipe-to-delete with undo, export button, retention setting (1 week / 1 month / 3 months default / 1 year / forever).
4. Privacy dashboard: what is stored (counts and sizes), retention, last network use (model download only), export all, delete all (two-step confirm).
5. Model pack screen: download progress with pause/resume, checksum-verified badge, size, delete pack.
6. Settings: personality (Friendly / Concise), display (Calm mode, hide duration), theme (Light/Dark/System, dynamic color), licenses screen.
Every screen needs empty, loading and error states plus dark and 200% font scale previews. Do not design onboarding or payments.
```

---

## 8. App icon and store assets

```
Create the app icon and Play Store graphics for Bibi from the chosen character:
- Adaptive icon: foreground SVG (the character face/body within the 66 dp safe zone of a 108 dp canvas), solid background color, and a monochrome themed-icon layer (Android 13+). Export to res/mipmap-anydpi-v26 XML + vector drawables.
- Notification small icon: a single-color silhouette, 24 dp, legible at small size.
- Play Store: 512x512 icon PNG, 1024x500 feature graphic SVG→PNG with the character and the tagline "Your private, offline companion" (no claims about speed or numbers).
Provide a tools/ script that renders all exports, and check that the icon reads at 48 px.
```

---

## 9. Design review pass

```
Review everything under design/ and core/designsystem against: the shared context rules, PRD §17.6 (accessibility of the character), §17.7 (companion ethics: no guilt, no dependence cues, always disclosed as AI), and §20.15 (accessibility checklist).
Output a markdown checklist of pass/fail items with file paths and concrete fixes. Do not change files in this pass.
```
