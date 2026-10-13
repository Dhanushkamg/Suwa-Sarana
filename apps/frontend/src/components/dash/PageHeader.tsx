'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import { Rise } from './Rise';
import { EYEBROW } from './styles';

interface PageHeaderProps {
  /** Short mono caption above the title, e.g. "Donor". */
  eyebrow: string;
  title: ReactNode;
  description?: ReactNode;
  /** Buttons on the right (wrap below the title on small screens). */
  actions?: ReactNode;
  /** Chips next to the eyebrow (verification badges etc). */
  badges?: ReactNode;
  backHref?: string;
  className?: string;
}

/** Every dashboard page starts with this: mono eyebrow, serif title, muted description, actions on the right. */
export function PageHeader({ eyebrow, title, description, actions, badges, backHref, className }: PageHeaderProps) {
  const { t } = useI18n();
  return (
    <Rise className={cn('mb-8 border-b border-line pb-8 md:mb-10', className)}>
      {backHref && (
        <Link
          href={backHref}
          className="mb-5 inline-flex items-center gap-2 font-mono text-xs text-gray-500 transition-colors hover:text-foreground"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          {t('common.back')}
        </Link>
      )}
      <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
        <div className="min-w-0 max-w-2xl">
          <div className="mb-3 flex flex-wrap items-center gap-x-3 gap-y-2">
            <span className="flex items-center gap-2">
              <span aria-hidden="true" className="h-px w-5 bg-ember" />
              <span className={cn(EYEBROW, 'text-ember')}>{eyebrow}</span>
            </span>
            {badges}
          </div>
          <h1 className="font-display text-4xl text-foreground sm:text-5xl">{title}</h1>
          {description && <p className="mt-3 text-[15px] leading-relaxed text-gray-400">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2.5 lg:justify-end">{actions}</div>}
      </div>
    </Rise>
  );
}
