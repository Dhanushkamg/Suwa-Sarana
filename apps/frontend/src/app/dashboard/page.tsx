'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import { Heart, Droplets, Bell, ArrowRight, Plus, ListOrdered } from 'lucide-react';
import Link from 'next/link';

export default function DashboardPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const { t } = useI18n();

  useEffect(() => {
    if (user?.role === 'DONOR') {
      router.replace('/dashboard/donor');
    } else if (user?.role === 'ADMIN') {
      router.replace('/dashboard/admin');
    } else if (user?.role === 'HOSPITAL_REQUESTER') {
      router.replace('/dashboard/hospital');
    }
  }, [user, router]);

  return (
    <div>
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-white">
          {t('dashboard.welcome')}
          <span className="gradient-text ml-2">👋</span>
        </h1>
        <p className="text-gray-400 mt-2">
          {t('dashboard.requesterSubtitle')}
        </p>
      </div>

      {/* Quick actions for Requester */}
      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
        <Link
          href="/dashboard/requests/new"
          className="glass-card rounded-2xl p-6 flex flex-col gap-4 transition-all duration-300 hover:-translate-y-1 hover:border-red-500/20 group"
        >
          <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg flex-shrink-0 group-hover:scale-110 transition-transform duration-300">
            <Plus className="w-5 h-5 text-white" />
          </div>
          <div>
            <div className="font-semibold text-white mb-1">{t('dashboard.newBloodRequest')}</div>
            <div className="text-sm text-gray-500">{t('dashboard.newBloodRequestDesc')}</div>
          </div>
        </Link>

        <Link
          href="/dashboard/requests"
          className="glass-card rounded-2xl p-6 flex flex-col gap-4 transition-all duration-300 hover:-translate-y-1 hover:border-red-500/20 group"
        >
          <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-orange-500 to-red-500 flex items-center justify-center shadow-lg flex-shrink-0 group-hover:scale-110 transition-transform duration-300">
            <ListOrdered className="w-5 h-5 text-white" />
          </div>
          <div>
            <div className="font-semibold text-white mb-1">{t('dashboard.activeRequests')}</div>
            <div className="text-sm text-gray-500">{t('dashboard.activeRequestsDesc')}</div>
          </div>
        </Link>

        <Link
          href="/dashboard/notifications"
          className="glass-card rounded-2xl p-6 flex flex-col gap-4 transition-all duration-300 hover:-translate-y-1 hover:border-red-500/20 group"
        >
          <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-purple-500 to-indigo-600 flex items-center justify-center shadow-lg flex-shrink-0 group-hover:scale-110 transition-transform duration-300">
            <Bell className="w-5 h-5 text-white" />
          </div>
          <div>
            <div className="font-semibold text-white mb-1">{t('dashboard.notifications')}</div>
            <div className="text-sm text-gray-500">{t('dashboard.notificationsDesc')}</div>
          </div>
        </Link>
      </div>

      {/* Info card */}
      <div className="glass-card rounded-2xl p-6 border-red-500/10 relative overflow-hidden">
        <div className="absolute top-0 right-0 w-48 h-48 bg-red-500/5 rounded-full blur-2xl pointer-events-none" />
        <div className="relative flex items-start gap-4">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/25 flex-shrink-0">
            <Heart className="w-6 h-6 text-white fill-white" />
          </div>
          <div className="flex-1">
            <h3 className="font-semibold text-white mb-1">
              {t('dashboard.platformStatus')}
            </h3>
            <p className="text-sm text-gray-400 leading-relaxed">
              {t('dashboard.platformDesc')}
            </p>
          </div>
          <Link
            href="/dashboard/requests/new"
            className="flex-shrink-0 flex items-center gap-1.5 text-sm font-medium text-red-400 hover:text-red-300 transition-colors"
          >
            {t('dashboard.newRequest')}
            <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      </div>
    </div>
  );
}
