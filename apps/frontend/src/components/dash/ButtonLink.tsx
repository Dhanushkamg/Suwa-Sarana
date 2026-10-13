import Link from 'next/link';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

/** A `<Link>` that looks like `components/ui/Button` (which only renders a `<button>`). Keep in sync with Button. */
const BASE =
  'group/btn relative inline-flex items-center justify-center gap-2 rounded-full font-medium tracking-[-0.005em] transition-[background-color,border-color,color,transform] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ember';

const VARIANTS = {
  primary: 'bg-ember text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.28)] hover:bg-[color-mix(in_oklch,var(--ember)_88%,white)]',
  secondary: 'border border-foreground/[0.16] bg-foreground/[0.04] text-foreground hover:border-foreground/30 hover:bg-foreground/[0.08]',
  ghost: 'text-gray-400 hover:bg-foreground/[0.06] hover:text-foreground',
} as const;

const SIZES = {
  sm: 'px-4 py-1.5 text-[13px]',
  md: 'px-6 py-2.5 text-sm',
  lg: 'px-8 py-3.5 text-[15px]',
} as const;

type Props = ComponentProps<typeof Link> & { variant?: keyof typeof VARIANTS; size?: keyof typeof SIZES };

export function ButtonLink({ variant = 'secondary', size = 'sm', className, ...props }: Props) {
  return <Link className={cn(BASE, VARIANTS[variant], SIZES[size], className)} {...props} />;
}
