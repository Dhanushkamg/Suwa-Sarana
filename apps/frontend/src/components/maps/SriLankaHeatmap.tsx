'use client';

import { useEffect, useRef } from 'react';
import 'leaflet/dist/leaflet.css';
import { useI18n } from '@/lib/i18n';
import { escapeHtml } from './escapeHtml';
import { TONE_VAR, captionHtml, cellHtml, markerHtml, pillHtml, titleHtml, type MapTone } from './mapStyles';

export interface DistrictSummary {
  district: string;
  latitude: number;
  longitude: number;
  donorCount: number;
  activeRequests: number;
  fulfilledRequests: number;
  totalRequests: number;
  fulfillmentRate: number;
  shortageLevel: 'CRITICAL' | 'WARNING' | 'BALANCED' | 'SURPLUS';
}

interface Props {
  districts: DistrictSummary[];
  filter?: string;
  onSelectDistrict?: (district: DistrictSummary) => void;
}

export default function SriLankaHeatmap({ districts, filter = 'ALL', onSelectDistrict }: Props) {
  const { t } = useI18n();
  const mapContainerRef = useRef<HTMLDivElement>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const mapInstanceRef = useRef<any>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const markersLayerRef = useRef<any>(null);

  useEffect(() => {
    let isMounted = true;

    async function initMap() {
      if (!mapContainerRef.current) return;
      const L = (await import('leaflet')).default;

      if (!mapInstanceRef.current && isMounted) {
        const map = L.map(mapContainerRef.current, {
          center: [7.8731, 80.7718], // Sri Lanka Centroid
          zoom: 7.5,
          minZoom: 7,
          maxZoom: 12,
          zoomControl: true,
          attributionControl: false,
        });

        // OpenStreetMap tiles with dark CSS filter
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19,
          className: 'dark-map-tiles',
        }).addTo(map);

        const markersLayer = L.layerGroup().addTo(map);
        mapInstanceRef.current = map;
        markersLayerRef.current = markersLayer;
      }

      if (mapInstanceRef.current && markersLayerRef.current) {
        markersLayerRef.current.clearLayers();

        const filtered = districts.filter((d) => {
          if (filter === 'ALL') return true;
          return d.shortageLevel === filter;
        });

        filtered.forEach((d) => {
          const meta: { tone: MapTone; ring: string; label: string } =
            d.shortageLevel === 'CRITICAL'
              ? { tone: 'ember', ring: 'marker-pulse-critical', label: t('pub.mapCritical') }
              : d.shortageLevel === 'WARNING'
              ? { tone: 'saffron', ring: 'marker-pulse-warning', label: t('pub.mapModerate') }
              : d.shortageLevel === 'SURPLUS'
              ? { tone: 'bone', ring: '', label: t('pub.mapSurplus') }
              : { tone: 'sage', ring: '', label: t('pub.mapBalanced') };

          const customIcon = L.divIcon({
            className: 'custom-district-marker',
            html: markerHtml(meta.tone, String(d.activeRequests > 0 ? d.activeRequests : d.donorCount), meta.ring),
            iconSize: [36, 36],
            iconAnchor: [18, 18],
            popupAnchor: [0, -18],
          });

          const marker = L.marker([d.latitude, d.longitude], { icon: customIcon });

          const rate = Math.max(0, Math.min(100, Number(d.fulfillmentRate) || 0));
          const popupContent = `
            <div style="padding:16px;min-width:230px;font-family:inherit;color:var(--foreground);">
              <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin-bottom:6px;">
                ${titleHtml(escapeHtml(d.district))}
                ${pillHtml(meta.tone, escapeHtml(d.shortageLevel))}
              </div>
              ${captionHtml(escapeHtml(meta.label), '0 0 14px 0')}
              <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:10px;">
                ${cellHtml(escapeHtml(t('pub.mapActiveRequests')), escapeHtml(d.activeRequests), TONE_VAR.ember)}
                ${cellHtml(escapeHtml(t('pub.mapAvailableDonors')), escapeHtml(d.donorCount), TONE_VAR.sage)}
              </div>
              <div style="padding:10px;border-radius:10px;border:1px solid var(--line);font-size:12px;color:var(--muted);">
                <div style="display:flex;justify-content:space-between;gap:10px;margin-bottom:6px;">
                  <span>${escapeHtml(t('pub.mapFulfillment'))}</span>
                  <span style="font-family:var(--font-mono);font-weight:600;color:var(--foreground);">${escapeHtml(rate)}%</span>
                </div>
                <div style="width:100%;height:3px;background:color-mix(in oklch, var(--foreground) 12%, transparent);border-radius:99px;overflow:hidden;">
                  <div style="width:${rate}%;height:100%;background:var(--saffron);"></div>
                </div>
              </div>
            </div>
          `;

          marker.bindPopup(popupContent);
          marker.on('click', () => {
            onSelectDistrict?.(d);
          });

          marker.addTo(markersLayerRef.current);
        });
      }
    }

    initMap();

    return () => {
      isMounted = false;
    };
  }, [districts, filter, onSelectDistrict, t]);

  return (
    <div className="relative h-[420px] w-full overflow-hidden rounded-2xl border border-line bg-background sm:h-[520px]">
      <div ref={mapContainerRef} className="h-full w-full" />

      {/* Legend overlay */}
      <div className="pointer-events-auto absolute bottom-4 left-4 z-[400] max-w-[calc(100%-2rem)] space-y-2 rounded-xl border border-line-strong bg-surface/95 p-3.5 text-xs">
        <div className="eyebrow mb-1 text-[10px]">{t('pub.mapLegendTitle')}</div>
        <div className="flex items-center gap-2.5 text-gray-300">
          <span className="relative flex h-2 w-2 shrink-0" aria-hidden="true">
            <span className="dot-ping absolute inset-0 rounded-full bg-ember" />
            <span className="relative h-2 w-2 rounded-full bg-ember" />
          </span>
          <span>{t('pub.mapLegendCritical')}</span>
        </div>
        <div className="flex items-center gap-2.5 text-gray-300">
          <span className="h-2 w-2 shrink-0 rounded-full bg-saffron" aria-hidden="true" />
          <span>{t('pub.mapLegendWarning')}</span>
        </div>
        <div className="flex items-center gap-2.5 text-gray-300">
          <span className="h-2 w-2 shrink-0 rounded-full bg-emerald-400" aria-hidden="true" />
          <span>{t('pub.mapLegendBalanced')}</span>
        </div>
      </div>
    </div>
  );
}
