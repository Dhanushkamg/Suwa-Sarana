'use client';

import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useCalm } from './useCalm';

const DOT = {
  primary: 'bg-primary',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
} as const;

/** Small status dot that pulses softly (still for reduced motion and Low-Data Mode). Decorative: always sits next to text. */
export function PulseDot({ className, tone = 'primary', still = false }: { className?: string; tone?: keyof typeof DOT; still?: boolean }) {
  const calm = useCalm();
  return (
    <span aria-hidden="true" className={cn('relative inline-flex h-2 w-2 shrink-0', className)}>
      {!still && !calm && <span className={cn('dot-ping absolute inset-0 rounded-full', DOT[tone])} />}
      <span className={cn('relative h-2 w-2 rounded-full', DOT[tone])} />
    </span>
  );
}

/** Urgency pill (icon + text + colour). */
export function UrgencyBadge({ urgency, className }: { urgency: string; className?: string }) {
  const { t } = useI18n();
  return <StatusBadge value={urgency} label={t(`dash.urgency.${urgency}`)} className={className} />;
}

/** Localised request status pill (OPEN, MATCHED ...). */
export function RequestStatusBadge({ status, className }: { status: string; className?: string }) {
  const { t } = useI18n();
  return <StatusBadge value={status} label={t('request.status' + status.charAt(0) + status.slice(1).toLowerCase())} className={className} />;
}
