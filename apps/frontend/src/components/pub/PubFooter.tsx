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
        <div className="flex items-center gap-3 font-mono text-[11px] uppercase tracking-[0.12em] text-gray-600">
          <LogoMark className="h-5 w-5" />
          <span>{t('footer.rights')}</span>
        </div>
        <nav aria-label="Footer" className="flex flex-wrap gap-x-6 gap-y-2 font-mono text-[11px] uppercase tracking-[0.12em]">
          {links.map((link) => (
            <Link
              key={link.key}
              href={link.href}
              aria-current={current === link.key ? 'page' : undefined}
              className={cn('transition-colors', current === link.key ? 'text-ember' : 'text-gray-500 hover:text-foreground')}
            >
              {link.label}
            </Link>
          ))}
        </nav>
      </div>
    </footer>
  );
}
