'use client';

import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import { Logo } from '@/components/brand/Logo';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { useI18n } from '@/lib/i18n';

/** Shared top bar for public secondary pages: Logo on the left, back link + theme + language on the right. */
export function PubHeader({ backHref = '/' }: { backHref?: string }) {
  const { t } = useI18n();
  return (
    <header className="sticky top-0 z-40 border-b border-line bg-background/95">
      <div className="mx-auto flex min-h-16 max-w-6xl flex-wrap items-center justify-between gap-x-3 gap-y-2 px-4 py-2 sm:px-8">
        <Logo />
        <div className="flex items-center gap-2 sm:gap-4">
          <Link
            href={backHref}
            className="group hidden min-h-11 items-center gap-2 rounded-full px-2 text-sm text-gray-400 transition-colors hover:text-foreground sm:inline-flex"
          >
            <ArrowLeft className="h-4 w-4 transition-transform duration-300 group-hover:-translate-x-0.5" aria-hidden="true" />
            {t('common.backToHome')}
          </Link>
          <ThemeToggle />
          <LanguageSwitcher />
        </div>
      </div>
    </header>
  );
}
