import { cn } from '@/lib/utils';

/** Class names that make an <a>/<Link> look like ui/Button (pill) without nesting a <button> in a link. */
export function linkButtonClass(variant: 'primary' | 'secondary' = 'primary', size: 'md' | 'lg' = 'md', extra?: string) {
  const base =
    'inline-flex items-center justify-center gap-2 rounded-full font-medium tracking-[-0.005em] transition-[background-color,border-color,color,transform] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] active:scale-[0.98] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ember';
  const variants = {
    primary: 'bg-ember text-white hover:bg-[color-mix(in_oklch,var(--ember)_88%,white)]',
    secondary: 'border border-line-strong bg-foreground/[0.04] text-foreground hover:border-foreground/30 hover:bg-foreground/[0.08]',
  };
  const sizes = {
    md: 'px-6 py-2.5 text-sm',
    lg: 'px-8 py-4 text-[15px]',
  };
  return cn(base, variants[variant], sizes[size], extra);
}
