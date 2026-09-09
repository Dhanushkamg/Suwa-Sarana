'use client';

import { useState, useEffect } from 'react';
import { ArrowLeft, Heart, MapPin, Activity, CheckCircle2, XCircle, Clock } from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import { API_BASE_URL } from '@/lib/constants';
import apiClient from '@/lib/apiClient';

type DonorMatch = {
  id: number;
  requestId: number;
  patientBloodType: string;
  urgency: string;
  hospitalName: string;
  district: string;
  status: string;
  createdAt: string;
};

export default function DonorMatchesPage() {
  const { t } = useI18n();
  const [matches, setMatches] = useState<DonorMatch[]>([]);
  const [loading, setLoading] = useState(true);
  const [respondingId, setRespondingId] = useState<number | null>(null);

  const loadMatches = async () => {
    try {
      const res = await apiClient.get<DonorMatch[]>('/donors/matches');
      setMatches(res.data || []);
    } catch {
      // Mock fallback for demonstration
      setMatches([
        {
          id: 1,
          requestId: 101,
          patientBloodType: 'O+',
          urgency: 'CRITICAL',
          hospitalName: 'National Hospital of Sri Lanka',
          district: 'Colombo',
          status: 'PENDING',
          createdAt: new Date().toISOString(),
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMatches();
  }, []);

  // Subscribe to SSE for real-time new match notifications
  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const eventSource = new EventSource(`${API_BASE_URL}/notifications/stream`);

    eventSource.addEventListener('NEW_MATCH', () => {
      loadMatches(); // Reload matches when a new one comes in
    });

    eventSource.onerror = () => {
      eventSource.close();
    };

    return () => eventSource.close();
  }, []);

  const respond = async (matchId: number, response: 'ACCEPTED' | 'DECLINED') => {
    setRespondingId(matchId);
    try {
      await apiClient.post(`/matches/${matchId}/respond?response=${response}`);
      setMatches((prev) =>
        prev.map((m) => (m.id === matchId ? { ...m, status: response } : m))
      );
    } catch {
      // Handle error
    } finally {
      setRespondingId(null);
    }
  };

  return (
    <div className="max-w-3xl mx-auto">
      <div className="mb-6">
        <Link href="/dashboard" className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4">
          <ArrowLeft className="w-4 h-4" />
          {t('common.back')}
        </Link>
        <h1 className="text-3xl font-bold text-white flex items-center gap-3">
          <Heart className="w-8 h-8 text-red-500 fill-red-500" />
          My Matches
        </h1>
        <p className="text-gray-400 mt-2">Respond to blood donation requests near you.</p>
      </div>

      {loading ? (
        <div className="text-center py-12 text-gray-500">{t('common.loading')}</div>
      ) : matches.length === 0 ? (
        <div className="glass-card rounded-2xl p-12 text-center border border-white/5">
          <Heart className="w-12 h-12 text-gray-600 mx-auto mb-4" />
          <p className="text-gray-400 font-medium">No active matches at the moment.</p>
          <p className="text-gray-600 text-sm mt-1">Make sure your profile is set to available.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {matches.map((match) => (
            <div
              key={match.id}
              className="glass-card rounded-2xl p-6 border border-white/10 hover:border-red-500/20 transition-all duration-200"
            >
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-4">
                  <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-red-500/20 to-rose-600/20 border border-red-500/30 flex items-center justify-center text-xl font-black text-red-400">
                    {match.patientBloodType}
                  </div>
                  <div>
                    <h2 className="font-bold text-white text-lg">{match.hospitalName}</h2>
                    <p className="text-sm text-gray-400 flex items-center gap-2 mt-1">
                      <MapPin className="w-3.5 h-3.5" /> {match.district}
                    </p>
                  </div>
                </div>

                {match.urgency === 'CRITICAL' && (
                  <span className="flex items-center gap-1.5 text-xs text-red-400 font-bold bg-red-500/10 border border-red-500/20 px-3 py-1.5 rounded-full">
                    <span className="w-2 h-2 rounded-full bg-red-400 animate-ping" />
                    CRITICAL
                  </span>
                )}
                {match.urgency === 'URGENT' && (
                  <span className="text-xs text-amber-400 font-medium bg-amber-500/10 border border-amber-500/20 px-3 py-1.5 rounded-full">
                    URGENT
                  </span>
                )}
              </div>

              <div className="flex items-center gap-2 text-xs text-gray-500 mb-5">
                <Clock className="w-3.5 h-3.5" />
                Received: {new Date(match.createdAt).toLocaleString()}
                <span className="mx-2 text-gray-700">·</span>
                <Activity className="w-3.5 h-3.5" />
                Status: <span className="font-medium text-white ml-1">{match.status}</span>
              </div>

              {match.status === 'PENDING' && (
                <div className="flex gap-3">
                  <button
                    onClick={() => respond(match.id, 'ACCEPTED')}
                    disabled={respondingId === match.id}
                    className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20 transition-colors font-medium text-sm disabled:opacity-50"
                  >
                    <CheckCircle2 className="w-4 h-4" />
                    Accept & Donate
                  </button>
                  <button
                    onClick={() => respond(match.id, 'DECLINED')}
                    disabled={respondingId === match.id}
                    className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/20 transition-colors font-medium text-sm disabled:opacity-50"
                  >
                    <XCircle className="w-4 h-4" />
                    Decline
                  </button>
                </div>
              )}

              {match.status === 'ACCEPTED' && (
                <div className="flex items-center gap-2 text-emerald-400 text-sm font-medium">
                  <CheckCircle2 className="w-5 h-5" /> You accepted this match. Thank you!
                </div>
              )}
              {match.status === 'DECLINED' && (
                <div className="flex items-center gap-2 text-gray-500 text-sm">
                  <XCircle className="w-5 h-5" /> You declined this match.
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
