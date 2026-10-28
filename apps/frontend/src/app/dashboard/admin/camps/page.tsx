'use client';

import { useCallback, useState } from 'react';
import dynamic from 'next/dynamic';
import { CalendarDays, MapPin, Map as MapIcon } from 'lucide-react';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { useLowData } from '@/lib/lowData';
import { asDate, asList, str } from '@/lib/api/common';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { Meta } from '@/components/dash/Meta';
import { Notice } from '@/components/dash/Notice';
import { PageHeader } from '@/components/dash/PageHeader';
import { Panel } from '@/components/dash/Panel';
import { RefreshButton } from '@/components/dash/RefreshButton';
import { Rise } from '@/components/dash/Rise';
import { BlockSkeleton, RowsSkeleton } from '@/components/dash/Skeletons';
import { useAsync } from '@/components/dash/useAsync';

// Leaflet is only downloaded when the map is actually shown
const AdminMap = dynamic(() => import('@/components/camps/AdminMap'), { ssr: false, loading: () => <BlockSkeleton className="h-[420px]" /> });

interface CampItem {
  id: number;
  name: string;
  district: string;
  location?: string;
  date: Date | null;
  status: string;
}

async function fetchCamps(): Promise<CampItem[]> {
  const res = await apiClient.get('/camps');
  return asList<Record<string, unknown>>(res.data).map((c, i) => ({
    id: Number(c.id ?? i),
    name: String(c.name ?? ''),
    district: String(c.district ?? ''),
    location: str(c.location),
    date: asDate(c.scheduledDate),
    status: String(c.status ?? 'SCHEDULED'),
  }));
}

const STATUS_TONE = { SCHEDULED: 'info', COMPLETED: 'success', CANCELLED: 'neutral' } as const;

/** Camp list: the text alternative to the map (used in Low-Data Mode, handy for screen readers too). */
function CampList() {
  const { t, format } = useI18n();
  const camps = useAsync(fetchCamps);

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <RefreshButton onClick={camps.reload} loading={camps.state.status === 'loading'} />
      </div>
      {camps.state.status === 'loading' ? (
        <RowsSkeleton rows={3} />
      ) : camps.state.status !== 'ready' ? (
        <Notice
          tone="error"
          action={
            <Button type="button" variant="secondary" onClick={() => void camps.reload()}>
              {t('dash.retry')}
            </Button>
          }
        >
          {(camps.state.status === 'error' && camps.state.message) || t('dash.loadFailed')}
        </Notice>
      ) : camps.state.data.length === 0 ? (
        <EmptyState icon={<CalendarDays className="h-10 w-10" aria-hidden="true" />} title={t('dashUi.admin.noCamps')} description={t('dashUi.admin.noCampsDesc')} />
      ) : (
        <ul className="divide-y divide-line rounded-2xl border border-line bg-surface">
          {camps.state.data.map((c) => {
            const known = c.status in STATUS_TONE;
            return (
              <li key={c.id} className="flex flex-wrap items-start justify-between gap-3 p-5">
                <div className="min-w-0">
                  <h3 className="break-words text-base font-medium text-foreground">{c.name}</h3>
                  <div className="mt-1.5 flex flex-wrap gap-x-4 gap-y-1">
                    <Meta icon={<MapPin />}>{[c.district, c.location].filter(Boolean).join(' · ')}</Meta>
                    {c.date && <Meta icon={<CalendarDays />}>{format.date(c.date, { dateStyle: 'medium' })}</Meta>}
                  </div>
                </div>
                <StatusBadge
                  value={c.status}
                  tone={known ? STATUS_TONE[c.status as keyof typeof STATUS_TONE] : 'neutral'}
                  label={t(`dashUi.campStatus.${known ? c.status : 'UNKNOWN'}`)}
                />
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

export default function AdminCampsPage() {
  const { t } = useI18n();
  const { lowData, ready } = useLowData();
  const [forceMap, setForceMap] = useState(false);
  const showMap = ready && (!lowData || forceMap);
  const loadMap = useCallback(() => setForceMap(true), []);

  return (
    <div>
      <PageHeader
        eyebrow={t('adminDashboard.adminMapView')}
        title={t('adminDashboard.geospatialTracking')}
        description={t('adminDashboard.geospatialTrackingDesc')}
        backHref="/dashboard/admin"
      />

      <Rise delay={0.05}>
        <Panel className="sm:p-6">
          {!ready ? (
            <BlockSkeleton className="h-[420px]" />
          ) : showMap ? (
            <AdminMap />
          ) : (
            <div className="space-y-5">
              <Notice
                tone="info"
                action={
                  <Button type="button" variant="secondary" onClick={loadMap}>
                    <MapIcon className="h-4 w-4" aria-hidden="true" />
                    {t('dashUi.loadMapAnyway')}
                  </Button>
                }
              >
                {t('dashUi.mapSavedData')}
              </Notice>
              <CampList />
            </div>
          )}
        </Panel>
      </Rise>
    </div>
  );
}
