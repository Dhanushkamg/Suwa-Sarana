import { describe, expect, it } from 'vitest';
import { parseApiError, retryAfterOf, type Translate } from '../apiError';
import { translateIn } from '../i18n';

const en: Translate = (key, values) => translateIn('en', key, values);
const si: Translate = (key, values) => translateIn('si', key, values);
const ta: Translate = (key, values) => translateIn('ta', key, values);

/** Axios-style error carrying a REAL backend error body (shapes from docs/API-CONTRACT.md 0.1). */
const fail = (status: number, body: unknown, headers: Record<string, string> = {}) => ({ response: { status, data: body, headers } });

const envelope = (code: string, message: string, data: unknown = null) => ({
  success: false,
  message,
  code,
  data,
  timestamp: '2026-10-31T08:47:50.835Z',
  requestId: 'a86e0a75-9072-4890-849b-4e7104ab72d5',
});

// Every error code in docs/API-CONTRACT.md section 12 and error_messages_en.properties
const SERVER_CODES = [
  'validation.failed', 'request.malformed', 'auth.unauthorized', 'auth.invalid_credentials', 'auth.forbidden', 'data.conflict',
  'rate.limited', 'internal.error', 'http.not_found', 'http.method_not_allowed', 'http.not_acceptable', 'http.payload_too_large',
  'http.unsupported_media_type', 'http.error', 'common.notFound',
  'auth.email.required', 'auth.email.invalid', 'auth.phone.required', 'auth.phone.invalid', 'auth.phone.inUse', 'auth.phone.notVerified',
  'auth.password.required', 'auth.password.tooShort', 'auth.password.tooLong', 'auth.password.common', 'auth.password.sameAsIdentifier',
  'auth.password.wrong', 'auth.firstName.required', 'auth.lastName.required', 'auth.name.tooLong', 'auth.role.required', 'auth.role.notAllowed',
  'auth.nic.invalid', 'auth.nic.inUse', 'auth.consent.required', 'user.language.invalid', 'auth.otp.required', 'auth.token.required',
  'admin.reason.required', 'admin.reason.tooLong',
  'auth.register.duplicate', 'auth.credentials.invalid', 'auth.account.locked', 'auth.account.suspended', 'auth.account.deleted',
  'auth.otp.invalid', 'auth.otp.cooldown', 'auth.sms.failed', 'auth.session.expired', 'auth.session.notSignedIn', 'auth.origin.blocked',
  'auth.emailVerification.invalid', 'auth.emailVerification.cooldown', 'auth.emailVerification.alreadyVerified', 'email.verificationRequired',
  'auth.reset.tokenInvalid', 'auth.reset.notAllowed', 'auth.google.invalid', 'auth.google.unavailable', 'auth.google.linkNeedsPassword',
  'auth.google.accountMismatch', 'verification.required', 'verification.rejected', 'admin.target.protected', 'admin.target.self',
  'admin.verification.notApplicable', 'admin.verification.alreadyDecided', 'admin.status.unchanged', 'admin.role.invalid', 'auth.invite.invalid',
  'admin.invite.userExists', 'privacy.delete.confirmationRequired', 'privacy.delete.confirmationInvalid', 'privacy.delete.adminForbidden',
  'user.phone.unchanged',
  'request.requester_not_verified', 'request.not_found', 'request.not_owned', 'request.not_active', 'request.already_closed', 'request.duplicate',
  'request.limit_reached', 'request.report_already_filed', 'request.cannot_report_own', 'geo.unknown_district', 'geo.outside_sri_lanka',
  'match.not_found', 'match.already_answered', 'match.not_accepted', 'donation.already_recorded', 'donor.profile_not_found', 'donor.ineligible',
  'circle.not_found', 'circle.expired',
  'camp.not_found', 'camp.in_past', 'time.range_invalid', 'blood.group_invalid', 'slot.not_found', 'slot.full', 'slot.duplicate_booking',
  'slot.in_past', 'slot.not_bookable', 'slot.booking_overlap',
  'inventory.stock_not_found', 'inventory.insufficient_stock', 'inventory.threshold_invalid', 'inventory.date_invalid', 'notification.not_found',
  'poster.unverified_requester', 'poster.patient_name_not_allowed', 'poster.unknown_fact', 'poster.invalid_fact', 'poster.missing_fact',
];

