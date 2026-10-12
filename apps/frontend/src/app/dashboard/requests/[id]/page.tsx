'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'next/navigation';
import {
  ArrowLeft, Clock, MapPin, Activity, Droplets, CheckCircle2,
  Share2, Copy, Check, Users, Phone, MessageSquare, AlertCircle, RefreshCw
} from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { API_BASE_URL } from '@/lib/constants';
import { BloodRequest } from '@/types';
import RequestDistanceMap from '@/components/maps/RequestDistanceMap';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { useAuthStore } from '@/store/authStore';

interface CircleInviteData {
  requestId: number;
  inviteToken: string;
  inviteUrl: string;
  expiresAt: string;
}

interface CircleVolunteer {
  id: number;
  responderName: string;
  responderPhone: string;
  bloodType: string;
  notes?: string;
  respondedAt: string;
}

interface ApiBody<T> {
  data?: T;
}

interface StreamPayload {
  referenceId?: number;
  requestId?: number;
  body?: string;
  message?: string;
}

/** Errors that must stop the stream for good (e.g. 401), as opposed to transient ones we retry. */
class FatalStreamError extends Error {}

const RETRY_MS = 3000;

function apiStatus(err: unknown): number | undefined {
  return (err as { response?: { status?: number } })?.response?.status;
}

function apiMessage(err: unknown): string | undefined {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
}

