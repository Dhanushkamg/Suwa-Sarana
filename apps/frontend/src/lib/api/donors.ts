import apiClient from '@/lib/apiClient';
import { asDate, asList, num, str, unwrap } from './common';

/** Donor endpoints (relative to the API base URL). */
export const DONOR_PATHS = {
  me: '/donors/me',
  availability: '/donors/me/availability',
  matches: '/donors/me/matches',
  eligibility: '/donors/me/eligibility',
  donations: '/donors/me/donations',
} as const;

/** GET /api/donors/me/eligibility -> { eligible: boolean, nextEligibleDate?: 'YYYY-MM-DD', reasons: string[] } */
export interface Eligibility {
  eligible: boolean;
  nextEligibleDate: Date | null;
  reasons: string[];
}

export async function fetchEligibility(): Promise<Eligibility> {
  const res = await apiClient.get(DONOR_PATHS.eligibility);
  const body = (unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>;
  const reasons = Array.isArray(body.reasons) ? body.reasons.map(String) : [];
  return {
    eligible: body.eligible === true,
    nextEligibleDate: asDate(body.nextEligibleDate),
    reasons,
  };
}

/** GET /api/donors/me/donations -> [{ id, donatedAt: ISO date, location?, bloodType?, units?, component? }] */
export interface Donation {
  id: number;
  donatedAt: Date | null;
  location?: string;
  units?: number;
  component?: string;
}

export async function fetchDonations(): Promise<Donation[]> {
  const res = await apiClient.get(DONOR_PATHS.donations);
  return asList<Record<string, unknown>>(res.data)
    .map((d, i) => ({
      id: num(d.id, i),
      donatedAt: asDate(d.donatedAt ?? d.donationDate ?? d.date ?? d.createdAt),
      location: str(d.location) ?? str(d.hospitalName) ?? str(d.campName) ?? str(d.hostName),
      units: d.units == null ? undefined : num(d.units),
      component: str(d.component),
    }))
    .sort((a, b) => (b.donatedAt?.getTime() ?? 0) - (a.donatedAt?.getTime() ?? 0));
}
