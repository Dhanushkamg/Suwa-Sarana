'use client';

import { useState, useEffect, useCallback } from 'react';
import { ShieldCheck, ShieldAlert, BarChart3, CheckCircle2, Flag, UserCheck, MapPin, ArrowUpRight } from 'lucide-react';
import Link from 'next/link';
import { motion, useReducedMotion } from 'motion/react';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { Skeleton } from '@/components/ui/Skeleton';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Reveal } from '@/components/motion/Reveal';
import { IconTile } from '@/components/dash/IconTile';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader } from '@/components/dash/Panel';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { StatsSkeleton } from '@/components/dash/Skeletons';
import { Stat } from '@/components/dash/Stat';
import { EASE } from '@/components/dash/styles';

interface ApiBody<T> {
  data?: T;
}

type AnalyticsData = Record<string, number>;
type Report = {
  id: number;
  requestId: number;
  reason: string;
  createdAt: string;
};

export default function AdminDashboardPage() {
  const { t } = useI18n();
  const reduce = useReducedMotion();
  const [analytics, setAnalytics] = useState<AnalyticsData>({});
  const [reports, setReports] = useState<Report[]>([]);
  const [verifyUserId, setVerifyUserId] = useState('');
  const [verifyStatus, setVerifyStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadAdminData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [analyticsRes, reportsRes] = await Promise.all([
        apiClient.get<ApiBody<AnalyticsData>>('/admin/analytics'),
        apiClient.get<ApiBody<Report[]>>('/admin/reports'),
      ]);
      const analyticsData = analyticsRes.data?.data;
      const reportsData = reportsRes.data?.data;
      setAnalytics(analyticsData || {});
      setReports(Array.isArray(reportsData) ? reportsData : []);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg || '');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAdminData();
  }, [loadAdminData]);

  const maxCount = Math.max(...Object.values(analytics).map((v) => Number(v) || 0), 1);

  const handleVerify = async () => {
    if (!verifyUserId.trim()) return;
    setVerifyStatus('loading');
    try {
      await apiClient.put(`/admin/verifications/${verifyUserId.trim()}/approve`);
      setVerifyStatus('success');
      setTimeout(() => setVerifyStatus('idle'), 3000);
    } catch {
      setVerifyStatus('error');
      setTimeout(() => setVerifyStatus('idle'), 3000);
    }
  };

  const totalRequests = Object.values(analytics).reduce((sum, v) => sum + (Number(v) || 0), 0);

  const hubLinks = [
    { href: '/dashboard/admin/triage', icon: ShieldAlert, title: t('adminDashboard.triage'), desc: t('adminDashboard.triageDesc') },
    { href: '/dashboard/admin/verifications', icon: UserCheck, title: t('adminDashboard.verifications'), desc: t('adminDashboard.verificationsDesc') },
    { href: '/dashboard/admin/analytics', icon: BarChart3, title: t('adminDashboard.analytics'), desc: t('adminDashboard.heatmapDesc') },
    { href: '/dashboard/admin/camps', icon: MapPin, title: t('layout.campsMapView'), desc: t('adminDashboard.geospatialTrackingDesc') },
  ];

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.admin')}
        title={t('adminDashboard.overview')}
        description={t('adminDashboard.overviewDesc')}
        actions={<RefreshButton onClick={loadAdminData} loading={loading} />}
      />

      <div className="space-y-12">
        {error !== null && (
          <Notice
            tone="error"
            action={
              <Button type="button" variant="secondary" size="sm" onClick={loadAdminData}>
                {t('dash.retry')}
              </Button>
            }
          >
            {error || t('dash.loadFailed')}
          </Notice>
        )}

        {/* KPIs */}
        {loading ? (
          <StatsSkeleton count={3} className="sm:grid-cols-3" />
        ) : (
          <div className="grid gap-4 sm:grid-cols-3">
            <Rise delay={0.04}>
              <Stat label={t('adminDashboard.distributionTitle')} value={totalRequests} icon={<BarChart3 />} hint={t('common.requests')} className="h-full" />
            </Rise>
            <Rise delay={0.08}>
              <Stat label={t('dash.district')} value={Object.keys(analytics).length} tone="saffron" icon={<MapPin />} className="h-full" />
            </Rise>
            <Rise delay={0.12}>
              <Stat label={t('adminDashboard.flaggedReports')} value={reports.length} tone={reports.length > 0 ? 'ember' : 'sage'} icon={<Flag />} className="h-full" />
            </Rise>
          </div>
        )}

        {/* Hub links */}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {hubLinks.map((l, i) => (
            <Rise key={l.href} delay={0.1 + i * 0.05}>
              <Link href={l.href} className="glass-card group flex h-full flex-col justify-between gap-8 rounded-2xl p-5">
                <div className="flex items-start justify-between">
                  <IconTile size="md">
                    <l.icon />
                  </IconTile>
                  <span
                    aria-hidden="true"
                    className="flex h-8 w-8 items-center justify-center rounded-full border border-line text-gray-500 transition-[transform,color,border-color] duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:border-ember/50 group-hover:text-ember"
                  >
                    <ArrowUpRight className="h-4 w-4" />
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
        <Reveal y={12}>
          <section>
            <SectionHeader
              eyebrow={t('dashUi.section.distribution')}
              title={t('adminDashboard.distributionTitle')}
              action={
                <Link href="/dashboard/admin/analytics" className="inline-flex items-center gap-1.5 text-sm text-ember transition-colors hover:text-foreground">
                  {t('adminDashboard.viewMap')} <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
                </Link>
              }
            />
            <Panel>
              {loading ? (
                <div className="space-y-4" role="status" aria-label={t('common.loading')}>
                  <Skeleton className="h-6 w-full" />
                  <Skeleton className="h-6 w-5/6" />
                  <Skeleton className="h-6 w-2/3" />
                </div>
              ) : Object.keys(analytics).length === 0 ? (
                <p className="py-6 text-center text-sm text-gray-500">{t('adminDashboard.noData')}</p>
              ) : (
                <ul className="space-y-3.5">
                  {Object.entries(analytics)
                    .sort(([, a], [, b]) => Number(b) - Number(a))
                    .map(([district, count], i) => {
                      const numCount = Number(count) || 0;
                      const pct = Math.round((numCount / maxCount) * 100);
                      const color = pct > 70 ? 'bg-ember' : pct > 40 ? 'bg-saffron' : 'bg-emerald-400';
                      return (
                        <li key={district} className="grid grid-cols-[6.5rem_minmax(0,1fr)_auto] items-center gap-x-4 sm:grid-cols-[9rem_minmax(0,1fr)_auto]">
                          <span className="break-words text-right text-sm leading-snug text-gray-300">{district}</span>
                          <span aria-hidden="true" className="block h-1.5 overflow-hidden rounded-full bg-foreground/[0.06]">
                            <motion.span
                              className={`block h-full w-full origin-left rounded-full ${color}`}
                              initial={reduce ? false : { scaleX: 0 }}
                              animate={{ scaleX: Math.max(pct, 3) / 100 }}
                              transition={{ duration: 0.8, ease: EASE, delay: 0.1 + i * 0.03 }}
                            />
                          </span>
                          <span className="min-w-12 text-right font-mono text-xs tabular-nums text-foreground">
                            {numCount}
                            <span className="ml-1 hidden text-gray-500 sm:inline">{t('common.requests')}</span>
                          </span>
                        </li>
                      );
                    })}
                </ul>
              )}
            </Panel>
          </section>
        </Reveal>

        <div className="grid items-start gap-6 lg:grid-cols-2">
          {/* Quick verification */}
          <Reveal y={12}>
            <Panel>
              <SectionHeader
                eyebrow={t('dashUi.section.verification')}
                title={t('adminDashboard.quickVerification')}
                description={t('adminDashboard.quickVerifyDesc')}
                action={
                  <Link href="/dashboard/admin/verifications" className="inline-flex items-center gap-1.5 text-sm text-ember transition-colors hover:text-foreground">
                    {t('adminDashboard.openQueue')} <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
                  </Link>
                }
              />
              <div className="flex flex-col gap-3 sm:flex-row">
                <div className="min-w-0 flex-1">
                  <Input
                    type="number"
                    placeholder={t('adminDashboard.enterUserId')}
                    aria-label={t('adminDashboard.enterUserId')}
                    value={verifyUserId}
                    onChange={(e) => setVerifyUserId(e.target.value)}
                  />
                </div>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={handleVerify}
                  disabled={verifyStatus === 'loading'}
                  className="border-emerald-500/30 bg-emerald-500/10 text-emerald-300 hover:border-emerald-500/50 hover:bg-emerald-500/20"
                >
                  {verifyStatus === 'loading' ? (
                    <span>{t('adminDashboard.verifying')}</span>
                  ) : verifyStatus === 'success' ? (
                    <><CheckCircle2 className="h-4 w-4" aria-hidden="true" /> {t('adminDashboard.verified')}</>
                  ) : (
                    <><ShieldCheck className="h-4 w-4" aria-hidden="true" /> {t('adminDashboard.verifyUser')}</>
                  )}
                </Button>
              </div>
              {verifyStatus === 'error' && (
                <Notice tone="error" className="mt-3">
                  {t('adminDashboard.verifyFailed')}
                </Notice>
              )}
            </Panel>
          </Reveal>

          {/* Abuse and moderation reports */}
          <Reveal y={12}>
            <Panel>
              <SectionHeader
                eyebrow={t('dashUi.section.moderation')}
                title={`${t('adminDashboard.flaggedReports')} (${reports.length})`}
              />
              {reports.length === 0 ? (
                <p className="py-6 text-center text-sm text-gray-500">{t('adminDashboard.noReports')}</p>
              ) : (
                <ul className="-mx-5 divide-y divide-line border-t border-line sm:-mx-6">
                  {reports.map((report) => (
                    <li key={report.id} className="flex items-start gap-3 px-5 py-4 sm:px-6">
                      <Flag className="mt-0.5 h-4 w-4 shrink-0 text-ember" aria-hidden="true" />
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-foreground">
                          {t('common.request')} <span className="font-mono">#{report.requestId}</span>
                        </p>
                        <p className="mt-0.5 break-words text-sm leading-relaxed text-gray-400">{report.reason}</p>
                        <p className="mt-1 font-mono text-xs text-gray-500">{new Date(report.createdAt).toLocaleString()}</p>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          </Reveal>
        </div>
      </div>
    </div>
  );
}
