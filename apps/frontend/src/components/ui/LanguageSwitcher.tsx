'use client';

import { useEffect, useRef, useState } from 'react';
import { useI18n, Locale } from '@/lib/i18n';
import { Globe, Check } from 'lucide-react';
import { cn } from '@/lib/utils';

const LOCALES: { code: Locale; label: string; short: string }[] = [
  { code: 'en', label: 'English', short: 'EN' },
  { code: 'si', label: 'සිංහල', short: 'සිං' },
  { code: 'ta', label: 'தமிழ்', short: 'த' },
];

export function LanguageSwitcher({ className }: { className?: string }) {
  const { locale, setLocale } = useI18n();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  // Close on outside click and Escape
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: MouseEvent | TouchEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    const onKeyDown = (e: KeyboardEvent) => {
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

  const current = LOCALES.find((l) => l.code === locale) ?? LOCALES[0];

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Switch language"
        className="inline-flex h-9 items-center gap-2 rounded-full border border-line-strong bg-foreground/[0.03] px-3 text-gray-300 transition-colors duration-300 hover:border-foreground/30 hover:bg-foreground/[0.07] hover:text-foreground"
      >
        <Globe className="h-3.5 w-3.5 text-gray-400" aria-hidden="true" />
        <span aria-hidden="true" className="font-mono text-[11px] font-medium uppercase leading-none tracking-[0.12em]">
          {current.short}
        </span>
        <span className="sr-only">{current.label}</span>
      </button>

      {/* Options stay in the DOM; visibility is toggled via the `hidden` class */}
      <div
        role="menu"
        aria-label="Language"
        className={cn(
          'absolute right-0 top-full z-50 mt-2 w-44 overflow-hidden rounded-2xl border border-line-strong bg-surface-raised p-1 shadow-[0_18px_40px_-12px_rgba(0,0,0,0.7)]',
          !open && 'hidden'
        )}
      >
        {LOCALES.map((lang) => (
          <button
            key={lang.code}
            type="button"
            role="menuitemradio"
            aria-checked={locale === lang.code}
            onClick={() => {
              setLocale(lang.code);
              setOpen(false);
            }}
            className={cn(
              'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition-colors',
              locale === lang.code ? 'bg-foreground/[0.06] font-medium text-foreground' : 'text-gray-400 hover:bg-foreground/[0.05] hover:text-foreground'
            )}
          >
            <span>{lang.label}</span>
            {locale === lang.code && <Check className="ml-auto h-3.5 w-3.5 text-ember" aria-hidden="true" />}
          </button>
        ))}
      </div>
    </div>
  );
}
