import type { ReactNode } from 'react';
import { AlertCircle, CheckCircle2 } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * Inline message: icon + text + colour (never colour alone).
 * `error` uses role="alert" (announced immediately), `success` uses role="status".
 */
export function Alert({
  tone = 'error',
  children,
  className,
}: {
  tone?: 'error' | 'success';
  children: ReactNode;
  className?: string;
}) {
  const isError = tone === 'error';
  const Icon = isError ? AlertCircle : CheckCircle2;
  return (
    <div
      role={isError ? 'alert' : 'status'}
      className={cn(
        'flex items-start gap-3 rounded-xl border px-4 py-3 text-sm leading-relaxed text-foreground',
        isError ? 'border-danger/30 bg-danger-soft' : 'border-success/30 bg-success-soft',
        className
      )}
    >
      <Icon className={cn('mt-0.5 h-4 w-4 shrink-0', isError ? 'text-danger-text' : 'text-success-text')} aria-hidden="true" />
      <div className="min-w-0 break-words">{children}</div>
    </div>
  );
}
