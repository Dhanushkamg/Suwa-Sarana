// Verifies the design tokens in src/app/globals.css meet WCAG 2.2 AA in BOTH themes.
//   normal text >= 4.5:1, large text / UI components / focus indicators >= 3:1
// Run: pnpm check:contrast   (exit code 1 on any failure, so CI can gate on it)
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const css = readFileSync(join(here, '..', 'src', 'app', 'globals.css'), 'utf8');

/** Collects `--token: value;` declarations from the rule block whose selector contains `marker`. */
function tokensFor(marker) {
  const tokens = {};
  const re = /([^{}]+)\{([^{}]*)\}/g;
  for (const [, selector, body] of css.matchAll(re)) {
    if (!selector.includes(marker) || selector.includes('@media') || selector.includes('not(')) continue;
    for (const [, name, value] of body.matchAll(/--([\w-]+):\s*([^;]+);/g)) tokens[name] = value.trim();
  }
  return tokens;
}

const light = tokensFor('[data-theme="light"]');
const dark = { ...light, ...tokensFor('[data-theme="dark"]') };

function parse(value) {
  const hex = value.match(/^#([0-9a-f]{6})$/i);
  if (hex) return { r: parseInt(hex[1].slice(0, 2), 16), g: parseInt(hex[1].slice(2, 4), 16), b: parseInt(hex[1].slice(4, 6), 16), a: 1 };
  const rgba = value.match(/^rgba?\(([^)]+)\)$/);
  if (rgba) {
    const [r, g, b, a = 1] = rgba[1].split(',').map((n) => parseFloat(n));
    return { r, g, b, a };
  }
  throw new Error(`Cannot parse colour: ${value}`);
}

const over = (fg, bg) => ({ r: fg.r * fg.a + bg.r * (1 - fg.a), g: fg.g * fg.a + bg.g * (1 - fg.a), b: fg.b * fg.a + bg.b * (1 - fg.a), a: 1 });
const lin = (c) => ((c /= 255) <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const luminance = ({ r, g, b }) => 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
const ratio = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

// [foreground token, background token, minimum ratio, description]
const PAIRS = [
  ['foreground', 'background', 4.5, 'body text on page'],
  ['foreground', 'surface', 4.5, 'body text on cards'],
  ['muted', 'background', 4.5, 'secondary text on page'],
  ['muted', 'surface', 4.5, 'secondary text on cards'],
  ['muted', 'surface-raised', 4.5, 'secondary text on raised surfaces'],
  ['primary-fg', 'primary', 4.5, 'text on primary button'],
  ['primary-text', 'background', 4.5, 'links on page'],
  ['primary-text', 'surface', 4.5, 'links on cards'],
  ['brand-fg', 'brand', 4.5, 'text on brand (urgent) button'],
  ['danger-fg', 'danger', 4.5, 'text on destructive button'],
  ['danger-text', 'surface', 4.5, 'error text on cards'],
  ['danger-text', 'danger-soft', 4.5, 'error badge text'],
  ['warning-text', 'warning-soft', 4.5, 'warning badge text'],
  ['success-text', 'success-soft', 4.5, 'success badge text'],
  ['info-text', 'info-soft', 4.5, 'info badge text'],
  ['primary', 'background', 3, 'focus ring / primary UI component on page'],
  ['primary', 'surface', 3, 'primary UI component on cards'],
  ['control', 'surface', 3, 'input / checkbox / switch border on cards'],
  ['control', 'background', 3, 'input / checkbox / switch border on page'],
];

let failures = 0;
for (const [name, tokens] of [['light', light], ['dark', dark]]) {
  console.log(`\n${name.toUpperCase()} theme`);
  const page = parse(tokens.background);
  for (const [fgName, bgName, min, label] of PAIRS) {
    const bgRaw = parse(tokens[bgName]);
    // translucent backgrounds (e.g. danger-soft) are painted over the card surface
    const bg = bgRaw.a < 1 ? over(bgRaw, parse(tokens.surface)) : bgRaw;
    const fgRaw = parse(tokens[fgName]);
    const fg = fgRaw.a < 1 ? over(fgRaw, bg) : fgRaw;
    const r = ratio(fg, bg);
    const ok = r >= min;
    if (!ok) failures++;
    console.log(`${ok ? 'ok  ' : 'FAIL'} ${r.toFixed(2).padStart(5)}:1 (min ${min}) ${fgName} on ${bgName} - ${label}`);
  }
  void page;
}

if (failures) {
  console.error(`\n${failures} contrast check(s) failed`);
  process.exit(1);
}
console.log('\nAll contrast checks passed');
