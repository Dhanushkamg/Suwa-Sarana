'use client';

import { useEffect, useRef, useState } from 'react';
import type { ComponentType, ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LogOut, Menu, X } from 'lucide-react';
import { Logo } from '@/components/brand/Logo';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import type { User } from '@/types';
import { EYEBROW } from './styles';
import { LowDataToggle } from './LowDataToggle';

export interface DashNavItem {
  href: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  /** Unread / pending count shown as a pill. `null`/`undefined`/0 hides it. */
  badge?: number | null;
}

interface DashShellProps {
  navItems: DashNavItem[];
  user: User;
  onLogout: () => void | Promise<void>;
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
 * App shell: sidebar (a drawer on mobile) + content area. The language switcher, theme toggle and Low-Data switch sit together:
 * in a slim bar above the content on desktop, in the drawer footer on mobile.
 * Auth and role guards stay in dashboard/layout.tsx; this only renders chrome.
 */
export function DashShell({ navItems, user, onLogout, children }: DashShellProps) {
  const { t, format } = useI18n();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
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

  const signOut = async () => {
    if (signingOut) return;
    setSigningOut(true);
    try {
      await onLogout();
    } finally {
      setSigningOut(false);
    }
  };

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
          className="-me-2 flex h-11 w-11 items-center justify-center rounded-full text-gray-400 transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-primary"
        >
          {open ? <X className="h-5 w-5" aria-hidden="true" /> : <Menu className="h-5 w-5" aria-hidden="true" />}
        </button>
      </header>

      {/* Backdrop (mobile only) */}
      <div
        aria-hidden="true"
        onClick={closeDrawer}
        className={cn(
          'fixed inset-0 z-40 bg-foreground/40 transition-opacity duration-300 motion-reduce:transition-none md:hidden',
          open ? 'opacity-100' : 'pointer-events-none opacity-0'
        )}
      />

      {/* Sidebar. Closed on mobile it is `invisible` (after the slide-out) so keyboard focus cannot land in it. */}
      <aside
        id="dash-sidebar"
        className={cn(
          'fixed inset-y-0 start-0 z-50 flex w-[17rem] max-w-[85vw] flex-col border-e border-line bg-surface transition-[transform,visibility] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none md:w-60 md:translate-x-0 md:visible',
          open ? 'visible translate-x-0' : 'invisible -translate-x-full rtl:translate-x-full'
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
            className="-me-2 flex h-11 w-11 items-center justify-center rounded-full text-gray-400 transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-primary md:hidden"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        {/* User block */}
        <div className="mx-4 mb-2 mt-1 border-y border-line py-4">
          <div className="flex items-start gap-3">
            <span
              aria-hidden="true"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary-soft font-display text-lg leading-none text-primary-text"
            >
              {user.email?.charAt(0).toUpperCase()}
            </span>
            <div className="min-w-0 flex-1">
              <p className="break-all text-sm leading-snug text-foreground">{user.email}</p>
              <p className={cn(EYEBROW, 'mt-1.5 leading-snug')}>{t(`dashUi.role.${user.role}`)}</p>
              {user.verificationStatus && (
                <StatusBadge value={user.verificationStatus} label={t(`dashUi.verification.${user.verificationStatus}`)} className="mt-2" />
              )}
            </div>
          </div>
        </div>

        {/* Navigation */}
        <nav aria-label={t('dashUi.mainNavigation')} className="flex-1 overflow-y-auto px-3 py-3">
          <ul className="space-y-1">
            {navItems.map((item) => {
              const active = item.href === current;
              const badge = item.badge && item.badge > 0 ? item.badge : null;
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    onClick={closeDrawer}
                    aria-current={active ? 'page' : undefined}
                    className={cn(
                      'group relative flex min-h-11 items-center gap-3 rounded-xl px-3 py-2 text-sm transition-colors duration-200 focus-visible:outline-2 focus-visible:outline-primary',
                      active ? 'bg-primary-soft font-medium text-primary-text' : 'text-gray-400 hover:bg-foreground/[0.05] hover:text-foreground'
                    )}
                  >
                    {active && <span aria-hidden="true" className="absolute inset-y-2 start-0 w-[3px] rounded-full bg-primary" />}
                    <item.icon className={cn('h-4 w-4 shrink-0', active ? 'text-primary-text' : 'text-gray-500 group-hover:text-foreground')} />
                    <span className="min-w-0 break-words leading-snug">{item.label}</span>
                    {badge !== null && (
                      <>
                        <span aria-hidden="true" className="ms-auto min-w-6 rounded-full bg-primary px-2 py-0.5 text-center text-xs font-semibold tabular-nums text-primary-fg">
                          {badge > 99 ? '99+' : format.number(badge)}
                        </span>
                        <span className="sr-only">{t('dashUi.unreadCount', { count: badge })}</span>
                      </>
                    )}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        {/* Preferences (mobile drawer only; on desktop they live in the bar above the content) + sign out */}
        <div className="shrink-0 space-y-2 border-t border-line p-4">
          <div className="flex flex-wrap items-center gap-2 md:hidden">
            <LanguageSwitcher className="[&_[role=menu]]:bottom-full [&_[role=menu]]:left-0 [&_[role=menu]]:right-auto [&_[role=menu]]:top-auto [&_[role=menu]]:mb-2 [&_[role=menu]]:mt-0" />
            <ThemeToggle />
            <LowDataToggle className="w-full justify-start" />
          </div>
          <button
            type="button"
            onClick={signOut}
            disabled={signingOut}
            aria-busy={signingOut || undefined}
            className="flex min-h-11 w-full items-center gap-3 rounded-xl px-3 py-2 text-sm text-gray-400 transition-colors duration-200 hover:bg-foreground/[0.05] hover:text-foreground focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-60"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            {signingOut ? t('dashUi.signingOut') : t('common.signOut')}
          </button>
        </div>
      </aside>

      {/* Content. `inert` while the mobile drawer is open so focus stays in the drawer. */}
      <main id="main-content" tabIndex={-1} inert={open} className="relative min-h-screen focus:outline-none md:ps-60">
        <div className="mx-auto hidden w-full max-w-6xl items-center justify-end gap-2 px-4 pt-4 sm:px-6 md:flex md:px-10">
          <LowDataToggle />
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
        <div className="relative mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 md:px-10 md:py-10">{children}</div>
      </main>
    </div>
  );
}
