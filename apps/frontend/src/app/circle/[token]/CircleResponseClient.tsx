'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Heart, MapPin, Droplets, CheckCircle2,
  AlertCircle, User, Sparkles, Share2
} from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { Logo } from '@/components/brand/Logo';
import { Reveal } from '@/components/motion/Reveal';
import { Alert } from '@/components/pub/Alert';
import { Textarea } from '@/components/pub/Textarea';
import { UrgencyBadge } from '@/components/pub/UrgencyBadge';
import { linkButtonClass } from '@/components/pub/linkButton';
import { focusFirstInvalid, parseApiError } from '@/components/pub/apiErrors';
import type { CircleInfo } from '@/components/pub/circleData';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { isValidPhone, normalizePhone } from '@/lib/auth';

interface Props {
  token: string;
  initialData?: CircleInfo | null;
}

export default function CircleResponseClient({ token, initialData }: Props) {
  const { t } = useI18n();

  const [info, setInfo] = useState<CircleInfo | null>(initialData || null);
  const [loading, setLoading] = useState(!initialData);
  const [error, setError] = useState<string | null>(null);

  // Form state
  const [form, setForm] = useState({
    responderName: '',
    responderPhone: '',
    bloodType: initialData?.patientBloodType || 'O+',
    notes: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [focusTick, setFocusTick] = useState(0);
  const formRef = useRef<HTMLFormElement>(null);

  useEffect(() => {
    if (focusTick > 0 && formRef.current) focusFirstInvalid(formRef.current);
  }, [focusTick]);

  // Translate the urgency enum when a label exists, otherwise show the raw value
  const urgencyLabel = (u: string) => {
    const key = `ui.urgency.${u}`;
    const label = t(key);
    return label === key ? u : label;
  };

  const bloodTypeOptions = [
    { value: 'A+', label: t('bloodTypes.APositive') },
    { value: 'A-', label: t('bloodTypes.ANegative') },
    { value: 'B+', label: t('bloodTypes.BPositive') },
    { value: 'B-', label: t('bloodTypes.BNegative') },
    { value: 'AB+', label: t('bloodTypes.ABPositive') },
    { value: 'AB-', label: t('bloodTypes.ABNegative') },
    { value: 'O+', label: t('bloodTypes.OPositive') },
    { value: 'O-', label: t('bloodTypes.ONegative') },
  ];

  const loadCircleInfo = useCallback(async () => {
    if (initialData) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<{ data?: CircleInfo } & CircleInfo>(`/circle/${token}`);
      const data: CircleInfo = res.data?.data ?? res.data;
      setInfo(data);
      if (data?.patientBloodType) {
        setForm((prev) => ({ ...prev, bloodType: data.patientBloodType }));
      }
    } catch (err: unknown) {
      // 404/410 = unknown or expired invite; anything else is a transient problem
      const status = (err as { response?: { status?: number } })?.response?.status;
      setError(status === 404 || status === 410 || status === undefined ? t('circle.invalidToken') : parseApiError(err, t, { fallback: t('circle.invalidToken') }).message);
    } finally {
      setLoading(false);
    }
  }, [token, initialData, t]);

  useEffect(() => {
    loadCircleInfo();
  }, [loadCircleInfo]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const errors: Record<string, string> = {};
    if (!form.responderName.trim()) errors.responderName = t('pub.circleNameRequired');
    if (!form.responderPhone.trim()) errors.responderPhone = t('authFlow.phoneRequired');
    else if (!isValidPhone(form.responderPhone)) errors.responderPhone = t('authFlow.phoneInvalid');
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setFocusTick((n) => n + 1);
      return;
    }
    setSubmitting(true);

    try {
      await apiClient.post(`/circle/${token}/respond`, {
        responderName: form.responderName.trim(),
        // Spaces/dashes and a leading 0 are normalised to +94... (the API only accepts digits with an optional +)
        responderPhone: normalizePhone(form.responderPhone),
        bloodType: form.bloodType,
        notes: form.notes || undefined,
      });
      setSubmitted(true);
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, { fallback: t('circle.submitFailed') });
      setFormError(parsed.message);
      if (Object.keys(parsed.fields).length > 0) {
        setFieldErrors(parsed.fields);
        setFocusTick((n) => n + 1);
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main id="main-content" className="flex min-h-screen flex-col bg-background text-foreground">
      <header className="border-b border-line">
        <div className="mx-auto flex min-h-16 w-full max-w-xl flex-wrap items-center justify-between gap-x-3 gap-y-2 px-4 py-2">
          <Logo />
          <div className="flex items-center gap-2">
            <Link
              href={`/share/${token}`}
              aria-label={t('circle.socialCard')}
              className="inline-flex h-11 w-11 items-center justify-center gap-2 rounded-full border border-line-strong text-sm font-medium text-foreground transition-colors hover:border-foreground/30 sm:w-auto sm:px-4"
            >
              <Share2 className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">{t('circle.socialCard')}</span>
            </Link>
            <ThemeToggle />
            <LanguageSwitcher />
          </div>
        </div>
      </header>

      <div className="mx-auto w-full max-w-xl flex-1 px-5 py-8 sm:py-12">
        {loading ? (
          <div role="status" className="glass-card rounded-3xl p-12 text-center">
            <div className="mx-auto mb-5 h-7 w-7 animate-spin motion-reduce:animate-none rounded-full border-2 border-line-strong border-t-primary" aria-hidden="true" />
            <p className="text-sm text-gray-400">{t('circle.loading')}</p>
          </div>
        ) : error || !info ? (
          <div role="alert" className="glass-card rounded-3xl p-8 text-center sm:p-12">
            <AlertCircle className="mx-auto mb-5 h-9 w-9 text-danger-text" aria-hidden="true" />
            <h1 className="font-display text-4xl text-foreground">{t('circle.inviteUnavailable')}</h1>
            {error && <p className="mx-auto mt-4 max-w-sm text-sm leading-relaxed text-gray-400">{error}</p>}
            <Link href="/" className={linkButtonClass('secondary', 'md', 'mt-8')}>
              {t('circle.goToHome')}
            </Link>
          </div>
        ) : submitted ? (
          /* Success screen */
          <Reveal y={16}>
            <div className="glass-card rounded-3xl p-8 text-center sm:p-12" role="status">
              <span className="mx-auto mb-6 flex h-14 w-14 items-center justify-center rounded-full border border-success/30 bg-success-soft text-success-text">
                <CheckCircle2 className="h-7 w-7" aria-hidden="true" />
              </span>
              <h1 className="font-display break-words text-4xl text-foreground sm:text-5xl">
                {t('circle.thankYou')}, {form.responderName}
              </h1>
              <p className="mx-auto mt-5 max-w-sm text-[15px] leading-relaxed text-foreground">
                {t('circle.successBody')} <strong className="font-mono font-medium text-foreground">{normalizePhone(form.responderPhone)}</strong>
              </p>

              <div className="mx-auto mt-8 max-w-sm rounded-2xl border border-line bg-surface-raised p-5 text-left">
                <p className="flex items-center gap-2 text-sm font-medium text-primary-text">
                  <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" /> {t('circle.wantMore')}
                </p>
                <p className="mt-2 text-sm leading-relaxed text-gray-400">{t('circle.wantMoreDesc')}</p>
              </div>

              <Link href="/register" className={linkButtonClass('primary', 'lg', 'mt-8 w-full max-w-sm')}>
                <Heart className="h-4 w-4" aria-hidden="true" /> {t('circle.registerBtn')}
              </Link>
            </div>
          </Reveal>
        ) : (
          /* Active requisition & volunteer form */
          <div className="space-y-6">
            <Reveal y={20}>
              <article className="glass-card overflow-hidden rounded-3xl">
                <div className="flex items-center justify-between gap-3 border-b border-line px-6 py-4 sm:px-8">
                  <span className="eyebrow">{t('pub.circleEyebrow')}</span>
                  <UrgencyBadge
                    label={t('circle.urgencyBadge', { urgency: urgencyLabel(info.urgency) })}
                    critical={info.urgency === 'CRITICAL'}
                  />
                </div>

                <div className="flex items-start gap-5 px-6 pb-7 pt-7 sm:gap-7 sm:px-8">
                  <p
                    className="shrink-0 font-display text-[5.5rem] leading-[0.9] text-brand sm:text-[7rem]"
                    aria-label={info.patientBloodType}
                  >
                    {info.patientBloodType}
                  </p>
                  <div className="min-w-0 pt-1">
                    <h1 className="font-display break-words text-3xl leading-snug text-foreground sm:text-4xl">{info.hospitalName}</h1>
                  </div>
                </div>

                <dl className="grid grid-cols-2 border-t border-line">
                  <div className="border-r border-line px-6 py-5 sm:px-8">
                    <dt className="eyebrow flex items-center gap-2">
                      <MapPin className="h-3.5 w-3.5 shrink-0 text-gray-500" aria-hidden="true" />
                      <span className="min-w-0">{t('circle.districtLabel')}</span>
                    </dt>
                    <dd className="mt-2 break-words font-mono text-sm text-foreground">{info.district}</dd>
                  </div>
                  <div className="px-6 py-5 sm:px-8">
                    <dt className="eyebrow flex items-center gap-2">
                      <Droplets className="h-3.5 w-3.5 shrink-0 text-gray-500" aria-hidden="true" />
                      <span className="min-w-0">{t('circle.requiredLabel')}</span>
                    </dt>
                    <dd className="mt-2 font-mono text-sm text-foreground">{t('pub.unitsCount', { count: info.unitsNeeded })}</dd>
                  </div>
                </dl>
              </article>
            </Reveal>

            {/* Volunteer form */}
            <Reveal y={20} delay={0.1}>
              <form ref={formRef} onSubmit={handleSubmit} noValidate className="glass-card space-y-5 rounded-3xl p-6 sm:p-8">
                <div>
                  <h2 className="flex items-center gap-3 font-display text-3xl text-foreground">
                    <User className="h-5 w-5 shrink-0 text-primary-text" aria-hidden="true" />
                    {t('circle.volunteerTitle')}
                  </h2>
                  <p className="mt-2 text-sm leading-relaxed text-gray-400">{t('circle.volunteerDesc')}</p>
                </div>

                {formError && <Alert>{formError}</Alert>}

                <Input
                  id="responder-name"
                  label={t('circle.fullName')}
                  placeholder={t('circle.fullNamePlaceholder')}
                  value={form.responderName}
                  onChange={(e) => {
                    setForm({ ...form, responderName: e.target.value });
                    setFieldErrors((f) => ({ ...f, responderName: '' }));
                  }}
                  autoComplete="name"
                  error={fieldErrors.responderName}
                  aria-required="true"
                />

                <Input
                  id="responder-phone"
                  type="tel"
                  label={t('circle.phone')}
                  placeholder={t('circle.phonePlaceholder')}
                  value={form.responderPhone}
                  onChange={(e) => {
                    setForm({ ...form, responderPhone: e.target.value });
                    setFieldErrors((f) => ({ ...f, responderPhone: '' }));
                  }}
                  autoComplete="tel"
                  error={fieldErrors.responderPhone}
                  aria-required="true"
                />

                <Select
                  id="responder-blood-type"
                  label={t('circle.bloodType')}
                  options={bloodTypeOptions}
                  value={form.bloodType}
                  onChange={(e) => setForm({ ...form, bloodType: e.target.value })}
                />

                <Textarea
                  id="responder-notes"
                  rows={3}
                  label={t('circle.notes')}
                  placeholder={t('circle.notesPlaceholder')}
                  value={form.notes}
                  onChange={(e) => setForm({ ...form, notes: e.target.value })}
                />

                <Button type="submit" size="lg" className="w-full" loading={submitting}>
                  {t('circle.submit')}
                </Button>
              </form>
            </Reveal>
          </div>
        )}
      </div>

      <footer className="border-t border-line px-5 py-6 text-center">
        <p className="mx-auto max-w-xl text-xs leading-relaxed text-gray-400">{t('circle.footer')}</p>
      </footer>
    </main>
  );
}
