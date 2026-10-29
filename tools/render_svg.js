// Render an SVG to PNG with the preinstalled Playwright Chromium.
// usage: node render.js in.svg out.png size [bg]
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const [inp, out, size, bg] = process.argv.slice(2);
  const svg = fs.readFileSync(inp, 'utf8');
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: +size, height: +size } });
  await p.setContent(`<html><body style="margin:0;background:${bg||'transparent'}"><img src="data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}" width="${size}" height="${size}" style="display:block"></body></html>`);
  await p.screenshot({ path: out, omitBackground: !bg }); await b.close();
})();
