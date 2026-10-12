'use client';

import { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import apiClient from '@/lib/apiClient';
import { Calendar, MapPin, Clock, Heart, ArrowLeft, Droplets, AlertCircle } from 'lucide-react';
import SlotPicker from '@/components/slots/SlotPicker';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { EmptyState } from '@/components/ui/EmptyState';
import { Button } from '@/components/ui/Button';

interface Camp {
  id: number;
  name: string;
  district: string;
  location: string;
  scheduledDate: string;
  startTime: string;
  endTime: string;
  organizerName: string;
  requiredBloodGroups: string;
}

export default function CampsPage() {
  const { t } = useI18n();
  const { user } = useAuthStore();
  const [camps, setCamps] = useState<Camp[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [expandedCampId, setExpandedCampId] = useState<number | null>(null);

  // Depend on the primitive role (not the whole user object) to avoid refetch loops
  const role = user?.role;

  const fetchCamps = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const endpoint = role === 'DONOR' ? '/camps/recommended' : '/camps';
      const response = await apiClient.get(endpoint);
      setCamps(response.data);
    } catch (err) {
      console.error('Failed to load camps', err);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [role]);

  useEffect(() => {
    fetchCamps();
  }, [fetchCamps]);

  return (
    <div className="min-h-screen bg-[#0d0d14] text-white">
      {/* Header */}
      <header className="border-b border-white/5 bg-[#0d0d14]/80 backdrop-blur-xl sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 sm:gap-4 min-w-0">
            <LanguageSwitcher />
            <Link
              href="/"
              className="flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors whitespace-nowrap"
            >
              <ArrowLeft className="w-4 h-4" /> {t('common.backToHome')}
            </Link>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/30">
              <Heart className="w-4 h-4 text-white fill-white" />
            </div>
            <span className="font-bold text-white hidden sm:inline">Suwa Sarana</span>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 py-12">
        <div className="mb-10 text-center max-w-2xl mx-auto">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-red-500/10 border border-red-500/20 text-red-400 text-xs font-semibold mb-4">
            <Calendar className="w-3.5 h-3.5" />
            {t('camps.communityDrives')}
          </div>
          <h1 className="text-3xl sm:text-4xl md:text-5xl font-extrabold tracking-tight text-white mb-4">
            {user?.role === 'DONOR' ? t('camps.recommendedCamps') : t('camps.upcomingCamps')}
          </h1>
          <p className="text-gray-400 text-sm md:text-base">
            {user?.role === 'DONOR' 
              ? t('camps.recommendedDesc')
              : t('camps.upcomingDesc')}
          </p>
        </div>


        {loading ? (
          <div className="flex justify-center items-center py-20 text-gray-400">
            <div className="w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin mr-3" />
            {t('camps.loadingCamps')}
          </div>
        ) : error ? (
          <div role="alert">
            <EmptyState
              icon={<AlertCircle className="w-12 h-12 text-red-400" />}
              title={t('camps.loadFailed')}
              description={t('camps.loadFailedDesc')}
              action={<Button variant="secondary" onClick={fetchCamps}>{t('common.refresh')}</Button>}
            />
          </div>
        ) : camps.length === 0 ? (
          <div className="text-center py-20 border border-white/5 rounded-2xl bg-white/[0.02]">
            <Calendar className="w-12 h-12 text-gray-500 mx-auto mb-4 opacity-50" />
            <h3 className="text-lg font-semibold text-white mb-2">{t('camps.noScheduledCamps')}</h3>
            <p className="text-gray-400 text-sm max-w-md mx-auto">
              {t('camps.noScheduledCampsDesc')}
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {camps.map((camp) => (
              <div
                key={camp.id}
                className="bg-white/[0.03] hover:bg-white/[0.05] border border-white/10 hover:border-red-500/30 rounded-2xl p-6 transition-all duration-300 flex flex-col"
              >
                <div>
                  <div className="flex items-start justify-between gap-4 mb-4">
                    <h3 className="text-xl font-bold text-white min-w-0 break-words">
                      {camp.name}
                    </h3>
                    <span className="px-2.5 py-1 text-xs font-semibold rounded-lg bg-red-500/10 text-red-400 border border-red-500/20 shrink-0">
                      {camp.district}
                    </span>
                  </div>

                  <div className="space-y-3 mb-4 text-sm text-gray-300">
                    <div className="flex items-center gap-3">
                      <Calendar className="w-4 h-4 text-red-400 flex-shrink-0" />
                      <span>{camp.scheduledDate}</span>
                    </div>
                    <div className="flex items-center gap-3">
                      <Clock className="w-4 h-4 text-amber-400 flex-shrink-0" />
                      <span>{camp.startTime} - {camp.endTime}</span>
                    </div>
                    <div className="flex items-center gap-3">
                      <MapPin className="w-4 h-4 text-emerald-400 flex-shrink-0" />
                      <span className="min-w-0 break-words">{camp.location}</span>
                    </div>
                    {camp.requiredBloodGroups && (
                      <div className="flex items-center gap-3">
                        <Droplets className="w-4 h-4 text-rose-400 flex-shrink-0" />
                        <span className="font-semibold text-rose-300">{t('camps.requires')} {camp.requiredBloodGroups}</span>
                      </div>
                    )}
                  </div>
                </div>

                {/* Slot booking toggle */}
                <button
                  type="button"
                  aria-expanded={expandedCampId === camp.id}
                  className="text-xs text-red-400 hover:text-red-300 transition-colors text-left mb-2 font-medium"
                  onClick={() => setExpandedCampId(expandedCampId === camp.id ? null : camp.id)}
                >
                  {expandedCampId === camp.id ? `▲ ${t('camps.hideSlots')}` : `▼ ${t('camps.showSlots')}`}
                </button>

                {expandedCampId === camp.id && (
                  <SlotPicker hostType="camp" hostId={camp.id} />
                )}
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
