/** sessionStorage key used to hand a Google ID token from the sign-in page to the register page. */
export const GOOGLE_TOKEN_KEY = 'suwa_google_token';

/** Same, for the "link my Google account to my existing account" step on the sign-in page (needs the account password). */
export const GOOGLE_LINK_TOKEN_KEY = 'suwa_google_link_token';
export const GOOGLE_LINK_EMAIL_KEY = 'suwa_google_link_email';

/** Shape of POST /api/auth/google/verify `data` (docs/API-CONTRACT.md section 1). */
export interface GoogleVerifyResult {
  requiresRegistration?: boolean;
  requiresLinking?: boolean;
  email?: string;
  authResponse?: import('@/lib/auth').AuthData;
}
