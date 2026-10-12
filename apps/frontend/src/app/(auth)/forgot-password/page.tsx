'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Phone, CheckCircle2, CheckCircle, Lock, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
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
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">{t('authFlow.resetTitle')}</h2>
        <p className="text-gray-400 mt-2">{t('authFlow.resetSubtitle')}</p>
      </div>

      {error && (
        <div role="alert" className="mb-5 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-400">
          {error}
        </div>
      )}

      {step === 'phone' && (
        <form onSubmit={sendCode} className="space-y-4">
          <Input
            id="reset-phone"
            type="tel"
            label={t('auth.phone')}
            placeholder="07XXXXXXXX"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            icon={<Phone className="w-4 h-4" />}
            autoComplete="tel"
            required
          />
          <Button type="submit" size="lg" className="w-full" loading={loading}>
            {t('authFlow.sendOtp')}
          </Button>
        </form>
      )}

      {step === 'reset' && (
        <form onSubmit={resetPassword} className="space-y-4">
          <p className="text-sm text-gray-400">{t('authFlow.otpSent', { phone: normalizePhone(phone) })}</p>
          <Input
            id="reset-otp"
            label={t('authFlow.otpLabel')}
            placeholder="123456"
            inputMode="numeric"
            maxLength={6}
            autoComplete="one-time-code"
            value={otp}
            onChange={(e) => setOtp(e.target.value)}
            icon={<CheckCircle className="w-4 h-4" />}
            required
          />
          <Input
            id="reset-password"
            type="password"
            label={t('authFlow.newPassword')}
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            icon={<Lock className="w-4 h-4" />}
            autoComplete="new-password"
            required
          />
          <Button type="submit" size="lg" className="w-full" loading={loading}>
            {t('authFlow.resetButton')}
          </Button>
          <div className="text-center text-sm">
            <button
              type="button"
              onClick={() => sendCode()}
              disabled={resendIn > 0 || loading}
              className="text-red-400 hover:text-red-300 transition-colors disabled:cursor-not-allowed disabled:text-gray-600"
            >
              {resendIn > 0 ? t('authFlow.resendIn', { seconds: resendIn }) : t('authFlow.resend')}
            </button>
          </div>
        </form>
      )}

      {step === 'done' && (
        <div className="glass-card rounded-2xl p-6 border-emerald-500/20 bg-emerald-500/5 space-y-4">
          <div className="flex items-start gap-3 text-emerald-400">
            <CheckCircle2 className="w-6 h-6 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-semibold text-white">{t('authFlow.resetDone')}</h3>
              <p className="text-sm text-gray-300 mt-1 leading-relaxed">{t('authFlow.resetDoneDesc')}</p>
            </div>
          </div>
          <Link href="/login">
            <Button size="lg" className="w-full">
              {t('auth.signIn')}
            </Button>
          </Link>
        </div>
      )}

      {step !== 'done' && (
        <div className="mt-8 text-center">
          <Link href="/login" className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-white transition-colors">
            <ArrowLeft className="w-3.5 h-3.5" />
            {t('authFlow.backToSignIn')}
          </Link>
        </div>
      )}
    </div>
  );
}
