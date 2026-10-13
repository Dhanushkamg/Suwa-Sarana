'use client';

import { useState, useEffect } from 'react';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';
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

  useEffect(() => {
    const fetchSignals = async () => {
      try {
        const response = await apiClient.get('/inventory/stock-signals');
        setSignals(response.data);
      } catch (error) {
        console.error('Failed to load stock signals', error);
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
        <span className="relative flex h-2 w-2 shrink-0" aria-hidden="true">
          {sig.status === 'CRITICAL' && <span className="dot-ping absolute inset-0 rounded-full bg-ember" />}
          <span className={cn('relative h-2 w-2 rounded-full', sig.status === 'CRITICAL' ? 'bg-ember' : 'bg-saffron')} />
        </span>
        <span className="text-foreground">
          {sig.hospitalName} <span className="text-gray-500">({sig.district})</span>
        </span>
        <span className="font-display text-xl text-ember">{sig.bloodType}</span>
        <span className="text-gray-500">{t('landing.stockNeeded')}</span>
        <span
          className={cn(
            'rounded-full border px-2 py-0.5 font-mono text-[10px] uppercase tracking-[0.12em] [:lang(si)_&]:normal-case [:lang(si)_&]:tracking-normal [:lang(ta)_&]:normal-case [:lang(ta)_&]:tracking-normal',
            sig.status === 'CRITICAL' ? 'border-ember/40 text-ember' : 'border-saffron/40 text-saffron'
          )}
        >
          {sig.status === 'CRITICAL' ? t('ui.urgency.CRITICAL') : t('landing.stockLow')}
        </span>
      </div>
    ));

  return (
    <div role="region" aria-label={t('landing.stockLabel')} className={cn('flex items-center bg-ember/[0.05]', className)}>
      <span className={cn('eyebrow shrink-0 border-r border-line px-4 py-3.5 sm:px-6', landing.scriptFix)}>
        <span className="text-ember">{t('landing.stockLabel')}</span>
      </span>
      <div className="min-w-0 flex-1 overflow-hidden whitespace-nowrap [mask-image:linear-gradient(90deg,transparent,#000_6%,#000_94%,transparent)]">
        {/* Two copies make the loop seamless; the second is hidden from assistive tech and from reduced-motion users */}
        <div className="hover-pause animate-marquee-horizontal flex w-max items-center py-3.5 motion-reduce:w-auto motion-reduce:flex-wrap motion-reduce:gap-y-2">
          <div className="flex shrink-0 items-center motion-reduce:flex-wrap">{renderItems()}</div>
          <div className="flex shrink-0 items-center motion-reduce:hidden" aria-hidden="true">
            {renderItems()}
          </div>
        </div>
      </div>
    </div>
  );
}
