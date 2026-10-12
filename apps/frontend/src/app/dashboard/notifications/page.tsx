'use client';

import { useState, useEffect, useRef } from 'react';
import { Bell, Zap, Radio, ArrowLeft, Heart } from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import { API_BASE_URL } from '@/lib/constants';
import { EmptyState } from '@/components/ui/EmptyState';
import { fetchEventSource } from '@microsoft/fetch-event-source';

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

  return (
    <div className="max-w-3xl mx-auto">
      <div className="mb-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <Link
            href="/dashboard"
            className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4"
          >
            <ArrowLeft className="w-4 h-4" />
            {t('common.back')}
          </Link>
          <h1 className="text-3xl font-bold text-white flex items-center gap-3">
            <Bell className="w-8 h-8 text-red-500" />
            {t('common.notifications')}
          </h1>
          <p className="text-gray-400 mt-1">{t('dashboard.notificationsDesc')}</p>
        </div>

        {/* Live SSE status indicator */}
        <div className="flex items-center gap-2 px-3.5 py-2 rounded-full bg-white/5 border border-white/10 text-xs self-start sm:self-auto">
          <Radio className={`w-3.5 h-3.5 ${connected ? 'text-emerald-400 animate-pulse' : 'text-amber-400'}`} />
          <span className={connected ? 'text-emerald-400 font-medium' : 'text-amber-400 font-medium'}>
            {connected ? t('notifications.liveConnected') : t('notifications.disconnected')}
          </span>
        </div>
      </div>

      <div className="space-y-4">
        {notifications.map((notif) => (
          <div
            key={notif.id}
            className="glass-card rounded-2xl p-4 sm:p-5 flex items-start gap-4 border-white/5 hover:border-red-500/20 transition-all duration-200"
          >
            <div
              className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${
                notif.type === 'MATCH'
                  ? 'bg-red-500/15 text-red-400'
                  : notif.type === 'ESCALATION'
                  ? 'bg-amber-500/15 text-amber-400'
                  : notif.type === 'DONATION'
                  ? 'bg-emerald-500/15 text-emerald-400'
                  : 'bg-blue-500/15 text-blue-400'
              }`}
            >
              {notif.type === 'MATCH' ? (
                <Heart className="w-5 h-5 fill-current" />
              ) : notif.type === 'ESCALATION' ? (
                <Zap className="w-5 h-5" />
              ) : (
                <Bell className="w-5 h-5" />
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex flex-wrap items-center justify-between gap-x-3 mb-1">
                <span className="font-semibold text-white text-sm">{titleFor(notif)}</span>
                <span className="text-xs text-gray-500 font-mono">{notif.timestamp}</span>
              </div>
              <p className="text-sm text-gray-400 leading-relaxed break-words">{messageFor(notif)}</p>
            </div>
          </div>
        ))}

        {/* Static intro card: always shown, never part of the live list so language switches cannot reset anything. */}
        {notifications.length === 0 ? (
          <EmptyState
            icon={<Bell className="w-10 h-10" />}
            title={t('notifications.streamInit')}
            description={t('notifications.streamInitDesc')}
          />
        ) : (
          <div className="glass-card rounded-2xl p-4 sm:p-5 flex items-start gap-4 border-white/5">
            <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 bg-blue-500/15 text-blue-400">
              <Bell className="w-5 h-5" />
            </div>
            <div className="flex-1 min-w-0">
              <span className="font-semibold text-white text-sm">{t('notifications.streamInit')}</span>
              <p className="text-sm text-gray-400 leading-relaxed mt-1">{t('notifications.streamInitDesc')}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
