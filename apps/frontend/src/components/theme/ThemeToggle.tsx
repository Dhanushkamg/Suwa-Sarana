'use client';

import { Monitor, Moon, Sun } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import { useTheme, type ThemeMode } from './ThemeProvider';

const OPTIONS: { mode: ThemeMode; icon: typeof Sun; key: string }[] = [
  { mode: 'light', icon: Sun, key: 'theme.light' },
  { mode: 'system', icon: Monitor, key: 'theme.system' },
  { mode: 'dark', icon: Moon, key: 'theme.dark' },
];

/** Three-way theme switch (light / system / dark). Each option is a real radio-style button with a text label for screen readers. */
export function ThemeToggle({ className }: { className?: string }) {
  const { mode, setMode } = useTheme();
  const { t } = useI18n();

  return (
    <div role="radiogroup" aria-label={t('theme.label')} className={cn('inline-flex items-center rounded-full border border-line bg-surface p-0.5', className)}>
      {OPTIONS.map(({ mode: m, icon: Icon, key }) => {
        const active = mode === m;
        return (
          <button
            key={m}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => setMode(m)}
            title={t(key)}
            className={cn(
              'inline-flex h-9 w-9 items-center justify-center rounded-full transition-colors',
              active ? 'bg-primary text-primary-fg' : 'text-muted hover:text-foreground'
            )}
          >
            <Icon className="h-4 w-4" aria-hidden="true" />
            <span className="sr-only">{t(key)}</span>
          </button>
        );
      })}
    </div>
  );
}
