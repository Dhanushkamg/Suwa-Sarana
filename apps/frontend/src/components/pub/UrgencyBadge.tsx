import { cn } from '@/lib/utils';

/** Calm urgency marker: a small pulsing dot and mono label. CRITICAL uses ember, everything else saffron. */
export function UrgencyBadge({ label, critical, className }: { label: string; critical: boolean; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-2 rounded-full border px-3 py-1 font-mono text-[11px] font-medium uppercase tracking-[0.14em]',
        critical ? 'border-ember/30 bg-ember/10 text-ember' : 'border-saffron/30 bg-saffron/10 text-saffron',
        className
      )}
    >
      <span className="relative flex h-1.5 w-1.5" aria-hidden="true">
        <span className={cn('dot-ping absolute inset-0 rounded-full', critical ? 'bg-ember' : 'bg-saffron')} />
        <span className={cn('relative h-1.5 w-1.5 rounded-full', critical ? 'bg-ember' : 'bg-saffron')} />
      </span>
      {label}
    </span>
  );
}
