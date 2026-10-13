'use client';

import { useEffect, useState, useCallback } from 'react';
import type { ReactNode } from 'react';
import { useParams } from 'next/navigation';
import {
  MapPin, Activity, Droplets, CheckCircle2,
  Share2, Copy, Check, Users, Phone, MessageSquare, AlertCircle
} from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { API_BASE_URL } from '@/lib/constants';
import { BloodRequest } from '@/types';
import RequestDistanceMap from '@/components/maps/RequestDistanceMap';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { Reveal } from '@/components/motion/Reveal';
import { ButtonLink } from '@/components/dash/ButtonLink';
import { BloodTypeTile, IconTile } from '@/components/dash/IconTile';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader, SectionLabel } from '@/components/dash/Panel';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { EYEBROW } from '@/components/dash/styles';
import { PulseDot, RequestStatusBadge, UrgencyBadge } from '@/components/dash/UrgencyBadge';
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
      <div role="status" aria-label={t('common.loading')}>
        <Skeleton className="mb-5 h-4 w-20" />
        <Skeleton className="mb-3 h-3 w-40" />
        <Skeleton className="mb-8 h-12 w-3/5 rounded-xl" />
        <Skeleton className="h-28 w-full rounded-2xl" />
        <Skeleton className="mt-6 h-56 w-full rounded-2xl" />
      </div>
    );
  }

  if (loadFailed || !requestData) {
    return (
      <div>
        <PageHeader
          eyebrow={t('dashUi.eyebrow.requestDetail')}
          title={loadFailed ? t('common.error') : t('bloodRequests.notFound')}
          backHref="/dashboard/requests"
        />
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

  const facts: { label: string; value: ReactNode; big?: boolean }[] = [
    {
      label: t('request.patientBloodType'),
      value: <span className="font-display text-5xl leading-none text-ember">{requestData.patientBloodType}</span>,
    },
    {
      label: t('request.unitsNeeded'),
      value: (
        <span className="flex items-baseline gap-2">
          <span className="font-display text-5xl leading-none tabular-nums text-foreground">{requestData.unitsNeeded ?? 0}</span>
          <Droplets className="h-4 w-4 text-ember" aria-hidden="true" />
        </span>
      ),
    },
    {
      label: t('bloodRequests.searchRadius'),
      value: (
        <span className="flex items-baseline gap-1.5">
          <span className="font-display text-5xl leading-none tabular-nums text-foreground">{requestData.currentRadiusKm}</span>
          <span className="font-mono text-sm text-gray-500">km</span>
        </span>
      ),
    },
    {
      label: t('bloodRequests.expires'),
      value: (
        <span className="font-mono text-sm leading-snug text-foreground">
          {new Date(requestData.expiresAt).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}
        </span>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        backHref="/dashboard/requests"
        eyebrow={`${t('bloodRequests.details')} #${requestData.id}`}
        title={requestData.hospitalName}
        badges={
          <span className="flex flex-wrap items-center gap-2">
            <UrgencyBadge urgency={requestData.urgency} />
            <RequestStatusBadge status={requestData.status} />
          </span>
        }
        description={
          <span className="inline-flex items-center gap-2">
            <MapPin className="h-4 w-4 text-gray-500" aria-hidden="true" /> {requestData.district}
          </span>
        }
      />

      <div className="space-y-6">
        {/* Key facts */}
        <Rise delay={0.05}>
          <dl className="glass-card grid grid-cols-2 gap-px overflow-hidden rounded-2xl bg-line md:grid-cols-4">
            {facts.map((f) => (
              <div key={f.label} className="flex flex-col justify-between gap-5 bg-surface p-5">
                <dt className={EYEBROW}>{f.label}</dt>
                <dd>{f.value}</dd>
              </div>
            ))}
          </dl>
        </Rise>

        {/* Replacement-donor private circle (owner only) */}
        {!circleForbidden && (
          <Reveal y={12}>
            <Panel className="sm:p-8">
              <SectionHeader
                eyebrow={t('bloodRequests.familyAndFriends')}
                title={t('bloodRequests.privateCircle')}
                description={t('bloodRequests.privateCircleDesc')}
                icon={
                  <IconTile size="md">
                    <Users />
                  </IconTile>
                }
                action={
                  !circleInvite ? (
                    <Button type="button" variant="primary" size="sm" onClick={handleGenerateCircle} disabled={circleLoading}>
                      <Share2 className="h-3.5 w-3.5" aria-hidden="true" />
                      {circleLoading ? t('bloodRequests.generatingLink') : t('bloodRequests.inviteCircle')}
                    </Button>
                  ) : (
                    <RefreshButton onClick={loadCircleResponses} label={t('bloodRequests.refreshCircle')} />
                  )
                }
              />

              {circleError && (
                <Notice tone="error" className="mb-4">
                  {circleError}
                </Notice>
              )}

              <div className="space-y-6">
                {circleInvite && (
                  <div className="flex flex-col justify-between gap-3 rounded-xl border border-line bg-background/60 p-4 lg:flex-row lg:items-center">
                    <p className="min-w-0 select-all break-all font-mono text-xs leading-relaxed text-gray-300">{fullInviteUrl}</p>
                    <div className="flex shrink-0 flex-wrap items-center gap-2">
                      <Button type="button" variant="secondary" size="sm" onClick={() => copyToClipboard(fullInviteUrl)}>
                        {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" aria-hidden="true" /> : <Copy className="h-3.5 w-3.5" aria-hidden="true" />}
                        {copied ? t('bloodRequests.copied') : t('bloodRequests.copyLink')}
                      </Button>
                      <ButtonLink href={`/share/${circleInvite.inviteToken}`} target="_blank" variant="secondary">
                        <Share2 className="h-3.5 w-3.5" aria-hidden="true" />
                        {t('bloodRequests.previewCard')}
                      </ButtonLink>
                      <a
                        href={`https://api.whatsapp.com/send?text=${encodeURIComponent(shareText + ' ' + fullInviteUrl)}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center justify-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-4 py-1.5 text-[13px] font-medium text-emerald-300 transition-colors duration-300 hover:border-emerald-500/50 hover:bg-emerald-500/20 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ember"
                      >
                        <MessageSquare className="h-3.5 w-3.5" aria-hidden="true" />
                        {t('bloodRequests.whatsapp')}
                      </a>
                    </div>
                  </div>
                )}

                {/* Volunteers: shown whenever the circle exists or responses were fetched */}
                {(circleInvite || circleVolunteers.length > 0) && (
                  <div>
                    <SectionLabel className="mb-4">
                      {t('bloodRequests.circleVolunteers')} ({circleVolunteers.length})
                    </SectionLabel>
                    {circleVolunteers.length === 0 ? (
                      <p className="rounded-xl border border-dashed border-foreground/[0.16] px-4 py-8 text-center text-xs text-gray-500">
                        {t('bloodRequests.noResponsesYet')}
                      </p>
                    ) : (
                      <ul className="grid gap-3 sm:grid-cols-2">
                        {circleVolunteers.map((vol) => (
                          <li key={vol.id} className="space-y-3 rounded-xl border border-line bg-foreground/[0.02] p-4">
                            <div className="flex items-start justify-between gap-3">
                              <span className="break-words text-sm font-medium text-foreground">{vol.responderName}</span>
                              <BloodTypeTile type={vol.bloodType} size="sm" className="h-9 w-9 rounded-lg text-base" />
                            </div>
                            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-400">
                              <Phone className="h-3.5 w-3.5 text-gray-500" aria-hidden="true" />
                              <a href={`tel:${vol.responderPhone}`} className="font-mono text-emerald-400 hover:underline">
                                {vol.responderPhone}
                              </a>
                              <span className="font-mono text-gray-500">
                                {new Date(vol.respondedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                              </span>
                            </div>
                            {vol.notes && (
                              <p className="break-words border-l border-foreground/[0.16] pl-3 text-xs italic leading-relaxed text-gray-400">
                                &quot;{vol.notes}&quot;
                              </p>
                            )}
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
              </div>
            </Panel>
          </Reveal>
        )}

        {/* Live updates */}
        <Reveal y={12}>
          <Panel className="sm:p-8">
            <SectionHeader
              eyebrow={t('dashUi.section.live')}
              title={t('bloodRequests.liveMatchingTelemetry')}
              action={
                <span className="inline-flex items-center gap-2 rounded-full border border-line px-3 py-1 font-mono text-xs text-gray-300">
                  <Activity className="h-3.5 w-3.5 text-emerald-400" aria-hidden="true" />
                  {t('bloodRequests.radius')}: {requestData.currentRadiusKm} km
                </span>
              }
            />

            {/* Search perimeter map */}
            <div className="mb-5 overflow-hidden rounded-xl border border-line">
              <RequestDistanceMap
                hospitalName={requestData.hospitalName}
                district={requestData.district}
                radiusKm={requestData.currentRadiusKm}
                latitude={requestData.latitude}
                longitude={requestData.longitude}
              />
            </div>

            {updates.length === 0 ? (
              <div className="flex items-center justify-center gap-3 rounded-xl border border-dashed border-foreground/[0.16] px-4 py-6 text-gray-500">
                <PulseDot />
                <p className="text-xs">{t('bloodRequests.scanningRadius')}</p>
              </div>
            ) : (
              <ul className="divide-y divide-line rounded-xl border border-line" aria-live="polite">
                {updates.map((msg, idx) => (
                  <li key={idx} className="flex items-start gap-3 px-4 py-3">
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" aria-hidden="true" />
                    <p className="min-w-0 break-words text-sm text-gray-300">{msg || t('dash.newActivity')}</p>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </Reveal>
      </div>
    </div>
  );
}
