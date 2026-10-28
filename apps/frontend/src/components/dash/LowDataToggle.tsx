'use client';

import { Wifi, WifiOff } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { useLowData } from '@/lib/lowData';
import { cn } from '@/lib/utils';

/** Compact Low-Data Mode switch for the dashboard chrome (same setting as the one on the donor profile). */
export function LowDataToggle({ className }: { className?: string }) {
  const { t } = useI18n();
  const { lowData, toggle } = useLowData();
  const Icon = lowData ? WifiOff : Wifi;
  return (
    <button
      type="button"
      role="switch"
      aria-checked={lowData}
      onClick={toggle}
      title={t('dashUi.lowDataHint')}
      className={cn(
        'inline-flex min-h-11 items-center gap-2 rounded-full border px-4 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        lowData ? 'border-primary bg-primary-soft text-primary-text' : 'border-line-strong bg-surface text-gray-400 hover:text-foreground',
        className
      )}
    >
      <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
      {t('donorDashboard.lowDataMode')}
    </button>
  );
}
