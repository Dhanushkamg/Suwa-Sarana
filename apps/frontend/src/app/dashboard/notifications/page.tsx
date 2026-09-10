'use client';

import { useState, useEffect } from 'react';
import { Bell, Zap, Radio, ArrowLeft, Heart } from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import { API_BASE_URL } from '@/lib/constants';

interface NotificationItem {
  id: string;
  type: 'MATCH' | 'ESCALATION' | 'DONATION' | 'SYSTEM';
  title: string;
  message: string;
  timestamp: string;
}

export default function NotificationsPage() {
  const { t } = useI18n();
  const { user } = useAuthStore();
  const [connected, setConnected] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);

  useEffect(() => {
    // Initial activity stream baseline
    setNotifications([
      {
        id: 'init-1',
        type: 'SYSTEM',
        title: 'Notification Stream Initialized',
        message: 'Listening for real-time donor matches, emergency escalation alerts, and platform broadcasts.',
        timestamp: 'Active',
      },
    ]);

    if (typeof window === 'undefined') return;

    let eventSource: EventSource | null = null;
    try {
      eventSource = new EventSource(`${API_BASE_URL}/notifications/stream`);

      eventSource.onopen = () => {
        setConnected(true);
      };

      eventSource.addEventListener('NEW_MATCH', (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data);
          setNotifications((prev) => [
            {
              id: String(Date.now()) + Math.random(),
              type: 'MATCH',
              title: data.title || 'New Blood Match Dispatch',
              message: data.body || data.message || 'A new urgent blood request match has been dispatched to your account.',
              timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
            },
            ...prev,
          ]);
        } catch {
          // Plain message
        }
      });

      eventSource.addEventListener('ESCALATION', (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data);
          setNotifications((prev) => [
            {
              id: String(Date.now()) + Math.random(),
              type: 'ESCALATION',
              title: data.title || 'Search Radius Escalation',
              message: data.body || data.message || 'Geospatial search radius expanded for pending request.',
              timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
            },
            ...prev,
          ]);
        } catch {
          // Plain message
        }
      });

      eventSource.onmessage = (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data);
          setNotifications((prev) => [
            {
              id: String(Date.now()) + Math.random(),
              type: data.type || 'SYSTEM',
              title: data.title || 'Platform Notification',
              message: data.body || data.message || JSON.stringify(data),
              timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
            },
            ...prev,
          ]);
        } catch {
          // Handle text message
          if (event.data) {
            setNotifications((prev) => [
              {
                id: String(Date.now()) + Math.random(),
                type: 'SYSTEM',
                title: 'Platform Alert',
                message: event.data,
                timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
              },
              ...prev,
            ]);
          }
        }
      };

      eventSource.onerror = () => {
        setConnected(false);
      };
    } catch {
      setConnected(false);
    }

    return () => {
      eventSource?.close();
    };
  }, [t, user?.id]);

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
            {connected ? 'Live SSE Stream Connected' : 'SSE Disconnected (Auto-reconnecting)'}
          </span>
        </div>
      </div>

      <div className="space-y-4">
        {notifications.map((notif) => (
          <div
            key={notif.id}
            className="glass-card rounded-2xl p-5 flex items-start gap-4 border-white/5 hover:border-red-500/20 transition-all duration-200"
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
              <div className="flex items-center justify-between mb-1">
                <span className="font-semibold text-white text-sm">{notif.title}</span>
                <span className="text-xs text-gray-500 font-mono">{notif.timestamp}</span>
              </div>
              <p className="text-sm text-gray-400 leading-relaxed">{notif.message}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
