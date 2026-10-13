'use client';

import { useEffect, useRef, useState } from 'react';
import type { ComponentType, ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LogOut, Menu, X } from 'lucide-react';
import { motion, useReducedMotion } from 'motion/react';
import { Logo } from '@/components/brand/Logo';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import type { User } from '@/types';
import { EYEBROW } from './styles';

export interface DashNavItem {
  href: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
}

interface DashShellProps {
  navItems: DashNavItem[];
  user: User;
  onLogout: () => void;
  children: ReactNode;
}

/** The nav item that best matches the URL: the longest href that equals or prefixes the path. */
function activeHref(items: DashNavItem[], pathname: string): string | null {
  let best: string | null = null;
  for (const { href } of items) {
    if ((pathname === href || pathname.startsWith(href + '/')) && (!best || href.length > best.length)) best = href;
  }
  return best;
}

/**
 * App shell: slim ink sidebar (drawer on mobile) + content area with a faint ember light at the top.
 * Auth and role guards stay in dashboard/layout.tsx; this only renders chrome.
 */
export function DashShell({ navItems, user, onLogout, children }: DashShellProps) {
  const { t } = useI18n();
  const pathname = usePathname();
  const reduce = useReducedMotion();
  const [open, setOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const current = activeHref(navItems, pathname);

  // While the drawer is open: Esc closes it, focus moves into it, and the page behind does not scroll.
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        menuButtonRef.current?.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeButtonRef.current?.focus();
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  const closeDrawer = () => setOpen(false);

  return (
    <div className="min-h-screen bg-background">
      {/* Mobile top bar */}
      <header className="sticky top-0 z-40 flex h-14 items-center justify-between border-b border-line bg-background px-4 md:hidden">
        <Logo />
        <button
          ref={menuButtonRef}
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-label={open ? t('dashUi.closeMenu') : t('dashUi.openMenu')}
          aria-expanded={open}
          aria-controls="dash-sidebar"
          className="-mr-2 flex h-11 w-11 items-center justify-center rounded-full text-gray-400 transition-colors hover:text-foreground"
        >
          {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </header>

      {/* Backdrop (mobile only) */}
      <div
        aria-hidden="true"
        onClick={closeDrawer}
        className={cn(
          'fixed inset-0 z-40 bg-black/60 transition-opacity duration-300 md:hidden',
          open ? 'opacity-100' : 'pointer-events-none opacity-0'
        )}
      />

      {/* Sidebar. Closed on mobile it is `invisible` (after the slide-out) so keyboard focus cannot land in it. */}
      <aside
        id="dash-sidebar"
        aria-label={t('dashUi.mainNavigation')}
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-[17rem] max-w-[85vw] flex-col border-r border-line bg-background transition-[transform,visibility] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] md:w-60 md:translate-x-0 md:visible',
          open ? 'visible translate-x-0' : 'invisible -translate-x-full'
        )}
      >
        <div className="flex h-16 shrink-0 items-center justify-between px-5">
          <Logo />
          <button
            ref={closeButtonRef}
            type="button"
            onClick={() => {
              closeDrawer();
              menuButtonRef.current?.focus();
            }}
            aria-label={t('dashUi.closeMenu')}
            className="-mr-2 flex h-10 w-10 items-center justify-center rounded-full text-gray-500 transition-colors hover:text-foreground md:hidden"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* User block */}
        <div className="mx-4 mb-2 mt-1 border-y border-line py-4">
          <div className="flex items-start gap-3">
            <span
              aria-hidden="true"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-foreground/[0.04] font-display text-lg leading-none text-foreground ring-1 ring-ember/60 ring-offset-2 ring-offset-background"
            >
              {user.email?.charAt(0).toUpperCase()}
            </span>
            <div className="min-w-0 flex-1">
              <p className="break-all text-[13px] leading-snug text-foreground" title={user.email}>
                {user.email}
              </p>
              <p className={cn(EYEBROW, 'mt-1.5 !text-[0.66rem] !leading-snug')}>{t(`dashUi.role.${user.role}`)}</p>
              {user.verificationStatus && (
                <StatusBadge
                  value={user.verificationStatus}
                  label={t(`dashUi.verification.${user.verificationStatus}`)}
                  className="mt-2 !px-2 !py-0 !text-[11px]"
                />
              )}
            </div>
          </div>
        </div>

        {/* Navigation */}
        <nav aria-label={t('dashUi.mainNavigation')} className="flex-1 overflow-y-auto px-3 py-3">
          <ul className="space-y-0.5">
            {navItems.map((item) => {
              const active = item.href === current;
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    onClick={closeDrawer}
                    aria-current={active ? 'page' : undefined}
                    className={cn(
                      'group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors duration-200',
                      active ? 'text-foreground' : 'text-gray-400 hover:text-foreground'
                    )}
                  >
                    {active && (
                      <>
                        <motion.span
                          layoutId="dash-nav-pill"
                          aria-hidden="true"
                          className="absolute inset-0 rounded-xl bg-foreground/[0.06]"
                          transition={reduce ? { duration: 0 } : { type: 'spring', stiffness: 420, damping: 38 }}
                        />
                        <motion.span
                          layoutId="dash-nav-tick"
                          aria-hidden="true"
                          className="absolute inset-y-2 left-0 w-[2px] rounded-full bg-ember"
                          transition={reduce ? { duration: 0 } : { type: 'spring', stiffness: 420, damping: 38 }}
                        />
                      </>
                    )}
                    <item.icon className={cn('relative h-4 w-4 shrink-0 transition-colors', active ? 'text-ember' : 'text-gray-500 group-hover:text-gray-300')} />
                    <span className="relative min-w-0 break-words leading-snug">{item.label}</span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        {/* Language + sign out. The switcher's menu is forced to open upwards so it is not clipped by the viewport. */}
        <div className="shrink-0 space-y-1 border-t border-line p-4">
          <LanguageSwitcher
            className="mb-2 [&>button]:w-full [&>button]:justify-start [&_[role=menu]]:bottom-full [&_[role=menu]]:left-0 [&_[role=menu]]:right-auto [&_[role=menu]]:top-auto [&_[role=menu]]:mb-2 [&_[role=menu]]:mt-0 [&_[role=menu]]:w-full"
          />
          <button
            type="button"
            onClick={onLogout}
            className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-gray-500 transition-colors duration-200 hover:bg-foreground/[0.04] hover:text-ember"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            {t('common.signOut')}
          </button>
        </div>
      </aside>

      {/* Content. `inert` while the mobile drawer is open so focus stays in the drawer. */}
      <main id="dash-main" inert={open} className="relative min-h-screen md:pl-60">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 top-0 h-[26rem] bg-[radial-gradient(55%_100%_at_50%_0%,color-mix(in_oklch,var(--ember)_9%,transparent),transparent)]"
        />
        <div className="relative mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 md:px-10 md:py-12">{children}</div>
      </main>
    </div>
  );
}
