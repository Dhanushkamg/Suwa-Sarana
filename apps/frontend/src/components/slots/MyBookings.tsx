'use client';

import { useState, useEffect, useCallback } from 'react';
import { CalendarCheck, Clock, MapPin, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { Button } from '@/components/ui/Button';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { SlotBooking } from '@/types';

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString([], {
    weekday: 'short', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function getErrorMessage(err: unknown, fallback: string): string {
  const message = (err as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
  return typeof message === 'string' && message ? message : fallback;
}

const STATUS_CONFIG = {
  BOOKED:     { labelKey: 'myBookings.upcoming',   color: 'text-emerald-400', bg: 'bg-emerald-500/10 border-emerald-500/20' },
  CHECKED_IN: { labelKey: 'myBookings.checkedIn',  color: 'text-blue-400',    bg: 'bg-blue-500/10 border-blue-500/20' },
  NO_SHOW:    { labelKey: 'myBookings.noShow',     color: 'text-red-400',     bg: 'bg-red-500/10 border-red-500/20' },
  CANCELLED:  { labelKey: 'myBookings.cancelled',  color: 'text-gray-400',    bg: 'bg-white/5 border-white/10' },
  COMPLETED:  { labelKey: 'myBookings.completed',  color: 'text-purple-400',  bg: 'bg-purple-500/10 border-purple-500/20' },
};

type Feedback = { bookingId: number; message: string; type: 'success' | 'error' } | null;

interface BookingCardProps {
  booking: SlotBooking;
  feedback: Feedback;
  cancelling: number | null;
  onCancel: (booking: SlotBooking) => void;
}

function BookingCard({ booking, feedback, cancelling, onCancel }: BookingCardProps) {
  const { t } = useI18n();
  const config = STATUS_CONFIG[booking.status];
  const slotFeedback = feedback?.bookingId === booking.id ? feedback : null;

  return (
    <div className={`rounded-2xl border p-5 transition-all duration-200 ${config.bg}`}>
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="flex-1 min-w-0">
          {/* Host name */}
          <div className="flex items-center gap-2 mb-2">
            <MapPin className="w-3.5 h-3.5 text-red-400 flex-shrink-0" />
            <span className="font-bold text-white truncate">{booking.slot.hostName}</span>
            <span className={`px-2 py-0.5 text-xs font-semibold rounded-full border ${config.bg} ${config.color} flex-shrink-0`}>
              {t(config.labelKey)}
            </span>
          </div>

          {/* Time */}
          <div className="flex items-center gap-2 text-sm text-gray-300 flex-wrap">
            <Clock className="w-3.5 h-3.5 text-amber-400 flex-shrink-0" />
            <span>{formatDateTime(booking.slot.startTime)}</span>
            <span className="text-gray-500">→</span>
            <span className="text-gray-400">{new Date(booking.slot.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
          </div>

          {booking.checkedInAt && (
            <p className="text-xs text-blue-300 mt-1">
              {t('myBookings.checkedInAt', { time: new Date(booking.checkedInAt).toLocaleTimeString() })}
            </p>
          )}
        </div>

        {/* Actions */}
        {booking.status === 'BOOKED' && (
          <div className="flex flex-col gap-2 items-end">
            {/* Actual QR Code */}
            <div className="flex flex-col items-center">
              <div className="bg-white p-2 rounded-xl mb-1 shadow-lg shadow-emerald-500/10">
                <QRCodeSVG
                  value={JSON.stringify({ s: booking.slotId, b: booking.id })}
                  size={72}
                  level="M"
                  includeMargin={false}
                />
              </div>
              <span className="text-[10px] text-gray-400 font-mono tracking-wider mb-2">ID #{booking.id}</span>
            </div>
            <Button
              variant="danger"
              size="sm"
              loading={cancelling === booking.id}
              disabled={cancelling === booking.id}
              onClick={() => onCancel(booking)}
            >
              {t('common.cancel')}
            </Button>
          </div>
        )}
      </div>

      {/* Per-booking feedback */}
      {slotFeedback && (
        <div
          role={slotFeedback.type === 'error' ? 'alert' : 'status'}
          className={`flex items-start gap-2 mt-3 text-xs rounded-lg p-2 border ${
            slotFeedback.type === 'success'
              ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/20'
              : 'bg-red-500/10 text-red-300 border-red-500/20'
          }`}
        >
          {slotFeedback.type === 'success'
            ? <CheckCircle2 className="w-3.5 h-3.5 flex-shrink-0" />
            : <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />}
          {slotFeedback.message}
        </div>
      )}
    </div>
  );
}

export default function MyBookings() {
  const { t } = useI18n();
  const [bookings, setBookings] = useState<SlotBooking[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [cancelling, setCancelling] = useState<number | null>(null);
  const [feedback, setFeedback] = useState<Feedback>(null);

  const fetchBookings = useCallback(async () => {
    try {
      const res = await apiClient.get('/donors/me/bookings');
      setBookings(res.data.data ?? res.data);
      setLoadError(false);
    } catch (err) {
      console.error('Failed to load bookings', err);
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchBookings(); }, [fetchBookings]);

  const handleCancel = async (booking: SlotBooking) => {
    setFeedback(null);
    setCancelling(booking.id);
    try {
      await apiClient.delete(`/slots/${booking.slotId}/book`);
      setFeedback({ bookingId: booking.id, type: 'success', message: t('myBookings.cancelSuccess') });
      await fetchBookings();
    } catch (err: unknown) {
      setFeedback({
        bookingId: booking.id,
        type: 'error',
        message: getErrorMessage(err, t('myBookings.cancelFailed')),
      });
    } finally {
      setCancelling(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center gap-2 py-8 text-gray-400 text-sm">
        <Loader2 className="w-4 h-4 animate-spin" />
        {t('myBookings.loading')}
      </div>
    );
  }

  if (loadError && bookings.length === 0) {
    return (
      <div role="alert" className="text-center py-10 border border-red-500/20 rounded-2xl bg-red-500/5">
        <AlertCircle className="w-8 h-8 text-red-400 mx-auto mb-3" />
        <p className="text-gray-300 text-sm mb-4">{t('myBookings.loadFailed')}</p>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => {
            setLoading(true);
            fetchBookings();
          }}
        >
          {t('common.refresh')}
        </Button>
      </div>
    );
  }

  const upcoming = bookings.filter(b => b.status === 'BOOKED');
  const past     = bookings.filter(b => b.status !== 'BOOKED');

  if (bookings.length === 0) {
    return (
      <div className="text-center py-10 border border-white/5 rounded-2xl bg-white/[0.02]">
        <CalendarCheck className="w-10 h-10 text-gray-500 mx-auto mb-3 opacity-40" />
        <p className="text-white font-semibold mb-1">{t('myBookings.emptyTitle')}</p>
        <p className="text-gray-400 text-sm">{t('myBookings.emptyText')}</p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {upcoming.length > 0 && (
        <section>
          <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">
            {t('myBookings.upcomingCount', { count: upcoming.length })}
          </h2>
          <div className="space-y-3">
            {upcoming.map(b => (
              <BookingCard key={b.id} booking={b} feedback={feedback} cancelling={cancelling} onCancel={handleCancel} />
            ))}
          </div>
        </section>
      )}

      {past.length > 0 && (
        <section>
          <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-3">
            {t('myBookings.pastCount', { count: past.length })}
          </h2>
          <div className="space-y-3">
            {past.map(b => (
              <BookingCard key={b.id} booking={b} feedback={feedback} cancelling={cancelling} onCancel={handleCancel} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
