'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Heart, MapPin, Activity, Droplets, Share2, Copy, Check,
  MessageSquare, Send, AlertCircle, Clock, Smartphone, Download, Hospital
} from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/Button';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { Logo } from '@/components/brand/Logo';
import { Reveal } from '@/components/motion/Reveal';
import { UrgencyBadge } from '@/components/pub/UrgencyBadge';
import { linkButtonClass } from '@/components/pub/linkButton';

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

export default function ShareCardClient({ token, initialData }: Props) {
  const { t } = useI18n();
  const [info, setInfo] = useState<CircleInfo | null>(initialData || null);
  const [loading, setLoading] = useState(!initialData);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
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

  useEffect(() => {
    if (!info?.expiresAt || info.isExpired) return;

    const calculateTimeLeft = () => {
      const difference = new Date(info.expiresAt).getTime() - new Date().getTime();
      
      if (difference <= 0) {
        setTimeLeft(t('share.expired'));
        return;
      }

      const hours = Math.floor((difference / (1000 * 60 * 60)) % 24);
      const minutes = Math.floor((difference / 1000 / 60) % 60);
      setTimeLeft(t('share.timeLeftValue', { hours, minutes }));
    };

    calculateTimeLeft();
    const timer = setInterval(calculateTimeLeft, 60000); // Update every minute

    return () => clearInterval(timer);
  }, [info?.expiresAt, info?.isExpired, t]);

  // Translate the urgency enum when a label exists, otherwise show the raw value
  const urgencyLabel = (u: string) => {
    const key = `ui.urgency.${u}`;
    const label = t(key);
    return label === key ? u : label;
  };

  const circleUrl = typeof window !== 'undefined'
    ? `${window.location.origin}/circle/${token}`
    : `${process.env.NEXT_PUBLIC_SITE_URL || 'https://suwasarana.lk'}/circle/${token}`;

  const shareText = info
    ? `🚨 EMERGENCY: ${info.unitsNeeded} Units of ${info.patientBloodType} blood needed URGENTLY at ${info.hospitalName} (${info.district}). Your donation can save a life today. Please volunteer or share this message:`
    : '🚨 EMERGENCY: Urgent blood needed. Please volunteer or share:';

  const copyLink = () => {
    navigator.clipboard.writeText(`${shareText}\n\n${circleUrl}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 3000);
  };

  const downloadImage = async () => {
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
      alert(t('share.downloadFailed'));
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
        { icon: Clock, label: t('share.timeLeft'), value: timeLeft || t('share.expired'), tone: 'text-foreground' },
        { icon: MapPin, label: t('share.location'), value: info.district, tone: 'text-foreground' },
        { icon: Droplets, label: t('share.required'), value: t('circle.unitsCount', { count: info.unitsNeeded }), tone: 'text-foreground' },
        { icon: Activity, label: t('share.status'), value: t('share.accepting'), tone: 'text-emerald-400' },
      ]
    : [];

  return (
    <main className="flex min-h-screen flex-col bg-background text-foreground">
      <header className="border-b border-line">
        <div className="mx-auto flex h-16 w-full max-w-xl items-center justify-between gap-3 px-5">
          <Logo />
          <LanguageSwitcher />
        </div>
      </header>

      <div className="mx-auto w-full max-w-xl flex-1 space-y-6 px-5 py-8 sm:py-12">
        {loading ? (
          <div role="status" className="glass-card rounded-3xl p-12 text-center">
            <div className="mx-auto mb-5 h-7 w-7 animate-spin rounded-full border-2 border-line-strong border-t-ember" aria-hidden="true" />
            <p className="text-sm text-gray-400">{t('share.loading')}</p>
          </div>
        ) : error || !info ? (
          <div role="alert" className="glass-card rounded-3xl p-8 text-center sm:p-12">
            <AlertCircle className="mx-auto mb-5 h-9 w-9 text-ember" aria-hidden="true" />
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
                  <span className="eyebrow flex items-center gap-2.5">
                    <span className="relative flex h-2 w-2" aria-hidden="true">
                      <span className="dot-ping absolute inset-0 rounded-full bg-ember" />
                      <span className="relative h-2 w-2 rounded-full bg-ember" />
                    </span>
                    {t('share.verifiedBroadcast')}
                  </span>
                  <UrgencyBadge label={urgencyLabel(info.urgency)} critical={info.urgency === 'CRITICAL'} />
                </div>

                <div className="px-6 pb-8 pt-8 sm:px-8 sm:pt-10">
                  <p
                    className="font-display text-[7.5rem] leading-[0.85] text-foreground sm:text-[10rem]"
                    aria-label={info.patientBloodType}
                  >
                    {info.patientBloodType}
                  </p>
                  <h1 className="mt-6 font-display break-words text-3xl text-foreground sm:text-4xl">
                    <span className="text-ember">{t('circle.unitsCount', { count: info.unitsNeeded })}</span>{' '}
                    {t('share.ofNeeded', { bloodType: info.patientBloodType })}
                  </h1>
                  <p className="mt-3 flex items-start gap-2.5 break-words text-base text-gray-300">
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
                        <dd className={cn('mt-2 break-words font-mono text-sm', fact.tone)}>{fact.value}</dd>
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
                        className="group flex items-center gap-3 rounded-2xl border border-line-strong px-4 py-3.5 text-sm font-medium text-foreground transition-colors hover:border-ember/50 hover:bg-ember/[0.05]"
                      >
                        <Icon className="h-4 w-4 shrink-0 text-gray-400 transition-colors group-hover:text-ember" aria-hidden="true" />
                        {c.name}
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
                    {copied ? <Check className="h-4 w-4 text-emerald-400" aria-hidden="true" /> : <Copy className="h-4 w-4" aria-hidden="true" />}
                    {copied ? t('share.copied') : t('share.copyLink')}
                  </Button>
                </div>
                <p className="sr-only" role="status">
                  {copied ? t('share.copied') : ''}
                </p>
              </section>
            </Reveal>

            <p className="text-center">
              <Link
                href={`/circle/${token}`}
                className="inline-flex items-center gap-1.5 text-sm text-gray-500 transition-colors hover:text-foreground"
              >
                {t('share.volunteerForm')} <span aria-hidden="true">→</span>
              </Link>
            </p>
          </>
        )}
      </div>

      <footer className="border-t border-line px-5 py-6 text-center">
        <p className="font-mono text-xs text-gray-400">{(process.env.NEXT_PUBLIC_SITE_URL || 'https://suwasarana.lk').replace(/^https?:\/\//, '')}</p>
        <p className="eyebrow mt-1.5">{t('share.network')}</p>
      </footer>
    </main>
  );
}
