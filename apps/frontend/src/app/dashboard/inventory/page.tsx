'use client';

import { useCallback, useMemo, useState } from 'react';
import { AlertCircle, CheckCircle2, CircleAlert, Clock, MapPin, Package, Pencil, Plus, SendHorizontal, SlidersHorizontal, Trash2, TriangleAlert } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { BLOOD_TYPES } from '@/lib/constants';
import { useToast } from '@/components/ui/Toast';
import {
  COMPONENTS,
  attentionLines,
  confirmStock,
  discardBatch,
  fetchMovements,
  fetchStock,
  fetchThresholds,
  type StockBatch,
  type StockLine,
  type StockStatus,
} from '@/lib/api/inventory';
import { apiMessage } from '@/lib/api/common';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { StatusBadge, type BadgeTone } from '@/components/ui/StatusBadge';
import { Table, Td, Th } from '@/components/ui/Table';
import { ConfirmDialog } from '@/components/dash/ConfirmDialog';
import { CollectionDialog, EditBatchDialog, IssueDialog, SiteDialog, ThresholdDialog } from '@/components/dash/InventoryDialogs';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader } from '@/components/dash/Panel';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { BlockSkeleton, StatsSkeleton } from '@/components/dash/Skeletons';
import { Stat } from '@/components/dash/Stat';
import { useAsync } from '@/components/dash/useAsync';
import { VerificationBadge } from '@/components/dash/VerificationBadge';
import { useAuthStore } from '@/store/authStore';

const STATUS_TONE: Record<StockStatus, BadgeTone> = { OK: 'success', LOW: 'warning', CRITICAL: 'danger', EXPIRING_SOON: 'info' };

/**
 * Stock for blood banks and hospitals (GET /api/blood-bank/stock): usable units by blood group and component, low/critical levels,
 * batches with expiry dates, issuing units, the movement log, and the public-ticker site settings.
 */
