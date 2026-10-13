import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

/** Inline meta fact with a small stone icon (location, time, units ...). Wraps freely for long translations. */
export function Meta({ icon, children, className }: { icon?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 text-xs text-gray-400', className)}>
      {icon && (
        <span aria-hidden="true" className="shrink-0 text-gray-500 [&>svg]:h-3.5 [&>svg]:w-3.5">
          {icon}
        </span>
      )}
      <span className="min-w-0 break-words">{children}</span>
    </span>
  );
}
