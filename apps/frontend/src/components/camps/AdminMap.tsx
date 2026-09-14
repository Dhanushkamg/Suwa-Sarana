'use client';

import { useEffect, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import apiClient from '@/lib/apiClient';
import { Calendar, MapPin, Users, Heart } from 'lucide-react';

// Fix Leaflet's default icon issue with Webpack by using a custom HTML div icon
const customMarkerIcon = L.divIcon({
  className: 'custom-map-marker',
  html: `<div style="background-color: #ef4444; width: 16px; height: 16px; border-radius: 50%; border: 3px solid white; box-shadow: 0 0 10px rgba(239,68,68,0.8);"></div>`,
  iconSize: [16, 16],
  iconAnchor: [8, 8],
  popupAnchor: [0, -10],
});

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
  const [camps, setCamps] = useState<Camp[]>([]);

  useEffect(() => {
    // Admin can fetch all upcoming camps
    apiClient.get('/camps')
      .then(res => setCamps(res.data))
      .catch(err => console.error(err));
  }, []);

  return (
    <div className="w-full h-[600px] rounded-2xl overflow-hidden border border-white/10 shadow-2xl relative">
      <MapContainer 
        center={[7.8731, 80.7718]} 
        zoom={7} 
        style={{ height: '100%', width: '100%', background: '#0f172a' }}
        scrollWheelZoom={true}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
          url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
        />
        
        {camps.map(camp => (
          camp.latitude && camp.longitude ? (
            <Marker 
              key={camp.id} 
              position={[camp.latitude, camp.longitude]}
              icon={customMarkerIcon}
            >
              <Popup className="custom-popup">
                <div className="p-1 space-y-2">
                  <h3 className="font-bold text-gray-900 text-lg border-b pb-2">{camp.name}</h3>
                  <div className="flex items-center gap-2 text-sm text-gray-600">
                    <MapPin className="w-4 h-4 text-rose-500" /> {camp.location}
                  </div>
                  <div className="flex items-center gap-2 text-sm text-gray-600">
                    <Calendar className="w-4 h-4 text-amber-500" /> {camp.scheduledDate} ({camp.startTime})
                  </div>
                  <div className="flex items-center gap-2 text-sm text-gray-600">
                    <Heart className="w-4 h-4 text-red-500" /> Required: {camp.requiredBloodGroups || 'All Types'}
                  </div>
                  <div className="flex items-center gap-2 text-sm text-gray-600 border-t pt-2 mt-2">
                    <Users className="w-4 h-4 text-blue-500" /> Org: {camp.organizerName}
                  </div>
                </div>
              </Popup>
            </Marker>
          ) : null
        ))}
      </MapContainer>
    </div>
  );
}
