'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import type * as Leaflet from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useI18n } from '@/lib/i18n';
import { useTheme } from '@/components/theme/ThemeProvider';
import { escapeHtml } from './escapeHtml';
import { SHORTAGE_RULES } from './mapConfig';
import { createSriLankaMap } from './leafletSetup';
import { MapLegend, type LegendItem } from './MapLegend';
import { TONE, captionHtml, cellHtml, pillHtml, statusMarkerHtml, titleHtml, type MapGlyph, type MapShape, type MapTone } from './mapStyles';

/** What the map needs from a district row (a DistrictRow from lib/api/admin satisfies it). */
export interface DistrictSummary {
  district: string;
  latitude: number;
  longitude: number;
  availableDonors: number;
  activeRequests: number;
  /** Percent; null = no data. */
  fulfillmentRate: number | null;
  /** NO_DATA is its own grey symbol: it is never drawn as balanced. */
  shortageLevel: 'CRITICAL' | 'WARNING' | 'BALANCED' | 'SURPLUS' | 'NO_DATA';
}

interface Props {
  districts: DistrictSummary[];
  filter?: string;
  onSelectDistrict?: (district: DistrictSummary) => void;
}

type Level = DistrictSummary['shortageLevel'];

/** One entry per shortage level: its symbol (shape + glyph + colour). Order = severity. */
const LEVELS: Record<Level, { shape: MapShape; glyph: MapGlyph; tone: MapTone; pulse: string; quiet: boolean }> = {
  CRITICAL: { shape: 'triangle', glyph: 'alert', tone: 'danger', pulse: 'marker-pulse-critical', quiet: false },
  WARNING: { shape: 'diamond', glyph: 'alert', tone: 'warning', pulse: 'marker-pulse-warning', quiet: false },
  BALANCED: { shape: 'circle', glyph: 'check', tone: 'success', pulse: '', quiet: true },
  SURPLUS: { shape: 'square', glyph: 'plus', tone: 'info', pulse: '', quiet: true },
  NO_DATA: { shape: 'circle', glyph: 'dot', tone: 'muted', pulse: '', quiet: true },
};
const LEVEL_ORDER: Level[] = ['CRITICAL', 'WARNING', 'BALANCED', 'SURPLUS', 'NO_DATA'];

