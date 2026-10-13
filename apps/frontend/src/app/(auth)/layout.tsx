'use client';

import Link from 'next/link';
import { motion, useReducedMotion } from 'motion/react';
import { ArrowLeft } from 'lucide-react';
import { Logo } from '@/components/brand/Logo';
import { SriLankaMap } from '@/components/brand/SriLankaMap';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { useI18n } from '@/lib/i18n';

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  const { t } = useI18n();
  const reduce = useReducedMotion();

  const stats = [
    { value: '25', label: t('authFlow.statDistricts') },
    { value: '8', label: t('authFlow.statBloodTypes') },
    { value: '3', label: t('authFlow.statLanguages') },
  ];

  return (
    <div className="min-h-screen bg-background lg:grid lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)]">
      {/* Left: calm editorial panel (desktop only) */}
      <aside className="relative hidden overflow-hidden border-r border-line bg-surface lg:flex lg:flex-col lg:justify-between lg:p-12 xl:p-16">
        {/* Dot-matrix island, breathing slowly behind the copy */}
        <motion.div
          aria-hidden="true"
          className="absolute -right-12 top-1/2 h-[min(88vh,860px)] -translate-y-1/2 xl:right-4"
          initial={reduce ? false : { opacity: 0 }}
          animate={reduce ? { opacity: 0.5 } : { opacity: [0.35, 0.6, 0.35] }}
          transition={reduce ? undefined : { duration: 9, ease: 'easeInOut', repeat: Infinity }}
        >
          <SriLankaMap className="h-full" showLabels={false} />
        </motion.div>

        <div className="relative z-10">
          <Logo />
        </div>

        <div className="relative z-10 max-w-md">
          <motion.p
            className="eyebrow mb-6 flex items-center gap-2.5"
            initial={reduce ? false : { opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1], delay: 0.1 }}
          >
            <span aria-hidden="true" className="h-px w-6 bg-ember" />
            {t('pub.authEyebrow')}
          </motion.p>
          <motion.p
            className="font-display text-5xl text-foreground xl:text-6xl"
            initial={reduce ? false : { opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 1, ease: [0.22, 1, 0.36, 1], delay: 0.2 }}
          >
            {t('pub.authHeadline')}
          </motion.p>
          <motion.p
            className="mt-6 max-w-sm text-[15px] leading-relaxed text-gray-400"
            initial={reduce ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 1, delay: 0.5 }}
          >
            {t('authFlow.tagline')}
          </motion.p>
        </div>

        <dl className="relative z-10 grid max-w-md grid-cols-3 gap-6 border-t border-line pt-6">
          {stats.map((stat) => (
            <div key={stat.label}>
              <dt className="eyebrow">{stat.label}</dt>
              <dd className="mt-2 font-display text-4xl text-foreground">{stat.value}</dd>
            </div>
          ))}
        </dl>
      </aside>

      {/* Right: the form */}
      <div className="relative flex min-h-screen flex-col px-5 sm:px-10 lg:px-16">
        <header className="flex items-center justify-between gap-3 py-5 lg:py-8">
          <Logo className="lg:hidden" />
          <Link
            href="/"
            className="group hidden items-center gap-2 text-sm text-gray-400 transition-colors hover:text-foreground lg:inline-flex"
          >
            <ArrowLeft className="h-3.5 w-3.5 transition-transform duration-300 group-hover:-translate-x-0.5" aria-hidden="true" />
            {t('authFlow.home')}
          </Link>
          <LanguageSwitcher />
        </header>

        <main className="mx-auto my-auto w-full max-w-md py-10 lg:py-14">{children}</main>

        <footer className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 border-t border-line py-5 font-mono text-[11px] uppercase tracking-[0.12em] text-gray-600">
          <span>{t('footer.rights')}</span>
          <span className="flex gap-5">
            <Link href="/privacy" className="transition-colors hover:text-gray-300">
              {t('footer.privacy')}
            </Link>
            <Link href="/terms" className="transition-colors hover:text-gray-300">
              {t('footer.terms')}
            </Link>
          </span>
        </footer>
      </div>
    </div>
  );
}
