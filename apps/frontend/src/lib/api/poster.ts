import apiClient from '@/lib/apiClient';
import type { PosterFacts, PosterLanguage, PosterTemplate } from '@/components/poster/types';
import { unwrap } from './common';

/** AI poster-copy endpoint. Kept in one place so the path can change without touching the UI. */
export const POSTER_PATHS = {
  copy: '/ai/poster-copy',
} as const;

/** Error code the backend sends (HTTP 422) when an URGENT_REQUEST poster is asked for by a requester who is not VERIFIED. */
export const UNVERIFIED_REQUESTER_CODE = 'poster.unverified_requester';

export type PosterCopyErrorKind = 'unverified' | 'rate_limit' | 'unavailable';

export class PosterCopyError extends Error {
  readonly kind: PosterCopyErrorKind;
  constructor(kind: PosterCopyErrorKind) {
    super(`Poster copy request failed: ${kind}`);
    this.name = 'PosterCopyError';
    this.kind = kind;
  }
}

export interface PosterCopyResponse {
  headline: unknown;
  body: unknown;
  caption: unknown;
  hashtags: unknown;
  usedLlm: boolean;
}

const hhmm = (v?: string) => (v ? /^(\d{1,2}):(\d{2})/.exec(v)?.slice(1).map((p) => p.padStart(2, '0')).join(':') : undefined);

/** Facts in the shape of the contract: all optional strings/numbers, empty ones left out. */
export function toBackendFacts(facts: PosterFacts) {
  const start = hhmm(facts.startTime);
  const end = hhmm(facts.endTime);
  const entries: Record<string, string | number | undefined> = {
    bloodType: facts.bloodTypes && facts.bloodTypes.length > 0 ? facts.bloodTypes.join(', ') : facts.bloodType,
    units: facts.units,
    hospital: facts.hospital,
    district: facts.district,
    date: facts.date ? String(facts.date).slice(0, 10) : undefined,
    time: start && end ? `${start}-${end}` : start,
    venue: facts.venue,
    contactPhone: facts.contactPhone,
    campName: facts.campName,
    organiser: facts.organiser && !facts.organiser.includes('@') ? facts.organiser : undefined,
  };
  return Object.fromEntries(Object.entries(entries).filter(([, v]) => v !== undefined && v !== ''));
}

/** POST /api/ai/poster-copy. Throws PosterCopyError; the caller falls back to the standard wording on anything but `unverified`. */
export async function requestPosterCopy(
  input: { template: PosterTemplate; language: PosterLanguage; facts: PosterFacts },
  signal?: AbortSignal
): Promise<PosterCopyResponse> {
  try {
    const res = await apiClient.post<unknown>(
      POSTER_PATHS.copy,
      { template: input.template, language: input.language, facts: toBackendFacts(input.facts) },
      { signal }
    );
    const data = (unwrap<Record<string, unknown> | null>(res.data) ?? {}) as Record<string, unknown>;
    return { headline: data.headline, body: data.body, caption: data.caption, hashtags: data.hashtags, usedLlm: data.usedLlm !== false };
  } catch (err) {
    const response = (err as { response?: { status?: number; data?: { code?: unknown } } } | null)?.response;
    if (response?.status === 422 && response.data?.code === UNVERIFIED_REQUESTER_CODE) throw new PosterCopyError('unverified');
    if (response?.status === 429) throw new PosterCopyError('rate_limit');
    throw new PosterCopyError('unavailable');
  }
}
