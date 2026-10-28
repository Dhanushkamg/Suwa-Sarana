'use client';

import { useState, useEffect, useCallback } from 'react';
import { Plus, Users, Clock, AlertCircle, Loader2, RefreshCw, QrCode } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { Dialog } from '@/components/ui/Dialog';
import { TIME_FORMAT, toDate } from '@/components/pub/dates';
import { parseApiError } from '@/components/pub/apiErrors';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { DonationSlot } from '@/types';

interface SlotManagerProps {
  hostType: 'camp' | 'hospital';
  hostId: number;
}

export default function SlotManager({ hostType, hostId }: SlotManagerProps) {
  const { t, format } = useI18n();
  const message = (err: unknown, fallback: string) => parseApiError(err, t, { fallback }).message;
  const formatDateTime = (v: unknown) => {
    const d = toDate(v);
    return d ? format.dateTime(d, { month: 'short', day: 'numeric', ...TIME_FORMAT }) : '';
  };
  const formatTime = (v: unknown) => {
    const d = toDate(v);
    return d ? format.dateTime(d, TIME_FORMAT) : '';
  };
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
      setFeedback(parseApiError(err, t, { fallback: t('slotManager.checkInFailed') }).message);
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
      setFeedback(message(err, t('slotManager.createFailed')));
    } finally {
      setCreating(false);
    }
  };

  if (loading) {
    return (
      <div role="status" className="flex items-center gap-2 py-8 text-gray-400 text-sm">
        <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
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
          <Button variant="ghost" size="sm" className="min-h-11 min-w-11" onClick={fetchSlots} aria-label={t('common.refresh')}>
            <RefreshCw className="w-3.5 h-3.5" aria-hidden="true" />
          </Button>
          <Button variant="secondary" size="sm" className="min-h-11" onClick={() => setShowScanner(true)}>
            <QrCode className="w-3.5 h-3.5" aria-hidden="true" />
            {t('slotManager.scanQR')}
          </Button>
          <Button variant="secondary" size="sm" className="min-h-11" aria-expanded={showBulkForm} onClick={() => setShowBulkForm(v => !v)}>
            <Plus className="w-3.5 h-3.5" aria-hidden="true" />
            {t('slotManager.addSlots')}
          </Button>
        </div>
      </div>

      {/* Global feedback */}
      {feedback && (
        <div role="status" className="flex items-start gap-2.5 rounded-xl border border-line bg-surface-raised p-3 text-sm text-foreground">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-warning-text" aria-hidden="true" />
          {feedback}
        </div>
      )}

      {/* Scanner dialog: native <dialog> gives focus trap, Esc and focus return */}
      <Dialog open={showScanner} onClose={() => setShowScanner(false)} title={t('slotManager.scanDonorQR')} description={t('slotManager.scanInstructions')}>
        {/* The ID matches what html5-qrcode targets; rendered only while open so the camera never starts early */}
        {showScanner && <div id="qr-reader" className="w-full overflow-hidden rounded-lg border border-line bg-background"></div>}
      </Dialog>

      {/* Bulk create form */}
      {showBulkForm && (
        <div className="space-y-5 rounded-2xl border border-line-strong bg-surface p-5">
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
            <Button variant="ghost" size="sm" className="min-h-11" onClick={() => setShowBulkForm(false)}>{t('common.cancel')}</Button>
            <Button variant="primary" size="sm" className="min-h-11" loading={creating} onClick={handleBulkCreate}>
              {t('slotManager.generateSlots')}
            </Button>
          </div>
        </div>
      )}

      {/* Slot list */}
      {slots.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-line-strong bg-surface px-6 py-10 text-center text-sm text-gray-400">
          {t('slotManager.noSlotsCreated')}
        </div>
      ) : (
        <div className="space-y-3">
          {slots.map((slot) => {
            const fillPct = slot.capacity > 0 ? Math.min(100, (slot.bookedCount / slot.capacity) * 100) : 0;
            const statusKey = `pub.slotStatus.${slot.status}`;
            const statusLabel = t(statusKey) !== statusKey ? t(statusKey) : undefined;

            return (
              <div
                key={slot.id}
                className="overflow-hidden rounded-2xl border border-line bg-surface"
              >
                {/* Slot header row */}
                <div className="flex flex-wrap items-center justify-between gap-4 p-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
                      <Clock className="h-3.5 w-3.5 shrink-0 text-gray-500" aria-hidden="true" />
                      <span className="font-mono text-[13px] text-foreground">
                        {formatDateTime(slot.startTime)} – {formatTime(slot.endTime)}
                      </span>
                      <StatusBadge
                        value={slot.status}
                        label={statusLabel}
                        tone={slot.status === 'OPEN' ? 'success' : slot.status === 'FULL' ? 'warning' : 'neutral'}
                      />
                    </div>
                    {/* Capacity bar */}
                    <div className="mt-3 flex items-center gap-3">
                      <div className="h-1 flex-1 overflow-hidden rounded-full bg-foreground/10" aria-hidden="true">
                        <div
                          className={`h-full w-full origin-left rounded-full transition-transform duration-500 ${slot.status === 'FULL' ? 'bg-warning' : 'bg-success'}`}
                          style={{ transform: `scaleX(${fillPct / 100})` }}
                        />
                      </div>
                      <span className="flex-shrink-0 font-mono text-xs text-gray-400">
                        <Users className="me-1 inline h-3 w-3" aria-hidden="true" />{format.number(slot.bookedCount)}/{format.number(slot.capacity)}
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
