import React from 'react';
import { cn } from '@/lib/utils';

/** Scrolls inside its own box on narrow screens so the page never scrolls sideways. Give it a <caption> (can be sr-only) for screen readers. */
export function Table({ className, children, ...props }: React.TableHTMLAttributes<HTMLTableElement>) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-line bg-surface">
      <table className={cn('w-full min-w-[32rem] border-collapse text-start text-sm', className)} {...props}>
        {children}
      </table>
    </div>
  );
}

export const Th = ({ className, ...props }: React.ThHTMLAttributes<HTMLTableCellElement>) => (
  <th scope="col" className={cn('border-b border-line bg-surface-raised px-4 py-3 text-start text-xs font-semibold uppercase tracking-wide text-muted', className)} {...props} />
);

export const Td = ({ className, ...props }: React.TdHTMLAttributes<HTMLTableCellElement>) => (
  <td className={cn('border-b border-line px-4 py-3 align-middle text-foreground last:border-b-0', className)} {...props} />
);
