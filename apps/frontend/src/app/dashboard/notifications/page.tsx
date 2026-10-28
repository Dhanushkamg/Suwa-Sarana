'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { Bell, CheckCheck, HeartHandshake, HeartPulse, Inbox, TriangleAlert, Wifi, WifiOff } from 'lucide-react';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import { API_BASE_URL } from '@/lib/constants';
import { apiMessage } from '@/lib/api/common';
import {
  adjustUnreadCount,
  fetchInbox,
  markAllNotificationsRead,
  markNotificationRead,
  NOTIFICATION_PATHS,
  refreshUnreadCount,
  setUnreadCount,
  toKind,
  type InboxNotification,
  type NotificationKind,
} from '@/lib/api/notifications';
import { lowDataInterval, useLowData } from '@/lib/lowData';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import { FilterChips } from '@/components/dash/FilterChips';
import { IconTile, type IconTone } from '@/components/dash/IconTile';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { CARD } from '@/components/dash/styles';
import { useAsync } from '@/components/dash/useAsync';
import { PulseDot } from '@/components/dash/UrgencyBadge';
import { cn } from '@/lib/utils';

interface StreamPayload {
  type?: string;
  title?: string;
  body?: string;
  message?: string;
  referenceId?: number;
}

/** Errors that must stop the stream for good (e.g. 401), as opposed to transient ones we retry. */
class FatalStreamError extends Error {}

const RETRY_MS = 3000;
const POLL_MS = 15_000;

const KIND_STYLE: Record<NotificationKind, { tone: IconTone; Icon: typeof Bell }> = {
  MATCH: { tone: 'primary', Icon: HeartPulse },
  ESCALATION: { tone: 'warning', Icon: TriangleAlert },
  DONATION: { tone: 'success', Icon: HeartHandshake },
  SYSTEM: { tone: 'muted', Icon: Bell },
};

