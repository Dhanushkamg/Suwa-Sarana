'use client';

import { useState, useEffect } from 'react';
import { Bell, Zap, Radio, ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';

interface NotificationItem {
  id: string;
  type: 'MATCH' | 'ESCALATION' | 'DONATION';
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
    // Seed initial notifications demonstration
    setNotifications([
      {
        id: '1',
        type: 'MATCH',
        title: 'Emergency Blood Request',
        message: t('alerts.pushMatch', {
          bloodType: 'O+',
          hospitalName: 'National Hospital Colombo',
          distanceKm: '4.2',
        }),
        timestamp: 'Just now',
      },
      {
        id: '2',
        type: 'ESCALATION',
        title: 'Search Radius Escalated',
        message: t('alerts.escalationNotice', {
          requestId: '104',
          radiusKm: '15',
        }),
        timestamp: '12m ago',
      },
      {
        id: '3',
        type: 'DONATION',
        title: 'Donation Confirmed',
        message: t('alerts.donationConfirmed', {
          requestId: '98',
        }),
        timestamp: '2h ago',
      },
    ]);

    // Connect to SSE stream if available
    const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
    try {
      const eventSource = new EventSource(`${apiUrl}/notifications/stream?userId=${user?.id || 1}`);
      
      eventSource.onopen = () => {
        setConnected(true);
      };

      eventSource.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          setNotifications((prev) => [
            {
              id: String(Date.now()),
              type: data.type || 'MATCH',
              title: data.title || 'Live Match Alert',
              message: data.message || JSON.stringify(data),
              timestamp: 'Just now',
            },
            ...prev,
          ]);
        } catch {
          // Plain message
        }
      };

      eventSource.onerror = () => {
        setConnected(false);
      };

      return () => {
        eventSource.close();
      };
    } catch {
      // SSE not available or offline
    }
  }, [t, user?.id]);

  return (
    <div className="max-w-3xl mx-auto">
      <div className="mb-6 flex items-center justify-between">
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
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/5 border border-white/10 text-xs">
          <Radio className={`w-3.5 h-3.5 ${connected ? 'text-emerald-400 animate-pulse' : 'text-amber-400'}`} />
          <span className={connected ? 'text-emerald-400' : 'text-gray-400'}>
            {connected ? 'Live SSE Connected' : 'Simulated Feed'}
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
                  : 'bg-emerald-500/15 text-emerald-400'
              }`}
            >
              {notif.type === 'MATCH' ? (
                <Bell className="w-5 h-5" />
              ) : notif.type === 'ESCALATION' ? (
                <Zap className="w-5 h-5" />
              ) : (
                <Bell className="w-5 h-5" />
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between mb-1">
                <span className="font-semibold text-white text-sm">{notif.title}</span>
                <span className="text-xs text-gray-500">{notif.timestamp}</span>
              </div>
              <p className="text-sm text-gray-400 leading-relaxed">{notif.message}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
