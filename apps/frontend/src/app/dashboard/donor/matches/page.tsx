'use client';

import { useState, useEffect, useCallback } from 'react';
import { Heart, MapPin, Activity, CheckCircle2, XCircle, Clock, AlertCircle, Droplets } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { apiMessage, asDate, asList } from '@/lib/api/common';
import { DONOR_PATHS } from '@/lib/api/donors';
import { lowDataInterval, useLowData } from '@/lib/lowData';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useToast } from '@/components/ui/Toast';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { BloodTypeTile } from '@/components/dash/IconTile';
import { matchStatusLabel, matchStatusTone } from '@/components/dash/matchStatus';
import { Meta } from '@/components/dash/Meta';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { RowsSkeleton } from '@/components/dash/Skeletons';
import { CARD } from '@/components/dash/styles';
import { UrgencyBadge } from '@/components/dash/UrgencyBadge';

interface MatchRequest {
  id: number;
  patientBloodType: string;
  urgency: string;
  hospitalName: string;
  district: string;
  unitsNeeded: number;
  status: string;
  expiresAt: unknown;
}

interface DonorMatch {
  id: number;
  status: string;
  createdAt: unknown;
  request?: MatchRequest | null;
}

const REFRESH_MS = 30_000;

export default function DonorMatchesPage() {
  const { t, format } = useI18n();
  const toast = useToast();
  const { lowData } = useLowData();
  const [matches, setMatches] = useState<DonorMatch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [respondingId, setRespondingId] = useState<number | null>(null);

  /** Returns false when the load failed (the RefreshButton shows that). */
  const loadMatches = useCallback(async (silent = false): Promise<boolean> => {
    if (!silent) {
      setLoading(true);
      setError(null);
    }
    try {
      const res = await apiClient.get(DONOR_PATHS.matches);
      // Only render matches that carry their request details.
      setMatches(asList<DonorMatch>(res.data).filter((m) => m.request));
      setError(null);
      return true;
    } catch (err: unknown) {
      // A background refresh failing should not wipe the list the donor is looking at.
      if (!silent) setError(apiMessage(err, ''));
      return false;
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadMatches();
    // Simple periodic refresh instead of a long-lived stream; 4x slower in Low-Data Mode.
    const timer = setInterval(() => {
      if (!document.hidden) loadMatches(true);
    }, lowDataInterval(REFRESH_MS, lowData));
    return () => clearInterval(timer);
  }, [loadMatches, lowData]);

  const respond = async (matchId: number, response: 'ACCEPTED' | 'DECLINED') => {
    if (respondingId !== null) return;
    setRespondingId(matchId);
    setActionError(null);
    try {
      await apiClient.post(`/matches/${matchId}/respond`, null, { params: { response } });
      setMatches((prev) => prev.map((m) => (m.id === matchId ? { ...m, status: response } : m)));
      toast.show(response === 'ACCEPTED' ? t('dashboard.youAccepted') : t('dashboard.youDeclined'), 'success');
    } catch (err: unknown) {
      setActionError(apiMessage(err, t('dash.respondFailed')));
    } finally {
      setRespondingId(null);
    }
  };

  const requestStatusLabel = (s: string) => t('request.status' + s.charAt(0) + s.slice(1).toLowerCase());
  const dateTime = (v: unknown) => {
    const d = asDate(v);
    return d ? format.dateTime(d, { dateStyle: 'medium', timeStyle: 'short' }) : t('dashUi.noData');
  };

  return (
    <div className="max-w-4xl">
      <PageHeader
        eyebrow={t('dashUi.eyebrow.matches')}
        title={t('common.myMatches')}
        description={t('dashboard.myMatchesDesc')}
        actions={<RefreshButton onClick={() => loadMatches()} loading={loading} />}
      />

      {lowData && (
        <Notice tone="info" className="mb-6">
          {t('dashUi.slowRefresh')}
        </Notice>
      )}

      {actionError && (
        <Notice tone="error" className="mb-6">
          {actionError}
        </Notice>
      )}

      {loading ? (
        <RowsSkeleton rows={3} />
      ) : error !== null ? (
        <EmptyState
          icon={<AlertCircle className="h-10 w-10 text-danger-text" aria-hidden="true" />}
          title={error || t('dash.loadFailed')}
          action={
            <Button variant="secondary" onClick={() => loadMatches()}>
              {t('dash.retry')}
            </Button>
          }
        />
      ) : matches.length === 0 ? (
        <EmptyState
          icon={<Heart className="h-12 w-12" aria-hidden="true" />}
          title={t('common.noActiveMatches')}
          description={t('dashboard.noActiveMatchesDesc')}
          action={
            <ButtonLink href="/dashboard/donor" variant="primary">
              {t('dashUi.donor.updateProfile')}
            </ButtonLink>
          }
        />
      ) : (
        <Rise delay={0.05}>
          <ul className={`${CARD} divide-y divide-line overflow-hidden`}>
            {matches.map((match) => {
              const req = match.request as MatchRequest;
              const requestActive = req.status === 'OPEN' || req.status === 'ESCALATING';
              const busy = respondingId === match.id;
              return (
                <li key={match.id} className="p-5 sm:p-6">
                  <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
                    <div className="flex min-w-0 items-center gap-4">
                      <BloodTypeTile type={req.patientBloodType} size="md" />
                      <div className="min-w-0">
                        <h2 className="break-words text-lg font-medium text-foreground">{req.hospitalName}</h2>
                        <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1">
                          <Meta icon={<MapPin />}>{req.district}</Meta>
                          <Meta icon={<Droplets />}>{t('dashUi.units', { count: req.unitsNeeded })}</Meta>
                        </div>
                      </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      <UrgencyBadge urgency={req.urgency} />
                      {!requestActive && <StatusBadge value={req.status} label={requestStatusLabel(req.status)} />}
                    </div>
                  </div>

                  <dl className="mt-4 flex flex-wrap gap-x-6 gap-y-2 text-sm text-gray-500">
                    <div className="flex items-center gap-1.5">
                      <Clock className="h-4 w-4" aria-hidden="true" />
                      <dt>{t('common.received')}:</dt>
                      <dd className="text-gray-300">{dateTime(match.createdAt)}</dd>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <Clock className="h-4 w-4" aria-hidden="true" />
                      <dt>{t('bloodRequests.expires')}:</dt>
                      <dd className="text-gray-300">{dateTime(req.expiresAt)}</dd>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <Activity className="h-4 w-4" aria-hidden="true" />
                      <dt>{t('bloodRequests.status')}:</dt>
                      <dd>
                        <StatusBadge value={match.status} label={matchStatusLabel(t, match.status)} tone={matchStatusTone(match.status)} />
                      </dd>
                    </div>
                  </dl>

                  {match.status === 'PENDING' && requestActive && (
                    <div className="mt-5 flex flex-col gap-3 sm:flex-row">
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => respond(match.id, 'ACCEPTED')}
                        disabled={respondingId !== null}
                        loading={busy}
                        className="flex-1 border-success/40 bg-success-soft text-success-text hover:bg-success-soft hover:brightness-95"
                      >
                        {!busy && <CheckCircle2 className="h-4 w-4" aria-hidden="true" />}
                        {t('dashboard.acceptAndDonate')}
                      </Button>
                      <Button type="button" variant="secondary" onClick={() => respond(match.id, 'DECLINED')} disabled={respondingId !== null} className="flex-1">
                        <XCircle className="h-4 w-4" aria-hidden="true" />
                        {t('dashboard.decline')}
                      </Button>
                    </div>
                  )}

                  {match.status === 'ACCEPTED' && (
                    <p className="mt-5 flex items-start gap-2 text-sm font-medium text-success-text">
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
