import apiClient from '@/lib/apiClient';
import type { BloodRequest } from '@/types';
import { asDate, asList, num, str, unwrap } from './common';

/**
 * Admin endpoints in one place. Paths are relative to the API base URL (".../api"). Contract: docs/API-CONTRACT.md sections 9 and 10.
 */
export const ADMIN_PATHS = {
  districtSummary: '/admin/analytics/district-summary',
  summary: '/admin/analytics/summary',
  reports: '/admin/reports',
  pendingVerifications: '/admin/verifications/pending',
  approve: (userId: number) => `/admin/verifications/${userId}/approve`,
  reject: (userId: number) => `/admin/verifications/${userId}/reject`,
  triageQueue: '/admin/triage/queue',
  triageReview: (requestId: number) => `/admin/triage/${requestId}/review`,
} as const;

/** Signed-in (any role) national and district analytics. */
export const ANALYTICS_PATHS = {
  summary: '/analytics/summary',
  districtSummary: '/analytics/district-summary',
} as const;

/* ------------------------------------------------------------------
   Analytics
------------------------------------------------------------------- */

/** `NO_DATA` = nothing is known about the district yet. It is never shown as, or counted as, BALANCED. */
export type ShortageLevel = 'CRITICAL' | 'WARNING' | 'BALANCED' | 'SURPLUS' | 'NO_DATA';

export const SHORTAGE_LEVELS: ShortageLevel[] = ['CRITICAL', 'WARNING', 'BALANCED', 'SURPLUS', 'NO_DATA'];

export interface DistrictRow {
  district: string;
  latitude: number;
  longitude: number;
  /** Same as registeredDonors (kept for older callers). */
  donorCount: number;
  registeredDonors: number;
  /** Donors who can be asked today: available, age and weight rules met, no active deferral. */
  availableDonors: number;
  activeRequests: number;
  /** The matched part of the active requests: donors have accepted, donation still to happen. */
  awaitingRequests: number;
  fulfilledRequests: number;
  /** Fulfilled + expired + cancelled in the last `windowDays` days. */
  closedRequests: number;
  totalRequests: number;
  /** Percent (0-100, one decimal). null = no data (nothing closed in the window): never a made-up 0 or 100. */
  fulfillmentRate: number | null;
  shortageLevel: ShortageLevel;
  /** False = the district has no donors, no active request and nothing closed in the window. */
  hasData: boolean;
  windowDays: number;
  /** Admin endpoint only. */
  expiredRequests?: number;
  cancelledRequests?: number;
  unitsOutstanding?: number;
}

export function toDistrictRow(raw: Record<string, unknown>): DistrictRow {
  const level = String(raw.shortageLevel ?? '') as ShortageLevel;
  const shortageLevel: ShortageLevel = SHORTAGE_LEVELS.includes(level) ? level : 'NO_DATA';
  const registered = num(raw.registeredDonors ?? raw.donorCount);
  const optional = (v: unknown) => (v == null ? undefined : num(v));
  return {
    district: String(raw.district ?? ''),
    latitude: num(raw.latitude),
    longitude: num(raw.longitude),
    donorCount: registered,
    registeredDonors: registered,
    availableDonors: num(raw.availableDonors),
    activeRequests: num(raw.activeRequests),
    awaitingRequests: num(raw.awaitingRequests),
    fulfilledRequests: num(raw.fulfilledRequests),
    closedRequests: num(raw.closedRequests),
    totalRequests: num(raw.totalRequests),
    fulfillmentRate: raw.fulfillmentRate == null ? null : num(raw.fulfillmentRate),
    shortageLevel,
    hasData: typeof raw.hasData === 'boolean' ? raw.hasData : shortageLevel !== 'NO_DATA',
    windowDays: num(raw.windowDays, 30),
    expiredRequests: optional(raw.expiredRequests),
    cancelledRequests: optional(raw.cancelledRequests),
    unitsOutstanding: optional(raw.unitsOutstanding),
  };
}

export async function fetchDistrictSummary(path: string = ADMIN_PATHS.districtSummary): Promise<DistrictRow[]> {
  const res = await apiClient.get(path);
  return asList<Record<string, unknown>>(res.data).map(toDistrictRow);
}

