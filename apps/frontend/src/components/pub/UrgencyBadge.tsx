import { AlertCircle, AlertTriangle } from 'lucide-react';
import { cn } from '@/lib/utils';

/** Urgency marker: icon + label + colour. CRITICAL is semantic red, anything else amber. */
export function UrgencyBadge({ label, critical, className }: { label: string; critical: boolean; className?: string }) {
  const Icon = critical ? AlertCircle : AlertTriangle;
  return (
    <span
      className={cn(
        'inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold',
        critical ? 'border-danger/30 bg-danger-soft text-danger-text' : 'border-warning/30 bg-warning-soft text-warning-text',
        className
      )}
    >
      <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      {label}
    </span>
  );
}
