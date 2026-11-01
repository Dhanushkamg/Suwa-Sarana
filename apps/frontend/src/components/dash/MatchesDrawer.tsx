'use client';

import { useCallback, useEffect, useState } from 'react';
import { Check, Phone, Users, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useToast } from '@/components/ui/Toast';
import { useI18n } from '@/lib/i18n';
import { apiMessage, isEndpointMissing } from '@/lib/api/common';
import { fetchRequestMatches, recordDonation, type RequestMatch, type RequestMatches } from '@/lib/api/requests';
import { ConfirmDialog } from './ConfirmDialog';
import { BloodTypeTile } from './IconTile';
import { effectiveMatchStatus, matchStatusLabel, matchStatusTone } from './matchStatus';
import { Notice } from './Notice';
import { RowsSkeleton } from './Skeletons';

interface MatchesDrawerProps {
  /** Request whose matches to show; `null` keeps the drawer closed. */
  requestId: number | null;
  hospitalName?: string;
  onClose: () => void;
  /** Called after a donation outcome was recorded (the request's counters changed). */
  onChanged?: () => void;
}

/**
 * Side sheet listing the donors matched to one request (GET /api/requests/{id}/matches): first name, blood group, distance, status.
 * The phone number appears only while the donor ACCEPTED and no outcome is recorded; then the owner can record whether they donated.
 */
export function MatchesDrawer({ requestId, hospitalName, onClose, onChanged }: MatchesDrawerProps) {
  const { t, format } = useI18n();
  const toast = useToast();
  const [data, setData] = useState<RequestMatches | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [recording, setRecording] = useState<{ match: RequestMatch; donated: boolean } | null>(null);

  const load = useCallback(async () => {
    if (requestId === null) return;
    setLoading(true);
    setError(null);
    try {
      setData(await fetchRequestMatches(requestId));
    } catch (err) {
      setData(null);
      setError(isEndpointMissing(err) ? t('dashUi.matchesUnavailable') : apiMessage(err, t('dash.loadFailed')));
    } finally {
      setLoading(false);
    }
  }, [requestId, t]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (requestId !== null) load();
  }, [requestId, load]);

  const open = requestId !== null;
  const matches = data?.matches ?? [];

  const confirmRecording = async () => {
    if (!recording) return;
    await recordDonation(recording.match.id, recording.donated);
    toast.show(recording.donated ? t('dashUi.donationRecorded') : t('dashUi.noShowRecorded'), 'success');
    await load();
    onChanged?.();
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={t('dashUi.matchesTitle')}
      description={hospitalName ? t('dashUi.matchesFor', { hospital: hospitalName }) : undefined}
      className="m-0 ms-auto h-dvh max-h-none w-[min(100vw,28rem)] max-w-none rounded-none rounded-s-2xl"
    >
      {!open ? null : loading && !data ? (
        <RowsSkeleton rows={3} />
      ) : error ? (
        <Notice
          tone="error"
          action={
            <Button type="button" variant="secondary" onClick={load}>
              {t('dash.retry')}
            </Button>
          }
        >
          {error}
        </Notice>
      ) : data && matches.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-10 text-center">
          <Users className="h-8 w-8 text-gray-500" aria-hidden="true" />
          <p className="font-display text-xl text-foreground">{t('dashUi.noMatchesTitle')}</p>
          <p className="max-w-xs text-sm text-gray-400">{t('dashUi.noMatchesDesc')}</p>
        </div>
      ) : (
        <>
          {data && (
            <p role="status" className="mb-3 rounded-xl border border-line bg-surface-raised px-4 py-3 text-sm text-foreground">
              {t('dashUi.matchesSummary', { accepted: data.acceptedCount, confirmed: data.confirmedDonations, needed: data.unitsNeeded })}
            </p>
          )}
          <p className="mb-4 text-sm text-gray-400">{t('dashUi.matchesPrivacy')}</p>
          <ul className="space-y-3">
            {matches.map((m) => {
              const status = effectiveMatchStatus(m.status, m.donated);
              const canRecord = m.status === 'ACCEPTED' && m.donated === undefined;
              return (
                <li key={m.id} className="rounded-xl border border-line bg-surface-raised p-4">
                  <div className="flex items-start gap-3">
                    <BloodTypeTile type={m.bloodType || '?'} size="sm" />
                    <div className="min-w-0 flex-1">
                      <p className="break-words text-base font-medium text-foreground">{m.donorFirstName || t('dashUi.anonymousDonor')}</p>
                      <p className="mt-0.5 text-sm text-gray-400">
                        {m.distanceKm === null ? t('dashUi.distanceUnknown') : t('dashUi.distanceKm', { km: format.number(m.distanceKm, { maximumFractionDigits: 1 }) })}
                      </p>
                      {m.respondedAt && <p className="mt-0.5 text-sm text-gray-500">{t('dashUi.respondedAt', { date: format.dateTime(m.respondedAt, { dateStyle: 'short', timeStyle: 'short' }) })}</p>}
                    </div>
                    <StatusBadge value={status} label={matchStatusLabel(t, status)} tone={matchStatusTone(status)} />
                  </div>
                  {canRecord && m.contactPhone ? (
                    <a
                      href={`tel:${m.contactPhone}`}
                      className="mt-3 inline-flex min-h-11 items-center gap-2 rounded-lg text-sm font-medium text-primary-text hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                    >
                      <Phone className="h-4 w-4" aria-hidden="true" />
                      <span dir="ltr">{m.contactPhone}</span>
                    </a>
                  ) : (
                    <p className="mt-3 text-sm text-gray-500">{canRecord ? t('dashUi.contactAfterAccept') : m.status === 'ACCEPTED' ? t('dashUi.contactHidden') : t('dashUi.contactAfterAccept')}</p>
                  )}
                  {canRecord && (
                    <div className="mt-2 flex flex-wrap gap-2">
                      <Button type="button" size="sm" variant="primary" onClick={() => setRecording({ match: m, donated: true })}>
                        <Check className="h-4 w-4" aria-hidden="true" />
                        {t('dashUi.markDonated')}
                      </Button>
                      <Button type="button" size="sm" variant="secondary" onClick={() => setRecording({ match: m, donated: false })}>
                        <X className="h-4 w-4" aria-hidden="true" />
                        {t('dashUi.markNotDonated')}
                      </Button>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </>
      )}

      <ConfirmDialog
        open={recording !== null}
        onClose={() => setRecording(null)}
        title={recording?.donated ? t('dashUi.recordDonatedTitle', { name: recording.match.donorFirstName || t('dashUi.anonymousDonor') }) : t('dashUi.recordNotDonatedTitle', { name: recording?.match.donorFirstName || t('dashUi.anonymousDonor') })}
        description={recording?.donated ? t('dashUi.recordDonatedDesc') : t('dashUi.recordNotDonatedDesc')}
        confirmLabel={recording?.donated ? t('dashUi.markDonated') : t('dashUi.markNotDonated')}
        confirmVariant={recording?.donated ? 'primary' : 'danger'}
        onConfirm={confirmRecording}
        errorMessage={(err) => apiMessage(err, t('common.error'))}
      />
    </Dialog>
  );
}
