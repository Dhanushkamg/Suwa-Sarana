'use client';

import { useEffect, useRef, useState } from 'react';
import 'leaflet/dist/leaflet.css';

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
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null);
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

        // CartoDB Dark Matter tiles
        L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
          maxZoom: 19,
          subdomains: 'abcd',
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
          const colorClass =
            d.shortageLevel === 'CRITICAL'
              ? { bg: '#ef4444', ring: 'marker-pulse-critical', text: 'text-red-400', label: 'CRITICAL DEFICIT' }
              : d.shortageLevel === 'WARNING'
              ? { bg: '#f59e0b', ring: 'marker-pulse-warning', text: 'text-amber-400', label: 'MODERATE SHORTAGE' }
              : d.shortageLevel === 'SURPLUS'
              ? { bg: '#06b6d4', ring: '', text: 'text-cyan-400', label: 'SURPLUS POOL' }
              : { bg: '#10b981', ring: '', text: 'text-emerald-400', label: 'BALANCED' };

          // Custom HTML Marker Icon
          const customIcon = L.divIcon({
            className: 'custom-district-marker',
            html: `
              <div style="position: relative; width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; cursor: pointer;">
                ${
                  colorClass.ring
                    ? `<div class="${colorClass.ring}" style="position: absolute; width: 36px; height: 36px; border-radius: 50%; background: ${colorClass.bg}; opacity: 0.5;"></div>`
                    : ''
                }
                <div style="width: 26px; height: 26px; border-radius: 50%; background: #0f0f1a; border: 2px solid ${colorClass.bg}; box-shadow: 0 0 12px ${colorClass.bg}80; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 800; color: #ffffff; z-index: 10;">
                  ${d.activeRequests > 0 ? d.activeRequests : d.donorCount}
                </div>
              </div>
            `,
            iconSize: [36, 36],
            iconAnchor: [18, 18],
            popupAnchor: [0, -18],
          });

          const marker = L.marker([d.latitude, d.longitude], { icon: customIcon });

          const popupContent = `
            <div style="padding: 16px; min-width: 220px; font-family: inherit;">
              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                <h4 style="font-weight: 800; font-size: 15px; color: #ffffff; margin: 0;">${d.district}</h4>
                <span style="font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 6px; background: ${colorClass.bg}20; color: ${colorClass.bg}; border: 1px solid ${colorClass.bg}40;">
                  ${d.shortageLevel}
                </span>
              </div>
              <p style="font-size: 11px; color: #9ca3af; margin: 0 0 12px 0;">${colorClass.label}</p>
              
              <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 12px;">
                <div style="background: rgba(255,255,255,0.05); padding: 8px; border-radius: 8px; border: 1px solid rgba(255,255,255,0.08);">
                  <div style="font-size: 10px; color: #9ca3af;">Active Requisitions</div>
                  <div style="font-size: 14px; font-weight: 800; color: #ef4444;">${d.activeRequests}</div>
                </div>
                <div style="background: rgba(255,255,255,0.05); padding: 8px; border-radius: 8px; border: 1px solid rgba(255,255,255,0.08);">
                  <div style="font-size: 10px; color: #9ca3af;">Available Donors</div>
                  <div style="font-size: 14px; font-weight: 800; color: #10b981;">${d.donorCount}</div>
                </div>
              </div>

              <div style="background: rgba(255,255,255,0.03); padding: 8px; border-radius: 8px; font-size: 11px; color: #d1d5db;">
                <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
                  <span>Fulfillment Rate:</span>
                  <span style="font-weight: 700; color: #38bdf8;">${d.fulfillmentRate}%</span>
                </div>
                <div style="width: 100%; height: 4px; background: rgba(255,255,255,0.1); border-radius: 99px; overflow: hidden;">
                  <div style="width: ${d.fulfillmentRate}%; height: 100%; background: #38bdf8;"></div>
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
  }, [districts, filter, onSelectDistrict]);

  return (
    <div className="relative w-full h-[520px] rounded-2xl overflow-hidden border border-white/10 shadow-2xl bg-[#09090e]">
      <div ref={mapContainerRef} className="w-full h-full" />
      
      {/* Legend Overlay */}
      <div className="absolute bottom-4 left-4 z-[400] glass-card p-3 rounded-xl border border-white/10 text-xs space-y-1.5 shadow-xl pointer-events-auto">
        <div className="font-bold text-gray-200 text-[11px] uppercase tracking-wider mb-1">
          Shortage Status
        </div>
        <div className="flex items-center gap-2 text-gray-300">
          <span className="w-2.5 h-2.5 rounded-full bg-red-500 animate-pulse"></span>
          <span>Critical Shortage (Active &gt; Donors)</span>
        </div>
        <div className="flex items-center gap-2 text-gray-300">
          <span className="w-2.5 h-2.5 rounded-full bg-amber-500"></span>
          <span>Warning (Low donor ratio)</span>
        </div>
        <div className="flex items-center gap-2 text-gray-300">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
          <span>Balanced / Sufficient</span>
        </div>
      </div>
    </div>
  );
}
