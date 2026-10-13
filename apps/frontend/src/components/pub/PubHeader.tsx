'use client';

import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import { Logo } from '@/components/brand/Logo';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { useI18n } from '@/lib/i18n';

/** Shared top bar for public secondary pages: Logo on the left, back link + language switcher on the right. */
export function PubHeader({ backHref = '/' }: { backHref?: string }) {
  const { t } = useI18n();
  return (
    <header className="sticky top-0 z-40 border-b border-line bg-background/95">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-3 px-5 sm:px-8">
        <Logo />
        <div className="flex items-center gap-2 sm:gap-5">
          <Link
            href={backHref}
            aria-label={t('common.backToHome')}
            className="group inline-flex h-9 items-center gap-2 rounded-full px-2 text-sm text-gray-400 transition-colors hover:text-foreground sm:px-0"
          >
            <ArrowLeft className="h-4 w-4 transition-transform duration-300 group-hover:-translate-x-0.5" aria-hidden="true" />
            <span className="hidden sm:inline">{t('common.backToHome')}</span>
          </Link>
          <LanguageSwitcher />
        </div>
      </div>
    </header>
  );
}
