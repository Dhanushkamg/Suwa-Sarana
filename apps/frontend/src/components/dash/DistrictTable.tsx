'use client';

import { MapPin } from 'lucide-react';
import { StatusBadge, type BadgeTone } from '@/components/ui/StatusBadge';
import { Table, Td, Th } from '@/components/ui/Table';
import { useI18n } from '@/lib/i18n';
import type { DistrictRow, ShortageLevel } from '@/lib/api/admin';
import { cn } from '@/lib/utils';
import { ROW_HOVER } from './styles';

const LEVEL_TONE: Record<ShortageLevel, BadgeTone> = {
  CRITICAL: 'danger',
  WARNING: 'warning',
  BALANCED: 'success',
  SURPLUS: 'info',
  NO_DATA: 'neutral',
};

/** Shortage level as icon + localised text + colour. */
export function ShortageBadge({ level }: { level: ShortageLevel }) {
  const { t } = useI18n();
  return <StatusBadge value={level} label={t(`dashUi.shortage.${level}`)} tone={LEVEL_TONE[level]} />;
}

interface DistrictTableProps {
  rows: DistrictRow[];
  caption: string;
  selected?: string | null;
  onSelect?: (row: DistrictRow) => void;
  emptyText?: string;
}

/**
 * District figures as a real table: the accessible alternative to the map (and the whole view in Low-Data Mode).
 * Scrolls inside its own box on narrow screens.
 */
export function DistrictTable({ rows, caption, selected, onSelect, emptyText }: DistrictTableProps) {
  const { t, format } = useI18n();
  return (
    <Table>
      <caption className="sr-only">{caption}</caption>
      <thead>
        <tr>
          <Th>{t('dash.district')}</Th>
          <Th>{t('bloodRequests.status')}</Th>
          <Th className="text-end">{t('adminDashboard.availableDonors')}</Th>
          <Th className="text-end">{t('adminDashboard.activeRequests')}</Th>
          <Th className="text-end">{t('adminDashboard.fulfilled')}</Th>
          <Th className="text-end">{t('dashUi.fulfilmentRate')}</Th>
        </tr>
      </thead>
      <tbody>
        {rows.length === 0 ? (
          <tr>
            <Td colSpan={6} className="py-10 text-center text-gray-500">
              {emptyText ?? t('dashUi.noData')}
            </Td>
          </tr>
        ) : (
          rows.map((d) => {
            const isSelected = selected === d.district;
            return (
              <tr key={d.district} className={cn(ROW_HOVER, isSelected && 'bg-primary-soft')}>
                <Td className="font-medium">
                  {onSelect ? (
                    <button
                      type="button"
                      onClick={() => onSelect(d)}
                      aria-pressed={isSelected}
                      className="inline-flex min-h-11 items-center gap-2 rounded text-start hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                    >
                      <MapPin className="h-4 w-4 text-gray-500" aria-hidden="true" />
                      {d.district}
                    </button>
                  ) : (
                    <span className="inline-flex items-center gap-2">
                      <MapPin className="h-4 w-4 text-gray-500" aria-hidden="true" />
                      {d.district}
                    </span>
                  )}
                </Td>
                <Td>
                  <ShortageBadge level={d.shortageLevel} />
                </Td>
                <Td className="text-end tabular-nums">{format.number(d.availableDonors)}</Td>
                <Td className="text-end tabular-nums">{format.number(d.activeRequests)}</Td>
                <Td className="text-end tabular-nums">{format.number(d.fulfilledRequests)}</Td>
                <Td className="text-end tabular-nums">
                  {d.fulfillmentRate === null ? <span className="text-gray-500">{t('dashUi.noData')}</span> : `${format.number(d.fulfillmentRate, { maximumFractionDigits: 1 })}%`}
                </Td>
              </tr>
            );
          })
        )}
      </tbody>
    </Table>
  );
}
