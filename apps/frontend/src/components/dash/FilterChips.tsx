'use client';

import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';

export interface ChipOption<T extends string> {
  value: T;
  label: string;
  count?: number;
}

interface FilterChipsProps<T extends string> {
  options: ChipOption<T>[];
  value: T;
  onChange: (value: T) => void;
  /** Accessible name of the group. */
  label: string;
  /** Kept for call-site compatibility (the pill no longer animates between chips). */
  id?: string;
}

/** Pill filter group (toggle buttons with aria-pressed). Chips are at least 44px tall and wrap onto several lines. */
export function FilterChips<T extends string>({ options, value, onChange, label }: FilterChipsProps<T>) {
  const { format } = useI18n();
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-2">
      {options.map((o) => {
        const active = o.value === value;
        return (
          <button
            key={o.value}
            type="button"
            onClick={() => onChange(o.value)}
            aria-pressed={active}
            className={cn(
              'inline-flex min-h-11 items-center gap-2 rounded-full border px-4 py-2 text-sm font-medium transition-colors duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
              active
                ? 'border-primary bg-primary-soft text-primary-text'
                : 'border-line-strong bg-surface text-gray-400 hover:bg-surface-raised hover:text-foreground'
            )}
          >
            <span>{o.label}</span>
            {o.count !== undefined && (
              <span className={cn('rounded-full px-1.5 text-xs tabular-nums', active ? 'bg-primary/15' : 'bg-foreground/[0.06] text-gray-500')}>
                {format.number(o.count)}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
