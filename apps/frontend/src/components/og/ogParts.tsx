/**
 * Building blocks for the Open Graph / share images (rendered by next/og, so: inline styles only,
 * every multi-child element is display:flex, and CSS variables are unavailable).
 * Palette literals mirror the Ink & Ember tokens: ink #0b0908, bone #f4eee4, ember #ff5a3c, saffron #f2b84b.
 */
export const OG = {
  ink: '#0b0908',
  surface: '#131010',
  bone: '#f4eee4',
  muted: '#a39a8e',
  ember: '#ff5a3c',
  saffron: '#f2b84b',
  line: 'rgba(244, 238, 228, 0.14)',
  dot: 'rgba(244, 238, 228, 0.085)',
};

/** The Suwa Sarana drop with its heartbeat notch (same geometry as components/brand/Logo). */
export function OgLogoMark({ size = 64 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32">
      <path d="M16 2.5c-3.3 4.6-9 10.2-9 16.1a9 9 0 0 0 18 0C25 12.7 19.3 7.1 16 2.5Z" fill={OG.ember} />
      <path
        d="M8.5 19.5h4.2l1.8-4.2 2.8 8 2-4h4.2"
        fill="none"
        stroke={OG.ink}
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
      <div style={{ display: 'flex', fontSize: size, fontWeight: 700, letterSpacing: -1, color: OG.bone }}>
        <span>Suwa&nbsp;</span>
        <span style={{ color: 'rgba(244, 238, 228, 0.6)' }}>Sarana</span>
      </div>
    </div>
  );
}

/** Full-bleed ink canvas with a faint dot grid. */
export function OgCanvas({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        height: '100%',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        position: 'relative',
        backgroundColor: OG.ink,
        backgroundImage: `radial-gradient(circle at 1px 1px, ${OG.dot} 1.5px, transparent 0)`,
        backgroundSize: '36px 36px',
        color: OG.bone,
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
