'use client';

import { useEffect, useRef, useState } from 'react';
import { useI18n, Locale } from '@/lib/i18n';
import { Globe, Check } from 'lucide-react';
import { cn } from '@/lib/utils';

const LOCALES: { code: Locale; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'si', label: 'සිංහල' },
  { code: 'ta', label: 'தமிழ்' },
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
        className="flex items-center gap-2 text-sm text-gray-300 hover:text-white transition-colors px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10"
      >
        <Globe className="w-4 h-4 text-red-400" />
        <span className="font-medium text-xs">{current.label}</span>
      </button>

      {/* Options stay in the DOM; visibility is toggled via the `hidden` class */}
      <div
        role="menu"
        aria-label="Language"
        className={cn(
          'absolute right-0 top-full mt-2 w-44 rounded-xl border border-white/10 bg-[#14141f] shadow-2xl shadow-black/80 z-50 py-1 backdrop-blur-xl',
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
              'w-full flex items-center gap-3 px-4 py-2.5 text-sm text-left transition-colors',
              locale === lang.code
                ? 'text-white bg-red-500/15 font-medium'
                : 'text-gray-400 hover:text-white hover:bg-white/5'
            )}
          >
            <span>{lang.label}</span>
            {locale === lang.code && <Check className="ml-auto w-4 h-4 text-red-400" />}
          </button>
        ))}
      </div>
    </div>
  );
}
