# Bibi character — brief + Gemini image prompts

**Direction (owner, 2026-10-29):** a **fluffy mochi puff**: soft, round, furry and very cute. It keeps the features that show Bibi is an **AI helper that does things on your phone**. Earlier directions (the plain seed, the green leaf) are dropped.
These prompts are for **Gemini image generation**: one prompt → **one image** of **one character**. Don't use sheets, grids, or multi-panel layouts. The Codex prompts in [codex-design-prompts.md](codex-design-prompts.md) later turn the approved images into vector assets.

---

## 1. Character brief

**Who Bibi is:** a tiny fluffy puff that lives in your phone. It's soft and squishy like mochi, curious, quick and helpful. It listens, thinks, and then *does* the thing (sets the timer, turns on the flashlight, opens the app).

### What makes Bibi unique
Several mochi-style AI companion apps already exist (see brainstorm.md). Bibi's three signature features keep it distinct:
1. **Screen-face:** a small, smooth, dark glossy oval nestled in the fur, like a tiny phone screen, with **glowing cyan eyes** on it. Cute fur outside, AI inside.
2. **Spark tuft:** one curly fluffy tuft on top whose **tip glows**, and the glow color shows what Bibi is doing.
3. **Mitten paws:** two tiny round paws that **hold tools** (stopwatch, flashlight, app tile, confirmation card), so it reads as a doer, not just a pet.

| Part | Design | Why |
|---|---|---|
| Body | Round **mochi dome**, slightly wider at the bottom, squashy. Covered in **short, dense, plush-toy fur** with soft, fluffy edges. | Mochi softness plus puff fur = cute and huggable |
| Fur color | Cloud white with a **lavender tint** (`#F3EEFF`), periwinkle shading (`#D6CCF5`) | Soft and friendly; avoids green and "wellness" palettes |
| Face | A smooth **deep-indigo glossy oval screen** (`#1E1B3A`) set into the fur, covering the upper-middle front | The AI signature: it reads as tech instantly |
| Eyes | Two big **glowing cyan** (`#4FE3FF`) rounded-pill eyes on the screen, with a tiny white sparkle each | Light-based eyes = AI. Easy to animate (blink = pill squash). |
| Cheeks | Two soft **pink glow dots** (`#FF9EC7`) on the screen's lower corners | Extra cute; they brighten when happy |
| Mouth | None when idle; a small glowing cyan curve when speaking | Keeps the face clean |
| Spark tuft | One curly fluffy tuft on top; its **tip glows** (cyan by default) | Unique silhouette and state indicator |
| Paws | Two tiny round mitten paws, fluffy | Lets Bibi hold things |
| Feet | None. Bibi floats with a soft glow shadow below. | Fits an overlay floating over apps |
| Outline | Thin deep-indigo outline plus a light rim around the fluff | Readable on any wallpaper |

**Palette (proposal, tune after the first images):**

| Role | Color | Hex |
|---|---|---|
| Fur | Lavender cloud white | `#F3EEFF` |
| Fur shading | Periwinkle | `#D6CCF5` |
| Screen-face / outline | Deep indigo | `#1E1B3A` |
| AI glow (eyes, tuft) | Electric cyan | `#4FE3FF` |
| Cheeks | Soft pink | `#FF9EC7` |
| Brand primary | Violet | `#7C5CFF` |
| Action accent (doing a task) | Warm amber | `#FFB547` |

**Avoid:** green as a main color, leaves or plants, hearts, pulse lines, medical or fitness symbols, realistic animal features (no snout, no animal ears), and hyper-real fur. It should look like a **plush toy**, not a real animal.

**Spark-tuft glow = state** (color is never the only signal; the pose changes too, per PRD §17.6):

