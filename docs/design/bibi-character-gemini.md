# Bibi character — brief + Gemini image prompts

**Direction chosen (owner, 2026-10-28):** the *seed* concept, redesigned so it reads as **a smart AI helper that does things on your phone**, not a plant, wellness, or health-tracker mascot.
These prompts are for **Gemini image generation**: one prompt → **one image** of **one character**. Don't use sheets, grids, or multi-panel layouts. The Codex prompts in [codex-design-prompts.md](codex-design-prompts.md) later turn the approved images into vector assets.

---

## 1. Character brief

**Who Bibi is:** a tiny tech-seed that "sprouted" inside your phone. It's curious, quick, and helpful. It listens, thinks, and then *does* the thing (sets the timer, turns on the flashlight, opens the app).

| Part | Design | Why |
|---|---|---|
| Body | Smooth seed/bean shape, slightly wider at the bottom. Glossy **pearl-white shell** with soft lavender shading. | Keeps the seed lineage, but white and glossy reads as "device", not "plant" |
| Face | A **dark indigo glass face panel** (like a tiny phone screen) on the front of the seed | Instantly says "tech / AI". It's also where expressions live. |
| Eyes | Two **glowing cyan rounded-pill eyes** on the panel; no mouth when idle, a small glowing line mouth when speaking | Light-based eyes = AI. They animate easily (blink = pill squash). |
| Sprout | One curled stem on top ending in a **glowing leaf-shaped light** (a "spark-sprout"), not a green leaf | The seed idea becomes an antenna/signal. Its glow color shows the state. |
| Arms | Two tiny stubby arms, no fingers | Lets Bibi *hold* things (timer, flashlight, card), so it reads as a doer |
| Feet | None. Bibi floats with a soft glow shadow underneath. | Fits an overlay that floats over apps |
| Outline | A thin deep-indigo outline plus a light rim | Readable on any wallpaper, light or dark |

**Palette (proposal, tune after the first images):**

| Role | Color | Hex |
|---|---|---|
| Shell | Pearl white | `#F4F1FA` |
| Shell shading | Soft lavender | `#D9D2EC` |
| Face panel / outline | Deep indigo | `#1E1B3A` |
| AI glow (eyes, sprout) | Electric cyan | `#4FE3FF` |
| Brand primary | Violet | `#7C5CFF` |
| Action accent (doing a task) | Warm amber | `#FFB547` |

**Avoid** (this is what made it look like a health app): green as a main color, real leaves, plant pots, soil, water drops, hearts, pulse lines, medical crosses, fitness rings, step counters, and "eco" vibes.

**Sprout glow = state** (color is never the only signal; the pose changes too, per PRD §17.6):

| State | Sprout | Eyes | Pose |
|---|---|---|---|
| Idle | soft cyan | relaxed pills | gentle float |
| Listening | brighter cyan with ring pulses | wide circles | leans forward |
| Thinking | violet, orbiting dots | look up-side | head tilt, one arm on "chin" |
| Speaking | cyan | happy arcs + line mouth | small gesture |
| Doing a task | amber | focused | holding the tool icon |
| Done | amber sparkle | happy arcs (^ ^) | small hop |
| Confused / error | dim violet with a "?" | one pill smaller | sprout curls into a question mark |
| Needs confirmation | amber | attentive | holds up a small blank card |
| Sleeping | dim, slow pulse | closed lines | curled, "z" |

---

## 2. How to use these prompts in Gemini

1. Paste **Prompt A (style anchor)** plus **Prompt 1 (master)**. Generate several times and pick the best one. That image becomes the **reference**.
2. For every later prompt, **attach the reference image** and start with:
   `Use the attached character exactly — same body, face panel, colors, outline and proportions. Change only what is described below.`
3. **One prompt per message, one image per prompt.** If Gemini returns a grid or several characters, reply: `Only one character, one single image, no panels, no grid.`
4. Save each approved image as `design/character/gemini/<nn>-<state>.png` so Codex can trace it later.
5. Treat Gemini output as **concept art**. Final assets are vector (see the Codex prompts). Check Gemini's terms for commercial use of generated images before using them in the store listing [VERIFY].

---

## 3. Prompts

