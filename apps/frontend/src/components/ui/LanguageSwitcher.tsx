'use client';

import { useI18n, Locale } from '@/lib/i18n';
import { Globe } from 'lucide-react';
import { cn } from '@/lib/utils';

const LOCALES: { code: Locale; label: string; flag: string }[] = [
  { code: 'en', label: 'English', flag: '🇬🇧' },
  { code: 'si', label: 'සිංහල', flag: '🇱🇰' },
  { code: 'ta', label: 'தமிழ்', flag: '🇱🇰' },
];

export function LanguageSwitcher({ className }: { className?: string }) {
  const { locale, setLocale } = useI18n();

  return (
    <div className={cn('relative group', className)}>
      <button
        type="button"
        className="flex items-center gap-2 text-sm text-gray-300 hover:text-white transition-colors px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10"
        aria-label="Switch language"
      >
        <Globe className="w-4 h-4 text-red-400" />
        <span className="font-medium text-xs">
          {LOCALES.find((l) => l.code === locale)?.flag} {LOCALES.find((l) => l.code === locale)?.label}
        </span>
      </button>

      {/* Dropdown */}
      <div className="absolute right-0 top-full mt-2 w-44 rounded-xl border border-white/10 bg-[#14141f] shadow-2xl shadow-black/80 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200 z-50 py-1 backdrop-blur-xl">
        {LOCALES.map((lang) => (
          <button
            key={lang.code}
            type="button"
            onClick={() => setLocale(lang.code)}
            className={cn(
              'w-full flex items-center gap-3 px-4 py-2.5 text-sm text-left transition-colors',
              locale === lang.code
                ? 'text-white bg-red-500/15 font-medium'
                : 'text-gray-400 hover:text-white hover:bg-white/5'
            )}
          >
            <span className="text-base">{lang.flag}</span>
            <span>{lang.label}</span>
            {locale === lang.code && (
              <span className="ml-auto w-2 h-2 rounded-full bg-red-500 shadow-sm shadow-red-500" />
            )}
          </button>
        ))}
      </div>
    </div>
  );
}
