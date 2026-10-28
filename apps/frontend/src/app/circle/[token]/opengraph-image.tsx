import { fetchCircleInfo } from '@/components/pub/circleData';
import { genericOgImage } from '@/components/og/genericOgImage';
import { emergencyOgImage } from '@/components/og/emergencyOgImage';

export const alt = 'Suwa Sarana blood request alert';
export const size = {
  width: 1200,
  height: 1200,
};
export const contentType = 'image/png';

export default async function Image({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;

  // Server-side fetch against the internal API URL when configured (API_INTERNAL_URL)
  const d = await fetchCircleInfo(token, { cache: 'no-store' });

  // Never show invented emergency details: without real data, render a generic branded card
  if (!d?.patientBloodType || !d?.hospitalName || !d?.unitsNeeded) {
    return genericOgImage(size);
  }

  return emergencyOgImage({
    size,
    bloodType: String(d.patientBloodType),
    hospitalName: String(d.hospitalName),
    district: String(d.district ?? ''),
    urgency: String(d.urgency || 'URGENT'),
    unitsNeeded: Number(d.unitsNeeded),
    eyebrow: 'Private volunteer request',
    cta: 'Your donation can save a life today.',
  });
}
