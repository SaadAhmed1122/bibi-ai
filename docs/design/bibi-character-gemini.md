# Bibi character — brief + Gemini image prompts

**Direction (owner, 2026-10-29):** Bibi is a **cute cartoon cat**. It's warm, soft and friendly, with **nothing robotic or "AI-looking"**, so people feel comfortable and safe using an AI assistant. The owner's style reference is a hand-drawn sitting orange kitten with big brown eyes, a cream chest, a striped tail and a little collar charm.
The earlier directions (seed, mochi puff with a screen-face) are dropped.

These prompts are for **Gemini image generation**: one prompt → **one image** of **one character**. Don't use sheets, grids, or multi-panel layouts. The Codex prompts in [codex-design-prompts.md](codex-design-prompts.md) later turn the approved images into vector assets.

> **Reference image:** use it only as a mood and style guide. Don't attach it to Gemini or trace it, because it may be someone else's artwork. Bibi's master image (Prompt 1) becomes our own reference.

---

## 1. Character brief

**Who Bibi is:** a small, gentle kitten who keeps you company on your phone. Calm, curious, a little playful, always polite. When you ask for something, Bibi listens with ears up, thinks with a tilted head, and then helps.

**Why a cat:** a familiar, soft animal feels safe and non-threatening, and lowers the "talking to a machine" anxiety. It also fits overlay behaviors naturally (PRD §17.3): sitting on the screen edge, napping in quiet hours, stretching, peeking. Bibi is still always **disclosed as an AI** in the app (PRD §17.7, FR-188). It never claims to be a real pet or to have real feelings.

### What makes Bibi recognizable (not just "a cat")
Cat mascots are common, so Bibi gets three signature details:
1. **Chat-bubble charm:** a small round charm shaped like a **speech bubble** hangs from its collar. It **glows softly** to show what Bibi is doing. It's the only "assistant" hint, and it's subtle and cute, not techy.
2. **Signature marking:** a small cream **crescent-moon patch** on the forehead between the ears.
3. **Violet collar:** a soft violet collar (the brand color) against warm orange fur.

| Part | Design | Why |
|---|---|---|
| Body | Small round kitten, sitting; big head (about 45% of the height), short body, soft rounded shapes | Big head = cute and readable at small size |
| Fur | Warm orange (`#F4A54A`) with darker orange tabby stripes (`#E07B2A`), cream chest, muzzle and paws (`#FFF4E0`) | Warm, friendly, close to the reference mood |
| Face | Big round **dark-brown eyes** (`#4A1E14`) with two white highlights each, small pink nose (`#F2A7A0`), small "w" mouth, a few thin whiskers | Gentle and trustworthy; no glowing or screen eyes |
| Ears | Large triangular ears with pink insides | Very expressive: up when listening, down when sorry or sleepy |
| Forehead | Cream crescent-moon patch | Signature marking |
| Collar | Soft violet (`#7C5CFF`) with a **chat-bubble charm** | Brand color plus the assistant cue |
| Tail | Striped, curled; very expressive | Shows state: question-mark curl when confused, wraps around the body when sleeping |
| Paws | Cream mitten paws | Can hold small items (stopwatch, flashlight, card) |
| Outline | Warm dark-brown outline (`#5A2E1E`), slightly hand-drawn line weight | Matches the reference's hand-drawn warmth and reads on any wallpaper |

**Palette (proposal, tune after the first images):**

| Role | Color | Hex |
|---|---|---|
| Fur | Warm orange | `#F4A54A` |
| Stripes | Deep orange | `#E07B2A` |
| Chest, muzzle, paws, moon patch | Cream | `#FFF4E0` |
| Eyes | Dark brown | `#4A1E14` |
| Nose, inner ears | Soft pink | `#F2A7A0` |
| Outline | Warm brown | `#5A2E1E` |
| Collar (brand primary) | Violet | `#7C5CFF` |
| Charm glow | Soft gold (idle) / changes by state | `#FFD36B` |

**Avoid:** robots, screens, antennas, circuits, glowing eyes, sci-fi elements; realistic or creepy cat anatomy; sharp teeth or claws; sad or crying faces; and copying the reference image's exact markings or heart pendant.

