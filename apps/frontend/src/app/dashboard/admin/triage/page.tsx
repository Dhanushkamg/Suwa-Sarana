'use client';

import { useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import { Bot, AlertTriangle, CheckCircle2, Check, Eye, MapPin, Droplets } from 'lucide-react';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { BloodRequest } from '@/types';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { BloodTypeTile } from '@/components/dash/IconTile';
import { Meta } from '@/components/dash/Meta';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton } from '@/components/dash/Skeletons';
import { EYEBROW } from '@/components/dash/styles';
import { PulseDot, UrgencyBadge } from '@/components/dash/UrgencyBadge';
import { cn } from '@/lib/utils';

interface ApiBody<T> {
  data?: T;
}

interface TriagedRequest extends BloodRequest {
  fraudRiskScore?: number;
  aiFlagReason?: string;
  triagedAt?: string;
}

export default function AdminTriageQueuePage() {
  const { t } = useI18n();
  const [requests, setRequests] = useState<TriagedRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<'ALL' | 'HIGH' | 'MODERATE' | 'LOW'>('ALL');
  const [resolvingId, setResolvingId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const loadTriageQueue = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<ApiBody<TriagedRequest[]>>('/admin/triage/queue');
      const data = res.data?.data;
      setRequests(Array.isArray(data) ? data : []);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg || '');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadTriageQueue();
  }, [loadTriageQueue]);

  const handleResolve = async (id: number) => {
    setResolvingId(id);
    setActionError(null);
    try {
      await apiClient.put(`/admin/triage/${id}/review`, {
        notes: 'Dismissed by administrator from triage console.',
      });
      loadTriageQueue();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setActionError(msg || t('dash.dismissFailed'));
    } finally {
      setResolvingId(null);
    }
  };

  const highRiskCount = requests.filter((r) => (r.fraudRiskScore || 0) >= 70).length;
  const moderateRiskCount = requests.filter((r) => (r.fraudRiskScore || 0) >= 40 && (r.fraudRiskScore || 0) < 70).length;
  const lowRiskCount = requests.filter((r) => (r.fraudRiskScore || 0) < 40).length;

  const filtered = requests.filter((r) => {
    const score = r.fraudRiskScore || 0;
    if (filter === 'HIGH') return score >= 70;
    if (filter === 'MODERATE') return score >= 40 && score < 70;
    if (filter === 'LOW') return score < 40;
    return true;
  });

  const tiles: { value: typeof filter; label: string; count: number; dot: ReactNode; tone: string }[] = [
    { value: 'ALL', label: t('adminDashboard.totalTriaged'), count: requests.length, dot: null, tone: 'text-foreground' },
    { value: 'HIGH', label: t('adminDashboard.highRisk'), count: highRiskCount, dot: <PulseDot />, tone: 'text-ember' },
    { value: 'MODERATE', label: t('adminDashboard.moderateRisk'), count: moderateRiskCount, dot: <PulseDot tone="saffron" still />, tone: 'text-saffron' },
    { value: 'LOW', label: t('adminDashboard.lowRisk'), count: lowRiskCount, dot: <PulseDot tone="sage" still />, tone: 'text-emerald-400' },
  ];

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.triage')}
        title={t('adminDashboard.triageTitle')}
        description={t('adminDashboard.triageDesc')}
        backHref="/dashboard/admin"
        actions={<RefreshButton onClick={loadTriageQueue} loading={loading} label={t('adminDashboard.refreshQueue')} />}
      />

      <div className="space-y-6">
        {/* Advisory */}
        <Notice tone="info" icon={<Bot />}>
          <strong className="mb-0.5 block font-medium text-foreground">{t('adminDashboard.aiPrioritization')}</strong>
          <span className="text-gray-400">{t('adminDashboard.aiPrioritizationDesc')}</span>
        </Notice>

        {actionError && <Notice tone="error">{actionError}</Notice>}

        {/* Risk counters double as filters */}
        <div role="group" aria-label={t('adminDashboard.riskScore')} className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
          {tiles.map((tile, i) => {
            const active = filter === tile.value;
            return (
              <Rise key={tile.value} delay={0.04 + i * 0.04}>
                <button
                  type="button"
                  onClick={() => setFilter(tile.value)}
                  aria-pressed={active}
                  className={cn(
                    'glass-card block h-full w-full rounded-2xl p-4 text-left transition-colors duration-200 sm:p-5',
                    active && 'border-ember/50 bg-ember/[0.05]'
                  )}
                >
                  <span className={cn(EYEBROW, 'flex items-center gap-2')}>
                    {tile.dot}
                    {tile.label}
                  </span>
                  <span className={cn('mt-4 block font-display text-5xl tabular-nums', tile.tone)}>{tile.count}</span>
                </button>
              </Rise>
            );
          })}
        </div>

        {/* Triage list */}
        {loading ? (
          <div role="status" aria-label={t('adminDashboard.evaluatingQueue')}>
            <RowsSkeleton rows={3} />
          </div>
        ) : error !== null ? (
          <EmptyState
            icon={<AlertTriangle className="w-10 h-10 text-red-400" />}
            title={error || t('dash.loadFailed')}
            action={<Button variant="secondary" onClick={loadTriageQueue}>{t('dash.retry')}</Button>}
          />
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={<CheckCircle2 className="w-12 h-12 text-emerald-400 opacity-60" />}
            title={t('adminDashboard.queueClear')}
            description={t('adminDashboard.noThreshold')}
          />
        ) : (
          <Rise delay={0.1}>
            <ul className="glass-card divide-y divide-line overflow-hidden rounded-2xl">
              {filtered.map((req) => {
                const score = req.fraudRiskScore || 0;
                const isHigh = score >= 70;
                const isMod = score >= 40 && score < 70;
                const scoreTone = isHigh ? 'text-ember' : isMod ? 'text-saffron' : 'text-emerald-400';
                const scoreBar = isHigh ? 'bg-ember' : isMod ? 'bg-saffron' : 'bg-emerald-400';

                return (
                  <li key={req.id} className={cn('relative p-5 sm:p-6', isHigh && 'bg-ember/[0.04]')}>
                    {isHigh && <span aria-hidden="true" className="absolute inset-y-0 left-0 w-[2px] bg-ember" />}
                    <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-center">
                      <div className="flex min-w-0 items-center gap-4">
                        <BloodTypeTile type={req.patientBloodType} size="md" />
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                            <h2 className="break-words text-base font-medium text-foreground">{req.hospitalName}</h2>
                            <span className="font-mono text-xs text-gray-500">#{req.id}</span>
                          </div>
                          <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1.5">
                            <Meta icon={<MapPin />}>{req.district}</Meta>
                            <Meta icon={<Droplets />}>{t('dash.unitsCount', { n: req.unitsNeeded ?? 0 })}</Meta>
                            <UrgencyBadge urgency={req.urgency} />
                          </div>
                        </div>
                      </div>

                      <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
                        <div className="min-w-[7.5rem]">
                          <p className="flex items-baseline justify-between gap-3">
                            <span className={EYEBROW}>{t('adminDashboard.riskScore')}</span>
                            <span className={cn('font-mono text-sm tabular-nums', scoreTone)}>{score}/100</span>
                          </p>
                          <span aria-hidden="true" className="mt-2 block h-px bg-line">
                            <span className={cn('block h-px origin-left', scoreBar)} style={{ width: `${Math.min(score, 100)}%` }} />
                          </span>
                        </div>

                        <div className="flex flex-wrap items-center gap-2">
                          <ButtonLink href={`/dashboard/requests/${req.id}`} variant="secondary">
                            <Eye className="h-3.5 w-3.5" aria-hidden="true" />
                            {t('common.view')}
                          </ButtonLink>

                          {score > 0 && (
                            <Button
                              type="button"
                              size="sm"
                              variant="secondary"
                              onClick={() => handleResolve(req.id)}
                              disabled={resolvingId === req.id}
                              className="border-emerald-500/30 bg-emerald-500/10 text-emerald-300 hover:border-emerald-500/50 hover:bg-emerald-500/20"
                            >
                              <Check className="h-3.5 w-3.5" aria-hidden="true" />
                              {resolvingId === req.id ? t('common.loading') : t('adminDashboard.dismissFlag')}
                            </Button>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* AI explainable flag reason */}
                    {req.aiFlagReason && (
                      <div className="mt-5 flex items-start gap-3 border-l border-ember/50 pl-4 text-xs">
                        <Bot className="mt-0.5 h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
                        <div className="min-w-0">
                          <span className={EYEBROW}>{t('adminDashboard.aiDiagnosticHint')}</span>
                          <p className="mt-1.5 break-words text-sm leading-relaxed text-gray-300">{req.aiFlagReason}</p>
                        </div>
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          </Rise>
        )}
      </div>
    </div>
  );
}
