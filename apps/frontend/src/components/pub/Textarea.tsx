import React, { useId } from 'react';
import { cn } from '@/lib/utils';

interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
}

/** Multi-line twin of ui/Input: same label, border and focus ring. */
export function Textarea({ label, error, className, id, ...props }: TextareaProps) {
  const autoId = useId();
  const fieldId = id ?? autoId;
  const errorId = `${fieldId}-error`;

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={fieldId} className="text-sm font-medium text-gray-300">
          {label}
        </label>
      )}
      <textarea
        id={fieldId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={cn(
          'w-full resize-none rounded-xl border bg-foreground/[0.035] px-4 py-3 text-sm leading-relaxed text-foreground placeholder-gray-600 transition-[border-color,background-color,box-shadow] duration-300',
          'border-line-strong hover:border-foreground/25 focus:border-ember focus:bg-foreground/[0.06] focus:outline-none focus:ring-4 focus:ring-ember/15',
          error && 'border-red-500/50',
          className
        )}
        {...props}
      />
      {error && (
        <p id={errorId} className="text-xs text-red-400">
          {error}
        </p>
      )}
    </div>
  );
}
