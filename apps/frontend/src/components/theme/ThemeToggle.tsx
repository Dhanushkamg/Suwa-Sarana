'use client';

import { useRef, type KeyboardEvent } from 'react';
import { Monitor, Moon, Sun } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import { useTheme, type ThemeMode } from './ThemeProvider';

const OPTIONS: { mode: ThemeMode; icon: typeof Sun; key: string }[] = [
  { mode: 'light', icon: Sun, key: 'theme.light' },
  { mode: 'system', icon: Monitor, key: 'theme.system' },
  { mode: 'dark', icon: Moon, key: 'theme.dark' },
];

/**
 * Three-way theme switch (light / system / dark). A real radio group: one tab stop, arrow keys move and select,
 * and each option carries a text label for screen readers (the icons alone never name it).
 */
export function ThemeToggle({ className }: { className?: string }) {
  const { mode, setMode } = useTheme();
  const { t } = useI18n();
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const current = OPTIONS.findIndex((o) => o.mode === mode);
    let next = -1;
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') next = (current + 1) % OPTIONS.length;
    else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') next = (current - 1 + OPTIONS.length) % OPTIONS.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = OPTIONS.length - 1;
    if (next === -1) return;
    e.preventDefault();
    setMode(OPTIONS[next].mode);
    refs.current[next]?.focus();
  };

  return (
    <div
      role="radiogroup"
      aria-label={t('theme.label')}
      onKeyDown={onKeyDown}
      className={cn('inline-flex items-center rounded-full border border-line-strong bg-surface p-0.5', className)}
    >
      {OPTIONS.map(({ mode: m, icon: Icon, key }, i) => {
        const active = mode === m;
        return (
          <button
            key={m}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="radio"
            aria-checked={active}
            tabIndex={active ? 0 : -1}
            onClick={() => setMode(m)}
            title={t(key)}
            // 44px on touch layouts; the compact 36px size only where a precise pointer is expected
            className={cn(
              'inline-flex h-11 w-11 items-center justify-center rounded-full transition-colors md:h-9 md:w-9',
              active ? 'bg-primary text-primary-fg' : 'text-muted hover:bg-foreground/[0.06] hover:text-foreground'
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
