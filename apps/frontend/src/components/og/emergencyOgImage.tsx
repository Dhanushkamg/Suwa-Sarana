import { ImageResponse } from 'next/og';
import { siteHost } from './genericOgImage';
import { OG, OgCanvas, OgEyebrow, OgWordmark } from './ogParts';

interface EmergencyOgProps {
  size: { width: number; height: number };
  bloodType: string;
  hospitalName: string;
  district: string;
  urgency: string;
  unitsNeeded: number;
  /** Mono caption above the blood type, e.g. "Urgent donation needed". */
  eyebrow: string;
  /** Call-to-action text at the bottom of the card. */
  cta: string;
}

/** Shared emergency card for /share and /circle: ink background, giant bone blood type, ember accents. */
export function emergencyOgImage({ size, bloodType, hospitalName, district, urgency, unitsNeeded, eyebrow, cta }: EmergencyOgProps) {
  const isCritical = urgency === 'CRITICAL';
  const accent = isCritical ? OG.ember : OG.saffron;
  const unitsLabel = `${unitsNeeded} ${unitsNeeded === 1 ? 'unit' : 'units'}`;

  return new ImageResponse(
    (
      <OgCanvas>
        {/* Header */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '64px 72px 0 72px',
          }}
        >
          <OgWordmark size={42} />
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 14,
              padding: '12px 28px',
              borderRadius: 999,
              border: `2px solid ${accent}`,
              color: accent,
              fontSize: 24,
              fontWeight: 700,
              letterSpacing: 4,
              textTransform: 'uppercase',
            }}
          >
            <div style={{ display: 'flex', width: 12, height: 12, borderRadius: 999, backgroundColor: accent }} />
            {urgency}
          </div>
        </div>

        {/* Hero */}
        <div style={{ display: 'flex', flexDirection: 'column', padding: '56px 72px 0 72px', flex: 1 }}>
          <OgEyebrow color={OG.ember}>{eyebrow}</OgEyebrow>
          <div
            style={{
              display: 'flex',
              fontSize: 470,
              fontWeight: 700,
              lineHeight: 0.92,
              letterSpacing: -18,
              color: OG.bone,
              marginTop: 8,
            }}
          >
            {bloodType}
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 22, marginTop: 16 }}>
            <div style={{ display: 'flex', fontSize: 72, fontWeight: 700, color: OG.ember, letterSpacing: -2 }}>{unitsLabel}</div>
            <div style={{ display: 'flex', fontSize: 52, color: OG.bone, letterSpacing: -1 }}>needed urgently</div>
          </div>
        </div>

        {/* Where */}
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
            margin: '0 72px',
            padding: '28px 0',
            borderTop: `2px solid ${OG.line}`,
          }}
        >
          <div style={{ display: 'flex', fontSize: 48, fontWeight: 700, color: OG.bone, letterSpacing: -1 }}>{hospitalName}</div>
          {district ? <OgEyebrow size={24}>{`${district} District`}</OgEyebrow> : <div style={{ display: 'flex' }} />}
        </div>

        {/* Footer / CTA */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '30px 72px',
            backgroundColor: OG.ember,
            color: OG.ink,
          }}
        >
          <div style={{ display: 'flex', fontSize: 34, fontWeight: 700, letterSpacing: -0.5 }}>{cta}</div>
          <div style={{ display: 'flex', fontSize: 28, fontWeight: 700, letterSpacing: 1 }}>{siteHost()}</div>
        </div>
      </OgCanvas>
    ),
    { ...size }
  );
}
