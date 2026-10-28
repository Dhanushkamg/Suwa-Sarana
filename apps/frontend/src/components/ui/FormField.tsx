'use client';

import React, { useId } from 'react';
import { cn } from '@/lib/utils';

interface FieldControlProps {
  id: string;
  'aria-describedby'?: string;
  'aria-invalid'?: true;
}

/** Label + help + error wired together. `children` receives the props the control must spread: <FormField label="X">{(p) => <input {...p} />}</FormField> */
export function FormField({
  label,
  help,
  error,
  required,
  className,
  children,
}: {
  label: string;
  help?: string;
  error?: string;
  required?: boolean;
  className?: string;
  children: (props: FieldControlProps) => React.ReactNode;
}) {
  const id = useId();
  const helpId = help ? `${id}-help` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [helpId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={id} className="text-sm font-medium text-foreground">
        {label}
        {required && (
          <span className="ms-0.5 text-danger-text" aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children({ id, 'aria-describedby': describedBy, 'aria-invalid': error ? true : undefined })}
      {help && !error && (
        <p id={helpId} className="text-xs text-muted">
          {help}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="text-xs text-danger-text">
          {error}
        </p>
      )}
    </div>
  );
}
