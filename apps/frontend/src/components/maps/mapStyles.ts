/**
 * Shared building blocks for the Leaflet maps. Everything here returns HTML strings (Leaflet divIcons and
 * popups are plain DOM), so Tailwind classes do not apply: colours come from the semantic CSS variables
 * (var(--danger) ...), which means open popups and markers follow a theme switch without being rebuilt.
 * Every text argument must already be HTML-escaped (use escapeHtml) by the caller.
 *
 * Marker encoding is never colour alone: each status has its own SHAPE, a GLYPH inside it, a colour, and a
 * short text label under the marker (see statusMarkerHtml).
 */

export type MapTone = 'danger' | 'warning' | 'success' | 'info' | 'primary' | 'muted';
export type MapShape = 'triangle' | 'diamond' | 'circle' | 'square' | 'pin';
export type MapGlyph = 'alert' | 'check' | 'plus' | 'x' | 'dot';

/** Fill/stroke colour, and the contrast-safe text colour for the same tone. */
export const TONE: Record<MapTone, { color: string; text: string }> = {
  danger: { color: 'var(--danger)', text: 'var(--danger-text)' },
  warning: { color: 'var(--warning)', text: 'var(--warning-text)' },
  success: { color: 'var(--success)', text: 'var(--success-text)' },
  info: { color: 'var(--info)', text: 'var(--info-text)' },
  primary: { color: 'var(--primary)', text: 'var(--primary-text)' },
  muted: { color: 'var(--muted)', text: 'var(--muted)' },
};

/** Paths in a 32x32 box. */
export const SHAPE_PATH: Record<MapShape, string> = {
  triangle: 'M16 3.5 29 27H3Z',
  diamond: 'M16 2.5 29.5 16 16 29.5 2.5 16Z',
  circle: 'M16 3a13 13 0 1 0 0 26 13 13 0 0 0 0-26Z',
  square: 'M7 3h18a4 4 0 0 1 4 4v18a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4Z',
  pin: 'M16 29S5 19.5 5 12.5a11 11 0 0 1 22 0C27 19.5 16 29 16 29Z',
};

/** Glyph strokes per shape (the exclamation mark sits lower inside a triangle). */
export function glyphPath(glyph: MapGlyph, shape: MapShape): string {
  switch (glyph) {
    case 'alert':
      return shape === 'triangle' ? 'M16 12v7M16 23h.01' : 'M16 9.5v8M16 22h.01';
    case 'check':
      return 'm10 16.5 4 4 8-9';
    case 'plus':
      return 'M16 10v12M10 16h12';
    case 'x':
      return 'm11 11 10 10m0-10L11 21';
    case 'dot':
      return 'M16 9.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7Z';
  }
}

