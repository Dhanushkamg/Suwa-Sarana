import { Metadata } from 'next';
import ShareCardClient from './ShareCardClient';
import { API_BASE_URL } from '@/lib/constants';

interface PageProps {
  params: Promise<{ token: string }>;
}

async function getCircleData(token: string) {
  try {
    const res = await fetch(`${API_BASE_URL}/circle/${token}`, {
      next: { revalidate: 30 },
    });
    if (!res.ok) return null;
    const json = await res.json();
    return json.data || null;
  } catch {
    return null;
  }
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { token } = await params;
  const data = await getCircleData(token);

  if (!data) {
    return {
      title: 'Emergency Blood Card | Suwa Sarana',
      description: 'Urgent blood requisition card in Sri Lanka. Click to view details and volunteer.',
    };
  }

  const title = `🚨 URGENT: ${data.patientBloodType} Blood Needed at ${data.hospitalName}`;
  const description = `Emergency requisition: ${data.unitsNeeded} units of ${data.patientBloodType} blood needed in ${data.district}. Click to volunteer or share with your network.`;

  return {
    title: `${title} | Suwa Sarana`,
    description,
    openGraph: {
      title,
      description,
      type: 'website',
      siteName: 'Suwa Sarana Emergency Blood Requisition Network',
      locale: 'en_LK',
    },
    twitter: {
      card: 'summary_large_image',
      title,
      description,
    },
  };
}

export default async function SharePage({ params }: PageProps) {
  const { token } = await params;
  const initialData = await getCircleData(token);

  return <ShareCardClient token={token} initialData={initialData} />;
}
