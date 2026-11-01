'use client';

import { useCallback, useMemo, useState } from 'react';
import dynamic from 'next/dynamic';
import { useI18n } from '@/lib/i18n';
import { useLowData } from '@/lib/lowData';
import type { DistrictRow } from '@/lib/api/admin';
import type { DistrictSummary } from '@/components/maps/SriLankaHeatmap';
import { Tabs } from '@/components/ui/Tabs';
import { DistrictTable } from './DistrictTable';
import { Notice } from './Notice';
import { BlockSkeleton } from './Skeletons';

// Leaflet and the tiles are only downloaded when the map is actually shown.
const SriLankaHeatmap = dynamic(() => import('@/components/maps/SriLankaHeatmap'), { ssr: false, loading: () => <BlockSkeleton className="h-[420px]" /> });

interface DistrictMapViewProps {
  rows: DistrictRow[];
  /** Level filter applied to the map markers and the table. */
  filter?: string;
  /** Text filter on the district name (table only). */
  query?: string;
  selected?: string | null;
  onSelect?: (row: DistrictRow) => void;
  caption: string;
}

/**
 * Map / table switch for district data. The table is always available (it is the accessible alternative),
 * and in Low-Data Mode it is the default: the map, Leaflet and the tiles are not even requested until the visitor asks for them.
 */
export function DistrictMapView({ rows, filter = 'ALL', query = '', selected, onSelect, caption }: DistrictMapViewProps) {
  const { t } = useI18n();
  const { lowData, ready } = useLowData();
  const [choice, setChoice] = useState<'map' | 'table' | null>(null);
  const view = choice ?? (lowData ? 'table' : 'map');

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return rows.filter((r) => (filter === 'ALL' || r.shortageLevel === filter) && (!q || r.district.toLowerCase().includes(q)));
  }, [rows, filter, query]);
  // DistrictRow already has the shape the map needs (a rate of null shows "No data" in the popup)
  const mapRows = rows satisfies DistrictSummary[];

  // Stable identity: a new function on every render would make the map rebuild its markers and close the open popup
  const handleSelect = useCallback(
    (d: DistrictSummary) => {
      const row = rows.find((r) => r.district === d.district);
      if (row) onSelect?.(row);
    },
    [rows, onSelect]
  );

  if (!ready) return <BlockSkeleton className="h-[420px]" />;

  return (
    <div className="space-y-4">
      <Tabs
        label={t('dashUi.viewMode')}
        value={view}
        onChange={(id) => setChoice(id as 'map' | 'table')}
        tabs={[
          { id: 'map', label: t('dashUi.viewMap') },
          { id: 'table', label: t('dashUi.viewTable') },
        ]}
      />
      {lowData && view === 'map' && <Notice tone="info">{t('dashUi.mapDataNote')}</Notice>}
      {lowData && view === 'table' && <p className="text-sm text-gray-500">{t('dashUi.mapSavedData')}</p>}
      <div role="tabpanel" aria-labelledby={`tab-${view}`}>
        {view === 'map' ? (
          <SriLankaHeatmap districts={mapRows} filter={filter} onSelectDistrict={onSelect ? handleSelect : undefined} />
        ) : (
          <DistrictTable rows={visible} caption={caption} selected={selected} onSelect={onSelect} emptyText={t('adminDashboard.noDistrictsFilter')} />
        )}
      </div>
    </div>
  );
}
