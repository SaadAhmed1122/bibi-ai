"""Build Bibi's state SVGs from the vector master.

Each state starts from docs/design/character/master/front_neutral.svg and swaps,
moves or recolors named layers (<g id="...">). Re-run after editing the master:

    python3 -I tools/build_states.py

Output: docs/design/character/states/<state>.svg + states.json
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MASTER = ROOT / "docs/design/character/master/front_neutral.svg"
OUT = ROOT / "docs/design/character/states"
PALETTE = json.loads((ROOT / "docs/design/character/master/palette.json").read_text())
GLOW = PALETTE["state_glow"]

FUR, CREAM, OUTLINE = "#FEB367", "#FDEDD3", "#6C3316"


def replace_group(svg: str, gid: str, body: str) -> str:
    """Replace the contents of <g id="gid" ...>...</g>, handling nested groups."""
    start = svg.find(f'<g id="{gid}"')
    if start < 0:
        raise ValueError(f"group {gid!r} not found")
    open_end = svg.index(">", start) + 1
    depth, i = 1, open_end
    for m in re.finditer(r"<g[ >]|</g>", svg[open_end:]):
        depth += -1 if m.group() == "</g>" else 1
        if depth == 0:
            close = open_end + m.start()
            return svg[:open_end] + body + svg[close:]
    raise ValueError(f"group {gid!r} is not closed")


def set_group_attr(svg: str, gid: str, attrs: str) -> str:
    tag = f'<g id="{gid}"'
    if tag not in svg:
        raise ValueError(f"group {gid!r} not found")
    return svg.replace(tag, f'{tag} {attrs}', 1)


def set_glow(svg: str, color: str) -> str:
    return re.sub(r'(<stop offset="[01]" stop-color=")#FFD36B(" stop-opacity)', r"\g<1>" + color + r"\2", svg)


def set_title(svg: str, title: str) -> str:
    return re.sub(r"<title>.*?</title>", f"<title>{title}</title>", svg, count=1)


def add_before_head(svg: str, fragment: str) -> str:
    return svg.replace('    <g id="head"', fragment + '\n    <g id="head"', 1)


def add_after_head(svg: str, fragment: str) -> str:
    # The head group is the last child of #bibi; insert before the two closing tags.
    idx = svg.rindex("  </g>\n</svg>")
    return svg[:idx] + fragment + "\n" + svg[idx:]


# --- reusable parts -------------------------------------------------------

CLOSED_EYES = (
    '\n        <path fill="none" stroke-width="2.4" d="M 93 107 C 97 112, 109 112, 113 107"/>'
    '\n        <path fill="none" stroke-width="2.4" d="M 143 107 C 147 112, 159 112, 163 107"/>\n      '
)


def eye(cx, cy, r, hx=-4.5, hy=-4.5):
    return (
        f'\n        <circle cx="{cx}" cy="{cy}" r="{r}" fill="#773923" stroke-width="1.6"/>'
        f'\n        <path stroke="none" fill="#8E4A30" d="M {cx-8.5*r/11:.1f} {cy+5*r/11:.1f} '
        f'C {cx-5*r/11:.1f} {cy+10*r/11:.1f}, {cx+5*r/11:.1f} {cy+10*r/11:.1f}, {cx+8.5*r/11:.1f} {cy+5*r/11:.1f} '
        f'C {cx+5*r/11:.1f} {cy+7.5*r/11:.1f}, {cx-5*r/11:.1f} {cy+7.5*r/11:.1f}, {cx-8.5*r/11:.1f} {cy+5*r/11:.1f} Z"/>'
        f'\n        <circle cx="{cx+hx*r/11:.1f}" cy="{cy+hy*r/11:.1f}" r="{3.6*r/11:.1f}" fill="#FFFFFF" stroke="none"/>'
        f'\n        <circle cx="{cx+3*r/11:.1f}" cy="{cy+0.5*r/11:.1f}" r="{1.6*r/11:.1f}" fill="#FFFFFF" stroke="none"/>\n      '
    )


def raised_arm(paw_x, paw_y, shoulder_x, side):
    """A soft, slightly bowed orange foreleg from the shoulder to a cream paw.

    The shoulder end is filled but not outlined, so the leg blends into the body.
    """
    sy = 178
    bow = 6 if paw_x > shoulder_x else -6  # bow the leg outward, away from the body center
    lx0, rx0 = shoulder_x - 9, shoulder_x + 9          # shoulder width
    lx1, rx1 = paw_x - 7, paw_x + 7                    # wrist width
    left = f"M {lx0} {sy} C {lx0+bow} {sy-14}, {lx1+bow} {paw_y+16}, {lx1} {paw_y+2}"
    right = f"M {rx0} {sy} C {rx0+bow} {sy-14}, {rx1+bow} {paw_y+16}, {rx1} {paw_y+2}"
    fill = (f"M {lx0} {sy} C {lx0+bow} {sy-14}, {lx1+bow} {paw_y+16}, {lx1} {paw_y+2} "
            f"L {rx1} {paw_y+2} C {rx1+bow} {paw_y+16}, {rx0+bow} {sy-14}, {rx0} {sy} Z")
    return (
        f'\n    <g id="arm-{side}">'
        f'\n      <path stroke="none" fill="{FUR}" d="{fill}"/>'
        f'\n      <path fill="none" d="{left}"/>'
        f'\n      <path fill="none" d="{right}"/>'
        f'\n      <path stroke="none" fill="#E9833B" d="M {lx1+1} {paw_y+14} C {paw_x-2} {paw_y+12.5}, {paw_x+2} {paw_y+12.5}, {rx1-1} {paw_y+14} '
        f'C {paw_x+2} {paw_y+16}, {paw_x-2} {paw_y+16}, {lx1+1} {paw_y+14} Z"/>'
        f'\n      <ellipse cx="{paw_x}" cy="{paw_y}" rx="9" ry="8" fill="{CREAM}"/>'
        f'\n      <path d="M {paw_x-3} {paw_y-7.5} L {paw_x-3} {paw_y-4} M {paw_x+3} {paw_y-7.5} L {paw_x+3} {paw_y-4}" stroke-width="1.3"/>'
        f'\n    </g>'
    )


# --- states -----------------------------------------------------------------

def idle(svg):
    """The master pose with the idle glow. Frame 1 of the idle loop."""
    return set_glow(svg, GLOW["idle"])


def idle_blink(svg):
    """Slow blink: a friendly cat signal. Frame used by the idle loop."""
    svg = set_glow(svg, GLOW["idle"])
    svg = replace_group(svg, "eye-left", "")
    svg = replace_group(svg, "eye-right", "")
    svg = svg.replace('<g id="eyelids" display="none">', '<g id="eyelids">', 1)
    svg = replace_group(svg, "eyelids", CLOSED_EYES)
    return svg


def thinking(svg):
    """Head tilt, gaze up, paw to chin, three dots, violet charm."""
    svg = set_glow(svg, GLOW["thinking"])
    svg = set_group_attr(svg, "head", 'transform="rotate(-8 128 142)"')
    svg = replace_group(svg, "eye-left", eye(104, 104, 10.5, hx=-2.5, hy=-6))
    svg = replace_group(svg, "eye-right", eye(154, 104, 10.5, hx=-2.5, hy=-6))
    svg = replace_group(svg, "mouth", '\n        <path d="M 123 123 C 126 121.5, 130 121.5, 133 123"/>\n      ')
    # right foreleg lifts to the chin: hide its ground paw and leg line
    svg = replace_group(svg, "paw-right", "")
    svg = svg.replace('<path d="M 155 168 C 151 181, 148 194, 147.5 204"/>', "", 1)
    svg = add_after_head(svg, raised_arm(146, 146, 150, "right"))
    dots = (
        '\n    <g id="thinking-dots" stroke="none" fill="#7C5CFF">'
        '\n      <circle cx="190" cy="58" r="3"/><circle cx="201" cy="49" r="3.6"/><circle cx="213" cy="39" r="4.2"/>'
        '\n    </g>'
    )
    return add_after_head(svg, dots)


def speaking(svg):
    """Open mouth, happy eyes, one paw raised in a small gesture."""
    svg = set_glow(svg, GLOW["idle"])
    svg = replace_group(svg, "mouth", (
        '\n        <path d="M 128 119 L 128 121.5"/>'
        '\n        <path fill="#8B3A2A" d="M 121.5 122 C 122 131.5, 134 131.5, 134.5 122 C 131 124.5, 125 124.5, 121.5 122 Z"/>'
        '\n        <ellipse cx="128" cy="128" rx="3.6" ry="2" fill="#FDB4B3" stroke="none"/>'
        '\n        <path d="M 117 120.5 C 118.5 125, 125 125.5, 128 121.5 C 131 125.5, 137.5 125, 139 120.5"/>\n      '
    ))
    svg = replace_group(svg, "paw-left", "")
    svg = svg.replace('<path d="M 101 168 C 105 181, 108 194, 108.5 204"/>', "", 1)
    return add_before_head(svg, raised_arm(92, 150, 104, "left"))


def confused(svg):
    """Head tilt the other way, one eye smaller, raised brow, wavy mouth, question-mark tail."""
    svg = set_glow(svg, GLOW["confused"])
    svg = set_group_attr(svg, "head", 'transform="rotate(9 128 142)"')
    svg = replace_group(svg, "eye-left", eye(104, 108, 9))
    svg = replace_group(svg, "eye-right", eye(153, 106, 11.5))
    svg = replace_group(svg, "mouth", '\n        <path d="M 120 124 C 123 121, 125 126, 128 123.5 C 131 121, 133 126, 136 123"/>\n      ')
    brow = '\n      <g id="brow" fill="none" stroke-width="2"><path d="M 144 89 C 150 85.5, 157 85.5, 163 89"/></g>'
    svg = svg.replace('      <g id="nose">', brow.lstrip("\n") + "\n      <g id=\"nose\">", 1)
    # question-mark tail: a thick double-stroked curve instead of the filled tail shape
    q = "M 92 197 C 70 200, 55 188, 55 171 C 55 160, 64 152, 64 141 C 64 130, 51 126, 45 134"
    tail = (
        f'\n      <path fill="none" stroke="{OUTLINE}" stroke-width="14" d="{q}"/>'
        f'\n      <path fill="none" stroke="{FUR}" stroke-width="9.6" d="{q}"/>'
        f'\n      <path fill="none" stroke="#E9833B" stroke-width="9.6" stroke-linecap="butt" stroke-dasharray="4 10" stroke-dashoffset="-8" d="{q}"/>\n    '
    )
    return replace_group(svg, "tail", tail)


STATES = {
    "idle": (idle, "Ambient", "Bibi sits calmly, ready to help."),
    "idle_blink": (idle_blink, "Ambient", "Bibi slowly blinks, a friendly cat hello."),
    "thinking": (thinking, "Interaction", "Bibi tilts its head with a paw on its chin, thinking."),
    "speaking": (speaking, "Interaction", "Bibi is talking and gesturing with one paw."),
    "confused": (confused, "Reaction", "Bibi tilts its head, puzzled; something didn't work."),
}


def main():
    master = MASTER.read_text()
    OUT.mkdir(parents=True, exist_ok=True)
    index = []
    for name, (fn, group, a11y) in STATES.items():
        svg = set_title(fn(master), f"Bibi — {name.replace('_', ' ')}")
        (OUT / f"{name}.svg").write_text(svg)
        index.append({"id": name, "file": f"{name}.svg", "group": group,
                      "charm_glow": GLOW.get(name.split("_")[0], GLOW["idle"]),
                      "a11y": a11y})
    (OUT / "states.json").write_text(json.dumps({"source": "tools/build_states.py", "states": index}, indent=2) + "\n")
    print("\n".join(f"wrote {OUT.relative_to(ROOT)}/{s['file']}" for s in index))


if __name__ == "__main__":
    main()
