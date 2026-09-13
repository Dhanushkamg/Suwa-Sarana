'use client';

import { useEffect, useRef, useMemo } from 'react';
import 'leaflet/dist/leaflet.css';

// District fallback coordinates in Sri Lanka
const DISTRICT_COORDS: Record<string, [number, number]> = {
  Colombo: [6.9271, 79.8612],
  Gampaha: [7.084, 80.0098],
  Kalutara: [6.5854, 79.9607],
  Kandy: [7.2906, 80.6337],
  Matale: [7.4675, 80.6234],
  'Nuwara Eliya': [6.9497, 80.7891],
  Galle: [6.0535, 80.221],
  Matara: [5.9549, 80.555],
  Hambantota: [6.1429, 81.1212],
  Jaffna: [9.6615, 80.0255],
  Kilinochchi: [9.3803, 80.377],
  Mannar: [8.981, 79.9044],
  Vavuniya: [8.7542, 80.4982],
  Mullaitivu: [9.2671, 80.8143],
  Batticaloa: [7.731, 81.6747],
  Ampara: [7.2912, 81.6724],
  Trincomalee: [8.5874, 81.2152],
  Kurunegala: [7.4863, 80.3623],
  Puttalam: [8.0408, 79.8394],
  Anuradhapura: [8.3114, 80.4037],
  Polonnaruwa: [7.9403, 81.0188],
  Badulla: [6.9934, 81.055],
  Monaragala: [6.8728, 81.3507],
  Ratnapura: [6.6828, 80.3992],
  Kegalle: [7.2513, 80.3464],
};

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

        // Hospital Marker Icon
        const hospitalIcon = L.divIcon({
          className: 'hospital-marker',
          html: `
            <div style="width: 32px; height: 32px; border-radius: 50%; background: #ef4444; border: 3px solid #ffffff; box-shadow: 0 0 20px rgba(239,68,68,0.8); display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; font-size: 14px;">
              🏥
            </div>
          `,
          iconSize: [32, 32],
          iconAnchor: [16, 16],
          popupAnchor: [0, -16],
        });

        const marker = L.marker(coords, { icon: hospitalIcon });
        marker.bindPopup(`
          <div style="padding: 12px; font-family: inherit;">
            <div style="font-weight: 800; color: #ffffff; font-size: 13px;">${hospitalName}</div>
            <div style="font-size: 11px; color: #9ca3af; margin-top: 2px;">${district} District</div>
            <div style="margin-top: 8px; font-size: 10px; font-weight: 700; color: #34d399;">Active Radius: ${radiusKm} km</div>
          </div>
        `);
        marker.addTo(circleLayerRef.current);

        // Search Radius Circle
        const radiusMeters = radiusKm * 1000;
        const circle = L.circle(coords, {
          radius: radiusMeters,
          color: '#10b981',
          fillColor: '#10b981',
          fillOpacity: 0.12,
          weight: 2,
          dashArray: '6, 6',
        });
        circle.addTo(circleLayerRef.current);
      }
    }

    initMap();

    return () => {
      isMounted = false;
    };
  }, [coords, hospitalName, district, radiusKm]);

  return (
    <div className="relative w-full h-64 rounded-xl overflow-hidden border border-white/10 bg-[#09090e]">
      <div ref={mapContainerRef} className="w-full h-full" />
      <div className="absolute top-3 right-3 z-[400] glass-card px-3 py-1.5 rounded-lg border border-white/10 text-xs font-semibold text-emerald-400 flex items-center gap-1.5 shadow-lg">
        <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping"></span>
        <span>Scanning {radiusKm} km Perimeter</span>
      </div>
    </div>
  );
}
