'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { Bell, CheckCheck, HeartHandshake, HeartPulse, Inbox, TriangleAlert, Wifi, WifiOff } from 'lucide-react';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import { API_BASE_URL } from '@/lib/constants';
import { apiMessage, isEndpointMissing } from '@/lib/api/common';
import {
  adjustUnreadCount,
  fetchInbox,
  markAllNotificationsRead,
  markNotificationRead,
  notificationText,
  NOTIFICATION_PATHS,
  setUnreadCount,
  toNotification,
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
import { PulseDot } from '@/components/dash/UrgencyBadge';
import { cn } from '@/lib/utils';

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

type Filter = 'all' | 'unread';
type Status = 'loading' | 'ready' | 'error' | 'unavailable';

/** Where a notification leads, by type and role. */
function hrefFor(n: InboxNotification, role: string | undefined): string | null {
  switch (n.type) {
    case 'NEW_MATCH':
      return role === 'DONOR' ? '/dashboard/donor/matches' : null;
    case 'LOW_STOCK':
      return '/dashboard/inventory';
    case 'SLOT_CONFIRMED':
    case 'SLOT_REMINDER':
      return role === 'DONOR' ? '/dashboard/donor' : null;
    default:
      return n.referenceId && role !== 'DONOR' ? `/dashboard/requests/${n.referenceId}` : null;
  }
}

export default function NotificationsPage() {
  const { t, format } = useI18n();
  const toast = useToast();
  const { lowData, ready } = useLowData();
  // Only the *presence* of a token matters here: a refreshed token must not restart the stream.
  const hasToken = useAuthStore((s) => !!s.accessToken);
  const role = useAuthStore((s) => s.user?.role);

  const [filter, setFilter] = useState<Filter>('all');
  const [items, setItems] = useState<InboxNotification[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [status, setStatus] = useState<Status>('loading');
  const [errorMessage, setErrorMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [connected, setConnected] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const unreadOnly = filter === 'unread';
  const unreadOnlyRef = useRef(unreadOnly);
  const itemsRef = useRef<InboxNotification[]>([]);
  useEffect(() => {
    unreadOnlyRef.current = unreadOnly;
    itemsRef.current = items;
  });

  /** First page (newest). Keeps the old list on screen while refreshing. */
  const load = useCallback(async (): Promise<boolean> => {
    setBusy(true);
    try {
      const page = await fetchInbox({ unread: unreadOnly });
      setItems(page.items);
      setNextCursor(page.nextCursor);
      setUnreadCount(page.unreadCount);
      setStatus('ready');
      return true;
    } catch (err) {
      if (isEndpointMissing(err)) setStatus('unavailable');
      else {
        setStatus('error');
        setErrorMessage(apiMessage(err, ''));
      }
      return false;
    } finally {
      setBusy(false);
    }
  }, [unreadOnly]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  const loadRef = useRef(load);
  useEffect(() => {
    loadRef.current = load;
  }, [load]);

  const loadMore = async () => {
    if (nextCursor === null || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await fetchInbox({ cursor: nextCursor, unread: unreadOnly });
      setItems((prev) => [...prev, ...page.items.filter((n) => !prev.some((p) => p.id === n.id))]);
      setNextCursor(page.nextCursor);
      setUnreadCount(page.unreadCount);
    } catch (err) {
      toast.show(apiMessage(err, t('dash.loadFailed')), 'error');
    } finally {
      setLoadingMore(false);
    }
  };

  const unread = useMemo(() => items.filter((n) => !n.read).length, [items]);

  // Live stream (normal mode). In Low-Data Mode it is replaced by slow polling.
  const pollInstead = lowData;
  useEffect(() => {
    if (!hasToken || !ready) return;

    if (pollInstead) {
      const timer = window.setInterval(() => {
        if (!document.hidden) loadRef.current();
      }, lowDataInterval(POLL_MS, true));
      return () => window.clearInterval(timer);
    }

    const abortController = new AbortController();

    fetchEventSource(`${API_BASE_URL}${NOTIFICATION_PATHS.stream}`, {
      method: 'GET',
      headers: { Accept: 'text/event-stream' },
      signal: abortController.signal,
      // Always send the latest token, including on automatic reconnects. The library adds Last-Event-ID itself (from the
      // `id:` of the last event), so after a dropped connection the server replays what was missed.
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
        let n: InboxNotification;
        try {
          const raw = JSON.parse(event.data) as Record<string, unknown>;
          n = toNotification({ ...raw, type: raw.type ?? event.event });
        } catch {
          return; // not one of our notification payloads
        }
        if (!n.id) return;
        if (itemsRef.current.some((p) => p.id === n.id)) return; // replayed after a reconnect
        if (unreadOnlyRef.current && n.read) return;
        if (!n.read) adjustUnreadCount(1);
        setItems((prev) => (prev.some((p) => p.id === n.id) ? prev : [n, ...prev]));
      },
      onerror: (err) => {
        setConnected(false);
        if (err instanceof FatalStreamError) throw err; // stop retrying
        return RETRY_MS; // transient: try again shortly
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
  }, [hasToken, ready, pollInstead]);

  const markRead = useCallback(
    async (id: number) => {
      if (busyId !== null) return;
      setBusyId(id);
      try {
        await markNotificationRead(id);
        setItems((rows) => rows.map((n) => (n.id === id ? { ...n, read: true, readAt: new Date() } : n)));
        adjustUnreadCount(-1);
      } catch (err) {
        toast.show(apiMessage(err, t('dashUi.notif.markFailed')), 'error');
      } finally {
        setBusyId(null);
      }
    },
    [busyId, toast, t]
  );

  const markAll = async () => {
    if (markingAll) return;
    setMarkingAll(true);
    try {
      await markAllNotificationsRead();
      setItems((rows) => rows.map((n) => ({ ...n, read: true, readAt: n.readAt ?? new Date() })));
      setUnreadCount(0);
      toast.show(t('dashUi.notif.allMarked'), 'success');
    } catch (err) {
      toast.show(apiMessage(err, t('dashUi.notif.markFailed')), 'error');
    } finally {
      setMarkingAll(false);
    }
  };

  const loading = status === 'loading';
  const available = status === 'ready';

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
            {available && <RefreshButton onClick={load} loading={busy} />}
          </>
        }
      />

      {available && (
        <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
          <FilterChips
            label={t('dashUi.notif.filter')}
            value={filter}
            onChange={setFilter}
            options={[
              { value: 'all', label: t('dash.all') },
              { value: 'unread', label: t('dashUi.notif.unread'), count: unreadOnly ? items.length : unread },
            ]}
          />
          <Button type="button" variant="secondary" onClick={markAll} loading={markingAll} disabled={unread === 0 || markingAll}>
            {!markingAll && <CheckCheck className="h-4 w-4" aria-hidden="true" />}
            {t('dashUi.notif.markAll')}
          </Button>
        </div>
      )}

      {status === 'unavailable' && (
        <Notice tone="info" className="mb-6">
          {t('dashUi.notif.liveOnly')}
        </Notice>
      )}
      {status === 'error' && (
        <Notice
          tone="error"
          className="mb-6"
          action={
            <Button type="button" variant="secondary" onClick={() => void load()}>
              {t('dash.retry')}
            </Button>
          }
        >
          {errorMessage || t('dash.loadFailed')}
        </Notice>
      )}

      {loading ? (
        <div role="status" aria-label={t('common.loading')} className="space-y-3">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      ) : items.length === 0 && status !== 'error' ? (
        <EmptyState
          icon={unreadOnly ? <CheckCheck className="h-10 w-10" aria-hidden="true" /> : <Inbox className="h-10 w-10" aria-hidden="true" />}
          title={unreadOnly ? t('dashUi.notif.noUnread') : t('dashUi.notif.emptyTitle')}
          description={unreadOnly ? undefined : t('notifications.streamInitDesc')}
        />
      ) : (
        <>
          <ol className="space-y-3" aria-live="polite">
            {items.map((n) => {
              const { tone, Icon } = KIND_STYLE[n.kind];
              const href = hrefFor(n, role);
              const isUnread = !n.read;
              const text = notificationText(n, t, format);
              return (
                <li key={n.id} className={cn(CARD, 'flex items-start gap-4 p-4 sm:p-5', isUnread && 'border-primary/50 bg-primary-soft')}>
                  <IconTile size="md" tone={tone}>
                    <Icon />
                  </IconTile>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                      <span className={cn('text-base text-foreground', isUnread ? 'font-semibold' : 'font-medium')}>
                        {isUnread && <span className="sr-only">{t('dashUi.notif.unread')}: </span>}
                        {text.title}
                      </span>
                      {n.createdAt && <span className="text-sm tabular-nums text-gray-500">{format.dateTime(n.createdAt, { dateStyle: 'medium', timeStyle: 'short' })}</span>}
                    </div>
                    {text.body && <p className="mt-1 break-words text-sm leading-relaxed text-gray-400">{text.body}</p>}
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
          {nextCursor !== null && (
            <div className="mt-6 flex justify-center">
              <Button type="button" variant="secondary" onClick={loadMore} loading={loadingMore}>
                {t('dashUi.notif.loadMore')}
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
