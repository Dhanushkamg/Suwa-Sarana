/**
 * Colours of the downloadable camp poster. The poster is a fixed piece of artwork that is exported as an image and
 * shared on social media, so it must look the same whichever theme the organiser is viewing the app in.
 * That is why these are literals and not CSS variables (same exception as the Open Graph images).
 * Values follow the light brand palette: background #F8FAFC, text #1E293B, primary #2563EB, brand crimson #B91C1C.
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
} as const;
