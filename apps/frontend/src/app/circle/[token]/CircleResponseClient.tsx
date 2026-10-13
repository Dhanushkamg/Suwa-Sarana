'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Heart, MapPin, Droplets, CheckCircle2,
  AlertCircle, User, Sparkles, Share2
} from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { Logo } from '@/components/brand/Logo';
import { Reveal } from '@/components/motion/Reveal';
import { Alert } from '@/components/pub/Alert';
import { Textarea } from '@/components/pub/Textarea';
import { UrgencyBadge } from '@/components/pub/UrgencyBadge';
import { linkButtonClass } from '@/components/pub/linkButton';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';

interface CircleInfo {
  inviteToken: string;
  patientBloodType: string;
  unitsNeeded: number;
  urgency: string;
  hospitalName: string;
  district: string;
  expiresAt: string;
  isExpired: boolean;
}

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
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || t('circle.invalidToken');
      setError(msg);
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
    setSubmitting(true);

    try {
      await apiClient.post(`/circle/${token}/respond`, {
        responderName: form.responderName,
        responderPhone: form.responderPhone,
        bloodType: form.bloodType,
        notes: form.notes || undefined,
      });
      setSubmitted(true);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || t('circle.submitFailed');
      setFormError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen flex-col bg-background text-foreground">
      <header className="border-b border-line">
        <div className="mx-auto flex h-16 w-full max-w-xl items-center justify-between gap-3 px-5">
          <Logo />
          <div className="flex items-center gap-2">
            <Link
              href={`/share/${token}`}
              aria-label={t('circle.socialCard')}
              className="inline-flex h-9 items-center gap-2 rounded-full border border-line-strong px-3 text-xs font-medium text-gray-300 transition-colors hover:border-foreground/30 hover:text-foreground"
            >
              <Share2 className="h-3.5 w-3.5" aria-hidden="true" />
              <span className="hidden sm:inline">{t('circle.socialCard')}</span>
            </Link>
            <LanguageSwitcher />
          </div>
        </div>
      </header>

      <div className="mx-auto w-full max-w-xl flex-1 px-5 py-8 sm:py-12">
        {loading ? (
          <div role="status" className="glass-card rounded-3xl p-12 text-center">
            <div className="mx-auto mb-5 h-7 w-7 animate-spin rounded-full border-2 border-line-strong border-t-ember" aria-hidden="true" />
            <p className="text-sm text-gray-400">{t('circle.loading')}</p>
          </div>
        ) : error || !info ? (
          <div role="alert" className="glass-card rounded-3xl p-8 text-center sm:p-12">
            <AlertCircle className="mx-auto mb-5 h-9 w-9 text-ember" aria-hidden="true" />
            <h1 className="font-display text-4xl text-foreground">{t('circle.inviteUnavailable')}</h1>
            {error && <p className="mx-auto mt-4 max-w-sm text-sm leading-relaxed text-gray-400">{error}</p>}
            <Link href="/" className={linkButtonClass('secondary', 'md', 'mt-8')}>
              {t('circle.goToHome')}
            </Link>
          </div>
        ) : submitted ? (
          /* Success screen */
          <Reveal y={16}>
            <div className="glass-card rounded-3xl border-l-2 border-l-emerald-400 p-8 text-center sm:p-12">
              <span className="mx-auto mb-6 flex h-14 w-14 items-center justify-center rounded-full border border-emerald-400/30 bg-emerald-400/10 text-emerald-400">
                <CheckCircle2 className="h-7 w-7" aria-hidden="true" />
              </span>
              <h1 className="font-display break-words text-4xl text-foreground sm:text-5xl">
                {t('circle.thankYou')}, {form.responderName}
              </h1>
              <p className="mx-auto mt-5 max-w-sm text-[15px] leading-relaxed text-gray-300">
                {t('circle.successBody')} <strong className="font-mono font-medium text-foreground">{form.responderPhone}</strong>
              </p>

              <div className="mx-auto mt-8 max-w-sm rounded-2xl border border-line bg-foreground/[0.03] p-5 text-left">
                <p className="flex items-center gap-2 text-sm font-medium text-saffron">
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
                    className="shrink-0 font-display text-[5.5rem] leading-[0.85] text-foreground sm:text-[7rem]"
                    aria-label={info.patientBloodType}
                  >
                    {info.patientBloodType}
                  </p>
                  <div className="min-w-0 pt-1">
                    <h1 className="font-display break-words text-3xl text-foreground sm:text-4xl">{info.hospitalName}</h1>
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
                    <dd className="mt-2 font-mono text-sm text-foreground">{t('circle.unitsCount', { count: info.unitsNeeded })}</dd>
                  </div>
                </dl>
              </article>
            </Reveal>

            {/* Volunteer form */}
            <Reveal y={20} delay={0.1}>
              <form onSubmit={handleSubmit} className="glass-card space-y-5 rounded-3xl p-6 sm:p-8">
                <div>
                  <h2 className="flex items-center gap-3 font-display text-3xl text-foreground">
                    <User className="h-5 w-5 shrink-0 text-ember" aria-hidden="true" />
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
                  onChange={(e) => setForm({ ...form, responderName: e.target.value })}
                  autoComplete="name"
                  required
                />

                <Input
                  id="responder-phone"
                  type="tel"
                  label={t('circle.phone')}
                  placeholder={t('circle.phonePlaceholder')}
                  value={form.responderPhone}
                  onChange={(e) => setForm({ ...form, responderPhone: e.target.value })}
                  autoComplete="tel"
                  required
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
        <p className="mx-auto max-w-xl font-mono text-[11px] uppercase leading-relaxed tracking-[0.12em] text-gray-600">{t('circle.footer')}</p>
      </footer>
    </main>
  );
}
