import type { Metadata } from 'next';
import CircleResponseClient from './CircleResponseClient';
import { fetchCircleInfo } from '@/components/pub/circleData';
import { serverTranslator } from '@/components/pub/pageMeta';

interface PageProps {
  params: Promise<{ token: string }>;
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { token } = await params;
  const [data, { t, locale }] = await Promise.all([fetchCircleInfo(token), serverTranslator()]);

  if (!data) {
    return { title: t('pub.meta.circleFallbackTitle'), description: t('pub.meta.circleFallbackDesc') };
  }

  const urgent = data.urgency === 'CRITICAL' || data.urgency === 'URGENT';
  const title = t(urgent ? 'pub.meta.shareTitleUrgent' : 'pub.meta.shareTitleRoutine', { bloodType: data.patientBloodType, hospital: data.hospitalName });
  const description = t('pub.meta.circleDesc', { units: data.unitsNeeded, bloodType: data.patientBloodType, district: data.district });

  return {
    title,
    description,
    openGraph: { title, description, type: 'website', siteName: 'Suwa Sarana', locale: `${locale}_LK` },
    twitter: { card: 'summary_large_image', title, description },
  };
}

export default async function CirclePage({ params }: PageProps) {
  const { token } = await params;
  const initialData = await fetchCircleInfo(token);

  return <CircleResponseClient token={token} initialData={initialData} />;
}
