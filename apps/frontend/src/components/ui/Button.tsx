import React from 'react';
import { cn } from '@/lib/utils';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'brand';
export type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** One `primary` per view; `brand` (crimson) is reserved for the single urgent call to action. */
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
}

/** Class names shared by <Button> and link-styled buttons (<Link className={buttonClasses(...)}>). */
export function buttonClasses(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', className?: string) {
  const base =
    'relative inline-flex items-center justify-center gap-2 rounded-full font-medium transition-[background-color,border-color,color,transform] duration-200 ease-[cubic-bezier(0.22,1,0.36,1)] active:scale-[0.98] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 aria-disabled:cursor-not-allowed aria-disabled:opacity-50';

  const variants: Record<ButtonVariant, string> = {
    primary: 'bg-primary text-primary-fg hover:bg-primary-hover',
    brand: 'bg-brand text-brand-fg hover:brightness-110',
    secondary: 'border border-line-strong bg-surface text-foreground hover:bg-surface-raised',
    danger: 'bg-danger text-white hover:brightness-110',
    ghost: 'text-muted hover:bg-foreground/[0.06] hover:text-foreground',
  };

  // 44px minimum touch target for md and lg; sm is for dense toolbars on desktop
  const sizes: Record<ButtonSize, string> = {
    sm: 'min-h-9 px-4 py-1.5 text-[13px]',
    md: 'min-h-11 px-6 py-2.5 text-sm',
    lg: 'min-h-12 px-8 py-3 text-[15px]',
  };

  return cn(base, variants[variant], sizes[size], className);
}

export function Button({ variant = 'primary', size = 'md', loading = false, className, children, disabled, type = 'button', ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={buttonClasses(variant, size, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && (
        <svg className="h-4 w-4 animate-spin motion-reduce:animate-none" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      )}
      {children}
    </button>
  );
}
