'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { ArrowLeft, Clock, MapPin, Activity, Droplets, CheckCircle2 } from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { API_BASE_URL } from '@/lib/constants';
import { BloodRequest } from '@/types';

export default function RequestLiveStatusPage() {
  const { t } = useI18n();
  const params = useParams();
  const router = useRouter();
  const requestId = params.id as string;

  const [requestData, setRequestData] = useState<BloodRequest | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [updates, setUpdates] = useState<string[]>([]);

  useEffect(() => {
    async function loadRequest() {
      try {
        const res = await apiClient.get<BloodRequest>(`/requests/${requestId}`);
        setRequestData(res.data);
      } catch (err) {
        setError(t('common.error'));
      } finally {
        setLoading(false);
      }
    }
    loadRequest();
  }, [requestId, t]);

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const eventSource = new EventSource(`${API_BASE_URL}/notifications/stream?token=${token}`);

    eventSource.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.requestId === Number(requestId) || data.type === 'NEW_MATCH') {
        setUpdates((prev) => [data.body, ...prev].slice(0, 5));
        
        // Reload request data to get updated status/radius
        apiClient.get<BloodRequest>(`/requests/${requestId}`).then((res) => {
          setRequestData(res.data);
        });
      }
    };

    eventSource.onerror = () => {
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, [requestId]);

  if (loading) return <div className="text-center py-12 text-gray-500">{t('common.loading')}</div>;
  if (error || !requestData) return <div className="text-center py-12 text-red-500">{error || 'Request not found'}</div>;

  return (
    <div className="max-w-3xl mx-auto">
      <Link href="/dashboard/requests" className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-6">
        <ArrowLeft className="w-4 h-4" />
        {t('common.back')}
      </Link>

      <div className="glass-card p-6 rounded-2xl border border-white/10 mb-6">
        <div className="flex items-start justify-between mb-6">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-red-500/20 to-rose-600/20 border border-red-500/30 flex items-center justify-center text-2xl font-black text-red-400">
              {requestData.patientBloodType}
            </div>
            <div>
              <h1 className="text-2xl font-bold text-white">{requestData.hospitalName}</h1>
              <p className="text-gray-400 flex items-center gap-2 mt-1">
                <MapPin className="w-4 h-4" /> {requestData.district}
              </p>
            </div>
          </div>
          <div className="text-right">
            <div className="inline-block px-3 py-1 rounded-full bg-blue-500/10 border border-blue-500/20 text-blue-400 font-medium text-sm mb-2">
              {requestData.status}
            </div>
            <p className="text-xs text-gray-500 font-mono">ID: #{requestData.id}</p>
          </div>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="p-4 rounded-xl bg-white/5">
            <p className="text-xs text-gray-400 mb-1">{t('request.unitsNeeded')}</p>
            <p className="text-lg font-bold text-white flex items-center gap-2">
              <Droplets className="w-4 h-4 text-red-400" /> {requestData.unitsNeeded}
            </p>
          </div>
          <div className="p-4 rounded-xl bg-white/5">
            <p className="text-xs text-gray-400 mb-1">{t('request.urgencyLevel')}</p>
            <p className="text-lg font-bold text-white flex items-center gap-2">
              <Activity className="w-4 h-4 text-amber-400" /> {requestData.urgency}
            </p>
          </div>
          <div className="p-4 rounded-xl bg-white/5">
            <p className="text-xs text-gray-400 mb-1">Search Radius</p>
            <p className="text-lg font-bold text-white flex items-center gap-2">
              <MapPin className="w-4 h-4 text-emerald-400" /> {requestData.currentRadiusKm} km
            </p>
          </div>
          <div className="p-4 rounded-xl bg-white/5">
            <p className="text-xs text-gray-400 mb-1">Expires</p>
            <p className="text-lg font-bold text-white flex items-center gap-2 text-sm">
              <Clock className="w-4 h-4 text-gray-400" /> 
              {new Date(requestData.expiresAt).toLocaleTimeString()}
            </p>
          </div>
        </div>
      </div>

      <div className="glass-card p-6 rounded-2xl border border-white/10">
        <h2 className="text-lg font-bold text-white mb-4 flex items-center gap-2">
          <Activity className="w-5 h-5 text-emerald-400" />
          Live Updates
        </h2>
        
        {updates.length === 0 ? (
          <div className="text-center py-8 text-gray-500 border border-dashed border-white/10 rounded-xl">
            <div className="animate-pulse flex flex-col items-center gap-2">
              <Activity className="w-6 h-6 text-emerald-500/50" />
              <p>Scanning radius for eligible donors...</p>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            {updates.map((msg, idx) => (
              <div key={idx} className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-start gap-3">
                <CheckCircle2 className="w-5 h-5 text-emerald-400 mt-0.5 flex-shrink-0" />
                <p className="text-emerald-200 text-sm">{msg}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