export default function RequestLiveStatusPage() {
  const { t } = useI18n();
  const params = useParams();
  const requestId = params.id as string;
  // Only the presence of a token matters; a refreshed token must not restart the stream.
  const hasToken = useAuthStore((s) => !!s.accessToken);

  const [requestData, setRequestData] = useState<BloodRequest | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [updates, setUpdates] = useState<string[]>([]);

  // Circle state
  const [circleInvite, setCircleInvite] = useState<CircleInviteData | null>(null);
  const [circleVolunteers, setCircleVolunteers] = useState<CircleVolunteer[]>([]);
  const [circleLoading, setCircleLoading] = useState(false);
  const [circleError, setCircleError] = useState<string | null>(null);
  const [circleForbidden, setCircleForbidden] = useState(false);
  const [copied, setCopied] = useState(false);

  const loadRequest = useCallback(async () => {
    try {
      const res = await apiClient.get<ApiBody<BloodRequest>>(`/requests/${requestId}`);
      setRequestData(res.data?.data ?? null);
      setLoadFailed(false);
    } catch {
      setLoadFailed(true);
    } finally {
      setLoading(false);
    }
  }, [requestId]);

  /** Returns true when the volunteer list was loaded (which only the request owner can do). */
  const loadCircleResponses = useCallback(async (): Promise<boolean> => {
    try {
      const res = await apiClient.get<ApiBody<CircleVolunteer[]>>(`/requests/${requestId}/circle/responses`);
      const data = res.data?.data;
      setCircleVolunteers(Array.isArray(data) ? data : []);
      setCircleForbidden(false);
      return true;
    } catch (err: unknown) {
      // 403: not the owner, hide the panel. 404: the circle has not been created yet, which is normal.
      if (apiStatus(err) === 403) setCircleForbidden(true);
      return false;
    }
  }, [requestId]);

  /** Idempotent: returns the existing invite or creates it (owner only). */
  const fetchInvite = useCallback(async () => {
    const res = await apiClient.post<ApiBody<CircleInviteData>>(`/requests/${requestId}/circle`);
    setCircleInvite(res.data?.data ?? null);
  }, [requestId]);

  useEffect(() => {
    loadRequest();
    // After a reload the invite is gone from memory: fetch it again whenever the owner already has a circle.
    loadCircleResponses().then((isOwnerWithCircle) => {
      if (isOwnerWithCircle) fetchInvite().catch(() => undefined);
    });
  }, [loadRequest, loadCircleResponses, fetchInvite]);

  useEffect(() => {
    if (!hasToken) return;

    const abortController = new AbortController();

    fetchEventSource(`${API_BASE_URL}/notifications/stream`, {
      method: 'GET',
      headers: { Accept: 'text/event-stream' },
      signal: abortController.signal,
      // Always send the latest token, including on automatic reconnects.
      fetch: (input, init) => {
        const token = useAuthStore.getState().accessToken;
        return fetch(input, {
          ...init,
          headers: { ...(init?.headers as Record<string, string>), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        });
      },
      onopen: async (res) => {
        const isStream = res.headers.get('content-type')?.startsWith('text/event-stream');
        if (res.ok && isStream) return;
        // 4xx (e.g. 401 expired token) will not fix itself: stop. Server hiccups are retried.
        if (res.status >= 400 && res.status < 500) throw new FatalStreamError(`HTTP ${res.status}`);
        throw new Error(`Unexpected stream response (${res.status})`);
      },
      onmessage: (event) => {
        if (!event.data) return;
        try {
          const data = JSON.parse(event.data) as StreamPayload;
          if (Number(data.referenceId ?? data.requestId) === Number(requestId)) {
            setUpdates((prev) => [data.body || data.message || '', ...prev].slice(0, 5));
            loadRequest();
            loadCircleResponses();
          }
        } catch {
          // Not a JSON payload for us
        }
      },
      onerror: (err) => {
        if (err instanceof FatalStreamError) throw err; // stop retrying
        return RETRY_MS; // transient: try again shortly
      },
      onclose: () => {
        // The server closes long-lived streams periodically; throwing makes the library reconnect.
        throw new Error('Stream closed');
      },
    }).catch(() => {
      // Fatal error or aborted: nothing more to do.
    });

    return () => {
      abortController.abort();
    };
  }, [requestId, hasToken, loadRequest, loadCircleResponses]);

  const handleGenerateCircle = async () => {
    setCircleLoading(true);
    setCircleError(null);
    try {
      await fetchInvite();
      loadCircleResponses();
    } catch (err: unknown) {
      if (apiStatus(err) === 403) setCircleForbidden(true);
      setCircleError(apiMessage(err) || t('dash.circleFailed'));
    } finally {
      setCircleLoading(false);
    }
  };

  const copyToClipboard = async (url: string) => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 3000);
    } catch {
      // Clipboard unavailable (e.g. insecure context): the link is still selectable on screen.
    }
  };

  if (loading) {
    return (
      <div className="max-w-4xl mx-auto space-y-6" role="status" aria-label={t('common.loading')}>
        <Skeleton className="h-8 w-24" />
        <Skeleton className="h-56 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (loadFailed || !requestData) {
    return (
      <div className="max-w-4xl mx-auto space-y-6">
        <Link href="/dashboard/requests" className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors">
          <ArrowLeft className="w-4 h-4" />
          {t('common.back')}
        </Link>
        <EmptyState
          icon={<AlertCircle className="w-10 h-10 text-red-400" />}
          title={loadFailed ? t('common.error') : t('bloodRequests.notFound')}
          action={
            loadFailed ? (
              <Button
                variant="secondary"
                onClick={() => {
                  setLoading(true);
                  loadRequest();
                }}
              >
                {t('dash.retry')}
              </Button>
            ) : undefined
          }
        />
      </div>
    );
  }

  const shareText = t('dash.shareText', {
    bloodType: requestData.patientBloodType,
    hospital: requestData.hospitalName,
    district: requestData.district,
  });
  const fullInviteUrl = circleInvite
    ? circleInvite.inviteUrl || `${typeof window !== 'undefined' ? window.location.origin : ''}/circle/${circleInvite.inviteToken}`
    : '';
  const statusLabel = t('request.status' + requestData.status.charAt(0) + requestData.status.slice(1).toLowerCase());

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <Link href="/dashboard/requests" className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors">
        <ArrowLeft className="w-4 h-4" />
        {t('common.back')}
      </Link>

      {/* Main Request Summary Card */}
      <div className="glass-card p-5 md:p-8 rounded-2xl border border-white/10">
        <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4 mb-6">
          <div className="flex items-center gap-4 min-w-0">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-red-500/20 to-rose-600/20 border border-red-500/30 flex items-center justify-center text-2xl font-black text-red-400 flex-shrink-0">
              {requestData.patientBloodType}
            </div>
            <div className="min-w-0">
              <h1 className="text-2xl font-bold text-white break-words">{requestData.hospitalName}</h1>
              <p className="text-gray-400 flex items-center gap-2 mt-1 text-sm">
                <MapPin className="w-4 h-4" /> {requestData.district}
              </p>
            </div>
          </div>
          <div className="text-left sm:text-right">
            <StatusBadge value={requestData.urgency} label={t(`dash.urgency.${requestData.urgency}`)} className="mb-1" />
            <p className="text-xs text-gray-500 font-mono">{t('bloodRequests.details')} #{requestData.id}</p>
          </div>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="p-4 rounded-xl bg-white/5 border border-white/5">
            <p className="text-xs text-gray-400 mb-1">{t('request.unitsNeeded')}</p>
            <p className="text-lg font-bold text-white flex items-center gap-2">
              <Droplets className="w-4 h-4 text-red-400" /> {t('dash.unitsCount', { n: requestData.unitsNeeded ?? 0 })}
            </p>
          </div>
          <div className="p-4 rounded-xl bg-white/5 border border-white/5">
            <p className="text-xs text-gray-400 mb-1">{t('bloodRequests.status')}</p>
            <p className="text-lg font-bold text-white flex items-center gap-2">
              <Activity className="w-4 h-4 text-blue-400" /> {statusLabel}
            </p>
          </div>
          <div className="p-4 rounded-xl bg-white/5 border border-white/5">
            <p className="text-xs text-gray-400 mb-1">{t('bloodRequests.searchRadius')}</p>
            <p className="text-lg font-bold text-white flex items-center gap-2">
              <MapPin className="w-4 h-4 text-emerald-400" /> {requestData.currentRadiusKm} km
            </p>
          </div>
          <div className="p-4 rounded-xl bg-white/5 border border-white/5">
            <p className="text-xs text-gray-400 mb-1">{t('bloodRequests.expires')}</p>
            <p className="font-bold text-white flex items-center gap-2 text-sm">
              <Clock className="w-4 h-4 text-gray-400 flex-shrink-0" />
              {new Date(requestData.expiresAt).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })}
            </p>
          </div>
        </div>
      </div>

      {/* Replacement-Donor Private Circle Panel (owner only) */}
      {!circleForbidden && (
      <div className="glass-card p-5 md:p-8 rounded-2xl border border-purple-500/20 relative overflow-hidden bg-gradient-to-r from-purple-950/30 via-neutral-900/40 to-neutral-900/60">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-6">
          <div className="flex items-start gap-4">
            <div className="w-12 h-12 rounded-xl bg-purple-500/20 border border-purple-500/30 flex items-center justify-center text-purple-400 flex-shrink-0">
              <Users className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-white flex flex-wrap items-center gap-2">
                {t('bloodRequests.privateCircle')}
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-purple-500/20 text-purple-300 font-semibold uppercase">
                  {t('bloodRequests.familyAndFriends')}
                </span>
              </h2>
              <p className="text-sm text-gray-300 mt-1 max-w-xl">
                {t('bloodRequests.privateCircleDesc')}
              </p>
            </div>
          </div>

          {!circleInvite ? (
            <button
              onClick={handleGenerateCircle}
              disabled={circleLoading}
              className="inline-flex items-center gap-2 px-5 py-3 rounded-xl bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white text-sm font-semibold shadow-lg shadow-purple-600/30 transition-all self-start md:self-auto disabled:opacity-50"
            >
              <Share2 className="w-4 h-4" />
              {circleLoading ? t('bloodRequests.generatingLink') : t('bloodRequests.inviteCircle')}
            </button>
          ) : (
            <button
              onClick={loadCircleResponses}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-gray-300 hover:text-white text-xs font-medium self-start md:self-auto transition-colors"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              {t('bloodRequests.refreshCircle')}
            </button>
          )}
        </div>

        {circleError && (
          <div className="mb-4 rounded-xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400 text-sm">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{circleError}</span>
          </div>
        )}

        <div className="space-y-4 pt-2">
          {circleInvite && (
            <div className="p-4 rounded-xl bg-black/40 border border-white/10 flex flex-col lg:flex-row lg:items-center justify-between gap-3">
              <div className="truncate text-xs font-mono text-gray-300 select-all">
                {fullInviteUrl}
              </div>
              <div className="flex flex-wrap items-center gap-2 flex-shrink-0">
                <button
                  onClick={() => copyToClipboard(fullInviteUrl)}
                  className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white text-xs font-medium transition-colors"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  {copied ? t('bloodRequests.copied') : t('bloodRequests.copyLink')}
                </button>
                <Link
                  href={`/share/${circleInvite.inviteToken}`}
                  target="_blank"
                  className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 border border-rose-500/30 text-xs font-medium transition-colors"
                >
                  <Share2 className="w-3.5 h-3.5" />
                  {t('bloodRequests.previewCard')}
                </Link>
                <a
                  href={`https://api.whatsapp.com/send?text=${encodeURIComponent(shareText + ' ' + fullInviteUrl)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium transition-colors"
                >
                  <MessageSquare className="w-3.5 h-3.5" />
                  {t('bloodRequests.whatsapp')}
                </a>
              </div>
            </div>
          )}

          {/* Circle Volunteers Roster: shown whenever the circle exists or responses were fetched */}
          {(circleInvite || circleVolunteers.length > 0) && (
            <div className="pt-2">
              <h3 className="text-sm font-semibold text-white mb-3 flex items-center gap-2">
                <Users className="w-4 h-4 text-purple-400" />
                {t('bloodRequests.circleVolunteers')} ({circleVolunteers.length})
              </h3>
              {circleVolunteers.length === 0 ? (
                <div className="text-center py-8 rounded-xl bg-white/2 border border-white/5 text-gray-500 text-xs">
                  {t('bloodRequests.noResponsesYet')}
                </div>
              ) : (
                <div className="grid gap-3 sm:grid-cols-2">
                  {circleVolunteers.map((vol) => (
                    <div key={vol.id} className="p-4 rounded-xl bg-white/5 border border-white/10 space-y-2">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-semibold text-white text-sm break-words">{vol.responderName}</span>
                        <span className="px-2 py-0.5 rounded-md text-xs font-bold bg-red-500/20 text-red-300 border border-red-500/30">
                          {vol.bloodType}
                        </span>
                      </div>
                      <div className="flex flex-wrap items-center gap-2 text-xs text-gray-400">
                        <Phone className="w-3.5 h-3.5 text-gray-500" />
                        <a href={`tel:${vol.responderPhone}`} className="text-emerald-400 hover:underline">
                          {vol.responderPhone}
                        </a>
                        <span className="text-gray-600">·</span>
                        <span>{new Date(vol.respondedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                      </div>
                      {vol.notes && (
                        <p className="text-xs text-gray-400 italic bg-black/20 p-2 rounded break-words">
                          &quot;{vol.notes}&quot;
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
      )}

      {/* Live System Updates */}
      <div className="glass-card p-5 md:p-6 rounded-2xl border border-white/10 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Activity className="w-5 h-5 text-emerald-400" />
            {t('bloodRequests.liveMatchingTelemetry')}
          </h2>
          <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            {t('bloodRequests.radius')}: {requestData.currentRadiusKm} km
          </span>
        </div>

        {/* Dynamic Leaflet Search Perimeter Map */}
        <RequestDistanceMap
          hospitalName={requestData.hospitalName}
          district={requestData.district}
          radiusKm={requestData.currentRadiusKm}
          latitude={requestData.latitude}
          longitude={requestData.longitude}
        />

        {updates.length === 0 ? (
          <div className="text-center py-6 text-gray-500 border border-dashed border-white/10 rounded-xl">
            <div className="animate-pulse flex flex-col items-center gap-2">
              <Activity className="w-5 h-5 text-emerald-500/50" />
              <p className="text-xs">{t('bloodRequests.scanningRadius')}</p>
            </div>
          </div>
        ) : (
          <div className="space-y-2.5">
            {updates.map((msg, idx) => (
              <div key={idx} className="p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-start gap-3">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 mt-0.5 flex-shrink-0" />
                <p className="text-emerald-200 text-xs">{msg || t('dash.newActivity')}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
