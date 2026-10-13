import React from 'react';
import { cn } from '@/lib/utils';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  className,
  children,
  disabled,
  ...props
}: ButtonProps) {
  const base =
    'group/btn relative inline-flex items-center justify-center gap-2 rounded-full font-medium tracking-[-0.005em] transition-[background-color,border-color,color,transform,box-shadow] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ember disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100';

  const variants = {
    primary:
      'bg-ember text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.28),0_6px_20px_-8px_color-mix(in_oklch,var(--ember)_70%,transparent)] hover:bg-[color-mix(in_oklch,var(--ember)_88%,white)]',
    secondary:
      'border border-line-strong bg-foreground/[0.04] text-foreground hover:border-foreground/30 hover:bg-foreground/[0.08]',
    danger:
      'border border-red-500/40 bg-red-500/10 text-red-300 hover:bg-red-500/20',
    ghost: 'text-gray-400 hover:bg-foreground/[0.06] hover:text-foreground',
  };

  const sizes = {
    sm: 'px-4 py-1.5 text-[13px]',
    md: 'px-6 py-2.5 text-sm',
    lg: 'px-8 py-3.5 text-[15px]',
  };

  return (
    <button
      className={cn(base, variants[variant], sizes[size], className)}
      disabled={disabled || loading}
      {...props}
    >
      {loading && (
        <svg
          className="mr-2 h-4 w-4 animate-spin"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
          />
        </svg>
      )}
      {children}
    </button>
  );
}
