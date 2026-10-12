'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { AlertTriangle } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';

export default function Error({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  const { t } = useI18n();

  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="flex min-h-[70vh] items-center justify-center px-4">
      <EmptyState
        icon={<AlertTriangle className="h-10 w-10" />}
        title={t('errors.title')}
        description={t('errors.description')}
        action={
          <div className="flex flex-wrap justify-center gap-3">
            <Button onClick={() => retry()}>{t('errors.retry')}</Button>
            <Link href="/">
              <Button variant="secondary">{t('common.backToHome')}</Button>
            </Link>
          </div>
        }
      />
    </main>
  );
}
