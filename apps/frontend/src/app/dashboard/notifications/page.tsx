'use client';

import { useState, useEffect, useRef } from 'react';
import { Bell, Zap, Heart } from 'lucide-react';
import { motion, useReducedMotion } from 'motion/react';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import { API_BASE_URL } from '@/lib/constants';
import { EmptyState } from '@/components/ui/EmptyState';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { IconTile } from '@/components/dash/IconTile';
import { PageHeader } from '@/components/dash/PageHeader';
import { EASE } from '@/components/dash/styles';
import { PulseDot } from '@/components/dash/UrgencyBadge';

type NotificationType = 'MATCH' | 'ESCALATION' | 'DONATION' | 'SYSTEM';

interface NotificationItem {
  id: number;
  type: NotificationType;
  title?: string;
  message?: string;
  timestamp: string;
}

interface StreamPayload {
  type?: string;
  title?: string;
  body?: string;
  message?: string;
}

/** Errors that must stop the stream for good (e.g. 401), as opposed to transient ones we retry. */
class FatalStreamError extends Error {}

const RETRY_MS = 3000;

export default function NotificationsPage() {
  const { t } = useI18n();
  const reduce = useReducedMotion();
  // Only the *presence* of a token matters here: a refreshed token must not restart the stream.
  const hasToken = useAuthStore((s) => !!s.accessToken);
  const [connected, setConnected] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const nextId = useRef(0);

  useEffect(() => {
    if (!hasToken) return;

    const abortController = new AbortController();

    const push = (type: NotificationType, payload: StreamPayload | null, fallbackText?: string) => {
      setNotifications((prev) =>
        [
          {
            id: nextId.current++,
            type,
            title: payload?.title,
            message: payload?.body || payload?.message || fallbackText,
            timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
          },
          ...prev,
        ].slice(0, 100)
      );
    };

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
        if (event.event === 'NEW_MATCH') {
          push('MATCH', payload);
        } else if (event.event === 'ESCALATION') {
          push('ESCALATION', payload);
        } else if (payload) {
          const type = payload.type === 'DONATION' ? 'DONATION' : 'SYSTEM';
          push(type, payload);
        } else {
          push('SYSTEM', null, event.data);
        }
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
  }, [hasToken]);

  const titleFor = (n: NotificationItem) =>
    n.title ||
    (n.type === 'MATCH' ? t('notifications.newMatch') : n.type === 'ESCALATION' ? t('notifications.escalation') : t('notifications.platformAlert'));

  const messageFor = (n: NotificationItem) =>
    n.message ||
    (n.type === 'MATCH' ? t('dash.matchDispatched') : n.type === 'ESCALATION' ? t('dash.radiusExpanded') : '');

  const TYPE_STYLE: Record<NotificationType, { tone: 'ember' | 'saffron' | 'sage' | 'muted'; Icon: typeof Bell }> = {
    MATCH: { tone: 'ember', Icon: Heart },
    ESCALATION: { tone: 'saffron', Icon: Zap },
    DONATION: { tone: 'sage', Icon: Bell },
    SYSTEM: { tone: 'muted', Icon: Bell },
  };

  return (
    <div className="max-w-3xl">
      <PageHeader
        eyebrow={t('dashUi.eyebrow.notifications')}
        title={t('common.notifications')}
        description={t('dashboard.notificationsDesc')}
        actions={
          /* Live SSE status */
          <span
            role="status"
            className="inline-flex items-center gap-2.5 rounded-full border border-line px-3.5 py-1.5 text-xs"
          >
            <PulseDot tone={connected ? 'sage' : 'saffron'} still={!connected} />
            <span className={connected ? 'font-medium text-emerald-300' : 'font-medium text-saffron'}>
              {connected ? t('notifications.liveConnected') : t('notifications.disconnected')}
            </span>
          </span>
        }
      />

      {notifications.length > 0 && (
        <ol className="relative mb-6 space-y-3 before:absolute before:bottom-4 before:left-[1.35rem] before:top-4 before:w-px before:bg-line" aria-live="polite">
          {notifications.map((notif) => {
            const { tone, Icon } = TYPE_STYLE[notif.type];
            return (
              <motion.li
                key={notif.id}
                initial={reduce ? false : { opacity: 0, y: -8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.35, ease: EASE }}
                className="glass-card relative flex items-start gap-4 rounded-2xl p-4 sm:p-5"
              >
                <IconTile size="md" tone={tone} className="relative bg-surface-raised">
                  <Icon />
                </IconTile>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                    <span className="text-sm font-medium text-foreground">{titleFor(notif)}</span>
                    <span className="font-mono text-xs tabular-nums text-gray-500">{notif.timestamp}</span>
                  </div>
                  <p className="mt-1 break-words text-sm leading-relaxed text-gray-400">{messageFor(notif)}</p>
                </div>
              </motion.li>
            );
          })}
        </ol>
      )}

      {/* Static intro card: always shown, never part of the live list so language switches cannot reset anything. */}
      {notifications.length === 0 ? (
        <EmptyState
          icon={<Bell className="w-10 h-10" />}
          title={t('notifications.streamInit')}
          description={t('notifications.streamInitDesc')}
        />
      ) : (
        <div className="flex items-start gap-4 rounded-2xl border border-dashed border-foreground/[0.16] p-4 sm:p-5">
          <IconTile size="sm" tone="muted">
            <Bell />
          </IconTile>
          <div className="min-w-0 flex-1">
            <span className="text-sm font-medium text-foreground">{t('notifications.streamInit')}</span>
            <p className="mt-1 text-sm leading-relaxed text-gray-400">{t('notifications.streamInitDesc')}</p>
          </div>
        </div>
      )}
    </div>
  );
}
