'use client';

import { useCallback, useEffect, useState } from 'react';
import { Phone, Users } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useI18n } from '@/lib/i18n';
import { apiMessage, isEndpointMissing } from '@/lib/api/common';
import { fetchRequestMatches, type RequestMatch } from '@/lib/api/requests';
import { BloodTypeTile } from './IconTile';
import { matchStatusLabel, matchStatusTone } from './matchStatus';
import { Notice } from './Notice';
import { RowsSkeleton } from './Skeletons';

interface MatchesDrawerProps {
  /** Request whose matches to show; `null` keeps the drawer closed. */
  requestId: number | null;
  hospitalName?: string;
  onClose: () => void;
}

/** Side sheet listing the donors matched to one request: first name, blood group, distance, status; the phone number only once they ACCEPTED. */
export function MatchesDrawer({ requestId, hospitalName, onClose }: MatchesDrawerProps) {
  const { t, format } = useI18n();
  const [matches, setMatches] = useState<RequestMatch[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (requestId === null) return;
    setLoading(true);
    setError(null);
    try {
      setMatches(await fetchRequestMatches(requestId));
    } catch (err) {
      setMatches(null);
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

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={t('dashUi.matchesTitle')}
      description={hospitalName ? t('dashUi.matchesFor', { hospital: hospitalName }) : undefined}
      className="m-0 ms-auto h-dvh max-h-none w-[min(100vw,28rem)] max-w-none rounded-none rounded-s-2xl"
    >
      {!open ? null : loading ? (
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
      ) : matches && matches.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-10 text-center">
          <Users className="h-8 w-8 text-gray-500" aria-hidden="true" />
          <p className="font-display text-xl text-foreground">{t('dashUi.noMatchesTitle')}</p>
          <p className="max-w-xs text-sm text-gray-400">{t('dashUi.noMatchesDesc')}</p>
        </div>
      ) : (
        <>
          <p className="mb-4 text-sm text-gray-400">{t('dashUi.matchesPrivacy')}</p>
          <ul className="space-y-3">
            {matches?.map((m) => (
              <li key={m.id} className="rounded-xl border border-line bg-surface-raised p-4">
                <div className="flex items-start gap-3">
                  <BloodTypeTile type={m.bloodType || '?'} size="sm" />
                  <div className="min-w-0 flex-1">
                    <p className="break-words text-base font-medium text-foreground">{m.firstName || t('dashUi.anonymousDonor')}</p>
                    <p className="mt-0.5 text-sm text-gray-400">
                      {m.distanceKm === null ? t('dashUi.distanceUnknown') : t('dashUi.distanceKm', { km: format.number(m.distanceKm, { maximumFractionDigits: 1 }) })}
                    </p>
                  </div>
                  <StatusBadge value={m.status} label={matchStatusLabel(t, m.status)} tone={matchStatusTone(m.status)} />
                </div>
                {m.status === 'ACCEPTED' && m.contactPhone ? (
                  <a
                    href={`tel:${m.contactPhone}`}
                    className="mt-3 inline-flex min-h-11 items-center gap-2 rounded-lg text-sm font-medium text-primary-text hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                  >
                    <Phone className="h-4 w-4" aria-hidden="true" />
                    <span dir="ltr">{m.contactPhone}</span>
                  </a>
                ) : (
                  <p className="mt-3 text-sm text-gray-500">{t('dashUi.contactAfterAccept')}</p>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
    </Dialog>
  );
}
