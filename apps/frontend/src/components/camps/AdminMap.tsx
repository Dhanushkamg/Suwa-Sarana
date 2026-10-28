'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import type * as Leaflet from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { AlertCircle } from 'lucide-react';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { useTheme } from '@/components/theme/ThemeProvider';
import { escapeHtml } from '@/components/maps/escapeHtml';
import { createSriLankaMap } from '@/components/maps/leafletSetup';
import { MapLegend, type LegendItem } from '@/components/maps/MapLegend';
import { captionHtml, cellHtml, pillHtml, statusMarkerHtml, titleHtml, type MapGlyph, type MapShape, type MapTone } from '@/components/maps/mapStyles';
import { TIME_FORMAT, toDate, toTimeOfDay } from '@/components/pub/dates';

interface Camp {
  id: number;
  name: string;
  district: string;
  location: string;
  latitude: number;
  longitude: number;
  scheduledDate: string;
  startTime: string;
  endTime: string;
  status: string;
  organizerName: string;
  requiredBloodGroups: string;
}

/** One symbol per camp status; the same table feeds the markers and the legend (audit F31). Scheduled is NOT the "critical" colour. */
const STATUS: Record<string, { shape: MapShape; glyph: MapGlyph; tone: MapTone }> = {
  SCHEDULED: { shape: 'pin', glyph: 'dot', tone: 'primary' },
  COMPLETED: { shape: 'circle', glyph: 'check', tone: 'success' },
  CANCELLED: { shape: 'square', glyph: 'x', tone: 'muted' },
};
const UNKNOWN = { shape: 'circle', glyph: 'dot', tone: 'muted' } as const;
const STATUS_ORDER = ['SCHEDULED', 'COMPLETED', 'CANCELLED'];

export default function AdminMap() {
  const { t, format } = useI18n();
  const { resolved } = useTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<Leaflet.Map | null>(null);
  const leafletRef = useRef<typeof Leaflet | null>(null);
  const layerRef = useRef<Leaflet.LayerGroup | null>(null);
  const [ready, setReady] = useState(false);
  const [camps, setCamps] = useState<Camp[] | null>(null);
  const [failed, setFailed] = useState(false);
  const openCampRef = useRef<number | null>(null);

  const statusLabel = useCallback(
    (status: string) => {
      const key = `pub.campStatus.${status}`;
      const text = t(key);
      return text === key ? t('pub.campStatus.UNKNOWN') : text;
    },
    [t]
  );

  // Camps
  useEffect(() => {
    let cancelled = false;
    apiClient
      .get('/camps')
      .then((res) => {
        if (cancelled) return;
        const list = Array.isArray(res.data) ? res.data : res.data?.data;
        setCamps(Array.isArray(list) ? list : []);
      })
      .catch((err) => {
        console.error('Failed to load camps for map:', err);
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Map, created once
  useEffect(() => {
    let cancelled = false;
    let observer: ResizeObserver | undefined;
    let created: Leaflet.Map | undefined;

    (async () => {
      const L = (await import('leaflet')).default;
      if (cancelled || !containerRef.current) return;
      const { map, refit } = createSriLankaMap(L, containerRef.current, { zoomIn: t('pub.mapZoomIn'), zoomOut: t('pub.mapZoomOut') }, { maxZoom: 14 });
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Markers
  useEffect(() => {
    const L = leafletRef.current;
    const layer = layerRef.current;
    if (!ready || !L || !layer || !camps) return;

    const reopen = openCampRef.current;
    layer.clearLayers();

    camps.forEach((camp) => {
      if (!camp.latitude || !camp.longitude) return;

      const symbol = STATUS[camp.status] ?? UNKNOWN;
      const label = statusLabel(camp.status);
      const date = toDate(camp.scheduledDate);
      const start = toTimeOfDay(camp.startTime);
      const end = toTimeOfDay(camp.endTime);
      const name = `${camp.name}: ${label}`;

      const icon = L.divIcon({
        className: 'custom-district-marker',
        html: statusMarkerHtml({ shape: symbol.shape, glyph: symbol.glyph, tone: symbol.tone, label: escapeHtml(label), quietLabel: true }),
        iconSize: [36, 36],
        iconAnchor: [18, 18],
        popupAnchor: [0, -20],
      });

      const marker = L.marker([camp.latitude, camp.longitude], { icon, title: name, keyboard: true });

      const popupContent = `
        <div style="padding:16px;min-width:240px;font-family:inherit;color:var(--foreground);">
          <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin-bottom:6px;">
            ${titleHtml(escapeHtml(camp.name))}
            ${pillHtml(symbol.tone, escapeHtml(label), { shape: symbol.shape, glyph: symbol.glyph })}
          </div>
          <div style="font-size:13px;line-height:1.5;color:var(--muted);margin-bottom:12px;overflow-wrap:anywhere;">${escapeHtml(camp.location)}, ${escapeHtml(camp.district)}</div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
            ${cellHtml(escapeHtml(t('publicPages.date')), escapeHtml(date ? format.date(date) : String(camp.scheduledDate)))}
            ${cellHtml(escapeHtml(t('publicPages.time')), escapeHtml(start && end ? `${format.dateTime(start, TIME_FORMAT)} – ${format.dateTime(end, TIME_FORMAT)}` : `${camp.startTime} – ${camp.endTime}`))}
          </div>
          <div style="margin-top:8px;padding:8px 10px;border-radius:10px;border:1px solid var(--line);font-size:13px;color:var(--muted);">
            ${escapeHtml(t('publicPages.required'))} <strong style="font-weight:700;color:var(--brand);">${escapeHtml(camp.requiredBloodGroups || t('publicPages.allTypes'))}</strong>
          </div>
          <div style="margin-top:10px;">${captionHtml(`${escapeHtml(t('publicPages.organizedBy'))} ${escapeHtml(camp.organizerName)}`)}</div>
        </div>
      `;

      marker.bindPopup(popupContent, { maxWidth: 300 });
      marker.on('popupopen', () => {
        openCampRef.current = camp.id;
      });
      marker.on('popupclose', () => {
        if (openCampRef.current === camp.id) openCampRef.current = null;
      });
      marker.on('add', () => marker.getElement()?.setAttribute('aria-label', name));
      marker.addTo(layer);
      if (reopen === camp.id) marker.openPopup();
    });
  }, [ready, camps, t, format, statusLabel, resolved]);

  const legend: LegendItem[] = STATUS_ORDER.map((status) => ({
    ...STATUS[status],
    label: statusLabel(status),
    description: t(`pub.campStatusDesc.${status}`),
  }));

  return (
    <div className="overflow-hidden rounded-2xl border border-line bg-surface">
      <div className="relative">
        <div ref={containerRef} role="region" aria-label={t('pub.campsMapAriaLabel')} className="h-[460px] w-full sm:h-[600px]" />
        {failed && (
          <div role="alert" className="absolute inset-x-4 top-4 z-[400] flex items-start gap-2.5 rounded-xl border border-danger/30 bg-surface p-3 text-sm text-foreground">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-danger-text" aria-hidden="true" />
            {t('pub.campsMapFailed')}
          </div>
        )}
        {!failed && camps && camps.length === 0 && (
          <div role="status" className="absolute inset-x-4 top-4 z-[400] rounded-xl border border-line bg-surface p-3 text-sm text-gray-400">
            {t('pub.campsMapEmpty')}
          </div>
        )}
      </div>
      <MapLegend title={t('publicPages.campStatus')} items={legend} className="border-t border-line p-4 sm:p-5" />
    </div>
  );
}
