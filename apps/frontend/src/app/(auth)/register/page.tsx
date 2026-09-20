'use client';

import { useState, useEffect, Suspense } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Mail, Lock, Phone, Eye, EyeOff, User as UserIcon, CreditCard, CheckCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { User } from '@/types';

import { GoogleLogin } from '@react-oauth/google';

interface AuthData {
  userId: number;
  email: string;
  phoneNumber?: string;
  role: string;
  accessToken: string;
}

interface GoogleVerifyResponse {
  requiresRegistration?: boolean;
  authResponse?: AuthData;
}

function RegisterForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t } = useI18n();
  const login = useAuthStore((s) => s.login);

  const roleOptions = [
    { value: 'DONOR', label: t('auth.roleDonor') },
    { value: 'REQUESTER', label: t('auth.roleRequester') },
    { value: 'BLOOD_BANK_REQUESTER', label: 'Blood Bank' },
  ];

  const roleParam = searchParams.get('role');
  const initialRole = roleParam === 'REQUESTER' ? 'REQUESTER' : 'DONOR';

  const [step, setStep] = useState<1 | 2>(1); // 1: Info, 2: OTP
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: '',
    phoneNumber: '',
    nicNumber: '',
    role: initialRole,
    otpCode: '',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState('');

  // Google OAuth registration state
  const [needsGoogleReg, setNeedsGoogleReg] = useState(false);
  const [googleToken, setGoogleToken] = useState('');
  const [googleRegData, setGoogleRegData] = useState({
    firstName: '',
    lastName: '',
    phoneNumber: '',
    nicNumber: '',
    role: initialRole,
    otpCode: '',
  });

  const validateInfo = (isGoogle = false) => {
    const newErrors: Record<string, string> = {};
    const data = isGoogle ? googleRegData : form;
    
    if (!data.firstName) newErrors.firstName = 'First name is required';
    if (!data.lastName) newErrors.lastName = 'Last name is required';
    if (!data.phoneNumber) newErrors.phoneNumber = 'Phone number is required';
    else if (!/^(\+94|0)\d{9}$/.test(data.phoneNumber)) newErrors.phoneNumber = 'Enter a valid number: 07XXXXXXXX or +94XXXXXXXXX';
    
    if (data.nicNumber && !/^[0-9]{9}[vVxX]$/.test(data.nicNumber) && !/^[0-9]{12}$/.test(data.nicNumber)) {
      newErrors.nicNumber = 'Enter a valid NIC format';
    }

    if (!isGoogle) {
      if (!form.email) newErrors.email = 'Email is required';
      else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) newErrors.email = 'Enter a valid email';
      if (!form.password) newErrors.password = 'Password is required';
      else if (form.password.length < 8) newErrors.password = 'Password must be at least 8 characters';
      if (form.password !== form.confirmPassword) newErrors.confirmPassword = 'Passwords do not match';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (field: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: '' }));
  };

  const handleGoogleChange = (field: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setGoogleRegData((prev) => ({ ...prev, [field]: e.target.value }));
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: '' }));
  };

  const handleAuthSuccess = (authData: AuthData) => {
    const user: User = {
      id: authData.userId,
      email: authData.email,
      phoneNumber: authData.phoneNumber || '',
      role: authData.role as User['role'],
    };

    login(authData.accessToken, user);

    if (user.role === 'DONOR') {
      router.push('/dashboard/donor');
    } else if (user.role === 'ADMIN') {
      router.push('/dashboard/admin');
    } else if (user.role === 'HOSPITAL_REQUESTER') {
      router.push('/dashboard/hospital');
    } else if (user.role === 'BLOOD_BANK_REQUESTER') {
      router.push('/dashboard/blood-bank');
    } else {
      router.push('/dashboard');
    }
  };

  const requestOtp = async (isGoogle = false) => {
    if (!validateInfo(isGoogle)) return;
    setServerError('');
    setLoading(true);
    try {
      await apiClient.post('/auth/send-otp', {
        phoneNumber: isGoogle ? googleRegData.phoneNumber : form.phoneNumber,
      });
      setStep(2);
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to send OTP';
      setServerError(message);
    } finally {
      setLoading(false);
    }
  };

  const verifyOtpAndRegister = async (isGoogle = false) => {
    const otp = isGoogle ? googleRegData.otpCode : form.otpCode;
    if (!otp) {
      setErrors({ otpCode: 'OTP is required' });
      return;
    }

    setServerError('');
    setLoading(true);

    try {
      // First verify OTP
      await apiClient.post('/auth/verify-otp', {
        phoneNumber: isGoogle ? googleRegData.phoneNumber : form.phoneNumber,
        otpCode: otp,
      });

      // Then register
      if (isGoogle) {
        const res = await apiClient.post<AuthData>('/auth/google/register', {
          idToken: googleToken,
          firstName: googleRegData.firstName,
          lastName: googleRegData.lastName,
          phoneNumber: googleRegData.phoneNumber,
          nicNumber: googleRegData.nicNumber,
          role: googleRegData.role,
          otpCode: otp,
        });
        const authData = (res.data as unknown as { data?: AuthData }).data ?? res.data;
        handleAuthSuccess(authData);
      } else {
        const res = await apiClient.post<AuthData>('/auth/register', {
          firstName: form.firstName,
          lastName: form.lastName,
          email: form.email,
          password: form.password,
          phoneNumber: form.phoneNumber,
          nicNumber: form.nicNumber,
          role: form.role,
          otpCode: otp,
        });
        const authData = (res.data as unknown as { data?: AuthData }).data ?? res.data;
        handleAuthSuccess(authData);
      }
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Registration failed';
      setServerError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">{t('auth.registerTitle')}</h2>
        <p className="text-gray-400 mt-2">{t('auth.registerSubtitle')}</p>
      </div>

      {serverError && (
        <div className="mb-5 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-400">
          {serverError}
        </div>
      )}

      {needsGoogleReg ? (
        <div className="space-y-4">
          <p className="text-gray-300 text-sm mb-4">Complete your profile to finish registration</p>
          {step === 1 ? (
            <>
              <Select
                id="googleRole"
                label={t('auth.roleLabel')}
                options={roleOptions}
                value={googleRegData.role}
                onChange={handleGoogleChange('role')}
              />
              <div className="grid grid-cols-2 gap-4">
                <Input
                  id="googleFirstName"
                  label="First Name"
                  placeholder="John"
                  value={googleRegData.firstName}
                  onChange={handleGoogleChange('firstName')}
                  error={errors.firstName}
                  icon={<UserIcon className="w-4 h-4" />}
                />
                <Input
                  id="googleLastName"
                  label="Last Name"
                  placeholder="Doe"
                  value={googleRegData.lastName}
                  onChange={handleGoogleChange('lastName')}
                  error={errors.lastName}
                  icon={<UserIcon className="w-4 h-4" />}
                />
              </div>
              <Input
                id="googlePhoneNumber"
                label={t('auth.phone')}
                placeholder="+94XXXXXXXXX"
                value={googleRegData.phoneNumber}
                onChange={handleGoogleChange('phoneNumber')}
                error={errors.phoneNumber}
                icon={<Phone className="w-4 h-4" />}
              />
              <Input
                id="googleNicNumber"
                label="NIC Number (Optional)"
                placeholder="1995XXXXXXXX or XXXXXXXXXV"
                value={googleRegData.nicNumber}
                onChange={handleGoogleChange('nicNumber')}
                error={errors.nicNumber}
                icon={<CreditCard className="w-4 h-4" />}
              />
              <Button type="button" size="lg" className="w-full mt-4" loading={loading} onClick={() => requestOtp(true)}>
                Send OTP
              </Button>
            </>
          ) : (
            <>
              <Input
                id="googleOtpCode"
                label="Enter OTP"
                placeholder="123456"
                value={googleRegData.otpCode}
                onChange={handleGoogleChange('otpCode')}
                error={errors.otpCode}
                icon={<CheckCircle className="w-4 h-4" />}
              />
              <Button type="button" size="lg" className="w-full mt-4" loading={loading} onClick={() => verifyOtpAndRegister(true)}>
                Verify & Register
              </Button>
              <button type="button" onClick={() => setStep(1)} className="text-sm text-gray-400 mt-2 block text-center w-full hover:text-white transition-colors">
                Back to info
              </button>
            </>
          )}
        </div>
      ) : (
        <>
          <div className="space-y-4">
            {step === 1 ? (
              <>
                <Select
                  id="role"
                  label={t('auth.roleLabel')}
                  options={roleOptions}
                  value={form.role}
                  onChange={handleChange('role')}
                />
                <div className="grid grid-cols-2 gap-4">
                  <Input
                    id="reg-first-name"
                    label="First Name"
                    placeholder="John"
                    value={form.firstName}
                    onChange={handleChange('firstName')}
                    error={errors.firstName}
                    icon={<UserIcon className="w-4 h-4" />}
                  />
                  <Input
                    id="reg-last-name"
                    label="Last Name"
                    placeholder="Doe"
                    value={form.lastName}
                    onChange={handleChange('lastName')}
                    error={errors.lastName}
                    icon={<UserIcon className="w-4 h-4" />}
                  />
                </div>
                <Input
                  id="reg-nic"
                  label="NIC Number"
                  placeholder="1995XXXXXXXX or XXXXXXXXXV"
                  value={form.nicNumber}
                  onChange={handleChange('nicNumber')}
                  error={errors.nicNumber}
                  icon={<CreditCard className="w-4 h-4" />}
                />
                <Input
                  id="reg-email"
                  type="email"
                  label={t('auth.email')}
                  placeholder="you@example.com"
                  value={form.email}
                  onChange={handleChange('email')}
                  error={errors.email}
                  icon={<Mail className="w-4 h-4" />}
                  autoComplete="email"
                />

                <Input
                  id="reg-phone"
                  type="tel"
                  label={t('auth.phone')}
                  placeholder="07XXXXXXXX or +94XXXXXXXXX"
                  value={form.phoneNumber}
                  onChange={handleChange('phoneNumber')}
                  error={errors.phoneNumber}
                  icon={<Phone className="w-4 h-4" />}
                  autoComplete="tel"
                />

                <div className="flex flex-col gap-1.5">
                  <label htmlFor="reg-password" className="text-sm font-medium text-gray-300">
                    {t('auth.password')}
                  </label>
                  <div className="relative">
                    <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-gray-500">
                      <Lock className="w-4 h-4" />
                    </div>
                    <input
                      id="reg-password"
                      type={showPassword ? 'text' : 'password'}
                      value={form.password}
                      onChange={handleChange('password')}
                      placeholder="••••••••"
                      autoComplete="new-password"
                      className={`w-full rounded-xl border bg-white/5 pl-10 pr-11 py-3 text-sm text-white placeholder-gray-500 backdrop-blur-sm transition-all duration-200 focus:outline-none focus:ring-2 ${errors.password ? 'border-red-500/50 focus:border-red-500 focus:ring-red-500/30' : 'border-white/10 focus:border-red-500/60 focus:ring-red-500/20'}`}
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-gray-500 hover:text-gray-300 transition-colors"
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                  {errors.password && <p className="text-xs text-red-400">{errors.password}</p>}
                </div>

                <Input
                  id="reg-confirm-password"
                  type="password"
                  label={t('auth.confirmPassword')}
                  placeholder="••••••••"
                  value={form.confirmPassword}
                  onChange={handleChange('confirmPassword')}
                  error={errors.confirmPassword}
                  icon={<Lock className="w-4 h-4" />}
                  autoComplete="new-password"
                />

                <div className="pt-2">
                  <Button type="button" size="lg" className="w-full" loading={loading} onClick={() => requestOtp(false)}>
                    Send OTP to Verify Phone
                  </Button>
                </div>
              </>
            ) : (
              <>
                <Input
                  id="reg-otp"
                  label="Enter OTP"
                  placeholder="123456"
                  value={form.otpCode}
                  onChange={handleChange('otpCode')}
                  error={errors.otpCode}
                  icon={<CheckCircle className="w-4 h-4" />}
                />
                <div className="pt-2">
                  <Button type="button" size="lg" className="w-full" loading={loading} onClick={() => verifyOtpAndRegister(false)}>
                    Verify & Create Account
                  </Button>
                </div>
                <button type="button" onClick={() => setStep(1)} className="text-sm text-gray-400 mt-2 block text-center w-full hover:text-white transition-colors">
                  Back to info
                </button>
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

          <div className="mt-6 flex items-center justify-center">
            <div className="h-px w-full bg-white/10"></div>
            <span className="px-4 text-sm text-gray-500">or</span>
            <div className="h-px w-full bg-white/10"></div>
          </div>

          <div className="mt-6 flex justify-center">
            <GoogleLogin
              onSuccess={async (credentialResponse) => {
                if (credentialResponse.credential) {
                  try {
                    setServerError('');
                    setLoading(true);
                    const res = await apiClient.post<GoogleVerifyResponse>('/auth/google/verify', {
                      idToken: credentialResponse.credential,
                    });
                    
                    const verifyData = (res.data as unknown as { data?: GoogleVerifyResponse }).data ?? res.data;
                    if (verifyData.requiresRegistration) {
                      setGoogleToken(credentialResponse.credential);
                      setNeedsGoogleReg(true);
                      setStep(1);
                    } else if (verifyData.authResponse) {
                      handleAuthSuccess(verifyData.authResponse);
                    }
                  } catch (err: unknown) {
                    const message =
                      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
                      t('authGoogle.googleRegFailed');
                    setServerError(message);
                  } finally {
                    setLoading(false);
                  }
                }
              }}
              onError={() => {
                setServerError(t('authGoogle.googleRegFailed'));
              }}
            />
          </div>

          <div className="mt-6 text-center">
            <p className="text-sm text-gray-500">
              {t('auth.haveAccount')}{' '}
              <Link href="/login" className="font-medium text-red-400 hover:text-red-300 transition-colors">
                {t('auth.signInLink')}
              </Link>
            </p>
          </div>
        </>
      )}
    </div>
  );
}

export default function RegisterPage() {
  return (
    <Suspense fallback={<div className="text-gray-400 text-sm">Loading...</div>}>
      <RegisterForm />
    </Suspense>
  );
}
