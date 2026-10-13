'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Mail, Lock, Phone, User as UserIcon, CreditCard, ArrowLeft } from 'lucide-react';
import { AnimatePresence, motion, useReducedMotion } from 'motion/react';
import { GoogleLogin } from '@react-oauth/google';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Alert } from '@/components/pub/Alert';
import { AuthDivider } from '@/components/pub/AuthDivider';
import { AuthHeading } from '@/components/pub/AuthHeading';
import { PasswordField } from '@/components/pub/PasswordField';
import { StepIndicator } from '@/components/pub/StepIndicator';
import { OTP_INPUT_CLASS } from '@/components/pub/otp';
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
  const reduce = useReducedMotion();
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
    { value: 'DONOR', label: t('pub.roleDonor') },
    { value: 'REQUESTER', label: t('pub.roleRequester') },
    { value: 'HOSPITAL_REQUESTER', label: t('pub.roleHospital') },
    { value: 'BLOOD_BANK_REQUESTER', label: t('pub.roleBloodBank') },
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
      <AuthHeading
        eyebrow={t('pub.eyebrowRegister')}
        title={t('auth.registerTitle')}
        subtitle={isGoogle ? t('authGoogle.completeProfile') : t('auth.registerSubtitle')}
      />

      <StepIndicator current={step === 'info' ? 1 : 2} labels={[t('pub.stepDetails'), t('pub.stepVerify')]} />

      {serverError && <Alert className="mb-6">{serverError}</Alert>}

      <div className="space-y-5">
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={step}
            className="space-y-5"
            initial={reduce ? false : { opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            exit={reduce ? { opacity: 0 } : { opacity: 0, y: -8 }}
            transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
          >
            {step === 'info' ? (
              <>
                <Select id="reg-role" label={t('auth.roleLabel')} options={roleOptions} value={form.role} onChange={set('role')} />

                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
                  <Input
                    id="reg-first-name"
                    label={t('authFlow.firstName')}
                    value={form.firstName}
                    onChange={set('firstName')}
                    error={errors.firstName}
                    icon={<UserIcon className="h-4 w-4" />}
                    autoComplete="given-name"
                  />
                  <Input
                    id="reg-last-name"
                    label={t('authFlow.lastName')}
                    value={form.lastName}
                    onChange={set('lastName')}
                    error={errors.lastName}
                    icon={<UserIcon className="h-4 w-4" />}
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
                    icon={<Mail className="h-4 w-4" />}
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
                  icon={<Phone className="h-4 w-4" />}
                  autoComplete="tel"
                />

                <Input
                  id="reg-nic"
                  label={t('authFlow.nicOptional')}
                  placeholder="1995XXXXXXXX / XXXXXXXXXV"
                  value={form.nicNumber}
                  onChange={set('nicNumber')}
                  error={errors.nicNumber}
                  icon={<CreditCard className="h-4 w-4" />}
                />

                {!isGoogle && (
                  <>
                    <PasswordField
                      id="reg-password"
                      label={t('auth.password')}
                      placeholder="••••••••"
                      value={form.password}
                      onChange={set('password')}
                      error={errors.password}
                      autoComplete="new-password"
                      visible={showPassword}
                      onVisibleChange={setShowPassword}
                    />
                    <Input
                      id="reg-confirm-password"
                      type={showPassword ? 'text' : 'password'}
                      label={t('auth.confirmPassword')}
                      placeholder="••••••••"
                      value={form.confirmPassword}
                      onChange={set('confirmPassword')}
                      error={errors.confirmPassword}
                      icon={<Lock className="h-4 w-4" />}
                      autoComplete="new-password"
                    />
                  </>
                )}

                <div className="pt-3">
                  <Button type="button" size="lg" className="w-full" loading={loading} onClick={handleContinue}>
                    {t('authFlow.sendOtp')}
                  </Button>
                </div>
              </>
            ) : (
              <>
                <p className="rounded-xl border border-line bg-foreground/[0.03] px-4 py-3 text-sm leading-relaxed text-gray-300">
                  {t('authFlow.otpSent', { phone: normalizePhone(form.phoneNumber) })}
                </p>
                <Input
                  id="reg-otp"
                  label={t('authFlow.otpLabel')}
                  placeholder="······"
                  inputMode="numeric"
                  maxLength={6}
                  autoComplete="one-time-code"
                  value={form.otpCode}
                  onChange={set('otpCode')}
                  error={errors.otpCode}
                  className={OTP_INPUT_CLASS}
                />
                <div className="pt-3">
                  <Button type="button" size="lg" className="w-full" loading={loading} onClick={handleVerifyAndRegister}>
                    {t('authFlow.verifyCreate')}
                  </Button>
                </div>
                <div className="flex items-center justify-between gap-4 text-sm">
                  <button
                    type="button"
                    onClick={() => setStep('info')}
                    className="group inline-flex items-center gap-2 text-gray-400 transition-colors hover:text-foreground"
                  >
                    <ArrowLeft className="h-3.5 w-3.5 transition-transform duration-300 group-hover:-translate-x-0.5" aria-hidden="true" />
                    {t('authFlow.backToInfo')}
                  </button>
                  <button
                    type="button"
                    onClick={sendOtp}
                    disabled={resendIn > 0 || loading}
                    className="text-right text-ember underline-offset-4 transition-colors hover:text-foreground hover:underline disabled:cursor-not-allowed disabled:text-gray-600 disabled:no-underline"
                  >
                    {resendIn > 0 ? t('authFlow.resendIn', { seconds: resendIn }) : t('authFlow.resend')}
                  </button>
                </div>
              </>
            )}
          </motion.div>
        </AnimatePresence>

        <p className="pt-2 text-center text-xs leading-relaxed text-gray-600">
          {t('auth.termsNote')}{' '}
          <Link href="/terms" className="text-gray-400 underline underline-offset-4 transition-colors hover:text-foreground">
            {t('auth.termsLink')}
          </Link>{' '}
          {t('auth.andText')}{' '}
          <Link href="/privacy" className="text-gray-400 underline underline-offset-4 transition-colors hover:text-foreground">
            {t('auth.privacyLink')}
          </Link>
          .
        </p>
      </div>

      {!isGoogle && step === 'info' && process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID && (
        <>
          <AuthDivider label={t('authFlow.or')} />
          <div className="flex justify-center">
            <GoogleLogin
              onSuccess={(credentialResponse) => {
                if (credentialResponse.credential) handleGoogleSignIn(credentialResponse.credential);
              }}
              onError={() => setServerError(t('authGoogle.googleRegFailed'))}
            />
          </div>
        </>
      )}

      <p className="mt-10 border-t border-line pt-6 text-center text-sm text-gray-500">
        {t('auth.haveAccount')}{' '}
        <Link href="/login" className="font-medium text-ember underline-offset-4 transition-colors hover:text-foreground hover:underline">
          {t('auth.signInLink')}
        </Link>
      </p>
    </div>
  );
}

export default function RegisterPage() {
  return (
    <Suspense fallback={<div className="text-sm text-gray-400">…</div>}>
      <RegisterForm />
    </Suspense>
  );
}
