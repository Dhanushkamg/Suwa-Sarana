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
            'w-full rounded-xl border bg-white/5 px-4 py-3 text-sm text-white placeholder-gray-500 backdrop-blur-sm transition-all duration-200',
            'border-white/10 focus:border-red-500/60 focus:bg-white/8 focus:outline-none focus:ring-2 focus:ring-red-500/20',
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
