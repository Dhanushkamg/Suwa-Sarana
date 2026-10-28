'use client';

import { useState, useEffect, useCallback } from 'react';
import { Clock, Users, Loader2, CalendarCheck } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/pub/Alert';
import { TIME_FORMAT, toDate } from '@/components/pub/dates';
import { parseApiError } from '@/components/pub/apiErrors';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import { DonationSlot, SlotBooking } from '@/types';

interface SlotPickerProps {
  hostType: 'camp' | 'hospital';
  hostId: number;
  onBooked?: (booking: SlotBooking) => void;
}

export default function SlotPicker({ hostType, hostId, onBooked }: SlotPickerProps) {
  const { t, format } = useI18n();
  const formatTime = (v: unknown) => {
    const d = toDate(v);
    return d ? format.dateTime(d, TIME_FORMAT) : '';
  };
  const formatDate = (v: unknown) => {
    const d = toDate(v);
    return d ? format.date(d, { weekday: 'short', month: 'short', day: 'numeric' }) : '';
  };
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
      // Prefer our own translation: by error code, then by what the status means for booking, then the server's text
      const parsed = parseApiError(err, t, { fallback: t('slotPicker.bookFailed') });
      const byStatus: Record<number, string> = {
        401: t('slotPicker.signIn'),
        409: t('slotPicker.conflict'),
        422: t('slotPicker.ineligible'),
      };
      const message = parsed.localised ? parsed.message : (parsed.status !== undefined && byStatus[parsed.status]) || parsed.message;
      setFeedback({ slotId: slot.id, type: 'error', message });
    } finally {
      setBookingId(null);
    }
  };

  if (loading) {
    return (
      <div role="status" className="flex items-center gap-2 py-4 text-gray-400 text-sm">
        <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
        {t('slotPicker.loading')}
      </div>
    );
  }

  if (slots.length === 0) {
    return (
      <div className="py-4 text-center text-sm text-gray-400">
        <CalendarCheck className="w-6 h-6 mx-auto mb-2 opacity-60" aria-hidden="true" />
        {t('slotPicker.empty')}
      </div>
    );
  }

  return (
    <div className="mt-4 space-y-2.5">
      <p className="eyebrow mb-3">
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
            className={`rounded-xl border p-3.5 transition-colors duration-300 ${
              isFull || isCancelled
                ? 'border-line bg-transparent opacity-60'
                : 'border-line-strong bg-surface hover:border-primary/40'
            }`}
          >
            <div className="flex items-center justify-between gap-3 flex-wrap">
              {/* Time & date */}
              <div>
                <div className="flex items-start gap-2 text-sm font-medium text-foreground">
                  <Clock className="mt-0.5 h-3.5 w-3.5 shrink-0 text-gray-500" aria-hidden="true" />
                  <span className="min-w-0">
                    {formatDate(slot.startTime)} · <span className="font-mono text-[13px]">{formatTime(slot.startTime)} – {formatTime(slot.endTime)}</span>
                  </span>
                </div>
                <div className="mt-1.5 flex items-center gap-2 text-xs text-gray-400">
                  <Users className="h-3 w-3 shrink-0" aria-hidden="true" />
                  {isFull
                    ? t('slotPicker.fullyBooked')
                    : t('pub.spotsLeft', { left: slot.spotsLeft, capacity: slot.capacity })}
                </div>
              </div>

              {/* Capacity bar + action */}
              <div className="flex items-center gap-3">
                {/* Visual fill bar */}
                <div className="h-1 w-20 overflow-hidden rounded-full bg-foreground/10" aria-hidden="true">
                  <div
                    className={`h-full w-full origin-left rounded-full transition-transform duration-500 ${isFull ? 'bg-warning' : 'bg-success'}`}
                    style={{ transform: `scaleX(${Math.min(1, slot.capacity > 0 ? slot.bookedCount / slot.capacity : 0)})` }}
                  />
                </div>

                {isCancelled ? (
                  <span className="text-xs text-gray-400">
                    {slot.status === 'COMPLETED' ? t('slotPicker.completed') : t('slotPicker.cancelled')}
                  </span>
                ) : (
                  <Button
                    variant={isFull ? 'secondary' : 'primary'}
                    size="sm"
                    className="min-h-11"
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
              <Alert tone={slotFeedback.type} className="mt-3 px-3 py-2 text-xs">
                {slotFeedback.message}
              </Alert>
            )}
          </div>
        );
      })}
    </div>
  );
}
