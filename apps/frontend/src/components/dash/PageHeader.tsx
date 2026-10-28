'use client';

import { useEffect } from 'react';
import type { ReactNode } from 'react';
import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import { Rise } from './Rise';
import { EYEBROW } from './styles';

interface PageHeaderProps {
  /** Short caption above the title, e.g. "Donor profile". */
  eyebrow: string;
  title: ReactNode;
  description?: ReactNode;
  /** Buttons on the right (wrap below the title on small screens). */
  actions?: ReactNode;
  /** Chips next to the eyebrow (verification badges etc). */
  badges?: ReactNode;
  backHref?: string;
  /** Browser tab title; defaults to `title` when that is a plain string. Keep it short. */
  documentTitle?: string;
  className?: string;
}

/** Every dashboard page starts with this: caption, serif title (the page's one h1), description, actions. Also sets the tab title. */
export function PageHeader({ eyebrow, title, description, actions, badges, backHref, documentTitle, className }: PageHeaderProps) {
  const { t } = useI18n();
  const tabTitle = documentTitle ?? (typeof title === 'string' ? title : undefined);

  useEffect(() => {
    if (!tabTitle) return;
    document.title = `${tabTitle} | Suwa Sarana`;
  }, [tabTitle]);

  return (
    <Rise className={cn('mb-8 border-b border-line pb-8 md:mb-10', className)}>
      {backHref && (
        <Link
          href={backHref}
          className="mb-4 inline-flex min-h-11 items-center gap-2 rounded text-sm text-gray-400 transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <ArrowLeft className="h-4 w-4 rtl:rotate-180" aria-hidden="true" />
          {t('common.back')}
        </Link>
      )}
      <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
        <div className="min-w-0 max-w-2xl">
          <div className="mb-3 flex flex-wrap items-center gap-x-3 gap-y-2">
            <span className="flex items-center gap-2">
              <span aria-hidden="true" className="h-px w-5 bg-primary" />
              <span className={cn(EYEBROW, '!text-primary-text')}>{eyebrow}</span>
            </span>
            {badges}
          </div>
          <h1 className="font-display break-words text-4xl text-foreground sm:text-5xl">{title}</h1>
          {description && <p className="mt-3 text-base leading-relaxed text-gray-400">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-3 lg:justify-end">{actions}</div>}
      </div>
    </Rise>
  );
}
