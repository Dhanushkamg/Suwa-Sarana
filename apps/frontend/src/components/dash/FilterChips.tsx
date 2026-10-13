'use client';

import { motion, useReducedMotion } from 'motion/react';
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
  /** Unique per group on a page: the active pill glides between chips via a shared layoutId. */
  id: string;
}

/** Pill filter group. The active pill is a flat ember wash that slides between options. */
export function FilterChips<T extends string>({ options, value, onChange, label, id }: FilterChipsProps<T>) {
  const reduce = useReducedMotion();
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-1.5">
      {options.map((o) => {
        const active = o.value === value;
        return (
          <button
            key={o.value}
            type="button"
            onClick={() => onChange(o.value)}
            aria-pressed={active}
            className={cn(
              'relative inline-flex items-center gap-2 rounded-full border px-3.5 py-1.5 text-[13px] font-medium transition-colors duration-200',
              active ? 'border-ember/50 text-foreground' : 'border-line text-gray-400 hover:border-foreground/[0.16] hover:text-foreground'
            )}
          >
            {active && (
              <motion.span
                layoutId={`chip-${id}`}
                aria-hidden="true"
                className="absolute inset-0 rounded-full bg-ember/15"
                transition={reduce ? { duration: 0 } : { type: 'spring', stiffness: 500, damping: 40 }}
              />
            )}
            <span className="relative">{o.label}</span>
            {o.count !== undefined && <span className="relative font-mono text-[11px] tabular-nums text-gray-500">{o.count}</span>}
          </button>
        );
      })}
    </div>
  );
}
