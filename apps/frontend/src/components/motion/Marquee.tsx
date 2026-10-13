import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

/** Endless horizontal ticker. Children are rendered twice for a seamless loop; the second copy is hidden from screen readers. */
export function Marquee({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn('overflow-hidden [mask-image:linear-gradient(90deg,transparent,#000_12%,#000_88%,transparent)]', className)}>
      <div className="animate-marquee-horizontal hover-pause flex w-max gap-12">
        <div className="flex shrink-0 items-center gap-12">{children}</div>
        <div className="flex shrink-0 items-center gap-12" aria-hidden="true">
          {children}
        </div>
      </div>
    </div>
  );
}
