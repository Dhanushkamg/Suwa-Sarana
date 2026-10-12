import { ImageResponse } from 'next/og';
import { API_BASE_URL } from '@/lib/constants';
import { genericOgImage, siteHost } from '@/components/og/genericOgImage';

export const alt = 'Suwa Sarana Emergency Blood Requisition Social Card';
export const size = {
  width: 1200,
  height: 1200,
};
export const contentType = 'image/png';

export default async function Image({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;

  let bloodType = '';
  let hospitalName = '';
  let district = '';
  let urgency = 'URGENT';
  let unitsNeeded = 0;

  try {
    const res = await fetch(`${API_BASE_URL}/circle/${token}`, { cache: 'no-store' });
    if (res.ok) {
      const json = await res.json();
      const d = json?.data;
      if (d?.patientBloodType && d?.hospitalName && d?.unitsNeeded) {
        bloodType = String(d.patientBloodType);
        hospitalName = String(d.hospitalName);
        district = String(d.district ?? '');
        urgency = String(d.urgency || urgency);
        unitsNeeded = Number(d.unitsNeeded);
      }
    }
  } catch {
    // handled below: no data -> generic card
  }

  // Never show invented emergency details: without real data, render a generic branded card
  if (!bloodType || !hospitalName || !unitsNeeded) {
    return genericOgImage(size);
  }

  const isCritical = urgency === 'CRITICAL';

  return new ImageResponse(
    (
      <div
        style={{
          height: '100%',
          width: '100%',
          display: 'flex',
          flexDirection: 'column',
          backgroundColor: '#0a0005',
          color: '#ffffff',
          fontFamily: 'sans-serif',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* Background: top-right glow */}
        <div
          style={{
            position: 'absolute',
            top: '-180px',
            right: '-180px',
            width: '700px',
            height: '700px',
            borderRadius: '50%',
            backgroundColor: 'rgba(220, 38, 38, 0.25)',
            filter: 'blur(120px)',
          }}
        />
        {/* Background: bottom-left glow */}
        <div
          style={{
            position: 'absolute',
            bottom: '-180px',
            left: '-180px',
            width: '600px',
            height: '600px',
            borderRadius: '50%',
            backgroundColor: 'rgba(185, 28, 28, 0.20)',
            filter: 'blur(100px)',
          }}
        />
        {/* Subtle diagonal red stripe accent */}
        <div
          style={{
            position: 'absolute',
            top: '0',
            left: '0',
            width: '12px',
            height: '100%',
            background: 'linear-gradient(180deg, #dc2626, #991b1b)',
          }}
        />

        {/* HEADER */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '48px 64px 32px 80px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            <div
              style={{
                width: '64px',
                height: '64px',
                borderRadius: '18px',
                background: 'linear-gradient(135deg, #ef4444, #b91c1c)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '32px',
                boxShadow: '0 8px 32px rgba(239,68,68,0.5)',
              }}
            >
              🩸
            </div>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <div style={{ display: 'flex', fontSize: '36px', fontWeight: '900', letterSpacing: '-1px', color: '#fff' }}>
                Suwa Sarana
              </div>
              <div style={{ fontSize: '18px', color: '#9ca3af', fontWeight: '500' }}>
                Sri Lanka Emergency Blood Network
              </div>
            </div>
          </div>
          {/* Urgency Badge */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              padding: '12px 28px',
              borderRadius: '999px',
              backgroundColor: isCritical ? 'rgba(220, 38, 38, 0.25)' : 'rgba(245, 158, 11, 0.25)',
              border: `2px solid ${isCritical ? 'rgba(220, 38, 38, 0.6)' : 'rgba(245, 158, 11, 0.6)'}`,
              color: isCritical ? '#f87171' : '#fbbf24',
              fontSize: '22px',
              fontWeight: '900',
              letterSpacing: '1px',
            }}
          >
            🚨 {urgency} EMERGENCY
          </div>
        </div>

        {/* HERO SECTION */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            padding: '20px 80px',
            gap: '60px',
            flex: 1,
          }}
        >
          {/* Giant Blood Drop + Type */}
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: '0',
              flexShrink: 0,
            }}
          >
            <div
              style={{
                width: '260px',
                height: '260px',
                borderRadius: '50% 50% 50% 50% / 40% 40% 60% 60%',
                background: 'linear-gradient(160deg, #ef4444 0%, #991b1b 60%, #7f1d1d 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '100px',
                fontWeight: '900',
                color: '#ffffff',
                boxShadow: '0 0 80px rgba(220,38,38,0.8), 0 0 160px rgba(220,38,38,0.3)',
                border: '3px solid rgba(239,68,68,0.6)',
              }}
            >
              {bloodType}
            </div>
          </div>

          {/* Text content */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1 }}>
            <div style={{ display: 'flex', fontSize: '30px', color: '#fca5a5', fontWeight: '600', letterSpacing: '2px', textTransform: 'uppercase' }}>
              Urgent Donation Needed
            </div>
            <div style={{ fontSize: '80px', fontWeight: '900', color: '#ffffff', lineHeight: 1, letterSpacing: '-2px' }}>
              {`${unitsNeeded} UNIT${unitsNeeded > 1 ? 'S' : ''} OF`}
            </div>
            <div style={{ fontSize: '80px', fontWeight: '900', color: '#ef4444', lineHeight: 1, letterSpacing: '-2px' }}>
              {`${bloodType} BLOOD`}
            </div>
            {/* Red brush stroke "URGENTLY" */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  fontSize: '60px',
                  fontWeight: '900',
                  color: '#fff',
                  backgroundColor: '#dc2626',
                  padding: '4px 28px 4px 12px',
                  borderRadius: '8px',
                  letterSpacing: '1px',
                  boxShadow: '4px 4px 0px #7f1d1d',
                }}
              >
                URGENTLY
              </div>
            </div>
          </div>
        </div>

        {/* HOSPITAL INFO */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            padding: '0 80px 24px 80px',
            gap: '16px',
          }}
        >
          <div style={{ fontSize: '32px' }}>🏥</div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <div style={{ fontSize: '32px', fontWeight: '800', color: '#fff' }}>{hospitalName}</div>
            <div style={{ fontSize: '20px', color: '#9ca3af' }}>{district ? `${district} District` : ''}</div>
          </div>
        </div>

        {/* PILLS ROW */}
        <div
          style={{
            display: 'flex',
            gap: '16px',
            padding: '0 80px 36px 80px',
          }}
        >
          {[
            { icon: '⏰', label: urgency, sub: 'Immediate Need' },
            { icon: '👥', label: `${unitsNeeded} UNITS`, sub: 'Required' },
            { icon: '🩸', label: bloodType, sub: 'Blood Type' },
          ].map((pill) => (
            <div
              key={pill.label}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                padding: '12px 24px',
                borderRadius: '999px',
                backgroundColor: 'rgba(220, 38, 38, 0.15)',
                border: '1.5px solid rgba(220, 38, 38, 0.4)',
              }}
            >
              <span style={{ fontSize: '24px' }}>{pill.icon}</span>
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                <span style={{ fontSize: '18px', fontWeight: '800', color: '#fff' }}>{pill.label}</span>
                <span style={{ fontSize: '14px', color: '#f87171' }}>{pill.sub}</span>
              </div>
            </div>
          ))}
        </div>

        {/* CTA */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '24px 80px',
            backgroundColor: 'rgba(220, 38, 38, 0.08)',
            borderTop: '1px solid rgba(220, 38, 38, 0.2)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            <div style={{ fontSize: '40px' }}>👥</div>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <div style={{ display: 'flex', fontSize: '26px', color: '#e5e7eb' }}>
                Your donation can save a life today.
              </div>
              <div style={{ fontSize: '30px', fontWeight: '900', color: '#ef4444' }}>
                Volunteer now →
              </div>
            </div>
          </div>
        </div>

        {/* FOOTER BANNER */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '20px 80px',
            backgroundColor: '#dc2626',
          }}
        >
          <div style={{ fontSize: '24px', fontWeight: '700', color: '#fff' }}>
            සුව සරණ · சுவ சரண · Suwa Sarana · Sri Lanka
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{ fontSize: '22px' }}>🌐</div>
            <div style={{ fontSize: '26px', fontWeight: '900', color: '#fff' }}>{siteHost()}</div>
          </div>
        </div>
      </div>
    ),
    {
      ...size,
    }
  );
}
