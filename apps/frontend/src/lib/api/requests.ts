import apiClient from '@/lib/apiClient';
import type { BloodRequest } from '@/types';
import { asDate, asList, isEndpointMissing, num, str, unwrap } from './common';

/** Request endpoints. Paths are relative to the API base URL (".../api"). */
export const REQUEST_PATHS = {
  all: '/requests',
  mine: '/requests/me',
  one: (id: number | string) => `/requests/${id}`,
  matches: (id: number | string) => `/requests/${id}/matches`,
  fulfil: (id: number | string) => `/requests/${id}/fulfil`,
  close: (id: number | string) => `/requests/${id}/close`,
} as const;

/** Admins see every request; everyone else only their own. */
export async function fetchRequests(all: boolean): Promise<BloodRequest[]> {
  const res = await apiClient.get(all ? REQUEST_PATHS.all : REQUEST_PATHS.mine);
  return asList<BloodRequest>(res.data);
}

export async function fetchRequest(id: number | string): Promise<BloodRequest | null> {
  const res = await apiClient.get(REQUEST_PATHS.one(id));
  return unwrap<BloodRequest | null>(res.data) ?? null;
}

export type MatchStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED' | 'NO_RESPONSE' | 'COMPLETED' | string;

/**
 * GET /api/requests/{id}/matches  ->  [{ id, firstName, bloodType, distanceKm, status, contactPhone? }]
 * Only the donor's first name is exposed, and `contactPhone` is present only when status === 'ACCEPTED'
 * (the UI also refuses to show it for any other status).
 */
export interface RequestMatch {
  id: number;
  firstName: string;
  bloodType: string;
  distanceKm: number | null;
  status: MatchStatus;
  contactPhone?: string;
  respondedAt: Date | null;
}

export async function fetchRequestMatches(id: number | string): Promise<RequestMatch[]> {
  const res = await apiClient.get(REQUEST_PATHS.matches(id));
  return asList<Record<string, unknown>>(res.data).map((m, i) => {
    const status = String(m.status ?? 'PENDING');
    const phone = str(m.contactPhone) ?? str(m.phoneNumber) ?? str(m.contact) ?? str(m.phone);
    const distance = m.distanceKm ?? m.distance;
    return {
      id: num(m.id ?? m.matchId, i),
      firstName: str(m.firstName) ?? str(m.donorFirstName) ?? str(m.donorName) ?? '',
      bloodType: String(m.bloodType ?? m.donorBloodType ?? ''),
      distanceKm: distance == null ? null : num(distance),
      status,
      contactPhone: status === 'ACCEPTED' ? phone : undefined,
      respondedAt: asDate(m.respondedAt),
    };
  });
}

/** POST /api/requests/{id}/fulfil: the patient received the blood. */
export async function fulfilRequest(id: number | string): Promise<void> {
  await apiClient.post(REQUEST_PATHS.fulfil(id));
}

/** POST /api/requests/{id}/close: stop the search without marking it fulfilled. */
export async function closeRequest(id: number | string): Promise<void> {
  await apiClient.post(REQUEST_PATHS.close(id));
}

export { isEndpointMissing };

/** A request is still "live" (can be fulfilled or closed) in these states. */
export const isRequestLive = (status: string) => status === 'OPEN' || status === 'ESCALATING' || status === 'MATCHED';

