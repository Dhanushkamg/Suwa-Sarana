'use client';

import dynamic from 'next/dynamic';
import { Map, MapPin, Calendar, Heart } from 'lucide-react';

const AdminMap = dynamic(() => import('@/components/camps/AdminMap'), { ssr: false });

export default function AdminCampsPage() {
  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Header */}
      <div className="relative glass-card rounded-3xl p-8 border border-blue-500/20 overflow-hidden bg-gradient-to-r from-blue-950/40 via-indigo-950/20 to-neutral-900/50">
        <div className="absolute top-0 right-0 w-96 h-96 bg-blue-500/10 rounded-full blur-3xl pointer-events-none" />
        
        <div className="relative flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div className="flex items-start gap-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center shadow-xl shadow-blue-500/30 flex-shrink-0">
              <Map className="w-8 h-8 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-500/20 text-blue-300 border border-blue-500/30 uppercase tracking-wider">
                  Admin Map View
                </span>
              </div>
              <h1 className="text-3xl font-bold text-white">Geospatial Camp Tracking</h1>
              <p className="text-gray-400 text-sm mt-1 max-w-xl">
                Monitor all national blood donation drives in real-time across the country.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Map Section */}
      <div className="glass-card rounded-3xl p-6 border border-white/5">
        <div className="flex items-center gap-6 mb-6">
          <div className="flex items-center gap-2 text-sm text-gray-400 font-medium">
            <span className="w-3 h-3 rounded-full bg-blue-500" /> Active Camps
          </div>
          <div className="flex items-center gap-2 text-sm text-gray-400 font-medium">
            <span className="w-3 h-3 rounded-full bg-red-500" /> Critical Shortages
          </div>
        </div>
        
        <AdminMap />
      </div>
    </div>
  );
}
