import apiClient from '@/lib/apiClient';
import type { PosterFacts, PosterLanguage, PosterTemplate } from '@/components/poster/types';
import { unwrap } from './common';

/** AI poster-copy endpoint. Kept in one place so the path can change without touching the UI. */
export const POSTER_PATHS = {
  copy: '/ai/poster-copy',
} as const;

/** Error code the backend sends (HTTP 422) when an URGENT_REQUEST poster is asked for by a requester who is not VERIFIED. */
export const UNVERIFIED_REQUESTER_CODE = 'poster.unverified_requester';

/**
 * The error `code` is at the top level of the envelope ({success:false, message, code, data:null}); older builds also put it in data.code.
 */
export function posterErrorCode(body: unknown): string | undefined {
  const o = body as { code?: unknown; data?: { code?: unknown } | null } | null | undefined;
  if (typeof o?.code === 'string') return o.code;
  return typeof o?.data?.code === 'string' ? o.data.code : undefined;
}

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

/**
 * Facts in the shape of the contract (docs/API-CONTRACT.md 11.3): all optional strings, empty ones left out.
 * The server accepts ONE blood type (a camp that asks for several sends none; the poster artwork still lists them) and a single
 * time of day (HH:mm), not a range: sending "O+, O-" or "09:00-15:00" is refused with 400 poster.invalid_fact.
 */
export function toBackendFacts(facts: PosterFacts) {
  const start = hhmm(facts.startTime);
  const bloodType = facts.bloodTypes && facts.bloodTypes.length > 0 ? (facts.bloodTypes.length === 1 ? facts.bloodTypes[0] : undefined) : facts.bloodType;
  const entries: Record<string, string | number | undefined> = {
    bloodType,
    units: facts.units,
    hospital: facts.hospital,
    district: facts.district,
    date: facts.date ? String(facts.date).slice(0, 10) : undefined,
    time: start,
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
    const response = (err as { response?: { status?: number; data?: unknown } } | null)?.response;
    if (response?.status === 422 && posterErrorCode(response.data) === UNVERIFIED_REQUESTER_CODE) throw new PosterCopyError('unverified');
    if (response?.status === 429) throw new PosterCopyError('rate_limit');
    throw new PosterCopyError('unavailable');
  }
}
