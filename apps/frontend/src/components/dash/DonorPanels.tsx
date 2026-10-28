'use client';

import { CalendarCheck, CalendarClock, History } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { Skeleton } from '@/components/ui/Skeleton';
import { useI18n } from '@/lib/i18n';
import { fetchDonations, fetchEligibility, type Donation, type Eligibility } from '@/lib/api/donors';
import { Notice } from './Notice';
import { useAsync } from './useAsync';
import { Panel, SectionHeader } from './Panel';

/** Eligibility reasons come from the server; a known code is translated, anything else is shown as sent. */
function reasonText(t: (key: string) => string, reason: string): string {
  const key = `dashUi.donor.reason.${reason}`;
  const translated = t(key);
  return translated === key ? reason : translated;
}

/** "Can I donate now?" card: eligible / next eligible date and the reasons, from GET /donors/me/eligibility. */
export function EligibilityCard() {
  const { t, format } = useI18n();
  const { state, reload } = useAsync<Eligibility>(fetchEligibility);

  return (
    <Panel>
      <SectionHeader
        as="h2"
        eyebrow={t('dashUi.donor.eligibilityEyebrow')}
        title={t('dashUi.donor.eligibilityTitle')}
        icon={<CalendarCheck className="mt-1.5 h-5 w-5 shrink-0 text-primary-text" aria-hidden="true" />}
        className="mb-4"
      />
      {state.status === 'loading' ? (
        <div role="status" aria-label={t('common.loading')} className="space-y-3">
          <Skeleton className="h-6 w-48" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      ) : state.status === 'unavailable' ? (
        <p className="text-sm text-gray-400">{t('dashUi.donor.eligibilityUnavailable')}</p>
      ) : state.status === 'error' ? (
        <Notice
          tone="error"
          action={
            <Button type="button" variant="secondary" onClick={() => void reload()}>
              {t('dash.retry')}
            </Button>
          }
        >
          {state.message || t('dash.loadFailed')}
        </Notice>
      ) : (
        <div className="space-y-3">
          <StatusBadge
            value={state.data.eligible ? 'COMPLETED' : 'PENDING'}
            tone={state.data.eligible ? 'success' : 'warning'}
            label={state.data.eligible ? t('dashUi.donor.eligibleNow') : t('dashUi.donor.notEligibleYet')}
          />
          {state.data.nextEligibleDate && !state.data.eligible && (
            <p className="flex items-start gap-2 text-base text-foreground">
              <CalendarClock className="mt-1 h-4 w-4 shrink-0 text-gray-500" aria-hidden="true" />
              {t('dashUi.donor.nextEligible', { date: format.date(state.data.nextEligibleDate, { dateStyle: 'long' }) })}
            </p>
          )}
          {state.data.eligible && <p className="text-sm text-gray-400">{t('dashUi.donor.eligibleDesc')}</p>}
          {state.data.reasons.length > 0 && (
            <ul className="list-disc space-y-1 ps-5 text-sm text-gray-400">
              {state.data.reasons.map((r) => (
                <li key={r}>{reasonText(t, r)}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Panel>
  );
}

/** Past donations from GET /donors/me/donations, newest first, with an empty state. */
export function DonationHistory() {
  const { t, format } = useI18n();
  const { state, reload } = useAsync<Donation[]>(fetchDonations);

  return (
    <section>
      <SectionHeader eyebrow={t('dashUi.section.history')} title={t('dashUi.donor.historyTitle')} />
      <Panel flush>
        {state.status === 'loading' ? (
          <div role="status" aria-label={t('common.loading')} className="space-y-3 p-5">
            <Skeleton className="h-5 w-2/3" />
            <Skeleton className="h-5 w-1/2" />
          </div>
        ) : state.status === 'unavailable' ? (
          <p className="p-6 text-sm text-gray-400">{t('dashUi.donor.historyUnavailable')}</p>
        ) : state.status === 'error' ? (
          <div className="p-5">
            <Notice
              tone="error"
              action={
                <Button type="button" variant="secondary" onClick={() => void reload()}>
                  {t('dash.retry')}
                </Button>
              }
            >
              {state.message || t('dash.loadFailed')}
            </Notice>
          </div>
        ) : state.data.length === 0 ? (
          <div className="flex flex-col items-center gap-2 px-6 py-10 text-center">
            <History className="h-8 w-8 text-gray-500" aria-hidden="true" />
            <p className="font-display text-xl text-foreground">{t('dashUi.donor.historyEmptyTitle')}</p>
            <p className="max-w-sm text-sm text-gray-400">{t('dashUi.donor.historyEmptyDesc')}</p>
          </div>
        ) : (
          <ul className="divide-y divide-line">
            {state.data.map((d) => (
              <li key={d.id} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 px-5 py-4">
                <div className="min-w-0">
                  <p className="text-base font-medium text-foreground">{d.donatedAt ? format.date(d.donatedAt, { dateStyle: 'long' }) : t('dashUi.noData')}</p>
                  {d.location && <p className="break-words text-sm text-gray-400">{d.location}</p>}
                </div>
                {d.units !== undefined && <p className="text-sm text-gray-400">{t('dashUi.units', { count: d.units })}</p>}
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </section>
  );
}
