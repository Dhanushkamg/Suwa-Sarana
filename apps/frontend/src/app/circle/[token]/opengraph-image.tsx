import { ImageResponse } from 'next/og';
import { API_BASE_URL } from '@/lib/constants';

export const alt = 'Suwa Sarana Emergency Blood Requisition Alert';
export const size = {
  width: 1200,
  height: 630,
};
export const contentType = 'image/png';

export default async function Image({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;

  let bloodType = 'O+';
  let hospitalName = 'National Hospital of Sri Lanka';
  let district = 'Colombo';
  let urgency = 'CRITICAL';
  let unitsNeeded = 1;

  try {
    const res = await fetch(`${API_BASE_URL}/circle/${token}`);
    if (res.ok) {
      const json = await res.json();
      if (json.data) {
        bloodType = json.data.patientBloodType || bloodType;
        hospitalName = json.data.hospitalName || hospitalName;
        district = json.data.district || district;
        urgency = json.data.urgency || urgency;
        unitsNeeded = json.data.unitsNeeded || unitsNeeded;
      }
    }
  } catch {
    // Fallback defaults
  }

  return new ImageResponse(
    (
      <div
        style={{
          height: '100%',
          width: '100%',
          display: 'flex',
          flexDirection: 'column',
          backgroundColor: '#0a0a0f',
          color: '#ffffff',
          fontFamily: 'sans-serif',
          padding: '48px 64px',
          justifyContent: 'space-between',
          position: 'relative',
        }}
      >
        {/* Background ambient red glow */}
        <div
          style={{
            position: 'absolute',
            top: '-100px',
            right: '-100px',
            width: '500px',
            height: '500px',
            borderRadius: '50%',
            backgroundColor: 'rgba(239, 68, 68, 0.15)',
            filter: 'blur(90px)',
          }}
        />

        {/* Top bar */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div
              style={{
                width: '48px',
                height: '48px',
                borderRadius: '14px',
                background: 'linear-gradient(135deg, #ef4444, #e11d48)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '24px',
                fontWeight: 'bold',
              }}
            >
              🩸
            </div>
            <div style={{ fontSize: '28px', fontWeight: '900', letterSpacing: '-0.5px' }}>
              Suwa Sarana
            </div>
          </div>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              padding: '8px 20px',
              borderRadius: '999px',
              backgroundColor: urgency === 'CRITICAL' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(245, 158, 11, 0.2)',
              border: `1px solid ${urgency === 'CRITICAL' ? 'rgba(239, 68, 68, 0.4)' : 'rgba(245, 158, 11, 0.4)'}`,
              color: urgency === 'CRITICAL' ? '#f87171' : '#fbbf24',
              fontSize: '18px',
              fontWeight: 'bold',
              textTransform: 'uppercase',
            }}
          >
            🚨 {urgency} BLOOD REQUEST
          </div>
        </div>

        {/* Center Hero */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            backgroundColor: 'rgba(255, 255, 255, 0.03)',
            borderRadius: '32px',
            padding: '36px 44px',
            border: '1px solid rgba(255, 255, 255, 0.08)',
            gap: '36px',
          }}
        >
          {/* Blood Type Badge */}
          <div
            style={{
              width: '150px',
              height: '150px',
              borderRadius: '28px',
              background: 'linear-gradient(135deg, #dc2626, #991b1b)',
              border: '2px solid rgba(239, 68, 68, 0.5)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '72px',
              fontWeight: '900',
              color: '#ffffff',
              boxShadow: '0 20px 40px rgba(220, 38, 38, 0.4)',
            }}
          >
            {bloodType}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', flex: 1 }}>
            <div style={{ fontSize: '20px', color: '#9ca3af', fontWeight: '500' }}>
              Urgent Hospital Blood Requisition
            </div>
            <div style={{ fontSize: '40px', fontWeight: '800', color: '#ffffff', lineHeight: 1.1 }}>
              {hospitalName}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '24px', marginTop: '8px' }}>
              <div style={{ fontSize: '20px', color: '#f87171', display: 'flex', alignItems: 'center', gap: '6px' }}>
                📍 {district} District
              </div>
              <div style={{ fontSize: '20px', color: '#60a5fa', display: 'flex', alignItems: 'center', gap: '6px' }}>
                💉 {unitsNeeded} Unit(s) Needed
              </div>
            </div>
          </div>
        </div>

        {/* Bottom Banner */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderTop: '1px solid rgba(255, 255, 255, 0.08)',
            paddingTop: '20px',
          }}
        >
          <div style={{ fontSize: '18px', color: '#9ca3af' }}>
            Click this link to volunteer as a donor directly or share with your circle.
          </div>
          <div style={{ fontSize: '16px', color: '#ef4444', fontWeight: 'bold' }}>
            suwasarana.lk/circle
          </div>
        </div>
      </div>
    ),
    {
      ...size,
    }
  );
}
