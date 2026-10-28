import React from 'react';
import { cn } from '@/lib/utils';

/** Standard panel. Use `as="section"` with a heading inside when it is a landmark-worthy region. */
export function Card({ as: Tag = 'div', className, children, ...props }: React.HTMLAttributes<HTMLElement> & { as?: 'div' | 'section' | 'article' | 'li' }) {
  return (
    <Tag className={cn('rounded-2xl border border-line bg-surface p-5 shadow-[var(--shadow-card)] sm:p-6', className)} {...props}>
      {children}
    </Tag>
  );
}

export function CardTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <h2 className={cn('text-lg font-semibold text-foreground', className)} {...props} />;
}
