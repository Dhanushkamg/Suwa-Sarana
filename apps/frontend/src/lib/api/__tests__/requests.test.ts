import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn(), delete: vi.fn() }));
vi.mock('@/lib/apiClient', () => ({ default: http }));

import { effectiveMatchStatus } from '@/components/dash/matchStatus';
import { CLOSE_REASONS, closeRequest, fetchRequest, fetchRequestMatches, fetchRequests, fulfilRequest, recordDonation, reportRequest } from '../requests';

const envelope = (data: unknown) => ({ data: { success: true, message: 'Success', data, timestamp: '2026-10-31T08:47:50.712Z' } });

/** Real RequestResponseDto, owner view. */
const owned = {
  id: 41, patientBloodType: 'O+', unitsNeeded: 3, urgency: 'URGENT', hospitalName: 'Karapitiya Teaching Hospital', district: 'Galle',
  latitude: 6.0535, longitude: 80.221, status: 'MATCHED', currentRadiusKm: 20, createdAt: '2026-10-30T04:00:00.000Z',
  expiresAt: '2026-11-02T04:00:00.000Z', fraudRiskScore: null, aiFlagReason: null, triagedAt: null, closedAt: null, closeReason: null,
  acceptedCount: 2, confirmedDonations: 1, coarseLocation: false,
};

beforeEach(() => vi.clearAllMocks());

describe('requests', () => {
  it('keeps the new fields: closedAt, closeReason, acceptedCount, confirmedDonations, coarseLocation', async () => {
    http.get.mockResolvedValueOnce(envelope([owned, { ...owned, id: 42, status: 'CANCELLED', closedAt: '2026-10-31T01:00:00.000Z', closeReason: 'FOUND_DONOR_ELSEWHERE' }]));
    const rows = await fetchRequests(false);
    expect(http.get).toHaveBeenCalledWith('/requests/me');
    expect(rows[0]).toMatchObject({ id: 41, acceptedCount: 2, confirmedDonations: 1, coarseLocation: false, closedAt: null, closeReason: null });
    expect(rows[1]).toMatchObject({ status: 'CANCELLED', closedAt: '2026-10-31T01:00:00.000Z', closeReason: 'FOUND_DONOR_ELSEWHERE' });
  });

  it('a non-owner gets null counters and rounded coordinates', async () => {
    http.get.mockResolvedValueOnce(envelope({ ...owned, latitude: 6.05, longitude: 80.22, acceptedCount: null, confirmedDonations: null, coarseLocation: true }));
    const r = await fetchRequest(41);
    expect(r).toMatchObject({ acceptedCount: null, confirmedDonations: null, coarseLocation: true, latitude: 6.05 });
  });

  it('admins read all requests', async () => {
    http.get.mockResolvedValueOnce(envelope([]));
    await fetchRequests(true);
    expect(http.get).toHaveBeenCalledWith('/requests');
  });
});

/** Real GET /api/requests/{id}/matches body (RequestMatchesDto). */
const matchesBody = {
  requestId: 41, unitsNeeded: 3, acceptedCount: 2, confirmedDonations: 1,
  matches: [
    { matchId: 501, donorFirstName: 'Dinesh', bloodType: 'O+', distanceKm: 3.4, status: 'ACCEPTED', notifiedAt: '2026-10-30T04:05:00.000Z', respondedAt: '2026-10-30T04:20:00.000Z', contact: { phoneNumber: '+94771234567' } },
    { matchId: 502, donorFirstName: 'Kamal', bloodType: 'O-', distanceKm: 7.1, status: 'ACCEPTED', notifiedAt: '2026-10-30T04:05:00.000Z', respondedAt: '2026-10-30T04:30:00.000Z', donated: true },
    { matchId: 503, donorFirstName: 'Nimali', bloodType: 'O+', distanceKm: 12, status: 'PENDING', notifiedAt: '2026-10-30T04:05:00.000Z' },
    { matchId: 504, donorFirstName: 'Saman', bloodType: 'O+', distanceKm: null, status: 'ACCEPTED', notifiedAt: '2026-10-30T04:05:00.000Z', respondedAt: '2026-10-30T05:00:00.000Z', donated: false },
    { matchId: 505, donorFirstName: 'Ruwan', bloodType: 'O+', distanceKm: 9, status: 'DECLINED', notifiedAt: '2026-10-30T04:05:00.000Z', respondedAt: '2026-10-30T04:40:00.000Z', contact: { phoneNumber: '+94770000000' } },
  ],
};

describe('request matches', () => {
  it('reads the summary and the matches', async () => {
    http.get.mockResolvedValueOnce(envelope(matchesBody));
    const result = await fetchRequestMatches(41);
    expect(http.get).toHaveBeenCalledWith('/requests/41/matches');
    expect(result).toMatchObject({ requestId: 41, unitsNeeded: 3, acceptedCount: 2, confirmedDonations: 1 });
    expect(result.matches).toHaveLength(5);
    expect(result.matches[0]).toMatchObject({ id: 501, donorFirstName: 'Dinesh', bloodType: 'O+', distanceKm: 3.4, status: 'ACCEPTED', contactPhone: '+94771234567' });
    expect(result.matches[0].respondedAt?.toISOString()).toBe('2026-10-30T04:20:00.000Z');
  });

  it('shows a phone number only for an ACCEPTED match without a recorded outcome', async () => {
    http.get.mockResolvedValueOnce(envelope(matchesBody));
    const { matches } = await fetchRequestMatches(41);
    expect(matches.map((m) => m.contactPhone)).toEqual(['+94771234567', undefined, undefined, undefined, undefined]);
  });

  it('maps the recorded outcome to a display status', async () => {
    http.get.mockResolvedValueOnce(envelope(matchesBody));
    const { matches } = await fetchRequestMatches(41);
    expect(matches.map((m) => effectiveMatchStatus(m.status, m.donated))).toEqual(['ACCEPTED', 'COMPLETED', 'PENDING', 'NO_SHOW', 'DECLINED']);
    expect(matches[3].distanceKm).toBeNull();
  });
});

describe('closing the loop', () => {
  it('fulfils with POST and no body', async () => {
    http.post.mockResolvedValue(envelope(owned));
    await fulfilRequest(41);
    expect(http.post).toHaveBeenCalledWith('/requests/41/fulfil');
  });

  it('closes with the CloseReason enum, or without a reason', async () => {
    expect([...CLOSE_REASONS]).toEqual(['NO_LONGER_NEEDED', 'FOUND_DONOR_ELSEWHERE', 'OTHER']);
    http.post.mockResolvedValue(envelope(owned));
    await closeRequest(41, 'NO_LONGER_NEEDED');
    expect(http.post).toHaveBeenLastCalledWith('/requests/41/close', { reason: 'NO_LONGER_NEEDED' });
    await closeRequest(41);
    expect(http.post).toHaveBeenLastCalledWith('/requests/41/close', {});
  });

  it('records whether an accepted donor donated through the query parameter', async () => {
    http.post.mockResolvedValue(envelope(null));
    await recordDonation(501, true);
    expect(http.post).toHaveBeenCalledWith('/matches/501/donated', null, { params: { donated: true } });
    await recordDonation(504, false);
    expect(http.post).toHaveBeenLastCalledWith('/matches/504/donated', null, { params: { donated: false } });
  });

  it('reports a request with a ReportReason', async () => {
    http.post.mockResolvedValue(envelope(null));
    await reportRequest(41, 'SPAM', '  looks like an advert ');
    expect(http.post).toHaveBeenCalledWith('/requests/41/report', { reason: 'SPAM', details: 'looks like an advert' });
  });
});
