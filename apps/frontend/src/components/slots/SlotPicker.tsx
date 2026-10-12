'use client';

import { useState, useEffect, useCallback } from 'react';
import { Clock, Users, CheckCircle2, AlertCircle, Loader2, CalendarCheck } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import { DonationSlot, SlotBooking } from '@/types';

interface SlotPickerProps {
  hostType: 'camp' | 'hospital';
  hostId: number;
  onBooked?: (booking: SlotBooking) => void;
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
}

export default function SlotPicker({ hostType, hostId, onBooked }: SlotPickerProps) {
  const { t } = useI18n();
  const { user } = useAuthStore();
  const [slots, setSlots] = useState<DonationSlot[]>([]);
  const [loading, setLoading] = useState(true);
  const [bookingId, setBookingId] = useState<number | null>(null);
  const [feedback, setFeedback] = useState<{ slotId: number; type: 'success' | 'error'; message: string } | null>(null);

  const endpoint = hostType === 'camp'
    ? `/camps/${hostId}/slots`
    : `/hospitals/${hostId}/slots`;

  const fetchSlots = useCallback(async () => {
    try {
      const res = await apiClient.get(endpoint, { params: { openOnly: false } });
      // Backend wraps in ApiResponse<List<DonationSlotDto>>
      setSlots(res.data.data ?? res.data);
    } catch {
      // Silently fail on background poll; initial load shows the spinner
    } finally {
      setLoading(false);
    }
  }, [endpoint]);

  // Initial load
  useEffect(() => { fetchSlots(); }, [fetchSlots]);

  // Poll every 15 seconds — SSE is per-user per-node, polling is safer for shared capacity display
  useEffect(() => {
    const interval = setInterval(fetchSlots, 15_000);
    return () => clearInterval(interval);
  }, [fetchSlots]);

  const handleBook = async (slot: DonationSlot) => {
    if (!user || user.role !== 'DONOR') {
      setFeedback({ slotId: slot.id, type: 'error', message: t('slotPicker.signInAsDonor') });
      return;
    }

    setFeedback(null);
    setBookingId(slot.id);

    try {
      const res = await apiClient.post(`/slots/${slot.id}/book`);
      const booking: SlotBooking = res.data.data ?? res.data;
      setFeedback({ slotId: slot.id, type: 'success', message: t('slotPicker.booked') });
      onBooked?.(booking);
      await fetchSlots(); // refresh capacity immediately
    } catch (err: unknown) {
      const resp = (err as { response?: { status?: number; data?: { message?: unknown } } })?.response;
      const status = resp?.status;
      const msg = typeof resp?.data?.message === 'string' ? resp.data.message : undefined;

      if (status === 409) {
        setFeedback({ slotId: slot.id, type: 'error', message: msg || t('slotPicker.conflict') });
      } else if (status === 422) {
        setFeedback({ slotId: slot.id, type: 'error', message: msg || t('slotPicker.ineligible') });
      } else if (status === 401) {
        setFeedback({ slotId: slot.id, type: 'error', message: t('slotPicker.signIn') });
      } else {
        setFeedback({ slotId: slot.id, type: 'error', message: msg || t('slotPicker.bookFailed') });
      }
    } finally {
      setBookingId(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center gap-2 py-4 text-gray-400 text-sm">
        <Loader2 className="w-4 h-4 animate-spin" />
        {t('slotPicker.loading')}
      </div>
    );
  }

  if (slots.length === 0) {
    return (
      <div className="py-4 text-center text-sm text-gray-500">
        <CalendarCheck className="w-6 h-6 mx-auto mb-2 opacity-40" />
        {t('slotPicker.empty')}
      </div>
    );
  }

  return (
    <div className="space-y-2 mt-4">
      <p className="text-xs text-gray-400 font-medium uppercase tracking-wider mb-3">
        {t('slotPicker.available')}
      </p>

      {slots.map((slot) => {
        const isFull = slot.status === 'FULL' || slot.spotsLeft <= 0;
        const isCancelled = slot.status === 'CANCELLED' || slot.status === 'COMPLETED';
        const isBooking = bookingId === slot.id;
        const slotFeedback = feedback?.slotId === slot.id ? feedback : null;

        return (
          <div
            key={slot.id}
            className={`rounded-xl border p-3 transition-all duration-200 ${
              isFull || isCancelled
                ? 'border-white/5 bg-white/[0.01] opacity-50'
                : 'border-white/10 bg-white/[0.03] hover:border-red-500/20'
            }`}
          >
            <div className="flex items-center justify-between gap-3 flex-wrap">
              {/* Time & date */}
              <div>
                <div className="flex items-center gap-1.5 text-sm font-semibold text-white">
                  <Clock className="w-3.5 h-3.5 text-amber-400" />
                  {formatDate(slot.startTime)} · {formatTime(slot.startTime)} – {formatTime(slot.endTime)}
                </div>
                <div className="flex items-center gap-1.5 mt-1 text-xs text-gray-400">
                  <Users className="w-3 h-3" />
                  {isFull
                    ? t('slotPicker.fullyBooked')
                    : t(slot.spotsLeft !== 1 ? 'slotPicker.spotsLeft' : 'slotPicker.spotLeft', { left: slot.spotsLeft, capacity: slot.capacity })}
                </div>
              </div>

              {/* Capacity bar + action */}
              <div className="flex items-center gap-3">
                {/* Visual fill bar */}
                <div className="w-20 h-1.5 rounded-full bg-white/10 overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all ${isFull ? 'bg-red-500' : 'bg-emerald-500'}`}
                    style={{ width: `${Math.min(100, (slot.bookedCount / slot.capacity) * 100)}%` }}
                  />
                </div>

                {isCancelled ? (
                  <span className="text-xs text-gray-500">
                    {slot.status === 'COMPLETED' ? t('slotPicker.completed') : t('slotPicker.cancelled')}
                  </span>
                ) : (
                  <Button
                    variant={isFull ? 'secondary' : 'primary'}
                    size="sm"
                    disabled={isFull || isBooking}
                    loading={isBooking}
                    onClick={() => handleBook(slot)}
                  >
                    {isFull ? t('slotPicker.full') : t('slotPicker.book')}
                  </Button>
                )}
              </div>
            </div>

            {/* Per-slot feedback */}
            {slotFeedback && (
              <div role={slotFeedback.type === 'error' ? 'alert' : 'status'} className={`flex items-start gap-2 mt-2 text-xs rounded-lg p-2 ${
                slotFeedback.type === 'success'
                  ? 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/20'
                  : 'bg-red-500/10 text-red-300 border border-red-500/20'
              }`}>
                {slotFeedback.type === 'success'
                  ? <CheckCircle2 className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
                  : <AlertCircle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />}
                {slotFeedback.message}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
