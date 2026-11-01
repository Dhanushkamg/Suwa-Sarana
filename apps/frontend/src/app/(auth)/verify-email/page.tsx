'use client';

import { Suspense, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { CheckCircle2, Loader2 } from 'lucide-react';
import { Button, buttonClasses } from '@/components/ui/Button';
import { Alert } from '@/components/pub/Alert';
import { AuthHeading } from '@/components/pub/AuthHeading';
import { parseApiError } from '@/components/pub/apiErrors';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { useAuthStore } from '@/store/authStore';

type State = 'idle' | 'verifying' | 'verified' | 'failed';

/**
 * Landing page of the emailed verification link: /verify-email?token=...
 * POST /api/auth/verify-email {token} is public. Asking for a NEW link (POST /api/auth/resend-verification, no body) needs a
 * signed-in user, so the resend button is only shown when signed in; otherwise the page says "sign in to get a new link".
 */
function VerifyEmailBody() {
  const { t } = useI18n();
  const token = useSearchParams().get('token') ?? '';
  const { initialize, isInitialized, isAuthenticated, user, updateUser } = useAuthStore();
  const [state, setState] = useState<State>(token ? 'verifying' : 'idle');
  const [message, setMessage] = useState('');
  const started = useRef(false);

  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [resendError, setResendError] = useState('');

  useEffect(() => {
    initialize();
  }, [initialize]);

  // A token is single use: guard against React StrictMode running the effect twice
  useEffect(() => {
    if (!token || started.current) return;
    started.current = true;
    apiClient
      .post('/auth/verify-email', { token })
      .then(() => {
        setState('verified');
        updateUser({ emailVerified: true });
      })
      .catch((err: unknown) => {
        setMessage(parseApiError(err, t, { fallback: t('authFlow.errors.auth.emailVerification.invalid') }).message);
        setState('failed');
      });
  }, [token, t, updateUser]);

  const resend = async () => {
    setResendError('');
    setSending(true);
    try {
      await apiClient.post('/auth/resend-verification');
      setSent(true);
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, { fallback: t('authFlow.linkSendFailed') });
      if (parsed.code === 'auth.emailVerification.alreadyVerified') updateUser({ emailVerified: true });
      setResendError(parsed.message);
    } finally {
      setSending(false);
    }
  };

  const alreadyVerified = isAuthenticated && user?.emailVerified === true;
  const canResend = state !== 'verified' && isInitialized && isAuthenticated && !alreadyVerified;
  const mustSignIn = state !== 'verified' && isInitialized && !isAuthenticated;

  return (
    <div>
      <AuthHeading eyebrow={t('pub.eyebrowVerifyEmail')} title={t('authFlow.verifyEmailTitle')} subtitle={state === 'idle' ? t('authFlow.verifyEmailIdle') : undefined} />

      <div aria-live="polite">
        {state === 'verifying' && (
          <p className="flex items-center gap-3 text-sm text-foreground" role="status">
            <Loader2 className="h-5 w-5 animate-spin motion-reduce:animate-none text-primary-text" aria-hidden="true" />
            {t('authFlow.verifying')}
          </p>
        )}

        {state === 'verified' && (
          <div className="space-y-6 rounded-2xl border border-success/30 bg-success-soft p-6" role="status">
            <div className="flex items-start gap-3">
              <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-success-text" aria-hidden="true" />
              <div>
                <h2 className="font-display text-2xl text-foreground">{t('authFlow.emailVerified')}</h2>
                <p className="mt-2 text-sm leading-relaxed text-foreground">{t('authFlow.emailVerifiedDesc')}</p>
              </div>
            </div>
            <Link href={isAuthenticated ? '/dashboard' : '/login'} className={buttonClasses('primary', 'lg', 'w-full')}>
              {isAuthenticated ? t('dash.overview') : t('auth.signIn')}
            </Link>
          </div>
        )}

        {state === 'failed' && <Alert className="mb-6">{message}</Alert>}
      </div>

      {(state === 'failed' || state === 'idle') && (
        <div className="mt-6 space-y-5">
          {alreadyVerified && <Alert tone="success">{t('authFlow.emailAlreadyVerified')}</Alert>}

          {canResend && (
            <>
              <p className="text-sm leading-relaxed text-gray-400">{t('authFlow.resendVerificationHelp', { email: user?.email ?? '' })}</p>
              {resendError && <Alert>{resendError}</Alert>}
              {sent ? (
                <Alert tone="success">{t('authFlow.verificationSent')}</Alert>
              ) : (
                <Button type="button" size="lg" className="w-full" loading={sending} onClick={resend}>
                  {t('authFlow.resendVerification')}
                </Button>
              )}
            </>
          )}

          {mustSignIn && (
            <Link href="/login" className={buttonClasses('secondary', 'lg', 'w-full')}>
              {t('authFlow.signInToResend')}
            </Link>
          )}
        </div>
      )}

      <p className="mt-10 border-t border-line pt-6 text-center text-sm">
        <Link href="/login" className="inline-flex min-h-11 items-center text-primary-text underline underline-offset-4">
          {t('authFlow.backToSignIn')}
        </Link>
      </p>
    </div>
  );
}

export default function VerifyEmailPage() {
  const { t } = useI18n();
  return (
    <Suspense fallback={<div role="status" className="text-sm text-gray-400">{t('a11y.loading')}</div>}>
      <VerifyEmailBody />
    </Suspense>
  );
}
