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
        <label htmlFor={fieldId} className="text-sm font-medium text-foreground">
          {label}
        </label>
      )}
      <textarea
        id={fieldId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={cn(
          'w-full resize-y rounded-xl border bg-surface px-4 py-3 text-sm leading-relaxed text-foreground placeholder:text-muted transition-[border-color,background-color,box-shadow] duration-300',
          'border-line-strong hover:border-foreground/25 focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/20',
          error && 'border-danger focus:border-danger focus:ring-danger/25',
          className
        )}
        {...props}
      />
      {error && (
        <p id={errorId} role="alert" className="text-xs text-danger-text">
          {error}
        </p>
      )}
    </div>
  );
}
