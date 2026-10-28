'use client';

import { useId, useState } from 'react';
import type { ReactNode } from 'react';
import { CircleHelp } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * "How is this calculated?" help: a button (44px target) that reveals the explanation right below it.
 * A disclosure rather than a hover tooltip, so it works with touch, keyboard and screen readers.
 */
export function InfoDisclosure({ label, children, className }: { label: string; children: ReactNode; className?: string }) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  return (
    <div className={className}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-controls={panelId}
        className="-ms-2 inline-flex min-h-11 items-center gap-1.5 rounded-lg px-2 text-sm font-medium text-primary-text hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <CircleHelp className="h-4 w-4 shrink-0" aria-hidden="true" />
        {label}
      </button>
      <div id={panelId} hidden={!open} className={cn('mt-1 rounded-xl border border-line bg-surface-raised p-3 text-sm leading-relaxed text-foreground')}>
        {children}
      </div>
    </div>
  );
}
