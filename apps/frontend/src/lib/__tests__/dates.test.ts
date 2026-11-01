import { describe, expect, it } from 'vitest';
import { asDateInput } from '../api/common';
import { toDate, toTimeOfDay } from '../dates';
import { formatterIn } from '../i18n';

const f = formatterIn('en');
const clock = { hour: '2-digit', minute: '2-digit', hour12: false } as const;

describe('API date handling (docs/API-CONTRACT.md 0.2)', () => {
  it('an instant with Z is shown in Asia/Colombo (UTC+05:30)', () => {
    const d = toDate('2026-10-31T08:47:50.628Z');
    expect(d?.toISOString()).toBe('2026-10-31T08:47:50.628Z');
    expect(f.dateTime(d as Date, clock)).toBe('14:17');
    // StockSignal.updatedAt carries an offset instead of Z
    expect(toDate('2026-10-31T01:12:39+05:30')?.toISOString()).toBe('2026-10-30T19:42:39.000Z');
  });

  it('a zone-less slot time is Colombo wall-clock and is not shifted', () => {
    const slot = toDate('2026-12-05T10:00:00');
    expect(slot?.toISOString()).toBe('2026-12-05T04:30:00.000Z');
    expect(f.dateTime(slot as Date, clock)).toBe('10:00');
    expect(f.dateTime('2026-12-05T10:00:00', clock)).toBe('10:00');
  });

  it('a calendar date shows the same day, whatever the browser time zone', () => {
    expect(f.date('2026-12-05', { day: '2-digit' })).toBe('05');
    expect(asDateInput('2026-12-05')).toBe('2026-12-05');
    expect(asDateInput('2026-12-05T10:00:00')).toBe('2026-12-05');
  });

  it('an instant near midnight UTC is the next Colombo day', () => {
    expect(asDateInput('2026-10-31T20:00:00Z')).toBe('2026-11-01');
  });

  it('camp times of day', () => {
    expect(f.dateTime(toTimeOfDay('09:00:00') as Date, clock)).toBe('09:00');
  });

  it('rejects junk', () => {
    expect(toDate('not a date')).toBeNull();
    expect(toDate(null)).toBeNull();
    expect(asDateInput(undefined)).toBe('');
  });
});
