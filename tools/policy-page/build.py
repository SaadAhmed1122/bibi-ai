"""Builds the hosted privacy policy page from docs/privacy-policy.md (the same text as the in-app copy).

    python3 -I tools/policy-page/build.py OUT_DIR

Then copy OUT_DIR/index.html to the public SaadAhmed1122/bibi-ai-privacy repo (GitHub Pages, main branch).
The text format: line 1 title, numbered section headings ("1. ..."), "- " bullets, other lines paragraphs.
"""
import html
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "..", "docs", "privacy-policy.md")

STYLE = """body{font:17px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;max-width:42rem;margin:0 auto;padding:2rem 1.25rem 4rem;color:#2b211c;background:#fbf6f1}
h1{font-size:2rem;line-height:1.2;margin:0 0 .25rem}h2{font-size:1.15rem;margin:2rem 0 .5rem}
.date{color:#6b5d55;margin:0 0 1.5rem}ul{padding-left:1.25rem}li{margin:.25rem 0}
@media(prefers-color-scheme:dark){body{background:#1d1714;color:#efe6df}.date{color:#b8a99f}}"""


def build(text):
    lines = [l.rstrip() for l in text.strip().splitlines()]
    title, body, in_list = lines[0].lstrip("# "), [], False
    for line in lines[1:]:
        if line.startswith("- "):
            if not in_list:
                body.append("<ul>")
                in_list = True
            body.append(f"<li>{html.escape(line[2:])}</li>")
            continue
        if in_list:
            body.append("</ul>")
            in_list = False
        if not line:
            continue
        if line.startswith("Last updated:"):
            body.append(f'<p class="date">{html.escape(line)}</p>')
        elif re.match(r"^#*\s*\d+\. ", line):
            body.append(f"<h2>{html.escape(line.lstrip('# '))}</h2>")
        else:
            body.append(f"<p>{html.escape(line)}</p>")
    if in_list:
        body.append("</ul>")
    return (f'<!DOCTYPE html>\n<html lang="en"><head><meta charset="utf-8">'
            f'<meta name="viewport" content="width=device-width,initial-scale=1">'
            f"<title>{html.escape(title)}</title><style>{STYLE}</style></head>\n"
            f"<body><h1>{html.escape(title)}</h1>\n" + "\n".join(body) + "\n</body></html>\n")


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    with open(SRC, encoding="utf-8") as f, open(os.path.join(out, "index.html"), "w", encoding="utf-8") as o:
        o.write(build(f.read()))
    print(os.path.join(out, "index.html"))
