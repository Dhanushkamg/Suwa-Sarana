'use client';

import { Suspense, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Mail } from 'lucide-react';
import { GoogleLogin } from '@react-oauth/google';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Alert } from '@/components/pub/Alert';
import { AuthDivider } from '@/components/pub/AuthDivider';
import { AuthHeading } from '@/components/pub/AuthHeading';
import { PasswordField } from '@/components/pub/PasswordField';
import { focusFirstInvalid, parseApiError } from '@/components/pub/apiErrors';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { AuthData, homeForRole, toUser, unwrap } from '@/lib/auth';
import { GOOGLE_LINK_EMAIL_KEY, GOOGLE_LINK_TOKEN_KEY, GOOGLE_TOKEN_KEY, type GoogleVerifyResult } from './googleToken';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t } = useI18n();
  const { login, initialize, isAuthenticated, user } = useAuthStore();
  const formRef = useRef<HTMLFormElement>(null);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({});
  // A Google account whose e-mail already has a password account: ask for that password once to link them
  const [linking, setLinking] = useState<{ idToken: string; email: string } | null>(null);
  // Focus the first invalid field after the errors have rendered
  const [focusTick, setFocusTick] = useState(0);

  // Someone who still has a valid session shouldn't see the sign-in form.
  useEffect(() => {
    initialize();
  }, [initialize]);

  // The register page can hand over a Google token that needs linking (?google=link)
  useEffect(() => {
    if (searchParams.get('google') !== 'link') return;
    try {
      const idToken = sessionStorage.getItem(GOOGLE_LINK_TOKEN_KEY);
      if (idToken) setLinking({ idToken, email: sessionStorage.getItem(GOOGLE_LINK_EMAIL_KEY) ?? '' });
    } catch {
      // sessionStorage unavailable: the person simply uses the Google button again
    }
  }, [searchParams]);

  useEffect(() => {
    if (isAuthenticated && user) router.replace(homeForRole(user.role));
  }, [isAuthenticated, user, router]);

  useEffect(() => {
    if (focusTick > 0 && formRef.current) focusFirstInvalid(formRef.current);
  }, [focusTick]);

  const handleAuthSuccess = (auth: AuthData) => {
    const signedIn = toUser(auth);
    login(auth.accessToken, signedIn);
    router.push(homeForRole(signedIn.role));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Localised client-side checks (the form is noValidate so the browser's own English/OS-language bubbles never appear)
    const next: { email?: string; password?: string } = {};
    if (!email.trim()) next.email = t('authFlow.emailRequired');
    else if (!EMAIL_RE.test(email.trim())) next.email = t('authFlow.emailInvalid');
    if (!password) next.password = t('authFlow.passwordRequired');
    setFieldErrors(next);
    if (Object.keys(next).length > 0) {
      setFocusTick((n) => n + 1);
      return;
    }

    setLoading(true);
    try {
      const res = await apiClient.post('/auth/login', { username: email.trim(), password });
      handleAuthSuccess(unwrap<AuthData>(res.data));
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, {
        fallback: t('common.error'),
        byStatus: { 401: 'auth.credentials.invalid' },
        fieldAliases: { username: 'email' },
      });
      setError(parsed.message);
      setFieldErrors({ email: parsed.fields.email, password: parsed.fields.password });
      if (parsed.fields.email || parsed.fields.password) setFocusTick((n) => n + 1);
    } finally {
      setLoading(false);
    }
  };

  const handleGoogle = async (idToken: string) => {
    setError('');
    setLoading(true);
    try {
      const res = await apiClient.post('/auth/google/verify', { idToken });
      const data = unwrap<GoogleVerifyResult>(res.data);
      if (data.requiresLinking) {
        setLinking({ idToken, email: data.email ?? '' });
        setPassword('');
      } else if (data.requiresRegistration) {
        // New Google user: the register page collects the rest of the profile and verifies the phone.
        // The token goes through sessionStorage rather than the URL so it never lands in history or logs.
        sessionStorage.setItem(GOOGLE_TOKEN_KEY, idToken);
        router.push('/register?google=1');
      } else if (data.authResponse) {
        handleAuthSuccess(data.authResponse);
      }
    } catch (err: unknown) {
      setError(parseApiError(err, t, { fallback: t('authGoogle.googleAuthFailed') }).message);
    } finally {
      setLoading(false);
    }
  };

  const handleLink = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!linking) return;
    setError('');
    if (!password) {
      setFieldErrors({ password: t('authFlow.passwordRequired') });
      setFocusTick((n) => n + 1);
      return;
    }
    setLoading(true);
    try {
      const res = await apiClient.post('/auth/google/link', { idToken: linking.idToken, password });
      try {
        sessionStorage.removeItem(GOOGLE_LINK_TOKEN_KEY);
        sessionStorage.removeItem(GOOGLE_LINK_EMAIL_KEY);
      } catch {
        // ignore
      }
      handleAuthSuccess(unwrap<AuthData>(res.data));
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, { fallback: t('authGoogle.googleAuthFailed'), byStatus: { 401: 'auth.credentials.invalid' } });
      setError(parsed.message);
      if (parsed.fields.password) {
        setFieldErrors({ password: parsed.fields.password });
        setFocusTick((n) => n + 1);
      }
    } finally {
      setLoading(false);
    }
  };

  if (linking) {
    return (
      <div>
        <AuthHeading eyebrow={t('pub.eyebrowSignIn')} title={t('authFlow.googleLinkTitle')} subtitle={t('authFlow.googleLinkDesc', { email: linking.email })} />
        {error && <Alert className="mb-6">{error}</Alert>}
        <form ref={formRef} onSubmit={handleLink} noValidate className="space-y-5">
          <PasswordField
            id="link-password"
            name="password"
            label={t('auth.password')}
            visible={showPassword}
            onVisibleChange={setShowPassword}
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              if (fieldErrors.password) setFieldErrors((f) => ({ ...f, password: undefined }));
            }}
            error={fieldErrors.password}
            placeholder="••••••••"
            autoComplete="current-password"
          />
          <div className="space-y-2 pt-3">
            <Button type="submit" size="lg" className="w-full" loading={loading}>
              {t('authFlow.googleLinkButton')}
            </Button>
            <Button type="button" variant="ghost" size="lg" className="w-full" onClick={() => { setLinking(null); setPassword(''); setError(''); }}>
              {t('common.cancel')}
            </Button>
          </div>
        </form>
      </div>
    );
  }

  return (
    <div>
      <AuthHeading eyebrow={t('pub.eyebrowSignIn')} title={t('auth.loginTitle')} subtitle={t('auth.loginSubtitle')} />

      {error && <Alert className="mb-6">{error}</Alert>}

      <form ref={formRef} onSubmit={handleSubmit} noValidate className="space-y-5">
        <Input
          id="email"
          name="username"
          type="email"
          inputMode="email"
          label={t('auth.email')}
          placeholder="you@example.com"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value);
            if (fieldErrors.email) setFieldErrors((f) => ({ ...f, email: undefined }));
          }}
          error={fieldErrors.email}
          icon={<Mail className="h-4 w-4" aria-hidden="true" />}
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
        />

        <PasswordField
          id="password"
          name="password"
          label={t('auth.password')}
          labelAction={
            <Link
              href="/forgot-password"
              className="-my-2 inline-flex min-h-11 items-center text-sm text-primary-text underline-offset-4 hover:underline"
            >
              {t('auth.forgotPassword')}
            </Link>
          }
          visible={showPassword}
          onVisibleChange={setShowPassword}
          value={password}
          onChange={(e) => {
            setPassword(e.target.value);
            if (fieldErrors.password) setFieldErrors((f) => ({ ...f, password: undefined }));
          }}
          error={fieldErrors.password}
          placeholder="••••••••"
          autoComplete="current-password"
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

      <p className="mt-10 border-t border-line pt-6 text-center text-sm text-gray-400">
        {t('auth.noAccount')}{' '}
        <Link href="/register" className="inline-flex min-h-11 items-center font-medium text-primary-text underline underline-offset-4">
          {t('auth.registerNow')}
        </Link>
      </p>
    </div>
  );
}

export default function LoginPage() {
  const { t } = useI18n();
  return (
    <Suspense fallback={<div role="status" className="text-sm text-gray-400">{t('a11y.loading')}</div>}>
      <LoginForm />
    </Suspense>
  );
}
