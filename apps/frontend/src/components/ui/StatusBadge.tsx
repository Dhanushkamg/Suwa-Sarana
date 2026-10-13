import { cn } from '@/lib/utils';

const TONES = {
  red: 'bg-red-500/15 text-red-300 border-red-500/30',
  amber: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
  green: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
  blue: 'bg-blue-500/15 text-blue-300 border-blue-500/30',
  gray: 'bg-foreground/[0.05] text-gray-300 border-line-strong',
} as const;

type Tone = keyof typeof TONES;

/** Maps backend enum values (request status, urgency, verification...) to a colour. Unknown values render gray. */
const VALUE_TONE: Record<string, Tone> = {
  CRITICAL: 'red', URGENT: 'amber', ROUTINE: 'blue',
  OPEN: 'blue', ESCALATING: 'amber', MATCHED: 'green', FULFILLED: 'green', CANCELLED: 'gray', EXPIRED: 'gray',
  PENDING: 'amber', VERIFIED: 'green', REJECTED: 'red',
  BOOKED: 'blue', CHECKED_IN: 'green', NO_SHOW: 'red', COMPLETED: 'green', FULL: 'amber', SCHEDULED: 'blue',
};

export function StatusBadge({ value, label, tone, className }: { value: string; label?: string; tone?: Tone; className?: string }) {
  const resolved = tone ?? VALUE_TONE[value] ?? 'gray';
  return (
    <span className={cn('inline-flex items-center rounded-full border px-2.5 py-0.5 font-mono text-[11px] uppercase tracking-[0.06em]', TONES[resolved], className)}>
      {label ?? value.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}
    </span>
  );
}
