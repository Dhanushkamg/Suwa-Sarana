'use client';

import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import { StatusBadge } from '@/components/ui/StatusBadge';

/** Small ember dot that pulses softly (the `.dot-ping` keyframes are disabled for reduced-motion users in globals.css). */
export function PulseDot({ className, tone = 'ember', still = false }: { className?: string; tone?: 'ember' | 'sage' | 'saffron'; still?: boolean }) {
  const color = tone === 'sage' ? 'bg-emerald-400' : tone === 'saffron' ? 'bg-saffron' : 'bg-ember';
  return (
    <span aria-hidden="true" className={cn('relative inline-flex h-2 w-2 shrink-0', className)}>
      {!still && <span className={cn('dot-ping absolute inset-0 rounded-full', color)} />}
      <span className={cn('relative h-2 w-2 rounded-full', color)} />
    </span>
  );
}

/** Urgency pill. CRITICAL gets a pulsing ember dot in front of it. */
export function UrgencyBadge({ urgency, className }: { urgency: string; className?: string }) {
  const { t } = useI18n();
  return (
    <span className={cn('inline-flex items-center gap-1.5', className)}>
      {urgency === 'CRITICAL' && <PulseDot />}
      <StatusBadge value={urgency} label={t(`dash.urgency.${urgency}`)} />
    </span>
  );
}

/** Localised request status pill (OPEN, MATCHED ...). */
export function RequestStatusBadge({ status, className }: { status: string; className?: string }) {
  const { t } = useI18n();
  return <StatusBadge value={status} label={t('request.status' + status.charAt(0) + status.slice(1).toLowerCase())} className={className} />;
}
