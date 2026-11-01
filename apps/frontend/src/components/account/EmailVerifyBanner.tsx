'use client';

import { useState } from 'react';
import Link from 'next/link';
import { MailWarning } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Notice } from '@/components/dash/Notice';
import { useToast } from '@/components/ui/Toast';
import { parseApiError } from '@/lib/apiError';
import { useI18n } from '@/lib/i18n';
import { resendVerificationEmail } from '@/lib/api/account';
import { useAuthStore } from '@/store/authStore';

/**
 * Shown at the top of the dashboard while the signed-in person's e-mail is not confirmed (emailVerified === false).
 * Institutions cannot publish camps, record stock or send urgent requests before that (403 email.verificationRequired).
 * Asking for a new link needs a session (POST /api/auth/resend-verification), which the dashboard always has.
 */
export function EmailVerifyBanner() {
  const { t } = useI18n();
  const toast = useToast();
  const user = useAuthStore((s) => s.user);
  const updateUser = useAuthStore((s) => s.updateUser);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);

  if (!user || user.emailVerified !== false) return null;

  const resend = async () => {
    setSending(true);
    try {
      await resendVerificationEmail();
      setSent(true);
    } catch (err) {
      const parsed = parseApiError(err, t, { fallback: t('authFlow.linkSendFailed') });
      if (parsed.code === 'auth.emailVerification.alreadyVerified') updateUser({ emailVerified: true });
      else toast.show(parsed.message, 'error');
    } finally {
      setSending(false);
    }
  };

  return (
    <Notice
      tone="warning"
      icon={<MailWarning />}
      className="mb-6"
      action={
        sent ? undefined : (
          <Button type="button" size="sm" variant="secondary" onClick={resend} loading={sending}>
            {t('authFlow.resendVerification')}
          </Button>
        )
      }
    >
      <p className="font-medium">{t('account.emailBannerTitle')}</p>
      <p className="mt-0.5 text-sm">
        {sent ? t('authFlow.verificationSent') : t('account.emailBannerDesc', { email: user.email })}{' '}
        <Link href="/dashboard/account" className="font-medium text-primary-text underline underline-offset-4">
          {t('account.title')}
        </Link>
      </p>
    </Notice>
  );
}
