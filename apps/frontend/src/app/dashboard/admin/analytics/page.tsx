'use client';

import { useState, useEffect, useCallback } from 'react';
import { MapPin, Users, Droplets, AlertTriangle, TrendingUp, Filter, Search } from 'lucide-react';
import { motion, useReducedMotion } from 'motion/react';
import apiClient from '@/lib/apiClient';
import SriLankaHeatmap, { DistrictSummary } from '@/components/maps/SriLankaHeatmap';
import { useI18n } from '@/lib/i18n';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { Input } from '@/components/ui/Input';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { Reveal } from '@/components/motion/Reveal';
import { FilterChips } from '@/components/dash/FilterChips';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader } from '@/components/dash/Panel';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { StatsSkeleton } from '@/components/dash/Skeletons';
import { Stat } from '@/components/dash/Stat';
import { EASE, EYEBROW, ROW_HOVER, TH } from '@/components/dash/styles';
import { cn } from '@/lib/utils';

interface ApiBody<T> {
  data?: T;
}

export default function AdminAnalyticsHeatmapPage() {
  const { t } = useI18n();
  const reduce = useReducedMotion();
  const [districts, setDistricts] = useState<DistrictSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<'ALL' | 'CRITICAL' | 'WARNING' | 'BALANCED' | 'SURPLUS'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedDistrict, setSelectedDistrict] = useState<DistrictSummary | null>(null);

  const loadAnalytics = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<ApiBody<DistrictSummary[]>>('/admin/analytics/district-summary');
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
    loadAnalytics();
  }, [loadAnalytics]);

  // Aggregate Metrics
  const totalDonors = districts.reduce((acc, d) => acc + d.donorCount, 0);
  const totalActiveRequests = districts.reduce((acc, d) => acc + d.activeRequests, 0);
  const criticalDistricts = districts.filter((d) => d.shortageLevel === 'CRITICAL').length;
  const avgFulfillment = districts.length > 0
    ? Math.round((districts.reduce((acc, d) => acc + d.fulfillmentRate, 0) / districts.length) * 10) / 10
    : 0;

  const filteredDistricts = districts.filter((d) => {
    const matchesFilter = filter === 'ALL' || d.shortageLevel === filter;
    const matchesSearch = d.district.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesFilter && matchesSearch;
  });

  const levelTone = (level: string): 'red' | 'amber' | 'blue' | 'green' =>
    level === 'CRITICAL' ? 'red' : level === 'WARNING' ? 'amber' : level === 'SURPLUS' ? 'blue' : 'green';

  const filterOptions = (['ALL', 'CRITICAL', 'WARNING', 'BALANCED', 'SURPLUS'] as const).map((lvl) => ({
    value: lvl,
    label: lvl === 'ALL' ? t('adminDashboard.allDistricts') : lvl,
  }));

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.analytics')}
        title={t('adminDashboard.heatmapTitle')}
        description={t('adminDashboard.heatmapDesc')}
        backHref="/dashboard/admin"
        actions={<RefreshButton onClick={loadAnalytics} loading={loading} label={t('adminDashboard.refreshTelemetry')} />}
      />

      {loading ? (
        <div className="space-y-6" role="status" aria-label={t('common.loading')}>
          <StatsSkeleton count={4} className="grid-cols-2 lg:grid-cols-4" />
          <Skeleton className="h-[420px] w-full rounded-2xl" />
        </div>
      ) : error !== null ? (
        <EmptyState
          icon={<AlertTriangle className="w-10 h-10 text-red-400" />}
          title={error || t('dash.loadFailed')}
          action={<Button variant="secondary" onClick={loadAnalytics}>{t('dash.retry')}</Button>}
        />
      ) : (
        <div className="space-y-8">
          {/* KPI summary */}
          <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
            <Rise delay={0.04}>
              <Stat label={t('adminDashboard.registeredDonors')} value={totalDonors} tone="sage" icon={<Users />} hint={t('adminDashboard.districtsActive')} className="h-full" />
            </Rise>
            <Rise delay={0.08}>
              <Stat label={t('adminDashboard.activeRequests')} value={totalActiveRequests} icon={<Droplets />} hint={t('adminDashboard.openEmergencies')} className="h-full" />
            </Rise>
            <Rise delay={0.12}>
              <Stat label={t('adminDashboard.criticalShortages')} value={criticalDistricts} tone="ember" icon={<AlertTriangle />} hint={t('adminDashboard.urgentMobilization')} className="h-full" />
            </Rise>
            <Rise delay={0.16}>
              <Stat
                label={t('adminDashboard.nationalFulfillment')}
                value={avgFulfillment}
                suffix="%"
                meter={avgFulfillment}
                icon={<TrendingUp />}
                hint={t('adminDashboard.avgResolution')}
                className="h-full"
              />
            </Rise>
          </div>

          {/* Map and district focus */}
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="space-y-4 lg:col-span-2">
              <div className="glass-card flex flex-col justify-between gap-3 rounded-2xl p-4 sm:flex-row sm:items-center">
                <span className={cn(EYEBROW, 'flex items-center gap-2')}>
                  <Filter className="h-3.5 w-3.5 text-ember" aria-hidden="true" />
                  {t('adminDashboard.filterMap')}
                </span>
                <FilterChips id="analytics-level" label={t('adminDashboard.filterMap')} value={filter} onChange={setFilter} options={filterOptions} />
              </div>

              <SriLankaHeatmap districts={districts} filter={filter} onSelectDistrict={(d) => setSelectedDistrict(d)} />
            </div>

            {/* District detail */}
            <Panel className="flex flex-col justify-between">
              <div>
                <SectionHeader
                  eyebrow={t('adminDashboard.districtFocus')}
                  title={selectedDistrict ? selectedDistrict.district : t('adminDashboard.districtFocus')}
                  description={selectedDistrict ? undefined : t('adminDashboard.inspectMetrics')}
                  action={
                    selectedDistrict ? (
                      <StatusBadge value={selectedDistrict.shortageLevel} label={selectedDistrict.shortageLevel} tone={levelTone(selectedDistrict.shortageLevel)} />
                    ) : undefined
                  }
                />

                {selectedDistrict ? (
                  <motion.div
                    key={selectedDistrict.district}
                    initial={reduce ? false : { opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.3, ease: EASE }}
                    className="space-y-5"
                  >
                    <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-xl bg-line">
                      {[
                        { label: t('adminDashboard.availableDonors'), value: selectedDistrict.donorCount, tone: 'text-emerald-400' },
                        { label: t('adminDashboard.activeRequests'), value: selectedDistrict.activeRequests, tone: 'text-ember' },
                        { label: t('adminDashboard.fulfilledRequests'), value: selectedDistrict.fulfilledRequests, tone: 'text-foreground' },
                        { label: t('adminDashboard.totalRequests'), value: selectedDistrict.totalRequests, tone: 'text-foreground' },
                      ].map((m) => (
                        <div key={m.label} className="bg-surface p-4">
                          <dt className="text-[11px] leading-snug text-gray-500">{m.label}</dt>
                          <dd className={cn('mt-2 font-display text-3xl tabular-nums', m.tone)}>{m.value}</dd>
                        </div>
                      ))}
                    </dl>

                    <div>
                      <div className="flex items-baseline justify-between gap-3 text-xs text-gray-400">
                        <span>{t('adminDashboard.fulfillmentRate')}</span>
                        <span className="font-mono text-sm tabular-nums text-foreground">{selectedDistrict.fulfillmentRate}%</span>
                      </div>
                      <div aria-hidden="true" className="mt-2.5 h-1.5 overflow-hidden rounded-full bg-foreground/[0.06]">
                        <motion.div
                          className="h-full w-full origin-left rounded-full bg-ember"
                          initial={reduce ? false : { scaleX: 0 }}
                          animate={{ scaleX: Math.min(Math.max(selectedDistrict.fulfillmentRate, 0), 100) / 100 }}
                          transition={{ duration: 0.7, ease: EASE }}
                        />
                      </div>
                    </div>
                  </motion.div>
                ) : (
                  <p className="rounded-xl border border-dashed border-foreground/[0.16] px-4 py-12 text-center text-xs text-gray-500">
                    {t('adminDashboard.noDistrictSelected')}
                  </p>
                )}
              </div>

              <p className="mt-6 border-t border-line pt-4 text-[11px] leading-relaxed text-gray-500">{t('adminDashboard.telemetryRefreshed')}</p>
            </Panel>
          </div>

          {/* Searchable district table */}
          <Reveal y={12}>
            <section className="glass-card overflow-hidden rounded-2xl">
              <div className="flex flex-col justify-between gap-4 p-5 sm:flex-row sm:items-end sm:p-6">
                <SectionHeader
                  className="mb-0"
                  eyebrow={t('dash.district')}
                  title={t('adminDashboard.telemetryTable')}
                  description={t('adminDashboard.telemetryTableDesc')}
                />
                <div className="w-full sm:w-64">
                  <Input
                    type="search"
                    icon={<Search className="h-4 w-4" />}
                    placeholder={t('common.search')}
                    aria-label={t('common.search')}
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="py-2.5"
                  />
                </div>
              </div>

              {/* Scrolls inside this container, never the page */}
              <div className="overflow-x-auto border-t border-line">
                <table className="w-full min-w-[46rem] text-left text-sm text-gray-300">
                  <thead className="border-b border-line">
                    <tr>
                      <th scope="col" className={TH}>{t('dash.district')}</th>
                      <th scope="col" className={TH}>{t('bloodRequests.status')}</th>
                      <th scope="col" className={cn(TH, 'text-right')}>{t('adminDashboard.availableDonors')}</th>
                      <th scope="col" className={cn(TH, 'text-right')}>{t('adminDashboard.activeRequests')}</th>
                      <th scope="col" className={cn(TH, 'text-right')}>{t('adminDashboard.fulfilled')}</th>
                      <th scope="col" className={TH}>{t('adminDashboard.fulfillmentRate').replace(':', '')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {filteredDistricts.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="px-5 py-10 text-center text-sm text-gray-500">
                          {t('adminDashboard.noDistrictsFilter')}
                        </td>
                      </tr>
                    ) : (
                      filteredDistricts.map((d) => (
                        <tr
                          key={d.district}
                          onClick={() => setSelectedDistrict(d)}
                          className={cn(ROW_HOVER, 'cursor-pointer', selectedDistrict?.district === d.district && 'bg-foreground/[0.04]')}
                        >
                          <td className="px-5 py-3.5 font-medium text-foreground">
                            <button
                              type="button"
                              onClick={() => setSelectedDistrict(d)}
                              aria-pressed={selectedDistrict?.district === d.district}
                              className="flex items-center gap-2 text-left"
                            >
                              <MapPin className="h-3.5 w-3.5 text-gray-500" aria-hidden="true" />
                              {d.district}
                            </button>
                          </td>
                          <td className="px-5 py-3.5">
                            <StatusBadge value={d.shortageLevel} label={d.shortageLevel} tone={levelTone(d.shortageLevel)} />
                          </td>
                          <td className="px-5 py-3.5 text-right font-mono tabular-nums text-emerald-400">{d.donorCount}</td>
                          <td className="px-5 py-3.5 text-right font-mono tabular-nums text-ember">{d.activeRequests}</td>
                          <td className="px-5 py-3.5 text-right font-mono tabular-nums text-gray-300">{d.fulfilledRequests}</td>
                          <td className="px-5 py-3.5">
                            <div className="flex items-center gap-3">
                              <div aria-hidden="true" className="h-px min-w-16 flex-1 bg-line">
                                <div className="h-px bg-foreground/70" style={{ width: `${Math.min(Math.max(d.fulfillmentRate, 0), 100)}%` }} />
                              </div>
                              <span className="w-12 text-right font-mono text-[11px] tabular-nums text-gray-400">{d.fulfillmentRate}%</span>
                            </div>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          </Reveal>
        </div>
      )}
    </div>
  );
}
