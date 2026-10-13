'use client';

import { useEffect, useRef } from 'react';
import 'leaflet/dist/leaflet.css';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { escapeHtml } from '@/components/maps/escapeHtml';
import { TONE_VAR, captionHtml, cellHtml, markerHtml, pillHtml, titleHtml, type MapTone } from '@/components/maps/mapStyles';

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

export default function AdminMap() {
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

      if (!isMounted) return;

      if (!mapInstanceRef.current) {
        const map = L.map(mapContainerRef.current, {
          center: [7.8731, 80.7718],
          zoom: 7.5,
          minZoom: 6,
          maxZoom: 14,
          zoomControl: true,
          attributionControl: false,
        });

        // Same tile source as SriLankaHeatmap
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19,
          className: 'dark-map-tiles',
        }).addTo(map);

        const markersLayer = L.layerGroup().addTo(map);
        mapInstanceRef.current = map;
        markersLayerRef.current = markersLayer;
      }

      // Fetch camps and plot markers
      try {
        const res = await apiClient.get<Camp[]>('/camps');
        const camps: Camp[] = res.data;

        if (!isMounted || !markersLayerRef.current) return;
        markersLayerRef.current.clearLayers();

        camps.forEach((camp) => {
          if (!camp.latitude || !camp.longitude) return;

          const isScheduled = camp.status === 'SCHEDULED';
          const tone: MapTone = isScheduled ? 'ember' : 'muted';
          const pulse = isScheduled ? 'marker-pulse-critical' : '';

          const customIcon = L.divIcon({
            className: 'custom-district-marker',
            html: markerHtml(
              tone,
              `<svg width="12" height="14" viewBox="0 0 32 32" aria-hidden="true"><path d="M16 2.5c-3.3 4.6-9 10.2-9 16.1a9 9 0 0 0 18 0C25 12.7 19.3 7.1 16 2.5Z" fill="${TONE_VAR[tone]}"/></svg>`,
              pulse
            ),
            iconSize: [36, 36],
            iconAnchor: [18, 18],
            popupAnchor: [0, -20],
          });

          const marker = L.marker([camp.latitude, camp.longitude], { icon: customIcon });

          const popupContent = `
            <div style="padding:16px;min-width:240px;font-family:inherit;color:var(--foreground);">
              <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin-bottom:6px;">
                ${titleHtml(escapeHtml(camp.name))}
                ${pillHtml(tone, escapeHtml(camp.status))}
              </div>
              <div style="font-size:12px;line-height:1.5;color:var(--muted);margin-bottom:12px;overflow-wrap:anywhere;">${escapeHtml(camp.location)}, ${escapeHtml(camp.district)}</div>
              <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
                ${cellHtml(escapeHtml(t('publicPages.date')), escapeHtml(camp.scheduledDate))}
                ${cellHtml(escapeHtml(t('publicPages.time')), `${escapeHtml(camp.startTime)} – ${escapeHtml(camp.endTime)}`)}
              </div>
              <div style="margin-top:8px;padding:8px 10px;border-radius:10px;border:1px solid var(--line);font-size:12px;color:var(--muted);">
                ${escapeHtml(t('publicPages.required'))} <strong style="font-family:var(--font-mono);font-weight:600;color:var(--ember);">${escapeHtml(camp.requiredBloodGroups || t('publicPages.allTypes'))}</strong>
              </div>
              <div style="margin-top:10px;">${captionHtml(`${escapeHtml(t('publicPages.organizedBy'))} ${escapeHtml(camp.organizerName)}`)}</div>
            </div>
          `;

          marker.bindPopup(popupContent, {
            className: 'dark-popup',
            maxWidth: 280,
          });
          marker.addTo(markersLayerRef.current);
        });
      } catch (err) {
        console.error('Failed to load camps for map:', err);
      }
    }

    initMap();

    return () => {
      isMounted = false;
    };
  }, [t]);

  return (
    <div className="relative h-[460px] w-full overflow-hidden rounded-2xl border border-line bg-background sm:h-[600px]">
      <div ref={mapContainerRef} className="h-full w-full" />

      {/* Legend overlay */}
      <div className="absolute bottom-4 left-4 z-[400] max-w-[calc(100%-2rem)] space-y-2 rounded-xl border border-line-strong bg-surface/95 p-3.5 text-xs">
        <div className="eyebrow mb-1 text-[10px]">{t('publicPages.campStatus')}</div>
        <div className="flex items-center gap-2.5 text-gray-300">
          <span className="relative flex h-2 w-2 shrink-0" aria-hidden="true">
            <span className="dot-ping absolute inset-0 rounded-full bg-ember" />
            <span className="relative h-2 w-2 rounded-full bg-ember" />
          </span>
          <span>{t('publicPages.scheduledActive')}</span>
        </div>
        <div className="flex items-center gap-2.5 text-gray-300">
          <span className="h-2 w-2 shrink-0 rounded-full bg-gray-500" aria-hidden="true" />
          <span>{t('publicPages.cancelledCompleted')}</span>
        </div>
      </div>
    </div>
  );
}
