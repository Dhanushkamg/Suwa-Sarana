'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Mail, Lock, Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
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

export default function LoginPage() {
  const router = useRouter();
  const { t } = useI18n();
  const login = useAuthStore((s) => s.login);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Google OAuth registration state
  const [needsGoogleReg, setNeedsGoogleReg] = useState(false);
  const [googleToken, setGoogleToken] = useState('');
  const [googleRegData, setGoogleRegData] = useState({
    phoneNumber: '',
    nicNumber: '',
    role: 'DONOR',
  });

  const handleAuthSuccess = (authData: AuthData) => {
    const user: User = {
      id: authData.userId,
      email: authData.email,
      phoneNumber: authData.phoneNumber,
      role: authData.role as User['role'],
    };

    login(authData.accessToken, user);

    if (user.role === 'DONOR') {
      router.push('/dashboard/donor');
    } else if (user.role === 'ADMIN') {
      router.push('/dashboard/admin');
    } else if (user.role === 'HOSPITAL_REQUESTER') {
      router.push('/dashboard/hospital');
    } else {
      router.push('/dashboard');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await apiClient.post<AuthData>('/auth/login', {
        username: email,
        email,
        password,
      });

      const authData = (res.data as unknown as { data?: AuthData }).data ?? res.data;
      handleAuthSuccess(authData);
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        t('common.error');
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">{t('auth.loginTitle')}</h2>
        <p className="text-gray-400 mt-2">{t('auth.loginSubtitle')}</p>
      </div>

      {error && (
        <div className="mb-5 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-400">
          {error}
        </div>
      )}

      {needsGoogleReg ? (
        <form onSubmit={async (e: React.FormEvent) => {
          e.preventDefault();
          setError('');
          setLoading(true);
          try {
            const res = await apiClient.post<AuthData>('/auth/google/register', {
              idToken: googleToken,
              phoneNumber: googleRegData.phoneNumber,
              nicNumber: googleRegData.nicNumber,
              role: googleRegData.role,
            });
            const authData = (res.data as unknown as { data?: AuthData }).data ?? res.data;
            handleAuthSuccess(authData);
          } catch (err: unknown) {
            const message =
              (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
              'Registration failed';
            setError(message);
            setLoading(false);
          }
        }} className="space-y-4">
          <p className="text-gray-300 text-sm mb-4">Please complete your profile to continue.</p>
          <Input
            id="phoneNumber"
            label="Phone Number"
            placeholder="+94XXXXXXXXX"
            value={googleRegData.phoneNumber}
            onChange={(e) => setGoogleRegData({ ...googleRegData, phoneNumber: e.target.value })}
            required
          />
          <Input
            id="nicNumber"
            label="NIC Number (Optional)"
            placeholder=""
            value={googleRegData.nicNumber}
            onChange={(e) => setGoogleRegData({ ...googleRegData, nicNumber: e.target.value })}
          />
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-gray-300">Role</label>
            <select
              value={googleRegData.role}
              onChange={(e) => setGoogleRegData({ ...googleRegData, role: e.target.value })}
              className="w-full rounded-xl border border-white/10 bg-white/5 py-3 px-4 text-sm text-white placeholder-gray-500 backdrop-blur-sm transition-all duration-200 focus:border-red-500/60 focus:outline-none focus:ring-2 focus:ring-red-500/20"
            >
              <option value="DONOR">Donor</option>
              <option value="REQUESTER">Requester</option>
            </select>
          </div>
          <Button type="submit" size="lg" className="w-full mt-4" loading={loading}>
            Complete Registration
          </Button>
        </form>
      ) : (
        <>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <Input
              id="email"
              type="email"
              label={t('auth.email')}
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              icon={<Mail className="w-4 h-4" />}
              autoComplete="email"
              required
            />

            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <label htmlFor="password" className="text-sm font-medium text-gray-300">
                  {t('auth.password')}
                </label>
                <Link href="/forgot-password" className="text-xs text-red-400 hover:text-red-300 transition-colors">
                  {t('auth.forgotPassword')}
                </Link>
              </div>
              <div className="relative">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-gray-500">
                  <Lock className="w-4 h-4" />
                </div>
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  autoComplete="current-password"
                  required
                  className="w-full rounded-xl border border-white/10 bg-white/5 pl-10 pr-11 py-3 text-sm text-white placeholder-gray-500 backdrop-blur-sm transition-all duration-200 focus:border-red-500/60 focus:outline-none focus:ring-2 focus:ring-red-500/20"
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
            </div>

            <div className="pt-2">
              <Button type="submit" size="lg" className="w-full" loading={loading}>
                {t('auth.signIn')}
              </Button>
            </div>
          </form>

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
                    setError('');
                    setLoading(true);
                    const res = await apiClient.post<GoogleVerifyResponse>('/auth/google/verify', {
                      idToken: credentialResponse.credential,
                    });
                    
                    const verifyData = (res.data as unknown as { data?: GoogleVerifyResponse }).data ?? res.data;
                    if (verifyData.requiresRegistration) {
                      setGoogleToken(credentialResponse.credential);
                      setNeedsGoogleReg(true);
                    } else if (verifyData.authResponse) {
                      handleAuthSuccess(verifyData.authResponse);
                    }
                  } catch (err: unknown) {
                    const message =
                      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
                      'Google authentication failed';
                    setError(message);
                  } finally {
                    setLoading(false);
                  }
                }
              }}
              onError={() => {
                setError('Google authentication failed');
              }}
            />
          </div>

          <div className="mt-8 text-center">
            <p className="text-sm text-gray-500">
              {t('auth.noAccount')}{' '}
              <Link href="/register" className="font-medium text-red-400 hover:text-red-300 transition-colors">
                {t('auth.registerNow')}
              </Link>
            </p>
          </div>
        </>
      )}
    </div>
  );
}
