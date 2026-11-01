import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock('@/lib/apiClient', () => ({ default: http }));

import { PosterCopyError, posterErrorCode, requestPosterCopy, toBackendFacts } from '../poster';
import type { PosterFacts } from '@/components/poster/types';

beforeEach(() => vi.clearAllMocks());

const envelope = (data: unknown) => ({ data: { success: true, message: 'Success', data, timestamp: '2026-10-31T08:47:50.712Z' } });

describe('poster-copy request (docs/API-CONTRACT.md 11.3)', () => {
  it('sends template, language and facts in the contract shape', async () => {
    http.post.mockResolvedValueOnce(
      envelope({
        headline: 'URGENT: O+ blood needed',
        body: '2 unit(s) of O+ blood are needed at Karapitiya Teaching Hospital, Galle.\nContact: 0771234567',
        hashtags: ['#BloodDonation', '#SriLanka'],
        caption: 'Urgent: O+ blood needed at Karapitiya Teaching Hospital, Galle.',
        usedLlm: false,
      })
    );
    const facts = { bloodType: 'O+', units: 2, hospital: 'Karapitiya Teaching Hospital', district: 'Galle', contactPhone: '0771234567' } as unknown as PosterFacts;
    const copy = await requestPosterCopy({ template: 'URGENT_REQUEST', language: 'en', facts });
    expect(http.post).toHaveBeenCalledWith(
      '/ai/poster-copy',
      { template: 'URGENT_REQUEST', language: 'en', facts: { bloodType: 'O+', units: 2, hospital: 'Karapitiya Teaching Hospital', district: 'Galle', contactPhone: '0771234567' } },
      { signal: undefined }
    );
    expect(copy).toMatchObject({ headline: 'URGENT: O+ blood needed', usedLlm: false });
  });

  it('sends one blood type and one time of day (the server refuses lists and ranges with poster.invalid_fact)', () => {
    const base = { campName: 'Drive', date: '2026-12-05', venue: 'Town hall', district: 'Galle' } as unknown as PosterFacts;
    expect(toBackendFacts({ ...base, bloodTypes: ['O+', 'A-'], startTime: '09:00:00', endTime: '15:00:00' } as PosterFacts)).toEqual({
      campName: 'Drive', date: '2026-12-05', venue: 'Town hall', district: 'Galle', time: '09:00',
    });
    expect(toBackendFacts({ ...base, bloodTypes: ['O-'] } as PosterFacts)).toMatchObject({ bloodType: 'O-' });
  });

  it('reads the error code from the top level of the envelope (and from data.code of older builds)', () => {
    expect(posterErrorCode({ success: false, message: 'm', code: 'poster.unverified_requester', data: null })).toBe('poster.unverified_requester');
    expect(posterErrorCode({ success: false, message: 'm', data: { code: 'poster.invalid_fact' } })).toBe('poster.invalid_fact');
    expect(posterErrorCode({ success: false })).toBeUndefined();
  });

  it('maps 422 poster.unverified_requester, 429 and everything else', async () => {
    http.post.mockRejectedValueOnce({ response: { status: 422, data: { success: false, message: 'Only verified requesters can create an urgent request poster.', code: 'poster.unverified_requester', data: null } } });
    await expect(requestPosterCopy({ template: 'URGENT_REQUEST', language: 'en', facts: {} as PosterFacts })).rejects.toMatchObject({ kind: 'unverified' });
    http.post.mockRejectedValueOnce({ response: { status: 429, data: { success: false, code: 'rate.limited', data: { retryAfterSeconds: 30 } } } });
    await expect(requestPosterCopy({ template: 'AWARENESS', language: 'en', facts: {} as PosterFacts })).rejects.toMatchObject({ kind: 'rate_limit' });
    http.post.mockRejectedValueOnce({ response: { status: 400, data: { success: false, code: 'poster.invalid_fact', data: null } } });
    const err = await requestPosterCopy({ template: 'CAMP', language: 'en', facts: {} as PosterFacts }).catch((e) => e);
    expect(err).toBeInstanceOf(PosterCopyError);
    expect(err.kind).toBe('unavailable');
  });
});
