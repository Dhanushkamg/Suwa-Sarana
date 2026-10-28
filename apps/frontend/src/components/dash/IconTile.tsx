import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

const TONES = {
  primary: 'bg-primary-soft text-primary-text',
  brand: 'bg-brand-soft text-brand',
  success: 'bg-success-soft text-success-text',
  warning: 'bg-warning-soft text-warning-text',
  danger: 'bg-danger-soft text-danger-text',
  muted: 'bg-foreground/[0.06] text-gray-400',
} as const;

const SIZES = {
  sm: 'h-10 w-10 rounded-lg [&>svg]:h-4 [&>svg]:w-4',
  md: 'h-11 w-11 rounded-xl [&>svg]:h-5 [&>svg]:w-5',
  lg: 'h-14 w-14 rounded-2xl [&>svg]:h-6 [&>svg]:w-6',
} as const;

export type IconTone = keyof typeof TONES;

/** Calm flat tile with a single line icon (decorative: the text next to it carries the meaning). */
export function IconTile({
  children,
  tone = 'primary',
  size = 'md',
  className,
}: {
  children: ReactNode;
  tone?: IconTone;
  size?: keyof typeof SIZES;
  className?: string;
}) {
  return (
    <span aria-hidden="true" className={cn('flex shrink-0 items-center justify-center', SIZES[size], TONES[tone], className)}>
      {children}
    </span>
  );
}

const BLOOD_SIZES = {
  sm: 'h-10 w-10 rounded-lg text-lg',
  md: 'h-14 w-14 rounded-2xl text-2xl',
  lg: 'h-16 w-16 rounded-2xl text-3xl',
} as const;

/** Blood group badge: one of the few places the crimson brand colour is used. */
export function BloodTypeTile({ type, size = 'md', className }: { type: string; size?: keyof typeof BLOOD_SIZES; className?: string }) {
  return (
    <span
      className={cn('flex shrink-0 items-center justify-center bg-brand-soft font-display leading-none text-brand', BLOOD_SIZES[size], className)}
    >
      {type}
    </span>
  );
}
