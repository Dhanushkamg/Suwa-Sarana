import { describe, expect, it } from 'vitest';
import { FaqError, normalizeFaqAnswer, parseRetryAfter, safeSourceUrl, toFaqError } from '../faq';

describe('normalizeFaqAnswer', () => {
  it('reads the envelope and marks grounded only for a literal true', () => {
    const grounded = normalizeFaqAnswer({ success: true, data: { answer: ' Hi ', grounded: true, language: 'si' } });
    expect(grounded.grounded).toBe(true);
    expect(grounded.answer).toBe('Hi');
    expect(grounded.language).toBe('si');

    expect(normalizeFaqAnswer({ data: { answer: 'x', grounded: 'true' } }).grounded).toBe(false);
    expect(normalizeFaqAnswer({ data: { answer: 'x' } }).grounded).toBe(false);
  });

  it('ignores the deprecated groundedInRules field', () => {
    expect(normalizeFaqAnswer({ data: { answer: 'x', groundedInRules: true } }).grounded).toBe(false);
  });

  it('keeps sources, drops untitled ones and defaults unknown statuses', () => {
    const result = normalizeFaqAnswer({
      data: {
        answer: 'x',
        sources: [
          { id: 'a', title: 'Eligibility', url: 'https://example.org/a', lastReviewed: '2026-03-01', status: 'CONFLICT' },
          { id: 'b', title: '', url: 'https://example.org/b' },
          { id: 'c', title: 'Other', url: 'https://example.org/c', status: 'WHATEVER' },
        ],
      },
    });
    expect(result.sources.map((s) => [s.id, s.status])).toEqual([
      ['a', 'CONFLICT'],
      ['c', 'VERIFIED'],
    ]);
    expect(result.sources[0].lastReviewed).toMatch(/^2026-0(2-28|3-01)/);
  });

  it('caps follow-ups at four and drops blanks', () => {
    const result = normalizeFaqAnswer({ data: { answer: 'x', followUps: ['a', ' ', 'b', 'c', 'd', 'e'] } });
    expect(result.followUps).toEqual(['a', 'b', 'c', 'd']);
  });
});

describe('errors', () => {
  it('parses Retry-After seconds and dates, with sane bounds', () => {
    expect(parseRetryAfter('7')).toBe(7);
    expect(parseRetryAfter(undefined)).toBe(30);
    expect(parseRetryAfter('0')).toBe(1);
    expect(parseRetryAfter('99999')).toBe(300);
    const now = Date.parse('2026-01-01T00:00:00Z');
    expect(parseRetryAfter('Thu, 01 Jan 2026 00:00:20 GMT', now)).toBe(20);
  });

  it('classifies failures', () => {
    expect(toFaqError({ response: { status: 429, headers: { 'retry-after': '12' } } })).toMatchObject({ kind: 'rate_limit', retryAfterSeconds: 12 });
    expect(toFaqError({ response: { status: 429, headers: {} } })).toMatchObject({ kind: 'rate_limit', retryAfterSeconds: 30 });
    expect(toFaqError({ response: { status: 400 } }).kind).toBe('invalid');
    expect(toFaqError({ response: { status: 502 } }).kind).toBe('server');
    expect(toFaqError(new Error('Network Error')).kind).toBe('offline');
    const existing = new FaqError('server');
    expect(toFaqError(existing)).toBe(existing);
  });
});

describe('safeSourceUrl', () => {
  it('allows http(s) and site paths only', () => {
    expect(safeSourceUrl('https://nbts.health.gov.lk/page')).toBe('https://nbts.health.gov.lk/page');
    expect(safeSourceUrl('/faq')).toBe('/faq');
    expect(safeSourceUrl('javascript:alert(1)')).toBeNull();
    expect(safeSourceUrl('data:text/html,hi')).toBeNull();
    expect(safeSourceUrl('//evil.example')).toBeNull();
    expect(safeSourceUrl('')).toBeNull();
  });
});
