'use client';

import { useState, useEffect, useCallback } from 'react';
import { ArrowLeft, Heart, MapPin, Activity, CheckCircle2, XCircle, Clock, AlertCircle, RefreshCw, Droplets } from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { SkeletonList } from '@/components/ui/Skeleton';
import { StatusBadge } from '@/components/ui/StatusBadge';

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
    <div className="max-w-3xl mx-auto">
      <div className="mb-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <Link href="/dashboard" className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4">
            <ArrowLeft className="w-4 h-4" />
            {t('common.back')}
          </Link>
          <h1 className="text-3xl font-bold text-white flex items-center gap-3">
            <Heart className="w-8 h-8 text-red-500 fill-red-500" />
            {t('common.myMatches')}
          </h1>
          <p className="text-gray-400 mt-1">{t('dashboard.myMatchesDesc')}</p>
        </div>

        <button
          onClick={() => loadMatches()}
          disabled={loading}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-gray-300 hover:text-white hover:bg-white/10 text-xs font-medium self-start sm:self-auto transition-colors"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          {t('common.refresh')}
        </button>
      </div>

      {actionError && (
        <div className="mb-6 rounded-2xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400 animate-in fade-in duration-200">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{actionError}</span>
        </div>
      )}

      {loading ? (
        <SkeletonList rows={3} />
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
            <Link
              href="/dashboard/donor"
              className="inline-flex items-center gap-2 text-xs text-red-400 hover:text-red-300 font-medium px-4 py-2 rounded-xl bg-red-500/10 border border-red-500/20 transition-colors"
            >
              {t('dashboard.updateDonorProfile')}
            </Link>
          }
        />
      ) : (
        <div className="space-y-4">
          {matches.map((match) => {
            const req = match.request as MatchRequest;
            const requestActive = req.status === 'OPEN' || req.status === 'ESCALATING';
            return (
              <div
                key={match.id}
                className="glass-card rounded-2xl p-4 sm:p-6 border border-white/10 hover:border-red-500/20 transition-all duration-200"
              >
                <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
                  <div className="flex items-center gap-4 min-w-0">
                    <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-red-500/20 to-rose-600/20 border border-red-500/30 flex items-center justify-center text-xl font-black text-red-400 flex-shrink-0">
                      {req.patientBloodType}
                    </div>
                    <div className="min-w-0">
                      <h2 className="font-bold text-white text-lg break-words">{req.hospitalName}</h2>
                      <p className="text-sm text-gray-400 flex flex-wrap items-center gap-x-3 gap-y-1 mt-1">
                        <span className="inline-flex items-center gap-1"><MapPin className="w-3.5 h-3.5" /> {req.district}</span>
                        <span className="inline-flex items-center gap-1"><Droplets className="w-3.5 h-3.5 text-red-400" /> {t('dash.unitsCount', { n: req.unitsNeeded })}</span>
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <StatusBadge value={req.urgency} label={t(`dash.urgency.${req.urgency}`)} />
                    {!requestActive && <StatusBadge value={req.status} label={requestStatusLabel(req.status)} />}
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-500 mb-5">
                  <span className="inline-flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5" />
                    {t('common.received')}: {new Date(match.createdAt).toLocaleString()}
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5" />
                    {t('bloodRequests.expires')}: {new Date(req.expiresAt).toLocaleString()}
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <Activity className="w-3.5 h-3.5" />
                    {t('bloodRequests.status')}: <span className="font-medium text-white">{t(`dash.matchStatus.${match.status}`)}</span>
                  </span>
                </div>

                {match.status === 'PENDING' && requestActive && (
                  <div className="flex flex-col sm:flex-row gap-3">
                    <button
                      onClick={() => respond(match.id, 'ACCEPTED')}
                      disabled={respondingId === match.id}
                      className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20 transition-colors font-medium text-sm disabled:opacity-50"
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      {t('dashboard.acceptAndDonate')}
                    </button>
                    <button
                      onClick={() => respond(match.id, 'DECLINED')}
                      disabled={respondingId === match.id}
                      className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/20 transition-colors font-medium text-sm disabled:opacity-50"
                    >
                      <XCircle className="w-4 h-4" />
                      {t('dashboard.decline')}
                    </button>
                  </div>
                )}

                {match.status === 'ACCEPTED' && (
                  <div className="flex items-center gap-2 text-emerald-400 text-sm font-medium">
                    <CheckCircle2 className="w-5 h-5 flex-shrink-0" /> {t('dashboard.youAccepted')}
                  </div>
                )}
                {match.status === 'DECLINED' && (
                  <div className="flex items-center gap-2 text-gray-500 text-sm">
                    <XCircle className="w-5 h-5 flex-shrink-0" /> {t('dashboard.youDeclined')}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
