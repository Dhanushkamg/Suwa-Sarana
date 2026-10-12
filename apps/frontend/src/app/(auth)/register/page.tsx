'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Mail, Lock, Phone, Eye, EyeOff, User as UserIcon, CreditCard, CheckCircle } from 'lucide-react';
import { GoogleLogin } from '@react-oauth/google';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { AuthData, apiMessage, homeForRole, isValidPhone, normalizePhone, toUser, unwrap } from '@/lib/auth';
import { GOOGLE_TOKEN_KEY } from '../login/googleToken';

interface GoogleVerifyResponse {
  requiresRegistration?: boolean;
  authResponse?: AuthData;
}

const RESEND_SECONDS = 60;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const NIC_RE = /^([0-9]{9}[vVxX]|[0-9]{12})$/;

type Form = {
  role: string;
  firstName: string;
  lastName: string;
  nicNumber: string;
  email: string;
  phoneNumber: string;
  password: string;
  confirmPassword: string;
  otpCode: string;
};

function RegisterForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t } = useI18n();
  const login = useAuthStore((s) => s.login);

  const roleParam = searchParams.get('role');
  const [form, setForm] = useState<Form>({
    role: ['REQUESTER', 'HOSPITAL_REQUESTER', 'BLOOD_BANK_REQUESTER'].includes(roleParam ?? '') ? (roleParam as string) : 'DONOR',
    firstName: '',
    lastName: '',
    nicNumber: '',
    email: '',
    phoneNumber: '',
    password: '',
    confirmPassword: '',
    otpCode: '',
  });
  const [step, setStep] = useState<'info' | 'otp'>('info');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState('');
  const [resendIn, setResendIn] = useState(0);

  // Set when a new Google user was sent here from the sign-in page: no email/password needed, the token proves the email.
  const [googleToken, setGoogleToken] = useState('');
  const isGoogle = googleToken !== '';

  useEffect(() => {
    if (searchParams.get('google') !== '1') return;
    try {
      setGoogleToken(sessionStorage.getItem(GOOGLE_TOKEN_KEY) ?? '');
    } catch {
      // sessionStorage unavailable — fall back to the normal form
    }
  }, [searchParams]);

  useEffect(() => {
    if (resendIn <= 0) return;
    const timer = setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

  const roleOptions = [
    { value: 'DONOR', label: t('auth.roleDonor') },
    { value: 'REQUESTER', label: t('auth.roleRequester') },
    { value: 'HOSPITAL_REQUESTER', label: t('authFlow.roleHospital') },
    { value: 'BLOOD_BANK_REQUESTER', label: t('authFlow.roleBloodBank') },
  ];

  const set = (field: keyof Form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: '' }));
  };

  const validateInfo = () => {
    const next: Record<string, string> = {};
    if (!form.firstName.trim()) next.firstName = t('authFlow.firstNameRequired');
    if (!form.lastName.trim()) next.lastName = t('authFlow.lastNameRequired');
    if (!form.phoneNumber) next.phoneNumber = t('authFlow.phoneRequired');
    else if (!isValidPhone(form.phoneNumber)) next.phoneNumber = t('authFlow.phoneInvalid');
    if (form.nicNumber && !NIC_RE.test(form.nicNumber)) next.nicNumber = t('authFlow.nicInvalid');

    if (!isGoogle) {
      if (!form.email) next.email = t('authFlow.emailRequired');
      else if (!EMAIL_RE.test(form.email)) next.email = t('authFlow.emailInvalid');
      if (!form.password) next.password = t('authFlow.passwordRequired');
      else if (form.password.length < 8) next.password = t('authFlow.passwordShort');
      if (form.password !== form.confirmPassword) next.confirmPassword = t('authFlow.passwordMismatch');
    }

    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const sendOtp = async () => {
    setServerError('');
    setLoading(true);
    try {
      await apiClient.post('/auth/send-otp', { phoneNumber: normalizePhone(form.phoneNumber) });
      setStep('otp');
      setResendIn(RESEND_SECONDS);
    } catch (err: unknown) {
      setServerError(apiMessage(err, t('authFlow.otpSendFailed')));
    } finally {
      setLoading(false);
    }
  };

  const handleContinue = () => {
    if (validateInfo()) sendOtp();
  };

  const handleVerifyAndRegister = async () => {
    if (!form.otpCode.trim()) {
      setErrors({ otpCode: t('authFlow.otpRequired') });
      return;
    }
    setServerError('');
    setLoading(true);

    const phoneNumber = normalizePhone(form.phoneNumber);
    try {
      await apiClient.post('/auth/verify-otp', { phoneNumber, otpCode: form.otpCode.trim() });

      const common = {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        phoneNumber,
        nicNumber: form.nicNumber || undefined,
        role: form.role,
        otpCode: form.otpCode.trim(),
      };
      const res = isGoogle
        ? await apiClient.post('/auth/google/register', { ...common, idToken: googleToken })
        : await apiClient.post('/auth/register', { ...common, email: form.email, password: form.password });

      if (isGoogle) sessionStorage.removeItem(GOOGLE_TOKEN_KEY);
      const auth = unwrap<AuthData>(res.data);
      const user = toUser(auth);
      login(auth.accessToken, user);
      router.push(homeForRole(user.role));
    } catch (err: unknown) {
      setServerError(apiMessage(err, t('authFlow.registerFailed')));
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleSignIn = async (idToken: string) => {
    setServerError('');
    setLoading(true);
    try {
      const res = await apiClient.post('/auth/google/verify', { idToken });
      const data = unwrap<GoogleVerifyResponse>(res.data);
      if (data.requiresRegistration) {
        setGoogleToken(idToken);
        setStep('info');
      } else if (data.authResponse) {
        const user = toUser(data.authResponse);
        login(data.authResponse.accessToken, user);
        router.push(homeForRole(user.role));
      }
    } catch (err: unknown) {
      setServerError(apiMessage(err, t('authGoogle.googleRegFailed')));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">{t('auth.registerTitle')}</h2>
        <p className="text-gray-400 mt-2">{isGoogle ? t('authGoogle.completeProfile') : t('auth.registerSubtitle')}</p>
      </div>

      {serverError && (
        <div role="alert" className="mb-5 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-400">
          {serverError}
        </div>
      )}

      <div className="space-y-4">
        {step === 'info' ? (
          <>
            <Select id="reg-role" label={t('auth.roleLabel')} options={roleOptions} value={form.role} onChange={set('role')} />

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input
                id="reg-first-name"
                label={t('authFlow.firstName')}
                value={form.firstName}
                onChange={set('firstName')}
                error={errors.firstName}
                icon={<UserIcon className="w-4 h-4" />}
                autoComplete="given-name"
              />
              <Input
                id="reg-last-name"
                label={t('authFlow.lastName')}
                value={form.lastName}
                onChange={set('lastName')}
                error={errors.lastName}
                icon={<UserIcon className="w-4 h-4" />}
                autoComplete="family-name"
              />
            </div>

            {!isGoogle && (
              <Input
                id="reg-email"
                type="email"
                label={t('auth.email')}
                placeholder="you@example.com"
                value={form.email}
                onChange={set('email')}
                error={errors.email}
                icon={<Mail className="w-4 h-4" />}
                autoComplete="email"
              />
            )}

            <Input
              id="reg-phone"
              type="tel"
              label={t('auth.phone')}
              placeholder="07XXXXXXXX"
              value={form.phoneNumber}
              onChange={set('phoneNumber')}
              error={errors.phoneNumber}
              icon={<Phone className="w-4 h-4" />}
              autoComplete="tel"
            />

            <Input
              id="reg-nic"
              label={t('authFlow.nicOptional')}
              placeholder="1995XXXXXXXX / XXXXXXXXXV"
              value={form.nicNumber}
              onChange={set('nicNumber')}
              error={errors.nicNumber}
              icon={<CreditCard className="w-4 h-4" />}
            />

            {!isGoogle && (
              <>
                <div className="relative">
                  <Input
                    id="reg-password"
                    type={showPassword ? 'text' : 'password'}
                    label={t('auth.password')}
                    placeholder="••••••••"
                    value={form.password}
                    onChange={set('password')}
                    error={errors.password}
                    icon={<Lock className="w-4 h-4" />}
                    autoComplete="new-password"
                    className="pr-11"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3.5 top-[2.5rem] text-gray-500 hover:text-gray-300 transition-colors"
                    aria-label={showPassword ? t('authFlow.hidePassword') : t('authFlow.showPassword')}
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
                <Input
                  id="reg-confirm-password"
                  type={showPassword ? 'text' : 'password'}
                  label={t('auth.confirmPassword')}
                  placeholder="••••••••"
                  value={form.confirmPassword}
                  onChange={set('confirmPassword')}
                  error={errors.confirmPassword}
                  icon={<Lock className="w-4 h-4" />}
                  autoComplete="new-password"
                />
              </>
            )}

            <div className="pt-2">
              <Button type="button" size="lg" className="w-full" loading={loading} onClick={handleContinue}>
                {t('authFlow.sendOtp')}
              </Button>
            </div>
          </>
        ) : (
          <>
            <p className="text-sm text-gray-400">{t('authFlow.otpSent', { phone: normalizePhone(form.phoneNumber) })}</p>
            <Input
              id="reg-otp"
              label={t('authFlow.otpLabel')}
              placeholder="123456"
              inputMode="numeric"
              maxLength={6}
              autoComplete="one-time-code"
              value={form.otpCode}
              onChange={set('otpCode')}
              error={errors.otpCode}
              icon={<CheckCircle className="w-4 h-4" />}
            />
            <div className="pt-2">
              <Button type="button" size="lg" className="w-full" loading={loading} onClick={handleVerifyAndRegister}>
                {t('authFlow.verifyCreate')}
              </Button>
            </div>
            <div className="flex items-center justify-between text-sm">
              <button type="button" onClick={() => setStep('info')} className="text-gray-400 hover:text-white transition-colors">
                {t('authFlow.backToInfo')}
              </button>
              <button
                type="button"
                onClick={sendOtp}
                disabled={resendIn > 0 || loading}
                className="text-red-400 hover:text-red-300 transition-colors disabled:cursor-not-allowed disabled:text-gray-600"
              >
                {resendIn > 0 ? t('authFlow.resendIn', { seconds: resendIn }) : t('authFlow.resend')}
              </button>
            </div>
          </>
        )}

        <p className="text-center text-xs text-gray-600 leading-relaxed mt-4">
          {t('auth.termsNote')}{' '}
          <Link href="/terms" className="text-gray-400 hover:text-white transition-colors underline underline-offset-2">
            {t('auth.termsLink')}
          </Link>{' '}
          {t('auth.andText')}{' '}
          <Link href="/privacy" className="text-gray-400 hover:text-white transition-colors underline underline-offset-2">
            {t('auth.privacyLink')}
          </Link>
          .
        </p>
      </div>

      {!isGoogle && step === 'info' && process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID && (
        <>
          <div className="mt-6 flex items-center justify-center">
            <div className="h-px w-full bg-white/10" />
            <span className="px-4 text-sm text-gray-500">{t('authFlow.or')}</span>
            <div className="h-px w-full bg-white/10" />
          </div>
          <div className="mt-6 flex justify-center">
            <GoogleLogin
              onSuccess={(credentialResponse) => {
                if (credentialResponse.credential) handleGoogleSignIn(credentialResponse.credential);
              }}
              onError={() => setServerError(t('authGoogle.googleRegFailed'))}
            />
          </div>
        </>
      )}

      <div className="mt-6 text-center">
        <p className="text-sm text-gray-500">
          {t('auth.haveAccount')}{' '}
          <Link href="/login" className="font-medium text-red-400 hover:text-red-300 transition-colors">
            {t('auth.signInLink')}
          </Link>
        </p>
      </div>
    </div>
  );
}

export default function RegisterPage() {
  return (
    <Suspense fallback={<div className="text-gray-400 text-sm">…</div>}>
      <RegisterForm />
    </Suspense>
  );
}