describe('parseApiError with the real error envelope', () => {
  it('has a translation (en, si and ta) for every server error code', () => {
    const missing = SERVER_CODES.filter((c) => [en, si, ta].some((t) => t(`authFlow.errors.${c}`) === `authFlow.errors.${c}`));
    expect(missing).toEqual([]);
  });

  it('translates a dotted code and keeps the code', () => {
    const parsed = parseApiError(fail(401, envelope('auth.credentials.invalid', 'Invalid credentials')), si, { fallback: 'x' });
    expect(parsed).toMatchObject({ code: 'auth.credentials.invalid', status: 401, localised: true });
    expect(parsed.message).toBe(si('authFlow.errors.auth.credentials.invalid'));
    expect(parsed.message).not.toBe('Invalid credentials');
  });

  it('maps validation.failed field codes onto the inputs, and keeps English sentences as they are', () => {
    const body = envelope('validation.failed', 'Validation failed', {
      username: 'auth.email.required',
      password: 'auth.password.tooShort',
      acceptTerms: 'auth.consent.required',
      hospitalName: 'must not be blank',
    });
    const parsed = parseApiError(fail(400, body), en, { fallback: 'x', fieldAliases: { username: 'email' } });
    expect(parsed.fields).toEqual({
      email: 'Enter your email address.',
      password: 'The password must be at least 10 characters.',
      acceptTerms: 'You must accept the Terms of Service and Privacy Policy to continue.',
      hospitalName: 'must not be blank',
    });
    expect(parsed.message).toBe('Please correct the highlighted fields.');
  });

  it('never shows a raw code: an unknown field code falls back to the generic validation text', () => {
    const body = envelope('validation.failed', 'Validation failed', { phoneNumber: 'auth.phone.somethingNew' });
    expect(parseApiError(fail(400, body), en, { fallback: 'x' }).fields.phoneNumber).toBe('Please correct the highlighted fields.');
  });

  it('adds the wait time for the account lock-out (data.retryAfterSeconds)', () => {
    const body = envelope('auth.account.locked', 'Account temporarily locked after too many failed attempts', { retryAfterSeconds: 900 });
    const parsed = parseApiError(fail(429, body, { 'retry-after': '900' }), en, { fallback: 'x' });
    expect(parsed.retryAfterSeconds).toBe(900);
    expect(parsed.message).toBe('This account is temporarily locked after too many failed attempts. Try again in 15 minutes.');
  });

  it('shows seconds for short waits and reads the Retry-After header when the body has none', () => {
    const body = envelope('rate.limited', 'Too many requests. Please slow down and try again later.', { retryAfterSeconds: 42 });
    expect(parseApiError(fail(429, body), en, { fallback: 'x' }).message).toContain('Try again in 42 seconds.');
    expect(retryAfterOf({ data: envelope('rate.limited', 'm'), headers: { 'retry-after': '30' } })).toBe(30);
    expect(retryAfterOf({ data: envelope('rate.limited', 'm'), headers: {} })).toBeUndefined();
  });

  it('reads the code from data.code for the AI endpoints', () => {
    const body = { success: false, message: 'Urgent request posters need a verified requester', code: 'poster.unverified_requester', data: { code: 'poster.unverified_requester' } };
    expect(parseApiError(fail(422, body), en, { fallback: 'x' }).code).toBe('poster.unverified_requester');
    const onlyInData = { success: false, message: 'm', data: { code: 'poster.invalid_fact' } };
    expect(parseApiError(fail(400, onlyInData), en, { fallback: 'x' }).message).toContain('not valid');
  });

  it('falls back to the server English message for an unknown code, then to the generic text', () => {
    const unknown = envelope('inventory.insufficient_stock_v2', 'Only 40 usable unit(s) of O+ WHOLE_BLOOD in stock');
    expect(parseApiError(fail(409, unknown), en, { fallback: 'generic' })).toMatchObject({
      message: 'Only 40 usable unit(s) of O+ WHOLE_BLOOD in stock',
      localised: false,
    });
    expect(parseApiError(fail(409, { success: false }), en, { fallback: 'generic' }).message).toBe('generic');
    expect(parseApiError(fail(409, envelope('x.y', 'auth.something.else')), en, { fallback: 'generic' }).message).toBe('generic');
  });

  it('uses byStatus, the server-error text for 5xx and the network text without a response', () => {
    const unauthorised = fail(401, { success: false, message: 'Unauthorized' });
    expect(parseApiError(unauthorised, en, { fallback: 'x', byStatus: { 401: 'auth.credentials.invalid' } }).code).toBe('auth.credentials.invalid');
    expect(parseApiError(fail(500, envelope('internal.error', 'An unexpected error occurred')), en, { fallback: 'x' }).message).toContain('went wrong on our side');
    expect(parseApiError(new Error('Network Error'), en, { fallback: 'x' }).message).toContain('reach the server');
  });
});
