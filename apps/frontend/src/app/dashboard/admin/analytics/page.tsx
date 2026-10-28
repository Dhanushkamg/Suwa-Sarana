'use client';

import { useState, useMemo, useCallback } from 'react';
import { Users, Droplets, AlertTriangle, TrendingUp, Filter, Search } from 'lucide-react';
import { fetchDistrictSummary, nationalTotals, type DistrictRow } from '@/lib/api/admin';
import { useI18n } from '@/lib/i18n';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { DistrictMapView } from '@/components/dash/DistrictMapView';
import { ShortageBadge } from '@/components/dash/DistrictTable';
import { FilterChips } from '@/components/dash/FilterChips';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader } from '@/components/dash/Panel';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { BlockSkeleton, StatsSkeleton } from '@/components/dash/Skeletons';
import { Stat } from '@/components/dash/Stat';
import { CARD, EYEBROW } from '@/components/dash/styles';
import { useAsync } from '@/components/dash/useAsync';
import { cn } from '@/lib/utils';

type LevelFilter = 'ALL' | 'CRITICAL' | 'WARNING' | 'BALANCED' | 'SURPLUS';
const LEVELS: LevelFilter[] = ['ALL', 'CRITICAL', 'WARNING', 'BALANCED', 'SURPLUS'];

