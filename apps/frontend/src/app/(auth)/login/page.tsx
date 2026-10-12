'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Mail, Lock, Eye, EyeOff } from 'lucide-react';
import { GoogleLogin } from '@react-oauth/google';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { AuthData, apiMessage, homeForRole, toUser, unwrap } from '@/lib/auth';
import { GOOGLE_TOKEN_KEY } from './googleToken';

interface GoogleVerifyResponse {
  requiresRegistration?: boolean;
  authResponse?: AuthData;
}

export default function LoginPage() {
  const router = useRouter();
  const { t } = useI18n();
  const { login, initialize, isAuthenticated, user } = useAuthStore();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Someone who still has a valid session shouldn't see the sign-in form.
  useEffect(() => {
    initialize();
  }, [initialize]);

  useEffect(() => {
    if (isAuthenticated && user) router.replace(homeForRole(user.role));
  }, [isAuthenticated, user, router]);

  const handleAuthSuccess = (auth: AuthData) => {
    const signedIn = toUser(auth);
    login(auth.accessToken, signedIn);
    router.push(homeForRole(signedIn.role));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await apiClient.post('/auth/login', { username: email, email, password });
      handleAuthSuccess(unwrap<AuthData>(res.data));
    } catch (err: unknown) {
      setError(apiMessage(err, t('common.error')));
    } finally {
      setLoading(false);
    }
  };

  const handleGoogle = async (idToken: string) => {
    setError('');
    setLoading(true);
    try {
      const res = await apiClient.post('/auth/google/verify', { idToken });
      const data = unwrap<GoogleVerifyResponse>(res.data);
      if (data.requiresRegistration) {
        // New Google user: the register page collects the rest of the profile and verifies the phone.
        // The token goes through sessionStorage rather than the URL so it never lands in history or logs.
        sessionStorage.setItem(GOOGLE_TOKEN_KEY, idToken);
        router.push('/register?google=1');
      } else if (data.authResponse) {
        handleAuthSuccess(data.authResponse);
      }
    } catch (err: unknown) {
      setError(apiMessage(err, t('authGoogle.googleAuthFailed')));
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
        <div role="alert" className="mb-5 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-400">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
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
              aria-label={showPassword ? t('authFlow.hidePassword') : t('authFlow.showPassword')}
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

      {process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID && (
        <>
          <div className="mt-6 flex items-center justify-center">
            <div className="h-px w-full bg-white/10" />
            <span className="px-4 text-sm text-gray-500">{t('authFlow.or')}</span>
            <div className="h-px w-full bg-white/10" />
          </div>

          <div className="mt-6 flex justify-center">
            <GoogleLogin
              onSuccess={(credentialResponse) => {
                if (credentialResponse.credential) handleGoogle(credentialResponse.credential);
              }}
              onError={() => setError(t('authGoogle.googleAuthFailed'))}
            />
          </div>
        </>
      )}

      <div className="mt-8 text-center">
        <p className="text-sm text-gray-500">
          {t('auth.noAccount')}{' '}
          <Link href="/register" className="font-medium text-red-400 hover:text-red-300 transition-colors">
            {t('auth.registerNow')}
          </Link>
        </p>
      </div>
    </div>
  );
}
