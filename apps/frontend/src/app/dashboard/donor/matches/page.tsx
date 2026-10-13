'use client';

import { useState, useEffect, useCallback } from 'react';
import { Heart, MapPin, Activity, CheckCircle2, XCircle, Clock, AlertCircle, Droplets } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { BloodTypeTile } from '@/components/dash/IconTile';
import { Meta } from '@/components/dash/Meta';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton } from '@/components/dash/Skeletons';
import { UrgencyBadge } from '@/components/dash/UrgencyBadge';

interface MatchRequest {
  id: number;
  patientBloodType: string;
  urgency: string;
  hospitalName: string;
  district: string;
  unitsNeeded: number;
  status: string;
  expiresAt: string;
}

interface DonorMatch {
  id: number;
  status: string;
  createdAt: string;
  request?: MatchRequest | null;
}

interface ApiBody<T> {
  data?: T;
}

const REFRESH_MS = 30_000;

function apiMessage(err: unknown): string | undefined {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
}

export default function DonorMatchesPage() {
  const { t } = useI18n();
  const [matches, setMatches] = useState<DonorMatch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [respondingId, setRespondingId] = useState<number | null>(null);

  const loadMatches = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true);
      setError(null);
    }
    try {
      const res = await apiClient.get<ApiBody<DonorMatch[]>>('/donors/me/matches');
      const data = res.data?.data;
      // Only render matches that carry their request details.
      setMatches(Array.isArray(data) ? data.filter((m) => m.request) : []);
      setError(null);
    } catch (err: unknown) {
      // A background refresh failing should not wipe the list the donor is looking at.
      if (!silent) setError(apiMessage(err) || '');
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadMatches();
    // Simple periodic refresh instead of a long-lived stream.
    const timer = setInterval(() => {
      if (!document.hidden) loadMatches(true);
    }, REFRESH_MS);
    return () => clearInterval(timer);
  }, [loadMatches]);

  const respond = async (matchId: number, response: 'ACCEPTED' | 'DECLINED') => {
    setRespondingId(matchId);
    setActionError(null);
    try {
      await apiClient.post(`/matches/${matchId}/respond`, null, { params: { response } });
      setMatches((prev) => prev.map((m) => (m.id === matchId ? { ...m, status: response } : m)));
    } catch (err: unknown) {
      setActionError(apiMessage(err) || t('dash.respondFailed'));
    } finally {
      setRespondingId(null);
    }
  };

  const requestStatusLabel = (s: string) => t('request.status' + s.charAt(0) + s.slice(1).toLowerCase());

  return (
    <div className="max-w-4xl">
      <PageHeader
        eyebrow={t('dashUi.eyebrow.matches')}
        title={t('common.myMatches')}
        description={t('dashboard.myMatchesDesc')}
        actions={<RefreshButton onClick={() => loadMatches()} loading={loading} />}
      />

      {actionError && (
        <Notice tone="error" className="mb-6">
          {actionError}
        </Notice>
      )}

      {loading ? (
        <RowsSkeleton rows={3} />
      ) : error !== null ? (
        <EmptyState
          icon={<AlertCircle className="w-10 h-10 text-red-400" />}
          title={error || t('dash.loadFailed')}
          action={<Button variant="secondary" onClick={() => loadMatches()}>{t('dash.retry')}</Button>}
        />
      ) : matches.length === 0 ? (
        <EmptyState
          icon={<Heart className="w-12 h-12" />}
          title={t('common.noActiveMatches')}
          description={t('dashboard.noActiveMatchesDesc')}
          action={
            <ButtonLink href="/dashboard/donor" variant="secondary">
              {t('dashboard.updateDonorProfile')}
            </ButtonLink>
          }
        />
      ) : (
        <Rise delay={0.05}>
          <ul className="glass-card divide-y divide-line overflow-hidden rounded-2xl">
            {matches.map((match) => {
              const req = match.request as MatchRequest;
              const requestActive = req.status === 'OPEN' || req.status === 'ESCALATING';
              return (
                <li key={match.id} className="p-5 sm:p-6">
                  <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
                    <div className="flex min-w-0 items-center gap-4">
                      <BloodTypeTile type={req.patientBloodType} size="md" />
                      <div className="min-w-0">
                        <h2 className="break-words text-base font-medium text-foreground">{req.hospitalName}</h2>
                        <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1">
                          <Meta icon={<MapPin />}>{req.district}</Meta>
                          <Meta icon={<Droplets />}>{t('dash.unitsCount', { n: req.unitsNeeded })}</Meta>
                        </div>
                      </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      <UrgencyBadge urgency={req.urgency} />
                      {!requestActive && <StatusBadge value={req.status} label={requestStatusLabel(req.status)} />}
                    </div>
                  </div>

                  <dl className="mt-4 flex flex-wrap gap-x-6 gap-y-2 text-xs text-gray-500">
                    <div className="flex items-center gap-1.5">
                      <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                      <dt>{t('common.received')}:</dt>
                      <dd className="font-mono text-gray-300">{new Date(match.createdAt).toLocaleString()}</dd>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                      <dt>{t('bloodRequests.expires')}:</dt>
                      <dd className="font-mono text-gray-300">{new Date(req.expiresAt).toLocaleString()}</dd>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <Activity className="h-3.5 w-3.5" aria-hidden="true" />
                      <dt>{t('bloodRequests.status')}:</dt>
                      <dd className="font-medium text-foreground">{t(`dash.matchStatus.${match.status}`)}</dd>
                    </div>
                  </dl>

                  {match.status === 'PENDING' && requestActive && (
                    <div className="mt-5 flex flex-col gap-3 sm:flex-row">
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => respond(match.id, 'ACCEPTED')}
                        disabled={respondingId === match.id}
                        className="flex-1 border-emerald-500/30 bg-emerald-500/10 text-emerald-300 hover:border-emerald-500/50 hover:bg-emerald-500/20"
                      >
                        <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                        {t('dashboard.acceptAndDonate')}
                      </Button>
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => respond(match.id, 'DECLINED')}
                        disabled={respondingId === match.id}
                        className="flex-1 text-gray-300"
                      >
                        <XCircle className="h-4 w-4" aria-hidden="true" />
                        {t('dashboard.decline')}
                      </Button>
                    </div>
                  )}

                  {match.status === 'ACCEPTED' && (
                    <p className="mt-5 flex items-start gap-2 text-sm font-medium text-emerald-400">
                      <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> {t('dashboard.youAccepted')}
                    </p>
                  )}
                  {match.status === 'DECLINED' && (
                    <p className="mt-5 flex items-start gap-2 text-sm text-gray-500">
                      <XCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> {t('dashboard.youDeclined')}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
        </Rise>
      )}
    </div>
  );
}
