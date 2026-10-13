'use client';

import { useAuthStore } from '@/store/authStore';
import CampManagerDashboard from '@/components/camps/CampManagerDashboard';
import { useI18n } from '@/lib/i18n';
import { Skeleton } from '@/components/ui/Skeleton';
import { PageHeader } from '@/components/dash/PageHeader';
import { VerificationBadge } from '@/components/dash/VerificationBadge';
import { Reveal } from '@/components/motion/Reveal';

export default function BloodBankDashboardPage() {
  const { user } = useAuthStore();
  const { t } = useI18n();

  if (!user) {
    return (
      <div role="status" aria-label={t('common.loading')}>
        <Skeleton className="mb-3 h-3 w-40" />
        <Skeleton className="mb-8 h-12 w-3/5 rounded-xl" />
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        eyebrow={t('dashboard.bloodBankPortal')}
        badges={<VerificationBadge status={user.verificationStatus} verifiedLabel={t('dashboard.verifiedOrg')} />}
        title={t('dashboard.bloodBankCommandCenter')}
        description={t('dashboard.bloodBankCommandCenterDesc')}
      />

      {/* Donation camp management */}
      <Reveal y={12}>
        <CampManagerDashboard />
      </Reveal>
    </div>
  );
}
