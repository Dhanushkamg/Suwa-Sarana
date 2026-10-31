/**
 * Colours of the downloadable posters. A poster is a fixed piece of artwork that is exported as an image and
 * shared on social media, so it must look the same whichever theme the organiser is viewing the app in.
 * That is why these are literals and not CSS variables (same exception as the Open Graph images).
 * Values follow the light brand palette: background #F8FAFC, text #1E293B, primary #2563EB, brand crimson #B91C1C.
 *
 * Contrast (WCAG, checked in poster/__tests__/palette.test.ts): text on background/surface >= 7:1, muted >= 4.5:1,
 * white on brand >= 4.5:1, onInk text on ink >= 7:1. Crimson is used for the blood type, one tile and the eyebrow chip only.
 */
export const POSTER = {
  background: '#F8FAFC',
  surface: '#FFFFFF',
  text: '#1E293B',
  muted: '#475569',
  line: '#E2E8F0',
  primary: '#2563EB',
  brand: '#B91C1C',
  brandSoft: '#FEE2E2',
  onBrand: '#FFFFFF',
  /** Dark neutral for the calm tiles (camp date, thank-you) and the QR code */
  ink: '#0F172A',
  onInk: '#F8FAFC',
  onInkMuted: '#CBD5E1',
  /** "Unverified" mark */
  warningSoft: '#FEF3C7',
  warningText: '#92400E',
} as const;
