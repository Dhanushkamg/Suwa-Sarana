'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { Logo } from '@/components/brand/Logo';
import { Reveal } from '@/components/motion/Reveal';
import { Button } from '@/components/ui/Button';
import { linkButtonClass } from '@/components/pub/linkButton';
import { useI18n } from '@/lib/i18n';

export default function Error({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  const { t } = useI18n();

  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="flex min-h-screen flex-col bg-background">
      <header className="mx-auto w-full max-w-6xl px-5 py-6 sm:px-8">
        <Logo />
      </header>

      <div className="mx-auto flex w-full max-w-6xl flex-1 items-center px-5 pb-20 sm:px-8">
        <Reveal y={20} className="max-w-xl">
          <p className="eyebrow mb-6 flex items-center gap-2.5">
            <span aria-hidden="true" className="h-px w-8 bg-ember" />
            {t('pub.errorEyebrow')}
          </p>
          <h1 className="font-display break-words text-5xl text-foreground sm:text-6xl lg:text-7xl">{t('errors.title')}</h1>
          <p role="alert" className="mt-6 max-w-md text-base leading-relaxed text-gray-400 sm:text-lg">
            {t('errors.description')}
          </p>
          <div className="mt-10 flex flex-wrap gap-3">
            <Button size="lg" onClick={() => retry()}>
              {t('errors.retry')}
            </Button>
            <Link href="/" className={linkButtonClass('secondary', 'lg')}>
              {t('common.backToHome')}
            </Link>
          </div>
        </Reveal>
      </div>
    </main>
  );
}
