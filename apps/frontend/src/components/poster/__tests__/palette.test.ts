import { describe, expect, it } from 'vitest';
import { POSTER } from '@/components/camps/posterPalette';

function luminance(hex: string) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
const contrast = (a: string, b: string) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

// Every text/background pair the poster artwork uses. Body text must reach WCAG AA (4.5:1).
const TEXT_PAIRS: [string, string, string][] = [
  ['headline on background', POSTER.text, POSTER.background],
  ['body (muted) on background', POSTER.muted, POSTER.background],
  ['fact text on background', POSTER.text, POSTER.background],
  ['white on brand tile', POSTER.onBrand, POSTER.brand],
  ['onInk on ink tile', POSTER.onInk, POSTER.ink],
  ['muted onInk on ink tile', POSTER.onInkMuted, POSTER.ink],
  ['brand on brandSoft pill', POSTER.brand, POSTER.brandSoft],
  ['brand on background (eyebrow, blood-type pills)', POSTER.brand, POSTER.background],
  ['unverified badge', POSTER.warningText, POSTER.warningSoft],
  ['QR code on white', POSTER.ink, POSTER.surface],
];

describe('poster palette', () => {
  it.each(TEXT_PAIRS)('%s has contrast >= 4.5:1', (_name, fg, bg) => {
    expect(contrast(fg, bg)).toBeGreaterThanOrEqual(4.5);
  });

  it('keeps the icon colour readable as a graphic (>= 3:1)', () => {
    expect(contrast(POSTER.primary, POSTER.background)).toBeGreaterThanOrEqual(3);
  });
});
