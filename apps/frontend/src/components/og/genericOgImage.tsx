import { ImageResponse } from 'next/og';
import { OG, OgCanvas, OgEyebrow, OgLogoMark } from './ogParts';

export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || 'https://suwasarana.lk';

/** Host part of the site URL for display (e.g. "suwasarana.lk"). */
export function siteHost(): string {
  try {
    return new URL(SITE_URL).host;
  } catch {
    return 'suwasarana.lk';
  }
}

/**
 * Generic branded card used when the request data cannot be loaded.
 * It deliberately contains NO emergency details (blood type, hospital, units, urgency).
 */
export function genericOgImage(size: { width: number; height: number }) {
  return new ImageResponse(
    (
      <OgCanvas>
        <div
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 36,
            padding: 80,
          }}
        >
          <OgLogoMark size={176} />
          <div style={{ display: 'flex', fontSize: 120, fontWeight: 700, letterSpacing: -4, color: OG.bone }}>
            <span>Suwa&nbsp;</span>
            <span style={{ color: 'rgba(244, 238, 228, 0.6)' }}>Sarana</span>
          </div>
          <div style={{ display: 'flex', width: 120, height: 3, backgroundColor: OG.ember }} />
          <OgEyebrow size={30}>Blood donation platform</OgEyebrow>
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '36px 72px',
            borderTop: `2px solid ${OG.line}`,
          }}
        >
          <OgEyebrow size={22}>Sri Lanka</OgEyebrow>
          <div style={{ display: 'flex', fontSize: 30, fontWeight: 700, color: OG.ember, letterSpacing: 1 }}>{siteHost()}</div>
        </div>
      </OgCanvas>
    ),
    { ...size }
  );
}
