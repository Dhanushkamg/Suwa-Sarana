'use client';

import { useState, useEffect, useCallback } from 'react';
import { Plus, Users, Clock, AlertCircle, Loader2, RefreshCw, QrCode } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { DonationSlot } from '@/types';

function getErrorMessage(err: unknown, fallback: string): string {
  const message = (err as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
  return typeof message === 'string' && message ? message : fallback;
}

interface SlotManagerProps {
  hostType: 'camp' | 'hospital';
  hostId: number;
}

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString([], {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export default function SlotManager({ hostType, hostId }: SlotManagerProps) {
  const { t } = useI18n();
  const [slots, setSlots] = useState<DonationSlot[]>([]);
  const [loading, setLoading] = useState(true);
  const [feedback, setFeedback] = useState<string | null>(null);
  
  // Scanner state
  const [showScanner, setShowScanner] = useState(false);

  // Bulk create form state
  const [showBulkForm, setShowBulkForm] = useState(false);
  const [bulkForm, setBulkForm] = useState({
    overallStartTime: '',
    overallEndTime: '',
    intervalMinutes: 30,
    capacityPerSlot: 4,
  });
  const [creating, setCreating] = useState(false);

  const slotsEndpoint = hostType === 'camp'
    ? `/camps/${hostId}/slots`
    : `/hospitals/${hostId}/slots`;

  const fetchSlots = useCallback(async () => {
    try {
      const res = await apiClient.get(slotsEndpoint, { params: { openOnly: false } });
      setSlots(res.data.data ?? res.data);
    } catch {
      setFeedback(t('slotManager.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [slotsEndpoint, t]);

  useEffect(() => { fetchSlots(); }, [fetchSlots]);

  const handleCheckIn = useCallback(async (slotId: number, bookingId: number) => {
    setFeedback(null);
    try {
      await apiClient.post(`/slots/${slotId}/check-in/${bookingId}`);
      setFeedback(t('slotManager.checkInSuccess', { id: bookingId }));
      await fetchSlots();
    } catch (err: unknown) {
      setFeedback(getErrorMessage(err, t('slotManager.checkInFailed')));
    }
  }, [fetchSlots, t]);

  useEffect(() => {
    if (!showScanner) return;

    let cancelled = false;
    let cleared = false;
    let scanner: { render: (ok: (text: string) => void, err: (e: unknown) => void) => void; clear: () => Promise<void> } | null = null;

    // Stop the camera and remove the scanner UI exactly once
    const clearScanner = async () => {
      if (!scanner || cleared) return;
      cleared = true;
      try {
        await scanner.clear();
      } catch (e) {
        console.error(e);
      }
    };

    import('html5-qrcode').then(({ Html5QrcodeScanner }) => {
      // Modal was closed before the library finished loading: never start the camera
      if (cancelled) return;
      scanner = new Html5QrcodeScanner(
        'qr-reader',
        { fps: 10, qrbox: { width: 250, height: 250 } },
        /* verbose= */ false
      );

      scanner.render(
        (decodedText: string) => {
          try {
            const data = JSON.parse(decodedText);
            if (data.s && data.b) {
              void clearScanner();
              setShowScanner(false);
              void handleCheckIn(data.s, data.b);
            } else {
              setFeedback(t('slotManager.invalidQR'));
            }
          } catch {
            setFeedback(t('slotManager.unrecognizedQR'));
          }
        },
        () => {
          // Ignore frequent frame-level scan errors
        }
      );
    });

    return () => {
      cancelled = true;
      void clearScanner();
    };
  }, [showScanner, handleCheckIn, t]);

  const handleBulkCreate = async () => {
    setCreating(true);
    setFeedback(null);
    try {
      const endpoint = `${slotsEndpoint}/bulk`;
      const res = await apiClient.post(endpoint, bulkForm);
      const created: DonationSlot[] = res.data.data ?? res.data;
      setFeedback(t('slotManager.createdSlots', { count: created.length }));
      setShowBulkForm(false);
      await fetchSlots();
    } catch (err: unknown) {
      setFeedback(getErrorMessage(err, t('slotManager.createFailed')));
    } finally {
      setCreating(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center gap-2 py-8 text-gray-400 text-sm">
        <Loader2 className="w-4 h-4 animate-spin" />
        {t('slotManager.loadingSlots')}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <h2 className="text-lg font-bold text-white">{t('slotManager.slotManager')}</h2>
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="sm" onClick={fetchSlots} aria-label={t('common.refresh')}>
            <RefreshCw className="w-3.5 h-3.5" />
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setShowScanner(true)}>
            <QrCode className="w-3.5 h-3.5 mr-1" />
            {t('slotManager.scanQR')}
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setShowBulkForm(v => !v)}>
            <Plus className="w-3.5 h-3.5 mr-1" />
            {t('slotManager.addSlots')}
          </Button>
        </div>
      </div>

      {/* Global feedback */}
      {feedback && (
        <div className="flex items-center gap-2 text-sm p-3 rounded-xl bg-white/5 border border-white/10 text-gray-300">
          <AlertCircle className="w-4 h-4 flex-shrink-0 text-amber-400" />
          {feedback}
        </div>
      )}

      {/* Scanner Modal */}
      {showScanner && (
        <div role="dialog" aria-modal="true" aria-label={t('slotManager.scanDonorQR')} className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="bg-[#12121a] border border-white/10 rounded-2xl p-6 w-full max-w-md">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-bold text-white">{t('slotManager.scanDonorQR')}</h3>
              <Button variant="ghost" size="sm" onClick={() => setShowScanner(false)}>{t('common.close')}</Button>
            </div>
            
            {/* The ID matches what html5-qrcode targets */}
            <div id="qr-reader" className="w-full rounded-lg overflow-hidden border border-white/10 bg-black/50"></div>
            
            <p className="text-xs text-gray-400 mt-4 text-center">
              {t('slotManager.scanInstructions')}
            </p>
          </div>
        </div>
      )}

      {/* Bulk create form */}
      {showBulkForm && (
        <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-5 space-y-4">
          <h3 className="text-sm font-semibold text-white">{t('slotManager.bulkGenerate')}</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="bulkStart" className="block text-xs text-gray-400 mb-1">{t('slotManager.overallStart')}</label>
              <input
                id="bulkStart"
                type="datetime-local"
                value={bulkForm.overallStartTime}
                onChange={e => setBulkForm(f => ({ ...f, overallStartTime: e.target.value }))}
                className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-red-500/50"
              />
            </div>
            <div>
              <label htmlFor="bulkEnd" className="block text-xs text-gray-400 mb-1">{t('slotManager.overallEnd')}</label>
              <input
                id="bulkEnd"
                type="datetime-local"
                value={bulkForm.overallEndTime}
                onChange={e => setBulkForm(f => ({ ...f, overallEndTime: e.target.value }))}
                className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-red-500/50"
              />
            </div>
            <div>
              <label htmlFor="bulkInterval" className="block text-xs text-gray-400 mb-1">{t('slotManager.intervalMinutes')}</label>
              <input
                id="bulkInterval"
                type="number"
                min={5}
                value={bulkForm.intervalMinutes}
                onChange={e => setBulkForm(f => ({ ...f, intervalMinutes: Number(e.target.value) }))}
                className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-red-500/50"
              />
            </div>
            <div>
              <label htmlFor="bulkCapacity" className="block text-xs text-gray-400 mb-1">{t('slotManager.donorsPerSlot')}</label>
              <input
                id="bulkCapacity"
                type="number"
                min={1}
                value={bulkForm.capacityPerSlot}
                onChange={e => setBulkForm(f => ({ ...f, capacityPerSlot: Number(e.target.value) }))}
                className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-red-500/50"
              />
            </div>
          </div>
          <div className="flex gap-2 justify-end">
            <Button variant="ghost" size="sm" onClick={() => setShowBulkForm(false)}>{t('common.cancel')}</Button>
            <Button variant="primary" size="sm" loading={creating} onClick={handleBulkCreate}>
              {t('slotManager.generateSlots')}
            </Button>
          </div>
        </div>
      )}

      {/* Slot list */}
      {slots.length === 0 ? (
        <div className="text-center py-10 border border-white/5 rounded-2xl bg-white/[0.02] text-gray-400 text-sm">
          {t('slotManager.noSlotsCreated')}
        </div>
      ) : (
        <div className="space-y-3">
          {slots.map((slot) => {
            const fillPct = Math.min(100, (slot.bookedCount / slot.capacity) * 100);

            return (
              <div
                key={slot.id}
                className="rounded-2xl border border-white/10 bg-white/[0.03] overflow-hidden"
              >
                {/* Slot header row */}
                <div className="flex items-center justify-between gap-4 p-4 flex-wrap">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <Clock className="w-3.5 h-3.5 text-amber-400" />
                      <span className="text-sm font-semibold text-white">
                        {formatDateTime(slot.startTime)} – {new Date(slot.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                        slot.status === 'OPEN'      ? 'bg-emerald-500/15 text-emerald-400' :
                        slot.status === 'FULL'      ? 'bg-red-500/15 text-red-400' :
                        slot.status === 'COMPLETED' ? 'bg-purple-500/15 text-purple-400' :
                                                      'bg-gray-500/15 text-gray-400'
                      }`}>
                        {slot.status}
                      </span>
                    </div>
                    {/* Capacity bar */}
                    <div className="flex items-center gap-2 mt-2">
                      <div className="flex-1 h-1.5 rounded-full bg-white/10 overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all ${slot.status === 'FULL' ? 'bg-red-500' : 'bg-emerald-500'}`}
                          style={{ width: `${fillPct}%` }}
                        />
                      </div>
                      <span className="text-xs text-gray-400 flex-shrink-0">
                        <Users className="w-3 h-3 inline mr-1" />{slot.bookedCount}/{slot.capacity}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
