'use client';

import type { ReactNode } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { AlertCircle, CheckCircle2, Info, ShieldAlert } from 'lucide-react';
import { cn } from '@/lib/utils';
import { EASE } from './styles';

const TONES = {
  success: { box: 'border-emerald-500/25 bg-emerald-500/[0.07] text-emerald-200', icon: 'text-emerald-400', Icon: CheckCircle2 },
  error: { box: 'border-red-500/30 bg-red-500/[0.08] text-red-200', icon: 'text-red-400', Icon: AlertCircle },
  warning: { box: 'border-amber-500/25 bg-amber-500/[0.07] text-amber-100', icon: 'text-amber-400', Icon: ShieldAlert },
  info: { box: 'border-foreground/[0.16] bg-foreground/[0.03] text-gray-300', icon: 'text-saffron', Icon: Info },
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

/** Inline banner for success / error / warning / hint messages. Errors and warnings are announced to screen readers. */
export function Notice({ tone = 'info', children, icon, action, className }: NoticeProps) {
  const reduce = useReducedMotion();
  const { box, icon: iconClass, Icon } = TONES[tone];
  return (
    <motion.div
      role={tone === 'error' || tone === 'warning' ? 'alert' : 'status'}
      initial={reduce ? false : { opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: EASE }}
      className={cn('flex flex-wrap items-start gap-x-3 gap-y-2 rounded-xl border px-4 py-3 text-sm leading-relaxed', box, className)}
    >
      <span aria-hidden="true" className={cn('mt-0.5 shrink-0 [&>svg]:h-4 [&>svg]:w-4', iconClass)}>
        {icon ?? <Icon />}
      </span>
      <div className="min-w-0 flex-1 break-words">{children}</div>
      {action}
    </motion.div>
  );
}
