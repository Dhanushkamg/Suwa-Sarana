'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { AlertCircle, Download, KeyRound, MailCheck, Phone, ShieldCheck, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { FormField } from '@/components/ui/FormField';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Skeleton } from '@/components/ui/Skeleton';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useToast } from '@/components/ui/Toast';
import { ChangePasswordDialog, ChangePhoneDialog, DeleteAccountDialog } from '@/components/account/AccountDialogs';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader } from '@/components/dash/Panel';
import { Rise } from '@/components/dash/Rise';
import { VerificationBadge } from '@/components/dash/VerificationBadge';
import { focusFirstInvalid } from '@/components/pub/apiErrors';
import { useI18n } from '@/lib/i18n';
import { parseApiError } from '@/lib/apiError';
import { apiMessage } from '@/lib/api/common';
import { downloadText, exportMyData, fetchMe, LANGUAGES, recordConsent, resendVerificationEmail, updateMe, userFromMe, type Language, type Me } from '@/lib/api/account';
import { useAuthStore } from '@/store/authStore';
import { resetUnread } from '@/lib/api/notifications';
import { setSessionHint } from '@/lib/auth';

/**
 * Account area for every role: name and language, e-mail and phone, password, privacy (terms, export, deletion).
 * Data: GET/PATCH /api/users/me, POST /users/me/change-password, GET /users/me/export, DELETE /users/me, POST /users/me/consent.
 */
