'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Activity, Users, AlertTriangle } from 'lucide-react';
import { fetchDistrictSummary, nationalTotals, type DistrictRow } from '@/lib/api/admin';
import { apiMessage } from '@/lib/api/common';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { useI18n } from '@/lib/i18n';
import { DistrictMapView } from '@/components/dash/DistrictMapView';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { BlockSkeleton, StatsSkeleton } from '@/components/dash/Skeletons';
import { Stat } from '@/components/dash/Stat';

export default function HeatmapPage() {
  const { t } = useI18n();
  const [districts, setDistricts] = useState<DistrictRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // The summary is shared by donors and admins; donors read the public path
  const fetchAnalytics = useCallback(async (): Promise<boolean> => {
    setLoading(true);
    setError(null);
    try {
      setDistricts(await fetchDistrictSummary('/analytics/district-summary'));
      return true;
    } catch (err: unknown) {
      setError(apiMessage(err, ''));
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchAnalytics();
  }, [fetchAnalytics]);

  const totals = useMemo(() => nationalTotals(districts), [districts]);

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.heatmap')}
        title={t('dash.heatmapTitle')}
        description={t('dash.heatmapDesc')}
        actions={<RefreshButton onClick={fetchAnalytics} loading={loading} />}
      />

      {loading ? (
        <div className="space-y-6" role="status" aria-label={t('dash.loadingIntel')}>
          <StatsSkeleton count={3} className="md:grid-cols-3" />
          <BlockSkeleton className="h-[420px]" />
        </div>
      ) : error !== null ? (
        <EmptyState
          icon={<AlertTriangle className="h-10 w-10 text-danger-text" aria-hidden="true" />}
          title={t('dash.connectionError')}
          description={error || t('dash.loadFailed')}
          action={
            <Button variant="secondary" onClick={fetchAnalytics}>
              {t('dash.retry')}
            </Button>
          }
        />
      ) : districts.length === 0 ? (
        <EmptyState icon={<Activity className="h-10 w-10" aria-hidden="true" />} title={t('dashUi.noDistrictData')} description={t('dashUi.noDistrictDataDesc')} />
      ) : (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-3">
            <Rise delay={0.04}>
              <Stat label={t('dash.criticalZones')} value={totals.criticalDistricts} tone="danger" icon={<AlertTriangle />} className="h-full" />
            </Rise>
            <Rise delay={0.08}>
              <Stat label={t('adminDashboard.activeRequests')} value={totals.activeRequests} tone="warning" icon={<Activity />} className="h-full" />
            </Rise>
            <Rise delay={0.12}>
              <Stat label={t('adminDashboard.availableDonors')} value={totals.donors} tone="success" icon={<Users />} className="h-full" />
            </Rise>
          </div>

          <Rise delay={0.16}>
            <DistrictMapView rows={districts} caption={t('dash.heatmapTitle')} />
          </Rise>
        </div>
      )}
    </div>
  );
}
