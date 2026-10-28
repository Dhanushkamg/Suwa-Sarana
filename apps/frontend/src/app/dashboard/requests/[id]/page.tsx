'use client';

import { useEffect, useState, useCallback } from 'react';
import type { ReactNode } from 'react';
import dynamic from 'next/dynamic';
import { useParams } from 'next/navigation';
import { MapPin, Activity, Droplets, CheckCircle2, Share2, Copy, Check, Users, Phone, MessageSquare, AlertCircle, ClipboardCheck, CircleX, ShieldOff } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { API_BASE_URL } from '@/lib/constants';
import { apiMessage, apiStatus, asDate, asList, unwrap } from '@/lib/api/common';
import { closeRequest, fetchRequest, fulfilRequest, isRequestLive } from '@/lib/api/requests';
import { NOTIFICATION_PATHS } from '@/lib/api/notifications';
import { lowDataInterval, useLowData } from '@/lib/lowData';
import { buttonClasses, Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import { ConfirmDialog } from '@/components/dash/ConfirmDialog';
import { BloodTypeTile, IconTile } from '@/components/dash/IconTile';
import { MatchesDrawer } from '@/components/dash/MatchesDrawer';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel, SectionHeader, SectionLabel } from '@/components/dash/Panel';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { BlockSkeleton } from '@/components/dash/Skeletons';
import { CARD, EYEBROW } from '@/components/dash/styles';
import { PulseDot, RequestStatusBadge, UrgencyBadge } from '@/components/dash/UrgencyBadge';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { useAuthStore } from '@/store/authStore';
import type { BloodRequest } from '@/types';

// Leaflet only loads when the map is shown (never in Low-Data Mode)
const RequestDistanceMap = dynamic(() => import('@/components/maps/RequestDistanceMap'), { ssr: false, loading: () => <BlockSkeleton className="h-64" /> });

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

interface StreamPayload {
  referenceId?: number;
  requestId?: number;
  body?: string;
  message?: string;
}

/** Errors that must stop the stream for good (e.g. 401), as opposed to transient ones we retry. */
class FatalStreamError extends Error {}

const RETRY_MS = 3000;
const POLL_MS = 15_000;

