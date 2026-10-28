'use client';

import Link from 'next/link';
import { LogoMark } from '@/components/brand/Logo';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';

type FooterPage = 'privacy' | 'terms' | 'contact';

/** Hairline footer with the legal links; `current` marks the page you are on. */
export function PubFooter({ current }: { current?: FooterPage }) {
  const { t } = useI18n();
  const links: { key: FooterPage; href: string; label: string }[] = [
    { key: 'privacy', href: '/privacy', label: t('footer.privacy') },
    { key: 'terms', href: '/terms', label: t('footer.terms') },
    { key: 'contact', href: '/contact', label: t('footer.contact') },
  ];
  return (
    <footer className="border-t border-line">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-5 py-8 sm:px-8 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-3 text-xs text-gray-500">
          <LogoMark className="h-5 w-5" />
          <span>{t('footer.rights')}</span>
        </div>
        <nav aria-label={t('pub.footerNav')} className="flex flex-wrap gap-x-2 gap-y-1 text-sm">
          {links.map((link) => (
            <Link
              key={link.key}
              href={link.href}
              aria-current={current === link.key ? 'page' : undefined}
              className={cn(
                'inline-flex min-h-11 items-center px-2 transition-colors',
                current === link.key ? 'font-medium text-primary-text' : 'text-gray-400 hover:text-foreground'
              )}
            >
              {link.label}
            </Link>
          ))}
        </nav>
      </div>
    </footer>
  );
}