export default function AccountPage() {
  const { t, format } = useI18n();
  const router = useRouter();
  const toast = useToast();
  const login = useAuthStore((s) => s.login);
  const updateUser = useAuthStore((s) => s.updateUser);

  const [me, setMe] = useState<Me | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [loadError, setLoadError] = useState('');

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [language, setLanguage] = useState<Language>('en');
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Record<string, string | undefined>>({});
  const [formError, setFormError] = useState('');
  const [focusTick, setFocusTick] = useState(0);

  const [phoneOpen, setPhoneOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [resending, setResending] = useState(false);
  const [consenting, setConsenting] = useState(false);
  const [exporting, setExporting] = useState(false);

  const apply = useCallback(
    (next: Me) => {
      setMe(next);
      setFirstName(next.firstName);
      setLastName(next.lastName);
      setLanguage(next.preferredLanguage);
      updateUser(userFromMe(next));
    },
    [updateUser]
  );

  const load = useCallback(async () => {
    setStatus('loading');
    try {
      apply(await fetchMe());
      setStatus('ready');
    } catch (err) {
      setLoadError(apiMessage(err, t('dash.loadFailed')));
      setStatus('error');
    }
  }, [apply, t]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
    // load once; its identity changes with the language, which must not refetch
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (focusTick > 0) focusFirstInvalid(document.getElementById('account-profile-form') ?? document);
  }, [focusTick]);

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!me || saving) return;
    setFormError('');
    const found: Record<string, string> = {};
    if (!firstName.trim()) found.firstName = t('authFlow.firstNameRequired');
    else if (firstName.length > 100) found.firstName = t('authFlow.errors.auth.name.tooLong');
    if (!lastName.trim()) found.lastName = t('authFlow.lastNameRequired');
    else if (lastName.length > 100) found.lastName = t('authFlow.errors.auth.name.tooLong');
    setErrors(found);
    if (Object.keys(found).length) {
      setFocusTick((n) => n + 1);
      return;
    }
    const patch: Parameters<typeof updateMe>[0] = {};
    if (firstName.trim() !== me.firstName) patch.firstName = firstName.trim();
    if (lastName.trim() !== me.lastName) patch.lastName = lastName.trim();
    if (language !== me.preferredLanguage) patch.preferredLanguage = language;
    if (Object.keys(patch).length === 0) {
      toast.show(t('account.nothingToSave'), 'info');
      return;
    }
    setSaving(true);
    try {
      apply(await updateMe(patch));
      toast.show(t('account.saved'), 'success');
    } catch (err) {
      const parsed = parseApiError(err, t, { fallback: t('common.error') });
      setFormError(parsed.message);
      setErrors(parsed.fields);
      if (Object.keys(parsed.fields).length) setFocusTick((n) => n + 1);
    } finally {
      setSaving(false);
    }
  };

  const resend = async () => {
    setResending(true);
    try {
      await resendVerificationEmail();
      toast.show(t('authFlow.verificationSent'), 'success');
    } catch (err) {
      const parsed = parseApiError(err, t, { fallback: t('authFlow.linkSendFailed') });
      if (parsed.code === 'auth.emailVerification.alreadyVerified') void load();
      toast.show(parsed.message, parsed.code === 'auth.emailVerification.alreadyVerified' ? 'info' : 'error');
    } finally {
      setResending(false);
    }
  };

  const agree = async () => {
    setConsenting(true);
    try {
      apply(await recordConsent());
      toast.show(t('account.consentSaved'), 'success');
    } catch (err) {
      toast.show(apiMessage(err, t('common.error')), 'error');
    } finally {
      setConsenting(false);
    }
  };

  const exportData = async () => {
    setExporting(true);
    try {
      const { filename, content } = await exportMyData();
      downloadText(filename, content);
      toast.show(t('account.exported'), 'success');
    } catch (err) {
      toast.show(apiMessage(err, t('account.exportFailed')), 'error');
    } finally {
      setExporting(false);
    }
  };

  /** The account is gone on the server: drop the session here and leave. */
  const afterDeletion = () => {
    setSessionHint(false);
    useAuthStore.setState({ accessToken: null, user: null, isAuthenticated: false });
    resetUnread();
    router.replace('/');
  };

  const languageOptions = LANGUAGES.map((l) => ({ value: l, label: t(`chatUi.lang.${l}`) }));

  if (status === 'loading') {
    return (
      <div role="status" aria-label={t('common.loading')} className="max-w-3xl space-y-4">
        <Skeleton className="h-10 w-1/2" />
        <Skeleton className="h-48 w-full rounded-2xl" />
        <Skeleton className="h-32 w-full rounded-2xl" />
      </div>
    );
  }

  if (status === 'error' || !me) {
    return (
      <div className="max-w-3xl">
        <PageHeader eyebrow={t('account.eyebrow')} title={t('account.title')} />
        <EmptyState
          icon={<AlertCircle className="h-10 w-10 text-danger-text" aria-hidden="true" />}
          title={loadError || t('dash.loadFailed')}
          action={
            <Button variant="secondary" onClick={() => void load()}>
              {t('dash.retry')}
            </Button>
          }
        />
      </div>
    );
  }

  const isAdmin = me.role === 'ADMIN';

  return (
    <div className="max-w-3xl">
      <PageHeader
        eyebrow={t('account.eyebrow')}
        title={t('account.title')}
        description={t('account.desc')}
        badges={me.verificationStatus && me.role !== 'DONOR' && !isAdmin ? <VerificationBadge status={me.verificationStatus} verifiedLabel={t('dashboard.verifiedOrg')} /> : undefined}
      />

      <div className="space-y-6">
        {me.verificationStatus === 'REJECTED' && (
          <Notice tone="error">
            {t('account.verificationRejected')}
            {me.verificationReason ? ` ${t('account.reason', { reason: me.verificationReason })}` : ''}
          </Notice>
        )}

        {/* Profile */}
        <Rise>
          <Panel>
            <SectionHeader eyebrow={t('account.profileEyebrow')} title={t('account.profileTitle')} />
            {formError && (
              <Notice tone="error" className="mb-4">
                {formError}
              </Notice>
            )}
            <form id="account-profile-form" onSubmit={saveProfile} noValidate className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <FormField label={t('authFlow.firstName')} error={errors.firstName} required>
                  {(p) => <Input {...p} autoComplete="given-name" value={firstName} onChange={(e) => setFirstName(e.target.value)} />}
                </FormField>
                <FormField label={t('authFlow.lastName')} error={errors.lastName} required>
                  {(p) => <Input {...p} autoComplete="family-name" value={lastName} onChange={(e) => setLastName(e.target.value)} />}
                </FormField>
              </div>
              <div>
                <Select
                  id="account-language"
                  label={t('account.language')}
                  options={languageOptions}
                  value={language}
                  onChange={(e) => setLanguage(e.target.value as Language)}
                  error={errors.preferredLanguage}
                  aria-describedby="account-language-help"
                />
                <p id="account-language-help" className="mt-1.5 text-xs text-muted">
                  {t('account.languageHelp')}
                </p>
              </div>
              <div className="flex justify-end">
                <Button type="submit" loading={saving}>
                  {t('dashUi.inv.save')}
                </Button>
              </div>
            </form>
          </Panel>
        </Rise>

        {/* Contact */}
        <Rise delay={0.04}>
          <Panel>
            <SectionHeader eyebrow={t('account.contactEyebrow')} title={t('account.contactTitle')} />
            <dl className="divide-y divide-line">
              <div className="flex flex-wrap items-center justify-between gap-3 py-3">
                <div className="min-w-0">
                  <dt className="text-sm text-gray-500">{t('auth.email')}</dt>
                  <dd className="break-all text-base text-foreground">{me.email}</dd>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <StatusBadge value={me.emailVerified ? 'VERIFIED' : 'PENDING'} tone={me.emailVerified ? 'success' : 'warning'} label={me.emailVerified ? t('account.emailConfirmed') : t('account.emailNotConfirmed')} />
                  {!me.emailVerified && (
                    <Button type="button" size="sm" variant="secondary" onClick={resend} loading={resending}>
                      <MailCheck className="h-4 w-4" aria-hidden="true" />
                      {t('authFlow.resendVerification')}
                    </Button>
                  )}
                </div>
              </div>
              <div className="flex flex-wrap items-center justify-between gap-3 py-3">
                <div className="min-w-0">
                  <dt className="text-sm text-gray-500">{t('auth.phone')}</dt>
                  <dd className="text-base text-foreground" dir="ltr">
                    {me.phoneNumber}
                  </dd>
                </div>
                <Button type="button" size="sm" variant="secondary" onClick={() => setPhoneOpen(true)}>
                  <Phone className="h-4 w-4" aria-hidden="true" />
                  {t('account.changePhone')}
                </Button>
              </div>
            </dl>
            {!me.emailVerified && <p className="mt-2 text-sm text-gray-400">{t('account.emailUnconfirmedHelp')}</p>}
          </Panel>
        </Rise>

        {/* Security */}
        <Rise delay={0.08}>
          <Panel>
            <SectionHeader eyebrow={t('account.securityEyebrow')} title={t('account.securityTitle')} />
            {me.hasPassword ? (
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-gray-400">{t('account.passwordHelp')}</p>
                <Button type="button" variant="secondary" onClick={() => setPasswordOpen(true)}>
                  <KeyRound className="h-4 w-4" aria-hidden="true" />
                  {t('account.changePassword')}
                </Button>
              </div>
            ) : (
              <p className="text-sm text-gray-400">{t('account.googleOnly')}</p>
            )}
            {me.googleLinked && (
              <p className="mt-3 flex items-center gap-2 text-sm text-foreground">
                <ShieldCheck className="h-4 w-4 text-success-text" aria-hidden="true" />
                {t('account.googleLinked')}
              </p>
            )}
          </Panel>
        </Rise>

        {/* Privacy */}
        <Rise delay={0.12}>
          <Panel>
            <SectionHeader eyebrow={t('account.privacyEyebrow')} title={t('account.privacyTitle')} />
            <div className="space-y-5">
              <div>
                {me.consentRequired ? (
                  <Notice
                    tone="warning"
                    action={
                      <Button type="button" onClick={agree} loading={consenting}>
                        {t('account.agree')}
                      </Button>
                    }
                  >
                    {t('account.consentRequired')}
                  </Notice>
                ) : (
                  <p className="text-sm text-gray-400">{me.consentedAt ? t('account.consentedOn', { date: format.date(me.consentedAt, { dateStyle: 'long' }) }) : t('account.consentOk')}</p>
                )}
                <p className="mt-2 flex flex-wrap gap-x-5 text-sm">
                  <Link href="/terms" className="inline-flex min-h-11 items-center text-primary-text underline underline-offset-4">
                    {t('auth.termsLink')}
                  </Link>
                  <Link href="/privacy" className="inline-flex min-h-11 items-center text-primary-text underline underline-offset-4">
                    {t('auth.privacyLink')}
                  </Link>
                </p>
              </div>

              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-5">
                <div className="min-w-0 max-w-md">
                  <h3 className="text-base font-medium text-foreground">{t('account.exportTitle')}</h3>
                  <p className="mt-1 text-sm text-gray-400">{t('account.exportDesc')}</p>
                </div>
                <Button type="button" variant="secondary" onClick={exportData} loading={exporting}>
                  <Download className="h-4 w-4" aria-hidden="true" />
                  {t('account.exportButton')}
                </Button>
              </div>

              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-5">
                <div className="min-w-0 max-w-md">
                  <h3 className="text-base font-medium text-foreground">{t('account.deleteTitle')}</h3>
                  <p className="mt-1 text-sm text-gray-400">{isAdmin ? t('account.deleteAdmin') : t('account.deleteDesc')}</p>
                </div>
                <Button type="button" variant="danger" onClick={() => setDeleteOpen(true)} disabled={isAdmin}>
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                  {t('account.deleteButton')}
                </Button>
              </div>
            </div>
          </Panel>
        </Rise>
      </div>

      <ChangePhoneDialog open={phoneOpen} onClose={() => setPhoneOpen(false)} currentPhone={me.phoneNumber} onChanged={(next) => { apply(next); toast.show(t('account.phoneChanged'), 'success'); }} />
      <ChangePasswordDialog
        open={passwordOpen}
        onClose={() => setPasswordOpen(false)}
        onChanged={(accessToken, user) => {
          login(accessToken, { ...user, ...userFromMe(me) });
          toast.show(t('account.passwordChanged'), 'success');
        }}
      />
      <DeleteAccountDialog open={deleteOpen} onClose={() => setDeleteOpen(false)} hasPassword={me.hasPassword} onDeleted={afterDeletion} />
    </div>
  );
}
