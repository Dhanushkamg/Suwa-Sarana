'use client';

import { useEffect, useRef, useState } from 'react';
import { AlertCircle, Check, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import { useCalm } from './useCalm';

type Phase = 'idle' | 'busy' | 'done' | 'failed';

interface RefreshButtonProps {
  /** Return a promise (resolving `false` on failure) to get the busy/success/error feedback and double-click protection for free. */
  onClick: () => Promise<boolean | void> | void;
  /** External loading flag (e.g. the page's own `loading` state). */
  loading?: boolean;
  label?: string;
  className?: string;
}

/** Secondary button with a spinning icon while loading, then a short "Updated" / "Could not refresh" confirmation (announced to screen readers). */
export function RefreshButton({ onClick, loading, label, className }: RefreshButtonProps) {
  const { t } = useI18n();
  const calm = useCalm();
  const [phase, setPhase] = useState<Phase>('idle');
  const busyRef = useRef(false);
  const timer = useRef<number | undefined>(undefined);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  const run = async () => {
    if (busyRef.current) return; // ignore double clicks
    busyRef.current = true;
    window.clearTimeout(timer.current);
    setPhase('busy');
    let ok = true;
    try {
      ok = (await onClick()) !== false;
    } catch {
      ok = false;
    }
    busyRef.current = false;
    setPhase(ok ? 'done' : 'failed');
    timer.current = window.setTimeout(() => setPhase('idle'), 3000);
  };

  const busy = phase === 'busy' || !!loading;
  const message = phase === 'done' ? t('dashUi.refreshDone') : phase === 'failed' ? t('dashUi.refreshFailed') : '';

  return (
    <>
      <Button type="button" variant="secondary" onClick={run} disabled={busy} aria-busy={busy || undefined} className={className}>
        {phase === 'done' ? (
          <Check className="h-4 w-4 text-success-text" aria-hidden="true" />
        ) : phase === 'failed' ? (
          <AlertCircle className="h-4 w-4 text-danger-text" aria-hidden="true" />
        ) : (
          <RefreshCw className={cn('h-4 w-4', busy && !calm && 'animate-spin')} aria-hidden="true" />
        )}
        {message || label || t('common.refresh')}
      </Button>
      <span role="status" className="sr-only">
        {message}
      </span>
    </>
  );
}
