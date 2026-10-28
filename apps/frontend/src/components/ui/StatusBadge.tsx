import { AlertCircle, AlertTriangle, CheckCircle2, Circle, Clock, Info, XCircle } from 'lucide-react';
import { cn } from '@/lib/utils';

/** Status is always icon + text + colour, never colour alone. */
const TONES = {
  danger: { cls: 'bg-danger-soft text-danger-text border-danger/30', Icon: AlertCircle },
  warning: { cls: 'bg-warning-soft text-warning-text border-warning/30', Icon: AlertTriangle },
  success: { cls: 'bg-success-soft text-success-text border-success/30', Icon: CheckCircle2 },
  info: { cls: 'bg-info-soft text-info-text border-info/30', Icon: Info },
  pending: { cls: 'bg-warning-soft text-warning-text border-warning/30', Icon: Clock },
  neutral: { cls: 'bg-foreground/[0.06] text-muted border-line-strong', Icon: Circle },
  failed: { cls: 'bg-danger-soft text-danger-text border-danger/30', Icon: XCircle },
} as const;

export type BadgeTone = keyof typeof TONES;

/** Maps backend enum values (request status, urgency, verification...) to a tone. Unknown values render neutral. */
const VALUE_TONE: Record<string, BadgeTone> = {
  CRITICAL: 'danger', URGENT: 'warning', ROUTINE: 'info',
  OPEN: 'info', ESCALATING: 'warning', MATCHED: 'success', FULFILLED: 'success', CANCELLED: 'neutral', EXPIRED: 'neutral',
  PENDING: 'pending', VERIFIED: 'success', REJECTED: 'failed',
  BOOKED: 'info', CHECKED_IN: 'success', NO_SHOW: 'failed', COMPLETED: 'success', FULL: 'warning', SCHEDULED: 'info',
};

export function StatusBadge({ value, label, tone, className }: { value: string; label?: string; tone?: BadgeTone | 'red' | 'amber' | 'green' | 'blue' | 'gray'; className?: string }) {
  // Old tone names kept so existing call sites compile
  const legacy = { red: 'danger', amber: 'warning', green: 'success', blue: 'info', gray: 'neutral' } as const;
  const resolved: BadgeTone = tone ? (tone in legacy ? legacy[tone as keyof typeof legacy] : (tone as BadgeTone)) : (VALUE_TONE[value] ?? 'neutral');
  const { cls, Icon } = TONES[resolved];
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium', cls, className)}>
      <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      {label ?? value.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}
    </span>
  );
}
