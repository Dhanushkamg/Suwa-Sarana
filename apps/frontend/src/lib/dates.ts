/**
 * Tolerant date/time parsing for API values (docs/API-CONTRACT.md 0.2). The backend sends:
 *  - moments (createdAt, expiresAt, closedAt, bookedAt ...) as ISO instants with a trailing "Z" (or an offset such as
 *    "+05:30"): the exact instant, parsed as is and shown in Asia/Colombo by useI18n().format;
 *  - wall-clock values read in Sri Lanka (slot/camp startTime and endTime, "2026-12-05T10:00:00", "09:00:00", "2026-12-05")
 *    without a zone: these are Colombo time and must NOT be shifted by the browser's time zone. They are anchored at
 *    UTC+05:30 here, and because the formatter renders in Asia/Colombo the wall-clock time round-trips exactly;
 *  - legacy shapes: arrays ([2026,10,14,9,30]) and epoch seconds/milliseconds.
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
  // Any date after 2006 works; the year 2000 would not: Sri Lanka was on UTC+06:00 then, which shifts the time by 30 minutes
  return fromWallClock(2020, 1, 1, h, m);
}

export const TIME_FORMAT: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' };

const COLOMBO_DAY = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo', year: 'numeric', month: '2-digit', day: '2-digit' });

/** The Sri Lankan calendar day (YYYY-MM-DD) of an instant: what "today" means for expiry dates, camps and slots. */
export function colomboDay(date: Date): string {
  return COLOMBO_DAY.format(date);
}

/** Today in Sri Lanka as YYYY-MM-DD (not the browser's or UTC's date). */
export function todayInColombo(now: Date = new Date()): string {
  return colomboDay(now);
}

/** YYYY-MM-DD plus a number of days (calendar arithmetic, no time zone involved). */
export function addDaysToDay(day: string, days: number): string {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}
