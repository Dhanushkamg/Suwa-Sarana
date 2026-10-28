'use client';

import { useCallback, useMemo, useState } from 'react';
import Link from 'next/link';
import { ShieldAlert, BarChart3, Flag, UserCheck, MapPin, ArrowUpRight, Droplets, Users, TrendingUp, CheckCircle2, Activity } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { useLowData } from '@/lib/lowData';
import { fetchDistrictSummary, fetchFlaggedReports, fetchPendingVerifications, nationalTotals, type DistrictRow } from '@/lib/api/admin';
import { Button } from '@/components/ui/Button';
import { Tabs } from '@/components/ui/Tabs';
import { Table, Td, Th } from '@/components/ui/Table';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { IconTile } from '@/components/dash/IconTile';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader } from '@/components/dash/Panel';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton, StatsSkeleton } from '@/components/dash/Skeletons';
import { Stat } from '@/components/dash/Stat';
import { CARD, TEXT_LINK } from '@/components/dash/styles';
import { useAsync } from '@/components/dash/useAsync';
import { Skeleton } from '@/components/ui/Skeleton';

/** Horizontal bars whose length is proportional to the count (the largest district fills the track). Numbers are always printed. */
function DistrictBars({ rows, max }: { rows: DistrictRow[]; max: number }) {
  const { t, format } = useI18n();
  const ticks = [0, max / 2, max];
  return (
    <div>
      <ul className="space-y-3">
        {rows.map((r) => (
          <li key={r.district} className="grid grid-cols-[6.5rem_minmax(0,1fr)_3.5rem] items-center gap-x-3 sm:grid-cols-[9rem_minmax(0,1fr)_4.5rem]">
            <span className="break-words text-end text-sm leading-snug text-foreground">{r.district}</span>
            <span aria-hidden="true" className="block h-3 overflow-hidden rounded-full bg-foreground/[0.08]">
              <span className="block h-full min-w-[3px] rounded-full bg-primary" style={{ width: `${(r.totalRequests / max) * 100}%` }} />
            </span>
            <span className="text-end text-sm font-medium tabular-nums text-foreground">{format.number(r.totalRequests)}</span>
          </li>
        ))}
      </ul>
      {/* Axis: 0, half, max (same columns as the bars) */}
      <div aria-hidden="true" className="mt-3 grid grid-cols-[6.5rem_minmax(0,1fr)_3.5rem] gap-x-3 sm:grid-cols-[9rem_minmax(0,1fr)_4.5rem]">
        <span />
        <span className="flex justify-between border-t border-line pt-1 text-xs tabular-nums text-gray-500">
          {ticks.map((tick, i) => (
            <span key={i}>{format.number(tick, { maximumFractionDigits: 1 })}</span>
          ))}
        </span>
        <span />
      </div>
      <p className="mt-2 text-sm text-gray-500">{t('dashUi.admin.barsNote')}</p>
    </div>
  );
}

