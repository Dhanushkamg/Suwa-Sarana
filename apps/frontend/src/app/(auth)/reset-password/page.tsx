'use client';

import { Suspense, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { CheckCircle2 } from 'lucide-react';
import { Button, buttonClasses } from '@/components/ui/Button';
import { Alert } from '@/components/pub/Alert';
import { AuthHeading } from '@/components/pub/AuthHeading';
import { PasswordField } from '@/components/pub/PasswordField';
import { focusFirstInvalid, parseApiError } from '@/components/pub/apiErrors';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';

const MIN_PASSWORD = 10;

/** Landing page of the emailed reset link: /reset-password?token=... (single-use token, sent to POST /api/auth/reset-password/token in the body; the phone-code route keeps POST /api/auth/reset-password). */
function ResetPasswordForm() {
  const { t } = useI18n();
  const token = useSearchParams().get('token') ?? '';
  const formRef = useRef<HTMLFormElement>(null);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [show, setShow] = useState(false);
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [focusTick, setFocusTick] = useState(0);

  useEffect(() => {
    if (focusTick > 0 && formRef.current) focusFirstInvalid(formRef.current);
  }, [focusTick]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const errors: Record<string, string> = {};
    if (!password) errors.password = t('authFlow.passwordRequired');
    else if (password.length < MIN_PASSWORD) errors.password = t('authFlow.passwordShort');
    if (password !== confirm) errors.confirm = t('authFlow.passwordMismatch');
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setFocusTick((n) => n + 1);
      return;
    }

    setLoading(true);
    try {
      await apiClient.post('/auth/reset-password/token', { token, newPassword: password });
      setDone(true);
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, { fallback: t('common.error'), fieldAliases: { newPassword: 'password' } });
      setError(parsed.message);
      if (parsed.fields.password) {
        setFieldErrors({ password: parsed.fields.password });
        setFocusTick((n) => n + 1);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <AuthHeading eyebrow={t('pub.eyebrowReset')} title={t('authFlow.newPasswordTitle')} subtitle={t('authFlow.newPasswordSubtitle')} />

      {done ? (
        <div className="space-y-6 rounded-2xl border border-success/30 bg-success-soft p-6" role="status">
          <div className="flex items-start gap-3">
            <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-success-text" aria-hidden="true" />
            <div>
              <h2 className="font-display text-2xl text-foreground">{t('authFlow.resetDone')}</h2>
              <p className="mt-2 text-sm leading-relaxed text-foreground">{t('authFlow.resetDoneDesc')}</p>
            </div>
          </div>
          <Link href="/login" className={buttonClasses('primary', 'lg', 'w-full')}>
            {t('auth.signIn')}
          </Link>
        </div>
      ) : !token ? (
        <>
          <Alert>{t('authFlow.errors.auth.reset.tokenInvalid')}</Alert>
          <p className="mt-6 text-center text-sm">
            <Link href="/forgot-password" className="inline-flex min-h-11 items-center text-primary-text underline underline-offset-4">
              {t('authFlow.requestNewLink')}
            </Link>
          </p>
        </>
      ) : (
        <>
          {error && <Alert className="mb-6">{error}</Alert>}
          <form ref={formRef} onSubmit={submit} noValidate className="space-y-5">
            <PasswordField
              id="new-password"
              label={t('authFlow.newPassword')}
              placeholder="••••••••"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setFieldErrors((f) => ({ ...f, password: '' }));
              }}
              error={fieldErrors.password}
              hint={t('authFlow.passwordHint', { min: MIN_PASSWORD })}
              autoComplete="new-password"
              visible={show}
              onVisibleChange={setShow}
            />
            <PasswordField
              id="confirm-new-password"
              label={t('auth.confirmPassword')}
              placeholder="••••••••"
              value={confirm}
              onChange={(e) => {
                setConfirm(e.target.value);
                setFieldErrors((f) => ({ ...f, confirm: '' }));
              }}
              error={fieldErrors.confirm}
              autoComplete="new-password"
              visible={show}
              onVisibleChange={setShow}
            />
            <div className="pt-3">
              <Button type="submit" size="lg" className="w-full" loading={loading}>
                {t('authFlow.resetButton')}
              </Button>
            </div>
            <p className="text-center text-sm">
              <Link href="/forgot-password" className="inline-flex min-h-11 items-center text-primary-text underline underline-offset-4">
                {t('authFlow.requestNewLink')}
              </Link>
            </p>
          </form>
        </>
      )}
    </div>
  );
}

export default function ResetPasswordPage() {
  const { t } = useI18n();
  return (
    <Suspense fallback={<div role="status" className="text-sm text-gray-400">{t('a11y.loading')}</div>}>
      <ResetPasswordForm />
    </Suspense>
  );
}
