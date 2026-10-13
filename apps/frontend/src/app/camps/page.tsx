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

/** Splits an ISO date ("2026-10-14") into the pieces for the mono date block; null when it cannot be parsed. */
function dateParts(iso: string, locale: string) {
  const d = new Date(`${iso}T00:00:00`);
  if (Number.isNaN(d.getTime())) return null;
  const tag = locale === 'en' ? 'en-GB' : locale;
  return {
    month: d.toLocaleDateString(tag, { month: 'short' }),
    day: d.toLocaleDateString(tag, { day: '2-digit' }),
    year: d.toLocaleDateString(tag, { year: 'numeric' }),
  };
}

function CampCard({ camp, expanded, onToggle }: { camp: Camp; expanded: boolean; onToggle: () => void }) {
  const { t, locale } = useI18n();
  const parts = dateParts(camp.scheduledDate, locale);
  const groups = camp.requiredBloodGroups
    ? camp.requiredBloodGroups.split(',').map((g) => g.trim()).filter(Boolean)
    : [];

  return (
    <article className="glass-card flex flex-col rounded-2xl p-6">
      <div className="flex items-start gap-5">
        {/* Date block */}
        <div className="w-[4.25rem] shrink-0 rounded-xl border border-line-strong bg-surface-raised py-2.5 text-center font-mono">
          {parts ? (
            <>
              <div className="text-[10px] uppercase tracking-[0.18em] text-ember">{parts.month}</div>
              <div className="my-0.5 text-2xl leading-none text-foreground">{parts.day}</div>
              <div className="text-[10px] tracking-wider text-gray-500">{parts.year}</div>
            </>
          ) : (
            <div className="px-1 text-[11px] leading-tight text-gray-300">{camp.scheduledDate}</div>
          )}
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
            <p className="eyebrow">{camp.district}</p>
            {camp.status && <StatusBadge value={camp.status} />}
          </div>
          <h2 className="mt-2 font-display break-words text-2xl text-foreground sm:text-[1.65rem]">{camp.name}</h2>
        </div>
      </div>

      <dl className="mt-6 space-y-3 text-sm text-gray-300">
        <div className="flex items-start gap-3">
          <dt className="shrink-0">
            <Clock className="mt-0.5 h-4 w-4 text-gray-500" aria-hidden="true" />
            <span className="sr-only">{t('publicPages.time')}</span>
          </dt>
          <dd className="font-mono text-[13px] tracking-wide">
            {camp.startTime} – {camp.endTime}
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
            <span
              key={g}
              className="rounded-full border border-ember/30 bg-ember/10 px-2.5 py-0.5 font-mono text-xs font-medium text-ember"
            >
              {g}
            </span>
          ))}
        </div>
      )}

      {/* Slot booking toggle */}
      <div className="mt-6 border-t border-line pt-4">
        <button
          type="button"
          aria-expanded={expanded}
          onClick={onToggle}
          className="group flex w-full items-center justify-between gap-3 text-left text-sm font-medium text-foreground transition-colors hover:text-ember"
        >
          <span>{expanded ? t('camps.hideSlots') : t('camps.showSlots')}</span>
          <ChevronDown
            className={cn('h-4 w-4 shrink-0 text-gray-500 transition-transform duration-300 group-hover:text-ember', expanded && 'rotate-180')}
            aria-hidden="true"
          />
        </button>
        {expanded && <SlotPicker hostType="camp" hostId={camp.id} />}
      </div>
    </article>
  );
}

export default function CampsPage() {
  const { t } = useI18n();
  const { user } = useAuthStore();
  const [camps, setCamps] = useState<Camp[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [expandedCampId, setExpandedCampId] = useState<number | null>(null);

  // Depend on the primitive role (not the whole user object) to avoid refetch loops
  const role = user?.role;

  const fetchCamps = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const endpoint = role === 'DONOR' ? '/camps/recommended' : '/camps';
      const response = await apiClient.get(endpoint);
      setCamps(response.data);
    } catch (err) {
      console.error('Failed to load camps', err);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [role]);

  useEffect(() => {
    fetchCamps();
  }, [fetchCamps]);

  return (
    <div className="min-h-screen bg-background text-foreground">
      <PubHeader />

      <main className="mx-auto max-w-6xl px-5 pb-24 pt-12 sm:px-8 sm:pt-20">
        <PageHeading
          eyebrow={t('camps.communityDrives')}
          title={role === 'DONOR' ? t('camps.recommendedCamps') : t('camps.upcomingCamps')}
          description={role === 'DONOR' ? t('camps.recommendedDesc') : t('camps.upcomingDesc')}
          aside={!loading && !error && camps.length > 0 ? t('pub.campsCount', { count: camps.length }) : undefined}
        />

        <div className="mt-10 sm:mt-14">
          {loading ? (
            <div role="status" className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-72 rounded-2xl" />
              ))}
              <span className="sr-only">{t('camps.loadingCamps')}</span>
            </div>
          ) : error ? (
            <div role="alert">
              <EmptyState
                icon={<AlertCircle className="h-10 w-10 text-ember" />}
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
              icon={<CalendarX2 className="h-10 w-10" />}
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
