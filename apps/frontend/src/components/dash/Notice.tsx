'use client';

import type { ReactNode } from 'react';
import { AlertCircle, CheckCircle2, Info, TriangleAlert } from 'lucide-react';
import { cn } from '@/lib/utils';

const TONES = {
  success: { box: 'border-success/30 bg-success-soft', icon: 'text-success-text', Icon: CheckCircle2 },
  error: { box: 'border-danger/30 bg-danger-soft', icon: 'text-danger-text', Icon: AlertCircle },
  warning: { box: 'border-warning/30 bg-warning-soft', icon: 'text-warning-text', Icon: TriangleAlert },
  info: { box: 'border-info/30 bg-info-soft', icon: 'text-info-text', Icon: Info },
} as const;

interface NoticeProps {
  tone?: keyof typeof TONES;
  children: ReactNode;
  /** Replaces the default tone icon. */
  icon?: ReactNode;
  /** Right-aligned control, e.g. a retry button. */
  action?: ReactNode;
  className?: string;
}

/** Inline banner for success / error / warning / hint messages: icon + text + colour. Errors and warnings are announced to screen readers. */
export function Notice({ tone = 'info', children, icon, action, className }: NoticeProps) {
  const { box, icon: iconClass, Icon } = TONES[tone];
  return (
    <div
      role={tone === 'error' || tone === 'warning' ? 'alert' : 'status'}
      className={cn('flex flex-wrap items-start gap-x-3 gap-y-2 rounded-xl border px-4 py-3 text-sm leading-relaxed text-foreground', box, className)}
    >
      <span aria-hidden="true" className={cn('mt-0.5 shrink-0 [&>svg]:h-4 [&>svg]:w-4', iconClass)}>
        {icon ?? <Icon />}
      </span>
      <div className="min-w-0 flex-1 break-words">{children}</div>
      {action}
    </div>
  );
}
