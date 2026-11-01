#!/usr/bin/env bash
# Renders Bibi's state SVGs to the 288 px overlay frames (96 dp at 3x) with headless Google Chrome.
# Same Chromium engine as tools/render_svg.js (Playwright); the idle frame renders pixel-identical.
#   tools/render_overlay_frames.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
OUT="$ROOT/core/designsystem/src/main/res/drawable-nodpi"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
for st in idle idle_blink thinking speaking confused; do
  b64="$(base64 -i "$ROOT/docs/design/character/states/$st.svg" | tr -d '\n')"
  echo "<html><body style=\"margin:0;background:transparent\"><img src=\"data:image/svg+xml;base64,$b64\" width=\"288\" height=\"288\" style=\"display:block\"></body></html>" > "$WORK/$st.html"
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --default-background-color=00000000 --window-size=288,288 --screenshot="$OUT/bibi_$st.png" "file://$WORK/$st.html" >/dev/null 2>&1
done
echo "frames written to $OUT"
