'use client';

import { useReducedMotion } from 'motion/react';
import { useI18n } from '@/lib/i18n';
import { Marquee } from '@/components/motion/Marquee';
import { DropGlyph } from './shared';

const TYPES = ['A+', 'A−', 'B+', 'B−', 'AB+', 'AB−', 'O+', 'O−'];

/** Thin ticker under the hero: the eight blood types in the display serif, separated by crimson drop glyphs. */
export function BloodTypeStrip() {
  const { t } = useI18n();
  const reduce = useReducedMotion();

  return (
    <section aria-label={t('stats.bloodTypes')} className="border-y border-line py-5 sm:py-6">
      {reduce ? (
        // Reduced motion: no scrolling ticker, so show every type at once, wrapping on small screens
        <ul className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-8 gap-y-3 px-4">
          {TYPES.map((type) => (
            <li key={type} className="flex items-center gap-4 font-display text-4xl text-foreground sm:text-5xl">
              {type}
              <DropGlyph className="h-3.5 w-3.5 text-brand" />
            </li>
          ))}
        </ul>
      ) : (
        // The shared Marquee leaves a gap between its two copies; zero it and pad each copy instead so the loop is seamless
        <Marquee className="[&_.animate-marquee-horizontal]:gap-0 [&_.animate-marquee-horizontal>div]:gap-0">
          {TYPES.map((type) => (
            <span key={type} className="flex items-center gap-10 pr-10 font-display text-4xl text-foreground sm:text-5xl">
              {type}
              <DropGlyph className="h-3.5 w-3.5 text-brand" />
            </span>
          ))}
        </Marquee>
      )}
    </section>
  );
}
