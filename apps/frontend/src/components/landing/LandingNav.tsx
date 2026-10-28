'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { AnimatePresence, motion, useReducedMotion } from 'motion/react';
import { Menu, X } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { Logo } from '@/components/brand/Logo';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { PillLink } from './shared';

const LINKS = [
  { href: '#how-it-works', key: 'nav.howItWorks' },
  { href: '#features', key: 'nav.features' },
  { href: '/camps', key: 'nav.donationCamps' },
  { href: '#stats', key: 'nav.impact' },
];

const EASE = [0.22, 1, 0.36, 1] as const;

/**
 * Contract with other floating UI (the chat assistant): while a full-screen mobile menu is open, `<body data-nav-open>`
 * is set so those widgets can get out of the way. The dashboard drawer should set the same attribute.
 */
const NAV_OPEN_ATTR = 'data-nav-open';

export function LandingNav() {
  const { t } = useI18n();
  const reduce = useReducedMotion();
  const [open, setOpen] = useState(false);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  // Full-screen menu: lock page scroll, make the page behind it inert, move focus in, let Escape close it and return focus
  useEffect(() => {
    if (!open) return;
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    document.body.setAttribute(NAV_OPEN_ATTR, 'true');
    const behind = Array.from(document.querySelectorAll<HTMLElement>('main, footer'));
    behind.forEach((el) => el.setAttribute('inert', ''));
    // Wait a frame so the menu is mounted before focusing its first link
    const raf = requestAnimationFrame(() => menuRef.current?.querySelector<HTMLElement>('a[href]')?.focus());
    const toggle = toggleRef.current;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => {
      cancelAnimationFrame(raf);
      document.body.style.overflow = prevOverflow;
      document.body.removeAttribute(NAV_OPEN_ATTR);
      behind.forEach((el) => el.removeAttribute('inert'));
      document.removeEventListener('keydown', onKey);
      toggle?.focus();
    };
  }, [open]);

  const close = () => setOpen(false);

  return (
    <header className="sticky top-0 z-[60] px-3 pt-3 sm:px-6 sm:pt-4">
      <nav
        aria-label={t('landing.navLabel')}
        className="relative z-10 mx-auto flex max-w-6xl items-center justify-between gap-3 rounded-full border border-line-strong bg-background/90 py-2 pl-4 pr-2 backdrop-blur-md sm:pl-5"
      >
        <Logo />

        <ul className="hidden items-center gap-1 xl:flex">
          {LINKS.map((l) => (
            <li key={l.href}>
              <Link
                href={l.href}
                className="inline-flex min-h-11 items-center rounded-full px-3.5 py-2 text-sm text-gray-400 transition-colors hover:bg-foreground/[0.06] hover:text-foreground"
              >
                {t(l.key)}
              </Link>
            </li>
          ))}
        </ul>

        <div className="hidden items-center gap-2 xl:flex">
          <ThemeToggle />
          <LanguageSwitcher />
          <PillLink href="/login" variant="ghost" size="sm">
            {t('nav.signIn')}
          </PillLink>
          <PillLink href="/register" size="sm">
            {t('nav.donateBlood')}
          </PillLink>
        </div>

        <button
          ref={toggleRef}
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-label={t('ui.toggleMenu')}
          aria-expanded={open}
          aria-controls="mobile-menu"
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-line-strong text-foreground transition-colors hover:bg-foreground/[0.08] xl:hidden"
        >
          {open ? <X className="h-5 w-5" aria-hidden="true" /> : <Menu className="h-5 w-5" aria-hidden="true" />}
        </button>
      </nav>

      <AnimatePresence>
        {open && (
          <motion.div
            ref={menuRef}
            id="mobile-menu"
            key="menu"
            initial={reduce ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
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
                    className="flex min-h-11 items-baseline gap-4 break-words py-5 font-display text-4xl text-foreground transition-colors hover:text-primary-text sm:text-5xl"
                  >
                    <span className="font-mono text-xs text-primary-text" aria-hidden="true">
                      0{i + 1}
                    </span>
                    {t(l.key)}
                  </Link>
                </motion.li>
              ))}
            </ul>

            <div className="mt-auto flex flex-col gap-3 pt-10">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <ThemeToggle />
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
