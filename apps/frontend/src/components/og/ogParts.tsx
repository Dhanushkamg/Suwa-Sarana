/**
 * Building blocks for the Open Graph / share images (rendered by next/og, so: inline styles only,
 * every multi-child element is display:flex, and CSS variables are unavailable).
 * These images are shared outside the app, so they use the fixed LIGHT brand palette as literals
 * (the one place hex values are allowed): background #F8FAFC, text #1E293B, primary #2563EB, brand #B91C1C.
 * Text is English only: the default next/og font has no Sinhala/Tamil glyphs.
 */
export const OG = {
  background: '#F8FAFC',
  surface: '#FFFFFF',
  text: '#1E293B',
  muted: '#475569',
  primary: '#2563EB',
  brand: '#B91C1C',
  onBrand: '#FFFFFF',
  warning: '#B45309',
  line: '#CBD5E1',
  dot: '#E2E8F0',
};

/** The Suwa Sarana drop with its heartbeat notch (same geometry as components/brand/Logo). */
export function OgLogoMark({ size = 64 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32">
      <path d="M16 2.5c-3.3 4.6-9 10.2-9 16.1a9 9 0 0 0 18 0C25 12.7 19.3 7.1 16 2.5Z" fill={OG.brand} />
      <path
        d="M8.5 19.5h4.2l1.8-4.2 2.8 8 2-4h4.2"
        fill="none"
        stroke={OG.background}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** Wordmark: logo mark + "Suwa Sarana" (second word dimmed, echoing the italic on the site). */
export function OgWordmark({ size = 44 }: { size?: number }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 18 }}>
      <OgLogoMark size={Math.round(size * 1.3)} />
      <div style={{ display: 'flex', fontSize: size, fontWeight: 700, letterSpacing: -1, color: OG.text }}>
        <span>Suwa&nbsp;</span>
        <span style={{ color: OG.muted }}>Sarana</span>
      </div>
    </div>
  );
}

/** Full-bleed light canvas with a faint dot grid. */
export function OgCanvas({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        height: '100%',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        position: 'relative',
        backgroundColor: OG.background,
        backgroundImage: `radial-gradient(circle at 1px 1px, ${OG.dot} 1.5px, transparent 0)`,
        backgroundSize: '36px 36px',
        color: OG.text,
        fontFamily: 'sans-serif',
      }}
    >
      {children}
    </div>
  );
}

/** Small uppercase caption with generous tracking. */
export function OgEyebrow({ children, color = OG.muted, size = 24 }: { children: React.ReactNode; color?: string; size?: number }) {
  return (
    <div style={{ display: 'flex', fontSize: size, letterSpacing: 6, textTransform: 'uppercase', color, fontWeight: 600 }}>{children}</div>
  );
}
