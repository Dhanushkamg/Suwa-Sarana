'use client';

import { Package, TriangleAlert } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import CampManagerDashboard from '@/components/camps/CampManagerDashboard';
import { useI18n } from '@/lib/i18n';
import { fetchStock } from '@/lib/api/inventory';
import { Skeleton } from '@/components/ui/Skeleton';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { PageHeader } from '@/components/dash/PageHeader';
import { Rise } from '@/components/dash/Rise';
import { StatsSkeleton } from '@/components/dash/Skeletons';
import { Stat } from '@/components/dash/Stat';
import { useAsync } from '@/components/dash/useAsync';
import { VerificationBadge } from '@/components/dash/VerificationBadge';

export default function BloodBankDashboardPage() {
  const { user } = useAuthStore();
  const { t } = useI18n();
  const stock = useAsync(fetchStock);

  if (!user) {
    return (
      <div role="status" aria-label={t('common.loading')}>
        <Skeleton className="mb-3 h-3 w-40" />
        <Skeleton className="mb-8 h-12 w-3/5 rounded-xl" />
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
    );
  }

  const ready = stock.state.status === 'ready';
  const lines = stock.state.status === 'ready' ? stock.state.data.lines : [];
  const below = lines.filter((l) => l.status === 'LOW' || l.status === 'CRITICAL').length;

  return (
    <div>
      <PageHeader
        eyebrow={t('dashboard.bloodBankPortal')}
        badges={<VerificationBadge status={user.verificationStatus} verifiedLabel={t('dashboard.verifiedOrg')} />}
        title={t('dashboard.bloodBankCommandCenter')}
        description={t('dashboard.bloodBankCommandCenterDesc')}
        actions={
          <ButtonLink href="/dashboard/inventory" variant="primary">
            <Package className="h-4 w-4" aria-hidden="true" />
            {t('dashUi.inv.manage')}
          </ButtonLink>
        }
      />

      <div className="space-y-12">
        {/* Inventory summary: real numbers from the inventory API, or "no data" */}
        {stock.state.status === 'loading' ? (
          <StatsSkeleton count={2} className="sm:grid-cols-2" />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <Rise delay={0.04}>
              <Stat
                label={t('dashUi.inv.totalUnits')}
                value={ready && lines.length ? lines.reduce((sum, l) => sum + l.units, 0) : null}
                icon={<Package />}
                hint={stock.state.status === 'unavailable' ? t('dashUi.inv.unavailableTitle') : undefined}
                className="h-full"
              />
            </Rise>
            <Rise delay={0.08}>
              <Stat label={t('dashUi.inv.belowMinimum')} value={ready && lines.length ? below : null} tone={below ? 'warning' : 'success'} icon={<TriangleAlert />} className="h-full" />
            </Rise>
          </div>
        )}

        {/* Donation camp management */}
        <Rise inView>
          <CampManagerDashboard />
        </Rise>
      </div>
    </div>
  );
}
