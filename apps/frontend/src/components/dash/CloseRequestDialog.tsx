'use client';

import { useEffect, useId, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { useI18n } from '@/lib/i18n';
import { apiMessage } from '@/lib/api/common';
import { CLOSE_REASONS, type CloseReason } from '@/lib/api/requests';
import { Notice } from './Notice';

interface CloseRequestDialogProps {
  open: boolean;
  onClose: () => void;
  /** Runs on confirm with the chosen reason (or undefined). Throw to keep the dialog open and show the error. */
  onConfirm: (reason: CloseReason | undefined) => Promise<void>;
}

/** "Close this request?" with the optional CloseReason (NO_LONGER_NEEDED, FOUND_DONOR_ELSEWHERE, OTHER). */
export function CloseRequestDialog({ open, onClose, onConfirm }: CloseRequestDialogProps) {
  const { t } = useI18n();
  const groupId = useId();
  const [reason, setReason] = useState<CloseReason | ''>('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setReason('');
      setError(null);
      setBusy(false);
    }
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await onConfirm(reason || undefined);
      onClose();
    } catch (err) {
      setError(apiMessage(err, t('common.error')));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={busy ? () => undefined : onClose} title={t('dashUi.req.closeTitle')} description={t('dashUi.req.closeDesc')}>
      <form onSubmit={submit} className="space-y-4">
        <fieldset className="space-y-1">
          <legend className="mb-2 text-sm font-medium text-foreground">{t('dashUi.req.closeReasonLegend')}</legend>
          {CLOSE_REASONS.map((value) => (
            <label key={value} htmlFor={`${groupId}-${value}`} className="flex min-h-11 cursor-pointer items-center gap-3 rounded-lg px-1 text-sm text-foreground">
              <input
                id={`${groupId}-${value}`}
                type="radio"
                name={groupId}
                value={value}
                checked={reason === value}
                onChange={() => setReason(value)}
                className="h-4 w-4 accent-[var(--primary)]"
              />
              {t(`dashUi.req.closeReason.${value}`)}
            </label>
          ))}
        </fieldset>
        {error && <Notice tone="error">{error}</Notice>}
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant="danger" loading={busy}>
            {t('dashUi.req.closeRequest')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