export default function InventoryPage() {
  const { t, format } = useI18n();
  const toast = useToast();
  const { user } = useAuthStore();
  const stock = useAsync(fetchStock);
  const thresholds = useAsync(fetchThresholds);
  const movements = useAsync(useCallback(() => fetchMovements(30), []));
  const [collectionOpen, setCollectionOpen] = useState(false);
  const [thresholdOpen, setThresholdOpen] = useState(false);
  const [issueFor, setIssueFor] = useState<{ open: boolean; bloodType?: string; component?: string }>({ open: false });
  const [siteOpen, setSiteOpen] = useState(false);
  const [editing, setEditing] = useState<StockBatch | null>(null);
  const [discarding, setDiscarding] = useState<StockBatch | null>(null);
  const [confirming, setConfirming] = useState(false);

  const overview = stock.state.status === 'ready' ? stock.state.data : null;
  const lines = useMemo(() => overview?.lines ?? [], [overview]);
  const batches = useMemo(() => (overview?.batches ?? []).filter((b) => b.status === 'ACTIVE'), [overview]);
  const index = useMemo(() => new Map(lines.map((l) => [`${l.bloodType}|${l.component}`, l])), [lines]);
  const attention = useMemo(() => attentionLines(lines), [lines]);
  const belowLevel = lines.filter((l) => l.status === 'LOW' || l.status === 'CRITICAL').length;
  const totalUnits = lines.reduce((sum, l) => sum + l.units, 0);
  const expiringUnits = lines.reduce((sum, l) => sum + l.expiringSoonUnits, 0);
  const unavailable = stock.state.status === 'unavailable';
  const site = overview?.site ?? null;

  const reloadAll = useCallback(async () => {
    const results = await Promise.all([stock.reload(), thresholds.reload(), movements.reload()]);
    return results.every(Boolean);
  }, [stock, thresholds, movements]);

  const lineLabel = (l: Pick<StockLine, 'bloodType' | 'component'>) => `${l.bloodType} · ${t(`dashUi.inv.component.${l.component}`)}`;
  const statusChip = (status: StockStatus) => <StatusBadge value={status} tone={STATUS_TONE[status]} label={t(`dashUi.inv.status.${status}`)} />;

  const confirmNow = async () => {
    setConfirming(true);
    try {
      await confirmStock();
      toast.show(t('dashUi.inv.confirmedMsg'), 'success');
      void stock.reload();
    } catch (err) {
      toast.show(apiMessage(err, t('common.error')), 'error');
    } finally {
      setConfirming(false);
    }
  };

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.nav.inventory')}
        badges={<VerificationBadge status={user?.verificationStatus} verifiedLabel={t('dashboard.verifiedOrg')} />}
        title={t('dashUi.inv.title')}
        description={t('dashUi.inv.desc')}
        actions={
          <>
            <RefreshButton onClick={reloadAll} loading={stock.state.status === 'loading'} />
            <Button type="button" variant="secondary" onClick={() => setThresholdOpen(true)} disabled={unavailable}>
              <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />
              {t('dashUi.inv.setThreshold')}
            </Button>
            <Button type="button" variant="secondary" onClick={() => setIssueFor({ open: true })} disabled={unavailable}>
              <SendHorizontal className="h-4 w-4" aria-hidden="true" />
              {t('dashUi.inv.issueTitle')}
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
          <StatsSkeleton count={3} className="sm:grid-cols-3" />
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
          <div className="grid gap-4 sm:grid-cols-3">
            <Rise delay={0.04}>
              <Stat label={t('dashUi.inv.totalUnits')} value={lines.length ? totalUnits : null} icon={<Package />} hint={t('dashUi.inv.usableHint')} className="h-full" />
            </Rise>
            <Rise delay={0.08}>
              <Stat
                label={t('dashUi.inv.belowMinimum')}
                value={lines.length ? belowLevel : null}
                tone={belowLevel ? 'warning' : 'success'}
                icon={<TriangleAlert />}
                hint={belowLevel ? undefined : t('dashUi.inv.allAboveMinimum')}
                className="h-full"
              />
            </Rise>
            <Rise delay={0.12}>
              <Stat
                label={t('dashUi.inv.expiringSoon')}
                value={lines.length ? expiringUnits : null}
                tone={expiringUnits ? 'warning' : 'default'}
                icon={<Clock />}
                hint={t('dashUi.inv.expiringHint', { days: overview?.expiringSoonDays ?? 3 })}
                className="h-full"
              />
            </Rise>
          </div>

          {/* Freshness and the public ticker: stock is only shown publicly when it was confirmed in the last 48 hours */}
          <Panel>
            <SectionHeader eyebrow={t('dashUi.inv.siteEyebrow')} title={site?.siteName || t('dashUi.inv.siteUnnamed')} />
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="space-y-1 text-sm text-gray-400">
                <p className="flex items-center gap-2">
                  <MapPin className="h-4 w-4 text-gray-500" aria-hidden="true" />
                  {site?.district ?? t('dashUi.inv.districtMissing')}
                </p>
                <p>{site?.publicSignals ? (site.district ? t('dashUi.inv.tickerOn') : t('dashUi.inv.tickerNeedsDistrict')) : t('dashUi.inv.tickerOff')}</p>
                <p>{site?.confirmedAt ? t('dashUi.inv.confirmedAt', { date: format.dateTime(site.confirmedAt) }) : t('dashUi.inv.neverConfirmed')}</p>
              </div>
              <div className="flex flex-wrap gap-3">
                <Button type="button" variant="secondary" onClick={() => setSiteOpen(true)}>
                  {t('dashUi.inv.siteTitle')}
                </Button>
                <Button type="button" variant="primary" onClick={confirmNow} loading={confirming}>
                  <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                  {t('dashUi.inv.confirmStock')}
                </Button>
              </div>
            </div>
          </Panel>

          {attention.length > 0 && (
            <Notice tone="warning" icon={<CircleAlert />}>
              <p className="font-medium">{t('dashUi.inv.lowTitle', { count: attention.length })}</p>
              <ul className="mt-1 list-disc space-y-0.5 ps-5">
                {attention.map((l) => (
                  <li key={`${l.bloodType}|${l.component}`}>
                    {lineLabel(l)}: {t('dashUi.units', { count: l.units })} ({t(`dashUi.inv.status.${l.status}`)}
                    {l.status === 'EXPIRING_SOON' ? `: ${t('dashUi.inv.expiringUnits', { count: l.expiringSoonUnits })}` : ''})
                  </li>
                ))}
              </ul>
            </Notice>
          )}

          {lines.length === 0 && <Notice tone="info">{t('dashUi.inv.emptyHint')}</Notice>}

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
                      const line = index.get(`${bt}|${c}`);
                      return (
                        <Td key={c}>
                          {line ? (
                            <div className="space-y-1">
                              <p className="text-base font-medium tabular-nums text-foreground">{t('dashUi.units', { count: line.units })}</p>
                              {statusChip(line.status)}
                              {line.lowThreshold !== null && (
                                <p className="text-sm text-gray-500">
                                  {t('dashUi.inv.levels', { low: line.lowThreshold, critical: line.criticalThreshold ?? 0 })}
                                </p>
                              )}
                              {line.nextExpiry && <p className="text-sm text-gray-500">{t('dashUi.inv.expires', { date: format.date(line.nextExpiry, { dateStyle: 'medium' }) })}</p>}
                              {line.units > 0 && (
                                <button
                                  type="button"
                                  onClick={() => setIssueFor({ open: true, bloodType: bt, component: c })}
                                  className="inline-flex min-h-11 items-center text-sm text-primary-text underline-offset-4 hover:underline"
                                  aria-label={`${t('dashUi.inv.issueTitle')}: ${lineLabel(line)}`}
                                >
                                  {t('dashUi.inv.issueShort')}
                                </button>
                              )}
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

          {/* Batches: the individual collections, earliest expiry first */}
          <section aria-labelledby="inv-batches">
            <SectionHeader eyebrow={t('dashUi.inv.batchesEyebrow')} title={t('dashUi.inv.batchesTitle')} />
            {batches.length === 0 ? (
              <p className="py-4 text-sm text-gray-500">{t('dashUi.inv.noBatches')}</p>
            ) : (
              <Table className="min-w-[40rem]">
                <caption className="sr-only" id="inv-batches">
                  {t('dashUi.inv.batchesTitle')}
                </caption>
                <thead>
                  <tr>
                    <Th>{t('dashUi.donor.bloodType')}</Th>
                    <Th>{t('dashUi.inv.componentLabel')}</Th>
                    <Th className="text-end">{t('dashUi.inv.units')}</Th>
                    <Th>{t('dashUi.inv.collectedOn')}</Th>
                    <Th>{t('dashUi.inv.expiresOn')}</Th>
                    <Th>
                      <span className="sr-only">{t('common.actions')}</span>
                    </Th>
                  </tr>
                </thead>
                <tbody>
                  {[...batches]
                    .sort((a, b) => a.expiresOn.localeCompare(b.expiresOn))
                    .map((b) => (
                      <tr key={b.id}>
                        <Td className="font-display text-xl text-brand">{b.bloodType}</Td>
                        <Td>{t(`dashUi.inv.component.${b.component}`)}</Td>
                        <Td className="text-end tabular-nums">{format.number(b.units)}</Td>
                        <Td>{b.collectedOn ? format.date(b.collectedOn, { dateStyle: 'medium' }) : <span className="text-gray-500">&mdash;</span>}</Td>
                        <Td>
                          <p>{format.date(b.expiresOn, { dateStyle: 'medium' })}</p>
                          <p className="text-sm text-gray-500">{t('dashUi.inv.daysLeft', { count: Math.max(b.daysToExpiry, 0) })}</p>
                        </Td>
                        <Td>
                          <div className="flex flex-wrap gap-2">
                            <Button type="button" size="sm" variant="secondary" onClick={() => setEditing(b)} aria-label={`${t('common.edit')}: ${b.bloodType} ${t(`dashUi.inv.component.${b.component}`)} (${b.expiresOn})`}>
                              <Pencil className="h-4 w-4" aria-hidden="true" />
                              {t('common.edit')}
                            </Button>
                            <Button type="button" size="sm" variant="secondary" className="border-danger/40 text-danger-text" onClick={() => setDiscarding(b)} aria-label={`${t('dashUi.inv.discard')}: ${b.bloodType} ${t(`dashUi.inv.component.${b.component}`)} (${b.expiresOn})`}>
                              <Trash2 className="h-4 w-4" aria-hidden="true" />
                              {t('dashUi.inv.discard')}
                            </Button>
                          </div>
                        </Td>
                      </tr>
                    ))}
                </tbody>
              </Table>
            )}
          </section>

          {/* Movement log */}
          <section>
            <SectionHeader eyebrow={t('dashUi.inv.movementsEyebrow')} title={t('dashUi.inv.movementsTitle')} />
            {movements.state.status === 'loading' ? (
              <BlockSkeleton className="h-32" />
            ) : movements.state.status !== 'ready' ? (
              <Notice tone="error">{movements.state.status === 'error' && movements.state.message ? movements.state.message : t('dash.loadFailed')}</Notice>
            ) : movements.state.data.length === 0 ? (
              <p className="py-4 text-sm text-gray-500">{t('dashUi.inv.noMovements')}</p>
            ) : (
              <ul className="divide-y divide-line rounded-xl border border-line">
                {movements.state.data.map((m) => (
                  <li key={m.id} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 px-4 py-3 text-sm">
                    <span className="text-foreground">
                      <span className="font-medium tabular-nums">{m.delta > 0 ? `+${format.number(m.delta)}` : format.number(m.delta)}</span> {lineLabel(m)} ·{' '}
                      {t(`dashUi.inv.reason.${m.reason}`)}
                      {m.requestId ? ` · ${t('common.request')} #${m.requestId}` : ''}
                      {m.note ? ` · ${m.note}` : ''}
                    </span>
                    {m.createdAt && <span className="text-gray-500">{format.dateTime(m.createdAt)}</span>}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}

      <CollectionDialog open={collectionOpen} onClose={() => setCollectionOpen(false)} onSaved={() => void reloadAll()} />
      <ThresholdDialog open={thresholdOpen} onClose={() => setThresholdOpen(false)} onSaved={() => void reloadAll()} thresholds={thresholds.state.status === 'ready' ? thresholds.state.data : []} />
      <IssueDialog open={issueFor.open} defaults={issueFor.bloodType && issueFor.component ? { bloodType: issueFor.bloodType, component: issueFor.component } : undefined} onClose={() => setIssueFor({ open: false })} onSaved={() => void reloadAll()} />
      <SiteDialog open={siteOpen} site={site} onClose={() => setSiteOpen(false)} onSaved={() => void reloadAll()} />
      <EditBatchDialog batch={editing} onClose={() => setEditing(null)} onSaved={() => void reloadAll()} />
      <ConfirmDialog
        open={discarding !== null}
        onClose={() => setDiscarding(null)}
        title={discarding ? t('dashUi.inv.discardTitle', { type: discarding.bloodType, component: t(`dashUi.inv.component.${discarding.component}`) }) : ''}
        description={discarding ? t('dashUi.inv.discardDesc', { count: discarding.units }) : undefined}
        confirmLabel={t('dashUi.inv.discard')}
        confirmVariant="danger"
        onConfirm={async () => {
          if (!discarding) return;
          await discardBatch(discarding.id);
          void reloadAll();
        }}
        errorMessage={(err) => apiMessage(err, t('common.error'))}
      />
    </div>
  );
}
