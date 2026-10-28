import { API_BASE_URL } from '@/lib/constants';

/** Public data of a donor-circle invite (GET /api/circle/{token}, no auth). */
export interface CircleInfo {
  inviteToken: string;
  patientBloodType: string;
  unitsNeeded: number;
  urgency: string;
  hospitalName: string;
  district: string;
  expiresAt: unknown;
  /** Java's isExpired() getter is serialised as `expired`; both spellings are accepted. */
  isExpired?: boolean;
  expired?: boolean;
  /** Present once the backend exposes the requester's verification state. Never claim "verified" without it. */
  requesterVerified?: boolean;
}

/**
 * Server-side base URL of the API. Inside a container the browser-facing NEXT_PUBLIC_API_URL (often
 * http://localhost:8080) is unreachable, so server components and OG routes prefer API_INTERNAL_URL (audit F27).
 */
export const SERVER_API_URL = process.env.API_INTERNAL_URL || API_BASE_URL;

/** Fetch an invite on the server. Returns null for unknown, expired or unreachable invites. */
export async function fetchCircleInfo(token: string, init?: RequestInit): Promise<CircleInfo | null> {
  try {
    const res = await fetch(`${SERVER_API_URL}/circle/${encodeURIComponent(token)}`, init ?? { next: { revalidate: 30 } });
    if (!res.ok) return null;
    const json = await res.json();
    return (json?.data as CircleInfo) || null;
  } catch {
    return null;
  }
}

export const isCircleExpired = (info: CircleInfo) => Boolean(info.isExpired ?? info.expired);
