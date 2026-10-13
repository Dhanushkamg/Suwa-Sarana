'use client';

import { RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';

/** Secondary pill with a spinning refresh icon while `loading`. */
export function RefreshButton({ onClick, loading, label, className }: { onClick: () => void; loading?: boolean; label?: string; className?: string }) {
  const { t } = useI18n();
  return (
    <Button type="button" variant="secondary" size="sm" onClick={onClick} disabled={loading} className={className}>
      <RefreshCw className={cn('h-3.5 w-3.5', loading && 'animate-spin')} aria-hidden="true" />
      {label ?? t('common.refresh')}
    </Button>
  );
}
