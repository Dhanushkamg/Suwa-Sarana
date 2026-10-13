'use client';

import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';

/** Slim two-tick progress marker: filled ticks are done/current, with mono "01 / 02" and a label. */
export function StepIndicator({ current, labels }: { current: number; labels: string[] }) {
  const { t } = useI18n();
  const total = labels.length;
  return (
    <div className="mb-8" role="group" aria-label={t('pub.stepOf', { current, total })}>
      <div className="flex items-center gap-2" aria-hidden="true">
        {labels.map((label, i) => (
          <span key={label} className="relative h-[3px] flex-1 overflow-hidden rounded-full bg-foreground/10">
            <span
              className={cn(
                'absolute inset-0 origin-left rounded-full bg-ember transition-transform duration-700 ease-[cubic-bezier(0.22,1,0.36,1)]',
                i < current ? 'scale-x-100' : 'scale-x-0'
              )}
            />
          </span>
        ))}
      </div>
      <div className="mt-3 flex items-baseline justify-between gap-4">
        <span className="eyebrow">
          {String(current).padStart(2, '0')} / {String(total).padStart(2, '0')}
        </span>
        <span className="text-right text-sm text-gray-300" aria-current="step">
          {labels[current - 1]}
        </span>
      </div>
    </div>
  );
}
