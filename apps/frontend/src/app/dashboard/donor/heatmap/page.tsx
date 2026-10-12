'use client';

import { useCallback, useEffect, useState } from 'react';
import apiClient from '@/lib/apiClient';
import SriLankaHeatmap, { DistrictSummary } from '@/components/maps/SriLankaHeatmap';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { useI18n } from '@/lib/i18n';
import { Activity, Users, AlertTriangle } from 'lucide-react';

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
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white mb-2">{t('dash.heatmapTitle')}</h1>
          <p className="text-gray-400 text-sm max-w-xl">{t('dash.heatmapDesc')}</p>
        </div>
      </div>

      {loading ? (
        <div className="space-y-4" role="status" aria-label={t('dash.loadingIntel')}>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
          </div>
          <Skeleton className="h-[420px] w-full" />
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
        <>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="glass-card p-6 rounded-2xl border border-white/5">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-red-500/10 flex items-center justify-center text-red-400">
                  <AlertTriangle className="w-5 h-5" />
                </div>
                <h3 className="text-sm font-semibold text-gray-300">{t('dash.criticalZones')}</h3>
              </div>
              <p className="text-3xl font-black text-white">{criticalDistricts}</p>
            </div>
            
            <div className="glass-card p-6 rounded-2xl border border-white/5">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-rose-500/10 flex items-center justify-center text-rose-400">
                  <Activity className="w-5 h-5" />
                </div>
                <h3 className="text-sm font-semibold text-gray-300">{t('adminDashboard.activeRequests')}</h3>
              </div>
              <p className="text-3xl font-black text-white">{totalRequests}</p>
            </div>

            <div className="glass-card p-6 rounded-2xl border border-white/5">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-emerald-500/10 flex items-center justify-center text-emerald-400">
                  <Users className="w-5 h-5" />
                </div>
                <h3 className="text-sm font-semibold text-gray-300">{t('adminDashboard.availableDonors')}</h3>
              </div>
              <p className="text-3xl font-black text-white">{totalDonors}</p>
            </div>
          </div>

          <div className="w-full">
            <SriLankaHeatmap districts={districts} />
          </div>
        </>
      )}
    </div>
  );
}
