'use client';

import { Suspense, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { ArrowLeft, Lock, Phone, User as UserIcon } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Checkbox } from '@/components/ui/Checkbox';
import { Alert } from '@/components/pub/Alert';
import { AuthHeading } from '@/components/pub/AuthHeading';
import { PasswordField } from '@/components/pub/PasswordField';
import { focusFirstInvalid, parseApiError } from '@/components/pub/apiErrors';
import { renderRich, richValues } from '@/components/pub/richMessage';
import { OTP_INPUT_CLASS } from '@/components/pub/otp';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { AuthData, homeForRole, isValidPhone, normalizePhone, toUser, unwrap } from '@/lib/auth';

const MIN_PASSWORD = 10;
const RESEND_SECONDS = 60;

/**
 * Landing page of an administrator invitation: /accept-invite?token=...&email=...
 * The person confirms their phone with a code (POST /auth/send-otp + /auth/verify-otp), then
 * POST /auth/accept-invite {token, firstName, lastName, phoneNumber, password, acceptTerms, preferredLanguage} signs them in.
 * The token travels in the request body only; `email` in the link is shown for orientation, the server decides.
 */
function AcceptInviteForm() {
  const router = useRouter();
  const { t, locale } = useI18n();
  const params = useSearchParams();
  const token = params.get('token') ?? '';
  const email = params.get('email') ?? '';
  const login = useAuthStore((s) => s.login);
  const formRef = useRef<HTMLFormElement>(null);

  const [step, setStep] = useState<'info' | 'otp'>('info');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [show, setShow] = useState(false);
  const [otp, setOtp] = useState('');
  const [accept, setAccept] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [focusTick, setFocusTick] = useState(0);
  const [resendIn, setResendIn] = useState(0);

  useEffect(() => {
    if (focusTick > 0 && formRef.current) focusFirstInvalid(formRef.current);
  }, [focusTick]);

  useEffect(() => {
    if (step === 'otp') document.getElementById('invite-otp')?.focus();
  }, [step]);

  useEffect(() => {
    if (resendIn <= 0) return;
    const timer = setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

  const fail = (next: Record<string, string>) => {
    setErrors(next);
    setFocusTick((n) => n + 1);
  };

  const validate = () => {
    const next: Record<string, string> = {};
    if (!firstName.trim()) next.firstName = t('authFlow.firstNameRequired');
    if (!lastName.trim()) next.lastName = t('authFlow.lastNameRequired');
    if (!phone.trim()) next.phoneNumber = t('authFlow.phoneRequired');
    else if (!isValidPhone(phone)) next.phoneNumber = t('authFlow.phoneInvalid');
    if (!password) next.password = t('authFlow.passwordRequired');
    else if (password.length < MIN_PASSWORD) next.password = t('authFlow.passwordShort');
    if (password !== confirm) next.confirm = t('authFlow.passwordMismatch');
    if (!accept) next.acceptTerms = t('authFlow.consentRequired');
    setErrors(next);
    if (Object.keys(next).length > 0) setFocusTick((n) => n + 1);
    return Object.keys(next).length === 0;
  };

  const sendOtp = async () => {
    setError('');
    setLoading(true);
    try {
      await apiClient.post('/auth/send-otp', { phoneNumber: normalizePhone(phone) });
      setStep('otp');
      setResendIn(RESEND_SECONDS);
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, { fallback: t('authFlow.otpSendFailed') });
      setError(parsed.message);
      if (Object.keys(parsed.fields).length > 0) fail(parsed.fields);
    } finally {
      setLoading(false);
    }
  };

  const accepted = async () => {
    if (!otp.trim()) return fail({ otpCode: t('authFlow.otpRequired') });
    setError('');
    setLoading(true);
    const phoneNumber = normalizePhone(phone);
    try {
      await apiClient.post('/auth/verify-otp', { phoneNumber, otpCode: otp.trim() });
      const res = await apiClient.post('/auth/accept-invite', {
        token,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        phoneNumber,
        password,
        acceptTerms: true,
        preferredLanguage: locale,
      });
      const auth = unwrap<AuthData>(res.data);
      const user = toUser(auth);
      login(auth.accessToken, user);
      router.push(homeForRole(user.role));
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, { fallback: t('common.error') });
      setError(parsed.message);
      const names = Object.keys(parsed.fields);
      if (names.length > 0) {
        setErrors(parsed.fields);
        if (names.some((n) => n !== 'otpCode')) setStep('info');
        setFocusTick((n) => n + 1);
      }
    } finally {
      setLoading(false);
    }
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (loading) return;
    if (step === 'info') {
      if (validate()) sendOtp();
    } else {
      accepted();
    }
  };

  const clear = (field: string) => setErrors((prev) => (prev[field] ? { ...prev, [field]: '' } : prev));

  return (
    <div>
      <AuthHeading eyebrow={t('pub.eyebrowInvite')} title={t('authFlow.invite.title')} subtitle={t('authFlow.invite.subtitle')} />

      {!token ? (
        <>
          <Alert>{t('authFlow.invite.missingToken')}</Alert>
          <p className="mt-6 text-center text-sm">
            <Link href="/login" className="inline-flex min-h-11 items-center text-primary-text underline underline-offset-4">
              {t('authFlow.backToSignIn')}
            </Link>
          </p>
        </>
      ) : (
        <>
          {email && <p className="mb-5 rounded-xl border border-line bg-surface-raised px-4 py-3 text-sm text-foreground">{t('authFlow.invite.sentTo', { email })}</p>}
          {error && <Alert className="mb-6">{error}</Alert>}

          <form ref={formRef} onSubmit={submit} noValidate className="space-y-5">
            {step === 'info' ? (
              <>
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
                  <Input
                    id="invite-first-name"
                    label={t('authFlow.firstName')}
                    value={firstName}
                    onChange={(e) => {
                      setFirstName(e.target.value);
                      clear('firstName');
                    }}
                    error={errors.firstName}
                    icon={<UserIcon className="h-4 w-4" aria-hidden="true" />}
                    autoComplete="given-name"
                  />
                  <Input
                    id="invite-last-name"
                    label={t('authFlow.lastName')}
                    value={lastName}
                    onChange={(e) => {
                      setLastName(e.target.value);
                      clear('lastName');
                    }}
                    error={errors.lastName}
                    icon={<UserIcon className="h-4 w-4" aria-hidden="true" />}
                    autoComplete="family-name"
                  />
                </div>
                <Input
                  id="invite-phone"
                  type="tel"
                  label={t('auth.phone')}
                  placeholder="07XXXXXXXX"
                  value={phone}
                  onChange={(e) => {
                    setPhone(e.target.value);
                    clear('phoneNumber');
                  }}
                  error={errors.phoneNumber}
                  icon={<Phone className="h-4 w-4" aria-hidden="true" />}
                  autoComplete="tel"
                />
                <PasswordField
                  id="invite-password"
                  label={t('auth.password')}
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    clear('password');
                  }}
                  error={errors.password}
                  hint={t('authFlow.passwordHint', { min: MIN_PASSWORD })}
                  autoComplete="new-password"
                  visible={show}
                  onVisibleChange={setShow}
                />
                <Input
                  id="invite-confirm-password"
                  type={show ? 'text' : 'password'}
                  label={t('auth.confirmPassword')}
                  placeholder="••••••••"
                  value={confirm}
                  onChange={(e) => {
                    setConfirm(e.target.value);
                    clear('confirm');
                  }}
                  error={errors.confirm}
                  icon={<Lock className="h-4 w-4" aria-hidden="true" />}
                  autoComplete="new-password"
                />
                <Checkbox
                  id="invite-accept-terms"
                  checked={accept}
                  onChange={(e) => {
                    setAccept(e.target.checked);
                    clear('acceptTerms');
                  }}
                  error={errors.acceptTerms}
                  required
                  label={renderRich(t('authFlow.consent', richValues(['terms', 'privacy'])), {
                    terms: (
                      <Link href="/terms" target="_blank" rel="noopener" className="font-medium text-primary-text underline underline-offset-4">
                        {t('auth.termsLink')}
                      </Link>
                    ),
                    privacy: (
                      <Link href="/privacy" target="_blank" rel="noopener" className="font-medium text-primary-text underline underline-offset-4">
                        {t('auth.privacyLink')}
                      </Link>
                    ),
                  })}
                />
                <div className="pt-3">
                  <Button type="submit" size="lg" className="w-full" loading={loading}>
                    {t('authFlow.sendOtp')}
                  </Button>
                </div>
              </>
            ) : (
              <>
                <p className="rounded-xl border border-line bg-surface-raised px-4 py-3 text-sm leading-relaxed text-foreground">
                  {t('authFlow.otpSent', { phone: normalizePhone(phone) })}
                </p>
                <Input
                  id="invite-otp"
                  label={t('authFlow.otpLabel')}
                  placeholder="······"
                  inputMode="numeric"
                  maxLength={6}
                  autoComplete="one-time-code"
                  value={otp}
                  onChange={(e) => {
                    setOtp(e.target.value);
                    clear('otpCode');
                  }}
                  error={errors.otpCode}
                  className={OTP_INPUT_CLASS}
                />
                <div className="pt-3">
                  <Button type="submit" size="lg" className="w-full" loading={loading}>
                    {t('authFlow.invite.create')}
                  </Button>
                </div>
                <div className="flex items-center justify-between gap-4 text-sm">
                  <button type="button" onClick={() => setStep('info')} className="group inline-flex min-h-11 items-center gap-2 text-gray-400 transition-colors hover:text-foreground">
                    <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
                    {t('authFlow.backToInfo')}
                  </button>
                  <button
                    type="button"
                    onClick={sendOtp}
                    disabled={resendIn > 0 || loading}
                    className="min-h-11 text-right text-primary-text underline-offset-4 transition-colors hover:underline disabled:cursor-not-allowed disabled:text-gray-500 disabled:no-underline"
                  >
                    {resendIn > 0 ? t('authFlow.resendIn', { seconds: resendIn }) : t('authFlow.resend')}
                  </button>
                </div>
              </>
            )}
          </form>
        </>
      )}
    </div>
  );
}

export default function AcceptInvitePage() {
  const { t } = useI18n();
  return (
    <Suspense fallback={<div role="status" className="text-sm text-gray-400">{t('a11y.loading')}</div>}>
      <AcceptInviteForm />
    </Suspense>
  );
}
