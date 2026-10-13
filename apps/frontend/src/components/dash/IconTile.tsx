import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

const TONES = {
  ember: 'text-ember',
  saffron: 'text-saffron',
  sage: 'text-emerald-400',
  muted: 'text-gray-400',
} as const;

const SIZES = {
  sm: 'h-9 w-9 rounded-lg [&>svg]:h-4 [&>svg]:w-4',
  md: 'h-11 w-11 rounded-xl [&>svg]:h-5 [&>svg]:w-5',
  lg: 'h-14 w-14 rounded-2xl [&>svg]:h-6 [&>svg]:w-6',
} as const;

/** Calm flat tile with a single line icon. Replaces the old glowing gradient tiles. */
export function IconTile({
  children,
  tone = 'ember',
  size = 'md',
  className,
}: {
  children: ReactNode;
  tone?: keyof typeof TONES;
  size?: keyof typeof SIZES;
  className?: string;
}) {
  return (
    <span
      aria-hidden="true"
      className={cn('flex shrink-0 items-center justify-center border border-line bg-foreground/[0.04]', SIZES[size], TONES[tone], className)}
    >
      {children}
    </span>
  );
}

const BLOOD_SIZES = {
  sm: 'h-11 w-11 rounded-xl text-xl',
  md: 'h-14 w-14 rounded-2xl text-2xl',
  lg: 'h-16 w-16 rounded-2xl text-3xl',
} as const;

/** Blood group in a flat serif tile. */
export function BloodTypeTile({ type, size = 'md', className }: { type: string; size?: keyof typeof BLOOD_SIZES; className?: string }) {
  return (
    <span
      className={cn(
        'flex shrink-0 items-center justify-center border border-line bg-foreground/[0.04] font-display leading-none text-ember',
        BLOOD_SIZES[size],
        className
      )}
    >
      {type}
    </span>
  );
}
