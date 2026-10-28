import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

/** Inline meta fact with a small icon (location, time, units ...). Wraps freely for long translations. */
export function Meta({ icon, children, className }: { icon?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <span className={cn('inline-flex items-start gap-1.5 text-sm text-gray-400', className)}>
      {icon && (
        <span aria-hidden="true" className="mt-0.5 shrink-0 text-gray-500 [&>svg]:h-4 [&>svg]:w-4">
          {icon}
        </span>
      )}
      <span className="min-w-0 break-words">{children}</span>
    </span>
  );
}
