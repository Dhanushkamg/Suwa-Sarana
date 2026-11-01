'use client';

import { useState, useMemo } from 'react';
import type { ReactNode } from 'react';
import { Bot, AlertTriangle, CheckCircle2, Check, Eye, MapPin, Droplets, CircleAlert, TriangleAlert, ShieldCheck, ListChecks } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { fetchTriageQueue, reviewTriage } from '@/lib/api/admin';
import { apiMessage } from '@/lib/api/common';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useToast } from '@/components/ui/Toast';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { BloodTypeTile } from '@/components/dash/IconTile';
import { Meta } from '@/components/dash/Meta';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton } from '@/components/dash/Skeletons';
import { CARD, EYEBROW } from '@/components/dash/styles';
import { UrgencyBadge } from '@/components/dash/UrgencyBadge';
import { useAsync } from '@/components/dash/useAsync';
import { cn } from '@/lib/utils';

type RiskFilter = 'ALL' | 'HIGH' | 'MODERATE' | 'LOW';

export default function AdminTriageQueuePage() {
  const { t, format } = useI18n();
  const toast = useToast();
  const queue = useAsync(fetchTriageQueue);
  const [filter, setFilter] = useState<RiskFilter>('ALL');
  const [resolvingId, setResolvingId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const requests = useMemo(() => (queue.state.status === 'ready' ? queue.state.data : []), [queue.state]);

  const handleResolve = async (id: number) => {
    if (resolvingId !== null) return;
    setResolvingId(id);
    setActionError(null);
    try {
      await reviewTriage(id, t('dashUi.admin.dismissNote'), 'DISMISSED');
      // The server keeps the AI score and its reasons and stores who reviewed it beside them: show the row as reviewed
      queue.mutate((rows) => rows.map((r) => (r.id === id ? { ...r, reviewed: true, reviewDecision: 'DISMISSED' } : r)));
      toast.show(t('dashUi.admin.flagDismissed'), 'success');
    } catch (err: unknown) {
      setActionError(apiMessage(err, t('dash.dismissFailed')));
    } finally {
      setResolvingId(null);
    }
  };

  const scoreOf = (r: { fraudRiskScore?: number }) => r.fraudRiskScore || 0;
  const highRiskCount = requests.filter((r) => scoreOf(r) >= 70).length;
  const moderateRiskCount = requests.filter((r) => scoreOf(r) >= 40 && scoreOf(r) < 70).length;
  const lowRiskCount = requests.filter((r) => scoreOf(r) < 40).length;

  const filtered = requests.filter((r) => {
    const score = scoreOf(r);
    if (filter === 'HIGH') return score >= 70;
    if (filter === 'MODERATE') return score >= 40 && score < 70;
    if (filter === 'LOW') return score < 40;
    return true;
  });

  const tiles: { value: RiskFilter; label: string; count: number; icon: ReactNode; tone: string }[] = [
    { value: 'ALL', label: t('adminDashboard.totalTriaged'), count: requests.length, icon: <ListChecks className="h-4 w-4" />, tone: 'text-foreground' },
    { value: 'HIGH', label: t('adminDashboard.highRisk'), count: highRiskCount, icon: <CircleAlert className="h-4 w-4" />, tone: 'text-danger-text' },
    { value: 'MODERATE', label: t('adminDashboard.moderateRisk'), count: moderateRiskCount, icon: <TriangleAlert className="h-4 w-4" />, tone: 'text-warning-text' },
    { value: 'LOW', label: t('adminDashboard.lowRisk'), count: lowRiskCount, icon: <ShieldCheck className="h-4 w-4" />, tone: 'text-success-text' },
  ];

  return (
    <div>
      <PageHeader
        eyebrow={t('dashUi.eyebrow.triage')}
        title={t('adminDashboard.triageTitle')}
        description={t('adminDashboard.triageDesc')}
        backHref="/dashboard/admin"
        actions={<RefreshButton onClick={queue.reload} loading={queue.state.status === 'loading'} label={t('adminDashboard.refreshQueue')} />}
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
                    CARD,
                    'block h-full w-full p-4 text-start transition-colors duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:p-5',
                    active && 'border-primary bg-primary-soft'
                  )}
                >
                  <span className={cn(EYEBROW, 'flex items-center gap-2')}>
                    <span aria-hidden="true" className={tile.tone}>
                      {tile.icon}
                    </span>
                    {tile.label}
                  </span>
                  <span className={cn('mt-4 block font-display text-5xl tabular-nums', tile.tone)}>{format.number(tile.count)}</span>
                </button>
              </Rise>
            );
          })}
        </div>

        {/* Triage list */}
        {queue.state.status === 'loading' ? (
          <div role="status" aria-label={t('adminDashboard.evaluatingQueue')}>
            <RowsSkeleton rows={3} />
          </div>
        ) : queue.state.status !== 'ready' ? (
          <EmptyState
            icon={<AlertTriangle className="h-10 w-10 text-danger-text" aria-hidden="true" />}
            title={(queue.state.status === 'error' && queue.state.message) || t('dash.loadFailed')}
            action={
              <Button variant="secondary" onClick={() => void queue.reload()}>
                {t('dash.retry')}
              </Button>
            }
          />
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={<CheckCircle2 className="h-12 w-12 text-success-text" aria-hidden="true" />}
            title={t('adminDashboard.queueClear')}
            description={t('adminDashboard.noThreshold')}
          />
        ) : (
          <Rise delay={0.1}>
            <ul className={`${CARD} divide-y divide-line overflow-hidden`}>
              {filtered.map((req) => {
                const score = scoreOf(req);
                const isHigh = score >= 70;
                const isMod = score >= 40 && score < 70;
                const scoreTone = isHigh ? 'text-danger-text' : isMod ? 'text-warning-text' : 'text-success-text';
                const scoreBar = isHigh ? 'bg-danger' : isMod ? 'bg-warning' : 'bg-success';
                const level = isHigh ? t('adminDashboard.highRisk') : isMod ? t('adminDashboard.moderateRisk') : t('adminDashboard.lowRisk');

                return (
                  <li key={req.id} className={cn('relative p-5 sm:p-6', isHigh && 'bg-danger-soft')}>
                    <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-center">
                      <div className="flex min-w-0 items-center gap-4">
                        <BloodTypeTile type={req.patientBloodType} size="md" />
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                            <h2 className="break-words text-lg font-medium text-foreground">{req.hospitalName}</h2>
                            <span className="text-sm text-gray-500">#{req.id}</span>
                          </div>
                          <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1.5">
                            <Meta icon={<MapPin />}>{req.district}</Meta>
                            <Meta icon={<Droplets />}>{t('dashUi.units', { count: req.unitsNeeded ?? 0 })}</Meta>
                            <UrgencyBadge urgency={req.urgency} />
                          </div>
                        </div>
                      </div>

                      <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
                        <div className="min-w-[8rem]">
                          <p className="flex items-baseline justify-between gap-3">
                            <span className={EYEBROW}>{t('adminDashboard.riskScore')}</span>
                            <span className={cn('text-sm font-medium tabular-nums', scoreTone)}>{t('dashUi.admin.scoreOf100', { score: format.number(score) })}</span>
                          </p>
                          <span aria-hidden="true" className="mt-2 block h-1.5 overflow-hidden rounded-full bg-foreground/[0.08]">
                            <span className={cn('block h-full rounded-full', scoreBar)} style={{ width: `${Math.min(score, 100)}%` }} />
                          </span>
                          <span className="sr-only">{level}</span>
                        </div>

                        <div className="flex flex-wrap items-center gap-2">
                          <ButtonLink href={`/dashboard/requests/${req.id}`} variant="secondary">
                            <Eye className="h-4 w-4" aria-hidden="true" />
                            {t('common.view')}
                          </ButtonLink>

                          {req.reviewed && (
                            <StatusBadge value={req.reviewDecision ?? 'REVIEWED'} tone="neutral" label={t(`dashUi.triage.decision.${req.reviewDecision ?? 'REVIEWED'}`)} />
                          )}
                          {score > 0 && !req.reviewed && (
                            <Button type="button" variant="secondary" onClick={() => handleResolve(req.id)} loading={resolvingId === req.id} disabled={resolvingId !== null}>
                              {resolvingId !== req.id && <Check className="h-4 w-4" aria-hidden="true" />}
                              {t('adminDashboard.dismissFlag')}
                            </Button>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Why the rule engine gave this score: one line per reason code, heaviest first */}
                    {req.reasons.length > 0 && (
                      <div className="mt-5 flex items-start gap-3 border-s-2 border-line-strong ps-4">
                        <Bot className="mt-0.5 h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
                        <div className="min-w-0">
                          <span className={EYEBROW}>{t('dashUi.triage.reasonsTitle')}</span>
                          <ul className="mt-1.5 space-y-1">
                            {req.reasons.map((r) => (
                              <li key={r.code} className="break-words text-sm leading-relaxed text-foreground">
                                {t(`dashUi.triage.reason.${r.code}`)} <span className="tabular-nums text-gray-500">(+{format.number(r.weight)})</span>
                              </li>
                            ))}
                          </ul>
                          <p className="mt-1.5 text-xs text-gray-500">{t('dashUi.triage.ruleBased')}</p>
                        </div>
                      </div>
                    )}

                    {/* AI explainable flag reason (the plain-text summary the server stored) */}
                    {req.reasons.length === 0 && req.aiFlagReason && (
                      <div className="mt-5 flex items-start gap-3 border-s-2 border-line-strong ps-4">
                        <Bot className="mt-0.5 h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
                        <div className="min-w-0">
                          <span className={EYEBROW}>{t('adminDashboard.aiDiagnosticHint')}</span>
                          <p className="mt-1.5 break-words text-sm leading-relaxed text-foreground">{req.aiFlagReason}</p>
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
