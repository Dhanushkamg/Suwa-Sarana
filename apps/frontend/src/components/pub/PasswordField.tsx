'use client';

import React, { useId, useState } from 'react';
import { Eye, EyeOff, Lock } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';

interface PasswordFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string;
  error?: string;
  /** Always-visible rule, e.g. "At least 10 characters". Linked to the input via aria-describedby. */
  hint?: string;
  /** Rendered opposite the label (e.g. a "Forgot password?" link). */
  labelAction?: React.ReactNode;
  /** Controlled visibility. When omitted the field manages its own show/hide state. */
  visible?: boolean;
  onVisibleChange?: (visible: boolean) => void;
}

/** Password input with a lock icon and a show/hide toggle that sits in the field, independent of label height. */
export function PasswordField({
  label,
  error,
  hint,
  labelAction,
  visible,
  onVisibleChange,
  className,
  id,
  ...props
}: PasswordFieldProps) {
  const { t } = useI18n();
  const autoId = useId();
  const fieldId = id ?? autoId;
  const errorId = `${fieldId}-error`;
  const hintId = `${fieldId}-hint`;
  const [ownVisible, setOwnVisible] = useState(false);
  const shown = visible ?? ownVisible;
  const toggle = () => (onVisibleChange ? onVisibleChange(!shown) : setOwnVisible(!shown));
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between gap-3">
        <label htmlFor={fieldId} className="text-sm font-medium text-foreground">
          {label}
        </label>
        {labelAction}
      </div>
      <div className="relative">
        <div className="pointer-events-none absolute inset-y-0 left-0 z-10 flex items-center pl-3.5 text-gray-500">
          <Lock className="h-4 w-4" aria-hidden="true" />
        </div>
        <input
          id={fieldId}
          type={shown ? 'text' : 'password'}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(
            'w-full rounded-xl border bg-surface py-3 pl-10 pr-12 text-sm text-foreground placeholder:text-muted transition-[border-color,background-color,box-shadow] duration-300',
            'border-line-strong hover:border-foreground/25 focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/20',
            error && 'border-danger focus:border-danger focus:ring-danger/25',
            className
          )}
          {...props}
        />
        <button
          type="button"
          onClick={toggle}
          aria-label={shown ? t('authFlow.hidePassword') : t('authFlow.showPassword')}
          aria-pressed={shown}
          className="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-e-xl text-gray-500 transition-colors hover:text-foreground focus-visible:text-foreground"
        >
          {shown ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
        </button>
      </div>
      {hint && (
        <p id={hintId} className={cn('text-xs text-muted', error && 'sr-only')}>
          {hint}
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
