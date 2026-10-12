import type { MetadataRoute } from 'next';
import { SITE_URL } from '@/lib/constants';

const PAGES = ['', '/camps', '/contact', '/login', '/register', '/privacy', '/terms'];

export default function sitemap(): MetadataRoute.Sitemap {
  return PAGES.map((path) => ({
    url: `${SITE_URL}${path}`,
    changeFrequency: path === '' || path === '/camps' ? 'daily' : 'monthly',
    priority: path === '' ? 1 : 0.6,
  }));
}
