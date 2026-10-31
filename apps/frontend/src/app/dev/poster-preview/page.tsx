import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import PosterPreviewClient from '@/components/poster/PosterPreviewClient';
import { isPosterSizeId } from '@/components/poster/sizes';
import { POSTER_LANGUAGES, POSTER_TEMPLATES, type PosterLanguage, type PosterTemplate } from '@/components/poster/types';

export const metadata: Metadata = {
  title: 'Poster preview (development only)',
  robots: { index: false, follow: false },
};

type Query = Record<string, string | string[] | undefined>;
const first = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);

/**
 * Development / visual-regression route: one poster, 1:1, from fixture data. Only exists when
 * NEXT_PUBLIC_ENABLE_DEV_ROUTES=true, otherwise it is a 404.
 *
 *   /dev/poster-preview?template=URGENT_REQUEST|CAMP|AWARENESS|THANK_YOU
 *                      &size=1080x1080|1080x1350|1200x630|1080x1920
 *                      &lang=en|si|ta
 *                      &unverified=1   (adds the "unverified" mark)
 *                      &long=1         (very long hospital / camp names)
 */
export default async function PosterPreviewPage({ searchParams }: { searchParams: Promise<Query> }) {
  if (process.env.NEXT_PUBLIC_ENABLE_DEV_ROUTES !== 'true') notFound();

  const q = await searchParams;
  const template = POSTER_TEMPLATES.find((t) => t === first(q.template)) ?? ('URGENT_REQUEST' as PosterTemplate);
  const language = POSTER_LANGUAGES.find((l) => l === first(q.lang)) ?? ('en' as PosterLanguage);
  const sizeParam = first(q.size);

  return (
    <PosterPreviewClient
      template={template}
      language={language}
      sizeId={isPosterSizeId(sizeParam) ? sizeParam : '1080x1080'}
      unverified={first(q.unverified) === '1'}
      long={first(q.long) === '1'}
    />
  );
}
