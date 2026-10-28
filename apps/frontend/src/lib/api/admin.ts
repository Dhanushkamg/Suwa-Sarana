import apiClient from '@/lib/apiClient';
import type { BloodRequest } from '@/types';
import { asDate, asList, isEndpointMissing, num, str } from './common';

/**
 * Admin endpoints in one place. Paths are relative to the API base URL (".../api").
 * The verification "decision" endpoint is being built by the backend engineer; change the path here if it differs.
 */
export const ADMIN_PATHS = {
  districtSummary: '/admin/analytics/district-summary',
  reports: '/admin/reports',
  pendingVerifications: '/admin/verifications/pending',
  verificationDecision: (userId: number) => `/admin/verifications/${userId}/decision`,
  triageQueue: '/admin/triage/queue',
  triageReview: (requestId: number) => `/admin/triage/${requestId}/review`,
} as const;

export type ShortageLevel = 'CRITICAL' | 'WARNING' | 'BALANCED' | 'SURPLUS';

export interface DistrictRow {
  district: string;
  latitude: number;
  longitude: number;
  donorCount: number;
  activeRequests: number;
  fulfilledRequests: number;
  totalRequests: number;
  /** null = no data (no requests); never a made-up 0 or 100. */
  fulfillmentRate: number | null;
  shortageLevel: ShortageLevel;
}

const LEVELS: ShortageLevel[] = ['CRITICAL', 'WARNING', 'BALANCED', 'SURPLUS'];

function toDistrictRow(raw: Record<string, unknown>): DistrictRow {
  const total = num(raw.totalRequests);
  const level = String(raw.shortageLevel ?? 'BALANCED') as ShortageLevel;
  return {
    district: String(raw.district ?? ''),
    latitude: num(raw.latitude),
    longitude: num(raw.longitude),
    donorCount: num(raw.donorCount),
    activeRequests: num(raw.activeRequests),
    fulfilledRequests: num(raw.fulfilledRequests),
    totalRequests: total,
    fulfillmentRate: raw.fulfillmentRate == null || total === 0 ? null : num(raw.fulfillmentRate),
    shortageLevel: LEVELS.includes(level) ? level : 'BALANCED',
  };
}

export async function fetchDistrictSummary(path: string = ADMIN_PATHS.districtSummary): Promise<DistrictRow[]> {
  const res = await apiClient.get(path);
  return asList<Record<string, unknown>>(res.data).map(toDistrictRow);
}

/** National figures are sums of the API's district totals. The fulfilment rate is weighted by request count (not a mean of rates). */
export function nationalTotals(rows: DistrictRow[]) {
  const sum = (pick: (r: DistrictRow) => number) => rows.reduce((acc, r) => acc + pick(r), 0);
  const totalRequests = sum((r) => r.totalRequests);
  const fulfilled = sum((r) => r.fulfilledRequests);
  return {
    totalRequests,
    activeRequests: sum((r) => r.activeRequests),
    fulfilledRequests: fulfilled,
    donors: sum((r) => r.donorCount),
    criticalDistricts: rows.filter((r) => r.shortageLevel === 'CRITICAL').length,
    districtsWithRequests: rows.filter((r) => r.totalRequests > 0).length,
    fulfillmentRate: totalRequests > 0 ? Math.round((fulfilled / totalRequests) * 1000) / 10 : null,
  };
}

export interface FlaggedReport {
  id: number;
  requestId: number;
  reason: string;
  createdAt: Date | null;
}

export async function fetchFlaggedReports(): Promise<FlaggedReport[]> {
  const res = await apiClient.get(ADMIN_PATHS.reports);
  return asList<Record<string, unknown>>(res.data).map((r) => ({
    id: num(r.id),
    requestId: num(r.requestId),
    reason: String(r.reason ?? ''),
    createdAt: asDate(r.createdAt),
  }));
}

export interface PendingVerification {
  id: number;
  email: string;
  phoneNumber?: string;
  role: string;
  fullName?: string;
  organisation?: string;
  submittedAt: Date | null;
}

/** GET /api/admin/verifications/pending: a bare array of users; name, organisation and submitted date are shown when the server sends them. */
export async function fetchPendingVerifications(): Promise<PendingVerification[]> {
  const res = await apiClient.get(ADMIN_PATHS.pendingVerifications);
  return asList<Record<string, unknown>>(res.data).map((r) => ({
    id: num(r.id ?? r.userId),
    email: String(r.email ?? ''),
    phoneNumber: str(r.phoneNumber) ?? str(r.phone),
    role: String(r.role ?? ''),
    fullName: str(r.fullName) ?? str(r.name) ?? str(r.contactName),
    organisation:
      str(r.organisationName) ?? str(r.organizationName) ?? str(r.organisation) ?? str(r.organization) ?? str(r.hospitalName),
    submittedAt: asDate(r.submittedAt ?? r.createdAt ?? r.registeredAt),
  }));
}

export type VerificationDecision = 'APPROVE' | 'REJECT';

export class EndpointUnavailableError extends Error {
  constructor(message = 'Endpoint not available yet') {
    super(message);
    this.name = 'EndpointUnavailableError';
  }
}

/**
 * POST /api/admin/verifications/{userId}/decision  {decision: 'APPROVE' | 'REJECT', reason: string}
 * Throws EndpointUnavailableError while the backend route does not exist yet.
 */
export async function decideVerification(userId: number, decision: VerificationDecision, reason: string): Promise<void> {
  try {
    await apiClient.post(ADMIN_PATHS.verificationDecision(userId), { decision, reason });
  } catch (err) {
    if (isEndpointMissing(err)) throw new EndpointUnavailableError();
    throw err;
  }
}

export interface TriagedRequest extends BloodRequest {
  fraudRiskScore?: number;
  aiFlagReason?: string;
  triagedAt?: string;
}

export async function fetchTriageQueue(): Promise<TriagedRequest[]> {
  const res = await apiClient.get(ADMIN_PATHS.triageQueue);
  return asList<TriagedRequest>(res.data);
}

export async function reviewTriage(requestId: number, notes: string): Promise<void> {
  await apiClient.put(ADMIN_PATHS.triageReview(requestId), { notes });
}
