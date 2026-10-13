'use client';

import React, { useId, useState } from 'react';
import { Eye, EyeOff, Lock } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';

interface PasswordFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string;
  error?: string;
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
  const [ownVisible, setOwnVisible] = useState(false);
  const shown = visible ?? ownVisible;
  const toggle = () => (onVisibleChange ? onVisibleChange(!shown) : setOwnVisible(!shown));

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between gap-3">
        <label htmlFor={fieldId} className="text-sm font-medium text-gray-300">
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
          aria-describedby={error ? errorId : undefined}
          className={cn(
            'w-full rounded-xl border bg-foreground/[0.035] py-3 pl-10 pr-12 text-sm text-foreground placeholder-gray-600 transition-[border-color,background-color,box-shadow] duration-300',
            'border-line-strong hover:border-foreground/25 focus:border-ember focus:bg-foreground/[0.06] focus:outline-none focus:ring-4 focus:ring-ember/15',
            error && 'border-red-500/50',
            className
          )}
          {...props}
        />
        <button
          type="button"
          onClick={toggle}
          aria-label={shown ? t('authFlow.hidePassword') : t('authFlow.showPassword')}
          aria-pressed={shown}
          className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-gray-500 transition-colors hover:text-foreground focus-visible:text-foreground"
        >
          {shown ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
        </button>
      </div>
      {error && (
        <p id={errorId} className="text-xs text-red-400">
          {error}
        </p>
      )}
    </div>
  );
}
