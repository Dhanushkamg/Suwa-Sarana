'use client';

import { useCallback, useEffect, useState } from 'react';
import apiClient from '@/lib/apiClient';
import SriLankaHeatmap, { DistrictSummary } from '@/components/maps/SriLankaHeatmap';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { useI18n } from '@/lib/i18n';
import { Activity, Users, AlertTriangle } from 'lucide-react';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { Stat } from '@/components/dash/Stat';
import { StatsSkeleton } from '@/components/dash/Skeletons';

interface ApiBody<T> {
  data?: T;
}

export default function HeatmapPage() {
  const { t } = useI18n();
  const [districts, setDistricts] = useState<DistrictSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAnalytics = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<ApiBody<DistrictSummary[]>>('/analytics/district-summary');
      const data = res.data?.data;
      setDistricts(Array.isArray(data) ? data : []);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg || '');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAnalytics();
  }, [fetchAnalytics]);

  const totalDonors = districts.reduce((acc, d) => acc + d.donorCount, 0);
  const totalRequests = districts.reduce((acc, d) => acc + d.activeRequests, 0);
  const criticalDistricts = districts.filter(d => d.shortageLevel === 'CRITICAL').length;

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
          <Skeleton className="h-[420px] w-full rounded-2xl" />
        </div>
      ) : error !== null ? (
        <EmptyState
          icon={<AlertTriangle className="w-10 h-10 text-red-400" />}
          title={t('dash.connectionError')}
          description={error || t('dash.loadFailed')}
          action={<Button variant="secondary" onClick={fetchAnalytics}>{t('dash.retry')}</Button>}
        />
      ) : districts.length === 0 ? (
        <EmptyState icon={<Activity className="w-10 h-10" />} title={t('adminDashboard.noData')} />
      ) : (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-3">
            <Rise delay={0.04}>
              <Stat label={t('dash.criticalZones')} value={criticalDistricts} tone="ember" icon={<AlertTriangle />} className="h-full" />
            </Rise>
            <Rise delay={0.08}>
              <Stat label={t('adminDashboard.activeRequests')} value={totalRequests} tone="saffron" icon={<Activity />} className="h-full" />
            </Rise>
            <Rise delay={0.12}>
              <Stat label={t('adminDashboard.availableDonors')} value={totalDonors} tone="sage" icon={<Users />} className="h-full" />
            </Rise>
          </div>

          <Rise delay={0.16}>
            <SriLankaHeatmap districts={districts} />
          </Rise>
        </div>
      )}
    </div>
  );
}
