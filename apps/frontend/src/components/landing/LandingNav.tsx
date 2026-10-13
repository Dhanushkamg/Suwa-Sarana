'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { AnimatePresence, motion, useReducedMotion } from 'motion/react';
import { Menu, X } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { Logo } from '@/components/brand/Logo';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { PillLink } from './shared';

const LINKS = [
  { href: '#how-it-works', key: 'nav.howItWorks' },
  { href: '#features', key: 'nav.features' },
  { href: '/camps', key: 'nav.donationCamps' },
  { href: '#stats', key: 'nav.impact' },
];

const EASE = [0.22, 1, 0.36, 1] as const;

export function LandingNav() {
  const { t } = useI18n();
  const reduce = useReducedMotion();
  const [open, setOpen] = useState(false);

  // Lock page scroll while the full-screen menu is open, and let Escape close it
  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.body.style.overflow = prev;
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const close = () => setOpen(false);

  return (
    <header className="sticky top-0 z-[60] px-3 pt-3 sm:px-6 sm:pt-4">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-10 focus:rounded-full focus:bg-ember focus:px-4 focus:py-2 focus:text-sm focus:text-white"
      >
        {t('landing.skip')}
      </a>

      <nav
        aria-label="Primary"
        className="relative z-10 mx-auto flex max-w-6xl items-center justify-between gap-3 rounded-full border border-line-strong bg-background/75 py-2 pl-4 pr-2 backdrop-blur-md sm:pl-5"
      >
        <Logo />

        <ul className="hidden items-center gap-1 xl:flex">
          {LINKS.map((l) => (
            <li key={l.href}>
              <Link
                href={l.href}
                className="rounded-full px-3.5 py-2 text-sm text-gray-400 transition-colors hover:bg-foreground/[0.06] hover:text-foreground"
              >
                {t(l.key)}
              </Link>
            </li>
          ))}
        </ul>

        <div className="hidden items-center gap-2 xl:flex">
          <LanguageSwitcher />
          <PillLink href="/login" variant="ghost" size="sm">
            {t('nav.signIn')}
          </PillLink>
          <PillLink href="/register" size="sm">
            {t('nav.donateBlood')}
          </PillLink>
        </div>

        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-label={t('ui.toggleMenu')}
          aria-expanded={open}
          aria-controls="mobile-menu"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-line-strong text-foreground transition-colors hover:bg-foreground/[0.08] xl:hidden"
        >
          {open ? <X className="h-5 w-5" aria-hidden="true" /> : <Menu className="h-5 w-5" aria-hidden="true" />}
        </button>
      </nav>

      <AnimatePresence>
        {open && (
          <motion.div
            id="mobile-menu"
            key="menu"
            initial={reduce ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={reduce ? { opacity: 0 } : { opacity: 0 }}
            transition={{ duration: 0.25, ease: EASE }}
            className="fixed inset-0 z-0 flex flex-col overflow-y-auto bg-background px-6 pb-10 pt-28 xl:hidden"
          >
            <ul className="flex flex-col">
              {LINKS.map((l, i) => (
                <motion.li
                  key={l.href}
                  initial={reduce ? false : { opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.6, ease: EASE, delay: 0.08 + i * 0.07 }}
                  className="border-b border-line"
                >
                  <Link
                    href={l.href}
                    onClick={close}
                    className="flex items-baseline gap-4 py-5 font-display text-4xl text-foreground transition-colors hover:text-ember sm:text-5xl"
                  >
                    <span className="font-mono text-xs text-ember">0{i + 1}</span>
                    {t(l.key)}
                  </Link>
                </motion.li>
              ))}
            </ul>

            <div className="mt-auto flex flex-col gap-3 pt-10">
              <div className="flex items-center justify-between gap-3">
                <LanguageSwitcher />
              </div>
              <PillLink href="/login" variant="secondary" size="lg" onClick={close} className="w-full">
                {t('nav.signIn')}
              </PillLink>
              <PillLink href="/register" size="lg" onClick={close} className="w-full">
                {t('nav.donateBlood')}
              </PillLink>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
}
