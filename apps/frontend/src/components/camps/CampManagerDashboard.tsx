'use client';

import { useState, useEffect, useCallback } from 'react';
import { Calendar, Plus, Clock, MapPin, Share2, RefreshCw } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { Button } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { Skeleton } from '@/components/ui/Skeleton';
import { Alert } from '@/components/pub/Alert';
import { TIME_FORMAT, toDate, toTimeOfDay } from '@/components/pub/dates';
import { parseApiError } from '@/components/pub/apiErrors';
import SlotManager from '@/components/slots/SlotManager';
import CampPostGenerator from './CampPostGenerator';
import CreateCampModal from './CreateCampModal';

interface DonationCampDto {
  id: number;
  name: string;
  district: string;
  location: string;
  latitude: number;
  longitude: number;
  scheduledDate: string;
  startTime: string;
  endTime: string;
  status: string;
  organizerName: string;
  requiredBloodGroups: string;
}

export default function CampManagerDashboard() {
  const { user } = useAuthStore();
  const { t, format } = useI18n();
  const [camps, setCamps] = useState<DonationCampDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [activeCampForSlots, setActiveCampForSlots] = useState<number | null>(null);
  const [campToGeneratePost, setCampToGeneratePost] = useState<DonationCampDto | null>(null);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);

  const loadCamps = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get('/camps/my-camps');
      const body = res.data;
      const list = Array.isArray(body) ? body : body?.data;
      setCamps(Array.isArray(list) ? list : []);
    } catch (err: unknown) {
      setError(parseApiError(err, t, { fallback: t('campManager.loadFailed') }).message);
    } finally {
      setLoading(false);
    }
  }, [t]);

  // Depend on the primitive id (not the whole user object) to avoid refetch loops
  const userId = user?.id;
  useEffect(() => {
    if (userId) {
      loadCamps();
    }
  }, [userId, loadCamps]);

  if (!user) return null;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-3 font-display text-3xl text-foreground">
          <Calendar className="h-5 w-5 shrink-0 text-primary-text" aria-hidden="true" />
          {t('campManager.managedCamps')}
        </h2>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="secondary" size="sm" className="min-h-11" onClick={loadCamps} disabled={loading}>
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin motion-reduce:animate-none' : ''}`} aria-hidden="true" />
            {t('campManager.refresh')}
          </Button>
          <Button size="sm" className="min-h-11" onClick={() => setIsCreateModalOpen(true)}>
            <Plus className="h-4 w-4" aria-hidden="true" /> {t('campManager.createCamp')}
          </Button>
        </div>
      </div>

      {error && <Alert>{error}</Alert>}

      {loading ? (
        <div role="status" aria-busy="true" className="grid gap-5">
          {[0, 1].map((i) => (
            <div key={i} className="glass-card flex flex-col gap-4 rounded-2xl p-6" aria-hidden="true">
              <Skeleton className="h-7 w-2/3" />
              <Skeleton className="h-4 w-full max-w-md" />
              <Skeleton className="h-4 w-1/2 max-w-xs" />
            </div>
          ))}
          <span className="sr-only">{t('campManager.loadingCampaigns')}</span>
        </div>
      ) : camps.length === 0 ? (
        <div className="space-y-3 rounded-2xl border border-dashed border-line-strong bg-surface p-12 text-center">
          <Calendar className="mx-auto h-10 w-10 text-gray-500" aria-hidden="true" />
          <p className="font-display text-2xl text-foreground">{t('campManager.noCampsOrganized')}</p>
          <p className="mx-auto max-w-md text-sm text-gray-400">{t('campManager.noCampsDesc')}</p>
        </div>
      ) : (
        <div className="grid gap-5">
          {camps.map((camp) => (
            <div key={camp.id} className="glass-card flex flex-col overflow-hidden rounded-2xl">
              <div className="flex flex-col justify-between gap-6 p-6 md:flex-row md:items-center">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
                    <h3 className="font-display break-words text-2xl text-foreground">{camp.name}</h3>
                    <StatusBadge
                      value={camp.status}
                      label={t(`pub.campStatus.${camp.status}`) !== `pub.campStatus.${camp.status}` ? t(`pub.campStatus.${camp.status}`) : undefined}
                      tone={camp.status === 'SCHEDULED' ? 'success' : camp.status === 'CANCELLED' ? 'neutral' : 'info'}
                    />
                  </div>
                  <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-foreground">
                    <span className="flex items-center gap-2 font-mono text-[13px]">
                      <Calendar className="h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
                      {toDate(camp.scheduledDate) ? format.date(toDate(camp.scheduledDate) as Date) : String(camp.scheduledDate)}
                    </span>
                    <span className="flex items-center gap-2 font-mono text-[13px]">
                      <Clock className="h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
                      {toTimeOfDay(camp.startTime) && toTimeOfDay(camp.endTime)
                        ? `${format.dateTime(toTimeOfDay(camp.startTime) as Date, TIME_FORMAT)} – ${format.dateTime(toTimeOfDay(camp.endTime) as Date, TIME_FORMAT)}`
                        : `${camp.startTime} – ${camp.endTime}`}
                    </span>
                    <span className="flex min-w-0 items-center gap-2">
                      <MapPin className="h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
                      <span className="min-w-0 break-words">
                        {camp.location}, {camp.district}
                      </span>
                    </span>
                  </div>
                </div>

                <div className="flex flex-wrap gap-3">
                  <Button variant="secondary" size="sm" className="min-h-11" onClick={() => setCampToGeneratePost(camp)}>
                    <Share2 className="h-3.5 w-3.5" aria-hidden="true" />
                    {t('campManager.sharePoster')}
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    className="min-h-11"
                    aria-expanded={activeCampForSlots === camp.id}
                    onClick={() => setActiveCampForSlots(activeCampForSlots === camp.id ? null : camp.id)}
                  >
                    <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                    {activeCampForSlots === camp.id ? t('campManager.closeSlots') : t('campManager.manageSlots')}
                  </Button>
                </div>
              </div>

              {/* Collapsible slot manager */}
              {activeCampForSlots === camp.id && (
                <div className="border-t border-line bg-surface-raised p-6">
                  <h4 className="eyebrow mb-5 flex items-center gap-2">
                    <Clock className="h-3.5 w-3.5 text-primary-text" aria-hidden="true" /> {t('campManager.appointmentsWalkins')}
                  </h4>
                  <SlotManager hostType="camp" hostId={camp.id} />
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {campToGeneratePost && <CampPostGenerator camp={campToGeneratePost} onClose={() => setCampToGeneratePost(null)} />}

      <CreateCampModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        onSuccess={() => {
          setIsCreateModalOpen(false);
          loadCamps();
        }}
      />
    </div>
  );
}