export default function NotificationsPage() {
  const { t, format } = useI18n();
  const toast = useToast();
  const { lowData, ready } = useLowData();
  // Only the *presence* of a token matters here: a refreshed token must not restart the stream.
  const hasToken = useAuthStore((s) => !!s.accessToken);
  const role = useAuthStore((s) => s.user?.role);

  const inbox = useAsync(fetchInbox);
  const inboxAvailable = inbox.state.status === 'ready';
  const inboxMissing = inbox.state.status === 'unavailable';

  const [connected, setConnected] = useState(false);
  const [local, setLocal] = useState<InboxNotification[]>([]); // live events while the inbox endpoint does not exist
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  const [markingAll, setMarkingAll] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const nextLocalId = useRef(-1);

  // A ref so the stream effect never restarts just because the inbox state object changed
  const inboxReload = useRef(inbox.reload);
  useEffect(() => {
    inboxReload.current = inbox.reload;
  }, [inbox.reload]);
  const inboxAvailableRef = useRef(false);
  useEffect(() => {
    inboxAvailableRef.current = inboxAvailable;
  }, [inboxAvailable]);

  // Keep the shared unread badge in step with what this page shows
  const items = useMemo(() => (inbox.state.status === 'ready' ? inbox.state.data : local), [inbox.state, local]);
  const unread = useMemo(() => (inboxAvailable ? items.filter((n) => !n.read).length : 0), [items, inboxAvailable]);
  useEffect(() => {
    if (inboxAvailable) setUnreadCount(unread);
  }, [inboxAvailable, unread]);

  // Live stream (normal mode). In Low-Data Mode with a working inbox it is replaced by slow polling.
  const pollInstead = lowData && inboxAvailable;
  useEffect(() => {
    if (!hasToken || !ready) return;

    if (pollInstead) {
      const timer = window.setInterval(() => {
        if (!document.hidden) {
          inboxReload.current();
          refreshUnreadCount();
        }
      }, lowDataInterval(POLL_MS, true));
      return () => window.clearInterval(timer);
    }

    const abortController = new AbortController();
    const retryMs = lowData ? RETRY_MS * 4 : RETRY_MS;

    const onLiveEvent = (kind: NotificationKind, payload: StreamPayload | null, fallbackText?: string) => {
      if (inboxAvailableRef.current) {
        // The server stores it: re-read the inbox so ids, read flags and order are the server's
        inboxReload.current();
        adjustUnreadCount(1);
        return;
      }
      setLocal((prev) =>
        [
          {
            id: nextLocalId.current--,
            kind,
            title: payload?.title,
            body: payload?.body || payload?.message || fallbackText,
            read: true,
            createdAt: new Date(),
            referenceId: payload?.referenceId,
          },
          ...prev,
        ].slice(0, 100)
      );
    };

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
        if (res.ok && isStream) {
          setConnected(true);
          return;
        }
        setConnected(false);
        // 4xx (e.g. 401 expired token) will not fix itself: stop. Server hiccups are retried.
        if (res.status >= 400 && res.status < 500) throw new FatalStreamError(`HTTP ${res.status}`);
        throw new Error(`Unexpected stream response (${res.status})`);
      },
      onmessage: (event) => {
        if (!event.data) return; // keep-alive
        let payload: StreamPayload | null = null;
        try {
          payload = JSON.parse(event.data) as StreamPayload;
        } catch {
          payload = null;
        }
        if (event.event === 'NEW_MATCH') onLiveEvent('MATCH', payload);
        else if (event.event === 'ESCALATION') onLiveEvent('ESCALATION', payload);
        else if (payload) onLiveEvent(toKind(payload.type), payload);
        else onLiveEvent('SYSTEM', null, event.data);
      },
      onerror: (err) => {
        setConnected(false);
        if (err instanceof FatalStreamError) throw err; // stop retrying
        return retryMs; // transient: try again shortly
      },
      onclose: () => {
        setConnected(false);
        // The server closes long-lived streams periodically; throwing makes the library reconnect.
        throw new Error('Stream closed');
      },
    }).catch(() => {
      // Fatal error or aborted: nothing more to do.
      setConnected(false);
    });

    return () => {
      abortController.abort();
    };
  }, [hasToken, ready, lowData, pollInstead]);

  const markRead = useCallback(
    async (id: number) => {
      if (busyId !== null) return;
      setBusyId(id);
      try {
        await markNotificationRead(id);
        inbox.mutate((rows) => rows.map((n) => (n.id === id ? { ...n, read: true } : n)));
      } catch (err) {
        toast.show(apiMessage(err, t('dashUi.notif.markFailed')), 'error');
      } finally {
        setBusyId(null);
      }
    },
    [busyId, inbox, toast, t]
  );

  const markAll = async () => {
    if (markingAll) return;
    setMarkingAll(true);
    try {
      await markAllNotificationsRead();
      inbox.mutate((rows) => rows.map((n) => ({ ...n, read: true })));
      toast.show(t('dashUi.notif.allMarked'), 'success');
    } catch (err) {
      toast.show(apiMessage(err, t('dashUi.notif.markFailed')), 'error');
    } finally {
      setMarkingAll(false);
    }
  };

  const titleFor = (n: InboxNotification) =>
    n.title || (n.kind === 'MATCH' ? t('notifications.newMatch') : n.kind === 'ESCALATION' ? t('notifications.escalation') : t('notifications.platformAlert'));

  const messageFor = (n: InboxNotification) => n.body || (n.kind === 'MATCH' ? t('dash.matchDispatched') : n.kind === 'ESCALATION' ? t('dash.radiusExpanded') : '');

  const hrefFor = (n: InboxNotification): string | null => {
    if (n.kind === 'MATCH' && role === 'DONOR') return '/dashboard/donor/matches';
    if (n.referenceId && role !== 'DONOR') return `/dashboard/requests/${n.referenceId}`;
    return null;
  };

  const shown = filter === 'unread' ? items.filter((n) => !n.read) : items;
  const loading = inbox.state.status === 'loading';

  return (
    <div className="max-w-3xl">
      <PageHeader
        eyebrow={t('dashUi.eyebrow.notifications')}
        title={t('common.notifications')}
        description={t('dashboard.notificationsDesc')}
        actions={
          <>
            {/* Live status: icon + text + colour */}
            <span role="status" className="inline-flex min-h-11 items-center gap-2.5 rounded-full border border-line px-4 text-sm">
              {pollInstead ? (
                <>
                  <WifiOff className="h-4 w-4 text-gray-500" aria-hidden="true" />
                  <span className="font-medium text-foreground">{t('dashUi.notif.polling')}</span>
                </>
              ) : (
                <>
                  <PulseDot tone={connected ? 'success' : 'warning'} still={!connected} />
                  {connected ? <Wifi className="h-4 w-4 text-success-text" aria-hidden="true" /> : <WifiOff className="h-4 w-4 text-warning-text" aria-hidden="true" />}
                  <span className={cn('font-medium', connected ? 'text-success-text' : 'text-warning-text')}>
                    {connected ? t('notifications.liveConnected') : t('notifications.disconnected')}
                  </span>
                </>
              )}
            </span>
            {inboxAvailable && <RefreshButton onClick={inbox.reload} loading={inbox.busy} />}
          </>
        }
      />

      {inboxAvailable && (
        <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
          <FilterChips
            label={t('dashUi.notif.filter')}
            value={filter}
            onChange={setFilter}
            options={[
              { value: 'all', label: t('dash.all'), count: items.length },
              { value: 'unread', label: t('dashUi.notif.unread'), count: unread },
            ]}
          />
          <Button type="button" variant="secondary" onClick={markAll} loading={markingAll} disabled={unread === 0 || markingAll}>
            {!markingAll && <CheckCheck className="h-4 w-4" aria-hidden="true" />}
            {t('dashUi.notif.markAll')}
          </Button>
        </div>
      )}

      {inboxMissing && (
        <Notice tone="info" className="mb-6">
          {t('dashUi.notif.liveOnly')}
        </Notice>
      )}
      {inbox.state.status === 'error' && (
        <Notice
          tone="error"
          className="mb-6"
          action={
            <Button type="button" variant="secondary" onClick={() => void inbox.reload()}>
              {t('dash.retry')}
            </Button>
          }
        >
          {inbox.state.message || t('dash.loadFailed')}
        </Notice>
      )}

      {loading ? (
        <div role="status" aria-label={t('common.loading')} className="space-y-3">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      ) : shown.length === 0 ? (
        <EmptyState
          icon={filter === 'unread' ? <CheckCheck className="h-10 w-10" aria-hidden="true" /> : <Inbox className="h-10 w-10" aria-hidden="true" />}
          title={filter === 'unread' ? t('dashUi.notif.noUnread') : t('dashUi.notif.emptyTitle')}
          description={filter === 'unread' ? undefined : t('notifications.streamInitDesc')}
        />
      ) : (
        <ol className="space-y-3" aria-live="polite">
          {shown.map((n) => {
            const { tone, Icon } = KIND_STYLE[n.kind];
            const href = hrefFor(n);
            const isUnread = inboxAvailable && !n.read;
            return (
              <li key={n.id} className={cn(CARD, 'flex items-start gap-4 p-4 sm:p-5', isUnread && 'border-primary/50 bg-primary-soft')}>
                <IconTile size="md" tone={tone}>
                  <Icon />
                </IconTile>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                    <span className={cn('text-base text-foreground', isUnread ? 'font-semibold' : 'font-medium')}>
                      {isUnread && <span className="sr-only">{t('dashUi.notif.unread')}: </span>}
                      {titleFor(n)}
                    </span>
                    {n.createdAt && <span className="text-sm tabular-nums text-gray-500">{format.dateTime(n.createdAt, { dateStyle: 'medium', timeStyle: 'short' })}</span>}
                  </div>
                  <p className="mt-1 break-words text-sm leading-relaxed text-gray-400">{messageFor(n)}</p>
                  {(href || isUnread) && (
                    <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1">
                      {href && (
                        <Link href={href} className="inline-flex min-h-11 items-center text-sm font-medium text-primary-text underline-offset-4 hover:underline">
                          {t('dashUi.notif.open')}
                        </Link>
                      )}
                      {isUnread && (
                        <button
                          type="button"
                          onClick={() => markRead(n.id)}
                          disabled={busyId !== null}
                          className="inline-flex min-h-11 items-center text-sm font-medium text-gray-400 underline-offset-4 hover:text-foreground hover:underline disabled:opacity-50"
                        >
                          {t('dashUi.notif.markRead')}
                        </button>
                      )}
                    </div>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}
