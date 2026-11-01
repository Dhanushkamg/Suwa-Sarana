import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() }));
vi.mock('@/lib/apiClient', () => ({ default: http }));

import {
  decideVerification,
  districtTotals,
  fetchNationalSummary,
  fetchPendingVerifications,
  fetchTriageQueue,
  reviewTriage,
  toDistrictRow,
  toNationalSummary,
} from '../admin';

/** Real shape of GET /api/admin/verifications/pending (ApiResponse<PageDto<AdminUserDto>>). */
const pendingPage = (items: unknown[], page = 0, totalPages = 1) => ({
  data: {
    success: true,
    message: 'Success',
    data: { items, page, size: 100, total: items.length, totalPages },
    timestamp: '2026-10-31T08:47:50.712Z',
  },
});

const hospital = {
  id: 91,
  email: 'hospital.pending@demo.suwasarana.test',
  phoneNumber: '+94771230091',
  firstName: 'Teaching Hospital Kandy (Demo)',
  lastName: 'Admin',
  role: 'HOSPITAL_REQUESTER',
  verificationStatus: 'PENDING',
  status: 'ACTIVE',
  emailVerified: false,
  preferredLanguage: 'en',
  authProvider: 'LOCAL',
  registeredAt: '2026-10-29T04:30:00Z',
  verificationReviewedBy: null,
  verificationReviewedAt: null,
  verificationReason: null,
};

beforeEach(() => vi.clearAllMocks());

describe('verification queue', () => {
  it('reads the PageDto and takes the organisation name from firstName', async () => {
    http.get.mockResolvedValueOnce(pendingPage([hospital]));
    const rows = await fetchPendingVerifications();
    expect(http.get).toHaveBeenCalledWith('/admin/verifications/pending', { params: { page: 0, size: 100 } });
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      id: 91,
      organisation: 'Teaching Hospital Kandy (Demo)',
      fullName: 'Admin',
      role: 'HOSPITAL_REQUESTER',
      emailVerified: false,
      phoneNumber: '+94771230091',
    });
    expect(rows[0].submittedAt?.toISOString()).toBe('2026-10-29T04:30:00.000Z');
  });

  it('shows a requester by personal name and passes role=REQUESTER', async () => {
    http.get.mockResolvedValueOnce(pendingPage([{ ...hospital, id: 5, role: 'REQUESTER', firstName: 'Nimal', lastName: 'Perera' }]));
    const rows = await fetchPendingVerifications('REQUESTER');
    expect(http.get).toHaveBeenCalledWith('/admin/verifications/pending', { params: { page: 0, size: 100, role: 'REQUESTER' } });
    expect(rows[0]).toMatchObject({ fullName: 'Nimal Perera', organisation: undefined });
  });

  it('follows totalPages', async () => {
    http.get.mockResolvedValueOnce(pendingPage([hospital], 0, 2)).mockResolvedValueOnce(pendingPage([{ ...hospital, id: 92 }], 1, 2));
    const rows = await fetchPendingVerifications();
    expect(rows.map((r) => r.id)).toEqual([91, 92]);
    expect(http.get).toHaveBeenCalledTimes(2);
    expect(http.get).toHaveBeenLastCalledWith('/admin/verifications/pending', { params: { page: 1, size: 100 } });
  });

  it('approves and rejects with PUT {reason}', async () => {
    http.put.mockResolvedValue({ data: {} });
    await decideVerification(91, 'APPROVE', '  Registration number checked  ');
    expect(http.put).toHaveBeenLastCalledWith('/admin/verifications/91/approve', { reason: 'Registration number checked' });
    await decideVerification(91, 'REJECT', 'Not a registered hospital');
    expect(http.put).toHaveBeenLastCalledWith('/admin/verifications/91/reject', { reason: 'Not a registered hospital' });
  });
});

/** Real GET /api/analytics/district-summary rows (DistrictSummaryDto). */
const colombo = {
  district: 'Colombo', latitude: 6.9271, longitude: 79.8612, donorCount: 10, registeredDonors: 10, availableDonors: 7,
  activeRequests: 3, awaitingRequests: 1, fulfilledRequests: 4, closedRequests: 8, totalRequests: 11, fulfillmentRate: 50.0,
  shortageLevel: 'WARNING', hasData: true, windowDays: 30,
};
const empty = {
  district: 'Mullaitivu', latitude: 9.2671, longitude: 80.8143, donorCount: 0, registeredDonors: 0, availableDonors: 0,
  activeRequests: 0, awaitingRequests: 0, fulfilledRequests: 0, closedRequests: 0, totalRequests: 0, fulfillmentRate: null,
  shortageLevel: 'NO_DATA', hasData: false, windowDays: 30,
};

