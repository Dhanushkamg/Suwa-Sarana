/**
 * Shared inline-style snippets for Leaflet markers and popups (HTML strings, so Tailwind classes do not apply).
 * Colours are Ink & Ember tokens via CSS variables; the popup shell itself is styled in globals.css.
 * Every text argument must already be HTML-escaped (use escapeHtml) by the caller.
 */

export type MapTone = 'ember' | 'saffron' | 'sage' | 'bone' | 'muted';

export const TONE_VAR: Record<MapTone, string> = {
  ember: 'var(--ember)',
  saffron: 'var(--saffron)',
  sage: 'var(--sage)',
  bone: 'var(--foreground)',
  muted: 'var(--muted)',
};

/** Resolved colour for SVG presentation attributes (Leaflet vector layers), where var() is not allowed. */
export function resolvedTone(tone: MapTone): string {
  const fallback: Record<MapTone, string> = {
    ember: 'oklch(0.66 0.205 31)',
    saffron: 'oklch(0.8 0.15 80)',
    sage: 'oklch(0.72 0.095 165)',
    bone: 'oklch(0.95 0.01 80)',
    muted: 'oklch(0.7 0.02 70)',
  };
  if (typeof window === 'undefined') return fallback[tone];
  const name = { ember: '--ember', saffron: '--saffron', sage: '--sage', bone: '--foreground', muted: '--muted' }[tone];
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback[tone];
}

const MIX = (color: string, pct: number) => `color-mix(in oklch, ${color} ${pct}%, transparent)`;

/** Round ink marker with a coloured ring and a mono label, optionally with a soft pulsing halo. */
export function markerHtml(tone: MapTone, inner: string, pulseClass = '') {
  const color = TONE_VAR[tone];
  return `
    <div style="position:relative;width:36px;height:36px;display:flex;align-items:center;justify-content:center;cursor:pointer;">
      ${pulseClass ? `<div class="${pulseClass}" style="position:absolute;width:36px;height:36px;border-radius:50%;background:${color};opacity:0.35;"></div>` : ''}
      <div style="position:relative;z-index:10;width:26px;height:26px;border-radius:50%;background:var(--background);border:2px solid ${color};display:flex;align-items:center;justify-content:center;font-family:var(--font-mono);font-size:11px;font-weight:600;color:var(--foreground);">
        ${inner}
      </div>
    </div>
  `;
}

/** Small uppercase pill, e.g. a status. */
export function pillHtml(tone: MapTone, text: string) {
  const color = TONE_VAR[tone];
  return `<span style="flex-shrink:0;font-family:var(--font-mono);font-size:9px;font-weight:500;letter-spacing:0.12em;text-transform:uppercase;padding:3px 8px;border-radius:999px;color:${color};border:1px solid ${MIX(color, 40)};background:${MIX(color, 12)};">${text}</span>`;
}

/** Mono caption inside a popup. */
export function captionHtml(text: string, margin = '0') {
  return `<div style="font-family:var(--font-mono);font-size:10px;letter-spacing:0.12em;text-transform:uppercase;color:var(--muted);margin:${margin};">${text}</div>`;
}

/** Hairline-bordered stat cell: caption on top, value below. */
export function cellHtml(label: string, value: string, valueColor = 'var(--foreground)') {
  return `
    <div style="padding:8px 10px;border-radius:10px;border:1px solid var(--line);background:${MIX('var(--foreground)', 4)};">
      ${captionHtml(label, '0 0 4px 0')}
      <div style="font-size:14px;font-weight:600;color:${valueColor};">${value}</div>
    </div>
  `;
}

/** Serif popup title. */
export function titleHtml(text: string) {
  return `<h4 style="font-family:var(--font-display);font-weight:400;font-size:21px;line-height:1.15;letter-spacing:-0.01em;margin:0;color:var(--foreground);overflow-wrap:anywhere;">${text}</h4>`;
}
