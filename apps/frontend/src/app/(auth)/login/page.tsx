'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Mail } from 'lucide-react';
import { GoogleLogin } from '@react-oauth/google';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Alert } from '@/components/pub/Alert';
import { AuthDivider } from '@/components/pub/AuthDivider';
import { AuthHeading } from '@/components/pub/AuthHeading';
import { PasswordField } from '@/components/pub/PasswordField';
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
      <AuthHeading eyebrow={t('pub.eyebrowSignIn')} title={t('auth.loginTitle')} subtitle={t('auth.loginSubtitle')} />

      {error && <Alert className="mb-6">{error}</Alert>}

      <form onSubmit={handleSubmit} className="space-y-5">
        <Input
          id="email"
          type="email"
          label={t('auth.email')}
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          icon={<Mail className="h-4 w-4" />}
          autoComplete="email"
          required
        />

        <PasswordField
          id="password"
          label={t('auth.password')}
          labelAction={
            <Link href="/forgot-password" className="text-xs text-ember underline-offset-4 transition-colors hover:text-foreground hover:underline">
              {t('auth.forgotPassword')}
            </Link>
          }
          visible={showPassword}
          onVisibleChange={setShowPassword}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="••••••••"
          autoComplete="current-password"
          required
        />

        <div className="pt-3">
          <Button type="submit" size="lg" className="w-full" loading={loading}>
            {t('auth.signIn')}
          </Button>
        </div>
      </form>

      {process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID && (
        <>
          <AuthDivider label={t('authFlow.or')} />
          <div className="flex justify-center">
            <GoogleLogin
              onSuccess={(credentialResponse) => {
                if (credentialResponse.credential) handleGoogle(credentialResponse.credential);
              }}
              onError={() => setError(t('authGoogle.googleAuthFailed'))}
            />
          </div>
        </>
      )}

      <p className="mt-10 border-t border-line pt-6 text-center text-sm text-gray-500">
        {t('auth.noAccount')}{' '}
        <Link href="/register" className="font-medium text-ember underline-offset-4 transition-colors hover:text-foreground hover:underline">
          {t('auth.registerNow')}
        </Link>
      </p>
    </div>
  );
}
