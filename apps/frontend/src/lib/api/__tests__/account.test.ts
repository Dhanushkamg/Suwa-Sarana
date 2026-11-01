import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() }));
vi.mock('@/lib/apiClient', () => ({ default: http }));

import { changePassword, deleteAccount, exportMyData, fetchMe, recordConsent, sendDeletionCode, sendPhoneCode, toMe, updateMe, verifyPhoneCode } from '../account';

const envelope = (data: unknown) => ({ data: { success: true, message: 'Success', data, timestamp: '2026-10-31T08:47:50.712Z' } });

/** Real MeDto (docs/API-CONTRACT.md section 2). */
const meBody = {
  id: 31, email: 'donor1@demo.suwasarana.test', emailVerified: true, phoneNumber: '+94771234567', firstName: 'Dinesh', lastName: 'Wickramasinghe',
  role: 'DONOR', verificationStatus: 'VERIFIED', verificationReason: null, status: 'ACTIVE', preferredLanguage: 'si', authProvider: 'LOCAL',
  hasPassword: true, googleLinked: false, consentVersion: '2026-10', consentedAt: '2026-10-01T05:00:00Z', consentRequired: false, createdAt: '2026-09-01T05:00:00Z',
};

beforeEach(() => vi.clearAllMocks());

describe('account (/users/me)', () => {
  it('reads MeDto', async () => {
    http.get.mockResolvedValueOnce(envelope(meBody));
    const me = await fetchMe();
    expect(http.get).toHaveBeenCalledWith('/users/me');
    expect(me).toMatchObject({ id: 31, preferredLanguage: 'si', hasPassword: true, googleLinked: false, consentRequired: false, emailVerified: true, role: 'DONOR' });
    expect(me.consentedAt?.toISOString()).toBe('2026-10-01T05:00:00.000Z');
  });

  it('an unknown language becomes English, a Google-only account has no password', () => {
    expect(toMe({ ...meBody, preferredLanguage: 'fr' }).preferredLanguage).toBe('en');
    expect(toMe({ ...meBody, hasPassword: false, authProvider: 'GOOGLE', googleLinked: true })).toMatchObject({ hasPassword: false, googleLinked: true });
  });

  it('sends only the changed fields with PATCH', async () => {
    http.patch.mockResolvedValueOnce(envelope({ ...meBody, preferredLanguage: 'ta' }));
    const me = await updateMe({ preferredLanguage: 'ta' });
    expect(http.patch).toHaveBeenCalledWith('/users/me', { preferredLanguage: 'ta' });
    expect(me.preferredLanguage).toBe('ta');
  });

  it('change of phone: the new number is verified first, then the account is patched', async () => {
    http.post.mockResolvedValue(envelope(null));
    await sendPhoneCode('+94771111111');
    expect(http.post).toHaveBeenLastCalledWith('/auth/send-otp', { phoneNumber: '+94771111111' });
    await verifyPhoneCode('+94771111111', '123456');
    expect(http.post).toHaveBeenLastCalledWith('/auth/verify-otp', { phoneNumber: '+94771111111', otpCode: '123456' });
  });

  it('change-password returns the new session', async () => {
    http.post.mockResolvedValueOnce(
      envelope({ accessToken: 'new.jwt', userId: 31, role: 'DONOR', email: 'donor1@demo.suwasarana.test', phoneNumber: '+94771234567', verificationStatus: 'VERIFIED', firstName: 'Dinesh', lastName: 'W', emailVerified: true, preferredLanguage: 'si' })
    );
    const session = await changePassword('old-Pass-12345', 'new-Pass-12345');
    expect(http.post).toHaveBeenCalledWith('/users/me/change-password', { currentPassword: 'old-Pass-12345', newPassword: 'new-Pass-12345' });
    expect(session.accessToken).toBe('new.jwt');
    expect(session.user).toMatchObject({ id: 31, firstName: 'Dinesh', emailVerified: true, preferredLanguage: 'si' });
  });

  it('records consent', async () => {
    http.post.mockResolvedValueOnce(envelope({ ...meBody, consentRequired: false }));
    expect((await recordConsent()).consentRequired).toBe(false);
    expect(http.post).toHaveBeenCalledWith('/users/me/consent');
  });

  it('deletes with a password or with an SMS code', async () => {
    http.delete.mockResolvedValue(envelope(null));
    await deleteAccount({ password: 'Demo-Pass-12345' });
    expect(http.delete).toHaveBeenLastCalledWith('/users/me', { data: { password: 'Demo-Pass-12345' } });
    http.post.mockResolvedValueOnce(envelope(null));
    await sendDeletionCode();
    expect(http.post).toHaveBeenLastCalledWith('/users/me/send-otp');
    await deleteAccount({ otpCode: '654321' });
    expect(http.delete).toHaveBeenLastCalledWith('/users/me', { data: { otpCode: '654321' } });
  });

  it('the export is a raw JSON document (not in the envelope) kept as downloadable text', async () => {
    http.get.mockResolvedValueOnce({ data: '{"user":{"id":31,"email":"donor1@demo.suwasarana.test"},"donations":[]}' });
    const file = await exportMyData();
    expect(http.get).toHaveBeenCalledWith('/users/me/export', expect.objectContaining({ responseType: 'text' }));
    expect(file.filename).toBe('suwa-sarana-my-data.json');
    expect(JSON.parse(file.content)).toEqual({ user: { id: 31, email: 'donor1@demo.suwasarana.test' }, donations: [] });
  });
});