describe('analytics', () => {
  it('keeps NO_DATA and a null fulfilment rate (never BALANCED, never 0 or 100)', () => {
    expect(toDistrictRow(empty)).toMatchObject({ shortageLevel: 'NO_DATA', hasData: false, fulfillmentRate: null });
    expect(toDistrictRow(colombo)).toMatchObject({ shortageLevel: 'WARNING', hasData: true, fulfillmentRate: 50, availableDonors: 7, awaitingRequests: 1, closedRequests: 8 });
  });

  it('turns an unknown or missing level into NO_DATA, not BALANCED', () => {
    expect(toDistrictRow({ ...colombo, shortageLevel: undefined }).shortageLevel).toBe('NO_DATA');
    expect(toDistrictRow({ ...colombo, shortageLevel: 'SOMETHING_NEW' }).shortageLevel).toBe('NO_DATA');
  });

  it('a rate with zero closed requests stays null even if there are total requests', () => {
    expect(toDistrictRow({ ...colombo, closedRequests: 0, fulfillmentRate: null }).fulfillmentRate).toBeNull();
  });

  it('admin-only counters are optional', () => {
    expect(toDistrictRow(colombo).unitsOutstanding).toBeUndefined();
    expect(toDistrictRow({ ...colombo, expiredRequests: 2, cancelledRequests: 1, unitsOutstanding: 5 })).toMatchObject({ expiredRequests: 2, cancelledRequests: 1, unitsOutstanding: 5 });
  });

  it('district totals count critical districts and districts with data', () => {
    const rows = [toDistrictRow(colombo), toDistrictRow({ ...colombo, district: 'Galle', shortageLevel: 'CRITICAL' }), toDistrictRow(empty)];
    expect(districtTotals(rows)).toMatchObject({ criticalDistricts: 1, districtsWithData: 2, availableDonors: 14, activeRequests: 6 });
  });

  const summary = {
    windowDays: 30, generatedAt: '2026-10-31T08:47:50.628Z', activeRequests: 18, awaitingRequests: 6, fulfilledRequests: 9,
    closedRequests: 18, fulfillmentRate: 50.0, registeredDonors: 60, donorsWithProfile: 60, availableDonors: 41,
    districtsWithData: 25, districtsTotal: 25,
  };

  it('reads the national summary and picks the admin or the public endpoint', async () => {
    http.get.mockResolvedValue({ data: { success: true, data: { ...summary, expiredRequests: 3, cancelledRequests: 6, unitsOutstanding: 22, unmappedRequests: 0 } } });
    const admin = await fetchNationalSummary(true);
    expect(http.get).toHaveBeenLastCalledWith('/admin/analytics/summary');
    expect(admin).toMatchObject({ activeRequests: 18, awaitingRequests: 6, fulfillmentRate: 50, unitsOutstanding: 22, districtsTotal: 25 });
    await fetchNationalSummary(false);
    expect(http.get).toHaveBeenLastCalledWith('/analytics/summary');
  });

  it('a null national rate is no data', () => {
    expect(toNationalSummary({ ...summary, fulfillmentRate: null, closedRequests: 0 }).fulfillmentRate).toBeNull();
  });
});

describe('triage queue', () => {
  const item = {
    id: 12, patientBloodType: 'O+', unitsNeeded: 9, urgency: 'CRITICAL', hospitalName: 'Unknown Clinic', district: 'Galle',
    status: 'OPEN', currentRadiusKm: 10, createdAt: '2026-10-31T04:00:00Z', expiresAt: '2026-11-01T04:00:00Z', fraudRiskScore: 80,
    aiFlagReason: 'Unverified requester asked for 9 units', triagedAt: '2026-10-31T04:00:05Z',
    reasons: [{ code: 'UNVERIFIED_HIGH_UNITS', weight: 35 }, { code: 'UNVERIFIED_CRITICAL', weight: 25 }],
    engineVersion: 'rules-v2', humanReviewRecommended: true, reviewed: false, reviewedBy: null, reviewedAt: null, reviewDecision: null, reviewNote: null,
  };

  it('reads reasons and the review state', async () => {
    http.get.mockResolvedValueOnce({ data: { success: true, data: [item] } });
    const [row] = await fetchTriageQueue();
    expect(row).toMatchObject({ id: 12, fraudRiskScore: 80, humanReviewRecommended: true, reviewed: false, engineVersion: 'rules-v2' });
    expect(row.reasons).toEqual([{ code: 'UNVERIFIED_HIGH_UNITS', weight: 35 }, { code: 'UNVERIFIED_CRITICAL', weight: 25 }]);
  });

  it('sends the review decision', async () => {
    http.put.mockResolvedValue({ data: {} });
    await reviewTriage(12, 'checked by phone', 'DISMISSED');
    expect(http.put).toHaveBeenCalledWith('/admin/triage/12/review', { notes: 'checked by phone', decision: 'DISMISSED' });
  });
});
