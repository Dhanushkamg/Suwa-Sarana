'use client';

import { useState, useEffect, useCallback } from 'react';
import { Plus, Users, Clock, AlertCircle, Loader2, RefreshCw, QrCode } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { StatusBadge } from '@/components/ui/StatusBadge';
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
        <h2 className="font-display text-2xl text-foreground">{t('slotManager.slotManager')}</h2>
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
        <div role="status" className="flex items-start gap-2.5 rounded-xl border border-line bg-foreground/[0.03] p-3 text-sm text-gray-300">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-saffron" aria-hidden="true" />
          {feedback}
        </div>
      )}

      {/* Scanner Modal */}
      {showScanner && (
        <div role="dialog" aria-modal="true" aria-label={t('slotManager.scanDonorQR')} className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-md rounded-2xl border border-line-strong bg-surface p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-display text-2xl text-foreground">{t('slotManager.scanDonorQR')}</h3>
              <Button variant="ghost" size="sm" onClick={() => setShowScanner(false)}>{t('common.close')}</Button>
            </div>
            
            {/* The ID matches what html5-qrcode targets */}
            <div id="qr-reader" className="w-full overflow-hidden rounded-lg border border-line bg-background"></div>
            
            <p className="mt-4 text-center text-xs text-gray-400">
              {t('slotManager.scanInstructions')}
            </p>
          </div>
        </div>
      )}

      {/* Bulk create form */}
      {showBulkForm && (
        <div className="space-y-5 rounded-2xl border border-line-strong bg-foreground/[0.03] p-5">
          <h3 className="eyebrow">{t('slotManager.bulkGenerate')}</h3>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input
              id="bulkStart"
              type="datetime-local"
              label={t('slotManager.overallStart')}
              value={bulkForm.overallStartTime}
              onChange={e => setBulkForm(f => ({ ...f, overallStartTime: e.target.value }))}
            />
            <Input
              id="bulkEnd"
              type="datetime-local"
              label={t('slotManager.overallEnd')}
              value={bulkForm.overallEndTime}
              onChange={e => setBulkForm(f => ({ ...f, overallEndTime: e.target.value }))}
            />
            <Input
              id="bulkInterval"
              type="number"
              min={5}
              label={t('slotManager.intervalMinutes')}
              value={bulkForm.intervalMinutes}
              onChange={e => setBulkForm(f => ({ ...f, intervalMinutes: Number(e.target.value) }))}
            />
            <Input
              id="bulkCapacity"
              type="number"
              min={1}
              label={t('slotManager.donorsPerSlot')}
              value={bulkForm.capacityPerSlot}
              onChange={e => setBulkForm(f => ({ ...f, capacityPerSlot: Number(e.target.value) }))}
            />
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => setShowBulkForm(false)}>{t('common.cancel')}</Button>
            <Button variant="primary" size="sm" loading={creating} onClick={handleBulkCreate}>
              {t('slotManager.generateSlots')}
            </Button>
          </div>
        </div>
      )}

      {/* Slot list */}
      {slots.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-line-strong bg-foreground/[0.02] px-6 py-10 text-center text-sm text-gray-400">
          {t('slotManager.noSlotsCreated')}
        </div>
      ) : (
        <div className="space-y-3">
          {slots.map((slot) => {
            const fillPct = Math.min(100, (slot.bookedCount / slot.capacity) * 100);

            return (
              <div
                key={slot.id}
                className="overflow-hidden rounded-2xl border border-line bg-foreground/[0.03]"
              >
                {/* Slot header row */}
                <div className="flex flex-wrap items-center justify-between gap-4 p-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
                      <Clock className="h-3.5 w-3.5 shrink-0 text-gray-500" aria-hidden="true" />
                      <span className="font-mono text-[13px] text-foreground">
                        {formatDateTime(slot.startTime)} – {new Date(slot.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                      <StatusBadge
                        value={slot.status}
                        tone={slot.status === 'OPEN' ? 'green' : slot.status === 'FULL' ? 'red' : 'gray'}
                      />
                    </div>
                    {/* Capacity bar */}
                    <div className="mt-3 flex items-center gap-3">
                      <div className="h-1 flex-1 overflow-hidden rounded-full bg-foreground/10" aria-hidden="true">
                        <div
                          className={`h-full w-full origin-left rounded-full transition-transform duration-500 ${slot.status === 'FULL' ? 'bg-ember' : 'bg-emerald-400'}`}
                          style={{ transform: `scaleX(${fillPct / 100})` }}
                        />
                      </div>
                      <span className="flex-shrink-0 font-mono text-xs text-gray-400">
                        <Users className="mr-1 inline h-3 w-3" aria-hidden="true" />{slot.bookedCount}/{slot.capacity}
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
