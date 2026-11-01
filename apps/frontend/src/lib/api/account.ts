import apiClient from '@/lib/apiClient';
import { toUser, type AuthData } from '@/lib/auth';
import type { User } from '@/types';
import { asDate, str, unwrap } from './common';

/** The signed-in person's own account (docs/API-CONTRACT.md section 2). */
export const ACCOUNT_PATHS = {
  me: '/users/me',
  changePassword: '/users/me/change-password',
  consent: '/users/me/consent',
  sendOtp: '/users/me/send-otp',
  exportData: '/users/me/export',
  sendPhoneOtp: '/auth/send-otp',
  verifyPhoneOtp: '/auth/verify-otp',
  resendVerification: '/auth/resend-verification',
} as const;

export type Language = 'en' | 'si' | 'ta';
export const LANGUAGES: Language[] = ['en', 'si', 'ta'];
export const isLanguage = (v: unknown): v is Language => v === 'en' || v === 'si' || v === 'ta';

/** GET /api/users/me (MeDto) */
export interface Me {
  id: number;
  email: string;
  emailVerified: boolean;
  phoneNumber: string;
  firstName: string;
  lastName: string;
  role: User['role'];
  verificationStatus: User['verificationStatus'];
  /** The administrator's reason when verification was rejected. */
  verificationReason: string | null;
  status: string;
  preferredLanguage: Language;
  authProvider: 'LOCAL' | 'GOOGLE' | string;
  /** False for accounts that only ever signed in with Google: no password to change or to confirm a deletion with. */
  hasPassword: boolean;
  googleLinked: boolean;
  consentVersion: string | null;
  consentedAt: Date | null;
  /** True = the terms changed since the person agreed: they must agree again. */
  consentRequired: boolean;
  createdAt: Date | null;
}

export function toMe(raw: Record<string, unknown>): Me {
  const lang = String(raw.preferredLanguage ?? 'en');
  return {
    id: Number(raw.id),
    email: String(raw.email ?? ''),
    emailVerified: raw.emailVerified === true,
    phoneNumber: String(raw.phoneNumber ?? ''),
    firstName: String(raw.firstName ?? ''),
    lastName: String(raw.lastName ?? ''),
    role: String(raw.role ?? 'DONOR') as User['role'],
    verificationStatus: (str(raw.verificationStatus) as User['verificationStatus']) ?? undefined,
    verificationReason: str(raw.verificationReason) ?? null,
    status: String(raw.status ?? 'ACTIVE'),
    preferredLanguage: isLanguage(lang) ? lang : 'en',
    authProvider: String(raw.authProvider ?? 'LOCAL'),
    hasPassword: raw.hasPassword === true,
    googleLinked: raw.googleLinked === true,
    consentVersion: str(raw.consentVersion) ?? null,
    consentedAt: asDate(raw.consentedAt),
    consentRequired: raw.consentRequired === true,
    createdAt: asDate(raw.createdAt),
  };
}

export async function fetchMe(): Promise<Me> {
  const res = await apiClient.get(ACCOUNT_PATHS.me);
  return toMe((unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>);
}

/** PATCH /api/users/me with only the fields that changed. A new phone number must have been verified with a code first. */
export async function updateMe(patch: { firstName?: string; lastName?: string; preferredLanguage?: Language; phoneNumber?: string }): Promise<Me> {
  const res = await apiClient.patch(ACCOUNT_PATHS.me, patch);
  return toMe((unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>);
}

/** What the session store keeps of a MeDto. */
export function userFromMe(me: Me): Partial<User> {
  return { firstName: me.firstName, lastName: me.lastName, phoneNumber: me.phoneNumber, emailVerified: me.emailVerified, preferredLanguage: me.preferredLanguage, verificationStatus: me.verificationStatus };
}

/** POST /api/users/me/change-password -> a new session (AuthResponse): the old refresh token stops working. */
export async function changePassword(currentPassword: string, newPassword: string): Promise<{ accessToken: string; user: User }> {
  const res = await apiClient.post(ACCOUNT_PATHS.changePassword, { currentPassword, newPassword });
  const auth = unwrap<AuthData>(res.data);
  return { accessToken: auth.accessToken, user: toUser(auth) };
}

/** POST /api/users/me/consent: records the current terms version. */
export async function recordConsent(): Promise<Me> {
  const res = await apiClient.post(ACCOUNT_PATHS.consent);
  return toMe((unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>);
}

/** Phone change step 1: text a code to the NEW number (the public OTP endpoint). */
export async function sendPhoneCode(phoneNumber: string): Promise<void> {
  await apiClient.post(ACCOUNT_PATHS.sendPhoneOtp, { phoneNumber });
}

/** Phone change step 2: verify the code; then updateMe({ phoneNumber }) is accepted. */
export async function verifyPhoneCode(phoneNumber: string, otpCode: string): Promise<void> {
  await apiClient.post(ACCOUNT_PATHS.verifyPhoneOtp, { phoneNumber, otpCode });
}

/** POST /api/users/me/send-otp: a code to the account's own phone, to confirm a deletion without a password. */
export async function sendDeletionCode(): Promise<void> {
  await apiClient.post(ACCOUNT_PATHS.sendOtp);
}

/** DELETE /api/users/me with {password} or {otpCode}. 400 privacy.delete.confirmationRequired / confirmationInvalid, 403 privacy.delete.adminForbidden. */
export async function deleteAccount(confirmation: { password: string } | { otpCode: string }): Promise<void> {
  await apiClient.delete(ACCOUNT_PATHS.me, { data: confirmation });
}

/** POST /api/auth/resend-verification (signed in; no body). 409 alreadyVerified, 429 cooldown. */
export async function resendVerificationEmail(): Promise<void> {
  await apiClient.post(ACCOUNT_PATHS.resendVerification);
}

/**
 * GET /api/users/me/export: a raw JSON document with all personal data (not wrapped in the envelope).
 * Returns the text so the page can offer it as a file download.
 */
export async function exportMyData(): Promise<{ filename: string; content: string }> {
  const res = await apiClient.get(ACCOUNT_PATHS.exportData, { responseType: 'text', transformResponse: (data) => data });
  const content = typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2);
  let pretty = content;
  try {
    pretty = JSON.stringify(JSON.parse(content), null, 2);
  } catch {
    // keep the text exactly as the server sent it
  }
  return { filename: 'suwa-sarana-my-data.json', content: pretty };
}

/** Starts a browser download of text content. */
export function downloadText(filename: string, content: string, type = 'application/json') {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
