'use client';

import { useEffect, useRef } from 'react';
import 'leaflet/dist/leaflet.css';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';

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
          const color = isScheduled ? '#ef4444' : '#6b7280';
          const pulse = isScheduled ? 'marker-pulse-critical' : '';

          const customIcon = L.divIcon({
            className: 'custom-district-marker',
            html: `
              <div style="position: relative; width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; cursor: pointer;">
                ${pulse ? `<div class="${pulse}" style="position: absolute; width: 36px; height: 36px; border-radius: 50%; background: ${color}; opacity: 0.4;"></div>` : ''}
                <div style="
                  width: 26px; height: 26px; border-radius: 50%;
                  background: #0f0f1a;
                  border: 2px solid ${color};
                  box-shadow: 0 0 12px ${color}80;
                  display: flex; align-items: center; justify-content: center;
                  font-size: 11px; font-weight: 800; color: #ffffff; z-index: 10;
                ">🩸</div>
              </div>
            `,
            iconSize: [36, 36],
            iconAnchor: [18, 18],
            popupAnchor: [0, -20],
          });

          const marker = L.marker([camp.latitude, camp.longitude], { icon: customIcon });

          const popupContent = `
            <div style="padding: 14px; min-width: 240px; font-family: inherit; background: #0f0f1a; border-radius: 12px;">
              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                <h4 style="font-weight: 800; font-size: 14px; color: #ffffff; margin: 0;">${camp.name}</h4>
                <span style="font-size: 9px; font-weight: 700; padding: 2px 6px; border-radius: 6px; background: ${color}20; color: ${color}; border: 1px solid ${color}40;">
                  ${camp.status}
                </span>
              </div>
              <div style="font-size: 12px; color: #9ca3af; margin-bottom: 10px;">📍 ${camp.location}, ${camp.district}</div>
              <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 6px; font-size: 11px;">
                <div style="background: rgba(255,255,255,0.05); padding: 6px 8px; border-radius: 8px; border: 1px solid rgba(255,255,255,0.08);">
                  <div style="color: #6b7280; margin-bottom: 2px;">${t('publicPages.date')}</div>
                  <div style="color: #e5e7eb; font-weight: 700;">${camp.scheduledDate}</div>
                </div>
                <div style="background: rgba(255,255,255,0.05); padding: 6px 8px; border-radius: 8px; border: 1px solid rgba(255,255,255,0.08);">
                  <div style="color: #6b7280; margin-bottom: 2px;">${t('publicPages.time')}</div>
                  <div style="color: #e5e7eb; font-weight: 700;">${camp.startTime} – ${camp.endTime}</div>
                </div>
              </div>
              <div style="margin-top: 8px; background: rgba(255,255,255,0.03); padding: 6px 8px; border-radius: 8px; font-size: 11px; color: #d1d5db; border: 1px solid rgba(255,255,255,0.06);">
                🩸 ${t('publicPages.required')} <strong style="color: #f87171;">${camp.requiredBloodGroups || t('publicPages.allTypes')}</strong>
              </div>
              <div style="margin-top: 6px; font-size: 10px; color: #6b7280;">${t('publicPages.organizedBy')} ${camp.organizerName}</div>
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
  }, []);

  return (
    <div className="relative w-full h-[600px] rounded-2xl overflow-hidden border border-white/10 shadow-2xl bg-[#09090e]">
      <div ref={mapContainerRef} className="w-full h-full" />

      {/* Legend Overlay */}
      <div className="absolute bottom-4 left-4 z-[400] glass-card p-3 rounded-xl border border-white/10 text-xs space-y-1.5 shadow-xl">
        <div className="font-bold text-gray-200 text-[11px] uppercase tracking-wider mb-1">
          {t('publicPages.campStatus')}
        </div>
        <div className="flex items-center gap-2 text-gray-300">
          <span className="w-2.5 h-2.5 rounded-full bg-red-500 animate-pulse"></span>
          <span>{t('publicPages.scheduledActive')}</span>
        </div>
        <div className="flex items-center gap-2 text-gray-300">
          <span className="w-2.5 h-2.5 rounded-full bg-gray-500"></span>
          <span>{t('publicPages.cancelledCompleted')}</span>
        </div>
      </div>
    </div>
  );
}
