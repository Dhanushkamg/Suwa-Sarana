'use client';

import type { ReactNode, MouseEvent } from 'react';
import { cn } from '@/lib/utils';

/** Wraps a card so a soft ember light follows the cursor (styles: `.spotlight` in globals.css). */
export function Spotlight({ children, className }: { children: ReactNode; className?: string }) {
  const onMove = (e: MouseEvent<HTMLDivElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    e.currentTarget.style.setProperty('--mx', `${e.clientX - r.left}px`);
    e.currentTarget.style.setProperty('--my', `${e.clientY - r.top}px`);
  };
  return (
    <div onMouseMove={onMove} className={cn('spotlight', className)}>
      {children}
    </div>
  );
}
