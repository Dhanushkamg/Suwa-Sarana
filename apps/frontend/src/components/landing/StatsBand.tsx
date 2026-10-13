'use client';

import type { ReactNode } from 'react';
import { useI18n } from '@/lib/i18n';
import { Reveal } from '@/components/motion/Reveal';
import { AnimatedNumber } from '@/components/motion/AnimatedNumber';
import { StockTicker } from '@/components/ui/StockTicker';
import { LanguageStack } from './FeatureArt';
import { H2_CLASS, SectionEyebrow } from './shared';

const NUMBER = 'font-display block text-6xl text-foreground sm:text-7xl lg:text-8xl';

function Tile({ index, label, children, footer }: { index: string; label: string; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="flex flex-col justify-between gap-8 bg-surface p-6 sm:p-8">
      <div className="flex items-start justify-between gap-4">
        <span className="font-mono text-xs text-gray-600" aria-hidden="true">
          {index}
        </span>
        {footer}
      </div>
      <div>
        {children}
        <p className="mt-3 text-sm leading-snug text-gray-400">{label}</p>
      </div>
    </div>
  );
}

/** Only figures that are true by construction: 25 districts, 8 blood types, 3 languages, plus a live-alerts tile. */
export function StatsBand() {
  const { t } = useI18n();

  return (
    <section id="stats" className="scroll-mt-24 px-4 py-24 sm:px-6 lg:py-32">
      <div className="mx-auto max-w-6xl">
        <Reveal className="max-w-3xl">
          <SectionEyebrow>{t('landing.statsEyebrow')}</SectionEyebrow>
          <h2 className={`${H2_CLASS} mt-6`}>{t('landing.statsTitle')}</h2>
        </Reveal>

        <Reveal className="mt-14 lg:mt-20">
          <div className="overflow-hidden rounded-3xl border border-line bg-surface">
            {/* Real shortages from the public inventory endpoint; renders nothing when there are none */}
            <StockTicker className="border-b border-line" />

            <div className="grid grid-cols-1 gap-px bg-line sm:grid-cols-2 lg:grid-cols-4">
              <Tile index="01" label={t('stats.districtsSupported')}>
                <AnimatedNumber value={25} className={NUMBER} />
              </Tile>
              <Tile index="02" label={t('stats.bloodTypes')}>
                <AnimatedNumber value={8} className={NUMBER} />
              </Tile>
              <Tile index="03" label={t('stats.languages')} footer={<LanguageStack />}>
                <AnimatedNumber value={3} className={NUMBER} />
              </Tile>
              <Tile
                index="04"
                label={t('stats.liveAlerts')}
                footer={
                  <span className="relative mt-1 flex h-2.5 w-2.5" aria-hidden="true">
                    <span className="dot-ping absolute inset-0 rounded-full bg-ember" />
                    <span className="relative h-2.5 w-2.5 rounded-full bg-ember" />
                  </span>
                }
              >
                <span className={`${NUMBER} break-words italic text-ember [:lang(si)_&]:not-italic [:lang(ta)_&]:not-italic`}>{t('stats.liveValue')}</span>
              </Tile>
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
