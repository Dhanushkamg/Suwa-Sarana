'use client';

import { useEffect, useId, useState } from 'react';
import { Button, type ButtonVariant } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { FormField } from '@/components/ui/FormField';
import { useI18n } from '@/lib/i18n';
import { Notice } from './Notice';

interface ConfirmDialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  confirmLabel: string;
  /** `danger` for destructive actions; `primary` otherwise. */
  confirmVariant?: ButtonVariant;
  /** Ask for a written reason. When `reasonRequired`, confirming is blocked (with an error under the field) until it is filled in. */
  reasonLabel?: string;
  reasonHelp?: string;
  reasonRequired?: boolean;
  /** Minimum / maximum length of the reason (the verification API wants 3 to 500 characters). */
  reasonMinLength?: number;
  reasonMaxLength?: number;
  /** Runs on confirm. Throw (or reject) to keep the dialog open and show `errorMessage(err)`. */
  onConfirm: (reason: string) => Promise<void>;
  errorMessage?: (err: unknown) => string;
}

/** Confirm / reject style dialog with an optional required reason. Prevents double submit and shows failures inline. */
export function ConfirmDialog({
  open,
  onClose,
  title,
  description,
  confirmLabel,
  confirmVariant = 'primary',
  reasonLabel,
  reasonHelp,
  reasonRequired = false,
  reasonMinLength,
  reasonMaxLength = 500,
  onConfirm,
  errorMessage,
}: ConfirmDialogProps) {
  const { t } = useI18n();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const formId = useId();

  // Start clean every time the dialog opens
  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setReason('');
      setError(null);
      setFieldError(undefined);
      setBusy(false);
    }
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (busy) return;
    if (reasonLabel && reasonRequired && !reason.trim()) {
      setFieldError(t('dashUi.reasonRequired'));
      return;
    }
    if (reasonLabel && reasonMinLength && reason.trim().length > 0 && reason.trim().length < reasonMinLength) {
      setFieldError(t('dashUi.reasonTooShort', { min: reasonMinLength }));
      return;
    }
    setFieldError(undefined);
    setError(null);
    setBusy(true);
    try {
      await onConfirm(reason.trim());
      onClose();
    } catch (err) {
      setError(errorMessage ? errorMessage(err) : t('common.error'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={busy ? () => undefined : onClose} title={title} description={description}>
      <form id={formId} onSubmit={submit} noValidate className="space-y-4">
        {reasonLabel && (
          <FormField label={reasonLabel} help={reasonHelp} error={fieldError} required={reasonRequired}>
            {(p) => (
              <textarea
                {...p}
                rows={4}
                maxLength={reasonMaxLength}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                className="w-full resize-y rounded-xl border border-line-strong bg-surface px-4 py-3 text-sm leading-relaxed text-foreground placeholder:text-muted hover:border-foreground/25 focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/20 aria-[invalid=true]:border-danger"
              />
            )}
          </FormField>
        )}
        {error && <Notice tone="error">{error}</Notice>}
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant={confirmVariant} loading={busy}>
            {confirmLabel}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
