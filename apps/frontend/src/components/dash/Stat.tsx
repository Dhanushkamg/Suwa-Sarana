'use client';

import type { ReactNode } from 'react';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import { CARD, EYEBROW } from './styles';
import { useCalm } from './useCalm';

const TONES = {
  default: 'text-foreground',
  primary: 'text-primary-text',
  success: 'text-success-text',
  warning: 'text-warning-text',
  danger: 'text-danger-text',
} as const;

const METER = {
  default: 'bg-foreground/60',
  primary: 'bg-primary',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
} as const;

interface StatProps {
  label: string;
  /** `null` means "no data": shows an em dash and the words "No data" instead of a made-up zero. */
  value: number | string | null;
  suffix?: string;
  hint?: ReactNode;
  tone?: keyof typeof TONES;
  icon?: ReactNode;
  /** 0-100: draws a thin meter under the number (a number is always shown too). */
  meter?: number;
  /** Extra content under the hint, e.g. an <InfoDisclosure>. */
  footer?: ReactNode;
  className?: string;
}

/** KPI block: small label, big tabular number (locale formatted), optional hint and meter. */
export function Stat({ label, value, suffix, hint, tone = 'default', icon, meter, footer, className }: StatProps) {
  const { t, format } = useI18n();
  const calm = useCalm();
  const empty = value === null;
  const shown = typeof value === 'number' ? format.number(value) : value;
  return (
    <div className={cn(CARD, 'p-5', className)}>
      <div className="flex items-start justify-between gap-3">
        <p className={EYEBROW}>{label}</p>
        {icon && (
          <span aria-hidden="true" className="text-gray-500 [&>svg]:h-4 [&>svg]:w-4">
            {icon}
          </span>
        )}
      </div>
      <p className="mt-4 flex flex-wrap items-baseline gap-x-1.5">
        {empty ? (
          <>
            <span aria-hidden="true" className="font-display text-5xl text-gray-500">
              &mdash;
            </span>
            <span className="sr-only">{t('dashUi.noData')}</span>
          </>
        ) : (
          <span className={cn('font-display text-5xl tabular-nums', TONES[tone])}>{shown}</span>
        )}
        {!empty && suffix && <span className="text-sm text-gray-500">{suffix}</span>}
      </p>
      {meter !== undefined && !empty && (
        <div aria-hidden="true" className="mt-4 h-1.5 w-full overflow-hidden rounded-full bg-foreground/[0.08]">
          <div
            className={cn('h-full origin-left rounded-full', METER[tone], !calm && 'transition-[width] duration-700')}
            style={{ width: `${Math.min(Math.max(meter, 0), 100)}%` }}
          />
        </div>
      )}
      {(hint || empty) && <p className="mt-3 text-sm leading-relaxed text-gray-500">{empty ? t('dashUi.noData') : hint}</p>}
      {footer}
    </div>
  );
}
