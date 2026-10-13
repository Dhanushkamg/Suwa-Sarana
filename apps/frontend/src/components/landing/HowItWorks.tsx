'use client';

import { useRef } from 'react';
import { motion, useReducedMotion, useScroll, useTransform } from 'motion/react';
import { useI18n } from '@/lib/i18n';
import { Reveal } from '@/components/motion/Reveal';
import { H2_CLASS, PillLink, SectionEyebrow } from './shared';
import styles from './landing.module.css';

const STEPS = [
  { n: '01', tag: 'landing.step1Tag', title: 'howItWorks.step1Title', desc: 'howItWorks.step1Desc' },
  { n: '02', tag: 'landing.step2Tag', title: 'howItWorks.step2Title', desc: 'howItWorks.step2Desc' },
  { n: '03', tag: 'landing.step3Tag', title: 'howItWorks.step3Title', desc: 'howItWorks.step3Desc' },
  { n: '04', tag: 'landing.step4Tag', title: 'landing.step4Title', desc: 'landing.step4Desc' },
];

export function HowItWorks() {
  const { t } = useI18n();
  const reduce = useReducedMotion();
  const listRef = useRef<HTMLOListElement>(null);

  // The ember rail fills as the steps scroll past (transform only)
  const { scrollYProgress } = useScroll({ target: listRef, offset: ['start 70%', 'end 55%'] });
  const fill = useTransform(scrollYProgress, [0, 1], [0, 1]);

  return (
    <section id="how-it-works" className="scroll-mt-24 px-4 py-24 sm:px-6 lg:py-32">
      <div className="mx-auto grid max-w-6xl grid-cols-1 gap-12 lg:grid-cols-12 lg:gap-16">
        {/* Sticky title column on desktop, plain heading on mobile */}
        <div className="lg:col-span-5">
          <div className="lg:sticky lg:top-32">
            <Reveal>
              <SectionEyebrow>{t('landing.howEyebrow')}</SectionEyebrow>
              <h2 className={`${H2_CLASS} mt-6`}>{t('howItWorks.title')}</h2>
              <p className="mt-6 max-w-sm text-base leading-relaxed text-gray-400 sm:text-lg">{t('howItWorks.subtitle')}</p>
              <div className="mt-8 hidden lg:block">
                <PillLink href="/register" variant="secondary">
                  {t('nav.donateBlood')}
                </PillLink>
              </div>
            </Reveal>
          </div>
        </div>

        <ol ref={listRef} className="relative lg:col-span-7 lg:pl-12">
          {/* rail + fill (desktop only) */}
          <span aria-hidden="true" className="absolute bottom-0 left-0 top-0 hidden w-px bg-line lg:block" />
          <motion.span
            aria-hidden="true"
            style={{ scaleY: reduce ? 1 : fill }}
            className="absolute bottom-0 left-0 top-0 hidden w-px origin-top bg-ember lg:block"
          />

          {STEPS.map((s) => (
            <li key={s.n} className="group/li relative border-t border-line py-10 first:border-t-0 first:pt-0 last:pb-0">
              <span
                aria-hidden="true"
                className="absolute -left-12 top-12 hidden h-2 w-2 -translate-x-[3.5px] rounded-full bg-ember group-first/li:top-2 lg:block"
              />
              <Reveal>
                <div className="grid grid-cols-[3.25rem_1fr] gap-x-4 sm:grid-cols-[5.5rem_1fr] sm:gap-x-6">
                  <span className="font-display text-5xl leading-none text-gray-700 sm:text-7xl" aria-hidden="true">
                    {s.n}
                  </span>
                  <div className="min-w-0">
                    <p className={`eyebrow ${styles.scriptFix}`}>
                      <span className="text-ember">{t(s.tag)}</span>
                    </p>
                    <h3 className="font-display mt-3 break-words text-3xl text-foreground sm:text-4xl">{t(s.title)}</h3>
                    <p className="mt-4 max-w-xl text-base leading-relaxed text-gray-400">{t(s.desc)}</p>
                  </div>
                </div>
              </Reveal>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
