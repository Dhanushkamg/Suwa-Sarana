import apiClient from '@/lib/apiClient';
import type { BloodRequest } from '@/types';
import { asDate, asList, isEndpointMissing, num, str, unwrap } from './common';

/** Request endpoints. Paths are relative to the API base URL (".../api"). Contract: docs/API-CONTRACT.md section 4. */
export const REQUEST_PATHS = {
  all: '/requests',
  mine: '/requests/me',
  one: (id: number | string) => `/requests/${id}`,
  matches: (id: number | string) => `/requests/${id}/matches`,
  fulfil: (id: number | string) => `/requests/${id}/fulfil`,
  close: (id: number | string) => `/requests/${id}/close`,
  report: (id: number | string) => `/requests/${id}/report`,
  donated: (matchId: number | string) => `/matches/${matchId}/donated`,
} as const;

const optionalCount = (v: unknown): number | undefined => (v == null ? undefined : num(v));

/**
 * RequestResponseDto -> BloodRequest. Dates stay as the ISO strings the server sent (instants with Z).
 * `acceptedCount` and `confirmedDonations` are null for everybody except the owner/admin; `coarseLocation` = the coordinates
 * were rounded (about 1 km) because the viewer does not own the request.
 */
export function toRequest(raw: Record<string, unknown>): BloodRequest {
  return {
    ...(raw as unknown as BloodRequest),
    unitsNeeded: optionalCount(raw.unitsNeeded),
    latitude: raw.latitude == null ? undefined : num(raw.latitude),
    longitude: raw.longitude == null ? undefined : num(raw.longitude),
    currentRadiusKm: num(raw.currentRadiusKm),
    closedAt: str(raw.closedAt) ?? null,
    closeReason: str(raw.closeReason) ?? null,
    acceptedCount: optionalCount(raw.acceptedCount) ?? null,
    confirmedDonations: optionalCount(raw.confirmedDonations) ?? null,
    coarseLocation: raw.coarseLocation === true,
  };
}

/** Admins see every request; everyone else only their own. */
export async function fetchRequests(all: boolean): Promise<BloodRequest[]> {
  const res = await apiClient.get(all ? REQUEST_PATHS.all : REQUEST_PATHS.mine);
  return asList<Record<string, unknown>>(res.data).map(toRequest);
}

export async function fetchRequest(id: number | string): Promise<BloodRequest | null> {
  const res = await apiClient.get(REQUEST_PATHS.one(id));
  const body = unwrap<Record<string, unknown> | null>(res.data);
  return body ? toRequest(body) : null;
}

/** MatchStatus enum of the server: PENDING | ACCEPTED | DECLINED | NO_RESPONSE (unknown values are shown neutrally). */
export type MatchStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'NO_RESPONSE' | string;

/** One donor matched to a request. Only the first name is exposed; `contactPhone` only while ACCEPTED and no outcome is recorded yet. */
export interface RequestMatch {
  id: number;
  donorFirstName: string;
  bloodType: string;
  distanceKm: number | null;
  status: MatchStatus;
  notifiedAt: Date | null;
  respondedAt: Date | null;
  /** true / false once the owner recorded whether the donor donated; undefined while open. */
  donated?: boolean;
  contactPhone?: string;
}

export interface RequestMatches {
  requestId: number;
  unitsNeeded: number;
  /** Donors who said yes and have not been recorded as a no-show. */
  acceptedCount: number;
  /** Donations the owner confirmed. */
  confirmedDonations: number;
  matches: RequestMatch[];
}

export function toMatch(m: Record<string, unknown>, index = 0): RequestMatch {
  const status = String(m.status ?? 'PENDING');
  const contact = (m.contact ?? {}) as Record<string, unknown>;
  const phone = str(contact.phoneNumber);
  return {
    id: num(m.matchId, index),
    donorFirstName: str(m.donorFirstName) ?? '',
    bloodType: String(m.bloodType ?? ''),
    distanceKm: m.distanceKm == null ? null : num(m.distanceKm),
    status,
    notifiedAt: asDate(m.notifiedAt),
    respondedAt: asDate(m.respondedAt),
    donated: typeof m.donated === 'boolean' ? m.donated : undefined,
    // The server only sends it for ACCEPTED matches; refuse it for any other status as well
    contactPhone: status === 'ACCEPTED' && m.donated === undefined ? phone : undefined,
  };
}

/** GET /api/requests/{id}/matches -> { requestId, unitsNeeded, acceptedCount, confirmedDonations, matches: [...] } (owner or admin). */
export async function fetchRequestMatches(id: number | string): Promise<RequestMatches> {
  const res = await apiClient.get(REQUEST_PATHS.matches(id));
  const body = (unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>;
  return {
    requestId: num(body.requestId),
    unitsNeeded: num(body.unitsNeeded),
    acceptedCount: num(body.acceptedCount),
    confirmedDonations: num(body.confirmedDonations),
    matches: asList<Record<string, unknown>>(body.matches).map(toMatch),
  };
}

/** POST /api/matches/{id}/donated?donated=true|false: the owner records whether an accepted donor really donated (history, deferral, reliability). */
export async function recordDonation(matchId: number, donated: boolean): Promise<void> {
  await apiClient.post(REQUEST_PATHS.donated(matchId), null, { params: { donated } });
}

/** POST /api/requests/{id}/fulfil: the patient received the blood. 409 request.already_closed when it is not live any more. */
export async function fulfilRequest(id: number | string): Promise<void> {
  await apiClient.post(REQUEST_PATHS.fulfil(id));
}

/** CloseReason enum of the server. */
export const CLOSE_REASONS = ['NO_LONGER_NEEDED', 'FOUND_DONOR_ELSEWHERE', 'OTHER'] as const;
export type CloseReason = (typeof CLOSE_REASONS)[number];

/** POST /api/requests/{id}/close {reason?}: stop the search without marking it fulfilled. */
export async function closeRequest(id: number | string, reason?: CloseReason): Promise<void> {
  await apiClient.post(REQUEST_PATHS.close(id), reason ? { reason } : {});
}

/** ReportReason enum of the server. */
export const REPORT_REASONS = ['FAKE_OR_MISLEADING', 'DUPLICATE', 'SPAM', 'ABUSIVE_CONTENT', 'OTHER'] as const;
export type ReportReason = (typeof REPORT_REASONS)[number];

/** POST /api/requests/{id}/report {reason, details?<=500}: any signed-in user except the owner (409 request.report_already_filed). */
export async function reportRequest(id: number | string, reason: ReportReason, details?: string): Promise<void> {
  await apiClient.post(REQUEST_PATHS.report(id), { reason, ...(details?.trim() ? { details: details.trim() } : {}) });
}

export { isEndpointMissing };

/** A request is still "live" (can be fulfilled or closed) in these states. */
export const isRequestLive = (status: string) => status === 'OPEN' || status === 'ESCALATING' || status === 'MATCHED';
