'use client';

import { LogoMark } from '@/components/brand/Logo';
import { useI18n } from '@/lib/i18n';

export default function Loading() {
  const { t } = useI18n();
  return (
    <div className="flex min-h-[60vh] items-center justify-center" role="status" aria-label={t('a11y.loading')}>
      <div className="relative flex h-14 w-14 items-center justify-center">
        <span aria-hidden="true" className="dot-ping absolute inset-1 rounded-full bg-primary/30" />
        <LogoMark className="relative h-8 w-8 animate-float" />
      </div>
    </div>
  );
}