| State | Tuft tip | Eyes | Pose |
|---|---|---|---|
| Idle | soft cyan | relaxed pills | gentle float, fur slightly breathing |
| Listening | bright cyan, ring pulses | wide circles | leans forward, fur perks up |
| Thinking | violet, orbiting dots | look up to the side | squished tilt, one paw on the screen's lower edge |
| Speaking | cyan | happy arcs + glowing curve mouth | small paw gesture |
| Doing a task | amber | focused | holding the tool |
| Done | amber sparkle | happy ^ ^ | bouncy hop, fur fluffs out |
| Confused / error | dim violet "?" | one pill smaller | tuft curls into a question mark |
| Needs confirmation | amber | attentive | holds up a small blank card |
| Sleeping | dim, slow pulse | closed curved lines | flattened mochi puddle, "z" |
| Being dragged | cyan | surprised circles | stretched like mochi, fur trailing |

---

## 2. How to use these prompts in Gemini

1. Paste **Prompt A (style anchor)** plus **Prompt 1 (master)**. Generate several times and pick the best one. That image becomes the **reference**.
2. For every later prompt, **attach the reference image** and start with:
   `Use the attached character exactly — same fluffy body, screen-face, eyes, cheeks, spark tuft, colors and proportions. Change only what is described below.`
3. **One prompt per message, one image per prompt.** If Gemini returns a grid or several characters, reply: `Only one character, one single image, no panels, no grid.`
4. Save each approved image as `design/character/gemini/<nn>-<state>.png` so Codex can trace it later.
5. Treat Gemini output as **concept art**. Final assets are vector (see the Codex prompts). Check Gemini's terms for commercial use of generated images before using them in the store listing [VERIFY].

---

## 3. Prompts

### A. Style anchor (put at the start of every prompt)
```
Style: super cute kawaii mascot, soft 3D plush-toy look, short dense fluffy fur, soft studio lighting, clean simple shapes, high readability at small size. Single character only, centered, full body, front view, plain very light lavender background (#F7F5FC), soft glow shadow under the character. No text, no letters, no logos, no watermark, no frame, no grid, no multiple panels, no extra characters.
Character "Bibi": a tiny round fluffy mochi puff, slightly wider at the bottom, covered in soft lavender-white fur (#F3EEFF) with periwinkle shading (#D6CCF5). On its front, a smooth glossy deep-indigo oval screen-face (#1E1B3A) nestled in the fur, with two big glowing cyan pill-shaped eyes (#4FE3FF) with tiny sparkles and two soft pink glowing cheek dots (#FF9EC7). One curly fluffy tuft on top with a glowing cyan tip. Two tiny round fluffy mitten paws. No legs, floating. Thin deep-indigo outline.
Avoid: green as main color, leaves, plants, hearts, pulse lines, medical or fitness symbols, animal snout, animal ears, realistic animal fur.
```

### 1. Master reference — neutral
```
[Style anchor]
Bibi in a calm neutral pose, paws relaxed at its sides, eyes open and friendly, tuft tip glowing soft cyan. This image will be the reference for all other poses.
```

### 2. Three-quarter view (for the animator)
```
[Attach reference] Use the attached character exactly...
Same character turned 3/4 to the right, showing how the screen-face sits in the fur and how the tuft attaches to the top. Neutral expression.
```

### 3. Idle
```
[Attach reference] ... Bibi idling: gently floating, eyes relaxed in a soft smile shape, fur softly fluffed, tuft tip soft cyan.
```

### 4. Listening
```
[Attach reference] ... Bibi listening: leaning slightly forward, eyes wide and round, fur slightly perked up, two thin cyan rings pulsing out from the tuft tip.
```

### 5. Thinking
```
[Attach reference] ... Bibi thinking: body squished and tilted, eyes looking up to the side, one tiny paw touching the bottom edge of its screen-face, tuft tip glowing violet (#7C5CFF), three small glowing dots orbiting the tuft.
```

### 6. Speaking
```
[Attach reference] ... Bibi talking: happy arc eyes, a small glowing cyan curved mouth on the screen-face, one paw raised in a small friendly gesture, cheeks glowing a bit brighter.
```

