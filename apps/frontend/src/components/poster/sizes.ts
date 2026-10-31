import type { PosterSize, PosterSizeId } from './types';

/** The four social-media export sizes (proposal 9.2). */
export const POSTER_SIZES: PosterSize[] = [
  { id: '1080x1080', width: 1080, height: 1080, kind: 'square' },
  { id: '1080x1350', width: 1080, height: 1350, kind: 'portrait' },
  { id: '1200x630', width: 1200, height: 630, kind: 'landscape' },
  { id: '1080x1920', width: 1080, height: 1920, kind: 'story' },
];

export function posterSize(id: string | null | undefined): PosterSize {
  return POSTER_SIZES.find((s) => s.id === id) ?? POSTER_SIZES[0];
}

export const isPosterSizeId = (v: unknown): v is PosterSizeId => POSTER_SIZES.some((s) => s.id === v);