export default function SriLankaHeatmap({ districts, filter = 'ALL', onSelectDistrict }: Props) {
  const { t, format } = useI18n();
  const { resolved } = useTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<Leaflet.Map | null>(null);
  const leafletRef = useRef<typeof Leaflet | null>(null);
  const layerRef = useRef<Leaflet.LayerGroup | null>(null);
  const [ready, setReady] = useState(false);

  // The parent often passes a new inline callback / array on every render. Keeping them in refs (and rebuilding markers
  // only when the *content* changes) is what stops the popup from being destroyed right after a marker click.
  const selectRef = useRef(onSelectDistrict);
  const districtsRef = useRef(districts);
  const openDistrictRef = useRef<string | null>(null);
  useEffect(() => {
    selectRef.current = onSelectDistrict;
    districtsRef.current = districts;
  });

  const signature = useMemo(
    () =>
      JSON.stringify([
        filter,
        districts.map((d) => [d.district, d.latitude, d.longitude, d.availableDonors, d.activeRequests, d.fulfillmentRate, d.shortageLevel]),
      ]),
    [districts, filter]
  );

  // Create the map once
  useEffect(() => {
    let cancelled = false;
    let observer: ResizeObserver | undefined;
    let created: Leaflet.Map | undefined;

    (async () => {
      const L = (await import('leaflet')).default;
      if (cancelled || !containerRef.current) return;
      const { map, refit } = createSriLankaMap(L, containerRef.current, { zoomIn: t('pub.mapZoomIn'), zoomOut: t('pub.mapZoomOut') });
      created = map;
      leafletRef.current = L;
      mapRef.current = map;
      layerRef.current = L.layerGroup().addTo(map);
      observer = new ResizeObserver(() => refit(false));
      observer.observe(containerRef.current);
      setReady(true);
    })();

    return () => {
      cancelled = true;
      observer?.disconnect();
      created?.remove();
      mapRef.current = null;
      layerRef.current = null;
    };
    // The zoom button titles are set once; a language switch updates them on the next mount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // (Re)draw markers when the data, filter, language or theme changes
  useEffect(() => {
    const L = leafletRef.current;
    const map = mapRef.current;
    const layer = layerRef.current;
    if (!ready || !L || !map || !layer) return;

    const reopen = openDistrictRef.current;
    layer.clearLayers();

    const shown = districtsRef.current.filter((d) => filter === 'ALL' || d.shortageLevel === filter);

    shown.forEach((d) => {
      const level = LEVELS[d.shortageLevel] ?? LEVELS.NO_DATA;
      const levelLabel = t(`pub.level.${d.shortageLevel}`);
      const accessibleName = t('pub.mapMarkerLabel', {
        district: d.district,
        level: levelLabel,
        requests: d.activeRequests,
        donors: d.availableDonors,
      });

      const icon = L.divIcon({
        className: 'custom-district-marker',
        html: statusMarkerHtml({ shape: level.shape, glyph: level.glyph, tone: level.tone, label: escapeHtml(levelLabel), pulseClass: level.pulse, quietLabel: level.quiet }),
        iconSize: [36, 36],
        iconAnchor: [18, 18],
        popupAnchor: [0, -18],
      });

      const marker = L.marker([d.latitude, d.longitude], { icon, title: accessibleName, keyboard: true });

      const hasRate = d.fulfillmentRate != null && Number.isFinite(Number(d.fulfillmentRate));
      const rate = hasRate ? Math.max(0, Math.min(100, Number(d.fulfillmentRate))) : 0;
      const popupContent = `
        <div style="padding:16px;min-width:230px;font-family:inherit;color:var(--foreground);">
          <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin-bottom:6px;">
            ${titleHtml(escapeHtml(d.district))}
            ${pillHtml(level.tone, escapeHtml(levelLabel), { shape: level.shape, glyph: level.glyph })}
          </div>
          ${captionHtml(escapeHtml(t(`pub.levelDesc.${d.shortageLevel}`, { ...SHORTAGE_RULES })), '0 0 14px 0')}
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:10px;">
            ${cellHtml(escapeHtml(t('pub.mapActiveRequests')), escapeHtml(format.number(d.activeRequests)), TONE.danger.text)}
            ${cellHtml(escapeHtml(t('pub.mapAvailableDonors')), escapeHtml(format.number(d.availableDonors)), TONE.success.text)}
          </div>
          <div style="padding:10px;border-radius:10px;border:1px solid var(--line);font-size:13px;color:var(--muted);">
            <div style="display:flex;justify-content:space-between;gap:10px;${hasRate ? 'margin-bottom:6px;' : ''}">
              <span>${escapeHtml(t('pub.mapFulfillment'))}</span>
              <span style="font-weight:600;color:var(--foreground);">${hasRate ? escapeHtml(format.number(rate / 100, { style: 'percent', maximumFractionDigits: 0 })) : escapeHtml(t('pub.mapNoData'))}</span>
            </div>
            ${hasRate ? `<div style="width:100%;height:4px;background:color-mix(in srgb, var(--foreground) 12%, transparent);border-radius:99px;overflow:hidden;"><div style="width:${rate}%;height:100%;background:var(--primary);"></div></div>` : ''}
          </div>
        </div>
      `;

      marker.bindPopup(popupContent, { maxWidth: 300, autoPan: true });
      marker.on('click', () => selectRef.current?.(d));
      marker.on('popupopen', () => {
        openDistrictRef.current = d.district;
      });
      marker.on('popupclose', () => {
        if (openDistrictRef.current === d.district) openDistrictRef.current = null;
      });
      marker.on('add', () => marker.getElement()?.setAttribute('aria-label', accessibleName));

      marker.addTo(layer);
      if (reopen === d.district) marker.openPopup();
    });
    // `signature` captures every field of `districts` that matters; the array identity is deliberately not a dependency
  }, [ready, signature, filter, t, format, resolved]);

  const legend: LegendItem[] = LEVEL_ORDER.map((level) => ({
    shape: LEVELS[level].shape,
    glyph: LEVELS[level].glyph,
    tone: LEVELS[level].tone,
    label: t(`pub.level.${level}`),
    description: t(`pub.levelDesc.${level}`, { ...SHORTAGE_RULES }),
  }));

  return (
    <div className="overflow-hidden rounded-2xl border border-line bg-surface">
      <div
        ref={containerRef}
        role="region"
        aria-label={t('pub.mapAriaLabel')}
        className="h-[420px] w-full sm:h-[520px]"
      />
      <MapLegend title={t('pub.mapLegendTitle')} items={legend} className="border-t border-line p-4 sm:p-5" />
    </div>
  );
}
