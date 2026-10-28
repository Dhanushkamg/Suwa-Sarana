'use client';

import { cn } from '@/lib/utils';

interface SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  /** Visible label text, also used as the accessible name */
  label: string;
  description?: string;
  disabled?: boolean;
  id?: string;
}

/** On/off setting. A real button with role="switch", so it works with keyboard (Space/Enter) and screen readers. */
export function Switch({ checked, onChange, label, description, disabled, id }: SwitchProps) {
  const labelId = `${id ?? label}-label`;
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <p id={labelId} className="text-sm font-medium text-foreground">
          {label}
        </p>
        {description && <p className="mt-0.5 text-sm text-muted">{description}</p>}
      </div>
      <button
        type="button"
        id={id}
        role="switch"
        aria-checked={checked}
        aria-labelledby={labelId}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={cn(
          'relative mt-0.5 inline-flex h-7 w-12 shrink-0 items-center rounded-full border transition-colors disabled:cursor-not-allowed disabled:opacity-50',
          checked ? 'border-primary bg-primary' : 'border-control bg-surface-raised'
        )}
      >
        <span
          aria-hidden="true"
          className={cn('inline-block h-5 w-5 rounded-full bg-white shadow transition-transform motion-reduce:transition-none', checked ? 'translate-x-[1.375rem]' : 'translate-x-1')}
        />
      </button>
    </div>
  );
}
