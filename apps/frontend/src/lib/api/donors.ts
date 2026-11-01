import apiClient from '@/lib/apiClient';
import { asDate, asList, num, str, unwrap } from './common';

/** Donor endpoints (relative to the API base URL). Contract: docs/API-CONTRACT.md section 3. */
export const DONOR_PATHS = {
  me: '/donors/me',
  availability: '/donors/me/availability',
  matches: '/donors/me/matches',
  eligibility: '/donors/me/eligibility',
  donations: '/donors/me/donations',
} as const;

/** Weight must be strictly above this to donate (server rule; 400 validation.failed on weightKg for 50 or less). */
export const MIN_WEIGHT_KG_EXCLUSIVE = 50;

/** The reason codes DeferralService can return (docs/API-CONTRACT.md section 3). Anything else is shown with a generic text. */
export const ELIGIBILITY_REASONS = ['AGE_OUT_OF_RANGE', 'AGE_ABOVE_FIRST_TIME_LIMIT', 'UNDERWEIGHT', 'RECENT_DONATION', 'ACTIVE_DEFERRAL'] as const;
export type EligibilityReason = (typeof ELIGIBILITY_REASONS)[number];

export interface EligibilityRules {
  minAge: number | null;
  maxAge: number | null;
  maxAgeFirstTimeDonor: number | null;
  minWeightKgExclusive: number | null;
  donationIntervalMonths: number | null;
  minHaemoglobinGdLExclusive: number | null;
  source: string | null;
  /** False = the figures come from the NBTS website and the NBTS Medical Officer has not signed them off yet. */
  ownerConfirmed: boolean;
}

/** GET /api/donors/me/eligibility */
export interface Eligibility {
  eligible: boolean;
  /** Only set when waiting is the whole reason (recent donation / deferral). */
  nextEligibleDate: Date | null;
  /** Codes, e.g. RECENT_DONATION: translate with dashUi.donor.reason.<code>. */
  reasons: string[];
  evaluatedOn: Date | null;
  lastDonationDate: Date | null;
  totalDonations: number | null;
  rules: EligibilityRules | null;
  rulesConfirmedByOwner: boolean;
}

const optionalNum = (v: unknown): number | null => (v == null ? null : num(v));

export function toEligibility(body: Record<string, unknown>): Eligibility {
  const r = body.rules && typeof body.rules === 'object' ? (body.rules as Record<string, unknown>) : null;
  return {
    eligible: body.eligible === true,
    nextEligibleDate: asDate(body.nextEligibleDate),
    reasons: Array.isArray(body.reasons) ? body.reasons.map(String) : [],
    evaluatedOn: asDate(body.evaluatedOn),
    lastDonationDate: asDate(body.lastDonationDate),
    totalDonations: optionalNum(body.totalDonations),
    rules: r && {
      minAge: optionalNum(r.minAge),
      maxAge: optionalNum(r.maxAge),
      maxAgeFirstTimeDonor: optionalNum(r.maxAgeFirstTimeDonor),
      minWeightKgExclusive: optionalNum(r.minWeightKgExclusive),
      donationIntervalMonths: optionalNum(r.donationIntervalMonths),
      minHaemoglobinGdLExclusive: optionalNum(r.minHaemoglobinGdLExclusive),
      source: str(r.source) ?? null,
      ownerConfirmed: r.ownerConfirmed === true,
    },
    rulesConfirmedByOwner: body.rulesConfirmedByOwner === true || r?.ownerConfirmed === true,
  };
}

export async function fetchEligibility(): Promise<Eligibility> {
  const res = await apiClient.get(DONOR_PATHS.eligibility);
  return toEligibility((unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>);
}

/** DonationSource of the server: where the donation was recorded. */
export type DonationSource = 'MATCH' | 'SLOT_CHECKIN' | string;

/** GET /api/donors/me/donations -> [{ id, donatedOn: 'YYYY-MM-DD', source, hostName, district, bloodType, requestId }], newest first */
export interface Donation {
  id: number;
  /** Calendar day of the donation (a date, no time). */
  donatedOn: Date | null;
  source: DonationSource;
  /** Hospital, blood bank or camp that collected it. */
  hostName: string | null;
  district: string | null;
  bloodType: string | null;
  requestId: number | null;
}

export function toDonation(d: Record<string, unknown>, index = 0): Donation {
  return {
    id: num(d.id, index),
    donatedOn: asDate(d.donatedOn),
    source: String(d.source ?? ''),
    hostName: str(d.hostName) ?? null,
    district: str(d.district) ?? null,
    bloodType: str(d.bloodType) ?? null,
    requestId: optionalNum(d.requestId),
  };
}

export async function fetchDonations(limit = 100): Promise<Donation[]> {
  const res = await apiClient.get(DONOR_PATHS.donations, { params: { limit } });
  return asList<Record<string, unknown>>(res.data)
    .map(toDonation)
    .sort((a, b) => (b.donatedOn?.getTime() ?? 0) - (a.donatedOn?.getTime() ?? 0));
}
