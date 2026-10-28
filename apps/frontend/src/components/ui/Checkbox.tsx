import React, { useId } from 'react';
import { Check } from 'lucide-react';

interface CheckboxProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: React.ReactNode;
  error?: string;
}

/** Native checkbox (keyboard + form semantics for free) with a themed box. */
export function Checkbox({ label, error, className, id, ...props }: CheckboxProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  return (
    <div className={className}>
      <label htmlFor={inputId} className="flex min-h-11 cursor-pointer items-start gap-3 py-1.5 text-sm text-foreground">
        <span className="relative mt-0.5 inline-flex h-5 w-5 shrink-0">
          <input
            id={inputId}
            type="checkbox"
            aria-invalid={error ? true : undefined}
            className="peer absolute inset-0 h-full w-full cursor-pointer appearance-none rounded-md border border-control bg-surface checked:border-primary checked:bg-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            {...props}
          />
          <Check className="pointer-events-none absolute inset-0 m-auto h-3.5 w-3.5 text-primary-fg opacity-0 peer-checked:opacity-100" aria-hidden="true" />
        </span>
        <span>{label}</span>
      </label>
      {error && <p className="text-xs text-danger-text">{error}</p>}
    </div>
  );
}
