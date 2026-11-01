import type { User } from '@/types';
import { apiErrorText } from '@/lib/apiError';
import { translateCurrent } from '@/lib/i18n';

/** Shape of the backend's AuthResponse (inside the ApiResponse envelope). */
export interface AuthData {
  userId: number;
  email: string;
  phoneNumber?: string;
  role: string;
  accessToken: string;
  verificationStatus?: User['verificationStatus'];
  firstName?: string;
  lastName?: string;
  emailVerified?: boolean;
  preferredLanguage?: string;
}

export function toUser(auth: AuthData): User {
  return {
    id: auth.userId,
    email: auth.email,
    phoneNumber: auth.phoneNumber || '',
    role: auth.role as User['role'],
    verificationStatus: auth.verificationStatus,
    firstName: auth.firstName ?? undefined,
    lastName: auth.lastName ?? undefined,
    emailVerified: auth.emailVerified,
    preferredLanguage: auth.preferredLanguage ?? undefined,
  };
}

/** Where each role lands after signing in. */
export function homeForRole(role?: User['role']): string {
  switch (role) {
    case 'DONOR':
      return '/dashboard/donor';
    case 'ADMIN':
      return '/dashboard/admin';
    case 'HOSPITAL_REQUESTER':
      return '/dashboard/hospital';
    case 'BLOOD_BANK_REQUESTER':
      return '/dashboard/blood-bank';
    default:
      return '/dashboard';
  }
}

/** Backend wraps payloads as {success, message, data}; tolerate an unwrapped payload too. */
export function unwrap<T>(body: unknown): T {
  const wrapped = body as { data?: T } | null | undefined;
  return (wrapped && 'data' in wrapped && wrapped.data !== undefined ? wrapped.data : body) as T;
}

/**
 * The user-facing message from a failed API call: the server's error `code` translated into the language on screen,
 * else the server's English message, else `fallback`. 429 / lock-out answers get the wait time appended.
 */
export function apiMessage(err: unknown, fallback: string): string {
  return apiErrorText(err, translateCurrent, fallback);
}

/** Sri Lankan numbers are accepted as 07XXXXXXXX or +947XXXXXXXX; the API wants the +94 form. */
export function normalizePhone(input: string): string {
  const digits = input.replace(/[\s-]/g, '');
  return digits.startsWith('0') ? `+94${digits.slice(1)}` : digits;
}

export const isValidPhone = (input: string) => /^\+94\d{9}$/.test(normalizePhone(input));

/** Non-secret marker that a refresh-token cookie probably exists, so we only try to restore a session when it can work. */
export const SESSION_HINT_KEY = 'suwa_session';

export function setSessionHint(on: boolean) {
  try {
    if (on) localStorage.setItem(SESSION_HINT_KEY, '1');
    else localStorage.removeItem(SESSION_HINT_KEY);
  } catch {
    // storage unavailable (private mode) — session simply won't survive a reload
  }
}

export function hasSessionHint(): boolean {
  try {
    return localStorage.getItem(SESSION_HINT_KEY) === '1';
  } catch {
    return false;
  }
}
