'use client';

import { useState, useEffect, useCallback } from 'react';
import { Calendar, Plus, Clock, MapPin, Share2, RefreshCw } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { Button } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { Alert } from '@/components/pub/Alert';
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
  const { t } = useI18n();
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
      setCamps(res.data);
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
      setError(typeof message === 'string' && message ? message : t('campManager.loadFailed'));
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
          <Calendar className="h-5 w-5 shrink-0 text-ember" aria-hidden="true" />
          {t('campManager.managedCamps')}
        </h2>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="secondary" size="sm" onClick={loadCamps} disabled={loading}>
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
            {t('campManager.refresh')}
          </Button>
          <Button size="sm" onClick={() => setIsCreateModalOpen(true)}>
            <Plus className="h-4 w-4" aria-hidden="true" /> {t('campManager.createCamp')}
          </Button>
        </div>
      </div>

      {error && <Alert>{error}</Alert>}

      {loading ? (
        <div role="status" className="glass-card rounded-2xl p-12 text-center text-gray-400">
          <div className="mx-auto mb-4 h-7 w-7 animate-spin rounded-full border-2 border-line-strong border-t-ember" aria-hidden="true" />
          <p className="text-sm">{t('campManager.loadingCampaigns')}</p>
        </div>
      ) : camps.length === 0 ? (
        <div className="space-y-3 rounded-2xl border border-dashed border-line-strong bg-foreground/[0.02] p-12 text-center">
          <Calendar className="mx-auto h-10 w-10 text-gray-600" aria-hidden="true" />
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
                      tone={camp.status === 'SCHEDULED' ? 'green' : camp.status === 'CANCELLED' ? 'red' : 'gray'}
                    />
                  </div>
                  <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-gray-300">
                    <span className="flex items-center gap-2 font-mono text-[13px]">
                      <Calendar className="h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
                      {new Date(camp.scheduledDate).toLocaleDateString()}
                    </span>
                    <span className="flex items-center gap-2 font-mono text-[13px]">
                      <Clock className="h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
                      {camp.startTime} – {camp.endTime}
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
                  <Button variant="secondary" size="sm" onClick={() => setCampToGeneratePost(camp)}>
                    <Share2 className="h-3.5 w-3.5" aria-hidden="true" />
                    {t('campManager.sharePoster')}
                  </Button>
                  <Button
                    variant={activeCampForSlots === camp.id ? 'primary' : 'secondary'}
                    size="sm"
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
                <div className="border-t border-line bg-background/60 p-6">
                  <h4 className="eyebrow mb-5 flex items-center gap-2">
                    <Clock className="h-3.5 w-3.5 text-ember" aria-hidden="true" /> {t('campManager.appointmentsWalkins')}
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
