'use client';

import type { ReactNode } from 'react';
import { useI18n } from '@/lib/i18n';
import { Reveal } from '@/components/motion/Reveal';
import { Spotlight } from '@/components/motion/Spotlight';
import { cn } from '@/lib/utils';
import { H2_CLASS, SectionEyebrow } from './shared';
import { ChatArt, CircleArt, EscalationArt, GaugeArt, GeoArt, LiveArt, ShieldArt } from './FeatureArt';

interface Card {
  id: string;
  art: ReactNode;
  title: string;
  desc: string;
  /** grid placement at md (2 cols) and lg (12 cols) */
  span: string;
  /** extra height for the illustration well on the bigger tiles */
  tall?: boolean;
}

const CARDS: Card[] = [
  { id: 'geo', art: <GeoArt />, title: 'features.geo', desc: 'features.geoDesc', span: 'md:col-span-2 lg:col-span-7', tall: true },
  { id: 'live', art: <LiveArt />, title: 'features.sse', desc: 'features.sseDesc', span: 'lg:col-span-5', tall: true },
  { id: 'esc', art: <EscalationArt />, title: 'features.escalation', desc: 'features.escalationDesc', span: 'lg:col-span-4' },
  { id: 'rel', art: <GaugeArt />, title: 'features.reliability', desc: 'features.reliabilityDesc', span: 'lg:col-span-4' },
  { id: 'ai', art: <ChatArt />, title: 'landing.aiTitle', desc: 'landing.aiDesc', span: 'md:col-span-2 lg:col-span-4' },
  { id: 'circle', art: <CircleArt />, title: 'landing.circleTitle', desc: 'landing.circleDesc', span: 'md:col-span-2 lg:col-span-7' },
  { id: 'verified', art: <ShieldArt />, title: 'landing.verifiedTitle', desc: 'landing.verifiedDesc', span: 'md:col-span-2 lg:col-span-5' },
];

export function Features() {
  const { t } = useI18n();

  return (
    <section id="features" className="scroll-mt-24 px-4 py-24 sm:px-6 lg:py-32">
      <div className="mx-auto max-w-6xl">
        <Reveal className="max-w-3xl">
          <SectionEyebrow>{t('landing.featuresEyebrow')}</SectionEyebrow>
          <h2 className={`${H2_CLASS} mt-6`}>{t('features.title')}</h2>
          <p className="mt-6 max-w-xl text-base leading-relaxed text-gray-400 sm:text-lg">{t('features.subtitle')}</p>
        </Reveal>

        <ul className="mt-14 grid grid-cols-1 gap-4 md:grid-cols-2 lg:mt-20 lg:grid-cols-12">
          {CARDS.map((c, i) => (
            <li key={c.id} className={cn('flex', c.span)}>
              <Reveal delay={(i % 3) * 0.08} className="flex w-full">
                <Spotlight className="glass-card flex w-full flex-col overflow-hidden rounded-3xl p-6 sm:p-8">
                  <div className={cn('w-full', c.tall ? 'h-40 sm:h-44' : 'h-32 sm:h-36')}>{c.art}</div>
                  <div className="mt-auto pt-10">
                    <h3 className="font-display break-words text-2xl text-foreground sm:text-3xl">{t(c.title)}</h3>
                    <p className="mt-3 break-words text-sm leading-relaxed text-gray-400 sm:text-base">{t(c.desc)}</p>
                  </div>
                </Spotlight>
              </Reveal>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
