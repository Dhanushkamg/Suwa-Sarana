'use client';

import { useMemo, useState } from 'react';
import { AlertCircle, CheckCircle2, CircleAlert, Package, Plus, SlidersHorizontal, TriangleAlert } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { BLOOD_TYPES } from '@/lib/constants';
import { COMPONENTS, fetchStock, stockLevel, type StockLevel, type StockRow } from '@/lib/api/inventory';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { StatusBadge, type BadgeTone } from '@/components/ui/StatusBadge';
import { Table, Td, Th } from '@/components/ui/Table';
import { CollectionDialog, ThresholdDialog } from '@/components/dash/InventoryDialogs';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { BlockSkeleton, StatsSkeleton } from '@/components/dash/Skeletons';
import { Stat } from '@/components/dash/Stat';
import { useAsync } from '@/components/dash/useAsync';
import { VerificationBadge } from '@/components/dash/VerificationBadge';
import { useAuthStore } from '@/store/authStore';

const LEVEL_TONE: Record<Exclude<StockLevel, 'unknown'>, BadgeTone> = { ok: 'success', low: 'warning', out: 'danger' };

/** Inventory for blood banks and hospitals: stock by blood group and component, minimum levels, low-stock warnings, recording collections. */
export default function InventoryPage() {
  const { t, format } = useI18n();
  const { user } = useAuthStore();
  const stock = useAsync(fetchStock);
  const [collectionOpen, setCollectionOpen] = useState(false);
  const [thresholdOpen, setThresholdOpen] = useState(false);

  const rows = useMemo(() => (stock.state.status === 'ready' ? stock.state.data : []), [stock.state]);
  const index = useMemo(() => new Map(rows.map((r) => [`${r.bloodType}|${r.component}`, r])), [rows]);
  const lowRows = useMemo(() => rows.filter((r) => ['low', 'out'].includes(stockLevel(r))).sort((a, b) => a.units - b.units), [rows]);
  const totalUnits = rows.reduce((sum, r) => sum + r.units, 0);
  const ready = stock.state.status === 'ready';
  const unavailable = stock.state.status === 'unavailable';

  const levelLabel = (level: StockLevel) => t(`dashUi.inv.level.${level}`);
  const rowLabel = (r: StockRow) => `${r.bloodType} · ${t(`dashUi.inv.component.${r.component}`)}`;

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.nav.inventory')}
        badges={<VerificationBadge status={user?.verificationStatus} verifiedLabel={t('dashboard.verifiedOrg')} />}
        title={t('dashUi.inv.title')}
        description={t('dashUi.inv.desc')}
        actions={
          <>
            <RefreshButton onClick={stock.reload} loading={stock.state.status === 'loading'} />
            <Button type="button" variant="secondary" onClick={() => setThresholdOpen(true)} disabled={unavailable}>
              <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />
              {t('dashUi.inv.setThreshold')}
            </Button>
            <Button type="button" variant="primary" onClick={() => setCollectionOpen(true)} disabled={unavailable}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              {t('dashUi.inv.addCollection')}
            </Button>
          </>
        }
      />

      {stock.state.status === 'loading' ? (
        <div className="space-y-6">
          <StatsSkeleton count={2} className="sm:grid-cols-2" />
          <BlockSkeleton className="h-96" />
        </div>
      ) : unavailable ? (
        <EmptyState icon={<Package className="h-10 w-10" aria-hidden="true" />} title={t('dashUi.inv.unavailableTitle')} description={t('dashUi.inv.unavailableDesc')} />
      ) : stock.state.status === 'error' ? (
        <EmptyState
          icon={<AlertCircle className="h-10 w-10 text-danger-text" aria-hidden="true" />}
          title={stock.state.message || t('dash.loadFailed')}
          action={
            <Button variant="secondary" onClick={() => void stock.reload()}>
              {t('dash.retry')}
            </Button>
          }
        />
      ) : (
        <div className="space-y-8">
          <div className="grid gap-4 sm:grid-cols-2">
            <Rise delay={0.04}>
              <Stat label={t('dashUi.inv.totalUnits')} value={ready && rows.length ? totalUnits : null} icon={<Package />} className="h-full" />
            </Rise>
            <Rise delay={0.08}>
              <Stat
                label={t('dashUi.inv.belowMinimum')}
                value={ready && rows.length ? lowRows.length : null}
                tone={lowRows.length ? 'warning' : 'success'}
                icon={<TriangleAlert />}
                hint={lowRows.length ? undefined : t('dashUi.inv.allAboveMinimum')}
                className="h-full"
              />
            </Rise>
          </div>

          {lowRows.length > 0 && (
            <Notice tone="warning" icon={<CircleAlert />}>
              <p className="font-medium">{t('dashUi.inv.lowTitle', { count: lowRows.length })}</p>
              <ul className="mt-1 list-disc space-y-0.5 ps-5">
                {lowRows.map((r) => (
                  <li key={`${r.bloodType}|${r.component}`}>
                    {rowLabel(r)}: {t('dashUi.units', { count: r.units })}
                    {r.threshold !== null && ` (${t('dashUi.inv.minimum', { count: r.threshold })})`}
                  </li>
                ))}
              </ul>
            </Notice>
          )}

          {rows.length === 0 && <Notice tone="info">{t('dashUi.inv.emptyHint')}</Notice>}

          <Rise delay={0.12}>
            <Table className="min-w-[44rem]">
              <caption className="sr-only">{t('dashUi.inv.title')}</caption>
              <thead>
                <tr>
                  <Th>{t('dashUi.donor.bloodType')}</Th>
                  {COMPONENTS.map((c) => (
                    <Th key={c}>{t(`dashUi.inv.component.${c}`)}</Th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {BLOOD_TYPES.map((bt) => (
                  <tr key={bt}>
                    <Td className="font-display text-2xl text-brand">{bt}</Td>
                    {COMPONENTS.map((c) => {
                      const row = index.get(`${bt}|${c}`);
                      const level = stockLevel(row);
                      return (
                        <Td key={c}>
                          {row ? (
                            <div className="space-y-1">
                              <p className="text-base font-medium tabular-nums text-foreground">{t('dashUi.units', { count: row.units })}</p>
                              {level !== 'unknown' && <StatusBadge value={level} tone={LEVEL_TONE[level]} label={levelLabel(level)} />}
                              {row.threshold !== null && <p className="text-sm text-gray-500">{t('dashUi.inv.minimum', { count: row.threshold })}</p>}
                              {row.nearestExpiry && <p className="text-sm text-gray-500">{t('dashUi.inv.expires', { date: format.date(row.nearestExpiry, { dateStyle: 'medium' }) })}</p>}
                            </div>
                          ) : (
                            <span className="text-gray-500">
                              <span aria-hidden="true">&mdash;</span>
                              <span className="sr-only">{t('dashUi.noData')}</span>
                            </span>
                          )}
                        </Td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </Table>
          </Rise>
          <p className="flex items-center gap-2 text-sm text-gray-500">
            <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
            {t('dashUi.inv.legend')}
          </p>
        </div>
      )}

      <CollectionDialog open={collectionOpen} onClose={() => setCollectionOpen(false)} onSaved={() => void stock.reload()} />
      <ThresholdDialog open={thresholdOpen} onClose={() => setThresholdOpen(false)} onSaved={() => void stock.reload()} />
    </div>
  );
}
