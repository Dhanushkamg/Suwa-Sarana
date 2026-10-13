'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Phone, CheckCircle2, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Alert } from '@/components/pub/Alert';
import { AuthHeading } from '@/components/pub/AuthHeading';
import { PasswordField } from '@/components/pub/PasswordField';
import { OTP_INPUT_CLASS } from '@/components/pub/otp';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { apiMessage, isValidPhone, normalizePhone } from '@/lib/auth';

const RESEND_SECONDS = 60;

/** Reset a password with a code texted to the phone number on the account. */
export default function ForgotPasswordPage() {
  const { t } = useI18n();
  const [step, setStep] = useState<'phone' | 'reset' | 'done'>('phone');
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [resendIn, setResendIn] = useState(0);

  useEffect(() => {
    if (resendIn <= 0) return;
    const timer = setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

  const sendCode = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setError('');
    if (!isValidPhone(phone)) {
      setError(t('authFlow.phoneInvalid'));
      return;
    }
    setLoading(true);
    try {
      await apiClient.post('/auth/send-otp', { phoneNumber: normalizePhone(phone) });
      setStep('reset');
      setResendIn(RESEND_SECONDS);
    } catch (err: unknown) {
      setError(apiMessage(err, t('authFlow.otpSendFailed')));
    } finally {
      setLoading(false);
    }
  };

  const resetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!otp.trim()) return setError(t('authFlow.otpRequired'));
    if (password.length < 8) return setError(t('authFlow.passwordShort'));

    setLoading(true);
    try {
      const phoneNumber = normalizePhone(phone);
      await apiClient.post('/auth/verify-otp', { phoneNumber, otpCode: otp.trim() });
      await apiClient.post('/auth/reset-password', { phoneNumber, newPassword: password });
      setStep('done');
    } catch (err: unknown) {
      setError(apiMessage(err, t('common.error')));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <AuthHeading eyebrow={t('pub.eyebrowReset')} title={t('authFlow.resetTitle')} subtitle={t('authFlow.resetSubtitle')} />

      {error && <Alert className="mb-6">{error}</Alert>}

      {step === 'phone' && (
        <form onSubmit={sendCode} className="space-y-5">
          <Input
            id="reset-phone"
            type="tel"
            label={t('auth.phone')}
            placeholder="07XXXXXXXX"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            icon={<Phone className="h-4 w-4" />}
            autoComplete="tel"
            required
          />
          <div className="pt-3">
            <Button type="submit" size="lg" className="w-full" loading={loading}>
              {t('authFlow.sendOtp')}
            </Button>
          </div>
        </form>
      )}

      {step === 'reset' && (
        <form onSubmit={resetPassword} className="space-y-5">
          <p className="rounded-xl border border-line bg-foreground/[0.03] px-4 py-3 text-sm leading-relaxed text-gray-300">
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
            onChange={(e) => setOtp(e.target.value)}
            className={OTP_INPUT_CLASS}
            required
          />
          <PasswordField
            id="reset-password"
            label={t('authFlow.newPassword')}
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
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
              className="text-ember underline-offset-4 transition-colors hover:text-foreground hover:underline disabled:cursor-not-allowed disabled:text-gray-600 disabled:no-underline"
            >
              {resendIn > 0 ? t('authFlow.resendIn', { seconds: resendIn }) : t('authFlow.resend')}
            </button>
          </div>
        </form>
      )}

      {step === 'done' && (
        <div className="space-y-6 rounded-2xl border border-line border-l-2 border-l-emerald-400 bg-emerald-400/[0.05] p-6">
          <div className="flex items-start gap-3">
            <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-400" aria-hidden="true" />
            <div>
              <h2 className="font-display text-2xl text-foreground">{t('authFlow.resetDone')}</h2>
              <p className="mt-2 text-sm leading-relaxed text-gray-300">{t('authFlow.resetDoneDesc')}</p>
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
        <p className="mt-10 border-t border-line pt-6 text-center">
          <Link href="/login" className="group inline-flex items-center gap-2 text-sm text-gray-500 transition-colors hover:text-foreground">
            <ArrowLeft className="h-3.5 w-3.5 transition-transform duration-300 group-hover:-translate-x-0.5" aria-hidden="true" />
            {t('authFlow.backToSignIn')}
          </Link>
        </p>
      )}
    </div>
  );
}
