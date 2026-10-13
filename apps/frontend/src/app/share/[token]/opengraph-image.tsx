import { API_BASE_URL } from '@/lib/constants';
import { genericOgImage } from '@/components/og/genericOgImage';
import { emergencyOgImage } from '@/components/og/emergencyOgImage';

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

  return emergencyOgImage({
    size,
    bloodType,
    hospitalName,
    district,
    urgency,
    unitsNeeded,
    eyebrow: 'Urgent donation needed',
    cta: 'Your donation can save a life today.',
  });
}
