import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}

/** Friendly "nothing here yet" / "something went wrong" block with an optional action button. */
export function EmptyState({ icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center justify-center rounded-2xl border border-dashed border-line-strong bg-foreground/[0.02] px-6 py-12 text-center', className)}>
      {icon && <div className="mb-4 text-gray-500">{icon}</div>}
      <h3 className="font-display text-2xl text-foreground">{title}</h3>
      {description && <p className="mt-1.5 max-w-sm text-sm text-gray-400">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