export default function AdminDashboardPage() {
  const { t, format } = useI18n();
  const { lowData } = useLowData();
  const summary = useAsync(fetchDistrictSummary);
  const reports = useAsync(fetchFlaggedReports);
  const pending = useAsync(fetchPendingVerifications);
  const [viewChoice, setViewChoice] = useState<'chart' | 'table' | null>(null);
  // Low-Data Mode: a table instead of a drawn chart
  const view = viewChoice ?? (lowData ? 'table' : 'chart');

  const refreshAll = useCallback(async () => {
    const results = await Promise.all([summary.reload(), reports.reload(), pending.reload()]);
    return results.every(Boolean);
  }, [summary, reports, pending]);

  const rows = useMemo(() => (summary.state.status === 'ready' ? summary.state.data : []), [summary.state]);
  const totals = useMemo(() => nationalTotals(rows), [rows]);
  const withRequests = useMemo(() => rows.filter((r) => r.totalRequests > 0).sort((a, b) => b.totalRequests - a.totalRequests), [rows]);
  const maxCount = withRequests[0]?.totalRequests ?? 0;
  const summaryLoading = summary.state.status === 'loading';

  const hubLinks = [
    { href: '/dashboard/admin/verifications', icon: UserCheck, title: t('adminDashboard.verifications'), desc: t('adminDashboard.verificationsDesc') },
    { href: '/dashboard/admin/triage', icon: ShieldAlert, title: t('adminDashboard.triage'), desc: t('adminDashboard.triageDesc') },
    { href: '/dashboard/admin/analytics', icon: BarChart3, title: t('adminDashboard.analytics'), desc: t('adminDashboard.heatmapDesc') },
    { href: '/dashboard/admin/camps', icon: MapPin, title: t('layout.campsMapView'), desc: t('adminDashboard.geospatialTrackingDesc') },
  ];

  const pendingList = pending.state.status === 'ready' ? pending.state.data : [];
  const reportList = reports.state.status === 'ready' ? reports.state.data : [];

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.admin')}
        title={t('adminDashboard.overview')}
        description={t('adminDashboard.overviewDesc')}
        actions={
          <>
            <RefreshButton onClick={refreshAll} />
            <ButtonLink href="/dashboard/admin/verifications" variant="primary">
              <UserCheck className="h-4 w-4" aria-hidden="true" />
              {t('dashUi.admin.reviewVerifications')}
            </ButtonLink>
          </>
        }
      />

      <div className="space-y-12">
        {summary.state.status === 'error' && (
          <Notice
            tone="error"
            action={
              <Button type="button" variant="secondary" onClick={() => void summary.reload()}>
                {t('dash.retry')}
              </Button>
            }
          >
            {summary.state.message || t('dash.loadFailed')}
          </Notice>
        )}

        {/* KPIs: national figures are sums of the API's district totals; no data shows "No data", never a made-up number */}
        {summaryLoading ? (
          <StatsSkeleton count={4} className="sm:grid-cols-2 lg:grid-cols-4" />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Rise delay={0.04}>
              <Stat label={t('adminDashboard.totalRequests')} value={summary.state.status === 'ready' ? totals.totalRequests : null} icon={<Droplets />} hint={t('dashUi.admin.totalHint')} className="h-full" />
            </Rise>
            <Rise delay={0.08}>
              <Stat label={t('adminDashboard.activeRequests')} value={summary.state.status === 'ready' ? totals.activeRequests : null} tone="warning" icon={<Activity />} hint={t('adminDashboard.openEmergencies')} className="h-full" />
            </Rise>
            <Rise delay={0.12}>
              <Stat label={t('adminDashboard.availableDonors')} value={summary.state.status === 'ready' ? totals.donors : null} tone="success" icon={<Users />} hint={t('dashUi.admin.donorsHint')} className="h-full" />
            </Rise>
            <Rise delay={0.16}>
              <Stat
                label={t('adminDashboard.nationalFulfillment')}
                value={summary.state.status === 'ready' ? totals.fulfillmentRate : null}
                suffix="%"
                meter={totals.fulfillmentRate ?? undefined}
                tone="primary"
                icon={<TrendingUp />}
                hint={t('dashUi.admin.fulfilmentHint')}
                className="h-full"
              />
            </Rise>
          </div>
        )}

        {/* Hub links (all neutral: the one primary action is in the page header) */}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {hubLinks.map((l, i) => (
            <Rise key={l.href} delay={0.1 + i * 0.05}>
              <Link href={l.href} className={`${CARD} group flex h-full flex-col justify-between gap-6 p-5 transition-colors hover:border-line-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary`}>
                <div className="flex items-start justify-between">
                  <IconTile size="md" tone="primary">
                    <l.icon />
                  </IconTile>
                  <span aria-hidden="true" className="flex h-8 w-8 items-center justify-center rounded-full border border-line text-gray-500 transition-colors group-hover:border-primary group-hover:text-primary-text">
                    <ArrowUpRight className="h-4 w-4 rtl:-scale-x-100" />
                  </span>
                </div>
                <div>
                  <h2 className="font-display text-2xl text-foreground">{l.title}</h2>
                  <p className="mt-1.5 text-sm leading-relaxed text-gray-400">{l.desc}</p>
                </div>
              </Link>
            </Rise>
          ))}
        </div>

        {/* Requests by district */}
        <Rise inView>
          <section>
            <SectionHeader
              eyebrow={t('dashUi.section.distribution')}
              title={t('adminDashboard.distributionTitle')}
              action={
                <Link href="/dashboard/admin/analytics" className={TEXT_LINK}>
                  {t('dashUi.admin.openAnalytics')} <ArrowUpRight className="h-4 w-4 rtl:-scale-x-100" aria-hidden="true" />
                </Link>
              }
            />
            <Panel>
              {summaryLoading ? (
                <div className="space-y-4" role="status" aria-label={t('common.loading')}>
                  <Skeleton className="h-6 w-full" />
                  <Skeleton className="h-6 w-5/6" />
                  <Skeleton className="h-6 w-2/3" />
                </div>
              ) : summary.state.status !== 'ready' ? (
                <p className="py-6 text-center text-sm text-gray-500">{t('dashUi.noData')}</p>
              ) : withRequests.length === 0 ? (
                <div className="py-6 text-center">
                  <p className="font-display text-xl text-foreground">{t('dashUi.noData')}</p>
                  <p className="mx-auto mt-1 max-w-md text-sm text-gray-400">{t('dashUi.admin.noRequestsYet')}</p>
                </div>
              ) : (
                <div className="space-y-5">
                  <Tabs
                    label={t('dashUi.viewMode')}
                    value={view}
                    onChange={(id) => setViewChoice(id as 'chart' | 'table')}
                    tabs={[
                      { id: 'chart', label: t('dashUi.viewChart') },
                      { id: 'table', label: t('dashUi.viewTable') },
                    ]}
                  />
                  <div role="tabpanel" aria-labelledby={`tab-${view}`}>
                    {view === 'chart' ? (
                      <DistrictBars rows={withRequests} max={maxCount} />
                    ) : (
                      <Table>
                        <caption className="sr-only">{t('adminDashboard.distributionTitle')}</caption>
                        <thead>
                          <tr>
                            <Th>{t('dash.district')}</Th>
                            <Th className="text-end">{t('adminDashboard.totalRequests')}</Th>
                            <Th className="text-end">{t('adminDashboard.activeRequests')}</Th>
                            <Th className="text-end">{t('adminDashboard.fulfilled')}</Th>
                          </tr>
                        </thead>
                        <tbody>
                          {withRequests.map((r) => (
                            <tr key={r.district}>
                              <Td className="font-medium">{r.district}</Td>
                              <Td className="text-end tabular-nums">{format.number(r.totalRequests)}</Td>
                              <Td className="text-end tabular-nums">{format.number(r.activeRequests)}</Td>
                              <Td className="text-end tabular-nums">{format.number(r.fulfilledRequests)}</Td>
                            </tr>
                          ))}
                        </tbody>
                      </Table>
                    )}
                  </div>
                </div>
              )}
            </Panel>
          </section>
        </Rise>

        <div className="grid items-start gap-6 lg:grid-cols-2">
          {/* Pending verifications (replaces the old raw "type a User ID" box) */}
          <Rise inView>
            <Panel>
              <SectionHeader
                eyebrow={t('dashUi.section.verification')}
                title={t('dashUi.admin.pendingTitle')}
                description={t('dashUi.admin.pendingDesc')}
                action={
                  <Link href="/dashboard/admin/verifications" className={TEXT_LINK}>
                    {t('dashUi.admin.openQueue')} <ArrowUpRight className="h-4 w-4 rtl:-scale-x-100" aria-hidden="true" />
                  </Link>
                }
              />
              {pending.state.status === 'loading' ? (
                <RowsSkeleton rows={2} />
              ) : pending.state.status === 'error' || pending.state.status === 'unavailable' ? (
                <Notice tone="error">{pending.state.status === 'error' && pending.state.message ? pending.state.message : t('dash.loadFailed')}</Notice>
              ) : pendingList.length === 0 ? (
                <p className="flex items-center gap-2 py-4 text-sm text-gray-400">
                  <CheckCircle2 className="h-4 w-4 text-success-text" aria-hidden="true" />
                  {t('adminDashboard.noPendingAccounts')}
                </p>
              ) : (
                <>
                  <p className="mb-3 text-sm font-medium text-foreground">{t('dashUi.admin.pendingCount', { count: pendingList.length })}</p>
                  <ul className="divide-y divide-line rounded-xl border border-line">
                    {pendingList.slice(0, 4).map((u) => (
                      <li key={u.id} className="px-4 py-3">
                        <p className="break-words text-sm font-medium text-foreground">{u.fullName || u.organisation || u.email}</p>
                        <p className="text-sm text-gray-400">{t(`dashUi.role.${u.role}`)}</p>
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </Panel>
          </Rise>

          {/* Abuse and moderation reports */}
          <Rise inView>
            <Panel>
              <SectionHeader
                eyebrow={t('dashUi.section.moderation')}
                title={reports.state.status === 'ready' ? t('dashUi.admin.flaggedCount', { count: reportList.length }) : t('adminDashboard.flaggedReports')}
              />
              {reports.state.status === 'loading' ? (
                <RowsSkeleton rows={2} />
              ) : reports.state.status !== 'ready' ? (
                <Notice tone="error">{reports.state.status === 'error' && reports.state.message ? reports.state.message : t('dash.loadFailed')}</Notice>
              ) : reportList.length === 0 ? (
                <p className="flex items-center gap-2 py-4 text-sm text-gray-400">
                  <CheckCircle2 className="h-4 w-4 text-success-text" aria-hidden="true" />
                  {t('adminDashboard.noReports')}
                </p>
              ) : (
                <ul className="-mx-5 divide-y divide-line border-t border-line sm:-mx-6">
                  {reportList.map((report) => (
                    <li key={report.id} className="flex items-start gap-3 px-5 py-4 sm:px-6">
                      <Flag className="mt-0.5 h-4 w-4 shrink-0 text-warning-text" aria-hidden="true" />
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-foreground">
                          {t('common.request')} <Link href={`/dashboard/requests/${report.requestId}`} className="text-primary-text underline-offset-4 hover:underline">#{report.requestId}</Link>
                        </p>
                        <p className="mt-0.5 break-words text-sm leading-relaxed text-gray-400">{report.reason}</p>
                        {report.createdAt && <p className="mt-1 text-sm text-gray-500">{format.dateTime(report.createdAt)}</p>}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          </Rise>
        </div>
      </div>
    </div>
  );
}
