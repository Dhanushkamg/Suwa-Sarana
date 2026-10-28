'use client';

import { useState, useEffect, useCallback } from 'react';
import { Clock, MapPin, ChevronDown, AlertCircle, CalendarX2 } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import apiClient from '@/lib/apiClient';
import SlotPicker from '@/components/slots/SlotPicker';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { Reveal } from '@/components/motion/Reveal';
import { PageHeading } from '@/components/pub/PageHeading';
import { PubFooter } from '@/components/pub/PubFooter';
import { PubHeader } from '@/components/pub/PubHeader';
import { TIME_FORMAT, toDate, toTimeOfDay } from '@/components/pub/dates';
import { cn } from '@/lib/utils';

interface Camp {
  id: number;
  name: string;
  district: string;
  location: string;
  scheduledDate: string;
  startTime: string;
  endTime: string;
  organizerName: string;
  requiredBloodGroups: string;
  status?: string;
}

function CampCard({ camp, expanded, onToggle }: { camp: Camp; expanded: boolean; onToggle: () => void }) {
  const { t, format } = useI18n();
  const date = toDate(camp.scheduledDate);
  const start = toTimeOfDay(camp.startTime);
  const end = toTimeOfDay(camp.endTime);
  const groups = camp.requiredBloodGroups
    ? camp.requiredBloodGroups.split(',').map((g) => g.trim()).filter(Boolean)
    : [];
  const statusKey = camp.status ? `pub.campStatus.${camp.status}` : '';
  const statusLabel = camp.status && t(statusKey) !== statusKey ? t(statusKey) : undefined;
  const panelId = `camp-slots-${camp.id}`;

  return (
    <article className="glass-card flex flex-col rounded-2xl p-6">
      <div className="flex items-start gap-5">
        {/* Date block */}
        <div className="w-[4.5rem] shrink-0 rounded-xl border border-line-strong bg-surface-raised px-1 py-2.5 text-center">
          {date ? (
            <>
              <div className="text-xs font-semibold uppercase text-primary-text">{format.date(date, { month: 'short' })}</div>
              <div className="my-0.5 font-mono text-2xl leading-none text-foreground">{format.date(date, { day: '2-digit' })}</div>
              <div className="font-mono text-[11px] text-gray-500">{format.date(date, { year: 'numeric' })}</div>
            </>
          ) : (
            <div className="px-1 text-xs leading-tight text-gray-400">{String(camp.scheduledDate)}</div>
          )}
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
            <p className="eyebrow">{camp.district}</p>
            {camp.status && <StatusBadge value={camp.status} label={statusLabel} />}
          </div>
          <h2 className="mt-2 font-display break-words text-2xl text-foreground sm:text-[1.65rem]">{camp.name}</h2>
        </div>
      </div>

      <dl className="mt-6 space-y-3 text-sm text-foreground">
        <div className="flex items-start gap-3">
          <dt className="shrink-0">
            <Clock className="mt-0.5 h-4 w-4 text-gray-500" aria-hidden="true" />
            <span className="sr-only">{t('publicPages.time')}</span>
          </dt>
          <dd className="font-mono text-[13px] tracking-wide">
            {start && end ? `${format.dateTime(start, TIME_FORMAT)} – ${format.dateTime(end, TIME_FORMAT)}` : `${camp.startTime} – ${camp.endTime}`}
          </dd>
        </div>
        <div className="flex items-start gap-3">
          <dt className="shrink-0">
            <MapPin className="mt-0.5 h-4 w-4 text-gray-500" aria-hidden="true" />
            <span className="sr-only">{t('pub.venue')}</span>
          </dt>
          <dd className="min-w-0 break-words">{camp.location}</dd>
        </div>
      </dl>

      {groups.length > 0 && (
        <div className="mt-5 flex flex-wrap items-center gap-2">
          <span className="eyebrow mr-1">{t('camps.requires')}</span>
          {groups.map((g) => (
            // Blood-type badges are one of the few places the brand crimson is used
            <span key={g} className="rounded-full border border-brand/30 bg-brand-soft px-2.5 py-0.5 font-mono text-xs font-semibold text-brand">
              {g}
            </span>
          ))}
        </div>
      )}

      {/* Slot booking toggle */}
      <div className="mt-6 border-t border-line pt-2">
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={panelId}
          onClick={onToggle}
          className="group flex min-h-11 w-full items-center justify-between gap-3 text-left text-sm font-medium text-foreground transition-colors hover:text-primary-text"
        >
          <span>{expanded ? t('camps.hideSlots') : t('camps.showSlots')}</span>
          <ChevronDown
            className={cn('h-4 w-4 shrink-0 text-gray-500 transition-transform duration-300 group-hover:text-primary-text motion-reduce:transition-none', expanded && 'rotate-180')}
            aria-hidden="true"
          />
        </button>
        <div id={panelId}>{expanded && <SlotPicker hostType="camp" hostId={camp.id} />}</div>
      </div>
    </article>
  );
}

