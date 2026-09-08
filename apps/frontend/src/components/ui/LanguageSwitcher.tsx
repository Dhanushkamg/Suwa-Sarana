'use client';

import { usePathname, useRouter } from 'next/navigation';
import { useI18n } from '@/lib/i18n';
import { Globe } from 'lucide-react';
import { cn } from '@/lib/utils';

const LOCALES = [
  { code: 'en', label: 'English', flag: '🇬🇧' },
  { code: 'si', label: 'සිංහල', flag: '🇱🇰' },
  { code: 'ta', label: 'தமிழ்', flag: '🇱🇰' },
] as const;

export function LanguageSwitcher({ className }: { className?: string }) {
  const { locale } = useI18n();
  const pathname = usePathname();
  const router = useRouter();

  const switchLocale = (newLocale: string) => {
    const locales = ['en', 'si', 'ta'];
    const segments = pathname.split('/');
    const firstSegment = segments[1];
    const basePath = locales.includes(firstSegment)
      ? '/' + segments.slice(2).join('/')
      : pathname;

    const newPath = newLocale === 'en' ? basePath || '/' : `/${newLocale}${basePath}`;
    router.push(newPath);
  };

  return (
    <div className={cn('relative group', className)}>
      <button
        className="flex items-center gap-1.5 text-sm text-gray-400 hover:text-white transition-colors px-3 py-1.5 rounded-lg hover:bg-white/5"
        aria-label="Switch language"
      >
        <Globe className="w-4 h-4" />
        <span>{LOCALES.find((l) => l.code === locale)?.flag}</span>
      </button>

      {/* Dropdown */}
      <div className="absolute right-0 top-full mt-1.5 w-40 rounded-xl border border-white/10 bg-[#14141f] shadow-xl shadow-black/50 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200 z-50">
        {LOCALES.map((lang) => (
          <button
            key={lang.code}
            onClick={() => switchLocale(lang.code)}
            className={cn(
              'w-full flex items-center gap-3 px-4 py-2.5 text-sm text-left transition-colors first:rounded-t-xl last:rounded-b-xl',
              locale === lang.code
                ? 'text-white bg-white/8'
                : 'text-gray-400 hover:text-white hover:bg-white/5'
            )}
          >
            <span>{lang.flag}</span>
            <span>{lang.label}</span>
            {locale === lang.code && (
              <span className="ml-auto w-1.5 h-1.5 rounded-full bg-red-400" />
            )}
          </button>
        ))}
      </div>
    </div>
  );
}
