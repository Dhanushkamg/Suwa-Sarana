import { ImageResponse } from 'next/og';

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
      <div
        style={{
          height: '100%',
          width: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '24px',
          backgroundColor: '#0a0005',
          color: '#ffffff',
          fontFamily: 'sans-serif',
        }}
      >
        <div
          style={{
            width: '120px',
            height: '120px',
            borderRadius: '32px',
            background: 'linear-gradient(135deg, #ef4444, #b91c1c)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '64px',
          }}
        >
          🩸
        </div>
        <div style={{ display: 'flex', fontSize: '72px', fontWeight: 900 }}>Suwa Sarana</div>
        <div style={{ display: 'flex', fontSize: '32px', color: '#9ca3af' }}>
          Blood donation platform
        </div>
        <div style={{ display: 'flex', fontSize: '24px', color: '#ef4444', fontWeight: 700 }}>
          {siteHost()}
        </div>
      </div>
    ),
    { ...size }
  );
}