/** Same footprint as CampCard so the page does not jump when data arrives. */
function CampCardSkeleton() {
  return (
    <div className="glass-card flex flex-col rounded-2xl p-6" aria-hidden="true">
      <div className="flex items-start gap-5">
        <Skeleton className="h-[5.5rem] w-[4.5rem] shrink-0" />
        <div className="min-w-0 flex-1 space-y-3">
          <Skeleton className="h-3 w-24" />
          <Skeleton className="h-7 w-full" />
          <Skeleton className="h-7 w-2/3" />
        </div>
      </div>
      <div className="mt-6 space-y-3">
        <Skeleton className="h-4 w-40" />
        <Skeleton className="h-4 w-56" />
      </div>
      <div className="mt-5 flex gap-2">
        <Skeleton className="h-6 w-10 rounded-full" />
        <Skeleton className="h-6 w-10 rounded-full" />
        <Skeleton className="h-6 w-10 rounded-full" />
      </div>
      <div className="mt-6 border-t border-line pt-4">
        <Skeleton className="h-5 w-48" />
      </div>
    </div>
  );
}

export default function CampsPage() {
  const { t } = useI18n();
  const user = useAuthStore((s) => s.user);
  const initialize = useAuthStore((s) => s.initialize);
  const isInitialized = useAuthStore((s) => s.isInitialized);
  const [camps, setCamps] = useState<Camp[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [expandedCampId, setExpandedCampId] = useState<number | null>(null);

  // Restore the session after a reload so a signed-in donor sees recommended camps and can book (audit F14)
  useEffect(() => {
    initialize();
  }, [initialize]);

  // Depend on the primitive role (not the whole user object) to avoid refetch loops
  const role = user?.role;

  const fetchCamps = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const endpoint = role === 'DONOR' ? '/camps/recommended' : '/camps';
      const response = await apiClient.get(endpoint);
      const body = response.data;
      const list = Array.isArray(body) ? body : body?.data;
      setCamps(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error('Failed to load camps', err);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [role]);

  // Wait for the session restore so we do not fetch the generic list and immediately refetch the donor list
  useEffect(() => {
    if (isInitialized) fetchCamps();
  }, [fetchCamps, isInitialized]);

  return (
    <div className="min-h-screen bg-background text-foreground">
      <PubHeader />

      <main id="main-content" className="mx-auto max-w-6xl px-5 pb-24 pt-12 sm:px-8 sm:pt-20">
        <PageHeading
          eyebrow={t('camps.communityDrives')}
          title={role === 'DONOR' ? t('camps.recommendedCamps') : t('camps.upcomingCamps')}
          description={role === 'DONOR' ? t('camps.recommendedDesc') : t('camps.upcomingDesc')}
          aside={!loading && !error && camps.length > 0 ? t('pub.campsCount', { count: camps.length }) : undefined}
        />

        <div className="mt-10 sm:mt-14">
          {loading ? (
            <div role="status" aria-busy="true" className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
              {[0, 1, 2].map((i) => (
                <CampCardSkeleton key={i} />
              ))}
              <span className="sr-only">{t('camps.loadingCamps')}</span>
            </div>
          ) : error ? (
            <div role="alert">
              <EmptyState
                icon={<AlertCircle className="h-10 w-10 text-danger-text" aria-hidden="true" />}
                title={t('camps.loadFailed')}
                description={t('camps.loadFailedDesc')}
                action={
                  <Button variant="secondary" onClick={fetchCamps}>
                    {t('common.refresh')}
                  </Button>
                }
              />
            </div>
          ) : camps.length === 0 ? (
            <EmptyState
              icon={<CalendarX2 className="h-10 w-10" aria-hidden="true" />}
              title={t('camps.noScheduledCamps')}
              description={t('camps.noScheduledCampsDesc')}
            />
          ) : (
            <div className="grid grid-cols-1 items-start gap-5 md:grid-cols-2 lg:grid-cols-3">
              {camps.map((camp, i) => (
                <Reveal key={camp.id} delay={(i % 3) * 0.08} y={24}>
                  <CampCard
                    camp={camp}
                    expanded={expandedCampId === camp.id}
                    onToggle={() => setExpandedCampId(expandedCampId === camp.id ? null : camp.id)}
                  />
                </Reveal>
              ))}
            </div>
          )}
        </div>
      </main>

      <PubFooter />
    </div>
  );
}
