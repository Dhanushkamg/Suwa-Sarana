import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

/**
 * Quiet inline message: hairline border, a thin vertical accent rule and a faint tint.
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
  return (
    <div
      role={isError ? 'alert' : 'status'}
      className={cn(
        'flex items-start gap-3 rounded-xl border border-line border-l-2 px-4 py-3 text-sm leading-relaxed text-gray-200',
        isError ? 'border-l-ember bg-ember/[0.07]' : 'border-l-emerald-400 bg-emerald-400/[0.07]',
        className
      )}
    >
      <span
        aria-hidden="true"
        className={cn('mt-[0.55rem] h-1.5 w-1.5 shrink-0 rounded-full', isError ? 'bg-ember' : 'bg-emerald-400')}
      />
      <div className="min-w-0 break-words">{children}</div>
    </div>
  );
}
