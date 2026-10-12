import type { MetadataRoute } from 'next';
import { SITE_URL } from '@/lib/constants';

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: '*',
      allow: '/',
      // Signed-in screens are not for search engines (share/circle links stay crawlable so social previews work)
      disallow: ['/dashboard/'],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
