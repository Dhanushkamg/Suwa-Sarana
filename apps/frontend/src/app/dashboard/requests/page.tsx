'use client';

import { useState, useEffect } from 'react';
import { Droplets, Plus, MapPin, Clock, ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { BloodRequest } from '@/types';

export default function RequestsListPage() {
  const { t } = useI18n();
  const [requests, setRequests] = useState<BloodRequest[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadRequests() {
      try {
        const res = await apiClient.get<any>('/requests');
        const reqData = res.data?.data ?? res.data;
        setRequests(reqData || []);
      } catch {
        // Mock fallback for demonstration
        setRequests([
          {
            id: 101,
            patientBloodType: 'O+',
            urgency: 'CRITICAL',
            status: 'ESCALATING',
            hospitalName: 'National Hospital of Sri Lanka',
            district: 'Colombo',
            currentRadiusKm: 15,
            expiresAt: new Date(Date.now() + 3600000 * 24).toISOString(),
          },
          {
            id: 102,
            patientBloodType: 'B-',
            urgency: 'URGENT',
            status: 'OPEN',
            hospitalName: 'Teaching Hospital Karapitiya',
            district: 'Galle',
            currentRadiusKm: 5,
            expiresAt: new Date(Date.now() + 3600000 * 48).toISOString(),
          },
        ]);
      } finally {
        setLoading(false);
      }
    }
    loadRequests();
  }, []);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'OPEN':
        return <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-blue-500/10 text-blue-400 border border-blue-500/20">{t('request.statusOpen')}</span>;
      case 'ESCALATING':
        return <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-amber-500/10 text-amber-400 border border-amber-500/20">{t('request.statusEscalating')}</span>;
      case 'MATCHED':
        return <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">{t('request.statusMatched')}</span>;
      case 'FULFILLED':
        return <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-purple-500/10 text-purple-400 border border-purple-500/20">{t('request.statusFulfilled')}</span>;
      default:
        return <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-gray-500/10 text-gray-400 border border-gray-500/20">{status}</span>;
    }
  };

  const getUrgencyBadge = (urgency: string) => {
    switch (urgency) {
      case 'CRITICAL':
        return <span className="text-xs text-red-400 font-semibold flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-red-400 animate-ping" /> CRITICAL</span>;
      case 'URGENT':
        return <span className="text-xs text-amber-400 font-medium">URGENT</span>;
      default:
        return <span className="text-xs text-gray-400">ROUTINE</span>;
    }
  };

  return (
    <div className="max-w-4xl mx-auto">
      <div className="mb-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <Link
            href="/dashboard"
            className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4"
          >
            <ArrowLeft className="w-4 h-4" />
            {t('common.back')}
          </Link>
          <h1 className="text-3xl font-bold text-white flex items-center gap-3">
            <Droplets className="w-8 h-8 text-red-500" />
            {t('common.bloodRequests')}
          </h1>
          <p className="text-gray-400 mt-1">{t('dashboard.activeRequestsDesc')}</p>
        </div>

        <Link
          href="/dashboard/requests/new"
          className="inline-flex items-center gap-2 bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold text-sm px-5 py-2.5 rounded-xl shadow-lg shadow-red-500/25 transition-all duration-200 self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          {t('dashboard.newRequest')}
        </Link>
      </div>

      {loading ? (
        <div className="text-center py-12 text-gray-500">{t('common.loading')}</div>
      ) : (
        <div className="space-y-4">
          {requests.map((req) => (
            <div
              key={req.id}
              className="glass-card rounded-2xl p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-white/5 hover:border-red-500/20 transition-all duration-200"
            >
              <div className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-red-500/20 to-rose-600/20 border border-red-500/30 flex items-center justify-center text-lg font-black text-red-400 flex-shrink-0">
                  {req.patientBloodType}
                </div>
                <div>
                  <div className="flex items-center gap-3 mb-1">
                    <span className="font-bold text-white text-base">{req.hospitalName}</span>
                    {getStatusBadge(req.status)}
                  </div>
                  <div className="flex flex-wrap items-center gap-4 text-xs text-gray-400">
                    <span className="flex items-center gap-1">
                      <MapPin className="w-3.5 h-3.5 text-gray-500" />
                      {req.district} ({req.currentRadiusKm} km radius)
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock className="w-3.5 h-3.5 text-gray-500" />
                      {getUrgencyBadge(req.urgency)}
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 self-end sm:self-auto">
                <span className="text-xs text-gray-500 font-mono">#{req.id}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