export default function AdminAnalyticsHeatmapPage() {
  const { t, format } = useI18n();
  const data = useAsync(fetchDistrictSummary);
  const [filter, setFilter] = useState<LevelFilter>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedName, setSelectedName] = useState<string | null>(null);

  const districts = useMemo(() => (data.state.status === 'ready' ? data.state.data : []), [data.state]);
  const totals = useMemo(() => nationalTotals(districts), [districts]);
  const selectedDistrict = districts.find((d) => d.district === selectedName) ?? null;

  // Stable identity: the map rebuilds its markers (and closes the open popup) whenever this function changes
  const handleSelect = useCallback((row: DistrictRow) => setSelectedName(row.district), []);

  const filterOptions = LEVELS.map((lvl) => ({
    value: lvl,
    label: lvl === 'ALL' ? t('adminDashboard.allDistricts') : t(`dashUi.shortage.${lvl}`),
  }));

  const loading = data.state.status === 'loading';

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.analytics')}
        title={t('adminDashboard.heatmapTitle')}
        description={t('adminDashboard.heatmapDesc')}
        backHref="/dashboard/admin"
        actions={<RefreshButton onClick={data.reload} loading={loading} label={t('adminDashboard.refreshTelemetry')} />}
      />

      {loading ? (
        <div className="space-y-6" role="status" aria-label={t('common.loading')}>
          <StatsSkeleton count={4} className="grid-cols-2 lg:grid-cols-4" />
          <BlockSkeleton className="h-[420px]" />
        </div>
      ) : data.state.status !== 'ready' ? (
        <EmptyState
          icon={<AlertTriangle className="h-10 w-10 text-danger-text" aria-hidden="true" />}
          title={(data.state.status === 'error' && data.state.message) || t('dash.loadFailed')}
          action={
            <Button variant="secondary" onClick={() => void data.reload()}>
              {t('dash.retry')}
            </Button>
          }
        />
      ) : districts.length === 0 ? (
        <EmptyState icon={<Droplets className="h-10 w-10" aria-hidden="true" />} title={t('dashUi.noDistrictData')} description={t('dashUi.noDistrictDataDesc')} />
      ) : (
        <div className="space-y-8">
          {/* KPI summary: sums of the district totals; the fulfilment rate is weighted by request count */}
          <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
            <Rise delay={0.04}>
              <Stat label={t('adminDashboard.availableDonors')} value={totals.donors} tone="success" icon={<Users />} hint={t('dashUi.admin.donorsHint')} className="h-full" />
            </Rise>
            <Rise delay={0.08}>
              <Stat label={t('adminDashboard.activeRequests')} value={totals.activeRequests} icon={<Droplets />} hint={t('adminDashboard.openEmergencies')} className="h-full" />
            </Rise>
            <Rise delay={0.12}>
              <Stat label={t('adminDashboard.criticalShortages')} value={totals.criticalDistricts} tone="danger" icon={<AlertTriangle />} hint={t('adminDashboard.urgentMobilization')} className="h-full" />
            </Rise>
            <Rise delay={0.16}>
              <Stat
                label={t('adminDashboard.nationalFulfillment')}
                value={totals.fulfillmentRate}
                suffix="%"
                meter={totals.fulfillmentRate ?? undefined}
                tone="primary"
                icon={<TrendingUp />}
                hint={t('dashUi.admin.fulfilmentHint')}
                className="h-full"
              />
            </Rise>
          </div>

          {/* Map / table and district focus */}
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="space-y-4 lg:col-span-2">
              <div className={cn(CARD, 'flex flex-col justify-between gap-3 p-4')}>
                <span className={cn(EYEBROW, 'flex items-center gap-2')}>
                  <Filter className="h-4 w-4 text-primary-text" aria-hidden="true" />
                  {t('dashUi.admin.filterLevel')}
                </span>
                <FilterChips id="analytics-level" label={t('dashUi.admin.filterLevel')} value={filter} onChange={setFilter} options={filterOptions} />
                <div className="max-w-xs">
                  <Input
                    type="search"
                    icon={<Search className="h-4 w-4" aria-hidden="true" />}
                    label={t('common.search')}
                    placeholder={t('dashUi.admin.searchDistrict')}
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />
                </div>
              </div>

              <DistrictMapView rows={districts} filter={filter} query={searchQuery} selected={selectedName} onSelect={handleSelect} caption={t('adminDashboard.telemetryTable')} />
            </div>

            {/* District detail */}
            <Panel className="flex flex-col justify-between">
              <div aria-live="polite">
                <SectionHeader
                  eyebrow={t('adminDashboard.districtFocus')}
                  title={selectedDistrict ? selectedDistrict.district : t('adminDashboard.districtFocus')}
                  description={selectedDistrict ? undefined : t('dashUi.admin.inspectMetrics')}
                  action={selectedDistrict ? <ShortageBadge level={selectedDistrict.shortageLevel} /> : undefined}
                />

                {selectedDistrict ? (
                  <div className="space-y-5">
                    <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-xl bg-line">
                      {[
                        { label: t('adminDashboard.availableDonors'), value: selectedDistrict.donorCount, tone: 'text-success-text' },
                        { label: t('adminDashboard.activeRequests'), value: selectedDistrict.activeRequests, tone: 'text-warning-text' },
                        { label: t('adminDashboard.fulfilledRequests'), value: selectedDistrict.fulfilledRequests, tone: 'text-foreground' },
                        { label: t('adminDashboard.totalRequests'), value: selectedDistrict.totalRequests, tone: 'text-foreground' },
                      ].map((m) => (
                        <div key={m.label} className="bg-surface p-4">
                          <dt className="text-sm leading-snug text-gray-500">{m.label}</dt>
                          <dd className={cn('mt-2 font-display text-3xl tabular-nums', m.tone)}>{format.number(m.value)}</dd>
                        </div>
                      ))}
                    </dl>

                    <div>
                      <div className="flex items-baseline justify-between gap-3 text-sm text-gray-400">
                        <span>{t('dashUi.fulfilmentRate')}</span>
                        <span className="font-medium tabular-nums text-foreground">
                          {selectedDistrict.fulfillmentRate === null ? t('dashUi.noData') : `${format.number(selectedDistrict.fulfillmentRate, { maximumFractionDigits: 1 })}%`}
                        </span>
                      </div>
                      {selectedDistrict.fulfillmentRate !== null && (
                        <div aria-hidden="true" className="mt-2.5 h-1.5 overflow-hidden rounded-full bg-foreground/[0.08]">
                          <div className="h-full rounded-full bg-primary" style={{ width: `${Math.min(Math.max(selectedDistrict.fulfillmentRate, 0), 100)}%` }} />
                        </div>
                      )}
                    </div>
                  </div>
                ) : (
                  <p className="rounded-xl border border-dashed border-line-strong px-4 py-12 text-center text-sm text-gray-500">{t('dashUi.admin.noDistrictSelected')}</p>
                )}
              </div>

              <p className="mt-6 border-t border-line pt-4 text-sm leading-relaxed text-gray-500">{t('dashUi.admin.metricsDefinition')}</p>
            </Panel>
          </div>
        </div>
      )}
    </div>
  );
}
