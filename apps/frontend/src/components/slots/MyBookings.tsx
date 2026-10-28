'use client';

import { useState, useEffect, useCallback } from 'react';
import { CalendarCheck, CalendarClock, CheckCircle2, Clock, MapPin, AlertCircle, Loader2, XCircle, Ban, type LucideIcon } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/pub/Alert';
import { TIME_FORMAT, toDate } from '@/components/pub/dates';
import { parseApiError } from '@/components/pub/apiErrors';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { SlotBooking } from '@/types';

/** Status = icon + text + colour. */
const STATUS_CONFIG: Record<SlotBooking['status'], { labelKey: string; Icon: LucideIcon; color: string; bg: string }> = {
  BOOKED:     { labelKey: 'myBookings.upcoming',   Icon: CalendarClock, color: 'text-info-text',    bg: 'bg-info-soft border-info/30' },
  CHECKED_IN: { labelKey: 'myBookings.checkedIn',  Icon: CheckCircle2,  color: 'text-success-text', bg: 'bg-success-soft border-success/30' },
  NO_SHOW:    { labelKey: 'myBookings.noShow',     Icon: XCircle,       color: 'text-danger-text',  bg: 'bg-danger-soft border-danger/30' },
  CANCELLED:  { labelKey: 'myBookings.cancelled',  Icon: Ban,           color: 'text-muted',        bg: 'bg-surface border-line-strong' },
  COMPLETED:  { labelKey: 'myBookings.completed',  Icon: CheckCircle2,  color: 'text-success-text', bg: 'bg-surface border-line-strong' },
};

type Feedback = { bookingId: number; message: string; type: 'success' | 'error' } | null;

interface BookingCardProps {
  booking: SlotBooking;
  feedback: Feedback;
  cancelling: number | null;
  onCancel: (booking: SlotBooking) => void;
}

function BookingCard({ booking, feedback, cancelling, onCancel }: BookingCardProps) {
  const { t, format } = useI18n();
  const config = STATUS_CONFIG[booking.status];
  const start = toDate(booking.slot.startTime);
  const end = toDate(booking.slot.endTime);
  const checkedIn = toDate(booking.checkedInAt);
  const slotFeedback = feedback?.bookingId === booking.id ? feedback : null;

  return (
    <div className={`rounded-2xl border p-5 transition-colors duration-300 ${config.bg}`}>
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="flex-1 min-w-0">
          {/* Host name */}
          <div className="mb-3 flex flex-wrap items-center gap-x-3 gap-y-1.5">
            <span className="flex min-w-0 items-center gap-2">
              <MapPin className="h-3.5 w-3.5 shrink-0 text-primary-text" aria-hidden="true" />
              <span className="break-words font-display text-xl text-foreground">{booking.slot.hostName}</span>
            </span>
            <span className={`inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold ${config.bg} ${config.color}`}>
              <config.Icon className="h-3.5 w-3.5" aria-hidden="true" />
              {t(config.labelKey)}
            </span>
          </div>

          {/* Time */}
          <div className="flex flex-wrap items-center gap-2 font-mono text-[13px] text-foreground">
            <Clock className="h-3.5 w-3.5 shrink-0 text-gray-500" aria-hidden="true" />
            <span>{start ? format.dateTime(start, { weekday: 'short', month: 'short', day: 'numeric', ...TIME_FORMAT }) : ''}</span>
            <span className="text-gray-500" aria-hidden="true">→</span>
            <span className="text-gray-400">{end ? format.dateTime(end, TIME_FORMAT) : ''}</span>
          </div>

          {checkedIn && (
            <p className="mt-1.5 text-xs text-success-text">
              {t('myBookings.checkedInAt', { time: format.dateTime(checkedIn, { timeStyle: 'short' }) })}
            </p>
          )}
        </div>

        {/* Actions */}
        {booking.status === 'BOOKED' && (
          <div className="flex flex-col gap-2 items-end">
            {/* Actual QR Code */}
            <div className="flex flex-col items-center">
              {/* The QR plate stays white in both themes on purpose: scanners need dark-on-light contrast */}
              <div className="mb-1 rounded-xl bg-white p-2">
                <QRCodeSVG
                  value={JSON.stringify({ s: booking.slotId, b: booking.id })}
                  size={72}
                  level="M"
                  includeMargin={false}
                />
              </div>
              <span className="mb-2 font-mono text-xs tracking-wider text-gray-400">ID #{booking.id}</span>
            </div>
            <Button
              variant="danger"
              size="sm"
              className="min-h-11"
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
        <Alert tone={slotFeedback.type} className="mt-3 px-3 py-2 text-xs">
          {slotFeedback.message}
        </Alert>
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
      const body = res.data.data ?? res.data;
      setBookings(Array.isArray(body) ? body : []);
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
        message: parseApiError(err, t, { fallback: t('myBookings.cancelFailed') }).message,
      });
    } finally {
      setCancelling(null);
    }
  };

  if (loading) {
    return (
      <div role="status" className="flex items-center gap-2 py-8 text-gray-400 text-sm">
        <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
        {t('myBookings.loading')}
      </div>
    );
  }

  if (loadError && bookings.length === 0) {
    return (
      <div role="alert" className="rounded-2xl border border-danger/30 bg-danger-soft py-10 text-center">
        <AlertCircle className="mx-auto mb-3 h-8 w-8 text-danger-text" aria-hidden="true" />
        <p className="text-foreground text-sm mb-4">{t('myBookings.loadFailed')}</p>
        <Button
          variant="secondary"
          size="sm"
          className="min-h-11"
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
      <div className="rounded-2xl border border-dashed border-line-strong bg-surface px-6 py-12 text-center">
        <CalendarCheck className="mx-auto mb-4 h-9 w-9 text-gray-500" aria-hidden="true" />
        <p className="mb-1 font-display text-2xl text-foreground">{t('myBookings.emptyTitle')}</p>
        <p className="text-gray-400 text-sm">{t('myBookings.emptyText')}</p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {upcoming.length > 0 && (
        <section>
          <h2 className="eyebrow mb-4">
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
          <h2 className="eyebrow mb-4">
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
