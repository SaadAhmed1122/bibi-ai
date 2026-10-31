import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import sys
OUT = sys.argv[1]
VIOLET, MUTED, INK, GRID = "#7C5CFF", "#B9B4C7", "#2B2340", "#E6E3EE"

def bars(name, title, subtitle, labels, values, fmt, limit=None, limit_label="", good=lambda v: True, unit=""):
    fig, ax = plt.subplots(figsize=(8, 4.5), dpi=150)
    colors = [VIOLET if good(v) else MUTED for v in values]
    b = ax.bar(labels, values, color=colors, width=0.55, zorder=2)
    for rect, v in zip(b, values):
        ax.text(rect.get_x() + rect.get_width() / 2, rect.get_height(), fmt(v), ha="center", va="bottom",
                fontsize=13, color=INK, fontweight="bold")
    if limit is not None:
        ax.axhline(limit, color=INK, linestyle=(0, (4, 3)), linewidth=1.5, zorder=3)
        ax.text(len(values) - 0.5, limit, f" {limit_label}", va="bottom", ha="right", fontsize=10, color=INK)
    ax.set_title(title, loc="left", fontsize=15, color=INK, fontweight="bold", pad=24)
    ax.text(0, 1.02, subtitle, transform=ax.transAxes, fontsize=10, color="#6B6580")
    ax.set_ylabel(unit, color="#6B6580")
    for s in ("top", "right", "left"):
        ax.spines[s].set_visible(False)
    ax.spines["bottom"].set_color(GRID)
    ax.yaxis.grid(True, color=GRID, zorder=0)
    ax.tick_params(colors=INK, labelsize=11, length=0)
    top = max(values + ([limit] if limit else []))
    ax.set_ylim(0, top * 1.22)
    fig.text(0.99, 0.01, "Bibi AI · vivo Y17s (6 GB, Helio G85) · on-device, offline", ha="right", fontsize=8, color="#6B6580")
    fig.tight_layout()
    fig.savefig(f"{OUT}/{name}.png")

bars("s2-first-word-time", "Time until the first word appears (p95)",
     "Gemma 3 1B int4 on LiteRT-LM, 50 prompts. Grey = over the 3 s limit.",
     ["New chat for\nevery message", "One ongoing chat\n(system prompt reused)"], [3.493, 2.409],
     lambda v: f"{v:.1f} s", limit=3.0, limit_label="limit 3 s", good=lambda v: v <= 3.0, unit="seconds")
bars("s4-actions-right", "Phone actions done right, out of 100 requests",
     "\"Set an alarm for 7\", \"open Maps\"… Grey = not usable.",
     ["Small LLM writes\nthe tool call", "Router + simple\nrule-based slots"], [0, 87],
     lambda v: f"{v:.0f}/100", good=lambda v: v >= 50, unit="correct actions")
bars("s1-battery-cost", "Extra battery current for the floating character",
     "Measured against the same phone without the overlay, 1-hour runs, screen on.",
     ["Animated\n12 fps", "Calm mode\n2 fps"], [27.8, 11.5],
     lambda v: f"+{v:.0f} mA", limit=150, limit_label="limit 150 mA", good=lambda v: v <= 150, unit="extra mA")
bars("s3-voice-speed", "Voice speed: time to process 1 s of audio",
     "Best setting of each model. Under 1.0 = faster than real time. Grey = too slow.",
     ["Kokoro\n(speech out)", "Piper\n(speech out)", "Parakeet\n(speech in)"], [3.769, 0.248, 0.324],
     lambda v: f"{v:.2f} s", limit=1.0, limit_label="real time", good=lambda v: v < 1.0, unit="seconds per 1 s of audio")
print("ok")
