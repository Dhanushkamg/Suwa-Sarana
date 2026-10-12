import { cn } from '@/lib/utils';

/** Pulsing placeholder block shown while data loads. Size it with className (e.g. "h-24 w-full"). */
export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn('animate-pulse rounded-xl bg-white/5', className)} />;
}

/** A few stacked card-shaped skeletons for list pages. */
export function SkeletonList({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-3" role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-24 w-full" />
      ))}
    </div>
  );
}
