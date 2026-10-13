'use client';

import type { ReactNode } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { ArrowRight, MapPin, MessageSquareText, Radar } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { SriLankaMap } from '@/components/brand/SriLankaMap';
import { ACCENT_CLASS, H1_CLASS, PillLink, SCRIPT_RESET } from './shared';
import styles from './landing.module.css';

const EASE = [0.22, 1, 0.36, 1] as const;

const LINES: { key: string; accent?: boolean }[][] = [
  [{ key: 'hero.headline1' }, { key: 'hero.headline2', accent: true }, { key: 'hero.headline3' }],
  [{ key: 'hero.headline4' }, { key: 'hero.headline5' }, { key: 'hero.headline6' }],
];

/** A small mono caption that drifts gently around the map. Transform-only; static for reduced-motion users. */
function FloatChip({
  icon,
  children,
  className,
  delay = 0,
  drift = 8,
}: {
  icon: ReactNode;
  children: ReactNode;
  className?: string;
  delay?: number;
  drift?: number;
}) {
  const reduce = useReducedMotion();
  return (
    <motion.div
      className={`glass-card absolute z-10 flex max-w-[11.5rem] items-start gap-2 rounded-2xl px-3 py-2.5 sm:max-w-[13rem] ${className ?? ''}`}
      initial={reduce ? false : { opacity: 0, y: 14 }}
      animate={reduce ? { opacity: 1 } : { opacity: 1, y: [0, -drift, 0] }}
      transition={
        reduce
          ? undefined
          : {
              opacity: { duration: 0.8, delay: 0.9 + delay, ease: EASE },
              y: { duration: 6 + delay * 2, delay: 0.9 + delay, repeat: Infinity, ease: 'easeInOut' },
            }
      }
    >
      <span className="mt-0.5 shrink-0 text-ember" aria-hidden="true">
        {icon}
      </span>
      <span className={`font-mono text-[10.5px] uppercase leading-snug tracking-[0.1em] text-gray-300 sm:text-[11px] ${SCRIPT_RESET}`}>{children}</span>
    </motion.div>
  );
}

export function Hero() {
  const { t } = useI18n();
  const reduce = useReducedMotion();

  // Flatten the headline into words (with a running index) so each one can reveal in sequence
  const words = LINES.flatMap((line, li) =>
    line.flatMap((part) =>
      t(part.key)
        .split(/\s+/)
        .filter(Boolean)
        .map((text, wi) => ({ text, line: li, accent: !!part.accent, id: `${part.key}-${wi}` }))
    )
  ).map((w, index) => ({ ...w, index }));

  return (
    <section className="relative isolate overflow-x-clip px-4 pb-16 pt-12 sm:px-6 sm:pb-24 sm:pt-16 lg:pt-20">
      <div aria-hidden="true" className={`${styles.dotGrid} absolute inset-x-0 top-0 -z-20 h-[44rem]`} />

      <div className="mx-auto grid max-w-6xl grid-cols-1 items-center gap-14 lg:grid-cols-12 lg:gap-8">
        <div className="lg:col-span-7">
          <motion.p
            initial={reduce ? false : { opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, ease: EASE }}
            className={`eyebrow ${styles.scriptFix} mb-7 inline-flex items-center gap-2.5 rounded-full border border-line px-3.5 py-1.5`}
          >
            <span className="relative flex h-2 w-2" aria-hidden="true">
              <span className="dot-ping absolute inset-0 rounded-full bg-ember" />
              <span className="relative h-2 w-2 rounded-full bg-ember" />
            </span>
            {t('hero.badge')}
          </motion.p>

          <h1 className={H1_CLASS}>
            {[0, 1].map((li) => (
              <span key={li} className={`block ${li === 1 ? 'text-gray-400' : ''}`}>
                {words
                  .filter((w) => w.line === li)
                  .map((w) => (
                    <span key={w.id}>
                      <motion.span
                        className={`inline-block ${w.accent ? ACCENT_CLASS : ''}`}
                        initial={reduce ? false : { opacity: 0, y: '0.45em' }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 1, ease: EASE, delay: 0.1 + w.index * 0.09 }}
                      >
                        {w.text}
                      </motion.span>{' '}
                    </span>
                  ))}
              </span>
            ))}
          </h1>

          <motion.p
            initial={reduce ? false : { opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.9, ease: EASE, delay: 0.75 }}
            className="mt-8 max-w-xl text-base leading-relaxed text-gray-400 sm:text-lg"
          >
            {t('hero.subtitle')}
          </motion.p>

          <motion.div
            initial={reduce ? false : { opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.9, ease: EASE, delay: 0.9 }}
            className="mt-10 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center"
          >
            <PillLink href="/register" size="lg">
              {t('hero.registerDonor')}
              <ArrowRight className="h-4 w-4 transition-transform duration-300 group-hover/btn:translate-x-0.5" aria-hidden="true" />
            </PillLink>
            <PillLink href="/register?role=REQUESTER" variant="secondary" size="lg">
              {t('hero.requestBlood')}
            </PillLink>
          </motion.div>
        </div>

        {/* Signature element: the island, its cities and a few drifting captions */}
        <div className="relative mx-auto w-full max-w-[22rem] sm:max-w-sm lg:col-span-5 lg:max-w-none">
          <div
            aria-hidden="true"
            className={`${styles.emberLight} pointer-events-none absolute left-1/2 top-1/2 -z-10 aspect-square w-[150%] -translate-x-1/2 -translate-y-1/2`}
          />

          <motion.div
            initial={reduce ? false : { opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 1.4, ease: EASE, delay: 0.2 }}
            className="mx-auto w-[72%] sm:w-[19rem] lg:w-[20rem] xl:w-[22rem]"
          >
            <SriLankaMap />
          </motion.div>

          <FloatChip icon={<MapPin className="h-3.5 w-3.5" />} className="left-0 top-[14%] lg:-left-16" delay={0} drift={8}>
            {t('landing.heroChipNearest')}
          </FloatChip>
          <FloatChip icon={<Radar className="h-3.5 w-3.5" />} className="right-0 top-[60%] lg:top-[44%] lg:-right-4 xl:-right-10" delay={0.6} drift={10}>
            {t('landing.heroChipRadius')}
          </FloatChip>
          <FloatChip icon={<MessageSquareText className="h-3.5 w-3.5" />} className="bottom-[6%] left-0 lg:-left-2 xl:-left-8" delay={1.2} drift={7}>
            {t('landing.heroChipSms')}
          </FloatChip>
        </div>
      </div>
    </section>
  );
}
