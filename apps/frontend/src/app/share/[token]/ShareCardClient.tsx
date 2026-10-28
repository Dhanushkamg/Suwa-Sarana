'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Heart, MapPin, Droplets, Share2, Copy, Check,
  MessageSquare, Send, AlertCircle, Clock, Smartphone, Download, Hospital, BadgeCheck, CheckCircle2
} from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/Button';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { Logo } from '@/components/brand/Logo';
import { Reveal } from '@/components/motion/Reveal';
import { Alert } from '@/components/pub/Alert';
import { UrgencyBadge } from '@/components/pub/UrgencyBadge';
import { linkButtonClass } from '@/components/pub/linkButton';
import { toDate } from '@/components/pub/dates';
import { isCircleExpired, type CircleInfo } from '@/components/pub/circleData';

interface Props {
  token: string;
  initialData?: CircleInfo | null;
}

const SITE = process.env.NEXT_PUBLIC_SITE_URL || 'https://suwasarana.lk';

export default function ShareCardClient({ token, initialData }: Props) {
  const { t } = useI18n();
  const [info, setInfo] = useState<CircleInfo | null>(initialData || null);
  const [loading, setLoading] = useState(!initialData);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [copyFailed, setCopyFailed] = useState(false);
  const [downloadError, setDownloadError] = useState(false);
  const [timeLeft, setTimeLeft] = useState<string>('');

  const loadInfo = useCallback(async () => {
    if (initialData) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<{ data?: CircleInfo } & CircleInfo>(`/circle/${token}`);
      const data: CircleInfo = res.data?.data ?? res.data;
      setInfo(data);
    } catch {
      setError(t('share.notFound'));
    } finally {
      setLoading(false);
    }
  }, [token, initialData, t]);

  useEffect(() => {
    loadInfo();
  }, [loadInfo]);

  const expired = info ? isCircleExpired(info) : false;

  useEffect(() => {
    const expiresAt = toDate(info?.expiresAt);
    if (!expiresAt || expired) return;

    const calculateTimeLeft = () => {
      const difference = expiresAt.getTime() - Date.now();
      if (difference <= 0) {
        setTimeLeft(t('share.expired'));
        return;
      }
      const minutesTotal = Math.floor(difference / 60000);
      const days = Math.floor(minutesTotal / 1440);
      const hours = Math.floor((minutesTotal % 1440) / 60);
      const minutes = minutesTotal % 60;
      if (days > 0) setTimeLeft(t('pub.timeLeftDays', { days, hours }));
      else if (hours > 0) setTimeLeft(t('pub.timeLeftHours', { hours, minutes }));
      else setTimeLeft(t('pub.timeLeftMinutes', { minutes }));
    };

    calculateTimeLeft();
    const timer = setInterval(calculateTimeLeft, 60000); // Update every minute

    return () => clearInterval(timer);
  }, [info?.expiresAt, expired, t]);

  // Translate the urgency enum when a label exists, otherwise show the raw value
  const urgencyLabel = (u: string) => {
    const key = `ui.urgency.${u}`;
    const label = t(key);
    return label === key ? u : label;
  };

  const circleUrl = typeof window !== 'undefined' ? `${window.location.origin}/circle/${token}` : `${SITE}/circle/${token}`;

  const shareText = info
    ? t('pub.shareText', { units: info.unitsNeeded, bloodType: info.patientBloodType, hospital: info.hospitalName, district: info.district })
    : t('pub.shareTextGeneric');

  const copyLink = async () => {
    setCopyFailed(false);
    try {
      await navigator.clipboard.writeText(`${shareText}\n\n${circleUrl}`);
      setCopied(true);
      setTimeout(() => setCopied(false), 3000);
    } catch {
      // Clipboard blocked (insecure context, denied permission): tell the user instead of failing silently
      setCopyFailed(true);
    }
  };

  const downloadImage = async () => {
    setDownloadError(false);
    try {
      const res = await fetch(`/share/${token}/opengraph-image?t=${Date.now()}`, { cache: 'no-store' });
      if (!res.ok) throw new Error('Failed to fetch image');
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `suwa-sarana-emergency.png`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err) {
      console.error('Download failed:', err);
      setDownloadError(true);
    }
  };

  const channels = [
    {
      name: 'WhatsApp',
      icon: MessageSquare,
      href: `https://api.whatsapp.com/send?text=${encodeURIComponent(shareText + ' ' + circleUrl)}`,
    },
    {
      name: 'Facebook',
      icon: Share2,
      href: `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(circleUrl)}`,
    },
    {
      name: 'Telegram',
      icon: Send,
      href: `https://t.me/share/url?url=${encodeURIComponent(circleUrl)}&text=${encodeURIComponent(shareText)}`,
    },
    {
      name: 'Viber',
      icon: Smartphone,
      href: `viber://forward?text=${encodeURIComponent(shareText + ' ' + circleUrl)}`,
    },
  ];

  const facts = info
    ? [
        { icon: Clock, label: t('share.timeLeft'), value: expired ? t('share.expired') : timeLeft || t('share.expired'), tone: 'text-foreground' },
        { icon: MapPin, label: t('share.location'), value: info.district, tone: 'text-foreground' },
        { icon: Droplets, label: t('share.required'), value: t('pub.unitsCount', { count: info.unitsNeeded }), tone: 'text-foreground' },
        {
          icon: expired ? AlertCircle : CheckCircle2,
          label: t('share.status'),
          value: expired ? t('share.expired') : t('share.accepting'),
          tone: expired ? 'text-danger-text' : 'text-success-text',
        },
      ]
    : [];

  return (
    <main id="main-content" className="flex min-h-screen flex-col bg-background text-foreground">
      <header className="border-b border-line">
        <div className="mx-auto flex min-h-16 w-full max-w-xl flex-wrap items-center justify-between gap-x-3 gap-y-2 px-4 py-2">
          <Logo />
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <LanguageSwitcher />
          </div>
        </div>
      </header>

      <div className="mx-auto w-full max-w-xl flex-1 space-y-6 px-5 py-8 sm:py-12">
        {loading ? (
          <div role="status" className="glass-card rounded-3xl p-12 text-center">
            <div className="mx-auto mb-5 h-7 w-7 animate-spin motion-reduce:animate-none rounded-full border-2 border-line-strong border-t-primary" aria-hidden="true" />
            <p className="text-sm text-gray-400">{t('share.loading')}</p>
          </div>
        ) : error || !info ? (
          <div role="alert" className="glass-card rounded-3xl p-8 text-center sm:p-12">
            <AlertCircle className="mx-auto mb-5 h-9 w-9 text-danger-text" aria-hidden="true" />
            <h1 className="font-display text-4xl text-foreground">{t('share.cardUnavailable')}</h1>
            {error && <p className="mx-auto mt-4 max-w-sm text-sm leading-relaxed text-gray-400">{error}</p>}
            <Link href="/" className={linkButtonClass('secondary', 'md', 'mt-8')}>
              {t('circle.goToHome')}
            </Link>
          </div>
        ) : (
          <>
            {/* The appeal */}
            <Reveal y={20}>
              <article className="glass-card overflow-hidden rounded-3xl">
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-6 py-4 sm:px-8">
                  <span className="flex flex-wrap items-center gap-2 text-sm font-medium text-foreground">
                    {t('pub.appealLabel')}
                    {/* Only claim "verified" when the API says so */}
                    {info.requesterVerified === true && (
                      <span className="inline-flex items-center gap-1.5 rounded-full border border-success/30 bg-success-soft px-2.5 py-0.5 text-xs font-semibold text-success-text">
                        <BadgeCheck className="h-3.5 w-3.5" aria-hidden="true" />
                        {t('pub.verifiedRequester')}
                      </span>
                    )}
                  </span>
                  <UrgencyBadge label={urgencyLabel(info.urgency)} critical={info.urgency === 'CRITICAL'} />
                </div>

                <div className="px-6 pb-8 pt-8 sm:px-8 sm:pt-10">
                  {/* The blood type is one of the places the brand crimson is used */}
                  <p className="font-display text-[7.5rem] leading-[0.9] text-brand sm:text-[10rem]" aria-label={info.patientBloodType}>
                    {info.patientBloodType}
                  </p>
                  <h1 className="mt-6 font-display break-words text-3xl leading-snug text-foreground sm:text-4xl">
                    <span className="text-primary-text">{t('pub.unitsCount', { count: info.unitsNeeded })}</span>{' '}
                    {t('share.ofNeeded', { bloodType: info.patientBloodType })}
                  </h1>
                  <p className="mt-3 flex items-start gap-2.5 break-words text-base text-foreground">
                    <Hospital className="mt-1 h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
                    {info.hospitalName}
                  </p>
                </div>

                <dl className="grid grid-cols-2 border-t border-line">
                  {facts.map((fact, i) => {
                    const Icon = fact.icon;
                    return (
                      <div
                        key={fact.label}
                        className={cn('px-6 py-5 sm:px-8', i % 2 === 0 && 'border-r border-line', i < 2 && 'border-b border-line')}
                      >
                        <dt className="eyebrow flex items-center gap-2">
                          <Icon className="h-3.5 w-3.5 shrink-0 text-gray-500" aria-hidden="true" />
                          <span className="min-w-0">{fact.label}</span>
                        </dt>
                        <dd className={cn('mt-2 break-words text-sm font-medium', fact.tone)}>{fact.value}</dd>
                      </div>
                    );
                  })}
                </dl>

                <div className="border-t border-line px-6 pb-8 pt-7 sm:px-8">
                  <p className="font-display text-xl italic leading-snug text-gray-400">{t('share.quote')}</p>
                  <Link href={`/circle/${token}`} className={linkButtonClass('primary', 'lg', 'mt-7 w-full')}>
                    <Heart className="h-4 w-4" aria-hidden="true" />
                    {t('share.volunteerBtn')}
                  </Link>
                </div>
              </article>
            </Reveal>

            {/* Share actions */}
            <Reveal y={20} delay={0.1}>
              <section className="glass-card rounded-3xl p-6 sm:p-8" aria-labelledby="share-title">
                <p className="eyebrow mb-3">{t('pub.shareEyebrow')}</p>
                <h2 id="share-title" className="font-display text-3xl text-foreground">
                  {t('share.shareTitle')}
                </h2>
                <p className="mt-2 text-sm leading-relaxed text-gray-400">{t('share.shareDesc')}</p>

                <div className="mt-6 grid grid-cols-2 gap-3">
                  {channels.map((c) => {
                    const Icon = c.icon;
                    return (
                      <a
                        key={c.name}
                        href={c.href}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="group flex min-h-11 items-center gap-3 rounded-2xl border border-line-strong px-4 py-3.5 text-sm font-medium text-foreground transition-colors hover:border-primary/50 hover:bg-primary-soft"
                      >
                        <Icon className="h-4 w-4 shrink-0 text-gray-400 transition-colors group-hover:text-primary-text" aria-hidden="true" />
                        {c.name}
                        <span className="sr-only">{t('pub.opensNewTab')}</span>
                      </a>
                    );
                  })}
                </div>

                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <Button type="button" variant="secondary" size="lg" onClick={downloadImage} className="w-full">
                    <Download className="h-4 w-4" aria-hidden="true" />
                    {t('share.downloadImage')}
                  </Button>
                  <Button type="button" variant="secondary" size="lg" onClick={copyLink} className="w-full">
                    {copied ? <Check className="h-4 w-4 text-success-text" aria-hidden="true" /> : <Copy className="h-4 w-4" aria-hidden="true" />}
                    {copied ? t('share.copied') : t('share.copyLink')}
                  </Button>
                </div>
                {downloadError && <Alert className="mt-4">{t('share.downloadFailed')}</Alert>}
                {copyFailed && <Alert className="mt-4">{t('pub.copyFailed')}</Alert>}
                <p className="sr-only" role="status">
                  {copied ? t('share.copied') : ''}
                </p>
              </section>
            </Reveal>

            <p className="text-center">
              <Link
                href={`/circle/${token}`}
                className="inline-flex min-h-11 items-center gap-1.5 text-sm text-gray-400 transition-colors hover:text-foreground"
              >
                {t('share.volunteerForm')} <span aria-hidden="true">→</span>
              </Link>
            </p>
          </>
        )}
      </div>

      <footer className="border-t border-line px-5 py-6 text-center">
        <p className="text-xs text-gray-400">{SITE.replace(/^https?:\/\//, '')}</p>
        <p className="eyebrow mt-1.5">{t('share.network')}</p>
      </footer>
    </main>
  );
}
