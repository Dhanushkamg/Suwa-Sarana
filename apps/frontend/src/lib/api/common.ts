import { apiMessage, unwrap } from '@/lib/auth';
import { toDate } from '@/lib/dates';

export { apiMessage, unwrap };

export function apiStatus(err: unknown): number | undefined {
  return (err as { response?: { status?: number } })?.response?.status;
}

/**
 * True when the server does not have this route yet (the backend is built in parallel with the UI).
 * Genuine domain errors ("user not found") come back inside the ApiResponse envelope, which has a boolean `success`;
 * a missing route does not, except 405/501 which always mean "no such operation".
 */
export function isEndpointMissing(err: unknown): boolean {
  const status = apiStatus(err);
  if (status === 405 || status === 501) return true;
  if (status !== 404) return false;
  const body = (err as { response?: { data?: { success?: unknown } } })?.response?.data;
  return typeof body?.success !== 'boolean';
}

/** A list from a bare array, an ApiResponse envelope, or a Spring page ({content: [...]}). */
export function asList<T>(body: unknown): T[] {
  const inner = unwrap<unknown>(body);
  if (Array.isArray(inner)) return inner as T[];
  if (inner && typeof inner === 'object') {
    const o = inner as Record<string, unknown>;
    for (const key of ['content', 'items', 'results', 'data']) {
      if (Array.isArray(o[key])) return o[key] as T[];
    }
  }
  return [];
}

/**
 * A Date from any shape the API sends (see lib/dates.ts): instants with Z, zone-less Colombo wall-clock strings,
 * arrays, epoch numbers. Returns null for anything unusable.
 */
export function asDate(value: unknown): Date | null {
  return toDate(value);
}

const COLOMBO_DAY = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo', year: 'numeric', month: '2-digit', day: '2-digit' });

/** Date input value (YYYY-MM-DD): a plain date stays as sent, a moment becomes the Colombo calendar day. */
export function asDateInput(value: unknown): string {
  if (typeof value === 'string') {
    const plain = /^(\d{4}-\d{2}-\d{2})(?:[T ][\d:.]*)?$/.exec(value); // no zone: already a Colombo calendar date
    if (plain) return plain[1];
  }
  const d = toDate(value);
  return d ? COLOMBO_DAY.format(d) : '';
}

export const num = (v: unknown, fallback = 0): number => {
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
};

export const str = (v: unknown): string | undefined => (typeof v === 'string' && v.trim() ? v : undefined);
