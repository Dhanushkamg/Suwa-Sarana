'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { Calendar, MapPin, Clock, Heart, ArrowLeft } from 'lucide-react';
import SlotPicker from '@/components/slots/SlotPicker';

interface Camp {
  id: number;
  name: string;
  district: string;
  location: string;
  scheduledDate: string;
  startTime: string;
  endTime: string;
  organizerName: string;
}

export default function CampsPage() {
  const { t } = useI18n();
  const [camps, setCamps] = useState<Camp[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedCampId, setExpandedCampId] = useState<number | null>(null);

  useEffect(() => {
    const fetchCamps = async () => {
      try {
        const response = await apiClient.get('/camps');
        setCamps(response.data);
      } catch (error) {
        console.error('Failed to load camps', error);
      } finally {
        setLoading(false);
      }
    };
    fetchCamps();
  }, []);

  return (
    <div className="min-h-screen bg-[#0d0d14] text-white">
      {/* Header */}
      <header className="border-b border-white/5 bg-[#0d0d14]/80 backdrop-blur-xl sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Link
              href="/"
              className="flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors"
            >
              <ArrowLeft className="w-4 h-4" /> Back to Home
            </Link>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/30">
              <Heart className="w-4 h-4 text-white fill-white" />
            </div>
            <span className="font-bold text-white">Suwa Sarana</span>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-6 py-12">
        <div className="mb-10 text-center max-w-2xl mx-auto">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-red-500/10 border border-red-500/20 text-red-400 text-xs font-semibold mb-4">
            <Calendar className="w-3.5 h-3.5" />
            Community Blood Drives
          </div>
          <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight text-white mb-4">
            Upcoming Donation Camps
          </h1>
          <p className="text-gray-400 text-sm md:text-base">
            Find and pre-register for upcoming blood donation camps organized across Sri Lanka in partnership with the National Blood Transfusion Service.
          </p>
        </div>


        {loading ? (
          <div className="flex justify-center items-center py-20 text-gray-400">
            <div className="w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin mr-3" />
            Loading upcoming donation camps...
          </div>
        ) : camps.length === 0 ? (
          <div className="text-center py-20 border border-white/5 rounded-2xl bg-white/[0.02]">
            <Calendar className="w-12 h-12 text-gray-500 mx-auto mb-4 opacity-50" />
            <h3 className="text-lg font-semibold text-white mb-2">No Scheduled Camps</h3>
            <p className="text-gray-400 text-sm max-w-md mx-auto">
              There are currently no active donation camps scheduled. Please check back soon or register as an on-demand donor.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {camps.map((camp) => (
              <div
                key={camp.id}
                className="bg-white/[0.03] hover:bg-white/[0.05] border border-white/10 hover:border-red-500/30 rounded-2xl p-6 transition-all duration-300 flex flex-col"
              >
                <div>
                  <div className="flex items-start justify-between gap-4 mb-4">
                    <h3 className="text-xl font-bold text-white">
                      {camp.name}
                    </h3>
                    <span className="px-2.5 py-1 text-xs font-semibold rounded-lg bg-red-500/10 text-red-400 border border-red-500/20 shrink-0">
                      {camp.district}
                    </span>
                  </div>

                  <div className="space-y-3 mb-4 text-sm text-gray-300">
                    <div className="flex items-center gap-3">
                      <Calendar className="w-4 h-4 text-red-400 flex-shrink-0" />
                      <span>{camp.scheduledDate}</span>
                    </div>
                    <div className="flex items-center gap-3">
                      <Clock className="w-4 h-4 text-amber-400 flex-shrink-0" />
                      <span>{camp.startTime} - {camp.endTime}</span>
                    </div>
                    <div className="flex items-center gap-3">
                      <MapPin className="w-4 h-4 text-emerald-400 flex-shrink-0" />
                      <span>{camp.location}</span>
                    </div>
                  </div>
                </div>

                {/* Slot booking toggle */}
                <button
                  className="text-xs text-red-400 hover:text-red-300 transition-colors text-left mb-2 font-medium"
                  onClick={() => setExpandedCampId(expandedCampId === camp.id ? null : camp.id)}
                >
                  {expandedCampId === camp.id ? '▲ Hide time slots' : '▼ View & book a time slot'}
                </button>

                {expandedCampId === camp.id && (
                  <SlotPicker hostType="camp" hostId={camp.id} />
                )}
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
