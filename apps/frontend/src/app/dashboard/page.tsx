'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import { homeForRole } from '@/lib/auth';
import { Bell, ArrowUpRight, Plus, ListOrdered } from 'lucide-react';
import Link from 'next/link';
import { SriLankaMap } from '@/components/brand/SriLankaMap';
import { Reveal } from '@/components/motion/Reveal';
import { IconTile } from '@/components/dash/IconTile';
import { PageHeader } from '@/components/dash/PageHeader';
import { Rise } from '@/components/dash/Rise';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { EYEBROW } from '@/components/dash/styles';
import { cn } from '@/lib/utils';

export default function DashboardPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const { t } = useI18n();

  useEffect(() => {
    // Only plain requesters stay here; every other role has its own home.
    const home = homeForRole(user?.role);
    if (user && home !== '/dashboard') router.replace(home);
  }, [user, router]);

  const actions = [
    {
      href: '/dashboard/requests/new',
      icon: Plus,
      title: t('dashboard.newBloodRequest'),
      desc: t('dashboard.newBloodRequestDesc'),
      primary: true,
    },
    {
      href: '/dashboard/requests',
      icon: ListOrdered,
      title: t('dashboard.activeRequests'),
      desc: t('dashboard.activeRequestsDesc'),
    },
    {
      href: '/dashboard/notifications',
      icon: Bell,
      title: t('dashboard.notifications'),
      desc: t('dashboard.notificationsDesc'),
    },
  ];

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.overview')}
        title={t('dashboard.welcome')}
        description={t('dashboard.requesterSubtitle')}
      />

      {/* Quick actions */}
      <div className="mb-6 grid gap-4 md:grid-cols-3">
        {actions.map((a, i) => (
          <Rise key={a.href} delay={0.05 + i * 0.06}>
            <Link
              href={a.href}
              className={cn(
                'glass-card group flex h-full flex-col justify-between gap-10 rounded-2xl p-6 transition-colors duration-300',
                a.primary && 'border-ember/30 hover:border-ember/60'
              )}
            >
              <div className="flex items-start justify-between">
                <IconTile size="md" tone={a.primary ? 'ember' : 'muted'}>
                  <a.icon />
                </IconTile>
                <span
                  aria-hidden="true"
                  className="flex h-8 w-8 items-center justify-center rounded-full border border-line text-gray-500 transition-[transform,color,border-color] duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:border-ember/50 group-hover:text-ember"
                >
                  <ArrowUpRight className="h-4 w-4" />
                </span>
              </div>
              <div>
                <h2 className="font-display text-2xl text-foreground">{a.title}</h2>
                <p className="mt-1.5 text-sm leading-relaxed text-gray-400">{a.desc}</p>
              </div>
            </Link>
          </Rise>
        ))}
      </div>

      {/* Platform note */}
      <Reveal y={12} delay={0.05}>
        <div className="glass-card relative overflow-hidden rounded-2xl p-6 sm:p-8">
          <div className="relative z-10 flex flex-col gap-6 md:max-w-[65%]">
            <p className={EYEBROW}>{t('dashUi.platform')}</p>
            <h2 className="font-display text-3xl text-foreground">{t('dashboard.platformStatus')}</h2>
            <p className="text-sm leading-relaxed text-gray-400">{t('dashboard.platformDesc')}</p>
            <div>
              <ButtonLink href="/dashboard/requests/new" variant="primary">
                {t('dashboard.newRequest')}
                <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
              </ButtonLink>
            </div>
          </div>
          <div aria-hidden="true" className="pointer-events-none absolute -right-4 top-1/2 hidden w-44 -translate-y-1/2 opacity-60 md:block">
            <SriLankaMap />
          </div>
        </div>
      </Reveal>
    </div>
  );
}
