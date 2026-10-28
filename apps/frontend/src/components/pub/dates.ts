/**
 * Tolerant date/time parsing for API values. The backend sends java.time values that may arrive as
 * ISO strings ("2026-10-14", "2026-10-14T09:30:00", "...Z"), as arrays ([2026,10,14] / [2026,10,14,9,30]) or as
 * epoch seconds. Values without a zone are Sri Lanka wall-clock time (UTC+05:30); callers format with
 * useI18n().format, which renders in Asia/Colombo, so the wall-clock time round-trips exactly.
 */
const COLOMBO_OFFSET_MIN = 330;

function fromWallClock(y: number, mo: number, d: number, h = 0, mi = 0, s = 0): Date {
  return new Date(Date.UTC(y, mo - 1, d, h, mi - COLOMBO_OFFSET_MIN, s));
}

export function toDate(value: unknown): Date | null {
  if (value == null || value === '') return null;
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;

  if (Array.isArray(value)) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = value.map(Number);
    if (![y, mo, d].every(Number.isFinite)) return null;
    // [y,m,d] is a calendar date: anchor at noon so any display time zone shows the same day
    return value.length <= 3 ? fromWallClock(y, mo, d, 12) : fromWallClock(y, mo, d, h, mi, s);
  }

  if (typeof value === 'number') {
    const ms = value < 1e11 ? value * 1000 : value; // seconds vs milliseconds
    const date = new Date(ms);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  if (typeof value === 'string') {
    const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    if (dateOnly) return fromWallClock(Number(dateOnly[1]), Number(dateOnly[2]), Number(dateOnly[3]), 12);
    const local = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?$/.exec(value);
    if (local) return fromWallClock(+local[1], +local[2], +local[3], +local[4], +local[5], local[6] ? +local[6] : 0);
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }
  return null;
}

/** "09:00:00", "09:00" or [9,0] -> a Date carrying that wall-clock time, ready for format.dateTime({hour, minute}). */
export function toTimeOfDay(value: unknown): Date | null {
  let h: number;
  let m: number;
  if (Array.isArray(value)) {
    [h, m] = value.map(Number);
  } else if (typeof value === 'string') {
    const match = /^(\d{1,2}):(\d{2})/.exec(value);
    if (!match) return null;
    h = Number(match[1]);
    m = Number(match[2]);
  } else {
    return null;
  }
  if (!Number.isFinite(h) || !Number.isFinite(m)) return null;
  return fromWallClock(2000, 1, 1, h, m);
}

export const TIME_FORMAT: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' };