### A. Style anchor (put at the start of every prompt)
```
Style: cute minimal 3D-ish vector mascot, soft studio lighting, clean shapes, glossy but simple, high readability at small size. Single character only, centered, full body, front view, plain very light lavender background (#F7F5FC), soft glow shadow under the character. No text, no letters, no logos, no watermark, no frame, no grid, no multiple panels, no extra characters.
Character "Bibi": a tiny AI helper shaped like a smooth seed/bean, pearl-white glossy shell (#F4F1FA) with soft lavender shading (#D9D2EC), a dark indigo glass face panel (#1E1B3A) like a small phone screen, two glowing cyan pill-shaped eyes (#4FE3FF), a single curled stem on top ending in a glowing leaf-shaped light (cyan), two tiny stubby arms, no legs, floating, thin deep-indigo outline.
Avoid: green color as main color, real plant leaves, plant pot, soil, water drops, hearts, pulse lines, medical or fitness symbols.
```

### 1. Master reference — neutral
```
[Style anchor]
Bibi in a calm neutral pose, arms relaxed at its sides, eyes open and friendly, sprout glowing soft cyan. This image will be the reference for all other poses.
```

### 2. Three-quarter view (for the animator)
```
[Attach reference] Use the attached character exactly...
Same character turned 3/4 to the right, showing the depth of the face panel and how the sprout stem attaches to the top of the shell. Neutral expression.
```

### 3. Idle
```
[Attach reference] ... Bibi idling: gently floating, eyes relaxed, slight smile shape in the eyes, sprout soft cyan.
```

### 4. Listening
```
[Attach reference] ... Bibi listening: leaning slightly forward, eyes wide and round, two thin cyan rings pulsing out from the sprout light.
```

### 5. Thinking
```
[Attach reference] ... Bibi thinking: head tilted, eyes looking up to the side, one tiny arm touching the bottom of its face panel, sprout glowing violet (#7C5CFF), three small glowing dots orbiting the sprout.
```

### 6. Speaking
```
[Attach reference] ... Bibi talking: happy arc eyes, a short glowing cyan line mouth on the face panel, one arm raised in a small friendly gesture.
```

### 7. Doing a task — timer
```
[Attach reference] ... Bibi holding up a small round stopwatch icon with both arms, focused eyes, sprout glowing warm amber (#FFB547). Clean flat icon, no numbers.
```

### 8. Doing a task — flashlight
```
[Attach reference] ... Bibi holding a tiny flashlight that shines a soft amber beam to the side, sprout glowing amber, determined happy eyes.
```

### 9. Doing a task — opening an app
```
[Attach reference] ... Bibi tapping a small floating rounded-square app tile (blank, no logo) with one arm, sprout amber, eyes focused.
```

### 10. Done / success
```
[Attach reference] ... Bibi doing a small happy hop, happy ^ ^ arc eyes, a few small amber sparkles around the sprout.
```

### 11. Confused / error (friendly, never sad)
```
[Attach reference] ... Bibi confused but friendly: one eye pill slightly smaller, head tilt, the sprout stem curled into a question-mark shape, sprout dim violet. Not sad, not crying.
```

### 12. Needs confirmation
```
[Attach reference] ... Bibi holding up a small blank rounded card in front of it with both arms, attentive eyes looking at the viewer, sprout amber. The card is blank (no text).
```

### 13. Sleeping
```
[Attach reference] ... Bibi asleep: eyes are two closed curved lines, body slightly curled, sprout dim with a slow soft glow, one small "z" shape floating (as a shape, not a letter in a font).
```

### 14. In context — floating over a phone
```
[Attach reference] ... A modern Android phone screen showing a blurred generic home screen (no real app logos), with Bibi small and floating near the right edge of the screen, casting a soft glow. Clean product shot, the phone fills the frame.
```

### 15. App icon concept
```
[Attach reference] ... App icon concept: only Bibi's head (seed top, face panel with cyan eyes, glowing sprout) centered on a rounded-square violet (#7C5CFF) background, bold and simple so it reads at 48 px. No text.
```

### 16. Silhouette check
```
[Attach reference] ... Same pose as the reference, rendered as a solid single dark indigo silhouette on a white background, no details, to test that the shape is recognizable.
```

---

## 4. Review checklist (for each image)
- [ ] Reads as **tech/AI helper** in under a second, not plant or health.
- [ ] Same proportions, colors, and face panel as the master reference.
- [ ] Still recognizable at 64 px (shrink the image and look).
- [ ] The state is clear from the pose and shape, not only from color.
- [ ] No text, logos, or extra characters.
- [ ] Friendly, never sad, needy, or guilt-inducing (PRD §17.2.3, §17.7).