/** Totals over the district rows (the national figures come from the summary endpoints, see fetchNationalSummary). */
export function districtTotals(rows: DistrictRow[]) {
  const sum = (pick: (r: DistrictRow) => number) => rows.reduce((acc, r) => acc + pick(r), 0);
  return {
    activeRequests: sum((r) => r.activeRequests),
    awaitingRequests: sum((r) => r.awaitingRequests),
    registeredDonors: sum((r) => r.registeredDonors),
    availableDonors: sum((r) => r.availableDonors),
    criticalDistricts: rows.filter((r) => r.shortageLevel === 'CRITICAL').length,
    districtsWithData: rows.filter((r) => r.hasData).length,
  };
}

export interface NationalSummary {
  windowDays: number;
  generatedAt: Date | null;
  activeRequests: number;
  /** MATCHED subset of the active requests. */
  awaitingRequests: number;
  fulfilledRequests: number;
  closedRequests: number;
  /** Percent over the window; null = no data. */
  fulfillmentRate: number | null;
  registeredDonors: number;
  donorsWithProfile: number;
  availableDonors: number;
  districtsWithData: number;
  districtsTotal: number;
  /** Admin endpoint only. */
  expiredRequests?: number;
  cancelledRequests?: number;
  unitsOutstanding?: number;
  unmappedRequests?: number;
}

export function toNationalSummary(raw: Record<string, unknown>): NationalSummary {
  const optional = (v: unknown) => (v == null ? undefined : num(v));
  return {
    windowDays: num(raw.windowDays, 30),
    generatedAt: asDate(raw.generatedAt),
    activeRequests: num(raw.activeRequests),
    awaitingRequests: num(raw.awaitingRequests),
    fulfilledRequests: num(raw.fulfilledRequests),
    closedRequests: num(raw.closedRequests),
    fulfillmentRate: raw.fulfillmentRate == null ? null : num(raw.fulfillmentRate),
    registeredDonors: num(raw.registeredDonors),
    donorsWithProfile: num(raw.donorsWithProfile),
    availableDonors: num(raw.availableDonors),
    districtsWithData: num(raw.districtsWithData),
    districtsTotal: num(raw.districtsTotal, 25),
    expiredRequests: optional(raw.expiredRequests),
    cancelledRequests: optional(raw.cancelledRequests),
    unitsOutstanding: optional(raw.unitsOutstanding),
    unmappedRequests: optional(raw.unmappedRequests),
  };
}

