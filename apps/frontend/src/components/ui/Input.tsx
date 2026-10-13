import React, { useId } from 'react';
import { cn } from '@/lib/utils';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  icon?: React.ReactNode;
}

export function Input({ label, error, icon, className, id, ...props }: InputProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const errorId = `${inputId}-error`;

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-gray-300">
          {label}
        </label>
      )}
      <div className="relative">
        {icon && (
          <div className="pointer-events-none absolute z-10 inset-y-0 left-0 flex items-center pl-3.5 text-gray-500">
            {icon}
          </div>
        )}
        <input
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          className={cn(
            'w-full rounded-xl border bg-foreground/[0.035] px-4 py-3 text-sm text-foreground placeholder-gray-600 transition-[border-color,background-color,box-shadow] duration-300',
            'border-line-strong hover:border-foreground/25 focus:border-ember focus:bg-foreground/[0.06] focus:outline-none focus:ring-4 focus:ring-ember/15',
            error && 'border-red-500/50 focus:border-red-500 focus:ring-red-500/30',
            icon && 'pl-10',
            className
          )}
          {...props}
        />
      </div>
      {error && (
        <p id={errorId} className="text-xs text-red-400">
          {error}
        </p>
      )}
    </div>
  );
}
