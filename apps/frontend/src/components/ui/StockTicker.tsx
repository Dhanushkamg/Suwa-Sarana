'use client';

import { useState, useEffect } from 'react';
import { Pause, Play } from 'lucide-react';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
import { StatusBadge } from '@/components/ui/StatusBadge';
import landing from '@/components/landing/landing.module.css';

interface StockSignal {
  hospitalName: string;
  district: string;
  bloodType: string;
  status: 'LOW' | 'CRITICAL';
}

/** Live blood-stock shortages from the public inventory endpoint, as a slow ticker. Renders nothing when there are none. */
export function StockTicker({ className }: { className?: string }) {
  const { t } = useI18n();
  const [signals, setSignals] = useState<StockSignal[]>([]);
  // WCAG 2.2.2: moving content that lasts more than 5 seconds needs a pause control that works without a mouse
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    const fetchSignals = async () => {
      try {
        const response = await apiClient.get('/inventory/stock-signals');
        setSignals(Array.isArray(response.data) ? response.data : []);
      } catch {
        // Decorative, best-effort feed: when it cannot load the strip simply stays hidden
      }
    };
    fetchSignals();

    // Refresh every 5 minutes
    const interval = setInterval(fetchSignals, 300000);
    return () => clearInterval(interval);
  }, []);

  if (signals.length === 0) return null;

  const renderItems = () =>
    signals.map((sig, idx) => (
      <div key={idx} className="flex shrink-0 items-center gap-2.5 pr-12 text-sm">
        <span className="text-foreground">
          {sig.hospitalName} <span className="text-gray-500">({sig.district})</span>
        </span>
        {/* Blood type = brand crimson, at display size so it passes contrast as large text in both themes */}
        <span className="font-display text-2xl text-brand">{sig.bloodType}</span>
        <span className="text-gray-500">{t('landing.stockNeeded')}</span>
        {/* Status is icon + text + colour: critical shortage is the semantic red, low is amber */}
        <StatusBadge
          value={sig.status}
          tone={sig.status === 'CRITICAL' ? 'danger' : 'warning'}
          label={sig.status === 'CRITICAL' ? t('landing.stockCritical') : t('landing.stockLowStatus')}
        />
      </div>
    ));

  return (
    <div role="region" aria-label={t('landing.stockLabel')} className={cn('flex items-center bg-surface-raised', className)}>
      <span className={cn('eyebrow shrink-0 border-r border-line px-4 py-3.5 text-foreground sm:px-6', landing.scriptFix)}>
        {t('landing.stockLabel')}
      </span>
      <div className="min-w-0 flex-1 overflow-hidden whitespace-nowrap [mask-image:linear-gradient(90deg,transparent,currentColor_6%,currentColor_94%,transparent)]">
        {/* Two copies make the loop seamless; the second is hidden from assistive tech and from reduced-motion users */}
        <div
          className={cn(
            'hover-pause animate-marquee-horizontal flex w-max items-center py-3 motion-reduce:w-auto motion-reduce:flex-wrap motion-reduce:gap-y-2',
            paused && '[animation-play-state:paused]'
          )}
        >
          <div className="flex shrink-0 items-center motion-reduce:flex-wrap">{renderItems()}</div>
          <div className="flex shrink-0 items-center motion-reduce:hidden" aria-hidden="true">
            {renderItems()}
          </div>
        </div>
      </div>
      <button
        type="button"
        onClick={() => setPaused((p) => !p)}
        aria-label={paused ? t('landing.tickerPlay') : t('landing.tickerPause')}
        className="mx-1 flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-muted transition-colors hover:bg-foreground/[0.06] hover:text-foreground motion-reduce:hidden"
      >
        {paused ? <Play className="h-4 w-4" aria-hidden="true" /> : <Pause className="h-4 w-4" aria-hidden="true" />}
      </button>
    </div>
  );
}
