'use client';

import { useEffect, useId, useRef } from 'react';
import { X } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';

interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: React.ReactNode;
  className?: string;
}

/**
 * Modal built on the native <dialog>: the browser traps focus, makes the rest of the page inert,
 * closes on Escape and returns focus to the opener. We add click-outside-to-close and the close button.
 */
export function Dialog({ open, onClose, title, description, children, className }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const { t } = useI18n();
  const uid = useId();

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (open && !el.open) el.showModal();
    if (!open && el.open) el.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={`${uid}-title`}
      aria-describedby={description ? `${uid}-desc` : undefined}
      onClose={onClose}
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
      className={cn('m-auto w-[min(92vw,32rem)] rounded-2xl border border-line bg-surface p-0 text-foreground shadow-2xl backdrop:bg-black/50', className)}
    >
      <div className="p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id={`${uid}-title`} className="text-lg font-semibold">
              {title}
            </h2>
            {description && (
              <p id={`${uid}-desc`} className="mt-1 text-sm text-muted">
                {description}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={t('common.close')}
            className="-m-2 inline-flex h-11 w-11 items-center justify-center rounded-full text-muted hover:bg-foreground/[0.06] hover:text-foreground"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
        <div className="mt-5">{children}</div>
      </div>
    </dialog>
  );
}
