import type { Locale } from '@/lib/i18n';

export type PosterTemplate = 'URGENT_REQUEST' | 'CAMP' | 'AWARENESS' | 'THANK_YOU';
export const POSTER_TEMPLATES: PosterTemplate[] = ['URGENT_REQUEST', 'CAMP', 'AWARENESS', 'THANK_YOU'];

export type PosterLanguage = Locale;
export const POSTER_LANGUAGES: PosterLanguage[] = ['en', 'si', 'ta'];

export type PosterSizeId = '1080x1080' | '1080x1350' | '1200x630' | '1080x1920';

export interface PosterSize {
  id: PosterSizeId;
  width: number;
  height: number;
  /** Where the size is used, for the picker label key `poster.size.<id>` */
  kind: 'square' | 'portrait' | 'landscape' | 'story';
}

/**
 * Facts that come from the system (the request, the camp, the signed-in account). The model never produces these, and
 * there is deliberately NO field for a patient's name, photo or free-text details.
 */
export interface PosterFacts {
  bloodType?: string;
  /** Camps: every blood type being asked for */
  bloodTypes?: string[];
  units?: number;
  hospital?: string;
  district?: string;
  /** ISO date or date-time. Sri Lanka wall-clock time when no zone is given. */
  date?: string;
  /** "09:00" or "09:00:00" */
  startTime?: string;
  endTime?: string;
  venue?: string;
  contactPhone?: string;
  campName?: string;
  organiser?: string;
}

/** The words on the poster and in the caption. Always short, always editable before export. */
export interface PosterCopy {
  headline: string;
  body: string;
  caption: string;
  hashtags: string[];
}

/** How long each editable field may be. The poster layout is designed for these lengths. */
export const COPY_LIMITS = { headline: 80, body: 240, caption: 600, hashtags: 8 } as const;
