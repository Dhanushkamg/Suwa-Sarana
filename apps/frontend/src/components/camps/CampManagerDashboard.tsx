'use client';

import { useState, useEffect, useCallback } from 'react';
import { Calendar, Plus, Clock, MapPin, Share2, AlertCircle, RefreshCw } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import apiClient from '@/lib/apiClient';
import { Button } from '@/components/ui/Button';
import SlotManager from '@/components/slots/SlotManager';
import CampPostGenerator from './CampPostGenerator';

interface DonationCampDto {
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

export default function CampManagerDashboard() {
  const { user } = useAuthStore();
  const [camps, setCamps] = useState<DonationCampDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [activeCampForSlots, setActiveCampForSlots] = useState<number | null>(null);
  const [campToGeneratePost, setCampToGeneratePost] = useState<DonationCampDto | null>(null);

  const loadCamps = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get('/camps/my-camps');
      setCamps(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load your donation camps.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (user) {
      loadCamps();
    }
  }, [user, loadCamps]);

  if (!user) return null;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-white flex items-center gap-2">
          <Calendar className="w-5 h-5 text-rose-500" />
          My Managed Donation Camps
        </h2>
        <div className="flex items-center gap-3">
          <button
            onClick={loadCamps}
            disabled={loading}
            className="inline-flex items-center gap-2 px-3 py-2 rounded-xl bg-white/5 hover:bg-white/10 text-gray-300 text-xs font-medium transition-colors border border-white/10"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <Button size="sm" className="bg-rose-600 hover:bg-rose-500 text-white rounded-xl shadow-lg shadow-rose-900/20">
            <Plus className="w-4 h-4 mr-1" /> Create Camp
          </Button>
        </div>
      </div>

      {error && (
        <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{error}</span>
        </div>
      )}

      {loading ? (
        <div className="glass-card rounded-2xl p-12 text-center text-gray-400">
          <div className="inline-block w-8 h-8 border-2 border-rose-500 border-t-transparent rounded-full animate-spin mb-4" />
          <p>Loading your campaigns...</p>
        </div>
      ) : camps.length === 0 ? (
        <div className="glass-card rounded-2xl p-12 text-center border-dashed border-white/10 space-y-3">
          <Calendar className="w-12 h-12 text-gray-600 mx-auto" />
          <p className="text-white font-medium">No donation camps organized yet</p>
          <p className="text-sm text-gray-400">Create a new blood drive to start recruiting donors.</p>
        </div>
      ) : (
        <div className="grid gap-6">
          {camps.map((camp) => (
            <div key={camp.id} className="glass-card rounded-2xl border border-white/5 overflow-hidden flex flex-col">
              <div className="p-6 flex flex-col md:flex-row gap-6 md:items-center justify-between">
                <div>
                  <div className="flex items-center gap-3 mb-2">
                    <h3 className="text-xl font-bold text-white">{camp.name}</h3>
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${
                      camp.status === 'SCHEDULED' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' :
                      camp.status === 'CANCELLED' ? 'bg-red-500/20 text-red-400 border border-red-500/30' :
                      'bg-gray-500/20 text-gray-400 border border-gray-500/30'
                    }`}>
                      {camp.status}
                    </span>
                  </div>
                  <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-gray-400 mt-3">
                    <span className="flex items-center gap-1.5 text-gray-300">
                      <Calendar className="w-4 h-4 text-rose-500" />
                      {new Date(camp.scheduledDate).toLocaleDateString()}
                    </span>
                    <span className="flex items-center gap-1.5 text-gray-300">
                      <Clock className="w-4 h-4 text-amber-500" />
                      {camp.startTime} - {camp.endTime}
                    </span>
                    <span className="flex items-center gap-1.5 text-gray-300">
                      <MapPin className="w-4 h-4 text-emerald-500" />
                      {camp.location}, {camp.district}
                    </span>
                  </div>
                </div>

                <div className="flex flex-wrap gap-3">
                  <Button 
                    variant="secondary" 
                    size="sm"
                    className="h-9 px-4 text-xs font-semibold bg-white/5 hover:bg-white/10 border border-white/10 text-white"
                    onClick={() => setCampToGeneratePost(camp)}
                  >
                    <Share2 className="w-3.5 h-3.5 mr-1.5 text-blue-400" />
                    Share Poster
                  </Button>
                  <Button 
                    variant="secondary"
                    size="sm"
                    className={`h-9 px-4 text-xs font-semibold transition-colors ${activeCampForSlots === camp.id ? 'bg-rose-600 border-rose-600 text-white' : 'bg-white/5 hover:bg-white/10 border border-white/10 text-white'}`}
                    onClick={() => setActiveCampForSlots(activeCampForSlots === camp.id ? null : camp.id)}
                  >
                    <Clock className={`w-3.5 h-3.5 mr-1.5 ${activeCampForSlots === camp.id ? 'text-white' : 'text-rose-400'}`} />
                    {activeCampForSlots === camp.id ? 'Close Slots' : 'Manage Slots'}
                  </Button>
                </div>
              </div>

              {/* Collapsible Slot Manager */}
              {activeCampForSlots === camp.id && (
                <div className="bg-black/20 border-t border-white/5 p-6 animate-in slide-in-from-top-2 duration-300">
                  <h4 className="text-white font-semibold mb-4 flex items-center gap-2">
                    <Clock className="w-4 h-4 text-rose-400" /> Appointments & Walk-ins
                  </h4>
                  <SlotManager hostType="camp" hostId={camp.id} />
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {campToGeneratePost && (
        <CampPostGenerator 
          camp={campToGeneratePost} 
          onClose={() => setCampToGeneratePost(null)} 
        />
      )}
    </div>
  );
}
