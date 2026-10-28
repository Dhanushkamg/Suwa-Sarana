'use client';

import { useEffect, useRef, useMemo, useState } from 'react';
import type * as Leaflet from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Radar } from 'lucide-react';
import { DISTRICT_COORDS } from '@/lib/districts';
import { useI18n } from '@/lib/i18n';
import { useTheme } from '@/components/theme/ThemeProvider';
import { escapeHtml } from './escapeHtml';
import { createSriLankaMap } from './leafletSetup';
import { TONE, captionHtml, readToken, statusMarkerHtml, titleHtml } from './mapStyles';

interface Props {
  hospitalName: string;
  district: string;
  radiusKm: number;
  latitude?: number;
  longitude?: number;
}

export default function RequestDistanceMap({ hospitalName, district, radiusKm, latitude, longitude }: Props) {
  const { t, format } = useI18n();
  const { resolved } = useTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<Leaflet.Map | null>(null);
  const leafletRef = useRef<typeof Leaflet | null>(null);
  const layerRef = useRef<Leaflet.LayerGroup | null>(null);
  const [ready, setReady] = useState(false);

  const coords = useMemo(() => {
    return latitude && longitude ? ([latitude, longitude] as [number, number]) : DISTRICT_COORDS[district] || [6.9271, 79.8612];
  }, [latitude, longitude, district]);

  // Map, created once
  useEffect(() => {
    let cancelled = false;
    let observer: ResizeObserver | undefined;
    let created: Leaflet.Map | undefined;

    (async () => {
      const L = (await import('leaflet')).default;
      if (cancelled || !containerRef.current) return;
      const { map, refit } = createSriLankaMap(L, containerRef.current, { zoomIn: t('pub.mapZoomIn'), zoomOut: t('pub.mapZoomOut') }, { maxZoom: 15 });
      created = map;
      leafletRef.current = L;
      mapRef.current = map;
      layerRef.current = L.layerGroup().addTo(map);
      // Only the minimum zoom follows the container size here; the view itself is fitted to the search circle below
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

  // Hospital marker and search circle
  useEffect(() => {
    const L = leafletRef.current;
    const map = mapRef.current;
    const layer = layerRef.current;
    if (!ready || !L || !map || !layer) return;

    layer.clearLayers();

    const label = t('pub.mapHospital');
    const icon = L.divIcon({
      className: 'hospital-marker',
      html: statusMarkerHtml({ shape: 'circle', glyph: 'plus', tone: 'primary', label: escapeHtml(label) }),
      iconSize: [36, 36],
      iconAnchor: [18, 18],
      popupAnchor: [0, -18],
    });

    const marker = L.marker(coords, { icon, title: `${hospitalName} (${label})`, keyboard: true });
    marker.bindPopup(`
      <div style="padding:14px 16px;min-width:180px;font-family:inherit;">
        ${titleHtml(escapeHtml(hospitalName))}
        ${captionHtml(escapeHtml(`${district} ${t('pub.mapDistrictSuffix')}`), '6px 0 0 0')}
        <div style="margin-top:10px;font-size:13px;font-weight:600;color:${TONE.primary.text};">${escapeHtml(t('pub.mapActiveRadius'))}: ${escapeHtml(format.number(radiusKm))} km</div>
      </div>
    `);
    marker.on('add', () => marker.getElement()?.setAttribute('aria-label', `${hospitalName} (${label})`));
    marker.addTo(layer);

    // Leaflet vector layers are SVG presentation attributes, where var() is not allowed: resolve the token for the current theme
    const colour = readToken('--primary', 'currentColor');
    const circle = L.circle(coords, {
      radius: radiusKm * 1000,
      color: colour,
      fillColor: colour,
      fillOpacity: 0.1,
      weight: 2,
      dashArray: '6, 6',
      interactive: false,
    });
    circle.addTo(layer);

    map.fitBounds(circle.getBounds(), { padding: [24, 24], animate: false });
  }, [ready, coords, hospitalName, district, radiusKm, t, format, resolved]);

  return (
    <div className="relative h-64 w-full overflow-hidden rounded-xl border border-line bg-surface">
      <div ref={containerRef} role="region" aria-label={t('pub.requestMapAriaLabel', { hospital: hospitalName })} className="h-full w-full" />
      <div className="absolute right-3 top-3 z-[400] flex max-w-[calc(100%-1.5rem)] items-center gap-2 rounded-full border border-line-strong bg-surface px-3 py-1.5 text-xs font-medium text-primary-text">
        <Radar className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span>{t('pub.mapScanning', { radius: format.number(radiusKm) })}</span>
      </div>
    </div>
  );
}
