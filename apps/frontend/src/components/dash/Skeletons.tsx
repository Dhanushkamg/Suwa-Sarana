'use client';

import { useI18n } from '@/lib/i18n';
import { Skeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/utils';

/** Skeleton rows that mirror the real list rows (tile + two text lines + trailing chip), inside one hairline panel. */
export function RowsSkeleton({ rows = 3, className }: { rows?: number; className?: string }) {
  const { t } = useI18n();
  return (
    <div role="status" aria-label={t('common.loading')} className={cn('glass-card divide-y divide-line overflow-hidden rounded-2xl', className)}>
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex items-center gap-4 px-5 py-4">
          <Skeleton className="h-14 w-14 shrink-0 rounded-2xl" />
          <div className="min-w-0 flex-1 space-y-2.5">
            <Skeleton className="h-4 w-2/5" />
            <Skeleton className="h-3 w-3/5" />
          </div>
          <Skeleton className="hidden h-7 w-20 rounded-full sm:block" />
        </div>
      ))}
    </div>
  );
}

/** Skeleton for a KPI strip. */
export function StatsSkeleton({ count = 3, className }: { count?: number; className?: string }) {
  const { t } = useI18n();
  return (
    <div role="status" aria-label={t('common.loading')} className={cn('grid gap-4', className)}>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="glass-card rounded-2xl p-5">
          <Skeleton className="h-3 w-1/3" />
          <Skeleton className="mt-5 h-11 w-1/2" />
          <Skeleton className="mt-4 h-3 w-2/3" />
        </div>
      ))}
    </div>
  );
}