export default function RequestLiveStatusPage() {
  const { t, format } = useI18n();
  const toast = useToast();
  const params = useParams();
  const requestId = params.id as string;
  const { lowData, ready } = useLowData();
  // Only the presence of a token matters; a refreshed token must not restart the stream.
  const hasToken = useAuthStore((s) => !!s.accessToken);
  const role = useAuthStore((s) => s.user?.role);

  const [requestData, setRequestData] = useState<BloodRequest | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState<'error' | 'forbidden' | null>(null);
  const [updates, setUpdates] = useState<string[]>([]);
  const [showMap, setShowMap] = useState(false);

  // Close-the-loop actions
  const [action, setAction] = useState<'fulfil' | 'close' | null>(null);
  const [matchesOpen, setMatchesOpen] = useState(false);

  // Circle state
  const [circleInvite, setCircleInvite] = useState<CircleInviteData | null>(null);
  const [circleVolunteers, setCircleVolunteers] = useState<CircleVolunteer[]>([]);
  const [circleLoading, setCircleLoading] = useState(false);
  const [circleError, setCircleError] = useState<string | null>(null);
  const [circleForbidden, setCircleForbidden] = useState(false);
  const [copied, setCopied] = useState(false);
  const [copyFailed, setCopyFailed] = useState(false);

  const loadRequest = useCallback(async (): Promise<boolean> => {
    try {
      setRequestData(await fetchRequest(requestId));
      setLoadFailed(null);
      return true;
    } catch (err) {
      setLoadFailed(apiStatus(err) === 403 ? 'forbidden' : 'error');
      return false;
    } finally {
      setLoading(false);
    }
  }, [requestId]);

  /** Returns true when the volunteer list was loaded (which only the request owner can do). */
  const loadCircleResponses = useCallback(async (): Promise<boolean> => {
    try {
      const res = await apiClient.get(`/requests/${requestId}/circle/responses`);
      setCircleVolunteers(asList<CircleVolunteer>(res.data));
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
    const res = await apiClient.post(`/requests/${requestId}/circle`);
    setCircleInvite(unwrap<CircleInviteData | null>(res.data) ?? null);
  }, [requestId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadRequest();
    // After a reload the invite is gone from memory: fetch it again whenever the owner already has a circle.
    loadCircleResponses().then((isOwnerWithCircle) => {
      if (isOwnerWithCircle) fetchInvite().catch(() => undefined);
    });
  }, [loadRequest, loadCircleResponses, fetchInvite]);

  // Live updates: a server-sent event stream normally; plain slow polling in Low-Data Mode.
  useEffect(() => {
    if (!hasToken || !ready) return;

    if (lowData) {
      const timer = window.setInterval(() => {
        if (document.hidden) return;
        loadRequest();
        loadCircleResponses();
      }, lowDataInterval(POLL_MS, true));
      return () => window.clearInterval(timer);
    }

    const abortController = new AbortController();

    fetchEventSource(`${API_BASE_URL}${NOTIFICATION_PATHS.stream}`, {
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
  }, [requestId, hasToken, ready, lowData, loadRequest, loadCircleResponses]);

  const handleGenerateCircle = async () => {
    if (circleLoading) return;
    setCircleLoading(true);
    setCircleError(null);
    try {
      await fetchInvite();
      loadCircleResponses();
    } catch (err: unknown) {
      if (apiStatus(err) === 403) setCircleForbidden(true);
      setCircleError(apiMessage(err, t('dash.circleFailed')));
    } finally {
      setCircleLoading(false);
    }
  };

  const copyToClipboard = async (url: string) => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setCopyFailed(false);
      setTimeout(() => setCopied(false), 3000);
    } catch {
      // Clipboard unavailable (e.g. insecure context): say so; the link is still selectable on screen.
      setCopyFailed(true);
    }
  };

  const runAction = async () => {
    if (action === 'fulfil') await fulfilRequest(requestId);
    else if (action === 'close') await closeRequest(requestId);
    await loadRequest();
    toast.show(action === 'fulfil' ? t('dashUi.req.fulfilledToast') : t('dashUi.req.closedToast'), 'success');
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
    const forbidden = loadFailed === 'forbidden';
    return (
      <div>
        <PageHeader
          eyebrow={t('dashUi.eyebrow.requestDetail')}
          title={forbidden ? t('dashUi.req.forbiddenTitle') : loadFailed ? t('common.error') : t('bloodRequests.notFound')}
          backHref="/dashboard/requests"
        />
        <EmptyState
          icon={forbidden ? <ShieldOff className="h-10 w-10 text-gray-500" aria-hidden="true" /> : <AlertCircle className="h-10 w-10 text-danger-text" aria-hidden="true" />}
          title={forbidden ? t('dashUi.req.forbiddenTitle') : loadFailed ? t('common.error') : t('bloodRequests.notFound')}
          description={forbidden ? t('dashUi.req.forbiddenDesc') : undefined}
          action={
            loadFailed === 'error' ? (
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
  const expires = asDate(requestData.expiresAt);
  const canClose = role !== 'ADMIN' && !circleForbidden && isRequestLive(requestData.status);

  const facts: { label: string; value: ReactNode }[] = [
    {
      label: t('request.patientBloodType'),
      value: <span className="font-display text-5xl leading-none text-brand">{requestData.patientBloodType}</span>,
    },
    {
      label: t('request.unitsNeeded'),
      value: (
        <span className="flex items-baseline gap-2">
          <span className="font-display text-5xl leading-none tabular-nums text-foreground">{format.number(requestData.unitsNeeded ?? 0)}</span>
          <Droplets className="h-4 w-4 text-gray-500" aria-hidden="true" />
        </span>
      ),
    },
    {
      label: t('bloodRequests.searchRadius'),
      value: (
        <span className="flex items-baseline gap-1.5">
          <span className="font-display text-5xl leading-none tabular-nums text-foreground">{format.number(requestData.currentRadiusKm)}</span>
          <span className="text-sm text-gray-500">km</span>
        </span>
      ),
    },
    {
      label: t('bloodRequests.expires'),
      value: <span className="text-base leading-snug text-foreground">{expires ? format.dateTime(expires, { dateStyle: 'medium', timeStyle: 'short' }) : t('dashUi.noData')}</span>,
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
        actions={
          role !== 'ADMIN' && !circleForbidden ? (
            <>
              <Button type="button" variant="secondary" onClick={() => setMatchesOpen(true)}>
                <Users className="h-4 w-4" aria-hidden="true" />
                {t('dashUi.viewMatches')}
              </Button>
              {canClose && (
                <>
                  <Button type="button" variant="primary" onClick={() => setAction('fulfil')}>
                    <ClipboardCheck className="h-4 w-4" aria-hidden="true" />
                    {t('dashUi.req.markFulfilled')}
                  </Button>
                  <Button type="button" variant="secondary" onClick={() => setAction('close')} className="border-danger/40 text-danger-text">
                    <CircleX className="h-4 w-4" aria-hidden="true" />
                    {t('dashUi.req.closeRequest')}
                  </Button>
                </>
              )}
            </>
          ) : undefined
        }
      />

      <div className="space-y-6">
        {/* Key facts */}
        <Rise delay={0.05}>
          <dl className={`${CARD} grid grid-cols-2 gap-px overflow-hidden bg-line md:grid-cols-4`}>
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
          <Rise inView>
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
                    <Button type="button" variant="secondary" onClick={handleGenerateCircle} loading={circleLoading}>
                      {!circleLoading && <Share2 className="h-4 w-4" aria-hidden="true" />}
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
                  <div className="flex flex-col justify-between gap-3 rounded-xl border border-line bg-surface-raised p-4 lg:flex-row lg:items-center">
                    <p className="min-w-0 select-all break-all text-sm leading-relaxed text-foreground" dir="ltr">
                      {fullInviteUrl}
                    </p>
                    <div className="flex shrink-0 flex-wrap items-center gap-2">
                      <Button type="button" variant="secondary" onClick={() => copyToClipboard(fullInviteUrl)}>
                        {copied ? <Check className="h-4 w-4 text-success-text" aria-hidden="true" /> : <Copy className="h-4 w-4" aria-hidden="true" />}
                        {copied ? t('bloodRequests.copied') : t('bloodRequests.copyLink')}
                      </Button>
                      <a href={`/share/${circleInvite.inviteToken}`} target="_blank" rel="noopener noreferrer" className={buttonClasses('secondary', 'md')}>
                        <Share2 className="h-4 w-4" aria-hidden="true" />
                        {t('bloodRequests.previewCard')}
                      </a>
                      <a
                        href={`https://api.whatsapp.com/send?text=${encodeURIComponent(shareText + ' ' + fullInviteUrl)}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={buttonClasses('secondary', 'md')}
                      >
                        <MessageSquare className="h-4 w-4" aria-hidden="true" />
                        {t('bloodRequests.whatsapp')}
                      </a>
                    </div>
                    {copyFailed && (
                      <p role="alert" className="text-sm text-danger-text lg:basis-full">
                        {t('dashUi.req.copyFailed')}
                      </p>
                    )}
                  </div>
                )}

                {/* Volunteers: shown whenever the circle exists or responses were fetched */}
                {(circleInvite || circleVolunteers.length > 0) && (
                  <div>
                    <SectionLabel className="mb-4">{t('dashUi.req.volunteers', { count: circleVolunteers.length })}</SectionLabel>
                    {circleVolunteers.length === 0 ? (
                      <p className="rounded-xl border border-dashed border-line-strong px-4 py-8 text-center text-sm text-gray-500">{t('bloodRequests.noResponsesYet')}</p>
                    ) : (
                      <ul className="grid gap-3 sm:grid-cols-2">
                        {circleVolunteers.map((vol) => (
                          <li key={vol.id} className="space-y-3 rounded-xl border border-line bg-surface-raised p-4">
                            <div className="flex items-start justify-between gap-3">
                              <span className="break-words text-base font-medium text-foreground">{vol.responderName}</span>
                              <BloodTypeTile type={vol.bloodType} size="sm" />
                            </div>
                            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-gray-400">
                              <Phone className="h-4 w-4 text-gray-500" aria-hidden="true" />
                              <a href={`tel:${vol.responderPhone}`} dir="ltr" className="inline-flex min-h-11 items-center text-primary-text hover:underline">
                                {vol.responderPhone}
                              </a>
                              {asDate(vol.respondedAt) && <span className="text-gray-500">{format.dateTime(asDate(vol.respondedAt) as Date, { timeStyle: 'short' })}</span>}
                            </div>
                            {vol.notes && <p className="break-words border-s-2 border-line-strong ps-3 text-sm italic leading-relaxed text-gray-400">&quot;{vol.notes}&quot;</p>}
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
              </div>
            </Panel>
          </Rise>
        )}

        {/* Live updates */}
        <Rise inView>
          <Panel className="sm:p-8">
            <SectionHeader
              eyebrow={t('dashUi.section.live')}
              title={t('bloodRequests.liveMatchingTelemetry')}
              action={
                <span className="inline-flex items-center gap-2 rounded-full border border-line px-3 py-1 text-sm text-gray-300">
                  <Activity className="h-4 w-4 text-primary-text" aria-hidden="true" />
                  {t('bloodRequests.radius')}: {format.number(requestData.currentRadiusKm)} km
                </span>
              }
            />

            {/* Search perimeter map: skipped in Low-Data Mode unless asked for */}
            {lowData && !showMap ? (
              <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line bg-surface-raised p-4">
                <p className="min-w-0 text-sm text-gray-400">{t('dashUi.req.searchAreaText', { km: format.number(requestData.currentRadiusKm), hospital: requestData.hospitalName, district: requestData.district })}</p>
                <Button type="button" variant="secondary" onClick={() => setShowMap(true)}>
                  {t('dashUi.loadMapAnyway')}
                </Button>
              </div>
            ) : (
              <div className="mb-5 overflow-hidden rounded-xl border border-line">
                <RequestDistanceMap
                  hospitalName={requestData.hospitalName}
                  district={requestData.district}
                  radiusKm={requestData.currentRadiusKm}
                  latitude={requestData.latitude}
                  longitude={requestData.longitude}
                />
              </div>
            )}

            {updates.length === 0 ? (
              <div className="flex items-center justify-center gap-3 rounded-xl border border-dashed border-line-strong px-4 py-6 text-gray-500">
                <PulseDot />
                <p className="text-sm">{lowData ? t('dashUi.slowRefresh') : t('bloodRequests.scanningRadius')}</p>
              </div>
            ) : (
              <ul className="divide-y divide-line rounded-xl border border-line" aria-live="polite">
                {updates.map((msg, idx) => (
                  <li key={idx} className="flex items-start gap-3 px-4 py-3">
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success-text" aria-hidden="true" />
                    <p className="min-w-0 break-words text-sm text-foreground">{msg || t('dash.newActivity')}</p>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </Rise>
      </div>

      <MatchesDrawer requestId={matchesOpen ? requestData.id : null} hospitalName={requestData.hospitalName} onClose={() => setMatchesOpen(false)} />

      <ConfirmDialog
        open={action !== null}
        onClose={() => setAction(null)}
        title={action === 'fulfil' ? t('dashUi.req.fulfilTitle') : t('dashUi.req.closeTitle')}
        description={action === 'fulfil' ? t('dashUi.req.fulfilDesc') : t('dashUi.req.closeDesc')}
        confirmLabel={action === 'fulfil' ? t('dashUi.req.markFulfilled') : t('dashUi.req.closeRequest')}
        confirmVariant={action === 'fulfil' ? 'primary' : 'danger'}
        onConfirm={runAction}
        errorMessage={(err) => apiMessage(err, t('common.error'))}
      />
    </div>
  );
}
