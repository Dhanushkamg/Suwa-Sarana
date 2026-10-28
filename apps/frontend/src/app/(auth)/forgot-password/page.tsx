'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Phone, Mail, CheckCircle2, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Alert } from '@/components/pub/Alert';
import { AuthHeading } from '@/components/pub/AuthHeading';
import { PasswordField } from '@/components/pub/PasswordField';
import { OTP_INPUT_CLASS } from '@/components/pub/otp';
import { focusFirstInvalid, parseApiError } from '@/components/pub/apiErrors';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { isValidPhone, normalizePhone } from '@/lib/auth';

const RESEND_SECONDS = 60;
const MIN_PASSWORD = 10;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Method = 'phone' | 'email';
type Step = 'request' | 'reset' | 'done' | 'emailSent';

/**
 * Account recovery. Two routes:
 *  - phone: a code is texted to the number on the account, then the new password is set here;
 *  - email: a single-use link is mailed; it opens /reset-password?token=...
 */
export default function ForgotPasswordPage() {
  const { t } = useI18n();
  const formRef = useRef<HTMLFormElement>(null);
  const [method, setMethod] = useState<Method>('phone');
  const [step, setStep] = useState<Step>('request');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [focusTick, setFocusTick] = useState(0);
  const [resendIn, setResendIn] = useState(0);

  useEffect(() => {
    if (resendIn <= 0) return;
    const timer = setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

  useEffect(() => {
    if (focusTick > 0 && formRef.current) focusFirstInvalid(formRef.current);
  }, [focusTick]);

  const fail = (errors: Record<string, string>) => {
    setFieldErrors(errors);
    setFocusTick((n) => n + 1);
  };

  const switchMethod = (next: Method) => {
    setMethod(next);
    setStep('request');
    setError('');
    setFieldErrors({});
  };

  const sendCode = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setError('');
    setFieldErrors({});
    if (!phone.trim()) return fail({ phone: t('authFlow.phoneRequired') });
    if (!isValidPhone(phone)) return fail({ phone: t('authFlow.phoneInvalid') });
    setLoading(true);
    try {
      await apiClient.post('/auth/send-otp', { phoneNumber: normalizePhone(phone) });
      setStep('reset');
      setResendIn(RESEND_SECONDS);
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, { fallback: t('authFlow.otpSendFailed'), fieldAliases: { phoneNumber: 'phone' } });
      setError(parsed.message);
      if (parsed.fields.phone) fail({ phone: parsed.fields.phone });
    } finally {
      setLoading(false);
    }
  };

  const resetWithCode = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    const errors: Record<string, string> = {};
    if (!otp.trim()) errors.otp = t('authFlow.otpRequired');
    if (!password) errors.password = t('authFlow.passwordRequired');
    else if (password.length < MIN_PASSWORD) errors.password = t('authFlow.passwordShort');
    if (Object.keys(errors).length > 0) return fail(errors);

    setLoading(true);
    try {
      const phoneNumber = normalizePhone(phone);
      await apiClient.post('/auth/verify-otp', { phoneNumber, otpCode: otp.trim() });
      await apiClient.post('/auth/reset-password', { phoneNumber, newPassword: password });
      setStep('done');
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, {
        fallback: t('common.error'),
        fieldAliases: { otpCode: 'otp', newPassword: 'password' },
      });
      setError(parsed.message);
      if (Object.keys(parsed.fields).length > 0) fail(parsed.fields);
    } finally {
      setLoading(false);
    }
  };

  const sendLink = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    if (!email.trim()) return fail({ email: t('authFlow.emailRequired') });
    if (!EMAIL_RE.test(email.trim())) return fail({ email: t('authFlow.emailInvalid') });
    setLoading(true);
    try {
      // The API answers the same way whether or not the address has an account (no account enumeration)
      await apiClient.post('/auth/forgot-password', { email: email.trim() });
      setStep('emailSent');
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, { fallback: t('authFlow.linkSendFailed') });
      setError(parsed.message);
      if (parsed.fields.email) fail({ email: parsed.fields.email });
    } finally {
      setLoading(false);
    }
  };

  const resendLabel = resendIn > 0 ? t('authFlow.resendIn', { seconds: resendIn }) : t('authFlow.resend');

  return (
    <div>
      <AuthHeading
        eyebrow={t('pub.eyebrowReset')}
        title={t('authFlow.resetTitle')}
        subtitle={method === 'phone' ? t('authFlow.resetSubtitle') : t('authFlow.resetEmailSubtitle')}
      />

      {error && <Alert className="mb-6">{error}</Alert>}

      {method === 'phone' && step === 'request' && (
        <form ref={formRef} onSubmit={sendCode} noValidate className="space-y-5">
          <Input
            id="reset-phone"
            type="tel"
            label={t('auth.phone')}
            placeholder="07XXXXXXXX"
            value={phone}
            onChange={(e) => {
              setPhone(e.target.value);
              setFieldErrors({});
            }}
            error={fieldErrors.phone}
            icon={<Phone className="h-4 w-4" aria-hidden="true" />}
            autoComplete="tel"
          />
          <div className="pt-3">
            <Button type="submit" size="lg" className="w-full" loading={loading}>
              {t('authFlow.sendOtp')}
            </Button>
          </div>
        </form>
      )}

      {method === 'phone' && step === 'reset' && (
        <form ref={formRef} onSubmit={resetWithCode} noValidate className="space-y-5">
          <p className="rounded-xl border border-line bg-surface-raised px-4 py-3 text-sm leading-relaxed text-foreground">
            {t('authFlow.otpSent', { phone: normalizePhone(phone) })}
          </p>
          <Input
            id="reset-otp"
            label={t('authFlow.otpLabel')}
            placeholder="······"
            inputMode="numeric"
            maxLength={6}
            autoComplete="one-time-code"
            value={otp}
            onChange={(e) => {
              setOtp(e.target.value);
              setFieldErrors((f) => ({ ...f, otp: '' }));
            }}
            error={fieldErrors.otp}
            className={OTP_INPUT_CLASS}
          />
          <PasswordField
            id="reset-password"
            label={t('authFlow.newPassword')}
            placeholder="••••••••"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              setFieldErrors((f) => ({ ...f, password: '' }));
            }}
            error={fieldErrors.password}
            hint={t('authFlow.passwordHint', { min: MIN_PASSWORD })}
            autoComplete="new-password"
          />
          <div className="pt-3">
            <Button type="submit" size="lg" className="w-full" loading={loading}>
              {t('authFlow.resetButton')}
            </Button>
          </div>
          <div className="text-center text-sm">
            <button
              type="button"
              onClick={() => sendCode()}
              disabled={resendIn > 0 || loading}
              className="min-h-11 text-primary-text underline-offset-4 transition-colors hover:underline disabled:cursor-not-allowed disabled:text-gray-500 disabled:no-underline"
            >
              {resendLabel}
            </button>
          </div>
        </form>
      )}

      {method === 'email' && step === 'request' && (
        <form ref={formRef} onSubmit={sendLink} noValidate className="space-y-5">
          <Input
            id="reset-email"
            type="email"
            inputMode="email"
            label={t('auth.email')}
            placeholder="you@example.com"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              setFieldErrors({});
            }}
            error={fieldErrors.email}
            icon={<Mail className="h-4 w-4" aria-hidden="true" />}
            autoComplete="email"
            autoCapitalize="none"
            spellCheck={false}
          />
          <div className="pt-3">
            <Button type="submit" size="lg" className="w-full" loading={loading}>
              {t('authFlow.sendLink')}
            </Button>
          </div>
        </form>
      )}

      {step === 'emailSent' && (
        <div className="space-y-4 rounded-2xl border border-success/30 bg-success-soft p-6" role="status">
          <div className="flex items-start gap-3">
            <Mail className="mt-0.5 h-5 w-5 shrink-0 text-success-text" aria-hidden="true" />
            <div>
              <h2 className="font-display text-2xl text-foreground">{t('authFlow.linkSentTitle')}</h2>
              <p className="mt-2 text-sm leading-relaxed text-foreground">{t('authFlow.linkSentDesc', { email: email.trim() })}</p>
            </div>
          </div>
        </div>
      )}

      {step === 'done' && (
        <div className="space-y-6 rounded-2xl border border-success/30 bg-success-soft p-6" role="status">
          <div className="flex items-start gap-3">
            <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-success-text" aria-hidden="true" />
            <div>
              <h2 className="font-display text-2xl text-foreground">{t('authFlow.resetDone')}</h2>
              <p className="mt-2 text-sm leading-relaxed text-foreground">{t('authFlow.resetDoneDesc')}</p>
            </div>
          </div>
          <Link href="/login" className="block">
            <Button size="lg" className="w-full">
              {t('auth.signIn')}
            </Button>
          </Link>
        </div>
      )}

      {step !== 'done' && (
        <div className="mt-8 flex flex-col items-center gap-1 text-sm">
          {step === 'request' && (
            <button
              type="button"
              onClick={() => switchMethod(method === 'phone' ? 'email' : 'phone')}
              className="min-h-11 text-primary-text underline underline-offset-4"
            >
              {method === 'phone' ? t('authFlow.useEmailInstead') : t('authFlow.usePhoneInstead')}
            </button>
          )}
          <Link href="/login" className="group inline-flex min-h-11 items-center gap-2 text-gray-400 transition-colors hover:text-foreground">
            <ArrowLeft className="h-3.5 w-3.5 transition-transform duration-300 group-hover:-translate-x-0.5" aria-hidden="true" />
            {t('authFlow.backToSignIn')}
          </Link>
        </div>
      )}
    </div>
  );
}
