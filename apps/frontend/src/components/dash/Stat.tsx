'use client';

import type { ReactNode } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { AnimatedNumber } from '@/components/motion/AnimatedNumber';
import { cn } from '@/lib/utils';
import { EASE, EYEBROW } from './styles';

const TONES = {
  default: 'text-foreground',
  ember: 'text-ember',
  saffron: 'text-saffron',
  sage: 'text-emerald-400',
} as const;

const METER = {
  default: 'bg-foreground/60',
  ember: 'bg-ember',
  saffron: 'bg-saffron',
  sage: 'bg-emerald-400',
} as const;

interface StatProps {
  label: string;
  value: number | string;
  suffix?: string;
  hint?: ReactNode;
  tone?: keyof typeof TONES;
  icon?: ReactNode;
  /** 0-100: draws a hairline meter under the number. */
  meter?: number;
  /** Count whole numbers up when scrolled into view. */
  animate?: boolean;
  className?: string;
}

/** KPI block: tiny mono label, big serif tabular number, optional hint and meter. */
export function Stat({ label, value, suffix, hint, tone = 'default', icon, meter, animate = true, className }: StatProps) {
  const reduce = useReducedMotion();
  const counts = animate && typeof value === 'number' && Number.isInteger(value);
  const valueClass = cn('font-display text-5xl tabular-nums', TONES[tone]);
  return (
    <div className={cn('glass-card rounded-2xl p-5', className)}>
      <div className="flex items-start justify-between gap-3">
        <p className={EYEBROW}>{label}</p>
        {icon && (
          <span aria-hidden="true" className="text-gray-500 [&>svg]:h-4 [&>svg]:w-4">
            {icon}
          </span>
        )}
      </div>
      <p className="mt-4 flex items-baseline gap-1.5">
        {counts ? <AnimatedNumber value={value as number} className={valueClass} /> : <span className={valueClass}>{value}</span>}
        {suffix && <span className="font-mono text-sm text-gray-500">{suffix}</span>}
      </p>
      {meter !== undefined && (
        <div aria-hidden="true" className="mt-4 h-px w-full bg-line">
          <motion.div
            className={cn('h-px w-full origin-left', METER[tone])}
            initial={reduce ? false : { scaleX: 0 }}
            animate={{ scaleX: Math.min(Math.max(meter, 0), 100) / 100 }}
            transition={{ duration: 0.9, ease: EASE, delay: 0.2 }}
          />
        </div>
      )}
      {hint && <p className="mt-3 text-xs leading-relaxed text-gray-500">{hint}</p>}
    </div>
  );
}
