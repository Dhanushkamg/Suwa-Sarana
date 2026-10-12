'use client';

import Link from 'next/link';
import { SearchX } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';

export default function NotFound() {
  const { t } = useI18n();

  return (
    <main className="flex min-h-[70vh] items-center justify-center px-4">
      <EmptyState
        icon={<SearchX className="h-10 w-10" />}
        title={t('errors.notFoundTitle')}
        description={t('errors.notFoundDescription')}
        action={
          <Link href="/">
            <Button>{t('common.backToHome')}</Button>
          </Link>
        }
      />
    </main>
  );
}
