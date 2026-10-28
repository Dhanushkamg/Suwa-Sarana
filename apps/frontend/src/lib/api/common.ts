import { apiMessage, unwrap } from '@/lib/auth';

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
 * Dates arrive as ISO strings, epoch numbers, or (if the server's Jackson config writes timestamps)
 * arrays like [2026, 3, 14, 9, 30]. Returns null for anything unusable.
 */
export function asDate(value: unknown): Date | null {
  if (value == null || value === '') return null;
  if (Array.isArray(value) && value.length >= 3) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = value.map(Number);
    const date = new Date(y, mo - 1, d, h, mi, s);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  const date = new Date(value as string | number);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** Date input value (YYYY-MM-DD) from any of the shapes above. */
export function asDateInput(value: unknown): string {
  const d = asDate(value);
  if (!d) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export const num = (v: unknown, fallback = 0): number => {
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
};

export const str = (v: unknown): string | undefined => (typeof v === 'string' && v.trim() ? v : undefined);