**State = ears + tail + pose, with the charm glow as a bonus** (color is never the only signal, PRD §17.6):

| State | Ears | Eyes | Tail / pose | Charm glow |
|---|---|---|---|---|
| Idle | relaxed | soft, slow blink | sitting, tail curled around the side | soft gold |
| Listening | perked up, turned forward | wide and bright | leaning forward slightly | light blue |
| Thinking | one ear tilted | looking up to the side | head tilt, paw raised to chin | violet, three small dots above the head |
| Speaking | relaxed up | happy | small paw gesture, mouth open slightly | soft gold |
| Doing a task | up | focused | holding the item with both paws | warm amber |
| Done | up | happy closed arcs ^ ^ | small hop, tail straight up (happy cat) | amber sparkle |
| Confused / error | one ear down | one eyebrow up | tail curled into a question mark | dim violet |
| Needs confirmation | up | attentive, looking at the user | holding a small blank card | amber |
| Sleeping | flat | closed | curled in a ball, tail over the nose, small "z" | dim, slow pulse |
| Being dragged | back | surprised | dangling gently like a kitten picked up, paws together | light blue |

---

## 2. How to use these prompts in Gemini

1. Paste **Prompt A (style anchor)** plus **Prompt 1 (master)**. Generate several times and pick the best one. That image becomes **Bibi's reference**.
2. For every later prompt, **attach Bibi's master image** (not the original reference cat) and start with:
   `Use the attached character exactly — same kitten, fur colors, stripes, crescent-moon forehead patch, violet collar with chat-bubble charm, eyes and proportions. Change only what is described below.`
3. **One prompt per message, one image per prompt.** If Gemini returns a grid or several characters, reply: `Only one character, one single image, no panels, no grid.`
4. Save each approved image as `design/character/gemini/<nn>-<state>.png` so Codex can trace it later.
5. Treat Gemini output as **concept art**. Final in-app assets are vector (see the Codex prompts). Check Gemini's terms for commercial use of generated images before using them in the store listing [VERIFY].

---

## 3. Prompts

### A. Style anchor (put at the start of every prompt)
```
Style: cute hand-drawn cartoon illustration, clean warm-brown outlines with slightly varied line weight, soft flat colors with gentle cel shading, cozy and friendly, high readability at small size. Single character only, centered, full body, plain white background, soft light-beige oval shadow under the character. No text, no letters, no logos, no watermark, no frame, no grid, no multiple panels, no extra characters.
Character "Bibi": a small cute kitten with a big round head, warm orange fur (#F4A54A) with darker orange tabby stripes (#E07B2A), cream chest, muzzle and paws (#FFF4E0), a small cream crescent-moon shaped patch on its forehead, big round dark-brown eyes with two white highlights each, small pink nose, small smiling "w" mouth, thin whiskers, large ears with pink insides, a striped curled tail, and a soft violet collar (#7C5CFF) with a small round charm shaped like a speech bubble that glows softly.
Avoid: robots, screens, antennas, glowing eyes, sci-fi elements, sharp teeth, claws, realistic anatomy, sad or crying expressions, heart-shaped pendant.
```

### 1. Master reference — sitting, neutral
```
[Style anchor]
Bibi sitting upright facing the viewer, paws together in front, tail curled around one side, calm friendly expression, charm glowing soft gold (#FFD36B). This image will be the reference for all other poses.
```

### 2. Three-quarter view (for the animator)
```
[Attach Bibi master] Use the attached character exactly...
Same kitten sitting, turned 3/4 to the right, showing the tail, the collar and charm from the side, and the shape of the ears. Neutral friendly expression.
```

### 3. Idle — slow blink
```
[Attach Bibi master] ... Bibi sitting relaxed, eyes in a gentle half-closed "slow blink" (a friendly cat gesture), tail curled, charm soft gold.
```

### 4. Listening
```
[Attach Bibi master] ... Bibi listening carefully: ears perked up and turned forward, eyes wide and bright, leaning slightly forward, charm glowing light blue (#8FD3FF).
```

### 5. Thinking
```
[Attach Bibi master] ... Bibi thinking: head tilted, one ear slightly tilted, eyes looking up to the side, one paw raised to its chin, three small violet dots floating above its head, charm glowing violet (#7C5CFF).
```

