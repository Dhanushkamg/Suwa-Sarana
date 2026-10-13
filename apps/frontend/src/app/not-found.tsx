'use client';

import Link from 'next/link';
import { Logo } from '@/components/brand/Logo';
import { SriLankaMap } from '@/components/brand/SriLankaMap';
import { Reveal } from '@/components/motion/Reveal';
import { linkButtonClass } from '@/components/pub/linkButton';
import { useI18n } from '@/lib/i18n';

export default function NotFound() {
  const { t } = useI18n();

  return (
    <main className="relative flex min-h-screen flex-col overflow-hidden bg-background">
      {/* Faint island, off to one side */}
      <div aria-hidden="true" className="pointer-events-none absolute -right-16 top-1/2 h-[80vh] -translate-y-1/2 opacity-20 sm:right-[8%] sm:opacity-30">
        <SriLankaMap className="h-full" showLabels={false} />
      </div>

      <header className="relative z-10 mx-auto w-full max-w-6xl px-5 py-6 sm:px-8">
        <Logo />
      </header>

      <div className="relative z-10 mx-auto flex w-full max-w-6xl flex-1 items-center px-5 pb-20 sm:px-8">
        <Reveal y={20} className="max-w-xl">
          <p className="eyebrow mb-6 flex items-center gap-2.5">
            <span aria-hidden="true" className="h-px w-8 bg-ember" />
            404
          </p>
          <h1 className="font-display break-words text-6xl text-foreground sm:text-7xl lg:text-8xl">{t('errors.notFoundTitle')}</h1>
          <p className="mt-6 max-w-md text-base leading-relaxed text-gray-400 sm:text-lg">{t('errors.notFoundDescription')}</p>
          <Link href="/" className={linkButtonClass('primary', 'lg', 'mt-10')}>
            {t('common.backToHome')}
          </Link>
        </Reveal>
      </div>
    </main>
  );
}