/** GET /api/admin/analytics/summary (admin, with the extra counters) or GET /api/analytics/summary (any signed-in user). */
export async function fetchNationalSummary(admin: boolean): Promise<NationalSummary> {
  const res = await apiClient.get(admin ? ADMIN_PATHS.summary : ANALYTICS_PATHS.summary);
  return toNationalSummary((unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>);
}

/* ------------------------------------------------------------------
   Reports
------------------------------------------------------------------- */

export interface FlaggedReport {
  id: number;
  requestId: number;
  /** ReportReason: FAKE_OR_MISLEADING | DUPLICATE | SPAM | ABUSIVE_CONTENT | OTHER (translated with dashUi.reportReason.<code>). */
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

/* ------------------------------------------------------------------
   Verification queue
------------------------------------------------------------------- */

export interface PendingVerification {
  id: number;
  email: string;
  phoneNumber?: string;
  role: string;
  /** For institutions (hospital, blood bank) the organisation name arrives in `firstName`. */
  firstName: string;
  lastName?: string;
  /** Person's name for REQUESTER accounts; for institutions the contact part (last name), if any. */
  fullName?: string;
  organisation?: string;
  emailVerified: boolean;
  submittedAt: Date | null;
}

export const isInstitutionRole = (role: string) => role === 'HOSPITAL_REQUESTER' || role === 'BLOOD_BANK_REQUESTER';

export function toPendingVerification(r: Record<string, unknown>): PendingVerification {
  const role = String(r.role ?? '');
  const firstName = str(r.firstName) ?? '';
  const lastName = str(r.lastName);
  const institution = isInstitutionRole(role);
  return {
    id: num(r.id ?? r.userId),
    email: String(r.email ?? ''),
    phoneNumber: str(r.phoneNumber),
    role,
    firstName,
    lastName,
    organisation: institution ? firstName || undefined : undefined,
    fullName: institution ? lastName : [firstName, lastName].filter(Boolean).join(' ') || undefined,
    emailVerified: r.emailVerified === true,
    submittedAt: asDate(r.registeredAt),
  };
}

export interface VerificationPage {
  items: PendingVerification[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

const QUEUE_PAGE_SIZE = 100;
const QUEUE_MAX_PAGES = 10;

/**
 * GET /api/admin/verifications/pending?role=&page=&size=: an ApiResponse whose data is a PageDto {items, page, size, total, totalPages}.
 * Without `role` the queue is the institutions (hospitals, blood banks); `role: 'REQUESTER'` lists patient/family requesters.
 * Loads every page (up to 1000 accounts) so the search box can filter the whole queue.
 */
export async function fetchPendingVerifications(role?: 'REQUESTER'): Promise<PendingVerification[]> {
  const items: PendingVerification[] = [];
  for (let page = 0; page < QUEUE_MAX_PAGES; page++) {
    const res = await apiClient.get(ADMIN_PATHS.pendingVerifications, { params: { page, size: QUEUE_PAGE_SIZE, ...(role ? { role } : {}) } });
    const body = (unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>;
    items.push(...asList<Record<string, unknown>>(body).map(toPendingVerification));
    if (page + 1 >= num(body.totalPages, 1)) break;
  }
  return items;
}

export type VerificationDecision = 'APPROVE' | 'REJECT';

/** The reason must be 3 to 500 characters (400 admin.reason.required / admin.reason.tooLong otherwise). */
export const VERIFICATION_REASON_MIN = 3;
export const VERIFICATION_REASON_MAX = 500;

/**
 * PUT /api/admin/verifications/{userId}/approve  or  /reject  with body {reason}. The user is notified by e-mail and SMS.
 * 409 admin.verification.alreadyDecided when somebody else was faster.
 */
export async function decideVerification(userId: number, decision: VerificationDecision, reason: string): Promise<void> {
  const path = decision === 'APPROVE' ? ADMIN_PATHS.approve(userId) : ADMIN_PATHS.reject(userId);
  await apiClient.put(path, { reason: reason.trim() });
}

/* ------------------------------------------------------------------
   Triage queue
------------------------------------------------------------------- */

export type TriageDecision = 'REVIEWED' | 'DISMISSED' | 'CONFIRMED';

export interface TriageReason {
  /** REQUESTER_HIGH_FREQUENCY | REQUESTER_REPEAT_24H | UNVERIFIED_HIGH_UNITS | UNVERIFIED_MODERATE_UNITS | SUSPICIOUS_HOSPITAL_NAME | UNVERIFIED_CRITICAL */
  code: string;
  weight: number;
}

export interface TriagedRequest extends BloodRequest {
  fraudRiskScore?: number;
  aiFlagReason?: string;
  triagedAt?: string;
  reasons: TriageReason[];
  engineVersion?: string;
  humanReviewRecommended: boolean;
  reviewed: boolean;
  reviewDecision?: string;
  reviewNote?: string;
}

export async function fetchTriageQueue(): Promise<TriagedRequest[]> {
  const res = await apiClient.get(ADMIN_PATHS.triageQueue);
  return asList<Record<string, unknown>>(res.data).map((raw) => ({
    ...(raw as unknown as BloodRequest),
    fraudRiskScore: raw.fraudRiskScore == null ? undefined : num(raw.fraudRiskScore),
    aiFlagReason: str(raw.aiFlagReason),
    triagedAt: str(raw.triagedAt),
    reasons: Array.isArray(raw.reasons)
      ? (raw.reasons as Record<string, unknown>[]).map((r) => ({ code: String(r.code ?? ''), weight: num(r.weight) })).filter((r) => r.code)
      : [],
    engineVersion: str(raw.engineVersion),
    humanReviewRecommended: raw.humanReviewRecommended === true,
    reviewed: raw.reviewed === true,
    reviewDecision: str(raw.reviewDecision),
    reviewNote: str(raw.reviewNote),
  }));
}

/** PUT /api/admin/triage/{id}/review {notes?, decision?}. The score itself is not changed; who/when/what is stored beside it. */
export async function reviewTriage(requestId: number, notes: string, decision: TriageDecision = 'REVIEWED'): Promise<void> {
  await apiClient.put(ADMIN_PATHS.triageReview(requestId), { notes: notes.trim() || undefined, decision });
}