### 6. Speaking
```
[Attach Bibi master] ... Bibi talking happily: mouth slightly open in a friendly smile, eyes happy, one paw raised in a small "let me explain" gesture, charm soft gold.
```

### 7. Doing a task — timer
```
[Attach Bibi master] ... Bibi holding a small round stopwatch with both front paws, focused but happy eyes, ears up, charm glowing warm amber (#FFB547). Simple flat stopwatch, no numbers.
```

### 8. Doing a task — flashlight
```
[Attach Bibi master] ... Bibi holding a tiny flashlight with both paws, shining a soft warm beam to the side, ears up, charm amber, determined cute expression.
```

### 9. Doing a task — opening an app
```
[Attach Bibi master] ... Bibi gently tapping a small floating rounded-square tile (blank, no logo) with one paw, like a cat batting at something, curious eyes, charm amber.
```

### 10. Done / success
```
[Attach Bibi master] ... Bibi doing a small happy hop, eyes closed in happy ^ ^ arcs, tail straight up (a happy cat signal), a few small amber sparkles around it, charm amber.
```

### 11. Confused / error (friendly, never sad)
```
[Attach Bibi master] ... Bibi confused but cute: head tilted, one ear down, one eyebrow raised, tail curled into a question-mark shape, charm dim violet. Not sad, not crying.
```

### 12. Needs confirmation
```
[Attach Bibi master] ... Bibi holding up a small blank rounded card in front of its chest with both paws, attentive eyes looking at the viewer, ears up, charm amber. The card is blank (no text).
```

### 13. Sleeping
```
[Attach Bibi master] ... Bibi asleep, curled up in a round ball, tail wrapped over its nose, eyes closed, ears relaxed, one small "z" shape floating above (a shape, not a font letter), charm dim.
```

### 14. Being dragged
```
[Attach Bibi master] ... Bibi gently lifted and dangling like a kitten being carried, front paws together, back legs relaxed, tail swaying, surprised but calm round eyes. Cute, not scared.
```

### 15. In context — sitting on the screen edge
```
[Attach Bibi master] ... A modern Android phone screen showing a blurred generic home screen (no real app logos), with Bibi small and sitting on the bottom-right edge of the screen, tail hanging over the edge. Clean product shot, the phone fills the frame.
```

### 16. App icon concept
```
[Attach Bibi master] ... App icon concept: only Bibi's face (big eyes, crescent-moon forehead patch, ears, small smile) centered on a rounded-square violet (#7C5CFF) background, bold and simple so it reads at 48 px. No text.
```

### 17. Silhouette check
```
[Attach Bibi master] ... Same sitting pose as the reference, rendered as a solid single dark-brown silhouette on a white background, no details, to test that the ears, head and tail shape are recognizable.
```

---

## 4. From illustration to the real app (technical note)

The in-app Bibi must fit the overlay budget (≤ 4 MB GPU memory, 12/24 fps, PRD §17.5, §12.10). The hand-drawn cartoon style works well for this:
- Flat colors plus outlines become clean vector shapes or small sprite frames. Fur is suggested with a few line strokes on the chest and cheeks, never per-hair detail.
- Animation lives in **ears, eyes, tail and paws** (separate layers), which is cheap and very expressive.
- The cat **sits on the screen edge** instead of floating, which fits PRD behaviors B-01 (walk to edge), B-03 (sleep), and B-15 (fall/land when dropped).
- Full Gemini illustrations can be used for the store listing, onboarding art and the icon, pending the Gemini license check [VERIFY].

---

## 5. Review checklist (for each image)
- [ ] Feels **warm, safe and friendly** in under a second, with nothing robotic.
- [ ] Recognizably *Bibi*, not a generic cat: crescent-moon patch, violet collar and chat-bubble charm all visible.
- [ ] Same proportions, colors and markings as Bibi's master image.
- [ ] Still recognizable at 64 px (shrink the image and look).
- [ ] The state is clear from ears, tail and pose, not only from the charm color.
- [ ] No text, logos, extra characters, sharp teeth or claws.
- [ ] Friendly, never sad, needy, or guilt-inducing (PRD §17.2.3, §17.7).
- [ ] Not a copy of the reference image's exact markings or pendant.
