# Bibi — vector master (v1)

Hand-built SVG of Bibi, traced by eye from [`../gemini/01-master.jpeg`](../gemini/01-master.jpeg) (Claude, 2026-10-29). This is the base for all states and animation frames.

| File | What it is |
|---|---|
| `front_neutral.svg` | The master: `viewBox 0 0 256 256`, paws resting at about (128, 232), ground shadow ending at y = 240 |
| `silhouette.svg` | Same shapes in one color, to check that the shape reads at small size |
| `palette.json` | Colors sampled from the Gemini master, plus the charm glow color for each state |
| `preview_64.png`, `preview_128.png`, `preview_512.png` | Renders of the master (transparent background) |

## Layers (`<g id="…">`)
Back to front: `ground-shadow`, `paw-back-left`, `paw-back-right`, `tail`, `body` (with `stripes-body`, `chest`, `legs`), `paw-left`, `paw-right`, `collar`, `charm` (with `charm-glow`), `head` (with `head-fur`, `ear-left`, `ear-right`, `stripes-head`, `muzzle`, `eye-left`, `eye-right`, `eyelids` (hidden), `nose`, `mouth`, `whiskers`).
- Ears, eyes, eyelids, mouth, tail, paws and the charm glow are separate, so states can be made by moving, swapping or recoloring them.
- The head outline (`#head-shape`) includes the ears, so ear rotation needs the ear tips split from the head outline when rigging. This is done in the animation step, not here.
- The tail is one shape. It gets split into 3–4 segments when the curl animations are built (codex-design-prompts.md prompt 3).

## Differences from the Gemini image
- Cleaner and simpler: no fur texture strokes, no tuft at the ear bases, fewer stripes. At the 64–96 dp overlay size those details disappear anyway.
- The body is slightly wider and the head slightly less domed than the Gemini art.
- The collar uses the light violet seen in the image (`#BEA5E8`). Brand violet `#7C5CFF` stays the UI seed color.

## Checks done
- Renders correctly in Chromium (Playwright).
- Readable at 64 px on white and on a dark background.
- Silhouette shows ears, head and tail clearly.
- Not yet checked: rendering in Android (`ImageVector` conversion or a sprite atlas) [VERIFY when the S1 overlay spike uses it].
