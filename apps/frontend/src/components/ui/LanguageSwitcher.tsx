'use client';

import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { useI18n, Locale } from '@/lib/i18n';
import { Globe, Check } from 'lucide-react';
import { cn } from '@/lib/utils';

const LOCALES: { code: Locale; label: string; short: string }[] = [
  { code: 'en', label: 'English', short: 'EN' },
  { code: 'si', label: 'සිංහල', short: 'සිං' },
  { code: 'ta', label: 'தமிழ்', short: 'த' },
];

export function LanguageSwitcher({ className }: { className?: string }) {
  const { locale, setLocale, t } = useI18n();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const current = LOCALES.find((l) => l.code === locale) ?? LOCALES[0];
  const currentIndex = LOCALES.findIndex((l) => l.code === current.code);

  // Close on outside click and Escape
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: MouseEvent | TouchEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    const onKeyDown = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('touchstart', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('touchstart', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  // Move focus onto the selected option when the menu opens, so arrow keys work straight away
  useEffect(() => {
    if (open) itemRefs.current[currentIndex]?.focus();
  }, [open, currentIndex]);

  const focusItem = (i: number) => itemRefs.current[(i + LOCALES.length) % LOCALES.length]?.focus();

  const onButtonKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      setOpen(true);
    }
  };

  const onMenuKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const index = itemRefs.current.findIndex((el) => el === document.activeElement);
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      focusItem(index + 1);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      focusItem(index - 1);
    } else if (e.key === 'Home') {
      e.preventDefault();
      focusItem(0);
    } else if (e.key === 'End') {
      e.preventDefault();
      focusItem(LOCALES.length - 1);
    } else if (e.key === 'Tab') {
      // Leaving the menu with Tab closes it, as a native menu would
      setOpen(false);
    }
  };

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((v) => !v)}
        onKeyDown={onButtonKeyDown}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={t('landing.switchLanguage')}
        className="inline-flex min-h-11 items-center gap-2 rounded-full border border-line-strong bg-surface px-3.5 text-foreground transition-colors duration-300 hover:bg-surface-raised md:min-h-9"
      >
        <Globe className="h-3.5 w-3.5 text-gray-400" aria-hidden="true" />
        <span aria-hidden="true" className="font-mono text-[11px] font-medium uppercase leading-none tracking-[0.12em] [:lang(si)_&]:tracking-normal [:lang(ta)_&]:tracking-normal">
          {current.short}
        </span>
        <span className="sr-only">{current.label}</span>
      </button>

      {/* Options stay in the DOM; visibility is toggled via the `hidden` class */}
      <div
        role="menu"
        aria-label={t('landing.languageMenu')}
        onKeyDown={onMenuKeyDown}
        className={cn(
          'absolute right-0 top-full z-50 mt-2 w-44 overflow-hidden rounded-2xl border border-line-strong bg-surface-raised p-1 shadow-lg',
          !open && 'hidden'
        )}
      >
        {LOCALES.map((lang, i) => (
          <button
            key={lang.code}
            ref={(el) => {
              itemRefs.current[i] = el;
            }}
            type="button"
            role="menuitemradio"
            aria-checked={locale === lang.code}
            tabIndex={-1}
            lang={lang.code}
            onClick={() => {
              setLocale(lang.code);
              setOpen(false);
              buttonRef.current?.focus();
            }}
            className={cn(
              'flex min-h-11 w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition-colors',
              locale === lang.code ? 'bg-foreground/[0.06] font-medium text-foreground' : 'text-gray-400 hover:bg-foreground/[0.05] hover:text-foreground'
            )}
          >
            <span>{lang.label}</span>
            {locale === lang.code && <Check className="ml-auto h-3.5 w-3.5 text-primary-text" aria-hidden="true" />}
          </button>
        ))}
      </div>
    </div>
  );
}
