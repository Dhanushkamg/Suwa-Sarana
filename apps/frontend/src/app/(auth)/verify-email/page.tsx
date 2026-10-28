'use client';

import { Suspense, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { CheckCircle2, Loader2, Mail } from 'lucide-react';
import { Button, buttonClasses } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Alert } from '@/components/pub/Alert';
import { AuthHeading } from '@/components/pub/AuthHeading';
import { parseApiError } from '@/components/pub/apiErrors';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';

type State = 'idle' | 'verifying' | 'verified' | 'failed';
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Landing page of the emailed verification link: /verify-email?token=... Also lets the user ask for a fresh link. */
function VerifyEmailBody() {
  const { t } = useI18n();
  const token = useSearchParams().get('token') ?? '';
  const [state, setState] = useState<State>(token ? 'verifying' : 'idle');
  const [message, setMessage] = useState('');
  const started = useRef(false);

  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState('');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [resendError, setResendError] = useState('');

  // A token is single use: guard against React StrictMode running the effect twice
  useEffect(() => {
    if (!token || started.current) return;
    started.current = true;
    apiClient
      .post('/auth/verify-email', { token })
      .then(() => setState('verified'))
      .catch((err: unknown) => {
        setMessage(parseApiError(err, t, { fallback: t('authFlow.errors.TOKEN_INVALID') }).message);
        setState('failed');
      });
  }, [token, t]);

  const resend = async (e: React.FormEvent) => {
    e.preventDefault();
    setResendError('');
    setEmailError('');
    if (!email.trim()) return setEmailError(t('authFlow.emailRequired'));
    if (!EMAIL_RE.test(email.trim())) return setEmailError(t('authFlow.emailInvalid'));
    setSending(true);
    try {
      await apiClient.post('/auth/resend-verification', { email: email.trim() });
      setSent(true);
    } catch (err: unknown) {
      setResendError(parseApiError(err, t, { fallback: t('authFlow.linkSendFailed') }).message);
    } finally {
      setSending(false);
    }
  };

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
            <Link href="/login" className={buttonClasses('primary', 'lg', 'w-full')}>
              {t('auth.signIn')}
            </Link>
          </div>
        )}

        {state === 'failed' && <Alert className="mb-6">{message}</Alert>}
      </div>

      {(state === 'failed' || state === 'idle') && (
        <form onSubmit={resend} noValidate className="mt-6 space-y-5">
          <p className="text-sm leading-relaxed text-gray-400">{t('authFlow.resendVerificationHelp')}</p>
          {resendError && <Alert>{resendError}</Alert>}
          {sent ? (
            <Alert tone="success">{t('authFlow.verificationSent')}</Alert>
          ) : (
            <>
              <Input
                id="verify-email-address"
                type="email"
                inputMode="email"
                label={t('auth.email')}
                placeholder="you@example.com"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  setEmailError('');
                }}
                error={emailError}
                icon={<Mail className="h-4 w-4" aria-hidden="true" />}
                autoComplete="email"
                autoCapitalize="none"
                spellCheck={false}
              />
              <Button type="submit" size="lg" className="w-full" loading={sending}>
                {t('authFlow.resendVerification')}
              </Button>
            </>
          )}
        </form>
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