/** The shape + glyph as an SVG string. Opaque surface fill so the glyph stays readable on any map tile. */
export function shapeSvg(shape: MapShape, glyph: MapGlyph, tone: MapTone, size: number): string {
  const { color, text } = TONE[tone];
  return `<svg width="${size}" height="${size}" viewBox="0 0 32 32" aria-hidden="true" focusable="false"><path d="${SHAPE_PATH[shape]}" fill="var(--surface)" stroke="${color}" stroke-width="2.6" stroke-linejoin="round"/><path d="${glyphPath(glyph, shape)}" fill="none" stroke="${text}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
}

const MIX = (color: string, pct: number) => `color-mix(in srgb, ${color} ${pct}%, transparent)`;

/**
 * Marker: shape + glyph + colour, an optional pulsing halo, and a text label chip under it.
 * `quietLabel` chips are hidden while the map is zoomed far out (the chip would overlap its neighbours).
 */
export function statusMarkerHtml(opts: { shape: MapShape; glyph: MapGlyph; tone: MapTone; label: string; pulseClass?: string; quietLabel?: boolean }): string {
  const { shape, glyph, tone, label, pulseClass = '', quietLabel = false } = opts;
  const { color, text } = TONE[tone];
  return `
    <div style="position:relative;width:36px;height:36px;display:flex;align-items:center;justify-content:center;cursor:pointer;">
      ${pulseClass ? `<div class="${pulseClass}" style="position:absolute;width:34px;height:34px;border-radius:50%;background:${color};opacity:0.3;"></div>` : ''}
      <div style="position:relative;z-index:10;display:flex;filter:drop-shadow(0 1px 2px color-mix(in srgb, var(--foreground) 35%, transparent));">${shapeSvg(shape, glyph, tone, 34)}</div>
      <span class="sl-marker-label${quietLabel ? ' sl-marker-label--quiet' : ''}" style="color:${text};border-color:${MIX(color, 55)};">${label}</span>
    </div>
  `;
}

/** Small pill, e.g. a status, with an optional shape icon in front so it is not colour-only. */
export function pillHtml(tone: MapTone, text: string, icon?: { shape: MapShape; glyph: MapGlyph }) {
  const { color, text: textColor } = TONE[tone];
  const svg = icon ? shapeSvg(icon.shape, icon.glyph, tone, 14) : '';
  return `<span style="flex-shrink:0;display:inline-flex;align-items:center;gap:5px;font-size:12px;font-weight:600;padding:3px 9px;border-radius:999px;color:${textColor};border:1px solid ${MIX(color, 45)};background:${MIX(color, 12)};">${svg}${text}</span>`;
}

/** Caption inside a popup. */
export function captionHtml(text: string, margin = '0') {
  return `<div style="font-size:12px;color:var(--muted);margin:${margin};">${text}</div>`;
}

/** Hairline-bordered stat cell: caption on top, value below. */
export function cellHtml(label: string, value: string, valueColor = 'var(--foreground)') {
  return `
    <div style="padding:8px 10px;border-radius:10px;border:1px solid var(--line);background:${MIX('var(--foreground)', 4)};">
      ${captionHtml(label, '0 0 4px 0')}
      <div style="font-size:15px;font-weight:600;color:${valueColor};">${value}</div>
    </div>
  `;
}

/** Popup title. */
export function titleHtml(text: string) {
  return `<h4 style="font-family:var(--font-display);font-weight:400;font-size:21px;line-height:1.25;margin:0;color:var(--foreground);overflow-wrap:anywhere;">${text}</h4>`;
}

/** Resolve a CSS custom property to a concrete colour, for SVG presentation attributes (Leaflet vector layers) where var() is not allowed. */
export function readToken(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback;
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

/**
 * Map chrome that Leaflet ships with fixed light styling: marker labels, zoom buttons (44px touch targets),
 * attribution, focus ring. Injected once; follows the theme through the CSS variables.
 */
const MAP_CSS = `
.sl-marker-label{position:absolute;top:100%;left:50%;transform:translateX(-50%);margin-top:1px;white-space:nowrap;font-size:11px;font-weight:600;line-height:1.35;padding:1px 7px;border-radius:999px;border:1px solid;background:var(--surface);pointer-events:none;}
.sl-zoomed-out .sl-marker-label--quiet{display:none;}
.leaflet-marker-icon:focus-visible{outline:2px solid var(--primary);outline-offset:3px;border-radius:8px;}
.leaflet-bar a,.leaflet-bar a:hover{width:44px !important;height:44px !important;line-height:44px !important;background:var(--surface) !important;color:var(--foreground) !important;border-color:var(--line-strong) !important;}
.leaflet-bar a:hover{background:var(--surface-raised) !important;}
.leaflet-bar a.leaflet-disabled{color:var(--muted) !important;opacity:.6;}
.leaflet-bar{border:1px solid var(--line-strong) !important;box-shadow:var(--shadow-card) !important;}
.leaflet-control-attribution{background:color-mix(in srgb, var(--surface) 88%, transparent) !important;color:var(--muted) !important;font-size:11px !important;padding:2px 6px !important;}
.leaflet-control-attribution a{color:var(--primary-text) !important;text-decoration:underline;}
.leaflet-popup-close-button{width:44px !important;height:44px !important;font-size:22px !important;line-height:44px !important;color:var(--muted) !important;}
`;

export function ensureMapStyles() {
  if (typeof document === 'undefined' || document.getElementById('sl-map-styles')) return;
  const style = document.createElement('style');
  style.id = 'sl-map-styles';
  style.textContent = MAP_CSS;
  document.head.appendChild(style);
}
