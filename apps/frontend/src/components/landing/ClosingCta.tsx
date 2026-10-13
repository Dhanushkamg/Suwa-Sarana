'use client';

import { ArrowRight } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { Reveal } from '@/components/motion/Reveal';
import { ACCENT_CLASS, CTA_CLASS, PillLink, SectionEyebrow, splitLastWord } from './shared';
import styles from './landing.module.css';

export function ClosingCta() {
  const { t } = useI18n();
  const [lead, last] = splitLastWord(t('cta.title'));

  return (
    <section className="relative isolate overflow-x-clip px-4 py-28 sm:px-6 lg:py-40">
      <div aria-hidden="true" className={`${styles.ctaWash} absolute inset-0 -z-10`} />
      <div aria-hidden="true" className={`${styles.dotGrid} absolute inset-0 -z-20 opacity-60`} />

      <Reveal className="mx-auto max-w-5xl text-center">
        <SectionEyebrow className="justify-center">{t('landing.ctaEyebrow')}</SectionEyebrow>
        <h2 className={`${CTA_CLASS} mt-8`}>
          {lead} <span className={ACCENT_CLASS}>{last}</span>
        </h2>
        <p className="mx-auto mt-8 max-w-xl text-base leading-relaxed text-gray-400 sm:text-lg">{t('cta.subtitle')}</p>
        <div className="mt-10 flex flex-col items-stretch justify-center gap-3 sm:flex-row sm:items-center">
          <PillLink href="/register" size="lg">
            {t('cta.button')}
            <ArrowRight className="h-4 w-4 transition-transform duration-300 group-hover/btn:translate-x-0.5" aria-hidden="true" />
          </PillLink>
          <PillLink href="/register?role=REQUESTER" variant="secondary" size="lg">
            {t('hero.requestBlood')}
          </PillLink>
        </div>
      </Reveal>
    </section>
  );
}