### 7. Doing a task — timer
```
[Attach reference] ... Bibi holding up a small round stopwatch icon with both paws, focused eyes, tuft tip glowing warm amber (#FFB547). Clean flat icon, no numbers.
```

### 8. Doing a task — flashlight
```
[Attach reference] ... Bibi holding a tiny flashlight with both paws, shining a soft amber beam to the side, tuft tip amber, determined happy eyes.
```

### 9. Doing a task — opening an app
```
[Attach reference] ... Bibi tapping a small floating rounded-square app tile (blank, no logo) with one paw, tuft tip amber, focused eyes.
```

### 10. Done / success
```
[Attach reference] ... Bibi doing a bouncy happy hop, fur fluffed out, happy ^ ^ arc eyes, cheeks glowing, a few small amber sparkles around the tuft.
```

### 11. Confused / error (friendly, never sad)
```
[Attach reference] ... Bibi confused but friendly: one eye pill slightly smaller, body tilted, the fluffy tuft curled into a question-mark shape with a dim violet tip. Not sad, not crying.
```

### 12. Needs confirmation
```
[Attach reference] ... Bibi holding up a small blank rounded card in front of it with both paws, attentive eyes looking at the viewer, tuft tip amber. The card is blank (no text).
```

### 13. Sleeping
```
[Attach reference] ... Bibi asleep: body relaxed and flattened like a soft mochi, eyes two closed curved lines, tuft drooping with a dim slow glow, one small "z" shape floating (a shape, not a font letter).
```

### 14. Being dragged
```
[Attach reference] ... Bibi being dragged across the screen: body stretched like soft mochi in the direction of movement, fur trailing behind, surprised round eyes, tuft tip cyan. Fun, not scared.
```

### 15. In context — floating over a phone
```
[Attach reference] ... A modern Android phone screen showing a blurred generic home screen (no real app logos), with Bibi small and floating near the right edge of the screen, casting a soft glow. Clean product shot, the phone fills the frame.
```

### 16. App icon concept
```
[Attach reference] ... App icon concept: Bibi's fluffy face (screen-face with cyan eyes, pink cheeks, glowing tuft) filling a rounded-square violet (#7C5CFF) background, bold and simple so it reads at 48 px. No text.
```

### 17. Silhouette check
```
[Attach reference] ... Same pose as the reference, rendered as a solid single dark indigo silhouette on a white background, no details, to test that the fluffy shape and tuft are recognizable.
```

---

## 4. From fluffy concept to the real app (technical note)

Real fur is expensive to render and animate in an overlay that must stay ≤ 4 MB of GPU memory at 12/24 fps (PRD §17.5, §12.10). The in-app Bibi is therefore **stylized fluff**:
- A **scalloped "cloud" outline** suggests fur at the edge, with a few fur-tuft strokes and a soft inner gradient. No per-hair rendering.
- The glossy screen-face, eyes, cheeks and tuft glow are simple shapes, so they stay sharp at 64–96 dp.
- **Squash-and-stretch** (mochi) is done by scaling the body, not by redrawing fur.
- The fully fluffy Gemini render can still be used for the store listing, the onboarding illustration and the app icon, where size and frame rate don't matter (pending Gemini license check [VERIFY]).

---

## 5. Review checklist (for each image)
- [ ] Reads as **cute fluffy AI helper** in under a second: fur outside, screen-face inside.
- [ ] Clearly different from generic mochi blobs, with the screen-face, spark tuft and mitten paws all visible.
- [ ] Same proportions, colors and features as the master reference.
- [ ] Still recognizable at 64 px (shrink the image and look).
- [ ] The state is clear from pose and shape, not only from color.
- [ ] No text, logos, extra characters, or animal snout or ears.
- [ ] Friendly, never sad, needy, or guilt-inducing (PRD §17.2.3, §17.7).
