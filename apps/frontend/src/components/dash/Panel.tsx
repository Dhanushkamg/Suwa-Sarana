import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { CARD, EYEBROW } from './styles';

/** The standard dashboard surface. `flush` removes padding (for lists and tables). */
export function Panel({ children, className, flush = false }: { children: ReactNode; className?: string; flush?: boolean }) {
  return <div className={cn(CARD, flush ? 'overflow-hidden' : 'p-5 sm:p-6', className)}>{children}</div>;
}

interface SectionHeaderProps {
  eyebrow?: string;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  icon?: ReactNode;
  className?: string;
  /** Heading level; the page's own h1 sits above these. */
  as?: 'h2' | 'h3';
}

/** Title block for a panel or a page section: optional caption, serif title, action on the right. */
export function SectionHeader({ eyebrow, title, description, action, icon, className, as: Tag = 'h2' }: SectionHeaderProps) {
  return (
    <div className={cn('mb-5 flex flex-wrap items-start justify-between gap-x-4 gap-y-3', className)}>
      <div className="flex min-w-0 items-start gap-3">
        {icon}
        <div className="min-w-0">
          {eyebrow && <p className={cn(EYEBROW, 'mb-1.5')}>{eyebrow}</p>}
          <Tag className="font-display break-words text-2xl text-foreground">{title}</Tag>
          {description && <p className="mt-1.5 max-w-xl text-sm leading-relaxed text-gray-400">{description}</p>}
        </div>
      </div>
      {action}
    </div>
  );
}

/** Caption with a hairline running to the edge: separates groups inside a form. */
export function SectionLabel({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn('flex items-center gap-3', className)}>
      <h3 className={EYEBROW}>{children}</h3>
      <span aria-hidden="true" className="h-px flex-1 bg-line" />
    </div>
  );
}
