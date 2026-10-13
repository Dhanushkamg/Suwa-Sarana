'use client';

import { useEffect, useRef, useMemo } from 'react';
import 'leaflet/dist/leaflet.css';
import { DISTRICT_COORDS } from '@/lib/districts';
import { useI18n } from '@/lib/i18n';
import { escapeHtml } from './escapeHtml';
import { captionHtml, resolvedTone, titleHtml } from './mapStyles';

interface Props {
  hospitalName: string;
  district: string;
  radiusKm: number;
  latitude?: number;
  longitude?: number;
}

export default function RequestDistanceMap({
  hospitalName,
  district,
  radiusKm,
  latitude,
  longitude,
}: Props) {
  const { t } = useI18n();
  const mapContainerRef = useRef<HTMLDivElement>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const mapInstanceRef = useRef<any>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const circleLayerRef = useRef<any>(null);

  const coords = useMemo(() => {
    return latitude && longitude
      ? [latitude, longitude] as [number, number]
      : DISTRICT_COORDS[district] || [6.9271, 79.8612];
  }, [latitude, longitude, district]);

  useEffect(() => {
    let isMounted = true;

    async function initMap() {
      if (!mapContainerRef.current) return;
      const L = (await import('leaflet')).default;

      if (!mapInstanceRef.current && isMounted) {
        const map = L.map(mapContainerRef.current, {
          center: coords,
          zoom: radiusKm > 30 ? 9 : radiusKm > 15 ? 10 : 11,
          zoomControl: false,
          attributionControl: false,
        });

        // OpenStreetMap tiles with dark CSS filter
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19,
          className: 'dark-map-tiles',
        }).addTo(map);

        const circleLayer = L.layerGroup().addTo(map);
        mapInstanceRef.current = map;
        circleLayerRef.current = circleLayer;
      }

      if (mapInstanceRef.current && circleLayerRef.current) {
        circleLayerRef.current.clearLayers();
        mapInstanceRef.current.setView(coords, radiusKm > 30 ? 9 : radiusKm > 15 ? 10 : 11);

        // Hospital marker: ember disc with a plain cross
        const hospitalIcon = L.divIcon({
          className: 'hospital-marker',
          html: `
            <div style="width:32px;height:32px;border-radius:50%;background:var(--ember);border:2px solid var(--foreground);display:flex;align-items:center;justify-content:center;">
              <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><path d="M7 1.5v11M1.5 7h11" stroke="var(--foreground)" stroke-width="2.4" stroke-linecap="round" fill="none"/></svg>
            </div>
          `,
          iconSize: [32, 32],
          iconAnchor: [16, 16],
          popupAnchor: [0, -16],
        });

        const marker = L.marker(coords, { icon: hospitalIcon });
        marker.bindPopup(`
          <div style="padding:14px 16px;min-width:180px;font-family:inherit;">
            ${titleHtml(escapeHtml(hospitalName))}
            ${captionHtml(escapeHtml(`${district} ${t('pub.mapDistrictSuffix')}`), '6px 0 0 0')}
            <div style="margin-top:10px;font-family:var(--font-mono);font-size:11px;letter-spacing:0.04em;color:var(--sage);">${escapeHtml(t('pub.mapActiveRadius'))}: ${escapeHtml(radiusKm)} km</div>
          </div>
        `);
        marker.addTo(circleLayerRef.current);

        // Search Radius Circle
        const radiusMeters = radiusKm * 1000;
        const sage = resolvedTone('sage');
        const circle = L.circle(coords, {
          radius: radiusMeters,
          color: sage,
          fillColor: sage,
          fillOpacity: 0.1,
          weight: 1.5,
          dashArray: '6, 6',
        });
        circle.addTo(circleLayerRef.current);
      }
    }

    initMap();

    return () => {
      isMounted = false;
    };
  }, [coords, hospitalName, district, radiusKm, t]);

  return (
    <div className="relative h-64 w-full overflow-hidden rounded-xl border border-line bg-background">
      <div ref={mapContainerRef} className="h-full w-full" />
      <div className="absolute right-3 top-3 z-[400] flex max-w-[calc(100%-1.5rem)] items-center gap-2 rounded-full border border-line-strong bg-surface/95 px-3 py-1.5 font-mono text-[11px] tracking-wide text-emerald-400">
        <span className="relative flex h-2 w-2 shrink-0" aria-hidden="true">
          <span className="dot-ping absolute inset-0 rounded-full bg-emerald-400" />
          <span className="relative h-2 w-2 rounded-full bg-emerald-400" />
        </span>
        <span>{t('pub.mapScanning', { radius: radiusKm })}</span>
      </div>
    </div>
  );
}
