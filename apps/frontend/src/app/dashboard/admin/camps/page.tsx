'use client';

import dynamic from 'next/dynamic';
import { useI18n } from '@/lib/i18n';
import { PageHeader } from '@/components/dash/PageHeader';
import { Rise } from '@/components/dash/Rise';
import { EYEBROW } from '@/components/dash/styles';

const AdminMap = dynamic(() => import('@/components/camps/AdminMap'), { ssr: false });

export default function AdminCampsPage() {
  const { t } = useI18n();
  return (
    <div>
      <PageHeader
        eyebrow={t('adminDashboard.adminMapView')}
        title={t('adminDashboard.geospatialTracking')}
        description={t('adminDashboard.geospatialTrackingDesc')}
        backHref="/dashboard/admin"
      />

      {/* Map */}
      <Rise delay={0.05}>
        <div className="glass-card rounded-2xl p-4 sm:p-6">
          <ul className="mb-5 flex flex-wrap items-center gap-x-6 gap-y-2" aria-label={t('dashUi.legend')}>
            <li className="flex items-center gap-2 text-xs text-gray-400">
              <span aria-hidden="true" className="h-2.5 w-2.5 rounded-full bg-blue-500" />
              <span className={EYEBROW}>{t('adminDashboard.activeCamps')}</span>
            </li>
            <li className="flex items-center gap-2 text-xs text-gray-400">
              <span aria-hidden="true" className="h-2.5 w-2.5 rounded-full bg-ember" />
              <span className={EYEBROW}>{t('adminDashboard.criticalShortages')}</span>
            </li>
          </ul>

          <AdminMap />
        </div>
      </Rise>
    </div>
  );
}
