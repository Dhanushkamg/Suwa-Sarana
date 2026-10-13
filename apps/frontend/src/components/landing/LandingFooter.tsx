'use client';

import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import { LogoMark } from '@/components/brand/Logo';
import styles from './landing.module.css';

const LINKS = [
  { href: '/camps', key: 'nav.donationCamps' },
  { href: '/privacy', key: 'footer.privacy' },
  { href: '/terms', key: 'footer.terms' },
  { href: '/contact', key: 'footer.contact' },
  { href: '/login', key: 'nav.signIn' },
];

export function LandingFooter() {
  const { t } = useI18n();

  return (
    <footer className="overflow-x-clip border-t border-line px-4 pt-14 sm:px-6">
      <div className="mx-auto max-w-6xl">
        <div className="flex flex-col gap-10 md:flex-row md:items-start md:justify-between">
          <div className="flex max-w-xs items-start gap-3">
            <LogoMark className="mt-0.5 h-7 w-7 shrink-0" />
            <p className="text-sm leading-relaxed text-gray-400">{t('landing.footerTagline')}</p>
          </div>

          <nav aria-label="Footer">
            <ul className="flex flex-wrap gap-x-8 gap-y-3">
              {LINKS.map((l) => (
                <li key={l.href}>
                  <Link href={l.href} className="rounded text-sm text-gray-300 transition-colors hover:text-ember">
                    {t(l.key)}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </div>

      {/* Oversized wordmark: hairline outline that lights up in ember on hover */}
      <div aria-hidden="true" className={`${styles.outlineWrap} mx-auto mt-12 max-w-[96rem] select-none text-center`}>
        <span className={`${styles.outline} font-display block whitespace-nowrap pb-2 text-[20vw] min-[1360px]:text-[17rem]`}>
          Suwa <span className="italic">Sarana</span>
        </span>
      </div>

      <div className="mx-auto flex max-w-6xl flex-col gap-2 border-t border-line pb-24 pt-6 text-xs text-gray-500 sm:flex-row sm:items-center sm:justify-between sm:pb-6 sm:pr-56">
        <p>{t('footer.rights')}</p>
        <p lang="en">{t('landing.footerLanguages')}</p>
      </div>
    </footer>
  );
}
