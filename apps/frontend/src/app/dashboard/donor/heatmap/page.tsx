'use client';

import { useEffect, useState } from 'react';
import { useAuthStore } from '@/store/authStore';
import apiClient from '@/lib/apiClient';
import SriLankaHeatmap, { DistrictSummary } from '@/components/maps/SriLankaHeatmap';
import { Activity, Users, AlertTriangle } from 'lucide-react';

export default function HeatmapPage() {
  const { accessToken } = useAuthStore();
  const [districts, setDistricts] = useState<DistrictSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;

    const fetchAnalytics = async () => {
      try {
        const res = await apiClient.get<any>('/analytics/district-summary');
        setDistricts(res.data?.data || res.data);
      } catch (err: any) {
        setError(err.response?.data?.message || 'Failed to load heatmap data');
      } finally {
        setLoading(false);
      }
    };

    fetchAnalytics();
  }, [accessToken]);

  const totalDonors = districts.reduce((acc, d) => acc + d.donorCount, 0);
  const totalRequests = districts.reduce((acc, d) => acc + d.activeRequests, 0);
  const criticalDistricts = districts.filter(d => d.shortageLevel === 'CRITICAL').length;

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white mb-2">Sri Lanka Supply Heatmap</h1>
          <p className="text-gray-400 text-sm max-w-xl">
            Live interactive view of district-level blood shortages. Critical zones indicate areas where active requisitions exceed available matched donors.
          </p>
        </div>
      </div>

      {loading ? (
        <div className="h-[520px] w-full flex items-center justify-center glass-card rounded-2xl border border-white/5">
          <div className="flex flex-col items-center gap-4">
            <div className="w-10 h-10 border-4 border-red-500 border-t-transparent rounded-full animate-spin"></div>
            <p className="text-gray-400 font-medium tracking-wide animate-pulse">Loading live intelligence...</p>
          </div>
        </div>
      ) : error ? (
        <div className="p-6 bg-red-500/10 border border-red-500/20 rounded-2xl text-red-400">
          <p className="font-semibold text-lg flex items-center gap-2">
            <AlertTriangle className="w-5 h-5" />
            Connection Error
          </p>
          <p className="text-sm mt-1">{error}</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="glass-card p-6 rounded-2xl border border-white/5">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-red-500/10 flex items-center justify-center text-red-400">
                  <AlertTriangle className="w-5 h-5" />
                </div>
                <h3 className="text-sm font-semibold text-gray-300">Critical Zones</h3>
              </div>
              <p className="text-3xl font-black text-white">{criticalDistricts}</p>
            </div>
            
            <div className="glass-card p-6 rounded-2xl border border-white/5">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-rose-500/10 flex items-center justify-center text-rose-400">
                  <Activity className="w-5 h-5" />
                </div>
                <h3 className="text-sm font-semibold text-gray-300">Active Requests</h3>
              </div>
              <p className="text-3xl font-black text-white">{totalRequests}</p>
            </div>

            <div className="glass-card p-6 rounded-2xl border border-white/5">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-10 h-10 rounded-lg bg-emerald-500/10 flex items-center justify-center text-emerald-400">
                  <Users className="w-5 h-5" />
                </div>
                <h3 className="text-sm font-semibold text-gray-300">Available Donors</h3>
              </div>
              <p className="text-3xl font-black text-white">{totalDonors}</p>
            </div>
          </div>

          <div className="w-full">
            <SriLankaHeatmap districts={districts} />
          </div>
        </>
      )}
    </div>
  );
}
